package vn.wms.inventory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tổng hợp sổ kho cho báo cáo Xuất - Nhập - Tồn (WMS-17.1) bằng SQL gốc: GROUP BY, đếm số nhóm và phân trang đều
 * chạy trong cơ sở dữ liệu, chỉ trang được yêu cầu được đưa về ứng dụng.
 *
 * <p>SQL gốc không được Hibernate lọc theo doanh nghiệp (docs/MULTI_TENANT.md mục 3), nên MỌI câu ở đây đều có
 * điều kiện {@code m.company_id = :companyId} tường minh, giá trị do service lấy từ {@code TenantContext}.
 * Thứ tự sắp xếp chỉ nhận các giá trị trong {@link SortField}, không ghép chuỗi từ dữ liệu người dùng.
 */
@Repository
public class StockReportQueryRepository {

    /** Cột sắp xếp được phép của báo cáo. */
    public enum SortField {
        PRODUCT_ID("product_id"),
        WAREHOUSE_ID("warehouse_id"),
        OPENING_QUANTITY("opening_qty"),
        QUANTITY_IN("in_qty"),
        QUANTITY_OUT("out_qty"),
        CLOSING_QUANTITY("closing_qty");

        private final String column;

        SortField(String column) {
            this.column = column;
        }
    }

    /**
     * Điều kiện tổng hợp.
     *
     * @param companyId        doanh nghiệp (bắt buộc)
     * @param from             đầu kỳ (bao gồm)
     * @param to               cuối kỳ (không bao gồm)
     * @param warehouseIds     kho được lọc; {@code null} là mọi kho của doanh nghiệp
     * @param productIds       sản phẩm được lọc; {@code null} là mọi sản phẩm
     * @param groupByWarehouse {@code true}: mỗi dòng là một cặp (sản phẩm, kho); {@code false}: gộp mọi kho
     * @param sortField        cột sắp xếp
     * @param ascending        chiều sắp xếp
     */
    public record Criteria(Long companyId, Instant from, Instant to, Collection<Long> warehouseIds,
                           Collection<Long> productIds, boolean groupByWarehouse, SortField sortField,
                           boolean ascending) {
    }

    /** Một nhóm tổng hợp: tồn đầu kỳ, nhập, xuất, tồn cuối kỳ theo sổ kho. */
    public record AggregateRow(Long productId, Long warehouseId, BigDecimal openingQuantity, BigDecimal quantityIn,
                               BigDecimal quantityOut, BigDecimal closingQuantity) {
    }

    /** Tổng cộng trên toàn bộ các nhóm khớp điều kiện (không chỉ trang hiện tại). */
    public record Totals(BigDecimal openingQuantity, BigDecimal quantityIn, BigDecimal quantityOut,
                         BigDecimal closingQuantity) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** Một trang các nhóm; {@code limit = null} trả toàn bộ (dùng cho xuất Excel, service tự giới hạn số dòng). */
    public List<AggregateRow> findRows(Criteria criteria, int offset, Integer limit) {
        Map<String, Object> params = new HashMap<>();
        String sql = groupedSql(criteria, params)
                + " ORDER BY " + criteria.sortField().column + (criteria.ascending() ? " ASC" : " DESC")
                + ", product_id ASC" + (criteria.groupByWarehouse() ? ", warehouse_id ASC" : "");
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        query.setFirstResult(Math.max(offset, 0));
        if (limit != null) {
            query.setMaxResults(limit);
        }
        List<?> raw = query.getResultList();
        List<AggregateRow> rows = new ArrayList<>(raw.size());
        for (Object item : raw) {
            Object[] r = (Object[]) item;
            rows.add(new AggregateRow(toLong(r[0]), toLong(r[1]), toDecimal(r[2]), toDecimal(r[3]),
                    toDecimal(r[4]), toDecimal(r[5])));
        }
        return rows;
    }

    /** Số nhóm khớp điều kiện, để tính tổng số trang. */
    public long countRows(Criteria criteria) {
        Map<String, Object> params = new HashMap<>();
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + groupedSql(criteria, params) + ") t");
        params.forEach(query::setParameter);
        Object result = query.getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /**
     * Tổng cộng của mọi nhóm khớp điều kiện. Nhóm bị loại bởi HAVING (cả tồn đầu, nhập, xuất đều bằng 0) không đóng
     * góp gì vào tổng nên tính thẳng trên các dòng sổ, không cần GROUP BY.
     */
    public Totals totals(Criteria criteria) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN m.occurred_at < :fromTs THEN m.quantity_in - m.quantity_out ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN m.occurred_at >= :fromTs THEN m.quantity_in ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN m.occurred_at >= :fromTs THEN m.quantity_out ELSE 0 END), 0), "
                + "COALESCE(SUM(m.quantity_in - m.quantity_out), 0) "
                + "FROM stock_movements m" + whereClause(criteria, params);
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        Object[] r = (Object[]) query.getSingleResult();
        return new Totals(toDecimal(r[0]), toDecimal(r[1]), toDecimal(r[2]), toDecimal(r[3]));
    }

    private String groupedSql(Criteria criteria, Map<String, Object> params) {
        String warehouseColumn = criteria.groupByWarehouse() ? "m.warehouse_id" : "NULL";
        return "SELECT m.product_id AS product_id, " + warehouseColumn + " AS warehouse_id, "
                + "COALESCE(SUM(CASE WHEN m.occurred_at < :fromTs THEN m.quantity_in - m.quantity_out ELSE 0 END), 0) AS opening_qty, "
                + "COALESCE(SUM(CASE WHEN m.occurred_at >= :fromTs THEN m.quantity_in ELSE 0 END), 0) AS in_qty, "
                + "COALESCE(SUM(CASE WHEN m.occurred_at >= :fromTs THEN m.quantity_out ELSE 0 END), 0) AS out_qty, "
                + "COALESCE(SUM(m.quantity_in - m.quantity_out), 0) AS closing_qty "
                + "FROM stock_movements m" + whereClause(criteria, params)
                + " GROUP BY m.product_id" + (criteria.groupByWarehouse() ? ", m.warehouse_id" : "")
                // Bỏ nhóm không có tồn đầu kỳ và không phát sinh trong kỳ (sản phẩm đã hết hàng từ trước kỳ)
                + " HAVING SUM(CASE WHEN m.occurred_at < :fromTs THEN m.quantity_in - m.quantity_out ELSE 0 END) <> 0"
                + " OR SUM(CASE WHEN m.occurred_at >= :fromTs THEN m.quantity_in + m.quantity_out ELSE 0 END) <> 0";
    }

    private String whereClause(Criteria criteria, Map<String, Object> params) {
        if (criteria.companyId() == null) {
            throw new IllegalArgumentException("companyId là bắt buộc với truy vấn SQL gốc của báo cáo");
        }
        StringBuilder where = new StringBuilder(" WHERE m.company_id = :companyId AND m.occurred_at < :toTs");
        params.put("companyId", criteria.companyId());
        params.put("fromTs", criteria.from());
        params.put("toTs", criteria.to());
        if (criteria.warehouseIds() != null) {
            where.append(" AND m.warehouse_id IN (:warehouseIds)");
            params.put("warehouseIds", criteria.warehouseIds());
        }
        if (criteria.productIds() != null) {
            where.append(" AND m.product_id IN (:productIds)");
            params.put("productIds", criteria.productIds());
        }
        return where.toString();
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return decimal.setScale(3, java.math.RoundingMode.UNNECESSARY);
    }
}
