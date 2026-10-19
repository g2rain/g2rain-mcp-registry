package com.g2rain.mcp.registry.client;


import com.g2rain.basis.api.ResourceApiApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * basis 资源接口 Feign（{@code /resource_api}）。
 */
@FeignClient(name = "g2rain-basis", contextId = "mcpResourceApiClient", path = "/resource_api")
public interface ResourceApiClient extends ResourceApiApi {
}
