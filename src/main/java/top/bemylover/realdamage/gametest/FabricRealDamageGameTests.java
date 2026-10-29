package top.bemylover.realdamage.gametest;

//? if fabric && >=1.21.11 {
/*import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
*///?}

//? if fabric && <1.21.11 {
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
 *       {@code fabric-gametest} 入口点。这不是可选项——模块会按入口点实例化这个类再扫描
 *       其方法上的 {@code @GameTest}，没登记就一个测试也注册不上，所以这个类会随正式产物
 *       一起打包（见 build.gradle.kts 的说明）。</li>
 *   <li>结构体交给框架的默认空结构：旧版用 {@code FabricGameTest.EMPTY_STRUCTURE}，
 *       新版注解默认就是 {@code fabric-gametest-api-v1:empty}（8x8 空场地），
 *       不需要自己提供 .nbt；另外两个加载器则要用本模组资源里的 {@code realdamage:empty}。</li>
 * </ul>
 *
 * <h2>为什么按 1.21.11 分成两套写法</h2>
 * <p>fabric-gametest-api-v1 在 3.x 里换掉了整套注解：{@code FabricGameTest} 接口被删除，
 * 改用模块自带的 {@link GameTest}（注意不是原版那个同名注解），并且要求测试方法是
 * <b>public 非 static</b>、返回 void、恰好一个 {@link GameTestHelper} 参数。
 * 1.21.1 上的 2.x 则是「原版 {@code @GameTest} + {@code FabricGameTest.EMPTY_STRUCTURE}」，
 * 两者无法用同一份代码兼顾，因此按 MC 版本各写一份（模块版本与 MC 版本在本仓库里一一对应）。</p>
 *
 * <p>测试体与另外两个加载器共用 {@link RealDamageGameTestSupport}，
 * 因此「Fabric 侧数值与 Forge/NeoForge 一致」本身就是被测的结论。</p>
 */
//? if fabric && >=1.21.11 {
/*public class FabricRealDamageGameTests {

    @GameTest
    public void realDamageBypassesArmor(GameTestHelper helper) {
        RealDamageGameTestSupport.realDamageBypassesArmor(helper);
    }

    @GameTest
    public void vanillaArmorReducesDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.vanillaArmorReducesDamage(helper);
    }

    @GameTest
    public void invulnerablePlayerTakesNoRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.invulnerablePlayerTakesNoRealDamage(helper);
    }

    @GameTest
    public void protectionPeriodSkipsRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.protectionPeriodSkipsRealDamage(helper);
    }

    @GameTest
    public void afterProtectionRealDamageApplies(GameTestHelper helper) {
        RealDamageGameTestSupport.afterProtectionRealDamageApplies(helper);
    }
}
*///?}
//? if fabric && <1.21.11 {
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
