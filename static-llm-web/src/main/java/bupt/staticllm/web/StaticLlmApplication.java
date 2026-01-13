package bupt.staticllm.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = {"bupt.staticllm"})
@MapperScan("bupt.staticllm.web.mapper")
@EnableAsync
public class StaticLlmApplication {
    public static void main(String[] args) {
        SpringApplication.run(StaticLlmApplication.class, args);
    }
}
