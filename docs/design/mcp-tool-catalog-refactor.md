# MCP 工具目录改造方案

- 状态：已评审，mcp-registry、agent-app 已实施
- 涉及项目：g2rain-mcp-registry、g2rain-agent-app、g2rain-agent-studio（技能工具绑定数据）
- 原则：开发阶段，不考虑存量数据，不做兼容设计；改完后重新同步 / 导入工具、重新绑定技能工具。

## 1. 目标

1. 工具名按统一、稳定的规则生成：`{sourcePrefix}_{serviceCode}_{httpMethod}_{normalizedPath}`。
2. 资源类型命名与工具名前缀统一。
3. 外部服务配置简化：去掉服务编码与上下文路径，客户只填名称、调用基址、说明。
4. 工具按服务 id 归属，页面按"服务 → 工具"组织。
5. 统一 HTTP 调用地址的拼接规则。
6. 修复 OpenAPI 3.1 文档展开后 `type` 丢失的问题。

## 2. 资源类型

`ResourceType` 改名，工具名前缀直接取枚举值小写：

| 枚举 | 原枚举 | 前缀 | 含义 | 可见范围 |
|---|---|---|---|---|
| `NATIVE` | `BUILT_IN` | `native_` | 谷雨原生服务 | 全局 |
| `EXTENSION` | `EXTENSION` | `extension_` | 运营公司接入的扩展系统 | 全局 |
| `CUSTOMER` | `CUSTOMER` | `customer_` | 租户自己接入的私有系统 | 本租户 |

说明：

- 不用 `builtin`，避免与 agent-runtime 的内置工具（`builtin_node_finish` 等）混淆。
- 用 `EXTENSION` 而不是 `EXTERNAL`：租户私有系统对谷雨同样是"外部"，`EXTERNAL` 会与 `CUSTOMER` 语义交叉；按"由谁提供、给谁用"划分，三者互斥。
- 可见范围由 `resource_type` 与 `organ_id` 判断，不解析工具名前缀。

## 3. 工具名规则

### 3.1 拼接

```
{sourcePrefix}_{serviceCode}_{httpMethod}_{normalizedPath}
```

| 段 | 取值 |
|---|---|
| `sourcePrefix` | `ResourceType` 小写 |
| `serviceCode` | OpenAPI `info.title` 规范化，不做任何前缀去除等特殊处理 |
| `httpMethod` | 小写 HTTP 方法 |
| `normalizedPath` | OpenAPI path 规范化 |

规范化沿用 `ToolNamingSupport` 现有规则，并做两处调整：

1. 驼峰边界插入 `_` 后转小写：`serviceCode` → `service_code`、`userId` → `user_id`（调整：现有实现不拆驼峰，得到 `servicecode`）。
2. `/`、`-`、`.` 及其他特殊字符转 `_`，连续 `_` 合并，去掉首尾 `_`。
3. 路径参数去掉花括号，只保留参数名：`{id}`、`{id:regex}` → `id`。
4. 总长上限 64（调整：现有为 256）。OpenAI 兼容接口要求函数名不超过 64 个字符，属于硬约束；超长时截断并拼接 `{uniquenessKey}` 的 SHA-256 前 8 位 hex（`ensureMaxLength`），结果稳定且唯一。

示例：

| 服务 title | 方法与路径 | 工具名 |
|---|---|---|
| `g2rain-basis` | `GET /user/identities` | `native_g2rain_basis_get_user_identities` |
| `g2rain-basis` | `POST /resource_api/{serviceCode}/import` | `native_g2rain_basis_post_resource_api_service_code_import` |
| `g2rain-knowledge` | `POST /knowledge/answer` | `native_g2rain_knowledge_post_knowledge_answer` |
| `erp` | `GET /orders/{id}` | `customer_erp_get_orders_id` |

- 不再使用 `operationId`：springdoc 默认取方法名，生成器方法名大量重名，自动追加的 `_1`、`_2` 后缀依赖扫描顺序，不稳定。
- 不再拼 contextPath。
- 工具名由规则确定生成，导入预览只展示，不允许编辑。

