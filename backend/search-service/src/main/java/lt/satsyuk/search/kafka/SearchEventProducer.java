package lt.satsyuk.search.kafka;

import lt.satsyuk.common.kafka.KafkaTopics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class SearchEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String searchEventsTopic;

    public SearchEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${search.topics.events:" + KafkaTopics.SEARCH_EVENTS + "}") String searchEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.searchEventsTopic = searchEventsTopic;
    }

    public void sendSearchEvent(String query) {
        kafkaTemplate.send(searchEventsTopic, query).join();
    }
}