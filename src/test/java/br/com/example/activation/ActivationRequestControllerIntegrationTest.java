package br.com.example.activation;

import br.com.example.activation.integration.*;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class ActivationRequestControllerIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ActivationRequestRepository repo;
    @Autowired
    JwtEncoder encoder;
    @MockBean
    ProviderActivationClient provider;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        reset(provider);
    }

    String token(String scope) {
        var c = JwtClaimsSet.builder().issuer("https://identity.example.com").audience(List.of("activation-service")).issuedAt(Instant.now().minusSeconds(2)).expiresAt(Instant.now().plusSeconds(300)).subject("client").claim("scope", scope).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), c)).getTokenValue();
    }

    String body = "{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"NET-1\"}";

    @Test
    void noToken401() throws Exception {
        mvc.perform(get("/api/v1/requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongScope403() throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:read")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    @Test
    void createsAndCompletes() throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:write")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(provider).activate("EXT-1", "CUST-1", "NET-1");
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:write")).contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"\",\"customerId\":\"CUST-1\",\"productCode\":\"NET-1\"}")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:write")).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void duplicateRetryDoesNotCallProviderAgain() throws Exception {
        String auth = "Bearer " + token("requests:write");
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/v1/requests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        verify(provider, times(1)).activate(anyString(), anyString(), anyString());
        org.assertj.core.api.Assertions.assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void duplicateExternalIdWithDifferentContentReturnsConflict() throws Exception {
        String auth = "Bearer " + token("requests:write");
        mvc.perform(post("/api/v1/requests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/requests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"OTHER\"}"))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(provider, times(1)).activate(anyString(), anyString(), anyString());
    }

    @Test
    void getsExistingRequest() throws Exception {
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:write")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        var request = repo.findByExternalId("EXT-1").orElseThrow();
        mvc.perform(get("/api/v1/requests/" + request.getId()).header("Authorization", "Bearer " + token("requests:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(request.getId().toString())).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void missingRequestReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/requests/00000000-0000-0000-0000-000000000001").header("Authorization", "Bearer " + token("requests:read")))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void providerErrorMarksFailed() throws Exception {
        doThrow(new ProviderActivationException("failed", true)).when(provider).activate(anyString(), anyString(), anyString());
        mvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token("requests:write")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void swaggerDocsArePublicAndDescribeBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.security[0].bearerAuth").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
