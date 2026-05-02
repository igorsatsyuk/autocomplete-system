package lt.satsyuk.search.streams;

import lt.satsyuk.common.kafka.KafkaTopics;
import lt.satsyuk.search.model.SearchStat;
import lt.satsyuk.search.model.SearchStatRepository;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class SearchStatsTopology {

    private final SearchStatRepository repository;
    private final String searchEventsTopic;
    private final String searchStatsTopic;
    private final String stateStoreName;
    private final String streamsApplicationId;
    private final String bootstrapServers;

    public SearchStatsTopology(
            SearchStatRepository repository,
            @Value("${search.topics.events:" + KafkaTopics.SEARCH_EVENTS + "}") String searchEventsTopic,
            @Value("${search.topics.stats:" + KafkaTopics.SEARCH_STATS + "}") String searchStatsTopic,
            @Value("${search.streams.state-store:search-counts}") String stateStoreName,
            @Value("${search.streams.application-id:" + KafkaTopics.SEARCH_STATS + "-app}") String streamsApplicationId,
            @Value("${spring.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers
    ) {
        this.repository = repository;
        this.searchEventsTopic = searchEventsTopic;
        this.searchStatsTopic = searchStatsTopic;
        this.stateStoreName = stateStoreName;
        this.streamsApplicationId = streamsApplicationId;
        this.bootstrapServers = bootstrapServers;
    }

    public void build(StreamsBuilder builder) {
        KStream<String, String> events = builder.stream(searchEventsTopic,
                Consumed.with(Serdes.String(), Serdes.String()));

        KTable<String, Long> counts = events
                .selectKey((key, value) -> value.toLowerCase())
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as(stateStoreName));

        counts.toStream().peek((query, count) -> {
            repository.findById(query)
                    .ifPresentOrElse(
                            stat -> {
                                stat.setFrequency(count);
                                repository.save(stat);
                            },
                            () -> repository.save(new SearchStat(query, count))
                    );
        }).to(searchStatsTopic, Produced.with(Serdes.String(), Serdes.Long()));
    }

    public Properties streamsConfig() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, streamsApplicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        return props;
    }
}