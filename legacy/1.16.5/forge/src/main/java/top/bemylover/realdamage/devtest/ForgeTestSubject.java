package top.bemylover.realdamage.devtest;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.ForgePlayerDamageContext;
import top.bemylover.realdamage.handler.RealDamageCalculator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Forge 1.16.5 侧的被测玩家：把 {@link TestSubject} 的动作落到这一代的 SRG 类名上。
 *
 * <p>为什么不用 Forge 的 {@code FakePlayer}：它的 {@code isInvulnerableTo} 恒定返回 true，
 * 而真实伤害结算的第一步就是这个判定，用它做断言只能观测到「什么都没发生」。
 * 这里直接用 {@code PlayerEntity} 的公开构造器造一个普通玩家：它的伤害链路与真实玩家一致，
 * 只是不在世界里、不接收同步包，因此可以完全同步地把一次伤害算完。</p>
 *
 * @author PrefersMin
 */
final class ForgeTestSubject implements TestSubject {

    /**
     * 测试伤害数值。10 点既能让护甲减免比例明显，又不会一击致死。
     */
    private static final float DAMAGE = 10.0F;

    private final MinecraftServer server;
    private ServerWorld level;
    private PlayerEntity player;
    private DamageSource source;

    ForgeTestSubject(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void prepare(int day, boolean protectionEnabled, int protectionDays, int constantProportion,
                        boolean invulnerable, boolean diamondArmor) {
        writeConfig(protectionEnabled, protectionDays, constantProportion);
        this.level = server.overworld();
        // 1.16.5 设置天数的方法在 ServerWorld 上（World#setDayTime 不存在）
        level.setDayTime(24_000L * day);
        this.player = newPlayer(level);
        this.source = DamageSource.playerAttack(player);
        player.setInvulnerable(invulnerable);
        if (diamondArmor) {
            equipDiamondArmor(player);
        }
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
        // 这里走的就是生产路径：PlayerEntity#hurt → actuallyHurt → LivingHurtEvent
        player.hurt(source, DAMAGE);
        return before - player.getHealth();
    }

    @Override
    public float directSplitAndMeasureReturned() {
        return RealDamageCalculator.splitAndApplyRealDamage(new ForgePlayerDamageContext(player, source), DAMAGE);
    }

    private static PlayerEntity newPlayer(World level) {
        // PlayerEntity 在 1.16.5 是抽象类（只差 isSpectator/isCreative 两个方法，
        // 生存/创造判定由具体实现给），所以这里用一个匿名子类：它的伤害链路与真实玩家一致。
        PlayerEntity created = new PlayerEntity(level, new BlockPos(0, 100, 0), 0.0F,
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
     * 所以在穿上装备之后把护甲/韧性直接按同等数值写进属性基值：这样「满护甲」这一前提
     * 在伤害算出来的那一刻就是真的，而不是等下一 tick 才算数。</p>
     */
    private static void equipDiamondArmor(PlayerEntity player) {
        player.setItemSlot(EquipmentSlotType.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlotType.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlotType.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlotType.FEET, new ItemStack(Items.DIAMOND_BOOTS));
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
