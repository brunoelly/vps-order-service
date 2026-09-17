package com.codechallenge.vps.outbox;

import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxWriter {

	private final OutboxRepository outbox;
	private final ObjectMapper mapper;

	public OutboxWriter(OutboxRepository outbox, ObjectMapper mapper) {
		this.outbox = outbox;
		this.mapper = mapper;
	}

	public void append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
		String json = mapper.writeValueAsString(payload);
		outbox.save(OutboxMessage.create(aggregateType, aggregateId, eventType, json));
	}
}
