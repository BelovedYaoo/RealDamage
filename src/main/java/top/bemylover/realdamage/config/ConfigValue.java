package top.bemylover.realdamage.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 一个配置项：点分路径 + 默认值 + 注释，读取时按需做类型校验与范围收敛。
 *
 * <p>这里不再用 ForgeConfigSpec / ModConfigSpec 的值对象：那两者分属 Forge 与 NeoForge，
 * 会把配置类在源码层面绑死到具体加载器，Fabric 上无从复用；而 nightconfig 是 Forge/NeoForge
 * 自带的底层库，Fabric 侧把它打进产物即可，三个加载器因此能共用同一份配置实现。</p>
 *
 * @author PrefersMin
 */
public abstract class ConfigValue<T> {

    protected static final Logger LOGGER = LoggerFactory.getLogger("RealDamage");

    // 点分路径，例如 RealDamage.Maximum.realDamageMax；保持与旧版 ForgeConfigSpec 生成的文件一致
    private final String path;

    // 中文注释原样保留旧常量中的文本，保证升级后配置文件仍然可读
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
        String normalizedComment = comment.stripLeading();
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
}
