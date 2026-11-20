package com.appnexus.masterdata.application.product;
import java.util.*;
/** 商品图片存取输出端口；固定可信租户与父商品，写入必须参与原聚合事务。 */
public interface ProductImageRepository {
 /** list固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> list(String tenant,String product);
 /** count固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 Integer count(String tenant,String product);
 /** insert固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param bytes 规范化PNG字节，8字节至5MiB，已移除原始元数据
  * @param digest 原规范内容的摘要；图片为SHA-256字节，迁移为64位十六进制文本
  * @param width 规范PNG宽度，单位像素，1至4096
  * @param height 规范PNG高度，单位像素，1至4096；总像素不超过16000000
  * @param creator 原可信创建账号UUID文本；不允许客户端指定或覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，与业务写入同事务
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int insert(String id,String tenant,String product,byte[] bytes,byte[] digest,int width,int height,String creator,String updater);
 /** detach固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int detach(String actor,String tenant,String product,String id);
 /** advanceParent固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param actor 认证上下文实际账号UUID文本，必填；不得替换为被代表人员
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param version 当前非负父商品CAS版本，成功更新必须恰好一行
  * @return 原固定事实或受影响行数；CAS写入非1必须拒绝，不视作成功
  */
 int advanceParent(String actor,String tenant,String product,long version);
 /** content固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param product 当前租户商品UUID文本，必填；读取和写入先经对应操作授权
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> content(String tenant, String product, String id);
 /** parent固定存取；写入、父CAS、审计及回执参与同一用例事务，失败由调用方整笔回滚。
  * @param tenant 服务端可信租户UUID文本，必填；不得由请求体自报
  * @param id 本操作固定资源UUID文本，必填；不能作为越权凭据
  * @param lock 原写事务是否持有行锁，默认调用方明确传入；不向客户端开放
  * @return 授权范围内原固定投影；未命中不查询其他租户
  */
 List<Map<String,Object>> parent(String tenant, String id, boolean lock);
}
