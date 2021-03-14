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
import com.alibaba.csp.sentinel.dashboard.config.SentinelApolloPublicProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * logic layer between sentinel's project and apollo's namespace, key.
 *
 * @author wxq
 */
@Service
public class SentinelApolloLogicService {

    private final SentinelApolloPublicProperties sentinelApolloPublicProperties;

    private final Integer projectNameLengthLimit;

    public SentinelApolloLogicService(
            SentinelApolloPublicProperties sentinelApolloPublicProperties,
            SentinelApolloOpenApiProperties sentinelApolloOpenApiProperties
    ) {
        this.sentinelApolloPublicProperties = sentinelApolloPublicProperties;
        this.projectNameLengthLimit = sentinelApolloOpenApiProperties.getNamespaceLengthLimit() - sentinelApolloPublicProperties.getNamespacePrefix().length();
    }

    /**
     * @return which public namespace save this project's config.
     * @throws IllegalArgumentException if projectName's length is longer than {@link #projectNameLengthLimit}
     */
    public String resolvePublicNamespaceName(String projectName) {
        if (projectName.length() > projectNameLengthLimit) {
            String message = String.format("project.name [%s] is too long. Its length should not exceed %d", projectName, this.projectNameLengthLimit);
            throw new IllegalArgumentException(message);
        }
        return this.sentinelApolloPublicProperties.getNamespacePrefix() + projectName;
    }

    public boolean isProjectPublicNamespaceName(String publicNamespaceName) {
        return publicNamespaceName.startsWith(this.sentinelApolloPublicProperties.getNamespacePrefix());
    }

    /**
     * reverse operation with {@link #resolvePublicNamespaceName(String)}.
     * @throws IllegalArgumentException if publicNamespaceName is not valid
     */
    public String deResolvePublicNamespaceName(String publicNamespaceName) {
        Assert.notNull(publicNamespaceName, "public namespace name should not be null");
        final String namespacePrefix = this.sentinelApolloPublicProperties.getNamespacePrefix();
        final int namespacePrefixLength = namespacePrefix.length();
        Assert.isTrue(
                publicNamespaceName.length() > namespacePrefixLength,
                "public namespace name's length " + publicNamespaceName.length() + "should bigger that namespace prefix " + namespacePrefix + "'s length" + namespacePrefixLength
        );
        Assert.isTrue(this.isProjectPublicNamespaceName(publicNamespaceName), "public namespace name " + publicNamespaceName + " is not belong to any sentinel project");
        return publicNamespaceName.substring(namespacePrefixLength);
    }

    /**
     * @see com.alibaba.cloud.sentinel.datasource.config.ApolloDataSourceProperties#setFlowRulesKey(String)
     */
    public String resolveFlowRulesKey(String projectName, RuleType ruleType) {
        return projectName + this.sentinelApolloPublicProperties.getSuffix().get(ruleType);
    }
}