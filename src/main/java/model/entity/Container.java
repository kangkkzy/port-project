package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
    /**
     * 集装箱实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class Container {
        private String id;          // 箱号
        private String sizeType;    // 尺寸类型
        private Double weight;      // 重量
        private String owner;       // 所属船舶
    }
