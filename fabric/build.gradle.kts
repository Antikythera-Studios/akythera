plugins {
    id("multiloader-platform")
    alias(libs.plugins.fabric.loom)
}

configureBaseArchive(ModLoader.Fabric.name)

repositories {
    parchmentMcRepository()
    terraformersRepository()
}

dependencies {
    minecraft(libs.fabric.minecraft)
    implementation(libs.fabric.loader)

    // Fabric API
    implementation(libs.fabric.api)

    implementation(libs.forgeconfigapiport.fabric) {
        exclude(group = libs.fabric.loader.get().group)
        exclude(group = libs.fabric.api.get().group)
    }

    // PAL implementation
    implementation(libs.playerAnimationLibrary)
    implementation(libs.bendableCuboids)
}

loom {
    // Removed due to lack of NeoForge support: https://github.com/neoforged/ModDevGradle/issues/3 and https://neoforged.net/news/21.5release/#split-sourcesets
//    splitEnvironmentSourceSets()

    accessWidenerPath = file("../common/src/main/resources/akythera.accesswidener")

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            // Removed due to lack of NeoForge support: https://github.com/neoforged/ModDevGradle/issues/3 and https://neoforged.net/news/21.5release/#split-sourcesets
//            sourceSet(sourceSets.named("client").get())
        }
    }
/*
    runs {
        register("datagen") {
            inherit(getByName("client"))
            name("Data Generation")
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${file("src/main/generated")}")
            vmArg("-Dfabric-api.datagen.modid=template-mod")

            runDir("build/datagen")
        }
    }
*/
}

tasks.processResources {
    val replaceProperties = modPlatformMetadataReplaceProperties

    inputs.properties(replaceProperties)

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}

configureModPublish(ModLoader.Fabric) { tasks.jar.get().archiveFile }