package com.propertyseacher.domain.repository;

import com.propertyseacher.domain.entity.PriceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByPropertyIdOrderByRecordedAtAsc(Long propertyId);

    @Query("SELECT ph.numericPrice FROM PriceHistory ph WHERE ph.currency = :currency AND ph.numericPrice IS NOT NULL ORDER BY ph.recordedAt DESC")
    List<BigDecimal> findRecentPricesByCurrency(@Param("currency") String currency, Pageable pageable);

    @Query("SELECT ph.numericPrice FROM PriceHistory ph WHERE ph.property.platform = :platform AND ph.currency = :currency AND ph.numericPrice IS NOT NULL ORDER BY ph.recordedAt DESC")
    List<BigDecimal> findRecentPricesByPlatformAndCurrency(@Param("platform") String platform, @Param("currency") String currency, Pageable pageable);
}
