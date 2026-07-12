package com.labwatch.incident.domain;

import com.labwatch.contracts.incident.IncidentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);

    List<Incident> findAllByOrderByCreatedAtDesc();

    Optional<Incident> findFirstByDeviceIdAndMetricAndStatusNotOrderByCreatedAtDesc(
            String deviceId, com.labwatch.contracts.policy.Metric metric, IncidentStatus status);

    long countByStatusNot(IncidentStatus status);
}
