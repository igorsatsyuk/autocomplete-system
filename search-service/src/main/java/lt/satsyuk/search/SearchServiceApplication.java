package lt.satsyuk.search;

import lt.satsyuk.search.streams.SearchStatsTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }

    @Bean
    public KafkaStreams kafkaStreams(SearchStatsTopology topology) {
        StreamsBuilder builder = new StreamsBuilder();
        topology.build(builder);
        KafkaStreams streams = new KafkaStreams(builder.build(), topology.streamsConfig());
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        return streams;
    }
}