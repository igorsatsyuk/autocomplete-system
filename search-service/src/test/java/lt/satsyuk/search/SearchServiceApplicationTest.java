package lt.satsyuk.search;

import lt.satsyuk.search.streams.SearchStatsTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        try (MockedConstruction<KafkaStreams> kafkaStreams = org.mockito.Mockito.mockConstruction(KafkaStreams.class)) {
            KafkaStreams streams = application.kafkaStreams(topology);

            assertThat(streams).isNotNull();
            assertThat(kafkaStreams.constructed()).hasSize(1);
            verify(topology, times(1)).build(any(StreamsBuilder.class));
            verify(topology, times(1)).streamsConfig();
        }
    }

    @Test
    void kafkaStreamsBeanAppliesInitAndDestroyLifecycleInContext() {
        SearchStatsTopology topology = mock(SearchStatsTopology.class);
        doAnswer(invocation -> null).when(topology).build(any(StreamsBuilder.class));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "search-stats-app-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        when(topology.streamsConfig()).thenReturn(properties);

        SearchServiceApplication application = new SearchServiceApplication();
        Bean beanMetadata;
        try {
            Method kafkaStreamsMethod = SearchServiceApplication.class
                    .getDeclaredMethod("kafkaStreams", SearchStatsTopology.class);
            beanMetadata = kafkaStreamsMethod.getAnnotation(Bean.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("kafkaStreams bean method is missing", e);
        }

        try (MockedConstruction<KafkaStreams> kafkaStreams = org.mockito.Mockito.mockConstruction(KafkaStreams.class)) {
            try (GenericApplicationContext context = new GenericApplicationContext()) {
                context.registerBean(SearchStatsTopology.class, () -> topology);

                RootBeanDefinition definition = new RootBeanDefinition(
                        KafkaStreams.class,
                        () -> application.kafkaStreams(context.getBean(SearchStatsTopology.class))
                );
                definition.setInitMethodName(beanMetadata.initMethod());
                definition.setDestroyMethodName(beanMetadata.destroyMethod());
                context.registerBeanDefinition("kafkaStreams", definition);

                context.refresh();

                assertThat(kafkaStreams.constructed()).hasSize(1);
                verify(kafkaStreams.constructed().get(0), times(1)).start();
            }

            verify(kafkaStreams.constructed().get(0), times(1)).close();
        }
    }
}

