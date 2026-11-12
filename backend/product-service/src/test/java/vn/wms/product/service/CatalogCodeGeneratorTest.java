package vn.wms.product.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCodeGeneratorTest {

    @Test
    void toCodeRemovesVietnameseAccentsAndSeparators() {
        assertThat(CatalogCodeGenerator.toCode("Đồ uống có ga", 50, "DM")).isEqualTo("DO-UONG-CO-GA");
        assertThat(CatalogCodeGenerator.toCode("  Thùng (24 lon) ", 20, "DVT")).isEqualTo("THUNG-24-LON");
        assertThat(CatalogCodeGenerator.toCode("đường", 20, "DVT")).isEqualTo("DUONG");
    }

    @Test
    void toCodeFallsBackWhenNothingIsLeftAndTruncatesWithoutTrailingDash() {
        assertThat(CatalogCodeGenerator.toCode("%%%", 20, "DVT")).isEqualTo("DVT");
        assertThat(CatalogCodeGenerator.toCode("Hộp giấy carton loại lớn", 10, "DVT")).isEqualTo("HOP-GIAY-C");
        assertThat(CatalogCodeGenerator.toCode("Hộp giấy carton", 9, "DVT")).isEqualTo("HOP-GIAY");
    }

    @Test
    void uniqueCodeAddsSuffixWithinMaxLength() {
        Set<String> taken = Set.of("THUNG", "THUNG-2");
        assertThat(CatalogCodeGenerator.uniqueCode("Thùng", 20, "DVT", taken::contains)).isEqualTo("THUNG-3");
        assertThat(CatalogCodeGenerator.uniqueCode("Lốc", 20, "DVT", taken::contains)).isEqualTo("LOC");

        Set<String> longTaken = Set.of("ABCDEFGHIJ");
        assertThat(CatalogCodeGenerator.uniqueCode("abcdefghij", 10, "DVT", longTaken::contains))
                .isEqualTo("ABCDEFGH-2").hasSize(10);
    }
}
