package com.labwatch.asset.application;

import com.labwatch.asset.domain.Device;
import com.labwatch.asset.domain.DeviceRepository;
import com.labwatch.asset.domain.DuplicateResourceException;
import com.labwatch.asset.domain.Metric;
import com.labwatch.asset.domain.MonitoringPolicy;
import com.labwatch.asset.domain.MonitoringPolicyRepository;
import com.labwatch.asset.domain.ResourceNotFoundException;
import com.labwatch.asset.domain.Severity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MonitoringPolicyService {

    private final MonitoringPolicyRepository policies;
    private final DeviceRepository devices;
    private final Clock clock;

    public MonitoringPolicyService(MonitoringPolicyRepository policies, DeviceRepository devices, Clock clock) {
        this.policies = policies;
        this.devices = devices;
        this.clock = clock;
    }

    public MonitoringPolicy create(String deviceId, Metric metric, BigDecimal minimum, BigDecimal maximum,
                                   int violationDurationSeconds, Severity severity) {
        Device device = getDevice(deviceId);
        if (policies.existsByDeviceIdAndMetric(device.getId(), metric)) {
            throw new DuplicateResourceException(
                    "Policy for metric %s already exists on device %s".formatted(metric, deviceId));
        }
        MonitoringPolicy policy = MonitoringPolicy.create(device, metric, minimum, maximum,
                violationDurationSeconds, severity, Instant.now(clock));
        return policies.save(policy);
    }

    @Transactional(readOnly = true)
    public List<MonitoringPolicy> findByDevice(String deviceId) {
        return policies.findByDeviceId(getDevice(deviceId).getId());
    }

    public MonitoringPolicy update(String deviceId, UUID policyId, BigDecimal minimum, BigDecimal maximum,
                                   int violationDurationSeconds, Severity severity) {
        MonitoringPolicy policy = getPolicy(deviceId, policyId);
        policy.update(minimum, maximum, violationDurationSeconds, severity, Instant.now(clock));
        return policy;
    }

    public void delete(String deviceId, UUID policyId) {
        policies.delete(getPolicy(deviceId, policyId));
    }

    private Device getDevice(String deviceId) {
        return devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    }

    private MonitoringPolicy getPolicy(String deviceId, UUID policyId) {
        return policies.findByIdAndDeviceId(policyId, getDevice(deviceId).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found: " + policyId));
    }
}
