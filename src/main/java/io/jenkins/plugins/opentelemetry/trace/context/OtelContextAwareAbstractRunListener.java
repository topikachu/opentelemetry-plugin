package io.jenkins.plugins.opentelemetry.trace.context;

import static com.google.common.base.Verify.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.opentelemetry.trace.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * {@link RunListener} that setups the OpenTelemetry {@link io.opentelemetry.context.Context}
 * with the current {@link Span}.
 */
public abstract class OtelContextAwareAbstractRunListener extends RunListener<Run> {

    private final static Logger LOGGER = Logger.getLogger(OtelContextAwareAbstractRunListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
        this.tracer = this.otelTraceService.getTracer();
    }

    @Override
    public final void onCompleted(Run run, @NonNull TaskListener listener) {
        try (Scope ignored = getTraceService().setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onCompleted(run, listener);
        }
    }

    public void _onCompleted(Run run, @NonNull TaskListener listener) {
    }

    @Override
    public final void onFinalized(Run run) {
        try (Scope ignored = getTraceService().setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onFinalized(run);
        }
    }


    public void _onFinalized(Run run) {
    }

    @Override
    public final void onInitialize(Run run) {
        Scope ignored = getTraceService().setupContext(run);
        verify(ignored == null, "No span should be defined for %s");
        this._onInitialize(run);
    }

    public void _onInitialize(Run run) {
    }

    @Override
    public final void onStarted(Run run, TaskListener listener) {
        try (Scope ignored = getTraceService().setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onStarted(run, listener);
        }
    }

    public void _onStarted(Run run, TaskListener listener) {
    }

    @Override
    public final Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        try (Scope ignored = getTraceService().setupContext(build)) {
            verifyNotNull(ignored, "No span found for %s", build);
            return this._setUpEnvironment(build, launcher, listener);
        }
    }

    public Environment _setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        return new Environment() {
        };
    }

    @Override
    public final void onDeleted(Run run) {
        try (Scope ignored = getTraceService().setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onDeleted(run);
        }
    }

    public void _onDeleted(Run run) {
    }

    public OtelTraceService getTraceService() {
        return otelTraceService;
    }

    public Tracer getTracer() {
        return tracer;
    }
}
