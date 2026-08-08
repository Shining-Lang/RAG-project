package com.lsn.ragkb.repository.sales;

import com.lsn.ragkb.entity.sales.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findByRepIdAndOrderDateBetween(Long repId, LocalDate start, LocalDate end);

    List<SalesOrder> findByRegionIdAndOrderDateBetween(Long regionId, LocalDate start, LocalDate end);

    @Query("SELECT o FROM SalesOrder o WHERE " +
            "(:repId IS NULL OR o.repId = :repId) " +
            "AND (:regionId IS NULL OR o.regionId = :regionId) " +
            "AND o.orderDate BETWEEN :start AND :end " +
            "ORDER BY o.orderDate DESC, o.id DESC")
    List<SalesOrder> findOrders(@Param("repId") Long repId,
                                @Param("regionId") Long regionId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM SalesOrder o " +
            "WHERE (:regionId IS NULL OR o.regionId = :regionId) " +
            "AND o.status = 'COMPLETED' AND o.orderDate BETWEEN :start AND :end")
    BigDecimal sumAmount(@Param("regionId") Long regionId,
                         @Param("start") LocalDate start,
                         @Param("end") LocalDate end);

    @Query("SELECT COUNT(o) FROM SalesOrder o " +
            "WHERE (:regionId IS NULL OR o.regionId = :regionId) " +
            "AND o.status = 'COMPLETED' AND o.orderDate BETWEEN :start AND :end")
    long countCompleted(@Param("regionId") Long regionId,
                        @Param("start") LocalDate start,
                        @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM SalesOrder o " +
            "WHERE o.repId = :repId AND o.status = 'COMPLETED' " +
            "AND o.orderDate BETWEEN :start AND :end")
    BigDecimal sumAmountByRep(@Param("repId") Long repId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    @Query("SELECT o.repId, SUM(o.amount) AS total, COUNT(o) AS orderCount FROM SalesOrder o " +
            "WHERE o.status = 'COMPLETED' AND o.orderDate BETWEEN :start AND :end " +
            "GROUP BY o.repId ORDER BY total DESC")
    List<Object[]> findRepRanking(@Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Query("SELECT o.regionId, SUM(o.amount) AS total, COUNT(o) AS orderCount, SUM(o.profit) AS profit " +
            "FROM SalesOrder o WHERE o.status = 'COMPLETED' AND o.orderDate BETWEEN :start AND :end " +
            "GROUP BY o.regionId ORDER BY total DESC")
    List<Object[]> findRegionRanking(@Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    @Query("SELECT o.productId, SUM(o.amount) AS total, SUM(o.quantity) AS qty " +
            "FROM SalesOrder o WHERE o.status = 'COMPLETED' " +
            "AND o.orderDate BETWEEN :start AND :end " +
            "GROUP BY o.productId ORDER BY total DESC")
    List<Object[]> findProductRanking(@Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    @Query(value = """
            SELECT to_char(order_date, 'YYYY-MM') AS month,
                   SUM(amount) AS total,
                   COUNT(*) AS order_count
            FROM sa_sales_order
            WHERE status = 'COMPLETED'
              AND (:regionId IS NULL OR region_id = :regionId)
              AND order_date BETWEEN :start AND :end
            GROUP BY month
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> findMonthlyTrend(@Param("regionId") Long regionId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

    @Query("SELECT MAX(o.orderDate) FROM SalesOrder o " +
            "WHERE o.productId = :productId AND o.status = 'COMPLETED'")
    LocalDate findLastOrderDateByProduct(@Param("productId") Long productId);

    @Query("SELECT o.repId, " +
            "SUM(CASE WHEN o.status = 'REFUNDED' THEN 1 ELSE 0 END) AS refunded, " +
            "COUNT(o) AS total " +
            "FROM SalesOrder o WHERE o.orderDate BETWEEN :start AND :end GROUP BY o.repId")
    List<Object[]> findRefundRateByRep(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    @Query("SELECT COUNT(o) FROM SalesOrder o " +
            "WHERE o.regionId = :regionId AND o.status = 'COMPLETED' " +
            "AND o.orderDate BETWEEN :start AND :end")
    Long countCompletedByRegion(@Param("regionId") Long regionId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query("SELECT COUNT(o) FROM SalesOrder o " +
            "WHERE o.repId = :repId AND o.status = 'COMPLETED' " +
            "AND o.orderDate BETWEEN :start AND :end")
    Long countCompletedByRep(@Param("repId") Long repId,
                             @Param("start") LocalDate start,
                             @Param("end") LocalDate end);
}
