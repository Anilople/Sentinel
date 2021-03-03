package com.alibaba.csp.sentinel.dashboard.service;

import com.alibaba.csp.sentinel.dashboard.config.SentinelApolloPrivateConfiguration;
import com.alibaba.csp.sentinel.dashboard.config.SentinelApolloPublicProperties;
import com.alibaba.csp.sentinel.dashboard.constant.RuleTypeConstants;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.fastjson.JSON;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient;
import com.ctrip.framework.apollo.openapi.dto.NamespaceReleaseDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenAppNamespaceDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenItemDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenNamespaceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class SentinelApolloPublicNamespaceService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> publicNamespaceNames = new ConcurrentSkipListSet<>();

    private final SentinelApolloPrivateConfiguration sentinelApolloPrivateConfiguration;

    private final SentinelApolloPublicProperties SentinelApolloPublicProperties;

    private final ApolloOpenApiClient apolloOpenApiClient;

    public SentinelApolloPublicNamespaceService(
            SentinelApolloPrivateConfiguration sentinelApolloPrivateConfiguration,
            SentinelApolloPublicProperties sentinelApolloPublicProperties, ApolloOpenApiClient apolloOpenApiClient) {
        this.sentinelApolloPrivateConfiguration = sentinelApolloPrivateConfiguration;
        SentinelApolloPublicProperties = sentinelApolloPublicProperties;
        this.apolloOpenApiClient = apolloOpenApiClient;
    }

    private String resolvePublicNamespaceName(String projectName) {
        // 部门.控制台的appId.应用自己的appId
        return this.SentinelApolloPublicProperties.getNamespacePrefix() + "." + projectName;
    }

    private void createPublicNamespace(String publicNamespaceName) {
        OpenAppNamespaceDTO openAppNamespaceDTO = new OpenAppNamespaceDTO();
        openAppNamespaceDTO.setName(publicNamespaceName);
        openAppNamespaceDTO.setAppId(this.sentinelApolloPrivateConfiguration.getAppId());
        openAppNamespaceDTO.setFormat(ConfigFileFormat.Properties.getValue());
        openAppNamespaceDTO.setPublic(true);
        // disable auto prefix with orgId
        openAppNamespaceDTO.setAppendNamespacePrefix(false);
        openAppNamespaceDTO.setDataChangeCreatedBy("apollo");
        this.apolloOpenApiClient.createAppNamespace(openAppNamespaceDTO);
    }

    private void ensurePublicNamespaceExists(String publicNamespaceName) {
        // memory mark?
        if (this.publicNamespaceNames.contains(publicNamespaceName)) {
            return;
        }

        this.logger.info("public namespace '{}' not exists in memory mark, now try to query it from portal", publicNamespaceName);

        // query
        final String appId = this.sentinelApolloPrivateConfiguration.getAppId();
        final String env = this.sentinelApolloPrivateConfiguration.getEnv();
        final String clusterName = this.sentinelApolloPrivateConfiguration.getClusterName();

        try {
            OpenNamespaceDTO openNamespaceDTO = this.apolloOpenApiClient.getNamespace(appId, env, clusterName, publicNamespaceName);
            if (null != openNamespaceDTO) {
                this.publicNamespaceNames.add(publicNamespaceName);
                return;
            }
        } catch (RuntimeException e) {
            this.logger.warn(publicNamespaceName + " may not exists, try to create it", e);
        }

        this.createPublicNamespace(publicNamespaceName);
        // mark
        this.publicNamespaceNames.add(publicNamespaceName);
    }

    private String resolveRuleKey(String projectName, String ruleType) {
        if (RuleTypeConstants.FLOW.equals(ruleType)) {
            return projectName + "." + this.SentinelApolloPublicProperties.getFlowRulesKeySuffix();
        }

        throw new UnsupportedOperationException("operate rule type " + ruleType);
    }

    public CompletableFuture<Void> setRulesAsync(String projectName, String ruleType, List<? extends RuleEntity> ruleEntities) {
        final String publicNamespaceName = this.resolvePublicNamespaceName(projectName);
        this.ensurePublicNamespaceExists(publicNamespaceName);

        OpenItemDTO openItemDTO = new OpenItemDTO();
        final String ruleKey = this.resolveRuleKey(projectName, ruleType);
        openItemDTO.setKey(ruleKey);

        final String value = JSON.toJSONString(ruleEntities, true);
        openItemDTO.setValue(value);
        openItemDTO.setDataChangeCreatedBy(this.sentinelApolloPrivateConfiguration.getOperateUser());

        final String appId = this.sentinelApolloPrivateConfiguration.getAppId();
        final String env = this.sentinelApolloPrivateConfiguration.getEnv();
        final String clusterName = this.sentinelApolloPrivateConfiguration.getClusterName();
        final String operateUser = this.sentinelApolloPrivateConfiguration.getOperateUser();
        NamespaceReleaseDTO namespaceReleaseDTO = new NamespaceReleaseDTO();
        namespaceReleaseDTO.setReleasedBy(operateUser);
        namespaceReleaseDTO.setReleaseTitle(projectName + "." + ruleType);

        Runnable runnable = () -> {
            apolloOpenApiClient.createOrUpdateItem(appId, env, clusterName, publicNamespaceName, openItemDTO);
            apolloOpenApiClient.publishNamespace(appId, env, clusterName, publicNamespaceName, namespaceReleaseDTO);
        };

        return CompletableFuture.runAsync(runnable);
    }


}
