# 本地开发

环境：JDK 25、Maven、MySQL 8.0.13+、Redis、Nacos 和可用于联调的下游工具服务。初始化脚本为 `scripts/g2rain-mcp-registry.sql`，仅用于独立开发库。

```bash
mvn clean verify
mvn -pl g2rain-mcp-registry-startup -am spring-boot:run -Dspring-boot.run.profiles=dev
```

默认端口 `8080`，MCP 端点为 `/mcp`；默认 profile 为 `nacos`，本地使用 `dev` 时不要同时启用两者。
