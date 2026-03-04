package model.dto.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景 JSON 文件根结构（数据驱动）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioFileDto {
    private String description;
    private List<InitTruckDto> initTrucks = new ArrayList<>();
    private List<InitQcDto> initQcs = new ArrayList<>();
    private List<InitAscDto> initAscs = new ArrayList<>();
    private List<InitContainerDto> initContainers = new ArrayList<>();
    private List<WorkInstructionEntryDto> workInstructions = new ArrayList<>();
}
