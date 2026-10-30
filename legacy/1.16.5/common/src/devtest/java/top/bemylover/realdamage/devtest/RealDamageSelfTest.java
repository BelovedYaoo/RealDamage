package top.bemylover.realdamage.devtest;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 开发期数值自检：在真实服务端进程里跑完整的伤害链路。
 *
 * <h2>为什么不是 GameTest</h2>
 * 1.16.5 这一代的测试设施两头都不齐：
 * <ul>
 *   <li>Forge 1.16.5-36.2.42 的产物里<b>没有</b> {@code net.minecraftforge.gametest.GameTestHolder}
 *       （userdev 的 patches 里没有任何 gametest 补丁，universal jar 里也搜不到该类），
 *       userdev 的 runs 里也只有 client/server/data 三种，没有 gameTestServer；</li>
 *   <li>Fabric 的 {@code fabric-gametest-api-v1} 最早一版（1.0.0）就已经是 MC 1.17+ 的形状：
 *       mixin 的 compatibilityLevel 是 JAVA_16，refmap 指向 {@code net/minecraft/test/TestFunction}
 *       ——那是 1.17 才把 gametest 框架从 {@code net.minecraft.gametest.framework} 改名过去的包；
 *       1.16.5 的 Fabric API（0.42.0+1.16）里也没有这个模块。</li>
 * </ul>
 *
 * <h2>所以这里怎么做</h2>
 * <p>把断言写成「服务端启动后同步跑一遍」的形式：真实 {@code ServerLevel} + 真实
 * {@code Player} 实例 + 真实 {@code hurt} 调用，因此 Forge 侧走的就是
 * {@code LivingHurtEvent}，Fabric 侧走的就是 mixin，两边被测的入口与生产路径完全一致。
 * 断言结果以 {@code [SELFTEST]} 前缀写进服务端日志，跑完自动停服，便于无头执行与日志取证。</p>
 *
 * <p>本类只用于开发期验证，两个加载器的构建都会把它排除在产物之外。</p>
 *
 * @author PrefersMin
 */
public final class RealDamageSelfTest {

    private static final Logger LOGGER = LogManager.getLogger("RealDamage-SelfTest");

    /**
     * 每次伤害的原始数值。10 点既能让护甲减免比例明显，又不会一击致死。
     */
    private static final float DAMAGE = 10.0F;

    /** 全套钻石甲的护甲值。 */
    private static final int DIAMOND_ARMOR_POINTS = 20;

    /**
     * 全套钻石甲、10 点伤害、原版结算时应当下降的血量
     * （CombatRules 的公式：10 * (1 - clamp(20 - 10/4, 4, 20)/25) = 3.0）。
     */
    private static final float VANILLA_ARMORED_DROP = 3.0F;

    /**
     * 保护期天数取配置允许的最大值 100：「保护期内」用第 5 天，「保护期外」用第 200 天，
     * 两者都远离边界，不受天数判定本身的影响。
     */
    private static final int PROTECTION_DAYS = 100;

    private static int failures;

    private RealDamageSelfTest() {
    }

    /**
     * 跑完全部断言，并在结束时停服（否则无头运行会一直挂在那里）。
     *
     * @param server  已启动完成的服务端
     * @param subject 加载器提供的被测玩家
     */
    public static void run(MinecraftServer server, TestSubject subject) {
        LOGGER.info("[SELFTEST] 开始：真实伤害数值自检（{}）", subject.getClass().getName());
        failures = 0;
        try {
            realDamageBypassesArmor(subject);
            vanillaArmorReducesDamage(subject);
            invulnerablePlayerTakesNoRealDamage(subject);
            creativeOrSpectatorTakesNoRealDamage(subject);
            protectionPeriodSkipsRealDamage(subject);
            afterProtectionRealDamageApplies(subject);
        } catch (RuntimeException e) {
            failures++;
            LOGGER.error("[SELFTEST] 断言过程抛出异常，按失败处理", e);
        }
        if (failures == 0) {
            LOGGER.info("[SELFTEST] RESULT PASSED（0 处失败）");
        } else {
            LOGGER.error("[SELFTEST] RESULT FAILED（{} 处失败）", failures);
        }
        // 停服：无头运行必须能自己退出，否则外层脚本只能靠超时杀进程
        LOGGER.info("[SELFTEST] 自检结束，正在停止服务端");
        server.halt(false);
    }

