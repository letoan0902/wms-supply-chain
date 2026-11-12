package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.wms.ai.client.AiChatMessage;
import vn.wms.ai.config.AiAssistantProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lưu, đọc, giới hạn lịch sử hội thoại và cách ly theo doanh nghiệp, người dùng (Redis giả lập bằng một Map). */
class ConversationStoreTest {

    private final Map<String, String> redisData = new HashMap<>();
    private ValueOperations<String, String> values;
    private ConversationStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisData.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> {
            redisData.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        store = new ConversationStore(redis, new ObjectMapper(), new AiAssistantProperties(2048,
                Duration.ofSeconds(90), 5, Duration.ofHours(1), 3, 50, 20));
    }

    @Test
    void newConversationThenContinuedWithHistory() {
        ConversationStore.Conversation first = store.open(7L, "kho_01", null);
        assertThat(first.created()).isTrue();
        assertThat(first.history()).isEmpty();
        store.append(7L, "kho_01", first, "Tạo kho thế nào?", "Vào menu Kho và vị trí.");

        ConversationStore.Conversation second = store.open(7L, "kho_01", first.id());

        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.history()).containsExactly(AiChatMessage.user("Tạo kho thế nào?"),
                AiChatMessage.assistant("Vào menu Kho và vị trí."));
        verify(values).set(eq(ConversationStore.key(7L, "kho_01", first.id())), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void conversationOfAnotherUserOrCompanyStartsNewConversation() {
        ConversationStore.Conversation mine = store.open(7L, "kho_01", null);
        store.append(7L, "kho_01", mine, "Câu hỏi riêng", "Câu trả lời riêng");

        ConversationStore.Conversation otherUser = store.open(7L, "ban_hang_01", mine.id());
        ConversationStore.Conversation otherCompany = store.open(8L, "kho_01", mine.id());

        assertThat(otherUser.created()).isTrue();
        assertThat(otherUser.history()).isEmpty();
        assertThat(otherUser.id()).isNotEqualTo(mine.id());
        assertThat(otherCompany.created()).isTrue();
        assertThat(otherCompany.history()).isEmpty();
        assertThat(otherCompany.id()).isNotEqualTo(mine.id());
    }

    @Test
    void keyContainsCompanyAndEncodedUserSoSimilarNamesDoNotCollide() {
        String id = "3f1c2b9e-1111-4a2b-9c3d-0123456789ab";
        assertThat(ConversationStore.key(7L, "a:b", id)).startsWith("wms:ai:chat:7:").endsWith(":" + id);
        assertThat(ConversationStore.key(7L, "a:b", id)).isNotEqualTo(ConversationStore.key(7L, "a_b", id));
        assertThat(ConversationStore.key(7L, null, id)).isEqualTo("wms:ai:chat:7:-:" + id);
    }

    @Test
    void historyKeepsOnlyTheLastExchangesAndLimitsAnswerLength() {
        ConversationStore.Conversation conversation = store.open(7L, "u", null);
        for (int i = 1; i <= 5; i++) {
            store.append(7L, "u", conversation, "Câu " + i, "Đáp " + i + " " + "x".repeat(100));
            conversation = store.open(7L, "u", conversation.id());
        }

        assertThat(conversation.history()).hasSize(6);
        assertThat(conversation.history().get(0)).isEqualTo(AiChatMessage.user("Câu 3"));
        assertThat(conversation.history().get(5).text()).hasSize(53).endsWith("...");
    }

    @Test
    void invalidExpiredOrCorruptedIdStartsNewConversation() {
        assertThat(store.open(7L, "u", "khong-phai-uuid").created()).isTrue();
        assertThat(store.open(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab").created()).isTrue();
        redisData.put(ConversationStore.key(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab"), "{hỏng");
        assertThat(store.open(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab").created()).isTrue();
        redisData.put(ConversationStore.key(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab"),
                "[{\"role\":\"assistant\",\"text\":\"lệch\"}]");
        assertThat(store.open(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab").created()).isTrue();
    }

    @Test
    void redisFailureDoesNotBreakTheAnswer() {
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("down"));
        doAnswer(invocation -> {
            throw new QueryTimeoutException("down");
        }).when(values).set(anyString(), anyString(), any(Duration.class));

        ConversationStore.Conversation conversation = store.open(7L, "u", "3f1c2b9e-1111-4a2b-9c3d-0123456789ab");
        store.append(7L, "u", conversation, "Câu", "Đáp");

        assertThat(conversation.created()).isTrue();
    }
}
