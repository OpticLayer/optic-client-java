package com.observex.sdk.spring;

import com.observex.sdk.ObserveX;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class ObservexHttpTelemetryFilter implements Filter {
    private final Tracer tracer;
    private final Logger logger;
    private final LongCounter requestCounter;
    private final DoubleHistogram requestDurationMs;
    private final boolean tracesEnabled;
    private final boolean logsEnabled;
    private final boolean metricsEnabled;

    ObservexHttpTelemetryFilter(ObserveX observeX) {
        this.tracer = observeX.tracer("observex-http");
        this.logger = observeX.logger("observex-http");
        this.requestCounter = observeX
                .meter("observex-http")
                .counterBuilder("http.server.request.count")
                .setDescription("Total inbound HTTP requests")
                .setUnit("1")
                .build();
        this.requestDurationMs = observeX
                .meter("observex-http")
                .histogramBuilder("http.server.request.duration")
                .setDescription("Inbound HTTP request duration")
                .setUnit("ms")
                .build();

        this.tracesEnabled = observeX.getConfig().isEnableTraces();
        this.logsEnabled = observeX.getConfig().isEnableLogs();
        this.metricsEnabled = observeX.getConfig().isEnableMetrics();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
            chain.doFilter(req, res);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();
        String route = request.getRequestURI();
        Span span = tracesEnabled
                ? tracer.spanBuilder(method + " " + route).setSpanKind(SpanKind.SERVER).startSpan()
                : Span.getInvalid();

        long startedAt = System.nanoTime();
        Throwable failed = null;

        if (logsEnabled) {
            logger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("HTTP " + method + " " + route + " started")
                    .setAttribute(AttributeKey.stringKey("http.method"), method)
                    .setAttribute(AttributeKey.stringKey("http.route"), route)
                    .emit();
        }

        try (Scope ignored = tracesEnabled ? span.makeCurrent() : null) {
            chain.doFilter(req, res);
        } catch (Throwable t) {
            failed = t;
            if (tracesEnabled) {
                span.recordException(t);
                span.setStatus(StatusCode.ERROR, safeMessage(t));
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof ServletException) {
                throw (ServletException) t;
            }
            throw new ServletException(t);
        } finally {
            int status = response.getStatus();
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            Attributes attrs = Attributes.builder()
                    .put(AttributeKey.stringKey("http.method"), method)
                    .put(AttributeKey.stringKey("http.route"), route)
                    .put(AttributeKey.longKey("http.status_code"), status)
                    .build();

            if (metricsEnabled) {
                requestCounter.add(1, attrs);
                requestDurationMs.record(durationMs, attrs);
            }

            if (tracesEnabled) {
                span.setAttribute("http.method", method);
                span.setAttribute("http.route", route);
                span.setAttribute("http.status_code", status);
                if (status >= 400 && failed == null) {
                    span.setStatus(StatusCode.ERROR, "HTTP " + status);
                }
                span.end();
            }

            if (logsEnabled) {
                LogRecordBuilder record = logger.logRecordBuilder()
                        .setBody("HTTP " + method + " " + route + " -> " + status + " in " + Math.round(durationMs) + "ms")
                        .setAttribute(AttributeKey.stringKey("http.method"), method)
                        .setAttribute(AttributeKey.stringKey("http.route"), route)
                        .setAttribute(AttributeKey.longKey("http.status_code"), (long) status)
                        .setAttribute(AttributeKey.doubleKey("http.duration_ms"), durationMs);
                if (failed != null) {
                    record.setAttribute(AttributeKey.stringKey("exception.message"), safeMessage(failed));
                }

                if (status >= 500 || failed != null) {
                    record.setSeverity(Severity.ERROR);
                } else if (status >= 400 || durationMs >= 1000.0) {
                    record.setSeverity(Severity.WARN);
                } else {
                    record.setSeverity(Severity.INFO);
                }
                record.emit();
            }
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
