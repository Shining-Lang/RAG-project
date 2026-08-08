package com.lsn.ragkb.repository.sales;

import com.lsn.ragkb.entity.sales.SalesRep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesRepRepository extends JpaRepository<SalesRep, Long> {
    List<SalesRep> findByRegionId(Long regionId);
    List<SalesRep> findByRole(String role);
    Optional<SalesRep> findByName(String name);
}
