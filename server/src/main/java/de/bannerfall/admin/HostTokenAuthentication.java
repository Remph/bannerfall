package de.bannerfall.admin;

import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A request that carried the right host token (08-host-console §4): ADMIN for this one request, no
 * session. The principal is the audit actor {@value AdminActor#HOST_CLI}.
 */
public final class HostTokenAuthentication extends AbstractAuthenticationToken {

    @Serial private static final long serialVersionUID = 1L;

    HostTokenAuthentication() {
        super(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return AdminActor.HOST_CLI;
    }
}
