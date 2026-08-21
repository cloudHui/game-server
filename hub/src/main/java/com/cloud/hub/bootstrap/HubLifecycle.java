package com.cloud.hub.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HubLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(HubLifecycle.class);
    private final EnumMap<HubComponent, ComponentState> states = new EnumMap<>(HubComponent.class);
    private final List<ManagedComponent> started = new ArrayList<>();

    public HubLifecycle() {
        for (HubComponent component : HubComponent.values()) states.put(component, ComponentState.NOT_STARTED);
    }

    public synchronized void start(List<? extends ManagedComponent> components) {
        for (ManagedComponent managed : components) {
            HubComponent component = managed.component();
            requireExpectedOrder(component);
            states.put(component, ComponentState.STARTING);
            try {
                managed.start();
                started.add(managed);
                states.put(component, ComponentState.READY);
                logger.info("Hub component ready: {}", component);
            } catch (Exception e) {
                states.put(component, ComponentState.FAILED);
                rollback();
                throw new IllegalStateException("Hub component failed: " + component, e);
            }
        }
    }

    public synchronized void markReady(HubComponent component) { states.put(component, ComponentState.READY); }
    public synchronized void markDegraded(HubComponent component) { states.put(component, ComponentState.DEGRADED); }
    public synchronized ComponentState state(HubComponent component) { return states.get(component); }

    public synchronized Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (HubComponent component : HubComponent.values())
            result.put(component.name().toLowerCase(), states.get(component).name());
        return Collections.unmodifiableMap(result);
    }

    public synchronized boolean isReady() {
        for (ComponentState state : states.values()) if (state != ComponentState.READY) return false;
        return true;
    }

    public synchronized boolean isDegraded() {
        for (ComponentState state : states.values()) if (state == ComponentState.DEGRADED) return true;
        return false;
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void close() {
        for (HubComponent component : HubComponent.values())
            if (states.get(component) == ComponentState.READY || states.get(component) == ComponentState.DEGRADED)
                states.put(component, ComponentState.STOPPING);
        rollback();
        for (HubComponent component : HubComponent.values())
            if (states.get(component) == ComponentState.STOPPING || states.get(component) == ComponentState.READY)
                states.put(component, ComponentState.STOPPED);
    }

    private void requireExpectedOrder(HubComponent component) {
        int expectedIndex = started.size();
        if (expectedIndex >= HubComponent.values().length ||
                HubComponent.values()[expectedIndex] != component)
            throw new IllegalArgumentException("Unexpected component order: " + component);
    }

    private void rollback() {
        for (int i = started.size() - 1; i >= 0; i--) {
            ManagedComponent managed = started.get(i);
            try { managed.stop(); }
            catch (Exception e) { logger.error("Hub component stop failed: {}", managed.component(), e); }
            finally { states.put(managed.component(), ComponentState.STOPPED); }
        }
        started.clear();
    }
}
