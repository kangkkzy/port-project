package common.config;

import model.bo.GlobalContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * 负责在 Spring 容器启动后，将 PhysicsConfig 注入到 GlobalContext 单例中
 * 使得非 Spring 管理的实体对象（如 BaseDevice）也可以通过 GlobalContext 访问物理配置。
 */
@Configuration
public class GlobalContextConfig implements InitializingBean {

    private final PhysicsConfig physicsConfig;

    public GlobalContextConfig(PhysicsConfig physicsConfig) {
        this.physicsConfig = physicsConfig;
    }

    @Override
    public void afterPropertiesSet() {
        GlobalContext.getInstance().setPhysicsConfig(physicsConfig);
    }
}