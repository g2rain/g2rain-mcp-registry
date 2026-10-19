package com.g2rain.mcp.registry.schema;


import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.schema.HttpRequestSpec.Parameters;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.g2rain.mcp.registry.schema.HttpRequestSpec.SUPPORTED_PARAMETER_TYPES;
import static java.lang.Boolean.TRUE;

/**
 * OpenAPI 操作输入 Schema 的<strong>组合器</strong>与<strong>分解器</strong>。
 * <p>
 * 在 g2rain-mcp-registry 中, 每个 MCP Tool 对应一个 OpenAPI {@link Operation}。
 * 本类负责在「注册阶段」将 Operation 的 parameters 与 requestBody 合并为 MCP Tool 所需的
 * 统一 JSON Schema（{@link #compose}）, 并在「调用阶段」将 MCP {@code tools/call} 传入的
 * arguments 反向解析为可执行的 HTTP 请求结构 {@link HttpRequestSpec}（{@link #decompose}）。
 * </p>
 *
 * <h2>组合（compose）策略</h2>
 * <p>根据 Operation 声明内容, 输出以下三种形态之一：</p>
 * <ul>
 *   <li><strong>仅 parameters</strong> — 直接返回 parameters 的 {@link ObjectSchema}</li>
 *   <li><strong>仅 requestBody</strong> — 直接返回 body 的 {@link Schema}</li>
 *   <li><strong>两者兼有</strong> — 用固定 wrapper key 包装后合并为顶层 {@link ObjectSchema}：
 *     <pre>{@code
 * {
 *   "type": "object",
 *   "required": ["_params", "_body"],
 *   "properties": {
 *     "_params": { ... },   // path / query / header 参数 schema
 *     "_body":   { ... }    // requestBody schema（必要时已 unwrap）
 *   }
 * }
 *     }</pre>
 *   </li>
 * </ul>
 * <p>若 Operation 既无有效 parameters 也无支持的 requestBody, 则返回 {@code null}。</p>
 *
 * <h2>分解（decompose）策略</h2>
 * <p>支持两种输入格式, 与 compose 输出一一对应：</p>
 * <ol>
 *   <li><strong>Wrapped 格式</strong> — arguments 含 {@value #PARAMETERS_KEY} 和/或
 *       {@value #REQUEST_BODY_KEY}, 按 key 拆分参数与 body</li>
 *   <li><strong>Direct 格式</strong> — 无 wrapper key, 仅当 Operation <em>只声明 parameters</em>
 *       或 <em>只声明 requestBody</em> 时可安全解析；若两者都有或都没有, 则记录 warn 并返回空模型</li>
 * </ol>
 *
 * <h2>支持范围与限制</h2>
 * <ul>
 *   <li>Parameters：{@link HttpRequestSpec#SUPPORTED_PARAMETER_TYPES} — path、query、header、cookie</li>
 *   <li>RequestBody：仅 {@link HttpRequestSpec#SUPPORTED_MEDIA_TYPE}（{@code application/json}）</li>
 *   <li>非 object 或无 properties 的 body schema 会先被包装, 合并时再 {@link #unwrapIfNeeded} 还原</li>
 *   <li>修改 schema 描述时会 {@link #cloneSchema} 克隆, 避免污染原始 OpenAPI 模型</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/6/30
 * @see HttpRequestSpec
 * @see OpenApiOperation
 * @see HttpRequestSpec#SUPPORTED_PARAMETER_TYPES
 */
@Slf4j
@NullMarked
@Component
public class InputSchemaComposer {

    /**
     * Wrapped 格式下, MCP Tool arguments 中<strong>参数集合</strong>的 JSON 属性名。
     * <p>
     * 对应 compose 时 {@code properties._params}, 值为 {@code Map<String, Object>},
     * key 为 OpenAPI parameter name, value 为调用方传入的实际值。
     * </p>
     */
    private static final String PARAMETERS_KEY = "_params";

    /**
     * Wrapped 格式下, MCP Tool arguments 中<strong>请求体</strong>的 JSON 属性名。
     * <p>
     * 对应 compose 时 {@code properties._body}。当 body schema 本身为非 object 类型
     * （如 string、array）时, resolve 阶段也会用此 key 做临时包装, 合并前再 unwrap。
     * </p>
     */
    private static final String REQUEST_BODY_KEY = "_body";

