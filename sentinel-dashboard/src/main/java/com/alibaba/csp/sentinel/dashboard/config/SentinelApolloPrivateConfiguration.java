package com.alibaba.csp.sentinel.dashboard.config;

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SentinelApolloPrivateProperties.class)
public class SentinelApolloPrivateConfiguration {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String appId;

    private final String env;

    private final String clusterName;

    private final SentinelApolloPrivateProperties properties;

    private final ApolloOpenApiClient apolloOpenApiClient;

    public SentinelApolloPrivateConfiguration(
            @Value("${app.id}") String appId,
            @Value("${env}") String env,
            @Value("${apollo.cluster}") String clusterName,
            SentinelApolloPrivateProperties properties,
            ApolloOpenApiClient apolloOpenApiClient) {
        this.appId = appId;
        this.env = env;
        this.clusterName = clusterName;
        this.properties = properties;
        this.apolloOpenApiClient = apolloOpenApiClient;
    }

//    private String resolveOrgId() {
//        List<OpenAppDTO> openAppDTOS = this.apolloOpenApiClient.getAppsByIds(Collections.singletonList(this.appId));
//
//        if (openAppDTOS.size() != 1) {
//            logger.error("openAppDTOS size not 1, size = {}, content = {}", openAppDTOS.size(), openAppDTOS);
//            throw new IllegalStateException("openAppDTOS size not 1");
//        }
//
//        OpenAppDTO openAppDTO = openAppDTOS.get(0);
//
//        logger.info("orgName of {} is {}", this.appId, openAppDTO.getOrgId());
//
//        return openAppDTO.getOrgId();
//    }

    public String getAppId() {
        return appId;
    }

    public String getEnv() {
        return env;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getOperateUser() {
        return this.properties.getOperateUser();
    }
}
