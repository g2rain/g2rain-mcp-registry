package com.g2rain.mcp.registry.runtime;


import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.StringRedisHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册的调用地址缓存。
 * <p>
 * 内存按服务编码给内置工具拼调用基址。Redis 哈希保存同一份地址，服务注册变更时改对应字段。
 * Redis 写失败只警告，内存仍以本次消息为准。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class ServiceEndpointCache {

    static final String REDIS_KEY = "mcp:service-registry";

    private static final JsonCodec JSON = JsonCodecFactory.instance();

    private final StringRedisHelper redisHelper;

    private final ConcurrentHashMap<Long, ServiceRegistryVo> byId = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ServiceRegistryVo> byServiceCode = new ConcurrentHashMap<>();

    /**
     * @param redisHelper 字符串 Redis 操作
     */
    public ServiceEndpointCache(StringRedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    /**
     * 用全量服务注册替换内存和 Redis。
     *
     * @param registries basis 返回的服务注册
     */
    public void replaceAll(List<ServiceRegistryVo> registries) {
        byId.clear();
        byServiceCode.clear();
        deleteRedis();
        if (Objects.isNull(registries)) {
            return;
        }
        for (ServiceRegistryVo registry : registries) {
            remember(registry);
        }
    }

    /**
     * 新增或更新一条服务注册。
     *
     * @param registry 消息里的服务注册
     */
    public void upsert(ServiceRegistryVo registry) {
        remember(registry);
    }

    /**
     * 按主键删除服务注册。
     *
     * @param id 服务注册主键
     * @return 被删记录的服务编码；原来没有这条记录时返回 {@code null}
     */
    public String remove(Long id) {
        ServiceRegistryVo removed = byId.remove(id);
        if (Objects.isNull(removed) || Strings.isBlank(removed.getServiceCode())) {
            return null;
        }
        byServiceCode.remove(removed.getServiceCode(), removed);
        deleteField(removed.getServiceCode());
        return removed.getServiceCode();
    }

    /**
     * 取某个服务的调用基址和上下文路径。
     * <p>
     * {@code service_registry} 没有 contextPath 字段，上下文路径用 {@code routePrefix}。
     * </p>
     *
     * @param serviceCode 服务编码
     * @return 地址；未注册时返回 {@code null}
     */
    public Endpoint endpoint(String serviceCode) {
        ServiceRegistryVo registry = byServiceCode.get(serviceCode);
        if (Objects.isNull(registry) || Strings.isBlank(registry.getEndpoint())) {
            return null;
        }
        return new Endpoint(registry.getEndpoint(), contextPath(registry.getRoutePrefix()));
    }

    private void remember(ServiceRegistryVo registry) {
        if (Objects.isNull(registry) || Objects.isNull(registry.getId()) || Strings.isBlank(registry.getServiceCode())) {
            return;
        }
        byId.put(registry.getId(), registry);
        byServiceCode.put(registry.getServiceCode(), registry);
        writeField(registry);
    }

    private void writeField(ServiceRegistryVo registry) {
        try {
            StoredEndpoint stored = new StoredEndpoint();
            stored.setEndpoint(registry.getEndpoint());
            stored.setContextPath(contextPath(registry.getRoutePrefix()));
            redisHelper.hSet(REDIS_KEY, registry.getServiceCode(), JSON.obj2str(stored));
        } catch (RuntimeException ex) {
            log.warn("更新服务注册 Redis 缓存失败 serviceCode={}", registry.getServiceCode(), ex);
        }
    }

    private void deleteField(String serviceCode) {
        try {
            redisHelper.hDel(REDIS_KEY, serviceCode);
        } catch (RuntimeException ex) {
            log.warn("删除服务注册 Redis 缓存失败 serviceCode={}", serviceCode, ex);
        }
    }

    private void deleteRedis() {
        try {
            redisHelper.delete(REDIS_KEY);
        } catch (RuntimeException ex) {
            log.warn("清空服务注册 Redis 缓存失败", ex);
        }
    }

    /**
     * 把路由前缀收成以 {@code /} 开头的上下文路径。空前缀返回空串。
     *
     * @param routePrefix 服务注册上的路由前缀
     * @return 上下文路径
     */
    public static String contextPath(String routePrefix) {
        if (Strings.isBlank(routePrefix) || "/".equals(routePrefix)) {
            return "";
        }
        return routePrefix.startsWith("/") ? routePrefix : "/" + routePrefix;
    }

    /**
     * 调用基址。{@code endpoint} 是服务地址；{@code contextPath} 现网联调中暂不拼进调用 URL。
     *
     * @param endpoint    服务地址，如 {@code lb://g2rain-basis}
     * @param contextPath 上下文路径，如 {@code /basis}（多来自 routePrefix；网关场景曾用于路由前缀）
     */
    public record Endpoint(String endpoint, String contextPath) {

        /**
         * 拼出 {@link com.g2rain.mcp.registry.tool.ToolHandler} 使用的基址。
         * 处理器只拼接 {@code endpoint + path}。
         *
         * @return 调用基址（当前仅为 endpoint）
         */
        public String callBase() {
            // TODO(联调临时): contextPath 来自 routePrefix，原为走网关时拼到基址；
            //  现网 MCP 调用改为 lb:// 直连服务，服务侧不再需要该前缀，故暂不拼接。
            //  联调通过后按「网关 / lb」场景恢复或拆分策略，再决定是否拼 contextPath。
            // 原逻辑保留如下，便于恢复：
            // if (Strings.isBlank(contextPath)) {
            //     return endpoint;
            // }
            // String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            // return base + contextPath;
            return endpoint;
        }
    }

    /**
     * Redis 里保存的服务地址。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StoredEndpoint {
        private String endpoint;
        private String contextPath;
    }
}
