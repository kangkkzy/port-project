package model.entity;

import lombok.Data;
import java.util.List;

    /**
     * 堆场箱区
     */
    @Data
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
        private List<Stack> stacks;

        @Data
        public static class Stack {
            private Integer row;          // 贝位
            private Integer column;       // 列
            private Integer maxTier;      // 该特定堆栈的最大层高
            // 包含的箱位
            private List<Slot> slots;
        }

        @Data
        public static class Slot {
            private Integer tier;         // 层号
            private boolean hasContainer; // 是否有箱子
            private String containerId;   // 箱号
        }
    }