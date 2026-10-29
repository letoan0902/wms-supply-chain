package vn.wms.inventory.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Tiện ích sinh ảnh mã vạch Code 128 chuẩn công nghiệp cho tem dán ô/kệ (WMS-06.2).
 * Thuần Java (AWT/ImageIO), hoạt động độc lập và không phụ thuộc thư viện ngoài.
 */
public final class BarcodeGenerator {

    private BarcodeGenerator() {}

    /**
     * Bảng mẫu 11 module cho 107 ký hiệu Code 128 (0 - 106).
     * Mỗi chuỗi gồm 6 số biểu thị độ rộng xen kẽ giữa vạch đen và khoảng trắng.
     */
    private static final String[] CODE128_PATTERNS = {
            "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213", // 0-9
            "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132", // 10-19
            "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211", // 20-29
            "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313", // 30-39
            "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331", // 40-49
            "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111", // 50-59
            "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214", // 60-69
            "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111", // 70-79
            "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141", // 80-89
            "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141", // 90-99
            "114131", "311141", "411131", "211412", "211214", "211232", "2331112" // 100-106 (106 là STOP có thêm vạch kết thúc 2)
    };

    /**
     * Sinh ảnh PNG của mã vạch Code 128 (chế độ B) kèm nhãn chữ bên dưới.
     */
    public static byte[] generateCode128Png(String text, int moduleWidth, int barHeight) {
        if (text == null || text.trim().isEmpty()) {
            text = "UNKNOWN";
        }
        text = text.trim();

        // 1. Tính toán chuỗi ký hiệu Code 128-B
        int startCode = 104; // START B
        int checksum = startCode;
        int[] symbols = new int[text.length() + 3];
        symbols[0] = startCode;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int value = (c >= 32 && c <= 127) ? (c - 32) : 0;
            symbols[i + 1] = value;
            checksum += (i + 1) * value;
        }
        symbols[symbols.length - 2] = checksum % 103;
        symbols[symbols.length - 1] = 106; // STOP code

        // 2. Chuyển chuỗi ký hiệu thành mảng các module đen (true) / trắng (false)
        StringBuilder patternModules = new StringBuilder();
        for (int symbolIndex : symbols) {
            String pattern = CODE128_PATTERNS[symbolIndex];
            boolean isBar = true;
            for (int k = 0; k < pattern.length(); k++) {
                int runLength = pattern.charAt(k) - '0';
                for (int m = 0; m < runLength; m++) {
                    patternModules.append(isBar ? '1' : '0');
                }
                isBar = !isBar;
            }
        }

        int totalModules = patternModules.length();
        int quietZone = 10 * moduleWidth; // Lề trắng chuẩn 10 modules
        int imageWidth = (totalModules * moduleWidth) + (quietZone * 2);
        int fontHeight = 16;
        int imageHeight = barHeight + fontHeight + 20;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Nền trắng
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, imageWidth, imageHeight);

            // Vẽ các vạch đen
            g2d.setColor(Color.BLACK);
            int currentX = quietZone;
            for (int i = 0; i < totalModules; i++) {
                if (patternModules.charAt(i) == '1') {
                    g2d.fillRect(currentX, 10, moduleWidth, barHeight);
                }
                currentX += moduleWidth;
            }

            // Vẽ nhãn chữ bên dưới mã vạch
            Font font = new Font(Font.MONOSPACED, Font.BOLD, 14);
            g2d.setFont(font);
            FontMetrics metrics = g2d.getFontMetrics(font);
            int textWidth = metrics.stringWidth(text);
            int textX = (imageWidth - textWidth) / 2;
            int textY = barHeight + fontHeight + 10;
            g2d.drawString(text, Math.max(textX, 5), textY);
        } finally {
            g2d.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể ghi ảnh mã vạch", e);
        }
    }

    /**
     * Sinh chuỗi Data URI base64 để hiển thị trực tiếp trên thẻ img của HTML/React.
     */
    public static String generateCode128Base64(String text) {
        byte[] pngBytes = generateCode128Png(text, 2, 60);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }
}

