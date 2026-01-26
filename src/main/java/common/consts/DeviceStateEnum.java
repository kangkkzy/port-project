package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备状态枚举
 * 对应《字段设计 状态设计.pdf》中对实体状态的定义
 */
@Getter
@AllArgsConstructor
public enum DeviceStateEnum {
    // 基础状态
    CHARGING("01", "充电 (仅集卡)"),
    OFFLINE("02", "退出/离线"),
    IDLE("03", "空闲"),
    WORKING("04", "工作"),
    FAULT("05", "故障"),

    // 工作子状态
    MOVING("041", "移动"),
    WAITING("042", "等待"),

    // 龙门吊/桥吊特有子状态
    MOVE_HORIZONTAL("0421", "横向移动 (龙门吊/桥吊)"),
    MOVE_VERTICAL("0422", "垂直移动 (龙门吊/桥吊)");

    private final String code;
    private final String desc;

    /**
     * 根据 code 获取枚举对象
     * * @param code 状态码
     * @return 对应的枚举对象 若未找到返回 null
     */
    @SuppressWarnings("unused")
    public static DeviceStateEnum getByCode(String code) {
        for (DeviceStateEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}