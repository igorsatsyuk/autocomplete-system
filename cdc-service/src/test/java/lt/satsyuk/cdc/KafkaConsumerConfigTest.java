package lt.satsyuk.cdc;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaConsumerConfigTest {

    @Test
    void setsConservativeDefaultMaxPollIntervalWhenMissing() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        StandardEnvironment environment = new StandardEnvironment();

        DefaultKafkaConsumerFactory<String, String> factory =
                (DefaultKafkaConsumerFactory<String, String>) config.consumerFactory(
                        "localhost:9092",
                        "cdc-test-group",
                        "earliest",
                        environment
                );

        assertEquals(
                30 * 60 * 1000,
                factory.getConfigurationProperties().get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)
        );
    }

    @Test
    void keepsExplicitMaxPollIntervalFromProperties() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of("spring.kafka.consumer.properties.max.poll.interval.ms", "900000")
        ));

        DefaultKafkaConsumerFactory<String, String> factory =
                (DefaultKafkaConsumerFactory<String, String>) config.consumerFactory(
                        "localhost:9092",
                        "cdc-test-group",
                        "earliest",
                        environment
                );

        assertEquals(
                "900000",
                factory.getConfigurationProperties().get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)
        );
    }
}

