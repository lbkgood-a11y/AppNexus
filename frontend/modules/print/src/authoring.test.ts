import { describe, expect, it } from "vitest";
import { autoArrange, fieldElement, overflowCount, paperTemplate, previewData, starterTemplate, type DesignContract } from "./authoring";

const contract:DesignContract={code:"test.v1",nameZh:"测试",nameEn:"Test",designOnly:true,fields:[
  {key:"number",labelZh:"单号",labelEn:"Number",sample:"TEST-001"},
  {key:"items",labelZh:"明细",labelEn:"Items",sample:"",columns:[{key:"lineNumber",labelZh:"序号",labelEn:"No.",sample:"1"},{key:"name",labelZh:"名称",labelEn:"Name",sample:"Sample"}]}
]};

describe("普通用户自助模板制作",()=>{
  it("自动排布消除重叠并保留内容、尺寸和原模板",()=>{
    const input={panels:[{width:210,height:297,printElements:[
      {options:{left:0,top:0,width:300,height:60,field:"number"}},
      {options:{left:0,top:0,width:300,height:80,title:"固定文字"}}
    ]}]};
    const arranged=autoArrange(input),elements=arranged.panels[0].printElements as any[];
    expect(elements[0].options.left).toBe(24);
    expect(elements[1].options.top).toBe(96);
    expect(elements[1].options.title).toBe("固定文字");
    expect(elements[0].options.field).toBe("number");
    expect(elements[0].options.width).toBe(300);
    expect(input.panels[0].printElements[1].options.top).toBe(0);
  });
  it("超宽、空间不足或复杂固定元素时整体拒绝",()=>{
    for(const options of [{width:1000,height:20},{width:100,height:1000},{width:100,height:20,fixed:true}]){
      expect(()=>autoArrange({panels:[{width:210,height:297,printElements:[{options}]}]})).toThrow();
    }
  });
  it("起始模板使用契约绑定动态表格，保留中英文名称且不修改契约",()=>{
    const template=starterTemplate(contract);
    const elements=template.panels[0].printElements as Record<string,any>[];
    expect(elements[0].options.title).toBe("出仓单");
    expect(elements.at(-1)!.options.field).toBe("items");
    expect(elements.at(-1)!.printElementType.columns[0][1].field).toBe("name");
    expect((fieldElement(contract.fields[0],true).options as any).title).toBe("Number");
    expect(contract.fields[0].sample).toBe("TEST-001");
  });
  it("预览生成真实数组结构，限制长表样本数量而非将所有字段填成同一字符串",()=>{
    const data=previewData(contract,50);
    expect(data.number).toBe("TEST-001");
    expect((data.items as any[])).toHaveLength(50);
    expect((data.items as any[])[49].lineNumber).toBe("50");
    expect(()=>previewData(contract,101)).toThrow();
    expect(()=>previewData(contract,NaN)).toThrow();
  });
  it("纸张尺寸校验不覆盖原设计，不悄悄缩放元素",()=>{
    const original=starterTemplate(contract),changed=paperTemplate(original,100,60);
    expect(original.panels[0].width).toBe(210);
    expect(changed.panels[0].width).toBe(100);
    expect(changed.panels[0].printElements).toEqual(original.panels[0].printElements);
    expect(overflowCount(original)).toBe(0);
    expect(overflowCount(changed)).toBeGreaterThan(0);
    expect(()=>paperTemplate(original,0,20)).toThrow();
  });
});
