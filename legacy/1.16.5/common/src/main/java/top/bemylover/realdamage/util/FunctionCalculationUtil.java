package top.bemylover.realdamage.util;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigDecimalMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.bemylover.realdamage.config.RealDamageSettings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 函数计算工具类。
 *
 * <p>与配置解耦：本类只接收 {@link RealDamageSettings}（加载器无关的只读快照），
 * 不再直接读 {@code RealDamageConfig} 的静态字段。求解结果封装成不可变的
 * {@link Parameters}，伤害计算阶段只做代入，不再反复迭代。</p>
 *
 * @author PrefersMin
 * @version 1.0
 */
public final class FunctionCalculationUtil {

    private static final Logger LOGGER = LogManager.getLogger("RealDamage");

    // 判定界限，由于logistic函数无法真正到达其上界，因此当逼近程度达到99.5%时，判定为最大值
    private static final BigDecimal DECISION_LIMIT = new BigDecimal("0.995");

    /**
     * 定点迭代的上限。正常情况下十几次内就收敛；设上限是为了让「参数本身无法收敛」的
     * 配置（例如最小比例等于最大比例）表现为一条警告而不是把服务器卡死在启动阶段。
     */
    private static final int MAX_ITERATIONS = 10_000;

    /**
     * 一次求解得到的函数参数，伤害计算阶段只读这里面的数值。
     *
     * <p>main 分支上这是一个 {@code record}；1.16.5 的运行时是 Java 8，record 的字节码版本
     * （58）在 Java 8 上根本加载不了，因此改写成 final 类。访问器方法名保持一致，
     * 算法主体与配置层无需改动。</p>
     */
    public static final class Parameters {

        private final BigDecimal incrementSpeed;
        private final BigDecimal transitionAmplitude;
        private final int startDays;
        private final int reachesMaximumDays;
        private final int damageProportionMin;
        private final int damageProportionMax;

        public Parameters(BigDecimal incrementSpeed,
                          BigDecimal transitionAmplitude,
                          int startDays,
                          int reachesMaximumDays,
                          int damageProportionMin,
                          int damageProportionMax) {
            this.incrementSpeed = incrementSpeed;
            this.transitionAmplitude = transitionAmplitude;
            this.startDays = startDays;
            this.reachesMaximumDays = reachesMaximumDays;
            this.damageProportionMin = damageProportionMin;
            this.damageProportionMax = damageProportionMax;
        }

        /**
         * 增算速度
         */
        public BigDecimal incrementSpeed() {
            return incrementSpeed;
        }

        /**
         * 真实伤害最小值补偿
         */
        public BigDecimal transitionAmplitude() {
            return transitionAmplitude;
        }

        /**
         * 从第几天开始增算真实伤害比例
         */
        public int startDays() {
            return startDays;
        }

        /**
         * 达到最大伤害比例所需的天数
         */
        public int reachesMaximumDays() {
            return reachesMaximumDays;
        }

        /**
         * 最小真实伤害比例（百分数，0-100）
         */
        public int damageProportionMin() {
            return damageProportionMin;
        }

        /**
         * 最大真实伤害比例（百分数，0-100）
         */
        public int damageProportionMax() {
            return damageProportionMax;
        }
    }

    private FunctionCalculationUtil() {
    }

    /**
     * 依据配置快照求解函数参数：通过定点迭代不断纠正增算速度与最小值补偿，直到逼近程度达到判定界限。
     *
     * @param settings 配置快照
     * @return 求解后的函数参数
     */
    public static Parameters compute(RealDamageSettings settings) {
        int startDays = settings.calculationStartIncreasingFromFewDays();
        int reachesMaximumDays = settings.reachesMaximumDays();
        int damageProportionMin = settings.damageProportionMin();
        int damageProportionMax = settings.damageProportionMax();
        try {
            return solve(startDays, reachesMaximumDays, damageProportionMin, damageProportionMax);
        } catch (RuntimeException e) {
            // 参数退化时（比如 reachesMaximumDays 为 0、或与起始天数相同、最小比例为 100%）
            // 迭代里会出现除零或 log(0)。这种情况下让真实伤害恒为 0 并明确告警，
            // 好过让服务端在建服阶段直接崩掉：配置改对再重载即可恢复。
            LOGGER.warn(
                    "真实伤害函数参数无法求解（起始天数={}，最大天数={}，最小比例={}%，最大比例={}%），"
                            + "真实伤害比例将恒为 0，请检查 realdamage-common.toml",
                    startDays, reachesMaximumDays, damageProportionMin, damageProportionMax, e);
            return new Parameters(BigDecimal.ZERO, BigDecimal.ZERO, startDays, reachesMaximumDays, 0, 0);
        }
    }

