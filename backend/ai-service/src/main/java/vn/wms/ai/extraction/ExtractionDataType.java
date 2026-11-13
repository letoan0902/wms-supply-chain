package vn.wms.ai.extraction;

import vn.wms.common.constant.Roles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loại dữ liệu danh mục trích xuất được, kèm các trường đích (khớp API nhập hàng loạt của WMS-22.4b), vai trò được
 * phép và API nhập hàng loạt tương ứng.
 */
public enum ExtractionDataType {

    PRODUCT("hàng hóa", "product-service", "/api/products/bulk",
            Set.of(Roles.ADMIN, Roles.WAREHOUSE_MANAGER, Roles.PURCHASING), productFields()),
    SUPPLIER("nhà cung cấp", "order-service", "/api/orders/suppliers/bulk",
            Set.of(Roles.ADMIN, Roles.WAREHOUSE_MANAGER, Roles.PURCHASING), supplierFields()),
    CUSTOMER("khách hàng", "order-service", "/api/orders/customers/bulk",
            Set.of(Roles.ADMIN, Roles.SALES), customerFields());

    private final String label;
    private final String serviceId;
    private final String bulkPath;
    private final Set<String> allowedRoles;
    private final List<TargetField> fields;

    ExtractionDataType(String label, String serviceId, String bulkPath, Set<String> allowedRoles,
                       List<TargetField> fields) {
        this.label = label;
        this.serviceId = serviceId;
        this.bulkPath = bulkPath;
        this.allowedRoles = allowedRoles;
        this.fields = fields;
    }

    /** Tên tiếng Việt viết thường, ví dụ "hàng hóa". */
    public String label() {
        return label;
    }

    /** Tên service trên Eureka nhận API nhập hàng loạt. */
    public String serviceId() {
        return serviceId;
    }

    /** Đường dẫn API nhập hàng loạt, body {@code { "items": [...] }}. */
    public String bulkPath() {
        return bulkPath;
    }

    /** Vai trò được trích xuất và nhập loại dữ liệu này (docs/SAAS_SPEC.md, mục WMS-24.2). */
    public Set<String> allowedRoles() {
        return allowedRoles;
    }

    /** Các trường đích theo đúng thứ tự cột hiển thị. */
    public List<TargetField> fields() {
        return fields;
    }

    public Optional<TargetField> field(String name) {
        return fields.stream().filter(field -> field.name().equals(name)).findFirst();
    }

