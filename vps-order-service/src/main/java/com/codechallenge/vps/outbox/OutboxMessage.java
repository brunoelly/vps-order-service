package com.codechallenge.vps.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox")
public class OutboxMessage {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxMessage() {
	}

	public static OutboxMessage create(String aggregateType, UUID aggregateId, String eventType, String payload) {
		return new OutboxMessage(UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, Instant.now());
	}

	private OutboxMessage(
			UUID id,
			String aggregateType,
			UUID aggregateId,
			String eventType,
			String payload,
			Instant createdAt) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public void markPublished(Instant at) {
		if (at == null) {
			throw new IllegalArgumentException("publishedAt is required");
		}
		this.publishedAt = at;
	}

	public UUID getId() {
		return id;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
