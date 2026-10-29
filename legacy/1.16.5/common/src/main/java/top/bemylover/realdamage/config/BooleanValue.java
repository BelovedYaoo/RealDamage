package top.bemylover.realdamage.config;

/**
 * 布尔配置项。
 *
 * @author PrefersMin
 */
public final class BooleanValue extends ConfigValue<Boolean> {

    BooleanValue(String path, boolean defaultValue, String comment) {
        super(path, defaultValue, comment);
    }

    @Override
    protected Boolean parse(Object raw) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        // 手写成带引号的 "true"/"false" 也尽量沿用，减少「改了配置却像没生效」的困惑
        if (raw instanceof String) {
            String text = (String) raw;
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