    /**
     * 为 OpenAPI Operation 组合 MCP Tool 的 input JSON Schema。
     * <p>
     * 先分别解析 parameters 与 requestBody schema, 再按「仅一方 / 两者合并」策略返回。
     * 两者都存在时, {@link #unwrapIfNeeded} 会去掉 body 侧多余的单层包装, 避免
     * {@code _body._body} 嵌套。
     * </p>
     *
     * <h3>三种场景示例</h3>
     *
     * <p><strong>场景 1 — 仅 parameters</strong>（如 {@code GET /users/{id}?page=1}）：</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "id":   { "type": "string" },
     *     "page": { "type": "integer" }
     *   },
     *   "required": ["id"]
     * }
     * }</pre>
     *
     * <p><strong>场景 2 — 仅 requestBody</strong>（如 {@code POST /users}, body 为 JSON object）：</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "name":  { "type": "string" },
     *     "email": { "type": "string" }
     *   },
     *   "required": ["name", "email"]
     * }
     * }</pre>
     *
     * <p><strong>场景 3 — parameters + requestBody 兼有</strong>（如 {@code PUT /users/{id}}）：</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "required": ["_params", "_body"],
     *   "properties": {
     *     "_params": {
     *       "type": "object",
     *       "properties": { "id": { "type": "string" } },
     *       "required": ["id"]
     *     },
     *     "_body": {
     *       "type": "object",
     *       "properties": {
     *         "name": { "type": "string" }
     *       }
     *     }
     *   }
     * }
     * }</pre>
     *
     * @param openApiOperation  catalog 中的 OpenAPI 操作元数据, 内含 {@link Operation}
     * @return 组合后的 input schema；无有效 parameters 且无支持的 requestBody 时返回 {@code null}
     */
    public @Nullable Schema<?> compose(OpenApiOperation openApiOperation) {
        // 分别解析 parameters 与 requestBody 的 JSON Schema
        var parameterSchema = resolveParameterSchema(openApiOperation);
        var requestBodySchema = resolveRequestBodySchema(openApiOperation);

        // 无 parameters：直接返回 body schema（可能为 null）
        if (Objects.isNull(parameterSchema)) {
            return requestBodySchema;
        }

        // 有 parameters 无 body：直接返回 parameters schema
        if (Objects.isNull(requestBodySchema)) {
            return parameterSchema;
        }

        // 两者都有：用固定 wrapper key 合并为顶层 ObjectSchema
        var combinedSchema = new ObjectSchema();
        combinedSchema.addProperty(PARAMETERS_KEY, parameterSchema);
        // 去掉 body 侧 resolve 阶段可能产生的单层包装, 再放入 _body
        var unwrappedBodySchema = unwrapIfNeeded(requestBodySchema);
        combinedSchema.addProperty(REQUEST_BODY_KEY, unwrappedBodySchema);
        combinedSchema.required(List.of(PARAMETERS_KEY, REQUEST_BODY_KEY));
        return combinedSchema;
    }

