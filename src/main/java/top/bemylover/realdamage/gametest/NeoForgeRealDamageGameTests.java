package top.bemylover.realdamage.gametest;

//? if neoforge && >=1.21.11 {
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import top.bemylover.realdamage.RealDamage;
//?}

//? if neoforge && <1.21.11 {
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
 * 所以 1.21.1 起读的是 {@code data/realdamage/structure/empty.nbt}
 * （用 1.21.11 反编译出的 {@code StructureTemplateManager} 确认过，资源目录仍叫 {@code structure}）。</p>
 *
 * <h2>为什么按 1.21.11 分成两套写法</h2>
 * <p>1.21.11 的原版把注解式的 {@code net.minecraft.gametest.framework.GameTest} 整个删了，
 * 测试改成「注册进 {@code Registries.TEST_INSTANCE}」的数据驱动体系；NeoForge 也随之删掉了
 * {@code @GameTestHolder} / {@code @PrefixGameTestTemplate}，改为在 mod 总线上发
 * {@link RegisterGameTestsEvent}，由模组自己把 {@code GameTestInstance} 注册进注册表。
 * 因此新版没有注解可挂，只能手写一个 {@code GameTestInstance} 子类把
 * {@link RealDamageGameTestSupport} 的测试体包起来。</p>
 *
 * <p>{@code @EventBusSubscriber} 在 FML 10 里已经没有 {@code bus} 属性：注解处理器会按
 * 「事件是否实现 {@code IModBusEvent}」自动选择 mod 总线还是游戏总线
 * （反编译 fml 的 {@code AutomaticEventSubscriber} 核实），而这个事件正是 {@code IModBusEvent}。</p>
 */
//? if neoforge && >=1.21.11 {
@EventBusSubscriber(modid = RealDamage.MODID)
public final class NeoForgeRealDamageGameTests {

    /** 每个测试都是同步跑完的，20 tick 足够；结构体只提供一个空场地。 */
    private static final int MAX_TICKS = 20;

    private NeoForgeRealDamageGameTests() {
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        // 测试环境决定分批方式（原版按环境分组），这里注册一个空环境，所有用例落在同一批里
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(RealDamage.MODID, "default"));
        TestData<Holder<TestEnvironmentDefinition>> testData = new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath(RealDamage.MODID, "empty"),
                MAX_TICKS,
                0,
                true
        );
        register(event, testData, "real_damage_bypasses_armor", RealDamageGameTestSupport::realDamageBypassesArmor);
        register(event, testData, "vanilla_armor_reduces_damage", RealDamageGameTestSupport::vanillaArmorReducesDamage);
        register(event, testData, "invulnerable_player_takes_no_real_damage", RealDamageGameTestSupport::invulnerablePlayerTakesNoRealDamage);
        register(event, testData, "protection_period_skips_real_damage", RealDamageGameTestSupport::protectionPeriodSkipsRealDamage);
        register(event, testData, "after_protection_real_damage_applies", RealDamageGameTestSupport::afterProtectionRealDamageApplies);
    }

    private static void register(RegisterGameTestsEvent event,
                                 TestData<Holder<TestEnvironmentDefinition>> testData,
                                 String name,
                                 Consumer<GameTestHelper> body) {
        event.registerTest(Identifier.fromNamespaceAndPath(RealDamage.MODID, name),
                new SupportBackedTestInstance(body, testData));
    }

    /**
     * 把 {@link RealDamageGameTestSupport} 里的测试体包成原版要求的测试实例。
     *
     * <p>{@code codec()} 只在把注册表序列化成数据包时才被调用，gametest 服务端不会走那条路径，
     * 因此直接抛异常标明「本实例不可序列化」，而不是伪造一个会误导人的编码器；
     * {@code typeDescription()} 只用于测试报告里的类型说明，写模组名即可。</p>
     */
    private static final class SupportBackedTestInstance extends GameTestInstance {

        private final Consumer<GameTestHelper> body;

        private SupportBackedTestInstance(Consumer<GameTestHelper> body,
                                          TestData<Holder<TestEnvironmentDefinition>> testData) {
            super(testData);
            this.body = body;
        }

        @Override
        public void run(GameTestHelper helper) {
            body.accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            throw new UnsupportedOperationException("RealDamage 的测试实例只在运行时注册，不支持序列化");
        }

        @Override
        public MutableComponent typeDescription() {
            return Component.literal("realdamage");
        }
    }
}
//?}
//? if neoforge && <1.21.11 {
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
