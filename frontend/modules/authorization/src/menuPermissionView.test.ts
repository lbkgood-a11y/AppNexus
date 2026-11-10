import {describe,it,expect} from 'vitest';
import {menuPermissionTree,nodePermissions} from './menuPermissionView';
import type {AuthorizationMenu,PermissionItem} from './index';
/** 验证共享权限去重、筛选批选和无菜单功能不丢失。 */
describe('menu permission view',()=>{
 const permission=(id:string,resource:string,enabled=true)=>({id,resource,code:resource+':'+id,enabled}) as PermissionItem;
 const menu=(id:string,parentId:string|null,resources:string[]):AuthorizationMenu=>({id,parentId,resources,operations:resources.flatMap(resource=>['read','delete'].map(action=>resource+':'+action)),titleZh:id,titleEn:id,sortOrder:1,permission:null});
 it('does not expand all resource actions when pages share one resource',()=>{
  const roles={...menu('roles',null,['access.role']),operations:['access.role:read','access.role:delete']};
  const center={...menu('center',null,['access.role']),operations:['access.role:read','access.role:grant']};
  const tree=menuPermissionTree([roles,center],['read','delete','grant','publish'].map(action=>permission(action,'access.role')),true,'other');
  expect(tree.find(item=>item.id==='roles')?.items.map(item=>item.code)).toEqual(['access.role:read','access.role:delete']);
  expect(tree.find(item=>item.id==='center')?.items.map(item=>item.code)).toEqual(['access.role:read','access.role:grant']);
  expect(tree.find(item=>item.id==='unlinked')?.children[0].items[0].code).toBe('access.role:publish');
 });
 it('shares permissions between pages and deduplicates directory selection',()=>{
  const read=permission('read','orders');
  const tree=menuPermissionTree([menu('root',null,[]),menu('a','root',['orders']),menu('b','root',['orders'])],[read],true,'other');
  expect(tree[0].children).toHaveLength(2);
  expect(nodePermissions(tree[0]).map(item=>item.id)).toEqual(['read']);
 });
 it('retains unlinked permissions and excludes disabled items from bulk selection',()=>{
  const tree=menuPermissionTree([menu('a',null,['orders'])],[permission('read','orders'),permission('delete','orders',false),permission('call','api')],false,'Other');
  expect(nodePermissions(tree[0]).map(item=>item.id)).toEqual(['read']);
  expect(tree[1].label).toBe('Other');
  expect(nodePermissions(tree[1])[0].resource).toBe('api');
 });
 it('only selects filtered permissions and prunes empty menus',()=>{
  const tree=menuPermissionTree([menu('a',null,['orders']),menu('b',null,['inventory'])],[permission('read','orders')],true,'other');
  expect(tree).toHaveLength(1);
  expect(nodePermissions(tree[0])).toHaveLength(1);
 });
});
