package com.appnexus.platform.persistence.businessobject.configuration;

import com.appnexus.platform.businessobject.port.ConfigurationStore;
import java.util.List;
import java.util.Map;

/** 固定 SQL 的配置存储适配器；沿用原表、租户条件、行锁、版本及数据库审计约束。 */
public final class MyBatisConfigurationStore implements ConfigurationStore {
  private final ConfigurationMapper mapper;
  /** @param mapper 与用例事务共用 SqlSession 的固定 Mapper，不允许为单条写入另开事务 */
  public MyBatisConfigurationStore(ConfigurationMapper mapper) { this.mapper = java.util.Objects.requireNonNull(mapper); }
  /** {@inheritDoc} */
  @Override public Boolean typeEnabled(String type) {
    return mapper.typeEnabled(type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> publishedConfiguration(String tenant, String type) {
    return mapper.publishedConfiguration(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> typeState(String type) {
    return mapper.typeState(type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> pageRelationshipState(String type) {
    return mapper.pageRelationshipState(type);
  }
  /** {@inheritDoc} */
  @Override public int createPageRelationships(String type, String relationships, String disabled, String actor) {
    return mapper.createPageRelationships(type, relationships, disabled, actor);
  }
  /** {@inheritDoc} */
  @Override public int updatePageRelationships(String relationships, String disabled, String actor, String type, long revision) {
    return mapper.updatePageRelationships(relationships, disabled, actor, type, revision);
  }
  /** {@inheritDoc} */
  @Override public int appendEntryControl(String type, String operation, boolean enabled, String reason, String actor) {
    return mapper.appendEntryControl(type, operation, enabled, reason, actor);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> disabledOperations(String type) {
    return mapper.disabledOperations(type);
  }
  /** {@inheritDoc} */
  @Override public int ensureType(String type) {
    return mapper.ensureType(type);
  }
  /** {@inheritDoc} */
  @Override public int updateTypeState(boolean enabled, String type, long revision) {
    return mapper.updateTypeState(enabled, type, revision);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> tenantGrants(String type) {
    return mapper.tenantGrants(type);
  }
  /** {@inheritDoc} */
  @Override public int saveTenantGrant(String tenant, String type, boolean enabled) {
    return mapper.saveTenantGrant(tenant, type, enabled);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> configuration(String tenant, String type) {
    return mapper.configuration(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int createDraft(String tenant, String type, String draft) {
    return mapper.createDraft(tenant, type, draft);
  }
  /** {@inheritDoc} */
  @Override public int updateDraft(String draft, String tenant, String type, long revision) {
    return mapper.updateDraft(draft, tenant, type, revision);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> lockConfiguration(String tenant, String type) {
    return mapper.lockConfiguration(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int appendConfigurationVersion(String tenant, String type, int version, int descriptorVersion, String bindings, String actor) {
    return mapper.appendConfigurationVersion(tenant, type, version, descriptorVersion, bindings, actor);
  }
  /** {@inheritDoc} */
  @Override public int publishConfiguration(String draft, String published, int version, String tenant, String type) {
    return mapper.publishConfiguration(draft, published, version, tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> resolutionByRequest(String tenant, String actor, String request) {
    return mapper.resolutionByRequest(tenant, actor, request);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> sharePublishedConfiguration(String tenant, String type) {
    return mapper.sharePublishedConfiguration(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public Integer descriptorVersion(String tenant, String type, Object version) {
    return mapper.descriptorVersion(tenant, type, version);
  }
  /** {@inheritDoc} */
  @Override public int createResolution(String id, String tenant, String actor, String request, String type, String object, long basis, Object version, String definition, Integer definitionVersion, String facts, String operation, String service, String scene, String contract) {
    return mapper.createResolution(id, tenant, actor, request, type, object, basis, version, definition, definitionVersion, facts, operation, service, scene, contract);
  }
  /** {@inheritDoc} */
  @Override public Map<String, Object> requiredResolutionByRequest(String tenant, String actor, String request) {
    return mapper.requiredResolutionByRequest(tenant, actor, request);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> publishedConfigurationState(String tenant, String type) {
    return mapper.publishedConfigurationState(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> resolutionById(String tenant, String actor, String id) {
    return mapper.resolutionById(tenant, actor, id);
  }
  /** {@inheritDoc} */
  @Override public Boolean tenantGranted(String tenant, String type) {
    return mapper.tenantGranted(tenant, type);
  }
  /** {@inheritDoc} */
  @Override public int appendAudit(String tenant, String actor, String type, String action) {
    return mapper.appendAudit(tenant, actor, type, action);
  }
}
