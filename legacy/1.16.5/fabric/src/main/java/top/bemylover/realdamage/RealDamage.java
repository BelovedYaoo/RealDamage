package top.bemylover.realdamage;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.util.LibraryReport;

/**
 * Fabric 1.16.5 的入口点。
 *
 * <p>这一代的 Fabric API 命令回调还是 v1：
 * {@code net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback}，签名是
 * {@code register(CommandDispatcher<CommandSourceStack>, boolean dedicated)}
 * （已按 fabric-command-api-v1 1.1.3+3cc0f0907d 的产物核实；v2 是 1.19 之后才有的）。</p>
 *
 * <p>伤害钩子不在入口里：见 {@code top.bemylover.realdamage.mixin.PlayerActuallyHurtMixin}。</p>
 *
 * @author PrefersMin
 */
public class RealDamage implements ModInitializer {

    public static final String MODID = "realdamage";

    @Override
    public void onInitialize() {
        // 配置目录由加载器提供，配置层本身与加载器无关；Fabric 没有配置文件 API，只能自己管路径
        RealDamageConfig.init(FabricLoader.getInstance().getConfigDir());
        // /realDamage 命令走 Fabric API 的回调（fabric-api 已在清单里声明为必需前置）。
        // 命令树与 Forge 侧各写一份：CommandSourceStack/Commands 的类名与 Forge 1.16.5 不同，
        // 真正干活的 RealDamageConfig.reloadConfig 是共享的
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        // 启动自检：把两个第三方库的实际加载位置写进日志（Fabric 侧两者都随产物 jar-in-jar）
        LibraryReport.log();
        registerDevSelfTestIfPresent();
    }

    /**
     * 开发期数值自检的注册（仅 dev run 生效）。
     *
     * <p>自检类连同它的断言都在 devtest 源集里，产物 jar 里没有（见 fabric/build.gradle 的 jar 排除）。
     * Fabric 没有 Forge 那种注解扫描，必须显式注册，所以这里用反射：类不存在时安静跳过，
     * 这样同一份入口代码既能用于开发期验证，又不会把测试代码带进产物。</p>
     */
    private static void registerDevSelfTestIfPresent() {
        try {
            Class.forName("top.bemylover.realdamage.devtest.FabricSelfTest")
                    .getMethod("register")
                    .invoke(null);
        } catch (ClassNotFoundException ignored) {
            // 产物里没有开发期自检，属于正常情况
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("开发期自检注册失败", e);
        }
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, boolean dedicated) {
        dispatcher.register(
                Commands.literal("realDamage")
                        // 等级 4 即服务器所有者（原版 /jfr 用的就是这个等级）
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("configReload")
                                .executes(context -> {
                                    RealDamageConfig.reloadConfig();
                                    return 1;
                                })
                        )
        );
    }

}
