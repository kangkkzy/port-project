package common.consts;

/**
 * 位置类型抽象：用于替代直接在业务代码中判断 "BAY"/"YARD" 等魔法字符串
 */
public enum PositionType {

    VESSEL,
    YARD,
    UNKNOWN;

    public static PositionType fromCode(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        if (raw.startsWith("BAY")) {
            return VESSEL;
        }
        if (raw.startsWith("YARD")) {
            return YARD;
        }
        return UNKNOWN;
    }

    public boolean isVessel() {
        return this == VESSEL;
    }

    public boolean isYard() {
        return this == YARD;
    }
}
