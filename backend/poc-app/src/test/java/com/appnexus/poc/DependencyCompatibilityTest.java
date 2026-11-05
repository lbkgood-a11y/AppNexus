package com.appnexus.poc;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class DependencyCompatibilityTest {

    @Test
    void bootFourDependenciesAreOnTheClasspath() {
        assertThat(SqlSessionFactory.class).isNotNull();
        assertThat(Flyway.class).isNotNull();
        assertThat(AuthorizationServerSettings.class).isNotNull();
    }
}
