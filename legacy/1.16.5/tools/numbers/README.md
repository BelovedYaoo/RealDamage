# legacy/1.16.5 的数值验证（开发期自检）

这一层回答的是**数值**问题：一次 10 点的伤害打在满套钻石甲的玩家身上，血到底掉多少。
它和 `tools/smoke` 分工不同——那边验产物能不能起来、依赖从哪来、命令有没有注册；
这边验 `RealDamageCalculator` 在两个加载器上的实际效果。

## 怎么跑

```bash
bash legacy/1.16.5/tools/numbers/forge.sh     # ForgeGradle 4.1 + Gradle 6.9.4 + JDK 8
bash legacy/1.16.5/tools/numbers/fabric.sh    # Loom 0.11 + Gradle 7.5.1 + JDK 17
```

脚本做三件事：nohup 起开发期 `runServer`（有上限地轮询，绝不在前台跑）→ 等自检自己跑完并停服 →
从日志里取 `[SELFTEST-CHECK]` 行核对六个数值。断言本身写在
`common/src/devtest/.../RealDamageSelfTest.java`，两个加载器共用同一份期望值；加载器差异只体现在
`TestSubject` 的实现里。

自检类被两个加载器的构建都排除在产物之外（`jar { exclude '**/devtest/**' }`），
所以这些数值只能由开发期 `runServer` 取得；产物 jar 里没有自检，跑产物只能验"起得来"。

## 实测结果（本分支 `legacy/1.16.5`，2026-10-30 实测）

| 断言 | 条件 | Forge 1.16.5 | Fabric 1.16.5 |
|---|---|---|---|
| `bypass_armor`      | 原始伤害 10、全套钻石甲、`damageConstantProportion=100`、第 5 天 | **10.0** | **10.0** |
| `vanilla_armor_baseline` | 同上但比例 `0`（完全按原版） | **3.0** | **3.0** |
| `invulnerable_entity` | 比例 100、`Entity#invulnerable=true`（伤害类型免疫） | **0.0** | **0.0** |
| `creative_abilities` | 比例 100、`abilities.invulnerable=true`（创造/旁观那道闸） | **0.0** | **0.0** |
| `protection_period` | 保护期开启、上限 100 天、世界第 5 天 | 3.0 | 3.0 |
| `after_protection`  | 保护期开启、世界第 200 天 | 10.0 | 10.0 |

前两条的 7 点差距（10.0 对 3.0）就是"护甲被绕过"的量：原版结算下 20 点护甲的减免是
`10 * (1 - clamp(20 - 10/4, 4, 20)/25) = 3.0`，真实伤害绕过之后直接是 10.0。

## 一、为什么不用 GameTest（可行性结论）

结论：**两侧都没有可用的 gametest 设施**，所以数值断言只能写成"服务端启动后同步跑一遍"。
这不是取舍，是这一代的工具链事实，逐条取证如下。

### Forge 1.16.5：没有 `net.minecraftforge.gametest`

- `forge-1.16.5-36.2.42-universal.jar` 里 `grep -c gametest` = **0**：没有
  `net/minecraftforge/gametest/GameTestHolder`、`GameTestHooks` 之类的注解与钩子。
- `forge-1.16.5-36.2.42-sources.jar`（Forge 自己的补丁源码）里提到 gametest 的地方只有 1 处，
  且是客户端网络补丁文件里的无关字符串；没有任何 gametest 补丁。
- `ForgeGradle-4.1.16.jar` 里 `gametest` 出现 **0** 次：这一代 ForgeGradle 没有
  `runGameTestServer` 这类 run，`minecraft.runs` 只有 client/server/data 三种形态。
- 原版 1.16.5 **有** `net.minecraft.gametest.framework`（`GameTestRegistry`、`TestFunction`、
  `GameTestHelper`、`/test` 命令…，named jar 里 `net/minecraft/gametest/` 下共 29 个条目），
  但那是"结构方块 + `/test run`"那套，Forge 侧没有把它接进启动流程，也没有注解扫描入口；
  要靠它还得先准备结构 NBT、把断言写进 `GameTestHelper` 的上下文里，
  与"施加一次 10 点伤害、读血量"完全不搭。

