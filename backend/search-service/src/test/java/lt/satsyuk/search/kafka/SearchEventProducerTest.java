package lt.satsyuk.search.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void sendsQueryToConfiguredTopic() {
        SearchEventProducer producer = new SearchEventProducer(kafkaTemplate, "custom-search-events");
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send("custom-search-events", "java")).thenReturn(future);

        producer.sendSearchEvent("java");

        verify(kafkaTemplate).send("custom-search-events", "java");
    }
}

