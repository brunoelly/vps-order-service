package com.codechallenge.vps.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codechallenge.vps.order.domain.OrderCreatedEvent;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

	@Mock
	OutboxRepository repo;

	OutboxWriter writer;

	@BeforeEach
	void setUp() {
		writer = new OutboxWriter(repo, JsonMapper.builder().build());
		lenient().when(repo.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void appendPersistsSerializedPayload() {
		UUID orderId = UUID.randomUUID();
		UUID partnerId = UUID.randomUUID();
		Instant at = Instant.parse("2026-03-01T12:00:00Z");

		writer.append("Order", orderId, "OrderCreated", new OrderCreatedEvent(orderId, partnerId, at));

		ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
		verify(repo).save(captor.capture());
		OutboxMessage row = captor.getValue();
		assertEquals("Order", row.getAggregateType());
		assertEquals(orderId, row.getAggregateId());
		assertEquals("OrderCreated", row.getEventType());
		assertTrue(row.getPayload().contains(orderId.toString()));
		assertTrue(row.getPayload().contains(partnerId.toString()));
	}

	@Test
	void appendDoesNotSaveWhenMapperFails() {
		ObjectMapper failing = mock(ObjectMapper.class);
		when(failing.writeValueAsString(any())).thenThrow(new IllegalStateException("cannot write"));
		OutboxWriter broken = new OutboxWriter(repo, failing);

		assertThrows(IllegalStateException.class,
				() -> broken.append("Order", UUID.randomUUID(), "OrderCreated", "x"));
		verify(repo, never()).save(any());
	}
}
