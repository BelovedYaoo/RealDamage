package top.bemylover.realdamage.gametest;

//? if neoforge {
/*import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
*///?}

/**
 * NeoForge 侧的 GameTest 套件。
 *
 * <p>与 Forge 侧的差别只有 {@code @GameTestHolder} 的包名（NeoForge 把它挪到了
 * {@code net.neoforged.neoforge.gametest}），测试体同样共用
 * {@link RealDamageGameTestSupport}。</p>
 *
 * <p>结构体路径在 1.21 变了：数据包目录由 {@code structures/} 改名为 {@code structure/}，
 * 所以 1.21.1 读的是 {@code data/realdamage/structure/empty.nbt}。</p>
 */
//? if neoforge {
/*@GameTestHolder("realdamage")
// 同 Forge：默认为 true 时结构体名会被加上类名前缀，与资源文件对不上
@PrefixGameTestTemplate(false)
public class NeoForgeRealDamageGameTests {

    @GameTest(templateNamespace = "realdamage", template = "empty")
    public static void realDamageBypassesArmor(GameTestHelper helper) {
        RealDamageGameTestSupport.realDamageBypassesArmor(helper);
    }

    @GameTest(templateNamespace = "realdamage", template = "empty")
    public static void vanillaArmorReducesDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.vanillaArmorReducesDamage(helper);
    }

    @GameTest(templateNamespace = "realdamage", template = "empty")
    public static void invulnerablePlayerTakesNoRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.invulnerablePlayerTakesNoRealDamage(helper);
    }

    @GameTest(templateNamespace = "realdamage", template = "empty")
    public static void protectionPeriodSkipsRealDamage(GameTestHelper helper) {
        RealDamageGameTestSupport.protectionPeriodSkipsRealDamage(helper);
    }

    @GameTest(templateNamespace = "realdamage", template = "empty")
    public static void afterProtectionRealDamageApplies(GameTestHelper helper) {
        RealDamageGameTestSupport.afterProtectionRealDamageApplies(helper);
    }
}
*///?}
