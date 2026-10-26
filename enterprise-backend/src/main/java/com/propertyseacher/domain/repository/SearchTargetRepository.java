package com.propertyseacher.domain.repository;

import com.propertyseacher.domain.entity.SearchTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchTargetRepository extends JpaRepository<SearchTarget, Long> {
    List<SearchTarget> findAllByActiveTrue();
    long countByActiveTrue();
}
