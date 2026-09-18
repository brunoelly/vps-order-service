package com.codechallenge.vps.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "outbox.poll.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

	private static final int BATCH = 50;

	private final OutboxRepository outbox;
	private final ApplicationEventPublisher events;

	public OutboxDispatcher(OutboxRepository outbox, ApplicationEventPublisher events) {
		this.outbox = outbox;
		this.events = events;
	}

	@Scheduled(fixedDelayString = "${outbox.poll.interval-ms:2000}")
	@Transactional
	public void publishPending() {
		List<OutboxMessage> batch = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, BATCH));
		for (OutboxMessage row : batch) {
			events.publishEvent(new OrderNotification(row.getEventType(), row.getAggregateId(), row.getPayload()));
			row.markPublished(Instant.now());
		}
	}
}
