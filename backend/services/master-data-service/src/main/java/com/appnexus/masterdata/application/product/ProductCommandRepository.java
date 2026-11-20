package com.appnexus.masterdata.application.product;
import java.util.*;
/** 商品主档固定存取输出端口；主档、迁移回执、历史保全及引用事实受完整用例控制，不开放SQL或自由表名。 */
public interface ProductCommandRepository {
 /** createProduct固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param code 创建时固定业务编码，1至64字符，创建后不可修改
  * @param name 业务名称，去首尾空白后1至200字符，必填
  * @param shortName 简称，最多100字符；null沿用原缺省语义
  * @param category 当前租户分类UUID文本，可按原草稿契约缺省
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @param form 原商品形式枚举PHYSICAL、SERVICE或COMPOSITE，可按草稿契约缺省
  * @param unit 当前租户基础单位UUID文本；必填性由商品形式与固定配置决定
  * @param description 描述，最多20000字符；null沿用原缺省语义
  * @param brand 品牌文本，最多200字符；null不伪造业务值
  * @param origin 原产地说明，最多200字符；null沿用原缺省语义
  * @param barcode 条码，最多500字符；更新null保留原值
  * @param organization 已授权当前租户启用组织UUID；新建必填，原更新null保留归属
  * @param creator 原可信创建账号UUID文本；不允许客户端指定或覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，与业务写入同事务
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int createProduct(String id,String tenant,String code,String name,String shortName,String category,String configuration,String form,String unit,String description,String brand,String origin,String barcode,String organization,String creator,String updater);
 /** updateMainFields固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param name 业务名称，去首尾空白后1至200字符，必填
  * @param shortName 简称，最多100字符；null沿用原缺省语义
  * @param description 描述，最多20000字符；null沿用原缺省语义
  * @param brand 品牌文本，最多200字符；null不伪造业务值
  * @param origin 原产地说明，最多200字符；null沿用原缺省语义
  * @param barcode 条码，最多500字符；更新null保留原值
  * @param organization 已授权当前租户启用组织UUID；新建必填，原更新null保留归属
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int updateMainFields(String name,String shortName,String description,String brand,String origin,String barcode,String organization,String tenant,String id);
 /** approvalStates固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> approvalStates(String tenant,String product);
 /** setProductStatus固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param status 原商品生命周期目标状态，必须经过领域转换校验
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int setProductStatus(String status,String tenant,String product);
 /** changeStructureTarget固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param category 当前租户分类UUID文本，可按原草稿契约缺省
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @param form 原商品形式枚举PHYSICAL、SERVICE或COMPOSITE，可按草稿契约缺省
  * @param unit 当前租户基础单位UUID文本；必填性由商品形式与固定配置决定
  * @param disposition 原历史规格处置枚举，按原迁移/复核规则验证；null保持原历史事实
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int changeStructureTarget(String category,String configuration,String form,String unit,String disposition,String tenant,String product);
 /** retireSpecification固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param specification 同一父商品规格UUID，已校验退出或退役资格
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int retireSpecification(String actor,String tenant,String product,String specification);
 /** changeSpecificationMode固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param mode 规格模式NONE、STANDARD或CUSTOM，按原用例转换约束
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int changeSpecificationMode(String mode,String tenant,String product);
 /** disableComponents固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int disableComponents(String actor,String tenant,String product);
 /** saveMigrationResult固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param requestKey 已校验的规范UUID幂等键，同租户、账号及操作内唯一
  * @param digest 原规范内容的摘要；图片为SHA-256字节，迁移为64位十六进制文本
  * @param productVersion 冻结或结果商品版本，非负；在同一聚合事务内确定
  * @param structureRevision 结果结构修订号，非负；与迁移回执同事务保存
  * @param changeId 同事务追加的商品历史UUID文本
  * @param resultJson 服务端产生的迁移结果JSON，与父修改及幂等回执原子提交
  * @param creator 原可信创建账号UUID文本；不允许客户端指定或覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，与业务写入同事务
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int saveMigrationResult(String id,String tenant,String actor,String product,String requestKey,String digest,long productVersion,long structureRevision,String changeId,String resultJson,String creator,String updater);
 /** serviceUnitRequired固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Boolean serviceUnitRequired(String tenant,String configuration);
 /** requiredItems固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<String> requiredItems(String tenant,String configuration);
 /** categoryAdmission固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param category 当前租户分类UUID文本，可按原草稿契约缺省
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> categoryAdmission(String tenant,String category);
 /** templateAdmission固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> templateAdmission(String tenant,String configuration);
 /** unitAdmission固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param unit 当前租户基础单位UUID文本；必填性由商品形式与固定配置决定
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> unitAdmission(String tenant,String unit);
 /** deleteChoices固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int deleteChoices(String tenant,String product);
 /** deleteValues固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int deleteValues(String tenant,String product);
 /** reviewLegacyDisposition固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param disposition 原历史规格处置枚举，按原迁移/复核规则验证；null保持原历史事实
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int reviewLegacyDisposition(String disposition,String tenant,String product);
 /** lockLegacySource固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> lockLegacySource(String tenant,String product);
 /** stagedCount固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Integer stagedCount(String tenant,String product);
 /** stageLegacyProduct固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int stageLegacyProduct(String tenant,String product);
 /** legacyReconciliation固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param size 分页条数，1至100，无截断放行
  * @param offset 从0开始的行偏移，非负，与同条件COUNT配套
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> legacyReconciliation(String tenant,int size,long offset);
 /** legacyCount固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Long legacyCount(String tenant);
 /** pendingApprovalCount固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Integer pendingApprovalCount(String tenant,String product);
 /** attributeDefinition固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param configuration 分类固定已发布配置UUID文本，可按原草稿契约缺省；不自动升级
  * @param item 固定配置模板项UUID，不接受任意列或字段路径
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> attributeDefinition(String tenant,String configuration,String item);
 /** validOptionCount固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param revision 固定属性修订UUID，必须属于当前租户和配置
  * @param option 原修订下启用选项UUID，禁止跨修订引用
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Integer validOptionCount(String tenant,String revision,String option);
 /** archive固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param archived 目标草稿归档状态，true归档、false恢复；不删除正文
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int archive(String tenant, String id, boolean archived);
 /** replay固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param key 规范UUID幂等键，来自已校验原请求
  * @param lock 原写事务是否持有行锁，默认调用方明确传入；不向客户端开放
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> replay(String tenant, String actor, String id, String key, boolean lock);
}
