package com.appnexus.masterdata.domain.product;

import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import java.util.*;

/** 商品结构切换的纯领域策略；输入是应用在父锁下重读的事实，不读取预检缓存或客户端快照。 */
public final class ProductStructurePolicy {
  private ProductStructurePolicy() {}

  /**
   * 当前聚合事实；配置、单位与形态使用固定身份比较，不追踪 latest。
   * @param version 当前非负父记录版本
   * @param structureRevision 当前非负结构修订
   * @param configId 当前固定配置 UUID，可为历史未知 NULL
   * @param form 当前商品形态，可空
   * @param unitId 当前基础单位 UUID，可空
   * @param specificationMode 当前规格模式
   * @param status 当前草稿或启停状态
   * @param archived 已归档时不能切换结构
   * @param approvalPending 已提交审批时冻结结构
   * @param structureState 历史结构处置状态
   * @param legacyDisposition 已落库的旧规格处置结论，不接受客户端代填
   * @param activeSpecifications 当前未退役规格 ID 集合，切换结构必须全量确认退出
   */
  public record Source(long version,long structureRevision,String configId,String form,String unitId,
      String specificationMode,String status,boolean archived,boolean approvalPending,
      String structureState,String legacyDisposition,Set<String> activeSpecifications) {
    public Source { activeSpecifications=Set.copyOf(activeSpecifications); }
  }

  /**
   * 已通过格式校验的切换意图；源版本由应用与数据库事实比较，预检成功不能替代此检查。
   * @param sourceVersion 客户端读取的父版本，必填非负
   * @param sourceStructureRevision 客户端读取的结构修订，必填非负
   * @param sourceConfigId 客户端读取的源配置身份，NULL 必须与源一致
   * @param configId 目标固定配置 UUID，可空表示草稿未配置
   * @param form 目标形态，可空
   * @param unitId 目标单位 UUID，可空
   * @param specificationMode 目标规格模式，NULL 表示保持原模式
   * @param retiredSpecifications 明确确认退出的原规格 ID；缺省空集合
   * @param legacyDisposition 本次请求的历史处置确认，仍须匹配数据库既有确认
   */
  public record Change(long sourceVersion,long sourceStructureRevision,String sourceConfigId,
      String configId,String form,String unitId,String specificationMode,
      Set<String> retiredSpecifications,String legacyDisposition) {
    public Change { retiredSpecifications=Set.copyOf(retiredSpecifications); }
  }

  /**
   * 检查审批冻结、源快照、规格退出及生命周期条件；顺序保持原业务错误优先级。
   * @param source 父锁下读取的当前事实，禁止使用浏览器缓存构造
   * @param change 已校验格式的切换意图
   * @throws Rejection 源版本过期、审批中、规格退出不完整或状态不允许切换
   */
  public static void requireAllowed(Source source,Change change) {
    if(source.approvalPending())throw conflict("PRODUCT_APPROVAL_IN_PROGRESS");
    if(source.version()!=change.sourceVersion()||source.structureRevision()!=change.sourceStructureRevision()
        ||!Objects.equals(source.configId(),change.sourceConfigId()))throw conflict("PRODUCT_SOURCE_VERSION_CHANGED");
    boolean changed=!Objects.equals(source.configId(),change.configId())||!Objects.equals(source.form(),change.form())
        ||!Objects.equals(source.unitId(),change.unitId())
        ||change.specificationMode()!=null&&!Objects.equals(source.specificationMode(),change.specificationMode());
    if(changed&&!source.activeSpecifications().equals(change.retiredSpecifications()))throw conflict("PRODUCT_SPEC_EXIT_REQUIRED");
    if(!changed&&!change.retiredSpecifications().isEmpty())throw new Rejection(400,"PRODUCT_V1_INPUT_INVALID");
    if("ENABLED".equals(source.status())||source.archived())throw conflict("PRODUCT_STRUCTURE_REQUIRES_DISABLED");
    if("LEGACY_PENDING".equals(source.structureState())&&(!"CONFIRMED_NONE".equals(change.legacyDisposition())
        ||!"CONFIRMED_NONE".equals(source.legacyDisposition())))throw conflict("PRODUCT_LEGACY_SPEC_REQUIRED");
  }

  private static Rejection conflict(String code){return new Rejection(409,code);}
}
