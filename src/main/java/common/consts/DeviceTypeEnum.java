package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备类型枚举
 */
@Getter
@AllArgsConstructor
public enum DeviceTypeEnum {
    //  在这里统一管理各类设备的默认移动速度
    INTERNAL_TRUCK(1, "内部集卡", 8.0),   // 约 28 km/h
    EXTERNAL_TRUCK(2, "外部集卡", 10.0),  // 约 36 km/h
    OIL_TRUCK(3, "油集卡", 8.0),
    ELECTRIC_TRUCK(4, "电集卡", 8.0),
    ASC(5, "龙门吊", 2.0),                // 大机设备移动较慢
    QC(6, "岸桥", 1.0);                   // 岸桥移动最慢

    private final int code;
    private final String desc;
    private final double defaultSpeed; //  该类型设备的默认物理速度

    public static DeviceTypeEnum getByCode(int code) {
        for (DeviceTypeEnum value : values()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        return null;
    }
}
