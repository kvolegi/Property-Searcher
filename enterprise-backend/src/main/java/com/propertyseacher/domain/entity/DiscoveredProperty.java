package com.propertyseacher.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "discovered_properties", indexes = {
    @Index(name = "idx_external_id_platform", columnList = "external_id, platform", unique = true),
    @Index(name = "idx_discovered_at", columnList = "discovered_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "price")
    private String price;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "category")
    private String category;

    @CreationTimestamp
    @Column(name = "discovered_at", updatable = false)
    private LocalDateTime discoveredAt;
}
