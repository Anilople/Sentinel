/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.service;

import com.alibaba.cloud.sentinel.datasource.RuleType;
import com.alibaba.csp.sentinel.dashboard.config.SentinelApolloOpenApiProperties;
import com.alibaba.csp.sentinel.dashboard.util.DataSourceConverterUtils;
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient;
import com.ctrip.framework.apollo.openapi.dto.NamespaceReleaseDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenAppNamespaceDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenItemDTO;
import com.ctrip.framework.apollo.openapi.dto.OpenNamespaceDTO;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class SentinelApolloPublicNamespaceService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String operatedAppId;

    private final String env;

    private final String clusterName;

    private final String operateUser;

    private final SentinelApolloLogicService sentinelApolloLogicService;

    private final ApolloOpenApiClient apolloOpenApiClient;

    private final Map<String, Object> publicNamespaceNames = new ConcurrentHashMap<>();

    public SentinelApolloPublicNamespaceService(
            SentinelApolloOpenApiProperties sentinelApolloOpenApiProperties,
            SentinelApolloLogicService sentinelApolloLogicService, ApolloOpenApiClient apolloOpenApiClient) {
        this.operateUser = sentinelApolloOpenApiProperties.getOperateUser();
        this.operatedAppId = sentinelApolloOpenApiProperties.getOperatedAppId();
        this.env = sentinelApolloOpenApiProperties.getOperatedEnv();
        this.clusterName = sentinelApolloOpenApiProperties.getOperatedCluster();
        this.sentinelApolloLogicService = sentinelApolloLogicService;
        this.apolloOpenApiClient = apolloOpenApiClient;
    }

    private static Map<String, String> toKeyValues(List<OpenItemDTO> openItemDTOS) {
        Map<String, String> map = new HashMap<>();
        for (OpenItemDTO openItemDTO : openItemDTOS) {
            String key = openItemDTO.getKey();
            String value = openItemDTO.getValue();
            map.put(key, value);
        }
        return map;
    }

    /**
     * create, publish, authorize to current user about new public namespace.
     * use synchronized to forbid concurrent problem, because there is no performance problem here.
     *
     * @return null if resolve failed
     */
    private Object resolvePublicNamespaceInApollo(String publicNamespaceName) {

        // create
        OpenAppNamespaceDTO openAppNamespaceDTO = new OpenAppNamespaceDTO();
        openAppNamespaceDTO.setName(publicNamespaceName);
        openAppNamespaceDTO.setAppId(this.operatedAppId);
        openAppNamespaceDTO.setFormat(ConfigFileFormat.Properties.getValue());
        openAppNamespaceDTO.setPublic(true);
        // disable auto prefix with orgId
        openAppNamespaceDTO.setAppendNamespacePrefix(false);
        openAppNamespaceDTO.setDataChangeCreatedBy(this.operateUser);

        try {
            this.apolloOpenApiClient.createAppNamespace(openAppNamespaceDTO);
        } catch (RuntimeException e) {
            logger.error("create public namespace fail. public namespace's name = " + publicNamespaceName, e);
        }

        // still execute follow operations

        // publish
        NamespaceReleaseDTO namespaceReleaseDTO = new NamespaceReleaseDTO();
        namespaceReleaseDTO.setReleasedBy(this.operateUser);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
        final String currentDateString = simpleDateFormat.format(new Date());
        namespaceReleaseDTO.setReleaseTitle(currentDateString + "-first-publish-release");

        this.apolloOpenApiClient.publishNamespace(this.operatedAppId, this.env, this.clusterName, publicNamespaceName, namespaceReleaseDTO);

        // authorize
        // TODO

        return new Object();
    }

    private void ensurePublicNamespaceExists(String publicNamespaceName) {
        // query by apollo portal
        try {
            this.publicNamespaceNames.computeIfAbsent(publicNamespaceName, key -> this.apolloOpenApiClient.getNamespace(operatedAppId, env, clusterName, key));
        } catch (RuntimeException e) {
            this.logger.warn("get public namespace [{}] meet an RuntimeException, may not exists in apollo. exception message = '{}'", publicNamespaceName, e.getMessage());
        }

        this.publicNamespaceNames.computeIfAbsent(publicNamespaceName, this::resolvePublicNamespaceInApollo);
    }

    public void registryProjectIfNotExists(String projectName) {
        final String publicNamespaceName = this.sentinelApolloLogicService.resolvePublicNamespaceName(projectName);
        if (this.publicNamespaceNames.containsKey(publicNamespaceName)) {
            return;
        }
        this.ensurePublicNamespaceExists(publicNamespaceName);
    }

    private OpenItemDTO resolveOpenItemDTO(String projectName, RuleType ruleType, List<? extends Rule> rules) {
        OpenItemDTO openItemDTO = new OpenItemDTO();
        final String ruleKey = this.sentinelApolloLogicService.resolveFlowRulesKey(projectName, ruleType);
        openItemDTO.setKey(ruleKey);

        // TODO, use json converter in spring-cloud-starter-alibaba-sentinel defined in SentinelConverterConfiguration?
        final String value = DataSourceConverterUtils.serializeToString(rules);

        openItemDTO.setValue(value);
        openItemDTO.setDataChangeCreatedBy(this.operateUser);

        return openItemDTO;
    }

    private void createOrUpdateConfig(String projectName, RuleType ruleType, List<? extends Rule> rules) {
        final String publicNamespaceName = this.sentinelApolloLogicService.resolvePublicNamespaceName(projectName);
        OpenItemDTO openItemDTO = this.resolveOpenItemDTO(projectName, ruleType, rules);
        apolloOpenApiClient.createOrUpdateItem(this.operatedAppId, this.env, this.clusterName, publicNamespaceName, openItemDTO);
    }

    private NamespaceReleaseDTO resolveNamespaceReleaseDTO() {
        NamespaceReleaseDTO namespaceReleaseDTO = new NamespaceReleaseDTO();
        namespaceReleaseDTO.setReleasedBy(this.operateUser);
        namespaceReleaseDTO.setReleaseTitle("sentinel dashboard operate on " + DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
        return namespaceReleaseDTO;
    }

    private void publishConfig(String projectName) {
        final String publicNamespaceName = this.sentinelApolloLogicService.resolvePublicNamespaceName(projectName);
        NamespaceReleaseDTO namespaceReleaseDTO = this.resolveNamespaceReleaseDTO();
        apolloOpenApiClient.publishNamespace(this.operatedAppId, this.env, this.clusterName, publicNamespaceName, namespaceReleaseDTO);
    }

    public CompletableFuture<Void> setRulesAsync(String projectName, RuleType ruleType, List<? extends Rule> rules) {
        this.registryProjectIfNotExists(projectName);
        Runnable runnable = () -> {
            this.createOrUpdateConfig(projectName, ruleType, rules);
            this.publishConfig(projectName);
        };
        return CompletableFuture.runAsync(runnable);
    }

    public boolean setRules(String projectName, RuleType ruleType, List<? extends Rule> rules) {
        CompletableFuture<Void> completableFuture = this.setRulesAsync(projectName, ruleType, rules);
        try {
            completableFuture.get();
            return true;
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("wait fail. setRules of project " + projectName, e);
        }

        return false;
    }

    private void setRules(String projectName, Map<RuleType, List<? extends Rule>> ruleTypeListMap) {
        // create or update config
        for (Map.Entry<RuleType, List<? extends Rule>> entry : ruleTypeListMap.entrySet()) {
            RuleType ruleType = entry.getKey();
            List<? extends Rule> rules = entry.getValue();
            this.createOrUpdateConfig(projectName, ruleType, rules);
        }

        // publish config
        this.publishConfig(projectName);
    }

    public void setRules(Map<String, Map<RuleType, List<? extends Rule>>> projectName2rules) {
        for (Map.Entry<String, Map<RuleType, List<? extends Rule>>> entry : projectName2rules.entrySet()) {
            String projectName = entry.getKey();
            Map<RuleType, List<? extends Rule>> ruleTypeListMap = entry.getValue();
            // TODO, consider that parallel with each project
            this.setRules(projectName, ruleTypeListMap);
        }
    }

    /**
     * @return project's name in sentinel dashboard's cache
     */
    public Set<String> listCachedProjectNames() {
        return this.publicNamespaceNames.keySet().stream().map(this.sentinelApolloLogicService::deResolvePublicNamespaceName).collect(Collectors.toSet());
    }

    public void clearCacheOfProject(String projectName) {
        String publicNamespaceName = this.sentinelApolloLogicService.resolvePublicNamespaceName(projectName);
        this.publicNamespaceNames.remove(publicNamespaceName);
    }

    /**
     * this method is not atomic.
     * maybe exists concurrent problem with {@link #ensurePublicNamespaceExists(String)}.
     *
     * @return project's names which cleared in cache
     */
    public Set<String> clearAllCachedProjectNames() {
        Set<String> projectNames = new TreeSet<>(this.listCachedProjectNames());
        for (String projectName : projectNames) {
            this.clearCacheOfProject(projectName);
        }
        return projectNames;
    }

    public Map<RuleType, List<? extends Rule>> getRules(String projectName) {
        final String publicNamespaceName = this.sentinelApolloLogicService.resolvePublicNamespaceName(projectName);
        OpenNamespaceDTO openNamespaceDTO = this.apolloOpenApiClient.getNamespace(this.operatedAppId, this.env, this.clusterName, publicNamespaceName);

        Map<RuleType, List<? extends Rule>> rules = new HashMap<>();

        Map<String, String> keyValues = toKeyValues(openNamespaceDTO.getItems());
        for (RuleType ruleType : RuleType.values()) {
            String ruleKey = this.sentinelApolloLogicService.resolveFlowRulesKey(projectName, ruleType);
            if (keyValues.containsKey(ruleKey)) {
                List<? extends Rule> ruleList = DataSourceConverterUtils.deserialize(keyValues.get(ruleKey), ruleType);
                rules.put(ruleType, ruleList);
            }
        }

        return rules;
    }

    private Set<String> getAllProjectNamesInApollo() {
        List<OpenNamespaceDTO> openNamespaceDTOS = this.apolloOpenApiClient.getNamespaces(this.operatedAppId, this.env, this.clusterName);
        return openNamespaceDTOS.stream()
                .filter(OpenNamespaceDTO::isPublic)
                .map(OpenNamespaceDTO::getNamespaceName)
                .filter(this.sentinelApolloLogicService::isProjectPublicNamespaceName)
                .map(this.sentinelApolloLogicService::deResolvePublicNamespaceName)
                .collect(Collectors.toSet());
    }

    /**
     * all project's rule config.
     * key is the project's name, value is its rules.
     */
    public Map<String, Map<RuleType, List<? extends Rule>>> getRules() {
        // find all projects
        Set<String> projectNames = this.getAllProjectNamesInApollo();

        // TODO, change to parallel
        Map<String, Map<RuleType, List<? extends Rule>>> projectName2Rules = new HashMap<>();
        for (String projectName : projectNames) {
            Map<RuleType, List<? extends Rule>> rules = this.getRules(projectName);
            projectName2Rules.put(projectName, rules);
        }

        return projectName2Rules;
    }

}