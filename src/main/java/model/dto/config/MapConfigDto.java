package model.dto.config;

import java.util.List;
import java.util.Map;

public class MapConfigDto {
    private Map<String, Double> coordinates;
    private Map<String, Double> parameters;
    private List<MapPathDto> paths;

    public Map<String, Double> getCoordinates() { return coordinates; }
    public void setCoordinates(Map<String, Double> coordinates) { this.coordinates = coordinates; }

    public Map<String, Double> getParameters() { return parameters; }
    public void setParameters(Map<String, Double> parameters) { this.parameters = parameters; }

    public List<MapPathDto> getPaths() { return paths; }
    public void setPaths(List<MapPathDto> paths) { this.paths = paths; }
}
