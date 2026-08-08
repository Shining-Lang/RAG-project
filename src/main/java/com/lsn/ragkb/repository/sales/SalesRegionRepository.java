package com.lsn.ragkb.repository.sales;

import com.lsn.ragkb.entity.sales.SalesRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesRegionRepository extends JpaRepository<SalesRegion, Long> {
    Optional<SalesRegion> findByName(String name);
}
