package top.bemylover.realdamage;

//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
//?}

//? if neoforge {
/*import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?}

import top.bemylover.realdamage.common.RealDamageCommon;
import top.bemylover.realdamage.config.RealDamageConfig;
import top.bemylover.realdamage.handler.RealDamageCalculationHandler;

@Mod(RealDamage.MODID)
public class RealDamage {

    public static final String MODID = "realdamage";

    //? if forge {
    public RealDamage() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        MinecraftForge.EVENT_BUS.register(RealDamageConfig.class);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RealDamageConfig.SPEC);
        RealDamageConfig.init();
    }
    //?}

    //? if neoforge {
    /*public RealDamage(ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        modContainer.registerConfig(ModConfig.Type.COMMON, RealDamageConfig.SPEC);
    }
    *///?}

    private void registerCommands(RegisterCommandsEvent event) {
        RealDamageCommon.register(event.getDispatcher());
    }

}
