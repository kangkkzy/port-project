package model.dto.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 场景中岸桥初始定义
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitQcDto {
    private String id;
    private Double posX;
    private Double posY;
    private String state = "IDLE";
}

