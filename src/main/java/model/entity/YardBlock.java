package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 堆场箱区
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YardBlock {
    private String blockCode;     // 箱区代码
    private String blockType;     // 箱区类型
    private Integer maxTier;      // 该箱区最大允许堆叠层数

    // 坐标与范围
    private Double invertX;       // 基准X坐标
    private Double invertY;       // 基准Y坐标
    private Integer firstRow;     // 起始排号
    private Integer lastRow;      // 结束排号
    private Integer firstBay;     // 起始贝位
    private Integer lastBay;      // 结束贝位
    private Integer firstTier;    // 起始层
    private Integer lastTier;     // 结束层

    // 包含的堆栈
    private List<Stack> stacks = new ArrayList<>();

    /**
     * 三维数组：真实存储箱位数据
     * dimensions: [bay][row][tier]
     * 用于快速基于位置的箱子查询和更新
     */
    private Container[][][] slots;

    /**
     * 初始化三维箱位数组
     * 根据 firstBay, lastBay, firstRow, lastRow, firstTier, lastTier 创建数组
     */
    public void initSlots() {
        if (firstBay == null || lastBay == null || firstRow == null || lastRow == null ||
                firstTier == null || lastTier == null) {
            return;
        }
        int bayCount = lastBay - firstBay + 1;
        int rowCount = lastRow - firstRow + 1;
        int tierCount = lastTier - firstTier + 1;

        this.slots = new Container[bayCount][rowCount][tierCount];
    }

    /**
     * 根据贝位、排号、层号获取箱子
     * @param bay 贝位号
     * @param row 排号
     * @param tier 层号
     * @return 箱子对象，如果没有则返回 null
     */
    public Container getContainer(int bay, int row, int tier) {
        if (slots == null) return null;
        int bayIdx = bay - firstBay;
        int rowIdx = row - firstRow;
        int tierIdx = tier - firstTier;

        if (bayIdx < 0 || bayIdx >= slots.length ||
                rowIdx < 0 || rowIdx >= slots[0].length ||
                tierIdx < 0 || tierIdx >= slots[0][0].length) {
            return null;
        }
        return slots[bayIdx][rowIdx][tierIdx];
    }

    /**
     * 放置箱子到指定位置
     * @param bay 贝位号
     * @param row 排号
     * @param tier 层号
     * @param container 箱子对象
     * @return 是否放置成功
     */
    public boolean putContainer(int bay, int row, int tier, Container container) {
        if (slots == null) initSlots();
        int bayIdx = bay - firstBay;
        int rowIdx = row - firstRow;
        int tierIdx = tier - firstTier;

        if (bayIdx < 0 || bayIdx >= slots.length ||
                rowIdx < 0 || rowIdx >= slots[0].length ||
                tierIdx < 0 || tierIdx >= slots[0][0].length) {
            return false;
        }
        // 检查目标位置是否为空
        if (slots[bayIdx][rowIdx][tierIdx] != null) {
            return false;
        }
        slots[bayIdx][rowIdx][tierIdx] = container;
        return true;
    }

    /**
     * 从指定位置移除箱子
     * @param bay 贝位号
     * @param row 排号
     * @param tier 层号
     * @return 移除的箱子对象，如果没有则返回 null
     */
    public Container removeContainer(int bay, int row, int tier) {
        if (slots == null) return null;
        int bayIdx = bay - firstBay;
        int rowIdx = row - firstRow;
        int tierIdx = tier - firstTier;

        if (bayIdx < 0 || bayIdx >= slots.length ||
                rowIdx < 0 || rowIdx >= slots[0].length ||
                tierIdx < 0 || tierIdx >= slots[0][0].length) {
            return null;
        }
        Container removed = slots[bayIdx][rowIdx][tierIdx];
        slots[bayIdx][rowIdx][tierIdx] = null;
        return removed;
    }

    /**
     * 检查指定位置上方是否有遮挡（用于验证是否可以抓取下层箱子）
     * @param bay 贝位号
     * @param row 排号
     * @param tier 层号
     * @return true 表示上方有遮挡
     */
    public boolean hasObstructionAbove(int bay, int row, int tier) {
        if (slots == null) return false;
        int bayIdx = bay - firstBay;
        int rowIdx = row - firstRow;
        int tierIdx = tier - firstTier;

        if (bayIdx < 0 || bayIdx >= slots.length ||
                rowIdx < 0 || rowIdx >= slots[0].length ||
                tierIdx < 0 || tierIdx >= slots[0][0].length) {
            return false;
        }
        // 检查上层是否有箱子
        for (int t = tierIdx + 1; t < slots[0][0].length; t++) {
            if (slots[bayIdx][rowIdx][t] != null) {
                return true;
            }
        }
        return false;
    }

    @Data
    @NoArgsConstructor
    public static class Stack {
        private Integer row;          // 贝位
        private Integer column;       // 列
        private Integer maxTier;      // 该特定堆栈的最大层高

        // 堆场约束
        private String protectStatus; // 进/提箱工作状态控制作业优先级 (F:优先提箱, C:禁止提箱)
        private String stackStatus;   // 堆场约束 (R:道路, B:建筑物, X:临时封闭等)

        // 包含的箱位
        private List<Slot> slots = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Slot {
        private Integer tier;         // 层号
        private boolean hasContainer; // 是否有箱子
        private String currentContainerId; // 当前箱号
        private String futureContainerId;  // 计划放置的箱号

        // 混堆约束标记
        private int bmUsingFlag;

        // 常量：集装箱尺寸
        private static final int SIZE_20_FT = 20;
        private static final int SIZE_40_FT = 40;

        // 常量：位掩码状态
        private static final int MASK_EMPTY_ONLY_20 = 0x00; // 00000000
        private static final int MASK_EMPTY_ONLY_40 = 0x01; // 00000001
        private static final int MASK_EMPTY_BOTH    = 0x02; // 00000010

        /**
         * 校验当前箱位是否允许放置指定尺寸的箱子
         */
        public boolean canPlace(int size) {
            if (size == SIZE_20_FT) {
                return bmUsingFlag == MASK_EMPTY_ONLY_20 || bmUsingFlag == MASK_EMPTY_BOTH;
            } else if (size == SIZE_40_FT) {
                return bmUsingFlag == MASK_EMPTY_ONLY_40 || bmUsingFlag == MASK_EMPTY_BOTH;
            }
            return false;
        }
    }
}