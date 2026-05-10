package lt.satsyuk.autocomplete.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutocompleteServiceRedisIT {

    private static final String AUTOCOMPLETE_KEY = "autocomplete:ja";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("autocomplete.redis-prefix", () -> "autocomplete:");
    }

    @LocalServerPort
    private int port;

    @Test
    void completeReturnsEntriesFromRedisSortedSet() {
        try (Jedis jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379))) {
            jedis.del(AUTOCOMPLETE_KEY);
            jedis.zadd(AUTOCOMPLETE_KEY, 5.0d, "java");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/complete?q=ja&limit=10"))
                .GET()
                .build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"query\":\"java\"");
            assertThat(response.body()).contains("\"score\":5.0");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void completeReturnsStrictJsonContract() throws Exception {
        try (Jedis jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379))) {
            jedis.del(AUTOCOMPLETE_KEY);
            jedis.zadd(AUTOCOMPLETE_KEY, 5.0d, "java");
            jedis.zadd(AUTOCOMPLETE_KEY, 3.0d, "javascript");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/complete?q=ja&limit=2"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode actual = objectMapper.readTree(response.body());
        JsonNode expected = objectMapper.readTree("""
                [
                  {"query":"java","score":5.0},
                  {"query":"javascript","score":3.0}
                ]
                """);

        assertThat(actual).isEqualTo(expected);
    }
}

