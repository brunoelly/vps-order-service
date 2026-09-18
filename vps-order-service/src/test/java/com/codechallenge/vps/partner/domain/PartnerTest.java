package com.codechallenge.vps.partner.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PartnerTest {

	@Test
	void newPartnerStartsWithFullCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));

		assertEquals(new BigDecimal("100.00"), partner.getCreditLimit());
		assertEquals(new BigDecimal("100.00"), partner.getAvailableCredit());
	}

	@Test
	void reserveReducesAvailableCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));

		partner.reserve(new BigDecimal("40.50"));

		assertEquals(new BigDecimal("59.50"), partner.getAvailableCredit());
	}

	@Test
	void reserveFailsWhenCreditIsShort() {
		Partner partner = Partner.create("Acme", new BigDecimal("10.00"));

		assertThrows(InsufficientCreditException.class,
				() -> partner.reserve(new BigDecimal("10.01")));
		assertEquals(new BigDecimal("10.00"), partner.getAvailableCredit());
	}

	@Test
	void reserveExactAvailableLeavesZero() {
		Partner partner = Partner.create("Acme", new BigDecimal("25.00"));
		partner.reserve(new BigDecimal("25.00"));
		assertEquals(new BigDecimal("0.00"), partner.getAvailableCredit());
	}

	@Test
	void reserveRejectsZeroAndNull() {
		Partner partner = Partner.create("Acme", new BigDecimal("10.00"));
		assertThrows(IllegalArgumentException.class, () -> partner.reserve(BigDecimal.ZERO));
		assertThrows(IllegalArgumentException.class, () -> partner.reserve(null));
	}

	@Test
	void createRejectsBlankName() {
		assertThrows(IllegalArgumentException.class, () -> Partner.create("  ", new BigDecimal("10.00")));
	}

	@Test
	void releaseRestoresReservedAmount() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("30.00"));

		partner.release(new BigDecimal("30.00"));

		assertEquals(new BigDecimal("100.00"), partner.getAvailableCredit());
	}

	@Test
	void releaseRejectsZero() {
		Partner partner = Partner.create("Acme", new BigDecimal("10.00"));
		assertThrows(IllegalArgumentException.class, () -> partner.release(BigDecimal.ZERO));
	}

	@Test
	void raisingLimitAddsTheDeltaToAvailableCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("60.00"));

		partner.changeCreditLimit(new BigDecimal("130.00"));

		assertEquals(new BigDecimal("130.00"), partner.getCreditLimit());
		assertEquals(new BigDecimal("70.00"), partner.getAvailableCredit());
	}

	@Test
	void loweringLimitClampsAvailableCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));

		partner.changeCreditLimit(new BigDecimal("40.00"));

		assertEquals(new BigDecimal("40.00"), partner.getCreditLimit());
		assertEquals(new BigDecimal("40.00"), partner.getAvailableCredit());
	}

	@Test
	void cannotLowerLimitBelowReservedCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("70.00"));

		assertThrows(IllegalArgumentException.class,
				() -> partner.changeCreditLimit(new BigDecimal("60.00")));
	}

	@Test
	void missingPartnerMessage() {
		UUID id = UUID.randomUUID();
		assertEquals("Partner not found: " + id, new PartnerNotFoundException(id).getMessage());
	}
}
