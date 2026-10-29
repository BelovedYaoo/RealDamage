package top.bemylover.realdamage.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import top.bemylover.realdamage.config.RealDamageConfig;

/**
 * 命令注册
 *
 * @author PrefersMin
 * @version 1.0
 */
public class RealDamageCommon {

    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("realDamage")
                        // 设置命令权限：等级 4 即服务器所有者（原版 /jfr 用的就是这个等级）
                        // 1.21.11 把整数权限等级换成了 PermissionSet/PermissionCheck 体系，
                        // CommandSourceStack#hasPermission(int) 被删除，改用 Commands 里预置的检查器；
                        // LEVEL_OWNERS 就对应原来的等级 4（以 1.21.11 的官方映射与反编译的 JfrCommand 核实）
                        //? if >=1.21.11 {
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        //?} else {
                        /*.requires(source -> source.hasPermission(4))
                        *///?}
                        // 配置文件重载
                        .then(Commands.literal("configReload")
                                .executes(RealDamageCommon::configReload)
                        )
        );
    }

    /**
     * 配置文件重载方法
     */
    private static int configReload(CommandContext<CommandSourceStack> context) {
        RealDamageConfig.reloadConfig();
        return 1;
    }

}
