package com.signalattention;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SignalAttentionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalAttentionApplication.class, args);
    }
}
