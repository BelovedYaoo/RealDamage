package top.bemylover.realdamage.config;

/**
 * 带上下界的整数配置项，等价于 ForgeConfigSpec 的 {@code defineInRange}。
 *
 * <p>越界时的处理是「收敛到最近的边界并告警」，而不是抛出异常：
 * 旧版 ForgeConfigSpec 在加载时同样会把越界值改为边界值，这里保持一致，
 * 免得有人手改配置后服务器直接启动失败。</p>
 *
 * @author PrefersMin
 */
public final class IntValue extends ConfigValue<Integer> {

    private final int min;
    private final int max;

    IntValue(String path, int defaultValue, int min, int max, String comment) {
        super(path, defaultValue, comment);
        this.min = min;
        this.max = max;
    }

    @Override
    protected Integer parse(Object raw) {
        // TOML 整数按大小可能是 Integer 或 Long，统一按 Number 取整数值
        if (!(raw instanceof Number)) {
            return null;
        }
        int rawValue = ((Number) raw).intValue();
        if (rawValue < min || rawValue > max) {
            int clamped = Math.min(max, Math.max(min, rawValue));
            LOGGER.warn("配置项 {} 的值 {} 超出范围 [{}, {}]，已收敛为 {}", path(), rawValue, min, max, clamped);
            return clamped;
        }
        return rawValue;
    }

    @Override
    protected boolean sameAsRaw(Integer parsed, Object raw) {
        return raw instanceof Number && ((Number) raw).intValue() == parsed;
    }
}
