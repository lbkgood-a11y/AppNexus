package com.appnexus.platform.businessobject.port;

import com.appnexus.contracts.businessobject.BusinessObjects.Binding;
import java.util.List;
import java.util.Map;

/** 版本化配置快照编解码端口；领域与应用不依赖 JSON 实现或数据库驱动对象。 */
public interface ConfigurationCodec {
  /**
   * 解析已保存的绑定快照；损坏数据必须拒绝，不能默认空配置。
   * @param snapshot 存储端口返回的非空 JSON 快照
   * @return 保持顺序的业务绑定集合
   */
  List<Binding> bindings(Object snapshot);

  /**
   * 解析入口禁用列表。
   * @param snapshot 平台管控记录中的 JSON，不接受客户端任意类型
   * @return 明确禁用的操作标识集合
   */
  List<String> operations(Object snapshot);

  /**
   * 解析幂等记录保存的条件事实。
   * @param snapshot 原解析事务保存的 JSON
   * @return 用于重放一致性核验的事实，不替代当前授权
   */
  Map<String, String> facts(Object snapshot);

  /**
   * 序列化已由应用校验的绑定、条件、关系或事实快照。
   * @param snapshot 业务快照，禁止凭据、技术对象或客户端任意类型
   * @return 与既有持久化协议一致的 JSON；失败向上传播使事务回滚
   */
  String encode(Object snapshot);
}
