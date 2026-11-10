package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 受控租户展示参数，显式权限/租户边界；配置不会修改业务日期、金额、会话或安全策略。 */
@Service
public class TenantParameters {
    /**
     * 参数定义与当前有效值。
     * @param key 受控参数编码，不接受任意系统配置
     * @param value 当前值，未配置时为明确默认值
     * @param defaultValue 默认值，不表示已持久化
     * @param description 相邻中文参数说明，覆盖用途、单位、默认及影响
     * @param version 未持久化为 -1，首次创建回传 -1；已有从零开始
     */
    public record Parameter(String key,String value,String defaultValue,String description,int version){}
    private static final Map<String,Parameter> DEFINITIONS=Map.of(
        "display.time-zone",new Parameter("display.time-zone","Asia/Shanghai","Asia/Shanghai","展示时间点使用的 IANA 时区，默认 Asia/Shanghai；只影响格式化，不修改业务日期、UTC 存储或授权有效期。",-1),
        "display.currency",new Parameter("display.currency","CNY","CNY","金额默认展示币种，ISO 4217 三位代码，默认 CNY；不执行汇率折算、不改业务金额及其原始币种。",-1),
        "display.page-size",new Parameter("display.page-size","20","20","列表建议每页条数，单位条，默认 20，范围 10–100；实际接口仍执行各自最大条数，修改不改变权限范围。",-1)
    );
    private final JdbcTemplate jdbc;
    private final AccessAuthorization authorization;
    private final TenantAccess tenants;
    public TenantParameters(JdbcTemplate jdbc,AccessAuthorization authorization,TenantAccess tenants){this.jdbc=jdbc;this.authorization=authorization;this.tenants=tenants;}
    /** 有效成员可读非敏感展示参数；没有配置的项返回定义默认值，不能伪造已保存版本。 */
    public List<Parameter> list(VerifiedTenantContext context){
        active(context);
        var rows=jdbc.queryForList("SELECT parameter_key,parameter_value,version FROM tenant_parameter WHERE tenant_id=UUID_TO_BIN(?)",context.tenantId().toString());
        Map<String,Map<String,Object>> values=new HashMap<>();rows.forEach(row->values.put((String)row.get("parameter_key"),row));
        return DEFINITIONS.values().stream().sorted(Comparator.comparing(Parameter::key)).map(definition->{var row=values.get(definition.key());return row==null?definition:new Parameter(definition.key(),(String)row.get("parameter_value"),definition.defaultValue(),definition.description(),((Number)row.get("version")).intValue());}).toList();
    }
    /**
     * 管理员按版本保存受控配置，与追加审计同事务；未知参数默认拒绝。
     * @param context 服务端可信账号、租户及成员
     * @param key 当前定义登记的稳定编码
     * @param value 参数值，校验时区/币种/数量范围，不接受密钥或脚本
     * @param version 首次 -1，后续当前版本，冲突 409 不覆盖
     * @return 更新后的参数含中文定义和新版本
     */
    @Transactional
    public Parameter save(VerifiedTenantContext context,String key,String value,int version){
        active(context);
        if(!authorization.hasPermission(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),"organization.permission:grant"))
            throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PARAMETER_MANAGEMENT_DENIED","无租户参数管理权限");
        var definition=DEFINITIONS.get(key);if(definition==null||value==null||version< -1)throw invalid();
        try{
            switch(key){
                case "display.time-zone"->{if(!java.time.ZoneId.getAvailableZoneIds().contains(value))throw invalid();}
                case "display.currency"->{if(!value.matches("[A-Z]{3}"))throw invalid();java.util.Currency.getInstance(value);}
                case "display.page-size"->{int size=Integer.parseInt(value);if(size<10||size>100||!Integer.toString(size).equals(value))throw invalid();}
                default->throw invalid();
            }
        }catch(IllegalArgumentException bad){throw invalid();}
        // 与租户停用共用租户行锁；成员状态再次复核，不能在失效上下文中写配置。
        if(jdbc.queryForList("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",context.tenantId().toString()).isEmpty())throw invalid();
        active(context);
        try{
            if(version==-1)jdbc.update("INSERT INTO tenant_parameter(tenant_id,parameter_key,parameter_value,updated_by) VALUES(UUID_TO_BIN(?),?,?,UUID_TO_BIN(?))",context.tenantId().toString(),key,value,context.accountId().toString());
            else if(jdbc.update("UPDATE tenant_parameter SET parameter_value=?,version=version+1,updated_by=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND parameter_key=? AND version=?",value,context.accountId().toString(),context.tenantId().toString(),key,version)!=1)throw conflict();
        }catch(org.springframework.dao.DuplicateKeyException duplicate){throw conflict();}
        int next=version+1;jdbc.update("INSERT INTO tenant_parameter_audit(tenant_id,actor_id,parameter_key,version) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",context.tenantId().toString(),context.accountId().toString(),key,next);
        return new Parameter(key,value,definition.defaultValue(),definition.description(),next);
    }
    private void active(VerifiedTenantContext context){if(context==null||tenants.available(context.accountId()).stream().noneMatch(value->value.tenantId().equals(context.tenantId())&&value.memberId().equals(context.memberId())))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"TENANT_UNAVAILABLE","租户不可访问");}
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","参数未登记或取值无效");}
    private org.springframework.web.ErrorResponseException conflict(){return AccountSettingsController.problem(HttpStatus.CONFLICT,"PARAMETER_CHANGED","参数已被其他管理员修改，请重新读取");}
}
