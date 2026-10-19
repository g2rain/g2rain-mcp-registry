package com.g2rain.mcp.registry.service.impl;


import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.id.IdGenerator;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.basis.dto.ResourceApiSelectDto;
import com.g2rain.basis.vo.ResourceApiVo;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.client.ResourceApiClient;
import com.g2rain.mcp.registry.dao.McpExternalServiceDao;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.BuiltInSyncDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.OpenApiConfirmDto;
import com.g2rain.mcp.registry.dto.OpenApiPreviewDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import com.g2rain.mcp.registry.openapi.OpenApiDocumentConverter;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import com.g2rain.mcp.registry.runtime.BuiltInToolLoader;
import com.g2rain.mcp.registry.runtime.ExtensionToolLoader;
import com.g2rain.mcp.registry.runtime.ServiceEndpointCache;
import com.g2rain.mcp.registry.service.McpToolNameGuard;
import com.g2rain.mcp.registry.vo.McpToolImportResultVo;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 三种工具来源的导入。
 * <p>
 * 自有系统按前端提交的资源接口 id（或该服务全选）向 basis 取文档再转换。
 * 扩展系统和客户系统先预览整份文档，确认时只写入勾选的接口。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Service
public class McpToolImportServiceImpl {

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    @Resource(name = "mcpExternalServiceDao")
    private McpExternalServiceDao mcpExternalServiceDao;

    @Resource
    private ResourceApiClient resourceApiClient;

    @Resource
    private OpenApiDocumentConverter openApiDocumentConverter;

    @Resource
    private BuiltInToolLoader builtInToolLoader;

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
     * @param dto 文档文本与命名上下文
     * @return 按接口展开的草稿，含失败原因
     */
    public List<ConvertedOperation> preview(OpenApiPreviewDto dto) {
        Asserts.isTrue(Objects.nonNull(dto) && Strings.isNotBlank(dto.getDocument()),
            SystemErrorCode.PARAM_REQUIRED, "document");
        String resourceType = dto.getResourceType();
        String serviceCode = dto.getServiceCode();
        String contextPath = dto.getContextPath();
        if (Strings.isNotBlank(resourceType) && Strings.isNotBlank(serviceCode) && Strings.isBlank(contextPath)) {
            Long organId = PrincipalContextHolder.getOrganId();
            contextPath = resolveContextPath(resourceType, serviceCode, organId);
        }
        return openApiDocumentConverter.convert(dto.getDocument(), resourceType, serviceCode, contextPath);
    }

    /**
     * 把勾选的扩展或客户接口写入工具表。同一服务下 method + path 已存在则更新。
     *
     * @param dto 服务编码、资源类型、勾选结果
     * @return 新增、更新、跳过条数
     */
    @Transactional(rollbackFor = Exception.class)
    public McpToolImportResultVo confirm(OpenApiConfirmDto dto) {
        Asserts.isTrue(Objects.nonNull(dto), SystemErrorCode.PARAM_REQUIRED, "dto");
        Asserts.isTrue(Strings.isNotBlank(dto.getServiceCode()), SystemErrorCode.PARAM_REQUIRED, "serviceCode");
        Asserts.isTrue(ResourceType.EXTENSION.name().equals(dto.getResourceType())
                || ResourceType.CUSTOMER.name().equals(dto.getResourceType()),
            SystemErrorCode.PARAM_VAL_INVALID, "resourceType");
        if (Collections.isEmpty(dto.getOperations())) {
            return new McpToolImportResultVo(0, 0, 0);
        }
        Long organId = PrincipalContextHolder.getOrganId();
        Asserts.isTrue(Objects.nonNull(organId), SystemErrorCode.PARAM_REQUIRED, "organId");
        String contextPath = resolveContextPath(dto.getResourceType(), dto.getServiceCode(), organId);
        Map<OpKey, McpToolProfilePo> existingByKey = loadExternalMap(dto.getServiceCode(), dto.getResourceType(), organId);
        Set<String> occupiedNames = mcpToolNameGuard.loadOccupiedNames(dto.getResourceType(), organId);
        UpsertCount count = new UpsertCount();
        for (ConvertedOperation operation : dto.getOperations()) {
            if (Objects.isNull(operation) || Strings.isNotBlank(operation.problem())
                || Strings.isBlank(operation.inputSchema())) {
                count.skipped++;
                continue;
            }
            OpKey key = new OpKey(operation.method(), operation.path());
            McpToolProfilePo existing = existingByKey.get(key);
            upsert(existing, organId, dto.getResourceType(), dto.getServiceCode(), null,
                contextPath, operation, occupiedNames, count);
        }
        if (ResourceType.EXTENSION.name().equals(dto.getResourceType())) {
            extensionToolLoader.reload(dto.getServiceCode());
        }
        return count.toVo();
    }

