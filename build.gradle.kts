plugins {
    id("dev.isxander.modstitch.base") version "0.5.12"
}

fun prop(name: String, consumer: (prop: String) -> Unit) {
    (findProperty(name) as? String?)?.let(consumer)
}

val minecraft = property("deps.minecraft") as String

// forge / neoforge / fabric
val constraint: String = name.split("-")[1]

// Fabric 变体才有这两个属性；Loom 的加载器版本必须显式声明，Fabric API 版本要跟 MC 对号入座
val fabricLoaderVersion = findProperty("deps.fabricLoader") as? String ?: ""
val fabricApiVersion = findProperty("deps.fabricApi") as? String ?: ""

// Fabric 侧没有 Forge/NeoForge 自带的 nightconfig，需要自己在编译期依赖 + jar-in-jar 打包；
// 版本对齐 1.20.1 Forge 内置的 3.6.4，保证三个加载器用到的 API 子集是同一份
val nightConfigVersion = "3.6.4"

// 1.21 起运行时最低就是 Java 21；构建 JVM 本身由 Modstitch 要求为 21，两者在 1.21 上重合
val javaTargetVersion = when (minecraft) {
    "1.19.2" -> 17
    "1.20.1" -> 17
    "1.21.1" -> 21
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的 Java 版本")
}

