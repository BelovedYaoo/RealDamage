package top.bemylover.realdamage.gametest;

//? if fabric {
/*import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
*///?}

/**
 * Fabric 侧的 GameTest 套件。
 *
 * <p>Fabric 与另外两个加载器的差别不止注解：</p>
 * <ul>
 *   <li>Fabric 没有 {@code @GameTestHolder}，测试类必须写进 {@code fabric.mod.json} 的
 *       {@code fabric-gametest} 入口点。这不是可选项——fabric-gametest-api-v1 的
 *       {@code TestFunctionsMixin} 会按「声明类」反查入口点登记的 mod id，
 *       没登记就直接抛异常，所以这个类会随正式产物一起打包（见 build.gradle.kts 的说明）。</li>
 *   <li>结构体用 fabric-api 自带的 {@link FabricGameTest#EMPTY_STRUCTURE}，
 *       不需要自己提供 .nbt；另外两个加载器则要用本模组资源里的 {@code realdamage:empty}。</li>
 * </ul>
 *
 * <p>测试体与另外两个加载器共用 {@link RealDamageGameTestSupport}，
 * 因此「Fabric 侧数值与 Forge/NeoForge 一致」本身就是被测的结论。</p>
 */
//? if fabric {
/*public class FabricRealDamageGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void realDamageBypassesArmor(GameTestHelper helper) {
        RealDamageGameTestSupport.realDamageBypassesArmor(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void vanillaArmorReducesDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.vanillaArmorReducesDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void invulnerablePlayerTakesNoRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.invulnerablePlayerTakesNoRealDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void protectionPeriodSkipsRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.protectionPeriodSkipsRealDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void afterProtectionRealDamageApplies(GameTestHelper helper) {
        RealDamageGameTestSupport.afterProtectionRealDamageApplies(helper);
    }
}
*///?}
