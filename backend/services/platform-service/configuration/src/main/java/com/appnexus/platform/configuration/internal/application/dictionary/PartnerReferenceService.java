package com.appnexus.platform.configuration.internal.application.dictionary;
import com.appnexus.platform.configuration.*;
import com.appnexus.platform.configuration.port.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** 合作方引用用例；客户端权威锁、字典项锁、幂等与业务引用共同提交或回滚。 */
public final class PartnerReferenceService implements PartnerReferences {
 private final PartnerReferenceRepository records;private final PartnerClientAuthority authority;private final PartnerReferenceTransaction transaction;
 /** @param records 固定存取端口 @param authority 当前客户端资格 @param transaction 同连接完整事务 */
 public PartnerReferenceService(PartnerReferenceRepository records,PartnerClientAuthority authority,PartnerReferenceTransaction transaction){this.records=records;this.authority=authority;this.transaction=transaction;}
 public Reference create(Context c,String key,Creation input){
  if(key==null||!key.matches("[A-Za-z0-9._:-]{16,128}")||input==null||!DictionaryValueStandard.validCode(input.dictionaryCode())||!valid(input.itemValue(),100)||!valid(input.referenceKey(),200))throw new Rejection(400,"INVALID_PARTNER_INPUT");
  return transaction.required(()->{
   requireAuthority(c);
   byte[] hash=hash(input);var existing=records.lockRequest(c.client(),key,c.tenant());
   if(!existing.isEmpty()){var row=existing.getFirst();if(!MessageDigest.isEqual(hash,(byte[])row.get("request_hash")))throw new Rejection(409,"IDEMPOTENCY_CONFLICT");return new Reference(UUID.fromString((String)row.get("id")),(String)row.get("reference_key"));}
   var items=records.lockItem(c.tenant(),c.tenant(),input.dictionaryCode(),input.itemValue(),c.client());if(items.size()!=1)throw new Rejection(404,"DICTIONARY_ITEM_UNAVAILABLE");
   String item=(String)items.getFirst().get("id");UUID id=UUID.randomUUID();records.registerUsage(c.tenant(),item,c.client(),input.referenceKey());records.saveRequest(c.client(),key,hash,id.toString(),c.tenant(),item,input.referenceKey());return new Reference(id,input.referenceKey());
  });
 }
 public Reference get(Context c,UUID id){return transaction.required(()->{
  requireAuthority(c);
  var rows=records.findReference(id.toString(),c.client(),c.tenant());
  if(rows.isEmpty())throw new Rejection(404,"REFERENCE_NOT_FOUND");
  return new Reference(id,(String)rows.getFirst().get("reference_key"));
 });}
 /** 直接调用输出用例也要复核机密客户端的权威版本，先于读取引用记录。 */
 private void requireAuthority(Context c){
  if(c==null||c.client()==null||c.tenant()==null||c.authorityVersion()==null
      ||records.lockAuthority(c.client(),c.tenant(),c.authorityVersion()).isEmpty()||!authority.authorizeReference(c.client(),c.tenant()))
    throw new Rejection(401,"INVALID_CLIENT_AUTHORITY");
 }
 private boolean valid(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.codePoints().noneMatch(Character::isISOControl);}
 private byte[] hash(Creation input){try{return MessageDigest.getInstance("SHA-256").digest((input.dictionaryCode()+"\u0000"+input.itemValue()+"\u0000"+input.referenceKey()).getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
