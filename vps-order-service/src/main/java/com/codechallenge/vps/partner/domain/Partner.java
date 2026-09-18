package com.codechallenge.vps.partner.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "partners")
public class Partner {

	private static final int SCALE = 2;

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(name = "credit_limit", nullable = false, precision = 19, scale = 2)
	private BigDecimal creditLimit;

	@Column(name = "available_credit", nullable = false, precision = 19, scale = 2)
	private BigDecimal availableCredit;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Partner() {
	}

	public static Partner create(String name, BigDecimal creditLimit) {
		return new Partner(UUID.randomUUID(), name, creditLimit, Instant.now());
	}

	private Partner(UUID id, String name, BigDecimal creditLimit, Instant now) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required");
		}
		this.id = id;
		this.name = name.trim();
		this.creditLimit = money(creditLimit);
		this.availableCredit = this.creditLimit;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void reserve(BigDecimal amount) {
		BigDecimal toHold = money(amount);
		if (toHold.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("reserve amount must be greater than 0");
		}
		if (availableCredit.compareTo(toHold) < 0) {
			throw new InsufficientCreditException(id, toHold, availableCredit);
		}
		availableCredit = availableCredit.subtract(toHold);
		touch();
	}

	public void release(BigDecimal amount) {
		BigDecimal toFree = money(amount);
		if (toFree.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("release amount must be greater than 0");
		}
		availableCredit = availableCredit.add(toFree);
		touch();
	}

	public void changeCreditLimit(BigDecimal newLimit) {
		BigDecimal next = money(newLimit);
		BigDecimal used = creditLimit.subtract(availableCredit);
		if (next.compareTo(used) < 0) {
			throw new IllegalArgumentException("credit limit cannot be below already reserved credit");
		}
		if (next.compareTo(creditLimit) > 0) {
			availableCredit = availableCredit.add(next.subtract(creditLimit));
		}
		creditLimit = next;
		if (availableCredit.compareTo(creditLimit) > 0) {
			availableCredit = creditLimit;
		}
		touch();
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	private static BigDecimal money(BigDecimal value) {
		if (value == null) {
			throw new IllegalArgumentException("amount is required");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("amount must be >= 0");
		}
		return value.setScale(SCALE, RoundingMode.HALF_UP);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getCreditLimit() {
		return creditLimit;
	}

	public BigDecimal getAvailableCredit() {
		return availableCredit;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