### 3.2 校验（导入 / 同步时）

| 校验 | 范围 | 失败时 |
|---|---|---|
| `info.title` 规范化后非空 | — | 拒绝导入，提示修改文档 title |
| 工具名不冲突 | 同一机构 + 同一资源类型（三类统一） | 拒绝导入，提示"该接口已由服务「XX」导入" |

- 跨资源类型不比较：三类前缀不同，不会重名。
- 去掉 `mcp_external_service.code` 后，两条服务记录导入同一份文档、title 相同、路径清洗后相同，最终都表现为工具名冲突，一次校验全部覆盖，不再单独校验"服务不重名"。
- 唯一索引 `uk_name`（`organ_id`、`resource_type`、`name`）与业务校验口径一致，作为并发导入的兜底。
- `organ_id` 取操作人当前机构（见第 10 节）：NATIVE 只由超管公司维护，EXTENSION 只由运营公司维护，各自机构固定，按机构校验即等价于该类型内唯一。前提：扩展工具的菜单权限只分配给运营公司一个租户。
- 校验范围（维护归属）与可见范围是两回事：NATIVE、EXTENSION 对所有租户可见，见 7.5 节。

### 3.3 重复导入

同一服务重复导入同一份（或更新后的）文档是更新，不是冲突：

| 情况 | 处理 |
|---|---|
| 同一 `service_id` 下 method + path 已存在 | 更新 `title`、`description`、`openapi_document`、`input_schema`；保留 `id`、`status`、`name` |
| 同名工具属于其他服务，或同一服务下的其他 method + path | 冲突，按 3.2 拒绝 |
| 新文档中已不存在、或本次未勾选的接口 | 不自动删除，由用户在工具管理中手动删除，避免误删技能正在使用的工具 |

## 4. 调用地址

统一一个拼接方法，导入预览与运行时调用共用：

```
URL = trimEnd(baseUrl, "/") + "/" + trimStart(path, "/")
```

| 资源类型 | baseUrl |
|---|---|
| `NATIVE` | basis `service_registry.endpoint`（`lb://` 直连服务，不拼 routePrefix） |
| `EXTENSION` / `CUSTOMER` | `mcp_external_service.endpoint`（客户填写的完整基址，含上下文路径） |

- 删除 `ServiceEndpointCache.Endpoint.callBase()` 中注释掉的 contextPath 拼接代码。
- 全链路去掉 contextPath 概念：`Endpoint` 只保留调用基址，`ToolOperationAssembler.assemble`、`OpenApiOperation` 去掉 contextPath 参数。

## 5. OpenAPI 序列化

问题：谷雨服务（springdoc）输出 OpenAPI 3.1，3.1 模型把类型存在 `types`；mcp-registry 全部用 `Json.mapper()`（3.0 序列化器）读写，`type` 丢失，工具入参只剩 `{}`。

方案：工具存"单接口小文档"，版本随数据自描述。

1. **存储**：`mcp_tool_profile.openapi_document` 存单接口小文档，即原文档根级字段（`openapi`、`info`、`servers`、`security` 等，不含 `tags`）+ 单个 path + method，`$ref` 已展开。basis 拆分出的原生接口本就是这种结构，三类工具存储一致。
2. **写入**：新增静态工具类，按文档版本选择序列化器，只在写入时使用：

```java
@NullMarked
public final class OpenApiJson {

    private OpenApiJson() {
    }

    public static ObjectMapper mapper(@Nullable SpecVersion version) {
        return version == SpecVersion.V31 ? Json31.mapper() : Json.mapper();
    }
}
```

3. **读取**：运行时把 `openapi_document` 交给 `OpenAPIParser` 解析，解析器按文档的 `openapi` 字段自动以 3.0 或 3.1 读取，不再手工选择序列化器，也不需要单独的版本列。
4. 工具只在启动与重载时加载，多一次解析不在调用热路径上。

