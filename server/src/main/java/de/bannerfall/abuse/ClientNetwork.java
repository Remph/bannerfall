package de.bannerfall.abuse;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * What the address limits count as one client (security review of 1.7). An IPv4 address is one
 * client. An IPv6 address counts by its /64 network, because whoever has an IPv6 connection has the
 * whole /64 (a home connection gets at least that): per full address a limit would be no limit at
 * all, since every request could come from a new one. The account server counts the same way.
 */
public final class ClientNetwork {

    private ClientNetwork() {}

    /**
     * The key to count a client's requests under: the IPv4 address, or the IPv6 address's first 64
     * bits. Anything that is not an address literal comes back unchanged (no name lookup).
     */
    public static String of(String address) {
        if (address == null || address.indexOf(':') < 0) {
            return address;
        }
        try {
            // a literal with a colon: parsed, never resolved
            InetAddress parsed = InetAddress.getByName(address);
            byte[] bytes = parsed.getAddress();
            if (bytes.length == 16) {
                return HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
            }
            // ::ffff:192.0.2.1 is the IPv4 client 192.0.2.1
            return parsed.getHostAddress();
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
