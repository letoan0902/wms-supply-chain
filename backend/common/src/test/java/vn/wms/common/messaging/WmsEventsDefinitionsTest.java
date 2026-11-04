package vn.wms.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hằng số trong {@link WmsEvents} phải khớp với topology được nạp vào broker từ docker/rabbitmq/definitions.json.
 * Nhờ vậy service nào dùng hằng số cũng chắc chắn exchange, queue, binding và chính sách dead-letter đã tồn tại.
 */
class WmsEventsDefinitionsTest {

    private static final Pattern NAME_CONVENTION = Pattern.compile("^[a-z]+\\.[a-z-]+$");

    private static JsonNode definitions;

    @BeforeAll
    static void loadDefinitions() throws IOException {
        definitions = new ObjectMapper().readTree(Files.readAllBytes(findDefinitionsFile()));
    }

    /** Gradle chạy test với thư mục làm việc là backend/common; đi ngược lên để tìm thư mục gốc của repo. */
    private static Path findDefinitionsFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("docker").resolve("rabbitmq").resolve("definitions.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không tìm thấy docker/rabbitmq/definitions.json từ " + Paths.get("").toAbsolutePath());
    }

    @Test
    void eventExchangeAndDeadLetterExchangeAreDurableTopics() {
        for (String name : List.of(WmsEvents.EXCHANGE, WmsEvents.DEAD_LETTER_EXCHANGE)) {
            Optional<JsonNode> exchange = find("exchanges", name);
            assertThat(exchange).as("exchange %s", name).isPresent();
            assertThat(exchange.get().path("type").asText()).isEqualTo("topic");
            assertThat(exchange.get().path("durable").asBoolean()).isTrue();
        }
    }

    @Test
    void everyQueueConstantExistsAndIsDurableWithoutArguments() {
        List<String> expected = new ArrayList<>(WmsEvents.Queues.ALL);
        expected.add(WmsEvents.DEAD_LETTER_QUEUE);
        for (String name : expected) {
            Optional<JsonNode> queue = find("queues", name);
            assertThat(queue).as("queue %s", name).isPresent();
            assertThat(queue.get().path("durable").asBoolean()).as("queue %s durable", name).isTrue();
            // Không đặt x-arguments trên queue (dead-letter đi qua policy) để service khai báo lại queue không bị lệch tham số.
            assertThat(queue.get().path("arguments").isEmpty()).as("queue %s không có arguments", name).isTrue();
        }
    }

    @Test
    void businessQueuesAreBoundToEventExchangeWithExpectedKeys() {
        assertThat(bindingKeys(WmsEvents.EXCHANGE, WmsEvents.Queues.INVENTORY_ORDER_EVENTS))
                .containsExactlyInAnyOrder(WmsEvents.RoutingKeys.ORDER_CREATED, WmsEvents.RoutingKeys.ORDER_DISPATCHED);
        assertThat(bindingKeys(WmsEvents.EXCHANGE, WmsEvents.Queues.NOTIFICATION_ORDER_EVENTS))
                .containsExactly(WmsEvents.RoutingKeys.ORDER_ALL);
        assertThat(bindingKeys(WmsEvents.EXCHANGE, WmsEvents.Queues.NOTIFICATION_INVENTORY_EVENTS))
                .containsExactly(WmsEvents.RoutingKeys.INVENTORY_ALL);
        assertThat(bindingKeys(WmsEvents.EXCHANGE, WmsEvents.Queues.NOTIFICATION_ALERTS))
                .containsExactly(WmsEvents.RoutingKeys.ALERT_ALL);
        assertThat(bindingKeys(WmsEvents.DEAD_LETTER_EXCHANGE, WmsEvents.DEAD_LETTER_QUEUE)).containsExactly("#");
    }

    @Test
    void everyRoutingKeyReachesAtLeastOneQueue() {
        for (String routingKey : WmsEvents.RoutingKeys.ALL) {
            List<String> queues = new ArrayList<>();
            for (JsonNode binding : list("bindings")) {
                if (binding.path("source").asText().equals(WmsEvents.EXCHANGE)
                        && topicMatches(binding.path("routing_key").asText(), routingKey)) {
                    queues.add(binding.path("destination").asText());
                }
            }
            assertThat(queues).as("routing key %s phải tới ít nhất một queue", routingKey).isNotEmpty();
        }
        assertThat(queuesReceiving(WmsEvents.RoutingKeys.ORDER_CREATED))
                .contains(WmsEvents.Queues.INVENTORY_ORDER_EVENTS, WmsEvents.Queues.NOTIFICATION_ORDER_EVENTS);
        assertThat(queuesReceiving(WmsEvents.RoutingKeys.INVENTORY_UPDATED))
                .containsExactly(WmsEvents.Queues.NOTIFICATION_INVENTORY_EVENTS);
        assertThat(queuesReceiving(WmsEvents.RoutingKeys.ALERT_LOW_STOCK))
                .containsExactly(WmsEvents.Queues.NOTIFICATION_ALERTS);
    }

    @Test
    void deadLetterPolicyCoversBusinessQueuesButNotTheDeadLetterQueue() {
        List<Pattern> deadLetterPatterns = new ArrayList<>();
        List<Pattern> retentionPatterns = new ArrayList<>();
        for (JsonNode policy : list("policies")) {
            JsonNode definition = policy.path("definition");
            assertThat(policy.path("apply-to").asText()).isEqualTo("queues");
            if (definition.path("dead-letter-exchange").asText().equals(WmsEvents.DEAD_LETTER_EXCHANGE)) {
                deadLetterPatterns.add(Pattern.compile(policy.path("pattern").asText()));
            }
            if (definition.has("message-ttl") && definition.has("max-length")) {
                retentionPatterns.add(Pattern.compile(policy.path("pattern").asText()));
            }
        }
        assertThat(deadLetterPatterns).as("phải có chính sách dead-letter").isNotEmpty();
        assertThat(retentionPatterns).as("phải có chính sách giới hạn queue dead-letter").isNotEmpty();
        for (String queue : WmsEvents.Queues.ALL) {
            assertThat(deadLetterPatterns.stream().anyMatch(p -> p.matcher(queue).find()))
                    .as("queue %s phải được chính sách dead-letter bao phủ", queue).isTrue();
        }
        assertThat(deadLetterPatterns.stream().anyMatch(p -> p.matcher(WmsEvents.DEAD_LETTER_QUEUE).find()))
                .as("queue dead-letter không được dead-letter lần nữa (tránh vòng lặp)").isFalse();
        assertThat(retentionPatterns.stream().anyMatch(p -> p.matcher(WmsEvents.DEAD_LETTER_QUEUE).find()))
                .as("queue dead-letter phải bị giới hạn TTL và độ dài").isTrue();
    }

    @Test
    void namesFollowDomainDotActionConvention() {
        for (String routingKey : WmsEvents.RoutingKeys.ALL) {
            assertThat(routingKey).matches(NAME_CONVENTION);
        }
        for (String queue : WmsEvents.Queues.ALL) {
            assertThat(queue).matches(NAME_CONVENTION);
        }
        assertThat(WmsEvents.DEAD_LETTER_QUEUE).matches(NAME_CONVENTION);
        for (String pattern : List.of(WmsEvents.RoutingKeys.ORDER_ALL, WmsEvents.RoutingKeys.INVENTORY_ALL,
                WmsEvents.RoutingKeys.ALERT_ALL)) {
            assertThat(pattern).matches("^[a-z]+\\.#$");
        }
    }

    // --- Tiện ích đọc file định nghĩa -----------------------------------------------------------------

    private static List<JsonNode> list(String field) {
        List<JsonNode> out = new ArrayList<>();
        definitions.path(field).forEach(out::add);
        return out;
    }

    private static Optional<JsonNode> find(String field, String name) {
        return list(field).stream().filter(node -> node.path("name").asText().equals(name)).findFirst();
    }

    private static List<String> bindingKeys(String exchange, String queue) {
        List<String> keys = new ArrayList<>();
        for (JsonNode binding : list("bindings")) {
            if (binding.path("source").asText().equals(exchange)
                    && binding.path("destination").asText().equals(queue)
                    && binding.path("destination_type").asText().equals("queue")) {
                keys.add(binding.path("routing_key").asText());
            }
        }
        return keys;
    }

    private static List<String> queuesReceiving(String routingKey) {
        List<String> queues = new ArrayList<>();
        for (JsonNode binding : list("bindings")) {
            if (binding.path("source").asText().equals(WmsEvents.EXCHANGE)
                    && topicMatches(binding.path("routing_key").asText(), routingKey)) {
                queues.add(binding.path("destination").asText());
            }
        }
        return queues;
    }

    /** Mô phỏng cách topic exchange so khớp: * là đúng một từ, # là không hoặc nhiều từ, từ ngăn cách bằng dấu chấm. */
    private static boolean topicMatches(String bindingPattern, String routingKey) {
        String regex = "^" + bindingPattern
                .replace(".", "\\.")
                .replace("*", "[^.]+")
                .replace("#", ".*") + "$";
        return Pattern.compile(regex).matcher(routingKey).matches();
    }
}
