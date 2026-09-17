package com.codechallenge.vps.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

	private static final int SCALE = 2;

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false, length = 64)
	private String sku;

	@Column(name = "product_name", nullable = false, length = 255)
	private String productName;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
	private BigDecimal unitPrice;

	protected OrderItem() {
	}

	public OrderItem(String sku, String productName, int quantity, BigDecimal unitPrice) {
		this(UUID.randomUUID(), sku, productName, quantity, unitPrice);
	}

	OrderItem(UUID id, String sku, String productName, int quantity, BigDecimal unitPrice) {
		if (sku == null || sku.isBlank()) {
			throw new IllegalArgumentException("sku is required");
		}
		if (productName == null || productName.isBlank()) {
			throw new IllegalArgumentException("productName is required");
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be greater than 0");
		}
		if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("unitPrice must be >= 0");
		}
		this.id = id;
		this.sku = sku.trim();
		this.productName = productName.trim();
		this.quantity = quantity;
		this.unitPrice = unitPrice.setScale(SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal lineTotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
	}

	public UUID getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getProductName() {
		return productName;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}
}
