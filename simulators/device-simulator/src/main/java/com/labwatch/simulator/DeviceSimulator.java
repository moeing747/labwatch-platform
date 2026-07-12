package com.labwatch.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DeviceSimulator {

    private final SimulatorConfig config;
    private final TelemetryGenerator generator;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong tickCounter = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final Instant startedAt = Instant.now();

    public DeviceSimulator(SimulatorConfig config) {
        this.config = config;
        this.generator = new TelemetryGenerator(config.failureProfile(), config.seed());
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static void main(String[] args) throws InterruptedException {
        DeviceSimulator simulator = new DeviceSimulator(SimulatorConfig.fromArgs(args));
        simulator.run();
    }

    private void run() throws InterruptedException {
        System.out.printf("Simulating %d devices at %.1f events/s against %s (profile: %s)%n",
                config.devices(), config.eventsPerSecond(), config.target(), config.failureProfile());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long periodMillis = Math.max(1, Math.round(1000.0 / config.eventsPerSecond()));
        scheduler.scheduleAtFixedRate(this::sendOne, 0, periodMillis, TimeUnit.MILLISECONDS);

        if (config.durationSeconds() > 0) {
            scheduler.schedule(() -> {
                scheduler.shutdown();
                System.out.printf("Done: %d sent, %d failed%n", sent.get(), failed.get());
            }, config.durationSeconds(), TimeUnit.SECONDS);
        }
        scheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    private void sendOne() {
        int deviceIndex = (int) (tickCounter.getAndIncrement() % config.devices());
        String deviceId = "sim-chamber-%03d".formatted(deviceIndex);
        Duration elapsed = Duration.between(startedAt, Instant.now());
        TelemetryGenerator.Reading reading = generator.next(deviceIndex, elapsed);

        ObjectNode body = objectMapper.createObjectNode()
                .put("deviceId", deviceId)
                .put("timestamp", Instant.now().toString())
                .put("temperature", reading.temperature())
                .put("humidity", reading.humidity())
                .put("operatingState", "RUNNING");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.target() + "/api/telemetry"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                long count = sent.incrementAndGet();
                if (count % 50 == 0) {
                    System.out.printf("%d events sent (last: %s temp=%s)%n", count, deviceId, reading.temperature());
                }
            } else {
                failed.incrementAndGet();
                System.err.printf("Rejected (%d): %s%n", response.statusCode(), response.body());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            failed.incrementAndGet();
            System.err.println("Send failed (telemetry service not reachable yet?): " + exception.getMessage());
        }
    }
}
