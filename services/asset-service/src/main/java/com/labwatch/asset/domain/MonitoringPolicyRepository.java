package com.labwatch.asset.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringPolicyRepository extends JpaRepository<MonitoringPolicy, UUID> {

    List<MonitoringPolicy> findByDeviceId(UUID deviceId);

    Optional<MonitoringPolicy> findByIdAndDeviceId(UUID id, UUID deviceId);

    boolean existsByDeviceIdAndMetric(UUID deviceId, Metric metric);
}
