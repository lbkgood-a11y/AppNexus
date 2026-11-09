import type {AuthorizationMenu,PermissionItem} from './index';

/** 组合节点只组织现有权限，同一权限可以出现在多个页面，草稿仍以权限 ID 去重。 */
export interface MenuPermissionNode {id:string;label:string;items:PermissionItem[];children:MenuPermissionNode[]}

/**
 * 构造授权用菜单树；保留隐藏/停用菜单的权限，避免导航配置变化造成权限无法维护。
 * @param menus 管理域完整目录，不是操作者本人可用菜单
 * @param permissions 当前筛选后的真实权限，目录批选仅作用于这些项
 * @param chinese 是否使用中文名称
 * @param otherLabel 无菜单权限分组名称
 * @returns 稳定排序、剪除空节点后的菜单树及无菜单权限
 */
export function menuPermissionTree(menus:AuthorizationMenu[],permissions:PermissionItem[],chinese:boolean,otherLabel:string):MenuPermissionNode[]{
 const linked=new Set<string>();
 const ordered=[...menus].sort((a,b)=>a.sortOrder-b.sortOrder||(a.id<b.id?-1:1));
 const visit=(parentId:string|null,path:Set<string>):MenuPermissionNode[]=>ordered.filter(menu=>(menu.parentId??null)===parentId).flatMap(menu=>{
  if(path.has(menu.id))return [];
  const items=permissions.filter(item=>menu.resources.includes(item.resource)||item.code===menu.permission);
  items.forEach(item=>linked.add(item.id));
  const children=visit(menu.id,new Set([...path,menu.id]));
  return items.length||children.length?[{id:menu.id,label:chinese?menu.titleZh:menu.titleEn,items,children}]:[];
 });
 const tree=visit(null,new Set());
 const unlinked=permissions.filter(item=>!linked.has(item.id));
 if(unlinked.length)tree.push({id:'unlinked',label:otherLabel,items:[],children:[...new Set(unlinked.map(item=>item.resource))].map(resource=>({id:'resource:'+resource,label:resource,items:unlinked.filter(item=>item.resource===resource),children:[]}))});
 return tree;
}

/** 目录批选集合保持去重，不包含禁用项或筛选外权限，不创建目录权限。 */
export function nodePermissions(node:MenuPermissionNode):PermissionItem[]{
 return [...new Map([...node.items,...node.children.flatMap(nodePermissions)].filter(item=>item.enabled).map(item=>[item.id,item])).values()];
}
