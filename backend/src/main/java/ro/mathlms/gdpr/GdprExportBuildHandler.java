package ro.mathlms.gdpr;

import org.springframework.stereotype.Component;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxHandler;
import ro.mathlms.outbox.OutboxPayloadCodec;

/** Builds one queued export and emails its link. Idempotent: an export that is no longer PENDING is skipped. */
@Component
public class GdprExportBuildHandler implements OutboxHandler {

    private final OutboxPayloadCodec codec;
    private final GdprExportRequests requests;

    public GdprExportBuildHandler(OutboxPayloadCodec codec, GdprExportRequests requests) {
        this.codec = codec;
        this.requests = requests;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.GDPR_EXPORT_BUILD;
    }

    @Override
    public void handle(String payload) {
        requests.build(codec.deserialize(payload, GdprExportPayload.class).exportId());
    }
}
