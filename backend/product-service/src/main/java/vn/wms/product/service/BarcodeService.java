package vn.wms.product.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.product.dto.BarcodeResponse;
import vn.wms.product.dto.BarcodeType;
import vn.wms.product.dto.BatchBarcodeRequest;
import vn.wms.product.dto.BatchBarcodeResponse;
import vn.wms.product.entity.Product;
import vn.wms.product.repository.ProductRepository;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Service
public class BarcodeService {

    private final ProductRepository productRepository;

    public BarcodeService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Sinh ảnh mã vạch hoặc QR Code dưới dạng mảng byte PNG bằng thư viện ZXing.
     */
    public byte[] generatePng(String rawContent, BarcodeType type, int width, int height, boolean includeLabel) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw ApiException.badRequest("Nội dung tạo mã không được để trống");
        }
        String content = rawContent.trim();
        BarcodeType resolvedType = (type != null) ? type : BarcodeType.CODE_128;

        try {
            BarcodeFormat zxingFormat;
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            switch (resolvedType) {
                case EAN_13 -> {
                    zxingFormat = BarcodeFormat.EAN_13;
                    content = normalizeEan13(content);
                }
                case QR_CODE -> {
                    zxingFormat = BarcodeFormat.QR_CODE;
                    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                }
                case CODE_128 -> zxingFormat = BarcodeFormat.CODE_128;
                default -> throw ApiException.badRequest("Loại mã không được hỗ trợ: " + resolvedType);
            }

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(content, zxingFormat, width, height, hints);
            BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Với mã vạch 1D (Code 128, EAN-13), vẽ thêm nhãn chữ bên dưới nếu includeLabel = true
            BufferedImage finalImage = barcodeImage;
            if (includeLabel && resolvedType != BarcodeType.QR_CODE) {
                finalImage = attachLabelToBarcode(barcodeImage, content);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(finalImage, "PNG", baos);
            return baos.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Không thể sinh mã vạch với nội dung '" + content + "': " + e.getMessage());
        }
    }

    /**
     * Sinh chuỗi Base64 Data URI để nhúng trực tiếp vào thẻ <img src="..." /> ở Frontend.
     */
    public String generateBase64(String content, BarcodeType type, int width, int height, boolean includeLabel) {
        byte[] pngBytes = generatePng(content, type, width, height, includeLabel);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }

    /**
     * Sinh mã vạch cho chuỗi nội dung tùy ý (SKU, vị trí, phiếu kho).
     */
    public BarcodeResponse generateGenericBarcode(String content, BarcodeType type, Integer width, Integer height, Boolean includeLabel) {
        if (content == null || content.trim().isEmpty()) {
            throw ApiException.badRequest("Nội dung tạo mã không được để trống");
        }
        BarcodeType resolvedType = (type != null) ? type : BarcodeType.CODE_128;
        int resolvedWidth = (width != null && width > 0) ? width : (resolvedType == BarcodeType.QR_CODE ? 250 : 300);
        int resolvedHeight = (height != null && height > 0) ? height : (resolvedType == BarcodeType.QR_CODE ? 250 : 100);
        boolean label = includeLabel == null || includeLabel;

        String normalizedContent = (resolvedType == BarcodeType.EAN_13)
                ? normalizeEan13(content.trim())
                : content.trim();

        String base64 = generateBase64(normalizedContent, resolvedType, resolvedWidth, resolvedHeight, label);
        return new BarcodeResponse(normalizedContent, resolvedType, resolvedWidth, resolvedHeight, base64);
    }

    /**
     * Sinh mã vạch / QR Code trực tiếp cho một sản phẩm theo ID.
     */
    @Transactional(readOnly = true)
    public BarcodeResponse generateProductBarcode(Long productId, BarcodeType type, Integer width, Integer height, Boolean includeLabel) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + productId));

        BarcodeType resolvedType = (type != null) ? type : BarcodeType.CODE_128;
        String contentToEncode;

        if (resolvedType == BarcodeType.EAN_13) {
            if (product.getBarcode() != null && !product.getBarcode().isBlank()) {
                contentToEncode = product.getBarcode();
            } else {
                throw ApiException.badRequest("Sản phẩm chưa có mã vạch barcode số (EAN-13). Vui lòng cập nhật barcode hoặc dùng CODE_128");
            }
        } else {
            contentToEncode = product.getSku();
        }

        return generateGenericBarcode(contentToEncode, resolvedType, width, height, includeLabel);
    }

    /**
     * Sinh tem nhãn kết hợp SKU + Lô hàng (Batch/Lot) + Hạn dùng (Exp Date) (FR07).
     */
    @Transactional(readOnly = true)
    public BatchBarcodeResponse generateProductBatchBarcode(Long productId, BatchBarcodeRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + productId));

        BarcodeType type = (request.type() != null) ? request.type() : BarcodeType.QR_CODE;
        int width = (request.width() != null && request.width() > 0) ? request.width() : (type == BarcodeType.QR_CODE ? 250 : 320);
        int height = (request.height() != null && request.height() > 0) ? request.height() : (type == BarcodeType.QR_CODE ? 250 : 100);

        // Định dạng dữ liệu mã hóa chuẩn công nghiệp: SKU:xxx|LOT:yyy|EXP:yyyy-MM-dd
        String encodedContent = String.format("SKU:%s|LOT:%s|EXP:%s",
                product.getSku(),
                request.batchNumber().trim(),
                request.expirationDate().toString()
        );

        String base64 = generateBase64(encodedContent, type, width, height, type != BarcodeType.QR_CODE);

        return new BatchBarcodeResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                request.batchNumber().trim(),
                request.expirationDate(),
                encodedContent,
                type,
                base64
        );
    }

    /**
     * Chuẩn hóa và tự động tính số kiểm tra (check digit) cho mã EAN-13 nếu người dùng nhập 12 chữ số.
     */
    private String normalizeEan13(String content) {
        if (!content.matches("^\\d{12,13}$")) {
            throw ApiException.badRequest("Mã EAN-13 phải gồm đúng 12 hoặc 13 chữ số. Giá trị hiện tại: " + content);
        }

        if (content.length() == 12) {
            int checkDigit = calculateEan13CheckDigit(content);
            return content + checkDigit;
        }

        return content;
    }

    private int calculateEan13CheckDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(twelveDigits.charAt(i));
            sum += (i % 2 == 1) ? digit * 3 : digit;
        }
        int mod = sum % 10;
        return (mod == 0) ? 0 : 10 - mod;
    }

    /**
     * Vẽ nhãn văn bản nằm dưới thanh mã vạch 1D.
     */
    private BufferedImage attachLabelToBarcode(BufferedImage barcodeImage, String text) {
        int fontHeight = 16;
        int paddingBottom = 8;
        int newHeight = barcodeImage.getHeight() + fontHeight + paddingBottom;
        int width = barcodeImage.getWidth();

        BufferedImage combined = new BufferedImage(width, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = combined.createGraphics();

        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, newHeight);

            // Vẽ ảnh mã vạch lên trên
            g2d.drawImage(barcodeImage, 0, 0, null);

            // Vẽ chữ bên dưới
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setColor(Color.BLACK);
            Font font = new Font(Font.MONOSPACED, Font.BOLD, 14);
            g2d.setFont(font);

            FontMetrics metrics = g2d.getFontMetrics(font);
            int textWidth = metrics.stringWidth(text);
            int textX = Math.max(5, (width - textWidth) / 2);
            int textY = barcodeImage.getHeight() + fontHeight - 2;

            g2d.drawString(text, textX, textY);
        } finally {
            g2d.dispose();
        }

        return combined;
    }
}
