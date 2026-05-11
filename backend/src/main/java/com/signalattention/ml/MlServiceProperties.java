package com.signalattention.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signalattention.ml-service")
public record MlServiceProperties(String baseUrl) {
}
