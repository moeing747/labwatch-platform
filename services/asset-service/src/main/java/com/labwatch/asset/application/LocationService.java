package com.labwatch.asset.application;

import com.labwatch.asset.domain.DuplicateResourceException;
import com.labwatch.asset.domain.Location;
import com.labwatch.asset.domain.LocationRepository;
import com.labwatch.asset.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LocationService {

    private final LocationRepository locations;
    private final Clock clock;

    public LocationService(LocationRepository locations, Clock clock) {
        this.locations = locations;
        this.clock = clock;
    }

    public Location create(String name, String description) {
        if (locations.existsByName(name)) {
            throw new DuplicateResourceException("Location already exists: " + name);
        }
        return locations.save(Location.create(name, description, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Location getById(UUID id) {
        return locations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Location> findAll() {
        return locations.findAll();
    }
}
