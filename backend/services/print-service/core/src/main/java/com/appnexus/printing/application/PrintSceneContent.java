package com.appnexus.printing.application;
/** 场景内容固定编解码端口，业务字段和数据契约校验由纯核心执行。 */
public interface PrintSceneContent {
 /** @param value 固定公开契约值 @return JDK对象、集合及标量，缺省null保持null */
 Object plain(Object value);
 /** @param value 原已验证数据库JSON @return JDK对象、集合及标量，不解析Java多态类型 */
 Object decode(String value);
 /** @param value 经领域契约验证的快照 @return 原长度及可执行键限制下的存储JSON */
 String snapshot(Object value);
 /** @param value 经领域验证的固定对象元数据 @return 原限制下的对象JSON，null沿用原空对象缺省 */
 String object(Object value);
}
