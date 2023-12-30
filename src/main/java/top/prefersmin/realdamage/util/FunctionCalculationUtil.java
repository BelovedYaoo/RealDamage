package top.prefersmin.realdamage.util;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigDecimalMath;
import top.prefersmin.realdamage.config.RealDamageConfig;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 函数计算工具类
 *
 * @author PrefersMin
 * @version 1.0
 */
public class FunctionCalculationUtil {

    // 判定界限，由于logistic函数无法真正到达其上界，因此当逼近程度达到99。5%时，判定为最大值
    private static final BigDecimal DECISION_LIMIT = new BigDecimal("0.995");

    // 增算速度
    public static BigDecimal incrementSpeed;

    // 真实伤害最小值补偿
    public static BigDecimal transitionAmplitude;

    /**
     * 初始化函数，自动计算增算速度与真实伤害最小值补偿，通过doWhile循环不断纠正，直到逼近程度达到判定界限
     */
    public static void init() {

        // 初始值为 0
        incrementSpeed = new BigDecimal("0");
        // 初始值为配置项值
        transitionAmplitude = new BigDecimal(RealDamageConfig.CALCULATION_START_INCREASING_FROM_FEW_DAYS.get().toString());

        // doWhile循环纠正
        do {
            incrementSpeed = FunctionCalculationUtil.getIncrementSpeed(transitionAmplitude);
            transitionAmplitude = FunctionCalculationUtil.getTransitionAmplitude(incrementSpeed);
        } while (FunctionCalculationUtil.isCalculationFinished());

    }

