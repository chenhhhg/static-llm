package bupt.staticllm.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "bupt.staticllm")
public class StaticLlmApplication {
    public static void main(String[] args) {
        SpringApplication.run(StaticLlmApplication.class, args);
    }
}

