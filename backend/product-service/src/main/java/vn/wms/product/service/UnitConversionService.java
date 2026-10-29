package vn.wms.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.product.dto.BreakdownItem;
import vn.wms.product.dto.BreakdownResponse;
import vn.wms.product.dto.CalculateConversionRequest;
import vn.wms.product.dto.CalculateConversionResponse;
import vn.wms.product.dto.ConvertToBaseRequest;
import vn.wms.product.dto.ConvertToBaseResponse;
import vn.wms.product.dto.CreateUnitConversionRequest;
import vn.wms.product.dto.UnitConversionResponse;
import vn.wms.product.dto.UpdateUnitConversionRequest;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.UnitConversion;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UnitConversionRepository;
import vn.wms.product.repository.UomRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

@Service
public class UnitConversionService {

    private final ProductRepository productRepository;
    private final UomRepository uomRepository;
    private final UnitConversionRepository unitConversionRepository;

    public UnitConversionService(ProductRepository productRepository,
                                 UomRepository uomRepository,
                                 UnitConversionRepository unitConversionRepository) {
        this.productRepository = productRepository;
        this.uomRepository = uomRepository;
        this.unitConversionRepository = unitConversionRepository;
    }

    /** Lấy danh sách toàn bộ quy đổi của một sản phẩm, kèm tỷ lệ tự động tính về đơn vị cơ sở. */
    @Transactional(readOnly = true)
    public List<UnitConversionResponse> getConversions(Long productId) {
        Product product = findProduct(productId);
        List<UnitConversion> conversions = unitConversionRepository.findByProductId(productId);
        Long baseUomId = product.getUom().getId();

        return conversions.stream()
                .map(uc -> {
                    BigDecimal factorToBase = findConversionRate(uc.getFromUom().getId(), baseUomId, conversions)
                            .orElse(null);
                    return UnitConversionResponse.from(uc, factorToBase);
                })
                .toList();
    }

