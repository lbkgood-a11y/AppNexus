# vue-pure-admin 集成基线

来源：vue-pure-admin v7.0.0，提交 b8177a202dceb1412d3bf56f3d15483dc1606c9c。
保留上游 MIT LICENSE。完整布局、主题、标签页、侧栏与设置组件位于应用壳内。
领域模块不得导入本应用的 src/layout、src/store、src/router 等内部文件。

本地调整：
- 使用 workspace 的统一安装和锁文件；提供跨平台 dev/build 命令。
- 只注册首页与错误路由，不将模板演示路由作为业务功能开放。
- 停用 fake-server，生产及开发均不模拟登录成功。
- 停用 URL Token SSO 和浏览器 Token 持久化，改用 BFF 会话。
- 登录入口跳转 /oauth2/authorization/appnexus，由授权服务器承载凭据交互。
- 已通过应用类型检查、生产构建和原有装配单元测试；后端已接入正式 MySQL 账号和 Valkey 共享会话；登录/布局/刷新/注销的浏览器闭环已通过，其他场景及生产故障验收仍待完成。

本地已实现的接入契约：
- GET /oauth2/authorization/appnexus：启动标准授权码登录。
- GET /bff/session：200 返回 {username,nickname,avatar,roles,permissions}；
  未认证返回 401，服务故障返回相应错误，不返回 Token。
- GET /bff/csrf：返回 {token,headerName}，绑定当前会话。
- POST /bff/logout：携带上述 CSRF 请求头，服务端注销后返回 204。
- 生产使用同源反向代理；开发需要将上述端点代理到实际 BFF。

清理记录（2026-11-06）：删除未使用的模板演示组件、静态资源、Mock、旧密码登录/刷新 Token 代码及示例账号设置页；首页改为基础控制台；保留完整布局及其实际依赖。删除 workspace components 占位包，未来按真实复用需求提取公共组件。

凭据页已使用模板样式，标准表单提交给授权服务器。正式账号由 MySQL 持久化，会话存入 Valkey；启动方式见 backend/README.md。

界面更新：登录页优化为响应式双栏布局，使用平台 @appnexus/icons（Remix Icon / Iconify 构建期 SVG）。ThemeToggle 复用模板主题持久化，在登录页及三种导航中提供浅色、深色、跟随系统；与原设置面板同步。
