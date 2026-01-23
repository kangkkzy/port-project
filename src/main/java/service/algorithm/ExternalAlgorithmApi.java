package service.algorithm;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;

/**
 * 外部算法接入 API 接口
 * 定义了外部算法与仿真引擎交互的标准契约
 */
public interface ExternalAlgorithmApi {

    /**
     *  运动控制：下发车辆移动路径
     */
    Result moveTruck(MoveCommandReq req);

    /**
     *  任务控制：为车辆指派业务任务
     */
    Result assignTask(AssignTaskReq req);

    /**
     *  环境控制：控制交通栅栏状态 (锁死/通行)
     */
    Result toggleFence(FenceControlReq req);

    /**
     *  时间控制：驱动仿真时钟向后推演指定的毫秒数
     */
    void stepTime(long stepMS);
}