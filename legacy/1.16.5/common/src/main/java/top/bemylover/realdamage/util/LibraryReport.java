package top.bemylover.realdamage.util;

import ch.obermuhlner.math.big.BigDecimalMath;
import com.electronwill.nightconfig.core.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动期的第三方库自检。
 *
 * <p>1.16.5 上这两个库的来源完全不同：big-math 既不在 Forge 也不在 Fabric 的运行时里，
 * 只能随产物以 jar-in-jar 分发；nightconfig 则相反——Forge 1.16.5 自带 3.6.3，
 * Fabric 侧同样要随产物分发。两边共同的故障模式都是「类找不到」，而报错往往只出现在
 * 第一次真正用到的那一刻（甚至是玩家受伤的那一瞬间）。
 *
 * <p>因此这里在启动时主动摸一下两个入口类，并把它们的**实际来源**（jar 路径 / 目录）写进日志：
 * 冒烟测试据此判断「加载到的到底是产物自带的那一份，还是加载器顺手提供的那一份」。
 *
 * @author PrefersMin
 */
public final class LibraryReport {

    private static final Logger LOGGER = LoggerFactory.getLogger("RealDamage");

    private LibraryReport() {
    }

    /**
     * 打印两个第三方库的加载位置。只在启动时调用一次。
     */
    public static void log() {
        LOGGER.info("RealDamage 运行时依赖：big-math <- {}；nightconfig <- {}",
                originOf(BigDecimalMath.class), originOf(Config.class));
    }

    /**
     * 取某个类的实际加载位置。
     *
     * @param type 需要探测的类
     * @return jar 或目录的路径；拿不到时给出可读的说明，绝不抛异常
     */
    public static String originOf(Class<?> type) {
        try {
            java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                // 模块系统下没有 CodeSource 的情况是存在的，不能因此让 mod 起不来
                return type.getName() + "（无 CodeSource）";
            }
            return source.getLocation().toString();
        } catch (RuntimeException e) {
            return type.getName() + "（取来源失败：" + e + "）";
        }
    }
}
