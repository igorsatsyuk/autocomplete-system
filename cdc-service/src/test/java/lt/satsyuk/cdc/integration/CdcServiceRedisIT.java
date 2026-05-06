package lt.satsyuk.cdc.integration;

import lt.satsyuk.common.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import java.util.concurrent.locks.LockSupport;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CdcServiceRedisIT {

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "cdc-it-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.properties.metadata.max.age.ms", () -> "1000");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("cdc.topic-pattern", () -> KafkaTopics.DB_CHANGES_SEARCH_STATS_PATTERN);
    }

    @Test
    void debeziumMessageUpdatesRedisAutocompleteIndex() throws Exception {
        createTopicIfMissing(KafkaTopics.DB_CHANGES_SEARCH_STATS);

        String payload = """
                {
                  "payload": {
                    "after": {
                      "query": "java",
                      "frequency": 10
                    }
                  }
                }
                """;

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(KafkaTopics.DB_CHANGES_SEARCH_STATS, "java", payload)).get();
        }

        long deadline = System.currentTimeMillis() + 10_000;
        Double score = null;
        while (System.currentTimeMillis() < deadline && score == null) {
            try (Jedis jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379), (int) Duration.ofSeconds(2).toMillis())) {
                score = jedis.zscore("autocomplete:ja", "java");
            }
            if (score == null) {
                LockSupport.parkNanos(Duration.ofMillis(250).toNanos());
            }
        }

        assertThat(score).isNotNull().isEqualTo(10.0d);
    }

    private static void createTopicIfMissing(String topicName) throws Exception {
        Map<String, Object> configs = Map.of("bootstrap.servers", kafka.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(configs)) {
            adminClient.createTopics(Collections.singletonList(new NewTopic(topicName, 1, (short) 1))).all().get();
        } catch (Exception _) {
            // Topic may already exist, which is acceptable for this test.
        }
    }
}

