package de.bannerfall.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

class AppVersionTest {

    @Test
    void usesTheVersionOfTheBuild() {
        Properties entries = new Properties();
        entries.setProperty("version", "1.1.0");
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("buildProperties", new BuildProperties(entries));

        assertThat(new AppVersion(beans.getBeanProvider(BuildProperties.class)).get())
                .isEqualTo("1.1.0");
    }

    @Test
    void isUnknownWithoutBuildInfo() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();

        assertThat(new AppVersion(beans.getBeanProvider(BuildProperties.class)).get())
                .isEqualTo("unknown");
    }
}
