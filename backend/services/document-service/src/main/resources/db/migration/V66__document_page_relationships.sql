-- 平台维护页面操作与单据行为的完整关系集；无记录沿用代码登记关系，空集合显式关闭全部页面入口。
CREATE TABLE doc_page_relationships (
 type_code VARCHAR(64) NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0,
 relationships JSON NOT NULL,
 updated_by VARCHAR(128) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
