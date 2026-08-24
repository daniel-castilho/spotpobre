package com.spotpobre.backend.infrastructure.security.ratelimit;

import com.spotpobre.backend.infrastructure.config.properties.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves the client address for rate-limit identity (spec section 8.2): forwarded headers
 * are believed ONLY when the direct peer is a configured trusted proxy (CIDR list). Untrusted
 * peers cannot spoof identities by setting X-Forwarded-For.
 *
 * <p>Order: {@code Forwarded: for=...} first, then the configured header's FIRST entry, then
 * the direct remote address.</p>
 */
@Component
@RequiredArgsConstructor
public class ClientAddressResolver {

    private final RateLimitProperties properties;

    public String resolve(final HttpServletRequest request) {
        if (isTrustedProxy(request.getRemoteAddr())) {
            String forwarded = request.getHeader("Forwarded");
            if (forwarded != null && !forwarded.isBlank()) {
                String candidate = extractForwardedFor(forwarded);
                if (candidate != null) {
                    return normalize(candidate);
                }
            }
            String headerValue = request.getHeader(properties.clientIpHeader());
            if (headerValue != null && !headerValue.isBlank()) {
                // Left-most entry is the original client; proxies append downstream hops.
                return normalize(headerValue.split(",")[0].trim());
            }
        }
        return normalize(request.getRemoteAddr());
    }

    boolean isTrustedProxy(final String remoteAddr) {
        try {
            InetAddress peer = InetAddress.getByName(remoteAddr.trim());
            for (String cidr : properties.trustedProxyCidrs()) {
                if (matchesCidr(peer, cidr)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String extractForwardedFor(final String forwardedHeader) {
        for (String part : forwardedHeader.split(",")) {
            String segment = part.trim();
            if (segment.toLowerCase(java.util.Locale.ROOT).startsWith("for=")) {
                String value = segment.substring(4).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    /** Zone-id stripped and IPv4-mapped IPv6 collapsed so equal addresses compare equal. */
    static String normalize(final String address) {
        try {
            InetAddress addr = InetAddress.getByName(address.trim());
            if (addr.getAddress().length == 16 && startsWithIPv4Mapped(addr.getAddress())) {
                return InetAddress.getByAddress(
                        java.util.Arrays.copyOfRange(addr.getAddress(), 12, 16))
                        .getHostAddress();
            }
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return address.trim();
        }
    }

    private static boolean startsWithIPv4Mapped(final byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    static boolean matchesCidr(final InetAddress address, final String cidr) {
        String[] parts = cidr.split("/");
        try {
            InetAddress network = InetAddress.getByName(parts[0].trim());
            byte[] networkBytes = network.getAddress();
            byte[] addressBytes = address.getAddress();
            if (networkBytes.length != addressBytes.length) {
                return false;
            }
            int prefixBits = parts.length == 2
                    ? Integer.parseInt(parts[1].trim())
                    : networkBytes.length * 8;
            int fullBytes = prefixBits / 8;
            int remainderBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != addressBytes[i]) {
                    return false;
                }
            }
            if (remainderBits > 0) {
                int mask = 0xFF << (8 - remainderBits);
                return (networkBytes[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}