    /**
     * 计算真实伤害原始比例
     * @param days 天数
     * @return 原始比例
     */
    public static BigDecimal getRealDamageProportion(BigDecimal days) {

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
     * @param days 天数
     * @param originalDamage 原始伤害值
     * @return 真实伤害值
     */
    public static float getRealDamageValue(int days, float originalDamage) {

        // 从第几天开始增算真实伤害比例
        int calculationStartIncreasingFromFewDays = RealDamageConfig.CALCULATION_START_INCREASING_FROM_FEW_DAYS.get();
        // 达到最大伤害比例所需的天数
        int reachesMaximumDays = RealDamageConfig.REACHES_MAXIMUM_DAYS.get();
        // 获得最大真实伤害比例
        float damageProportionMax = RealDamageConfig.DAMAGE_PROPORTION_MAX.get() / 100F;
        System.out.println(damageProportionMax);

        // 计算真实伤害数值
        float damageValue;
        // 判定当前天数是否在计算开始的天数之内，如果是，则使用最小真实伤害比例计算真实伤害数值，否则使用当前真实伤害比例计算真实伤害数值
        if (days < calculationStartIncreasingFromFewDays) {
            // 获得最小真实伤害比例
            float damageProportionMin = RealDamageConfig.DAMAGE_PROPORTION_MIN.get() / 100F;
            // 使用最小真实伤害比例计算真实伤害数值
            damageValue = originalDamage * damageProportionMin;
        } else if (days > reachesMaximumDays) {
            // 使用最大真实伤害比例计算真实伤害数值
            return originalDamage * damageProportionMax;
        } else {
            // 获得原始比例
            float damageProportion = Float.parseFloat(String.valueOf(FunctionCalculationUtil.getRealDamageProportion(new BigDecimal(String.valueOf(days))).setScale(3, RoundingMode.HALF_UP)));
            // 计算当前真实伤害比例
            float calculationProportion = damageProportion * damageProportionMax;
            // 使用当前真实伤害比例计算真实伤害数值
            damageValue = calculationProportion * originalDamage;
        }

        return damageValue;

    }

    /**
     * 计算增算速度
     * @param transitionAmplitude 真实伤害最小值补偿
     * @return 增算速度
     */
    public static BigDecimal getIncrementSpeed(BigDecimal transitionAmplitude) {

        // 运算精度
        int scale = 12;
        MathContext mathContext = new MathContext(scale);

        // 达到最大伤害比例所需的天数，这里new BigDecimal的时候使用更加可靠的String类型构造方法，若使用int或double可能导致不可预知的错误
        BigDecimal reachesMaximumDays = new BigDecimal(RealDamageConfig.REACHES_MAXIMUM_DAYS.get().toString());

        // 计算分子部分：ln(((2)/(1+MAX_PROPORTION))-1)
        BigDecimal numerator = new BigDecimal("2").divide(new BigDecimal("1").add(DECISION_LIMIT), scale, RoundingMode.HALF_UP).subtract(new BigDecimal("1"));
        BigDecimal logarithmResult = BigDecimalMath.log(numerator, mathContext);

        // 计算分母部分：(reachesMaximumDays-transitionAmplitude)
        BigDecimal denominator = reachesMaximumDays.subtract(transitionAmplitude);

        // 返回最终结果：-((ln(((2)/(1+MAX_PROPORTION))-1))/(reachesMaximumDays-transitionAmplitude))
        return logarithmResult.divide(denominator, scale, RoundingMode.HALF_UP).negate();

    }

    /**
     * 计算真实伤害最小值补偿
     * @param incrementSpeed 增算速度
     * @return 真实伤害最小值补偿
     */
    public static BigDecimal getTransitionAmplitude(BigDecimal incrementSpeed) {

        // 运算精度
        int scale = 14;
        MathContext mathContext = new MathContext(scale);

        // 最小真实伤害比例
        BigDecimal damageProportionMin = BigComplex.valueOf(new BigDecimal(RealDamageConfig.DAMAGE_PROPORTION_MIN.get().toString())).re.divide(new BigDecimal("100"), mathContext);
        // 从第几天开始增算真实伤害比例
        BigDecimal calculationStartIncreasingFromFewDays = new BigDecimal(RealDamageConfig.CALCULATION_START_INCREASING_FROM_FEW_DAYS.get().toString());

        // 计算分子部分：((1)/(incrementSpeed)) ln(((1-minProportion)/(minProportion+1)))
        BigDecimal numerator = BigComplex.ONE.divide(incrementSpeed, mathContext).re;
        BigDecimal fraction = BigComplex.ONE.subtract(damageProportionMin).divide(damageProportionMin.add(BigComplex.ONE.re), mathContext).re;
        BigDecimal logarithmResult = BigDecimalMath.log(fraction, mathContext);

        // 返回最终结果：y=x+((1)/(incrementSpeed)) ln(((1-minProportion)/(minProportion+1)))
        return calculationStartIncreasingFromFewDays.add(numerator.multiply(logarithmResult), mathContext);

    }

    /**
     * 纠正判定
     * @return 逼近程度是否达到判定界限
     */
    public static boolean isCalculationFinished() {

        // 运算精度
        int scale = 14;
        MathContext mathContext = new MathContext(scale);

        // 达到最大真实伤害比例所需的天数
        BigDecimal reachesMaximumDays = new BigDecimal(RealDamageConfig.REACHES_MAXIMUM_DAYS.get().toString());
        // 达到最大伤害比例所需的前一天
        BigDecimal reachesMaximumDaysBefore = reachesMaximumDays.subtract(BigDecimal.ONE);
        // 从第几天开始增算真实伤害比例
        BigDecimal calculationStartIncreasingFromFewDays = new BigDecimal(RealDamageConfig.CALCULATION_START_INCREASING_FROM_FEW_DAYS.get().toString());
        // 最小真实伤害比例
        BigDecimal damageProportionMin = BigComplex.valueOf(new BigDecimal(RealDamageConfig.DAMAGE_PROPORTION_MIN.get().toString())).re.divide(new BigDecimal("100"), mathContext);

        // 当天数为 calculationStartIncreasingFromFewDays 时，真实伤害比例 是否等于 最小真实伤害比例
        boolean minimumCalibration = FunctionCalculationUtil.getRealDamageProportion(calculationStartIncreasingFromFewDays).compareTo(damageProportionMin) == 0;

        // 若不满足 minimumCalibration，则继续纠正
        if (!minimumCalibration) {
            return false;
        }

        // 当天数为 reachesMaximumDays 时，真实伤害比例 是否大于 判定界限
        boolean lessMaximum = FunctionCalculationUtil.getRealDamageProportion(reachesMaximumDaysBefore).compareTo(DECISION_LIMIT) == -1;
        // 当天数为 reachesMaximumDays 的前一天时，真实伤害比例 是否小于 判定界限
        boolean greaterMaximum = FunctionCalculationUtil.getRealDamageProportion(reachesMaximumDays).compareTo(DECISION_LIMIT) > -1;

        // 若不同时满足 reachesMaximumDays 与 greaterMaximum，则继续纠正
        return !lessMaximum || !greaterMaximum;

    }

}
