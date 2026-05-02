package lt.satsyuk.search.streams;

import lt.satsyuk.search.model.SearchStatRepository;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SearchStatsTopologyTest {

    @Mock
    private SearchStatRepository repository;

    @Test
    void streamsConfigUsesConfiguredValues() {
        SearchStatsTopology topology = new SearchStatsTopology(
                repository,
                "search-events-test",
                "search-stats-test",
                "search-counts-test",
                "search-stats-app-test",
                "kafka-test:9092"
        );

        Properties props = topology.streamsConfig();

        assertThat(props.getProperty(StreamsConfig.APPLICATION_ID_CONFIG)).isEqualTo("search-stats-app-test");
        assertThat(props.getProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka-test:9092");
    }

    @Test
    void builtTopologyContainsConfiguredTopics() {
        SearchStatsTopology topology = new SearchStatsTopology(
                repository,
                "search-events-test",
                "search-stats-test",
                "search-counts-test",
                "search-stats-app-test",
                "kafka-test:9092"
        );
        StreamsBuilder builder = new StreamsBuilder();

        topology.build(builder);

        String description = builder.build().describe().toString();
        assertThat(description)
                .contains("search-events-test")
                .contains("search-stats-test");
    }
}

