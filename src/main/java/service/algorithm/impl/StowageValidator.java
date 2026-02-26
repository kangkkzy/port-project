package service.algorithm.impl;

import common.consts.BizTypeEnum;
import common.consts.PositionType;
import common.exception.BusinessException;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
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
                throw new BusinessException("堆场提箱受阻: " + from);
            }
            if (toType.isVessel() && !vesselMock.isLoadAllowed(to)) {
                throw new BusinessException("装船顺序错误: " + to);
            }
        } else if (type == BizTypeEnum.DSCH) {
            if (fromType.isVessel() && !vesselMock.isDischargeAllowed(from)) {
                throw new BusinessException("卸船顺序错误: " + from);
            }
            if (toType.isYard() && !yardMock.isPutAllowed(to)) {
                throw new BusinessException("堆场落箱受阻: " + to);
            }
        }
    }
}


