package model.entity;

/**
 * 集装箱实体
 */
public class Container {
    private String id;          // 箱号
    private String sizeType;    // 尺寸类型
    private Double weight;      // 重量
    private String owner;       // 所属船舶

    // 无参构造
    public Container() {
    }

    // 常用构造
    public Container(String id, String sizeType) {
        this.id = id;
        this.sizeType = sizeType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSizeType() {
        return sizeType;
    }

    public void setSizeType(String sizeType) {
        this.sizeType = sizeType;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

}
