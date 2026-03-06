package model.dto.config;

import java.util.List;
import java.util.Map;

public class MapConfigDto {
    private Map<String, Double> coordinates;
    private Map<String, Double> parameters;
    private List<YardBlockDto> yardBlocks;
    private List<ChargingStationDto> chargingStations;
    private List<MapPathDto> paths;
    private List<TransferZoneDto> transferZones;

    public Map<String, Double> getCoordinates() { return coordinates; }
    public void setCoordinates(Map<String, Double> coordinates) { this.coordinates = coordinates; }

    public Map<String, Double> getParameters() { return parameters; }
    public void setParameters(Map<String, Double> parameters) { this.parameters = parameters; }

    public List<YardBlockDto> getYardBlocks() { return yardBlocks; }
    public void setYardBlocks(List<YardBlockDto> yardBlocks) { this.yardBlocks = yardBlocks; }

    public List<ChargingStationDto> getChargingStations() { return chargingStations; }
    public void setChargingStations(List<ChargingStationDto> chargingStations) { this.chargingStations = chargingStations; }

    public List<MapPathDto> getPaths() { return paths; }
    public void setPaths(List<MapPathDto> paths) { this.paths = paths; }

    public List<TransferZoneDto> getTransferZones() { return transferZones; }
    public void setTransferZones(List<TransferZoneDto> transferZones) { this.transferZones = transferZones; }
}