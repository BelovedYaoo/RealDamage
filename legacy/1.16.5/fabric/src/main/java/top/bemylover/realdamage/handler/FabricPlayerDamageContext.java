package top.bemylover.realdamage.handler;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric 侧的 {@link PlayerDamageContext} 适配器。
 *
 * <p>这里出现的是完整官方名（{@code Player}、{@code DamageSource}、{@code Level}），
 * 与 Forge 1.16.5 的「SRG 类名 + 官方成员名」混合命名不同，因此这类胶水只能按加载器各写一份；
 * 数值逻辑仍然只有 {@link RealDamageCalculator} 一份。</p>
 *
 * @author PrefersMin
 */
public final class FabricPlayerDamageContext implements PlayerDamageContext {

    private final Player player;
    private final DamageSource source;

    public FabricPlayerDamageContext(Player player, DamageSource source) {
        this.player = player;
        this.source = source;
    }

    @Override
    public boolean isInvulnerableTo() {
        return player.isInvulnerableTo(source);
    }

    @Override
    public long dayTime() {
        // 1.16.5 里 Entity#level 是 public 字段（类型 Level），没有 level() 方法
        return player.level.getDayTime();
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public void setHealth(float health) {
        player.setHealth(health);
    }

    @Override
    public float absorption() {
        return player.getAbsorptionAmount();
    }

    @Override
    public void setAbsorption(float amount) {
        player.setAbsorptionAmount(amount);
    }

    @Override
    public String damageSourceId() {
        return source.getMsgId();
    }

    @Override
    public String damageSourceEntity() {
        return String.valueOf(source.getEntity());
    }
}
