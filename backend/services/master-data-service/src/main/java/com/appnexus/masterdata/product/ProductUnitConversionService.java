package com.appnexus.masterdata.product;

import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 同维度显式直连换算；不改变商品基础单位或动态属性存储单位。 */
@Service
public final class ProductUnitConversionService {
 private final JdbcTemplate jdbc; private final AccessAuthorization access; private final TransactionTemplate tx;
 /** @param jdbc UTC数据源 @param access 可信主体授权端口 @param manager 同数据源事务管理器 */
 public ProductUnitConversionService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** @param sourceUnitId 源单位UUID @param targetUnitId 目标单位UUID @param factor 正数十进制字符串，整数最多18位、小数最多12位 */
 public record Input(String sourceUnitId,String targetUnitId,String factor){}
 /** @param enabled 目标启停状态，必填 @param version 当前CAS，非负且必填 */
 public record Status(Boolean enabled,Long version){}
 /** 原单位读取权内只读列表，审计由Controller公共投影提供。 */
 public Object list(VerifiedTenantContext c){require(c,"read");return jdbc.queryForList("SELECT BIN_TO_UUID(x.id) id,BIN_TO_UUID(source_unit_id) sourceUnitId,BIN_TO_UUID(target_unit_id) targetUnitId,s.code sourceCode,t.code targetCode,CAST(factor AS CHAR) factor,x.enabled,x.version FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=UUID_TO_BIN(?) ORDER BY s.code,t.code",c.tenantId().toString());}
 /** 系数不可原地修改；锁两个单位按UUID顺序防止反向关系并发绕过往返校验。 */
 public Object create(VerifiedTenantContext c,Input i){require(c,"convert");if(i==null)throw invalid();uuid(i.sourceUnitId());uuid(i.targetUnitId());if(i.sourceUnitId().equals(i.targetUnitId()))throw invalid();var factor=decimal(i.factor(),true);return tx.execute(s->{
  var ids=new ArrayList<>(List.of(i.sourceUnitId(),i.targetUnitId()));Collections.sort(ids);var units=new HashMap<String,Map<String,Object>>();for(String id:ids){var rows=jdbc.queryForList("SELECT dimension_code dimensionCode,enabled FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId().toString(),id);if(rows.size()!=1||!Boolean.TRUE.equals(rows.get(0).get("enabled")))throw invalid();units.put(id,rows.get(0));}
  if(!Objects.equals(units.get(i.sourceUnitId()).get("dimensionCode"),units.get(i.targetUnitId()).get("dimensionCode")))throw invalid();
  var reverse=jdbc.queryForList("SELECT factor FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND source_unit_id=UUID_TO_BIN(?) AND target_unit_id=UUID_TO_BIN(?) AND enabled=TRUE",c.tenantId().toString(),i.targetUnitId(),i.sourceUnitId());if(!reverse.isEmpty()&&((BigDecimal)reverse.get(0).get("factor")).multiply(factor).subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000000000001"))>0)throw invalid();
  String id=UUID.randomUUID().toString();try{jdbc.update("INSERT INTO md_product_unit_conversion(id,tenant_id,source_unit_id,target_unit_id,factor,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,c.tenantId().toString(),i.sourceUnitId(),i.targetUnitId(),factor,c.accountId().toString(),c.accountId().toString());}catch(org.springframework.dao.DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}return Map.of("id",id);});}
 /** 启停只推进修改审计和CAS，不改创建审计或系数；停用单位的关系不能重新启用。 */
 public Object status(VerifiedTenantContext c,String id,Status i){require(c,"convert");uuid(id);if(i==null||i.enabled()==null||i.version()==null||i.version()<0)throw invalid();return tx.execute(s->{if(i.enabled()){var relation=jdbc.queryForList("SELECT BIN_TO_UUID(source_unit_id) sourceId,BIN_TO_UUID(target_unit_id) targetId,factor FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),id);if(relation.size()!=1)throw invalid();var r=relation.getFirst();var ids=new ArrayList<>(List.of((String)r.get("sourceId"),(String)r.get("targetId")));Collections.sort(ids);for(String unit:ids){var locked=jdbc.queryForList("SELECT enabled FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId().toString(),unit);if(locked.size()!=1||!Boolean.TRUE.equals(locked.getFirst().get("enabled")))throw invalid();}var reverse=jdbc.queryForList("SELECT factor FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND source_unit_id=UUID_TO_BIN(?) AND target_unit_id=UUID_TO_BIN(?) AND enabled=TRUE",c.tenantId().toString(),r.get("targetId"),r.get("sourceId"));if(!reverse.isEmpty()&&((BigDecimal)reverse.getFirst().get("factor")).multiply((BigDecimal)r.get("factor")).subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000000000001"))>0)throw invalid();}int count;try{count=jdbc.update("UPDATE md_product_unit_conversion x SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=? AND enabled<>? AND (?=FALSE OR (EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.source_unit_id AND u.enabled=TRUE) AND EXISTS(SELECT 1 FROM md_product_unit u WHERE u.tenant_id=x.tenant_id AND u.id=x.target_unit_id AND u.enabled=TRUE)))",i.enabled(),c.accountId().toString(),c.tenantId().toString(),id,i.version(),i.enabled(),i.enabled());}catch(org.springframework.dao.DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_CONVERSION_CONFLICT");}if(count!=1)throw new MasterData.Rejection(409,"MASTER_DATA_CHANGED");return Map.of("ok",true);});}
 /** @param quantity 源数量精确十进制，最多18整数位和12小数位；不接收浮点数 @return 精确乘积和按目标单位精度HALF_UP的展示值，调用方不可将展示值当原值保存 */
 public Object calculate(VerifiedTenantContext c,String id,String quantity){require(c,"read");uuid(id);var amount=decimal(quantity,false);var rows=jdbc.queryForList("SELECT x.factor,t.precision_scale scale FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=UUID_TO_BIN(?) AND x.id=UUID_TO_BIN(?) AND x.enabled=TRUE AND s.enabled=TRUE AND t.enabled=TRUE",c.tenantId().toString(),id);if(rows.size()!=1)throw invalid();var exact=amount.multiply((BigDecimal)rows.get(0).get("factor"));return Map.of("exact",exact.stripTrailingZeros().toPlainString(),"display",exact.setScale(((Number)rows.get(0).get("scale")).intValue(),RoundingMode.HALF_UP).toPlainString());}
 private void require(VerifiedTenantContext c,String action){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.productunit:"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static BigDecimal decimal(String value,boolean positive){if(value==null||!value.matches("-?(0|[1-9][0-9]{0,17})([.][0-9]{1,12})?"))throw invalid();var n=new BigDecimal(value);if(positive&&n.signum()<=0)throw invalid();return n;}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_CONVERSION_INVALID");}
}
