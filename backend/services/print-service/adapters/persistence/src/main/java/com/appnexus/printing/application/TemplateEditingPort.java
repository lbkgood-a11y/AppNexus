package com.appnexus.printing.application;

import com.appnexus.contracts.printing.PrintContext;
import java.util.Map;

/** 模板编辑输出；JSON 安全验证及固定 SQL 留在适配器，所有写入参与完整编辑事务。 */
public interface TemplateEditingPort {
  /** @param code 模板稳定编码 @param name 显示名称 @param category 纸张类别 @param contract 数据契约编码 @param revision 原草稿版本，新建 -1 */
  record Draft(String code,String name,String category,String contract,long revision) {}
  /** 验证原 JSON 模板与范围，禁止执行性扩展；仅可在真实设计权限通过后调用。 */
  void prepare();
  /** @param value 原主体或对象标识 @return 验证 UUID 后的原值；原稳定错误码不变 */
  String identifier(String value);
  /** @param context 可信主体 @param id 新 UUID @param draft 已验证字段；创建审计来自可信账号及数据库 UTC */
  void create(PrintContext context,String id,Draft draft);
  /** @param context 可信主体 @param id 目标 UUID @param draft 原草稿版本与新内容 @return CAS 影响行数，零必须冲突，不覆盖创建信息 */
  int update(PrintContext context,String id,Draft draft);
  /** @param context 可信主体 @param id 目标 UUID @return 原读取权限下的模板响应；保留额外 read 权限要求 */
  Map<String,Object> result(PrintContext context,String id);
}
