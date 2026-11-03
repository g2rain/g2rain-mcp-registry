# 模块与依赖

`api` 发布外部服务、工具资料和查询契约；`biz` 实现 Controller、Service、DAO、OpenAPI/schema 解析、MCP 上下文、工具列表与调用处理器；`startup` 组装 Spring Boot 和 MCP Server。依赖方向固定为 `startup -> biz -> api`，Controller -> Service -> DAO。
