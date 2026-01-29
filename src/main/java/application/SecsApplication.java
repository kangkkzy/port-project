package application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"application", "common", "controller", "engine", "model", "service"})
public class SecsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecsApplication.class, args);
    }
}