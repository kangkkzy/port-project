package model.dto.config;

import java.util.Map;

public class MapConfigDto {
    private Map<String, Double> coordinates;
    private Map<String, Double> parameters;

    public Map<String, Double> getCoordinates() { return coordinates; }
    public void setCoordinates(Map<String, Double> coordinates) { this.coordinates = coordinates; }
    public Map<String, Double> getParameters() { return parameters; }
    public void setParameters(Map<String, Double> parameters) { this.parameters = parameters; }
}
