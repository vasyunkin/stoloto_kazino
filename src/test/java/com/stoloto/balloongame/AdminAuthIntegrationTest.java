package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.domain.repository.AdminUserRepository;
import com.stoloto.balloongame.domain.repository.ConfigSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 6 A1–A3 smoke: bootstrap admin, JWT login/me, dual config auth, refresh, password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired ConfigSnapshotRepository configSnapshotRepository;

    @Test
    void bootstrap_createsAdminUser() {
        assertThat(adminUserRepository.findByUsername("admin")).isPresent();
    }

    @Test
    void login_ok_returnsTokens() throws Exception {
        JsonNode body = login("admin", "admin");
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("expiresIn").asLong()).isPositive();
        assertThat(body.path("admin").path("username").asText()).isEqualTo("admin");
    }

    @Test
    void login_badPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void me_withBearer_returnsProfile() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void config_withJwt_getAndPut_ok() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        mockMvc.perform(get("/api/admin/config")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.math.growthRate").exists())
                .andExpect(jsonPath("$.admin.apiKey").doesNotExist());

        mockMvc.perform(put("/api/admin/config")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"math\":{\"growthRate\":0.04}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.math.growthRate").value(0.04));
    }

    @Test
    void config_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void config_legacyKey_stillWorks() throws Exception {
        mockMvc.perform(get("/api/admin/config")
                        .header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.math.growthRate").exists());
    }

    @Test
    void putConfig_negativeGrowthRate_returns400() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        mockMvc.perform(put("/api/admin/config")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"math\":{\"growthRate\":-5}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIG_VALIDATION_FAILED"));
    }

    @Test
    void putConfig_jwt_setsAppliedByUsername() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        long before = configSnapshotRepository.count();
        mockMvc.perform(put("/api/admin/config")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"math\":{\"houseEdge\":0.07}}"))
                .andExpect(status().isOk());
        assertThat(configSnapshotRepository.count()).isEqualTo(before + 1);
        var latest = configSnapshotRepository.findAllByOrderByAppliedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 1)).get(0);
        assertThat(latest.getAppliedBy()).isEqualTo("admin");
    }

    @Test
    void refresh_rotates_andOldTokenRejected() throws Exception {
        JsonNode first = login("admin", "admin");
        String oldRefresh = first.get("refreshToken").asText();

        MvcResult rotated = mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode second = objectMapper.readTree(rotated.getResponse().getContentAsString());
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void logout_revokesRefresh() throws Exception {
        String refresh = login("admin", "admin").get("refreshToken").asText();
        mockMvc.perform(post("/api/admin/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_thenLoginWithNew_thenRestore() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        try {
            mockMvc.perform(post("/api/admin/auth/change-password")
                            .header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"admin\",\"newPassword\":\"admin2\"}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                    .andExpect(status().isUnauthorized());

            JsonNode relogin = login("admin", "admin2");
            mockMvc.perform(post("/api/admin/auth/change-password")
                            .header("Authorization", "Bearer " + relogin.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"admin2\",\"newPassword\":\"admin\"}"))
                    .andExpect(status().isNoContent());
            login("admin", "admin");
        } catch (Exception e) {
            try {
                JsonNode fallback = login("admin", "admin2");
                mockMvc.perform(post("/api/admin/auth/change-password")
                        .header("Authorization", "Bearer " + fallback.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"admin2\",\"newPassword\":\"admin\"}"));
            } catch (Exception ignored) {
                // bootstrap password may already be restored
            }
            throw e;
        }
    }

    @Test
    void configHistory_withJwt_ok() throws Exception {
        String access = login("admin", "admin").get("accessToken").asText();
        mockMvc.perform(put("/api/admin/config")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"math\":{\"houseEdge\":0.06}}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/config/history?limit=5")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appliedBy").exists())
                .andExpect(jsonPath("$[0].payloadPreview").exists());
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
