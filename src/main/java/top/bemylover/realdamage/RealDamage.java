package top.bemylover.realdamage;

//? if fabric {
/*import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
*///?}

//? if forge {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
*///?}

//? if neoforge {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
//?}

import top.bemylover.realdamage.common.RealDamageCommon;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.RealDamageCalculationHandler;

//? if fabric {
/*public class RealDamage implements ModInitializer {
*///?}
//? if forge {
/*@Mod(RealDamage.MODID)
public class RealDamage {
*///?}
//? if neoforge {
@Mod(RealDamage.MODID)
public class RealDamage {
//?}

    public static final String MODID = "realdamage";

    //? if fabric {
    /*@Override
    public void onInitialize() {
        // 配置目录由加载器提供，配置层本身与加载器无关；Fabric 没有配置文件 API，只能自己管路径
        RealDamageConfig.init(FabricLoader.getInstance().getConfigDir());
        // /realDamage 命令走 Fabric API 的回调（fabric-api 已在清单里声明为必需前置）
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RealDamageCommon.register(dispatcher));
    }
    *///?}

    //? if forge {
    /*public RealDamage() {
        MinecraftForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        // 自己读配置文件：不再向 FML 注册 ForgeConfigSpec，因此也不会收到 ModConfigEvent
        RealDamageConfig.init(FMLPaths.CONFIGDIR.get());
    }
    *///?}

    //? if neoforge {
    public RealDamage() {
        NeoForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        // 自己读配置文件：不再向 FML 注册 ModConfigSpec，因此也不会收到 ModConfigEvent
        RealDamageConfig.init(FMLPaths.CONFIGDIR.get());
    }
    //?}

    //? if forge {
    /*private void registerCommands(RegisterCommandsEvent event) {
        RealDamageCommon.register(event.getDispatcher());
    }
    *///?}

    //? if neoforge {
    private void registerCommands(RegisterCommandsEvent event) {
        RealDamageCommon.register(event.getDispatcher());
    }
    //?}

}
