package controller;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.context.GlobalContext;
import lombok.RequiredArgsConstructor;
import model.dto.request.AssignTaskReq;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.MoveCommandReq;
import model.entity.AscDevice;
import model.entity.Point;
import model.entity.QcDevice;
import model.entity.Truck;
import model.entity.WorkInstruction;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.ExternalAlgorithmApi;

@RestController
@RequestMapping("/sim/mock")
@RequiredArgsConstructor
public class MockAlgorithmController {

    private final ExternalAlgorithmApi algorithmApi;

    @PostMapping("/flow-normal")
    public Result runNormalFlow() {
        try {
            GlobalContext ctx = GlobalContext.getInstance();

            // 1. 获取场景中加载的实体
            QcDevice qc = ctx.getQcMap().values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("场景中缺少 QC，请先加载 JSON 场景"));
            Truck truck = ctx.getTruckMap().values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("场景中缺少 集卡，请先加载 JSON 场景"));
            AscDevice asc = ctx.getAscMap().values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("场景中缺少 ASC，请先加载 JSON 场景"));
            WorkInstruction wi = ctx.getWorkInstructionMap().values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("场景中缺少 作业指令，请先加载 JSON 场景"));

            String qcId = qc.getId();
            String truckId = truck.getId();

            // 首先下发业务任务
            AssignTaskReq assignReq = new AssignTaskReq();
            assignReq.setWiRefNo(wi.getWiRefNo());
            assignReq.setTruckId(truckId);
            assignReq.setCraneId(qcId);
            algorithmApi.assignTask(assignReq);

            // 动态读取合规坐标
            double truckQcTransferLaneY = truck.getPosY(); // 合法车道 Y=150.0
            double targetBayX = qc.getPosX() + 40.0;       // 模拟岸桥移动到贝位 X=190.0
            double ascTransferLaneY = asc.getPosY();       // 堆场合法车道 Y=230.0
            double ascX = asc.getPosX();                   // ASC 轨道 X=175.0

            // 动作1：岸桥移动到贝位
            CraneMoveReq qcMove = new CraneMoveReq();
            qcMove.setCraneId(qcId);
            qcMove.setMoveType(DeviceStateEnum.MOVE_HORIZONTAL);
            qcMove.setDistance(40.0);
            qcMove.setSpeed(10.0);
            algorithmApi.moveCrane(qcMove);

            // 动作2：岸桥Z轴抓取
            CraneOperationReq qcFetch = new CraneOperationReq();
            qcFetch.setCraneId(qcId);
            qcFetch.setAction(EventTypeEnum.FETCH_DONE);
            qcFetch.setDurationMS(3000);
            algorithmApi.operateCrane(qcFetch);

            // 动作3：集卡开往岸桥正下方交接区 (X对齐桥吊)
            MoveCommandReq truckMoveQc = new MoveCommandReq();
            truckMoveQc.setTruckId(truckId);
            truckMoveQc.setTargetPoint(new Point(targetBayX, truckQcTransferLaneY));
            truckMoveQc.setSpeed(15.0);
            algorithmApi.moveDevice(truckMoveQc);

            // 动作4：岸桥Z轴放箱给集卡
            CraneOperationReq qcPut = new CraneOperationReq();
            qcPut.setCraneId(qcId);
            qcPut.setAction(EventTypeEnum.PUT_DONE);
            qcPut.setDurationMS(3000);
            algorithmApi.operateCrane(qcPut);

            // 动作5：集卡载箱开往 ASC 场桥交接区
            MoveCommandReq truckMoveAsc = new MoveCommandReq();
            truckMoveAsc.setTruckId(truckId);
            truckMoveAsc.setTargetPoint(new Point(ascX, ascTransferLaneY));
            truckMoveAsc.setSpeed(15.0);
            algorithmApi.moveDevice(truckMoveAsc);

            return Result.success(" 任务绑定与完整作业动作已成功派发！");
        } catch (Exception e) {
            return Result.error("指令下发失败：" + e.getMessage());
        }
    }

    @PostMapping("/flow-error")
    public Result runErrorFlow() {
        try {
            GlobalContext ctx = GlobalContext.getInstance();
            Truck truck = ctx.getTruckMap().values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("缺少集卡"));

            // 模拟下发非法的空间坐标 Y=999
            MoveCommandReq badMove = new MoveCommandReq();
            badMove.setTruckId(truck.getId());
            badMove.setTargetPoint(new Point(400.0, 999.0));
            badMove.setSpeed(15.0);

            algorithmApi.moveDevice(badMove);
            return Result.success("错误指令居然下发成功了？请检查校验器！");
        } catch (Exception e) {
            return Result.success("引擎基于外部地图拦截成功: " + e.getMessage());
        }
    }
}