约束：

- `ObjectMapper` 为 `com.fasterxml.jackson.databind.ObjectMapper`（swagger-core 依赖的 Jackson 2）。
- Swagger 2.0 由解析器转成 3.0，按 3.0 处理；3.0 与 3.1 保持原版本，不做 3.0 → 3.1 转换。
- 写入替换点：`OpenApiDocumentConverter`（resolve、单接口文档、inputSchema 序列化）、`ToolRegistry`；读取替换点：`ToolOperationAssembler` 改为解析小文档后取出唯一的 Operation。

## 6. 数据结构（DDL）

### 6.1 mcp_external_service

- 删除 `code`、`context_path`。
- `endpoint` 注释改为含上下文路径的完整基址。
- `resource_type` 注释改为新枚举。

```sql
CREATE TABLE `mcp_external_service` (
    `id` BIGINT NOT NULL COMMENT                                                                        '主键标识',
    `organ_id` BIGINT NOT NULL COMMENT                                                                  '机构标识',
    `name` VARCHAR(128) NOT NULL COMMENT                                                                '服务名称',
    `endpoint` VARCHAR(512) NOT NULL COMMENT                                                            '调用基址(http/https，含上下文路径，如 https://erp.example.com/api)',
    `resource_type` VARCHAR(32) NOT NULL COMMENT                                                        '资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT                                              '状态[ACTIVE:启用, INACTIVE:禁用]',
    `description` VARCHAR(500) DEFAULT NULL COMMENT                                                     '说明',
    `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT                                      '创建时间',
    `update_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT          '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT                                                            '记录版本',
    `delete_flag` TINYINT NOT NULL DEFAULT 0 COMMENT                                                    '删除标识[0:未删除, 1:已删除]',
    PRIMARY KEY (`id`),
    INDEX `idx_organ_type` (`organ_id`, `resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=                             'MCP外部服务表';
