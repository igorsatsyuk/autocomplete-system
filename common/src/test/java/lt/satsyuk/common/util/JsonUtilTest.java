package lt.satsyuk.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilTest {

    @Test
    void fromJsonTypeReferenceShouldDeserializeGenericPayload() {
        String json = "{\"ja\":[1,2],\"jav\":[3]}";

        Map<String, List<Integer>> result = JsonUtil.fromJson(
                json,
                new TypeReference<Map<String, List<Integer>>>() {
                }
        );

        assertEquals(List.of(1, 2), result.get("ja"));
        assertEquals(List.of(3), result.get("jav"));
    }

    @Test
    void fromJsonTypeReferenceShouldFailOnBlankJson() {
        TypeReference<Map<String, Integer>> typeReference = new TypeReference<Map<String, Integer>>() {
        };

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JsonUtil.fromJson("   ", typeReference)
        );

        assertEquals("JSON input must not be null or blank", ex.getMessage());
    }

    @Test
    void fromJsonTypeReferenceShouldFailOnNullTypeReference() {
        assertThrows(NullPointerException.class, () -> JsonUtil.fromJson("{}", (TypeReference<Map<String, Integer>>) null));
    }

    @Test
    void fromJsonTypeReferenceShouldWrapJacksonException() {
        TypeReference<Map<String, Integer>> typeReference = new TypeReference<Map<String, Integer>>() {
        };

        JsonUtilException ex = assertThrows(
                JsonUtilException.class,
                () -> JsonUtil.fromJson("{bad json}", typeReference)
        );

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
    }
}
