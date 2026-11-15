import type { Binding, Descriptor } from './index';

/** 只允许选择业务已接入的页面操作，不把任意权限码当作可执行业务动作。 */
export function pageOperations(descriptor: Descriptor) {
  return descriptor.operations.filter(action=>(!!action.pageOperation||!!action.aliases?.length)&&action.interaction.capabilities.some(c=>['BUSINESS','WORKFLOW','PRINT'].includes(c.service)));

}

/** 选择业务行为后由声明确定行为，清除旧目标，防止界面标签与实际执行错配。 */
export function selectPageOperation(binding: Binding, descriptor: Descriptor, operationId: string) {
  const action = pageOperations(descriptor).find(item => item.operationId === operationId);
  binding.operationId = action?.operationId || '';
  binding.operationCode = action?.operationCode || '';
  binding.service = action?.interaction.capabilities.map(c=>c.service).includes('WORKFLOW') ? 'WORKFLOW' : action?.interaction.capabilities.map(c=>c.service).includes('PRINT')?'PRINT':'BUSINESS';
  binding.definitionId = binding.service === 'WORKFLOW' ? '' : null;
  binding.definitionVersion = 0;
  binding.sceneCode=null;
}

/** 保存前核对操作与行为确实属于本业务对象；服务器仍执行相同约束。 */
export function hasPageOperation(binding: Binding, descriptor: Descriptor): boolean {
  return pageOperations(descriptor).some(action => action.operationId === binding.operationId && action.operationCode === binding.operationCode);
}
