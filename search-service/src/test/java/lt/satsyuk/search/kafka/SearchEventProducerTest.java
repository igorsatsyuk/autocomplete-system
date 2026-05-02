package lt.satsyuk.search.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void sendsQueryToConfiguredTopic() {
        SearchEventProducer producer = new SearchEventProducer(kafkaTemplate, "custom-search-events");

        producer.sendSearchEvent("java");

        verify(kafkaTemplate).send("custom-search-events", "java");
    }
}

