# 依赖边界

固定模块方向为 `g2rain-mcp-registry-startup -> g2rain-mcp-registry-biz -> g2rain-mcp-registry-api`。API 不依赖实现模块；Biz 负责 MCP 协议适配、schema 与数据访问；Startup 只装配运行时。

Registry 是工具资料和代理行为的数据所有者。Agent Runtime 通过 MCP 协议消费工具，不应绕过 Registry 直接依赖其持久化模型；下游业务服务只作为受控工具调用目标，不成为 Registry 的数据所有者。
