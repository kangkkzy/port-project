package common.exception;

/**
 * 仿真死循环异常 检测到时 给外部算法
 */
public class SimulationDeadLoopException extends RuntimeException {
    private final long simTime;
    private final int eventCount;

    public SimulationDeadLoopException(String message, long simTime, int eventCount) {
        super(message);
        this.simTime = simTime;
        this.eventCount = eventCount;
    }

    public long getSimTime() {
        return simTime;
    }

    public int getEventCount() {
        return eventCount;
    }
}
