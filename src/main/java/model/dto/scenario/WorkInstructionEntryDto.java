package model.dto.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 场景中作业指令条目（要派发的任务列表）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkInstructionEntryDto {
    private String wiRefNo;
    private String moveKind;
    private String containerId;
    private String fetchCheId;
    private String carryCheId;
    private String putCheId;
    private String fromPos;
    private String toPos;
}
