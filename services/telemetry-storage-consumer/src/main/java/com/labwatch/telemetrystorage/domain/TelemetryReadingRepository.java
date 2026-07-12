package com.labwatch.telemetrystorage.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {

    boolean existsByEventId(UUID eventId);

    List<TelemetryReading> findByDeviceIdOrderByOccurredAt(String deviceId);
}
