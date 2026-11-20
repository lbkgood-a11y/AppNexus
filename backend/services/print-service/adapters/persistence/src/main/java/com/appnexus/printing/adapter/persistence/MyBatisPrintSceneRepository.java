package com.appnexus.printing.adapter.persistence;
import java.util.*;
import com.appnexus.printing.application.PrintSceneRepository;
/** 固定打印存储适配，语义参数与业务端口一致，SQL位置绑定仅存在技术边界。 */
public final class MyBatisPrintSceneRepository implements PrintSceneRepository {
 private final PrintFlowMapper mapper;
 /** @param mapper 原固定打印SQL映射，参与宿主同连接事务 */
 public MyBatisPrintSceneRepository(PrintFlowMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> scenes(String tenant){return PrintStorageValues.stable(mapper.scenes(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockScene(String tenant,String scene){return PrintStorageValues.stable(mapper.lockScene(tenant,scene));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> sceneTemplate(String tenant,String template){return PrintStorageValues.stable(mapper.sceneTemplate(tenant,template));}
 /** {@inheritDoc} */
 public int createScene(String tenant,String id,String code,String name,String contract,String template,int copies,boolean enabled,String actor){return mapper.createScene(tenant,id,code,name,contract,template,copies,enabled,actor);}
 /** {@inheritDoc} */
 public int updateScene(String code,String name,String contract,String template,int copies,boolean enabled,String actor,String tenant,String id,long revision){return mapper.updateScene(code,name,contract,template,copies,enabled,actor,tenant,id,revision);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> ownDocuments(String tenant,String actor){return PrintStorageValues.stable(mapper.ownDocuments(tenant,actor));}
 /** {@inheritDoc} */
 public int createDocument(String tenant,String id,String scene,String number,String content,String actor){return mapper.createDocument(tenant,id,scene,number,content,actor);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> ownDocument(String tenant,String id,String actor){return PrintStorageValues.stable(mapper.ownDocument(tenant,id,actor));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> publishedTemplate(String tenant,String template){return PrintStorageValues.stable(mapper.publishedTemplate(tenant,template));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> snapshotTemplate(String tenant,String template){return PrintStorageValues.stable(mapper.snapshotTemplate(tenant,template));}
 /** {@inheritDoc} */
 public List<String> contractDefinitions(String tenant){return PrintStorageValues.stable(mapper.contractDefinitions(tenant));}
 /** {@inheritDoc} */
 public int createContract(String tenant,String code,String content,String actor){return mapper.createContract(tenant,code,content,actor);}
 /** {@inheritDoc} */
 public int deleteAgentQueues(String tenant,String site,String agent){return mapper.deleteAgentQueues(tenant,site,agent);}
 /** {@inheritDoc} */
 public int reportAgentQueue(String tenant,String site,String agent,String queue){return mapper.reportAgentQueue(tenant,site,agent,queue);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> queues(String tenant){return PrintStorageValues.stable(mapper.queues(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> enabledScene(String tenant,String scene){return PrintStorageValues.stable(mapper.enabledScene(tenant,scene));}
}
