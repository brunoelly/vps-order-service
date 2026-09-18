package com.codechallenge.vps.order.infra;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderStatus;

public final class OrderSpecs {

	private OrderSpecs() {
	}

	public static Specification<Order> partnerId(UUID partnerId) {
		return (root, query, cb) -> partnerId == null ? null : cb.equal(root.get("partnerId"), partnerId);
	}

	public static Specification<Order> status(OrderStatus status) {
		return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
	}

	public static Specification<Order> createdFrom(Instant from) {
		return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
	}

	public static Specification<Order> createdTo(Instant to) {
		return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
	}
}
