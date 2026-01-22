package common.consts;

/**
 * 设备状态枚举
 */
public enum DeviceStateEnum {
    // 通用状态
    OFFLINE("02", "离线"),
    IDLE("03", "空闲"),
    WORKING("04", "工作"),
    FAULT("05", "故障"),

    // 电集卡特有
    CHARGING("01", "充电"),

    // 工作子状态
    MOVING("041", "移动"),
    WAITING("042", "等待");

    private final String code;
    private final String desc;

    DeviceStateEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }
}