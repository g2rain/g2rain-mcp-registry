package com.g2rain.mcp.registry.service.impl;

import com.g2rain.basis.dto.ResourceApiSelectDto;
import com.g2rain.basis.vo.ResourceApiVo;
import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.id.IdGenerator;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.mcp.registry.client.ResourceApiClient;
import com.g2rain.mcp.registry.dao.McpExternalServiceDao;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.NativeSyncDto;
import com.g2rain.mcp.registry.dto.OpenApiConfirmDto;
import com.g2rain.mcp.registry.dto.OpenApiPreviewDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import com.g2rain.mcp.registry.openapi.OpenApiDocumentConverter;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import com.g2rain.mcp.registry.runtime.ExtensionToolLoader;
import com.g2rain.mcp.registry.runtime.NativeToolLoader;
import com.g2rain.mcp.registry.runtime.ServiceEndpointCache;
import com.g2rain.mcp.registry.service.McpToolNameGuard;
import com.g2rain.mcp.registry.vo.McpToolImportResultVo;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 三种工具来源的导入。
 * <p>
 * 原生系统按前端提交的资源接口 id（或该服务全选）向 basis 取单接口文档再转换。
 * 扩展系统和租户系统先预览整份文档，确认时只写入勾选的接口。资源类型取自服务本身，工具名由服务端按
 * {@link EndpointStrategy} 生成。同一服务下 method + path 已存在时更新，否则新增；工具名被其他服务占用时拒绝。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Service
public class McpToolImportServiceImpl {

    private static final JsonCodec JSON = JsonCodecFactory.instance();

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    @Resource(name = "mcpExternalServiceDao")
    private McpExternalServiceDao mcpExternalServiceDao;

    @Resource
    private ResourceApiClient resourceApiClient;

    @Resource
    private OpenApiDocumentConverter openApiDocumentConverter;

    @Resource
    private NativeToolLoader nativeToolLoader;

    @Resource
    private ExtensionToolLoader extensionToolLoader;

    @Resource
    private ServiceEndpointCache serviceEndpointCache;

    @Resource
    private McpToolNameGuard mcpToolNameGuard;

    private IdGenerator idGenerator;

    /**
     * @param idGenerator 主键生成器，测试环境可缺省
     */
    @Qualifier("idGenerator")
    @Autowired(required = false)
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 预览整份 OpenAPI / Swagger，不写库。
     *
     * @param dto 文档文本与外部服务
     * @return 按接口展开的草稿，含失败原因
     */
    public List<ConvertedOperation> preview(OpenApiPreviewDto dto) {
        Asserts.isTrue(Objects.nonNull(dto) && Strings.isNotBlank(dto.getDocument()),
            SystemErrorCode.PARAM_REQUIRED, "document");
        McpExternalServicePo service = requireService(dto.getServiceId());
        return openApiDocumentConverter.convert(dto.getDocument(), service.getResourceType());
    }

    /**
     * 把勾选的扩展或租户接口写入工具表。同一服务下 method + path 已存在则更新。
     *
     * @param dto 外部服务与勾选结果
     * @return 新增、更新、跳过条数
     */
    @Transactional(rollbackFor = Exception.class)
    public McpToolImportResultVo confirm(OpenApiConfirmDto dto) {
        Asserts.isTrue(Objects.nonNull(dto), SystemErrorCode.PARAM_REQUIRED, "dto");
        McpExternalServicePo service = requireService(dto.getServiceId());
        if (Collections.isEmpty(dto.getOperations())) {
            return new McpToolImportResultVo(0, 0, 0);
        }

        Long organId = requireOrganId();
        String resourceType = service.getResourceType();
        Map<OpKey, McpToolProfilePo> existingByKey = loadByKey(service.getId());
        Map<String, McpToolProfilePo> owners = mcpToolNameGuard.loadOwners(resourceType, organId);
        UpsertCount count = new UpsertCount();
        for (ConvertedOperation operation : dto.getOperations()) {
            if (Objects.isNull(operation) || Strings.isNotBlank(operation.problem())
                || Strings.isBlank(operation.inputSchema()) || Strings.isBlank(operation.openapiDocument())) {
                count.skipped++;
                continue;
            }

            McpToolProfilePo existing = existingByKey.get(new OpKey(operation.method(), operation.path()));
            upsert(existing, organId, resourceType, service.getId(), null, operation, owners, count);
        }

        if (ResourceType.EXTENSION.name().equals(resourceType)) {
            extensionToolLoader.reload(service.getId());
        }
        return count.toVo();
    }

