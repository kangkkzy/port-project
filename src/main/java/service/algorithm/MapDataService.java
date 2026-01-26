package service.algorithm;

import model.entity.Point;

/**
 * 地图数据与坐标解析服务
 * 负责将业务上的位置标识 (如：箱区号、泊位号、设备当前位置) 转换为绝对坐标
 */
public interface MapDataService {

    /**
     * 根据位置编码获取绝对坐标
     * @param locationCode 位置编码 (例如箱区代码 "BLK-A1" 或设备编号 "QC-01")
     * @return 绝对坐标 Point
     */
    Point resolveCoordinate(String locationCode);
}