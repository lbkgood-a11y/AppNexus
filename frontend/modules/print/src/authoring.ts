import { copyTemplate, createBlankTemplate, type PrintTemplate } from "./template";

/** 设计用字段定义；由服务端契约提供，示例值仅用于预览，不授予业务读取权限。 */
export interface DesignField { key:string; labelZh:string; labelEn:string; sample:string; type?:"TEXT"|"NUMBER"|"BOOLEAN"; required?:boolean; unit?:string; columns?:DesignField[] }
/** 已注册的设计契约；designOnly=true 表示尚未接入真实业务快照。 */
export interface DesignContract { code:string; version?:number; purpose?:string; nameZh:string; nameEn:string; designOnly:boolean; fields:DesignField[] }

/**
 * 创建受控文字或动态明细元素，不要求用户输入字段编码或回调脚本。
 * @param field 服务端已注册字段，数组字段必须包含受控列定义。
 * @param english 使用英文标签，默认中文。
 * @param top 元素纵坐标，单位 pt，默认 120；由调用方在可打印区域内放置。
 */
export function fieldElement(field:DesignField,english=false,top=120):Record<string,unknown>{
  const title=english?field.labelEn:field.labelZh;
  if(field.columns?.length)return {
    options:{field:field.key,top,left:24,width:540,height:60,fontSize:10},
    printElementType:{type:"table",title,columns:[field.columns.map(column=>({
      title:english?column.labelEn:column.labelZh,field:column.key,width:540/field.columns!.length,align:"left"
    }))]}
  };
  return {options:{field:field.key,title,hideTitle:false,top,left:24,width:250,height:24,fontSize:12,testData:field.sample},printElementType:{type:"text",title}};
}

/** 构造明确标注为示例的预览快照；数组字段使用不同样本行验证表格绑定。 */
export function previewData(contract:DesignContract,rows=3):Record<string,unknown>{
  if(!Number.isInteger(rows)||rows<1||rows>100)throw new Error("PRINT_PREVIEW_ROWS_INVALID");
  return Object.fromEntries(contract.fields.map(field=>[field.key,field.columns
    ?Array.from({length:rows},(_,index)=>Object.fromEntries(field.columns!.map(column=>[column.key,column.key==="lineNumber"?String(index+1):column.sample])))
    :field.sample]));
}

/**
 * 创建出仓单起始版式；只绑定所选服务端契约字段，不注入真实单据或额外字段。
 * @param contract 服务端注册契约，缺少明细或字段时仍只生成可用元素。
 * @param english 使用英文抬头，默认中文。
 * @returns A4 模板副本；用户可继续拖动和编辑，不会自动保存或发布。
 */
export function starterTemplate(contract:DesignContract,english=false):PrintTemplate{
  const template=createBlankTemplate();
  // 底部保留约 15 mm 页码/驱动容差，表格不能紧贴纸张边缘。
  template.panels[0].paperFooter=800;
  const title={options:{title:english?"Outbound delivery":"出仓单",top:24,left:205,width:240,height:30,fontSize:20},printElementType:{type:"text",title:""}};
  const fields=contract.fields.filter(field=>!field.columns);
  const elements:Record<string,unknown>[]=[title,...fields.map((field,index)=>{
    const element=fieldElement(field,english,76+Math.floor(index/2)*32);
    (element.options as Record<string,unknown>).left=index%2===0?24:310;return element;
  })];
  const table=contract.fields.find(field=>field.columns?.length);
  if(table)elements.push(fieldElement(table,english,92+Math.ceil(fields.length/2)*32));
  template.panels[0].printElements=elements;
  return copyTemplate(template);
}

/** 调整纸张仅修改当前模板副本，超过纸张边界的既有元素由调用方检查并提示，不悄悄缩放业务内容。 */
export function paperTemplate(value:PrintTemplate,width:number,height:number):PrintTemplate{
  if(!Number.isFinite(width)||!Number.isFinite(height)||width<20||width>1000||height<20||height>2000)throw new Error("PRINT_PAPER_INVALID");
  const template=copyTemplate(value);
  for(const panel of template.panels){panel.width=width;panel.height=height;panel.paperType="custom";panel.paperFooter=Math.max(20,(height-15)*72/25.4)}
  return template;
}

/**
 * 检查静态元素初始边界；动态表格高度由引擎分页，不把长表当成越界。
 * @param value 纸张宽高为 mm、元素位置和宽高为 pt 的受控模板。
 * @returns 越界元素总数，用于明确提示重新布局；不自动删减内容。
 */
export function overflowCount(value:PrintTemplate):number{
  let count=0;
  for(const panel of value.panels){
    const width=Number(panel.width)*72/25.4,height=Number(panel.height)*72/25.4;
    for(const element of (Array.isArray(panel.printElements)?panel.printElements:[])){
      const options=element.options??{},type=element.printElementType?.type;
      const left=Number(options.left??0),top=Number(options.top??0);
      if(left<0||top<0||left+Number(options.width??0)>width||top+(type==="table"?0:Number(options.height??0))>height)count++;
    }
  }
  return count;
}

/**
 * 按原有从上到下、从左到右顺序进行逐行紧凑排布；只改变位置，不改变内容、绑定、字号或尺寸。
 * @param value 当前引擎快照，纸张为 mm、元素为 pt；边距 24pt、元素间距 12pt。
 * @returns 独立模板副本；每个面板独立排布，动态表格按设计高度占位，实际分页由引擎处理。
 * @throws Error 纸张/元素尺寸无效、含固定或旋转元素、元素超宽或空间不足时整体拒绝，保留原设计。
 */
export function autoArrange(value:PrintTemplate):PrintTemplate{
  const result=copyTemplate(value),margin=24,gap=12;
  for(const panel of result.panels){
    const width=Number(panel.width)*72/25.4,height=Number(panel.height)*72/25.4;
    if(!Number.isFinite(width)||!Number.isFinite(height)||width<=margin*2||height<=margin*2)throw new Error("PRINT_ARRANGE_SPACE");
    const elements=Array.isArray(panel.printElements)?panel.printElements:[];
    const ordered=[...elements].sort((a,b)=>Number(a.options?.top??0)-Number(b.options?.top??0)||Number(a.options?.left??0)-Number(b.options?.left??0));
    let x=margin,y=margin,rowHeight=0;
    for(const element of ordered){
      const options=element.options??(element.options={});
      // 固定/旋转元素有跨页和包围盒语义，普通货架排布不能安全推断，拒绝而非破坏布局。
      if(options.fixed||Number(options.rotate??options.rotation??0)!==0)throw new Error("PRINT_ARRANGE_COMPLEX");
      const w=Number(options.width),h=Number(options.height);
      if(!Number.isFinite(w)||!Number.isFinite(h)||w<=0||h<=0||w>width-margin*2)throw new Error("PRINT_ARRANGE_SPACE");
      if(x+w>width-margin){x=margin;y+=rowHeight+gap;rowHeight=0}
      if(y+h>height-margin)throw new Error("PRINT_ARRANGE_SPACE");
      options.left=Math.round(x*100)/100;options.top=Math.round(y*100)/100;
      x+=w+gap;rowHeight=Math.max(rowHeight,h);
    }
  }
  return result;
}
