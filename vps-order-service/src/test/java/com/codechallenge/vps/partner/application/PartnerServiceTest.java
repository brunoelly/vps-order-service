package com.codechallenge.vps.partner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

	@Mock
	PartnerRepository partners;

	PartnerService service;

	@BeforeEach
	void setUp() {
		service = new PartnerService(partners);
		lenient().when(partners.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void createPersistsNewPartner() {
		Partner saved = service.create("Acme", new BigDecimal("100.00"));

		assertEquals("Acme", saved.getName());
		assertEquals(new BigDecimal("100.00"), saved.getAvailableCredit());
		verify(partners).save(saved);
	}

	@Test
	void getReturnsPartner() {
		Partner partner = Partner.create("Acme", new BigDecimal("50.00"));
		when(partners.findById(partner.getId())).thenReturn(Optional.of(partner));

		assertEquals(partner, service.get(partner.getId()));
	}

	@Test
	void getUnknownPartner() {
		UUID id = UUID.randomUUID();
		when(partners.findById(id)).thenReturn(Optional.empty());

		assertThrows(PartnerNotFoundException.class, () -> service.get(id));
	}

	@Test
	void changeCreditLimitLocksRow() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("40.00"));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));

		Partner updated = service.changeCreditLimit(partner.getId(), new BigDecimal("200.00"));

		assertEquals(new BigDecimal("200.00"), updated.getCreditLimit());
		assertEquals(new BigDecimal("160.00"), updated.getAvailableCredit());
	}

	@Test
	void changeCreditLimitUnknownPartner() {
		UUID id = UUID.randomUUID();
		when(partners.findByIdForUpdate(id)).thenReturn(Optional.empty());

		assertThrows(PartnerNotFoundException.class,
				() -> service.changeCreditLimit(id, new BigDecimal("10.00")));
		verify(partners, never()).save(any());
	}

	@Test
	void changeCreditLimitRejectsBelowReserved() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("40.00"));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));

		assertThrows(IllegalArgumentException.class,
				() -> service.changeCreditLimit(partner.getId(), new BigDecimal("30.00")));
	}
}
