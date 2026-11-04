package com.ticatch.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 에러 응답이 API 설계(docs/api.md 2절)의 계약을 지키는지 확인한다.
 * 상황별로 예외를 일으키는 테스트 전용 컨트롤러를 쓴다.
 * 스프링 MVC의 예외 처리만 보는 테스트라 보안 필터는 끈다. 401·403은 SecurityTest가 확인한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ErrorTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 업무_예외는_에러_코드의_상태와_code로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("테스트용 충돌입니다."))
                .andExpect(jsonPath("$.instance").value("/test/business-error"))
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"));
    }

    @Test
    void 입력_검증에_실패하면_400_INVALID_INPUT과_필드별_사유를_준다() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "count": 5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].reason").value("이름은 비어 있을 수 없습니다."))
                .andExpect(jsonPath("$.errors[?(@.field == 'count')].reason").value("4 이하로 입력해야 합니다."));
    }

    @Test
    void 읽을_수_없는_JSON은_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_경로는_404_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("요청한 주소를 찾을 수 없습니다."));
    }

    @Test
    void 지원하지_않는_메서드는_405_METHOD_NOT_ALLOWED와_허용_메서드를_준다() throws Exception {
        mockMvc.perform(delete("/test/business-error"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 지원하지_않는_Content_Type은_415_UNSUPPORTED_MEDIA_TYPE이다() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 예상하지_못한_예외는_500_INTERNAL_ERROR이고_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(content().string(not(containsString("내부 비밀 정보"))));
    }

    enum TestErrorCode implements ErrorCode {
        TEST_CONFLICT(HttpStatus.CONFLICT, "테스트용 충돌입니다.");

        private final HttpStatus status;
        private final String message;

        TestErrorCode(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public String message() {
            return message;
        }
    }

    record TestRequest(
            @NotBlank(message = "이름은 비어 있을 수 없습니다.") String name,
            @Min(value = 1, message = "1 이상으로 입력해야 합니다.")
            @Max(value = 4, message = "4 이하로 입력해야 합니다.") int count) {
    }

    @RestController
    static class ErrorTestController {

        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException(TestErrorCode.TEST_CONFLICT);
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("내부 비밀 정보");
        }
    }
}
