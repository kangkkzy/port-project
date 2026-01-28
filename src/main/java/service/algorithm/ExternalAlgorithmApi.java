package service.algorithm;

import common.Result;
import model.dto.request.*;
import model.dto.request.MoveCommandReq;
import model.dto.response.AssignTaskResp;

/**
 * 外部算法接入 API 接口
 */
public interface ExternalAlgorithmApi {

    /** 运动控制 下发 集卡 的移动路径 */
    Result moveDevice(MoveCommandReq req);

    /** 移动控制 控制桥吊/龙门吊进行横向大车移动或垂直起升 */
    Result moveCrane(CraneMoveReq req);

    /** 任务控制 设备指派业务任务 支持闭环返回任务分配与耗电检查结果 */
    AssignTaskResp assignTask(AssignTaskReq req);

    /** 环境控制 控制交通栅栏状态 (锁死/通行) */
    Result toggleFence(FenceControlReq req);

    /** 作业控制 控制桥吊/龙门吊进行抓箱/放箱作业 (支持设置作业耗时) */
    Result operateCrane(CraneOperationReq req);

    /** 电量控制 指令集卡前往指定充电桩充电 */
    Result chargeTruck(ChargeCommandReq req);

    /** 时间控制 驱动仿真时钟向后推演指定的时间 */
    void stepTime(long stepMS);

    /** 事件控制 取消指定的事件 */
    Result cancelEvent(String eventId);
}