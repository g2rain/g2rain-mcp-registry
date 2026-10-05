package com.g2rain.mcp.registry.bootstrap;


import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.openapi.naming.NamingStrategy;
import com.g2rain.mcp.registry.tool.ToolRegistry;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 启动时将 {@code g2rain-cms} 的 REST 接口预注册进 {@link ToolRegistry},
 * 便于本地联调 {@code tools/list} 与 {@code tools/call}.
 * <p>
 * 不做热更新; profileId / sourceId 为占位值, 仅用于内存索引与权限过滤占位.
 * </p>
 */
@Slf4j
@Component
public class LocalMcpToolRegistryBootstrap implements ApplicationRunner {

    private final ToolRegistry toolRegistry;
    private final NamingStrategy namingStrategy;

    @Value("${g2rain.mcp.registry.local-cms-endpoint:lb://g2rain-cms}")
    private String cmsEndpoint;

    public LocalMcpToolRegistryBootstrap(ToolRegistry toolRegistry, NamingStrategy namingStrategy) {
        this.toolRegistry = toolRegistry;
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void run(ApplicationArguments args) {
        var profileId = new AtomicLong(1L);
        var sourceId = new AtomicLong(101L);
        var ops = buildCmsOperations(cmsEndpoint);
        for (OpenApiOperation operation : ops) {
            register(profileId.getAndIncrement(), sourceId.getAndIncrement(), operation);
        }
        log.info("Local MCP tool registry seeded with {} CMS tools, endpoint={}", ops.size(), cmsEndpoint);
    }

    private void register(Long profileId, Long sourceId, OpenApiOperation operation) {
        var code = namingStrategy.name(operation);
        toolRegistry.register(profileId, code, sourceId, operation);
        log.debug("Registered local MCP tool, profileId={}, code={}, path={} {}",
            profileId, code, operation.method(), operation.path());
    }

    private List<OpenApiOperation> buildCmsOperations(String endpoint) {
        var ops = new ArrayList<OpenApiOperation>();
        ops.addAll(articleOps(endpoint));
        ops.addAll(articleCategoryOps(endpoint));
        ops.addAll(articleTagRelationOps(endpoint));
        ops.addAll(channelOps(endpoint));
        ops.addAll(pageOps(endpoint));
        ops.addAll(spaceOps(endpoint));
        ops.addAll(tagOps(endpoint));
        ops.addAll(webSiteOps(endpoint));
        return ops;
    }

    // ===================== article =====================

    private List<OpenApiOperation> articleOps(String endpoint) {
        var base = "/article";
        var selectParams = articleSelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectArticleList",
                "查询文章列表", "按查询条件筛选文章，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectArticlePage",
                "分页查询文章", "按查询条件筛选文章并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            get(endpoint, base + "/detail", "articleDetail",
                "文章详情查询", "根据主键查询文章详情",
                List.of(optionalQueryLongParam("id", "主键"))),
            post(endpoint, base + "/save", "saveArticle",
                "新增或更新文章", "根据请求体主键是否存在，新增或更新文章信息",
                "文章", articleBodySchema()),
            delete(endpoint, base + "/{id}", "deleteArticle",
                "删除文章", "根据主键删除文章记录")
        );
    }

    private List<Parameter> articleSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryLongParam("spaceId", "空间标识"),
            optionalQueryLongParam("categoryId", "分类标识"),
            optionalQueryLongParam("sourceApplicationId", "来源应用标识"),
            optionalQueryParam("sourceTraceId", "来源追踪ID"),
            optionalQueryParam("title", "标题"),
            optionalQueryParam("summary", "摘要"),
            optionalQueryParam("cover", "封面"),
            optionalQueryParam("contentType", "内容类型[MARKDOWN/HTML]"),
            optionalQueryParam("author", "作者"),
            optionalQueryParam("status", "状态[DRAFT/PUBLISHED]"),
            optionalQueryParam("publishTime", "发布时间"),
            optionalQueryParam("content", "内容")
        );
    }

    private Schema<?> articleBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("spaceId", longProp("空间标识"))
            .addProperty("categoryId", longProp("分类标识"))
            .addProperty("sourceApplicationId", longProp("来源应用标识"))
            .addProperty("sourceTraceId", stringProp("来源追踪ID"))
            .addProperty("title", stringProp("标题"))
            .addProperty("summary", stringProp("摘要"))
            .addProperty("cover", stringProp("封面"))
            .addProperty("contentType", stringProp("内容类型[MARKDOWN:Markdown, HTML:HTML]"))
            .addProperty("author", stringProp("作者"))
            .addProperty("status", stringProp("状态[DRAFT:草稿, PUBLISHED:发布]"))
            .addProperty("publishTime", stringProp("发布时间"))
            .addProperty("content", stringProp("内容"));
    }

    // ===================== article_category =====================

    private List<OpenApiOperation> articleCategoryOps(String endpoint) {
        var base = "/article_category";
        var selectParams = articleCategorySelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectArticleCategoryList",
                "查询文章分类列表", "按查询条件筛选文章分类，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectArticleCategoryPage",
                "分页查询文章分类", "按查询条件筛选文章分类并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/save", "saveArticleCategory",
                "新增或更新文章分类", "根据请求体主键是否存在，新增或更新文章分类信息",
                "文章分类", articleCategoryBodySchema()),
            post(endpoint, base + "/update_status", "updateArticleCategoryStatus",
                "文章分类状态变更", "变更文章分类启用/禁用状态",
                "文章分类状态", statusBodySchema("状态[ENABLED:启用, DISABLED:禁用]")),
            delete(endpoint, base + "/{id}", "deleteArticleCategory",
                "删除文章分类", "根据主键删除文章分类记录")
        );
    }

    private List<Parameter> articleCategorySelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryLongParam("spaceId", "空间标识"),
            optionalQueryParam("categoryName", "分类名称"),
            optionalQueryParam("categoryCode", "分类编码"),
            optionalQueryParam("status", "状态[ENABLED/DISABLED]")
        );
    }

    private Schema<?> articleCategoryBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("spaceId", longProp("空间标识"))
            .addProperty("categoryName", stringProp("分类名称"))
            .addProperty("categoryCode", stringProp("分类编码"))
            .addProperty("status", stringProp("状态[ENABLED:启用, DISABLED:禁用]"));
    }

    // ===================== article_tag_relation =====================

    private List<OpenApiOperation> articleTagRelationOps(String endpoint) {
        var base = "/article_tag_relation";
        var selectParams = articleTagRelationSelectParams();
        var batchAddSchema = objectSchema()
            .addProperty("articleId", longProp("文章标识"))
            .addProperty("tagIds", new ArraySchema()
                .items(new IntegerSchema().format("int64").description("标签标识"))
                .description("标签标识列表"));
        var saveSchema = objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("articleId", longProp("文章标识"))
            .addProperty("tagId", longProp("标签标识"));
        return List.of(
            get(endpoint, base + "/list", "selectArticleTagRelationList",
                "查询文章标签关系列表", "按查询条件筛选文章标签关系，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectArticleTagRelationPage",
                "分页查询文章标签关系", "按查询条件筛选文章标签关系并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/batch_add_tags", "batchAddArticleTags",
                "单文章批量添加标签", "为指定文章一次性绑定多个标签关系",
                "批量打标请求", batchAddSchema),
            post(endpoint, base + "/save", "saveArticleTagRelation",
                "新增或更新文章标签关系", "根据请求体主键是否存在，新增或更新单条文章标签关系",
                "文章标签关系", saveSchema),
            delete(endpoint, base + "/{id}", "deleteArticleTagRelation",
                "删除文章标签关系", "根据主键删除文章标签关系记录")
        );
    }

    private List<Parameter> articleTagRelationSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("articleId", "文章标识"),
            optionalQueryLongParam("tagId", "标签标识")
        );
    }

    // ===================== channel =====================

    private List<OpenApiOperation> channelOps(String endpoint) {
        var base = "/channel";
        var selectParams = channelSelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectChannelList",
                "查询栏目列表", "按查询条件筛选栏目，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectChannelPage",
                "分页查询栏目", "按查询条件筛选栏目并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/save", "saveChannel",
                "新增或更新栏目", "根据请求体主键是否存在，新增或更新栏目信息",
                "栏目", channelBodySchema()),
            post(endpoint, base + "/update_status", "updateChannelStatus",
                "栏目状态变更", "变更栏目启用/禁用状态",
                "栏目状态", statusBodySchema("状态[ACTIVE:启用, INACTIVE:禁用]")),
            delete(endpoint, base + "/{id}", "deleteChannel",
                "删除栏目", "根据主键删除栏目记录")
        );
    }

    private List<Parameter> channelSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryLongParam("spaceId", "空间标识"),
            optionalQueryLongParam("siteId", "站点标识"),
            optionalQueryLongParam("parentId", "父栏目标识"),
            optionalQueryParam("channelName", "栏目名称"),
            optionalQueryParam("channelCode", "栏目编码"),
            optionalQueryParam("channelType", "栏目类型[LIST/PAGE/LINK]"),
            optionalQueryParam("path", "访问路径"),
            optionalQueryLongParam("categoryId", "分类标识"),
            optionalQueryLongParam("pageId", "页面标识"),
            optionalQueryParam("linkUrl", "外链URL"),
            optionalQueryIntParam("sortOrder", "排序"),
            optionalQueryIntParam("visible", "是否显示[0:否, 1:是]"),
            optionalQueryParam("status", "状态")
        );
    }

    private Schema<?> channelBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("spaceId", longProp("空间标识"))
            .addProperty("siteId", longProp("站点标识"))
            .addProperty("parentId", longProp("父栏目标识"))
            .addProperty("channelName", stringProp("栏目名称"))
            .addProperty("channelCode", stringProp("栏目编码"))
            .addProperty("channelType", stringProp("栏目类型[LIST:列表, PAGE:页面, LINK:外链]"))
            .addProperty("path", stringProp("访问路径"))
            .addProperty("categoryId", longProp("分类标识"))
            .addProperty("pageId", longProp("页面标识"))
            .addProperty("linkUrl", stringProp("外链URL"))
            .addProperty("sortOrder", intProp("排序"))
            .addProperty("visible", intProp("是否显示[0:否, 1:是]"))
            .addProperty("status", stringProp("状态"));
    }

    // ===================== page =====================

    private List<OpenApiOperation> pageOps(String endpoint) {
        var base = "/page";
        var selectParams = pageSelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectPageList",
                "查询页面列表", "按查询条件筛选页面，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectPagePage",
                "分页查询页面", "按查询条件筛选页面并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/save", "savePage",
                "新增或更新页面", "根据请求体主键是否存在，新增或更新页面信息",
                "页面", pageBodySchema()),
            post(endpoint, base + "/update_status", "updatePageStatus",
                "页面状态变更", "变更页面草稿/发布状态",
                "页面状态", statusBodySchema("状态[DRAFT:草稿, PUBLISHED:发布]")),
            delete(endpoint, base + "/{id}", "deletePage",
                "删除页面", "根据主键删除页面记录")
        );
    }

    private List<Parameter> pageSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryLongParam("spaceId", "空间标识"),
            optionalQueryParam("pageName", "页面名称"),
            optionalQueryParam("pageCode", "页面编码"),
            optionalQueryParam("path", "访问路径"),
            optionalQueryParam("contentType", "内容类型[MARKDOWN/HTML]"),
            optionalQueryParam("status", "状态[DRAFT/PUBLISHED]"),
            optionalQueryParam("content", "页面内容")
        );
    }

    private Schema<?> pageBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("spaceId", longProp("空间标识"))
            .addProperty("pageName", stringProp("页面名称"))
            .addProperty("pageCode", stringProp("页面编码"))
            .addProperty("path", stringProp("访问路径"))
            .addProperty("contentType", stringProp("内容类型[MARKDOWN:Markdown, HTML:HTML]"))
            .addProperty("status", stringProp("状态[DRAFT:草稿, PUBLISHED:发布]"))
            .addProperty("content", stringProp("页面内容"));
    }

    // ===================== space =====================

    private List<OpenApiOperation> spaceOps(String endpoint) {
        var base = "/space";
        var selectParams = spaceSelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectSpaceList",
                "查询内容空间列表", "按查询条件筛选内容空间，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectSpacePage",
                "分页查询内容空间", "按查询条件筛选内容空间并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/save", "saveSpace",
                "新增或更新内容空间", "根据请求体主键是否存在，新增或更新内容空间信息",
                "内容空间", spaceBodySchema()),
            post(endpoint, base + "/update_status", "updateSpaceStatus",
                "内容空间状态变更", "变更内容空间启用/禁用状态",
                "内容空间状态", statusBodySchema("状态[ENABLED:启用, DISABLED:禁用]")),
            delete(endpoint, base + "/{id}", "deleteSpace",
                "删除内容空间", "根据主键删除内容空间记录")
        );
    }

    private List<Parameter> spaceSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryParam("spaceName", "空间名称"),
            optionalQueryParam("spaceCode", "空间编码"),
            optionalQueryParam("spaceType", "空间类型[WEBSITE/KNOWLEDGE/INTERNAL]"),
            optionalQueryParam("status", "状态[ENABLED/DISABLED]")
        );
    }

    private Schema<?> spaceBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("spaceName", stringProp("空间名称"))
            .addProperty("spaceCode", stringProp("空间编码"))
            .addProperty("spaceType", stringProp("空间类型[WEBSITE:官网, KNOWLEDGE:知识库, INTERNAL:内部]"))
            .addProperty("status", stringProp("状态[ENABLED:启用, DISABLED:禁用]"))
            // 新建空间业务必填；organId 由 runtime 平台注入，不进 required，避免 ArgPolicy 早于 ParamContract 误拒
            .required(List.of("spaceName", "spaceCode", "spaceType", "status"));
    }

    // ===================== tag =====================

    private List<OpenApiOperation> tagOps(String endpoint) {
        var base = "/tag";
        var selectParams = tagSelectParams();
        var byArticleSchema = objectSchema()
            .addProperty("articleIds", new ArraySchema()
                .items(new IntegerSchema().format("int64").description("文章标识"))
                .description("文章标识列表"));
        var saveSchema = objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("tagName", stringProp("标签名称"));
        return List.of(
            get(endpoint, base + "/list", "selectTagList",
                "查询标签列表", "按查询条件筛选标签，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectTagPage",
                "分页查询标签", "按查询条件筛选标签并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/by_article", "selectTagsByArticleIds",
                "按文章批量查询标签", "根据文章主键列表，返回每篇文章对应的标签列表映射",
                "按文章查标签请求", byArticleSchema),
            post(endpoint, base + "/save", "saveTag",
                "新增或更新标签", "根据请求体主键是否存在，新增或更新标签信息",
                "标签", saveSchema),
            delete(endpoint, base + "/{id}", "deleteTag",
                "删除标签", "根据主键删除标签记录")
        );
    }

    private List<Parameter> tagSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryParam("tagName", "标签名称")
        );
    }

    // ===================== web_site =====================

    private List<OpenApiOperation> webSiteOps(String endpoint) {
        var base = "/web_site";
        var selectParams = webSiteSelectParams();
        return List.of(
            get(endpoint, base + "/list", "selectWebSiteList",
                "查询站点列表", "按查询条件筛选站点，不分页返回列表", selectParams),
            get(endpoint, base + "/page", "selectWebSitePage",
                "分页查询站点", "按查询条件筛选站点并分页，含总数与当前页数据",
                withPageParams(selectParams)),
            post(endpoint, base + "/save", "saveWebSite",
                "新增或更新站点", "根据请求体主键是否存在，新增或更新站点信息",
                "站点", webSiteBodySchema()),
            post(endpoint, base + "/update_status", "updateWebSiteStatus",
                "站点状态变更", "变更站点启用/禁用状态",
                "站点状态", statusBodySchema("状态[ACTIVE:启用, INACTIVE:禁用]")),
            delete(endpoint, base + "/{id}", "deleteWebSite",
                "删除站点", "根据主键删除站点记录")
        );
    }

    private List<Parameter> webSiteSelectParams() {
        return List.of(
            optionalQueryLongParam("id", "主键"),
            optionalQueryLongParam("organId", "机构标识"),
            optionalQueryParam("siteName", "站点名称"),
            optionalQueryParam("siteCode", "站点编码"),
            optionalQueryParam("domain", "站点域名（多个用逗号分隔）"),
            optionalQueryParam("description", "站点描述"),
            optionalQueryParam("status", "状态[ENABLED/DISABLED]")
        );
    }

    private Schema<?> webSiteBodySchema() {
        return objectSchema()
            .addProperty("id", longProp("主键标识"))
            .addProperty("organId", longProp("机构标识"))
            .addProperty("siteName", stringProp("站点名称"))
            .addProperty("siteCode", stringProp("站点编码"))
            .addProperty("domain", stringProp("站点域名（多个用逗号分隔）"))
            .addProperty("description", stringProp("站点描述"))
            .addProperty("status", stringProp("状态[ENABLED:启用, DISABLED:禁用]"));
    }

    // ===================== builders =====================

    private OpenApiOperation get(String endpoint, String path, String operationId,
                                 String summary, String description, List<Parameter> parameters) {
        return new OpenApiOperation(
            endpoint,
            "",
            path,
            PathItem.HttpMethod.GET,
            new Operation()
                .summary(summary)
                .description(description)
                .operationId(operationId)
                .parameters(parameters)
        );
    }

    private OpenApiOperation post(String endpoint, String path, String operationId,
                                  String summary, String description,
                                  String bodyDescription, Schema<?> bodySchema) {
        return new OpenApiOperation(
            endpoint,
            "",
            path,
            PathItem.HttpMethod.POST,
            new Operation()
                .summary(summary)
                .description(description)
                .operationId(operationId)
                .requestBody(new RequestBody()
                    .required(true)
                    .description(bodyDescription)
                    .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(bodySchema)
                    ))
                )
        );
    }

    private OpenApiOperation delete(String endpoint, String path, String operationId,
                                    String summary, String description) {
        return new OpenApiOperation(
            endpoint,
            "",
            path,
            PathItem.HttpMethod.DELETE,
            new Operation()
                .summary(summary)
                .description(description)
                .operationId(operationId)
                .parameters(List.of(requiredPathLongParam("id", "主键标识")))
        );
    }

    private static List<Parameter> withPageParams(List<Parameter> selectParams) {
        var params = new ArrayList<Parameter>(selectParams.size() + 2);
        params.add(optionalQueryIntParam("pageNum", "当前页码"));
        params.add(optionalQueryIntParam("pageSize", "每页条数"));
        params.addAll(selectParams);
        return params;
    }

    private static Schema<?> statusBodySchema(String statusDescription) {
        return objectSchema()
            .addProperty("id", longProp("主键ID"))
            .addProperty("status", stringProp(statusDescription));
    }

    private static ObjectSchema objectSchema() {
        return new ObjectSchema();
    }

    private static Schema<?> stringProp(String description) {
        return new StringSchema().description(description);
    }

    private static Schema<?> longProp(String description) {
        return new IntegerSchema().format("int64").description(description);
    }

    private static Schema<?> intProp(String description) {
        return new IntegerSchema().description(description);
    }

    private static Parameter optionalQueryParam(String name, String description) {
        return new Parameter()
            .name(name)
            .in("query")
            .required(false)
            .schema(new StringSchema().description(description));
    }

    private static Parameter optionalQueryIntParam(String name, String description) {
        return new Parameter()
            .name(name)
            .in("query")
            .required(false)
            .schema(new IntegerSchema().description(description));
    }

    private static Parameter optionalQueryLongParam(String name, String description) {
        return new Parameter()
            .name(name)
            .in("query")
            .required(false)
            .schema(new IntegerSchema().format("int64").description(description));
    }

    private static Parameter requiredPathLongParam(String name, String description) {
        return new Parameter()
            .name(name)
            .in("path")
            .required(true)
            .schema(new IntegerSchema().format("int64").description(description));
    }
}
