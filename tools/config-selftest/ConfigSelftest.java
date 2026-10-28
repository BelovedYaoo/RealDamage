import top.bemylover.realdamage.config.ConfigValue;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.config.RealDamageSettings;
import top.bemylover.realdamage.util.FunctionCalculationUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 配置层与函数计算的自检程序：不依赖 Minecraft、不依赖任何加载器，直接用 javac/java 跑。
 *
 * <p>之所以单独做这样一份自检：配置层被重写成自研实现后，最容易出问题的地方（缺项补全、
 * 越界收敛、注释写回、坏文件不覆盖、函数参数退化）都不需要开服务器就能验证，
 * 而服务器冒烟测试慢且依赖网络下载。</p>
 */
public final class ConfigSelftest {

    private static int failures;

    public static void main(String[] args) throws IOException {
        firstLoadCreatesFileWithAllItems();
        outOfRangeValuesAreClampedAndWrittenBack();
        missingKeysAreRestoredAndUnknownKeysKept();
        brokenTomlKeepsPreviousValuesAndFile();
        degenerateFunctionConfigFallsBackInsteadOfThrowing();
        defaultFunctionCurveBehavesAsDocumented();

        if (failures > 0) {
            System.out.println("CONFIG SELFTEST FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("CONFIG SELFTEST PASSED");
    }

    // 1. 首次启动：文件被创建，12 项齐全，默认值正确，中文注释与分组表头都在
    private static void firstLoadCreatesFileWithAllItems() throws IOException {
        Path dir = newTempDir("first-load");
        RealDamageConfig.init(dir);
        Path file = dir.resolve(RealDamageConfig.FILE_NAME);
        check("配置文件已创建", Files.exists(file));
        String text = Files.readString(file, StandardCharsets.UTF_8);

        check("有 12 个配置项", RealDamageConfig.VALUES.size() == 12);
        for (ConfigValue<?> value : RealDamageConfig.VALUES) {
            String key = value.path().substring(value.path().lastIndexOf('.') + 1);
            check("文件含键 " + key, text.contains(key + " ="));
        }
        check("注释-是否限制真实伤害最大值", text.contains("是否限制真实伤害最大值"));
        check("注释-真实伤害最大值范围", text.contains("真实伤害最大值(范围:0-30)"));
        check("注释-保护期限", text.contains("保护期限(范围:0-100天)"));
        check("注释-固定比例", text.contains("真实伤害固定比例(范围:0-100%)"));
        check("注释-调试模式", text.contains("是否启用调试模式"));
        check("分组-RealDamage.Maximum", text.contains("RealDamage.Maximum"));
        check("分组-ConstantCalculation", text.contains("ConstantCalculation"));

        RealDamageSettings settings = RealDamageConfig.settings();
        check("默认 realDamageMax=3", settings.realDamageMax() == 3);
        check("默认 protectionDays=3", settings.protectionDays() == 3);
        check("默认 damageProportionMax=30", settings.damageProportionMax() == 30);
        check("默认 damageProportionMin=10", settings.damageProportionMin() == 10);
        check("默认 reachesMaximumDays=100", settings.reachesMaximumDays() == 100);
        check("默认 debug=false", !settings.isEnableDebugMode());
        check("默认 函数计算=true", settings.isRealDamageCalculationMethodFunction());
    }

    // 2. 越界值收敛到边界并写回文件，而不是抛异常
    private static void outOfRangeValuesAreClampedAndWrittenBack() throws IOException {
        Path dir = newTempDir("clamp");
        Files.writeString(dir.resolve(RealDamageConfig.FILE_NAME),
                "[RealDamage]\n"
                        + "\tisLimitMaximumRealDamage = true\n"
                        + "\t[RealDamage.Maximum]\n"
                        + "\t\trealDamageMax = 999\n"
                        + "\t[RealDamage.FunctionCalculation]\n"
                        + "\t\tdamageProportionMin = -5\n"
                        + "\t\tdamageProportionMax = 500\n",
                StandardCharsets.UTF_8);
        RealDamageConfig.init(dir);
        check("越界 realDamageMax 收敛为 30", RealDamageConfig.settings().realDamageMax() == 30);
        check("越界 damageProportionMin 收敛为 0", RealDamageConfig.settings().damageProportionMin() == 0);
        check("越界 damageProportionMax 收敛为 100", RealDamageConfig.settings().damageProportionMax() == 100);
        String text = Files.readString(dir.resolve(RealDamageConfig.FILE_NAME), StandardCharsets.UTF_8);
        check("收敛结果已写回文件", text.contains("realDamageMax = 30"));
        // 注意：不能直接搜 "500"——注释里本来就有「范围:0-500天」，按赋值行断言才准确
        check("越界值不再出现在文件里",
                !text.contains("= 999") && !text.contains("= 500") && !text.contains("= -5"));

        // 类型不符同样只回落默认值，不崩
        Files.writeString(dir.resolve(RealDamageConfig.FILE_NAME),
                "[RealDamage]\n\trealDamageCalculationMethodIsFunction = \"notABoolean\"\n", StandardCharsets.UTF_8);
        RealDamageConfig.init(dir);
        check("类型不符回落默认值", RealDamageConfig.settings().isRealDamageCalculationMethodFunction());
    }

    // 3. 缺项被补全，用户自己加的键与注释不被抹掉
    private static void missingKeysAreRestoredAndUnknownKeysKept() throws IOException {
        Path dir = newTempDir("restore");
        RealDamageConfig.init(dir);
        Path file = dir.resolve(RealDamageConfig.FILE_NAME);
        String text = Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("(?m)^.*protectionDays = .*$", "")
                + "\n# 用户自己写的注释\ncustomKey = 42\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);

        RealDamageConfig.reloadConfig();
        String after = Files.readString(file, StandardCharsets.UTF_8);
        check("缺项 protectionDays 被补回", after.contains("protectionDays ="));
        check("用户自定义键保留", after.contains("customKey = 42"));
        check("用户自定义注释保留", after.contains("用户自己写的注释"));
        check("缺项补回后取默认值", RealDamageConfig.settings().protectionDays() == 3);
    }

    // 4. 语法坏掉的配置文件：沿用旧值，且绝不覆盖用户文件
    private static void brokenTomlKeepsPreviousValuesAndFile() throws IOException {
        Path dir = newTempDir("broken");
        RealDamageConfig.init(dir);
        Path file = dir.resolve(RealDamageConfig.FILE_NAME);
        int before = RealDamageConfig.settings().realDamageMax();
        String broken = "this is not toml [[[\n";
        Files.writeString(file, broken, StandardCharsets.UTF_8);

        RealDamageConfig.reloadConfig();
        check("坏文件时沿用旧值", RealDamageConfig.settings().realDamageMax() == before);
        check("坏文件未被覆盖", Files.readString(file, StandardCharsets.UTF_8).equals(broken));
    }

    // 5. 函数参数退化（最大天数=0）时只告警，真实伤害恒为 0，不抛异常
    private static void degenerateFunctionConfigFallsBackInsteadOfThrowing() throws IOException {
        Path dir = newTempDir("degenerate");
        Files.writeString(dir.resolve(RealDamageConfig.FILE_NAME),
                "[RealDamage]\n"
                        + "\t[RealDamage.FunctionCalculation]\n"
                        + "\t\tcalculationStartIncreasingFromFewDays = 0\n"
                        + "\t\treachesMaximumDays = 0\n"
                        + "\t\tdamageProportionMin = 100\n"
                        + "\t\tdamageProportionMax = 0\n",
                StandardCharsets.UTF_8);
        RealDamageConfig.init(dir);
        FunctionCalculationUtil.Parameters parameters = RealDamageConfig.functionParameters();
        check("退化参数不抛异常", parameters != null);
        check("退化时真实伤害为 0（区间内）",
                FunctionCalculationUtil.getRealDamageValue(parameters, 50, 100F) == 0F);
        check("退化时真实伤害为 0（超界）",
                FunctionCalculationUtil.getRealDamageValue(parameters, 500, 100F) == 0F);
    }

    // 6. 默认配置下的曲线：与旧实现一致（起始天数前用最小比例、超界后用最大比例、区间内 logistic）
    private static void defaultFunctionCurveBehavesAsDocumented() throws IOException {
        Path dir = newTempDir("curve");
        Files.writeString(dir.resolve(RealDamageConfig.FILE_NAME),
                "[RealDamage]\n"
                        + "\t[RealDamage.FunctionCalculation]\n"
                        + "\t\tcalculationStartIncreasingFromFewDays = 10\n"
                        + "\t\treachesMaximumDays = 100\n"
                        + "\t\tdamageProportionMin = 10\n"
                        + "\t\tdamageProportionMax = 30\n",
                StandardCharsets.UTF_8);
        RealDamageConfig.init(dir);
        FunctionCalculationUtil.Parameters parameters = RealDamageConfig.functionParameters();

        checkClose("起始天数之前用最小比例 10%",
                FunctionCalculationUtil.getRealDamageValue(parameters, 5, 100F), 10F, 0.001F);
        checkClose("超过最大天数后用最大比例 30%",
                FunctionCalculationUtil.getRealDamageValue(parameters, 101, 100F), 30F, 0.001F);

        float previous = -1F;
        boolean monotonic = true;
        for (int day = 10; day <= 100; day++) {
            float value = FunctionCalculationUtil.getRealDamageValue(parameters, day, 100F);
            if (value < previous) {
                monotonic = false;
            }
            previous = value;
        }
        check("10..100 天真实伤害单调不减", monotonic);
        check("第 100 天接近最大比例", previous > 29F && previous <= 30F);

        // 到达最大天数的前一天仍未达判定界限，符合「99.5% 判定」的既有语义
        check("第 99 天尚未达到判定界限",
                FunctionCalculationUtil.getRealDamageValue(parameters, 99, 100F) < 29.9F);
    }

    private static Path newTempDir(String name) throws IOException {
        Path dir = Files.createTempDirectory("realdamage-selftest-" + name);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir)));
        return dir;
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清不掉不影响结论
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  [ok] " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static void checkClose(String name, float actual, float expected, float tolerance) {
        check(name + "（实际 " + actual + "，期望 " + expected + "）", Math.abs(actual - expected) <= tolerance);
    }
}
