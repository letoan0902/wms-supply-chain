package vn.wms.ai.extraction;

/** Kiểu giá trị của một trường đích, quyết định cách chuẩn hóa và kiểm tra từng ô. */
public enum FieldKind {
    /** Chuỗi tự do. */
    TEXT,
    /** Mã SKU: chỉ chữ cái, số, dấu gạch ngang và gạch dưới (giống {@code CreateProductRequest}). */
    SKU,
    /** Địa chỉ email. */
    EMAIL,
    /** Số không âm (giá, số lượng, hạn mức), chấp nhận dấu phân cách hàng nghìn kiểu Việt Nam. */
    NUMBER,
    /** Phần trăm (thuế suất VAT): "10%", "10" và "0,1" đều thành 10. */
    PERCENT,
    /** Một giá trị trong danh sách cố định (loại khách hàng). */
    ENUM
}
