package service.algorithm;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;

/**
 * 外部算法接入 API 接口
 */
public interface ExternalAlgorithmApi {

    /**  运动控制 下发 设备的移动路径 */
    Result moveDevice(MoveCommandReq req);

    /**  任务控制 设备指派业务任务 */
    Result assignTask(AssignTaskReq req);

    /**  环境控制 控制交通栅栏状态 (锁死/通行) */
    Result toggleFence(FenceControlReq req);

    /**  作业控制：控制桥吊/龙门吊进行抓箱/放箱作业 (支持设置作业耗时) */
    Result operateCrane(CraneOperationReq req);

    /**  时间控制 驱动仿真时钟向后推演指定的时间 */
    void stepTime(long stepMS);
}