### 假玩家（`FakePlayer`）能不能用来做数值断言

不能，至少不能用来做"绕过护甲"那一条。`forge-1.16.5-36.2.42` 的
`net/minecraftforge/common/util/FakePlayer` 里直接写死：

```java
@Override public boolean func_180431_b(DamageSource source){ return true; }   // isInvulnerableTo
```

而 `RealDamageCalculator.splitAndApplyRealDamage` 的第一步就是 `isInvulnerableTo`，
用 `FakePlayer` 只能观测到"什么都没发生"。所以自检里用的是
`new PlayerEntity(...)/new Player(...)` 的匿名子类（1.16.5 的 Player 是抽象类，只差
`isSpectator`/`isCreative` 两个判定）：伤害链路与真实玩家一致，只是不在世界里、不收同步包。

### Fabric 1.16.5：`fabric-gametest-api-v1` 在 1.16.5 不存在

- `maven.fabricmc.net` 上 `fabric-gametest-api-v1` 的第一版是 `1.0.0+e409d7e818`。
  下载该 jar 后看 `fabric-gametest-api-v1.mixins.json`：`compatibilityLevel` 是 **JAVA_16**
  （1.16.5 的运行时是 Java 8，直接不兼容）；refmap 里的命名目标是 `net/minecraft/test/TestFunction`、
  `net/minecraft/test/TestServer` —— 而 1.16.5 的 named jar 里 `net/minecraft/test/` 包有
  **0 个条目**（这一代的同类类在 `net/minecraft/gametest/framework/` 下）。
- 1.16 支线的 Fabric API 到 `0.42.0+1.16` 为止（maven 元数据里 `+1.16` 的最后一个版本），
  本工程用的就是它；它的模块列表里没有 `fabric-gametest-api-v1`
  （Loom 的 remapped 模块缓存目录里也找不到这个模块）。

## 二、Fabric 注入点的字节码级确认

### 1. 目标方法：`Player#actuallyHurt`（1.16.5 确实有这个方法）

先纠正一个容易想当然的前提。**1.16.5 有 `Player#actuallyHurt`**，
`net/minecraft/world/entity/player/Player` 覆写了 `LivingEntity#actuallyHurt` 且不调 super。
用 `javap`（官方名 jar）取到的原文：

```
protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource, float);
   0: aload_0; 1: aload_1
   2: invokevirtual isInvulnerableTo:(LDamageSource;)Z
   5: ifeq 9
   8: return                                  // 无敌则整段跳过
   9: aload_0; 10: aload_1; 11: fload_2
  12: invokevirtual getDamageAfterArmorAbsorb:(LDamageSource;F)F    // 护甲减免
  19: invokevirtual getDamageAfterMagicAbsorb:(LDamageSource;F)F    // 附魔减伤
```

调用链（同样是字节码取证）：`Player#hurt` 末尾 `invokespecial LivingEntity.hurt`，
`LivingEntity#hurt` 里对 `actuallyHurt` 发的是 **invokevirtual**（偏移 249 / 277），
所以玩家会走到自己那份 `Player#actuallyHurt`——因此注入 `LivingEntity#actuallyHurt` 是抓不到玩家的。

### 2. 注入位置：`Player#actuallyHurt` 的 **HEAD**，改的是即将进护甲的那个 amount

跑 `MIXIN_EXPORT=1`（→ JVM 参数 `mixin.debug.export=true`）导出注入后的类，
落在 `fabric/run/.mixin.out/class/net/minecraft/world/entity/player/Player.class`，
反汇编（`javap -p -c`）的 `actuallyHurt` 开头是：

