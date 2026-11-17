import { describe, expect, it } from "vitest";
import { GeographyTreeContractError, validateNavigationPage } from "../../../modules/master-data/src/navigation";

/** 回归旧后端返回业务列表、父级循环与续载不推进，避免在树上重复显示同批节点。 */
describe("geographic tree contract",()=>{
  const node={code:"CN",name:"中国（China）",originalName:"China",hierarchyCode:"AS-CN",parentCode:"continent:AS",parentHierarchyCode:"AS",leaf:false};
  const valid={parentCode:"continent:AS",rows:[node],total:1};
  it("accepts a direct child with explicit parent hierarchy",()=>{
    expect(validateNavigationPage(valid,"continent:AS","AS",new Set(["continent:AS"]),1).rows).toEqual([node]);
  });
  it("rejects an old business-list response and root repeated under a parent",()=>{
    expect(()=>validateNavigationPage({rows:[{code:"CN",name:"China",id:"record"}],total:1},"","",new Set(),1)).toThrow(GeographyTreeContractError);
    expect(()=>validateNavigationPage({parentCode:"",rows:[node],total:1},"CN","AS-CN",new Set(["CN"]),1)).toThrow(GeographyTreeContractError);
  });
  it("rejects ancestor cycles, wrong parent paths and duplicate siblings",()=>{
    for(const response of [valid,{...valid,rows:[{...node,parentHierarchyCode:"EU"}]},{...valid,rows:[node,node],total:2}]){
      expect(()=>validateNavigationPage(response,"continent:AS","AS",new Set(["CN"]),1)).toThrow(GeographyTreeContractError);
    }
    expect(()=>validateNavigationPage({...valid,rows:[node,node],total:2},"continent:AS","AS",new Set(),1)).toThrow(GeographyTreeContractError);
  });
  it("rejects duplicate next pages and empty pages that still advertise more",()=>{
    expect(()=>validateNavigationPage({...valid,total:51},"continent:AS","AS",new Set(["CN"]),2)).toThrow(GeographyTreeContractError);
    expect(()=>validateNavigationPage({parentCode:"continent:AS",rows:[],total:51},"continent:AS","AS",new Set(),2)).toThrow(GeographyTreeContractError);
  });
});
