package top.bemylover.realdamage.devtest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.FabricPlayerDamageContext;
import top.bemylover.realdamage.handler.RealDamageCalculator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Fabric 侧的被测玩家：把 {@link TestSubject} 的动作落到官方类名上。
 *
 * <p>造的是一个真正走原版链路的 {@code Player} 实例（不是 FakePlayer）：
 * 它的伤害链路与真实玩家一致，只是不在世界里、不接收同步包，因此可以完全同步地把一次伤害算完。
 * 1.16.5 的 {@code Player} 是抽象类（只差 {@code isSpectator}/{@code isCreative}），
 * 所以用匿名子类补上这两个判定。</p>
 *
 * @author PrefersMin
 */
final class FabricTestSubject implements TestSubject {

    /**
     * 测试伤害数值。10 点既能让护甲减免比例明显，又不会一击致死。
     */
    private static final float DAMAGE = 10.0F;

    private final MinecraftServer server;
    private ServerLevel level;
    private Player player;
    private DamageSource source;

    FabricTestSubject(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void prepare(int day, boolean protectionEnabled, int protectionDays, int constantProportion,
                        boolean invulnerable, boolean diamondArmor) {
        writeConfig(protectionEnabled, protectionDays, constantProportion);
        this.level = server.overworld();
        // 1.16.5 设置天数的方法在 ServerLevel 上（Level#setDayTime 不存在）
        level.setDayTime(24_000L * day);
        this.player = newPlayer(level);
        this.source = DamageSource.playerAttack(player);
        player.setInvulnerable(invulnerable);
        if (diamondArmor) {
            equipDiamondArmor(player);
        }
    }

    @Override
    public void prepareInvulnerableAbilities(int day, int constantProportion) {
        // 保护期关闭（第二个参数 false：day 那一段判定不会生效，protectionDays 只是占位）
        prepare(day, false, 100, constantProportion, false, true);
        // 创造与旁观模式在 1.16.5 里共用这一个标记（GameType#updatePlayerAbilities 对两者都写 true），
        // 而它在 Player#hurt 里就早退了，所以 actuallyHurt（mixin 的注入目标）根本不会被执行。
        // 这里直接设标记＝直接站在那道闸后面，而不是去模仿整个模式的切换。
        player.abilities.invulnerable = true;
    }

    @Override
    public int armorValue() {
        return player.getArmorValue();
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public boolean isInvulnerableToTestDamage() {
        return player.isInvulnerableTo(source);
    }

    @Override
    public float hurtAndMeasureDrop() {
        float before = player.getHealth();
        // 这里走的就是生产路径：Player#hurt → LivingEntity#hurt → Player#actuallyHurt（mixin）
        player.hurt(source, DAMAGE);
        return before - player.getHealth();
    }

    @Override
    public float directSplitAndMeasureReturned() {
        return RealDamageCalculator.splitAndApplyRealDamage(new FabricPlayerDamageContext(player, source), DAMAGE);
    }

    private static Player newPlayer(ServerLevel level) {
        Player created = new Player(level, new BlockPos(0, 100, 0), 0.0F,
                new GameProfile(UUID.randomUUID(), "realdamage-selftest")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        created.setHealth(created.getMaxHealth());
        return created;
    }

    /**
     * 给玩家穿上全套钻石甲，并让它立刻生效。
     *
     * <p>装备本身不会直接改护甲值：护甲值来自 {@code Attributes.ARMOR} 上的修饰符，
     * 而修饰符是 {@code LivingEntity#tick} 里的装备变化检测才加上去的。自检里没法让这个
     * 玩家走一次 tick 再回来断言（它不在世界里，tick 会牵出移动、饥饿等无关逻辑），
     * 所以在穿上装备之后把护甲/韧性直接按同等数值写进属性基值。</p>
     */
    private static void equipDiamondArmor(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        player.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
        player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
    }

    /**
     * 测试前整份写入配置文件。
     *
     * <p>为什么写整份而不是只改一两项：配置层只提供「读一份 TOML」这一条路径
     * （{@link RealDamageConfig#reloadConfig()}），没有供测试直接改内存值的后门。
     * 写整份既避免了依赖上一次测试残留的配置，也顺带验证了「改配置文件 → configReload」
     * 这条线上路径确实能改到数值。</p>
     */
    private static void writeConfig(boolean protectionEnabled, int protectionDays, int constantProportion) {
        Path path = RealDamageConfig.path();
        if (path == null) {
            throw new IllegalStateException("配置尚未初始化，RealDamageConfig.init 没有跑过");
        }
        // 函数计算关闭、固定比例由参数给定：真实伤害是否生效必须只由被测的那一项决定
        String text = "[RealDamage]\n"
                + "isLimitMaximumRealDamage = false\n"
                + "isEnableProtection = " + protectionEnabled + "\n"
                + "realDamageCalculationMethodIsFunction = false\n"
                + "isPlayerAbsorptionWithstandRealDamage = false\n"
                + "\n"
                + "[RealDamage.Maximum]\n"
                + "realDamageMax = 30\n"
                + "\n"
                + "[RealDamage.Protection]\n"
                + "protectionDays = " + protectionDays + "\n"
                + "\n"
                + "[RealDamage.FunctionCalculation]\n"
                + "damageProportionMax = 30\n"
                + "damageProportionMin = 10\n"
                + "calculationStartIncreasingFromFewDays = 10\n"
                + "reachesMaximumDays = 100\n"
                + "\n"
                + "[RealDamage.ConstantCalculation]\n"
                + "damageConstantProportion = " + constantProportion + "\n"
                + "\n"
                + "[RealDamage.DebugMode]\n"
                + "isEnableDebugMode = false\n";
        try {
            Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("写入测试配置失败：" + path, e);
        }
        RealDamageConfig.reloadConfig();
    }
}
