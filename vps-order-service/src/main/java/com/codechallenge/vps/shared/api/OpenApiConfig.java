package com.codechallenge.vps.shared.api;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("vps-order-service")
						.version("0.0.1")
						.description("B2B order management"))
				.tags(List.of(
						new Tag().name("Partners").description("Partner accounts and credit limit"),
						new Tag().name("Orders").description("Place, search, status, and cancel")));
	}
}
