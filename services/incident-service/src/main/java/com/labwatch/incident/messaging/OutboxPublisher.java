package com.labwatch.incident.messaging;

import com.labwatch.incident.domain.OutboxEvent;
import com.labwatch.incident.domain.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains PENDING outbox events to Kafka in creation order and marks them
 * PUBLISHED. Delivery is at-least-once: a crash between send and mark causes a
 * resend on restart, which consumers absorb by deduplicating on eventId.
 * A send failure stops the batch so ordering is preserved; the next tick retries.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findTop100ByStatusOrderByCreatedAt(OutboxEvent.Status.PENDING);
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("Outbox publish failed for event {} ({}); will retry",
                        event.getEventId(), event.getEventType(), exception);
                return;
            }
            event.markPublished(Instant.now(clock));
            outbox.save(event);
        }
    }
}
