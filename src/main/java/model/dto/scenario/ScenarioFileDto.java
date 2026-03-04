package model.dto.scenario;

import lombok.Data;
import java.util.List;

@Data
public class ScenarioFileDto {
    private List<InitTruckDto> initTrucks;
    private List<InitQcDto> initQcs;
    private List<InitAscDto> initAscs;
    private List<InitContainerDto> initContainers;
    private List<WorkInstructionEntryDto> workInstructions;

    @Data
    public static class InitTruckDto {
        private String id;
        private String type;
        private Double posX;
        private Double posY;
        private String state;
    }

    @Data
    public static class InitQcDto {
        private String id;
        private Double posX;
        private Double posY;
        private String state;
    }

    @Data
    public static class InitAscDto {
        private String id;
        private Double posX;
        private Double posY;
        private String state;
    }

    @Data
    public static class InitContainerDto {
        private String containerId;
        private String currentPos;
    }

    @Data
    public static class WorkInstructionEntryDto {
        private String wiRefNo;
        private String moveKind;
        private String fetchCheId;
        private String carryCheId;
        private String putCheId;
        private String fromPos;
        private String toPos;
        private String containerId;
    }
}
