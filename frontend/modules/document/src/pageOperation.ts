import type { Binding, Descriptor } from './index';

/** 只允许选择业务已接入的页面操作，不把任意权限码当作可执行业务动作。 */
export function pageOperations(descriptor: Descriptor) {
  return descriptor.actions.flatMap(action=>[
    ...(action.pageOperation?[action]:[]),
    ...(action.aliases||[]).map(alias=>({...action,operationKey:alias.operationKey,pageOperation:alias.pageOperation}))
  ]).filter(action=>action.interaction.capabilities.map(c=>c.service).some(mode=>['BUSINESS','WORKFLOW','PRINT'].includes(mode)));
}

/** 选择页面操作后由声明确定行为，清除旧目标，防止界面标签与实际执行错配。 */
export function selectPageOperation(binding: Binding, descriptor: Descriptor, operationKey: string) {
  const action = pageOperations(descriptor).find(item => item.operationKey === operationKey);
  binding.operationKey = action?.operationKey || '';
  binding.actionCode = action?.actionCode || '';
  binding.service = action?.interaction.capabilities.map(c=>c.service).includes('WORKFLOW') ? 'WORKFLOW' : action?.interaction.capabilities.map(c=>c.service).includes('PRINT')?'PRINT':'BUSINESS';
  binding.definitionId = binding.service === 'WORKFLOW' ? '' : null;
  binding.definitionVersion = 0;
  binding.sceneCode=null;
}

/** 保存前核对操作与行为确实属于本单据；服务器仍执行相同约束。 */
export function hasPageOperation(binding: Binding, descriptor: Descriptor): boolean {
  return pageOperations(descriptor).some(action => action.operationKey === binding.operationKey && action.actionCode === binding.actionCode);
}
