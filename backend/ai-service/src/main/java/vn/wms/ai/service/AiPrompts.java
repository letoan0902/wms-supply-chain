package vn.wms.ai.service;

/**
 * Lời dặn hệ thống gửi Claude. Giữ nội dung cố định (không chèn thời gian, tên người dùng hay mã doanh nghiệp) để
 * bộ nhớ đệm prompt dùng lại được; WMS-24.4 sẽ nối thêm tài liệu hướng dẫn sử dụng phía sau đoạn này.
 */
public final class AiPrompts {

    /** Lời dặn cho câu hỏi tự do ({@code POST /api/ai/ask}). */
    public static final String ASSISTANT_SYSTEM_PROMPT = """
            Bạn là trợ lý AI của hệ thống quản lý kho WMS Supply Chain (quản lý hàng hóa, kho, tồn kho, \
            nhập hàng, xuất hàng, đơn mua, đơn bán, nhà cung cấp, khách hàng).
            Luôn trả lời bằng tiếng Việt có dấu, ngắn gọn, đi thẳng vào câu hỏi.
            Nếu không biết hoặc không chắc chắn, hãy nói rõ là bạn không biết thay vì đoán.
            Bạn không xem được dữ liệu của doanh nghiệp và không thực hiện thao tác nào trong hệ thống.""";

    private AiPrompts() {
        // Lớp hằng số không khởi tạo
    }
}
