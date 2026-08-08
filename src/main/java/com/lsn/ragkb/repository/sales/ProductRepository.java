package com.lsn.ragkb.repository.sales;

import com.lsn.ragkb.entity.sales.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySkuCode(String skuCode);
    List<Product> findByCategory(String category);
    List<Product> findByStatus(String status);
}
