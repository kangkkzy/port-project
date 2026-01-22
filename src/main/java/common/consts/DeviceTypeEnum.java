package common.consts;

/**
 * 设备类型枚举
 */
public enum DeviceTypeEnum {
    INTERNAL_TRUCK(1, "内部集卡"),
    EXTERNAL_TRUCK(2, "外部集卡"),
    OIL_TRUCK(3, "油集卡"),
    ELECTRIC_TRUCK(4, "电集卡"),
    ASC(5, "龙门吊"),
    QC(6, "岸桥");

    private final int code;
    private final String desc;

    DeviceTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
