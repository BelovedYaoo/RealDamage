package top.bemylover.realdamage.config;

/**
 * 只读的配置快照，算法层（{@link top.bemylover.realdamage.util.FunctionCalculationUtil}）
 * 只依赖这个接口。
 *
 * <p>抽出接口的目的：函数计算工具类原先直接读 {@code RealDamageConfig.*} 静态字段，
 * 于是算法与配置类、配置类与加载器彼此纠缠在一起；改成「算法只认快照」之后，
 * 算法可以脱离 Minecraft 与加载器单独编译、单独验证，三个加载器也共享同一份实现。</p>
 *
 * @author PrefersMin
 */
public interface RealDamageSettings {

    /**
     * 是否限制真实伤害最大值
     */
    boolean isLimitMaximumRealDamage();

    /**
     * 真实伤害最大值
     */
    int realDamageMax();

    /**
     * 是否启用保护期
     */
    boolean isEnableProtection();

    /**
     * 保护期限（天）
     */
    int protectionDays();

    /**
     * 真实伤害计算方式是否为函数计算
     */
    boolean isRealDamageCalculationMethodFunction();

    /**
     * 最大真实伤害比例
     */
    int damageProportionMax();

    /**
     * 最小真实伤害比例
     */
    int damageProportionMin();

    /**
     * 从第几天开始增算真实伤害比例
     */
    int calculationStartIncreasingFromFewDays();

    /**
     * 达到最大伤害比例所需的天数
     */
    int reachesMaximumDays();

    /**
     * 真实伤害固定比例
     */
    int damageConstantProportion();

    /**
     * 伤害吸收 Buff 能否抵挡真实伤害
     */
    boolean isPlayerAbsorptionWithstandRealDamage();

    /**
     * 是否启用调试模式
     */
    boolean isEnableDebugMode();
}
