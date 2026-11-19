package com.appnexus.platform.persistence.businessobject.configuration;

import com.appnexus.platform.businessobject.port.ConfigurationStore;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** 固定 SQL 的配置存储适配器；沿用原表、租户条件、行锁、版本及数据库审计约束。 */
public final class JdbcConfigurationStore implements ConfigurationStore {
  private final JdbcTemplate jdbc;
  /** @param jdbc 与用例事务共用的数据源客户端，不允许为单条写入另开事务 */
  public JdbcConfigurationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  /** {@inheritDoc} */
  @Override public Boolean typeEnabled(String type) {
    return jdbc.queryForObject("SELECT COALESCE(MAX(enabled),FALSE) FROM business_object_type WHERE object_type=?", Boolean.class, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> publishedConfiguration(String tenant, String type) {
    return jdbc.queryForList("SELECT published FROM business_object_configuration WHERE tenant_id=? AND object_type=? AND published_version>0", tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> typeState(String type) {
    return jdbc.queryForList("SELECT enabled,revision FROM business_object_type WHERE object_type=?", type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> pageRelationshipState(String type) {
    return jdbc.queryForList("SELECT revision,relationships FROM business_object_page_relationships WHERE object_type=?", type);
  }
  /** {@inheritDoc} */
  @Override public int createPageRelationships(String type, String relationships, String disabled, String actor) {
    return jdbc.update("INSERT IGNORE INTO business_object_page_relationships(object_type,relationships,disabled_operations,updated_by) VALUES(?,?,?,?)", type, relationships, disabled, actor);
  }
  /** {@inheritDoc} */
  @Override public int updatePageRelationships(String relationships, String disabled, String actor, String type, long revision) {
    return jdbc.update("UPDATE business_object_page_relationships SET relationships=?,disabled_operations=?,updated_by=?,revision=revision+1 WHERE object_type=? AND revision=?", relationships, disabled, actor, type, revision);
  }
  /** {@inheritDoc} */
  @Override public int appendEntryControl(String type, String operation, boolean enabled, String reason, String actor) {
    return jdbc.update("INSERT INTO business_object_entry_control_audit(object_type,operation_id,enabled,reason,actor) VALUES(?,?,?,?,?)", type, operation, enabled, reason, actor);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> disabledOperations(String type) {
    return jdbc.queryForList("SELECT disabled_operations FROM business_object_page_relationships WHERE object_type=?", type);
  }
  /** {@inheritDoc} */
  @Override public int ensureType(String type) {
    return jdbc.update("INSERT IGNORE INTO business_object_type(object_type) VALUES(?)", type);
  }
  /** {@inheritDoc} */
  @Override public int updateTypeState(boolean enabled, String type, long revision) {
    return jdbc.update("UPDATE business_object_type SET enabled=?,revision=revision+1 WHERE object_type=? AND revision=?", enabled, type, revision);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> tenantGrants(String type) {
    return jdbc.queryForList("SELECT tenant_id,enabled FROM business_object_tenant_grant WHERE object_type=? ORDER BY tenant_id", type);
  }
  /** {@inheritDoc} */
  @Override public int saveTenantGrant(String tenant, String type, boolean enabled) {
    return jdbc.update("INSERT INTO business_object_tenant_grant(tenant_id,object_type,enabled) VALUES(?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)", tenant, type, enabled);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> configuration(String tenant, String type) {
    return jdbc.queryForList("SELECT revision,published_version,draft FROM business_object_configuration WHERE tenant_id=? AND object_type=?", tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int createDraft(String tenant, String type, String draft) {
    return jdbc.update("INSERT IGNORE INTO business_object_configuration(tenant_id,object_type,draft) VALUES(?,?,?)", tenant, type, draft);
  }
  /** {@inheritDoc} */
  @Override public int updateDraft(String draft, String tenant, String type, long revision) {
    return jdbc.update("UPDATE business_object_configuration SET draft=?,revision=revision+1 WHERE tenant_id=? AND object_type=? AND revision=?", draft, tenant, type, revision);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> lockConfiguration(String tenant, String type) {
    return jdbc.queryForList("SELECT * FROM business_object_configuration WHERE tenant_id=? AND object_type=? FOR UPDATE", tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int appendConfigurationVersion(String tenant, String type, int version, int descriptorVersion, String bindings, String actor) {
    return jdbc.update("INSERT INTO business_object_configuration_version(tenant_id,object_type,version,descriptor_version,bindings,actor) VALUES(?,?,?,?,?,?)", tenant, type, version, descriptorVersion, bindings, actor);
  }
  /** {@inheritDoc} */
  @Override public int publishConfiguration(String draft, String published, int version, String tenant, String type) {
    return jdbc.update("UPDATE business_object_configuration SET draft=?,published=?,published_version=?,revision=revision+1 WHERE tenant_id=? AND object_type=?", draft, published, version, tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> resolutionByRequest(String tenant, String actor, String request) {
    return jdbc.queryForList("SELECT * FROM business_object_resolution WHERE tenant_id=? AND actor=? AND request_key=?", tenant, actor, request);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> sharePublishedConfiguration(String tenant, String type) {
    return jdbc.queryForList("SELECT published,published_version FROM business_object_configuration WHERE tenant_id=? AND object_type=? AND published_version>0 FOR SHARE", tenant, type);
  }
  /** {@inheritDoc} */
  @Override public Integer descriptorVersion(String tenant, String type, Object version) {
    return jdbc.queryForObject("SELECT descriptor_version FROM business_object_configuration_version WHERE tenant_id=? AND object_type=? AND version=?", Integer.class, tenant, type, version);
  }
  /** {@inheritDoc} */
  @Override public int createResolution(String id, String tenant, String actor, String request, String type, String object, long basis, Object version, String definition, Integer definitionVersion, String facts, String operation, String service, String scene, String contract) {
    return jdbc.update("INSERT IGNORE INTO business_object_resolution(id,tenant_id,actor,request_key,object_type,object_id,basis_version,configuration_version,definition_id,definition_version,facts,operation_code,service,scene_code,data_contract) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, tenant, actor, request, type, object, basis, version, definition, definitionVersion, facts, operation, service, scene, contract);
  }
  /** {@inheritDoc} */
  @Override public Map<String, Object> requiredResolutionByRequest(String tenant, String actor, String request) {
    return jdbc.queryForMap("SELECT * FROM business_object_resolution WHERE tenant_id=? AND actor=? AND request_key=?", tenant, actor, request);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> publishedConfigurationState(String tenant, String type) {
    return jdbc.queryForList("SELECT published,published_version FROM business_object_configuration WHERE tenant_id=? AND object_type=? AND published_version>0", tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> resolutionById(String tenant, String actor, String id) {
    return jdbc.queryForList("SELECT * FROM business_object_resolution WHERE tenant_id=? AND actor=? AND id=?", tenant, actor, id);
  }
  /** {@inheritDoc} */
  @Override public Boolean tenantGranted(String tenant, String type) {
    return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM business_object_tenant_grant WHERE tenant_id=? AND object_type=? AND enabled=TRUE)", Boolean.class, tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int appendAudit(String tenant, String actor, String type, String action) {
    return jdbc.update("INSERT INTO business_object_audit(tenant_id,actor,object_type,action) VALUES(?,?,?,?)", tenant, actor, type, action);
  }
}
