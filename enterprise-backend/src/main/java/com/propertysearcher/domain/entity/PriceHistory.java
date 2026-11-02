package com.propertysearcher.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private DiscoveredProperty property;

    @Column(name = "raw_price")
    private String rawPrice;

    @Column(name = "numeric_price", precision = 12, scale = 2)
    private BigDecimal numericPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;
}
