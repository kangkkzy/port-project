package common.consts;
/**
 * 业务类型枚举
 */
public enum BizTypeEnum {
    DSCH("DSCH", "卸船 (Vessel -> Yard)"),
    LOAD("LOAD", "装船 (Yard -> Vessel)"),
    YARD_SHIFT("YARD", "移箱 (Yard -> Yard)"),
    DLVR("DLVR", "提箱 (Yard -> Gate)"),
    RECV("RECV", "收箱 (Gate -> Yard)");

    private final String code;
    private final String desc;

    BizTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}