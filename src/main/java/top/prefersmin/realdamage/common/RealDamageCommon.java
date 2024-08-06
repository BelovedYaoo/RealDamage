package top.prefersmin.realdamage.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import top.prefersmin.realdamage.config.RealDamageConfig;

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
                        // 设置命令权限
                        .requires(source -> source.hasPermission(4))
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