    /**
     * 将 MCP {@code tools/call} 的 arguments 分解为 {@link HttpRequestSpec}。
     * <p>
     * 这是 {@link #compose} 的逆操作：根据 Operation 定义与 arguments 结构,
     * 还原 path/query/header 参数及 JSON requestBody, 供后续 HTTP 客户端组装请求。
     * </p>
     *
     * <h3>决策流程</h3>
     * <pre>
     * callToolRequest == null          → empty()
     * arguments 含 _params 或 _body    → Wrapped 格式解析
     * 仅 hasParameters                  → Direct：arguments 即扁平参数 Map
     * 仅 hasRequestBody                 → Direct：arguments 即 body 内容
     * 其他（两者都有/都没有且无 wrapper）→ warn + empty()
     * </pre>
     *
     * @param callToolRequest MCP 工具调用请求；为 {@code null} 时返回空模型
     * @param operation       与 Tool 绑定的 OpenAPI Operation, 用于校验与参数归类
     * @return 分解后的 HTTP 请求规格, 含按类型分组的 parameters 与可选 body
     */
    public HttpRequestSpec decompose(McpSchema.@Nullable CallToolRequest callToolRequest, Operation operation) {
        if (Objects.isNull(callToolRequest)) {
            return HttpRequestSpec.empty();
        }

        // 依据 Operation 声明判断是否存在 parameters / 支持的 JSON body
        var hasParameters = Objects.nonNull(operation.getParameters()) && !operation.getParameters().isEmpty();
        var hasRequestBody = Objects.nonNull(getSupportedRequestBodySchema(operation.getRequestBody()));
        var arguments = callToolRequest.arguments();

        // 检测是否为 compose 产生的 Wrapped 格式
        boolean isWrappedFormat = arguments.containsKey(PARAMETERS_KEY) || arguments.containsKey(REQUEST_BODY_KEY);

        if (isWrappedFormat) {
            Map<String, Object> parameters = null;
            Object requestBody = null;

            // --- 解析 _params ---
            if (arguments.containsKey(PARAMETERS_KEY)) {
                if (!hasParameters) {
                    // Operation 未声明 parameters, 但调用方传了 _params
                    log.warn(
                        "Parameters are not defined in the operation, but '{}' key is present in the input arguments."
                            + " Skipping passed parameters. Please verify if this is the intended behavior.",
                        PARAMETERS_KEY
                    );
                } else if (!(arguments.get(PARAMETERS_KEY) instanceof Map)) {
                    // _params 类型不合法, 必须是 Map
                    log.warn(
                        "Expected '{}' to be a Map when decomposing the input arguments. Skipping parameters.",
                        PARAMETERS_KEY
                    );
                } else {
                    try {
                        @SuppressWarnings("unchecked")
                        var parametersMap = (Map<String, Object>) arguments.get(PARAMETERS_KEY);
                        parameters = parametersMap;
                    } catch (ClassCastException exception) {
                        log.warn("Failed to cast '{}' to Map<String, Object>. Skipping parameters.", PARAMETERS_KEY);
                    }
                }
            }

            // --- 解析 _body ---
            if (arguments.containsKey(REQUEST_BODY_KEY)) {
                if (!hasRequestBody) {
                    // Operation 未声明 body, 但调用方传了 _body
                    log.warn(
                        "Request body is not defined in the operation, but '{}' key is present in the input arguments."
                            + " Skipping passed request body. Please verify if this is the intended behavior.",
                        REQUEST_BODY_KEY
                    );
                } else {
                    // 非 object body（string / array 等）也直接接受任意类型值
                    requestBody = arguments.get(REQUEST_BODY_KEY);
                }
            }

            return HttpRequestSpec.withParametersAndBodyContent(organizeParameters(parameters, operation), requestBody);
        }

        // --- Direct 格式：无 wrapper key, 按 Operation 结构推断 ---
        if (hasParameters && !hasRequestBody) {
            // 整个 arguments 视为扁平参数 Map
            return HttpRequestSpec.withParameters(organizeParameters(arguments, operation));
        }

        if (!hasParameters && hasRequestBody) {
            // 整个 arguments 视为 requestBody 内容
            return HttpRequestSpec.withRequestBody(arguments);
        }

        // 两者都有或都没有时, Direct 格式无法可靠区分, compose 侧应已使用 Wrapped 格式
        log.warn("Cannot reliably determine schema type for operation."
            + " Both parameters and request body exist, or neither exists."
            + " Returning blank model.");
        return HttpRequestSpec.empty();
    }

