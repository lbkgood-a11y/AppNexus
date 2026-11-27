package com.appnexus.platform.authorization;

import java.util.*;

/**
 * 统一业务对象声明派生的数据权限只读投影，不再提供独立业务声明入口。
 * 对象身份、版本、操作和权限码由BusinessObjects.Descriptor生成；禁止表名、SQL或脚本。
 */
public final class DataAccessDeclaration {
    private DataAccessDeclaration() {}
    /** 业务执行适配器标记；装配校验确认资源存在真实执行器，行为仍由真实数据库反向测试保证。 */
    public interface Executor extends com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {}
    /**
     * 操作引用及实际支持的行范围。
     * @param operationId 已登记的对象操作标识
     * @param permissionCode 已登记的功能权限码，必须属于主资源
     * @param scopes 已执行且可配置的 ALL 或 RULES；空集合表示不配置行范围
     */
    public record Operation(String operationId,String permissionCode,Set<DataScopeAuthorization.ScopeType> scopes) {
        public Operation { scopes=Set.copyOf(scopes); }
    }
    /**
     * 字段保护能力；字段编码为资源内稳定业务键，不接受自由属性路径。
     * @param field 字段稳定编码
     * @param nameZh 中文业务名称，必填
     * @param nameEn 英文业务名称，必填
     * @param kind 固定脱敏语义类型，不根据字段名称猜测
     * @param modes 实际执行的限制模式；未声明能力不得配置
     */
    public record Field(String field,String nameZh,String nameEn,FieldSecurity.Kind kind,Set<FieldSecurity.Mode> modes) {
        public Field { modes=Set.copyOf(modes); }
    }
    /**
     * 数据资源；子资源只继承主资源行范围，不生成平行功能权限。
     * @param resource 全局稳定资源编码，小写点分段
     * @param parentResource 主资源为空串；子资源必须指向同声明内主资源
     * @param nameZh 中文名称
     * @param nameEn 英文名称
     * @param responseType 自动响应投影record完整类名；空串表示业务自行执行
     * @param fields 当前已接通的字段能力
     */
    public record Resource(String resource,String parentResource,String nameZh,String nameEn,List<Field> fields,String responseType) {
        /** 缺省不启用自动响应投影，不改变已有业务执行器。 */
        public Resource(String resource,String parentResource,String nameZh,String nameEn,List<Field> fields){this(resource,parentResource,nameZh,nameEn,fields,"");}
        public Resource {fields=List.copyOf(fields);Objects.requireNonNull(responseType);}
    }
    /**
     * 对象数据权限声明快照。
     * @param objectType 已登记业务对象标识
     * @param version 正整数声明版本；删除或改变字段语义必须同步迁移存量策略
     * @param scope 平台或租户管理域，不能跨域授权
     * @param resources 主资源在首位，后续子资源继承主资源行范围
     * @param operations 已执行的数据权限操作引用
     * @param dimensions 从统一对象声明派生的条件维度；不含SQL映射
     */
    public record Descriptor(String objectType,int version,PermissionCatalog.ScopeType scope,List<Resource> resources,List<Operation> operations,List<com.appnexus.contracts.businessobject.BusinessObjects.DataDimension> dimensions) {
        /** 旧声明缺省不开放条件字段。 */
        public Descriptor(String objectType,int version,PermissionCatalog.ScopeType scope,List<Resource> resources,List<Operation> operations){this(objectType,version,scope,resources,operations,List.of());}
        public Descriptor { resources=List.copyOf(resources);operations=List.copyOf(operations);dimensions=List.copyOf(dimensions); }
    }
}
