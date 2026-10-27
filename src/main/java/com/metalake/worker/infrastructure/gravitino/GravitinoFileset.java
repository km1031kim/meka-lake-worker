package com.metalake.worker.infrastructure.gravitino;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GravitinoFileset(
        String name,
        String type,
        String storageLocation,
        Map<String, String> storageLocations,
        Map<String, String> properties
) {

    public String resolvedStorageLocation() {
        if (storageLocations != null && !storageLocations.isEmpty()) {

            String defaultName = properties == null
                    ? null
                    : properties.get("default-location-name");

            if (defaultName != null && !defaultName.isBlank()) {
                String location = storageLocations.get(defaultName);

                if (location == null) {
                    throw new IllegalStateException(
                            "Default location not found: " + defaultName
                    );
                }
                return location;
            }

            String unnamed = storageLocations.get("unknown");
            if (unnamed != null) {
                return unnamed;
            }

            if (storageLocations.size() == 1) {
                return storageLocations.values().iterator().next();
            }

            throw new IllegalStateException(
                    "Multiple storage locations. Explicit selection required."
            );
        }

        if (storageLocation != null
                && !storageLocation.isBlank()
                && !"unknown".equalsIgnoreCase(storageLocation)) {
            return storageLocation;
        }

        throw new IllegalStateException(
                "Storage location is not available: " + name
        );
    }
}