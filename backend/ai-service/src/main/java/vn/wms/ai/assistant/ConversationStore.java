package vn.wms.ai.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.wms.ai.client.AiChatMessage;
import vn.wms.ai.config.AiAssistantProperties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lịch sử hội thoại của trợ lý AI trên Redis (WMS-24.4). Khóa có cả doanh nghiệp lẫn người dùng
 * (docs/SAAS_SPEC.md, mục 2.3, quy tắc 8): {@value #KEY_PREFIX}{companyId}:{người dùng}:{conversationId}, nên mã hội
 * thoại của người khác hay doanh nghiệp khác không đọc được gì và được coi như hội thoại mới. Giữ
 * {@code app.ai.assistant.history-ttl} (mặc định 1 giờ) kể từ câu hỏi gần nhất, tối đa
 * {@code app.ai.assistant.max-exchanges} lượt hỏi đáp gần nhất.
 *
 * <p>Redis lỗi không làm hỏng câu trả lời: đọc lỗi thì bắt đầu hội thoại mới, ghi lỗi thì chỉ mất lịch sử.
 */
@Component
public class ConversationStore {

    static final String KEY_PREFIX = "wms:ai:chat:";
    /** Người dùng không xác định (gateway tắt JWT ở máy phát triển). */
    static final String ANONYMOUS = "-";

    private static final Pattern CONVERSATION_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final TypeReference<List<AiChatMessage>> MESSAGES = new TypeReference<>() {
    };
    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);

    /**
     * Hội thoại đang dùng cho câu hỏi hiện tại.
     *
     * @param id      mã hội thoại trả cho giao diện
     * @param history các lượt trước (rỗng khi hội thoại mới)
     * @param created {@code true} khi bắt đầu hội thoại mới (không gửi mã, mã sai, đã hết hạn hoặc không thuộc về
     *                người hỏi)
     */
    public record Conversation(String id, List<AiChatMessage> history, boolean created) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiAssistantProperties properties;

    public ConversationStore(StringRedisTemplate redis, ObjectMapper objectMapper, AiAssistantProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Mở hội thoại {@code conversationId} của người hỏi, hoặc bắt đầu hội thoại mới. */
    public Conversation open(long companyId, String username, String conversationId) {
        if (conversationId != null && CONVERSATION_ID.matcher(conversationId).matches()) {
            List<AiChatMessage> history = read(key(companyId, username, conversationId));
            if (history != null) {
                return new Conversation(conversationId, history, false);
            }
        }
        return new Conversation(UUID.randomUUID().toString(), List.of(), true);
    }

    /** Thêm một lượt hỏi đáp, giữ {@code max-exchanges} lượt gần nhất và đặt lại thời hạn. */
    public void append(long companyId, String username, Conversation conversation, String question, String answer) {
        List<AiChatMessage> messages = new ArrayList<>(conversation.history());
        messages.add(AiChatMessage.user(question));
        messages.add(AiChatMessage.assistant(limit(answer)));
        int maxMessages = properties.maxExchanges() * 2;
        if (messages.size() > maxMessages) {
            messages = new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        }
        try {
            redis.opsForValue().set(key(companyId, username, conversation.id()),
                    objectMapper.writeValueAsString(messages), properties.historyTtl());
        } catch (JsonProcessingException | DataAccessException ex) {
            log.warn("Không lưu được lịch sử hội thoại của trợ lý: {}", ex.getClass().getSimpleName());
        }
    }

    private List<AiChatMessage> read(String key) {
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.warn("Không đọc được lịch sử hội thoại của trợ lý: {}", ex.getClass().getSimpleName());
            return null;
        }
        if (json == null) {
            return null;
        }
        try {
            List<AiChatMessage> messages = objectMapper.readValue(json, MESSAGES);
            return validHistory(messages) ? List.copyOf(messages) : null;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.warn("Lịch sử hội thoại trên Redis bị hỏng: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /** Lịch sử phải xen kẽ user, assistant và bắt đầu bằng user (yêu cầu của Messages API). */
    private static boolean validHistory(List<AiChatMessage> messages) {
        if (messages == null || messages.size() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < messages.size(); i++) {
            AiChatMessage message = messages.get(i);
            String expected = i % 2 == 0 ? AiChatMessage.USER : AiChatMessage.ASSISTANT;
            if (message == null || !expected.equals(message.role())) {
                return false;
            }
        }
        return true;
    }

    private String limit(String answer) {
        int max = properties.maxStoredAnswerLength();
        return answer.length() <= max ? answer : answer.substring(0, max) + "...";
    }

    /**
     * Tên người dùng mã hóa base64url để ký tự lạ (dấu hai chấm) không làm lẫn khóa của hai người khác nhau.
     */
    static String key(long companyId, String username, String conversationId) {
        String user = username == null || username.isBlank() ? ANONYMOUS
                : Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + companyId + ":" + user + ":" + conversationId;
    }
}