    /**
     * 断言 1：真实伤害绕过护甲。
     *
     * <p>固定比例 100% ⇒ 真实伤害 = 原始伤害、交回原版结算的剩余伤害为 0，
     * 于是「满护甲下血量下降 10」就直接证明这一击没有被护甲打折。</p>
     */
    private static void realDamageBypassesArmor(TestSubject subject) {
        subject.prepare(5, false, PROTECTION_DAYS, 100, false, true);
        checkArmor(subject);
        assertDrop("bypass_armor", "真实伤害应绕过护甲", DAMAGE, subject.hurtAndMeasureDrop());
    }

    /**
     * 对照组：同一套护甲、同样的伤害，但真实伤害比例设为 0，这一击完全按原版结算。
     *
     * <p>没有这个基线，「真实伤害绕过护甲」就只是一句无法证伪的话——
     * 两组数字（3.0 与 10.0）的差异才是护甲被绕过的证据。</p>
     */
    private static void vanillaArmorReducesDamage(TestSubject subject) {
        subject.prepare(5, false, PROTECTION_DAYS, 0, false, true);
        checkArmor(subject);
        assertDrop("vanilla_armor_baseline", "真实伤害比例 0% 时应完全按原版结算（护甲生效）",
                VANILLA_ARMORED_DROP, subject.hurtAndMeasureDrop());
    }

    /**
     * 断言 2：无敌玩家不被扣真实伤害。
     *
     * <p>分两层验证：一层走完整 {@code hurt} 流程，一层直接调结算函数。</p>
     */
    private static void invulnerablePlayerTakesNoRealDamage(TestSubject subject) {
        // 真实伤害拉满并关掉保护期：如果无敌判定缺失，这里必然掉 10 点血
        subject.prepare(5, false, PROTECTION_DAYS, 100, true, false);
        check(subject.isInvulnerableToTestDamage(), "前置条件：无敌玩家应当对 player_attack 免疫");
        assertDrop("invulnerable_entity", "无敌玩家走完整 hurt 流程不应掉血", 0.0F, subject.hurtAndMeasureDrop());

        float healthBefore = subject.health();
        float returned = subject.directSplitAndMeasureReturned();
        check(Math.abs(returned - DAMAGE) < 0.01F,
                "无敌玩家应原样返回原始伤害（继续交给原版），实测返回 " + returned);
        check(Math.abs(subject.health() - healthBefore) < 0.01F,
                "无敌玩家不应被直接扣真实伤害，实测血量 " + subject.health() + "（期望 " + healthBefore + "）");
        LOGGER.info("[SELFTEST] 无敌判定：返回原始伤害 {}，血量 {} -> {}", returned, healthBefore, subject.health());
    }

