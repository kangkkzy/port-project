package model.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 堆场箱区实体
 * 结构层级: YardBlock -> Stack -> Slot
 */
public class YardBlock {
    // 基础属性
    private String blockCode;     // 箱区代码
    private String blockType;     // 箱区类型
    private Integer maxTier;      // 该箱区最大允许堆叠层数

    // 物理坐标和堆区范围
    private Double invertX;       // 基准X坐标
    private Double invertY;       // 基准Y坐标
    private Integer firstRow;     // 起始排号
    private Integer lastRow;      // 结束排号

    //构造函数
    private List<Stack> stacks = new ArrayList<>();

    // 无参构造
    public YardBlock() {
    }

    //  Getter / Setter
    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getBlockType() { return blockType; }
    public void setBlockType(String blockType) { this.blockType = blockType; }

    public Integer getMaxTier() { return maxTier; }
    public void setMaxTier(Integer maxTier) { this.maxTier = maxTier; }

    public Double getInvertX() { return invertX; }
    public void setInvertX(Double invertX) { this.invertX = invertX; }

    public Double getInvertY() { return invertY; }
    public void setInvertY(Double invertY) { this.invertY = invertY; }

    public Integer getFirstRow() { return firstRow; }
    public void setFirstRow(Integer firstRow) { this.firstRow = firstRow; }

    public Integer getLastRow() { return lastRow; }
    public void setLastRow(Integer lastRow) { this.lastRow = lastRow; }

    public List<Stack> getStacks() { return stacks; }
    public void setStacks(List<Stack> stacks) { this.stacks = stacks; }

    // 堆栈
    public static class Stack {
        private Integer row;          // 贝位
        private Integer column;       // 列
        private Integer maxTier;      // 该特定堆栈的最大层高

        // 包含的箱位列表
        private List<Slot> slots = new ArrayList<>();

        public Stack() {}

        public Integer getRow() { return row; }
        public void setRow(Integer row) { this.row = row; }

        public Integer getColumn() { return column; }
        public void setColumn(Integer column) { this.column = column; }

        public Integer getMaxTier() { return maxTier; }
        public void setMaxTier(Integer maxTier) { this.maxTier = maxTier; }

        public List<Slot> getSlots() { return slots; }
        public void setSlots(List<Slot> slots) { this.slots = slots; }
    }

    // 箱位
    public static class Slot {
        private Integer tier;          // 层号
        private boolean hasContainer;  // 是否有箱
        private String containerId;    // 集装箱号

        public Slot() {}

        public Integer getTier() { return tier; }
        public void setTier(Integer tier) { this.tier = tier; }

        public boolean isHasContainer() { return hasContainer; }
        public void setHasContainer(boolean hasContainer) { this.hasContainer = hasContainer; }

        public String getContainerId() { return containerId; }
        public void setContainerId(String containerId) { this.containerId = containerId; }
    }

}