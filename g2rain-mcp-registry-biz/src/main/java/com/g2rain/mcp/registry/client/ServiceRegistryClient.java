package com.g2rain.mcp.registry.client;


import com.g2rain.basis.api.ServiceRegistryApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * basis 服务注册 Feign（{@code /service_registry}）。
 */
@FeignClient(name = "g2rain-basis", contextId = "mcpServiceRegistryClient", path = "/service_registry")
public interface ServiceRegistryClient extends ServiceRegistryApi {
}
