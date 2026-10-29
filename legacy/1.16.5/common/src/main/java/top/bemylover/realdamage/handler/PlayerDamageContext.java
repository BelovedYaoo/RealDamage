package top.bemylover.realdamage.handler;

/**
 * 「本次伤害的玩家视图」：真实伤害结算核心只通过这个接口访问游戏世界。
 *
 * <h2>为什么需要这一层</h2>
 * <p>1.16.5 的两个加载器拿到的 MC 类型名并不一致，而这一代又没有 Stonecutter 那种源码预处理
 * （1.16.5 完全脱离 main 分支的构建体系）。实测两边的差异只在<b>类名/包名</b>上：
 * <ul>
 *   <li>Forge 1.16.5（ForgeGradle 的 official 渠道）是「SRG 类名 + 官方成员名」的混合体：
 *       {@code net.minecraft.entity.player.PlayerEntity}、{@code net.minecraft.util.DamageSource}、
 *       {@code net.minecraft.world.World}，而方法名是 {@code hurt/actuallyHurt/getHealth}；</li>
 *   <li>Fabric（Loom + officialMojangMappings）是完整官方名：
 *       {@code net.minecraft.world.entity.player.Player}、
 *       {@code net.minecraft.world.damagesource.DamageSource}、
 *       {@code net.minecraft.world.level.Level}。</li>
 * </ul>
 * 成员名一致，所以纯数值逻辑（倍率、保护期、吸收、上限）可以完全共享，
 * 真正没法共享的只有「取/写玩家状态」这几句胶水。把这几句收进本接口后，
 * 结算核心仍是一份 Java 代码，两个加载器只各写一个适配器。</p>
 *
 * <p>接口只暴露基本类型，避免任何 MC 类型出现在 common 源集里。</p>
 *
 * @author PrefersMin
 */
public interface PlayerDamageContext {

    /**
     * 玩家是否对本次伤害免疫（对应 {@code Entity#isInvulnerableTo}）。
     */
    boolean isInvulnerableTo();

    /**
     * 当前世界时间（tick），用于换算保护期天数（对应 {@code World#getDayTime}）。
     */
    long dayTime();

    /**
     * 当前生命值（对应 {@code getHealth}）。
     */
    float health();

    /**
     * 设置生命值（对应 {@code setHealth}）。
     */
    void setHealth(float health);

    /**
     * 当前伤害吸收量（金心，对应 {@code getAbsorptionAmount}）。
     */
    float absorption();

    /**
     * 设置伤害吸收量（金心，对应 {@code setAbsorptionAmount}）。
     */
    void setAbsorption(float amount);

    /**
     * 伤害类型标识（对应 {@code DamageSource#getMsgId}），仅调试输出使用。
     */
    String damageSourceId();

    /**
     * 伤害来源实体的描述（对应 {@code DamageSource#getEntity}），仅调试输出使用。
     */
    String damageSourceEntity();
}
