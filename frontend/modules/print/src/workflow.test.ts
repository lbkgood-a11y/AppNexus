import { describe, expect, it } from "vitest";
import { sceneContract, selectDesignContract } from "./workflow";
import type { DesignContract } from "./authoring";

describe("契约到模板再到场景的操作顺序",()=>{
  const first:DesignContract={code:"outbound.v1",nameZh:"出仓单",nameEn:"Outbound",designOnly:false,fields:[]};
  const created:DesignContract={...first,code:"custom.v1",nameZh:"新定义契约"};
  it("新增契约立即进入模板选择，不退回首个内置契约",()=>{
    expect(selectDesignContract([first,created],created.code)).toBe(created);
    expect(selectDesignContract([first])).toBe(first);
    expect(selectDesignContract([],created.code)).toBeUndefined();
  });
  it("场景不能使用草稿模板，发布后契约唯一随模板确定",()=>{
    const draft={id:"template-a",data_contract:created.code,published_version:null};
    expect(sceneContract([draft],draft.id)).toBe("");
    expect(sceneContract([{...draft,published_version:1}],draft.id)).toBe(created.code);
    expect(sceneContract([{...draft,published_version:1}],"removed-template")).toBe("");
  });
});
