package top.prefersmin.realdamage;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import top.prefersmin.realdamage.common.RealDamageCommon;
import top.prefersmin.realdamage.config.RealDamageConfig;
import top.prefersmin.realdamage.handler.RealDamageCalculationHandler;

@Mod(RealDamage.MODID)
public class RealDamage {

    public static final String MODID = "realdamage";

    public RealDamage() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(RealDamageCalculationHandler.class);
        MinecraftForge.EVENT_BUS.register(RealDamageConfig.class);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RealDamageConfig.SPEC);
        RealDamageConfig.init();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        RealDamageCommon.register(event.getDispatcher());
    }

}
