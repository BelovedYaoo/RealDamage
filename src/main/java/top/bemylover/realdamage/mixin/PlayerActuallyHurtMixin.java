package top.bemylover.realdamage.mixin;

//? if fabric {
/*import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.bemylover.realdamage.handler.RealDamageCalculator;
*///?}

//? if fabric && >=1.21.2 {
/*import net.minecraft.server.level.ServerLevel;
*///?}

/**
 * Fabric 侧的伤害钩子。
 *
 * <h2>为什么必须用 Mixin</h2>
 * 1.20.1 的 Fabric API 里能插手伤害的只有 ServerLivingEntityEvents.AllowDamage，
 * 它的签名是 boolean allowDamage(entity, source, amount)，官方注释写明
 * "false to cancel the damage"——只能放行或取消，改不了数值；而且 1.20.1 上连
 * AFTER_DAMAGE 都还没有。本模组的手法恰恰是把原始伤害拆成「真实伤害 + 剩余伤害」
 * （setAmount(ordinaryDamage) 是支点），Fabric 侧因此只能注入原版方法。
 *
 * <h2>为什么注入 actuallyHurt 的 HEAD，而不是 hurt</h2>
 * 先把 Forge 的等价物核实了一遍（1.20.1 Forge 补丁后的 LivingEntity 源码）：
 * <pre>
 * protected void actuallyHurt(DamageSource pDamageSource, float pDamageAmount) {
 *     if (!this.isInvulnerableTo(pDamageSource)) {
 *         pDamageAmount = ForgeHooks.onLivingHurt(this, pDamageSource, pDamageAmount); // LivingHurtEvent
 *         if (pDamageAmount &lt;= 0) return;
 *         pDamageAmount = this.getDamageAfterArmorAbsorb(pDamageSource, pDamageAmount); // 护甲减免在这之后
 * </pre>
 * 也就是说 LivingHurtEvent 在<b>护甲减免之前</b>触发，位置在 actuallyHurt 内部、
 * hurt 的那些提前返回（无敌、已死亡、火焰抗性，以及无敌帧内的伤害截断）之后。
 * 若注入 hurt 的 HEAD，连本该直接返回的情况（例如无敌帧内被重复结算）也会被扣一次真实伤害，
 * 与 Forge 版语义不符。因此这里选 actuallyHurt 的 HEAD：注入点与 Forge 的 onLivingHurt 对齐，
 * 拿到的 amount 就是「即将进入护甲结算」的数，改小它之后剩余伤害照样走护甲/附魔减免，
 * 真实伤害则由 RealDamageCalculator 直接扣血，从而绕过护甲。
 *
 * <h2>为什么只注入 Player</h2>
 * 原版 Player 覆写了 LivingEntity#actuallyHurt（为了统计与饱食度消耗），玩家的伤害结算
 * 实际走的是 Player 这一份；本模组本来就只处理玩家（旧版事件处理器对非玩家直接返回）。
 * 1.19.2 / 1.20.1 / 1.21.1 / 1.21.11 的 ServerPlayer 都没有再覆写该方法，所以不需要额外目标。
 *
 * <h2>为什么整个类按 1.21.2 分成两份</h2>
 * 1.21.2 给 actuallyHurt 追加了一个 ServerLevel 参数
 * （Player#actuallyHurt(ServerLevel, DamageSource, float)，用 1.21.11 的官方映射核实过），
 * 于是两者的注入点槽位与 handler 签名都变了：旧版是 (this, source, amount) ⇒ index = 2，
 * 新版是 (this, level, source, amount) ⇒ index = 3。参数槽位与签名无法只靠包一层方法体抹平，
 * 所以按版本各写一份。index 是绝对局部变量槽位（0 是 this），配合 argsOnly = true
 * 只在该方法的参数里找，避免误捕同名局部变量。
 *
 * <p>handler 的参数顺序必须与目标方法一致：先是被改的变量，再按目标方法的参数顺序排列。
 * 新版因此把 ServerLevel 也带上——即使本模组用不到它。</p>
 */
//? if fabric && >=1.21.2 {
/*@Mixin(Player.class)
public abstract class PlayerActuallyHurtMixin {

    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, index = 3)
    private float realdamage$splitRealDamage(float amount, ServerLevel level, DamageSource source) {
        return RealDamageCalculator.splitAndApplyRealDamage((Player) (Object) this, source, amount);
    }
}
*///?}
//? if fabric && <1.21.2 {
/*@Mixin(Player.class)
public abstract class PlayerActuallyHurtMixin {

    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, index = 2)
    private float realdamage$splitRealDamage(float amount, DamageSource source) {
        return RealDamageCalculator.splitAndApplyRealDamage((Player) (Object) this, source, amount);
    }
}
*///?}
