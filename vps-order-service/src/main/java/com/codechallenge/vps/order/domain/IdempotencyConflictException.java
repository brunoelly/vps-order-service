package com.codechallenge.vps.order.domain;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String idempotencyKey) {
		super("Idempotency key already used with a different payload: " + idempotencyKey);
	}
}
