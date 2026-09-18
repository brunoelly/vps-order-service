package com.codechallenge.vps.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

	@Mock
	OutboxRepository repo;

	@Mock
	ApplicationEventPublisher events;

	OutboxDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new OutboxDispatcher(repo, events);
	}

	@Test
	void publishesUnpublishedRowsAndMarksThem() {
		UUID orderId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.create("Order", orderId, "OrderCreated", "{\"ok\":true}");
		when(repo.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(List.of(row));

		dispatcher.publishPending();

		ArgumentCaptor<OrderNotification> captor = ArgumentCaptor.forClass(OrderNotification.class);
		verify(events).publishEvent(captor.capture());
		assertEquals("OrderCreated", captor.getValue().eventType());
		assertEquals(orderId, captor.getValue().orderId());
		assertEquals("{\"ok\":true}", captor.getValue().payload());
		assertNotNull(row.getPublishedAt());
	}

	@Test
	void doesNothingWhenQueueIsEmpty() {
		when(repo.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(List.of());

		dispatcher.publishPending();

		verify(events, never()).publishEvent(any());
	}

	@Test
	void leavesRowUnpublishedWhenBrokerCallFails() {
		OutboxMessage row = OutboxMessage.create("Order", UUID.randomUUID(), "OrderCreated", "{}");
		when(repo.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(List.of(row));
		doThrow(new IllegalStateException("broker down")).when(events).publishEvent(any(OrderNotification.class));

		assertThrows(IllegalStateException.class, () -> dispatcher.publishPending());
		assertNull(row.getPublishedAt());
	}

	@Test
	void markPublishedRejectsNullInstant() {
		OutboxMessage row = OutboxMessage.create("Order", UUID.randomUUID(), "OrderCreated", "{}");
		assertThrows(IllegalArgumentException.class, () -> row.markPublished(null));
		assertNull(row.getPublishedAt());
	}
}
