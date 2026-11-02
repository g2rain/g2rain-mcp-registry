# 运行流程

1. 管理端维护 MCP 外部服务与工具资料。
2. Runtime 向 `/mcp` 发送 `tools/list`，Registry 根据可用工具资料返回工具定义。
3. Runtime 发送 `tools/call`，Registry 组装 MCP 请求上下文、输入 schema 和可信主体信息，再调用下游服务并映射结果或错误。

工具调用可能带有进度通知；调用链需要保留关联 ID 且避免记录敏感参数。
