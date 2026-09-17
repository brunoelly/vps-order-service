package com.codechallenge.vps;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@SpringBootTest
@ActiveProfiles("test")
class VpsApplicationTests {

	@MockitoBean
	PartnerRepository partners;

	@MockitoBean
	OrderRepository orders;

	@Test
	void contextLoads() {
	}

}
