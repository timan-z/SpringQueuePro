package com.springqprobackend.springqpro.controller.rest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.Timer;

@RestController
@RequestMapping("/api/internal")
@PreAuthorize("isAuthenticated()")
public class SystemHealthController {

    private final HealthEndpoint health;      // returns Map-style JSON (always works)
    private final MeterRegistry registry;

    public SystemHealthController(HealthEndpoint health, MeterRegistry registry) {
        this.health = health;
        this.registry = registry;
    }

    // -----------------------------
    // 1) Return Actuator health JSON
    // -----------------------------
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        HealthComponent hc = health.health();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", hc.getStatus().getCode());

        if (hc instanceof CompositeHealth composite) {
            Map<String, Object> sub = new LinkedHashMap<>();
            composite.getComponents().forEach((name, comp) -> {
                sub.put(name, Map.of(
                        "status", comp.getStatus().getCode()
                ));
            });
            out.put("components", sub);
        }

        return out;
    }

    // -----------------------------
    // 2) Return simple metric values
    // -----------------------------
    @GetMapping("/metric/{name}")
    public Map<String, Object> getMetric(@PathVariable String name) {

        // Try Gauge
        Gauge g = registry.find(name).gauge();
        if (g != null) {
            return Map.of(
                    "name", name,
                    "type", "gauge",
                    "value", g.value()
            );
        }

        // Try Timer
        io.micrometer.core.instrument.Timer t = registry.find(name).timer();
        if (t != null) {
            return Map.of(
                    "name", name,
                    "type", "timer",
                    "totalMs", t.totalTime(TimeUnit.MILLISECONDS),
                    "count", t.count(),
                    "meanMs", t.mean(TimeUnit.MILLISECONDS)
            );
        }

        // Fallback
        return Map.of(
                "name", name,
                "type", "unknown",
                "value", null
        );
    }
}
