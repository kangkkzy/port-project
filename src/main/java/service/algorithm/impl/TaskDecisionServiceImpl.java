package service.algorithm.impl;

import common.exception.BusinessException;
import engine.log.SimulationStatisticsService;
import engine.context.GlobalContext;
import engine.websocket.SimulationEventWebSocketService;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;
import service.algorithm.WorkInstructionValidator;

import java.util.List;

@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();
    private final List<WorkInstructionValidator> validators;
    private final SimulationStatisticsService statisticsService;

    @Autowired(required = false)
    private SimulationEventWebSocketService webSocketService;

    public TaskDecisionServiceImpl(List<WorkInstructionValidator> validators,
                                   SimulationStatisticsService statisticsService) {
        this.validators = validators;
        this.statisticsService = statisticsService;
    }

    // 将方法名重命名为 assignTask 以完美实现接口
    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        statisticsService.recordTaskAttempt();

        // 动态获取调度主体ID（兼容外部算法新加的 truckId/craneId，或旧的 deviceId）
        String devId = null;
        if (req.getTruckId() != null) {
            devId = req.getTruckId();
        } else if (req.getCraneId() != null) {
            devId = req.getCraneId();
        } else {
            devId = req.getDeviceId(); // 兼容老代码
        }

        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) {
            throw new BusinessException("作业指令不存在: " + req.getWiRefNo());
        }

        // 核心：校验器责任链。所有的设备存在性、绑定关系、空间堆叠、防碰撞都在这里统一完成
        try {
            for (WorkInstructionValidator validator : validators) {
                validator.validate(req, context);
            }
        } catch (BusinessException e) {
            // 捕获堆叠或防碰撞等业务异常，通过 WebSocket 推送到前端沙盘控制台标红展示
            if (webSocketService != null) {
                webSocketService.broadcastError(devId, e.getMessage(), context.getSimTime());
            }
            throw e; // 继续抛出异常，阻断该错误指令加入物理执行队列
        }

        AssignTaskResp resp = new AssignTaskResp();
        // 将成功分配的状态返回
        resp.setTruckId(devId);
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setNextAction("PROCEED_TASK");
        return resp;
    }
}