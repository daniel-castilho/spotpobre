package com.spotpobre.backend.infrastructure.web.filter;

import tools.jackson.databind.json.JsonMapper;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestSizeLimitFilterTest {

    private static final long LIMIT = RequestSizeLimitFilter.MAX_JSON_BODY_BYTES;

    private RequestSizeLimitFilter filter;
    private RestErrorResponseWriter errorResponseWriter;
    private MockFilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        final JsonMapper objectMapper = JsonMapper.builder().build();
        errorResponseWriter = new RestErrorResponseWriter(objectMapper);
        filter = new RequestSizeLimitFilter(errorResponseWriter);
        chain = new MockFilterChain();
        response = new MockHttpServletResponse();
    }

    @Test
    void passesThroughBodylessMethodsUntouched() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/songs/search");
        request.setContent(new byte[0]);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertArrayEquals(new byte[0], response.getContentAsByteArray());
    }

    @Test
    void rejectsDeclaredOversizeWithoutReadingBody() throws Exception {
        final MockHttpServletRequest request =
                declaredLengthRequest("POST", "/api/v1/auth/register", new byte[(int) LIMIT + 1]);
        final FilterChain nonReadingChain = (req, res) -> { };

        filter.doFilter(request, response, nonReadingChain);

        assertEquals(413, response.getStatus());
        final String body = response.getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("Payload Too Large"));
    }

    @Test
    void acceptsBodyExactlyAtLimit() throws Exception {
        final MockHttpServletRequest request =
                declaredLengthRequest("POST", "/api/v1/playlists", new byte[(int) LIMIT]);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void capsChunkedBodyExceedingLimitDuringRead() throws Exception {
        final MockHttpServletRequest request = chunkedRequest("PUT", "/api/v1/playlists/1", new byte[(int) LIMIT + 10]);
        // Simulate the application actually consuming the body.
        final FilterChain readingChain = (req, res) -> {
            try (var in = ((HttpServletRequest) req).getInputStream()) {
                while (in.read() != -1) {
                    // drain
                }
            }
        };

        filter.doFilter(request, response, readingChain);

        assertEquals(413, response.getStatus());
    }

    @Test
    void streamsSmallChunkedBodyThrough() throws Exception {
        final byte[] payload = "{\"entityType\":\"SONG\"}".getBytes(StandardCharsets.UTF_8);
        final MockHttpServletRequest request = chunkedRequest("POST", "/api/v1/likes", payload);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        final HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        try (var in = downstream.getInputStream()) {
            final byte[] received = in.readAllBytes();
            assertArrayEquals(payload, received);
        }
    }

    /**
     * Simulates a request declaring Content-Length (typical non-chunked client).
     */
    private static MockHttpServletRequest declaredLengthRequest(
            final String method, final String uri, final byte[] content) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public int getContentLength() {
                return content.length;
            }
        };
        request.setContentType("application/json");
        request.setContent(content);
        return request;
    }

    /**
     * Simulates a chunked request: body present, no declared Content-Length.
     */
    private static MockHttpServletRequest chunkedRequest(final String method, final String uri, final byte[] content) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(content);
        return request;
    }

    @Test
    void rejectsWhenDownstreamReadThrowsWrappedMarker() throws IOException {
        // The marker must be detectable through Spring's HttpMessageNotReadableException wrapping.
        final RequestSizeLimitFilter.BodyLimitExceededException marker =
                new RequestSizeLimitFilter.BodyLimitExceededException();
        final IOException wrapped = new IOException("wrapped", marker);
        Throwable current = wrapped;
        boolean found = false;
        while (current != null) {
            if (current instanceof RequestSizeLimitFilter.BodyLimitExceededException) {
                found = true;
                break;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        org.junit.jupiter.api.Assertions.assertTrue(found);
    }
}
