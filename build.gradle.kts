plugins {
    id("dev.isxander.modstitch.base") version "0.5.12"
}

fun prop(name: String, consumer: (prop: String) -> Unit) {
    (findProperty(name) as? String?)?.let(consumer)
}

val minecraft = property("deps.minecraft") as String

modstitch {
    minecraftVersion = minecraft

    // 1.20.1 运行时是 Java 17；构建 JVM 本身由 Modstitch 要求为 21
    javaTarget = when (minecraft) {
        "1.20.1" -> 17
        else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的 Java 版本")
    }

    parchment {
        prop("deps.parchment") { mappingsVersion = it }
    }

    metadata {
        modId = "realdamage"
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
            put("pack_format", when (minecraft) {
                "1.20.1" -> 15
                else -> throw IllegalArgumentException("请在此登记 ${minecraft} 对应的资源包格式版本")
            }.toString())
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

// Modstitch 在 legacyforge 平台会无条件注册 <modId>.refmap.json 及其映射文件，
// 但 mixin 注解处理器在项目尚无 mixin 时不会产出这两个文件，导致 reobfJar 找不到输入。
// 当前仓库没有 mixin，故在缺失时补出空文件；日后加入 mixin 后由注解处理器正常产出。
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
listOf("jar", "jarJar", "reobfJar", "reobfJarJar").forEach { name ->
    tasks.matching { it.name == name }.configureEach {
        dependsOn(provideEmptyRefmap)
    }
}
