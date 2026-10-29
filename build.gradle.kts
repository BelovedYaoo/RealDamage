plugins {
    id("dev.isxander.modstitch.base") version "0.5.12"
    // Modstitch 0.5.12 传递进来的 Loom 是 1.10.x，而 Fabric API 0.138+（包括 1.21.11 那一支）
    // 是用 Loom 1.13.3 构建的，旧 Loom 会在配置阶段直接拒绝加载：
    // "Mod was built with a newer version of Loom"。插件 classpath 上同名插件按版本冲突
    // 取高版本，所以在这里显式声明一份新 Loom、apply false（只上 classpath，不自作主张应用）
    // 就能抬高 Modstitch 实际用到的那个版本，同时不动其它变体的平台选择。
    id("fabric-loom") version "1.13.3" apply false
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

// 每个 MC 版本要求的最低运行时 Java，也是产物字节码版本：
// 26.2 的 version.json 写的是 java_version = 25（java-runtime-epsilon），
// 所以编译与运行都必须用 JDK 25（Fabric API 26.2 的清单里同样写着 java: ">=25"）。
// 1.21 起运行时最低就是 Java 21；构建 JVM 本身由 Modstitch 要求为 21+，
// 而 26.2 要求 target 25，因此构建 JVM 必须是 25（或已注册的 25 toolchain）。
val javaTargetVersion = when (minecraft) {
    "1.19.2" -> 17
    "1.20.1" -> 17
    "1.21.1" -> 21
    "1.21.11" -> 21
    "26.2" -> 25
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的 Java 版本")
}

// 每个 MC 版本的资源包/数据包格式号，写错会让客户端提示资源包不兼容。
// 1.21.9（快照 25w31a）起格式号带次版本号，字段也从单一 pack_format 换成 min_format/max_format
//（1.21.11 的取值来自客户端 jar 里的 version.json：资源包 75.0、数据包 94.1；
//  26.2 同样是客户端 jar 里的 version.json：资源包 88.0、数据包 107.1）。
data class PackFormat(val major: Int, val minor: Int)

val resourcePackFormat = when (minecraft) {
    "1.19.2" -> PackFormat(9, 0)
    "1.20.1" -> PackFormat(15, 0)
    "1.21.1" -> PackFormat(34, 0)
    "1.21.11" -> PackFormat(75, 0)
    "26.2" -> PackFormat(88, 0)
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的资源包格式版本")
}

val dataPackFormat = when (minecraft) {
    "1.19.2" -> PackFormat(9, 0)
    "1.20.1" -> PackFormat(15, 0)
    "1.21.1" -> PackFormat(34, 0)
    "1.21.11" -> PackFormat(94, 1)
    "26.2" -> PackFormat(107, 1)
    else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的数据包格式版本")
}

// 1.21.9 之前的写法是单一 pack_format（两种包同号）；之后模组 jar 里这份 pack.mcmeta 会被
// 资源包与数据包各解析一次，而两者的兼容校验互斥：数据包格式号仍 <= 1.21.8 的旧号时必须额外
// 声明 supported_formats，资源包一侧则明令禁止 supported_formats。也就是说单一文件无法同时
// 声明两种包，只能选一种：模组 jar 里真正要加载的内容（测试结构体等）都在数据包侧，
// 资源包侧本来就没有 assets，因此按数据包格式号声明——这与 NeoForge 21.11 自己的
// pack.mcmeta（min_format/max_format 直接写 [94, 1]）是同一做法。
val usesModernPackFormat = minecraft == "1.21.11" || minecraft.startsWith("26.")
val packFormatFields = if (usesModernPackFormat) {
    "\"min_format\": [${dataPackFormat.major}, ${dataPackFormat.minor}], " +
            "\"max_format\": [${dataPackFormat.major}, ${dataPackFormat.minor}]"
} else {
    "\"pack_format\": ${resourcePackFormat.major}"
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

    "1.21.11" to "neoforge" -> LoaderManifest(
        modId = "neoforge",
        versionRange = "[21.11,)",
        languageVersion = "[4,)",
        minecraftVersionRange = "[1.21.11,1.22)",
    )

    // 26.x 的 NeoForge 版本号直接跟 MC 对齐（26.2.0.93），MC 版本区间改按年份序号写。
    // javafml 的版本门槛按 NeoForge 26.2 自己清单里的写法取 [3,)：
    // FML 的语言加载器版本号在各代之间并不单调（21.11 自述 [3,)，这里的模组侧 1.21.11 用的却是 [4,)）
    "26.2" to "neoforge" -> LoaderManifest(
        modId = "neoforge",
        versionRange = "[26.2.0,)",
        languageVersion = "[3,)",
        minecraftVersionRange = "[26.2,26.3)",
    )

    // Fabric 的清单是 fabric.mod.json，用的是 Fabric 自己的依赖谓词语法：
    // loader 版本写成 >=x.y.z，MC 版本用 ~（同一次版本）——不能用 Forge 那边的 Maven 区间 [...]
    "1.19.2" to "fabric",
    "1.20.1" to "fabric",
    "1.21.1" to "fabric",
    "1.21.11" to "fabric",
    "26.2" to "fabric",
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

        // Modstitch 未内建的占位符需要自行登记；populate 是 MapProperty 的局部扩展。
        // 类型参数显式约束到 Any：Gradle 9 给 MapProperty 的键值型参加了非空下界，
        // 不写的话在 9.x 上编译期就报 "Type argument is not within its bounds"
        fun <K : Any, V : Any> MapProperty<K, V>.populate(block: MapProperty<K, V>.() -> Unit) {
            block()
        }

        replacementProperties.populate {
            put("mod_license", "0BSD")
            put("mod_description", "Do you like this?")
            put("pack_format", resourcePackFormat.major.toString())
            put("pack_format_fields", packFormatFields)
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
            // 这里刻意不配置 loom.mixin：Loom 1.13 起默认不再跑 Mixin 注解处理器，
            // 而是直接在 remapJar 阶段把 @ModifyVariable(method = "...") 这类注解值改写成
            // intermediary 名 + 描述符（产物里实际是 method_6074(Lnet/minecraft/class_3218;...)），
            // 因此既不需要 refmap，也不该再声明它（否则运行时会警告 refmap 读不到）。
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
            // GameTest 数值验证的入口。defaultRuns() 只给 client / server，而 gameTestServer
            // 是 userdev 配置里另有一份的 run 类型：MDG 会从它拿到主类、--launchTarget 以及
            // forge.gameTestServer / neoforge.gameTestServer 这类系统属性。类型名不能自定义，
            // 必须与 userdev 里的键同名，否则 MDG 在准备运行时找不到该 run 类型会直接报错
            runs.register("gameTestServer") {
                type.set("gameTestServer")
            }
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

// 26.x 起 Mojang 发布的 MC 不再混淆（26.2 的 version.json 里已经没有 client_mappings /
// server_mappings，jar 里的类名就是 net.minecraft.* 的真名），Loom 从 1.16 起为这种版本
// 准备了一条独立插件 id（net.fabricmc.fabric-loom，即 LoomNoRemapGradlePlugin），它的作用
// 只是在构造 LoomGradleExtension 时把内部的 disableObfuscation 标志置为 true。
// 该标志一旦为真，Loom 的 CompileConfiguration 就整体跳过 mappings 解析（反编译
// CompileConfiguration#setupMinecraft 第 67 行起的 disableObfuscation 分支核实）。
//
// 问题在于 Modstitch 0.5.x 只会应用老的那个 fabric-loom（remap）插件，并且无条件把
// officialMojangMappings() 塞进 mappings 配置；对 26.x 来说这条映射根本不存在（版本清单里
// 没有 client_mappings），Loom 会直接失败：Failed to find official mojang mappings for 26.2。
// 而 net.fabricmc.fabric-loom 必须早于 fabric-loom 应用，Modstitch 已经抢先应用了后者，
// 因此这里按 Loom 自己的判定依据把同一个标志补上——效果与「用 net.fabricmc.fabric-loom 插件」
// 完全一致，只是绕开了插件应用顺序的限制。标志本身没有公开 setter，只能取内部属性。
if (modstitch.isLoom && minecraft.startsWith("26.")) {
    val loomExtension = extensions.getByName("loom")
    // Gradle 会给扩展生成一个 *_Decorated 子类，字段实际声明在父类上，这里沿继承链找
    var extensionClass: Class<*>? = loomExtension.javaClass
    var field: java.lang.reflect.Field? = null
    while (extensionClass != null && field == null) {
        field = extensionClass.declaredFields.firstOrNull { it.name == "disableObfuscation" }
        extensionClass = extensionClass.superclass
    }
    val disableObfuscationField = requireNotNull(field) {
        "Loom 的 disableObfuscation 属性找不到了（Loom 升级了？），26.2 变体需要它来跳过映射解析"
    }.apply { isAccessible = true }
    // 这个 Property 在 Loom 构造扩展时就已经 finalizeValue 了，set 会报
    // 「The value for this property is final and cannot be changed any further」，
    // 只能把整个属性对象换成一个已经定好值的新对象（Loom 每次都是读该字段，不做缓存）
    disableObfuscationField.set(loomExtension, objects.property(Boolean::class.java).value(true))
}

// 非混淆环境下 Loom 的 remapJar 连属性都读不了（knownIndyBsms 会抛
// "Cannot configure known indyBsms in a non-obfuscated environment"），而 26.x 本来也不需要
// 重映射：类名就是 Mojang 名，mixin 注解值也无需改写。这正是官方 no-remap 插件
// （net.fabricmc.fabric-loom）的效果——它的产物就是普通 jar。所以这里直接把 remapJar 关掉，
// 并让 assemble 依赖普通 jar，保证 build/libs 里仍然是那份带 jar-in-jar 的完整产物。
if (modstitch.isLoom && minecraft.startsWith("26.")) {
    tasks.named("remapJar") { enabled = false }
    // Loom 把普通 jar 放在 build/devlibs（带 -dev 分类），build/libs 里那份原本由 remapJar 产出。
    // 非混淆环境不需要重映射，但 jar-in-jar 仍然要打进去，所以这里补一个只做打包的 Jar 任务，
    // 产物路径与命名跟其他变体保持一致（build/libs/realdamage-<version>.jar）
    val plainJar = tasks.named<Jar>("jar")
    val noRemapJar = tasks.register<Jar>("noRemapJar") {
        dependsOn(plainJar, "processIncludeJars")
        from(plainJar.map { zipTree(it.archiveFile.get().asFile) })
        // processIncludeJars 是 Loom 把 include 的依赖摊开的地方（big-math、nightconfig）
        from(tasks.named("processIncludeJars")) { into("META-INF/jars") }
        archiveFileName.set(plainJar.map { "${it.archiveBaseName.get()}-${it.archiveVersion.get()}.jar" })
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }
    tasks.named("assemble") { dependsOn(noRemapJar) }
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
        // 26.x 起 MC 不再混淆，Loom 走 no-remap 分支，modImplementation 这些 remap 配置不再
        // 汇入 compileClasspath（官方 26.2 模板也是直接用 implementation 声明 fabric-loader 与
        // fabric-api 的）。Modstitch 固定往 modImplementation 上加 fabric-loader，所以 26.x 这里
        // 必须再补一份普通配置，否则 loader / api / mixin 的类全都不在编译类路径上。
        if (minecraft.startsWith("26.")) {
            implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
        }
        val fabricDependencyConfiguration =
            if (minecraft.startsWith("26.")) "implementation" else "modImplementation"
        // Fabric 的命令注册靠 Fabric API
        add(fabricDependencyConfiguration, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        // 26.x 起 Fabric API 的 bundle 里不再包含 gametest 模块（0.149 ~ 0.161 的所有 26.2 构建里
        // 都没有 fabric-gametest-api-v1），而 Fabric 侧的 GameTest 就靠它提供 @GameTest 与
        // fabric-api.gametest 开关，所以 26.2 变体单独声明一份（版本按该模块 manifest 里的
        // Fabric-Minecraft-Version = 26.2 挑最新的一版）。旧变体仍然由 bundle 自带，不需要这一项。
        prop("deps.fabricGametest") {
            add(fabricDependencyConfiguration, "net.fabricmc.fabric-api:fabric-gametest-api-v1:$it")
        }
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
    // Modstitch 的 applyMixinConfigToModMetadata 每次执行都会往 build/resources/main 里的清单里
    // 再追加一遍同一份 mixin 配置；而 processResources 一旦 UP-TO-DATE 就不会重新生成该文件，
    // 于是从第二次开发运行起 fabric.mod.json 里就会出现重复的 mixin 配置，Fabric 直接拒绝启动：
    // Non-unique Mixin config name realdamage-fabric.mixins.json used by the mods realdamage and realdamage。
    // 正式产物的清单来自 generateModMetadata，不受影响；这里只让开发期的资源始终重做一遍。
    outputs.upToDateWhen { false }
}

// GameTest 只在开发期由 gameTestServer 运行，代码本身却被 Stonecutter 生成进了 main 源集
// （GameTest 类要引用三个加载器各自的注解，又没法塞进独立源集而不失去 Stonecutter 的预处理）。
tasks.withType<Jar>().configureEach {
    // 空结构体是 GameTest 专用数据：1.20.1 读 data/<ns>/structures/，1.21.1 读 data/<ns>/structure/，
    // 运行时靠 MOD_CLASSES 指向 build/resources，排掉 jar 不影响 gametest 本身
    exclude("data/realdamage/**")
    // Forge / NeoForge 的测试类靠扫描 @GameTestHolder 注解发现，没有任何代码引用它们，可以放心排掉。
    // Fabric 排不掉：fabric-gametest-api-v1 要求测试类以 fabric.mod.json 的 fabric-gametest
    // 入口点登记（TestFunctionsMixin 会按声明类反查 mod id，没登记就直接抛异常），
    // 而入口点里的类必须真实存在，否则正式环境一启动就会找不到类。
    if (!modstitch.isLoom) {
        exclude("top/bemylover/realdamage/gametest/**")
    }
}

// 开发期跑起来时补回 big-math（仅 MDG 平台）。
//
// 为什么需要这一步：big-math 同时出现在 implementation 与 modstitchJiJ 里（正式产物靠 jar-in-jar
// 带上它），而 MDG 会把 jarJar 里的坐标从 run classpath 里扣掉——那个设计的前提是「运行时装的是
// jar，jar-in-jar 会展开」。开发期装的是 MOD_CLASSES 指向的源集目录，没有 jar-in-jar 元数据，
// 于是 mod 构造期就 NoClassDefFoundError: ch/obermuhlner/math/big/BigDecimalMath。
// 光往 JVM classpath 上加也不够：Forge/NeoForge 的开发启动走 ModLauncher 的模块系统，依赖必须落到
// <run>AdditionalRuntimeClasspath（它会汇入 legacy classpath 文件）里才对游戏可见。
// Loom 侧不需要：include 不会把 implementation 的依赖从 run classpath 里挖掉。
//
// 1.21.11 的 NeoForge 声明了 noLegacyClasspath 特性，MDG 从那一代起不再生成
// <run>AdditionalRuntimeClasspath（往上面加依赖会直接报错），开发运行的 classpath 就是普通配置，
// 所以这一档改走 runtimeOnly：效果同样是「运行时能看见，而产物靠 jar-in-jar 带」。
// 26.2 沿用同一套特性（仍然后 noLegacyClasspath），所以一并走 runtimeOnly。
val usesLegacyRunClasspath = minecraft != "1.21.11" && minecraft != "26.2"
if (!modstitch.isLoom) {
    val devRunJarJarDependencies = listOf("ch.obermuhlner:big-math:2.3.2")
            .map { dependencies.create(it) }
    if (usesLegacyRunClasspath) {
        configurations.matching { it.name.endsWith("AdditionalRuntimeClasspath") }.configureEach {
            devRunJarJarDependencies.forEach { dependency -> dependencies.add(dependency) }
        }
    } else {
        devRunJarJarDependencies.forEach { dependencies.add("runtimeOnly", it) }
    }
}

// 开发期可选的两个开关，都靠 Gradle 属性显式打开，避免日常运行时多出参数与日志：
// -Prealdamage.mixinVerbose：Mixin 默认只在 DEBUG 级别记录「Mixing ... into ...」，
//   客户端验证要取证就必须显式打开
// -Prealdamage.fabricGametest：Fabric 侧没有独立的 gametest run 配置，
//   fabric-gametest-api-v1 是靠这个系统属性把自己的 headless GameTestServer 接管 runServer 的
val devRunJvmArgs = buildList {
    if (providers.gradleProperty("realdamage.mixinVerbose").isPresent) {
        add("-Dmixin.debug.verbose=true")
    }
    // 验证 mixin 注入点时，光有 verbose 日志不够：要拿到「注入后的类」做字节码级确认，
    // 必须让 Mixin 把改写后的类导出到 run/.mixin.out/class/ 下
    if (providers.gradleProperty("realdamage.mixinExport").isPresent) {
        add("-Dmixin.debug.export=true")
    }
    if (providers.gradleProperty("realdamage.fabricGametest").isPresent && modstitch.isLoom) {
        add("-Dfabric-api.gametest=1")
        add("-Dfabric-api.gametest.report-file=${layout.buildDirectory.get().asFile.resolve("gametest-report.xml")}")
    }
}
if (devRunJvmArgs.isNotEmpty()) {
    tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
        // Loom 的 runClient/runServer 与 MDG 的 RunGameTask 都是 JavaExec
        (this as JavaExec).jvmArgs(devRunJvmArgs)
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
