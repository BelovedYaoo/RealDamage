package top.bemylover.realdamage.gametest;

//? if forge {
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
//?}

/**
 * Forge 侧的 GameTest 套件。
 *
 * <p>这个类只负责把注解挂对，测试体全在 {@link RealDamageGameTestSupport} 里，
 * 保证三个加载器跑的是同一份数值断言。</p>
 *
 * <p>结构体名用 {@code realdamage:empty}（资源见
 * {@code data/realdamage/structures/empty.nbt}）：Forge 的 {@code @GameTest} 由
 * {@code templateNamespace} + {@code template} 拼出完整结构 id。测试本身不关心方块，
 * 只是需要一个可放置的空测试场地。</p>
 */
//? if forge {
@GameTestHolder("realdamage")
// 关掉「用类名给结构体名加前缀」：默认为 true 时结构体名会变成
// realdamage:forgerealdamagegametests.empty，与资源文件对不上
@PrefixGameTestTemplate(false)
public class ForgeRealDamageGameTests {

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
//?}
