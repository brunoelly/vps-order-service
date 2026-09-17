package com.codechallenge.vps.shared.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.outbox.OutboxWriter;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PartnerRepository partners;

	@MockitoBean
	OrderRepository orders;

	@MockitoBean
	OutboxWriter outbox;

	@Test
	void apiDocsExposePartnerAndOrderOperations() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("vps-order-service"))
				.andExpect(jsonPath("$.tags[*].name", hasItems("Partners", "Orders")))
				.andExpect(jsonPath("$.paths['/api/v1/partners']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/orders']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/orders'].post.parameters[?(@.name=='Idempotency-Key')].required", hasItem(true)));
	}

	@Test
	void swaggerUiRedirectsToIndex() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}

	@Test
	void swaggerUiIndexIsServed() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void unknownApiDocsGroupIsNotFound() throws Exception {
		mvc.perform(get("/v3/api-docs/does-not-exist"))
				.andExpect(status().isNotFound());
	}
}
