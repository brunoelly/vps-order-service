package com.codechallenge.vps.order.api;

import java.math.BigDecimal;

import com.codechallenge.vps.order.domain.OrderItem;

public record OrderItemResponse(
		String sku,
		String productName,
		int quantity,
		BigDecimal unitPrice,
		BigDecimal lineTotal) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(
				item.getSku(),
				item.getProductName(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.lineTotal());
	}
}
