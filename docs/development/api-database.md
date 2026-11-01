# API 与数据约定

管理 API 维护 `mcp_external_service` 与 `mcp_tool_profile`；跨服务默认发布查询契约，写入经受控的管理入口。MCP 协议入口为 `/mcp`，其中 `tools/list` 与 `tools/call` 的输入输出以当前 Spring AI MCP Server 和工具 schema 实现为准。

数据库和工具目录的租户、逻辑删除、版本及唯一性语义必须一致；生产变更使用增量迁移，不复用初始化脚本。
