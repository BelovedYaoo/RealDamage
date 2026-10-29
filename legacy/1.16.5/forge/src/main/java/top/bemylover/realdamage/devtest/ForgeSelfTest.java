package top.bemylover.realdamage.devtest;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

/**
 * Forge 侧的自检入口：服务端启动完成后同步跑一遍数值断言。
 *
 * <p>为什么挂在 {@code FMLServerStartedEvent}：这一刻所有注册（命令、事件总线）都已经完成，
 * 世界也已经建好，因此被验证的是生产路径本身。1.16.5 的事件类还在
 * {@code net.minecraftforge.fml.event.server} 包下（改到 {@code net.minecraftforge.event.server}
 * 是 1.17 之后的事，已按 36.2.42 的产物核实）。</p>
 *
 * <p>只在系统属性 {@code realdamage.selftest=1} 时执行，且本类被排除在产物之外。</p>
 */
@Mod.EventBusSubscriber(modid = "realdamage", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeSelfTest {

    private ForgeSelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(FMLServerStartedEvent event) {
        if (!"1".equals(System.getProperty("realdamage.selftest"))) {
            return;
        }
        RealDamageSelfTest.run(event.getServer(), new ForgeTestSubject(event.getServer()));
    }
}
