// Shared utilities between all Gradle projects or plugins.

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import groovy.util.Node
import me.modmuss50.mpp.ModPublishExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.plugins.signing.SigningExtension
import java.net.URI

// Note: Prefer "strictMaven()" over "maven { ... }" to reduce unnecessary repository lookups (i.e., reduce network calls)
fun RepositoryHandler.strictMaven(name: String, url: String, vararg includeGroups: String) {
    exclusiveContent {
        forRepository { maven { this.name = name; this.url = URI(url) } }
        filter { includeGroups.forEach { includeGroup(it) } }
    }
}

fun RepositoryHandler.parchmentMcRepository() {
    strictMaven(
        "ParchmentMC",
        "https://maven.parchmentmc.org",
        "org.parchmentmc", "org.parchmentmc.data",
    )
}

fun RepositoryHandler.terraformersRepository() {
    strictMaven(
        "Terraformers (Mod Menu)",
        "https://maven.terraformersmc.com",
        "com.terraformersmc",
    )
}

const val generationTaskGroup = "generation"

private val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

private fun Project.catalogVersion(name: String): String = versionCatalog.findVersion(name).get().toString()

private fun Project.gradleProperty(name: String): String {
    return this.providers.gradleProperty(name).get()
}

val Project.modVersion: String
    get() = catalogVersion("mod")

private val Project.modLicense: String
    get() = gradleProperty("license")

val Project.modId: String
    get() = gradleProperty("mod_id")

val Project.mcVersion: String
    get() = catalogVersion("minecraft")

val Project.javaVersion: Int
    get() = catalogVersion("java").toInt()

private val Project.modIssueTracker: String
    get() = gradleProperty("issue_tracker")

private val Project.modDisplayName: String
    get() = gradleProperty("display_name")

private val Project.modDisplayUrl: String
    get() = gradleProperty("display_url")

private val Project.modSourceCode: String
    get() = gradleProperty("source_code")

private val Project.modDescription: String
    get() = gradleProperty("description")

private val Project.modCredits: String
    get() = gradleProperty("credits")

private val Project.modAuthors: String
    get() = gradleProperty("authors")

val Project.groupId: String
    get() = gradleProperty("group_id")

private val Project.mavenArtifactId: String
    get() = gradleProperty("artifact_id")

private val Project.fabricLoaderVersion: String
    get() = catalogVersion("fabric-loader")

private val Project.fabricApiVersion: String
    get() = catalogVersion("fabric-api")

private val Project.neoForgeVersion: String
    get() = catalogVersion("neoforge")

/**
 * Configures the JAR file names to be unique, clear, and consistent.
 */
fun Project.configureBaseArchive(variant: String) {
    extensions.getByType(BasePluginExtension::class.java).apply {
        // "epic-fight" is intentionally being used instead of the mod ID (i.e., modId),
        // to keep the JAR file name consistent with the mod project slug URL,
        // and therefore Modrinth will automatically download the sources JAR file: https://support.modrinth.com/en/articles/8801191-modrinth-maven#h_1b24106498
        // This workaround is not needed if the mod ID matches the project slug.
        archivesName.set(modDisplayName)
        version = getFullModVersion(variant)
    }
}

private fun Project.getFullModVersion(variant: String): String = "$variant-mc${mcVersion}-${modVersion}"

private fun Project.getCompactModVersion(variant: String): String = "$variant-${modVersion}"

enum class ModLoader(val conventionalName: String) {
    NeoForge("NeoForge"),
    Fabric("Fabric"),
    ;

    val isForgeLike: Boolean
        get() = this == NeoForge

    val isFabricLike: Boolean
        get() = this == Fabric
}

private fun Project.extractCurrentVersionChangelog(): String? {
    val changelogFile = rootProject.file("CHANGELOG.md")
    val fullChangelogText = changelogFile.readText()

    // Extracts the current version changelog without the version heading 2 and "For Devs" heading 3.
    val versionSectionRegex =
        "(?s)## \\[$modVersion\\] - \\d{4}-\\d{2}-\\d{2}\\R(.*?)(?=\\R### For Devs|\\R## \\[.*?\\] |\\Z)"
    val matcher = Regex(versionSectionRegex).find(fullChangelogText) ?: return null

    val versionChangelog = matcher.groupValues[1]
    return versionChangelog
}

