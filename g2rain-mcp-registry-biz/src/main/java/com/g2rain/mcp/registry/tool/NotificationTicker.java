package com.g2rain.mcp.registry.tool;


/**
 * 进度通知循环中的「等待一拍」抽象.
 * <p>
 * {@link ToolHandler} 在向下游 HTTP API 发起请求期间, 若客户端支持且启用了进度通知,
 * 会在独立线程中周期性发送 {@code notifications/progress}.
 * 每发送一条通知后, 需等待一个间隔再发送下一条; 本接口封装这一等待动作.
 * </p>
 * <p>
 * 设计为函数式接口, 便于:
 * </p>
 * <ul>
 *   <li>生产环境注入 {@code () -> Thread.sleep(intervalMs)} 实现真实间隔</li>
 *   <li>单元测试注入 no-op 或可控的「虚拟时钟」, 避免 {@code Thread.sleep} 拖慢测试</li>
 * </ul>
 * <p>
 * 单元测试可声明同名 Bean 替换默认实现.
 * </p>
 *
 * @author alpha
 * @since 2026/6/30
 * @see ToolHandler
 * @see com.g2rain.mcp.registry.support.ProgressUpdateProvider
 */
@FunctionalInterface
public interface NotificationTicker {

    /**
     * 阻塞当前线程, 直到下一次进度通知应被发送.
     * <p>
     * 由进度通知后台线程在每次 {@code progressNotification} 推送之后调用.
     * 生产实现通常等价于 {@code Thread.sleep(interval)}; 测试可替换为立即返回.
     * </p>
     *
     * @throws InterruptedException 等待被中断时抛出, 调用方应终止通知循环并恢复中断状态
     */
    void tick() throws InterruptedException;
}
