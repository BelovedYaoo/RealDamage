package top.bemylover.realdamage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bemylover.realdamage.util.FunctionCalculationUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 配置层：自研的 nightconfig 实现，两个加载器共用。
 *
 * <p>nightconfig 在 Forge 1.16.5 运行时里自带（3.6.3），Fabric 侧随产物以 jar-in-jar 分发，
 * 因此 {@link #reloadConfig()} 只需要重新解析同一个文件，不必依赖任何加载器的配置 API。</p>
 *
 * <p>本类刻意不引用任何 Minecraft / 加载器类型：配置文件目录由各加载器入口点
 * 通过 {@link #init(Path)} 传入，因此配置与算法两层可以脱离游戏环境单独验证。</p>
 *
 * @author PrefersMin
 */
public final class RealDamageConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("RealDamage");

    /**
     * 配置文件名。这里写死而不引用 {@code RealDamage.MODID}：那个类带加载器注解，
     * 引用它会让配置层重新绑到具体加载器上。文件名同时是对外契约（老配置直接沿用），本就不该改。
     */
    public static final String FILE_NAME = "realdamage-common.toml";

    private static final String ROOT = "RealDamage";

    public static final BooleanValue IS_LIMIT_MAXIMUM_REAL_DAMAGE =
            new BooleanValue(ROOT + ".isLimitMaximumRealDamage", true, "\n是否限制真实伤害最大值");

    public static final IntValue REAL_DAMAGE_MAX =
            new IntValue(ROOT + ".Maximum.realDamageMax", 3, 0, 30, "\n真实伤害最大值(范围:0-30)");

    public static final BooleanValue IS_ENABLE_PROTECTION =
            new BooleanValue(ROOT + ".isEnableProtection", true, "\n是否启用保护期(保护期内免受真实伤害)");

    public static final IntValue PROTECTION_DAYS =
            new IntValue(ROOT + ".Protection.protectionDays", 3, 0, 100, "\n保护期限(范围:0-100天)");

    public static final BooleanValue REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION =
            new BooleanValue(ROOT + ".realDamageCalculationMethodIsFunction", true, "\n真实伤害计算方式是否为函数计算");

    public static final IntValue DAMAGE_PROPORTION_MAX =
            new IntValue(ROOT + ".FunctionCalculation.damageProportionMax", 30, 0, 100, "\n最大真实伤害比例(范围:0-100%)");

    public static final IntValue DAMAGE_PROPORTION_MIN =
            new IntValue(ROOT + ".FunctionCalculation.damageProportionMin", 10, 0, 100, "\n最小真实伤害比例(范围:0-100%,不得大于最大真实伤害比例)");

    public static final IntValue CALCULATION_START_INCREASING_FROM_FEW_DAYS =
            new IntValue(ROOT + ".FunctionCalculation.calculationStartIncreasingFromFewDays", 10, 0, 100, "\n从第几天开始增算真实伤害比例(范围:0-100天)");

    public static final IntValue REACHES_MAXIMUM_DAYS =
            new IntValue(ROOT + ".FunctionCalculation.reachesMaximumDays", 100, 0, 500, "\n达到最大伤害比例所需的天数(范围:0-500天)");

    public static final IntValue DAMAGE_CONSTANT_PROPORTION =
            new IntValue(ROOT + ".ConstantCalculation.damageConstantProportion", 30, 0, 100, "\n真实伤害固定比例(范围:0-100%)(当计算方式为函数计算时，此项设置始终无效)");

    public static final BooleanValue IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE =
            new BooleanValue(ROOT + ".isPlayerAbsorptionWithstandRealDamage", true, "\n伤害吸收Buff能否抵挡真实伤害");

    public static final BooleanValue IS_ENABLE_DEBUG_MODE =
            new BooleanValue(ROOT + ".DebugMode.isEnableDebugMode", false, "\n是否启用调试模式(调试模式下将会在控制台与聊天框输出调试信息)");

    /**
     * 全部配置项，校验脚本据此检查「12 项齐全」，也避免漏加载某一项。
     *
     * <p>用 {@code Collections.unmodifiableList(Arrays.asList(...))} 而不是 {@code List.of}：
     * Java 8 没有后者，而 1.16.5 的运行时就是 Java 8。</p>
     */
    public static final List<ConfigValue<?>> VALUES = Collections.unmodifiableList(Arrays.<ConfigValue<?>>asList(
            IS_LIMIT_MAXIMUM_REAL_DAMAGE,
            REAL_DAMAGE_MAX,
            IS_ENABLE_PROTECTION,
            PROTECTION_DAYS,
            REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION,
            DAMAGE_PROPORTION_MAX,
            DAMAGE_PROPORTION_MIN,
            CALCULATION_START_INCREASING_FROM_FEW_DAYS,
            REACHES_MAXIMUM_DAYS,
            DAMAGE_CONSTANT_PROPORTION,
            IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE,
            IS_ENABLE_DEBUG_MODE
    ));

    // 单次伤害计算里读到的必须是同一份数值，因此做成不可变快照，整体替换而不是逐项改
    private static volatile RealDamageSettings settings = snapshot();

    // 由快照推导出来的函数参数（增算速度、最小值补偿），只在加载/重载时重算一次
    private static volatile FunctionCalculationUtil.Parameters functionParameters =
            FunctionCalculationUtil.compute(settings);

    private static Path path;

    private RealDamageConfig() {
    }

    /**
     * 由加载器入口点在 mod 初始化阶段调用。
     *
     * @param configDir 加载器提供的配置文件目录
     */
    public static synchronized void init(Path configDir) {
        path = configDir.resolve(FILE_NAME);
        load();
    }

    /**
     * 配置文件重载，由 {@code /realDamage configReload} 命令调用。
     */
    public static synchronized void reloadConfig() {
        if (path == null) {
            // 正常流程里 init 发生在 mod 构造期，早于任何命令；这里只是防御
            LOGGER.warn("配置尚未初始化，本次重载已跳过");
            return;
        }
        load();
    }

    /**
     * 当前配置快照，供算法层只读使用。
     */
    public static RealDamageSettings settings() {
        return settings;
    }

    /**
     * 由当前快照推导出的函数计算参数。
     */
    public static FunctionCalculationUtil.Parameters functionParameters() {
        return functionParameters;
    }

    /**
     * 当前配置文件路径，未初始化时为 null。
     */
    public static Path path() {
        return path;
    }

    private static void load() {
        Path file = path;
        if (file.getParent() != null) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                LOGGER.error("创建配置目录 {} 失败，本次沿用旧配置", file.getParent(), e);
                return;
            }
        }
        // 显式指定 TOML 而非让 nightconfig 按扩展名探测：探测依赖 ServiceLoader 查找 ConfigFormat，
        // 而 Fabric 侧 nightconfig 是以 jar-in-jar 形式随产物加载的，探测并不可靠
        CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance())
                // 同步落盘：异步写盘时进程退出可能丢注释
                .sync()
                .writingMode(WritingMode.REPLACE)
                .build();
        boolean dirty;
        try {
            config.load();
        } catch (RuntimeException e) {
            // 文件语法坏了：保留上一次的可用数值，并且不写回，避免把用户内容覆盖成残缺版本
            LOGGER.error("配置文件 {} 解析失败，本次沿用旧配置", file, e);
            config.close();
            return;
        }
        try {
            dirty = false;
            for (ConfigValue<?> value : VALUES) {
                dirty |= value.load(config);
            }
            if (dirty) {
                // 只在真的需要时才写回：补默认值/注释，或把越界值收敛后的结果落盘
                config.save();
            }
        } finally {
            config.close();
        }
        settings = snapshot();
        // 派生值必须在配置读完之后重算：增算速度与最小值补偿都是由配置项迭代推导出来的
        functionParameters = FunctionCalculationUtil.compute(settings);
    }

    private static RealDamageSettings snapshot() {
        return new Snapshot(
                IS_LIMIT_MAXIMUM_REAL_DAMAGE.get(),
                REAL_DAMAGE_MAX.get(),
                IS_ENABLE_PROTECTION.get(),
                PROTECTION_DAYS.get(),
                REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION.get(),
                DAMAGE_PROPORTION_MAX.get(),
                DAMAGE_PROPORTION_MIN.get(),
                CALCULATION_START_INCREASING_FROM_FEW_DAYS.get(),
                REACHES_MAXIMUM_DAYS.get(),
                DAMAGE_CONSTANT_PROPORTION.get(),
                IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE.get(),
                IS_ENABLE_DEBUG_MODE.get()
        );
    }

    /**
     * 配置的不可变快照。
     *
     * <p>main 分支上这是一个 {@code record}，但 1.16.5 的运行时是 Java 8，record 连字节码层面
     * 都不存在（class 文件版本 58 起才有），所以这里改写成 final 类 + 全参构造器，
     * 方法名保持不变，调用点无需改动。</p>
     */
    private static final class Snapshot implements RealDamageSettings {

        private final boolean isLimitMaximumRealDamage;
        private final int realDamageMax;
        private final boolean isEnableProtection;
        private final int protectionDays;
        private final boolean isRealDamageCalculationMethodFunction;
        private final int damageProportionMax;
        private final int damageProportionMin;
        private final int calculationStartIncreasingFromFewDays;
        private final int reachesMaximumDays;
        private final int damageConstantProportion;
        private final boolean isPlayerAbsorptionWithstandRealDamage;
        private final boolean isEnableDebugMode;

        private Snapshot(boolean isLimitMaximumRealDamage,
                         int realDamageMax,
                         boolean isEnableProtection,
                         int protectionDays,
                         boolean isRealDamageCalculationMethodFunction,
                         int damageProportionMax,
                         int damageProportionMin,
                         int calculationStartIncreasingFromFewDays,
                         int reachesMaximumDays,
                         int damageConstantProportion,
                         boolean isPlayerAbsorptionWithstandRealDamage,
                         boolean isEnableDebugMode) {
            this.isLimitMaximumRealDamage = isLimitMaximumRealDamage;
            this.realDamageMax = realDamageMax;
            this.isEnableProtection = isEnableProtection;
            this.protectionDays = protectionDays;
            this.isRealDamageCalculationMethodFunction = isRealDamageCalculationMethodFunction;
            this.damageProportionMax = damageProportionMax;
            this.damageProportionMin = damageProportionMin;
            this.calculationStartIncreasingFromFewDays = calculationStartIncreasingFromFewDays;
            this.reachesMaximumDays = reachesMaximumDays;
            this.damageConstantProportion = damageConstantProportion;
            this.isPlayerAbsorptionWithstandRealDamage = isPlayerAbsorptionWithstandRealDamage;
            this.isEnableDebugMode = isEnableDebugMode;
        }

        @Override
        public boolean isLimitMaximumRealDamage() {
            return isLimitMaximumRealDamage;
        }

        @Override
        public int realDamageMax() {
            return realDamageMax;
        }

        @Override
        public boolean isEnableProtection() {
            return isEnableProtection;
        }

        @Override
        public int protectionDays() {
            return protectionDays;
        }

        @Override
        public boolean isRealDamageCalculationMethodFunction() {
            return isRealDamageCalculationMethodFunction;
        }

        @Override
        public int damageProportionMax() {
            return damageProportionMax;
        }

        @Override
        public int damageProportionMin() {
            return damageProportionMin;
        }

        @Override
        public int calculationStartIncreasingFromFewDays() {
            return calculationStartIncreasingFromFewDays;
        }

        @Override
        public int reachesMaximumDays() {
            return reachesMaximumDays;
        }

        @Override
        public int damageConstantProportion() {
            return damageConstantProportion;
        }

        @Override
        public boolean isPlayerAbsorptionWithstandRealDamage() {
            return isPlayerAbsorptionWithstandRealDamage;
        }

        @Override
        public boolean isEnableDebugMode() {
            return isEnableDebugMode;
        }
    }
}
