package com.appnexus.platform;

import com.appnexus.platform.authorization.RowAccessControl;
import com.appnexus.platform.persistence.ScopedQueryLanguageDriver;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证公共范围绑定保留 SQL 结构与参数顺序，无需连接数据库。 */
class ScopedQueryLanguageDriverTest {
    private final ScopedQueryLanguageDriver driver = new ScopedQueryLanguageDriver();
    private final Configuration configuration = new Configuration();

    /** 普通 Provider SQL 和动态注解 SQL 的过滤值均按实际占位顺序绑定。 */
    @Test void bindsBetweenBusinessParameters() {
        var access = new RowAccessControl.Predicate(" AND id=UUID_TO_BIN(?)", List.of("record"));
        var parameters = Map.of("tenant", "tenant", "limit", 10, "query", Map.of("access", access));
        for (String sql : List.of(
                "SELECT id FROM records WHERE tenant_id=#{tenant} AND score < 10 /*appnexus-bind:query.access*/ LIMIT #{limit}",
                "<script>SELECT id FROM records WHERE tenant_id=#{tenant} AND score &lt; 10 /*appnexus-bind:query.access*/ LIMIT #{limit}</script>")) {
            var bound = driver.createSqlSource(configuration, sql, Map.class).getBoundSql(parameters);
            assertThat(bound.getSql()).contains("score < 10", "AND /*appnexus-scope*/ id=UUID_TO_BIN(?)");
            assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
                .containsExactly("tenant", "query.access.parameters[0]", "limit");
        }
    }

    /** 全量计划仍显式绑定；无读取计划的原写许可分支继续使用自己的门禁。 */
    @Test void preservesExplicitAllAndConditionalFallback() {
        String script = "<script>SELECT id FROM records WHERE tenant_id=#{tenant}"
            + "<if test='access != null'> /*appnexus-bind:access*/</if></script>";
        var source = driver.createSqlSource(configuration, script, Map.class);
        assertThat(source.getBoundSql(Map.of("tenant", "tenant", "access",
            new RowAccessControl.Predicate("", List.of()))).getSql()).contains("AND /*appnexus-scope*/ 1=1");
        assertThat(source.getBoundSql(Map.of("tenant", "tenant")).getSql()).doesNotContain("appnexus");
    }

    /** XML include 展开后仍由同一查询驱动绑定列条件。 */
    @Test void bindsSharedXmlConditions() {
        configuration.setDefaultScriptingLanguage(ScopedQueryLanguageDriver.class);
        String xml = """
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="scope.test">
              <sql id="where">WHERE tenant_id=#{tenant} /*appnexus-bind:access*/</sql>
              <select id="count" resultType="int">SELECT COUNT(*) FROM records <include refid="where"/></select>
            </mapper>
            """;
        var source = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        new XMLMapperBuilder(source, configuration, "scope-test.xml", configuration.getSqlFragments()).parse();
        var access = new RowAccessControl.Predicate(" AND id=UUID_TO_BIN(?)", List.of("record"));
        var bound = configuration.getMappedStatement("scope.test.count")
            .getBoundSql(Map.of("access", access, "tenant", "tenant"));
        assertThat(bound.getSql()).contains("AND /*appnexus-scope*/ id=UUID_TO_BIN(?)");
        assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
            .containsExactly("tenant", "access.parameters[0]");
    }

}
