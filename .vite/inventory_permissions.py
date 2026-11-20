from pathlib import Path
import re,json
files=[p for p in Path('backend').rglob('*.java') if '/src/main/java/' in p.as_posix() and not any(x.startswith('target') for x in p.parts)]
rows=[]
for p in sorted(files):
 s=p.read_text(encoding='utf-8-sig')
 if not any(x in s for x in ['JdbcTemplate','@Mapper','java.sql.Connection','getConnection()']):continue
 rows.append({'path':p.as_posix(),'jdbcTemplate':bool(re.search(r'\b(?:NamedParameter)?JdbcTemplate\b',s)),'mapper':'@Mapper' in s,'historicalMigration':'/db/migration/' in p.as_posix(),'accessPlanning':'planning.plan(' in s or 'AccessPlanning' in s,'rowExecution':'RowAccessControl' in s,'fieldProtection':'FieldProtection' in s,'functionalAuthorization':'AccessAuthorization' in s or 'EndpointOperation' in s,'status':'未验证：需要沿入口和上层调用链核对，标记仅为文本线索'})
out=Path('docs/validation/全局数据权限与MyBatis迁移清单.json')
out.write_text(json.dumps({'baseline':'d48bd9d；保留任务开始前未提交对象运行抽象改动','scope':'全部自有生产 Java；包括历史迁移和底层连接，分类不构成豁免','files':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('production database files',len(rows),'JdbcTemplate',sum(r['jdbcTemplate'] for r in rows))
