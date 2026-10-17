package com.g2rain.mcp.registry.tool;


import com.g2rain.common.utils.MediaTypes;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 记录工具出站请求和响应，供审计和计量。
 * <p>
 * 请求体用拦截器入参里的字节，不再拷贝。响应体读进字节后交回新的流，调用方仍能读取。
 * 响应体只有 JSON 才写入日志。打日志失败只警告。没有响应时按 502 记一行，再把异常抛出去。
 * 以后把打日志换成发 Kafka，只改 {@link #report}。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
@NullMarked
public class ToolCallInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @Nullable [] body, ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response;
        try {
            response = execution.execute(request, Objects.requireNonNullElse(body, new byte[0]));
        } catch (IOException ex) {
            report(request, body, 502, null, false);
            throw ex;
        }
        byte[] responseBody;
        try (InputStream stream = response.getBody()) {
            responseBody = stream.readAllBytes();
        } catch (IOException ex) {
            report(request, body, 502, null, false);
            response.close();
            throw ex;
        }
        boolean json = MediaTypes.isJson(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        int status = response.getStatusCode().value();
        report(request, body, status, responseBody, json);
        return new ReplayClientHttpResponse(response, responseBody);
    }

    /**
     * 打一行出站日志。失败只警告，不往外抛。
     *
     * @param request      出站请求
     * @param requestBody  请求体字节，可空
     * @param status       HTTP 状态码；没有响应时为 502
     * @param responseBody 响应体；非 JSON 或没有响应时不写入日志，可空
     * @param json         响应是否为 JSON
     */
    private void report(HttpRequest request, byte @Nullable [] requestBody, int status, byte @Nullable [] responseBody, boolean json) {
        try {
            byte[] requestBytes = Objects.requireNonNullElse(requestBody, new byte[0]);
            String requestText = new String(requestBytes, StandardCharsets.UTF_8);
            if (json) {
                byte[] responseBytes = Objects.requireNonNullElse(responseBody, new byte[0]);
                log.info("tool call method={} url={} status={} requestBody={} responseBody={}",
                    request.getMethod(), request.getURI(), status, requestText,
                    new String(responseBytes, StandardCharsets.UTF_8));
                return;
            }
            log.info("tool call method={} url={} status={} requestBody={}",
                request.getMethod(), request.getURI(), status, requestText);
        } catch (RuntimeException ex) {
            log.warn("记录工具出站日志失败", ex);
        }
    }

    /**
     * 用已读出的字节重新提供响应体，状态码和头仍走原来的响应。
     */
    private record ReplayClientHttpResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
