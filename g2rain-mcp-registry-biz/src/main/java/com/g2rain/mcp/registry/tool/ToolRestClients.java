package com.g2rain.mcp.registry.tool;


import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Objects;

/**
 * {@code tools/call} 出站 HTTP 客户端选择器.
 * <p>
 * 与 Spring Cloud Gateway {@code ReactiveLoadBalancerClientFilter} 一致:
 * {@code lb://} 走 {@link org.springframework.cloud.client.loadbalancer.LoadBalanced} 客户端,
 * {@code http(s)://} 走直连客户端.
 * </p>
 */
public final class ToolRestClients {

    private static final String LOAD_BALANCER_SCHEME = "lb";

    private final RestClient loadBalancedRestClient;

    private final RestClient directRestClient;

    public ToolRestClients(RestClient loadBalancedRestClient, RestClient directRestClient) {
        this.loadBalancedRestClient = Objects.requireNonNull(loadBalancedRestClient, "loadBalancedRestClient");
        this.directRestClient = Objects.requireNonNull(directRestClient, "directRestClient");
    }

    /**
     * 按 endpoint scheme 选择出站 {@link RestClient}.
     *
     * @param endpoint OpenAPI 服务基址, 如 {@code lb://g2rain-basis} 或 {@code http://127.0.0.1:8080}
     * @return 负载均衡或直连客户端
     */
    public RestClient forEndpoint(String endpoint) {
        return requiresLoadBalancer(endpoint) ? loadBalancedRestClient : directRestClient;
    }

    static boolean requiresLoadBalancer(String endpoint) {
        return LOAD_BALANCER_SCHEME.equalsIgnoreCase(URI.create(endpoint).getScheme());
    }
}
