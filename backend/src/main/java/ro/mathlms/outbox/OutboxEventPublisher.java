package ro.mathlms.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the outbox row for a side effect owed once the current transaction commits.
 *
 * <p>{@link Propagation#MANDATORY}: it must run INSIDE the business transaction so the row and the business
 * change are one atomic unit. A caller without a transaction is a bug and fails loudly here instead of
 * silently enqueuing work that may never have a matching commit.
 */
@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final OutboxPayloadCodec codec;

    public OutboxEventPublisher(OutboxEventRepository repository, OutboxPayloadCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(String eventType, Object payload) {
        return repository.save(OutboxEvent.of(eventType, codec.serialize(payload)));
    }
}
