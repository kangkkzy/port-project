package service.algorithm.impl;

import common.consts.PositionType;
import common.exception.BusinessException;
import engine.context.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.YardBlock;
import model.entity.WorkInstruction;
import service.algorithm.WorkInstructionValidator;
import org.springframework.stereotype.Component;

@Component
public class StowageValidator implements WorkInstructionValidator {

    @Override
    public void validate(AssignTaskReq req, GlobalContext context) {
        WorkInstruction wi = context.getWorkInstruction(req.getWiRefNo());
        if (wi == null) return;

        // 真实堆场提箱阻挡校验
        if (PositionType.fromCode(wi.getFromPos()) == PositionType.YARD) {
            checkYardFetch(wi.getFromPos(), context);
        }
    }

    private void checkYardFetch(String posCode, GlobalContext context) {
        // 假设 posCode 格式为 "YARD_A_01_02_03" (堆区_Bay_Row_Tier)
        String[] parts = posCode.split("_");
        if (parts.length >= 5) {
            String blockId = parts[0] + "_" + parts[1]; // "YARD_A"
            int bay = Integer.parseInt(parts[2]) - 1;
            int row = Integer.parseInt(parts[3]) - 1;
            int tier = Integer.parseInt(parts[4]) - 1;

            YardBlock block = context.getYardBlockMap().get(blockId);
            if (block != null && tier < 4) { // 检查上方 (tier+1) 是否有箱子
                if (block.getContainer(bay, row, tier + 1) != null) {
                    throw new BusinessException("堆场提箱受阻: " + posCode + " 上方有集装箱遮挡");
                }
            }
        }
    }
}





