package com.g2rain.mcp.registry.client;


import com.g2rain.basis.api.AuthorityApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * basis 资源授权 Feign（{@code /authority}）。
 */
@FeignClient(name = "g2rain-basis", contextId = "mcpAuthorityClient", path = "/authority")
public interface AuthorityClient extends AuthorityApi {
}
