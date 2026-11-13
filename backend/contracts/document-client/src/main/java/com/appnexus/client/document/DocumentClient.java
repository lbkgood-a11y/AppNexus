package com.appnexus.client.document;

import com.appnexus.contracts.document.Documents.*;
import com.appnexus.contracts.printing.BusinessPrinting;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/** 业务远程客户端；Token 由宿主提供，不存储密钥、不自动重试写请求、不跟随重定向。 */
public final class DocumentClient {
  private final URI base;private final Supplier<String> token;private final Duration timeout;
  private final HttpClient http;private final ObjectMapper json=new ObjectMapper();
  /**
   * @param base HTTPS 服务根地址，无查询/片段/用户信息；本机回环 HTTP 仅用于开发
   * @param token 返回当前短期 Bearer Token；分别需 document:resolve 和 print:submit scope
   * @param timeout 单次超时，正数且最多 120 秒；连接超时固定不超过该值
   */
  public DocumentClient(URI base,Supplier<String> token,Duration timeout){
    if(base==null||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null
      ||!(base.getScheme().equals("https")||(base.getScheme().equals("http")&&Set.of("localhost","127.0.0.1","[::1]").contains(base.getHost())))
      ||!(base.getPath().isEmpty()||base.getPath().equals("/")))throw new IllegalArgumentException("SDK_BASE_INVALID");
    if(timeout==null||timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofSeconds(120))>0)throw new IllegalArgumentException("SDK_TIMEOUT_INVALID");
    this.base=base;this.token=Objects.requireNonNull(token);this.timeout=timeout;
    http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
  }
  /** 解析非敏感依据；租户与服务身份从 Token 获取，不能通过 SDK 参数覆盖。 */
  public Resolution resolve(String requestId,String type,String action,String object,long basis,Map<String,String> facts){
    return post("/api/documents/integration/resolutions",requestId,Map.of("typeCode",type,"actionCode",action,"objectId",object,"basisVersion",basis,"facts",facts),Resolution.class);
  }
  /** 依据已固定的打印解析构造请求，避免业务调用者重复维护场景与投影契约。数据仍须由业务授权取数。 */
  public Map<String,Object> printResolved(String requestId,Resolution resolved,Map<String,Object> authorizedData,String mode,String deviceId,int copies){
    if(!"PRINT".equals(resolved.service())||resolved.sceneCode()==null||resolved.dataContract()==null)throw new IllegalArgumentException("SDK_PRINT_RESOLUTION_REQUIRED");
    return print(requestId,new BusinessPrinting.Request(resolved.sceneCode(),resolved.dataContract(),resolved.typeCode()+":"+resolved.objectId(),authorizedData,mode,deviceId,copies));
  }
  /** 查询本客户端输出，404 不向调用者泄露其他主体请求。 */
  @SuppressWarnings("unchecked") public Map<String,Object> output(String outputId){
    UUID.fromString(outputId);return get("/api/printing/external/outputs/"+outputId,Map.class);
  }
  /** 对固定预览显式确认；保持原请求键，不自动执行实体打印重试。 */
  @SuppressWarnings("unchecked") public Map<String,Object> confirm(String requestId,String outputId,String deviceId){
    UUID.fromString(outputId);var body=new LinkedHashMap<String,Object>();body.put("deviceId",deviceId);
    return post("/api/printing/external/outputs/"+outputId+"/confirm",requestId,body,Map.class);
  }
  /** 业务已授权打印数据通过现有外部打印入口提交；sceneCode 必须来自固定解析结果。 */
  @SuppressWarnings("unchecked") public Map<String,Object> print(String requestId,BusinessPrinting.Request input){
    var payload=new LinkedHashMap<String,Object>();payload.put("sceneCode",input.sceneCode());payload.put("dataContract",input.dataContract());payload.put("sourceId",input.sourceId());payload.put("data",input.data());payload.put("mode",input.mode());payload.put("deviceId",input.deviceId());payload.put("copies",input.copies());
    return post("/api/printing/external/outputs",requestId,payload,Map.class);
  }
  private <T>T post(String path,String key,Object payload,Class<T> type){
    UUID.fromString(key);String credential=token.get();if(credential==null||credential.isBlank()||credential.contains("\r")||credential.contains("\n"))throw new IllegalArgumentException("SDK_TOKEN_REQUIRED");
    try{
      var request=HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("Authorization","Bearer "+credential).header("Content-Type","application/json").header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
      var response=http.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200||response.statusCode()>=300){String code="REMOTE_REQUEST_FAILED";try{var node=json.readTree(response.body());if(node.has("code")&&node.get("code").asText().matches("[A-Z0-9_]{1,100}"))code=node.get("code").asText();}catch(Exception ignored){}throw new RemoteFailure(response.statusCode(),code);}
      return json.readValue(response.body(),type);
    }catch(RemoteFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RemoteFailure(503,"REMOTE_INTERRUPTED");}catch(Exception e){throw new RemoteFailure(503,"REMOTE_OUTCOME_UNKNOWN");}
  }
  private <T>T get(String path,Class<T> type){
    String credential=token.get();if(credential==null||credential.isBlank()||credential.contains("\r")||credential.contains("\n"))throw new IllegalArgumentException("SDK_TOKEN_REQUIRED");
    try{var response=http.send(HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("Authorization","Bearer "+credential).GET().build(),HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200)throw new RemoteFailure(response.statusCode(),"REMOTE_REQUEST_FAILED");return json.readValue(response.body(),type);
    }catch(RemoteFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RemoteFailure(503,"REMOTE_INTERRUPTED");}catch(Exception e){throw new RemoteFailure(503,"REMOTE_READ_UNAVAILABLE");}
  }
  /** 只暴露状态与安全错误码，不包含 Token、业务正文或远端堆栈；未知结果需原键查询/重放。 */
  public static final class RemoteFailure extends RuntimeException {private final int status;public RemoteFailure(int status,String code){super(code);this.status=status;}public int status(){return status;}}
}
