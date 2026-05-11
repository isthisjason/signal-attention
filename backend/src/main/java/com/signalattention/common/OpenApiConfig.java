package com.signalattention.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI signalAttentionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SignalAttention API")
                        .version("0.0.1")
                        .description("Backend API for local-first strategy backtesting and risk scoring."));
    }
}
