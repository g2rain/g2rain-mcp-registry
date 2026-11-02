-- =============================================
-- g2rain_mcp_registry 数据库表结构
-- MySQL 8.0 版本
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `g2rain_mcp_registry` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `g2rain_mcp_registry`;

-- =============================================
-- 1. MCP外部服务 (mcp_external_service)
-- =============================================
DROP TABLE IF EXISTS `mcp_external_service`;

CREATE TABLE `mcp_external_service` (
    `id` BIGINT NOT NULL COMMENT                                                                        '主键标识',
    `organ_id` BIGINT NOT NULL COMMENT                                                                  '机构标识',
    `name` VARCHAR(128) NOT NULL COMMENT                                                                '服务名称',
    `endpoint` VARCHAR(512) NOT NULL COMMENT                                                            '调用基址(http/https，含上下文路径，如 https://erp.example.com/api)',
    `resource_type` VARCHAR(32) NOT NULL COMMENT                                                        '资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT                                              '状态[ACTIVE:启用, INACTIVE:禁用]',
    `description` VARCHAR(500) DEFAULT NULL COMMENT                                                     '说明',
    `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT                                      '创建时间',
    `update_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT          '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT                                                            '记录版本',
    `delete_flag` TINYINT NOT NULL DEFAULT 0 COMMENT                                                    '删除标识[0:未删除, 1:已删除]',
    PRIMARY KEY (`id`),
    INDEX `idx_organ_type` (`organ_id`, `resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=                             'MCP外部服务表';

-- =============================================
-- 2. MCP Tool 暴露档案 (mcp_tool_profile)
-- =============================================
DROP TABLE IF EXISTS `mcp_tool_profile`;

CREATE TABLE `mcp_tool_profile` (
    `id` BIGINT NOT NULL COMMENT                                                                        '主键标识',
    `organ_id` BIGINT NOT NULL COMMENT                                                                  '机构标识',
    `name` VARCHAR(256) NOT NULL COMMENT                                                                'Tool名称(MCP tools/call name)',
    `title` VARCHAR(128) NOT NULL COMMENT                                                               'Tool标题(MCP title)',
    `resource_type` VARCHAR(32) NOT NULL COMMENT                                                        '资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]',
    `service_id` BIGINT NOT NULL COMMENT                                                                '所属服务(NATIVE→basis.service_registry.id; 其余→mcp_external_service.id)',
    `source_id` BIGINT DEFAULT NULL COMMENT                                                             '来源标识(仅NATIVE=basis.resource_api.id)',
    `method` VARCHAR(32) NOT NULL COMMENT                                                               'HTTP方法',
    `path` VARCHAR(512) NOT NULL COMMENT                                                                'HTTP路径',
    `openapi_document` JSON NOT NULL COMMENT                                                            '单接口OpenAPI文档(含openapi版本、info与单个path+method, $ref已展开)',
    `input_schema` JSON NOT NULL COMMENT                                                                'MCP inputSchema',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT                                              '状态[ACTIVE:启用, INACTIVE:禁用]',
    `description` VARCHAR(500) DEFAULT NULL COMMENT                                                     'Tool说明(给LLM)',
    `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT                                      '创建时间',
    `update_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT          '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT                                                            '记录版本',
    `delete_flag` TINYINT NOT NULL DEFAULT 0 COMMENT                                                    '删除标识[0:未删除, 1:已删除]',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_name` (`organ_id`, `resource_type`, `name`, (IF(`delete_flag` = 0, 0, NULL))),
    INDEX `idx_service` (`resource_type`, `service_id`),
    INDEX `idx_source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=                             'MCP Tool暴露档案表';
