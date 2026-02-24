package bupt.staticllm.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI staticLlmOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Static LLM API")
                        .description("静态代码分析与LLM审计系统 API文档")
                        .version("v1.0.0"));
    }
}
