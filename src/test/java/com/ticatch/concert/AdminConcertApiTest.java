package com.ticatch.concert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticatch.TestTokens;
import com.ticatch.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 공연 등록: 공연장, 공연, 회차와 좌석(docs/api.md 3·4절). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminConcertApiTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = TestTokens.bearer(jwtEncoder, 1L, "ADMIN");
    }

    @Test
    void 공연장을_등록하면_201이고_목록에서_볼_수_있다() throws Exception {
        long venueId = createVenue("올림픽홀");

        mockMvc.perform(get("/api/v1/admin/venues").param("size", "1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].venueId").value(venueId))
                .andExpect(jsonPath("$.items[0].name").value("올림픽홀"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void 목록의_size가_100을_넘으면_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/venues").param("size", "101").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void 공연을_등록하면_준비_중이고_제목과_설명을_수정할_수_있다() throws Exception {
        String body = postJson("/api/v1/admin/concerts", """
                {"title": "겨울 콘서트", "description": "첫 공연"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.createdAt", endsWith("+09:00")))
                .andReturn().getResponse().getContentAsString();
        int concertId = JsonPath.read(body, "$.concertId");

        mockMvc.perform(patch("/api/v1/admin/concerts/{id}", concertId)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "겨울 앙코르 콘서트", "description": "추가 공연"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("겨울 앙코르 콘서트"))
                .andExpect(jsonPath("$.description").value("추가 공연"));
    }

    @Test
    void 회차를_등록하면_등급별로_좌석이_만들어진다() throws Exception {
        long venueId = createVenue("올림픽홀");
        long concertId = createConcert();
        OffsetDateTime startsAt = OffsetDateTime.now(KST).plusDays(30).truncatedTo(ChronoUnit.SECONDS);

        String body = registerPerformance(concertId, venueId, startsAt, startsAt.minusDays(20), """
                [{"name": "VIP", "price": 150000, "seatCount": 3}, {"name": "R", "price": 120000, "seatCount": 2}]
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSeats").value(5))
                .andReturn().getResponse().getContentAsString();
        int performanceId = JsonPath.read(body, "$.performanceId");

        List<String> labels = jdbc.queryForList(
                "SELECT label FROM seat WHERE performance_id = ? AND status = 'AVAILABLE' ORDER BY id",
                String.class, performanceId);
        assertThat(labels).containsExactly("VIP-1", "VIP-2", "VIP-3", "R-1", "R-2");

        mockMvc.perform(get("/api/v1/admin/concerts/{id}", concertId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performances", hasSize(1)))
                .andExpect(jsonPath("$.performances[0].venueName").value("올림픽홀"))
                .andExpect(jsonPath("$.performances[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.performances[0].startsAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.performances[0].seatGrades[*].name", hasItems("VIP", "R")));
    }

    @Test
    void 회차_입력이_올바르지_않으면_400과_필드별_사유를_준다() throws Exception {
        long venueId = createVenue("올림픽홀");
        long concertId = createConcert();
        OffsetDateTime startsAt = OffsetDateTime.now(KST).plusDays(30);

        registerPerformance(concertId, venueId, startsAt, startsAt.plusHours(1), """
                [{"name": "VIP", "price": 150000, "seatCount": 3}, {"name": "VIP", "price": 100000, "seatCount": 2}]
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("bookingOpensBeforeStart", "seatGradeNamesUnique")));
    }

    @Test
    void 없는_공연이나_공연장이면_404다() throws Exception {
        long venueId = createVenue("올림픽홀");
        long concertId = createConcert();
        OffsetDateTime startsAt = OffsetDateTime.now(KST).plusDays(30);
        String grades = """
                [{"name": "VIP", "price": 150000, "seatCount": 1}]
                """;

        registerPerformance(999_999L, venueId, startsAt, startsAt.minusDays(1), grades)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONCERT_NOT_FOUND"));
        registerPerformance(concertId, 999_999L, startsAt, startsAt.minusDays(1), grades)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"));
    }

    @Test
    void 회차가_없는_공연은_공개할_수_없다() throws Exception {
        long concertId = createConcert();

        postJson("/api/v1/admin/concerts/" + concertId + "/open", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_HAS_NO_PERFORMANCE"));
    }

    @Test
    void 회차가_있으면_공개되고_이미_공개된_공연은_다시_공개할_수_없다() throws Exception {
        long concertId = createConcertWithPerformance();

        postJson("/api/v1/admin/concerts/" + concertId + "/open", "").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/concerts/{id}", concertId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("OPEN"));

        postJson("/api/v1/admin/concerts/" + concertId + "/open", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_INVALID_STATUS"));
    }

    @Test
    void 공개된_공연만_종료할_수_있고_종료된_공연에는_회차를_등록할_수_없다() throws Exception {
        long venueId = createVenue("올림픽홀");
        long concertId = createConcertWithPerformance();

        postJson("/api/v1/admin/concerts/" + concertId + "/end", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_INVALID_STATUS"));

        postJson("/api/v1/admin/concerts/" + concertId + "/open", "").andExpect(status().isNoContent());
        postJson("/api/v1/admin/concerts/" + concertId + "/end", "").andExpect(status().isNoContent());

        OffsetDateTime startsAt = OffsetDateTime.now(KST).plusDays(40);
        registerPerformance(concertId, venueId, startsAt, startsAt.minusDays(1), """
                [{"name": "VIP", "price": 150000, "seatCount": 1}]
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_INVALID_STATUS"));
    }

    @Test
    void 상태로_공연_목록을_거를_수_있다() throws Exception {
        createConcert();
        long openConcertId = createConcertWithPerformance();
        postJson("/api/v1/admin/concerts/" + openConcertId + "/open", "").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/concerts").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status", everyItem(org.hamcrest.Matchers.is("OPEN"))))
                .andExpect(jsonPath("$.items[*].concertId", hasItems((int) openConcertId)));
    }

    private long createVenue(String name) throws Exception {
        String body = postJson("/api/v1/admin/venues", """
                {"name": "%s", "address": "서울 송파구 올림픽로 424"}
                """.formatted(name))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.venueId")).longValue();
    }

    private long createConcert() throws Exception {
        String body = postJson("/api/v1/admin/concerts", """
                {"title": "겨울 콘서트", "description": "첫 공연"}
                """).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.concertId")).longValue();
    }

    private long createConcertWithPerformance() throws Exception {
        long venueId = createVenue("올림픽홀");
        long concertId = createConcert();
        OffsetDateTime startsAt = OffsetDateTime.now(KST).plusDays(30);
        registerPerformance(concertId, venueId, startsAt, startsAt.minusDays(20), """
                [{"name": "VIP", "price": 150000, "seatCount": 1}]
                """).andExpect(status().isCreated());
        return concertId;
    }

    private ResultActions registerPerformance(
            long concertId, long venueId, OffsetDateTime startsAt, OffsetDateTime bookingOpensAt, String seatGrades)
            throws Exception {
        return postJson("/api/v1/admin/concerts/" + concertId + "/performances", """
                {"venueId": %d, "startsAt": "%s", "bookingOpensAt": "%s", "seatGrades": %s}
                """.formatted(venueId, startsAt, bookingOpensAt, seatGrades));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
