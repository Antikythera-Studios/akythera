plugins {
    // ModDevGradle is applied in both the ":common" and ":neoforge" projects,
    // so it is declared at the root to ensure the plugin is resolved only once
    // and shared consistently across Gradle projects.
    alias(libs.plugins.moddevgradle) apply false

    // "multiloader-platform" is applied in both the ":neoforge" and ":fabric" projects,
    // and bundles the "com.vanniktech.maven.publish" plugin, whose shared build service
    // requires a single classloader copy across the sibling platform projects.
    id("multiloader-platform") apply false
}
