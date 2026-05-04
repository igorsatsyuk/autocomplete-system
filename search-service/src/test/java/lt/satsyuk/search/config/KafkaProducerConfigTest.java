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
        Map<String, Object> properties = ((DefaultKafkaProducerFactory<String, String>) factory).getConfigurationProperties();
        assertThat(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka-test:9092");
        assertThat(properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
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

