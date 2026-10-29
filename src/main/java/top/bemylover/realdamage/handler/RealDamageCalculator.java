package top.bemylover.realdamage.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.config.RealDamageSettings;
import top.bemylover.realdamage.util.FunctionCalculationUtil;

/**
 * 真实伤害的核心结算逻辑，与加载器无关。
 *
 * <p>Forge / NeoForge 由伤害事件调用（{@link RealDamageCalculationHandler}），
 * Fabric 由 mixin 调用（{@code PlayerActuallyHurtMixin}）。把逻辑收在这里，
 * 三个加载器的数值行为就只有一份实现，不会因为入口点不同而漂移。</p>
 *
 * @author PrefersMin
 * @version 1.0
 */
public final class RealDamageCalculator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RealDamageCalculator() {
    }

    /**
     * 把一次伤害拆成「真实伤害」与「剩余伤害」：
     * 真实伤害直接扣血（绕过护甲与附魔减伤），剩余伤害原样返回，继续交给原版结算走护甲减免。
     *
     * @param player         受伤玩家
     * @param source         伤害来源
     * @param originalDamage 进入护甲结算前的原始伤害
     * @return 仍需交给原版结算的剩余伤害
     */
    public static float splitAndApplyRealDamage(Player player, DamageSource source, float originalDamage) {

        // Forge / NeoForge 的 LivingHurtEvent 是在 actuallyHurt 内部的 isInvulnerableTo 分支里
        // 触发的，本就不会对无敌实体走到这里；Fabric 侧的 mixin 注入在方法开头，早于该检查。
        // 在这里统一补一次判定，否则创造/旁观模式玩家、以及火焰抗性这类伤害类型免疫的情况
        // 会在 Fabric 上被扣真实伤害，与另外两个加载器不一致。
        if (player.isInvulnerableTo(source)) {
            return originalDamage;
        }

        // 每次结算都用同一份配置快照，避免算到一半配置被重载导致数值前后不一致
        RealDamageSettings settings = RealDamageConfig.settings();
        FunctionCalculationUtil.Parameters parameters = RealDamageConfig.functionParameters();

        // 获取当前世界天数
        // 1.20 起 Entity#level() 取代了 getLevel()，两者只在这一处有差异
        //? if >=1.20 {
        int dayTime = Math.toIntExact(player.level().getDayTime() / 24000);
        //?} else {
        /*int dayTime = Math.toIntExact(player.getLevel().getDayTime() / 24000);
        *///?}

        // 判定是否启用保护期
        if (settings.isEnableProtection()) {
            // 判定是否在保护期间
            if (dayTime <= settings.protectionDays()) {
                return originalDamage;
            }
        }

        // 真实伤害
        float realDamage;

        // 判定真实伤害计算方式是否为函数计算
        if (settings.isRealDamageCalculationMethodFunction()) {
            // 使用函数计算真实伤害数值
            realDamage = FunctionCalculationUtil.getRealDamageValue(parameters, dayTime, originalDamage);
        } else {
            // 使用固定比例计算真实伤害数值
            realDamage = originalDamage * settings.damageConstantProportion() / 100F;
        }

        // 判定是否限制真实伤害最大值
        if (settings.isLimitMaximumRealDamage()) {
            // 真实伤害最大值 与 计算伤害 二者取较小值
            realDamage = Math.min(realDamage, settings.realDamageMax());
        }

        // 剩余伤害（使用减运算来避免浮点运算导致的精度丢失）
        float ordinaryDamage = originalDamage - realDamage;
        // 伤害吸收Buff（金心）
        float playerAbsorption = player.getAbsorptionAmount();
        // 是否存在伤害吸收Buff（金心）
        boolean haveAbsorption = playerAbsorption > 0;
        // 是否允许使用伤害吸收Buff抵挡真实伤害
        boolean isPlayerAbsorptionWithstandRealDamage = false;
        if (haveAbsorption) {
            isPlayerAbsorptionWithstandRealDamage = settings.isPlayerAbsorptionWithstandRealDamage();
        }

        // 判断是否存在伤害吸收Buff（金心）并且是否允许使用伤害吸收Buff抵挡真实伤害
        if (haveAbsorption && isPlayerAbsorptionWithstandRealDamage) {
            // 判断伤害吸收Buff（金心）是否足以吸收完真实伤害
            if (playerAbsorption < realDamage) {
                // 金心归零
                player.setAbsorptionAmount(0);
                // 处理剩余真实伤害
                player.setHealth((player.getHealth() - (realDamage - playerAbsorption)));
            } else {
                // 否则，金心减去真实伤害
                player.setAbsorptionAmount(playerAbsorption - realDamage);
            }
        } else {
            // 否则，生命值直接减去真实伤害
            player.setHealth((player.getHealth() - realDamage));
        }

        if (settings.isEnableDebugMode()) {
            LOGGER.info("---------------------------------------");
            LOGGER.info("当前生命：{}", player.getHealth());
            if (haveAbsorption) {
                LOGGER.info("当前金心：{}", playerAbsorption);
            }
            LOGGER.info("原始伤害：{}", originalDamage);
            LOGGER.info("真实伤害：{}", realDamage);
            LOGGER.info("伤害来源{}", source.getEntity());
            LOGGER.info("伤害类型：{}", source.getMsgId());
            LOGGER.info("---------------------------------------");
        }

        return ordinaryDamage;
    }

}
