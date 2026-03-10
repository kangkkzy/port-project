package common.exception;

/**
 * 碰撞异常 - 设备轨迹冲突检测失败时抛出
 */
public class CollisionException extends RuntimeException {

    private final String deviceIdA;
    private final String deviceIdB;
    private final long collisionTime;
    private final double collisionX;
    private final double collisionY;

    public CollisionException(String message, String deviceIdA, String deviceIdB,
                              long collisionTime, double collisionX, double collisionY) {
        super(message);
        this.deviceIdA = deviceIdA;
        this.deviceIdB = deviceIdB;
        this.collisionTime = collisionTime;
        this.collisionX = collisionX;
        this.collisionY = collisionY;
    }

    public String getDeviceIdA() {
        return deviceIdA;
    }

    public String getDeviceIdB() {
        return deviceIdB;
    }

    public long getCollisionTime() {
        return collisionTime;
    }

    public double getCollisionX() {
        return collisionX;
    }

    public double getCollisionY() {
        return collisionY;
    }
}