    /**
     * 同步原生接口。只处理当前服务下前端选中的 id；{@code importAll} 时取该服务全部文档。
     *
     * @param dto 服务注册主键与选择范围
     * @return 新增、更新、跳过条数
     */
    @Transactional(rollbackFor = Exception.class)
    public McpToolImportResultVo syncNative(NativeSyncDto dto) {
        Asserts.isTrue(Objects.nonNull(dto) && Objects.nonNull(dto.getServiceId()),
            SystemErrorCode.PARAM_REQUIRED, "serviceId");
        Asserts.isTrue(dto.isImportAll() || Collections.isNotEmpty(dto.getResourceApiIds()),
            SystemErrorCode.PARAM_REQUIRED, "resourceApiIds");
        ServiceRegistryVo registry = serviceEndpointCache.get(dto.getServiceId());
        Asserts.isTrue(Objects.nonNull(registry), SystemErrorCode.DATA_NOT_EXISTS, dto.getServiceId());

        Long organId = requireOrganId();
        List<ResourceApiVo> documents = listWithDocument(registry.getServiceCode(),
            dto.isImportAll() ? List.of() : dto.getResourceApiIds());
        Map<Long, McpToolProfilePo> existingBySourceId = loadNativeMap(dto.getServiceId());
        Map<String, McpToolProfilePo> owners = mcpToolNameGuard.loadOwners(ResourceType.NATIVE.name(), organId);
        UpsertCount count = new UpsertCount();
        for (ResourceApiVo document : documents) {
            if (Objects.isNull(document) || Strings.isBlank(document.getOpenapiDocument())) {
                count.skipped++;
                continue;
            }

            List<ConvertedOperation> converted = openApiDocumentConverter.convert(
                document.getOpenapiDocument(), ResourceType.NATIVE.name());
            if (converted.isEmpty() || Strings.isNotBlank(converted.getFirst().problem())) {
                count.skipped++;
                continue;
            }

            McpToolProfilePo existing = existingBySourceId.get(document.getId());
            upsert(existing, organId, ResourceType.NATIVE.name(), dto.getServiceId(), document.getId(),
                converted.getFirst(), owners, count);
        }

        nativeToolLoader.reload(dto.getServiceId());
        return count.toVo();
    }

    /**
     * 取外部服务；租户系统只能操作本机构的服务。
     */
    private McpExternalServicePo requireService(Long serviceId) {
        Asserts.isTrue(Objects.nonNull(serviceId), SystemErrorCode.PARAM_REQUIRED, "serviceId");
        McpExternalServicePo service = mcpExternalServiceDao.selectByIdWithoutIsolation(serviceId);
        Asserts.isTrue(Objects.nonNull(service), SystemErrorCode.DATA_NOT_EXISTS, serviceId);
        if (ResourceType.CUSTOMER.name().equals(service.getResourceType())) {
            Asserts.isTrue(Objects.equals(service.getOrganId(), PrincipalContextHolder.getOrganId()),
                SystemErrorCode.DATA_NOT_EXISTS, serviceId);
        }
        return service;
    }

    private Long requireOrganId() {
        Long organId = PrincipalContextHolder.getOrganId();
        Asserts.isTrue(Objects.nonNull(organId), SystemErrorCode.PARAM_REQUIRED, "organId");
        return organId;
    }

    private List<ResourceApiVo> listWithDocument(String serviceCode, List<Long> resourceApiIds) {
        Asserts.isTrue(Strings.isNotBlank(serviceCode), SystemErrorCode.PARAM_REQUIRED, "serviceCode");
        ResourceApiSelectDto selectDto = new ResourceApiSelectDto();
        selectDto.setServiceCode(serviceCode);
        if (Collections.isNotEmpty(resourceApiIds)) {
            selectDto.setIds(new HashSet<>(resourceApiIds));
        }

        Result<List<ResourceApiVo>> result = resourceApiClient.selectListWithDocument(selectDto);
        Asserts.isTrue(Objects.nonNull(result) && result.isSuccess(),
            SystemErrorCode.SYSTEM_INTERNAL_ERROR, "basis resource_api");
        return Objects.isNull(result.getData()) ? List.of() : result.getData();
    }

