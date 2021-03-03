package com.alibaba.csp.sentinel.dashboard.config;

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SentinelApolloPrivateProperties.class)
public class SentinelApolloOpenApiConfiguration {

    private final SentinelApolloPrivateProperties properties;

    public SentinelApolloOpenApiConfiguration(SentinelApolloPrivateProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ApolloOpenApiClient apolloOpenApiClient() {
        ApolloOpenApiClient apolloOpenApiClient = ApolloOpenApiClient.newBuilder()
                .withPortalUrl(this.properties.getPortalUrl())
                .withToken(this.properties.getToken())
                .build();
        return apolloOpenApiClient;
    }
}
