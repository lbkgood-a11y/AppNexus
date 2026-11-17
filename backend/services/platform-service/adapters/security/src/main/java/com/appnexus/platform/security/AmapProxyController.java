package com.appnexus.platform.security;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 高德 JS API 安全代理；仅向固定官方 HTTPS 上游发 GET，不转发 Cookie、认证头或任意 URL。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
public class AmapProxyController {
    private static final String PREFIX="/api/maps/_AMapService";
    private final TenantParameters parameters;
    private final Path secretDirectory;
    private final HttpClient client;
    // 每进程最多16个在途请求，防止地图并行下载耗尽业务线程；超出直接429，不排队。
    private final Semaphore permits=new Semaphore(16);

    /**
     * @param parameters 只读取可信租户登记的公开 Key，禁止客户端选择其他租户凭据
     * @param directory 安全密钥文件目录；默认空表示禁用，文件名为公开 Web Key，内容为对应 jscode；只允许服务账号读取
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AmapProxyController(TenantParameters parameters,@Value("${app.amap.secret-directory:}") String directory){
        this(parameters,directory,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    /** 测试注入受控传输；生产构造器固定 HTTPS 上游且禁止重定向。 */
    AmapProxyController(TenantParameters parameters,String directory,HttpClient client){this.parameters=parameters;this.secretDirectory=directory.isBlank()?null:Path.of(directory).toAbsolutePath().normalize();this.client=client;}

    /** 仅返回当前租户公开配置，提前复核服务端密钥可用性；密钥内容不进入响应。 */
    @GetMapping(PREFIX+"/configuration")
    @EndpointOperation("masterdata.warehouse:read")
    public ResponseEntity<java.util.Map<String,String>> configuration(HttpServletRequest request){
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))throw problem(409,"TENANT_REQUIRED");
        String key=parameters.list(context).stream().filter(p->p.key().equals("map.amap-key")).map(TenantParameters.Parameter::value).findFirst().orElse("");
        if(!key.matches("[a-fA-F0-9]{32}"))throw problem(503,"AMAP_WEB_KEY_NOT_CONFIGURED");
        readSecret(key);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("key",key,"serviceHost","/_AMapService"));
    }

    /**
     * 复用仓库读取权限，只供已授权租户地图使用；密钥缺失时503，不回显请求、上游正文或异常 URL。
     * @param request 可信租户上下文及 SDK GET 参数；key 必须匹配当前租户，jscode 一律由服务端覆盖
     * @return 限定大小的官方地图资源；禁止缓存，不向浏览器透传重定向及上游凭据
     */
    @GetMapping(PREFIX+"/**")
    @EndpointOperation("masterdata.warehouse:read")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request){
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))throw problem(409,"TENANT_REQUIRED");
        String key=parameters.list(context).stream().filter(p->p.key().equals("map.amap-key")).map(TenantParameters.Parameter::value).findFirst().orElse("");
        if(!key.matches("[a-fA-F0-9]{32}")||!key.equals(request.getParameter("key")))throw problem(403,"AMAP_KEY_DENIED");
        String path=request.getRequestURI().substring(request.getContextPath().length()+PREFIX.length());
        if(!allowedPath(path)||request.getQueryString()!=null&&request.getQueryString().length()>8192)throw problem(400,"AMAP_REQUEST_INVALID");
        String callback=request.getParameter("callback");
        if(callback!=null&&!callback.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))throw problem(400,"AMAP_REQUEST_INVALID");
        String secret=readSecret(key);
        if(!permits.tryAcquire())throw problem(429,"AMAP_PROXY_BUSY");
        try{
            StringBuilder query=new StringBuilder();
            request.getParameterMap().forEach((name,values)->{
                if(!name.equalsIgnoreCase("jscode")&&!name.equalsIgnoreCase("key"))for(String value:values){
                    if(!query.isEmpty())query.append('&');query.append(encode(name)).append('=').append(encode(value));
                }
            });
            if(!query.isEmpty())query.append('&');
            query.append("key=").append(encode(key)).append("&jscode=").append(encode(secret));
            var uri=URI.create(origin(path)+path+"?"+query);
            var upstream=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=upstream.body()){
                if(upstream.statusCode()!=200)throw problem(502,"AMAP_UPSTREAM_UNAVAILABLE");
                // 单响应上限8MiB，读取额外1字节识别超限，禁止无界分配。
                // 响应头到达后仍可能无限拖延正文；虚拟线程读取设置独立15秒期限，超时关闭流并释放并发槽。
                var reading=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                    try{return body.readNBytes(8*1024*1024+1);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                },task->Thread.startVirtualThread(task));
                byte[] bytes;
                try{bytes=reading.get(15,java.util.concurrent.TimeUnit.SECONDS);}
                catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException failure){reading.cancel(true);throw problem(502,"AMAP_UPSTREAM_UNAVAILABLE");}
                if(bytes.length>8*1024*1024||new String(bytes,StandardCharsets.UTF_8).contains(secret))throw problem(502,"AMAP_RESPONSE_INVALID");
                String type=upstream.headers().firstValue("Content-Type").orElse("application/octet-stream");
                // 高德 JSONP 上游仍标注 application/json；校验回调包装后纠正类型，保留 nosniff，不能全局放宽浏览器保护。
                if(callback!=null){
                    String script=new String(bytes,StandardCharsets.UTF_8).strip();
                    if(!script.startsWith(callback+"(")||!(script.endsWith(");")||script.endsWith(")")))throw problem(502,"AMAP_RESPONSE_INVALID");
                    type="application/javascript;charset=UTF-8";
                }
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(type)).body(bytes);
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw problem(502,"AMAP_UPSTREAM_UNAVAILABLE");}
        catch(java.io.IOException|IllegalArgumentException failure){throw problem(502,"AMAP_UPSTREAM_UNAVAILABLE");}
        finally{permits.release();}
    }

    /** 路径不接受编码、点段或双斜线；仅开放 JS API 官方安全代理使用的服务范围。 */
    static boolean allowedPath(String path){return path.matches("/(?:v3|v4)/[A-Za-z0-9_/-]+")&&!path.contains("//")&&!path.endsWith("/");}
    /** 样式和矢量瓦片使用专用上游，其余服务使用固定 REST 上游；不可由参数覆盖。 */
    static String origin(String path){return path.startsWith("/v4/map/styles")?"https://webapi.amap.com":path.startsWith("/v3/vectormap")?"https://fmap01.amap.com":"https://restapi.amap.com";}
    /** 每次读取支持密钥文件原子替换；不读取符号链接或在错误中暴露文件路径。 */
    private String readSecret(String key){
        if(secretDirectory==null)throw problem(503,"AMAP_PROXY_NOT_CONFIGURED");
        Path file=secretDirectory.resolve(key);
        try{
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>256)throw problem(503,"AMAP_PROXY_NOT_CONFIGURED");
            String value=Files.readString(file,StandardCharsets.UTF_8).trim();
            if(!value.matches("[a-fA-F0-9]{32}"))throw problem(503,"AMAP_PROXY_NOT_CONFIGURED");
            return value;
        }catch(java.io.IOException failure){throw problem(503,"AMAP_PROXY_NOT_CONFIGURED");}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static org.springframework.web.ErrorResponseException problem(int status,String code){return AccountSettingsController.problem(HttpStatus.valueOf(status),code,"地图服务暂不可用");}
}
