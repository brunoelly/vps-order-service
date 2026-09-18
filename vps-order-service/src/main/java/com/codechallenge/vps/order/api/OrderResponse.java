package com.codechallenge.vps.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.codechallenge.vps.order.domain.Order;

public record OrderResponse(
		UUID id,
		UUID partnerId,
		String status,
		BigDecimal total,
		BigDecimal reservedAmount,
		List<OrderItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getPartnerId(),
				order.getStatus().name(),
				order.getTotal(),
				order.getReservedAmount(),
				order.getItems().stream().map(OrderItemResponse::from).toList(),
				order.getCreatedAt(),
				order.getUpdatedAt());
	}
}
