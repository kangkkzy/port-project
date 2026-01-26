package service.algorithm.impl;

import common.consts.ErrorCodes;
import common.exception.BusinessException;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Point;
import model.entity.YardBlock;
import org.springframework.stereotype.Service;
import service.algorithm.MapDataService;

@Service
public class MapDataServiceImpl implements MapDataService {

    private final GlobalContext context = GlobalContext.getInstance();

    @Override
    public Point resolveCoordinate(String locationCode) {
        if (locationCode == null || locationCode.trim().isEmpty()) {
            throw new BusinessException("位置编码为空，无法解析坐标");
        }

        // 1. 尝试从堆场箱区匹配
        YardBlock block = context.getYardBlockMap().get(locationCode);
        if (block != null) {
            return new Point(block.getInvertX(), block.getInvertY());
        }

        // 2. 尝试从设备位置匹配 (例如作业指令终点是某台岸桥下面)
        BaseDevice device = context.getDevice(locationCode);
        if (device != null) {
            return new Point(device.getPosX(), device.getPosY());
        }

        // TODO: 可以在这里继续扩展对 泊位、大门(Gate) 等位置的解析

        throw new BusinessException("无法解析位置编码 [" + locationCode + "] 的坐标");
    }
}