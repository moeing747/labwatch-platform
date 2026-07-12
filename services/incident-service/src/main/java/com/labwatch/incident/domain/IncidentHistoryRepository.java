package com.labwatch.incident.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentHistoryRepository extends JpaRepository<IncidentHistoryEntry, UUID> {

    List<IncidentHistoryEntry> findByIncidentIdOrderByOccurredAt(UUID incidentId);
}
