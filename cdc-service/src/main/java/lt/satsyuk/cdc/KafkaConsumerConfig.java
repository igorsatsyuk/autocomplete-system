package lt.satsyuk.cdc;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final String CONSUMER_PROPERTIES_PREFIX = "spring.kafka.consumer.properties.";
    private static final int DEFAULT_MAX_POLL_INTERVAL_MS = 30 * 60 * 1000;

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset,
            Environment environment
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.putAll(resolveAdditionalConsumerProperties(environment));
        // clearIndex() may block during truncate rebuild; keep poll interval conservative by default.
        config.putIfAbsent(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, DEFAULT_MAX_POLL_INTERVAL_MS);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    private static Map<String, Object> resolveAdditionalConsumerProperties(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return Map.of();
        }

        Map<String, Object> properties = new HashMap<>();
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }

            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                if (!propertyName.startsWith(CONSUMER_PROPERTIES_PREFIX)) {
                    continue;
                }
                String kafkaPropertyName = propertyName.substring(CONSUMER_PROPERTIES_PREFIX.length());
                String propertyValue = environment.getProperty(propertyName);
                if (propertyValue != null) {
                    properties.putIfAbsent(kafkaPropertyName, propertyValue);
                }
            }
        }
        return properties;
    }
}

