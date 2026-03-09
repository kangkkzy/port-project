package application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"application", "common", "controller", "engine", "model", "service"})
@ConfigurationPropertiesScan
public class SecsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecsApplication.class, args);
    }
}