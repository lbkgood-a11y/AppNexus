import { expect, it } from "vitest";
import { reactive } from "vue";
import { messagePayload } from "./messagePayload";

/** Vue 契约代理不能直接跨 iframe；回归覆盖初始化以及预览的嵌套响应式字段。 */
it("初始化消息去除嵌套 Vue 代理且保留字段协议",()=>{
  const contract=reactive({code:"test",fields:[{key:"lines",columns:[{key:"quantity"}]}]});
  const message={action:"initialize",payload:{contract,language:"cn",template:{panels:[{width:100,height:60}]}}};
  expect(()=>structuredClone(message)).toThrow();
  const copied=messagePayload(message);
  expect(structuredClone(copied)).toEqual(JSON.parse(JSON.stringify(message)));
  copied.payload.contract.fields[0].columns[0].key="changed";
  expect(contract.fields[0].columns[0].key).toBe("quantity");
});
