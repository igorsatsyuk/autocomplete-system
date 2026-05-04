package lt.satsyuk.search;

import lt.satsyuk.search.streams.SearchStatsTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceApplicationTest {

    @Test
    void kafkaStreamsBeanBuildsTopologyAndCreatesStreamInstance() {
        SearchStatsTopology topology = mock(SearchStatsTopology.class);
        doAnswer(invocation -> null).when(topology).build(any(StreamsBuilder.class));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "search-stats-app-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        when(topology.streamsConfig()).thenReturn(properties);

        SearchServiceApplication application = new SearchServiceApplication();
        KafkaStreams streams = application.kafkaStreams(topology);

        try {
            assertThat(streams).isNotNull();
            verify(topology).build(any(StreamsBuilder.class));
            verify(topology).streamsConfig();
        } finally {
            streams.close();
        }
    }
}

