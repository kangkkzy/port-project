package common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import service.algorithm.MapDataService;

import javax.annotation.PostConstruct; // 替换为 javax 兼容老版本
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 地图配置初始化器
 * 应用启动时自动加载默认地图配置
 */
@Component
public class MapConfigInitializer {

    @Autowired
    private MapDataService mapDataService;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("map-config.json");
            // 使用 Spring 的 StreamUtils 替代 Java 11 的 Files.readString，完美兼容 Java 8 和 jar 运行
            try (InputStream inputStream = resource.getInputStream()) {
                String jsonConfig = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                mapDataService.loadMapConfiguration(jsonConfig);
                System.out.println(">>> [Init] 地图配置加载完成");
            }
        } catch (Exception e) {
            throw new RuntimeException("启动时加载地图配置失败: " + e.getMessage(), e);
        }
    }
}