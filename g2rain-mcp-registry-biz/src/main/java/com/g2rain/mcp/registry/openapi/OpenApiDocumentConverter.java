package com.g2rain.mcp.registry.openapi;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import com.g2rain.mcp.registry.openapi.naming.NamingStrategy;
import com.g2rain.mcp.registry.schema.InputSchemaComposer;
import io.swagger.v3.core.util.Json;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAPI / Swagger 文档到 MCP 工具草稿的转换器。
 * <p>
 * 外部服务预览传入整份文档，自有服务同步传入 basis 已拆好的单接口文档，都走 {@link #convert(String)}。
 * 文档中每个 {@code path + HTTP method} 对应一条 {@link ConvertedOperation}。本类只做转换，不写库。
 * </p>
 *
 * <h2>处理顺序</h2>
 * <ol>
 *   <li>发现 {@code swaggerVersion} 为 1.x 时直接拒绝。这段只认 JSON；YAML 交给下一步解析器。</li>
 *   <li>{@link OpenAPIParser} 解析文档。Swagger 2.0 在此转成 OpenAPI 3.0 模型，3.0 与 3.1 保持原版本。</li>
 *   <li>将模型序列化后再次解析并 {@code resolveFully}，展开全部 {@code $ref}。
 *       MCP 的 inputSchema 不能保留引用。</li>
 *   <li>逐个接口用 {@link InputSchemaComposer#compose} 生成 inputSchema，
 *       用 {@link NamingStrategy} 生成建议工具名。</li>
 *   <li>版本为 OpenAPI 3.0（含由 2.0 转出的 3.0）时，把 {@code nullable: true}
 *       改写为 {@code type} 数组中的 {@code "null"}。</li>
 * </ol>
 *
 * <h2>失败方式</h2>
 * <ul>
 *   <li>整份文档无法解析，或解析结果不是 OpenAPI 3.x：抛 {@link BusinessException}，不返回列表。</li>
 *   <li>单个接口的命名或 schema 生成失败：写入该条 {@link ConvertedOperation#problem()}，其余接口继续。</li>
 *   <li>接口既无参数也无受支持的 requestBody：inputSchema 为 {@code {"type":"object"}}。</li>
 * </ul>
 *
 * @author alpha
 * @see ConvertedOperation
 * @see InputSchemaComposer
 * @see NamingStrategy
 * @since 2026/10/16
 */
@Component
public class OpenApiDocumentConverter {

    private static final JsonCodec CODEC = JsonCodecFactory.instance();

    private static final String EMPTY_OBJECT_SCHEMA = "{\"type\":\"object\"}";

    private final InputSchemaComposer inputSchemaComposer;

    private final NamingStrategy namingStrategy;

    private final OpenAPIParser parser = new OpenAPIParser();

    /**
     * 注入 schema 组合与工具命名。解析器在本类内创建，不作为 Bean 注入。
     *
     * @param inputSchemaComposer 把已展开的 Operation 合成 MCP inputSchema
     * @param namingStrategy      按 method 与 path 生成建议工具名
     */
    public OpenApiDocumentConverter(InputSchemaComposer inputSchemaComposer, NamingStrategy namingStrategy) {
        this.inputSchemaComposer = inputSchemaComposer;
        this.namingStrategy = namingStrategy;
    }

    /**
     * 把一份 OpenAPI / Swagger 文档转成按接口展开的 MCP 草稿。
     * <p>
     * 先拒绝 Swagger 1.x，再解析（2.0 在解析时转成 3.0），然后展开 {@code $ref}，最后逐接口生成
     * inputSchema 与建议工具名。整份文档和单接口最小文档使用同一入口。
     * </p>
     *
     * @param document OpenAPI 3.x 或 Swagger 2.0 的 JSON / YAML 文本，不能为空白
     * @return 每个 path + method 一条；文档没有 paths 时返回空列表
     * @throws BusinessException 文档为空白、是 Swagger 1.x、无法解析，或解析结果不是 OpenAPI 3.x
     */
    public List<ConvertedOperation> convert(String document) {
        return convert(document, null, null, null);
    }

    /**
     * 同 {@link #convert(String)}，并按资源类型生成 {@code method_[self|serviceCode]_contextPath_path} 建议名。
     *
     * @param document     文档文本
     * @param resourceType {@link com.g2rain.mcp.registry.enums.ResourceType} 名；用于命名中间段
     * @param serviceCode  外部服务编码；BUILT_IN 可空
     * @param contextPath  上下文路径，可空
     */
    public List<ConvertedOperation> convert(String document, @Nullable String resourceType,
                                            @Nullable String serviceCode, @Nullable String contextPath) {
        Asserts.isTrue(Strings.isNotBlank(document), SystemErrorCode.PARAM_REQUIRED, "document");
        rejectSwagger1(document);

        // 先解析（2.0 在这一步转成 3.0 模型），再展开 $ref
        OpenAPI parsed = read(document, false);
        OpenAPI resolved = resolve(parsed);
        return toOperations(resolved, resourceType, serviceCode, contextPath);
    }

    /**
     * 拒绝 Swagger 1.x。只检查 JSON 根节点上的 {@code swaggerVersion}。
     * <p>
     * {@link JsonCodec} 读不了 YAML，读失败时不在这里报错，交给 {@link #read} 用解析器判断。
     * 根节点不是对象、或没有 {@code swaggerVersion} 时同样放行。
     * </p>
     *
     * @param document 原始文档文本
     * @throws BusinessException {@code swaggerVersion} 以 {@code 1.} 开头
     */
    private void rejectSwagger1(String document) {
        JsonNode root;
        try {
            root = CODEC.str2node(document);
        } catch (BusinessException ignored) {
            return;
        }

        if (Objects.isNull(root) || !root.isObject()) {
            return;
        }

        JsonNode swaggerVersion = root.get("swaggerVersion");
        if (Objects.nonNull(swaggerVersion) && swaggerVersion.isString()
            && swaggerVersion.asString().startsWith("1.")) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "document.swaggerVersion");
        }
    }

    /**
     * 用 {@link OpenAPIParser} 读取文档。Swagger 2.0 由解析器转成 OpenAPI 3.0 模型。
     *
     * @param document    JSON 或 YAML 文本；展开引用时传入已序列化的 3.x JSON
     * @param resolveFully {@code true} 时展开 {@code $ref}、requestBody、responses 与 combinators
     * @return 解析后的 OpenAPI 模型，版本字段以 {@code 3.} 开头
     * @throws BusinessException 解析结果为空，或 {@code openapi} 版本不是 3.x
     */
    private OpenAPI read(String document, boolean resolveFully) {
        SwaggerParseResult result = parser.readContents(document, null, parseOptions(resolveFully));
        OpenAPI openAPI = result.getOpenAPI();
        Asserts.isTrue(Objects.nonNull(openAPI), SystemErrorCode.PARAM_VAL_INVALID, "document");
        String version = openAPI.getOpenapi();
        // 转完后必须是 3.x；1.x / 无法识别的文档解析器给不出 openapi 版本
        Asserts.isTrue(Strings.isNotBlank(version) && version.startsWith("3."),
            SystemErrorCode.PARAM_VAL_INVALID, "document.openapi");
        return openAPI;
    }

    /**
     * 展开模型中的全部 {@code $ref}。
     * <p>
     * 解析器只能对文档文本做 {@code resolveFully}，所以先把模型序列化再调用 {@link #read}。
     * 序列化必须用 swagger-core 的 {@link Json#mapper()}。{@link JsonCodec} 是 tools.jackson，
     * 对不上这套 Swagger 模型。
     * </p>
     *
     * @param openAPI 尚未展开引用的 OpenAPI 模型
     * @return 引用已内联的新模型
     * @throws BusinessException 模型序列化失败，或再次解析失败
     */
    private OpenAPI resolve(OpenAPI openAPI) {
        String json;
        try {
            json = Json.mapper().writeValueAsString(openAPI);
        } catch (Exception exception) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "document");
        }

        return read(json, true);
    }

    /**
     * 组装解析选项。首次解析关闭展开，避免未转换完的 2.0 文档在展开阶段失败。
     *
     * @param resolveFully {@code true} 时同时打开 resolve、resolveFully、requestBody、responses、combinators
     * @return 传给 {@link OpenAPIParser#readContents} 的选项
     */
    private ParseOptions parseOptions(boolean resolveFully) {
        ParseOptions options = new ParseOptions();
        options.setResolve(resolveFully);
        options.setResolveFully(resolveFully);
        options.setResolveRequestBody(resolveFully);
        options.setResolveResponses(resolveFully);
        options.setResolveCombinators(resolveFully);
        return options;
    }

    /**
     * 遍历已展开模型的全部接口。{@link PathItem#readOperationsMap()} 只含 HTTP 方法，不含 path 级 parameters。
     *
     * @param openAPI 引用已展开的 OpenAPI 模型
     * @return 每个接口一条；没有 paths 时返回空列表。单接口失败记在对应结果的 problem 上
     */
    private List<ConvertedOperation> toOperations(OpenAPI openAPI, @Nullable String resourceType,
                                                  @Nullable String serviceCode, @Nullable String contextPath) {
        if (Objects.isNull(openAPI.getPaths())) {
            return List.of();
        }

        boolean rewriteNullable = Strings.isNotBlank(openAPI.getOpenapi()) && openAPI.getOpenapi().startsWith("3.0");
        List<ConvertedOperation> operations = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = pathEntry.getValue();
            if (Objects.isNull(pathItem)) {
                continue;
            }

            // readOperationsMap 只含 HTTP 方法，不含 path 级 parameters
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                operations.add(toOperation(pathEntry.getKey(), opEntry.getKey(), opEntry.getValue(), rewriteNullable,
                    resourceType, serviceCode, contextPath));
            }
        }

        return operations;
    }

    /**
     * 把一个 Operation 收成一条草稿。命名或 schema 失败时不抛出，返回带 problem 的结果。
     *
     * @param path            接口路径，如 {@code /orders/{id}}
     * @param method          HTTP 方法
     * @param operation       已展开 {@code $ref} 的 Operation
     * @param rewriteNullable 为 true 时把 OpenAPI 3.0 的 {@code nullable} 改成 JSON Schema 的 null 类型
     * @return 成功时 problem 为空；失败时 inputSchema 与 openapiOperation 为空
     */
    private ConvertedOperation toOperation(String path, PathItem.HttpMethod method, Operation operation,
                                           boolean rewriteNullable, @Nullable String resourceType,
                                           @Nullable String serviceCode, @Nullable String contextPath) {
        String httpMethod = method.name();
        String name = Strings.isNotBlank(operation.getSummary()) ? operation.getSummary() : httpMethod + " " + path;
        String serviceSegment = EndpointStrategy.resolveServiceSegment(resourceType, serviceCode);
        OpenApiOperation catalog = new OpenApiOperation("", Objects.toString(contextPath, ""), path, method,
            operation, serviceSegment);
        String toolName;
        try {
            toolName = namingStrategy.name(catalog);
        } catch (BusinessException exception) {
            return failed(httpMethod, path, name, operation.getDescription(), exception.getMessage());
        }

        try {
            String inputSchema = schemaJson(inputSchemaComposer.compose(catalog));
            if (rewriteNullable) {
                inputSchema = rewriteNullable(inputSchema);
            }

            String operationJson = Json.mapper().writeValueAsString(operation);
            return new ConvertedOperation(httpMethod, path, name, operation.getDescription(), toolName,
                inputSchema, operationJson, null);
        } catch (Exception exception) {
            return failed(httpMethod, path, name, operation.getDescription(), exception.getMessage());
        }
    }

    /**
     * 把组合后的 Schema 写成 JSON。没有参数也没有受支持的 requestBody 时，compose 返回 null，这里补空对象。
     *
     * @param schema {@link InputSchemaComposer#compose} 的结果，可为 null
     * @return schema 的 JSON；schema 为 null 时为 {@code {"type":"object"}}
     * @throws Exception swagger-core 的 {@link Json#mapper()} 序列化失败
     */
    private String schemaJson(Schema<?> schema) throws Exception {
        if (Objects.isNull(schema)) {
            return EMPTY_OBJECT_SCHEMA;
        }

        return Json.mapper().writeValueAsString(schema);
    }

    /**
     * 把 inputSchema 里的 OpenAPI 3.0 {@code nullable: true} 改成 JSON Schema 能表达的 null。
     * <p>
     * 只处理这一份 schema 文本，不改原始 Operation。节点不是 JSON 时原样返回。
     * </p>
     *
     * @param schemaJson {@link #schemaJson} 产出的 JSON
     * @return 改写后的 JSON
     */
    private String rewriteNullable(String schemaJson) {
        JsonNode root = CODEC.str2node(schemaJson);
        if (Objects.isNull(root)) {
            return schemaJson;
        }

        rewriteNullableNode(root);
        return CODEC.obj2str(root);
    }

    /**
     * 深度遍历 schema。对象上出现 {@code nullable: true} 时删掉该字段，并给 {@code type} 补上 {@code null}。
     *
     * @param node schema 节点，对象与数组都会继续往下走
     */
    private void rewriteNullableNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            JsonNode nullable = objectNode.get("nullable");
            if (Objects.nonNull(nullable) && nullable.isBoolean() && nullable.booleanValue()) {
                objectNode.remove("nullable");
                appendNullType(objectNode);
            }

            for (Map.Entry<String, JsonNode> field : objectNode.properties()) {
                rewriteNullableNode(field.getValue());
            }

            return;
        }

        if (node.isArray()) {
            node.forEach(this::rewriteNullableNode);
        }
    }

    /**
     * 给当前 schema 对象的 {@code type} 加上 {@code null}。
     * <p>
     * 没有 type 时写成 {@code ["null"]}。type 是字符串时改成 {@code [原类型, "null"]}。
     * type 已是数组且含 {@code null} 时不重复添加。
     * </p>
     *
     * @param objectNode 刚刚移除 {@code nullable} 的 schema 对象
     */
    private void appendNullType(ObjectNode objectNode) {
        JsonNode typeNode = objectNode.get("type");
        if (Objects.isNull(typeNode)) {
            ArrayNode types = objectNode.arrayNode();
            types.add("null");
            objectNode.set("type", types);
            return;
        }

        if (typeNode.isString()) {
            ArrayNode types = objectNode.arrayNode();
            types.add(typeNode.asString());
            types.add("null");
            objectNode.set("type", types);
            return;
        }

        if (typeNode.isArray() && !containsNull((ArrayNode) typeNode)) {
            ((ArrayNode) typeNode).add("null");
        }
    }

    /**
     * 判断 {@code type} 数组里是否已经包含 JSON Schema 的 null。
     *
     * @param types schema 的 {@code type} 数组
     * @return 存在字符串 {@code "null"} 时为 true
     */
    private boolean containsNull(ArrayNode types) {
        for (JsonNode type : types) {
            if (type.isString() && "null".equals(type.asString())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 组装一条失败草稿。工具名、inputSchema、openapiOperation 留空，避免调用方把半成品写入工具表。
     *
     * @param method      HTTP 方法
     * @param path        接口路径
     * @param name        展示名，优先 summary
     * @param description 接口说明，可空
     * @param problem     失败原因；空白时写成 {@code convert failed}
     * @return problem 非空、schema 为空的 {@link ConvertedOperation}
     */
    private ConvertedOperation failed(String method, String path, String name, @Nullable String description, @Nullable String problem) {
        return new ConvertedOperation(method, path, name, description, "", null, null, Strings.isNotBlank(problem) ? problem : "convert failed");
    }
}
