import type { DesignContract } from "./authoring";

/**
 * 选择新建模板的契约；显式入口优先，其次沿用刚保存的契约，最后取服务端首项。
 * @param contracts 本次从服务端取得的完整目录，不使用分区旧缓存。
 * @param preferred 用户从契约行进入时指定的编码，缺省可为空。
 * @returns 匹配契约；空目录返回 undefined，调用方必须引导先建契约。
 */
export function selectDesignContract(contracts:DesignContract[],preferred?:string):DesignContract|undefined{
  return contracts.find(value=>value.code===preferred)??contracts[0];
}

/**
 * 场景只绑定有发布版本的模板；契约始终来自模板，不允许独立选出不兼容组合。
 * @param templates 当前服务端模板元数据。
 * @param id 当前用户选中的模板 UUID。
 * @returns 可用契约编码；缺少发布版本或选项失效返回空串。
 */
export function sceneContract(templates:Record<string,unknown>[],id:unknown):string{
  const template=templates.find(value=>value.id===id&&Number(value.published_version)>0);
  return typeof template?.data_contract==="string"?template.data_contract:"";
}
