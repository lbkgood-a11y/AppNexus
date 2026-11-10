# 工作流中心前端

复用当前vue-pure-admin应用壳，选择标准列表工作区：任务、定义和实例为平面查询对象，使用BusinessWorkspace及ManagementTable，保留刷新、密度、列设置、全屏和分页。
编辑与详情使用ManagedDialog，表单使用Element Plus，中英文进入应用locales，API/CSRF/租户上下文由admin应用注入。

## 专用组件说明

已检查ManagementTable、BusinessWorkspace、ManagedDialog及模板表单能力；它们不提供流程图节点/连接编辑，因此新增WorkflowCanvas与WorkflowDesigner。
Canvas仅使用SVG绘制连接、Element Plus按钮展示可键盘选择的节点；Designer以Element Plus表单编辑受控图，不引入UI库、独立主题或任意脚本执行。
设计器已独立为/workflow/designer菜单，使用顶部操作、左侧节点库/大纲、中央拓扑画布、右侧属性的整页布局；支持内存撤销/重做和缩放，支持节点拖入、节点移动及锚点拖线；展示坐标随受控模型保存，不提供任意BPMN编辑。样本预演无业务副作用，不能代替真实运行验收。

工作台包含待处理、已处理、本人发起、抄送、编排和运行管理；后两项按后端能力控制。办理支持受控输入及构建内业务路由穿透。
查询当前限定最近100条，筛选和分页在该集合内执行，达到上限时明确提示；不是全量业务查询。发布版本以独立页签查看，不承诺自动语义差异合并。

package.json只声明包元数据、公开入口、构建命令及仓库已有依赖；没有系统运行配置或敏感值。tsconfig.json为TypeScript编译选项，沿用相邻领域包，不改变运行时权限。

本轮以不连接业务接口的临时组件夹具验证添加节点、属性定位、撤销/重做、未保存提示和校验定位，并观察深色英文及1440×900、1024×768、390×844视口。夹具已删除，不交付模拟接口；真实登录后的完整保存发布仍未做浏览器验收。

## 构建参数说明

| 参数 | 用途及默认/缺省行为 |
| --- | --- |
| package.name/private/type | 包名固定@appnexus/workflow；private=true禁止发布；type=module使用ES模块 |
| exports | 仅公开模型/API类型入口和center页面入口，不允许业务深层导入 |
| scripts.build/typecheck | vue-tsc只做类型校验不输出文件；生产资产由admin的Vite构建 |
| scripts.test | Vitest；当前无前端独立用例，passWithNoTests不构成界面验收 |
| dependencies | 仅复用仓库ui/icons；peerDependencies由宿主提供Vue、Element Plus、i18n和路由，不建立平行实例 |
| devDependencies | 沿用仓库TypeScript、vue-tsc与Vitest版本，不引入新UI运行时 |
| compilerOptions.target/module | ES2022/ESNext，仅控制编译语义，不改变HTTP或安全契约 |
| moduleResolution | Bundler，匹配宿主Vite与公开exports解析 |
| strict/noImplicitAny | strict=true，noImplicitAny=false沿用相邻领域模板回调基线；不代替接口类型约束 |
| lib | ES2022与DOM，用于浏览器平台类型 |
| include | 仅包含src下ts、d.ts和vue文件，编译输出由宿主负责 |
