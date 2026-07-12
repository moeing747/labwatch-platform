package com.labwatch.asset.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    boolean existsByName(String name);
}
