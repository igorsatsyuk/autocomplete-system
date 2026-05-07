package lt.satsyuk.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static String toJson(Object o) {
        Objects.requireNonNull(o, "Object to serialize must not be null");
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new JsonUtilException("JSON serialization error", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON input must not be null or blank");
        }
        Objects.requireNonNull(type, "Target type must not be null");
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new JsonUtilException("JSON deserialization error for type: " + type.getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON input must not be null or blank");
        }
        Objects.requireNonNull(typeReference, "Target type reference must not be null");
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonUtilException("JSON deserialization error for generic type", e);
        }
    }

    private JsonUtil() {}
}