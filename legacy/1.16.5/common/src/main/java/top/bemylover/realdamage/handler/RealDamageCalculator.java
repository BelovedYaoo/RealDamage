package top.bemylover.realdamage.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.config.RealDamageSettings;
import top.bemylover.realdamage.util.FunctionCalculationUtil;

/**
 * 真实伤害的核心结算逻辑，与加载器、与具体 MC 映射都无关。
 *
 * <p>Forge 由伤害事件调用（{@code RealDamageCalculationHandler}），
 * Fabric 由 mixin 调用（{@code PlayerActuallyHurtMixin}）。把逻辑收在这里，
 * 两个加载器的数值行为就只有一份实现，不会因为入口点不同而漂移。
 * 游戏状态的读写通过 {@link PlayerDamageContext} 完成——原因见该接口的注释。</p>
 *
 * <h2>1.16.5 的 API 差异</h2>
 * <ul>
 *   <li>没有 {@code Entity#level()} 方法，那个年代是<b>字段</b> {@code level}；</li>
 *   <li>{@code World#getDayTime()} 在本版本里<b>存在</b>（与 26.2 相反，那一代换成了 WorldClock）；</li>
 *   <li>{@code isInvulnerableTo} / {@code getAbsorptionAmount} / {@code setHealth} 的签名
 *       与 main 分支的旧版本分支一致；</li>
 *   <li>{@code Player#actuallyHurt} 在本版本<b>存在</b>（Player 覆写了 LivingEntity 的同名方法，
 *       且不再调 super）——这一点与任务书里的猜测相反，已用字节码核实。</li>
 * </ul>
 *
 * @author PrefersMin
 * @version 1.0
 */
public final class RealDamageCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger("RealDamage");

    private RealDamageCalculator() {
    }

    /**
     * 把一次伤害拆成「真实伤害」与「剩余伤害」：
     * 真实伤害直接扣血（绕过护甲与附魔减伤），剩余伤害原样返回，继续交给原版结算走护甲减免。
     *
     * @param player         受伤玩家（本次伤害的视图）
     * @param originalDamage 进入护甲结算前的原始伤害
     * @return 仍需交给原版结算的剩余伤害
     */
    public static float splitAndApplyRealDamage(PlayerDamageContext player, float originalDamage) {

        // Forge 的 LivingHurtEvent 在 1.16.5 里挂在 actuallyHurt 内部、isInvulnerableTo 判定
        // 之后触发，本就不会对无敌实体走到这里；Fabric 侧的 mixin 注入在该方法开头，早于那道判定。
        // 所以在这里统一补一次判定，否则创造/旁观模式玩家、以及火焰抗性这类伤害类型免疫的情况
        // 会在 Fabric 上被扣真实伤害，与 Forge 上不一致。
        if (player.isInvulnerableTo()) {
            return originalDamage;
        }

        // 每次结算都用同一份配置快照，避免算到一半配置被重载导致数值前后不一致
        RealDamageSettings settings = RealDamageConfig.settings();
        FunctionCalculationUtil.Parameters parameters = RealDamageConfig.functionParameters();

        // 获取当前世界天数（主世界时钟的 tick 数 / 24000）
        int dayTime = Math.toIntExact(player.dayTime() / 24000L);

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
        float playerAbsorption = player.absorption();
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
                player.setAbsorption(0);
                // 处理剩余真实伤害
                player.setHealth(player.health() - (realDamage - playerAbsorption));
            } else {
                // 否则，金心减去真实伤害
                player.setAbsorption(playerAbsorption - realDamage);
            }
        } else {
            // 否则，生命值直接减去真实伤害
            player.setHealth(player.health() - realDamage);
        }

        if (settings.isEnableDebugMode()) {
            LOGGER.info("---------------------------------------");
            LOGGER.info("当前生命：{}", player.health());
            if (haveAbsorption) {
                LOGGER.info("当前金心：{}", playerAbsorption);
            }
            LOGGER.info("原始伤害：{}", originalDamage);
            LOGGER.info("真实伤害：{}", realDamage);
            LOGGER.info("伤害来源{}", player.damageSourceEntity());
            LOGGER.info("伤害类型：{}", player.damageSourceId());
            LOGGER.info("---------------------------------------");
        }

        return ordinaryDamage;
    }

}
