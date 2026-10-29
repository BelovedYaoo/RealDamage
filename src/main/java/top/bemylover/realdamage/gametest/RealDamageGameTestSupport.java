package top.bemylover.realdamage.gametest;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.RealDamageCalculator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GameTest 的共用测试体：三个加载器跑的是同一份数值断言。
 *
 * <p>把测试体放在这里、让各自的套件类只负责挂注解，是因为三个加载器的差别只有注解
 * （{@code @GameTestHolder} 的包名不同、Fabric 用 {@code FabricGameTest.EMPTY_STRUCTURE}）。
 * 数值断言如果各写一份，「三个加载器数值一致」这句话就无法靠测试本身证明。</p>
 *
 * <h2>为什么挨打的不是 FakePlayer</h2>
 * <p>Forge 与 NeoForge 的 {@code FakePlayer} 把 {@code isInvulnerableTo} 恒定返回 true，
 * 而真实伤害结算的第一步就是这个判定，于是 FakePlayer 永远走不到扣血分支，
 * 用它做断言只有「什么都没发生」这一种结果，护甲有没有被绕过根本观测不到。</p>
 *
 * <h2>为什么也不用 GameTestHelper#makeMockServerPlayerInLevel</h2>
 * <p>那个方法会把假玩家塞进 {@code PlayerList}，在 Forge 系上必然失败：Forge 在
 * {@code PlayerList#placeNewPlayer} 里调 {@code NetworkHooks#sendMCRegistryPackets}，
 * 它需要往连接的信道管线里插过滤器，而假玩家挂的 Connection 没有 channel，直接 NPE。
 * 因此这里用 {@link GameTestHelper#makeMockPlayer()}：一个真正的 {@code Player} 实例，
 * 伤害链路（hurt → actuallyHurt → 护甲减免 → 扣血）与真实玩家一致，只是不在世界里、
 * 不会收到同步包，于是可以完全同步地把一次伤害算完。</p>
 *
 * <p>断言用到的两组数值：全套钻石甲是护甲 20、韧性 8；对 10 点伤害，
 * 原版减免后只剩 {@code 10 * (1 - clamp(20 - 10/4, 4, 20)/25) = 3.0}。
 * 因此「掉 10」只可能来自绕甲的真实伤害，「掉 3」只可能是走完护甲结算的结果。</p>
 *
 * @author PrefersMin
 */
public final class RealDamageGameTestSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 每次伤害的原始数值。10 点既能让护甲减免比例明显，又不会一击致死，
     * 便于在断言里区分「扣了真实伤害」和「扣了护甲减免后的剩余伤害」。
     */
    private static final float DAMAGE = 10.0F;

    /** 全套钻石甲的护甲值。 */
    private static final int DIAMOND_ARMOR_POINTS = 20;

    /** 全套钻石甲的盔甲韧性。 */
    private static final double DIAMOND_ARMOR_TOUGHNESS = 8.0D;

    /**
     * 全套钻石甲、10 点伤害、原版结算时应当下降的血量（由 {@code CombatRules#getDamageAfterAbsorb} 推出）。
     * 这是对照组，真实伤害绕过护甲的那一组要和它拉开差距才有说服力。
     */
    private static final float VANILLA_ARMORED_DROP = 3.0F;

    /**
     * 保护期天数取配置允许的最大值 100：本组测试里「保护期内」用第 5 天，
     * 「保护期外」用第 200 天，两者都远离边界，不受天数判定本身的影响。
     */
    private static final int PROTECTION_DAYS = 100;

    /**
     * 测试前整份写入配置文件。
     *
     * <p>为什么写整份而不是只改一两项：配置层只提供「读一份 TOML」这一条路径
     * （{@link RealDamageConfig#reloadConfig()}），没有供测试直接改内存值的后门。
     * 写整份既避免了依赖上一次测试残留的配置，也顺带验证了
     * 「改配置文件 → configReload」这条线上路径确实能改到数值。</p>
     *
     * <p>函数计算关闭、固定比例由参数给定：真实伤害是否生效必须只由被测的那一项决定。</p>
     */
    private static final String CONFIG_TEMPLATE = """
            [RealDamage]
            isLimitMaximumRealDamage = %s
            isEnableProtection = %s
            realDamageCalculationMethodIsFunction = false
            isPlayerAbsorptionWithstandRealDamage = false

            [RealDamage.Maximum]
            realDamageMax = 30

            [RealDamage.Protection]
            protectionDays = %d

            [RealDamage.FunctionCalculation]
            damageProportionMax = 30
            damageProportionMin = 10
            calculationStartIncreasingFromFewDays = 10
            reachesMaximumDays = 100

            [RealDamage.ConstantCalculation]
            damageConstantProportion = %d

            [RealDamage.DebugMode]
            isEnableDebugMode = false
            """;

    private RealDamageGameTestSupport() {
    }

    // ---------------------------------------------------------------- 测试体

    /**
     * 断言 1：真实伤害绕过护甲。
     *
     * <p>固定比例 100% ⇒ 真实伤害 = 原始伤害、交回原版结算的剩余伤害为 0，
     * 于是「满护甲下血量下降 10」就直接证明这一击没有被护甲打折。</p>
     */
    public static void realDamageBypassesArmor(GameTestHelper helper) {
        Player player = spawnMockPlayer(helper);
        writeConfig(false, false, PROTECTION_DAYS, 100);
        setDay(helper.getLevel(), 5);
        equipDiamondArmor(player);
        assertDiamondArmor(player);
        float drop = hurtAndMeasureHealthDrop(player, attackDamage(helper.getLevel(), player));
        assertDrop("真实伤害应绕过护甲", DAMAGE, drop);
        helper.succeed();
    }

    /**
     * 对照组：同一套护甲、同样的伤害，但真实伤害比例设为 0，这一击完全按原版结算。
     *
     * <p>没有这个基线，「真实伤害绕过护甲」就只是一句无法证伪的话——
     * 有了它，两组数字（3.0 与 10.0）的差异才是护甲被绕过的证据。</p>
     */
    public static void vanillaArmorReducesDamage(GameTestHelper helper) {
        Player player = spawnMockPlayer(helper);
        writeConfig(false, false, PROTECTION_DAYS, 0);
        setDay(helper.getLevel(), 5);
        equipDiamondArmor(player);
        assertDiamondArmor(player);
        float drop = hurtAndMeasureHealthDrop(player, attackDamage(helper.getLevel(), player));
        assertDrop("真实伤害比例 0% 时应完全按原版结算（护甲生效）", VANILLA_ARMORED_DROP, drop);
        helper.succeed();
    }

    /**
     * 断言 2：无敌玩家不被扣真实伤害（对应 Issue #5）。
     *
     * <p>分两层验证：一层走完整 {@code hurt} 流程，一层直接调结算函数。
     * 后者才是这条修复真正的回归点——Forge / NeoForge 的事件入口本身就位于
     * {@code isInvulnerableTo} 判定之后，只有 Fabric 的 mixin 注入点在它之前，
     * 所以「直接调结算函数」是唯一能在三个加载器上都验证该守卫的方式。</p>
     *
     * <p>用 {@code setInvulnerable} 而不是创造模式能力：直到 1.20.1，
     * {@code Player#isInvulnerableTo} 体里并没有创造模式/Abilities 这一支
     * （它只查 {@code Entity#invulnerable} 与 drowning/fall/fire/freezing 的规则开关），
     * {@code abilities.invulnerable} 只在 {@code Player#hurt} 里被检查。
     * 而待验证的守卫问的是 {@code isInvulnerableTo}，所以必须把那个字段置上。</p>
     */
    public static void invulnerablePlayerTakesNoRealDamage(GameTestHelper helper) {
        Player player = spawnMockPlayer(helper);
        // 真实伤害拉满并关掉保护期：如果无敌判定缺失，这里必然掉 10 点血
        writeConfig(false, false, PROTECTION_DAYS, 100);
        setDay(helper.getLevel(), 5);
        player.setInvulnerable(true);
        DamageSource source = attackDamage(helper.getLevel(), player);
        check(player.isInvulnerableTo(source), "前置条件：无敌玩家应当对 player_attack 免疫");
        float healthBefore = player.getHealth();

        float drop = hurtAndMeasureHealthDrop(player, source);
        assertDrop("无敌玩家走完整 hurt 流程不应掉血", 0.0F, drop);

        float returned = RealDamageCalculator.splitAndApplyRealDamage(player, source, DAMAGE);
        check(returned == DAMAGE, "无敌玩家应原样返回原始伤害（继续交给原版），实测返回 " + returned);
        check(player.getHealth() == healthBefore,
                "无敌玩家不应被直接扣真实伤害，实测血量 " + player.getHealth() + "（期望 " + healthBefore + "）");
        LOGGER.info("GameTest 无敌判定：返回原始伤害 {}，血量 {} -> {}", returned, healthBefore, player.getHealth());
        helper.succeed();
    }

    /**
     * 断言 3：保护期内不扣真实伤害。
     *
     * <p>保护期开启、上限 100 天、世界第 5 天 ⇒ 真实伤害被跳过，
     * 这一击回到原版护甲结算，血量下降应当回落到对照组的 3.0。</p>
     */
    public static void protectionPeriodSkipsRealDamage(GameTestHelper helper) {
        Player player = spawnMockPlayer(helper);
        writeConfig(false, true, PROTECTION_DAYS, 100);
        setDay(helper.getLevel(), 5);
        equipDiamondArmor(player);
        assertDiamondArmor(player);
        float drop = hurtAndMeasureHealthDrop(player, attackDamage(helper.getLevel(), player));
        assertDrop("保护期内真实伤害应为 0（回到原版护甲结算）", VANILLA_ARMORED_DROP, drop);
        helper.succeed();
    }

    /**
     * 断言 4：保护期外扣真实伤害。
     *
     * <p>与上一条唯一的差别是世界天数（200 &gt; protectionDays），
     * 血量下降从 3.0 变成 10.0，证明保护期这个开关确实在控制真实伤害。</p>
     */
    public static void afterProtectionRealDamageApplies(GameTestHelper helper) {
        Player player = spawnMockPlayer(helper);
        writeConfig(false, true, PROTECTION_DAYS, 100);
        setDay(helper.getLevel(), 200);
        equipDiamondArmor(player);
        assertDiamondArmor(player);
        float drop = hurtAndMeasureHealthDrop(player, attackDamage(helper.getLevel(), player));
        assertDrop("保护期外真实伤害应生效（满护甲也照扣 10 点）", DAMAGE, drop);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 造一个可以同步走完伤害链路的玩家。
     *
     * <p>1.21 起 {@code makeMockPlayer} 多了一个 GameType 参数，这里按版本分支。</p>
     */
    //? if >=1.21 {
    /*static Player spawnMockPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        return player;
    }
    *///?} else {
    static Player spawnMockPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setHealth(player.getMaxHealth());
        return player;
    }
    //?}

    /**
     * 给玩家穿上全套钻石甲，并让它立刻生效。
     *
     * <p>装备本身不会直接改护甲值：护甲值来自 {@code Attributes#ARMOR} 上的修饰符，
     * 而修饰符是 {@code LivingEntity#tick} 里的装备变化检测才加上去的。GameTest 里没法
     * 让这个假玩家走一次 tick 再回来断言（它不在世界里，tick 会牵出移动、流体、饥饿等
     * 与本次断言无关的逻辑），所以在穿上装备之后，把护甲/韧性直接按同等数值写进属性基值：
     * 这样「满护甲」这一前提在伤害算出来的那一刻就是真的，而不是等下一 tick 才算数。</p>
     */
    static void equipDiamondArmor(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        player.getAttribute(Attributes.ARMOR).setBaseValue(DIAMOND_ARMOR_POINTS);
        player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(DIAMOND_ARMOR_TOUGHNESS);
    }

    /**
     * 施加一次伤害并返回血量下降量。
     */
    static float hurtAndMeasureHealthDrop(LivingEntity entity, DamageSource source) {
        float before = entity.getHealth();
        entity.hurt(source, DAMAGE);
        return before - entity.getHealth();
    }

    /**
     * 测试用的伤害类型。
     *
     * <p>为什么不用 {@code damageSources().generic()}：{@code minecraft:generic} 在
     * {@code bypasses_armor} 标签里（这一点在 1.19.2 / 1.20.1 / 1.21.1 的原版数据包里都一样），
     * 拿它测护甲会得到「护甲本来就不生效」的假象。{@code player_attack} 既不在
     * {@code bypasses_armor} 也不在 {@code bypasses_invulnerability} 里，
     * 正好能被护甲减免、也能被无敌判定拦下。</p>
     */
    //? if >=1.20 {
    static DamageSource attackDamage(ServerLevel level, Player player) {
        return level.damageSources().playerAttack(player);
    }
    //?} else {
    /*static DamageSource attackDamage(ServerLevel level, Player player) {
        return DamageSource.GENERIC;
    }
    *///?}

    /**
     * 把世界天数设到指定值。
     *
     * <p>真实伤害的保护期是按「世界天数」判定的，而 GameTest 服务端默认从第 0 天开始，
     * 所以「保护期内」「保护期外」两组测试必须各自显式设定天数，
     * 否则测试之间的先后顺序会决定结果。</p>
     */
    static void setDay(ServerLevel level, int day) {
        level.setDayTime(24_000L * day);
    }

    static void writeConfig(boolean limitMaximum, boolean protectionEnabled, int protectionDays, int constantProportion) {
        Path path = RealDamageConfig.path();
        if (path == null) {
            throw new IllegalStateException("配置尚未初始化，RealDamageConfig.init 没有跑过");
        }
        try {
            Files.writeString(path, String.format(CONFIG_TEMPLATE,
                    limitMaximum, protectionEnabled, protectionDays, constantProportion));
        } catch (IOException e) {
            throw new UncheckedIOException("写入测试配置失败：" + path, e);
        }
        RealDamageConfig.reloadConfig();
    }

    private static void assertDiamondArmor(Player player) {
        check(player.getArmorValue() == DIAMOND_ARMOR_POINTS,
                "钻石全套护甲值应为 " + DIAMOND_ARMOR_POINTS + "，实测 " + player.getArmorValue());
    }

    private static void assertDrop(String label, float expected, float actual) {
        LOGGER.info("GameTest {}：期望血量下降 {}，实测 {}", label, expected, actual);
        // 伤害全程是 float 运算，达不到精确相等，这里按 0.01 判定
        check(Math.abs(expected - actual) < 0.01F,
                label + "：期望血量下降 " + expected + "，实测 " + actual);
    }

    /**
     * 断言失败时抛框架自己的异常。
     *
     * <p>不用 {@link GameTestHelper#assertTrue}：1.19.2 的 GameTestHelper 还没有这个方法，
     * 而这个类由 Stonecutter 生成进所有变体的 main 源集，必须能在每个版本上编译通过。
     * {@link GameTestAssertException} 三个加载器的框架都会当成「测试失败」处理。</p>
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
