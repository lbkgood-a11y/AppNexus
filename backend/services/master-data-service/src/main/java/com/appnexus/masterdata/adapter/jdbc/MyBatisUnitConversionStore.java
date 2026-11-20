package com.appnexus.masterdata.adapter.jdbc;

import com.appnexus.masterdata.application.product.UnitConversionStore;
import com.appnexus.masterdata.MasterData.Rejection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;

/** 单位换算固定 SQL 适配；租户条件、两端锁、CAS 及四审计保持原协议，技术冲突在此转换。 */
public final class MyBatisUnitConversionStore implements UnitConversionStore {
  private final UnitConversionMapper mapper;
  /** @param mapper 与用例事务共享SqlSession的固定Mapper，禁止独立提交 */
  public MyBatisUnitConversionStore(UnitConversionMapper mapper) { this.mapper = java.util.Objects.requireNonNull(mapper); }
  /** {@inheritDoc} */
  @Override public List<Map<String, Object>> list(String tenant) {
    return mapper.list(tenantBytes(tenant));
  }
  /** {@inheritDoc} */
  @Override public Unit lockUnit(String tenant,String id) {
    var rows=mapper.lockUnit(tenantBytes(tenant),id);
    if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Unit((String)row.get("dimensionCode"),Boolean.TRUE.equals(row.get("enabled")));
  }
  /** {@inheritDoc} */
  @Override public BigDecimal reverse(String tenant,String source,String target) {
    var rows=mapper.reverse(tenantBytes(tenant),source,target);
    return rows.isEmpty()?null:(BigDecimal)rows.getFirst().get("factor");
  }
  /** {@inheritDoc} */
  @Override public void create(String tenant,String id,String source,String target,BigDecimal factor,String actor) {
    try{mapper.create(id,tenantBytes(tenant),source,target,factor,actor);}
    catch(DataIntegrityViolationException error){throw new Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}
  }
  /** {@inheritDoc} */
  @Override public Relation relation(String tenant,String id) {
    var rows=mapper.relation(tenantBytes(tenant),id);if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Relation((String)row.get("sourceId"),(String)row.get("targetId"),(BigDecimal)row.get("factor"));
  }
  /** {@inheritDoc} */
  @Override public int status(String tenant,String id,boolean enabled,long version,String actor) {
    try{return mapper.status(enabled,actor,tenantBytes(tenant),id,version);}
    catch(DataIntegrityViolationException error){throw new Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}
  }
  /** {@inheritDoc} */
  @Override public Calculation calculation(String tenant,String id) {
    var rows=mapper.calculation(tenantBytes(tenant),id);if(rows.size()!=1)return null;var row=rows.getFirst();
    return new Calculation((BigDecimal)row.get("factor"),((Number)row.get("scale")).intValue());
  }
  /** 可信租户UUID转数据库二进制；不从请求体读取租户，正式Mapper拦截器再次覆盖验证。 */
  private byte[] tenantBytes(String value){var id=java.util.UUID.fromString(value);return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
