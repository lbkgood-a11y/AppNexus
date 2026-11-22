import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * 在组合应用构建前，使用 Java AST 和 MyBatis XML 检查新增 HTTP 入口及持久化语句。
 * 不加载 Spring、不执行 SQL，也不在检查时改写存量清单。
 */
class AccessBuildGate {
    private static final Set<String> ROUTES=Set.of("GetMapping","PostMapping","PutMapping","PatchMapping","DeleteMapping","RequestMapping");
    private static final Set<String> SQL=Set.of("Select","Insert","Update","Delete","SelectProvider","InsertProvider","UpdateProvider","DeleteProvider");
    private static final Pattern OPERATION_PAIR=Pattern.compile("\\boperation\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern OPERATION_DESCRIPTOR=Pattern.compile("\\bDescriptor\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern LOCAL_OPERATION=Pattern.compile("\\boperation\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern ENUM_VALUES=Pattern.compile("\\b([A-Z][A-Za-z0-9]*)\\.values\\(\\)");
    private static final Pattern ENUM_EXTRA=Pattern.compile("\\boperation\\s*\\(\\s*([A-Z][A-Za-z0-9]*)\\.OBJECT\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern CONDITIONAL_OBJECT=Pattern.compile("return\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\?\\s*([A-Z_]+)\\s*:\\s*([A-Z_]+)\\s*;");
    private static final Pattern QUALIFIED_OPERATION=Pattern.compile("\\boperation\\s*\\(\\s*([A-Za-z][A-Za-z0-9_.]*\\.[A-Z_]+)\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern KIND_DESCRIPTOR=Pattern.compile("return\\s*\\(\\)\\s*->\\s*descriptor\\(\\s*Kind\\.([A-Z_]+)");
    private static final Pattern KIND_OPERATION=Pattern.compile("\\boperation\\s*\\(\\s*kind\\s*,\\s*page\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern OPERATION_CODE=Pattern.compile("\\bOperation\\s*\\(\\s*\"([^\"]+)\"");
    private record MapperMethod(String owner,MethodTree method,String source) {}

    /** @param args 仓库根目录；第二个参数 --inventory 只输出待审核的现状清单。 */
    public static void main(String[] args) throws Exception {
        var root=Path.of(args[0]).toAbsolutePath();
        var java=sources(root,"java");
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var files=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)){
            var diagnostics=new DiagnosticCollector<JavaFileObject>();
            var task=(JavacTask)compiler.getTask(null,files,diagnostics,List.of("-proc:none"),null,files.getJavaFileObjectsFromPaths(java));
            var units=new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))
                throw new IllegalStateException("无法解析生产源码 "+diagnostics.getDiagnostics());
            var ids=operationIds(units);
            var sourcesByType=new HashMap<String,String>();
            for(var unit:units)for(var entry:unit.getTypeDecls())
                if(entry instanceof ClassTree type)sourcesByType.put(unit.getPackageName()+"."+type.getSimpleName(),type.toString());
            var xml=xmlStatements(sources(root,"xml"));
            var issues=new TreeMap<String,String>();
            for(var unit:units){
                checkDirectDatabaseAccess(unit,issues);
                new TreeScanner<Void,Void>(){
                    @Override public Void visitClass(ClassTree type,Void unused){
                        var anns=type.getModifiers().getAnnotations();
                        String owner=unit.getPackageName()+"."+type.getSimpleName();
                        for(var member:type.getMembers()){
                            if(!(member instanceof MethodTree method))continue;
                            if(has(anns,"RestController")||has(anns,"Controller"))
                                checkEndpoint(owner,method,ids,issues);
                            if(has(anns,"Mapper")||has(anns,"PlatformScoped")||has(anns,"TenantScoped")||has(anns,"TenantAggregateScoped")||method.getModifiers().getAnnotations().stream().anyMatch(a->SQL.contains(name(a))))
                                checkMapper(new MapperMethod(owner,method,type.toString()),xml,sourcesByType,issues);
                        }
                        return super.visitClass(type,unused);
                    }
                }.scan(unit,null);
            }
            for(var entry:xml.entrySet()){
                add(issues,"SQL_XML",entry.getKey(),entry.getValue(),"XML 语句没有对应 Mapper 方法");
            }
            if(args.length>1&&"--inventory".equals(args[1])){
                issues.keySet().forEach(System.out::println);
                return;
            }
            var baseline=new HashSet<>(Files.readAllLines(root.resolve("scripts/access-build-baseline.txt")));
            var reviewed=reviewedKeys(Files.readAllLines(root.resolve("scripts/access-build-reviewed.tsv")));
            if(!Collections.disjoint(baseline,reviewed))throw new IllegalStateException("已复核条目仍存在于待复核清单");
            var retained=new HashSet<>(baseline);
            retained.addAll(reviewed);
            verifyBaseline(root,retained);
            var failures=issues.entrySet().stream().filter(e->!retained.contains(e.getKey())).toList();
            if(!failures.isEmpty()){
                failures.forEach(e->System.err.println(e.getKey()+" "+e.getValue()));
                throw new IllegalStateException("新增或变更的权限入口未通过构建门禁，共 "+failures.size()+" 项");
            }
            System.out.println("权限构建门禁通过。待复核 "+issues.keySet().stream().filter(baseline::contains).count()
                +" 项；已明确不适用 "+issues.keySet().stream().filter(reviewed::contains).count()+" 项。");
        }
    }

    /** @param lines 审核清单，每行精确指纹加不适用原因 @return 不可扩张的已审核指纹 */
    private static Set<String> reviewedKeys(List<String> lines){
        var keys=new HashSet<String>();
        for(String line:lines){
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] entry=line.split("\\t",2);
            if(entry.length!=2||entry[1].isBlank()||!keys.add(entry[0]))
                throw new IllegalStateException("无效的不适用审查记录");
        }
        return keys;
    }

    /** CI 只允许从原清单复核或移除，新增入口不可写入例外。 */
    private static void verifyBaseline(Path root,Set<String> baseline) throws Exception{
        String base=System.getenv("API_BASE_REF");
        if(base==null||base.isBlank()||base.matches("0+"))return;
        if(!base.matches("[a-fA-F0-9]{7,64}"))throw new IllegalStateException("权限门禁构建基准无效");
        var revision=new ProcessBuilder("git","cat-file","-e",base+"^{commit}")
            .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(revision.waitFor()!=0)throw new IllegalStateException("权限门禁无法读取构建基准 "+base);
        var process=new ProcessBuilder("git","show",base+":scripts/access-build-baseline.txt")
            .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var previous=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if(process.waitFor()!=0)return; // 首次引入清单的基准提交尚无该文件。
        var old=new HashSet<>(previous.lines().toList());
        var review=new ProcessBuilder("git","show",base+":scripts/access-build-reviewed.tsv")
            .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var reviewContent=new String(review.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if(review.waitFor()==0)old.addAll(reviewedKeys(reviewContent.lines().toList()));
        if(!old.containsAll(baseline))throw new IllegalStateException("权限门禁禁止增加存量例外");
    }

    private static List<Path> sources(Path root,String extension) throws IOException{
        try(var paths=Files.walk(root.resolve("backend"))){
            return paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith("."+extension))
                .filter(p->p.toString().replace('\\','/').contains("/src/main/")).sorted().toList();
        }
    }

    private static Set<String> operationIds(List<CompilationUnitTree> units){
        var ids=new HashSet<String>();
        var objectConstants=new HashMap<String,String>();
        var operationConstants=new HashMap<String,String>();
        var kindsByType=new HashMap<String,Map<String,String>>();
        for(var unit:units){
            new TreeScanner<Void,String>(){
                @Override public Void visitClass(ClassTree type,String enclosing){
                    String name=enclosing+"."+type.getSimpleName();
                    var kindNames=new HashMap<String,String>();
                    for(var member:type.getMembers()){
                        if(!(member instanceof VariableTree variable))continue;
                        String field=name+"."+variable.getName();
                        if(variable.getName().toString().matches("[A-Z_]+")){
                            if(variable.getInitializer() instanceof LiteralTree literal&&literal.getValue() instanceof String v)
                                objectConstants.put(field,v);
                            if(variable.getInitializer() instanceof NewClassTree initializer){
                                var arguments=initializer.getArguments();
                                if(initializer.getIdentifier().toString().endsWith("Operation")&&arguments.size()>4
                                    &&arguments.get(4) instanceof LiteralTree arg&&arg.getValue() instanceof String id)
                                    operationConstants.put(field,id);
                                if(type.getKind()==Tree.Kind.ENUM&&arguments.size()>=2
                                    &&arguments.get(1) instanceof LiteralTree arg&&arg.getValue() instanceof String object
                                    &&object.matches("[A-Z][A-Za-z0-9]*"))
                                    kindNames.put(variable.getName().toString(),object);
                            }
                        }
                    }
                    if(!kindNames.isEmpty())kindsByType.put(name,kindNames);
                    return super.visitClass(type,name);
                }
            }.scan(unit,unit.getPackageName().toString());
        }
        var enums=new HashMap<String,ClassTree>();
        for(var unit:units)for(var entry:unit.getTypeDecls())
            if(entry instanceof ClassTree type&&type.getKind()==Tree.Kind.ENUM)
                enums.merge(type.getSimpleName().toString(),type,(one,two)->one==two?one:null);
        for(var unit:units){
            for(var entry:unit.getTypeDecls()){
                if(!(entry instanceof ClassTree type))continue;
                var source=type.toString();
                boolean provider=type.getImplementsClause().stream().anyMatch(t->t.toString().matches(".*\\bProvider"));
                if(!provider){
                    var factories=type.getMembers().stream().filter(member->member instanceof MethodTree method
                        &&method.getReturnType()!=null&&method.getReturnType().toString().endsWith("Provider")
                        &&has(method.getModifiers().getAnnotations(),"Bean")).toList();
                    if(!factories.isEmpty())recognizeKindFactories(unit,factories,source,kindsByType,ids);
                    continue;
                }
                recognizeOperationConstants(unit,source,objectConstants,operationConstants,ids);
                recognizeEnumeratedOperations(source,enums,ids);
                var pair=OPERATION_PAIR.matcher(source);
                while(pair.find())ids.add(pair.group(1)+"."+pair.group(2));
                var descriptor=OPERATION_DESCRIPTOR.matcher(source);
                if(descriptor.find()){
                    var operation=OPERATION_CODE.matcher(source);
                    while(operation.find())ids.add(descriptor.group(1)+"."+operation.group(1));
                    boolean localHelper=type.getMembers().stream().anyMatch(member->member instanceof MethodTree method
                        &&method.getName().contentEquals("operation")&&method.getReturnType()!=null
                        &&method.getReturnType().toString().endsWith("Operation")&&!method.getParameters().isEmpty()
                        &&Set.of("code","operationCode").contains(method.getParameters().getFirst().getName().toString()));
                    if(localHelper){
                        var local=LOCAL_OPERATION.matcher(source);
                        while(local.find())ids.add(descriptor.group(1)+"."+local.group(1));
                    }
                }
            }
        }
        return ids;
    }

    /** 识别从公开契约引用的常量定义，避免按控制器名字猜测对象。 */
    private static void recognizeOperationConstants(CompilationUnitTree unit,String provider,
            Map<String,String> objects,Map<String,String> operations,Set<String> ids){
        var imports=new HashMap<String,String>();
        for(var statement:unit.getImports()){
            String imported=statement.getQualifiedIdentifier().toString();
            String shortName=imported.substring(imported.lastIndexOf('.')+1);
            imports.put(shortName,imported);
            if(statement.isStatic()&&shortName.equals("*")){
                String prefix=imported.substring(0,imported.length()-1);
                operations.forEach((field,id)->{
                    if(field.startsWith(prefix)&&token(provider,field.substring(prefix.length())))ids.add(id);
                });
            }
        }
        var call=QUALIFIED_OPERATION.matcher(provider);
        while(call.find()){
            String reference=call.group(1);
            int dot=reference.indexOf('.');
            String qualified=imports.getOrDefault(reference.substring(0,dot),reference.substring(0,dot))+reference.substring(dot);
            var object=objects.get(qualified);
            if(object!=null)ids.add(object+"."+call.group(2));
        }
        imports.forEach((alias,qualified)->{
            if(operations.containsKey(qualified)&&token(provider,alias))ids.add(operations.get(qualified));
        });
    }

    private static boolean token(String source,String identifier){
        return Pattern.compile("\\b"+Pattern.quote(identifier)+"\\b").matcher(source).find();
    }

    private static void recognizeKindFactories(CompilationUnitTree unit,List<? extends Tree> factories,String source,
            Map<String,Map<String,String>> kindsByType,Set<String> ids){
        if(!source.contains("Descriptor(kind.objectType")||!source.contains("new Operation(code"))return;
        var kind=unit.getImports().stream().map(i->i.getQualifiedIdentifier().toString())
            .filter(i->i.endsWith(".Kind")).findFirst().orElse("");
        var kinds=kindsByType.getOrDefault(kind,Map.of());
        for(var factory:factories){
            var binding=KIND_DESCRIPTOR.matcher(factory.toString());
            while(binding.find()){
                var object=kinds.get(binding.group(1));
                if(object==null)continue;
                var method=KIND_OPERATION.matcher(source);
                while(method.find())ids.add(object+"."+method.group(1));
            }
        }
    }

    /** 识别 Provider 实际调用的操作枚举和同一对象上的显式补充操作。 */
    private static void recognizeEnumeratedOperations(String provider,Map<String,ClassTree> enums,Set<String> ids){
        var references=ENUM_VALUES.matcher(provider);
        if(!provider.contains(".code()")||!provider.contains(".permission()"))return;
        while(references.find()){
            String enumName=references.group(1);
            var type=enums.get(enumName);
            if(type==null)continue;
            var names=new HashMap<String,String>();
            for(var member:type.getMembers())
                if(member instanceof VariableTree variable&&variable.getInitializer() instanceof LiteralTree literal
                    &&literal.getValue() instanceof String value)names.put(variable.getName().toString(),value);
            var objectMethod=type.getMembers().stream().filter(m->m instanceof MethodTree method
                &&method.getName().contentEquals("object")).findFirst().orElse(null);
            var conditional=CONDITIONAL_OBJECT.matcher(Objects.toString(objectMethod,""));
            String[] alternate=conditional.find() ? new String[]{names.get(conditional.group(2)),names.get(conditional.group(3))} : null;
            for(var member:type.getMembers()){
                if(!(member instanceof VariableTree variable)||!(variable.getInitializer() instanceof NewClassTree value))continue;
                var arguments=value.getArguments();
                if(arguments.isEmpty()||!(arguments.getFirst() instanceof LiteralTree first)||!(first.getValue() instanceof String code))continue;
                String object=names.get("OBJECT");
                if(code.matches("[A-Z][A-Za-z0-9]+")&&arguments.size()>=3
                        &&arguments.get(2) instanceof LiteralTree third&&third.getValue() instanceof String explicitCode){
                    object=code;
                    code=explicitCode;
                }else if(object==null&&alternate!=null&&arguments.getLast() instanceof LiteralTree last
                        &&last.getValue() instanceof Boolean selector){
                    object=alternate[selector?0:1];
                }
                if(object!=null&&code.matches("[a-z][A-Za-z0-9_]*"))ids.add(object+"."+code);
            }
            var extra=ENUM_EXTRA.matcher(provider);
            while(extra.find())
                if(extra.group(1).equals(enumName)&&names.containsKey("OBJECT"))
                    ids.add(names.get("OBJECT")+"."+extra.group(2));
        }
    }

    private static void checkDirectDatabaseAccess(CompilationUnitTree unit,Map<String,String> issues){
        // 连接类型只作为 MyBatis 拦截方法签名时，不构成直接数据库入口。
        String code=unit.getTypeDecls().toString().replaceAll("\\b(?:java\\.sql\\.)?Connection\\s*\\.\\s*class\\b","");
        var direct=unit.getImports().stream().map(Object::toString)
            .filter(i->i.contains("org.springframework.jdbc.core.JdbcTemplate")||i.contains("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
                ||(i.contains("java.sql.Connection")&&token(code,"Connection"))||i.contains("java.sql.DriverManager")).toList();
        if(!direct.isEmpty()){
            String owner=unit.getPackageName()+"."+Path.of(unit.getSourceFile().toUri()).getFileName();
            add(issues,"DATABASE",owner,direct+" "+unit.getTypeDecls(),"不得新增直接数据库入口；业务数据库操作必须使用受控 Mapper");
        }
    }

    private static void checkEndpoint(String owner,MethodTree method,Set<String> ids,Map<String,String> issues){
        var annotations=method.getModifiers().getAnnotations();
        var routes=annotations.stream().filter(a->ROUTES.contains(name(a))).toList();
        if(routes.isEmpty())return;
        var binding=annotations.stream().filter(a->name(a).equals("OperationBinding")).toList();
        var signature=owner+"#"+method.getName();
        if(binding.isEmpty()){
            add(issues,"HTTP",signature,method.toString(),"HTTP 入口缺少唯一操作引用");
            return;
        }
        for(var annotation:binding){
            var id=value(annotation,"id");
            if(!"true".equals(raw(annotation,"reference"))||id.isEmpty()||!ids.contains(id))
                add(issues,"HTTP_BINDING",signature,method+" "+annotation,"须引用唯一 Provider 中明确声明的对象与操作");
        }
    }

    private static void checkMapper(MapperMethod entry,Map<String,String> xml,Map<String,String> sourcesByType,Map<String,String> issues){
        var method=entry.method();
        var annotations=method.getModifiers().getAnnotations();
        String key=entry.owner()+"."+method.getName();
        var statement=xml.remove(key);
        var sqlAnnotations=annotations.stream().filter(a->SQL.contains(name(a))).toList();
        if(statement==null&&sqlAnnotations.isEmpty())return;
        var guards=annotations.stream().filter(a->name(a).equals("GuardedScope")).toList();
        var fingerprint=method.getParameters()+" "+sqlAnnotations+" "+guards+" "+Objects.toString(statement,"");
        var parameters=new HashSet<String>();
        for(var parameter:method.getParameters()){
            var explicit=parameter.getModifiers().getAnnotations().stream().filter(a->name(a).equals("Param")).findFirst();
            if(explicit.isPresent()&&!explicit.get().getArguments().isEmpty()){
                var argument=explicit.get().getArguments().getFirst();
                if(argument instanceof AssignmentTree assignment)argument=assignment.getExpression();
                if(argument instanceof LiteralTree literal&&literal.getValue() instanceof String alias)parameters.add(alias);
            }else parameters.add(parameter.getName().toString());
        }
        if(guards.isEmpty()||guards.stream().anyMatch(a->!validGuard(a,parameters))
                ||guards.stream().noneMatch(a->value(a,"when").isEmpty())
                ||guards.stream().anyMatch(a->(!value(a,"predicate").isEmpty()||!value(a,"readFallback").isEmpty())&&!usesPublicBinder(entry,sqlAnnotations,statement,sourcesByType)))
            add(issues,"SQL",key,fingerprint,"真实 Mapper 语句缺少完整的统一权限门禁");
    }

    /** 检查真实 SQL 声明是否接入公共绑定；精确参数出现位置继续由运行时复核。 */
    private static boolean usesPublicBinder(MapperMethod entry,List<? extends AnnotationTree> annotations,String xml,
            Map<String,String> sourcesByType){
        if(xml!=null&&xml.contains("ScopedSql@bind")&&xml.contains("${"))return true;
        for(var annotation:annotations){
            if(annotation.toString().contains("ScopedSql@bind")&&annotation.toString().contains("${"))return true;
            if(!name(annotation).endsWith("Provider"))continue;
            String type=raw(annotation,"type");
            if(type.isEmpty())type=raw(annotation,"value");
            if(!type.endsWith(".class"))continue;
            type=type.substring(0,type.length()-6);
            String pkg=entry.owner().substring(0,entry.owner().lastIndexOf('.')+1);
            String provider=entry.source();
            for(String candidate=type;;candidate=candidate.substring(0,candidate.lastIndexOf('.'))){
                String match=sourcesByType.getOrDefault(candidate,sourcesByType.get(pkg+candidate));
                if(match!=null){provider=match;break;}
                if(!candidate.contains("."))break;
            }
            if(provider.contains("ScopedSql.bind("))return true;
        }
        return false;
    }

    private static boolean validGuard(AnnotationTree annotation,Set<String> parameters){
        if(!boundPath(value(annotation,"tenant"),parameters))return false;
        for(String key:List.of("predicate","readFallback","recordId","target","when")){
            var path=value(annotation,key);
            if(!path.isEmpty()&&!boundPath(path,parameters))return false;
        }
        int modes=0;
        if(!value(annotation,"predicate").isEmpty()){
            if(!validPath(value(annotation,"predicate"))||value(annotation,"resource").isEmpty())return false;
            modes++;
        }
        if(!value(annotation,"recordId").isEmpty()){
            if(!validPath(value(annotation,"recordId"))||value(annotation,"resource").isEmpty()||(raw(annotation,"actions").isEmpty()||raw(annotation,"actions").equals("{}")||raw(annotation,"actions").equals("\"\"")))return false;
            modes++;
        }
        if(!raw(annotation,"operationPermissions").isEmpty()&&!raw(annotation,"operationPermissions").equals("{}"))modes++;
        if(!value(annotation,"catalogPublication").isEmpty())modes++;
        if(!value(annotation,"serviceOperation").isEmpty()){
            var keys=annotation.getArguments().stream().filter(a->a instanceof AssignmentTree assignment
                &&assignment.getVariable().toString().equals("serviceKeys")).findFirst().orElse(null);
            if(!(keys instanceof AssignmentTree binding)||!(binding.getExpression() instanceof NewArrayTree array)
                || array.getInitializers()==null || array.getInitializers().isEmpty()
                ||array.getInitializers().stream().anyMatch(key->!(key instanceof LiteralTree literal)
                    ||!(literal.getValue() instanceof String path)||!boundPath(path,parameters)))return false;
            modes++;
        }else if(!raw(annotation,"serviceKeys").isEmpty())return false;
        if(!value(annotation,"readFallback").isEmpty()&&(value(annotation,"recordId").isEmpty()
                ||!value(annotation,"predicate").isEmpty()||!value(annotation,"target").isEmpty()||!raw(annotation,"parentKey").isEmpty()))return false;
        return modes==1&&(value(annotation,"when").isEmpty()==value(annotation,"whenValue").isEmpty());
    }

    private static boolean boundPath(String value,Set<String> parameters){
        return validPath(value)&&parameters.contains(value.split("\\.",2)[0]);
    }
    private static boolean validPath(String value){return value.matches("[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)*");}
    private static String name(AnnotationTree annotation){
        var name=annotation.getAnnotationType().toString();
        return name.substring(name.lastIndexOf('.')+1);
    }
    private static boolean has(List<? extends AnnotationTree> annotations,String name){
        return annotations.stream().anyMatch(a->name(a).equals(name));
    }
    private static String raw(AnnotationTree annotation,String key){
        for(var argument:annotation.getArguments())
            if(argument instanceof AssignmentTree assignment&&assignment.getVariable().toString().equals(key))
                return assignment.getExpression().toString();
        return "";
    }
    private static String value(AnnotationTree annotation,String key){
        String raw=raw(annotation,key);
        return raw.startsWith("\"")&&raw.endsWith("\"")?raw.substring(1,raw.length()-1):"";
    }

    private static Map<String,String> xmlStatements(List<Path> paths) throws Exception{
        var result=new TreeMap<String,String>();
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        var builder=factory.newDocumentBuilder();
        for(var path:paths){
            String source=Files.readString(path);
            if(!source.contains("<mapper"))continue;
            var root=builder.parse(new InputSource(new StringReader(source))).getDocumentElement();
            if(!root.getTagName().equals("mapper"))continue;
            var elements=root.getChildNodes();
            var fragments=new HashMap<String,Element>();
            for(int i=0;i<elements.getLength();i++)
                if(elements.item(i) instanceof Element e&&e.getTagName().equals("sql"))fragments.put(e.getAttribute("id"),e);
            for(int i=0;i<elements.getLength();i++){
                if(!(elements.item(i) instanceof Element e)||!Set.of("select","insert","update","delete").contains(e.getTagName()))continue;
                String key=root.getAttribute("namespace")+"."+e.getAttribute("id");
                var output=new StringBuilder();
                xmlShape(e,output,fragments,new HashSet<>());
                if(result.putIfAbsent(key,output.toString())!=null)throw new IllegalStateException("重复 Mapper 语句 "+key);
            }
        }
        return result;
    }

    private static void xmlShape(org.w3c.dom.Node node,StringBuilder output,Map<String,Element> fragments,Set<String> expansion){
        if(node instanceof Element e){
            output.append(e.getTagName());
            var attrs=e.getAttributes();
            for(int i=0;i<attrs.getLength();i++)output.append(attrs.item(i));
            if(e.getTagName().equals("include")){
                String ref=e.getAttribute("refid");
                if(fragments.containsKey(ref)&&expansion.add(ref)){
                    xmlShape(fragments.get(ref),output,fragments,expansion);
                    expansion.remove(ref);
                }
            }
        }else if(node.getNodeType()==org.w3c.dom.Node.TEXT_NODE||node.getNodeType()==org.w3c.dom.Node.CDATA_SECTION_NODE){
            output.append(node.getNodeValue().trim().replaceAll("\\s+"," "));
        }
        for(var child=node.getFirstChild();child!=null;child=child.getNextSibling())xmlShape(child,output,fragments,expansion);
    }

    private static void add(Map<String,String> issues,String kind,String target,String fingerprint,String reason){
        try{
            var digest=MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            issues.put(kind+" "+target+" "+HexFormat.of().formatHex(digest),reason);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
