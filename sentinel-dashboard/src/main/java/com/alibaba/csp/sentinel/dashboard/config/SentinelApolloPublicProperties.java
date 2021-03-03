package com.alibaba.csp.sentinel.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;

@ConfigurationProperties(prefix = "sentinel.apollo.public")
@Validated
public class SentinelApolloPublicProperties {

    @NotEmpty
    private String orgId;

    @NotEmpty
    private String dashboardAppId;

    @NotEmpty
    private String namespacePrefix;

    @NotEmpty
    private String flowRulesKeySuffix;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getDashboardAppId() {
        return dashboardAppId;
    }

    public void setDashboardAppId(String dashboardAppId) {
        this.dashboardAppId = dashboardAppId;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String getFlowRulesKeySuffix() {
        return flowRulesKeySuffix;
    }

    public void setFlowRulesKeySuffix(String flowRulesKeySuffix) {
        this.flowRulesKeySuffix = flowRulesKeySuffix;
    }
}
