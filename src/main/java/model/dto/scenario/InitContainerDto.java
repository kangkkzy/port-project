package model.dto.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 场景中集装箱初始定义（物理位置如 BAY_01、YARD_A）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitContainerDto {
    private String containerId;
    /** 初始所在物理位置，如 BAY_01、YARD_A */
    private String currentPos;
}
