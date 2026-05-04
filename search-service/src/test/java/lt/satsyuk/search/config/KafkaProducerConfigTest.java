package lt.satsyuk.search.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    void producerFactoryContainsExpectedKafkaProperties() {
        KafkaProducerConfig config = new KafkaProducerConfig();

        ProducerFactory<String, String> factory = config.producerFactory("kafka-test:9092");

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        DefaultKafkaProducerFactory<String, String> kafkaFactory = (DefaultKafkaProducerFactory<String, String>) factory;
        Map<String, Object> properties = kafkaFactory.getConfigurationProperties();
        assertThat(properties)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-test:9092")
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }

    @Test
    void kafkaTemplateIsCreatedFromProducerFactory() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ProducerFactory<String, String> factory = config.producerFactory("kafka-test:9092");

        KafkaTemplate<String, String> template = config.kafkaTemplate(factory);

        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isSameAs(factory);
    }
}

