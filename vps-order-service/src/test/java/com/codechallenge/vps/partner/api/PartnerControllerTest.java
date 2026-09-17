package com.codechallenge.vps.partner.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codechallenge.vps.partner.application.PartnerService;
import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.shared.api.RestExceptionHandler;

@WebMvcTest(PartnerController.class)
@Import(RestExceptionHandler.class)
class PartnerControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PartnerService partners;

	@Test
	void createReturns201AndLocation() throws Exception {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		when(partners.create(eq("Acme"), eq(new BigDecimal("100.00")))).thenReturn(partner);

		mvc.perform(post("/api/v1/partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\",\"creditLimit\":100.00}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/partners/" + partner.getId()))
				.andExpect(jsonPath("$.id").value(partner.getId().toString()))
				.andExpect(jsonPath("$.name").value("Acme"))
				.andExpect(jsonPath("$.creditLimit").value(100.00))
				.andExpect(jsonPath("$.availableCredit").value(100.00));
	}

	@Test
	void createRejectsBlankName() throws Exception {
		mvc.perform(post("/api/v1/partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \",\"creditLimit\":10}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.errors[0]").value("name: must not be blank"));
	}

	@Test
	void createRejectsMissingCreditLimit() throws Exception {
		mvc.perform(post("/api/v1/partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\"}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void createRejectsNegativeCreditLimit() throws Exception {
		mvc.perform(post("/api/v1/partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\",\"creditLimit\":-1}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void getReturnsPartner() throws Exception {
		Partner partner = Partner.create("Acme", new BigDecimal("80.00"));
		when(partners.get(partner.getId())).thenReturn(partner);

		mvc.perform(get("/api/v1/partners/{id}", partner.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit").value(80.00));
	}

	@Test
	void changeCreditLimitReturnsUpdatedPartner() throws Exception {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.changeCreditLimit(new BigDecimal("250.00"));
		when(partners.changeCreditLimit(eq(partner.getId()), eq(new BigDecimal("250.00")))).thenReturn(partner);

		mvc.perform(patch("/api/v1/partners/{id}/credit-limit", partner.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"creditLimit\":250.00}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditLimit").value(250.00));
		verify(partners).changeCreditLimit(partner.getId(), new BigDecimal("250.00"));
	}

	@Test
	void changeCreditLimitRejectsNullBodyField() throws Exception {
		mvc.perform(patch("/api/v1/partners/{id}/credit-limit", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void getUnknownPartnerReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(partners.get(id)).thenThrow(new PartnerNotFoundException(id));

		mvc.perform(get("/api/v1/partners/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	void changeCreditLimitBelowReservedReturns422() throws Exception {
		UUID id = UUID.randomUUID();
		when(partners.changeCreditLimit(eq(id), eq(new BigDecimal("10.00"))))
				.thenThrow(new IllegalArgumentException("credit limit cannot be below already reserved credit"));

		mvc.perform(patch("/api/v1/partners/{id}/credit-limit", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"creditLimit\":10.00}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.detail").value("credit limit cannot be below already reserved credit"));
	}
}
