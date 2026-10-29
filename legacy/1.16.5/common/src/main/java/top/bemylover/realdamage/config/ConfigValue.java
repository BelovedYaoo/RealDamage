package top.bemylover.realdamage.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * 一个配置项：点分路径 + 默认值 + 注释，读取时按需做类型校验与范围收敛。
 *
 * <p>为什么用自研 nightconfig 实现而不是 ForgeConfigSpec：1.16.5 时代 Forge 的 ForgeConfigSpec
 * 与 Fabric 侧的「无配置 API」无法共用一份实现；而 nightconfig 恰好是 Forge 1.16.5 运行时自带的
 * 底层库（userdev config.json 的 libraries 里写明 core/toml 3.6.3），Fabric 侧把它打进产物即可，
 * 两个加载器因此能共用同一份代码与同一份配置文件。</p>
 *
 * @author PrefersMin
 */
public abstract class ConfigValue<T> {

    // 日志统一用 log4j2 而不是 slf4j：1.16.5 的原版只带 log4j2（Forge 侧 2.15.0、
    // Fabric 侧 2.8.1 来自 MC 自身的 libraries），Fabric 1.16.5 的运行时里没有 slf4j-api，
    // 用了会在第一次打日志时 NoClassDefFoundError。
    protected static final Logger LOGGER = LogManager.getLogger("RealDamage");

    // 点分路径，例如 RealDamage.Maximum.realDamageMax；保持与 main 分支生成的配置文件一致，
    // 老配置文件可以直接沿用
    private final String path;

    // 中文注释原样保留，保证升级后配置文件仍然可读
    private final String comment;

    private final T defaultValue;

    private T value;

    protected ConfigValue(String path, T defaultValue, String comment) {
        this.path = path;
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String path() {
        return path;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    /**
     * 从已解析的配置文件里读取本项并更新内存值。
     *
     * @return 是否需要把配置写回磁盘（缺项、类型不符、越界收敛都算，保证文件始终自解释）
     */
    final boolean load(CommentedConfig config) {
        Object raw = config.get(path);
        T parsed = raw == null ? null : parse(raw);
        if (parsed == null) {
            // 缺项或类型不符都回落到默认值。手改坏的配置只该被修正，不该让服务器起不来
            if (raw != null) {
                LOGGER.warn("配置项 {} 的值 \"{}\" 无法解析，已改用默认值 {}", path, raw, defaultValue);
            }
            parsed = defaultValue;
        }
        boolean changed = raw == null || !sameAsRaw(parsed, raw);
        this.value = parsed;
        config.set(path, parsed);
        // 注释常量沿用 ForgeConfigSpec 时代的写法（以 \n 开头，那是它用来在注释前留空行的约定）。
        // nightconfig 会把这个 \n 原样渲染成一个孤零零的 "#"，所以写盘前去掉行首空行；中文文本不变。
        // 3.6.3 的 setComment 返回旧注释，Java 8 没有 String#stripLeading，这里自己裁。
        String normalizedComment = stripLeading(comment);
        String previousComment = config.setComment(path, normalizedComment);
        return changed || !Objects.equals(previousComment, normalizedComment);
    }

    /**
     * 把文件里的原始值解析成配置值；无法解析时返回 null，由 {@link #load} 回落默认值。
     */
    protected abstract T parse(Object raw);

    /**
     * 解析后的值与文件里的原始值是否等价。
     *
     * <p>TOML 的数字可能被解析成 Integer 也可能被解析成 Long，直接用 equals 比较会把
     * 「值其实没变」误判成「值变了」，于是每次启动都重写一次配置文件。</p>
     */
    protected boolean sameAsRaw(T parsed, Object raw) {
        return Objects.equals(parsed, raw);
    }

    /**
     * 去掉行首空白（等价于 Java 11 的 {@code String#stripLeading}）。
     *
     * <p>1.16.5 跑在 Java 8 上，只要有一个方法用到更高版本的 API，产物在真实环境里就会
     * 抛 NoSuchMethodError，而开发机上的 JDK 17/21 什么都看不出来，所以这里手写。</p>
     */
    static String stripLeading(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.substring(start);
    }
}
