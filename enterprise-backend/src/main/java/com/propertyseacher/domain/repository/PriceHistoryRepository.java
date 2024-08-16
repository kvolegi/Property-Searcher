package com.propertyseacher.domain.repository;

import com.propertyseacher.domain.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByPropertyIdOrderByRecordedAtAsc(Long propertyId);
}