    /**
     * 断言 4：创造/旁观模式玩家不被扣真实伤害。
     *
     * <p>这是历史上真正抓出过缺陷的地方：{@code main} 分支曾经把注入点放在无敌判定之前，
     * 于是创造/旁观模式玩家与伤害类型免疫的玩家被扣了真实伤害。1.16.5 的注入点在
     * {@code Player#actuallyHurt} 头部——它在 {@code Player#hurt} 里那道
     * {@code abilities.invulnerable} 闸的下游，所以这类玩家根本走不到注入点。</p>
     *
     * <p>前置条件是「该玩家不因伤害类型免疫」（{@code isInvulnerableTo} 为 false）。
     * 这很关键：{@code abilities.invulnerable} 与原版的那个 {@code invulnerable} 字段是两回事，
     * 前者挡在 {@code hurt} 上，后者被 {@code isInvulnerableTo} 读取。只有当前置条件成立时，
     * 「掉血 0.0」才能唯一地证明拦截来自 {@code hurt} 那道闸，而不是结算核心自己又判了一次免疫。</p>
     */
    private static void creativeOrSpectatorTakesNoRealDamage(TestSubject subject) {
        // 真实伤害拉满（100%）并关掉保护期：如果注入点在这道闸之前，这里必然掉 10 点血
        subject.prepareInvulnerableAbilities(200, 100);
        checkArmor(subject);
        check(!subject.isInvulnerableToTestDamage(),
                "前置条件：创造/旁观模式玩家不应被 isInvulnerableTo 判定为免疫（否则无法区分是哪道闸挡住的）");
        assertDrop("creative_abilities", "创造/旁观模式玩家不应被扣真实伤害", 0.0F, subject.hurtAndMeasureDrop());

        // 记录「若绕过原版那道闸会怎样」：直接调结算核心，它会照常扣真实伤害。
        // 这不是断言，只是把保护的实际来源（原版 hurt 的早退，而非结算核心）写进日志，
        // 免得以后有人看到「创造模式不掉血」就以为结算核心里有创造模式判定。
        float healthBefore = subject.health();
        float returned = subject.directSplitAndMeasureReturned();
        LOGGER.info("[SELFTEST] 创造/旁观模式若直接调结算核心：返回剩余伤害 {}，血量 {} -> {}",
                returned, healthBefore, subject.health());
    }

    /**
     * 断言 5：保护期内不扣真实伤害。
     *
     * <p>保护期开启、上限 100 天、世界第 5 天 ⇒ 真实伤害被跳过，
     * 这一击回到原版护甲结算，血量下降应当回落到对照组的 3.0。</p>
     */
    private static void protectionPeriodSkipsRealDamage(TestSubject subject) {
        subject.prepare(5, true, PROTECTION_DAYS, 100, false, true);
        checkArmor(subject);
        assertDrop("protection_period", "保护期内真实伤害应为 0（回到原版护甲结算）",
                VANILLA_ARMORED_DROP, subject.hurtAndMeasureDrop());
    }

    /**
     * 断言 6：保护期外扣真实伤害。
     *
     * <p>与上一条唯一的差别是世界天数（200 &gt; protectionDays），
     * 血量下降从 3.0 变成 10.0，证明保护期这个开关确实在控制真实伤害。</p>
     */
    private static void afterProtectionRealDamageApplies(TestSubject subject) {
        subject.prepare(200, true, PROTECTION_DAYS, 100, false, true);
        checkArmor(subject);
        assertDrop("after_protection", "保护期外真实伤害应生效（满护甲也照扣 10 点）", DAMAGE, subject.hurtAndMeasureDrop());
    }

    private static void checkArmor(TestSubject subject) {
        check(subject.armorValue() == DIAMOND_ARMOR_POINTS,
                "钻石全套护甲值应为 " + DIAMOND_ARMOR_POINTS + "，实测 " + subject.armorValue());
    }

    /**
     * 记录并判定一条「血量下降」断言。
     *
     * <p>每次断言都额外输出一行 {@code [SELFTEST-CHECK] key=... expected=... actual=... ok=...}：
     * 中文日志给人看，这一行给脚本看。冒烟脚本要在两种 stdout 编码（Forge 侧 GBK、Fabric 侧 UTF-8）
     * 之间取数，只靠中文关键词非常容易假阴性，所以断言值本身走纯 ASCII 的 key/value。</p>
     */
    private static void assertDrop(String key, String label, float expected, float actual) {
        LOGGER.info("[SELFTEST] {}：期望血量下降 {}，实测 {}", label, expected, actual);
        // 伤害全程是 float 运算，达不到精确相等，这里按 0.01 判定
        boolean passed = Math.abs(expected - actual) < 0.01F;
        LOGGER.info("[SELFTEST-CHECK] key={} expected={} actual={} ok={}", key, expected, actual, passed);
        check(passed, label + "：期望血量下降 " + expected + "，实测 " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            LOGGER.error("[SELFTEST] 断言失败：{}", message);
        }
    }
}
