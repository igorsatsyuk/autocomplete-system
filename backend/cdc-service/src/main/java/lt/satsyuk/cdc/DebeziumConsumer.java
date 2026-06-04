package lt.satsyuk.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lt.satsyuk.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DebeziumConsumer {

    private static final Logger log = LoggerFactory.getLogger(DebeziumConsumer.class);
    private static final String DEFAULT_TOPIC_PATTERN = KafkaTopics.DB_CHANGES_SEARCH_STATS_PATTERN;

    private final RedisSearchUpdater updater;
    private final ObjectMapper objectMapper;

    public DebeziumConsumer(RedisSearchUpdater updater, ObjectMapper objectMapper) {
        this.updater = updater;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topicPattern = "${cdc.topic-pattern:" + DEFAULT_TOPIC_PATTERN + "}")
    public void handleDbChange(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        log.debug("Received DB change event: {}", message);

        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            String operation = payload.path("op").asText("");
            if ("t".equals(operation)) {
                updater.clearIndex();
                return;
            }

            JsonNode after = payload.path("after");
            if (after.isMissingNode() || after.isNull()) {
                return;
            }

            String query = after.path("query").asText("").trim();
            long frequency = after.path("frequency").asLong(0L);
            if (query.isBlank()) {
                return;
            }

            updater.updateQueryScore(query, frequency);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse DB change event", e);
        }
    }
}