package com.appnexus.contracts.printing;
import java.util.*;
/** 业务构建提供的打印字段目录；只含字段元数据和合成样本，不含真实单据原文。 */
public interface PrintDataContractProvider {
  /** 格式复用现有打印目录 code/nameZh/nameEn/fields；同编码冲突拒绝，禁止远程数据源。 */
  List<Map<String,Object>> contracts();
}
