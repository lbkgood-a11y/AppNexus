package com.appnexus.bulkwarehousing.domain.process;

import java.util.List;
import java.util.Objects;

/** 模板选取及已绑定版本解析，规则按业务类型和作业方式精确匹配。 */
public final class WarehouseFlowCatalog {
  /**
   * @param business 业务类型
   * @param mode 作业方式，null 表示该业务的缺省规则
   * @param template 匹配时采用的版本化模板
   */
  public record Policy(String business, String mode, WarehouseFlowTemplate template) {
    public Policy {
      Objects.requireNonNull(business);
      Objects.requireNonNull(template);
    }
  }

  private final List<Policy> policies;
  private final List<WarehouseFlowTemplate> versions;

  /**
   * @param policies 已发布的模板匹配规则，精确作业方式优先于缺省规则
   * @param versions 可续办的已发布模板版本，含已停止新任务选取的历史版本
   */
  public WarehouseFlowCatalog(List<Policy> policies, List<WarehouseFlowTemplate> versions) {
    this.policies = List.copyOf(policies);
    this.versions = List.copyOf(versions);
  }

  /**
   * @param business 业务类型
   * @param mode 可信作业方式
   * @return 唯一匹配的已发布模板
   * @throws IllegalStateException 缺失或同优先级冲突时抛出
   */
  public WarehouseFlowTemplate select(String business, String mode) {
    var exact = policies.stream().filter(policy -> policy.business().equals(business)
        && policy.mode() != null && policy.mode().equals(mode)).map(Policy::template).toList();
    if (!exact.isEmpty()) return unique(exact);
    return unique(policies.stream().filter(policy -> policy.business().equals(business) && policy.mode() == null)
        .map(Policy::template).toList());
  }

  /**
   * @param code 已绑定的模板编码
   * @param version 已绑定版本
   * @return 原版本模板，即使新任务已使用其他版本
   * @throws IllegalStateException 找不到原版本或版本重复时抛出
   */
  public WarehouseFlowTemplate resume(String code, int version) {
    return unique(versions.stream().filter(template -> template.code().equals(code)
        && template.version() == version).toList());
  }

  private static WarehouseFlowTemplate unique(List<WarehouseFlowTemplate> matches) {
    if (matches.size() != 1) throw new IllegalStateException("仓储流程模板缺失或冲突");
    return matches.getFirst();
  }
}