private fun Project.buildReleaseChangelog(
    currentVersionChangelog: String,
    modLoader: ModLoader,
): String {
    return buildString {
        append(currentVersionChangelog)
        appendLine(); appendLine()

        val modLoaderName = modLoader.name
        val modLoaderVersion: String = when (modLoader) {
            ModLoader.NeoForge -> neoForgeVersion
            ModLoader.Fabric -> fabricLoaderVersion
        }

        append(
            """
            ### **Tested against:**
            - **Minecraft:** $mcVersion
            - **$modLoaderName:** $modLoaderVersion
            """.trimIndent()
        )

        if (modLoader.isFabricLike) {
            appendLine("- **Fabric API:** $fabricApiVersion")
        }
    }
}

/**
 * Configures the mod publishing to mod sites (e.g., Modrinth, CurseForge).
 */
fun Project.configureModPublish(
    modLoader: ModLoader,
    jarFile: () -> Provider<RegularFile>,
) {
    val project = this
    project.tasks.named("publishMods") {
        doLast {
            if (extractCurrentVersionChangelog() == null) {
                throw GradleException("Cannot run publishMods task: No changelog found for version $modVersion in CHANGELOG.md file")
            }
        }
    }
    extensions.getByType(ModPublishExtension::class.java).apply {
        // Assumes `java { withSourcesJar() }` is called.
        val sourcesJar = project.tasks.named("sourcesJar")

        dryRun.set(false)

        val latestChangelog = extractCurrentVersionChangelog() ?: run {
            logger.warn("Skipping the publishMods configuration: No changelog found for version $modVersion in CHANGELOG.md file")
            return@apply
        }
        val releaseChangelog = project.buildReleaseChangelog(latestChangelog, modLoader)
        changelog.set(releaseChangelog)

        modLoaders.add(modLoader.conventionalName)
        val fullVersion = project.getFullModVersion(modLoader.conventionalName);
        displayName.set("$modDisplayName+$fullVersion")
        version.set(project.getCompactModVersion(modLoader.conventionalName))
        type.set(STABLE)
        file.set(jarFile())
        additionalFiles.from(sourcesJar)

        val requiredDependencies = buildList {
            if (modLoader.isFabricLike) {
                add("fabric-api")
                add("forge-config-api-port")
            }
        }

        val optionalDependencies =
            buildList {
                add("player-animation-library")
                add("bendable-cuboids")

                if (modLoader.isFabricLike) {
                    add("modmenu")
                }
            }

        curseforge {
            accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
            projectId.set("1594145")
            minecraftVersions.add(mcVersion)
            projectSlug.set("akythera")

            client.set(true);
            server.set(true);

            requiredDependencies.forEach { requires(it) }
            optionalDependencies.forEach { optional(it) }
        }

        modrinth {
            accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
            projectId.set("jHm8Ac9W")
            minecraftVersions.add(mcVersion)

            requiredDependencies.forEach { requires(it) }
            optionalDependencies.forEach { optional(it) }
        }
        /*
         * Disable discord alarm until we officially release the mod
        discord {
            webhookUrl.set(providers.environmentVariable("DISCORD_WEBHOOK"))
            dryRunWebhookUrl.set(providers.environmentVariable("DRY_RUN_DISCORD_WEBHOOK"))
            username.set("Update Notification")
            avatarUrl.set("https://i.imgur.com/FrxDviN.png")
            content.set(
                changelog.map {
                    buildString {
                        appendLine("<@&1074034800849059930>")
                        appendLine("# Akythera $modVersion is out!")
                        appendLine(releaseChangelog)
                    }
                }
            )

            style {
                look.set("MODERN")
                link.set("EMBED")
                thumbnailUrl.set("https://i.imgur.com/nI8xOCy.png")
            }
        }
        */
    }
}

/**
 * Configures publishing the platform mod JAR file to Maven Central,
 * using the "com.vanniktech.maven.publish" Gradle plugin.
 *
 * The uploaded JAR files are the same ones produced by the "build" task:
 * the plugin publishes the "java" component, which Fabric Loom wires to the remapped JAR
 * (and ModDevGradle leaves as the plain "jar" task output).
 *
 * The group and artifact IDs are read from "gradle.properties" ("group_id" and "artifact_id");
 * the artifact ID is suffixed with the mod loader name (e.g., "akythera-neoforge"),
 * since each platform needs unique Maven coordinates.
 *
 * Run "gradlew publishToMavenCentral" to upload (or "publishAndReleaseToMavenCentral" to also
 * release automatically). Requires the "mavenCentralUsername", "mavenCentralPassword" and
 * "signingInMemoryKey" Gradle properties, or their "ORG_GRADLE_PROJECT_*" environment variables:
 * https://vanniktech.github.io/gradle-maven-publish-plugin/central/#secrets
 */
