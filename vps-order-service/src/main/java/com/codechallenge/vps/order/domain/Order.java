package com.codechallenge.vps.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "orders",
		uniqueConstraints = @UniqueConstraint(name = "uk_orders_partner_idempotency", columnNames = { "partner_id", "idempotency_key" }))
public class Order {

	private static final int SCALE = 2;

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "partner_id", nullable = false)
	private UUID partnerId;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "order_id", nullable = false)
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal total;

	@Column(name = "reserved_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal reservedAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OrderStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Order() {
	}

	public static Order create(UUID partnerId, String idempotencyKey, List<OrderItem> items) {
		return new Order(UUID.randomUUID(), partnerId, idempotencyKey, items, Instant.now());
	}

	private Order(UUID id, UUID partnerId, String idempotencyKey, List<OrderItem> items, Instant now) {
		if (partnerId == null) {
			throw new IllegalArgumentException("partnerId is required");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("idempotencyKey is required");
		}
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("order must contain at least one item");
		}
		this.id = id;
		this.partnerId = partnerId;
		this.idempotencyKey = idempotencyKey.trim();
		this.items = new ArrayList<>(items);
		this.status = OrderStatus.PENDENTE;
		this.createdAt = now;
		this.updatedAt = now;
		recalculateTotal();
		this.reservedAmount = this.total;
	}

	public void recalculateTotal() {
		BigDecimal sum = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
		for (OrderItem item : items) {
			sum = sum.add(item.lineTotal());
		}
		this.total = sum;
	}

	public void transitionTo(OrderStatus target) {
		if (target == OrderStatus.CANCELADO) {
			cancel();
			return;
		}
		if (!status.canTransitionTo(target)) {
			throw new IllegalOrderTransitionException(status, target);
		}
		this.status = target;
		this.updatedAt = Instant.now();
	}

	public BigDecimal cancel() {
		if (!status.canTransitionTo(OrderStatus.CANCELADO)) {
			throw new IllegalOrderTransitionException(status, OrderStatus.CANCELADO);
		}
		BigDecimal held = reservedAmount;
		this.status = OrderStatus.CANCELADO;
		this.reservedAmount = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
		this.updatedAt = Instant.now();
		return held;
	}

	public boolean hasSameLines(List<OrderItem> other) {
		if (other == null || other.size() != items.size()) {
			return false;
		}
		List<String> left = lineKeys(items);
		List<String> right = lineKeys(other);
		left.sort(String::compareTo);
		right.sort(String::compareTo);
		return left.equals(right);
	}

	private static List<String> lineKeys(List<OrderItem> lines) {
		List<String> keys = new ArrayList<>(lines.size());
		for (OrderItem item : lines) {
			keys.add(item.getSku() + "|" + item.getQuantity() + "|" + item.getUnitPrice().toPlainString());
		}
		return keys;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPartnerId() {
		return partnerId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public List<OrderItem> getItems() {
		return List.copyOf(items);
	}

	public BigDecimal getTotal() {
		return total;
	}

	public BigDecimal getReservedAmount() {
		return reservedAmount;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
