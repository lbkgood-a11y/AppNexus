package com.appnexus.masterdata.domain.product;

import com.appnexus.masterdata.MasterData.Rejection;
import java.util.Objects;

/** 商品生命周期规则；只判断领域状态，不读取数据库、审批引擎或认证上下文。 */
public final class ProductLifecycle {
  private ProductLifecycle() {}
  /**
   * 普通修改要求未归档且历史结构已处理；审批冻结由应用层通过公开事实另行检查。
   * @param archived 是否已归档，归档记录不得普通修改
   * @param structureState 当前结构状态，LEGACY_PENDING 必须先完成人工处置
   * @throws Rejection 领域状态不允许修改，错误码保持 PRODUCT_V1_CHANGED
   */
  public static void requireMutable(boolean archived,String structureState){if(archived||"LEGACY_PENDING".equals(structureState))throw changed();}
  /**
   * 验证显式启停转换；DRAFT 只允许启用，重复转换和归档记录拒绝。
   * @param status 当前 DRAFT、ENABLED 或 DISABLED 状态
   * @param target 已由输入校验限定的 ENABLED 或 DISABLED
   * @param archived 当前是否归档
   * @throws Rejection 当前状态不允许此转换
   */
  public static void requireTransition(String status,String target,boolean archived){if(archived||Objects.equals(status,target)||"DRAFT".equals(status)&&"DISABLED".equals(target))throw changed();}
  /** 只有草稿允许归档或恢复；重复目标不产生事件或推进审计。 */
  public static void requireArchive(String status,boolean archived,boolean target){if(!"DRAFT".equals(status)||archived==target)throw changed();}
  private static Rejection changed(){return new Rejection(409,"PRODUCT_V1_CHANGED");}
}