    /**
     * 将扁平参数 Map 按 OpenAPI parameter {@code in} 字段归类为 path / query / header / cookie。
     * <p>
     * 仅处理 Operation 中声明且在 {@code parameters} Map 中有值的参数；
     * 未出现在 Map 中的参数视为未传；不支持的 {@code in} 类型（如 form）记录 warn 后跳过。
     * </p>
     *
     * @param parameters 参数名 → 参数值；可为 {@code null} 或空 Map
     * @param operation    提供 parameter 元数据（name、in、required 等）
     * @return 按 HTTP 位置分组的 {@link Parameters}；无有效输入时返回 {@link Parameters#empty()}
     */
    private Parameters organizeParameters(@Nullable Map<String, Object> parameters, Operation operation) {
        if (Objects.isNull(parameters) || parameters.isEmpty()) {
            return Parameters.empty();
        }

        if (Objects.isNull(operation.getParameters())) {
            return Parameters.empty();
        }

        var pathParameters = new HashMap<String, Object>();
        var queryParameters = new HashMap<String, Object>();
        var headerParameters = new HashMap<String, Object>();
        var cookieParameters = new HashMap<String, Object>();

        operation.getParameters().forEach(parameter -> {
            var parameterName = parameter.getName();
            var parameterIn = parameter.getIn();
            var inputValue = parameters.get(parameterName);

            // 调用方未传该参数, 跳过
            if (Objects.isNull(inputValue)) {
                return;
            }

            switch (parameterIn) {
                case Parameters.PATH -> pathParameters.put(parameterName, inputValue);
                case Parameters.QUERY -> queryParameters.put(parameterName, inputValue);
                case Parameters.HEADER -> headerParameters.put(parameterName, inputValue);
                case Parameters.COOKIE -> cookieParameters.put(parameterName, inputValue);
                default -> log.warn(
                    "Unsupported parameter type '{}' for parameter '{}'."
                        + " Skipping parameter decomposition.",
                    parameterIn,
                    parameterName
                );
            }
        });

        return new Parameters(pathParameters, queryParameters, headerParameters, cookieParameters);
    }

    /**
     * 解析 Operation 中所有所支持 parameters 的 JSON Schema。
     * <p>
     * 遍历 {@link Operation#getParameters()}, 仅保留 {@link HttpRequestSpec#SUPPORTED_PARAMETER_TYPES}
     * 中的类型（path / query / header / cookie）。若 parameter schema 无 description 而 parameter 本身有
     * description, 则克隆 schema 后补全描述, 避免修改原始 OpenAPI 模型。
     * </p>
     *
     * <h3>示例：原始 OpenAPI parameters → 解析后的 input schema</h3>
     *
     * <p><strong>原始数据</strong>（Operation {@code GET /orders/{orderId}} 片段）：</p>
     * <pre>{@code
     * parameters:
     *   - name: orderId
     *     in: path
     *     required: true
     *     description: 订单唯一标识
     *     schema: { type: string, format: uuid }
     *   - name: page
     *     in: query
     *     required: false
     *     schema: { type: integer, minimum: 1, default: 1 }
     *   - name: status
     *     in: query
     *     required: true
     *     description: 订单状态筛选
     *     schema:
     *       type: string
     *       enum: [PENDING, PAID, SHIPPED, CANCELLED]
     *   - name: X-Trace-Id
     *     in: header
     *     required: false
     *     schema: { type: string }
     *   - name: sessionToken
     *     in: cookie
     *     schema: { type: string }
     * }</pre>
     *
     * <p><strong>解析后</strong>（返回的 {@link ObjectSchema},
     * {@code status} 的 description 从 parameter 层补入 schema）：</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "orderId": {
     *       "type": "string",
     *       "format": "uuid",
     *       "description": "订单唯一标识"
     *     },
     *     "page": {
     *       "type": "integer",
     *       "minimum": 1,
     *       "default": 1
     *     },
     *     "status": {
     *       "type": "string",
     *       "enum": ["PENDING", "PAID", "SHIPPED", "CANCELLED"],
     *       "description": "订单状态筛选"
     *     },
     *     "X-Trace-Id": {
     *       "type": "string"
     *     },
     *     "sessionToken": {
     *       "type": "string"
     *     }
     *   },
     *   "required": ["orderId", "status"]
     * }
     * }</pre>
     *
     * @param openApiOperation catalog 操作元数据
     * @return 包含所有有效 parameter properties 的 {@link ObjectSchema}；
     *         无 parameters 或全部 unsupported 时返回 {@code null}
     */
    private @Nullable ObjectSchema resolveParameterSchema(OpenApiOperation openApiOperation) {
        var operation = openApiOperation.operation();

        // Operation 未声明任何 parameters, 无需生成 schema
        if (Objects.isNull(operation.getParameters()) || operation.getParameters().isEmpty()) {
            return null;
        }

        // 将所有受支持 parameter 的 schema 合并为单一 ObjectSchema
        var inputSchema = new ObjectSchema();
        for (var parameter : operation.getParameters()) {
            var parameterName = parameter.getName();
            var parameterIn = parameter.getIn();

            // 仅处理 path / query / header / cookie
            if (SUPPORTED_PARAMETER_TYPES.contains(parameterIn)) {
                var originalSchema = parameter.getSchema();
                if (Objects.isNull(originalSchema)) {
                    continue;
                }
                Schema<?> schemaToUse;

                // parameter 层有 description 而 schema 层没有时, 克隆后补全, 避免污染原始 OpenAPI 模型
                if (Objects.isNull(originalSchema.getDescription()) && Objects.nonNull(parameter.getDescription())) {
                    schemaToUse = cloneSchema(originalSchema);
                    schemaToUse.setDescription(parameter.getDescription());
                } else {
                    schemaToUse = originalSchema;
                }

                // 以 parameter name 作为 JSON property key 写入合并 schema
                inputSchema.addProperty(parameterName, schemaToUse);

                // OpenAPI required=true 的参数标记为 input schema 的 required 字段
                if (TRUE.equals(parameter.getRequired())) {
                    inputSchema.addRequiredItem(parameterName);
                }
            }
        }

        // 全部为 unsupported 或未解析出任何 property 时, 视为无有效 parameters
        if (Objects.isNull(inputSchema.getProperties()) || inputSchema.getProperties().isEmpty()) {
            return null;
        }

        return inputSchema;
    }

