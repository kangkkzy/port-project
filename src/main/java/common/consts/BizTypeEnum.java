package common.consts;
/*
 * 业务类型枚举
 */
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BizTypeEnum {
    /**
     * 卸船：将靠泊的船上的箱子卸到码头内
     */
    DSCH("DSCH", "卸船 (Vessel -> Yard)"),

    /**
     * 装船：将码头内的箱子运送到靠泊的船上
     */
    LOAD("LOAD", "装船 (Yard -> Vessel)"),

    /**
     * 移箱：在码头内部移动箱子的位置
     */
    YARD_SHIFT("YARD", "移箱 (Yard -> Yard)"),

    /**
     * 提箱：外集卡将码头内部的箱子运输到码头外
     */
    DLVR("DLVR", "提箱 (Yard -> Gate)"),

    /**
     * 进箱：外集卡将码头外部的箱子运输到码头内
     */
    RECV("RECV", "收箱 (Gate -> Yard)"),

    /**
     * 直进：外集卡将码头外部的箱子直接运送到靠泊的船上
     */
    DIRECT_IN("DIRECT_IN", "直进 (Gate -> Vessel)"),

    /**
     * 直提：外集卡将靠泊的船上的箱子直接运送到码头外
     */
    DIRECT_OUT("DIRECT_OUT", "直提 (Vessel -> Gate)");

    private final String code;
    private final String desc;
}