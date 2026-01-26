package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备类型枚举
 * 对应港口内的核心物理实体类型。
 */
@Getter
@AllArgsConstructor
public enum DeviceTypeEnum {
    INTERNAL_TRUCK(1, "内部集卡"),
    EXTERNAL_TRUCK(2, "外部集卡"),
    OIL_TRUCK(3, "油集卡"),
    ELECTRIC_TRUCK(4, "电集卡"),
    ASC(5, "龙门吊"),
    QC(6, "岸桥");

    private final int code;
    private final String desc;

    /**
     * 根据 code 获取枚举对象
     */
    @SuppressWarnings("unused")
    public static DeviceTypeEnum getByCode(int code) {
        for (DeviceTypeEnum value : values()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        return null;
    }
}
