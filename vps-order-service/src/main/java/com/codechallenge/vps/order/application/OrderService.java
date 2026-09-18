package com.codechallenge.vps.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codechallenge.vps.order.domain.IdempotencyConflictException;
import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderCreatedEvent;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderNotFoundException;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.order.domain.OrderStatusChangedEvent;
import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.order.infra.OrderSpecs;
import com.codechallenge.vps.outbox.OutboxWriter;
import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@Service
public class OrderService {

	private final PartnerRepository partners;
	private final OrderRepository orders;
	private final OutboxWriter outbox;

	public OrderService(PartnerRepository partners, OrderRepository orders, OutboxWriter outbox) {
		this.partners = partners;
		this.orders = orders;
		this.outbox = outbox;
	}

	@Transactional
	public Order place(UUID partnerId, String idempotencyKey, List<OrderItem> items) {
		Partner partner = partners.findByIdForUpdate(partnerId)
				.orElseThrow(() -> new PartnerNotFoundException(partnerId));

		var existing = orders.findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey);
		if (existing.isPresent()) {
			Order previous = existing.get();
			if (previous.hasSameLines(items)) {
				return previous;
			}
			throw new IdempotencyConflictException(idempotencyKey);
		}

		Order order = Order.create(partnerId, idempotencyKey, items);
		partner.reserve(order.getTotal());
		partners.save(partner);
		Order saved = orders.save(order);
		outbox.append("Order", saved.getId(), "OrderCreated",
				new OrderCreatedEvent(saved.getId(), saved.getPartnerId(), saved.getCreatedAt()));
		return saved;
	}

	@Transactional(readOnly = true)
	public Order get(UUID orderId) {
		return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
	}

	@Transactional(readOnly = true)
	public Page<Order> search(
			UUID partnerId,
			OrderStatus status,
			Instant createdFrom,
			Instant createdTo,
			Pageable pageable) {
		Specification<Order> spec = OrderSpecs.partnerId(partnerId)
				.and(OrderSpecs.status(status))
				.and(OrderSpecs.createdFrom(createdFrom))
				.and(OrderSpecs.createdTo(createdTo));
		return orders.findAll(spec, pageable);
	}

	@Transactional
	public Order changeStatus(UUID orderId, OrderStatus target) {
		if (target == null) {
			throw new IllegalArgumentException("status is required");
		}
		if (target == OrderStatus.CANCELLED) {
			return cancel(orderId);
		}
		Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
		OrderStatus from = order.getStatus();
		order.transitionTo(target);
		Order saved = orders.save(order);
		outbox.append("Order", saved.getId(), "OrderStatusChanged",
				new OrderStatusChangedEvent(saved.getId(), saved.getPartnerId(), from, saved.getStatus(), saved.getUpdatedAt()));
		return saved;
	}

	@Transactional
	public Order cancel(UUID orderId) {
		Order current = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
		Partner partner = partners.findByIdForUpdate(current.getPartnerId())
				.orElseThrow(() -> new PartnerNotFoundException(current.getPartnerId()));
		Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

		OrderStatus from = order.getStatus();
		BigDecimal held = order.cancel();
		if (held.compareTo(BigDecimal.ZERO) > 0) {
			partner.release(held);
		}
		partners.save(partner);
		Order saved = orders.save(order);
		outbox.append("Order", saved.getId(), "OrderStatusChanged",
				new OrderStatusChangedEvent(saved.getId(), saved.getPartnerId(), from, saved.getStatus(), saved.getUpdatedAt()));
		return saved;
	}
}
