from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')
labels={'tenant':'安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户','id':'已由调用用例校验的记录UUID','product':'已通过原商品操作准入的父商品UUID','pid':'已通过原商品操作准入的父商品UUID','config':'商品明确绑定的配置版本UUID，不追随最新版本','category':'当前租户分类UUID','categoryId':'当前租户分类UUID','unit':'当前租户基础单位UUID','revision':'真实属性修订UUID','actor':'来自可信上下文的实际账号UUID，不冒充被代表人员','version':'当前聚合非负版本，用于CAS校验','lock':'写用例是否在原事务内锁定父记录，默认无额外锁','locks':'写用例是否按原顺序加共享锁，false用于无写锁预检','source':'当前租户来源单位UUID','target':'当前租户目标单位UUID','factor':'严格正数换算系数，精度及上下限由领域规则校验','enabled':'目标启用状态，不改变其他租户关系','kind':'只允许构建内固定category或unit目录','keyword':'最多200字的字面包含关键词，空字符串表示不过滤','size':'每页1–100条，由上层验证','offset':'从0开始的分页偏移，由合法页码计算','state':'领域规则产生的固定完整性状态，不来自客户端自报事实','name':'最多200字字面包含名称，空字符串表示不过滤','code':'最多64字字面包含编码，空字符串表示不过滤','field':'配置内可搜索属性的稳定字段','item':'配置内可搜索属性UUID','option':'当前属性修订内的选项UUID','event':'固定业务事件编码','before':'创建时可空的原始授权JSON快照','after':'已授权结果JSON快照','query':'已验证的业务筛选；不能作为权限计划或SQL输入'}
for p in root.glob('*Mapper.java'):
 s=p.read_text(encoding='utf-8')
 methods=list(re.finditer(r'\n( +)(?:List<[^;\n]+>|Map<[^;\n]+>|int|long|Boolean|Integer)\s+\w+\(([^;\n]*)\);',s))
 for m in reversed(methods):
  names=re.findall(r'@Param\("[^"\n]+"\)\s+(?:[\w.<>\[\],]+)\s+(\w+)',m.group(2))
  begin=s.rfind('/**',0,m.start());end=s.find('*/',begin)
  if begin<0 or end>m.start():raise ValueError(p)
  block=s[begin:end+2]
  if '@param' in block:continue
  description=block[3:-2].strip();indent=m.group(1)
  doc='/**\n'+indent+' * '+description+'\n'
  for name in names:doc+=indent+' * @param '+name+' '+labels[name]+'\n'
  doc+=indent+' * @return '+('当前租户固定查询结果，不扩展原操作授权' if m.group(0).strip().startswith(('List','Map')) else '原存储契约定义的结果；并发或引用冲突由调用用例复核')+'\n'+indent+' */'
  s=s[:begin]+doc+s[end+2:]
 # 单行多个标准标签逐项换行，保证JavaDoc工具可以识别。
 def format_doc(m):
  block=m.group(0)
  if '@param' not in block:return block
  inner=block[3:-2].strip();inner=re.sub(r'\s+(?=@(?:param|return|throws)\b)','\n * ',inner)
  return '/**\n * '+inner+'\n */' if '\n' not in block else '/**'+re.sub(r'(?<!\n)\s+(?=@(?:param|return|throws)\b)','\n * ',block[3:-2])+'*/'
 s=re.sub(r'/\*\*[\s\S]*?\*/',format_doc,s)
 p.write_text(s,encoding='utf-8')
for p in root.glob('MyBatis*.java'):
 s=p.read_text(encoding='utf-8').replace('import java.math.BigDecimal;\n','') if p.name=='MyBatisProductList.java' else p.read_text(encoding='utf-8')
 if p.name=='MyBatisProductList.java':s=s.replace('import java.time.LocalDate;\n','')
 s=re.sub(r'\n private static String tenant\(VerifiedTenantContext c\)\{return c.tenantId\(\).toString\(\);\}','',s)
 p.write_text(s,encoding='utf-8')