    /**
     * 同步自有系统接口。只处理当前服务下前端选中的 id；{@code importAll} 时取该服务全部文档。
     *
     * @param dto 服务编码与选择范围
     * @return 新增、更新、跳过条数
     */
    @Transactional(rollbackFor = Exception.class)
    public McpToolImportResultVo syncBuiltIn(BuiltInSyncDto dto) {
        Asserts.isTrue(Objects.nonNull(dto) && Strings.isNotBlank(dto.getServiceCode()),
            SystemErrorCode.PARAM_REQUIRED, "serviceCode");
        Asserts.isTrue(dto.isImportAll() || Collections.isNotEmpty(dto.getResourceApiIds()),
            SystemErrorCode.PARAM_REQUIRED, "resourceApiIds");
        Long organId = PrincipalContextHolder.getOrganId();
        Asserts.isTrue(Objects.nonNull(organId), SystemErrorCode.PARAM_REQUIRED, "organId");
        List<ResourceApiVo> documents = listWithDocument(dto.getServiceCode(),
            dto.isImportAll() ? List.of() : dto.getResourceApiIds());
        Map<Long, McpToolProfilePo> existingBySourceId = loadBuiltInMap(dto.getServiceCode());
        String contextPath = resolveContextPath(ResourceType.BUILT_IN.name(), dto.getServiceCode(), organId);
        Set<String> occupiedNames = mcpToolNameGuard.loadOccupiedNames(ResourceType.BUILT_IN.name(), organId);
        UpsertCount count = new UpsertCount();
        for (ResourceApiVo document : documents) {
            if (Objects.isNull(document) || Strings.isBlank(document.getOpenapiDocument())) {
                count.skipped++;
                continue;
            }
            List<ConvertedOperation> converted = openApiDocumentConverter.convert(
                document.getOpenapiDocument(), ResourceType.BUILT_IN.name(), dto.getServiceCode(), contextPath);
            if (converted.isEmpty() || Strings.isNotBlank(converted.getFirst().problem())) {
                count.skipped++;
                continue;
            }
            ConvertedOperation operation = converted.getFirst();
            McpToolProfilePo existing = existingBySourceId.get(document.getId());
            upsert(existing, organId, ResourceType.BUILT_IN.name(), dto.getServiceCode(), document.getId(),
                contextPath, operation, occupiedNames, count);
        }
        builtInToolLoader.reload(dto.getServiceCode());
        return count.toVo();
    }

    private List<ResourceApiVo> listWithDocument(String serviceCode, List<Long> resourceApiIds) {
        boolean byIds = Collections.isNotEmpty(resourceApiIds);
        Asserts.isTrue(byIds || Strings.isNotBlank(serviceCode), SystemErrorCode.PARAM_REQUIRED, "serviceCode");
        ResourceApiSelectDto selectDto = new ResourceApiSelectDto();
        if (Strings.isNotBlank(serviceCode)) {
            selectDto.setServiceCode(serviceCode);
        }
        if (byIds) {
            selectDto.setIds(new HashSet<>(resourceApiIds));
        }
        Result<List<ResourceApiVo>> result = resourceApiClient.selectListWithDocument(selectDto);
        Asserts.isTrue(Objects.nonNull(result) && result.isSuccess(),
            SystemErrorCode.SYSTEM_INTERNAL_ERROR, "basis resource_api");
        return Objects.isNull(result.getData()) ? List.of() : result.getData();
    }

    private void upsert(McpToolProfilePo existing, Long organId, String resourceType, String serviceCode,
                        Long sourceId, String contextPath, ConvertedOperation operation,
                        Set<String> occupiedNames, UpsertCount count) {
        if (Objects.isNull(existing)) {
            insert(organId, resourceType, serviceCode, sourceId, contextPath, operation, occupiedNames);
            count.created++;
        } else {
            update(existing, contextPath, operation, occupiedNames);
            count.updated++;
        }
    }

    private Map<Long, McpToolProfilePo> loadBuiltInMap(String serviceCode) {
        McpToolProfileSelectDto selectDto = new McpToolProfileSelectDto();
        selectDto.setResourceType(ResourceType.BUILT_IN.name());
        selectDto.setServiceCode(serviceCode);
        List<McpToolProfilePo> rows = mcpToolProfileDao.selectListWithoutIsolation(selectDto);
        Map<Long, McpToolProfilePo> bySourceId = new HashMap<>();
        if (Collections.isEmpty(rows)) {
            return bySourceId;
        }
        for (McpToolProfilePo row : rows) {
            if (Objects.nonNull(row.getSourceId())) {
                bySourceId.put(row.getSourceId(), row);
            }
        }
        return bySourceId;
    }