```
public void actuallyHurt(DamageSource, float);
   0: aload_0; 1: fload_2; 2: aload_1
   3: invokespecial localvar$zdk000$realdamage$splitRealDamage:(FLnet/minecraft/world/damagesource/DamageSource;)F   // ← 注入
   6: fstore_2                                                                                                    // ← 写回 amount（局部变量 2）
   7: aload_0; 8: aload_1
   9: invokevirtual isInvulnerableTo:(LDamageSource;)Z
  12: ifeq 16
  15: return
  16: aload_0; 17: aload_1; 18: fload_2
  19: invokevirtual getDamageAfterArmorAbsorb:(LDamageSource;F)F                                                   // 护甲在这之后
```

那个合成方法的实体（同一份反汇编里也能看到）：

```
private float localvar$zdk000$realdamage$splitRealDamage(float, net.minecraft.world.damagesource.DamageSource);
   0: new top/bemylover/realdamage/handler/FabricPlayerDamageContext
   6: invokespecial <init>:(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/damagesource/DamageSource;)V
  10: invokestatic top/bemylover/realdamage/handler/RealDamageCalculator.splitAndApplyRealDamage:(Ltop/bemylover/realdamage/handler/PlayerDamageContext;F)F
```

也就是说：**注入点在护甲结算之前**（真实伤害因此绕过护甲），
**且位于 `Player#actuallyHurt` 内部**（剩余伤害仍然原样返回、继续走护甲与附魔减免）。

### 3. 与"无敌判定"的相对位置——这里必须说清楚

1.16.5 上注入点在 `Player#actuallyHurt` 的**第一行**，因此它**早于** `actuallyHurt` 自己那道
`isInvulnerableTo` 判定。这正是 `main` 分支历史上抓出缺陷的那种形状，所以要看
`actuallyHurt` 上游还有什么闸：

```
Player#hurt(DamageSource, float)                     // 字节码 0..28
   0: invokevirtual isInvulnerableTo            → return false
  10: getfield abilities; getfield invulnerable
      invokevirtual DamageSource.isBypassInvul   → return false       ← 创造/旁观模式在这里就被挡住
 124: invokespecial LivingEntity.hurt(...)       // 才轮到 LivingEntity#hurt
```

而创造与旁观模式用的就是同一个标记（`GameType#updatePlayerAbilities`，反编译原文）：

```java
if (this == CREATIVE)       { abilities.mayfly = true; abilities.instabuild = true; abilities.invulnerable = true; }
else if (this == SPECTATOR){ abilities.mayfly = true; abilities.instabuild = false; abilities.invulnerable = true; abilities.flying = true; }
```

结论：**1.16.5 没有 `main` 分支那个问题**。创造/旁观模式玩家在 `Player#hurt` 里就早退了，
`actuallyHurt`（注入点所在方法）根本不会被调用；这条不是靠推理，自检里有一条实测断言
（`creative_abilities` = 0.0），并且日志还顺带记录了"若直接调结算核心会掉 10 点"
（`创造/旁观模式若直接调结算核心：返回剩余伤害 0.0，血量 20.0 -> 10.0`）——
说明拦住它的是原版 `hurt` 那道闸，不是结算核心自己判了创造模式。

注入点早于 `actuallyHurt` 内的 `isInvulnerableTo` 这件事仍然有影响：伤害类型免疫
（例如火焰伤害的 gamerule 关掉、`Entity#invulnerable`）的玩家会走到注入点。因此
`RealDamageCalculator.splitAndApplyRealDamage` 开头自己补了一次 `isInvulnerableTo` 判定并原样返回
原始伤害。实测覆盖：`invulnerable_entity` = 0.0，且直接调核心时返回原始伤害 10.0、血量不变。

### 4. 与 Forge `LivingHurtEvent` 的语义对齐

Forge 36.2.42 的 `PlayerEntity` / `LivingEntity` 补丁（`*-sources.jar` 里的 `.patch`）：

```java
protected void actuallyHurt(DamageSource src, float amount) {
   if (!this.isInvulnerableTo(src)) {
      amount = net.minecraftforge.common.ForgeHooks.onLivingHurt(this, src, amount);  // ← LivingHurtEvent
      if (amount <= 0) return;
      amount = this.getDamageAfterArmorAbsorb(src, amount);                          // 护甲
      amount = this.getDamageAfterMagicAbsorb(src, amount);                          // 附魔
```

