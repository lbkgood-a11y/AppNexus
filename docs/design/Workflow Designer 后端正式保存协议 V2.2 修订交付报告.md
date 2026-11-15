# Workflow Designer 后端正式保存协议 V2.2 修订交付报告

日期：2026-11-15；性质：仅设计，待独立反向复审。[完整协议](Workflow Designer 后端正式保存协议 V2.2.md)。

## 修订处理

| V2.1评审项 | V2.2选择 | 当前结论 |
|---|---|---|
| RESERVED/LIVE首存缺口 | CREATE同T0控制行+合法空草稿rev0，首次save从0；RESERVED不可编辑 | 协议明确，未实现 |
| 删除恢复不闭合 | 服务器保留正文软删，恢复同来源，rev/epoch递增，永久墓碑 | 待产品确认及DB验证 |
| Publish旁路 | 明确当前Runtime部署链，定义带Session/epoch的P1/P2/P3；V1及内部旁路必须关闭 | 正式代码尚未纳管 |
| Flowable原子性 | 首选A同资源完整事务，但必须故障证明；B独立提交/C对账不获本版实施资格 | A未验证，B/C BLOCKED |
| GET混合信封 | 主库MySQL8+/InnoDB，D→Session→正文FOR SHARE短顶层事务 | 待数据库竞争验证 |
| UUID/规范字节 | 小写v4 UUID、BINARY16网络序；Definition二进制比较；canonical BLOB权威 | 待迁移兼容及跨实现验证 |

## 关键决策

发布绑定不可变source_revision和publishRequestId，实际deploymentId/processDefinitionId写不可变映射；不假定引擎版本号等于业务版本。P3持D锁与所有写排序；部署原子性未证实则关闭发布，不留旧HTTP或内部Runtime路径。独立部署若没有引擎端幂等与栅栏，只能描述孤儿隔离，不能承诺最多一次或旧Session不产生部署。

GET在统一锁区返回document/revision/lifecycle/epoch及授权Session状态；返回后仍可能被合法接管，后续写复核epoch。canonical保留原28组并补充至33组向量，原始/规范字节/摘要/长度/版本分存，MySQL JSON只查询投影；永久保留规范信封，暂不启用只留摘要清理优化。

A–E保留条件证明，新增F发布资格、G发布映射、H读取信封、I删除恢复证明，分别列前提/反例/线性化点。保存安全不等于发布、读取或执行系统全局一致。

## 待确认及风险

产品/安全：空草稿结构合法但不可发布、独占接管、撤权判定宽限、软删恢复来源、历史回执可见性。运维：单主不丢确认提交、旧主隔离、真实事务同源及连接路由。发布：Flowable事务传播/缓存/监听副作用、部署失败恢复及孤儿处理必须真实验证；B/C另审。

真实测试前置：MySQL8+ InnoDB、隔离应用与Flowable数据源、两实例可控屏障、commit/进程/连接故障注入、禁止V1旁路、无生产数据。第22节27项规格全部未执行，不编造PASS。

具备提交下一轮独立反向复审的文档条件；不代表评审通过、真实MySQL通过、允许正式改造或上线。完成后停止。

## 工作区保护

本轮仅新增V2.2及本报告。开始Git状态263项，状态及既有文件哈希保存在/tmp/v22-baseline.json。只执行读取、rg/源码核对、git状态、哈希与文档创建，未运行构建/测试/服务/迁移。交付回复记录结束哈希核查；旧协议、报告、代码、Demo、配置和依赖未改。
