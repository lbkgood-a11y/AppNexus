package com.appnexus.contracts.operation;

import java.util.*;
import java.util.function.Function;

/** 响应契约引用字段的批量名称解析，存储标识与结果名称分别保留。 */
public final class ReferenceNames {
    /** 每次提供方调用最多接受的不同引用数。 */
    public static final int MAX_BATCH = 200;

    private ReferenceNames() {}

    /** 引用类型及标识；字典等复合标识由提供方按公开契约编码。 */
    public record Key(String type, String value) {}

    /** @param context 当前可信主体，远程适配器须传播可验证的调用身份
     * @param keys 单批所需的唯一引用，最多 200 项
     * @return 当前主体可见的名称；缺失键表示不可用 */
    @FunctionalInterface
    public interface Source<C> {
        Map<Key,String> load(C context, List<Key> keys);
    }

    /** @param type 名称所属目录类型
     * @param identifier 从授权响应记录取得标识，不读取自由请求路径 */
    public record Field<R>(String type, Function<R,String> identifier) {
        /** @param row 已获准使用的记录 @return 非空引用键，无引用时为空 */
        public Key key(R row) {
            String value=identifier.apply(row);
            return value==null || value.isBlank()?null:new Key(type,value);
        }
    }

    /** @param source 名称提供方 @param context 可信上下文 @param keys 契约收集的引用
     * @return 去重分批后的映射，无法解析的键对应空值 */
    public static <C> Map<Key,String> resolveKeys(Source<C> source,C context,Collection<Key> keys){
        var result=new HashMap<Key,String>();
        load(source,context,new LinkedHashSet<>(keys),result);
        return result;
    }

    /** 按同一提供方合并多个字段并去重分批，可复用请求内已查过的键（包括无结果的键）。
     * @param source 同进程或远程批量提供方
     * @param context 可信调用上下文
     * @param rows 当前授权页或用于可见性判定的受控候选批
     * @param fields 响应契约声明
     * @param resolved 只在本请求内使用的结果，空值表示已确认无结果 */
    public static <C,R> void resolve(Source<C> source,C context,Collection<R> rows,
            List<Field<R>> fields,Map<Key,String> resolved) {
        var keys=new LinkedHashSet<Key>();
        for(R row:rows) for(var field:fields){
            Key key=field.key(row);
            if(key!=null&&!resolved.containsKey(key))keys.add(key);
        }
        load(source,context,keys,resolved);
    }

    private static <C> void load(Source<C> source,C context,Set<Key> keys,Map<Key,String> resolved){
        var pending=List.copyOf(keys);
        for(int start=0;start<pending.size();start+=MAX_BATCH){
            var batch=pending.subList(start,Math.min(start+MAX_BATCH,pending.size()));
            var loaded=source.load(context,batch);
            for(Key key:batch)resolved.put(key,loaded.get(key));
        }
    }
}