    /**
     * 解析 Operation requestBody 的 JSON Schema。
     * <p>
     * 仅处理 {@link HttpRequestSpec#SUPPORTED_MEDIA_TYPE}（{@code application/json}）对应的 content。
     * 若 requestBody schema 无 description 而 requestBody 本身有 description, 则克隆 schema 后补全描述,
     * 避免修改原始 OpenAPI 模型。
     * </p>
     * <p>返回策略：</p>
     * <ul>
     *   <li><strong>object 且含 properties</strong> — 直接返回（必要时补 requestBody description）</li>
     *   <li><strong>非 object 或 object 无 properties</strong> — 用 {@value #REQUEST_BODY_KEY}
     *       包装为 {@link ObjectSchema}；后续 {@link #compose} 合并时会 {@link #unwrapIfNeeded} 还原</li>
     * </ul>
     *
     * <h3>示例：原始 OpenAPI requestBody → 解析后的 input schema</h3>
     *
     * <p><strong>原始数据</strong>（Operation {@code POST /orders} 片段）：</p>
     * <pre>{@code
     * requestBody:
     *   required: true
     *   description: 创建订单请求体
     *   content:
     *     application/json:
     *       schema:
     *         type: object
     *         required: [customerId, items]
     *         properties:
     *           customerId:
     *             type: string
     *             format: uuid
     *           items:
     *             type: array
     *             items:
     *               type: object
     *               required: [sku, quantity]
     *               properties:
     *                 sku:      { type: string }
     *                 quantity: { type: integer, minimum: 1 }
     *           remark:
     *             type: string
     *     application/xml:              # 非 SUPPORTED_MEDIA_TYPE, 忽略
     *       schema: { type: object }
     * }</pre>
     *
     * <p><strong>解析后</strong>（标准 object schema 直接返回, description 从 requestBody 层补入）：</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "description": "创建订单请求体",
     *   "required": ["customerId", "items"],
     *   "properties": {
     *     "customerId": {
     *       "type": "string",
     *       "format": "uuid"
     *     },
     *     "items": {
     *       "type": "array",
     *       "items": {
     *         "type": "object",
     *         "required": ["sku", "quantity"],
     *         "properties": {
     *           "sku":      { "type": "string" },
     *           "quantity": { "type": "integer", "minimum": 1 }
     *         }
     *       }
     *     },
     *     "remark": {
     *       "type": "string"
     *     }
     *   }
     * }
     * }</pre>
     *
     * <p>若 schema 为 {@code type: string} 等非 object 类型, 则包装为
     * {@code { "type": "object", "required": ["_body"], "properties": { "_body": { ... } } }}。</p>
     *
     * @param openApiOperation catalog 操作元数据
     * @return requestBody schema；无 body 或不支持的 content type 时返回 {@code null}
     */
    private @Nullable Schema<?> resolveRequestBodySchema(OpenApiOperation openApiOperation) {
        var operation = openApiOperation.operation();
        var requestBody = operation.getRequestBody();

        // Operation 未声明 requestBody, 无需生成 schema
        if (Objects.isNull(requestBody)) {
            return null;
        }

        // 从 content 中提取 application/json 对应的 schema
        var originalSchema = getSupportedRequestBodySchema(requestBody);
        if (Objects.isNull(originalSchema)) {
            log.warn(
                "Unsupported content types in request body for operation '{}'. Skipping.",
                operation.getOperationId()
            );
            return null;
        }

        // 判断是否为「带 properties 的 object schema」（兼容 OAS 3.0 type 与 3.1 types）
        var isObjectType = "object".equals(originalSchema.getType())
            || (Objects.nonNull(originalSchema.getTypes())
            && originalSchema.getTypes().contains("object"));
        var hasProperties = originalSchema.getProperties() != null
            && !originalSchema.getProperties().isEmpty();

        if (isObjectType && hasProperties) {
            // requestBody 层有 description 而 schema 层没有时, 克隆后补全, 避免污染原始 OpenAPI 模型
            if (Objects.isNull(originalSchema.getDescription()) && Objects.nonNull(requestBody.getDescription())) {
                var clonedSchema = cloneSchema(originalSchema);
                clonedSchema.setDescription(requestBody.getDescription());
                return clonedSchema;
            }

            return originalSchema;
        }

        // 非 object 或 object 无 properties（如 string、array、allOf）— 用 _body 包装为单层 ObjectSchema
        log.warn(
            "Request body schema is not an ObjectSchema with properties for operation '{}'. Wrapping in ObjectSchema.",
            operation.getOperationId()
        );
        var objectSchema = new ObjectSchema();
        objectSchema.setDescription(requestBody.getDescription());
        objectSchema.addProperty(REQUEST_BODY_KEY, originalSchema);
        objectSchema.addRequiredItem(REQUEST_BODY_KEY);
        return objectSchema;
    }

