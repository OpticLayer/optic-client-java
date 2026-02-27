package com.optic.sdk;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class OpticConfig {
    private String apiKey = "";
    private String serviceName = "";
    private String endpoint = "http://localhost:8080";
    private String environment = "local";
    private String serviceVersion = "";
    private boolean enableTraces = true;
    private boolean enableMetrics = true;
    private boolean enableLogs = true;
    private Duration exportInterval = Duration.ofSeconds(10);

    public static OpticConfig fromEnv() {
        OpticConfig cfg = new OpticConfig();
        Map<String, String> env = System.getenv();

        cfg.apiKey = firstNonBlank(env.get("OPTIC_API_KEY"), env.get("OTEL_API_KEY"));
        cfg.serviceName = firstNonBlank(env.get("OPTIC_SERVICE_NAME"), env.get("OTEL_SERVICE_NAME"));
        cfg.endpoint = firstNonBlank(env.get("OPTIC_ENDPOINT"), env.get("OTEL_EXPORTER_OTLP_ENDPOINT"), cfg.endpoint);
        cfg.environment = firstNonBlank(env.get("OPTIC_ENVIRONMENT"), cfg.environment);
        cfg.serviceVersion = firstNonBlank(env.get("OPTIC_SERVICE_VERSION"));

        cfg.enableTraces = parseBoolean(env.get("OPTIC_ENABLE_TRACES"), cfg.enableTraces);
        cfg.enableMetrics = parseBoolean(env.get("OPTIC_ENABLE_METRICS"), cfg.enableMetrics);
        cfg.enableLogs = parseBoolean(env.get("OPTIC_ENABLE_LOGS"), cfg.enableLogs);

        long intervalMs = parseLong(env.get("OPTIC_EXPORT_INTERVAL_MS"), -1L);
        if (intervalMs <= 0) {
            intervalMs = parseLong(env.get("OTEL_METRIC_EXPORT_INTERVAL"), -1L);
        }
        if (intervalMs > 0) {
            cfg.exportInterval = Duration.ofMillis(intervalMs);
        }

        return cfg;
    }

    public void validate() {
        if (!enableMetrics && !enableTraces && !enableLogs) {
            return;
        }
        if (isBlank(apiKey)) {
            throw new IllegalArgumentException("Optic API key is required. Set OPTIC_API_KEY or OTEL_API_KEY");
        }
        if (isBlank(serviceName)) {
            throw new IllegalArgumentException("service name is required. Set OPTIC_SERVICE_NAME or OTEL_SERVICE_NAME");
        }
        if (isBlank(endpoint)) {
            throw new IllegalArgumentException("endpoint must not be empty");
        }
        if (exportInterval == null || exportInterval.isZero() || exportInterval.isNegative()) {
            throw new IllegalArgumentException("exportInterval must be greater than zero");
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public OpticConfig setApiKey(String apiKey) {
        this.apiKey = nullToEmpty(apiKey).trim();
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public OpticConfig setServiceName(String serviceName) {
        this.serviceName = nullToEmpty(serviceName).trim();
        return this;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public OpticConfig setEndpoint(String endpoint) {
        String normalized = nullToEmpty(endpoint).trim();
        if (!normalized.isEmpty()) {
            this.endpoint = normalized;
        }
        return this;
    }

    public String getEnvironment() {
        return environment;
    }

    public OpticConfig setEnvironment(String environment) {
        String normalized = nullToEmpty(environment).trim();
        if (!normalized.isEmpty()) {
            this.environment = normalized;
        }
        return this;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public OpticConfig setServiceVersion(String serviceVersion) {
        this.serviceVersion = nullToEmpty(serviceVersion).trim();
        return this;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public OpticConfig setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
        return this;
    }

    public boolean isEnableTraces() {
        return enableTraces;
    }

    public OpticConfig setEnableTraces(boolean enableTraces) {
        this.enableTraces = enableTraces;
        return this;
    }

    public boolean isEnableLogs() {
        return enableLogs;
    }

    public OpticConfig setEnableLogs(boolean enableLogs) {
        this.enableLogs = enableLogs;
        return this;
    }

    public Duration getExportInterval() {
        return exportInterval;
    }

    public OpticConfig setExportInterval(Duration exportInterval) {
        if (exportInterval != null && !exportInterval.isZero() && !exportInterval.isNegative()) {
            this.exportInterval = exportInterval;
        }
        return this;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (isBlank(raw)) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        if (Objects.equals(normalized, "true") || Objects.equals(normalized, "1") || Objects.equals(normalized, "yes")) {
            return true;
        }
        if (Objects.equals(normalized, "false") || Objects.equals(normalized, "0") || Objects.equals(normalized, "no")) {
            return false;
        }
        return fallback;
    }

    private static long parseLong(String raw, long fallback) {
        if (isBlank(raw)) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
