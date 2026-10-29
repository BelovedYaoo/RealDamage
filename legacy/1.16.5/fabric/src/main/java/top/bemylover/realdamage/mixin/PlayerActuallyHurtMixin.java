package top.bemylover.realdamage.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.bemylover.realdamage.handler.FabricPlayerDamageContext;
import top.bemylover.realdamage.handler.RealDamageCalculator;

/**
 * Fabric 侧的伤害钩子。
 *
 * <h2>为什么必须用 Mixin</h2>
 * 1.16.5 的 Fabric API 里能插手伤害的只有 {@code ServerLivingEntityEvents}，且只能放行或取消，
 * 改不了数值。本模组的手法恰恰是把原始伤害拆成「真实伤害 + 剩余伤害」
 * （改写传进护甲结算的那个 amount 是支点），所以只能注入原版方法。
 *
 * <h2>注入点：Player#actuallyHurt 的 HEAD，index = 2</h2>
 * 先把 1.16.5 的真实调用链用字节码核实了一遍（Forge 1.16.5-36.2.42 的
 * {@code net.minecraft.entity.player.PlayerEntity} / {@code net.minecraft.entity.LivingEntity}，
 * 官方成员名 + SRG 类名）：
 * <pre>
 * // Player#hurt 尾部：难度缩放后交给父类，不直接结算
 * 134: aload_0; aload_1; fload_2
 * 137: invokespecial LivingEntity.hurt(DamageSource, float)Z
 *
 * // LivingEntity#hurt 里是虚调用，玩家会走到 Player 自己的覆写
 * 260: invokevirtual actuallyHurt(DamageSource, float)V
 *
 * // Player#actuallyHurt 开头（未打补丁的原版即如此，Forge 只是往里面插了 onLivingHurt）
 *   0: aload_0; aload_1
 *   2: invokevirtual isInvulnerableTo(DamageSource)Z
 *   5: ifne 160                       // 免疫则整段跳过
 *  11: invokestatic ForgeHooks.onLivingHurt(...)   // &lt;-- Forge 的 LivingHurtEvent 就在这里
 *  22: invokevirtual getDamageAfterArmorAbsorb(...) // 护甲减免在这之后
 * </pre>
 * 由此得到两条结论：
 * <ol>
 *   <li>目标方法必须钉在 {@code Player} 上。{@code Player#actuallyHurt} 是<b>覆写</b>，
 *       且<b>不调 super</b>；如果注入 {@code LivingEntity#actuallyHurt}，
 *       玩家的伤害根本不会经过它（玩家只会走自己的那份）。</li>
 *   <li>注入点选 HEAD：拿到的 amount 就是「即将进入护甲结算」的数，改小它之后剩余伤害照样
 *       走护甲/附魔减免，真实伤害则由 {@link RealDamageCalculator} 直接扣血，从而绕过护甲。
 *       HEAD 早于方法内的 {@code isInvulnerableTo} 判定，所以结算核心自己又判了一次免疫
 *       （见 RealDamageCalculator 的注释），语义与 Forge 的 LivingHurtEvent 对齐。</li>
 * </ol>
 * <p>1.16.5 的 {@code actuallyHurt} 没有 1.21.2 之后追加的 ServerLevel 参数，
 * 签名就是 (this, source, amount)，所以槽位 index = 2，与 main 分支 <1.21.2 的那份完全一致。</p>
 *
 * <p>handler 的参数顺序必须与目标方法一致：先是被改的变量，再按目标方法的参数顺序排列。</p>
 */
@Mixin(Player.class)
public abstract class PlayerActuallyHurtMixin {

    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, index = 2)
    private float realdamage$splitRealDamage(float amount, DamageSource source) {
        return RealDamageCalculator.splitAndApplyRealDamage(
                new FabricPlayerDamageContext((Player) (Object) this, source), amount);
    }
}
