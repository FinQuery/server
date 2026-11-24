package com.finquery.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 경량화된 Actuator 헬스체크 엔드포인트의 정상 동작과 민감 엔드포인트 비노출을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("GET /actuator/health는 세부 정보 없이 UP 상태를 반환한다")
	void healthEndpointReturnsUpWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	@DisplayName("GET /actuator/health/liveness는 UP 상태를 반환한다")
	void livenessProbeReturnsUp() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("GET /actuator/health/readiness는 UP 상태를 반환한다")
	void readinessProbeReturnsUp() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("노출이 제한된 Actuator 민감 엔드포인트(/actuator/env, /actuator/beans)는 404를 반환한다")
	void unexposedActuatorEndpointsReturnNotFound() throws Exception {
		mockMvc.perform(get("/actuator/env"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/actuator/beans"))
			.andExpect(status().isNotFound());
	}
}