    private void upsert(McpToolProfilePo existing, Long organId, String resourceType, Long serviceId,
                        Long sourceId, ConvertedOperation operation, Map<String, McpToolProfilePo> owners,
                        UpsertCount count) {
        if (Objects.isNull(existing)) {
            insert(organId, resourceType, serviceId, sourceId, operation, owners);
            count.created++;
        } else {
            update(existing, operation, owners);
            count.updated++;
        }
    }

    private Map<Long, McpToolProfilePo> loadNativeMap(Long serviceId) {
        Map<Long, McpToolProfilePo> bySourceId = new HashMap<>();
        for (McpToolProfilePo row : listByService(ResourceType.NATIVE.name(), serviceId)) {
            if (Objects.nonNull(row.getSourceId())) {
                bySourceId.put(row.getSourceId(), row);
            }
        }
        return bySourceId;
    }

    private Map<OpKey, McpToolProfilePo> loadByKey(Long serviceId) {
        Map<OpKey, McpToolProfilePo> byKey = new HashMap<>();
        for (McpToolProfilePo row : listByService(null, serviceId)) {
            byKey.put(new OpKey(row.getMethod(), row.getPath()), row);
        }
        return byKey;
    }

    private List<McpToolProfilePo> listByService(String resourceType, Long serviceId) {
        McpToolProfileSelectDto selectDto = new McpToolProfileSelectDto();
        selectDto.setResourceType(resourceType);
        selectDto.setServiceId(serviceId);
        List<McpToolProfilePo> rows = mcpToolProfileDao.selectListWithoutIsolation(selectDto);
        return Collections.isEmpty(rows) ? List.of() : rows;
    }

    private void insert(Long organId, String resourceType, Long serviceId, Long sourceId,
                        ConvertedOperation operation, Map<String, McpToolProfilePo> owners) {
        McpToolProfilePo entity = new McpToolProfilePo();
        entity.setId(idGenerator.generateId());
        entity.setOrganId(organId);
        entity.setResourceType(resourceType);
        entity.setServiceId(serviceId);
        entity.setSourceId(sourceId);
        apply(entity, operation);
        mcpToolNameGuard.claim(owners, entity);
        entity.setStatus(ResourceStatus.ACTIVE.name());
        LocalDateTime now = Moments.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        int success = mcpToolProfileDao.insert(entity);
        Asserts.greaterThan(success, 0, SystemErrorCode.CREATE_DATA_ERROR);
    }

    /**
     * 重复导入：更新标题、说明、文档与 inputSchema；保留 id、状态，名称按规则重新生成。
     */
    private void update(McpToolProfilePo existing, ConvertedOperation operation, Map<String, McpToolProfilePo> owners) {
        apply(existing, operation);
        mcpToolNameGuard.claim(owners, existing);
        existing.setUpdateTime(Moments.now());
        int success = mcpToolProfileDao.update(existing);
        Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, existing.getId());
    }

    /**
     * 写入来源字段。工具名由服务端按单接口文档的 {@code info.title} 与 method + path 重新生成，不取前端传值。
     */
    private void apply(McpToolProfilePo entity, ConvertedOperation operation) {
        String serviceCode = EndpointStrategy.serviceCode(titleOf(operation.openapiDocument()));
        entity.setName(EndpointStrategy.build(entity.getResourceType(), serviceCode, operation.method(), operation.path()));
        entity.setMethod(operation.method());
        entity.setPath(operation.path());
        entity.setTitle(truncate(Strings.isNotBlank(operation.name()) ? operation.name() : entity.getName(), 128));
        entity.setDescription(truncate(operation.description(), 500));
        entity.setOpenapiDocument(operation.openapiDocument());
        entity.setInputSchema(operation.inputSchema());
    }

    private String titleOf(String document) {
        JsonNode info = JSON.str2node(document).get("info");
        JsonNode title = Objects.isNull(info) ? null : info.get("title");
        return Objects.isNull(title) || !title.isString() ? null : title.asString();
    }

    private String truncate(String value, int max) {
        if (Strings.isBlank(value)) {
            return value;
        }

        return value.length() <= max ? value : value.substring(0, max);
    }

    private record OpKey(String method, String path) {
    }

    private static final class UpsertCount {
        private int created;
        private int updated;
        private int skipped;

        private McpToolImportResultVo toVo() {
            return new McpToolImportResultVo(created, updated, skipped);
        }
    }
}
