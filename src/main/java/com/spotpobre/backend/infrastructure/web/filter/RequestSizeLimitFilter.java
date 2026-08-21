package com.spotpobre.backend.infrastructure.web.filter;

import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces the 64 KiB API JSON body limit at the outermost boundary.
 *
 * <p>Requests declaring {@code Content-Length} above the limit are rejected immediately
 * without reading any body bytes. Chunked bodies are capped by a counting stream; the
 * excess never reaches application code and is never logged.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    public static final long MAX_JSON_BODY_BYTES = 64L * 1024;

    private static final Set<String> BODY_METHODS =
            Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name());

    private final RestErrorResponseWriter errorResponseWriter;

    public RequestSizeLimitFilter(final RestErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        if (!BODY_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        final long declaredLength = parseContentLength(request);
        if (declaredLength > MAX_JSON_BODY_BYTES) {
            reject(request, response);
            return;
        }

        try {
            filterChain.doFilter(new SizeLimitedRequest(request), response);
        } catch (BodyLimitExceededException ex) {
            logger.warn("Rejected {} {} : body exceeded {} byte limit",
                    request.getMethod(), request.getRequestURI(), MAX_JSON_BODY_BYTES);
            reject(request, response);
        }
    }

    private long parseContentLength(final HttpServletRequest request) {
        return request.getContentLengthLong();
    }

    private void reject(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Payload Too Large",
                "Request body exceeds the maximum allowed size of 64 KB."
        );
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private SizeLimitedRequest(final HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedServletInputStream(super.getInputStream());
        }
    }

    /**
     * Marker signalling the streaming limit was hit; carries no body content.
     * Public so the exception layer can detect it through wrapped I/O errors.
     */
    public static final class BodyLimitExceededException extends IOException {

        BodyLimitExceededException() {
            super("body exceeds size limit");
        }
    }

    private static final class SizeLimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long remaining = MAX_JSON_BODY_BYTES + 1;

        private SizeLimitedServletInputStream(final ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            final int value = delegate.read();
            if (value != -1 && --remaining < 0) {
                throw new BodyLimitExceededException();
            }
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                remaining -= count;
                if (remaining < 0) {
                    throw new BodyLimitExceededException();
                }
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
