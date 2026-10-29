plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.21.11-neoforge"

stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.parchmentmc.org")
    }
}
