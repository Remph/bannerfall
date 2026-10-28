package de.bannerfall.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientNetworkTest {

    @Test
    void anIpv4AddressIsItsOwnClient() {
        assertThat(ClientNetwork.of("198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void anIpv6AddressCountsByItsFirst64Bits() {
        String network = ClientNetwork.of("2001:db8:1:2:3:4:5:6");

        assertThat(network).isEqualTo("20010db800010002::/64");
        assertThat(ClientNetwork.of("2001:0db8:0001:0002:ffff:ffff:ffff:ffff")).isEqualTo(network);
        assertThat(ClientNetwork.of("2001:db8:1:2::")).isEqualTo(network);
        assertThat(ClientNetwork.of("2001:db8:1:3::1")).isNotEqualTo(network);
    }

    @Test
    void anIpv4MappedAddressIsTheIpv4Client() {
        assertThat(ClientNetwork.of("::ffff:198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void whatIsNotAnAddressComesBackUnchanged() {
        assertThat(ClientNetwork.of(null)).isNull();
        assertThat(ClientNetwork.of("unknown")).isEqualTo("unknown");
        assertThat(ClientNetwork.of("not:an:address:at:all:xyz"))
                .isEqualTo("not:an:address:at:all:xyz");
    }
}
