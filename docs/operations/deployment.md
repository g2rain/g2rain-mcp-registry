# 构建、镜像与部署

MCP Registry 是 G2rain 开源智能体平台中的 MCP 工具目录与调用代理边界，不是整个平台或完整的 Agent、模型、权限与部署能力。平台由多个服务、应用、工具和部署编排共同构成；本组件实际交付的是外部服务/工具资料、`/mcp` 的工具发现与调用代理，以及相关 Spring Boot 服务组装。

## 本地构建与测试

```bash
mvn clean verify
```

这会运行 Maven `verify` 生命周期；它不等同于容器构建、GHCR 推送、匿名拉取、在线演示或实际部署验证。

## 容器构建

启动模块使用 Jib，而非 Dockerfile。最终镜像以 `eclipse-temurin:25.0.4.1_1-jre` 为基础，Jib 组装 Spring Boot 依赖、资源和应用类，并以 `com.g2rain.mcp.registry.Application` 启动，声明端口 `8080`。本地构建到 Docker daemon：

```bash
./build.sh <tag>
```

默认目标为 `ghcr.io/g2rain/g2rain-mcp-registry:<tag>`；可通过 `APP_IMAGE` 覆盖镜像仓库以便本地测试。该脚本跳过测试，因此不能替代 `mvn clean verify`。Jib 配置的 OCI 元数据包括 source、url、title、version 和与仓库 LICENSE 一致的 `Apache-2.0` 许可证。

## GHCR 发布

跨仓库 `g2rain-*` Maven 依赖从各组件的 GitHub Maven Packages 仓库解析。镜像工作流在临时 Maven settings 中配置 `g2rain-common`、`g2rain-spring-boot-starter`、`g2rain-basis`、`g2rain-mybatis-extensions` 的依赖仓库及 `g2rain-crafter` 插件仓库；API Maven 工作流只配置所需的 `g2rain-common` 与本仓 `github` deploy server。两者通过环境变量引用 `GITHUB_TOKEN`，不将 Token 写入项目文件。维护者须在各外部包的 Actions access 中授予本仓库读取权限；`packages: write` 不自动授予跨仓库读取权限。若包不存在于对应仓库或版本未发布，认证设置无法补足缺失的制品。

GitHub Actions 只会在稳定版 tag `vX.Y.Z` 被推送且该 tag 指向的提交已在 `origin/main` 历史中时发布。工作流还会验证同名 `docs/releases/vX.Y.Z.md`、根 POM 的项目版本和完整 Maven 验证，再以 `GITHUB_TOKEN` 登录 GHCR。成功后只推送 `<版本>` 与 `sha-<完整提交 SHA>`；不创建 GitHub Release、不部署服务，也不更新 `latest`。

首次成功推送后，在 GitHub Packages 的 **Package settings → Change visibility → Public** 将包设为公开。若组织管理员禁止修改，联系管理员。已有包如返回 `denied`，先检查 **Package settings → Actions access** 是否已授予 `g2rain/g2rain-mcp-registry` 写权限，以及组织 Packages 策略；`packages: write` 本身不能绕过这些设置。

公开后可匿名拉取：

```bash
docker pull ghcr.io/g2rain/g2rain-mcp-registry:1.0.0
```

匿名拉取只有在包被设为 Public 后才可验证，且不代表服务可运行或已部署。在线演示与实际部署验证均不由该工作流执行。

## API Maven Packages 发布

镜像工作流成功推送 `1.0.0` 和 `sha-<完整提交 SHA>` 后，才调用 `.github/workflows/publish-maven.yml`。后者也可手动运行，但所选提交必须属于 `origin/main`。其 `-pl g2rain-mcp-registry-api -am clean deploy` 只选择根父 POM 和 API 模块，不发布 Biz 或 Startup；上传坐标为 `com.g2rain:g2rain-mcp-registry:1.0.0`（父 POM）与 `com.g2rain:g2rain-mcp-registry-api:1.0.0`，目标仓库为 `https://maven.pkg.github.com/g2rain/g2rain-mcp-registry`。镜像成功而 Maven 上传失败时，镜像不会自动回滚；重复发布同一稳定版本可能冲突，应按版本策略处理，不删除或覆盖已发布版本。

消费方需在 Maven settings 中安全配置该仓库和认证。跨仓库的 `GITHUB_TOKEN` 读取须给消费仓库授予包访问权限；个人读取使用具备 `read:packages` 的 PAT classic，切勿提交 Token。GitHub Maven Packages 的认证与公开 GHCR 镜像的匿名拉取不同。CI 会执行全模块 `clean install` 后上传 API；本地 verify、镜像推送、Maven 上传和消费方解析分别验证，不能互相代替。

## 运行与安全

运行时需要 Java 25，且依赖 MySQL、Redis、Nacos 和下游 MCP/HTTP 服务。应用监听 `SERVER_PORT`（默认 `8080`），默认 profile 为 `nacos`。生产环境通过运行时环境变量、Nacos Secret/配置管理或编排平台的 Secret 挂载注入密钥，不能把生产密钥烘焙进镜像。

当前已跟踪的 `g2rain-mcp-registry-startup/src/main/resources/application-nacos.yml` 和 `application-dev.yml` 含 Nacos/MySQL 默认凭据和本机地址；它们会作为 Spring Boot 资源进入 Jib 最终镜像。这是敏感配置风险，维护者应在发布前审阅并以安全的运行时配置覆盖；本次未删除文件、改写凭据或重写历史。发布前还应确认 Nacos、MySQL、Redis、Gateway 路由、`/mcp` 暴露策略、服务身份、下游工具可用性与滚动发布期间的调用/会话影响。
