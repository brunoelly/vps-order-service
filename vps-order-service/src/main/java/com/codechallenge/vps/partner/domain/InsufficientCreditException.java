package com.codechallenge.vps.partner.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientCreditException extends RuntimeException {

	public InsufficientCreditException(UUID partnerId, BigDecimal required, BigDecimal available) {
		super("Partner " + partnerId + " has insufficient credit (required " + required + ", available " + available + ")");
	}
}
