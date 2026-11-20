package com.appnexus.printing.adapter.persistence;
import java.util.*;
import com.appnexus.printing.application.PrintOutputRepository;
/** 固定打印存储适配，语义参数与业务端口一致，SQL位置绑定仅存在技术边界。 */
public final class MyBatisPrintOutputRepository implements PrintOutputRepository {
 private final PrintOutputsMapper mapper;
 /** @param mapper 原固定打印SQL映射，参与宿主同连接事务 */
 public MyBatisPrintOutputRepository(PrintOutputsMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> businessScene(String tenant,String sceneCode,String contract){return PrintStorageValues.stable(mapper.businessScene(tenant,sceneCode,contract));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> ownDocument(String tenant,String document,String actor){return PrintStorageValues.stable(mapper.ownDocument(tenant,document,actor));}
 /** {@inheritDoc} */
 public Boolean sceneReady(String tenant,String scene){return mapper.sceneReady(tenant,scene);}
 /** {@inheritDoc} */
 public Boolean rendererReady(String tenant){return mapper.rendererReady(tenant);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> enabledDevice(String tenant,String device){return PrintStorageValues.stable(mapper.enabledDevice(tenant,device));}
 /** {@inheritDoc} */
 public Boolean deviceQueueOnline(String tenant,String site,String queue){return mapper.deviceQueueOnline(tenant,site,queue);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> externalScene(String tenant,String sceneCode){return PrintStorageValues.stable(mapper.externalScene(tenant,sceneCode));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTenantOutputs(String tenant){return PrintStorageValues.stable(mapper.lockTenantOutputs(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingRequest(String tenant,String owner,String request){return PrintStorageValues.stable(mapper.existingRequest(tenant,owner,request));}
 /** {@inheritDoc} */
 public int createOutput(String tenant,String id,String owner,String request,String digest,String mode,String job,String device,int copies){return mapper.createOutput(tenant,id,owner,request,digest,mode,job,device,copies);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> outputStatus(String tenant,String id){return PrintStorageValues.stable(mapper.outputStatus(tenant,id));}
 /** {@inheritDoc} */
 public Boolean rendererOnline(String tenant){return mapper.rendererOnline(tenant);}
 /** {@inheritDoc} */
 public Map<String,Object> confirmedJob(String tenant,String job){return PrintStorageValues.stable(mapper.confirmedJob(tenant,job));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockOutput(String tenant,String id){return PrintStorageValues.stable(mapper.lockOutput(tenant,id));}
 /** {@inheritDoc} */
 public int confirmOutput(String job,String tenant,String id){return mapper.confirmOutput(job,tenant,id);}
}
