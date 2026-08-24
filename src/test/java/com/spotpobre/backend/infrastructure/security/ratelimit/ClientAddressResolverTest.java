package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ClientAddressResolverTest {

    private RateLimitProperties properties;
    private ClientAddressResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties(
                true, "secret", 20, Duration.ofHours(1), 5, Duration.ofHours(1),
                100, Duration.ofMinutes(15), 10, Duration.ofMinutes(15),
                20, Duration.ofMinutes(1), 40, Duration.ofHours(1),
                60, Duration.ofMinutes(1), 120, Duration.ofHours(1),
                120, Duration.ofMinutes(1), 1, Duration.ofMinutes(2),
                List.of("/api/v1/auth/register", "/api/v1/auth/authenticate"),
                "/api/v1/albums/{albumId}/songs",
                "/api/v1/albums/{albumId}/songs/{songId}/confirm",
                "/api/v1/songs/search",
                List.of("127.0.0.0/8", "10.0.0.0/8", "::1/128"),
                "X-Forwarded-For");
        resolver = new ClientAddressResolver(properties);
        request = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    void resolve_untrustedPeer_ignoresForwardedHeaderAndUsesRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertEquals("203.0.113.7", resolver.resolve(request),
                "an untrusted peer must not be able to spoof identities via XFF");
    }

    @Test
    void resolve_trustedProxy_usesFirstForwardedEntry() {
        when(request.getRemoteAddr()).thenReturn("10.1.2.3");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 10.1.2.4");

        assertEquals("198.51.100.9", resolver.resolve(request));
    }

    @Test
    void resolve_trustedProxyWithoutHeader_fallsBackToRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    @Test
    void resolve_forwardedHeaderIsParsedBeforeXff() {
        when(request.getRemoteAddr()).thenReturn("::1");
        when(request.getHeader("Forwarded")).thenReturn("for=192.0.2.77");
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.0.2.99");

        assertEquals("192.0.2.77", resolver.resolve(request));
    }

    @Test
    void isTrustedProxy_cidrMatchingWorksForV4() {
        assertTrue(resolver.isTrustedProxy("10.42.0.9"));
        assertFalse(resolver.isTrustedProxy("11.0.0.1"));
    }

    @Test
    void normalize_collapsesIPv4MappedIPv6Addresses() {
        assertEquals("192.0.2.5",
                ClientAddressResolver.normalize("::ffff:192.0.2.5"));
        assertEquals("192.0.2.5",
                ClientAddressResolver.normalize("::FFFF:192.0.2.5"));
    }

    @Test
    void matchesCidr_supportsIpv6Prefixes() {
        var loopback = address("0:0:0:0:0:0:0:1");
        assertTrue(ClientAddressResolver.matchesCidr(loopback, "::1/128"));
        var other = address("2001:db8::1");
        assertFalse(ClientAddressResolver.matchesCidr(other, "::1/128"));
        assertTrue(ClientAddressResolver.matchesCidr(address("2001:db8::1234"), "2001:db8::/32"));
    }

    private static java.net.InetAddress address(String literal) {
        try {
            return java.net.InetAddress.getByName(literal);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
