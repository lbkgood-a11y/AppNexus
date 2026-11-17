/** 租户地理摘要；code 是稳定业务编码，父级为空表示国家，停用节点仅用于历史回显。 */
export interface GeographyNode {code:string;name:string;countryCode:string;parentCode:string;enabled:boolean}
/** 组件选择结果；path 为从国家到所选节点的目录路径，不作为服务端授权或保存事实。 */
export interface GeographySelection {node:GeographyNode;path:GeographyNode[]}
/** Element Plus 树节点；禁选祖先仍可展开，后代是否可选由业务目录状态决定。 */
export interface GeographyTree {value:string;label:string;search:string;disabled:boolean;children:GeographyTree[]}
/**
 * 生成真实父级路径，缺失父级或环返回空，避免把残缺关系伪装成根节点。
 * @param nodes 当前租户完整摘要，不与其他会话合并
 * @param code 节点编码，空或历史文本返回空路径
 */
export function geographyPath(nodes:GeographyNode[],code:string):GeographyNode[]{
 return indexedPath(new Map(nodes.map(node=>[node.code,node])),code);
}
/** 共用索引避免构造树时为每个节点重复扫描全目录。 */
function indexedPath(index:Map<string,GeographyNode>,code:string):GeographyNode[]{
 const path:GeographyNode[]=[];const seen=new Set<string>();let current=index.get(code);
 while(current){if(seen.has(current.code))return [];seen.add(current.code);path.unshift(current);if(!current.parentCode)return path;const parent=index.get(current.parentCode);if(!parent||parent.countryCode!==current.countryCode)return [];current=parent;}
 return [];
}
/**
 * 按真实父级构造可选择树；停用祖先会阻止新选后代，历史值单独回显。
 * @param nodes 当前租户摘要
 * @param countryCode 可选国家限制；缺省空表示全部，不隐式假设中国
 * @param parentCode 可选区划祖先限制；缺省空表示不限
 * @param level any 允许任意区划，province 仅国家直属区划，city 允许其直属下级，默认 any
 */
export function geographyTree(nodes:GeographyNode[],countryCode="",parentCode="",level:"any"|"province"|"city"="any"):GeographyTree[]{
 const index=new Map(nodes.map(n=>[n.code,n]));const tree=new Map<string,GeographyTree>();
 for(const node of nodes){
  const path=indexedPath(index,node.code);if(!path.length||countryCode&&node.countryCode!==countryCode)continue;
  if(parentCode&&!path.some(n=>n.code===parentCode))continue;
  const depth=path.length-1;if(level==="province"&&depth>1||level==="city"&&parentCode&&depth>2)continue;
  tree.set(node.code,{value:node.code,label:node.name+" · "+node.code,search:path.map(n=>n.name+" "+n.code).join(" / "),disabled:!node.parentCode||path.some(n=>!n.enabled)||(level==="city"&&!!parentCode&&node.code===parentCode),children:[]});
 }
 const roots:GeographyTree[]=[];
 for(const [code,node] of tree){const parent=tree.get(index.get(code)!.parentCode);if(parent)parent.children.push(node);else roots.push(node);}
 return roots;
}
