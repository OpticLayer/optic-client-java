package com.optic.sdk.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "optic")
public class OpticProperties {
    private boolean enabled = true;
    private String apiKey;
    private String serviceName;
    private String endpoint = "http://localhost:8080";
    private String environment = "local";
    private String serviceVersion;
    private boolean enableTraces = true;
    private boolean enableMetrics = true;
    private boolean enableLogs = true;
    private Duration exportInterval = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public boolean isEnableTraces() {
        return enableTraces;
    }

    public void setEnableTraces(boolean enableTraces) {
        this.enableTraces = enableTraces;
    }

    public boolean isEnableLogs() {
        return enableLogs;
    }

    public void setEnableLogs(boolean enableLogs) {
        this.enableLogs = enableLogs;
    }

    public Duration getExportInterval() {
        return exportInterval;
    }

    public void setExportInterval(Duration exportInterval) {
        this.exportInterval = exportInterval;
    }
}
