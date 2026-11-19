package com.appnexus.masterdata.adapter.jdbc;

import com.appnexus.masterdata.application.product.UnitConversionStore;
import com.appnexus.masterdata.MasterData.Rejection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 单位换算固定 SQL 适配；租户条件、两端锁、CAS 及四审计保持原协议，技术冲突在此转换。 */
public final class JdbcUnitConversionStore implements UnitConversionStore {
  private final JdbcTemplate jdbc;
  /** @param jdbc 与用例事务共享数据源的 JDBC 客户端，禁止独立提交 */
  public JdbcUnitConversionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> list(String tenant) {
    return jdbc.queryForList("SELECT BIN_TO_UUID(x.id) id,BIN_TO_UUID(source_unit_id) sourceUnitId,BIN_TO_UUID(target_unit_id) targetUnitId,s.code sourceCode,t.code targetCode,CAST(factor AS CHAR) factor,x.enabled,x.version FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=UUID_TO_BIN(?) ORDER BY s.code,t.code",tenant);
  }
  /** {@inheritDoc} */
  @Override public Unit lockUnit(String tenant,String id) {
    var rows=jdbc.queryForList("SELECT dimension_code dimensionCode,enabled FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",tenant,id);
    if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Unit((String)row.get("dimensionCode"),Boolean.TRUE.equals(row.get("enabled")));
  }
  /** {@inheritDoc} */
  @Override public BigDecimal reverse(String tenant,String source,String target) {
    var rows=jdbc.queryForList("SELECT factor FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND source_unit_id=UUID_TO_BIN(?) AND target_unit_id=UUID_TO_BIN(?) AND enabled=TRUE",tenant,source,target);
    return rows.isEmpty()?null:(BigDecimal)rows.getFirst().get("factor");
  }
  /** {@inheritDoc} */
  @Override public void create(String tenant,String id,String source,String target,BigDecimal factor,String actor) {
    try{jdbc.update("INSERT INTO md_product_unit_conversion(id,tenant_id,source_unit_id,target_unit_id,factor,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant,source,target,factor,actor,actor);}
    catch(DataIntegrityViolationException error){throw new Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}
  }
  /** {@inheritDoc} */
  @Override public Relation relation(String tenant,String id) {
    var rows=jdbc.queryForList("SELECT BIN_TO_UUID(source_unit_id) sourceId,BIN_TO_UUID(target_unit_id) targetId,factor FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,id);if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Relation((String)row.get("sourceId"),(String)row.get("targetId"),(BigDecimal)row.get("factor"));
  }
  /** {@inheritDoc} */
  @Override public int status(String tenant,String id,boolean enabled,long version,String actor) {
    try{return jdbc.update("UPDATE md_product_unit_conversion x SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=? AND enabled<>? AND (?=FALSE OR (EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.source_unit_id AND u.enabled=TRUE) AND EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.target_unit_id AND u.enabled=TRUE)))",enabled,actor,tenant,id,version,enabled,enabled);}
    catch(DataIntegrityViolationException error){throw new Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}
  }
  /** {@inheritDoc} */
  @Override public Calculation calculation(String tenant,String id) {
    var rows=jdbc.queryForList("SELECT x.factor,t.precision_scale scale FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=UUID_TO_BIN(?) AND x.id=UUID_TO_BIN(?) AND x.enabled=TRUE AND s.enabled=TRUE AND t.enabled=TRUE",tenant,id);if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Calculation((BigDecimal)row.get("factor"),((Number)row.get("scale")).intValue());
  }
}
