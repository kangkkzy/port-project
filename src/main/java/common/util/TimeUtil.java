package common.util;

/**
 * 仿真时间与系统时间转换等工具（预留）
 * 当前离散仿真以毫秒时间戳为主，如需挂接真实时间或回放可在此扩展
 */
public final class TimeUtil {

    private TimeUtil() {}

    /** 预留：仿真时间戳转可读时间 */
    @SuppressWarnings("unused")
    public static String formatSimTime(long simTimeMs) {
        long sec = simTimeMs / 1000;
        long ms = simTimeMs % 1000;
        return sec + "." + String.format("%03d", ms) + "s";
    }
}
