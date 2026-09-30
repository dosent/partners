package com.simbirsoft.partners.shared.api;

import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@WebMvcTest(controllers = ErrorContractWebMvcTest.FixtureController.class)
@Import(ErrorContractWebMvcTest.FixtureController.class)
class ErrorContractWebMvcTest {
    @Autowired
    MockMvc mvc;

    @Test
    void validationErrorHasFieldDetailsAndServerTraceId() throws Exception {
        mvc.perform(post("/api/v1/error-fixture").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}").header("X-Trace-Id", "client-supplied"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.not("client-supplied")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedJsonUsesErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/error-fixture").contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void unauthorizedUsesErrorEnvelope() throws Exception {
        assertError("unauthorized", 401, "UNAUTHORIZED");
    }

    @Test
    void forbiddenUsesErrorEnvelope() throws Exception {
        assertError("forbidden", 403, "FORBIDDEN");
    }

    @Test
    void missingResourceUsesErrorEnvelope() throws Exception {
        assertError("missing", 404, "NOT_FOUND");
    }

    @Test
    void unknownApiRouteUsesErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void conflictUsesErrorEnvelope() throws Exception {
        assertError("conflict", 409, "CONCURRENT_MODIFICATION");
    }

    @Test
    void invalidPathVariableUsesBadRequestEnvelope() throws Exception {
        mvc.perform(get("/api/v1/error-fixture/typed/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void traceIdMatchesHeaderChangesPerRequestAndLeavesNoMdcValue() throws Exception {
        MvcResult first = mvc.perform(get("/api/v1/error-fixture/conflict"))
                .andExpect(status().isConflict()).andReturn();
        String firstTrace = first.getResponse().getHeader("X-Trace-Id");
        assertEquals(firstTrace, JsonPath.read(first.getResponse().getContentAsString(), "$.traceId"));
        assertNull(MDC.get("traceId"));

        MvcResult second = mvc.perform(get("/api/v1/error-fixture/conflict"))
                .andExpect(status().isConflict()).andReturn();
        String secondTrace = second.getResponse().getHeader("X-Trace-Id");
        assertEquals(secondTrace, JsonPath.read(second.getResponse().getContentAsString(), "$.traceId"));
        assertNotEquals(firstTrace, secondTrace);
        assertNull(MDC.get("traceId"));
    }

    private void assertError(String path, int expectedStatus, String expectedCode) throws Exception {
        mvc.perform(get("/api/v1/error-fixture/" + path))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @RestController
    @RequestMapping("/api/v1/error-fixture")
    static class FixtureController {
        @PostMapping
        void create(@Valid @RequestBody FixtureRequest request) {
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ResponseStatusException(FORBIDDEN, "Access denied");
        }

        @GetMapping("/missing")
        void missing() {
            throw new ResponseStatusException(NOT_FOUND, "Resource not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(CONFLICT, "Concurrent modification");
        }

        @GetMapping("/typed/{id}")
        void typed(@PathVariable long id) {
        }
    }

    record FixtureRequest(@NotBlank String name) {
    }
}
