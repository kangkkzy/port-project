package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 靠泊船只实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vessel {
    private String vesselId;      // 船只ID
    private String vesselBerth;   // 泊位号
    private Double berthLocation; // 泊位坐标
    private String sideTo;        // 靠泊方向
    private Double length;        // 船长

    // 包含的贝位列表
    private List<VesselBay> bays = new ArrayList<>();
}