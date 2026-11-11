package vn.wms.ai.config;

import org.junit.jupiter.api.Test;
import vn.wms.ai.AiTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Chọn chế độ chạy: chỉ gọi Claude thật khi đã tắt giả lập và có khóa (giá trị khóa ở đây là chuỗi giả). */
class AiClientConfigTest {

    @Test
    void mockFlagAlwaysWins() {
        assertThat(AiClientConfig.selectMode(AiTestSupport.properties(true, true), "khoa-gia")).isEqualTo(AiMode.MOCK);
    }

    @Test
    void blankKeyFallsBackToMock() {
        assertThat(AiClientConfig.selectMode(AiTestSupport.properties(false, true), null)).isEqualTo(AiMode.MOCK);
        assertThat(AiClientConfig.selectMode(AiTestSupport.properties(false, true), "  ")).isEqualTo(AiMode.MOCK);
    }

    @Test
    void keyAndMockOffSelectsLive() {
        assertThat(AiClientConfig.selectMode(AiTestSupport.properties(false, true), "khoa-gia")).isEqualTo(AiMode.LIVE);
    }
}
