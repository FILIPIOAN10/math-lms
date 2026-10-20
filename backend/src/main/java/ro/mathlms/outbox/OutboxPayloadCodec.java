package ro.mathlms.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * JSON (de)serialisation of outbox payloads. A private mapper, not the MVC one: the stored payload format is
 * an internal detail that must not drift with web configuration. Unknown properties are ignored so rows
 * queued before a payload change stay readable.
 */
@Component
public class OutboxPayloadCodec {

    // Jackson 2 defaults: rows queued before the Boot 4 upgrade are read exactly as they were written.
    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Outbox payload is not serialisable: " + payload.getClass().getName(), e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Outbox payload cannot be read as " + type.getName(), e);
        }
    }
}