    /** Đọc giá trị {@code dataType} của request, không phân biệt hoa thường; sai thì rỗng. */
    public static Optional<ExtractionDataType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (ExtractionDataType type : values()) {
            if (type.name().equalsIgnoreCase(value.strip())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    // Trường của POST /api/products/bulk (WMS-22.4b): độ dài theo CreateProductRequest, tên danh mục 150 và tên đơn
    // vị tính 100 theo CreateCategoryRequest, CreateUomRequest của product-service.
    private static List<TargetField> productFields() {
        return List.of(
                TargetField.sku("sku", "Mã SKU", 64, "sku", "mã sku", "mã hàng", "mã hàng hóa", "mã sản phẩm",
                        "mã sp", "mã vật tư", "mã", "item code", "product code", "code"),
                TargetField.text("name", "Tên hàng hóa", true, 255, "tên", "tên hàng", "tên hàng hóa",
                        "tên sản phẩm", "tên sp", "tên vật tư", "mặt hàng", "sản phẩm", "hàng hóa", "product name",
                        "item name", "product", "name"),
                TargetField.text("barcode", "Mã vạch", false, 64, "mã vạch", "barcode", "ean", "upc", "mã ean"),
                TargetField.text("description", "Mô tả", false, 1000, "mô tả", "diễn giải", "ghi chú", "quy cách",
                        "description", "note", "notes"),
                TargetField.text("categoryName", "Danh mục", false, 150, "danh mục", "nhóm hàng", "loại hàng",
                        "ngành hàng", "nhóm", "category", "category name"),
                TargetField.text("uomName", "Đơn vị tính", true, 100, "đơn vị tính", "đơn vị", "đvt", "uom",
                        "unit", "unit of measure"),
                TargetField.number("purchasePrice", "Giá nhập", 2, "giá nhập", "giá mua", "đơn giá nhập", "giá vốn",
                        "purchase price", "cost price", "cost"),
                TargetField.number("sellingPrice", "Giá bán", 2, "giá bán", "đơn giá bán", "giá bán lẻ",
                        "giá niêm yết", "đơn giá", "giá", "selling price", "sale price", "price"),
                TargetField.percent("vatRate", "Thuế suất VAT (%)", "thuế suất vat", "thuế suất", "thuế vat",
                        "thuế gtgt", "vat", "thuế", "vat rate", "tax rate", "tax"),
                TargetField.number("safetyStock", "Tồn an toàn", 3, "tồn an toàn", "tồn kho an toàn",
                        "mức tồn an toàn", "tồn tối thiểu", "safety stock", "min stock"),
                TargetField.number("reorderPoint", "Điểm đặt hàng lại", 3, "điểm đặt hàng lại", "điểm đặt hàng",
                        "mức đặt hàng lại", "reorder point", "reorder level"),
                TargetField.number("maxStock", "Tồn tối đa", 3, "tồn tối đa", "mức tồn tối đa", "tồn kho tối đa",
                        "max stock", "maximum stock"));
    }

    // Trường của POST /api/orders/suppliers/bulk = các trường của CreateSupplierRequest (order-service).
    private static List<TargetField> supplierFields() {
        return List.of(
                TargetField.text("code", "Mã nhà cung cấp", true, 30, "mã nhà cung cấp", "mã ncc", "mã",
                        "supplier code", "vendor code", "code"),
                TargetField.text("name", "Tên nhà cung cấp", true, 150, "tên nhà cung cấp", "tên ncc",
                        "nhà cung cấp", "tên công ty", "tên doanh nghiệp", "tên", "supplier name", "supplier",
                        "vendor name", "vendor", "company", "name"),
                TargetField.text("taxCode", "Mã số thuế", false, 30, "mã số thuế", "mst", "tax code", "tax id"),
                TargetField.text("contactName", "Người liên hệ", false, 100, "người liên hệ", "liên hệ",
                        "người đại diện", "contact name", "contact person", "contact"),
                TargetField.email("email", "Email", 100, "email", "e-mail", "thư điện tử", "mail"),
                TargetField.text("phone", "Số điện thoại", false, 30, "số điện thoại", "điện thoại", "sđt", "đt",
                        "di động", "phone", "tel", "mobile"),
                TargetField.text("address", "Địa chỉ", false, 255, "địa chỉ", "trụ sở", "address"),
                TargetField.text("bankAccount", "Số tài khoản", false, 50, "số tài khoản", "stk",
                        "tài khoản ngân hàng", "tài khoản", "bank account", "account number"),
                TargetField.text("bankName", "Ngân hàng", false, 100, "tên ngân hàng", "ngân hàng", "bank name",
                        "bank"),
                TargetField.text("paymentTerms", "Điều khoản thanh toán", false, 50, "điều khoản thanh toán",
                        "hạn thanh toán", "thanh toán", "payment terms", "terms"),
                TargetField.number("creditLimit", "Hạn mức công nợ", 2, "hạn mức công nợ", "hạn mức nợ",
                        "hạn mức tín dụng", "hạn mức", "credit limit"),
                TargetField.number("initialDebt", "Công nợ ban đầu", 2, "công nợ ban đầu", "nợ đầu kỳ",
                        "số dư đầu kỳ", "dư nợ", "công nợ", "opening balance", "initial debt", "debt"));
    }

    // Trường của POST /api/orders/customers/bulk = các trường của CreateCustomerRequest (order-service).
    private static List<TargetField> customerFields() {
        Map<String, List<String>> customerTypes = new LinkedHashMap<>();
        customerTypes.put("WHOLESALE", List.of("sỉ", "bán sỉ", "khách sỉ", "đại lý", "doanh nghiệp", "công ty",
                "b2b", "wholesale"));
        customerTypes.put("RETAIL", List.of("lẻ", "bán lẻ", "khách lẻ", "cá nhân", "b2c", "retail"));
        return List.of(
                TargetField.text("code", "Mã khách hàng", true, 30, "mã khách hàng", "mã kh", "mã",
                        "customer code", "code"),
                TargetField.text("name", "Tên khách hàng", true, 150, "tên khách hàng", "tên kh", "khách hàng",
                        "tên công ty", "tên doanh nghiệp", "tên", "customer name", "customer", "company", "name"),
                TargetField.enumeration("customerType", "Loại khách hàng", true, customerTypes, "loại khách hàng",
                        "loại kh", "phân loại", "nhóm khách hàng", "loại", "customer type", "type"),
                TargetField.text("taxCode", "Mã số thuế", false, 30, "mã số thuế", "mst", "tax code", "tax id"),
                TargetField.text("contactName", "Người liên hệ", false, 100, "người liên hệ", "liên hệ",
                        "người đại diện", "contact name", "contact person", "contact"),
                TargetField.email("email", "Email", 100, "email", "e-mail", "thư điện tử", "mail"),
                TargetField.text("phone", "Số điện thoại", false, 30, "số điện thoại", "điện thoại", "sđt", "đt",
                        "di động", "phone", "tel", "mobile"),
                TargetField.text("address", "Địa chỉ", false, 255, "địa chỉ giao hàng", "địa chỉ", "trụ sở",
                        "address"),
                TargetField.text("paymentTerms", "Điều khoản thanh toán", false, 50, "điều khoản thanh toán",
                        "hạn thanh toán", "thanh toán", "payment terms", "terms"),
                TargetField.number("creditLimit", "Hạn mức công nợ", 2, "hạn mức công nợ", "hạn mức nợ",
                        "hạn mức tín dụng", "hạn mức", "credit limit"),
                TargetField.number("initialDebt", "Công nợ ban đầu", 2, "công nợ ban đầu", "nợ đầu kỳ",
                        "số dư đầu kỳ", "dư nợ", "công nợ", "opening balance", "initial debt", "debt"),
                TargetField.text("notes", "Ghi chú", false, 500, "ghi chú", "mô tả", "note", "notes", "remark"));
    }
}
