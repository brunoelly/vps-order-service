package com.codechallenge.vps.partner.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.codechallenge.vps.partner.domain.Partner;

public record PartnerResponse(
		UUID id,
		String name,
		BigDecimal creditLimit,
		BigDecimal availableCredit,
		Instant createdAt,
		Instant updatedAt) {

	public static PartnerResponse from(Partner partner) {
		return new PartnerResponse(
				partner.getId(),
				partner.getName(),
				partner.getCreditLimit(),
				partner.getAvailableCredit(),
				partner.getCreatedAt(),
				partner.getUpdatedAt());
	}
}
