package com.codechallenge.vps.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codechallenge.vps.order.domain.IdempotencyConflictException;
import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderNotFoundException;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@Service
public class OrderService {

	private final PartnerRepository partners;
	private final OrderRepository orders;

	public OrderService(PartnerRepository partners, OrderRepository orders) {
		this.partners = partners;
		this.orders = orders;
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
		return orders.save(order);
	}

	@Transactional
	public Order changeStatus(UUID orderId, OrderStatus target) {
		if (target == null) {
			throw new IllegalArgumentException("status is required");
		}
		if (target == OrderStatus.CANCELADO) {
			return cancel(orderId);
		}
		Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
		order.transitionTo(target);
		return orders.save(order);
	}

	@Transactional
	public Order cancel(UUID orderId) {
		Order current = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
		Partner partner = partners.findByIdForUpdate(current.getPartnerId())
				.orElseThrow(() -> new PartnerNotFoundException(current.getPartnerId()));
		Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

		BigDecimal held = order.cancel();
		if (held.compareTo(BigDecimal.ZERO) > 0) {
			partner.release(held);
		}
		partners.save(partner);
		return orders.save(order);
	}
}