| | Forge（`LivingHurtEvent`） | Fabric（`@ModifyVariable` at `actuallyHurt` HEAD） |
|---|---|---|
| 看到的 amount | 进护甲结算前的原始伤害 | 进护甲结算前的原始伤害 |
| 真实伤害 | 结算核心里 `setHealth` 直接扣 | 同左（同一份 `RealDamageCalculator`） |
| 剩余伤害 | `event.setAmount` 回给原版，继续走护甲/附魔 | 返回值写回局部变量 2，继续走护甲/附魔 |
| 无敌判定 | 事件在 `isInvulnerableTo` **之后**触发 | 注入点在其**之前**，由结算核心补一次同样的判定 |
| 创造/旁观 | `hurt` 里早退，走不到事件 | `hurt` 里早退，走不到注入点 |

差异只有一处且被显式补偿：Fabric 侧的注入点在 `actuallyHurt` 内的无敌判定之前，
所以"不绕过无敌判定"这件事由 `RealDamageCalculator` 开头那次 `isInvulnerableTo` 守住（有断言）。
其余语义（真实伤害绕过护甲、剩余伤害仍受护甲减免、无敌玩家不掉真实伤害）两边一致，
且是同一份代码算出来的。

### 5. 产物 jar 里注入的还是同一个方法

开发期跑的是 named 映射；产物 jar 里的 mixin 注解值会被 Loom 改写成 intermediary 名。
`fabric/build/libs/realdamage-1.16.5-fabric-1.0.0.jar` 里的 `realdamage-refmap.json`：

```
"actuallyHurt": "Lnet/minecraft/class_1657;method_6074(Lnet/minecraft/class_1282;F)V"
```

`class_1657` 是 `Player`、`class_1282` 是 `DamageSource`；在 merged-intermediary jar 里
`class_1657.method_6074(class_1282, float)` 的字节码与 named 版 `actuallyHurt` 完全同形
（先 `method_5679` = `isInvulnerableTo`，再 `method_6132` = `getDamageAfterArmorAbsorb`），
所以在产物里注入的也是这同一个方法、同一个位置。

## 三、没有覆盖到的部分（别把结论读过头）

1. **产物 jar 没有数值断言**：自检类被排除在产物之外，数值只在开发期 `runServer` 里取。
   产物 jar 这一侧验的是"注入点确实是 `actuallyHurt`"（refmap + intermediary 字节码），
   不是"产物跑起来的血量是 10.0"。要连产物一起验数值，得把自检打进产物或提供调试命令，
   本轮没有做。
2. **创造/旁观是用标记模拟的**：自检里直接设 `abilities.invulnerable = true`，
   而不是真的 `/gamemode creative`（那需要 `ServerPlayer` + 连接）。这条闸本身就是模式切换写的那一个标记，
   但"真实客户端的模式切换 + 网络同步"这一层没有覆盖。
3. **只测了 `player_attack` 一种伤害源**：`DamageSource.playerAttack`，`isBypassInvul` 为 false。
   `/kill`（`GENERIC_KILL`）这类绕过无敌的伤害源没有断言。
4. **开发期运行时与产物的类并不逐字节相同**：Loom 的开发期 jar 把 `protected` 成员放宽成 `public`
   （`isMovementNoisy`、`blockUsingShield` 等全都如此），这是开发环境的属性，与注入无关；
   但它意味着"开发期字节码"不能直接当作"产物字节码"的逐字节参照，只能按方法与位置对照。
5. Forge 侧 `runServer` 的 `gradlew` 退出码不可信：自检末尾 `server.halt(false)` 会让
   Gradle 6 的 daemon 连接断掉，构建末尾报 `Could not receive a message from the daemon`。
   脚本因此只认日志里的正向证据（`RESULT PASSED` 与每条 `ok=true`），不看退出码。
