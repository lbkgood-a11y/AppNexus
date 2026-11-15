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
    getElementById:()=>null,querySelector:()=>null,querySelectorAll:()=>[],
    createElement:()=>({addEventListener:vi.fn()}),body:{append:vi.fn()}});
  frameBootstrap();
  expect((window as unknown as Record<string,unknown>)["bwip-js"]).toBe(barcode);
  receive({source:parent,data:{channel:"test-channel",id:"initialize-1",action:"initialize",payload:{template:{panels:[]},language:"cn"}}});
  expect(build).toHaveBeenCalledWith("#elements","defaultModule");
  expect(design).toHaveBeenCalledWith("#designer");
  expect(send).toHaveBeenCalledWith({channel:"test-channel",id:"initialize-1",value:null},"*");
});

/** 视图缩放不得把既有模板 scale 覆盖掉，也不得改写物理纸张尺寸。 */
it("工作台缩放保留模板配置并返回真实纸张状态",()=>{
  vi.useFakeTimers();
  let receive:(event:any)=>void=()=>{};
  const panel={width:100,height:60,scale:.8,zoom:vi.fn(function(this:{scale:number|undefined},_value:number){this.scale=undefined})};
  const send=vi.fn(),parent={postMessage:send};
  vi.stubGlobal("parent",parent);
  vi.stubGlobal("window",{addEventListener:(_name:string,handler:typeof receive)=>{receive=handler},"vue-plugin-hiprint":{
    disAutoConnect:vi.fn(),defaultElementTypeProvider:class {},hiprint:{init:vi.fn(),PrintElementTypeManager:{build:vi.fn()},PrintTemplate:class {
      editingPanel=panel;printPanels=[panel];design=vi.fn();getSelectEls(){return []}getJson(){return {panels:[{width:panel.width,height:panel.height,scale:panel.scale}]}}
    }}
  }});
  vi.stubGlobal("document",{documentElement:{dataset:{channel:"test",engine:"data:"}},getElementById:()=>null,querySelector:()=>null,querySelectorAll:()=>[],createElement:()=>({addEventListener:vi.fn()}),body:{append:vi.fn()}});
  frameBootstrap();
  const command=(action:string,payload?:unknown)=>receive({source:parent,data:{channel:"test",id:action,action,payload}});
  command("initialize",{template:{panels:[]},language:"cn"});
  command("workspace",{action:"zoom",value:3});
  expect(panel.zoom).toHaveBeenCalledWith(2,false);
  expect(panel.scale).toBe(.8);
  command("state");
  expect(send).toHaveBeenLastCalledWith({channel:"test",id:"state",value:expect.objectContaining({width:100,height:60,zoom:2,undo:false,redo:false,selected:0})},"*");
});