// 每个 MC 版本对应的资源包/数据包格式号，写错会让客户端提示资源包不兼容
val resourcePackFormat = when (minecraft) {
    "1.19.2" -> 9
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

// 同一个加载器在不同 MC 版本上的版本范围并不相同（Forge 1.19.2 是 43.x、1.20.1 是 47.x），
// 所以清单信息要同时按 MC 版本与加载器两个维度取值
val loaderManifest = when (minecraft to constraint) {
    "1.19.2" to "forge" -> LoaderManifest(
        modId = "forge",
        versionRange = "[43,)",
        languageVersion = "[43,)",
        minecraftVersionRange = "[1.19.2,1.20)",
    )

    "1.20.1" to "forge" -> LoaderManifest(
        modId = "forge",
        versionRange = "[47,)",
        languageVersion = "[47,)",
        minecraftVersionRange = "[1.20.1,1.21)",
    )

    "1.21.1" to "neoforge" -> LoaderManifest(
        modId = "neoforge",
        versionRange = "[21.1,)",
        languageVersion = "[4,)",
        minecraftVersionRange = "[1.21,1.21.2)",
    )

    // Fabric 的清单是 fabric.mod.json，用的是 Fabric 自己的依赖谓词语法：
    // loader 版本写成 >=x.y.z，MC 版本用 ~（同一次版本）——不能用 Forge 那边的 Maven 区间 [...]
    "1.19.2" to "fabric",
    "1.20.1" to "fabric",
    "1.21.1" to "fabric",
    -> LoaderManifest(
        modId = "fabricloader",
        versionRange = ">=$fabricLoaderVersion",
        languageVersion = "",
        minecraftVersionRange = "~$minecraft",
    )

    else -> throw IllegalArgumentException("请在此登记 ${minecraft} + ${constraint} 对应的加载器清单信息")
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
            // Fabric API 是硬前置（/realDamage 命令要用它的 CommandRegistrationCallback）；
            // 去掉 +1.20.1 这类构建元数据，只留数字版本，Fabric 的版本谓词更好解析
            put("fabric_api_version_range", ">=${fabricApiVersion.substringBefore('+')}")
        }
    }

    // Loom（Fabric）：映射用官方 Mojang 映射 + Parchment，所以 src/ 里的 Mojang 名原样可用
    loom {
        prop("deps.fabricLoader") { fabricLoaderVersion = it }
        configureLoom {
            // 把 refmap 文件名钉死。realdamage-fabric.mixins.json 里要写死这个文件名，
            // 而 Loom 默认按 archivesName 推导（realdamage-refmap.json），变体一多就不该靠巧合
            mixin {
                defaultRefmapName = "realdamage-refmap.json"
            }
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

    // Modstitch 的 reobf 会重映射 mixin refmap，所以必须注册配置（即使该加载器的 mixin 列表是空的），
    // 否则 reobfJar 找不到 refmap 输入
    mixin {
        addMixinsToModManifest = true
        // Fabric 用独立配置文件：mixin 类只存在于 Fabric 产物里，而 JSON 没法用 Stonecutter
        // 注释守卫，只能靠“每个加载器注册自己那份”来隔离，否则 Forge 上会把真实伤害结算两遍
        if (modstitch.isLoom) {
            configs.register("realdamage-fabric")
        } else {
            configs.register("realdamage")
        }
    }
}

// Stonecutter 常量：供 //? if <loader> { 注释守卫使用
stonecutter {
    consts(
        "fabric" to constraint.equals("fabric"),
        "neoforge" to constraint.equals("neoforge"),
        "forge" to constraint.equals("forge"),
    )
}

dependencies {
    // 编译期需要该库；运行时则需要 jar-in-jar 把它打进产物，两者缺一不可
    implementation("ch.obermuhlner:big-math:2.3.2")
    // Modstitch 的平台无关 jar-in-jar 配置：MDG 侧接到 FML 的 jarJar，Loom 侧接到 include。
    // 不能直接用 jarJar：那个配置是 MDG 自己建的，Loom 上没有
    add("modstitchJiJ", "ch.obermuhlner:big-math:2.3.2")

    if (modstitch.isLoom) {
        // nightconfig 只给 Fabric 打包：Forge/NeoForge 运行时已经自带（3.6.4 / 3.8.3）。
        // modstitchJiJ 是 transitive=false，所以 core 与 toml 必须分别声明，否则只打进一半
        implementation("com.electronwill.night-config:toml:$nightConfigVersion")
        add("modstitchJiJ", "com.electronwill.night-config:core:$nightConfigVersion")
        add("modstitchJiJ", "com.electronwill.night-config:toml:$nightConfigVersion")
        // Fabric 的命令注册靠 Fabric API；modImplementation 由 Loom 提供
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    }
}

// Modstitch 本来会按平台排除「别的加载器」的清单，但它拿文件基名（mods.toml）
// 去比对带目录的清单路径（META-INF/mods.toml），实际匹配不上，于是 legacy 产物里
// 也混进了 neoforge.mods.toml。这里按路径再排一次，保证每个变体只带自己那份清单。
val otherLoaderManifests =
    listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "fabric.mod.json") - modstitch.modLoaderManifest
tasks.withType<ProcessResources>().configureEach {
    exclude(otherLoaderManifests)
    // 两份 mixin 配置一一对应各自的加载器：没注册的那份不该留在产物里
    exclude(if (modstitch.isLoom) "$modIdConst.mixins.json" else "$modIdConst-fabric.mixins.json")
}

// src/main/resources 不经过 Modstitch 的占位符渲染（那一套只作用于 templates），
// 所以 mixins.json 里的 ${java_compatibility} 需要在这里单独展开；Fabric 那份同理
tasks.named<ProcessResources>("processResources") {
    filesMatching(listOf("$modIdConst.mixins.json", "$modIdConst-fabric.mixins.json")) {
        expand(mapOf("java_compatibility" to javaCompatibilityLevel))
    }
}

// Modstitch 在 legacyforge 平台会无条件注册 <modId>.refmap.json 及其映射文件，
// 但 mixin 注解处理器只有在真的处理到 mixin 类时才产出这两个文件。
// 目前唯一的 mixin（PlayerActuallyHurtMixin）只在 Fabric 侧编译，Forge/NeoForge 的
// realdamage.mixins.json 的 mixins 列表是空的，所以 legacy 变体需要补出空 refmap，
// 否则 reobfJar 会因找不到输入而失败。
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