    private static Parameters solve(int startDays, int reachesMaximumDays, int damageProportionMin, int damageProportionMax) {
        BigDecimal incrementSpeed;
        // 初始值为配置项值
        BigDecimal transitionAmplitude = BigDecimal.valueOf(startDays);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            incrementSpeed = getIncrementSpeed(reachesMaximumDays, transitionAmplitude);
            transitionAmplitude = getTransitionAmplitude(startDays, damageProportionMin, incrementSpeed);
            if (isCalculationFinished(incrementSpeed, transitionAmplitude, startDays, reachesMaximumDays, damageProportionMin)) {
                return new Parameters(incrementSpeed, transitionAmplitude, startDays, reachesMaximumDays,
                        damageProportionMin, damageProportionMax);
            }
        }
        throw new IllegalStateException("定点迭代 " + MAX_ITERATIONS + " 次仍未收敛");
    }

    /**
     * 计算真实伤害原始比例
     *
     * @param incrementSpeed      增算速度
     * @param transitionAmplitude 真实伤害最小值补偿
     * @param days                天数
     * @return 原始比例
     */
    public static BigDecimal getRealDamageProportion(BigDecimal incrementSpeed, BigDecimal transitionAmplitude, BigDecimal days) {

        // 运算精度
        int scale = 13;
        MathContext mathContext = new MathContext(scale);

        // 计算指数部分：ℯ^(-(incrementSpeed * (days - transitionAmplitude)))
        BigDecimal exponent = BigDecimalMath.exp(incrementSpeed.multiply(days.subtract(transitionAmplitude)).multiply(new BigDecimal("-1")), mathContext);

        // 分子部分：常数 2
        BigDecimal numerator = new BigDecimal("2");

        // 计算分母部分：1 + ℯ^(-(incrementSpeed * (days - transitionAmplitude)))
        BigDecimal denominator = BigDecimal.ONE.add(exponent);

        // 计算最终结果：((2)/(1 + ℯ^(-(incrementSpeed * (days - transitionAmplitude))))) - 1
        return numerator.divide(denominator, mathContext).subtract(BigDecimal.ONE);

    }

    /**
     * 计算真实伤害数值
     *
     * @param parameters     函数参数
     * @param days           天数
     * @param originalDamage 原始伤害值
     * @return 真实伤害值
     */
    public static float getRealDamageValue(Parameters parameters, int days, float originalDamage) {

        // 从第几天开始增算真实伤害比例
        int calculationStartIncreasingFromFewDays = parameters.startDays();
        // 达到最大伤害比例所需的天数
        int reachesMaximumDays = parameters.reachesMaximumDays();
        // 获得最大真实伤害比例
        float damageProportionMax = parameters.damageProportionMax() / 100F;

        // 计算真实伤害数值
        float damageValue;
        // 判定当前天数是否在计算开始的天数之内，如果是，则使用最小真实伤害比例计算真实伤害数值，否则使用当前真实伤害比例计算真实伤害数值
        if (days < calculationStartIncreasingFromFewDays) {
            // 获得最小真实伤害比例
            float damageProportionMin = parameters.damageProportionMin() / 100F;
            // 使用最小真实伤害比例计算真实伤害数值
            damageValue = originalDamage * damageProportionMin;
        } else if (days > reachesMaximumDays) {
            // 使用最大真实伤害比例计算真实伤害数值
            return originalDamage * damageProportionMax;
        } else {
            // 获得原始比例
            float damageProportion = Float.parseFloat(String.valueOf(FunctionCalculationUtil.getRealDamageProportion(
                    parameters.incrementSpeed(), parameters.transitionAmplitude(),
                    new BigDecimal(String.valueOf(days))).setScale(3, RoundingMode.HALF_UP)));
            // 计算当前真实伤害比例
            float calculationProportion = damageProportion * damageProportionMax;
            // 使用当前真实伤害比例计算真实伤害数值
            damageValue = calculationProportion * originalDamage;
        }

        return damageValue;

    }

    /**
     * 计算增算速度
     *
     * @param reachesMaximumDays 达到最大伤害比例所需的天数
     * @param transitionAmplitude 真实伤害最小值补偿
     * @return 增算速度
     */
    private static BigDecimal getIncrementSpeed(int reachesMaximumDays, BigDecimal transitionAmplitude) {

        // 运算精度
        int scale = 12;
        MathContext mathContext = new MathContext(scale);

        // 达到最大伤害比例所需的天数，这里new BigDecimal的时候使用更加可靠的String类型构造方法，若使用int或double可能导致不可预知的错误
        BigDecimal reachesMaximumDaysValue = new BigDecimal(String.valueOf(reachesMaximumDays));

        // 计算分子部分：ln(((2)/(1+MAX_PROPORTION))-1)
        BigDecimal numerator = new BigDecimal("2").divide(new BigDecimal("1").add(DECISION_LIMIT), scale, RoundingMode.HALF_UP).subtract(new BigDecimal("1"));
        BigDecimal logarithmResult = BigDecimalMath.log(numerator, mathContext);

        // 计算分母部分：(reachesMaximumDays-transitionAmplitude)
        BigDecimal denominator = reachesMaximumDaysValue.subtract(transitionAmplitude);

        // 返回最终结果：-((ln(((2)/(1+MAX_PROPORTION))-1))/(reachesMaximumDays-transitionAmplitude))
        return logarithmResult.divide(denominator, scale, RoundingMode.HALF_UP).negate();

    }

    /**
     * 计算真实伤害最小值补偿
     *
     * @param startDays           从第几天开始增算真实伤害比例
     * @param damageProportionMin 最小真实伤害比例（百分数）
     * @param incrementSpeed      增算速度
     * @return 真实伤害最小值补偿
     */
    private static BigDecimal getTransitionAmplitude(int startDays, int damageProportionMin, BigDecimal incrementSpeed) {

        // 运算精度
        int scale = 14;
        MathContext mathContext = new MathContext(scale);

        // 最小真实伤害比例
        BigDecimal damageProportionMinValue = BigComplex.valueOf(new BigDecimal(String.valueOf(damageProportionMin))).re.divide(new BigDecimal("100"), mathContext);
        // 从第几天开始增算真实伤害比例
        BigDecimal calculationStartIncreasingFromFewDays = new BigDecimal(String.valueOf(startDays));

        // 计算分子部分：((1)/(incrementSpeed)) ln(((1-minProportion)/(minProportion+1)))
        BigDecimal numerator = BigComplex.ONE.divide(incrementSpeed, mathContext).re;
        BigDecimal fraction = BigComplex.ONE.subtract(damageProportionMinValue).divide(damageProportionMinValue.add(BigComplex.ONE.re), mathContext).re;
        BigDecimal logarithmResult = BigDecimalMath.log(fraction, mathContext);

        // 返回最终结果：y=x+((1)/(incrementSpeed)) ln(((1-minProportion)/(minProportion+1)))
        return calculationStartIncreasingFromFewDays.add(numerator.multiply(logarithmResult), mathContext);

    }

    /**
     * 纠正判定
     *
     * @param incrementSpeed      增算速度
     * @param transitionAmplitude 真实伤害最小值补偿
     * @param startDays           从第几天开始增算真实伤害比例
     * @param reachesMaximumDays  达到最大伤害比例所需的天数
     * @param damageProportionMin 最小真实伤害比例（百分数）
     * @return 逼近程度是否达到判定界限
     */
    private static boolean isCalculationFinished(BigDecimal incrementSpeed, BigDecimal transitionAmplitude,
                                                 int startDays, int reachesMaximumDays, int damageProportionMin) {

        // 运算精度
        int scale = 14;
        MathContext mathContext = new MathContext(scale);

        // 达到最大伤害比例所需的后一天
        BigDecimal reachesMaximumDaysValue = new BigDecimal(String.valueOf(reachesMaximumDays));
        // 达到最大伤害比例所需的前一天
        BigDecimal reachesMaximumDaysBefore = reachesMaximumDaysValue.subtract(BigDecimal.ONE);
        // 从第几天开始增算真实伤害比例
        BigDecimal calculationStartIncreasingFromFewDays = new BigDecimal(String.valueOf(startDays));
        // 最小真实伤害比例
        BigDecimal damageProportionMinValue = BigComplex.valueOf(new BigDecimal(String.valueOf(damageProportionMin))).re.divide(new BigDecimal("100"), mathContext);

        // 当天数为 calculationStartIncreasingFromFewDays 时，真实伤害比例 是否等于 最小真实伤害比例
        boolean minimumCalibration = FunctionCalculationUtil.getRealDamageProportion(incrementSpeed, transitionAmplitude, calculationStartIncreasingFromFewDays).compareTo(damageProportionMinValue) == 0;

        // 若不满足 minimumCalibration，则继续纠正
        if (!minimumCalibration) {
            return false;
        }

        // 当天数为 reachesMaximumDays 时，真实伤害比例 是否大于 判定界限
        boolean lessMaximum = FunctionCalculationUtil.getRealDamageProportion(incrementSpeed, transitionAmplitude, reachesMaximumDaysBefore).compareTo(DECISION_LIMIT) == -1;
        // 当天数为 reachesMaximumDays 的前一天时，真实伤害比例 是否小于 判定界限
        boolean greaterMaximum = FunctionCalculationUtil.getRealDamageProportion(incrementSpeed, transitionAmplitude, reachesMaximumDaysValue).compareTo(DECISION_LIMIT) > -1;

        // 若不同时满足 reachesMaximumDays 与 greaterMaximum，则继续纠正
        return !lessMaximum || !greaterMaximum;

    }

}
