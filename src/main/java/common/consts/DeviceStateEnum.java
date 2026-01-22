package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备状态枚举
 */
@Getter
@AllArgsConstructor
public enum DeviceStateEnum {
    // 通用状态
    OFFLINE("02", "退出/离线"),
    IDLE("03", "空闲"),
    WORKING("04", "工作"),
    FAULT("05", "故障"),

    // 集卡特有
    CHARGING("01", "充电"),

    // 工作子状态 (用于细分 WORKING)
    MOVING("041", "移动"),
    WAITING("042", "等待");

    private final String code;
    private final String desc;

    /**
     * 根据 code 获取枚举对象
     * @param code 状态码
     * @return 对应的枚举对象 若未找到返回 null
     */
    public static DeviceStateEnum getByCode(String code) {
        for (DeviceStateEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}