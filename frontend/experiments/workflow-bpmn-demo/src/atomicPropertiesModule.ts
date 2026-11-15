/** bpmn-js command handler types from the public command stack extension point. */
type PropertyUpdate = { moddleElement: any; properties: Record<string, unknown> };
type Context = { element: any; updates: PropertyUpdate[]; oldValues?: unknown[] };
type Modeling = { updateModdleProperties: (element: any, moddleElement: any, properties: Record<string, unknown>) => unknown };
type EventBus = { on: (event: string, priority: number, callback: (event: unknown) => void) => void };
type CommandStack = { registerHandler: (command: string, handler: new (modeling: Modeling) => AtomicPropertiesHandler) => void };
/** 多个 moddle 属性经diagram-js组合命令记录在同一撤销历史组。 */
export class AtomicPropertiesHandler {
  static $inject = ['modeling'];
  constructor(private readonly modeling: Modeling) {}
  preExecute(context: Context) {
    context.oldValues = context.updates.map(({ moddleElement, properties }) =>
      Object.fromEntries(Object.keys(properties).map(key => [key, moddleElement.get(key)]))
    );
    for (const update of context.updates) this.modeling.updateModdleProperties(context.element, update.moddleElement, update.properties);
  }
  execute() {}
  revert() {}
}
/** 不捕获或吞掉命令错误；测试与Modeler共用显式模块注册入口。 */
export function registerAtomicProperties(eventBus: EventBus, commandStack: CommandStack) {
  commandStack.registerHandler('devhub.atomicProperties', AtomicPropertiesHandler);
  eventBus.on('commandStack.devhub.atomicProperties.preExecute', 1000, () => {});
}
registerAtomicProperties.$inject = ['eventBus', 'commandStack'];
export const atomicPropertiesModule = {
  __init__: ['devhubAtomicProperties'],
  devhubAtomicProperties: ['type', registerAtomicProperties]
};
