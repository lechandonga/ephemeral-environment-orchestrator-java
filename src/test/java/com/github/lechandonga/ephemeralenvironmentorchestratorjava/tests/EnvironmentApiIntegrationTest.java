package com.github.lechandonga.ephemeralenvironmentorchestratorjava.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务五：REST 接口端到端验证（含幂等、配额、归属、错误码区分）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("业务五：REST 接口端到端")
class EnvironmentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("用例5.1 申请返回201，重复申请幂等返回200且 idempotentHit=true")
    void applyThenIdempotentApply() throws Exception {
        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-a","envName":"e1","ttl":"PT10M"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotentHit").value(false))
                .andExpect(jsonPath("$.environment.state").value("READY"));

        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-a","envName":"e1","ttl":"PT10M"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentHit").value(true));
    }

    @Test
    @DisplayName("用例5.2 超配额返回409与QUOTA_EXCEEDED，错误原因可区分")
    void quotaExceededOverHttp() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/environments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"team\":\"api-quota\",\"envName\":\"e" + i
                                    + "\",\"ttl\":\"PT10M\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-quota","envName":"e4","ttl":"PT10M"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("用例5.3 跨团队续租返回403与TEAM_MISMATCH")
    void crossTeamRenewRejected() throws Exception {
        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-owner","envName":"sec","ttl":"PT10M"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/environments/sec/renew?team=api-intruder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extendBy":"PT5M"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEAM_MISMATCH"));
    }

    @Test
    @DisplayName("用例5.4 释放后查询返回410与ENV_RECLAIMED")
    void queryAfterReleaseGone() throws Exception {
        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-rel","envName":"tmp","ttl":"PT10M"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/environments/tmp/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"team":"api-rel"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECLAIMED"));

        mockMvc.perform(get("/api/environments/tmp").param("team", "api-rel"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ENV_RECLAIMED"));
    }
}
