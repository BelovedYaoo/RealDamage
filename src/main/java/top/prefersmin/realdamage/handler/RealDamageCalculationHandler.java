package top.prefersmin.realdamage.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.prefersmin.realdamage.RealDamage;
import top.prefersmin.realdamage.config.RealDamageConfig;
import top.prefersmin.realdamage.util.FunctionCalculationUtil;

@Mod.EventBusSubscriber(modid = RealDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealDamageCalculationHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {

        // 判断受伤实体是否为玩家
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 获取当前世界天数
        int DayTime = Math.toIntExact(player.getLevel().getDayTime() / 24000);

        // 判定是否启用保护期
        if (RealDamageConfig.IS_ENABLE_PROTECTION.get()) {
            // 判定是否在保护期间
            if (DayTime <= RealDamageConfig.PROTECTION_DAYS.get()) {
                return;
            }
        }

        // 原始伤害
        float originalDamage = event.getAmount();
        // 真实伤害
        float realDamage;

        // 判定是否真实伤害计算方式是否为函数计算
        if (RealDamageConfig.REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION.get()) {
            // 使用函数计算真实伤害数值
            realDamage = FunctionCalculationUtil.getRealDamageValue(DayTime, originalDamage);
        } else {
            // 使用固定比例计算真实伤害数值
            realDamage = originalDamage * RealDamageConfig.DAMAGE_CONSTANT_PROPORTION.get() / 100F;
        }

        // 判定是否限制真实伤害最大值
        if (RealDamageConfig.IS_LIMIT_MAXIMUM_REAL_DAMAGE.get()) {
            // 真实伤害最大值 与 计算伤害 二者取较小值
            realDamage = Math.min(realDamage, RealDamageConfig.REAL_DAMAGE_MAX.get());
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
            isPlayerAbsorptionWithstandRealDamage = (RealDamageConfig.IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE.get());
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

        LOGGER.info("_______________________________________________________");
        // LOGGER.info("incrementSpeed：" + FunctionCalculationUtil.incrementSpeed);
        // LOGGER.info("transitionAmplitude：" + FunctionCalculationUtil.transitionAmplitude);
        // LOGGER.info("最大天数：" + FunctionCalculationUtil.getRealDamageProportion(new BigDecimal(String.valueOf(RealDamageConfig.REACHES_MAXIMUM_DAYS.get()))));
        // LOGGER.info("最大天数-1：" + FunctionCalculationUtil.getRealDamageProportion(new BigDecimal(String.valueOf(RealDamageConfig.REACHES_MAXIMUM_DAYS.get() - 1))));
        // LOGGER.info("游戏时间：" + (player.getLevel().getDayTime() / 24000));
        // LOGGER.info("恒定比例：" + RealDamageConfig.DAMAGE_CONSTANT_PROPORTION.get());
        LOGGER.info("当前生命：" + player.getHealth());
        // LOGGER.info("当前护甲：" + player.getArmorValue());
        // if (haveAbsorption) LOGGER.info("当前金心：" + playerAbsorption);
        LOGGER.info("原始伤害：" + originalDamage);
        LOGGER.info("真实伤害：" + realDamage);
        LOGGER.info("伤害来源" + event.getSource().getEntity());
        LOGGER.info("伤害类型：" + event.getSource().getMsgId());

        // 造成剩余伤害
        event.setAmount(ordinaryDamage);

    }

}
