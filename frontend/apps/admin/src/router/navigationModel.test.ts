import {describe,it,expect} from "vitest";
import type {MenuNode} from "@appnexus/configuration";
import {buildNavigationMenus,safeNavigationUrl} from "./navigationModel";

/** 验证过滤不破坏排序、隐藏祖先及外链边界，不依赖浏览器身份或模拟 Token。 */
describe("navigation composition",()=>{
  const node=(id:string,extra:Partial<MenuNode>={}):MenuNode=>({id,code:id,type:"MENU",titleZh:id,titleEn:id,
    path:`/${id}`,scope:"TENANT",sortOrder:5,enabled:true,visible:true,version:0,...extra});
  it("keeps stable sibling order after filtering and prunes empty directories",()=>{
    const menus=buildNavigationMenus([node("root",{type:"DIRECTORY",sortOrder:0}),node("z",{parentId:"root"}),
      node("a",{parentId:"root"}),node("hidden",{parentId:"root",sortOrder:1,visible:false}),node("empty",{type:"DIRECTORY"})]);
    expect(menus).toHaveLength(1);
    expect(menus[0].children?.map(item=>item.meta.title.en)).toEqual(["a","z"]);
  });
  it("does not expose hidden ancestors or buttons and preserves external targets",()=>{
    const menus=buildNavigationMenus([node("hidden",{type:"DIRECTORY",visible:false}),node("child",{parentId:"hidden"}),
      node("button",{type:"BUTTON"}),node("external",{type:"EXTERNAL",externalUrl:"https://example.com/tasks"})]);
    expect(menus).toHaveLength(1);
    expect(menus[0].name).toBe("https://example.com/tasks");
    expect(menus[0].path).toBe("https://example.com/tasks");
  });
  it("rejects unsafe URLs while supporting ordinary intranet HTTP and HTTPS",()=>{
    for(const value of ["javascript:alert(1)","//example.com","https://user:secret@example.com","https://example.com\\evil","https://example.com\n"])
      expect(safeNavigationUrl(value)).toBe(false);
    expect(safeNavigationUrl("http://intranet.example/tasks")).toBe(true);
    expect(safeNavigationUrl("https://example.com/tasks?id=1")).toBe(true);
  });
});
