plugins {
    id("dev.isxander.modstitch.base") version "0.5.12"
}

fun prop(name: String, consumer: (prop: String) -> Unit) {
    (findProperty(name) as? String?)?.let(consumer)
}

val minecraft = property("deps.minecraft") as String

// 1.21 起运行时最低就是 Java 21；构建 JVM 本身由 Modstitch 要求为 21，两者在 1.21 上重合
val javaTargetVersion = when (minecraft) {
    "1.20.1" -> 17
    "1.21.1" -> 21
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的 Java 版本")
}

// 每个 MC 版本对应的资源包/数据包格式号，写错会让客户端提示资源包不兼容
val resourcePackFormat = when (minecraft) {
    "1.20.1" -> 15
    "1.21.1" -> 34
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的资源包格式版本")
}

// mixins.json 的 compatibilityLevel 必须不低于 mixin 类自身的字节码版本，
// 否则 Mixin 会拒绝处理版本更高的 mixin 类。JSON 里没法写 Stonecutter 注释守卫，
// 因此改由占位符注入（展开位置见文件末尾的 processResources 配置）。
// modId 在多处用到（清单占位符、mixin 配置名），抽成变量免得多处各写一遍后不同步。
// 名字不能叫 modId：metadata { } 里同名会被解析成 receiver 自己的属性，形成自赋值
val modIdConst = "realdamage"

val javaCompatibilityLevel = "JAVA_$javaTargetVersion"

// 加载器清单的差异集中在这里，两个模板文件共享同一套占位符，避免模板之间各自漂移
data class LoaderManifest(
    val modId: String,
    val versionRange: String,
    // 语言加载器（javafml）自身的版本号，跟加载器发行版本号是两回事
    val languageVersion: String,
    val minecraftVersionRange: String,
)

val loaderManifest = when (val loader = name.split("-")[1]) {
    "forge" -> LoaderManifest(
        modId = "forge",
        versionRange = "[47,)",
        languageVersion = "[47,)",
        minecraftVersionRange = "[1.20.1,1.21)",
    )

    "neoforge" -> LoaderManifest(
        modId = "neoforge",
        versionRange = "[21.1,)",
        languageVersion = "[4,)",
        minecraftVersionRange = "[1.21,1.21.2)",
    )

    else -> throw IllegalArgumentException("请在此登记 ${loader} 对应的加载器清单信息")
}

modstitch {
    minecraftVersion = minecraft

    javaTarget = javaTargetVersion

    parchment {
        prop("deps.parchment") { mappingsVersion = it }
    }

    metadata {
        modId = modIdConst
        modName = "Real Damage"
        modVersion = "1.0.0"
        modGroup = "top.bemylover.realdamage"
        modAuthor = "PrefersMin"

        // Modstitch 未内建的占位符需要自行登记；populate 是 MapProperty 的局部扩展
        fun <K, V> MapProperty<K, V>.populate(block: MapProperty<K, V>.() -> Unit) {
            block()
        }

        replacementProperties.populate {
            put("mod_license", "0BSD")
            put("mod_description", "Do you like this?")
            put("pack_format", resourcePackFormat.toString())
            put("java_compatibility", javaCompatibilityLevel)
            put("loader_mod_id", loaderManifest.modId)
            put("loader_version_range", loaderManifest.versionRange)
            put("loader_language_version", loaderManifest.languageVersion)
            put("minecraft_version_range", loaderManifest.minecraftVersionRange)
        }
    }

    // ModDevGradle：1.20.1 走 legacyforge
    moddevgradle {
        enable {
            prop("deps.forge") { forgeVersion = it }
            prop("deps.neoforge") { neoForgeVersion = it }
        }
        defaultRuns()
        configureNeoforge {
            runs.all { disableIdeRun() }
        }
    }

    // Modstitch 的 reobf 会重映射 mixin refmap，因此即使当前没有 mixin
    // 也必须注册配置，否则 refmap 不会被生成、reobfJar 会失败
    mixin {
        addMixinsToModManifest = true
        configs.register("realdamage")
    }
}

// Stonecutter 常量：供 //? if <loader> { 注释守卫使用
val constraint: String = name.split("-")[1]
stonecutter {
    consts(
        "fabric" to constraint.equals("fabric"),
        "neoforge" to constraint.equals("neoforge"),
        "forge" to constraint.equals("forge"),
    )
}

dependencies {
    // 编译期需要该库；运行时则需要 Jar-in-Jar 把它打进产物，两者缺一不可
    implementation("ch.obermuhlner:big-math:2.3.2")
    add("jarJar", "ch.obermuhlner:big-math:2.3.2")
}

// Modstitch 本来会按平台排除「别的加载器」的清单，但它拿文件基名（mods.toml）
// 去比对带目录的清单路径（META-INF/mods.toml），实际匹配不上，于是 legacy 产物里
// 也混进了 neoforge.mods.toml。这里按路径再排一次，保证每个变体只带自己那份清单。
val otherLoaderManifests =
    listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml") - modstitch.modLoaderManifest
tasks.withType<ProcessResources>().configureEach {
    exclude(otherLoaderManifests)
}

// src/main/resources 不经过 Modstitch 的占位符渲染（那一套只作用于 templates），
// 所以 mixins.json 里的 ${java_compatibility} 需要在这里单独展开
tasks.named<ProcessResources>("processResources") {
    filesMatching("$modIdConst.mixins.json") {
        expand(mapOf("java_compatibility" to javaCompatibilityLevel))
    }
}

// Modstitch 在 legacyforge 平台会无条件注册 <modId>.refmap.json 及其映射文件，
// 但 mixin 注解处理器在项目尚无 mixin 时不会产出这两个文件，导致 reobfJar 找不到输入。
// 当前仓库没有 mixin，故在缺失时补出空文件；日后加入 mixin 后由注解处理器正常产出。
//
// 这段只对 legacyforge 生效：NeoForge/Fabric 不经过 reobf 重映射，不需要 refmap，
// 无条件生成只会往 build/mixin 里塞没人引用的死文件。
if (modstitch.isModDevGradleLegacy) {
    val refmapModId = "realdamage"
    val provideEmptyRefmap = tasks.register("provideEmptyRefmap") {
        val mixinDir = layout.buildDirectory.dir("mixin")
        outputs.dir(mixinDir)
        doLast {
            val dir = mixinDir.get().asFile
            dir.mkdirs()
            val refmap = dir.resolve("$refmapModId.refmap.json")
            if (!refmap.exists()) refmap.writeText("{\n  \"mappings\": {}\n}\n")
            val mappings = dir.resolve("$refmapModId.refmap.json.mappings.tsrg")
            if (!mappings.exists()) mappings.writeText("")
        }
    }

    // jar 与 reobf 都会读取该 refmap，因此一并声明依赖
    listOf("jar", "jarJar", "reobfJar", "reobfJarJar").forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            dependsOn(provideEmptyRefmap)
        }
    }
}
