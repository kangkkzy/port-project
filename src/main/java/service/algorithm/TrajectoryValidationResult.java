package service.algorithm;

import model.entity.Point;

import java.util.List;

/**
 * 轨迹验证结果
 */
public class TrajectoryValidationResult {
    private boolean valid;
    private List<Point> validSegments;
    private List<Point> invalidPoints;
    private String errorMessage;

    public static TrajectoryValidationResult success(List<Point> validSegments) {
        TrajectoryValidationResult result = new TrajectoryValidationResult();
        result.valid = true;
        result.validSegments = validSegments;
        return result;
    }

    public static TrajectoryValidationResult failure(String errorMessage, List<Point> invalidPoints) {
        TrajectoryValidationResult result = new TrajectoryValidationResult();
        result.valid = false;
        result.errorMessage = errorMessage;
        result.invalidPoints = invalidPoints;
        return result;
    }

    public boolean isValid() { return valid; }
    public List<Point> getValidSegments() { return validSegments; }
    public List<Point> getInvalidPoints() { return invalidPoints; }
    public String getErrorMessage() { return errorMessage; }
}