    /**
     * 去掉 requestBody resolve 阶段产生的单层 {@link #REQUEST_BODY_KEY} 包装。
     * <p>
     * 当 Operation 同时有 parameters 与 body 时, {@link #resolveRequestBodySchema} 可能对
     * 非 object body 产生 {@code { "_body": <actualSchema> }} 结构；合并进顶层 schema 前
     * 需 unwrap, 避免出现 {@code _body._body} 双重嵌套。
     * </p>
     *
     * @param schema 待检查的 schema, 通常来自 {@link #resolveRequestBodySchema}
     * @return 若满足「唯一 property 为 {@link #REQUEST_BODY_KEY}」则返回内层 schema, 否则原样返回
     */
    private Schema<?> unwrapIfNeeded(Schema<?> schema) {
        if (schema instanceof ObjectSchema objectSchema
            && Objects.nonNull(objectSchema.getProperties())
            && objectSchema.getProperties().size() == 1
            && objectSchema.getProperties().containsKey(REQUEST_BODY_KEY)
        ) {
            return objectSchema.getProperties().get(REQUEST_BODY_KEY);
        }

        return schema;
    }

    /**
     * 深拷贝 OpenAPI {@link Schema}, 避免 setDescription 等修改污染原始模型。
     * <p>
     * 第二个参数 {@code resolveFully} 传 {@code true}, 按 OpenAPI 3.1 语义解析 clone。
     * </p>
     *
     * @param originalSchema 待克隆的 schema
     * @return 独立副本, 可安全修改 description 等字段
     */
    private Schema<?> cloneSchema(Schema<?> originalSchema) {
        return AnnotationsUtils.clone(originalSchema, true);
    }

    /**
     * 从 {@link RequestBody} 中提取所支持的 media type 对应的 schema。
     * <p>
     * 当前仅查找 {@link HttpRequestSpec#SUPPORTED_MEDIA_TYPE}（{@code application/json}）。
     * 若 content 中无该 type, 返回 {@code null}。
     * </p>
     *
     * @param requestBody OpenAPI requestBody 定义；可为 {@code null}
     * @return JSON body 的 {@link Schema}, 或 {@code null}
     */
    private @Nullable Schema<?> getSupportedRequestBodySchema(@Nullable RequestBody requestBody) {
        return Optional.ofNullable(requestBody)
            .map(RequestBody::getContent)
            .map(c -> c.get(HttpRequestSpec.SUPPORTED_MEDIA_TYPE.toString()))
            .map(MediaType::getSchema)
            .orElse(null);
    }
}
