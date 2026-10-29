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

        mc("1.19.2", loaders = listOf("forge", "fabric"))
        mc("1.20.1", loaders = listOf("forge", "fabric"))
        mc("1.21.1", loaders = listOf("neoforge", "fabric"))
        mc("1.21.11", loaders = listOf("neoforge", "fabric"))
        // 26.x 起 Mojang 改用「年份.序号」的版本号体系，26.2 对应 2026 年的第二个正式版
        // 26.2 的 fabric 变体暂时无法注册：Modstitch 0.5.x 的 Loom 路径无条件加 officialMojangMappings()，
        // 而 26.x 起 MC 不再混淆、版本清单里没有 client_mappings，Loom 1.16.2 会在这里直接失败
        mc("26.2", loaders = listOf("neoforge"))

        // src/ 以该变体的代码形态提交，其他变体由预处理器生成
        vcsVersion = "1.20.1-forge"
    }
}

rootProject.name = "RealDamage"

// Modstitch 0.5.12 把 fabric-loom 与 ModDevGradle 都钉成了老版本（strictly 1.10.0-alpha.9 / 2.0.74），
// 而 1.21.11 之后这条线要求更新的工具链：
// - Fabric API 0.138+（含 1.21.11 那一支）是用 Loom 1.13.3 构建的，旧 Loom 会在配置阶段直接拒绝：
//   "Mod was built with a newer version of Loom"；
// - MDG 2.0.74 内置的 neoform-runtime 处理不了 NeoForge 21.11 的补丁包
//   （inject 阶段 ZipException: STORED entry missing size）。
// 插件 classpath 上同名模块按版本冲突取高版本，force 的优先级又高于 strictly，所以这里统一抬高版本。
// 这些插件只对各自平台的变体生效（modstitch.platform 决定），其它变体会用到同一份新版本工具链，
// 因此每次改动都要跑一遍 chiseledBuild 回归全部 10 个变体。
//
// Loom 定在 1.16.2：26.2 的 Fabric API（0.149+ 全部 26.2 构建）manifest 里写的是
// Fabric-Loom-Version: 1.16.2，低于它的 Loom 会在配置阶段直接拒绝；
// 而 Loom 1.16.2 的 Gradle Module Metadata 声明 org.gradle.plugin.api-version = 9.4.0，
// 也就意味着整个构建必须跑在 Gradle 9.4+ 上（见 gradle/wrapper/gradle-wrapper.properties）。
gradle.beforeProject {
    buildscript.configurations.configureEach {
        resolutionStrategy.force(
            "fabric-loom:fabric-loom.gradle.plugin:1.16.2",
            "net.fabricmc:fabric-loom:1.16.2",
            "net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.148",
            "net.neoforged.moddev.legacyforge:net.neoforged.moddev.legacyforge.gradle.plugin:2.0.148",
            "net.neoforged:moddev-gradle:2.0.148",
        )
    }
}
