package com.appnexus.platform;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 人工浏览器验收专用隔离宿主；仅在系统参数product.browser.qa=true时启动，默认跳过，使用一次性MySQL/Valkey与测试账号。
 * 不增加生产登录入口，不重置管理员密码；会话只写忽略目录，生命周期由停止标志控制。
 */
@Tag("browser-fixture")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="product.browser.qa",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"server.port=18089","app.public-url=http://localhost:18848","app.frontend-url=http://localhost:18848"})
@ActiveProfiles("local")
class ProductBrowserFixtureTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 @Autowired com.appnexus.masterdata.application.product.ProductCatalogService catalog;
 @Autowired com.appnexus.masterdata.application.product.ProductService products;
 @Autowired com.appnexus.masterdata.application.product.ProductAttributeService attributes;
 @Autowired com.appnexus.masterdata.application.product.ProductTemplateService templates;
 @Test void isolatedBrowserHost()throws Exception{
  UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品V1浏览器验收')",tenant.toString());
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'商品验收测试账号','unused')",account.toString(),"product-browser-"+account);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
  bootstrap.initialize(tenant,member);
  // 一次性真实目录种子仅用于隔离浏览器验收，全部通过领域命令，不写共享库或伪造接口返回。
  var trusted=new com.appnexus.platform.tenant.VerifiedTenantContext(account,tenant,member,1);
  var gram=catalog.createUnit(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.UnitInput("QA-G","验收克","MASS",3,0));
  catalog.createUnit(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.UnitInput("QA-KG","验收千克","MASS",3,0));
  var category=catalog.createCategory(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.CategoryInput("QA-EXT","扩展验收分类",null,0));
  var attribute=attributes.createAttribute(trusted,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("diameter",null,0)).get("id");
  var revision=attributes.createRevision(trusted,attribute,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("验收直径","DECIMAL",gram.id(),2,"{}",0)).get("id");attributes.publishRevision(trusted,revision,1);
  var template=templates.create(trusted,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("QA-SPEC","验收规格模板","SPEC",0)).get("id");
  var templateVersion=templates.createVersion(trusted,template,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("规格验收",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(attribute,revision,null,null,true,false,false,0)),0)).get("id");templates.publish(trusted,templateVersion,1);
  var slots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);slots.put("SPEC",templateVersion);
  var config=catalog.publish(trusted,category.id(),new com.appnexus.masterdata.api.product.ProductCatalogData.Publish(slots,false,0));
  products.create(trusted,new com.appnexus.masterdata.api.product.ProductMainData.Input("QA-EXT-P","扩展浏览器验收商品",null,category.id(),config.id(),"PHYSICAL",gram.id(),null,null,null,null));

  var session=sessionRepository.createSession();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(org.springframework.security.core.userdetails.User.withUsername(account.toString()).password("").authorities("ACCOUNT").build(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT"))));
  session.setAttribute("SPRING_SECURITY_CONTEXT",context);session.setAttribute("appnexus.credentialVersion",0L);
  session.setAttribute("appnexus.tenant",tenant.toString());session.setAttribute("appnexus.member",member.toString());session.setAttribute("appnexus.tenantVersion",1L);sessionRepository.save(session);
  String cookie=Base64.getEncoder().encodeToString(session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var directory=Path.of(System.getProperty("product.qa.dir"));Files.createDirectories(directory);
  Files.writeString(directory.resolve("session.json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("cookie",cookie,"tenant",tenant.toString())));
  try{long deadline=System.nanoTime()+java.time.Duration.ofMinutes(60).toNanos();while(System.nanoTime()<deadline&&!Files.exists(directory.resolve("stop")))Thread.sleep(1000);}
  finally{Files.deleteIfExists(directory.resolve("session.json"));sessionRepository.deleteById(session.getId());}
 }
}
