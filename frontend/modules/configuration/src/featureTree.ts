import type { FeatureRegistration, MenuScope, OperationDefinition } from "./index";

/** 功能树只承载代码登记的展示信息，不参与授权判定。 */
export interface FeatureRow {
  /** 整树唯一标识；操作使用页面标识与权限码共同定位。 */
  id: string;
  /** 明确区分分类、页面与操作权限，避免将操作权限冒充实际按钮。 */
  type: "scope" | "page" | "object" | "operation";
  title: string;
  permission?: string | null;
  path?: string;
  component?: string;
  gate?: string;
  operationId?: string;
  category?: string;
  module?: string;
  objectType?: string;
  operationCode?: string;
  endpoints?: string;
  policy?: string;
  children?: FeatureRow[];
}

/**
 * 按管理域构建代码功能树；命中操作时保留页面和分类祖先，不按普通行分页。
 * @param registrations 服务端已经授权返回的构建内快照
 * @param query 名称、权限码、路由或组件关键字；空字符串表示不筛选
 * @param scope 管理域；空字符串表示全部管理域
 * @param chinese 是否使用中文页面名称
 * @returns 只包含匹配项及其祖先的新树，不修改原始快照
 */
export function buildFeatureTree(registrations: FeatureRegistration[], query: string, scope: MenuScope | "", chinese: boolean, definitions: OperationDefinition[] = []): FeatureRow[] {
  const needle = query.trim().toLocaleLowerCase();
  const matches = (...values: (string | null | undefined)[]) => values.some(value => value?.toLocaleLowerCase().includes(needle));
  return (["PLATFORM", "TENANT", "AUTHENTICATED"] as const).filter(value => !scope || scope === value).flatMap(value => {
    const children = registrations.filter(item => item.page.scope === value).flatMap(({ page, operations }) => {
      const all: FeatureRow[] = definitions.filter(item=>item.pages.includes(page.key)).map(item=>({id: `${page.key}:${item.id}`,type:"operation",title:chinese?item.nameZh:item.nameEn,operationId:item.id,objectType:item.objectType,operationCode:item.operationCode,permission:item.permissions.join(" OR "),endpoints:item.endpoints.join("；"),policy:item.policy,gate:page.gate}));
      const registered=new Set(all.flatMap(item=>(item.permission??"").split(/ OR |&/)));
      // 暂未迁移的权限明确保留原码，避免迁移过程中静默丢失已有展示。
      all.push(...[...new Set(operations)].filter(code=>!registered.has(code)).map(permission=>({id:`${page.key}:${permission}`,type:"operation" as const,title:permission,permission,gate:page.gate})));
      const pageMatches = matches(page.titleZh, page.titleEn, page.key, page.path, page.permission);
      const selected = pageMatches ? all : all.filter(row => matches(row.title,row.permission,row.operationId,row.endpoints));
      if (!pageMatches && !selected.length) return [];
      return [{ id: page.key, type: "page" as const, title: chinese ? page.titleZh : page.titleEn, permission: page.permission, path: page.path, component: page.key, gate: page.gate, children: selected.length ? selected : undefined }];
    });
    return children.length ? [{ id: value, type: "scope" as const, title: value, children }] : [];
  });
}
