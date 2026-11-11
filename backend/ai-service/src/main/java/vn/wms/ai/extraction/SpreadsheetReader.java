package vn.wms.ai.extraction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import vn.wms.common.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Đọc bảng tính ngay trong ai-service, không gửi cả file cho Claude:
 * <ul>
 *   <li>xlsx: đọc kiểu luồng (XSSF event API) nên file 10 MB không bị bung toàn bộ vào bộ nhớ;</li>
 *   <li>xls: Excel 97-2003 qua HSSF;</li>
 *   <li>csv: UTF-8 (có hoặc không có BOM), tự nhận dấu phân cách phẩy, chấm phẩy hoặc tab, ô có dấu ngoặc kép.</li>
 * </ul>
 * Lấy trang tính đầu tiên có dữ liệu. Số đọc theo giá trị thật trong ô (không theo định dạng hiển thị) để không mất
 * chữ số; ô định dạng phần trăm thành "10%", ô ngày thành yyyy-MM-dd.
 */
public final class SpreadsheetReader {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetReader.class);
    private static final String UNREADABLE = "Không đọc được file %s: file bị hỏng, có mật khẩu hoặc không phải "
            + "bảng tính hợp lệ. Lượt này không bị tính.";

    private SpreadsheetReader() {
    }

    public static SheetTable read(UploadedFile file) {
        return switch (file.type()) {
            case XLSX -> readXlsx(file.bytes());
            case XLS -> readXls(file.bytes());
            case CSV -> readCsv(file.bytes());
            default -> throw new IllegalArgumentException("Không phải bảng tính: " + file.type());
        };
    }

    // ---------------------------------------------------------------- xlsx

    static SheetTable readXlsx(byte[] bytes) {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            RowCollector collector = new RowCollector();
            while (sheets.hasNext()) {
                collector = new RowCollector();
                try (InputStream sheet = sheets.next()) {
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, new SheetHandler(collector),
                            new RawValueFormatter(), false));
                    parser.parse(new InputSource(sheet));
                }
                if (collector.hasData()) {
                    break;
                }
            }
            return collector.toTable();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.info("Không đọc được file xlsx: {}", ex.getClass().getSimpleName());
            throw ApiException.badRequest(String.format(UNREADABLE, "Excel (.xlsx)"));
        }
    }

    /** Nhận từng ô từ bộ đọc luồng của POI và chuyển cả dòng cho {@link RowCollector}. */
    private static final class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final RowCollector collector;
        private Map<Integer, String> cells = new HashMap<>();
        private int nextColumn;

        SheetHandler(RowCollector collector) {
            this.collector = collector;
        }

        @Override
        public void startRow(int rowNum) {
            cells = new HashMap<>();
            nextColumn = 0;
        }

        @Override
        public void endRow(int rowNum) {
            collector.accept(rowNum + 1, cells);
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference != null ? new CellReference(cellReference).getCol() : nextColumn;
            nextColumn = column + 1;
            cells.put(column, formattedValue);
        }
    }

    /**
     * Trả giá trị số thật thay vì chuỗi hiển thị theo định dạng ô (tránh "1.5E+12" hay "150,000" phụ thuộc
     * locale), giữ ký hiệu % với ô phần trăm và đổi ô ngày thành yyyy-MM-dd.
     */
    static final class RawValueFormatter extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString,
                                            boolean use1904Windowing) {
            return numericText(value, formatIndex, formatString, use1904Windowing);
        }
    }

    static String numericText(double value, int formatIndex, String formatString, boolean use1904Windowing) {
        if (formatString != null && DateUtil.isADateFormat(formatIndex, formatString)
                && DateUtil.isValidExcelDate(value)) {
            LocalDateTime dateTime = DateUtil.getLocalDateTime(value, use1904Windowing);
            return dateTime.toLocalDate().toString();
        }
        BigDecimal number = BigDecimal.valueOf(value);
        if (formatString != null && formatString.contains("%")) {
            return plain(number.movePointRight(2)) + "%";
        }
        return plain(number);
    }

    private static String plain(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    // ---------------------------------------------------------------- xls

    static SheetTable readXls(byte[] bytes) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            RowCollector collector = new RowCollector();
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                collector = new RowCollector();
                Sheet sheet = workbook.getSheetAt(index);
                for (Row row : sheet) {
                    Map<Integer, String> cells = new HashMap<>();
                    for (Cell cell : row) {
                        cells.put(cell.getColumnIndex(), xlsCellText(cell));
                    }
                    collector.accept(row.getRowNum() + 1, cells);
                }
                if (collector.hasData()) {
                    break;
                }
            }
            return collector.toTable();
        } catch (ApiException ex) {
            throw ex;
        } catch (EncryptedDocumentException ex) {
            throw ApiException.badRequest("File Excel (.xls) có mật khẩu, hãy bỏ mật khẩu rồi tải lại. "
                    + "Lượt này không bị tính.");
        } catch (Exception ex) {
            log.info("Không đọc được file xls: {}", ex.getClass().getSimpleName());
            throw ApiException.badRequest(String.format(UNREADABLE, "Excel (.xls)"));
        }
    }

    private static String xlsCellText(Cell cell) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getRichStringCellValue().getString();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : numericText(cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(),
                    cell.getCellStyle().getDataFormatString(), false);
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> "";
        };
    }

    // ---------------------------------------------------------------- csv

    static SheetTable readCsv(byte[] bytes) {
        String text = decodeUtf8(bytes);
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .setIgnoreSurroundingSpaces(true)
                .get();
        RowCollector collector = new RowCollector();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                Map<Integer, String> cells = new HashMap<>();
                for (int column = 0; column < record.size(); column++) {
                    cells.put(column, record.get(column));
                }
                collector.accept((int) record.getRecordNumber(), cells);
            }
        } catch (IOException | UncheckedIOException | IllegalStateException ex) {
            log.info("Không đọc được file csv: {}", ex.getClass().getSimpleName());
            throw ApiException.badRequest("File CSV sai định dạng (thường do dấu ngoặc kép chưa đóng). "
                    + "Lượt này không bị tính.");
        }
        return collector.toTable();
    }

    /** Giải mã UTF-8 nghiêm ngặt, bỏ BOM ở đầu; file lưu bằng bảng mã khác thì báo lỗi rõ ràng. */
    static String decodeUtf8(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw ApiException.badRequest("File CSV phải lưu bằng bảng mã UTF-8 (trong Excel chọn \"CSV UTF-8\"). "
                    + "Lượt này không bị tính.");
        }
    }

    /** Chọn dấu phân cách xuất hiện nhiều nhất (ngoài ngoặc kép) ở dòng có dữ liệu đầu tiên: phẩy, chấm phẩy, tab. */
    static char detectDelimiter(String text) {
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean quoted = false;
        for (char ch : firstLine.toCharArray()) {
            if (ch == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (ch == ',') {
                    commas++;
                } else if (ch == ';') {
                    semicolons++;
                } else if (ch == '\t') {
                    tabs++;
                }
            }
        }
        if (semicolons > commas && semicolons >= tabs) {
            return ';';
        }
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return ',';
    }
}
