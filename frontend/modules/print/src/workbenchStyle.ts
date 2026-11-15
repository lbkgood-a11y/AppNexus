/**
 * 第二轮工作区排版；仅控制外壳和原生属性控件的排版，不覆盖纸张尺寸、坐标或 transform。
 * 左栏默认 232px、右栏 360px；折叠资源栏保留 48px 图标轨道，窄屏优先释放画布宽度。
 */
export const workbenchStyle = `
.workspace{grid-template-columns:var(--resources-width,232px) minmax(0,1fr) var(--properties-width,360px)}
.side{padding:12px 16px}.side h2{height:44px;padding:10px 24px 10px 32px;top:-12px;margin:-12px -16px 12px;font-size:13px}
.resource-tabs{margin-bottom:12px}.resource-tabs button{font-size:13px;padding:8px 4px}
#elements section{margin:0 0 22px}#elements .title{font-size:12px;color:var(--muted);margin:0 0 8px;font-weight:500}
#elements ul{gap:4px}#elements .ep-draggable-item{border:0;min-height:34px;padding:7px 6px;font-size:13px;border-radius:4px;white-space:nowrap}
#elements .ep-draggable-item:hover{background:var(--stage)}#elements .ep-draggable-item svg{width:16px;height:16px}
.capability-note{font-size:12px;line-height:1.6}.stage{padding:40px 48px;background-image:none}/* 网格仅用于设计态纸张，间隔 10pt；background 不占布局，不参与 pt/mm 坐标计算。 */
.stage:not(.no-grid) #designer .hiprint-printPaper.design{background-image:linear-gradient(to right,rgba(128,128,128,.08) 1px,transparent 1px),linear-gradient(to bottom,rgba(128,128,128,.08) 1px,transparent 1px);background-size:var(--grid-spacing,10pt) var(--grid-spacing,10pt)}
.stage.no-grid #designer .hiprint-printPaper.design{background-image:none}
.hiprint_rul_wrapper{opacity:.5}.hiprint_rul_wrapper:hover{opacity:1}
.side .hiprint-option-item-row{padding-bottom:10px;margin-bottom:10px;border:0}.side .hiprint-option-item-label{font-size:12px;padding-bottom:4px}
.side input,.side select{min-height:28px;font-size:13px}.prop-tab-items{flex-wrap:wrap;gap:4px}.prop-tab-item{padding:6px;font-size:12px}
.property-section{margin:0 0 16px;border-bottom:1px solid var(--border);padding-bottom:12px}.property-section>summary{cursor:pointer;font-size:13px;font-weight:600;padding:8px 0 12px;color:var(--text)}
.property-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:0 12px}.property-fields>.hiprint-option-item-row{grid-column:1/-1}.property-fields>.compact-property{grid-column:auto}
.workspace.left-collapsed{grid-template-columns:48px minmax(0,1fr) var(--properties-width,360px)}.workspace.right-collapsed{grid-template-columns:var(--resources-width,232px) minmax(0,1fr) 0}.workspace.left-collapsed.right-collapsed{grid-template-columns:48px minmax(0,1fr) 0}
.left-collapsed .left{visibility:visible;padding:48px 4px 8px;overflow:auto}.left-collapsed .left h2,.left-collapsed .left .resource-tabs,.left-collapsed #element-search,.left-collapsed .left .capability-note,.left-collapsed #elements .title,.left-collapsed #resource-sources{display:none!important}.left-collapsed #resource-elements{display:block!important}.left-collapsed #elements ul{grid-template-columns:1fr}.left-collapsed #elements .ep-draggable-item{font-size:0;justify-content:center}.left-collapsed #elements .ep-draggable-item svg{margin:0}.left-collapsed #elements section{margin-bottom:12px}
.quick-panel-active .toggle-right{display:none}.quick-panel-active .right{visibility:hidden}
.panel-resize{position:absolute;top:44px;bottom:0;width:6px;cursor:col-resize;z-index:4;touch-action:none}.resize-left{left:calc(var(--resources-width,232px) - 3px)}.resize-right{right:calc(var(--properties-width,360px) - 3px)}.panel-resize:hover,.panel-resize:focus-visible{background:var(--primary);opacity:.35}.left-collapsed .resize-left,.right-collapsed .resize-right{display:none}
@media(max-width:1100px){.workspace{--resources-width:200px;--properties-width:360px}.side{padding:12px}.stage{padding:32px}}
@media(max-width:700px){.workspace{--resources-width:168px;--properties-width:360px}.stage{padding:24px}.panel-resize{display:none}.property-fields{grid-template-columns:1fr}}
/* 预览仅展示引擎生成内容，防御性排除所有设计辅助元素。 */
#preview .hiprint_rul_wrapper,#preview .verLine,#preview .horLine,#preview .toplineOfPosition,#preview .leftlineOfPosition,#preview .rightlineOfPosition,#preview .bottomlineOfPosition,#preview .resize-panel{display:none!important}
#preview .hiprint-printPaper{background:#fff!important;background-image:none!important}
.hiprint-printPaper{box-shadow:0 2px 10px #00000012}
`;
