package model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岸桥 (QC) 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QcDevice extends BaseDevice {
    private String currentBlockBay; // 当前位置
    private String targetBlockBay;  // 目标移动位置
    private Double hoistSpeed;      // 吊箱速度
}