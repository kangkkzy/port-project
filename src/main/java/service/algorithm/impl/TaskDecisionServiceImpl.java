package service.algorithm.impl;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.exception.BusinessException;
import common.util.BizTypeUtil;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;

/**
 * 任务决策服务
 */
@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();

    // [新增] 引入校验工具实例
    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        BaseDevice device = null;

        // 1. 根据具体类型查找设备 (保持原有逻辑)
        if (DeviceTypeEnum.ELECTRIC_TRUCK.equals(req.getDeviceType()) || DeviceTypeEnum.OIL_TRUCK.equals(req.getDeviceType())) {
            device = context.getTruckMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.ASC.equals(req.getDeviceType())) {
            device = context.getAscMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            device = context.getQcMap().get(req.getDeviceId());
        } else {
            if (req.getDeviceType() == null) throw new BusinessException("设备类型不能为空");
            device = context.getDevice(req.getDeviceId());
        }

        if (device == null) {
            throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);
        }

        // 2. 验证工单 (保持原有逻辑)
        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) {
            throw new BusinessException("指定的作业指令 [" + req.getWiRefNo() + "] 不存在");
        }

        // 3. 验证作业指令的设备配置 (保持原有逻辑)
        BizTypeEnum bizType = wi.getMoveKind();
        if (bizType != null) {
            String validationError = BizTypeUtil.validateWorkInstructionDevices(wi);
            if (validationError != null) {
                throw new BusinessException("作业指令 [" + req.getWiRefNo() + "] 设备配置错误: " + validationError);
            }

            // 验证设备是否是指令中指定的设备之一
            boolean isDeviceMatching = BizTypeUtil.isDeviceMatchingWorkInstruction(req.getDeviceId(), wi);
            if (!isDeviceMatching) {
                // 警告但不阻止（外部算法可能有意指派）
            }

            // 4. [新增] 业务逻辑全流程约束校验
            // 对应 PDF 3.2 执行层面的问题：保证指令的可执行性
            checkOperationalConstraints(wi);
        }

        // 5. 生成响应
        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(req.getDeviceId());
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setEstimatedCost(0.0); // 留空
        resp.setNextAction("PROCEED_TASK");

        return resp;
    }

    /**
     * 校验具体业务类型的物理/逻辑约束
     */
    private void checkOperationalConstraints(WorkInstruction wi) {
        String fromPos = wi.getFromPos();
        String toPos = wi.getToPos();
        BizTypeEnum type = wi.getMoveKind();

        switch (type) {
            case LOAD: // 装船: 堆场提箱 -> 船上落箱
                // 校验堆场: 上面不能有箱子
                if (isYardPos(fromPos) && !yardMock.isFetchAllowed(fromPos)) {
                    throw new BusinessException("违反堆场提箱约束: " + fromPos + " 被压住");
                }
                // 校验船图: 下面必须有箱子
                if (isVesselPos(toPos) && !vesselMock.isLoadAllowed(toPos)) {
                    throw new BusinessException("违反装船顺序约束: " + toPos + " 悬空，前序位置未装船");
                }
                break;

            case DSCH: // 卸船: 船上提箱 -> 堆场落箱
                // 校验船图: 上面不能有箱子
                if (isVesselPos(fromPos) && !vesselMock.isDischargeAllowed(fromPos)) {
                    throw new BusinessException("违反卸船顺序约束: " + fromPos + " 被压住，需先卸上方箱子");
                }
                // 校验堆场: 下面必须有箱子
                if (isYardPos(toPos) && !yardMock.isPutAllowed(toPos)) {
                    throw new BusinessException("违反堆场落箱约束: " + toPos + " 悬空，需先放下方箱子");
                }
                break;

            case RECV: // 进箱: 闸口 -> 堆场
                if (isYardPos(toPos) && !yardMock.isPutAllowed(toPos)) {
                    throw new BusinessException("违反进箱堆叠约束: " + toPos + " 悬空");
                }
                break;

            case DLVR: // 提箱: 堆场 -> 闸口
                if (isYardPos(fromPos) && !yardMock.isFetchAllowed(fromPos)) {
                    throw new BusinessException("违反提箱堆叠约束: " + fromPos + " 被压住");
                }
                break;

            default:
                break;
        }
    }

    // 简单的位置类型判断辅助方法
    private boolean isVesselPos(String pos) {
        // 假设船上位置以 "BAY" 开头 (与 Mock 数据一致)
        return pos != null && pos.startsWith("BAY");
    }

    private boolean isYardPos(String pos) {
        // 假设堆场位置以 "YARD" 开头
        return pos != null && pos.startsWith("YARD");
    }
}