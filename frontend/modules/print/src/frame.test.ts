import { afterEach, expect, it, vi } from "vitest";
import { frameBootstrap } from "./frame";

afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers()});

/** 默认提供器与元素栏必须使用同一模块；省略参数时上游返回空目录且不报错。 */
it("初始化绑定 defaultModule 并启用原生设计画布",()=>{
  vi.useFakeTimers();
  let receive:(event:unknown)=>void=()=>{};
  const build=vi.fn(),design=vi.fn(),send=vi.fn();
  const library={disAutoConnect:vi.fn(),defaultElementTypeProvider:class {},hiprint:{
    init:vi.fn(),PrintElementTypeManager:{build},
    PrintTemplate:class {design=design;getJson(){return {panels:[]}}}
  }};
  const parent={postMessage:send};
  vi.stubGlobal("parent",parent);
  const barcode={toSVG:vi.fn()};
  vi.stubGlobal("window",{bwipjs:barcode,"vue-plugin-hiprint":library,addEventListener:(_name:string,handler:typeof receive)=>{receive=handler}});
  vi.stubGlobal("document",{documentElement:{dataset:{channel:"test-channel",engine:"data:text/javascript,"}},
    createElement:()=>({addEventListener:vi.fn()}),body:{append:vi.fn()}});
  frameBootstrap();
  expect((window as unknown as Record<string,unknown>)["bwip-js"]).toBe(barcode);
  receive({source:parent,data:{channel:"test-channel",id:"initialize-1",action:"initialize",payload:{template:{panels:[]},language:"cn"}}});
  expect(build).toHaveBeenCalledWith("#elements","defaultModule");
  expect(design).toHaveBeenCalledWith("#designer");
  expect(send).toHaveBeenCalledWith({channel:"test-channel",id:"initialize-1",value:null},"*");
});
