package vn.wms.ai.extraction;

import org.junit.jupiter.api.Test;
import vn.wms.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đọc xlsx (kiểu luồng), xls và csv tại chỗ; file dựng ngay trong test bằng POI. */
class SpreadsheetReaderTest {

    @Test
    void xlsx_readsHeaderRowsRawNumbersAndPercent() {
        byte[] bytes = TestSpreadsheets.xlsx(TestSpreadsheets.productRows(3));

        SheetTable table = SpreadsheetReader.readXlsx(bytes);

        assertThat(table.headers()).containsExactly("Mã hàng", "Tên hàng hóa", "Đơn vị tính", "Giá nhập", "Giá bán",
                "Thuế VAT", "Nhóm hàng");
        assertThat(table.rows()).hasSize(3);
        assertThat(table.totalDataRows()).isEqualTo(3);
        assertThat(table.truncated()).isFalse();
        SheetTable.SheetRow first = table.rows().get(0);
        assertThat(first.sourceRow()).isEqualTo(2);
        assertThat(first.cells()).containsExactly("SP001", "Sản phẩm 1", "Thùng", "150001", "180000.5", "10%",
                "Đồ uống");
    }

    @Test
    void xlsx_largeNumbersAreNotWrittenInScientificNotation() {
        byte[] bytes = TestSpreadsheets.xlsx(List.of(
                List.of("Mã hàng", "Mã vạch"),
                List.of("SP001", 8938501234567L)));

        assertThat(SpreadsheetReader.readXlsx(bytes).rows().get(0).cells()).containsExactly("SP001", "8938501234567");
    }

    @Test
    void xlsx_skipsTitleRowAndEmptyRowsAndUsesFirstSheetWithData() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("DANH SÁCH HÀNG HÓA THÁNG 11"));
        rows.add(List.of());
        rows.add(List.of("Mã hàng", "Tên hàng"));
        rows.add(List.of("SP001", "Nước suối"));
        rows.add(List.of());
        rows.add(List.of("SP002", "Bánh quy"));
        byte[] bytes = TestSpreadsheets.xlsxWithEmptyFirstSheet(rows);

        SheetTable table = SpreadsheetReader.readXlsx(bytes);

        assertThat(table.headers()).containsExactly("Mã hàng", "Tên hàng");
        assertThat(table.rows()).extracting(SheetTable.SheetRow::sourceRow).containsExactly(4, 6);
        assertThat(table.totalDataRows()).isEqualTo(2);
    }

    @Test
    void xlsx_blankHeaderGetsColumnLetterAndShortRowsArePadded() {
        byte[] bytes = TestSpreadsheets.xlsx(List.of(
                Arrays.asList("Mã hàng", null, "Tên hàng"),
                List.of("SP001", "x", "Nước suối", "thừa"),
                List.of("SP002")));

        SheetTable table = SpreadsheetReader.readXlsx(bytes);

        assertThat(table.headers()).containsExactly("Mã hàng", "Cột B", "Tên hàng", "Cột D");
        assertThat(table.rows().get(1).cells()).containsExactly("SP002", "", "", "");
    }

    @Test
    void xlsx_moreThan200RowsKeepsFirst200AndCountsAll() {
        byte[] bytes = TestSpreadsheets.xlsx(TestSpreadsheets.productRows(250));

        SheetTable table = SpreadsheetReader.readXlsx(bytes);

        assertThat(table.rows()).hasSize(ExtractionLimits.MAX_ROWS);
        assertThat(table.totalDataRows()).isEqualTo(250);
        assertThat(table.truncated()).isTrue();
        assertThat(table.rows().get(199).cells().get(0)).isEqualTo("SP200");
    }

    @Test
    void xls_readsCellsLikeXlsx() {
        byte[] bytes = TestSpreadsheets.xls(TestSpreadsheets.productRows(2));

        SheetTable table = SpreadsheetReader.readXls(bytes);

        assertThat(table.headers()).hasSize(7).startsWith("Mã hàng");
        assertThat(table.rows().get(1).cells()).containsExactly("SP002", "Sản phẩm 2", "Thùng", "150002", "180000.5",
                "10%", "Đồ uống");
    }

    @Test
    void csv_withBomQuotesEmbeddedCommaAndNewline() {
        String csv = "﻿Mã hàng,Tên hàng,Giá bán\r\n"
                + "SP001,\"Bánh quy, hộp 200g\",\"1.250.000\"\r\n"
                + "SP002,\"Nước \"\"Lavie\"\"\nchai 500ml\",12000\r\n";

        SheetTable table = SpreadsheetReader.readCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(table.headers()).containsExactly("Mã hàng", "Tên hàng", "Giá bán");
        assertThat(table.rows().get(0).cells()).containsExactly("SP001", "Bánh quy, hộp 200g", "1.250.000");
        assertThat(table.rows().get(1).cells()).containsExactly("SP002", "Nước \"Lavie\"\nchai 500ml", "12000");
    }

    @Test
    void csv_semicolonDelimiterIsDetected() {
        String csv = "Mã;Tên;Giá bán\nSP001;Bánh;12,5\n";

        SheetTable table = SpreadsheetReader.readCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(table.headers()).containsExactly("Mã", "Tên", "Giá bán");
        assertThat(table.rows().get(0).cells()).containsExactly("SP001", "Bánh", "12,5");
    }

    @Test
    void csv_notUtf8IsRejectedWithClearMessage() {
        byte[] latin1 = "Mã hàng,Tên\nSP001,Bánh\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> SpreadsheetReader.readCsv(latin1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void csv_unclosedQuoteIsRejected() {
        byte[] bytes = "Mã,Tên\nSP001,\"Bánh\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> SpreadsheetReader.readCsv(bytes))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CSV sai định dạng");
    }

    @Test
    void emptySheetAndHeaderOnlyAreRejected() {
        assertThatThrownBy(() -> SpreadsheetReader.readCsv("\n\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("trang tính trống");
        assertThatThrownBy(() -> SpreadsheetReader.readXlsx(TestSpreadsheets.xlsx(List.of(List.of("Mã", "Tên")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chỉ có dòng tiêu đề");
    }

    @Test
    void corruptedFilesAreRejectedAsBadRequest() {
        byte[] zipButNotExcel = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5};
        byte[] ole2Garbage = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
                (byte) 0xE1, 0, 0, 0};

        assertThatThrownBy(() -> SpreadsheetReader.readXlsx(zipButNotExcel))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getHttpStatus()).isEqualTo(400))
                .hasMessageContaining("Không đọc được file Excel (.xlsx)");
        assertThatThrownBy(() -> SpreadsheetReader.readXls(ole2Garbage))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getHttpStatus()).isEqualTo(400));
    }
}
