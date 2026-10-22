package vn.wms.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void fromSpringDataPageCopiesPagingMetadata() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }

    @Test
    void ofComputesTotalPagesAndBoundaryFlags() {
        PageResponse<Integer> last = PageResponse.of(List.of(5), 2, 2, 5);
        PageResponse<Integer> empty = PageResponse.of(List.of(), 0, 20, 0);

        assertThat(last.totalPages()).isEqualTo(3);
        assertThat(last.last()).isTrue();
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.first()).isTrue();
        assertThat(empty.last()).isTrue();
    }
}
