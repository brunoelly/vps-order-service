package com.codechallenge.vps.order.domain;

public class IllegalOrderTransitionException extends RuntimeException {

	public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
		super("Cannot change order from " + from + " to " + to);
	}
}
