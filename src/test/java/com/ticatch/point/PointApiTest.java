package com.ticatch.point;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticatch.TestTokens;
import com.ticatch.TestcontainersConfiguration;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 포인트 잔액·충전·거래 내역(docs/api.md 3·4절). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PointApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    UserService userService;

    private String token;

    @BeforeEach
    void setUp() {
        long userId = userService.signUp(new SignUpCommand("point@ticatch.com", "password1234", "회원")).id();
        token = TestTokens.bearer(jwtEncoder, userId, "USER");
    }

    @Test
    void 가입_직후_잔액은_0이다() throws Exception {
        mockMvc.perform(get("/api/v1/me/points").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void 충전하면_201과_충전_후_잔액을_주고_잔액과_거래_내역에_반영된다() throws Exception {
        charge(UUID.randomUUID().toString(), 50_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CHARGE"))
                .andExpect(jsonPath("$.amount").value(50_000))
                .andExpect(jsonPath("$.balanceAfter").value(50_000))
                .andExpect(jsonPath("$.createdAt", endsWith("+09:00")));

        mockMvc.perform(get("/api/v1/me/points").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.balance").value(50_000));
    }

    @Test
    void 같은_멱등성_키로_다시_보내면_한_번만_충전되고_처음과_같은_응답을_준다() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = charge(key, 30_000).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        charge(key, 30_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value((Integer) JsonPath.read(first, "$.transactionId")))
                .andExpect(jsonPath("$.balanceAfter").value(30_000));

        mockMvc.perform(get("/api/v1/me/points").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.balance").value(30_000));
    }

    @Test
    void 같은_멱등성_키로_다른_금액을_보내면_409_IDEMPOTENCY_KEY_CONFLICT다() throws Exception {
        String key = UUID.randomUUID().toString();
        charge(key, 30_000).andExpect(status().isCreated());

        charge(key, 40_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void 멱등성_키가_없으면_400_IDEMPOTENCY_KEY_REQUIRED다() throws Exception {
        mockMvc.perform(post("/api/v1/me/points/charges")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void 충전_금액이_범위를_벗어나면_400_INVALID_INPUT이다() throws Exception {
        charge(UUID.randomUUID().toString(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        charge(UUID.randomUUID().toString(), 1_000_001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 거래_내역은_최근_거래부터_준다() throws Exception {
        charge(UUID.randomUUID().toString(), 10_000).andExpect(status().isCreated());
        charge(UUID.randomUUID().toString(), 20_000).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/points/transactions").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].amount", contains(20_000, 10_000)))
                .andExpect(jsonPath("$.items[*].balanceAfter", contains(30_000, 10_000)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private ResultActions charge(String idempotencyKey, long amount) throws Exception {
        return mockMvc.perform(post("/api/v1/me/points/charges")
                .header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %d}
                        """.formatted(amount)));
    }
}
