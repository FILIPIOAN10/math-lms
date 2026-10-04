package ro.mathlms.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * JSON (de)serialisation of outbox payloads. A private mapper, not the MVC one: the stored payload format is
 * an internal detail that must not drift with web configuration. Unknown properties are ignored so rows
 * queued before a payload change stay readable.
 */
@Component
public class OutboxPayloadCodec {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload is not serialisable: " + payload.getClass().getName(), e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload cannot be read as " + type.getName(), e);
        }
    }
}
