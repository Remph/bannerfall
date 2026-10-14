package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The request id in the MDC: set while the request runs, gone afterwards (no Spring needed). */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void theIdIsInTheMdcDuringTheRequestAndRemovedAfterwards() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (request, res) -> duringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(duringRequest.get())
                .matches("[0-9a-f]{8}")
                .isEqualTo(response.getHeader(RequestIdFilter.HEADER));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theIdIsRemovedAlsoWhenTheRequestFails() {
        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        new MockHttpServletRequest(),
                                        new MockHttpServletResponse(),
                                        (request, response) -> {
                                            throw new ServletException("boom");
                                        }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
