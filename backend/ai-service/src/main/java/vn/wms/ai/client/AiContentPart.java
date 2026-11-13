package vn.wms.ai.client;

import java.util.Objects;

/**
 * Một phần nội dung trong tin nhắn gửi mô hình: đoạn văn bản, tài liệu PDF hoặc hình ảnh. Cài đặt {@link AiClient}
 * chuyển mỗi phần thành khối nội dung tương ứng của SDK (text, document, image).
 */
public sealed interface AiContentPart {

    /** Đoạn văn bản. */
    record Text(String text) implements AiContentPart {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Tài liệu PDF, gửi nguyên nội dung (base64) để mô hình đọc cả chữ lẫn bảng biểu. */
    record Pdf(byte[] data) implements AiContentPart {
        public Pdf {
            Objects.requireNonNull(data, "data");
        }
    }

    /**
     * Hình ảnh (ảnh chụp bảng, hóa đơn...).
     *
     * @param data      nội dung ảnh
     * @param mediaType {@code image/png} hoặc {@code image/jpeg}
     */
    record Image(byte[] data, String mediaType) implements AiContentPart {
        public Image {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(mediaType, "mediaType");
        }
    }
}
