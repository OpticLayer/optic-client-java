package com.observex.sdk.spring;

import com.observex.sdk.ObserveX;
import com.observex.sdk.ObserveXConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(ObserveX.class)
@EnableConfigurationProperties(ObservexProperties.class)
@ConditionalOnProperty(prefix = "observex", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservexAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ObserveX observexSdk(ObservexProperties properties, Environment environment) {
        ObserveXConfig config = buildConfig(properties, environment);
        return ObserveX.init(config);
    }

    @Bean
    @ConditionalOnClass({MeterRegistry.class, OpenTelemetryMeterRegistry.class})
    @ConditionalOnProperty(prefix = "observex", name = "enable-metrics", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "observexOpenTelemetryMeterRegistry")
    public MeterRegistry observexOpenTelemetryMeterRegistry(ObserveX observeX) {
        MeterRegistry registry = OpenTelemetryMeterRegistry.builder(observeX.getOpenTelemetry()).build();

        List<String> commonTags = new ArrayList<>();
        ObserveXConfig cfg = observeX.getConfig();
        addTag(commonTags, "service.name", cfg.getServiceName());
        addTag(commonTags, "deployment.environment", cfg.getEnvironment());
        addTag(commonTags, "service.version", cfg.getServiceVersion());

        if (!commonTags.isEmpty()) {
            registry.config().commonTags(commonTags.toArray(new String[0]));
        }

        return registry;
    }

    @Bean
    @ConditionalOnClass({Filter.class, FilterRegistrationBean.class})
    @ConditionalOnMissingBean(name = "observexHttpTelemetryFilter")
    public FilterRegistrationBean<Filter> observexHttpTelemetryFilter(ObserveX observeX) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setName("observexHttpTelemetryFilter");
        registration.setFilter(new ObservexHttpTelemetryFilter(observeX));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    private static ObserveXConfig buildConfig(ObservexProperties properties, Environment environment) {
        ObserveXConfig config = ObserveXConfig.fromEnv();

        if (hasText(properties.getApiKey())) {
            config.setApiKey(properties.getApiKey());
        }
        if (hasText(properties.getServiceName())) {
            config.setServiceName(properties.getServiceName());
        }
        if (hasText(properties.getEndpoint())) {
            config.setEndpoint(properties.getEndpoint());
        }
        if (hasText(properties.getEnvironment())) {
            config.setEnvironment(properties.getEnvironment());
        }
        if (hasText(properties.getServiceVersion())) {
            config.setServiceVersion(properties.getServiceVersion());
        }
        config.setEnableTraces(properties.isEnableTraces());
        config.setEnableMetrics(properties.isEnableMetrics());
        config.setEnableLogs(properties.isEnableLogs());
        config.setExportInterval(properties.getExportInterval());

        if (!hasText(config.getServiceName())) {
            config.setServiceName(environment.getProperty("spring.application.name", ""));
        }

        return config;
    }

    private static void addTag(List<String> tags, String key, String value) {
        if (hasText(value)) {
            tags.add(key);
            tags.add(value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
