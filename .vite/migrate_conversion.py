from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')
p=root/'JdbcUnitConversionStore.java';s=p.read_text(encoding='utf-8')
pat=re.compile(r'jdbc\.(queryForList|update)\("([^"]+)",([^;]*?)\)(?=[;}]|==)')
entries=list(pat.finditer(s));assert len(entries)==7,len(entries)
names=['list','lockUnit','reverse','create','relation','status','calculation'];methods=[]
for name,m in zip(names,entries):
 call,sql,args=m.groups();args=[x.strip() for x in args.split(',')];unique=list(dict.fromkeys(args));params=[]
 for arg in args:
  sql=sql.replace('?', '#{__verifiedTenant}' if arg=='tenant' else '#{'+arg+'}',1)
 sql=sql.replace('UUID_TO_BIN(#{__verifiedTenant})','#{__verifiedTenant}')
 for arg in unique:
  typ='byte[]' if arg=='tenant' else 'BigDecimal' if arg=='factor' else 'boolean' if arg=='enabled' else 'long' if arg=='version' else 'String'
  params.append('@Param("'+('__verifiedTenant' if arg=='tenant' else arg)+'") '+typ+' '+arg)
 ret='List<Map<String,Object>>' if call=='queryForList' else 'int';ann='Select' if call=='queryForList' else 'Insert' if sql.startswith('INSERT') else 'Update'
 methods.append('  /** '+name+' 固定SQL；可信租户由执行拦截器复核，行锁及CAS沿用业务事务。 */\n  @'+ann+'("'+sql+'")\n  '+ret+' '+name+'('+','.join(params)+');')
 s=s.replace(m.group(0),'mapper.'+name+'('+','.join('tenantBytes(tenant)' if x=='tenant' else x for x in unique)+')',1)
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 单位换算固定多表Mapper；只访问本主数据上下文表，不接受SQL或动态列。 */
@Mapper
@TenantAggregateScoped(tables={"md_product_unit_conversion","md_product_unit"},statements={'''+','.join('"'+x+'"' for x in names)+'''})
public interface UnitConversionMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'UnitConversionMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcUnitConversionStore','MyBatisUnitConversionStore').replace('private final JdbcTemplate jdbc;','private final UnitConversionMapper mapper;').replace('@param jdbc 与用例事务共享数据源的 JDBC 客户端，禁止独立提交','@param mapper 与用例事务共享SqlSession的固定Mapper，禁止独立提交').replace('public MyBatisUnitConversionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }','public MyBatisUnitConversionStore(UnitConversionMapper mapper) { this.mapper = java.util.Objects.requireNonNull(mapper); }')
s=s.rstrip()[:-1]+'''  /** 可信租户UUID转数据库二进制；不从请求体读取租户，正式Mapper拦截器再次覆盖验证。 */
  private byte[] tenantBytes(String value){var id=java.util.UUID.fromString(value);return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
(root/'MyBatisUnitConversionStore.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/MasterDataConfiguration.java');s=p.read_text(encoding='utf-8').replace('JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){\n    return new com.appnexus.masterdata.application.product.ProductUnitConversionService(', 'com.appnexus.masterdata.adapter.jdbc.UnitConversionMapper mapper,AccessAuthorization access,PlatformTransactionManager manager){\n    return new com.appnexus.masterdata.application.product.ProductUnitConversionService(').replace('new com.appnexus.masterdata.adapter.jdbc.JdbcUnitConversionStore(jdbc)','new com.appnexus.masterdata.adapter.jdbc.MyBatisUnitConversionStore(mapper)').replace('/** @param jdbc UTC 数据源，与事务共用连接 @param access','/** @param mapper 固定单位换算Mapper，与事务共用连接 @param access');p.write_text(s,encoding='utf-8')
