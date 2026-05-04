package lt.satsyuk.search.streams;

import lt.satsyuk.search.model.SearchStatRepository;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void normalizesLowercaseValueBeforeAggregation() {
        SearchStatsTopology topology = new SearchStatsTopology(
                repository,
                "search-events-test",
                "search-stats-test",
                "search-counts-test",
                "search-stats-app-test",
                "kafka-test:9092"
        );
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        StreamsBuilder builder = new StreamsBuilder();
        topology.build(builder);

        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), topology.streamsConfig())) {
            TestInputTopic<String, String> inputTopic = driver.createInputTopic(
                    "search-events-test",
                    new StringSerializer(),
                    new StringSerializer()
            );
            TestOutputTopic<String, Long> outputTopic = driver.createOutputTopic(
                    "search-stats-test",
                    new StringDeserializer(),
                    new LongDeserializer()
            );

            inputTopic.pipeInput(null, "  JaVa  ");

            KeyValue<String, Long> result = outputTopic.readKeyValue();
            assertThat(result.key).isEqualTo("java");
            assertThat(result.value).isEqualTo(1L);
            verify(repository).findById("java");
        }
    }

    @Test
    void usesLocaleRootForNormalization() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try {
            SearchStatsTopology topology = new SearchStatsTopology(
                    repository,
                    "search-events-test",
                    "search-stats-test",
                    "search-counts-test",
                    "search-stats-app-test",
                    "kafka-test:9092"
            );
            when(repository.findById(anyString())).thenReturn(Optional.empty());

            StreamsBuilder builder = new StreamsBuilder();
            topology.build(builder);

            try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), topology.streamsConfig())) {
                TestInputTopic<String, String> inputTopic = driver.createInputTopic(
                        "search-events-test",
                        new StringSerializer(),
                        new StringSerializer()
                );
                TestOutputTopic<String, Long> outputTopic = driver.createOutputTopic(
                        "search-stats-test",
                        new StringDeserializer(),
                        new LongDeserializer()
                );

                inputTopic.pipeInput(null, "I");

                KeyValue<String, Long> result = outputTopic.readKeyValue();
                assertThat(result.key).isEqualTo("i");
            }
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void filtersOutNullAndBlankValues() {
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

        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), topology.streamsConfig())) {
            TestInputTopic<String, String> inputTopic = driver.createInputTopic(
                    "search-events-test",
                    new StringSerializer(),
                    new StringSerializer()
            );
            TestOutputTopic<String, Long> outputTopic = driver.createOutputTopic(
                    "search-stats-test",
                    new StringDeserializer(),
                    new LongDeserializer()
            );

            inputTopic.pipeInput(null, (String) null);
            inputTopic.pipeInput(null, "   ");

            assertThat(outputTopic.isEmpty()).isTrue();
            verifyNoInteractions(repository);
        }
    }
}

