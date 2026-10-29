package top.bemylover.realdamage;

import net.minecraft.command.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.RealDamageCalculationHandler;
import top.bemylover.realdamage.util.LibraryReport;

/**
 * Forge 1.16.5 的入口点。
 *
 * <p>伤害钩子走 {@code LivingHurtEvent}（见 {@link RealDamageCalculationHandler}），
 * 命令走 {@code RegisterCommandsEvent}，配置目录由 FML 提供。这三个类在 1.16.5 上的
 * 位置已按 Forge 1.16.5-36.2.42 的产物核实：
 * {@code net.minecraftforge.event.entity.living.LivingHurtEvent}、
 * {@code net.minecraftforge.event.RegisterCommandsEvent}、
 * {@code net.minecraftforge.fml.loading.FMLPaths}。命令 API 这一代还叫
 * {@code net.minecraft.command.Commands}（1.17 才改到 commands 包）。</p>
 *
 * @author PrefersMin
 */
@Mod(RealDamage.MODID)
public class RealDamage {

    public static final String MODID = "realdamage";

    public RealDamage() {
        MinecraftForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        // 自己读配置文件：不注册 ForgeConfigSpec，因此也不会收到 ModConfigEvent
        RealDamageConfig.init(FMLPaths.CONFIGDIR.get());
        // 启动自检：把两个第三方库的实际加载位置写进日志。1.16.5 上这两个库的来源完全不同
        // （big-math 随产物 jar-in-jar，nightconfig 由 Forge 自带），出问题时第一个要看的
        // 就是「加载到的到底是哪一份」
        LibraryReport.log();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        // 命令树在两个加载器上各写一份：Commands/CommandSource 的类名不一致，
        // 而真正干活的 RealDamageConfig.reloadConfig 是共享的
        event.getDispatcher().register(
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
