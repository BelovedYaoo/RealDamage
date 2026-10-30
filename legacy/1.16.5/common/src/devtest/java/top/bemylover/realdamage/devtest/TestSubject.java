package top.bemylover.realdamage.devtest;

/**
 * 自检用的「被测玩家」抽象：把两个加载器的 MC 类型差异挡在断言之外。
 *
 * <p>断言本身（期望血量下降 10.0 / 3.0 / 0.0）必须只有一份，否则「两个加载器数值一致」
 * 这句话就无法靠测试证明；而构造玩家、穿装备、设天数这些动作又绕不开加载器各自的类名
 * （Forge 1.16.5 是 SRG 类名，Fabric 是官方类名）。于是把动作收进本接口，
 * 断言留在 {@link RealDamageSelfTest} 里共享。</p>
 *
 * <p>本接口与实现都只在开发期使用，两个加载器的构建都会把它们排除在产物之外。</p>
 *
 * @author PrefersMin
 */
public interface TestSubject {

    /**
     * 把世界与玩家恢复到一个确定的初始状态。
     *
     * @param day                世界天数（保护期判定用）
     * @param protectionEnabled  是否启用保护期
     * @param protectionDays     保护期限（天）
     * @param constantProportion 固定比例真实伤害（百分数）
     * @param invulnerable       玩家是否无敌
     * @param diamondArmor       是否穿整套钻石甲
     */
    void prepare(int day, boolean protectionEnabled, int protectionDays, int constantProportion,
                 boolean invulnerable, boolean diamondArmor);

    /**
     * 把玩家设成「创造/旁观模式那道闸」挡住的状态，其余条件（世界天数、真实伤害比例、满护甲）同
     * {@link #prepare}。
     *
     * <p>为什么单独一个方法而不是给 {@link #prepare} 再加一个布尔量：这一条验证的是「注入点是否落在
     * 原版那道闸的下游」，前置条件比其它断言更讲究——必须保证玩家<b>不</b>因伤害类型免疫，
     * 否则「不掉血」可能来自另一道闸，断言就失去意义。把它做成独立入口，调用处一眼能看出区别。</p>
     *
     * <p>1.16.5 里创造与旁观共用同一个标记：{@code GameType#updatePlayerAbilities} 对 CREATIVE 和
     * SPECTATOR 都写 {@code abilities.invulnerable = true}，因此测这一个标记等于同时覆盖两种模式。</p>
     *
     * @param day                世界天数（保护期判定用，须在保护期之外）
     * @param constantProportion 固定比例真实伤害（百分数），用 100 才能看出「如果没被挡住会掉多少」
     */
    void prepareInvulnerableAbilities(int day, int constantProportion);

    /**
     * 当前护甲值（用于断言「满护甲」这个前提真的成立）。
     */
    int armorValue();

    /**
     * 当前生命值。
     */
    float health();

    /**
     * 玩家对测试伤害是否免疫（{@code isInvulnerableTo} 的前置断言）。
     */
    boolean isInvulnerableToTestDamage();

    /**
     * 施加一次测试伤害（{@code player_attack}，10 点）并返回血量下降量。
     * 这条路径会经过加载器各自的真实伤害入口（Forge 事件 / Fabric mixin）。
     */
    float hurtAndMeasureDrop();

    /**
     * 绕过原版链路，直接调用真实伤害结算核心，返回它交还给原版的剩余伤害。
     *
     * <p>这是「无敌玩家不被扣真实伤害」真正的回归点：Forge 的事件入口本身就在
     * {@code isInvulnerableTo} 之后，只有 Fabric 的 mixin 注入点在它之前，
     * 因此只有直接调用核心才能同时验证两个加载器的守卫。</p>
     */
    float directSplitAndMeasureReturned();
}
