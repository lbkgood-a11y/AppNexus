package com.appnexus.platform.persistence;

import java.util.regex.Pattern;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;

/** 在 MyBatis 生成参数映射前，将固定查询中的范围位置替换为平台谓词绑定。 */
public final class ScopedQueryLanguageDriver extends XMLLanguageDriver {
    private static final Pattern SCOPE = Pattern.compile(
        "/\\*appnexus-bind:([a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)*)\\*/");

    /**
     * @param configuration 当前 MyBatis 配置
     * @param script 固定 Mapper 注解或 SQL Provider 的查询脚本
     * @param parameterType Mapper 参数类型
     * @return 在缓存键生成前完成范围参数绑定的 SQL 源
     */
    @Override
    public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {
        if (!SCOPE.matcher(script).find()) {
            return super.createSqlSource(configuration, script, parameterType);
        }
        String trimmed = script.stripLeading();
        String xml = trimmed.startsWith("<script>") ? trimmed
            : "<script><![CDATA[" + script.replace("]]>", "]]]]><![CDATA[>") + "]]></script>";
        var parser = new XPathParser(xml, false, configuration.getVariables());
        return createSqlSource(configuration, parser.evalNode("/script"), parameterType);
    }

    /**
     * @param configuration 当前 MyBatis 配置
     * @param script 已展开 include 的固定 XML Mapper 语句
     * @param parameterType Mapper 参数类型
     * @return 保留原动态分支与参数顺序的 SQL 源
     */
    @Override
    public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType) {
        Node query = script.getNode();
        expand(query, 0);
        return super.createSqlSource(configuration, script, parameterType);
    }

    private static int expand(Node parent, int index) {
        var document = parent.getOwnerDocument();
        for (Node child = parent.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> index = expand(child, index);
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    String sql = child.getNodeValue();
                    var marker = SCOPE.matcher(sql);
                    int offset = 0;
                    while (marker.find()) {
                        String path = marker.group(1);
                        String name = "_appnexusRowScope" + index++;
                        parent.insertBefore(document.createTextNode(sql.substring(offset, marker.start())), child);
                        var binding = document.createElement("bind");
                        binding.setAttribute("name", name);
                        binding.setAttribute("value", "@com.appnexus.persistence.mybatis.ScopedSql@bind("
                            + path + ".sql(), " + path + ".parameters(), \"" + path + "\")");
                        parent.insertBefore(binding, child);
                        parent.insertBefore(document.createTextNode(" ${" + name + "} "), child);
                        offset = marker.end();
                    }
                    if (offset > 0) child.setNodeValue(sql.substring(offset));
                }
                default -> { }
            }
            child = next;
        }
        return index;
    }
}
