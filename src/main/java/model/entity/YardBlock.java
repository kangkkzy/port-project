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

    // 包含的堆栈
    private List<Stack> stacks = new ArrayList<>();

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