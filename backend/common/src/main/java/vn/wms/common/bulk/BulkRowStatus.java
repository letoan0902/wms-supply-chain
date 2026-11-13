package vn.wms.common.bulk;

/** Kết quả xử lý một dòng của API nhập hàng loạt (docs/SAAS_SPEC.md, WMS-22.4b phần 2). */
public enum BulkRowStatus {

    /** Dòng hợp lệ, đã tạo bản ghi mới. */
    CREATED,

    /** Mã đã tồn tại trong doanh nghiệp: bỏ qua, không ghi đè bản ghi đang có. */
    SKIPPED,

    /** Dòng lỗi (sai dữ liệu, trùng mã trong cùng file, vượt giới hạn gói...): không ghi gì cho dòng này. */
    FAILED
}
