package com.codechallenge.vps.partner.domain;

import java.util.UUID;

public class PartnerNotFoundException extends RuntimeException {

	public PartnerNotFoundException(UUID id) {
		super("Partner not found: " + id);
	}
}
