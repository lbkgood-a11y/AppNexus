from pathlib import Path
import re

root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/businessobject/configuration')
path=root/'JdbcConfigurationStore.java'
source=path.read_text(encoding='utf-8')
pattern=re.compile(r'@Override public ([^\n]+?) (\w+)\(([^\n]*?)\) \{\s*return jdbc\.(queryForObject|queryForList|queryForMap|update)\("([^"]+)"(?:, ([^;]+))?\);\s*\}')
matches=list(pattern.finditer(source))
assert len(matches)==27,len(matches)
methods=[]; tables=set()
for m in matches:
    result,name,params,call,sql,args=m.groups()
    names=[p.strip().split()[-1] for p in params.split(',')] if params else []
    values=[p.strip() for p in args.split(',')] if args else []
    if call=='queryForObject': values=values[1:]
    assert values==names,(name,values,names)
    for value in values: sql=sql.replace('?', '#{'+value+'}',1)
    assert '?' not in sql,name
    tables.update(re.findall(r'(?i)(?:FROM|INTO|UPDATE)\s+(business_object_\w+)',sql))
    annotation='Select' if call.startswith('query') else ('Insert' if sql.startswith('INSERT') else 'Update')
    bound=', '.join('@Param("'+p.strip().split()[-1]+'") '+p.strip() for p in params.split(',')) if params else ''
    methods.append('  /** '+name+' 对应原配置存储契约；平台配置范围由用例校验，所有值参数绑定。 */\n  @'+annotation+'("'+sql+'")\n  '+result+' '+name+'('+bound+');')
mapper='''package com.appnexus.platform.persistence.businessobject.configuration;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.PlatformScoped;

/** 平台对象配置固定 SQL 映射；不接收 SQL 或列名，锁、版本与审计复用用例事务。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+x+'"' for x in sorted(tables))+'''})
public interface ConfigurationMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'ConfigurationMapper.java').write_text(mapper,encoding='utf-8')
def replace(m):
    result,name,params,*_=m.groups()
    args=', '.join(p.strip().split()[-1] for p in params.split(',')) if params else ''
    return '@Override public '+result+' '+name+'('+params+') {\n    return mapper.'+name+'('+args+');\n  }'
source=pattern.sub(replace,source).replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcConfigurationStore','MyBatisConfigurationStore').replace('private final JdbcTemplate jdbc;','private final ConfigurationMapper mapper;').replace('/** @param jdbc 与用例事务共用的数据源客户端，不允许为单条写入另开事务 */','/** @param mapper 与用例事务共用 SqlSession 的固定 Mapper，不允许为单条写入另开事务 */').replace('public MyBatisConfigurationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }','public MyBatisConfigurationStore(ConfigurationMapper mapper) { this.mapper = java.util.Objects.requireNonNull(mapper); }')
(root/'MyBatisConfigurationStore.java').write_text(source,encoding='utf-8')
path.unlink()

