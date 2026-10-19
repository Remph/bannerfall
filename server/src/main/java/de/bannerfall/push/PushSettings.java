package de.bannerfall.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How this world sends Web Push (1.5): its VAPID keys, the contact it gives the push services, and
 * the push services it talks to at all. Without keys push is off: the web hides its menu row.
 *
 * <p>The services are a list of origins ({@code https://fcm.googleapis.com}, a leading {@code *.}
 * for any subdomain): a subscription's endpoint is chosen by the browser, so without this list a
 * made-up endpoint could make the server call any address, the private network included.
 */
@Component
public class PushSettings {

    private final VapidKeys keys;
    private final String subject;
    private final List<Service> services;

    /** One entry of the list: {@code https://*.push.apple.com} or {@code http://localhost:8099}. */
    record Service(String scheme, String host, int port) {

        static Service parse(String origin) {
            String text = origin.strip().toLowerCase(Locale.ROOT);
            int separator = text.indexOf("://");
            if (separator < 1 || text.indexOf('/', separator + 3) >= 0) {
                throw new IllegalStateException("Not a push service origin: " + origin);
            }
            String scheme = text.substring(0, separator);
            String authority = text.substring(separator + 3);
            int colon = authority.lastIndexOf(':');
            if (colon < 0) {
                return new Service(scheme, authority, defaultPort(scheme));
            }
            return new Service(
                    scheme,
                    authority.substring(0, colon),
                    Integer.parseInt(authority.substring(colon + 1)));
        }

        boolean matches(String scheme, String host, int port) {
            if (!this.scheme.equals(scheme) || this.port != port) {
                return false;
            }
            if (this.host.startsWith("*.")) {
                return host.endsWith(this.host.substring(1));
            }
            return this.host.equals(host);
        }
    }

    PushSettings(
            @Value("${bannerfall.push.public-key:}") String publicKey,
            @Value("${bannerfall.push.private-key:}") String privateKey,
            @Value("${bannerfall.push.subject:}") String subject,
            @Value("${bannerfall.operator.contact:}") String operatorContact,
            @Value("${bannerfall.push.services}") List<String> services) {
        if (publicKey.isBlank() != privateKey.isBlank()) {
            throw new IllegalStateException(
                    "Set both BANNERFALL_PUSH_PUBLIC_KEY and BANNERFALL_PUSH_PRIVATE_KEY, or"
                            + " neither (bannerfall-admin push keys prints a pair)");
        }
        this.keys = publicKey.isBlank() ? null : VapidKeys.parse(publicKey, privateKey);
        this.subject = subject.isBlank() ? subjectFrom(operatorContact.strip()) : subject.strip();
        this.services = services.stream().map(Service::parse).toList();
    }

    /** Whether this world has keys, i.e. sends push at all. */
    public boolean enabled() {
        return keys != null;
    }

    /** The keys; only when {@link #enabled()}. */
    VapidKeys keys() {
        if (keys == null) {
            throw new IllegalStateException("Push is off on this world");
        }
        return keys;
    }

    /** The public key for the browser, null when push is off. */
    String publicKey() {
        return keys == null ? null : keys.publicKeyText();
    }

    /** {@code mailto:} or https, for the push services' operators; null if the world has none. */
    String subject() {
        return subject;
    }

    /**
     * Whether {@code endpoint} belongs to one of the push services: same scheme, the host or a
     * subdomain of a {@code *.} entry, the same port, and no user name in it.
     */
    boolean allowed(URI endpoint) {
        if (endpoint.getScheme() == null
                || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null) {
            return false;
        }
        String scheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
        String host = endpoint.getHost().toLowerCase(Locale.ROOT);
        int port = endpoint.getPort() == -1 ? defaultPort(scheme) : endpoint.getPort();
        return services.stream().anyMatch(service -> service.matches(scheme, host, port));
    }

    private static int defaultPort(String scheme) {
        return "https".equals(scheme) ? 443 : 80;
    }

    /** The operator's contact as a VAPID subject: an e-mail address or an https address. */
    static String subjectFrom(String contact) {
        if (contact.startsWith("https://") && !contact.contains(" ")) {
            return contact;
        }
        if (contact.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")) {
            return "mailto:" + contact;
        }
        return null;
    }
}
