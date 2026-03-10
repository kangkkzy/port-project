package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轨迹片段 - 表示设备在特定时间段内的直线运动
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrajectorySegment {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 起始 X 坐标
     */
    private double startX;

    /**
     * 起始 Y 坐标
     */
    private double startY;

    /**
     * 终止 X 坐标
     */
    private double endX;

    /**
     * 终止 Y 坐标
     */
    private double endY;

    /**
     * 起始时间（仿真时间戳，毫秒）
     */
    private long startTime;

    /**
     * 终止时间（仿真时间戳，毫秒）
     */
    private long endTime;
}
