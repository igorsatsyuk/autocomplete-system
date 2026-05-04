package lt.satsyuk.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void ignoresNullKafkaMessage() {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange(null);

        verify(updater, never()).updateQueryScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void ignoresBlankKafkaMessage() {
        DebeziumConsumer consumer = new DebeziumConsumer(updater, new ObjectMapper());

        consumer.handleDbChange("   ");

        verify(updater, never()).updateQueryScore(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}

