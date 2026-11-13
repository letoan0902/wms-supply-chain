package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.DocumentSequence;

/**
 * Bộ đếm số chứng từ qua JPA (đọc, xóa khi dọn dữ liệu). Việc cấp số KHÔNG đi qua repository này: DocumentNumberService
 * khóa và tăng bộ đếm bằng JDBC trên nhóm kết nối riêng để không lấy kết nối thứ hai từ nhóm chính (WMS-20.4).
 */
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {
}
