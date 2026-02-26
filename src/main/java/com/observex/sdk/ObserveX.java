package com.observex.sdk;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

public final class ObserveX implements AutoCloseable {
    public static final String VERSION = "0.1.0";

    private static final Object LOCK = new Object();
    private static ObserveX instance;

    private final ObserveXConfig config;
    private final OpenTelemetry openTelemetry;
    private final OpenTelemetrySdk sdk;

    private volatile boolean closed;

    private ObserveX(ObserveXConfig config, OpenTelemetry openTelemetry, OpenTelemetrySdk sdk) {
        this.config = config;
        this.openTelemetry = openTelemetry;
        this.sdk = sdk;
    }

    public static ObserveX init() {
        return init(ObserveXConfig.fromEnv());
    }

    public static ObserveX init(ObserveXConfig config) {
        synchronized (LOCK) {
            if (instance != null) {
                return instance;
            }

            ObserveXConfig effective = config == null ? ObserveXConfig.fromEnv() : config;
            effective.validate();

            ObserveX created;
            if (!effective.isEnableMetrics() && !effective.isEnableTraces() && !effective.isEnableLogs()) {
                created = new ObserveX(effective, OpenTelemetry.noop(), null);
            } else {
                Resource resource = buildResource(effective);
                String authValue = "Bearer " + effective.getApiKey();
                OpenTelemetrySdkBuilder sdkBuilder = OpenTelemetrySdk.builder();

                if (effective.isEnableTraces()) {
                    OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                            .setEndpoint(signalEndpoint(effective.getEndpoint(), "/otlp/v1/traces"))
                            .addHeader("Authorization", authValue)
                            .build();
                    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                            .setResource(resource)
                            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                            .build();
                    sdkBuilder = sdkBuilder.setTracerProvider(tracerProvider);
                }

                if (effective.isEnableMetrics()) {
                    OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
                            .setEndpoint(signalEndpoint(effective.getEndpoint(), "/otlp/v1/metrics"))
                            .addHeader("Authorization", authValue)
                            .build();
                    PeriodicMetricReader reader = PeriodicMetricReader.builder(metricExporter)
                            .setInterval(effective.getExportInterval())
                            .build();
                    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                            .setResource(resource)
                            .registerMetricReader(reader)
                            .build();
                    sdkBuilder = sdkBuilder.setMeterProvider(meterProvider);
                }

                if (effective.isEnableLogs()) {
                    OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
                            .setEndpoint(signalEndpoint(effective.getEndpoint(), "/otlp/v1/logs"))
                            .addHeader("Authorization", authValue)
                            .build();
                    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                            .setResource(resource)
                            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                            .build();
                    sdkBuilder = sdkBuilder.setLoggerProvider(loggerProvider);
                }

                OpenTelemetrySdk sdk = sdkBuilder.buildAndRegisterGlobal();

                created = new ObserveX(effective, sdk, sdk);
            }

            instance = created;
            return created;
        }
    }

    public static boolean isInitialized() {
        synchronized (LOCK) {
            return instance != null;
        }
    }

    public static void shutdownGlobal() {
        ObserveX current;
        synchronized (LOCK) {
            current = instance;
        }
        if (current != null) {
            current.shutdown();
        }
    }

    public Meter meter(String instrumentationScope) {
        return openTelemetry.getMeter(instrumentationScope);
    }

    public Tracer tracer(String instrumentationScope) {
        return openTelemetry.getTracer(instrumentationScope);
    }

    public Logger logger(String instrumentationScope) {
        return openTelemetry.getLogsBridge().loggerBuilder(instrumentationScope).build();
    }

    public ObserveXConfig getConfig() {
        return config;
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    public void shutdown() {
        if (closed) {
            return;
        }
        synchronized (LOCK) {
            if (closed) {
                return;
            }
            if (sdk != null) {
                sdk.shutdown().join(10, TimeUnit.SECONDS);
            }
            closed = true;
            if (instance == this) {
                instance = null;
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private static Resource buildResource(ObserveXConfig config) {
        AttributesBuilder attrs = Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), config.getServiceName())
                .put(AttributeKey.stringKey("deployment.environment"), config.getEnvironment())
                .put(AttributeKey.stringKey("telemetry.sdk.name"), "observex-sdk")
                .put(AttributeKey.stringKey("telemetry.sdk.language"), "java")
                .put(AttributeKey.stringKey("telemetry.sdk.version"), VERSION);

        if (notBlank(config.getServiceVersion())) {
            attrs.put(AttributeKey.stringKey("service.version"), config.getServiceVersion());
        }
        String hostName = resolveHostName();
        if (notBlank(hostName)) {
            attrs.put(AttributeKey.stringKey("host.name"), hostName);
        }

        return Resource.getDefault().merge(Resource.create(attrs.build()));
    }

    private static String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String signalEndpoint(String endpoint, String signalPath) {
        String base = endpoint == null ? "" : endpoint.trim();
        if (base.isEmpty()) {
            base = "http://localhost:8080";
        }
        if (base.endsWith(signalPath)) {
            return base;
        }
        return trimTrailingSlash(base) + signalPath;
    }

    private static String trimTrailingSlash(String value) {
        String output = value;
        while (output.endsWith("/")) {
            output = output.substring(0, output.length() - 1);
        }
        return output;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
