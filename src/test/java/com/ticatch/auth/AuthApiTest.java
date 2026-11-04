package com.ticatch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticatch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 회원 가입, 로그인, 토큰 재발급, 로그아웃 흐름(docs/api.md 3절, ADR 0007). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTest {

    private static final String PASSWORD = "password1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 회원가입하면_201과_회원정보를_주고_포인트_지갑을_연다() throws Exception {
        signUp("new@ticatch.com")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("new@ticatch.com"))
                .andExpect(jsonPath("$.name").value("회원"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "new@ticatch.com");
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        Long balance = jdbc.queryForObject("""
                SELECT w.balance FROM point_wallet w JOIN users u ON u.id = w.user_id WHERE u.email = ?
                """, Long.class, "new@ticatch.com");
        assertThat(balance).isZero();
    }

    @Test
    void 같은_이메일로_다시_가입하면_409_EMAIL_ALREADY_USED() throws Exception {
        signUp("dup@ticatch.com").andExpect(status().isCreated());

        signUp("dup@ticatch.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void 가입_입력이_올바르지_않으면_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "name": "회원"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void 로그인하면_토큰을_주고_access_token으로_내_정보를_볼_수_있다() throws Exception {
        signUp("login@ticatch.com");

        String body = login("login@ticatch.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(1800))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@ticatch.com"));
    }

    @Test
    void 비밀번호가_틀리거나_가입하지_않은_이메일이면_같은_401_LOGIN_FAILED를_준다() throws Exception {
        signUp("wrong@ticatch.com");

        login("wrong@ticatch.com", "different-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login("nobody@ticatch.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 재발급하면_새_토큰을_주고_한_번_쓴_refresh_token은_다시_쓸_수_없다() throws Exception {
        signUp("refresh@ticatch.com");
        String first = refreshTokenOf(login("refresh@ticatch.com", PASSWORD));

        String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));
        assertThat(second).isNotEqualTo(first);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void 로그아웃한_refresh_token으로는_재발급할_수_없다() throws Exception {
        signUp("logout@ticatch.com");
        String body = login("logout@ticatch.com", PASSWORD).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(body, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "회원"}
                        """.formatted(email, PASSWORD)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken)));
    }

    private static String refreshJson(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private static String bearer(String tokenResponseBody) {
        return "Bearer " + JsonPath.read(tokenResponseBody, "$.accessToken");
    }
}
