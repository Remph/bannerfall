package de.bannerfall.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class StaticResourcesTest {

    @Autowired MockMvc mockMvc;

    @Test
    void hashedAssetsAreCachedForGood() throws Exception {
        mockMvc.perform(get("/assets/cache-test.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
    }

    @Test
    void theWebAppPageIsCheckedAtEveryLoad() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void aMissingAssetIsNotCached() throws Exception {
        mockMvc.perform(get("/assets/missing-0123abcd.js"))
                .andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        "no-cache, no-store, max-age=0, must-revalidate"));
    }
}
