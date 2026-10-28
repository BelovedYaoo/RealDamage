pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Modstitch
        maven("https://maven.isxander.dev/releases/")
        // Fabric Loom
        maven("https://maven.fabricmc.net/")
        // ModDevGradle
        maven("https://maven.neoforged.net/releases/")
        // Stonecutter
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.6+"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        fun mc(mcVersion: String, name: String = mcVersion, loaders: Iterable<String>) =
            loaders.forEach { vers("$name-$it", mcVersion) }

        // 目标矩阵：Fabric 在后续步骤加入
        mc("1.20.1", loaders = listOf("forge"))
        mc("1.21.1", loaders = listOf("neoforge"))

        // src/ 以该变体的代码形态提交，其他变体由预处理器生成
        vcsVersion = "1.20.1-forge"
    }
}

rootProject.name = "RealDamage"
