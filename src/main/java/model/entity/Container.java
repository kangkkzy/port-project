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
    private String containerId;   // 箱号

    private String equipType;     // 箱型
    private String sizeType;      // 尺寸类型 (20/40尺)
    private Double totalWeight;   // 箱重
    private String owner;         // 对应船只

    private Integer nTEUs;        // 吞吐量 (标准箱单位)
    private String departRef;     // 离港方式 (如 VESSEL, TRUCK, RAIL)
    private String arriveRef;     // 进港方式

    private String currentPos;    // 当前位置信息
    private String status;        // 集装箱状态 (01即将作业/02不可作业)
}