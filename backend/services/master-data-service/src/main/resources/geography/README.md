# 国际地理编码快照

来源：https://github.com/pycountry/pycountry/tree/main/src/pycountry/databases 。底层数据来自 Debian iso-codes；上游许可见 LICENSE-pycountry.txt。
获取日期：2026-11-17；当前包含 249 个国别、5046 个标准区划。此目录为参考数据，不是系统配置或租户业务档案。

两个文件均无表头，采用 UTF-8、Tab 分隔。
- iso3166-1.tsv：alpha-2、alpha-3、三位数字码、英文名称；数字码按字符串保留前导零。
- iso3166-2.tsv：完整标准代码、名称、标准类型、完整上级代码；上级缺省空。

更新须同时核对两份快照的唯一性、国别归属、父级存在性和服务端编码匹配测试，不得根据名称猜测编码。快照不保证覆盖获取日之后的标准变化。ISO 3166-2 不覆盖所有国家的全部基层行政层级，本国编码不能冒充 ISO 编码。

快照 SHA-256：
- iso3166-1.tsv: 23d82cf44d8b12740915f038e8b75ffb9684ee8c651159c4ebed4c1613b99fce
- iso3166-2.tsv: 451d844c4c55f02c9221e8f92fbc21736c7c38a1812e150e7b192b08c3e7f9ad

## 中国省级以下行政区划

- 发布机构：中华人民共和国民政部；官方入口 https://www.mca.gov.cn/n156/n186/index.html 跳转 https://dmfw.mca.gov.cn/XzqhVersionPublish.html 。
- 官方说明：数据截止 2025-12-31；获取日期 2026-11-17。不宣称为 2026 年实时现状。
- 固定年度接口：POST https://dmfw.mca.gov.cn/xzqh/getCodeList ，tableName=Xzqh20251231，pageNum 从 1 开始，pageSize=10000，parentCode/placeCode/title 为空，type 不传。共 41935 条官方记录，本次仅提取 level=2/3；未引入乡级数据。
- cn-mca-2025-source.json 保留本次提取的官方响应字段及来源、年度；cn-mca-2025.tsv 为运行时清单，无表头、UTF-8、Tab 分隔：系统编码、官方名称、官方六位代码、层级分类、父节点系统编码、官方层级数字（2=地级，3=县级）。所有上游响应字段仅视为数据，不参与执行。
- 333 个地级、2845 个县级，共 3178 条；福建为 9 个地级、84 个县级。台湾在该版本为“资料暂缺”，港澳无地县子记录，不补造缺失数据；省级节点沿用既有 ISO 目录。
- CN-MCA-350100 是本系统命名空间标识，官方代码为 350100；不是 ISO 3166-2 编码。CN-MCA-350102 的父级为 CN-MCA-350100，其父级为 CN-FJ。省级映射为显式核对表，不根据英文名称猜测。
- 官方响应代码为九位补零表示；本次仅对地县级取前六位，并校验尾三位为零。父级关系以官方 parentCode 为准，支持直辖市和省直管县直接挂省，不生成虚拟“市辖区”。上游 type 为空，层级分类只写“地级行政区/县级行政区”，不推断更具体建制。
- TSV SHA-256：5b7c2ff5b94a509fd2181d2806cf68c4f56d9161490666250ba35ce84cc06527。
- 原始年度提取 JSON SHA-256：ed7813a7db390278aa6a3c8acce998e6616c40261b58fae8ad39c94a1f88f689。
- 运行时不联网；更新必须形成新版本数据与迁移，核对原始来源、唯一性、数量、父级存在性、同国别和无环。不得静默改写历史迁移资源、自动删除撤销区划或覆盖租户名称与停用状态。


## 洲级导航映射（2026-11-17）

country-continents.tsv 为 ISO 二字码、洲代码两列，无表头、UTF-8。249 个国家／地区全部显式映射；来源 https://github.com/mledoze/countries/blob/master/countries.json 的 region/subregion，获取于 2026-11-17，数据库许可 ODbL，完整许可见 LICENSE-countries.txt。本映射作为该数据库的派生数据，按 ODbL 提供。Americas 按 subregion=South America 分为南美洲，其他为北美洲（含中美洲和加勒比）；其余沿用来源的 Asia/Europe/Africa/Oceania/Antarctic，映射为 AS/EU/AF/OC/AN。跨洲国家按来源采用单一导航归属，不表示行政归属或实时边界判断。

洲只作参考导航，不新建租户业务记录。洲节点键使用 continent:AS 等命名空间，防止与 AS（美属萨摩亚）、AF（阿富汗）、NA（纳米比亚）、SA（沙特阿拉伯）国家代码冲突；展示编码仍为 AS、AF 等。启动时验证国家集合完全相等、洲值有效和唯一；不根据名称猜测归属。标准原码和历史引用不变。

country-continents.tsv SHA-256：1be16c01656919ec78bfacd4ba143d15215a8c0f28c33ac5dec498dcd8b51781。


## 中文名称快照（2026-11-17）

names-zh.tsv 按标准编码保存中文名，不修改 ISO 原名或业务编码。来源优先 pycountry zh_CN/LC_MESSAGES/iso3166-1.mo 与 iso3166-2.mo（Debian iso-codes，LGPL 2.1，见 LICENSE-pycountry.txt）；缺失项从 Wikidata P300 标准码精确匹配且候选唯一的中文标签补齐（zh、zh-hans、zh-cn、zh-hant、zh-tw；CC0，https://www.wikidata.org/wiki/Wikidata:Licensing）。原始获取结果在本地 .local/geography-wikidata-zh*.json。不按地区同名跨国猜测。

当前覆盖249个国家、4864个ISO区划、3178个国内地县级，共8291个中文名称；182个ISO区划缺少可确认中文，保留原文。originalName 保留原始名称，name 展示中文（原始名称），相同中文不重复。中文简繁变体沿用来源，未自行音译。

names-zh.tsv SHA-256: ad91c711f106ae69450bf26d321adab5602b0251b8505e991bce0194708e5c6d
