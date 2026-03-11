package model.dto.request;

import lombok.Data;
import model.entity.Point;

import java.util.List;

/**
 * 集卡移动指令请求
 *
 * 设计原则（DES架构）：
 * - 外部算法负责"算路"，生成符合路网拓扑的轨迹点序列
 * - 引擎作为执行器，负责物理约束校验和时间推演
 *
 * 两种使用模式：
 * 1. 简化模式：只设置 targetPoint（端点），引擎自动计算关键点
 * 2. 精确模式：设置 pathPoints（轨迹点列表），引擎按序执行
 */
@Data
public class MoveCommandReq {
    private String truckId;                      // 控制哪辆车
    private Point targetPoint;                   // 单次移动的最终目标点 (端点)
    private Double speed;                         // 外部算法指定的本次移动速度

    /**
     * 轨迹点列表（精确模式）
     * 外部算法应按照 map-config.json 的路网拓扑，手动拼接折线转折点
     * 例如：从(200,140)走到路口(200,200)，再走到(175,200)
     *
     * 注意：这些点必须位于合法的 TRUCK_ROAD 上，引擎会进行路径合法性校验
     */
    private List<Point> pathPoints;

    /**
     * 连续轨迹点列表（waypoints 模式）
     * 与 pathPoints 功能相同，用于接收外部 MAPF 算法下发的连续轨迹点
     * 引擎会取出第一个点作为本次移动目标，其余点在到达后自动接力执行
     */
    private List<Point> waypoints;

    /**
     * 是否强制校验路径合法性
     * true: 如果路径脱离路网，引擎抛出异常并中断指令
     * false: 宽松模式，允许脱网移动（仅记录警告）
     */
    private Boolean enforcePathValidation = true;
}