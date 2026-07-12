package com.labwatch.asset.application;

import com.labwatch.asset.domain.Device;
import com.labwatch.asset.domain.DeviceRepository;
import com.labwatch.asset.domain.DuplicateResourceException;
import com.labwatch.asset.domain.LocationRepository;
import com.labwatch.asset.domain.ResourceNotFoundException;
import com.labwatch.asset.messaging.PolicyEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceService {

    private final DeviceRepository devices;
    private final LocationRepository locations;
    private final PolicyEventPublisher publisher;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, LocationRepository locations,
                         PolicyEventPublisher publisher, Clock clock) {
        this.devices = devices;
        this.locations = locations;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Device create(String deviceId, String name, UUID locationId) {
        if (devices.existsByDeviceId(deviceId)) {
            throw new DuplicateResourceException("Device already exists: " + deviceId);
        }
        requireLocationExists(locationId);
        return devices.save(Device.create(deviceId, name, locationId, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Device getByDeviceId(String deviceId) {
        return devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    }

    @Transactional(readOnly = true)
    public List<Device> findAll() {
        return devices.findAll();
    }

    public Device update(String deviceId, String name, UUID locationId) {
        Device device = getByDeviceId(deviceId);
        requireLocationExists(locationId);
        device.update(name, locationId, Instant.now(clock));
        return device;
    }

    public void delete(String deviceId) {
        devices.delete(getByDeviceId(deviceId));
        // The DB cascade removes the device's policies; without this snapshot the
        // monitoring service would keep enforcing them against a deleted device.
        publisher.publishPolicySnapshot(deviceId, List.of());
    }

    private void requireLocationExists(UUID locationId) {
        if (locationId != null && !locations.existsById(locationId)) {
            throw new ResourceNotFoundException("Location not found: " + locationId);
        }
    }
}
