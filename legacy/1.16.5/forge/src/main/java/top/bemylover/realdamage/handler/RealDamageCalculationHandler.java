package top.bemylover.realdamage.handler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.bemylover.realdamage.RealDamage;

/**
 * 真实伤害计算的事件入口（仅 Forge）。
 *
 * <p>Fabric 侧没有等价事件（1.16.5 的 Fabric API 里能插手伤害的只有
 * {@code ServerLivingEntityEvents}，且只能放行或取消，改不了数值），
 * 因此 Fabric 的入口改由 mixin 在 {@code Player#actuallyHurt} 头部注入，
 * 两者共用 {@link RealDamageCalculator}，详见该 mixin 的注释。</p>
 *
 * <p>1.16.5 的 {@code LivingHurtEvent} 触发点在 {@code Player#actuallyHurt} /
 * {@code LivingEntity#actuallyHurt} 内部：{@code isInvulnerableTo} 判定<b>之后</b>、
 * 护甲减免<b>之前</b>（已用 Forge 36.2.42 的字节码核实，见 README 里的反汇编片段）。
 * 这是本模组语义的基准，Fabric 侧的注入点必须与它对齐。</p>
 *
 * @author PrefersMin
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = RealDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealDamageCalculationHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 判断受伤实体是否为玩家
        if (!(event.getEntity() instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) event.getEntity();
        // 真实伤害已在 RealDamageCalculator 里直接扣血，这里只把剩余伤害交回原版继续走护甲结算
        event.setAmount(RealDamageCalculator.splitAndApplyRealDamage(
                new ForgePlayerDamageContext(player, event.getSource()), event.getAmount()));
    }

}
