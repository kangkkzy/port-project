package service.algorithm.impl;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.exception.BusinessException;
import common.util.BizTypeUtil;
import common.util.SafetyValidator;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;

/**
 * 任务决策服务实现类 (完整版)
 * 集成了设备查找、工单验证、业务类型校验、
 * PDF Part 2 (堆叠约束) 和 PDF Part 4 (物理安全与时间协同) 的全部校验逻辑。
 */
@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();

    // 引入各个校验组件
    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();
    private final SafetyValidator safetyValidator = SafetyValidator.getInstance();

    @Autowired
    private SimulationStatisticsService statisticsService;

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        // 0. 记录统计开始
        statisticsService.recordTaskAttempt();

        BaseDevice device = null;

        // 1. 根据具体类型查找设备 (保留原有逻辑)
        if (DeviceTypeEnum.ELECTRIC_TRUCK.equals(req.getDeviceType()) || DeviceTypeEnum.OIL_TRUCK.equals(req.getDeviceType())) {
            device = context.getTruckMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.ASC.equals(req.getDeviceType())) {
            device = context.getAscMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            device = context.getQcMap().get(req.getDeviceId());
        } else {
            // 通用查找
            if (req.getDeviceType() == null) throw new BusinessException("设备类型不能为空");
            device = context.getDevice(req.getDeviceId());
        }

        if (device == null) {
            throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);
        }

        // 2. 验证工单存在性 (保留原有逻辑)
        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) {
            throw new BusinessException("指定的作业指令 [" + req.getWiRefNo() + "] 不存在");
        }

        // 3. 验证作业指令的设备配置是否符合业务类型要求 (保留原有逻辑)
        BizTypeEnum bizType = wi.getMoveKind();
        if (bizType != null) {
            String validationError = BizTypeUtil.validateWorkInstructionDevices(wi);
            if (validationError != null) {
                throw new BusinessException("作业指令 [" + req.getWiRefNo() + "] 设备配置错误: " + validationError);
            }

            // 验证设备是否是指令中指定的设备之一
            boolean isDeviceMatching = BizTypeUtil.isDeviceMatchingWorkInstruction(req.getDeviceId(), wi);
            if (!isDeviceMatching) {
                // 仅做日志记录，暂不阻断，因为某些调度算法可能允许预调度
                // System.out.println("Warn: Device mismatch for WI " + wi.getWiRefNo());
            }
        }

        // =========================================================================
        // 4. [PDF Part 2] 业务逻辑堆叠顺序校验 (Stowage Logic Constraints)
        // =========================================================================
        checkStowageConstraints(wi);

        // =========================================================================
        // 5. [PDF Part 4] 物理与协同约束校验 (Physical & Sync Constraints)
        // =========================================================================
        checkPhysicalAndSyncConstraints(req, wi);

        // 6. 生成响应
        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(req.getDeviceId()); // 或者是 QC ID，视请求而定
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setEstimatedCost(0.0); // 可扩展：填入计算出的 cost
        resp.setNextAction("PROCEED_TASK");

        return resp;
    }

    /**
     * 校验装卸船和堆场的堆叠顺序约束 (PDF Part 2)
     */
    private void checkStowageConstraints(WorkInstruction wi) {
        String fromPos = wi.getFromPos();
        String toPos = wi.getToPos();
        BizTypeEnum type = wi.getMoveKind();

        if (type == BizTypeEnum.LOAD) {
            // 装船：从堆场提箱 -> 到船上落箱

            // 校验堆场: 提箱是否被压住
            if (isYardPos(fromPos) && !yardMock.isFetchAllowed(fromPos)) {
                throw new BusinessException("违反堆场提箱约束: 位置 " + fromPos + " 被压住");
            }
            // 校验船图: 落箱是否悬空
            if (isVesselPos(toPos) && !vesselMock.isLoadAllowed(toPos)) {
                throw new BusinessException("违反装船顺序约束: 位置 " + toPos + " 悬空，前序位置未完成");
            }

        } else if (type == BizTypeEnum.DSCH) {
            // 卸船：从船上提箱 -> 到堆场落箱

            // 校验船图: 提箱是否被压住
            if (isVesselPos(fromPos) && !vesselMock.isDischargeAllowed(fromPos)) {
                throw new BusinessException("违反卸船顺序约束: 位置 " + fromPos + " 被压住，需先卸上方箱子");
            }
            // 校验堆场: 落箱是否悬空
            if (isYardPos(toPos) && !yardMock.isPutAllowed(toPos)) {
                throw new BusinessException("违反堆场落箱约束: 位置 " + toPos + " 悬空，需先放下方箱子");
            }
        }
    }

    /**
     * 校验物理防碰撞和时间协同 (PDF Part 4)
     */
    private void checkPhysicalAndSyncConstraints(AssignTaskReq req, WorkInstruction wi) {
        // 仅当申请设备为岸桥(QC)时，进行防碰撞和集卡协同检查
        if (DeviceTypeEnum.QC.equals(req.getDeviceType())) {

            // --- 4.1 岸桥防碰撞 (Hard Constraint) ---
            double targetX = safetyValidator.parsePositionToX(wi.getToPos());
            boolean isSafe = safetyValidator.checkQcInterference(req.getDeviceId(), targetX);

            if (!isSafe) {
                statisticsService.recordSafetyViolation();
                throw new BusinessException("违反岸桥防碰撞约束: 目标位置 " + wi.getToPos() + " 会导致设备交叉或距离过近");
            }

            // --- 4.2 时间协同 (Soft Constraint / Objective) ---
            // 检查该工单是否指定了集卡 (FetchCheId)
            String truckId = wi.getFetchCheId();
            if (truckId != null && context.getTruckMap().containsKey(truckId)) {

                long waitTime = safetyValidator.checkTimeSync(truckId, req.getDeviceId(), wi.getToPos());

                if (waitTime == -1) {
                    // 协同严重失败 (超时)
                    statisticsService.recordSyncFailure();
                    throw new BusinessException("违反时间协同约束: 集卡与岸桥到达时间偏差过大，调度驳回");
                } else {
                    // 记录等待成本 (用于目标函数优化)
                    statisticsService.recordWaitTime(waitTime);
                }
            }
        }
    }

    // --- 辅助判断方法 ---

    private boolean isVesselPos(String pos) {
        // 简单判断：以 BAY 开头视为船上位置
        return pos != null && pos.startsWith("BAY");
    }

    private boolean isYardPos(String pos) {
        // 简单判断：以 YARD 开头视为堆场位置
        return pos != null && pos.startsWith("YARD");
    }
}