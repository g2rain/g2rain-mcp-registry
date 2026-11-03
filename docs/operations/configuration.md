# 配置说明

配置入口为 `g2rain-mcp-registry-startup/src/main/resources/application.yml`。

| 配置 | 说明 |
| --- | --- |
| `SERVER_PORT` | HTTP 端口，默认 `8080`。 |
| `SPRING_PROFILES_ACTIVE` | 默认 `nacos`；本地可选 `dev`。 |
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | MCP HTTP 端点，当前为 `/mcp`。 |
| `g2rain.mcp.registry.*` | Registry 的进度通知与本地联调下游服务配置。 |

不得在文档或提交中写入真实下游地址、密码或 Token。
