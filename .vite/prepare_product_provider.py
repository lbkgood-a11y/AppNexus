from pathlib import Path
import re,json
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')
operations=[]
for file in ['ProductController.java','ProductStructureController.java','ProductImageController.java']:
 s=(root/file).read_text(encoding='utf-8')
 prefix=re.search(r'@RequestMapping\("([^"]+)"\)',s).group(1)
 mappings=list(re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\(([^)]*)\))?',s))
 for binding in re.finditer(r'@OperationBinding\(([^)]*)\)',s):
  a=binding.group(1)
  if 'objectType="Product"' not in a:continue
  def value(key):return re.search(key+r'="([^"]*)"',a).group(1)
  mapping=min(mappings,key=lambda m:min(abs(m.end()-binding.start()),abs(m.start()-binding.end())))
  attrs=(mapping.group(2) or '').strip();suffix=re.search(r'^(?:value\s*=\s*)?"([^"]*)"',attrs)
  pages=re.findall(r'"([^"]+)"',re.search(r'pages=\{([^}]+)\}',a).group(1))
  permissions=re.findall(r'"([^"]+)"',re.search(r'permissions=\{([^}]+)\}',a).group(1));assert len(permissions)==1
  operations.append(dict(code=value('operationCode'),id=value('id'),zh=value('nameZh'),en=value('nameEn'),permission=permissions[0].split('&')[0],preconditions=permissions[0].split('&')[1:],method=mapping.group(1).upper(),path=prefix+(suffix.group(1) if suffix else ''),pages=pages,file=file))
assert len(operations)==25,len(operations)
Path('.vite/product-operations.json').write_text(json.dumps(operations,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for op in operations:print(op['id'],op['method'],op['path'])
