package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 船上贝位
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VesselBay {
    private String bayNo;         // 贝位号
    private String bayPos;        // 贝位的位置 (A-舱面, B-舱内)
    private String bayName;       // 贝位名称

    // 贝位类型 (FRONT-前贝位, BACK-后贝位, SINGLE-单小箱贝, BIG-大贝位)
    private String bayType;
    private Integer tierFirstDef; // 贝位分层

    // 贝位所包含的列
    private List<Column> colInfos = new ArrayList<>();

    /**
     * 船上列
     */
    @Data
    @NoArgsConstructor
    public static class Column {
        private String colNo;         // 列号
        private Integer tierNoBottom; // 底层列
        private Integer tierNoTop;    // 顶层列
    }
}
