package common.consts;

/**
 * 全局错误信息与错误码常量池
 */
public class ErrorCodes {
    // 基础错误
    public static final String SYSTEM_ERROR = "系统内部错误";

    // 设备错误
    public static final String TRUCK_NOT_FOUND = "指定的集卡不存在";
    public static final String FENCE_NOT_FOUND = "指定的栅栏不存在";

    //  参数错误
    public static final String INVALID_FENCE_STATUS = "非法的栅栏状态码，仅支持 01(锁死) 或 02(通行)";
}
