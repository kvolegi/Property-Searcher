package com.propertysearcher.domain.repository;

import com.propertysearcher.domain.entity.DiscoveredProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscoveredPropertyRepository extends JpaRepository<DiscoveredProperty, Long> {
    boolean existsByExternalIdAndPlatform(String externalId, String platform);
    Optional<DiscoveredProperty> findByExternalIdAndPlatform(String externalId, String platform);
    long countByPlatform(String platform);
}
