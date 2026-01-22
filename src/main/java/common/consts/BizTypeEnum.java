package common.consts;
/*
 * 业务类型枚举
 */
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BizTypeEnum {
    DSCH("DSCH", "卸船 (Vessel -> Yard)"),
    LOAD("LOAD", "装船 (Yard -> Vessel)"),
    YARD_SHIFT("YARD", "移箱 (Yard -> Yard)"),
    DLVR("DLVR", "提箱 (Yard -> Gate)"),
    RECV("RECV", "收箱 (Gate -> Yard)");

    private final String code;
    private final String desc;
}