```

### 6.2 mcp_tool_profile

- `service_code` 改为 `service_id`：NATIVE → basis `service_registry.id`；其余 → `mcp_external_service.id`。服务编码（规范化 title）只在生成工具名时使用，不单独存储。
- `openapi_operation` 改为 `openapi_document`，存单接口小文档（见第 5 节），不单独存版本。
- `resource_type` 注释改为新枚举；`source_id` 注释改为 NATIVE。
- 工具名唯一：同一机构 + 同一资源类型内唯一，业务校验（3.2 节）与唯一索引 `uk_name` 口径一致。

```sql
CREATE TABLE `mcp_tool_profile` (
    `id` BIGINT NOT NULL COMMENT                                                                        '主键标识',
    `organ_id` BIGINT NOT NULL COMMENT                                                                  '机构标识',
    `name` VARCHAR(256) NOT NULL COMMENT                                                                'Tool名称(MCP tools/call name)',
    `title` VARCHAR(128) NOT NULL COMMENT                                                               'Tool标题(MCP title)',
    `resource_type` VARCHAR(32) NOT NULL COMMENT                                                        '资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]',
    `service_id` BIGINT NOT NULL COMMENT                                                                '所属服务(NATIVE→basis.service_registry.id; 其余→mcp_external_service.id)',
    `source_id` BIGINT DEFAULT NULL COMMENT                                                             '来源标识(仅NATIVE=basis.resource_api.id)',
    `method` VARCHAR(32) NOT NULL COMMENT                                                               'HTTP方法',
    `path` VARCHAR(512) NOT NULL COMMENT                                                                'HTTP路径',
    `openapi_document` JSON NOT NULL COMMENT                                                            '单接口OpenAPI文档(含openapi版本、info与单个path+method, $ref已展开)',
    `input_schema` JSON NOT NULL COMMENT                                                                'MCP inputSchema',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT                                              '状态[ACTIVE:启用, INACTIVE:禁用]',
    `description` VARCHAR(500) DEFAULT NULL COMMENT                                                     'Tool说明(给LLM)',
    `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT                                      '创建时间',
    `update_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT          '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT                                                            '记录版本',
    `delete_flag` TINYINT NOT NULL DEFAULT 0 COMMENT                                                    '删除标识[0:未删除, 1:已删除]',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name` (`organ_id`, `resource_type`, `name`, (IF(`delete_flag` = 0, 0, NULL))),
    INDEX `idx_service` (`resource_type`, `service_id`),
    INDEX `idx_source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=                             'MCP Tool暴露档案表';
```

DDL 已写入 `scripts/g2rain-mcp-registry.sql` 与 `g2rain-deploy/config/mysql/g2rain-mcp-registry.sql`，评审后执行。

### 6.3 CRUD 手工调整

不使用代码生成器重新生成：两张表的 Service、Mapper 已有大量手写扩展（导入、同步、启停、按服务删除、唯一校验等），重新生成会覆盖。按字段变更逐个文件手工调整：

| 文件 | mcp_external_service | mcp_tool_profile |
|---|---|---|
| `dao/po/*Po` | 删除 `code`、`contextPath` | `serviceCode` → `serviceId`；`openapiOperation` → `openapiDocument` |
| `dto/*Dto`（biz） | 同上；删除相关校验注解 | 同上 |
| `vo/*Vo`（api） | 同上 | 同上；另加非表字段 `serviceName`（原生取服务注册名称，外部取 `mcp_external_service.name`），由后端填充 |
| `dto/*SelectDto`（api） | 删除 `code`、`contextPath` 查询条件 | `serviceCode` → `serviceId`；删除 `openapiOperation` 查询条件（如有） |
| `converter/*Converter` | 检查显式 `@Mapping` 是否引用被删字段 | 同左 |
| `mybatis/mapper/*Mapper.xml` | `resultMap`、列清单、`insert`、`updateBody`、`commonWhereClause` 删除 `code`、`context_path` | 同处 `service_code` → `service_id`、`openapi_operation` → `openapi_document` |
| `dao/*Dao` | 删除按 `code` 查询的方法 | `deleteByServiceCodeAndOrganId` 改为按 `serviceId` |
| `api/*Api`、`controller/*Controller` | 接口签名随 DTO 变化 | 同左；删除 NATIVE 单条新增入口 |
| `service/*Service`、`service/impl/*ServiceImpl` | 删除 `code`、`contextPath` 相关校验与拼接 | 业务改造见第 7 节 |

## 7. mcp-registry 代码改造

### 7.1 命名

| 位置 | 调整 |
|---|---|
| `ResourceType` | `BUILT_IN` → `NATIVE` |
| `openapi/naming/EndpointStrategy` | 按 3.1 节规则重写：`build(resourceType, serviceCode, method, path)`；删除 `resolveServiceSegment`、`BUILT_IN_SEGMENT`、contextPath 参数 |
| `openapi/naming/OperationIdStrategy`、`NamingStrategy` | 删除；只保留一种规则，不再需要策略接口（`McpRegistryConfiguration` 中对应 Bean 一并删除） |
| `ToolNamingSupport` | 保留路径规范化与 `ensureMaxLength`；增加驼峰拆分；`MAX_TOOL_NAME_LENGTH` 改为 64；新增 title → serviceCode 规范化 |
| `OpenApiDocumentConverter` | 从 `info.title` 取 serviceCode 并校验；生成工具名不再依赖 contextPath；产出单接口小文档 |
| `McpToolImportServiceImpl.resolveToolName` | 改为调用新规则；删除 `resolveContextPath` |
| `ToolOperationAssembler` | 不再计算 serviceSegment |
| `McpToolNameGuard` / `countNameConflict` | 按 3.2、3.3 节：三类统一按"同一机构 + 同一资源类型"查同名工具，排除同一服务同一 method + path；删除现有跨类型比较（CUSTOMER 与 BUILT_IN、EXTENSION 互查）与共享工具的全局比较 |

### 7.2 服务归属与调用

| 位置 | 调整 |
|---|---|
| `McpToolProfilePo/Dto/Vo/SelectDto` | 见 6.3 节 |
| `McpExternalServicePo/Dto/Vo/SelectDto` | 删除 `code`、`contextPath` |
| `OpenApiPreviewDto` | `serviceCode`、`contextPath` → `serviceId` |
| `OpenApiConfirmDto` | `serviceCode` → `serviceId` |
| `BuiltInSyncDto` | 增加 `serviceId`（basis `service_registry.id`）；保留 basis 侧服务编码用于拉取 `resource_api` |
| `McpToolProfileDao.deleteByServiceCodeAndOrganId` | 改为按 `serviceId` 删除 |
| `ServiceEndpointCache` | 以 `service_registry.id` 为键；`Endpoint` 只保留调用基址；按 4 节拼接 |
| `BuiltInToolLoader` / `ExtensionToolLoader` | 按 `serviceId` 分组缓存、重载、下线；外部服务按 id 查 `mcp_external_service` |
| `RuntimeToolCatalog` | 外部服务地址按 `serviceId` 查询 |
| `ServiceRegistrySync` | 服务删除时按 id 下线 NATIVE 工具 |
| `McpExternalServiceServiceImpl` | 删除 `code`、`contextPath` 相关校验；服务删除时按 id 级联删除工具 |
| `McpToolProfileController` | 删除 NATIVE 的单条新增（NATIVE 只能同步）；EXTENSION / CUSTOMER 的列表、导入均带 `serviceId` |

### 7.3 序列化

按第 5 节：写入用 `OpenApiJson` 选择序列化器并存单接口小文档；读取用 `OpenAPIParser` 解析小文档，删除运行时对 `Json.mapper()` 的直接使用。

### 7.4 BuiltIn 命名统一为 Native

`ResourceType.BUILT_IN` 改为 `NATIVE` 后，代码中所有 BuiltIn 命名一并改为 Native，避免与 agent-runtime 内置工具（builtin）混淆：

| 原名 | 新名 |
|---|---|
| `BuiltInToolLoader` | `NativeToolLoader` |
| `BuiltInApiPermission`（及测试 `BuiltInApiPermissionTest`） | `NativeApiPermission`（`NativeApiPermissionTest`） |
| `BuiltInSyncDto` | `NativeSyncDto` |
| `McpToolImportServiceImpl.syncBuiltIn`、`loadBuiltInMap` | `syncNative`、`loadNativeMap` |
| `McpToolProfileApi/Controller.selectBuiltInList`、`selectBuiltInPage`、`saveBuiltIn` | `selectNativeList`、`selectNativePage`、`saveNative`（`/native/save`，只更新不新增；NATIVE 只能同步生成） |
| 路径 `/mcp_tool_profile/built_in/*`、`/mcp_tool_profile/sync_built_in` | `/mcp_tool_profile/native/*`、`/mcp_tool_profile/sync_native` |

路径变化需同步网关资源登记（`resource_api`）与菜单权限。

### 7.5 技能选择工具接口

管理接口（`/built_in|extension|customer/list|page`）带数据隔离，租户用户查原生、扩展工具时会被加上 `organ_id = 当前机构`，结果为空；且返回完整 `McpToolProfileVo`（含 OpenAPI 文档与 inputSchema）。技能选择工具改用独立接口，管理接口不动。

| 接口 | 用途 | 参数 |
|---|---|---|
| `GET /mcp_tool_profile/options/page` | `ToolPicker` 分页选择工具 | `resourceType`（枚举）、`serviceId`、`keyword`（匹配 name / title）、分页 |
| `POST /mcp_tool_profile/options/by_names` | `SkillDrawer` 加载已绑定工具 | 请求体：工具名数组，单次上限 200 |

可见范围由后端强制，不取参数中的机构，客户端改 `resourceType` 也拿不到越权数据：

- NATIVE、EXTENSION：全局可见，不限机构。
- CUSTOMER：限当前机构，`organ_id` 取 `PrincipalContextHolder.getOrganId()`；不依赖隔离拦截器（超管不被拦截，依赖拦截器会让超管看到所有租户的私有工具）。
- 只返回启用状态（ACTIVE）。
- DAO 使用不带隔离的专用查询，SQL 只查返回所需列，不 `SELECT *`：

```sql
SELECT p.name, p.title, p.description, p.resource_type, p.service_id, p.method
       -- by_names 另查：, p.input_schema
FROM mcp_tool_profile p
WHERE p.delete_flag = 0
  AND p.status = 'ACTIVE'
  AND p.name IN (...)                      -- by_names
  -- options/page：AND p.resource_type = ? [AND p.service_id = ?] [AND (p.name LIKE ? OR p.title LIKE ?)]
  AND (
      p.resource_type IN ('NATIVE', 'EXTENSION')
      OR (p.resource_type = 'CUSTOMER' AND p.organ_id = #{当前机构})
  )
```

两个接口返回独立 VO，不复用 `McpToolProfileVo`，字段按使用方所需的并集确定。

`options/page` 返回 `McpToolOptionVo`：

| 字段 | 用途 |
|---|---|
| `name` | 绑定用工具名 |
| `title` | 展示标题 |
| `description` | 帮助判断工具用途 |
| `resourceType` | 区分原生 / 扩展 / 租户 |
| `serviceId`、`serviceName` | 按服务分组展示；`serviceName` 由 service 层填充（NATIVE 取服务注册名称缓存，其余取 `mcp_external_service.name`） |
| `method` | 选择列表展示方法标签；绑定后工具名后的"读 / 写"标记；契约面板中未配置契约的工具按此分到"查询 / 写入"组（非 GET 视为写） |

`by_names` 返回 `McpToolMetaVo`：在 `McpToolOptionVo` 基础上增加一个字段，供 `SkillDrawer` 与执行契约面板 `ContractPanel` 使用：

| 字段 | 用途 |
|---|---|
| `inputSchema` | 契约面板新建工具契约骨架时生成 `args` |

两个 VO 均不返回：`openapiDocument`、`path`、`sourceId`、`organId`、`status`、审计字段；`options/page` 另外不返回 `inputSchema`。全局工具对租户只开放"可选"与契约配置所需的入参结构，不暴露完整接口文档。

## 8. agent-app 改造

### 8.1 菜单与页面

| 菜单 | 落地页 | 说明 |
|---|---|---|
| 平台原生工具 | `mcp_tool_profile`（NATIVE） | 只保留"同步平台接口"，去掉"新增工具"；保留编辑、启停、删除 |
| 平台扩展工具 | `mcp_external_service`（EXTENSION） | 服务列表；行操作"工具管理" |
| 租户系统工具 | `mcp_external_service`（CUSTOMER） | 服务列表；行操作"工具管理" |

- 删除菜单"扩展系统接入""租户系统接入"；`route-map.ts` 中 `/mcp_external_service/extension`、`/mcp_external_service/customer` 改用"平台扩展工具""租户系统工具"标题，`/mcp_tool_profile/extension`、`/mcp_tool_profile/customer` 两个路由删除。
- 页面配置（`pages.json`、`resources.json`）由 `npm run build:config` 重新生成。

### 8.2 服务列表页（mcp_external_service）

- 查询与表单删除"服务编码""上下文路径"；"调用基址"提示改为完整基址（如 `https://erp.example.com/api`）。
- 删除 `callUrl` 拼接，列表直接展示 `endpoint`。
- 行操作新增"工具管理"，打开宽抽屉（70%～80%），展示该服务下的工具。

### 8.3 工具管理抽屉

- 复用 `mcp_tool_profile` 的列表与编辑能力，抽成组件，按 `serviceId` 过滤。
- 抽屉内提供"导入接口"（`OpenApiImport`，自动带 `serviceId`）、编辑、启停、删除。
- 导入预览中工具名只读展示；title 规范化失败或重名时展示后端错误。
- 不提供单条"新增工具"：工具一律由 OpenAPI 导入生成，保证名称、来源一致。

### 8.4 类型与 API

| 位置 | 调整 |
|---|---|
| `mcp_external_service/type.ts` | 删除 `code`、`contextPath` |
| `mcp_tool_profile/type.ts` | `serviceCode` → `serviceId`，新增 `serviceName`；`openapiOperation` → `openapiDocument`；`resourceType` 取值改为 `NATIVE` / `EXTENSION` / `CUSTOMER` |
| `ResourceType` 常量 | `BUILT_IN` → `NATIVE` |
| `mcp_tool_profile/api.ts` | 列表、导入接口改为传 `serviceId` |

### 8.5 工具相关组件

| 组件 | 调整 |
|---|---|
| `BuiltInSync.vue` | 改名 `NativeSync.vue`；服务下拉的取值由 `serviceCode` 改为服务注册 `id`，提交 `serviceId`；调用 `McpToolProfileApi.syncNative`（`/sync_native`） |
| `OpenApiImport.vue` | 在工具管理抽屉中使用，固定带当前服务的 `serviceId`；删除选择服务的下拉框 |
| `ToolDrawer.vue` | 删除"服务编码"输入框；工具名只读（按规则生成） |
| `ToolPicker.vue` | `builtInLabel` 属性改名 `nativeLabel`；见 8.6 |
| `route-map.ts`、`pages.json` | `/mcp_tool_profile/built_in`、`mcp_tool_profile-built_in` 改为 `native`（`pages.json` 由 `npm run build:config` 生成） |
| `api.ts` | `syncBuiltIn` 改名 `syncNative`；`kindOf` 中 `built_in` 改为 `native` |

### 8.6 技能关联工具（ToolPicker / SkillDrawer）

- `ToolPicker`：改调 `GET /mcp_tool_profile/options/page`（7.5 节），一个接口按 `resourceType` 区分页签；页签内按 `serviceId` 分组，标题显示 `serviceName`；类型改为 `McpToolOptionVo`。
- `SkillDrawer`：删除 `listAll()`（全量拉取三类工具），改为先取技能已绑定工具名，再调 `POST /mcp_tool_profile/options/by_names` 一次取回 `McpToolMetaVo`，同时传给 `ContractPanel`；新勾选的工具确认后按名字补查一次 `by_names`（`ToolPicker` 返回的 `McpToolOptionVo` 不含 `inputSchema`）。
- 工具名后的"读 / 写"标记与契约面板按 `method` 分组的逻辑保留不变（非 GET 视为写）。
- 绑定数据：工具名全部变化，改造完成后重新同步 / 导入工具，并在 agent-studio 中重新绑定技能工具、更新执行契约 `tools` 的 key（如智能客服的 `native_g2rain_knowledge_post_knowledge_answer`）。

## 9. 改造顺序

1. 评审本文档与 SQL 脚本中的 DDL。
2. 按 6.3 节手工调整两张表的 CRUD（不使用生成器）。
3. mcp-registry：命名、服务归属、调用地址、序列化（第 7 节）。
4. agent-app：菜单、服务页、工具抽屉、ToolPicker（第 8 节）；执行 `npm run build:config`。
5. 重新同步原生工具、导入外部工具，重新绑定技能工具并更新执行契约。

## 10. 已确认事项

1. 不保留跨服务的全局工具搜索入口；扩展、租户工具一律从"服务 → 工具管理"进入。技能选工具的 `ToolPicker` 本身跨服务，不受影响。
2. `organ_id` 沿用现有逻辑，取操作人当前机构：NATIVE 为超管所属公司；EXTENSION 为运营公司（一个普通租户，通过菜单权限获得扩展工具的管理权）；CUSTOMER 为租户自己。
