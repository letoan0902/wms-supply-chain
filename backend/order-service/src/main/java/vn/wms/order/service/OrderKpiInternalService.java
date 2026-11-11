package vn.wms.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.dto.internal.OrderKpiMetricsResponse;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderKpiInternalService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public OrderKpiMetricsResponse getMetrics(Long warehouseId) {
        LocalDate today = LocalDate.now();

        // 1. Thẻ 1: Công nợ phải thu
        BigDecimal totalReceivables = customerRepository.sumTotalDebt();
        long overdueCustomers = customerRepository.countCustomersWithDebt();

        // 2. Thẻ 2: Công nợ phải trả
        BigDecimal totalPayables = supplierRepository.sumTotalDebt();
        long dueSoonSuppliers = supplierRepository.countSuppliersWithDebt();

        // 4. Thẻ 4: Doanh số tháng này vs tháng trước
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        List<OrderStatus> revenueStatuses = List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);
        BigDecimal currentMonthRevenue = salesOrderRepository.sumRevenueBetweenDates(
                startOfMonth, endOfMonth, revenueStatuses, warehouseId
        );

        LocalDate prevMonth = today.minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.withDayOfMonth(1);
        LocalDate endOfPrevMonth = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());
        BigDecimal prevMonthRevenue = salesOrderRepository.sumRevenueBetweenDates(
                startOfPrevMonth, endOfPrevMonth, revenueStatuses, warehouseId
        );

        // 6. Thẻ 6: Đơn bán chờ xuất (SO)
        List<OrderStatus> pendingSoStatuses = List.of(OrderStatus.CONFIRMED);
        long pendingSalesOrders = salesOrderRepository.countByStatusInAndWarehouseId(pendingSoStatuses, warehouseId);
        long urgentSalesOrders = salesOrderRepository.countUrgentOrders(pendingSoStatuses, today, warehouseId);

        // 7. Thẻ 7: Đơn mua chờ tiếp nhận (PO)
        List<OrderStatus> pendingPoStatuses = List.of(OrderStatus.APPROVED);
        long pendingPurchaseOrders = purchaseOrderRepository.countByStatusInAndWarehouseId(pendingPoStatuses, warehouseId);
        long arrivingTodayPurchaseOrders = purchaseOrderRepository.countArrivingOrders(pendingPoStatuses, today, warehouseId);

        return new OrderKpiMetricsResponse(
                totalReceivables != null ? totalReceivables : BigDecimal.ZERO,
                overdueCustomers,
                totalPayables != null ? totalPayables : BigDecimal.ZERO,
                dueSoonSuppliers,
                currentMonthRevenue != null ? currentMonthRevenue : BigDecimal.ZERO,
                prevMonthRevenue != null ? prevMonthRevenue : BigDecimal.ZERO,
                pendingSalesOrders,
                urgentSalesOrders,
                pendingPurchaseOrders,
                arrivingTodayPurchaseOrders
        );
    }
}