    /** Thêm mới một quy tắc quy đổi đơn vị cho sản phẩm. */
    @Transactional
    public UnitConversionResponse createConversion(Long productId, CreateUnitConversionRequest request) {
        Product product = findProduct(productId);

        if (request.fromUomId().equals(request.toUomId())) {
            throw ApiException.badRequest("Đơn vị nguồn và đơn vị đích không được trùng nhau");
        }

        if (unitConversionRepository.existsByProductIdAndFromUomIdAndToUomId(productId, request.fromUomId(), request.toUomId())) {
            throw ApiException.conflict("Quy tắc quy đổi giữa 2 đơn vị này đã tồn tại cho sản phẩm");
        }

        Uom fromUom = uomRepository.findById(request.fromUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị nguồn không tồn tại: " + request.fromUomId()));
        Uom toUom = uomRepository.findById(request.toUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị đích không tồn tại: " + request.toUomId()));

        UnitConversion uc = new UnitConversion();
        uc.setProduct(product);
        uc.setFromUom(fromUom);
        uc.setToUom(toUom);
        uc.setConversionFactor(request.conversionFactor());
        uc.setBarcode(request.barcode() != null && !request.barcode().isBlank() ? request.barcode().trim() : null);
        uc.setDescription(request.description() != null ? request.description().trim() : null);

        UnitConversion saved = unitConversionRepository.save(uc);

        List<UnitConversion> allConversions = unitConversionRepository.findByProductId(productId);
        BigDecimal factorToBase = findConversionRate(fromUom.getId(), product.getUom().getId(), allConversions)
                .orElse(null);

        return UnitConversionResponse.from(saved, factorToBase);
    }

    /** Cập nhật một quy tắc quy đổi. */
    @Transactional
    public UnitConversionResponse updateConversion(Long productId, Long conversionId, UpdateUnitConversionRequest request) {
        Product product = findProduct(productId);

        UnitConversion uc = unitConversionRepository.findById(conversionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình quy đổi với id: " + conversionId));

        if (!uc.getProduct().getId().equals(productId)) {
            throw ApiException.badRequest("Cấu hình quy đổi không thuộc sản phẩm này");
        }

        if (request.fromUomId().equals(request.toUomId())) {
            throw ApiException.badRequest("Đơn vị nguồn và đơn vị đích không được trùng nhau");
        }

        if (unitConversionRepository.existsByProductIdAndFromUomIdAndToUomIdAndIdNot(productId, request.fromUomId(), request.toUomId(), conversionId)) {
            throw ApiException.conflict("Quy tắc quy đổi giữa 2 đơn vị này đã tồn tại cho sản phẩm");
        }

        Uom fromUom = uomRepository.findById(request.fromUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị nguồn không tồn tại: " + request.fromUomId()));
        Uom toUom = uomRepository.findById(request.toUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị đích không tồn tại: " + request.toUomId()));

        uc.setFromUom(fromUom);
        uc.setToUom(toUom);
        uc.setConversionFactor(request.conversionFactor());
        uc.setBarcode(request.barcode() != null && !request.barcode().isBlank() ? request.barcode().trim() : null);
        uc.setDescription(request.description() != null ? request.description().trim() : null);

        UnitConversion saved = unitConversionRepository.save(uc);

        List<UnitConversion> allConversions = unitConversionRepository.findByProductId(productId);
        BigDecimal factorToBase = findConversionRate(fromUom.getId(), product.getUom().getId(), allConversions)
                .orElse(null);

        return UnitConversionResponse.from(saved, factorToBase);
    }

    /** Xóa một quy tắc quy đổi. */
    @Transactional
    public void deleteConversion(Long productId, Long conversionId) {
        findProduct(productId);
        UnitConversion uc = unitConversionRepository.findById(conversionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình quy đổi với id: " + conversionId));

        if (!uc.getProduct().getId().equals(productId)) {
            throw ApiException.badRequest("Cấu hình quy đổi không thuộc sản phẩm này");
        }

        unitConversionRepository.delete(uc);
    }

    /**
     * Thuật toán quy đổi tự động về đơn vị cơ sở khi tính toán tồn kho (FR06).
     * Ví dụ: Nhập 5 Thùng -> Hệ thống quy đổi thành 120 Chai để ghi nhận vào OnHand Stock.
     */
    @Transactional(readOnly = true)
    public ConvertToBaseResponse convertToBase(Long productId, ConvertToBaseRequest request) {
        Product product = findProduct(productId);
        Uom baseUom = product.getUom();

        Uom inputUom = uomRepository.findById(request.uomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị tính đầu vào không tồn tại: " + request.uomId()));

        if (inputUom.getId().equals(baseUom.getId())) {
            return new ConvertToBaseResponse(
                    product.getId(),
                    product.getSku(),
                    inputUom.getId(),
                    inputUom.getCode(),
                    inputUom.getName(),
                    request.quantity(),
                    baseUom.getId(),
                    baseUom.getCode(),
                    baseUom.getName(),
                    request.quantity().setScale(3, RoundingMode.HALF_UP),
                    BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP)
            );
        }

        List<UnitConversion> conversions = unitConversionRepository.findByProductId(productId);
        BigDecimal rate = findConversionRate(inputUom.getId(), baseUom.getId(), conversions)
                .orElseThrow(() -> ApiException.badRequest(
                        "Không tìm thấy đường quy đổi từ đơn vị '" + inputUom.getName() + "' về đơn vị cơ sở '" + baseUom.getName() + "'"
                ));

        BigDecimal baseQuantity = request.quantity().multiply(rate).setScale(3, RoundingMode.HALF_UP);

        return new ConvertToBaseResponse(
                product.getId(),
                product.getSku(),
                inputUom.getId(),
                inputUom.getCode(),
                inputUom.getName(),
                request.quantity(),
                baseUom.getId(),
                baseUom.getCode(),
                baseUom.getName(),
                baseQuantity,
                rate
        );
    }

    /** Quy đổi giữa hai đơn vị tính bất kỳ trên cây quy đổi đa cấp. */
    @Transactional(readOnly = true)
    public CalculateConversionResponse calculate(Long productId, CalculateConversionRequest request) {
        Product product = findProduct(productId);

        Uom fromUom = uomRepository.findById(request.fromUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị nguồn không tồn tại: " + request.fromUomId()));
        Uom toUom = uomRepository.findById(request.toUomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị đích không tồn tại: " + request.toUomId()));

        if (request.fromUomId().equals(request.toUomId())) {
            return new CalculateConversionResponse(
                    product.getId(),
                    product.getSku(),
                    fromUom.getId(),
                    fromUom.getCode(),
                    toUom.getId(),
                    toUom.getCode(),
                    request.quantity(),
                    request.quantity().setScale(3, RoundingMode.HALF_UP),
                    BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP)
            );
        }

        List<UnitConversion> conversions = unitConversionRepository.findByProductId(productId);
        BigDecimal rate = findConversionRate(fromUom.getId(), toUom.getId(), conversions)
                .orElseThrow(() -> ApiException.badRequest(
                        "Không tìm thấy đường quy đổi giữa đơn vị '" + fromUom.getName() + "' và '" + toUom.getName() + "'"
                ));

        BigDecimal convertedQuantity = request.quantity().multiply(rate).setScale(3, RoundingMode.HALF_UP);

        return new CalculateConversionResponse(
                product.getId(),
                product.getSku(),
                fromUom.getId(),
                fromUom.getCode(),
                toUom.getId(),
                toUom.getCode(),
                request.quantity(),
                convertedQuantity,
                rate
        );
    }

    /**
     * Phân rã số lượng tồn cơ sở sang các cấp đóng gói từ lớn đến nhỏ.
     * Ví dụ: 50 Lon -> 2 Thùng (24 lon/thùng) + 2 Lon lẻ.
     */
    @Transactional(readOnly = true)
    public BreakdownResponse breakdown(Long productId, BigDecimal baseQuantity) {
        Product product = findProduct(productId);
        Uom baseUom = product.getUom();

        if (baseQuantity == null || baseQuantity.compareTo(BigDecimal.ZERO) < 0) {
            baseQuantity = BigDecimal.ZERO;
        }

        List<UnitConversion> conversions = unitConversionRepository.findByProductId(productId);

        // Tập hợp tất cả các đơn vị có tỷ lệ quy đổi về đơn vị cơ sở > 1
        Map<Long, UomConversionPair> unitsAboveBase = new HashMap<>();
        for (UnitConversion uc : conversions) {
            checkAndAddUnit(uc.getFromUom(), baseUom.getId(), conversions, unitsAboveBase);
            checkAndAddUnit(uc.getToUom(), baseUom.getId(), conversions, unitsAboveBase);
        }

        // Sắp xếp các đơn vị đóng gói giảm dần theo hệ số quy đổi về base
        List<UomConversionPair> sortedUnits = new ArrayList<>(unitsAboveBase.values());
        sortedUnits.sort(Comparator.comparing(UomConversionPair::factorToBase).reversed());

        List<BreakdownItem> breakdownItems = new ArrayList<>();
        List<String> summaryParts = new ArrayList<>();
        BigDecimal remaining = baseQuantity;

        for (UomConversionPair pair : sortedUnits) {
            if (pair.factorToBase.compareTo(BigDecimal.ONE) <= 0) {
                continue;
            }
            if (remaining.compareTo(pair.factorToBase) >= 0) {
                BigDecimal count = remaining.divideToIntegralValue(pair.factorToBase);
                remaining = remaining.remainder(pair.factorToBase);

                if (count.compareTo(BigDecimal.ZERO) > 0) {
                    breakdownItems.add(new BreakdownItem(
                            pair.uom.getId(),
                            pair.uom.getCode(),
                            pair.uom.getName(),
                            count,
                            pair.factorToBase
                    ));
                    summaryParts.add(count.stripTrailingZeros().toPlainString() + " " + pair.uom.getName());
                }
            }
        }

        // Phần dư còn lại theo đơn vị cơ sở
        if (remaining.compareTo(BigDecimal.ZERO) > 0 || breakdownItems.isEmpty()) {
            breakdownItems.add(new BreakdownItem(
                    baseUom.getId(),
                    baseUom.getCode(),
                    baseUom.getName(),
                    remaining.setScale(3, RoundingMode.HALF_UP),
                    BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP)
            ));
            summaryParts.add(remaining.stripTrailingZeros().toPlainString() + " " + baseUom.getName());
        }

        String summary = String.join(", ", summaryParts);

        return new BreakdownResponse(
                product.getId(),
                product.getSku(),
                baseQuantity.setScale(3, RoundingMode.HALF_UP),
                baseUom.getName(),
                breakdownItems,
                summary
        );
    }

    private void checkAndAddUnit(Uom uom, Long baseUomId, List<UnitConversion> conversions, Map<Long, UomConversionPair> map) {
        if (!uom.getId().equals(baseUomId) && !map.containsKey(uom.getId())) {
            findConversionRate(uom.getId(), baseUomId, conversions).ifPresent(factor -> {
                if (factor.compareTo(BigDecimal.ONE) > 0) {
                    map.put(uom.getId(), new UomConversionPair(uom, factor));
                }
            });
        }
    }

    /**
     * Thuật toán BFS tìm tỷ lệ quy đổi từ startUom sang targetUom trên đồ thị tỷ lệ.
     * Hỗ trợ quy đổi đa cấp (Pallet -> Thùng -> Lốc -> Chai) và cả chiều ngược lại.
     */
    public Optional<BigDecimal> findConversionRate(Long startUomId, Long targetUomId, List<UnitConversion> conversions) {
        if (startUomId.equals(targetUomId)) {
            return Optional.of(BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP));
        }

        // Xây dựng danh sách kề có trọng số
        Map<Long, List<Edge>> graph = new HashMap<>();
        for (UnitConversion uc : conversions) {
            Long from = uc.getFromUom().getId();
            Long to = uc.getToUom().getId();
            BigDecimal factor = uc.getConversionFactor();

            // Cạnh xuôi: 1 from = factor to
            graph.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, factor));

            // Cạnh ngược: 1 to = (1 / factor) from
            if (factor.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal inverse = BigDecimal.ONE.divide(factor, 12, RoundingMode.HALF_UP);
                graph.computeIfAbsent(to, k -> new ArrayList<>()).add(new Edge(from, inverse));
            }
        }

        // BFS tìm đường đi
        Queue<NodeState> queue = new LinkedList<>();
        Set<Long> visited = new HashSet<>();

        queue.add(new NodeState(startUomId, BigDecimal.ONE));
        visited.add(startUomId);

        while (!queue.isEmpty()) {
            NodeState current = queue.poll();
            if (current.uomId.equals(targetUomId)) {
                return Optional.of(current.cumulativeRate.setScale(6, RoundingMode.HALF_UP));
            }

            List<Edge> neighbors = graph.getOrDefault(current.uomId, Collections.emptyList());
            for (Edge edge : neighbors) {
                if (!visited.contains(edge.toUomId)) {
                    visited.add(edge.toUomId);
                    BigDecimal nextRate = current.cumulativeRate.multiply(edge.factor);
                    queue.add(new NodeState(edge.toUomId, nextRate));
                }
            }
        }

        return Optional.empty();
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + productId));
    }

    private record Edge(Long toUomId, BigDecimal factor) {}

    private record NodeState(Long uomId, BigDecimal cumulativeRate) {}

    private record UomConversionPair(Uom uom, BigDecimal factorToBase) {}
}
