# 存量本人查询迁移与客商 MySQL 验收

## 实际调用链与 SQL

| HTTP 入口 | 业务路径 | SQL 执行 |
|---|---|---|
| GET /api/leave-poc | LeavePocController.list → LeavePocService.list 校验有效成员 → MyBatisLeaveRepository.ownRequests → LeaveRecordMapper.ownRequests | 从唯一对象映射取得 leave_poc_request 和 tenant_id，绑定可信租户，公共层追加 applicant 相等及当前 person_id 或历史 NULL。未选人员时只取历史 NULL。按创建时间降序取最近 100 张。 |
| GET /api/leave-poc/{id}/timeline | LeavePocController.timeline → LeavePocService.timeline 校验有效成员及原申请人 → MyBatisLeaveRepository.appliedApprovals → LeaveRecordMapper.appliedApprovals | 按同租户、父单据和已应用回执读取 leave_poc_approval_cycle，EXISTS 父单据且公共层应用申请账号范围。打印及流程子路径各自按原服务准入。 |
| GET /api/leave-poc/{id}/cycles | LeavePocController.cycles → LeavePocService.cycles 校验有效成员及原申请人 → MyBatisLeaveRepository.approvalCycles → LeaveRecordMapper.approvalCycles | 按同租户、父单据读取轮次摘要，EXISTS 父单据且公共层应用申请账号范围。 |

这三条语句共用平台绑定与拦截校验，缺少可信身份、租户或实际谓词时拒绝。入库接口已经消费统一 ALL 计划，本批不扩大其策略能力。请假原文详情的有效待办分支、打印历史的本人范围以及其他留档语句仍需逐入口复核，本记录不宣告全仓迁移完成。

仍待迁移的实际路径包括请假原文和打印历史。原文从 `GET /api/leave-poc/{id}` 进入 `LeavePocService.read`，`LeaveRecordMapper.request` 按租户和单据键读取，再由用例验证申请人或当前有效任务。时间线的打印分支从 `LeavePocService.timeline` 调用 `BusinessPrintingService.history`，`BusinessPrintingMapper.history` 在打印任务读取准入后按租户、来源和输出拥有账号筛选。

## 声明同步复核

| 条目 | 本批影响 |
|---|---|
| D01 | 唯一对象 LeaveRequest，原模块归属及版本保持，策略语义未改变。 |
| D02 | 复用已有 list、timeline、approvalHistory 操作。 |
| D03 | 原三个 GET 路由和操作引用保持。 |
| D04 | 自助读取保持有效成员及申请人准入，审批待办和写入走原准入。 |
| D05 | 复用唯一 Provider 记录映射的主表、租户、申请人和人员列，Mapper 明确绑定相应对象与本人范围。审批历史按父键并核对父申请人。 |
| D06 | 原文、附件及审批字段投影保持，未改字段策略。 |
| D07 | 未新增专业能力。 |
| D08 | 响应、日期、分页上限和历史回执契约保持。 |
| D09 | 无编码变化。 |
| D10 | 查询由组合应用的公共驱动和执行拦截器绑定、校验，未新增对象运行器。 |
| D11 | 无策略数据迁移、建表、审批提交事务或状态改变。 |
| D12 | 组合应用隔离库执行结果见下。 |

## 交付门禁

| 条目 | 适用性与证据 |
|---|---|
| G01 | 本次确认并迁移上述三个本人固定查询，其他边界见调用链。 |
| G02 | 公共驱动、拦截器在平台持久化边界，业务只依赖中立范围元数据。 |
| G03 | 继续使用可信成员和自助准入，漏绑改为语句拒绝。精确复核清单删除了对应三条旧记录。 |
| G04 | 真实业务接入和返回正反向结果见下。 |
| G05 | MySQL 用例覆盖缺失身份、错租户、错账号、错人员、同账号不同人员、历史空人员和父记录过滤。 |
| G06 | 源码门禁及隔离 Maven 结果见下。 |
| G07 | 中文参数说明与声明标准已同步。 |
| G08 | 本地隔离验证，未部署。 |
| G09 | D01–D12 对照见上。 |
| C01–C03 | 适用，权限执行和客商原字段、写入越权断言走真实 MySQL。 |
| C04–C05 | 无数据库和写入行为迁移。客商原写入约束由测试复核。 |
| C06 | 已用真实 MySQL 临时表执行三条查询的 EXPLAIN，数据分布与访问路径见下。未做生产负载或并发容量验证。 |
| C07–C10 | 无前端、正式依赖、外部协议或专业能力接入变化。 |

## 运行记录

- `java scripts/AccessBuildGate.java .` 通过，待复核 0 项，留档 582 项。
- 普通 Maven 输出目录的并行读写期间出现附件 Mapper 的不完整类产物。使用独立 Maven 输出目录 `target-stock-owner-qa` 重新编译并验收，不复用该产物。
- 隔离 MySQL 8.4 中复制原表索引建立临时表，生成 10,000 张请假单和 10,000 条轮次，分布于 20 个租户、每租户 10 个申请账号，2,599 张的人员为空。本人列表使用 `ix_leave_poc_applicant` 反向范围扫描，估计检查 50 行。轮次和审批回执经父单复合主键定位，子表使用 `uq_leave_cycle_basis`，两个访问各估计检查 1 行。数据为合成，连接断开即清除。

- 组合应用隔离命令为 `mvn -q -f backend/pom.xml -pl applications/monolith-app -am -Dappnexus.build.directory=target-stock-owner-qa -Dtest=BusinessPartnerDataAccessTest,ScopedQueryLanguageDriverTest -Dsurefire.failIfNoSpecifiedTests=false test`，退出 0。绑定测试 3 项、组合应用真实 MySQL 测试 4 项通过，无失败或跳过。
- 客商旧用例以 ALL、指定记录、成员、字典条件和 CURRENT_ACCOUNT 改写授权输入。仍覆盖跨租户、直读指定记录、禁止更新或启停其他记录、跨租户归属移动、未知字典值、受限字段回传、删除明细绕过、字段隐藏及掩码、成员继承切换、过期及撤权。固定组织角色范围已退役，分支权限只按现行可授权维度测试。

- 最终联合验收在同一隔离构建目录执行，`-Dtest=BusinessPartnerDataAccessTest,ScopedQueryLanguageDriverTest,LeavePocTest,BusinessObjectIntegrationTest`，退出 0。客商与请假 SQL 执行门禁 4 项、查询驱动 3 项、请假模块 13 项，共 20 项通过，无失败或跳过。最终复核包含本人条件漏绑、无可信请求、错租户、错账号、错人员、同账号不同人员、历史空人员和非本人审批历史。