fun Project.configureMavenCentralPublish(modLoader: ModLoader) {
    val project = this

    extensions.getByType(MavenPublishBaseExtension::class.java).apply {
        publishToMavenCentral()
        signAllPublications()

        // Signing is skipped when no key is configured, so "publishToMavenLocal" works without
        // credentials. Maven Central rejects unsigned uploads regardless, so this cannot
        // accidentally publish an unsigned release.
        extensions.getByType(SigningExtension::class.java).isRequired =
            providers.gradleProperty("signingInMemoryKey").isPresent

        // Maven Central requires a javadoc JAR; an empty one is uploaded since
        // javadoc doesn't run cleanly on the mod sources.
        configure(JavaLibrary(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))

        // The version is intentionally not passed to coordinates(), since it would override
        // "project.version" and therefore the JAR file names set by configureBaseArchive().
        coordinates(
            groupId = project.groupId,
            artifactId = "${project.mavenArtifactId}-${modLoader.name.lowercase()}",
        )

        pom {
            name.set("$modDisplayName (${modLoader.conventionalName})")
            description.set(modDescription)
            url.set(modDisplayUrl)

            licenses {
                license {
                    name.set(modLicense)
                    url.set("${modSourceCode.trimEnd('/')}/blob/main/LICENSE")
                }
            }

            developers {
                modAuthors.split(",").forEach { author ->
                    developer { name.set(author.trim()) }
                }
            }

            scm {
                url.set(modSourceCode)
                connection.set("scm:git:${modSourceCode.trimEnd('/')}.git")
                developerConnection.set("scm:git:${modSourceCode.trimEnd('/')}.git")
            }

            issueManagement {
                url.set(modIssueTracker)
            }

            withXml {
                // The ":common" classes and resources are bundled into the platform JAR
                // (see multiloader-platform.gradle.kts), and ":common" itself is not published,
                // so it must not be declared as a POM dependency.
                asNode().childrenNamed("dependencies").forEach { dependenciesNode ->
                    dependenciesNode.childrenNamed("dependency")
                        .filter { dependency ->
                            dependency.childrenNamed("groupId").any { it.text() == project.groupId } &&
                                    dependency.childrenNamed("artifactId").any { it.text() == "common" }
                        }
                        .forEach { dependenciesNode.remove(it) }
                }
            }
        }
    }

    // Distinguishes uploads per Minecraft version, since the platform artifact ID stays the same across them.
    extensions.getByType(PublishingExtension::class.java)
        .publications.withType(MavenPublication::class.java)
        .configureEach { version = "${mcVersion}-${modVersion}" }

    // Unlike the POM, the Gradle module metadata cannot drop the bundled ":common" project
    // dependency, so only the POM is published.
    tasks.withType(GenerateModuleMetadata::class.java).configureEach { isEnabled = false }
}

// XML element lookup by local name, since POM elements are namespaced (groovy.namespace.QName).
private fun Node.childrenNamed(localName: String): List<Node> =
    children().filterIsInstance<Node>().filter {
        val name = it.name()
        name == localName || (name is groovy.namespace.QName && name.localPart == localName)
    }

/**
 * The placeholders to be replaced with in mod loader metadata (fabric.mod.json, neoforge.mods.toml).
 *
 * ### **Example:**
 *
 * ```json
 * "id": "${modId}"
 * ```
 *
 * Gradle projects are expected to use this in `tasks.processResources {}` configuration,
 * to replace the properties.
 *
 * This is done to avoid hardcoding the values directly in these resource files, and allows sharing them
 * in "gradle.properties".
 */
val Project.modPlatformMetadataReplaceProperties: Map<String, Any>
    get() = mapOf(
        "modId" to modId,
        "modVersion" to modVersion,
        "license" to modLicense,
        "issueTracker" to modIssueTracker,
        "displayName" to modDisplayName,
        "displayUrl" to modDisplayUrl,
        "description" to modDescription,
        "authors" to modAuthors,
        // Fabric requires authors as a JSON array; Forge accepts a single string.
        // Authors are read from gradle.properties, comma-separated.
        // This converts `X, Y` -> `"X", "Y"` for Fabric support.
        "authorEntries" to modAuthors
            .split(",")
            .joinToString(
                separator = "\", \"",
                prefix = "\"",
                postfix = "\""
            ) { it.trim() },
        "credits" to modCredits,
        "sourceCode" to modSourceCode,
        "minecraft" to mcVersion,
        "neoforgeVersion" to neoForgeVersion,
        "javaVersion" to javaVersion,
        // TODO: when forgeconfigapiport ported to 26.1 import the project
        // "forgeconfigapiportVersion" to catalogVersion("forgeconfigapiport"),
        "fabricLoader" to fabricLoaderVersion,
    )
