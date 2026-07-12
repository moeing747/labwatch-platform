package com.labwatch.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    /** Business identifier reported by the device itself, e.g. "chamber-042". */
    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Device() {
    }

    private Device(UUID id, String deviceId, String name, UUID locationId, Instant now) {
        this.id = id;
        this.deviceId = deviceId;
        this.name = name;
        this.locationId = locationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Device create(String deviceId, String name, UUID locationId, Instant now) {
        return new Device(UUID.randomUUID(), deviceId, name, locationId, now);
    }

    public void update(String name, UUID locationId, Instant now) {
        this.name = name;
        this.locationId = locationId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