    private Map<OpKey, McpToolProfilePo> loadExternalMap(String serviceCode, String resourceType, Long organId) {
        McpToolProfileSelectDto selectDto = new McpToolProfileSelectDto();
        selectDto.setResourceType(resourceType);
        selectDto.setServiceCode(serviceCode);
        List<McpToolProfilePo> rows;
        if (ResourceType.CUSTOMER.name().equals(resourceType)) {
            selectDto.setOrganId(organId);
            rows = mcpToolProfileDao.selectList(selectDto);
        } else {
            rows = mcpToolProfileDao.selectListWithoutIsolation(selectDto);
        }
        Map<OpKey, McpToolProfilePo> byKey = new HashMap<>();
        if (Collections.isEmpty(rows)) {
            return byKey;
        }
        for (McpToolProfilePo row : rows) {
            byKey.put(new OpKey(row.getMethod(), row.getPath()), row);
        }
        return byKey;
    }

    private String resolveContextPath(String resourceType, String serviceCode, Long organId) {
        if (ResourceType.BUILT_IN.name().equals(resourceType)) {
            ServiceEndpointCache.Endpoint endpoint = serviceEndpointCache.endpoint(serviceCode);
            return Objects.nonNull(endpoint) ? Objects.toString(endpoint.contextPath(), "") : "";
        }
        McpExternalServiceSelectDto selectDto = new McpExternalServiceSelectDto();
        selectDto.setCode(serviceCode);
        selectDto.setResourceType(resourceType);
        List<McpExternalServicePo> rows;
        if (ResourceType.CUSTOMER.name().equals(resourceType)) {
            selectDto.setOrganId(organId);
            rows = mcpExternalServiceDao.selectList(selectDto);
        } else {
            rows = mcpExternalServiceDao.selectListWithoutIsolation(selectDto);
        }
        Asserts.isTrue(Collections.isNotEmpty(rows), SystemErrorCode.DATA_NOT_EXISTS, serviceCode);
        return Objects.toString(rows.getFirst().getContextPath(), "");
    }

    private void insert(Long organId, String resourceType, String serviceCode, Long sourceId,
                        String contextPath, ConvertedOperation operation, Set<String> occupiedNames) {
        McpToolProfilePo entity = new McpToolProfilePo();
        entity.setId(idGenerator.generateId());
        entity.setOrganId(organId);
        applySource(entity, resourceType, serviceCode, sourceId, contextPath, operation);
        entity.setTitle(truncate(Strings.isNotBlank(operation.name()) ? operation.name() : entity.getName(), 128));
        entity.setDescription(truncate(operation.description(), 500));
        mcpToolNameGuard.claim(occupiedNames, entity.getName());
        entity.setStatus(ResourceStatus.ACTIVE.name());
        LocalDateTime now = Moments.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        int success = mcpToolProfileDao.insert(entity);
        Asserts.greaterThan(success, 0, SystemErrorCode.CREATE_DATA_ERROR);
    }

    private void update(McpToolProfilePo existing, String contextPath, ConvertedOperation operation,
                        Set<String> occupiedNames) {
        String previousName = existing.getName();
        applySource(existing, existing.getResourceType(), existing.getServiceCode(), existing.getSourceId(),
            contextPath, operation);
        if (Strings.isNotBlank(previousName)) {
            occupiedNames.remove(previousName);
        }
        mcpToolNameGuard.claim(occupiedNames, existing.getName());
        existing.setUpdateTime(Moments.now());
        int success = mcpToolProfileDao.update(existing);
        Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, existing.getId());
    }

    /** 只写来源字段（含按公式生成的 name） */
    private void applySource(McpToolProfilePo entity, String resourceType, String serviceCode, Long sourceId,
                             String contextPath, ConvertedOperation operation) {
        String toolName = resolveToolName(resourceType, serviceCode, contextPath, operation);
        entity.setName(truncate(toolName, 256));
        entity.setResourceType(resourceType);
        entity.setServiceCode(serviceCode);
        entity.setSourceId(sourceId);
        entity.setMethod(operation.method());
        entity.setPath(operation.path());
        entity.setOpenapiOperation(operation.openapiOperation());
        entity.setInputSchema(operation.inputSchema());
    }

    private String resolveToolName(String resourceType, String serviceCode, String contextPath,
                                   ConvertedOperation operation) {
        String method = operation.method().trim().toUpperCase(Locale.ROOT);
        String serviceSegment = EndpointStrategy.resolveServiceSegment(resourceType, serviceCode);
        return EndpointStrategy.build(method, serviceSegment, contextPath, operation.path());
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
