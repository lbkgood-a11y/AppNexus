package com.appnexus.poc.api;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiSurfaceConfiguration {

    @Bean
    GroupedOpenApi appApi() {
        return GroupedOpenApi.builder().group("app").packagesToScan("com.appnexus.poc.api.app").build();
    }

    @Bean
    GroupedOpenApi partnerApi() {
        return GroupedOpenApi.builder().group("partner-v1").packagesToScan("com.appnexus.poc.api.partner").build();
    }

    @Bean
    GroupedOpenApi platformApi() {
        return GroupedOpenApi.builder().group("platform").packagesToScan("com.appnexus.poc.api.platform").build();
    }
}
