package lt.satsyuk.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DebeziumConsumerTest {

    @Mock
    private RedisSearchUpdater updater;

    @Test
    void updatesRedisForValidDebeziumPayload() {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange("""
                {
                  "payload": {
                    "after": {
                      "query": "java",
                      "frequency": 8
                    }
                  }
                }
                """);

        verify(updater).updateQueryScore("java", 8L);
    }

    @Test
    void ignoresDeleteEventWithoutAfterPayload() {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange("""
                {
                  "payload": {
                    "after": null
                  }
                }
                """);

        verify(updater, never()).updateQueryScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void ignoresBlankQuery() {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange("""
                {
                  "payload": {
                    "after": {
                      "query": "   ",
                      "frequency": 3
                    }
                  }
                }
                """);

        verify(updater, never()).updateQueryScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    void ignoresInvalidKafkaMessages(String message) {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange(message);

        verify(updater, never()).updateQueryScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private static Stream<Arguments> invalidMessages() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("   "),
                Arguments.of("{not-json")
        );
    }
}

