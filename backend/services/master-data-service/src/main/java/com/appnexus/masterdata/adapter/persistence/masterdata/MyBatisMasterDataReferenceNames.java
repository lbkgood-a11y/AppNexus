package com.appnexus.masterdata.adapter.persistence.masterdata;

import com.appnexus.contracts.operation.ReferenceNames;
import com.appnexus.contracts.operation.ReferenceNames.Key;
import com.appnexus.masterdata.adapter.persistence.MasterDataReferenceNameMapper;
import com.appnexus.masterdata.api.masterdata.MasterDataReferenceNames;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.masterdata.adapter.persistence.ProductAccess;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 主数据模块批量定位名称，沿用原租户、对象范围和目录权限。 */
@Service
public final class MyBatisMasterDataReferenceNames implements MasterDataReferenceNames {
    private static final String UNIT_READ="masterdata.productunit:read";
    private final MasterDataReferenceNameMapper mapper;
    private final PartnerDataAccess partner;
    private final ProductAccess product;
    private final StandardCrudScope warehouse;
    private final AccessAuthorization authorization;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 自有表名称投影 @param partner 客商读取范围 @param product 商品读取范围
     * @param planning 公共权限计划 @param rows 公共行范围 @param references 权限引用目录
     * @param authorization 权威功能准入 @param gate 单位目录许可 @param manager 宿主数据库事务管理器 */
    public MyBatisMasterDataReferenceNames(MasterDataReferenceNameMapper mapper,PartnerDataAccess partner,
            ProductAccess product,AccessPlanning planning,RowAccessControl rows,DataAccessReferences references,
            AccessAuthorization authorization,ScopedWriteGate gate,PlatformTransactionManager manager){
        this.mapper=mapper;
        this.partner=partner;
        this.product=product;
        this.warehouse=new StandardCrudScope(planning,rows,references,MyBatisMasterDataRelations.warehouseMapping(""));
        this.authorization=authorization;
        this.gate=gate;
        this.transaction=new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public Map<Key,String> load(VerifiedTenantContext context,List<Key> keys){
        if(keys.size()>ReferenceNames.MAX_BATCH)throw new IllegalArgumentException("REFERENCE_BATCH_TOO_LARGE");
        var ids=new HashMap<String,Set<String>>();
        for(Key key:keys){
            String id=UUID.fromString(key.value()).toString();
            ids.computeIfAbsent(key.type(),ignored->new LinkedHashSet<>()).add(id);
        }
        return read(context,ids);
    }

    /** 按对象类型执行固定批量查询，原对象拒绝时整个类型不可见。 */
    private Map<Key,String> read(VerifiedTenantContext context,Map<String,Set<String>> ids){
        var result=new HashMap<Key,String>();
        var subject=new AccessAuthorization.Subject(context.accountId(),context.tenantId(),context.memberId(),
            context.personId(),context.organizationId());
        String tenant=context.tenantId().toString();
        if(ids.containsKey(WAREHOUSE)||ids.containsKey(ACTIVE_WAREHOUSE)||ids.containsKey(AREA)||ids.containsKey(LOCATION)){
            RowAccessControl.Predicate scope;
            try{scope=warehouse.predicate(warehouse.plan(subject,"read"));}
            catch(SecurityException denied){scope=null;}
            if(scope!=null){
                var requested=union(ids,WAREHOUSE,ACTIVE_WAREHOUSE);
                if(!requested.isEmpty())putActive(result,WAREHOUSE,ACTIVE_WAREHOUSE,mapper.warehouses(tenant,requested,scope));
                if(ids.containsKey(AREA))put(result,AREA,"name",mapper.areas(tenant,batch(ids,AREA),scope));
                if(ids.containsKey(LOCATION))put(result,LOCATION,"name",mapper.locations(tenant,batch(ids,LOCATION),scope));
            }
        }
        if(ids.containsKey(PARTNER)||ids.containsKey(ACTIVE_PARTNER)){
            var scope=visible(()->partner.compiled(partner.plan(context,"read")));
            if(scope!=null)putActive(result,PARTNER,ACTIVE_PARTNER,mapper.partners(tenant,union(ids,PARTNER,ACTIVE_PARTNER),scope));
        }
        if(ids.containsKey(PRODUCT)||ids.containsKey(SPECIFICATION)||ids.containsKey(SKU)||ids.containsKey(SKU_SPECIFICATION)){
            var scope=visible(()->product.predicate(context,"read"));
            if(scope!=null){
                if(ids.containsKey(PRODUCT))put(result,PRODUCT,"name",mapper.products(tenant,batch(ids,PRODUCT),scope));
                if(ids.containsKey(SPECIFICATION))put(result,SPECIFICATION,"name",mapper.specifications(tenant,batch(ids,SPECIFICATION),scope));
                var skus=new LinkedHashSet<>(batch(ids,SKU));
                skus.addAll(batch(ids,SKU_SPECIFICATION));
                if(!skus.isEmpty()){
                    var loaded=mapper.skus(tenant,List.copyOf(skus),scope);
                    put(result,SKU,"sku_code",loaded);
                    put(result,SKU_SPECIFICATION,"name",loaded);
                }
            }
        }
        if(ids.containsKey(PRODUCT_UNIT)&&authorization.hasPermission(subject,UNIT_READ)){
            var units=transaction.execute(status->{
                gate.authorizeCatalogPublication(authorization,subject,UNIT_READ);
                return mapper.units(tenant,batch(ids,PRODUCT_UNIT));
            });
            put(result,PRODUCT_UNIT,"name",units);
        }
        result.keySet().removeIf(key->!ids.getOrDefault(key.type(),Set.of()).contains(key.value()));
        return Map.copyOf(result);
    }

    /** 权限拒绝返回空；持久化或服务故障应向调用方传播。 */
    private static <T> T visible(Supplier<T> read){
        try{return read.get();}
        catch(Rejection denied){if(denied.status==403)return null;throw denied;}
    }

    /** 将固定 SQL 投影转成公开引用键，空名称不进入展示结果。 */
    private static void put(Map<Key,String> target,String type,String field,List<Map<String,Object>> rows){
        for(var row:rows)if(row.get(field) instanceof String name&&!name.isBlank()){
            target.put(new Key(type,(String)row.get("id")),name);
        }
    }

    /** 历史读取和有效引用检查共享查询，但以不同键表达启用条件。 */
    private static void putActive(Map<Key,String> target,String type,String active,List<Map<String,Object>> rows){
        put(target,type,"name",rows);
        put(target,active,"name",rows.stream().filter(row->Boolean.TRUE.equals(row.get("enabled"))
            || row.get("enabled") instanceof Number value && value.intValue()!=0).toList());
    }

    private static List<String> union(Map<String,Set<String>> ids,String first,String second){
        var result=new LinkedHashSet<>(batch(ids,first));
        result.addAll(batch(ids,second));
        return List.copyOf(result);
    }

    private static List<String> batch(Map<String,Set<String>> ids,String type){
        return List.copyOf(ids.getOrDefault(type,Set.of()));
    }
}
