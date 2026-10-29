package top.bemylover.realdamage.devtest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric 侧的自检入口：服务端启动完成后同步跑一遍数值断言。
 *
 * <p>为什么挂在 {@code ServerLifecycleEvents.SERVER_STARTED}：这一刻所有注册（命令、事件）
 * 都已经完成，世界也已经建好，因此被验证的是生产路径本身。
 * 1.16.5 的 Fabric API 里这个事件在 {@code net.fabricmc.fabric.api.event.lifecycle.v1} 下，
 * 回调签名是 {@code (MinecraftServer)}（已按 fabric-lifecycle-events-v1 1.2.2 的产物核实）。</p>
 *
 * <p>只在系统属性 {@code realdamage.selftest=1} 时执行，且本类被排除在产物之外。</p>
 */
public final class FabricSelfTest {

    private FabricSelfTest() {
    }

    /**
     * 在入口点里注册（Fabric 没有事件总线的自动扫描，必须显式挂）。
     */
    public static void register() {
        if (!"1".equals(System.getProperty("realdamage.selftest"))) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                RealDamageSelfTest.run(server, new FabricTestSubject(server)));
    }
}
