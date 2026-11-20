from pathlib import Path
import re
base=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence')
params={'code':'已生成并校验唯一性的业务编码，失败不保存回执','tenant':'服务端可信租户 UUID，不可由页面自报覆盖','type':'已登记业务对象类型编码，不是表名','actor':'可信账号 UUID；系统执行按原契约允许空值','request':'当前账号与租户内的幂等请求 UUID','key':'租户内稳定编码器或规则键，不接受 SQL 标识符','field':'对象已声明的编码字段键','name':'显示名称，沿用业务用例的长度及非空约束','enabled':'启用状态；false 不影响历史回执','ruleKey':'租户内已发布的编码规则键','ruleVersion':'明确规则版本，正整数，不自动追随最新版本','version':'配置或规则版本，沿用调用用例的版本检查','revision':'期望修订号，用于 CAS 并发检查','relationships':'已通过用例校验的页面关系 JSON','disabled':'已通过用例校验的禁用操作 JSON','reason':'操作原因，不得记录凭据或业务敏感原文','operation':'已登记业务操作编码','draft':'通过用例校验的配置草稿 JSON','descriptorVersion':'代码声明版本，用于配置兼容性验证','bindings':'已校验的不可变绑定 JSON 快照','published':'已校验的配置发布 JSON 快照','id':'服务端生成或已校验的记录 UUID','object':'已授权业务记录标识，不作为动态表名','basis':'业务依据版本，沿用聚合版本协议','definition':'已发布专业流程定义标识','definitionVersion':'已发布流程版本；非工作流按契约允许 null','facts':'已授权投影的业务事实 JSON，不接受客户端自报事实','service':'受控专业服务编码','scene':'已声明打印场景编码，非打印允许 null','contract':'已声明数据契约编码，按专业能力可空','action':'受控审计动作编码','ruleJson':'通过编码规则校验的不可变 JSON','period':'服务端按规则与数据库 UTC 时间计算的流水周期','next':'持有行锁后计算的下一个流水值，失败整笔回滚'}
for rel in ['businessobject/configuration/ConfigurationMapper.java','businessobject/coding/CodingMapper.java']:
 p=base/rel;s=p.read_text(encoding='utf-8')
 pattern=re.compile(r'  /\*\* ([^\n]+) \*/\n(  @\w+\([^\n]+\)\n  ([^\n]+);)')
 def rewrite(m):
  description,body,signature=m.groups();names=re.findall(r'@Param\("([^"]+)"\)',signature)
  doc='  /**\n   * '+description+'\n'
  for n in names:doc+='   * @param '+n+' '+params[n]+'\n'
  doc+='   * @return '+('受影响行数；由调用方复核冲突及回滚' if signature.startswith('int ') else '原存储契约定义的查询结果；范围与字段保护由用例执行')+'\n   */\n'
  return doc+body
 s=pattern.sub(rewrite,s);p.write_text(s,encoding='utf-8')
for rel in ['organization/package-info.java','businessobject/configuration/package-info.java','businessobject/coding/package-info.java']:
 p=base/rel;s=p.read_text(encoding='utf-8').replace('JdbcOrganizationMembership','MyBatisOrganizationMembership')
 s=s.replace(' * <p>当前状态：',' * <p>固定查询已逐步迁移到 MyBatis Mapper；其余 JDBC 存量不视为完成迁移。\n * <p>当前状态：',1)
 p.write_text(s,encoding='utf-8')

