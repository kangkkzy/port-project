package model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * 龙门吊 (ASC) 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AscDevice extends BaseDevice {
    private String currentBlockBay;     // 当前位置
    private String targetBlockBay;      // 目标移动位置
    private List<String> enabledRangeList; // 负责的堆区

    // 垂直起升速度 (米/秒)
    private Double hoistSpeed;
}