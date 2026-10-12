package com.stoloto.balloongame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TD-12 / TD-14 — player registration, JWT identity, server puzzle album.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PlayerAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void register_createsWallet_loginReturnsSamePlayer_andRejectsForeignHeader() throws Exception {
        JsonNode registered = register("Pilot@Example.com", "long-enough");
        String playerId = registered.get("playerId").asText();
        String access = registered.get("accessToken").asText();
        assertThat(registered.get("email").asText()).isEqualTo("pilot@example.com");

        mockMvc.perform(get("/api/players/" + playerId + "/balance")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(org.hamcrest.Matchers.greaterThan(0.0)));

        JsonNode loggedIn = login("pilot@example.com", "long-enough");
        assertThat(loggedIn.get("playerId").asText()).isEqualTo(playerId);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pilot@example.com\",\"password\":\"long-enough\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_TAKEN"));

        mockMvc.perform(post("/api/game/start")
                        .header("Authorization", "Bearer " + access)
                        .header("X-Player-Id", "someone-else")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s","betAmount":12,"balloonType":"STANDARD","boosterPreference":"NONE"}
                                """.formatted(playerId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/game/start")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s","betAmount":12,"balloonType":"STANDARD","boosterPreference":"NONE"}
                                """.formatted(playerId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/game/start")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s","betAmount":12,"balloonType":"STANDARD","boosterPreference":"NONE"}
                                """.formatted(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").exists());

        mockMvc.perform(get("/api/admin/config")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    @Test
    void albumSync_isIdempotent_andRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/album"))
                .andExpect(status().isUnauthorized());

        String access = register("album@example.com", "long-enough").get("accessToken").asText();
        MvcResult first = mockMvc.perform(post("/api/album/sync")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pieces\":[{\"slot\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slot").value(3))
                .andReturn();
        String earnedAt = objectMapper.readTree(first.getResponse().getContentAsString())
                .get(0).get("earnedAt").asText();

        mockMvc.perform(post("/api/album/sync")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pieces\":[{\"slot\":3},{\"slot\":4}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].earnedAt").value(earnedAt));
    }

    private JsonNode register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
