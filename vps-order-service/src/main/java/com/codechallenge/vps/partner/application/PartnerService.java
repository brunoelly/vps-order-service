package com.codechallenge.vps.partner.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@Service
public class PartnerService {

	private final PartnerRepository partners;

	public PartnerService(PartnerRepository partners) {
		this.partners = partners;
	}

	@Transactional
	public Partner create(String name, BigDecimal creditLimit) {
		return partners.save(Partner.create(name, creditLimit));
	}

	@Transactional(readOnly = true)
	public Partner get(UUID id) {
		return partners.findById(id).orElseThrow(() -> new PartnerNotFoundException(id));
	}

	@Transactional
	public Partner changeCreditLimit(UUID id, BigDecimal creditLimit) {
		Partner partner = partners.findByIdForUpdate(id).orElseThrow(() -> new PartnerNotFoundException(id));
		partner.changeCreditLimit(creditLimit);
		return partners.save(partner);
	}
}
