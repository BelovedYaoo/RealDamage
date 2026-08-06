# 协作记录

- 分支：`fix-gradle-9-5-1`
- Issue：#3
- 结论：项目配置一律不改。本地 Gradle 9.5.1 与 ForgeGradle 6.x（要求 ≥8.1 且 <9.0）不兼容，按用户要求在 E:\Library 安装 Gradle 8.1.1。
- 已验证：`E:/Library/gradle-8.1.1/bin/gradle.bat processResources --no-daemon`（JDK 17）构建成功，mods.toml 正确渲染 license=0BSD。
- 注意：wrapper 仍是 7.5.1，与 settings.gradle 的 foojay 0.7.0（要求 ≥7.6）矛盾，gradlew 不可用；构建请直接用本地 Gradle 8.1.1。
