package model.dto.request;

import lombok.Data;
import model.entity.*;

import java.util.List;

/**
 * 接收 KData 发送的全量快照请求
 * 结构：Request -> Data -> Lists
 */
@Data
public class KDataSnapReq {

    private String reqId;       // 请求ID
    private Long sendTime;      // 发送时间戳

    // 对应 JSON
    private SnapData data;

    @Data
    public static class SnapData {
        // 直接复用 Entity 依靠 JacksonConfig 自动映射字段
        private List<Truck> truckList;
        private List<AscDevice> ascList;
        private List<QcDevice> qcList;
        private List<YardBlock> blockList;
        private List<WorkInstruction> wiList;
        private List<Container> containerList;
    }
}