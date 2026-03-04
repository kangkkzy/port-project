package service.algorithm.impl;

import common.consts.BizTypeEnum;
import common.consts.PositionType;
import common.exception.BusinessException;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import engine.context.GlobalContext;
import engine.websocket.SimulationEventWebSocketService;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;
import service.algorithm.WorkInstructionValidator;

/**
 * 舱位/堆场堆叠与作业顺序校验
 * 只关心 from/to 与 moveKind，不依赖设备状态。
 */
@Component
public class StowageValidator implements WorkInstructionValidator {

    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();
    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEventWebSocketService webSocketService;

    public StowageValidator(SimulationEventWebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void validate(WorkInstruction wi, AssignTaskReq req) {
        if (wi == null || wi.getMoveKind() == null) {
            return;
        }
        checkStowage(wi);
    }

    private void checkStowage(WorkInstruction wi) {
        String from = wi.getFromPos();
        String to = wi.getToPos();
        BizTypeEnum type = wi.getMoveKind();

        PositionType fromType = PositionType.fromCode(from);
        PositionType toType = PositionType.fromCode(to);

        if (type == BizTypeEnum.LOAD) {
            if (fromType.isYard() && !yardMock.isFetchAllowed(from)) {
                webSocketService.broadcastError(null, "堆场提箱受阻: " + from, context.getSimTime());
                throw new BusinessException("堆场提箱受阻: " + from);
            }
            if (toType.isVessel() && !vesselMock.isLoadAllowed(to)) {
                webSocketService.broadcastError(null, "装船顺序错误: " + to, context.getSimTime());
                throw new BusinessException("装船顺序错误: " + to);
            }
        } else if (type == BizTypeEnum.DSCH) {
            if (fromType.isVessel() && !vesselMock.isDischargeAllowed(from)) {
                webSocketService.broadcastError(null, "卸船顺序错误: " + from, context.getSimTime());
                throw new BusinessException("卸船顺序错误: " + from);
            }
            if (toType.isYard() && !yardMock.isPutAllowed(to)) {
                webSocketService.broadcastError(null, "堆场落箱受阻: " + to, context.getSimTime());
                throw new BusinessException("堆场落箱受阻: " + to);
            }
        }
    }
}




