package top.bemylover.realdamage.handler;

import net.minecraft.world.entity.player.Player;
//? if forge {
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?}

//? if neoforge {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
*///?}
import top.bemylover.realdamage.RealDamage;

/**
 * 真实伤害计算的事件入口（仅 Forge / NeoForge）。
 *
 * <p>Fabric 侧没有等价事件，因此这个入口在 Fabric 产物里不存在：
 * 1.20.1 的 {@code ServerLivingEntityEvents.AllowDamage} 只能放行或取消伤害，
 * 无法改写数值，而本模组的手法正是「把原始伤害拆成真实伤害 + 剩余伤害」。
 * Fabric 的入口改由 {@code PlayerActuallyHurtMixin} 在 {@code Player#actuallyHurt} 里注入，
 * 结算逻辑与本类共用 {@link RealDamageCalculator}，详见该 mixin 的注释。</p>
 *
 * @author PrefersMin
 * @version 1.0
 */
//? if forge {
@Mod.EventBusSubscriber(modid = RealDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
//?}
//? if neoforge {
/*@EventBusSubscriber(modid = RealDamage.MODID)
*///?}
public class RealDamageCalculationHandler {

    //? if forge {
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 判断受伤实体是否为玩家
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 真实伤害已在 RealDamageCalculator 里直接扣血，这里只把剩余伤害交回原版继续走护甲结算
        event.setAmount(RealDamageCalculator.splitAndApplyRealDamage(player, event.getSource(), event.getAmount()));
    }
    //?}

    //? if neoforge {
    /*@SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        // 判断受伤实体是否为玩家
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 真实伤害已在 RealDamageCalculator 里直接扣血，这里只把剩余伤害交回原版继续走护甲结算
        event.setAmount(RealDamageCalculator.splitAndApplyRealDamage(player, event.getSource(), event.getAmount()));
    }
    *///?}

}
