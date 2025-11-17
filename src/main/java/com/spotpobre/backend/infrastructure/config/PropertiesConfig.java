package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import com.spotpobre.backend.infrastructure.config.properties.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AwsProperties.class})
public class PropertiesConfig {
}
