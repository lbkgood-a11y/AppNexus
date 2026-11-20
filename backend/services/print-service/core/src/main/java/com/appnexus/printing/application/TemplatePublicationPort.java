package com.appnexus.printing.application;

import com.appnexus.contracts.printing.PrintContext;
import java.util.Map;

/** 模板发布输出端口；同一模板行锁、不可变版本与发布指针必须处于调用方完整事务。 */
public interface TemplatePublicationPort {
  /** @param revision 当前草稿版本 @param published 当前发布版本，可空 @param contract 数据契约稳定编码 */
  record Draft(long revision,Integer published,String contract) {}
  /** @param context 可信主体 @param id 模板 UUID @return 锁定草稿事实；不存在按原协议拒绝 */
  Draft lock(PrintContext context,String id);
  /** @param context 可信主体 @param id 已锁模板 UUID @param contract 草稿契约编码 @return 校验草稿字段引用后的契约快照，可空；不读业务原文 */
  String validatedSchema(PrintContext context,String id,String contract);
  /** @param context 可信主体 @param id 已锁模板 UUID @param version 新发布正整数版本 @param schema 已验证 JSON 契约快照，可空；追加版本与更新发布指针共同提交 */
  void publish(PrintContext context,String id,int version,String schema);
  /** @param context 可信主体 @param id 模板 UUID @return 原授权响应投影，不要求额外设计或读取权限 */
  Map<String,Object> result(PrintContext context,String id);
}
