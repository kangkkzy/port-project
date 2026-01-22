package service.sync;

import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.dto.request.KDataSnapReq;
import model.entity.*;
import org.springframework.stereotype.Service;

/**
 * 数据同步服务
 */
@Service
@Slf4j
public class KDataSyncService {

    private final GlobalContext context = GlobalContext.getInstance();

    /**
     * 处理全量快照
     */
    public void handleSnapshot(KDataSnapReq req) {
        if (req == null || req.getData() == null) {
            log.warn("收到空的快照数据，跳过处理");
            return;
        }

        long start = System.currentTimeMillis();
        KDataSnapReq.SnapData data = req.getData();

        //  清空老数据
        context.clearAll();

        //  处理数据适配实体类的字段名

        //  设备
        if (data.getTruckList() != null) {
            for (Truck truck : data.getTruckList()) {
                context.getTruckMap().put(truck.getId(), truck);
            }
        }

        if (data.getAscList() != null) {
            for (AscDevice asc : data.getAscList()) {
                context.getAscMap().put(asc.getId(), asc);
            }
        }

        if (data.getQcList() != null) {
            for (QcDevice qc : data.getQcList()) {
                context.getQcMap().put(qc.getId(), qc);
            }
        }

        //  基础设施
        if (data.getBlockList() != null) {
            for (YardBlock block : data.getBlockList()) {
                if (block.getBlockCode() != null) {
                    context.getYardBlockMap().put(block.getBlockCode(), block);
                }
            }
        }

        //  任务
        if (data.getWiList() != null) {
            for (WorkInstruction wi : data.getWiList()) {
                if (wi.getWiRefNo() != null) {
                    context.getWorkInstructionMap().put(wi.getWiRefNo(), wi);
                }
            }
        }

        // 箱子
        if (data.getContainerList() != null) {
            for (Container c : data.getContainerList()) {
                if (c.getContainerId() != null) {
                    context.getContainerMap().put(c.getContainerId(), c);
                }
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("全量快照同步完成! 耗时:{}ms. 内存: Truck[{}], ASC[{}], Block[{}], WI[{}], Cnt[{}]",
                cost,
                context.getTruckMap().size(),
                context.getAscMap().size(),
                context.getYardBlockMap().size(),
                context.getWorkInstructionMap().size(),
                context.getContainerMap().size());
    }
}