package service;

import common.Result;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.entity.Fence;
import model.entity.Truck;
import org.springframework.stereotype.Service;

@Service
public class CommandService {

    private final GlobalContext context = GlobalContext.getInstance();

    public Result moveTruck(MoveCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error("车辆不存在");

        truck.assignWaypoints(req.getPoints());
        return Result.success("车辆 " + req.getTruckId() + " 开始向目标移动", null);
    }

    public Result assignTask(AssignTaskReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error("车辆不存在");

        truck.setCurrWiRefNo(req.getWiRefNo());
        return Result.success("任务 " + req.getWiRefNo() + " 已绑定给车辆 " + req.getTruckId(), null);
    }

    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error("栅栏不存在");

        fence.setStatus(req.getStatus());
        String msg = "01".equals(req.getStatus()) ? "锁死" : "放行";
        return Result.success("栅栏 " + req.getFenceId() + " 已被" + msg, null);
    }
}
