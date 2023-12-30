package top.prefersmin.realdamage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import top.prefersmin.realdamage.util.FunctionCalculationUtil;

import java.nio.file.Path;

import static top.prefersmin.realdamage.RealDamage.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RealDamageConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue IS_LIMIT_MAXIMUM_REAL_DAMAGE;
    private static final boolean IS_LIMIT_MAXIMUM_REAL_DAMAGE_DEFAULT = true;
    private static final String IS_LIMIT_MAXIMUM_REAL_DAMAGE_NAME = "isLimitMaximumRealDamage";
    private static final String IS_LIMIT_MAXIMUM_REAL_DAMAGE_COMMENT = "\n是否限制真实伤害最大值";

    public static final ForgeConfigSpec.IntValue REAL_DAMAGE_MAX;
    private static final int REAL_DAMAGE_MAX_DEFAULT = 3;
    private static final String REAL_DAMAGE_MAX_NAME = "realDamageMax";
    private static final String REAL_DAMAGE_MAX_COMMENT = "\n真实伤害最大值(范围:0-30)";

    public static final ForgeConfigSpec.BooleanValue IS_ENABLE_PROTECTION;
    private static final boolean IS_ENABLE_PROTECTION_DEFAULT = true;
    private static final String IS_ENABLE_PROTECTION_NAME = "isEnableProtection";
    private static final String IS_ENABLE_PROTECTION_COMMENT = "\n是否启用保护期(保护期内免受真实伤害)";

    public static final ForgeConfigSpec.IntValue PROTECTION_DAYS;
    private static final int PROTECTION_DAYS_DEFAULT = 3;
    private static final String PROTECTION_DAYS_NAME = "protectionDays";
    private static final String PROTECTION_DAYS_COMMENT = "\n保护期限(范围:0-100天)";

    public static final ForgeConfigSpec.BooleanValue REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION;
    private static final boolean REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_DEFAULT = true;
    private static final String REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_NAME = "realDamageCalculationMethodIsFunction";
    private static final String REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_COMMENT = "\n真实伤害计算方式是否为函数计算";

    public static final ForgeConfigSpec.IntValue DAMAGE_PROPORTION_MAX;
    private static final int DAMAGE_PROPORTION_MAX_DEFAULT = 30;
    private static final String DAMAGE_PROPORTION_MAX_NAME = "damageProportionMax";
    private static final String DAMAGE_PROPORTION_MAX_COMMENT = "\n最大真实伤害比例(范围:0-100%)";

    public static final ForgeConfigSpec.IntValue DAMAGE_PROPORTION_MIN;
    private static final int DAMAGE_PROPORTION_MIN_DEFAULT = 10;
    private static final String DAMAGE_PROPORTION_MIN_NAME = "damageProportionMin";
    private static final String DAMAGE_PROPORTION_MIN_COMMENT = "\n最小真实伤害比例(范围:0-100%,不得大于最大真实伤害比例)";

    public static final ForgeConfigSpec.IntValue CALCULATION_START_INCREASING_FROM_FEW_DAYS;
    private static final int CALCULATION_START_INCREASING_FROM_FEW_DAYS_DEFAULT = 10;
    private static final String CALCULATION_START_INCREASING_FROM_FEW_DAYS_NAME = "calculationStartIncreasingFromFewDays";
    private static final String CALCULATION_START_INCREASING_FROM_FEW_DAYS_COMMENT = "\n从第几天开始增算真实伤害比例(范围:0-100天)";

    public static final ForgeConfigSpec.IntValue REACHES_MAXIMUM_DAYS;
    private static final int REACHES_MAXIMUM_DAYS_DEFAULT = 100;
    private static final String REACHES_MAXIMUM_DAYS_NAME = "reachesMaximumDays";
    private static final String REACHES_MAXIMUM_DAYS_COMMENT = "\n达到最大伤害比例所需的天数(范围:0-500天)";

    public static final ForgeConfigSpec.IntValue DAMAGE_CONSTANT_PROPORTION;
    private static final int DAMAGE_CONSTANT_PROPORTION_DEFAULT = 30;
    private static final String DAMAGE_CONSTANT_PROPORTION_NAME = "damageConstantProportion";
    private static final String DAMAGE_CONSTANT_PROPORTION_COMMENT = "\n真实伤害固定比例(范围:0-100%)(当计算方式为函数计算时，此项设置始终无效)";

    public static final ForgeConfigSpec.BooleanValue IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE;
    private static final boolean IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_DEFAULT = true;
    private static final String IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_NAME = "isPlayerAbsorptionWithstandRealDamage";
    private static final String IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_COMMENT = "\n伤害吸收Buff能否抵挡真实伤害";

    public static final ForgeConfigSpec.BooleanValue IS_ENABLE_DEBUG_MODE;
    private static final boolean IS_ENABLE_DEBUG_MODE_DEFAULT = false;
    private static final String IS_ENABLE_DEBUG_MODE_NAME = "isEnableDebugMode";
    private static final String IS_ENABLE_DEBUG_MODE_COMMENT = "\n是否启用调试模式(调试模式下将会在控制台与聊天框输出调试信息)";

    public static final ForgeConfigSpec SPEC;
    public static final Path path = FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml");

    static {
        BUILDER.push("RealDamage");
        IS_LIMIT_MAXIMUM_REAL_DAMAGE = BUILDER.comment(IS_LIMIT_MAXIMUM_REAL_DAMAGE_COMMENT).define(IS_LIMIT_MAXIMUM_REAL_DAMAGE_NAME, IS_LIMIT_MAXIMUM_REAL_DAMAGE_DEFAULT);
        BUILDER.push("Maximum");
        REAL_DAMAGE_MAX = BUILDER.comment(REAL_DAMAGE_MAX_COMMENT).defineInRange(REAL_DAMAGE_MAX_NAME, REAL_DAMAGE_MAX_DEFAULT, 0, 30);
        BUILDER.pop();
        IS_ENABLE_PROTECTION = BUILDER.comment(IS_ENABLE_PROTECTION_COMMENT).define(IS_ENABLE_PROTECTION_NAME, IS_ENABLE_PROTECTION_DEFAULT);
        BUILDER.push("Protection");
        PROTECTION_DAYS = BUILDER.comment(PROTECTION_DAYS_COMMENT).defineInRange(PROTECTION_DAYS_NAME, PROTECTION_DAYS_DEFAULT, 0, 100);
        BUILDER.pop();
        REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION = BUILDER.comment(REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_COMMENT).define(REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_NAME, REAL_DAMAGE_CALCULATION_METHOD_IS_FUNCTION_DEFAULT);
        BUILDER.push("FunctionCalculation");
        DAMAGE_PROPORTION_MAX = BUILDER.comment(DAMAGE_PROPORTION_MAX_COMMENT).defineInRange(DAMAGE_PROPORTION_MAX_NAME, DAMAGE_PROPORTION_MAX_DEFAULT, 0, 100);
        DAMAGE_PROPORTION_MIN = BUILDER.comment(DAMAGE_PROPORTION_MIN_COMMENT).defineInRange(DAMAGE_PROPORTION_MIN_NAME, DAMAGE_PROPORTION_MIN_DEFAULT, 0, 100);
        CALCULATION_START_INCREASING_FROM_FEW_DAYS = BUILDER.comment(CALCULATION_START_INCREASING_FROM_FEW_DAYS_COMMENT).defineInRange(CALCULATION_START_INCREASING_FROM_FEW_DAYS_NAME, CALCULATION_START_INCREASING_FROM_FEW_DAYS_DEFAULT, 0, 100);
        REACHES_MAXIMUM_DAYS = BUILDER.comment(REACHES_MAXIMUM_DAYS_COMMENT).defineInRange(REACHES_MAXIMUM_DAYS_NAME, REACHES_MAXIMUM_DAYS_DEFAULT, 0, 500);
        BUILDER.pop();
        BUILDER.push("ConstantCalculation");
        DAMAGE_CONSTANT_PROPORTION = BUILDER.comment(DAMAGE_CONSTANT_PROPORTION_COMMENT).defineInRange(DAMAGE_CONSTANT_PROPORTION_NAME, DAMAGE_CONSTANT_PROPORTION_DEFAULT, 0, 100);
        BUILDER.pop();
        IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE = BUILDER.comment(IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_COMMENT).define(IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_NAME, IS_PLAYER_ABSORPTION_WITHSTAND_REAL_DAMAGE_DEFAULT);
        BUILDER.push("DebugMode");
        IS_ENABLE_DEBUG_MODE = BUILDER.comment(IS_ENABLE_DEBUG_MODE_COMMENT).define(IS_ENABLE_DEBUG_MODE_NAME, IS_ENABLE_DEBUG_MODE_DEFAULT);
        BUILDER.pop();
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * 初始化配置项
     *
     * @param file 配置项路径
     */
    public static void init() {
        CommentedFileConfig commentedFileConfig = CommentedFileConfig.builder(path).sync().autosave().writingMode(WritingMode.REPLACE).build();
        commentedFileConfig.load();
        SPEC.setConfig(commentedFileConfig);
        // 初始化增算速度与真实伤害最小值补偿，用于根据世界天数计算真实伤害比例
        FunctionCalculationUtil.init();
    }

    /**
     * 监听配置文件重载事件，并通过重载事件重载配置文件
     *
     * @param event 事件
     */
    @SubscribeEvent
    public void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            SPEC.setConfig(event.getConfig().getConfigData());
        }
    }

    public static void reloadConfig() {

        // 创建并加载配置文件
        CommentedFileConfig commentedFileConfig = CommentedFileConfig.builder(path).sync().autosave().writingMode(WritingMode.REPLACE).build();
        commentedFileConfig.load();

        // 设置新的配置文件
        SPEC.setConfig(commentedFileConfig);

        // 初始化增算速度与真实伤害最小值补偿，用于根据世界天数计算真实伤害比例
        FunctionCalculationUtil.init();

    }

}
