# ************************************************************
# Sequel Ace SQL dump
# 版本号： 20094
#
# https://sequel-ace.com/
# https://github.com/Sequel-Ace/Sequel-Ace
#
# 主机: 127.0.0.1 (MySQL 8.0.42)
# 数据库: grafana
# 生成时间: 2025-08-16 01:59:50 +0000
# ************************************************************


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE database if NOT EXISTS `grafana` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `grafana`;

# 转储表 alert
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert`;

CREATE TABLE `alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `panel_id` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `state` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `settings` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `frequency` bigint NOT NULL,
  `handler` bigint NOT NULL,
  `severity` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `silenced` tinyint(1) NOT NULL,
  `execution_error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `eval_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `eval_date` datetime DEFAULT NULL,
  `new_state_date` datetime NOT NULL,
  `state_changes` int NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `for` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_alert_org_id_id` (`org_id`,`id`),
  KEY `IDX_alert_state` (`state`),
  KEY `IDX_alert_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_configuration
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_configuration`;

CREATE TABLE `alert_configuration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alertmanager_configuration` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `configuration_version` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` int NOT NULL,
  `default` tinyint(1) NOT NULL DEFAULT '0',
  `org_id` bigint NOT NULL DEFAULT '0',
  `configuration_hash` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'not-yet-calculated',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_configuration_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `alert_configuration` WRITE;
/*!40000 ALTER TABLE `alert_configuration` DISABLE KEYS */;

INSERT INTO `alert_configuration` (`id`, `alertmanager_configuration`, `configuration_version`, `created_at`, `default`, `org_id`, `configuration_hash`)
VALUES
	(1,'{\n	\"alertmanager_config\": {\n		\"route\": {\n			\"receiver\": \"grafana-default-email\",\n			\"group_by\": [\"grafana_folder\", \"alertname\"]\n		},\n		\"receivers\": [{\n			\"name\": \"grafana-default-email\",\n			\"grafana_managed_receiver_configs\": [{\n				\"uid\": \"\",\n				\"name\": \"email receiver\",\n				\"type\": \"email\",\n				\"isDefault\": true,\n				\"settings\": {\n					\"addresses\": \"<example@email.com>\"\n				}\n			}]\n		}]\n	}\n}\n','v1',1698466015,1,1,'e0528a75784033ae7b15c40851d89484');

/*!40000 ALTER TABLE `alert_configuration` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 alert_configuration_history
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_configuration_history`;

CREATE TABLE `alert_configuration_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL DEFAULT '0',
  `alertmanager_configuration` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `configuration_hash` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'not-yet-calculated',
  `configuration_version` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` int NOT NULL,
  `default` tinyint(1) NOT NULL DEFAULT '0',
  `last_applied` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `alert_configuration_history` WRITE;
/*!40000 ALTER TABLE `alert_configuration_history` DISABLE KEYS */;

INSERT INTO `alert_configuration_history` (`id`, `org_id`, `alertmanager_configuration`, `configuration_hash`, `configuration_version`, `created_at`, `default`, `last_applied`)
VALUES
	(1,1,'{\n	\"alertmanager_config\": {\n		\"route\": {\n			\"receiver\": \"grafana-default-email\",\n			\"group_by\": [\"grafana_folder\", \"alertname\"]\n		},\n		\"receivers\": [{\n			\"name\": \"grafana-default-email\",\n			\"grafana_managed_receiver_configs\": [{\n				\"uid\": \"\",\n				\"name\": \"email receiver\",\n				\"type\": \"email\",\n				\"isDefault\": true,\n				\"settings\": {\n					\"addresses\": \"<example@email.com>\"\n				}\n			}]\n		}]\n	}\n}\n','e0528a75784033ae7b15c40851d89484','v1',1698466015,1,1755306000);

/*!40000 ALTER TABLE `alert_configuration_history` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 alert_image
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_image`;

CREATE TABLE `alert_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_image_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_instance
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_instance`;

CREATE TABLE `alert_instance` (
  `rule_org_id` bigint NOT NULL DEFAULT '0',
  `rule_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `labels` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `labels_hash` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_state` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_state_since` bigint NOT NULL,
  `last_eval_time` bigint NOT NULL,
  `current_state_end` bigint NOT NULL DEFAULT '0',
  `current_reason` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`rule_org_id`,`rule_uid`,`labels_hash`),
  KEY `IDX_alert_instance_rule_org_id_rule_uid_current_state` (`rule_org_id`,`rule_uid`,`current_state`),
  KEY `IDX_alert_instance_rule_org_id_current_state` (`rule_org_id`,`current_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_notification
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_notification`;

CREATE TABLE `alert_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `settings` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `frequency` bigint DEFAULT NULL,
  `send_reminder` tinyint(1) DEFAULT '0',
  `disable_resolve_message` tinyint(1) NOT NULL DEFAULT '0',
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secure_settings` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_notification_org_id_uid` (`org_id`,`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_notification_state
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_notification_state`;

CREATE TABLE `alert_notification_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `alert_id` bigint NOT NULL,
  `notifier_id` bigint NOT NULL,
  `state` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  `alert_rule_state_updated_version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_notification_state_org_id_alert_id_notifier_id` (`org_id`,`alert_id`,`notifier_id`),
  KEY `IDX_alert_notification_state_alert_id` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_rule
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_rule`;

CREATE TABLE `alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `title` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `condition` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated` datetime NOT NULL,
  `interval_seconds` bigint NOT NULL DEFAULT '60',
  `version` int NOT NULL DEFAULT '0',
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `namespace_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_group` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `no_data_state` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NoData',
  `exec_err_state` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Alerting',
  `for` bigint NOT NULL DEFAULT '0',
  `annotations` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `labels` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `dashboard_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `panel_id` bigint DEFAULT NULL,
  `rule_group_idx` int NOT NULL DEFAULT '1',
  `is_paused` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_rule_org_id_uid` (`org_id`,`uid`),
  UNIQUE KEY `UQE_alert_rule_org_id_namespace_uid_title` (`org_id`,`namespace_uid`,`title`),
  KEY `IDX_alert_rule_org_id_namespace_uid_rule_group` (`org_id`,`namespace_uid`,`rule_group`),
  KEY `IDX_alert_rule_org_id_dashboard_uid_panel_id` (`org_id`,`dashboard_uid`,`panel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_rule_tag
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_rule_tag`;

CREATE TABLE `alert_rule_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alert_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_rule_tag_alert_id_tag_id` (`alert_id`,`tag_id`),
  KEY `IDX_alert_rule_tag_alert_id` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 alert_rule_version
# ------------------------------------------------------------

DROP TABLE IF EXISTS `alert_rule_version`;

CREATE TABLE `alert_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_org_id` bigint NOT NULL,
  `rule_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `rule_namespace_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_group` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_version` int NOT NULL,
  `restored_from` int NOT NULL,
  `version` int NOT NULL,
  `created` datetime NOT NULL,
  `title` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `condition` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `interval_seconds` bigint NOT NULL,
  `no_data_state` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NoData',
  `exec_err_state` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Alerting',
  `for` bigint NOT NULL DEFAULT '0',
  `annotations` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `labels` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `rule_group_idx` int NOT NULL DEFAULT '1',
  `is_paused` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_alert_rule_version_rule_org_id_rule_uid_version` (`rule_org_id`,`rule_uid`,`version`),
  KEY `IDX_alert_rule_version_rule_org_id_rule_namespace_uid_rule_group` (`rule_org_id`,`rule_namespace_uid`,`rule_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 annotation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `annotation`;

CREATE TABLE `annotation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `alert_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `dashboard_id` bigint DEFAULT NULL,
  `panel_id` bigint DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `type` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `metric` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prev_state` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `new_state` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `epoch` bigint NOT NULL,
  `region_id` bigint DEFAULT '0',
  `tags` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` bigint DEFAULT '0',
  `updated` bigint DEFAULT '0',
  `epoch_end` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `IDX_annotation_org_id_alert_id` (`org_id`,`alert_id`),
  KEY `IDX_annotation_org_id_type` (`org_id`,`type`),
  KEY `IDX_annotation_org_id_created` (`org_id`,`created`),
  KEY `IDX_annotation_org_id_updated` (`org_id`,`updated`),
  KEY `IDX_annotation_org_id_dashboard_id_epoch_end_epoch` (`org_id`,`dashboard_id`,`epoch_end`,`epoch`),
  KEY `IDX_annotation_org_id_epoch_end_epoch` (`org_id`,`epoch_end`,`epoch`),
  KEY `IDX_annotation_alert_id` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 annotation_tag
# ------------------------------------------------------------

DROP TABLE IF EXISTS `annotation_tag`;

CREATE TABLE `annotation_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `annotation_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_annotation_tag_annotation_id_tag_id` (`annotation_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 anon_device
# ------------------------------------------------------------

DROP TABLE IF EXISTS `anon_device`;

CREATE TABLE `anon_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `device_id` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime NOT NULL,
  `user_agent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_anon_device_device_id` (`device_id`),
  KEY `IDX_anon_device_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 api_key
# ------------------------------------------------------------

DROP TABLE IF EXISTS `api_key`;

CREATE TABLE `api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `expires` bigint DEFAULT NULL,
  `service_account_id` bigint DEFAULT NULL,
  `last_used_at` datetime DEFAULT NULL,
  `is_revoked` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_api_key_key` (`key`),
  UNIQUE KEY `UQE_api_key_org_id_name` (`org_id`,`name`),
  KEY `IDX_api_key_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `api_key` WRITE;
/*!40000 ALTER TABLE `api_key` DISABLE KEYS */;

INSERT INTO `api_key` (`id`, `org_id`, `name`, `key`, `role`, `created`, `updated`, `expires`, `service_account_id`, `last_used_at`, `is_revoked`)
VALUES
	(3,1,'sa-xiaofuge-d7c4372a-6938-4873-86ec-ea02877856a4','a1f2d4b62caa55c159b3fce5e6a60b49e9e241fe96abfe3dbd0e4a01cdd604871fda325da6809921f791a9759c39f1d5301f','Viewer','2025-06-15 02:29:37','2025-06-15 02:29:37',NULL,2,NULL,0),
	(4,1,'sa-liergou-e58852b4-25bb-482b-bbc8-564e0c56f5ac','51724f1b5d14c5bf4011a83ec3947021b532ac72a198aed94ddb0c231e0e7746586790000b45e5787996792397759efeec9d','Viewer','2025-06-15 05:04:51','2025-06-15 05:04:51',NULL,3,NULL,0),
	(5,1,'sa-xiaofuge-84b45e01-bc75-4b2f-845e-8d09bb2cb159','9bf981e76e7572ef8f263297a3a0a1723ff9b7c7388570d4576324a932b256a4a636120673fa4ed036df99ca70ddfcb606ad','Viewer','2025-08-16 01:29:29','2025-08-16 01:29:29',NULL,2,NULL,0);

/*!40000 ALTER TABLE `api_key` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 builtin_role
# ------------------------------------------------------------

DROP TABLE IF EXISTS `builtin_role`;

CREATE TABLE `builtin_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `org_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_builtin_role_org_id_role_id_role` (`org_id`,`role_id`,`role`),
  KEY `IDX_builtin_role_role_id` (`role_id`),
  KEY `IDX_builtin_role_role` (`role`),
  KEY `IDX_builtin_role_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `builtin_role` WRITE;
/*!40000 ALTER TABLE `builtin_role` DISABLE KEYS */;

INSERT INTO `builtin_role` (`id`, `role`, `role_id`, `created`, `updated`, `org_id`)
VALUES
	(1,'Editor',2,'2023-10-28 04:10:39','2023-10-28 04:10:39',1),
	(2,'Viewer',3,'2023-10-28 04:10:39','2023-10-28 04:10:39',1);

/*!40000 ALTER TABLE `builtin_role` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 cache_data
# ------------------------------------------------------------

DROP TABLE IF EXISTS `cache_data`;

CREATE TABLE `cache_data` (
  `cache_key` varchar(168) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` blob NOT NULL,
  `expires` int NOT NULL,
  `created_at` int NOT NULL,
  PRIMARY KEY (`cache_key`),
  UNIQUE KEY `UQE_cache_data_cache_key` (`cache_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 correlation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `correlation`;

CREATE TABLE `correlation` (
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL DEFAULT '0',
  `source_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `label` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `provisioned` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`uid`,`org_id`,`source_uid`),
  KEY `IDX_correlation_uid` (`uid`),
  KEY `IDX_correlation_source_uid` (`source_uid`),
  KEY `IDX_correlation_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 dashboard
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard`;

CREATE TABLE `dashboard` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` int NOT NULL,
  `slug` varchar(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `updated_by` int DEFAULT NULL,
  `created_by` int DEFAULT NULL,
  `gnet_id` bigint DEFAULT NULL,
  `plugin_id` varchar(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `folder_id` bigint NOT NULL DEFAULT '0',
  `is_folder` tinyint(1) NOT NULL DEFAULT '0',
  `has_acl` tinyint(1) NOT NULL DEFAULT '0',
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_dashboard_org_id_folder_id_title` (`org_id`,`folder_id`,`title`),
  UNIQUE KEY `UQE_dashboard_org_id_uid` (`org_id`,`uid`),
  KEY `IDX_dashboard_org_id` (`org_id`),
  KEY `IDX_dashboard_gnet_id` (`gnet_id`),
  KEY `IDX_dashboard_org_id_plugin_id` (`org_id`,`plugin_id`),
  KEY `IDX_dashboard_title` (`title`),
  KEY `IDX_dashboard_is_folder` (`is_folder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `dashboard` WRITE;
/*!40000 ALTER TABLE `dashboard` DISABLE KEYS */;

INSERT INTO `dashboard` (`id`, `version`, `slug`, `title`, `data`, `org_id`, `created`, `updated`, `updated_by`, `created_by`, `gnet_id`, `plugin_id`, `folder_id`, `is_folder`, `has_acl`, `uid`, `is_public`)
VALUES
	(8,1,'55fdfc7e-cf5d-5400-ba31-cf298529f4fb','SpringBoot APM Dashboard（中文版本）','{\"annotations\":{\"list\":[{\"builtIn\":1,\"datasource\":{\"type\":\"datasource\",\"uid\":\"grafana\"},\"enable\":true,\"hide\":true,\"iconColor\":\"rgba(0, 211, 255, 1)\",\"name\":\"Annotations \\u0026 Alerts\",\"type\":\"dashboard\"}]},\"description\":\"Spring Boot指标面板\",\"editable\":true,\"fiscalYearStartMonth\":0,\"gnetId\":21319,\"graphTooltip\":0,\"id\":null,\"links\":[],\"liveNow\":false,\"panels\":[{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":0},\"id\":54,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"基础信息\",\"type\":\"row\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"\",\"fieldConfig\":{\"defaults\":{\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"s\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":6,\"x\":0,\"y\":1},\"id\":52,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"fieldOptions\":{\"calcs\":[\"lastNotNull\"]},\"graphMode\":\"none\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"mean\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(process_uptime_seconds{application=\\\"$application\\\", instance=~\\\"$instance\\\"})\",\"format\":\"time_series\",\"hide\":false,\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"运行时间\",\"metric\":\"\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"运行时间\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"最大堆内存，-Xmx设置的参数\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null}]},\"unit\":\"decbytes\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":6,\"y\":1},\"id\":58,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"heap\\\"})\",\"format\":\"time_series\",\"instant\":true,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"堆内存\",\"range\":false,\"refId\":\"A\",\"step\":14400}],\"title\":\"堆内存\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"统计请求量，包含当前服务总体请求量以及最近5分钟内的请求量。\\n单位：次\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":0,\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":9,\"y\":1},\"id\":117,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"text\":{},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(http_server_requests_seconds_count{application=\\\"$application\\\",instance=~\\\"$instance\\\", uri!~\\\".*actuator.*\\\"})\",\"instant\":true,\"interval\":\"\",\"legendFormat\":\"总请求量\",\"refId\":\"A\"}],\"title\":\"总请求量\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"process_files_max_files：代表一个进程可以打开的最大文件描述符数量。在 Linux 中，每个进程都有一个文件描述符表，用于跟踪该进程打开的所有文件、套接字等。默认情况下，这个数量是有限的，超过显示会报错（too many open files）。\\nprocess_files_open_files：表示当前进程已经打开的文件描述符数量。\\n可以通过ulimit -a查看系统配置的最大文件描述符数量（open files）。可以在/etc/security/limits.conf修改\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"locale\"},\"overrides\":[{\"matcher\":{\"id\":\"byName\",\"options\":\"Max Files\"},\"properties\":[{\"id\":\"color\",\"value\":{\"fixedColor\":\"red\",\"mode\":\"fixed\"}}]}]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":12,\"y\":1},\"id\":66,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_files_open_files{application=\\\"$application\\\", instance=~\\\"$instance\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Open Files\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_files_max_files{application=\\\"$application\\\", instance=~\\\"$instance\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max Files\",\"range\":true,\"refId\":\"B\"}],\"title\":\"进程打开文件数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"服务启动的时间\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"dateTimeFromNow\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":6,\"x\":0,\"y\":4},\"id\":56,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"none\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"first\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"avg(process_start_time_seconds{application=\\\"$application\\\", instance=~\\\"$instance\\\"})*1000\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"启动时间\",\"metric\":\"\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"启动时间\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"非堆内存大小，方法区（Method Area）与 Java 堆一样，是各个线程共享的内存区域，它用于存储\\n已被虚拟机加载的类型信息、常量、静态变量、即时编译器编译后的代码缓存等数据。\\n虽然《Java 虚拟机规范》中把方法区描述为堆的一个逻辑部分，但是它却有一个别名叫\\n作“非堆”（Non-Heap），目的是与 Java 堆区分开来。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null}]},\"unit\":\"decbytes\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":6,\"y\":4},\"id\":112,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})\",\"format\":\"time_series\",\"instant\":true,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"非堆内存\",\"range\":false,\"refId\":\"A\",\"step\":14400}],\"title\":\"非堆内存\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"统计请求量，包含当前服务总体请求量以及最近5分钟内的请求量。\\n单位：次\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":0,\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":9,\"y\":4},\"id\":141,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"text\":{},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(increase(http_server_requests_seconds_count{application=\\\"$application\\\",instance=~\\\"$instance\\\", uri!~\\\".*actuator.*\\\"}[5m]) )\",\"hide\":false,\"instant\":true,\"interval\":\"\",\"legendFormat\":\"请求量【近5分钟】\",\"refId\":\"B\"}],\"title\":\"请求量【近5分钟】\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"QPS（每秒请求数）是指单位时间内的请求次数，是一个衡量系统吞吐量的重要指标。应用服务的请求QPS指的是在单位时间内，应用服务处理的请求数量。它可以帮助评估系统的性能，以及请求处理能力是否足够，是否需要对系统进行优化或扩容。\\n\\n优化应用服务请求QPS（每秒请求数）的方法可能包括：\\n\\n减少请求时间：通过优化代码，缩短请求执行时间，从而提高每秒请求数。\\n\\n增加服务器性能：通过升级服务器的配置，提高服务器的处理能力，从而提高每秒请求数。\\n\\n分布式系统：通过使用分布式系统，将请求分配到多台服务器上，从而提高每秒请求数。\\n\\n缓存：通过使用缓存，减少请求的数据处理时间，从而提高每秒请求数。\\n\\n减少请求数量：通过消除不必要的请求，减少请求数量，从而提高每秒请求数。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":7},\"id\":125,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":true,\"expr\":\"sum(irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m]))\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"当前数据\",\"range\":true,\"refId\":\"A\"}],\"title\":\"请求QPS\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"QPS（每秒请求数）是指单位时间内的请求次数，是一个衡量系统吞吐量的重要指标。应用服务的请求QPS指的是在单位时间内，应用服务处理的请求数量。它可以帮助评估系统的性能，以及请求处理能力是否足够，是否需要对系统进行优化或扩容。\\n\\n优化应用服务请求QPS（每秒请求数）的方法可能包括：\\n\\n减少请求时间：通过优化代码，缩短请求执行时间，从而提高每秒请求数。\\n\\n增加服务器性能：通过升级服务器的配置，提高服务器的处理能力，从而提高每秒请求数。\\n\\n分布式系统：通过使用分布式系统，将请求分配到多台服务器上，从而提高每秒请求数。\\n\\n缓存：通过使用缓存，减少请求的数据处理时间，从而提高每秒请求数。\\n\\n减少请求数量：通过消除不必要的请求，减少请求数量，从而提高每秒请求数。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":7},\"id\":133,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"lastNotNull\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"topk(5, sum by(uri, method) (rate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m])))\",\"format\":\"time_series\",\"instant\":false,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"{{method}}-{{uri}}\",\"range\":true,\"refId\":\"A\"}],\"title\":\"请求QPS(TOP5)\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"内存使用率\\n\\n堆内存使用率正常指标范围参考：\\n\\nJava应用会有一定的内存占用，但内存使用率应该保持在合理范围内。通常情况下，70-80%的内存使用率是可以接受的。\\n\\n如果内存使用率长期接近或达到100%，可能存在内存泄漏问题或内存不足。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":0,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":2,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"auto\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"decimals\":1,\"mappings\":[],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null},{\"color\":\"rgba(237, 129, 40, 0.89)\",\"value\":80},{\"color\":\"rgba(245, 54, 54, 0.9)\",\"value\":90}]},\"unit\":\"percent\"},\"overrides\":[]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":0,\"y\":14},\"id\":101,\"links\":[],\"maxDataPoints\":100,\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"single\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\", area=\\\"heap\\\"})*100/sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"heap\\\"})\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Heap Memory Usage（堆内存使用率）\",\"range\":true,\"refId\":\"A\",\"step\":14400},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})*100/sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})\",\"hide\":false,\"instant\":false,\"legendFormat\":\"Non-Heap Memory Usage（非堆内存使用率）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"内存使用率（%）\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"应用所在磁盘使用率\\n\\n正常指标范围参考：\\n\\n这个指标其实和CPU数和内存都没啥关系，一般来说磁盘超过80%就要报警了。有的日志比较多的应用，可能在60%左右就需要来看了。\\n\\n一般为了让磁盘的利用率维持在一定水位上，机器都需要配置日志的自动清理。\\t\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":0,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":1,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"auto\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"decimals\":1,\"mappings\":[],\"max\":1,\"min\":0,\"noValue\":\"-\",\"thresholds\":{\"mode\":\"percentage\",\"steps\":[{\"color\":\"super-light-green\",\"value\":null},{\"color\":\"orange\",\"value\":60},{\"color\":\"red\",\"value\":80}]},\"unit\":\"percentunit\"},\"overrides\":[]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":12,\"y\":14},\"id\":149,\"links\":[],\"maxDataPoints\":100,\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"single\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"(disk_total_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\"} - disk_free_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\"})/(disk_total_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\"})\",\"format\":\"time_series\",\"instant\":false,\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"{{path}}-磁盘使用率\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"磁盘使用率（%）\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"正常指标范围参考：\\n\\n在空闲或轻负载情况下，CPU使用率应较低，通常在0-30%左右。\\n\\n在中等负载下，使用率可能在30-70%。\\n\\n高负载或高性能需求的情况下，CPU使用率可能会更高，但是最好不要长期超过80%。一般建议控制在70%以下。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"percentunit\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":20},\"id\":95,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_cpu_usage{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"System CPU Usage（系统CPU使用率）\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_cpu_usage{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Process CPU Usage（进程cpu使用率）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"CPU使用率\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"system_load_average_1m 通常指的是系统在过去1分钟内的平均负载（Load Average）。在Linux系统中，你可以使用多种命令来查看当前的平均负载，包括 uptime、top以及查看 /proc/loadavg 文件。load average后边会有三个值，分别代表1分钟，5分钟，15分钟负载情况。\\n\\n正常指标范围参考：\\n对于4核CPU，理想的系统负载值应低于4。超过这个值可能意味着过载。一般来说应该维持在50%以下，也就是2以下。\\n如果长时间超过75%，也就是3的话，那么就需要进行排查看看是不是存在问题了。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"short\"},\"overrides\":[{\"matcher\":{\"id\":\"byName\",\"options\":\"CPU Core Size（CPU核数）\"},\"properties\":[{\"id\":\"color\",\"value\":{\"fixedColor\":\"red\",\"mode\":\"fixed\"}}]}]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":20},\"id\":96,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_load_average_1m{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Load Average [1m](1分钟系统负载)\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_cpu_count{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"CPU Core Size（CPU核数）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"Load average（系统负载）\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":27},\"id\":48,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"JVM内存\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"\",\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":6,\"x\":0,\"y\":28},\"hiddenSeries\":false,\"id\":85,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"repeat\":\"memory_pool_heap\",\"repeatDirection\":\"h\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used\",\"range\":true,\"refId\":\"C\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_committed_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Commited\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_max_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"$memory_pool_heap (heap)\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"bytes\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":8,\"x\":0,\"y\":44},\"hiddenSeries\":false,\"id\":88,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"repeat\":\"memory_pool_nonheap\",\"repeatDirection\":\"h\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Used\",\"refId\":\"C\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_memory_committed_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Commited\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_max_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"$memory_pool_nonheap (non-heap)\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"bytes\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"decimals\":0,\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":12,\"x\":0,\"y\":52},\"hiddenSeries\":false,\"id\":50,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_classes_loaded_classes{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Classes Loaded\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Classes Loaded\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"decimals\":0,\"format\":\"locale\",\"label\":\"\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":12,\"x\":12,\"y\":52},\"hiddenSeries\":false,\"id\":80,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(jvm_classes_unloaded_classes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Classes Unloaded\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Classes Unloaded\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":60},\"hiddenSeries\":false,\"id\":82,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"direct\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used Bytes\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_total_capacity_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"direct\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Capacity Bytes\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Direct Buffers\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":60},\"hiddenSeries\":false,\"id\":83,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"mapped\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used Bytes\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_total_capacity_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"mapped\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Capacity Bytes\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Mapped Buffers\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"在JVM（Java Virtual Machine）的性能监控和诊断中，jvm_threads_daemon_threads、jvm_threads_live_threads 和 jvm_threads_peak_threads 是常见的度量指标，用于描述JVM中线程的状态和数量。下面我将逐一解释这些指标：\\n\\njvm_threads_daemon_threads\\n定义：当前JVM中守护线程（Daemon Threads）的数量。\\n解释：守护线程是一种特殊的线程，它在其他所有非守护线程结束后自动结束。通常，守护线程用于执行后台任务，如垃圾回收线程就是一个守护线程。如果JVM中只剩下守护线程在运行，那么JVM会退出。\\njvm_threads_live_threads\\n定义：当前JVM中活动线程（Live Threads）的数量。\\n解释：活动线程是指已经启动但尚未结束的线程。这个数字通常包括了应用代码创建的线程，以及JVM内部使用的线程（如垃圾回收线程）。这个指标可以帮助你了解当前JVM的线程使用情况，如果这个数字持续很高，可能意味着你的应用正在创建大量的线程，这可能会导致性能问题或资源耗尽。\\njvm_threads_peak_threads\\n定义：从JVM启动到当前时间，活动线程数量的峰值。\\n解释：这个指标记录了JVM在其生命周期中活动线程数量的最大值。通过比较jvm_threads_live_threads和jvm_threads_peak_threads，你可以了解你的应用在何时达到了线程使用的峰值，并据此进行性能调优或资源规划。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"short\"},\"overrides\":[]},\"gridPos\":{\"h\":8,\"w\":12,\"x\":0,\"y\":67},\"id\":68,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_daemon_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Daemon（守护线程）\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_live_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Live（活动线程）\",\"range\":true,\"refId\":\"B\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_peak_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Peak(活动线程峰值)\",\"range\":true,\"refId\":\"C\"}],\"title\":\"线程数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"jvm_gc_memory_allocated_bytes_total 是一个 JVM 指标，用于记录自 JVM 启动以来分配的总内存大小。它是一个累积计数器，随着时间的推移，其值会不断增加，因为 JVM 会不断分配内存。\\njvm_gc_memory_promoted_bytes_total 则记录了从新生代（Young Generation）晋升到老年代（Old Generation）的内存大小总和。它反映了对象在垃圾回收过程中的存活率。\\n这两个指标都可以帮助你了解 JVM 中内存的使用情况和对象的生命周期。通过监控这些指标，你可以观察到应用程序中内存的分配和晋升情况，并根据需要调整内存分配和垃圾回收策略，以优化应用程序的性能和内存使用效率。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"bytes\"},\"overrides\":[]},\"gridPos\":{\"h\":8,\"w\":12,\"x\":12,\"y\":67},\"id\":78,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_memory_allocated_bytes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"allocated\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_memory_promoted_bytes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"promoted\",\"range\":true,\"refId\":\"B\"}],\"title\":\"Memory Allocate/Promote\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":75},\"id\":72,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"JVM-GC\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":10,\"w\":12,\"x\":0,\"y\":76},\"hiddenSeries\":false,\"id\":74,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":false,\"hideEmpty\":true,\"hideZero\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(jvm_gc_pause_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{action}} [{{cause}}]\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"GC Count\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"locale\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":10,\"w\":12,\"x\":12,\"y\":76},\"hiddenSeries\":false,\"id\":76,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":false,\"hideEmpty\":true,\"hideZero\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_pause_seconds_sum{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{action}} [{{cause}}]\",\"range\":true,\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"GC Stop the World Duration\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"s\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":86},\"id\":18,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"吞吐量\",\"type\":\"row\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"}]},\"unit\":\"ops\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":87},\"id\":157,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"last\"],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"5..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"5xx - Server Errors\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"4..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"intervalFactor\":1,\"legendFormat\":\"4xx - Client Errors\",\"range\":true,\"refId\":\"B\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"3..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"3xx - Redirections\",\"range\":true,\"refId\":\"D\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"2..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"instant\":false,\"intervalFactor\":1,\"legendFormat\":\"2xx - Success\",\"range\":true,\"refId\":\"C\"}],\"title\":\"HTTP状态码\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":94},\"id\":4,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"table\",\"placement\":\"right\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{method}} [{{status}}] - {{uri}}\",\"refId\":\"A\"}],\"title\":\"HTTP请求数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"dashed+area\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"orange\",\"value\":3},{\"color\":\"red\",\"value\":5}]},\"unit\":\"s\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":101},\"id\":2,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"right\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(http_server_requests_seconds_sum{instance=~\\\"$instance\\\", application=\\\"$application\\\", exception=\\\"None\\\", uri!~\\\".*actuator.*\\\"}[5m]) / irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", exception=\\\"None\\\", uri!~\\\".*actuator.*\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{method}} [{{status}}] - {{uri}}\",\"range\":true,\"refId\":\"A\"}],\"title\":\"响应时间\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":108},\"id\":8,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"Logback日志\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":109},\"hiddenSeries\":false,\"id\":6,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"info\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"info\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"INFO logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"dashed+area\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":1}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":109},\"id\":10,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\",\"sum\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"error\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"error\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"title\":\"ERROR logs\",\"type\":\"timeseries\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":0,\"y\":116},\"hiddenSeries\":false,\"id\":14,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"warn\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"warn\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"WARN logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":8,\"y\":116},\"hiddenSeries\":false,\"id\":16,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"debug\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"debug\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"DEBUG logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":16,\"y\":116},\"hiddenSeries\":false,\"id\":20,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"trace\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"trace\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"TRACE logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}}],\"refresh\":\"\",\"schemaVersion\":38,\"style\":\"dark\",\"tags\":[],\"templating\":{\"list\":[{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"\",\"hide\":0,\"includeAll\":false,\"label\":\"Application\",\"multi\":false,\"name\":\"application\",\"options\":[],\"query\":\"label_values(application)\",\"refresh\":2,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":0,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"allFormat\":\"glob\",\"allValue\":\".*\",\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"\",\"hide\":0,\"includeAll\":true,\"label\":\"Instance\",\"multi\":false,\"multiFormat\":\"glob\",\"name\":\"instance\",\"options\":[],\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\"}, instance)\",\"refresh\":2,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":0,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(hikaricp_connections{application=\\\"$application\\\"}, pool)\",\"hide\":2,\"includeAll\":false,\"label\":\"HikariCP-Pool\",\"multi\":false,\"name\":\"hikaricp\",\"options\":[],\"query\":\"label_values(hikaricp_connections{application=\\\"$application\\\"}, pool)\",\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"heap\\\"},id)\",\"hide\":0,\"includeAll\":true,\"label\":\"Memory Pool (heap)\",\"multi\":false,\"name\":\"memory_pool_heap\",\"options\":[],\"query\":{\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"heap\\\"},id)\",\"refId\":\"PrometheusVariableQueryEditor-VariableQuery\"},\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"nonheap\\\"},id)\",\"hide\":0,\"includeAll\":true,\"label\":\"Memory Pool (nonheap)\",\"multi\":false,\"name\":\"memory_pool_nonheap\",\"options\":[],\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"nonheap\\\"},id)\",\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false}]},\"time\":{\"from\":\"now-6h\",\"to\":\"now\"},\"timepicker\":{\"refresh_intervals\":[\"10s\",\"30s\",\"1m\",\"5m\",\"15m\",\"30m\",\"1h\",\"2h\",\"1d\"],\"time_options\":[\"5m\",\"15m\",\"1h\",\"6h\",\"12h\",\"24h\",\"2d\",\"7d\",\"30d\"]},\"timezone\":\"\",\"title\":\"SpringBoot APM Dashboard（中文版本）\",\"uid\":\"sbapmwalker\",\"version\":1,\"weekStart\":\"\"}',1,'2025-08-16 00:53:33','2025-08-16 00:53:33',1,1,21319,'',0,0,0,'sbapmwalker',0);

/*!40000 ALTER TABLE `dashboard` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 dashboard_acl
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_acl`;

CREATE TABLE `dashboard_acl` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `permission` smallint NOT NULL DEFAULT '4',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_dashboard_acl_dashboard_id_user_id` (`dashboard_id`,`user_id`),
  UNIQUE KEY `UQE_dashboard_acl_dashboard_id_team_id` (`dashboard_id`,`team_id`),
  KEY `IDX_dashboard_acl_dashboard_id` (`dashboard_id`),
  KEY `IDX_dashboard_acl_user_id` (`user_id`),
  KEY `IDX_dashboard_acl_team_id` (`team_id`),
  KEY `IDX_dashboard_acl_org_id_role` (`org_id`,`role`),
  KEY `IDX_dashboard_acl_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `dashboard_acl` WRITE;
/*!40000 ALTER TABLE `dashboard_acl` DISABLE KEYS */;

INSERT INTO `dashboard_acl` (`id`, `org_id`, `dashboard_id`, `user_id`, `team_id`, `permission`, `role`, `created`, `updated`)
VALUES
	(1,-1,-1,NULL,NULL,1,'Viewer','2017-06-20 00:00:00','2017-06-20 00:00:00'),
	(2,-1,-1,NULL,NULL,2,'Editor','2017-06-20 00:00:00','2017-06-20 00:00:00');

/*!40000 ALTER TABLE `dashboard_acl` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 dashboard_provisioning
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_provisioning`;

CREATE TABLE `dashboard_provisioning` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dashboard_id` bigint DEFAULT NULL,
  `name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_id` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated` int NOT NULL DEFAULT '0',
  `check_sum` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_dashboard_provisioning_dashboard_id` (`dashboard_id`),
  KEY `IDX_dashboard_provisioning_dashboard_id_name` (`dashboard_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 dashboard_public
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_public`;

CREATE TABLE `dashboard_public` (
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dashboard_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `time_settings` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `template_variables` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `access_token` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` int NOT NULL,
  `updated_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime DEFAULT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `annotations_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `time_selection_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `share` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'public',
  PRIMARY KEY (`uid`),
  UNIQUE KEY `UQE_dashboard_public_config_uid` (`uid`),
  UNIQUE KEY `UQE_dashboard_public_config_access_token` (`access_token`),
  KEY `IDX_dashboard_public_config_org_id_dashboard_uid` (`org_id`,`dashboard_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 dashboard_snapshot
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_snapshot`;

CREATE TABLE `dashboard_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `delete_key` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `external` tinyint(1) NOT NULL,
  `external_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dashboard` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires` datetime NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `external_delete_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dashboard_encrypted` mediumblob,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_dashboard_snapshot_key` (`key`),
  UNIQUE KEY `UQE_dashboard_snapshot_delete_key` (`delete_key`),
  KEY `IDX_dashboard_snapshot_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 dashboard_tag
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_tag`;

CREATE TABLE `dashboard_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dashboard_id` bigint NOT NULL,
  `term` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_dashboard_tag_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 dashboard_version
# ------------------------------------------------------------

DROP TABLE IF EXISTS `dashboard_version`;

CREATE TABLE `dashboard_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dashboard_id` bigint NOT NULL,
  `parent_version` int NOT NULL,
  `restored_from` int NOT NULL,
  `version` int NOT NULL,
  `created` datetime NOT NULL,
  `created_by` bigint NOT NULL,
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_dashboard_version_dashboard_id_version` (`dashboard_id`,`version`),
  KEY `IDX_dashboard_version_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `dashboard_version` WRITE;
/*!40000 ALTER TABLE `dashboard_version` DISABLE KEYS */;

INSERT INTO `dashboard_version` (`id`, `dashboard_id`, `parent_version`, `restored_from`, `version`, `created`, `created_by`, `message`, `data`)
VALUES
	(20,8,105,0,1,'2025-08-16 00:53:33',1,'','{\"annotations\":{\"list\":[{\"builtIn\":1,\"datasource\":{\"type\":\"datasource\",\"uid\":\"grafana\"},\"enable\":true,\"hide\":true,\"iconColor\":\"rgba(0, 211, 255, 1)\",\"name\":\"Annotations \\u0026 Alerts\",\"type\":\"dashboard\"}]},\"description\":\"Spring Boot指标面板\",\"editable\":true,\"fiscalYearStartMonth\":0,\"gnetId\":21319,\"graphTooltip\":0,\"id\":null,\"links\":[],\"liveNow\":false,\"panels\":[{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":0},\"id\":54,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"基础信息\",\"type\":\"row\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"\",\"fieldConfig\":{\"defaults\":{\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"s\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":6,\"x\":0,\"y\":1},\"id\":52,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"fieldOptions\":{\"calcs\":[\"lastNotNull\"]},\"graphMode\":\"none\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"mean\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(process_uptime_seconds{application=\\\"$application\\\", instance=~\\\"$instance\\\"})\",\"format\":\"time_series\",\"hide\":false,\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"运行时间\",\"metric\":\"\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"运行时间\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"最大堆内存，-Xmx设置的参数\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null}]},\"unit\":\"decbytes\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":6,\"y\":1},\"id\":58,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"heap\\\"})\",\"format\":\"time_series\",\"instant\":true,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"堆内存\",\"range\":false,\"refId\":\"A\",\"step\":14400}],\"title\":\"堆内存\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"统计请求量，包含当前服务总体请求量以及最近5分钟内的请求量。\\n单位：次\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":0,\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":9,\"y\":1},\"id\":117,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"text\":{},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(http_server_requests_seconds_count{application=\\\"$application\\\",instance=~\\\"$instance\\\", uri!~\\\".*actuator.*\\\"})\",\"instant\":true,\"interval\":\"\",\"legendFormat\":\"总请求量\",\"refId\":\"A\"}],\"title\":\"总请求量\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"process_files_max_files：代表一个进程可以打开的最大文件描述符数量。在 Linux 中，每个进程都有一个文件描述符表，用于跟踪该进程打开的所有文件、套接字等。默认情况下，这个数量是有限的，超过显示会报错（too many open files）。\\nprocess_files_open_files：表示当前进程已经打开的文件描述符数量。\\n可以通过ulimit -a查看系统配置的最大文件描述符数量（open files）。可以在/etc/security/limits.conf修改\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"locale\"},\"overrides\":[{\"matcher\":{\"id\":\"byName\",\"options\":\"Max Files\"},\"properties\":[{\"id\":\"color\",\"value\":{\"fixedColor\":\"red\",\"mode\":\"fixed\"}}]}]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":12,\"y\":1},\"id\":66,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_files_open_files{application=\\\"$application\\\", instance=~\\\"$instance\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Open Files\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_files_max_files{application=\\\"$application\\\", instance=~\\\"$instance\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max Files\",\"range\":true,\"refId\":\"B\"}],\"title\":\"进程打开文件数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"服务启动的时间\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"dateTimeFromNow\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":6,\"x\":0,\"y\":4},\"id\":56,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"none\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"first\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"avg(process_start_time_seconds{application=\\\"$application\\\", instance=~\\\"$instance\\\"})*1000\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"启动时间\",\"metric\":\"\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"启动时间\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"非堆内存大小，方法区（Method Area）与 Java 堆一样，是各个线程共享的内存区域，它用于存储\\n已被虚拟机加载的类型信息、常量、静态变量、即时编译器编译后的代码缓存等数据。\\n虽然《Java 虚拟机规范》中把方法区描述为堆的一个逻辑部分，但是它却有一个别名叫\\n作“非堆”（Non-Heap），目的是与 Java 堆区分开来。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":1,\"mappings\":[{\"options\":{\"match\":\"null\",\"result\":{\"text\":\"N/A\"}},\"type\":\"special\"}],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null}]},\"unit\":\"decbytes\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":6,\"y\":4},\"id\":112,\"links\":[],\"maxDataPoints\":100,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"horizontal\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})\",\"format\":\"time_series\",\"instant\":true,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"非堆内存\",\"range\":false,\"refId\":\"A\",\"step\":14400}],\"title\":\"非堆内存\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"统计请求量，包含当前服务总体请求量以及最近5分钟内的请求量。\\n单位：次\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"thresholds\"},\"decimals\":0,\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":3,\"w\":3,\"x\":9,\"y\":4},\"id\":141,\"options\":{\"colorMode\":\"value\",\"graphMode\":\"area\",\"justifyMode\":\"auto\",\"orientation\":\"auto\",\"reduceOptions\":{\"calcs\":[\"lastNotNull\"],\"fields\":\"\",\"values\":false},\"text\":{},\"textMode\":\"auto\"},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(increase(http_server_requests_seconds_count{application=\\\"$application\\\",instance=~\\\"$instance\\\", uri!~\\\".*actuator.*\\\"}[5m]) )\",\"hide\":false,\"instant\":true,\"interval\":\"\",\"legendFormat\":\"请求量【近5分钟】\",\"refId\":\"B\"}],\"title\":\"请求量【近5分钟】\",\"type\":\"stat\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"QPS（每秒请求数）是指单位时间内的请求次数，是一个衡量系统吞吐量的重要指标。应用服务的请求QPS指的是在单位时间内，应用服务处理的请求数量。它可以帮助评估系统的性能，以及请求处理能力是否足够，是否需要对系统进行优化或扩容。\\n\\n优化应用服务请求QPS（每秒请求数）的方法可能包括：\\n\\n减少请求时间：通过优化代码，缩短请求执行时间，从而提高每秒请求数。\\n\\n增加服务器性能：通过升级服务器的配置，提高服务器的处理能力，从而提高每秒请求数。\\n\\n分布式系统：通过使用分布式系统，将请求分配到多台服务器上，从而提高每秒请求数。\\n\\n缓存：通过使用缓存，减少请求的数据处理时间，从而提高每秒请求数。\\n\\n减少请求数量：通过消除不必要的请求，减少请求数量，从而提高每秒请求数。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":7},\"id\":125,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":true,\"expr\":\"sum(irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m]))\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"当前数据\",\"range\":true,\"refId\":\"A\"}],\"title\":\"请求QPS\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"QPS（每秒请求数）是指单位时间内的请求次数，是一个衡量系统吞吐量的重要指标。应用服务的请求QPS指的是在单位时间内，应用服务处理的请求数量。它可以帮助评估系统的性能，以及请求处理能力是否足够，是否需要对系统进行优化或扩容。\\n\\n优化应用服务请求QPS（每秒请求数）的方法可能包括：\\n\\n减少请求时间：通过优化代码，缩短请求执行时间，从而提高每秒请求数。\\n\\n增加服务器性能：通过升级服务器的配置，提高服务器的处理能力，从而提高每秒请求数。\\n\\n分布式系统：通过使用分布式系统，将请求分配到多台服务器上，从而提高每秒请求数。\\n\\n缓存：通过使用缓存，减少请求的数据处理时间，从而提高每秒请求数。\\n\\n减少请求数量：通过消除不必要的请求，减少请求数量，从而提高每秒请求数。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":7},\"id\":133,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"lastNotNull\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"topk(5, sum by(uri, method) (rate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m])))\",\"format\":\"time_series\",\"instant\":false,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"{{method}}-{{uri}}\",\"range\":true,\"refId\":\"A\"}],\"title\":\"请求QPS(TOP5)\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"内存使用率\\n\\n堆内存使用率正常指标范围参考：\\n\\nJava应用会有一定的内存占用，但内存使用率应该保持在合理范围内。通常情况下，70-80%的内存使用率是可以接受的。\\n\\n如果内存使用率长期接近或达到100%，可能存在内存泄漏问题或内存不足。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":0,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":2,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"auto\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"decimals\":1,\"mappings\":[],\"max\":100,\"min\":0,\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"rgba(50, 172, 45, 0.97)\",\"value\":null},{\"color\":\"rgba(237, 129, 40, 0.89)\",\"value\":80},{\"color\":\"rgba(245, 54, 54, 0.9)\",\"value\":90}]},\"unit\":\"percent\"},\"overrides\":[]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":0,\"y\":14},\"id\":101,\"links\":[],\"maxDataPoints\":100,\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"single\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\", area=\\\"heap\\\"})*100/sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"heap\\\"})\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Heap Memory Usage（堆内存使用率）\",\"range\":true,\"refId\":\"A\",\"step\":14400},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})*100/sum(jvm_memory_max_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\", area=\\\"nonheap\\\"})\",\"hide\":false,\"instant\":false,\"legendFormat\":\"Non-Heap Memory Usage（非堆内存使用率）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"内存使用率（%）\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"应用所在磁盘使用率\\n\\n正常指标范围参考：\\n\\n这个指标其实和CPU数和内存都没啥关系，一般来说磁盘超过80%就要报警了。有的日志比较多的应用，可能在60%左右就需要来看了。\\n\\n一般为了让磁盘的利用率维持在一定水位上，机器都需要配置日志的自动清理。\\t\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":0,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":1,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"auto\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"decimals\":1,\"mappings\":[],\"max\":1,\"min\":0,\"noValue\":\"-\",\"thresholds\":{\"mode\":\"percentage\",\"steps\":[{\"color\":\"super-light-green\",\"value\":null},{\"color\":\"orange\",\"value\":60},{\"color\":\"red\",\"value\":80}]},\"unit\":\"percentunit\"},\"overrides\":[]},\"gridPos\":{\"h\":6,\"w\":12,\"x\":12,\"y\":14},\"id\":149,\"links\":[],\"maxDataPoints\":100,\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"single\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"(disk_total_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\"} - disk_free_bytes{application=\\\"$application\\\", instance=~\\\"$instance\\\"})/(disk_total_bytes{application=\\\"$application\\\",instance=~\\\"$instance\\\"})\",\"format\":\"time_series\",\"instant\":false,\"interval\":\"\",\"intervalFactor\":2,\"legendFormat\":\"{{path}}-磁盘使用率\",\"range\":true,\"refId\":\"A\",\"step\":14400}],\"title\":\"磁盘使用率（%）\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"正常指标范围参考：\\n\\n在空闲或轻负载情况下，CPU使用率应较低，通常在0-30%左右。\\n\\n在中等负载下，使用率可能在30-70%。\\n\\n高负载或高性能需求的情况下，CPU使用率可能会更高，但是最好不要长期超过80%。一般建议控制在70%以下。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"percentunit\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":20},\"id\":95,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_cpu_usage{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"System CPU Usage（系统CPU使用率）\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"process_cpu_usage{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Process CPU Usage（进程cpu使用率）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"CPU使用率\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"system_load_average_1m 通常指的是系统在过去1分钟内的平均负载（Load Average）。在Linux系统中，你可以使用多种命令来查看当前的平均负载，包括 uptime、top以及查看 /proc/loadavg 文件。load average后边会有三个值，分别代表1分钟，5分钟，15分钟负载情况。\\n\\n正常指标范围参考：\\n对于4核CPU，理想的系统负载值应低于4。超过这个值可能意味着过载。一般来说应该维持在50%以下，也就是2以下。\\n如果长时间超过75%，也就是3的话，那么就需要进行排查看看是不是存在问题了。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\",\"value\":null},{\"color\":\"red\",\"value\":80}]},\"unit\":\"short\"},\"overrides\":[{\"matcher\":{\"id\":\"byName\",\"options\":\"CPU Core Size（CPU核数）\"},\"properties\":[{\"id\":\"color\",\"value\":{\"fixedColor\":\"red\",\"mode\":\"fixed\"}}]}]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":20},\"id\":96,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_load_average_1m{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Load Average [1m](1分钟系统负载)\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"system_cpu_count{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"CPU Core Size（CPU核数）\",\"range\":true,\"refId\":\"B\"}],\"title\":\"Load average（系统负载）\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":27},\"id\":48,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"JVM内存\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"\",\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":6,\"x\":0,\"y\":28},\"hiddenSeries\":false,\"id\":85,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"repeat\":\"memory_pool_heap\",\"repeatDirection\":\"h\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used\",\"range\":true,\"refId\":\"C\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_committed_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Commited\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_max_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_heap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"$memory_pool_heap (heap)\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"bytes\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":8,\"x\":0,\"y\":44},\"hiddenSeries\":false,\"id\":88,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"repeat\":\"memory_pool_nonheap\",\"repeatDirection\":\"h\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Used\",\"refId\":\"C\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_memory_committed_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Commited\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_memory_max_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"$memory_pool_nonheap\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Max\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"$memory_pool_nonheap (non-heap)\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"bytes\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"decimals\":0,\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":12,\"x\":0,\"y\":52},\"hiddenSeries\":false,\"id\":50,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":false,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_classes_loaded_classes{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Classes Loaded\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Classes Loaded\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"decimals\":0,\"format\":\"locale\",\"label\":\"\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":8,\"w\":12,\"x\":12,\"y\":52},\"hiddenSeries\":false,\"id\":80,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(jvm_classes_unloaded_classes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Classes Unloaded\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Classes Unloaded\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":60},\"hiddenSeries\":false,\"id\":82,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"direct\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used Bytes\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_total_capacity_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"direct\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Capacity Bytes\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Direct Buffers\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":60},\"hiddenSeries\":false,\"id\":83,\"legend\":{\"avg\":false,\"current\":false,\"max\":false,\"min\":false,\"show\":true,\"total\":false,\"values\":false},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_memory_used_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"mapped\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Used Bytes\",\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"jvm_buffer_total_capacity_bytes{instance=~\\\"$instance\\\", application=\\\"$application\\\", id=\\\"mapped\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Capacity Bytes\",\"refId\":\"B\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"Mapped Buffers\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"short\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"在JVM（Java Virtual Machine）的性能监控和诊断中，jvm_threads_daemon_threads、jvm_threads_live_threads 和 jvm_threads_peak_threads 是常见的度量指标，用于描述JVM中线程的状态和数量。下面我将逐一解释这些指标：\\n\\njvm_threads_daemon_threads\\n定义：当前JVM中守护线程（Daemon Threads）的数量。\\n解释：守护线程是一种特殊的线程，它在其他所有非守护线程结束后自动结束。通常，守护线程用于执行后台任务，如垃圾回收线程就是一个守护线程。如果JVM中只剩下守护线程在运行，那么JVM会退出。\\njvm_threads_live_threads\\n定义：当前JVM中活动线程（Live Threads）的数量。\\n解释：活动线程是指已经启动但尚未结束的线程。这个数字通常包括了应用代码创建的线程，以及JVM内部使用的线程（如垃圾回收线程）。这个指标可以帮助你了解当前JVM的线程使用情况，如果这个数字持续很高，可能意味着你的应用正在创建大量的线程，这可能会导致性能问题或资源耗尽。\\njvm_threads_peak_threads\\n定义：从JVM启动到当前时间，活动线程数量的峰值。\\n解释：这个指标记录了JVM在其生命周期中活动线程数量的最大值。通过比较jvm_threads_live_threads和jvm_threads_peak_threads，你可以了解你的应用在何时达到了线程使用的峰值，并据此进行性能调优或资源规划。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"short\"},\"overrides\":[]},\"gridPos\":{\"h\":8,\"w\":12,\"x\":0,\"y\":67},\"id\":68,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_daemon_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"Daemon（守护线程）\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_live_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Live（活动线程）\",\"range\":true,\"refId\":\"B\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"jvm_threads_peak_threads{instance=~\\\"$instance\\\", application=\\\"$application\\\"}\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"Peak(活动线程峰值)\",\"range\":true,\"refId\":\"C\"}],\"title\":\"线程数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"description\":\"jvm_gc_memory_allocated_bytes_total 是一个 JVM 指标，用于记录自 JVM 启动以来分配的总内存大小。它是一个累积计数器，随着时间的推移，其值会不断增加，因为 JVM 会不断分配内存。\\njvm_gc_memory_promoted_bytes_total 则记录了从新生代（Young Generation）晋升到老年代（Old Generation）的内存大小总和。它反映了对象在垃圾回收过程中的存活率。\\n这两个指标都可以帮助你了解 JVM 中内存的使用情况和对象的生命周期。通过监控这些指标，你可以观察到应用程序中内存的分配和晋升情况，并根据需要调整内存分配和垃圾回收策略，以优化应用程序的性能和内存使用效率。\",\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"bytes\"},\"overrides\":[]},\"gridPos\":{\"h\":8,\"w\":12,\"x\":12,\"y\":67},\"id\":78,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_memory_allocated_bytes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"allocated\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_memory_promoted_bytes_total{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"promoted\",\"range\":true,\"refId\":\"B\"}],\"title\":\"Memory Allocate/Promote\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":75},\"id\":72,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"JVM-GC\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":10,\"w\":12,\"x\":0,\"y\":76},\"hiddenSeries\":false,\"id\":74,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":false,\"hideEmpty\":true,\"hideZero\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(jvm_gc_pause_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{action}} [{{cause}}]\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"GC Count\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"locale\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":10,\"w\":12,\"x\":12,\"y\":76},\"hiddenSeries\":false,\"id\":76,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":false,\"hideEmpty\":true,\"hideZero\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(jvm_gc_pause_seconds_sum{instance=~\\\"$instance\\\", application=\\\"$application\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{action}} [{{cause}}]\",\"range\":true,\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"GC Stop the World Duration\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"s\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":86},\"id\":18,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"吞吐量\",\"type\":\"row\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"smooth\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"}]},\"unit\":\"ops\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":87},\"id\":157,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"last\"],\"displayMode\":\"list\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"5..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"5xx - Server Errors\",\"range\":true,\"refId\":\"A\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"4..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"intervalFactor\":1,\"legendFormat\":\"4xx - Client Errors\",\"range\":true,\"refId\":\"B\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"3..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"interval\":\"\",\"intervalFactor\":1,\"legendFormat\":\"3xx - Redirections\",\"range\":true,\"refId\":\"D\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"exemplar\":false,\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"$application\\\", status=~\\\"2..\\\",instance=~\\\"$instance\\\"}[5m]))\",\"format\":\"time_series\",\"hide\":false,\"instant\":false,\"intervalFactor\":1,\"legendFormat\":\"2xx - Success\",\"range\":true,\"refId\":\"C\"}],\"title\":\"HTTP状态码\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"off\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":80}]},\"unit\":\"reqps\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":94},\"id\":4,\"links\":[],\"options\":{\"legend\":{\"calcs\":[],\"displayMode\":\"table\",\"placement\":\"right\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", uri!~\\\".*actuator.*\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{method}} [{{status}}] - {{uri}}\",\"refId\":\"A\"}],\"title\":\"HTTP请求数\",\"type\":\"timeseries\"},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"dashed+area\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"orange\",\"value\":3},{\"color\":\"red\",\"value\":5}]},\"unit\":\"s\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":24,\"x\":0,\"y\":101},\"id\":2,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"max\",\"min\"],\"displayMode\":\"table\",\"placement\":\"right\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"editorMode\":\"code\",\"expr\":\"irate(http_server_requests_seconds_sum{instance=~\\\"$instance\\\", application=\\\"$application\\\", exception=\\\"None\\\", uri!~\\\".*actuator.*\\\"}[5m]) / irate(http_server_requests_seconds_count{instance=~\\\"$instance\\\", application=\\\"$application\\\", exception=\\\"None\\\", uri!~\\\".*actuator.*\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"{{method}} [{{status}}] - {{uri}}\",\"range\":true,\"refId\":\"A\"}],\"title\":\"响应时间\",\"type\":\"timeseries\"},{\"collapsed\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"gridPos\":{\"h\":1,\"w\":24,\"x\":0,\"y\":108},\"id\":8,\"panels\":[],\"targets\":[{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"fb65406e-39df-40f2-966c-9987082bdcfe\"},\"refId\":\"A\"}],\"title\":\"Logback日志\",\"type\":\"row\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":12,\"x\":0,\"y\":109},\"hiddenSeries\":false,\"id\":6,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"info\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"info\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"INFO logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"color\":{\"mode\":\"palette-classic\"},\"custom\":{\"axisCenteredZero\":false,\"axisColorMode\":\"text\",\"axisLabel\":\"\",\"axisPlacement\":\"auto\",\"barAlignment\":0,\"drawStyle\":\"line\",\"fillOpacity\":10,\"gradientMode\":\"none\",\"hideFrom\":{\"legend\":false,\"tooltip\":false,\"viz\":false},\"lineInterpolation\":\"linear\",\"lineWidth\":1,\"pointSize\":5,\"scaleDistribution\":{\"type\":\"linear\"},\"showPoints\":\"never\",\"spanNulls\":false,\"stacking\":{\"group\":\"A\",\"mode\":\"none\"},\"thresholdsStyle\":{\"mode\":\"dashed+area\"}},\"links\":[],\"mappings\":[],\"thresholds\":{\"mode\":\"absolute\",\"steps\":[{\"color\":\"green\"},{\"color\":\"red\",\"value\":1}]},\"unit\":\"none\"},\"overrides\":[]},\"gridPos\":{\"h\":7,\"w\":12,\"x\":12,\"y\":109},\"id\":10,\"links\":[],\"options\":{\"legend\":{\"calcs\":[\"mean\",\"lastNotNull\",\"max\",\"min\",\"sum\"],\"displayMode\":\"table\",\"placement\":\"bottom\",\"showLegend\":true},\"tooltip\":{\"mode\":\"multi\",\"sort\":\"none\"}},\"pluginVersion\":\"10.0.1\",\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"error\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"error\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"title\":\"ERROR logs\",\"type\":\"timeseries\"},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":0,\"y\":116},\"hiddenSeries\":false,\"id\":14,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"warn\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"warn\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"WARN logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":8,\"y\":116},\"hiddenSeries\":false,\"id\":16,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"debug\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"debug\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"DEBUG logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}},{\"aliasColors\":{},\"bars\":false,\"dashLength\":10,\"dashes\":false,\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"fieldConfig\":{\"defaults\":{\"links\":[]},\"overrides\":[]},\"fill\":1,\"fillGradient\":0,\"gridPos\":{\"h\":7,\"w\":8,\"x\":16,\"y\":116},\"hiddenSeries\":false,\"id\":20,\"legend\":{\"alignAsTable\":true,\"avg\":true,\"current\":true,\"max\":true,\"min\":true,\"show\":true,\"total\":true,\"values\":true},\"lines\":true,\"linewidth\":1,\"links\":[],\"nullPointMode\":\"null\",\"options\":{\"alertThreshold\":true},\"percentage\":false,\"pluginVersion\":\"10.0.1\",\"pointradius\":5,\"points\":false,\"renderer\":\"flot\",\"seriesOverrides\":[],\"spaceLength\":10,\"stack\":false,\"steppedLine\":false,\"targets\":[{\"alias\":\"\",\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"expr\":\"irate(logback_events_total{instance=~\\\"$instance\\\", application=\\\"$application\\\", level=\\\"trace\\\"}[5m])\",\"format\":\"time_series\",\"intervalFactor\":1,\"legendFormat\":\"trace\",\"rawSql\":\"SELECT\\n  $__time(time_column),\\n  value1\\nFROM\\n  metric_table\\nWHERE\\n  $__timeFilter(time_column)\\n\",\"refId\":\"A\"}],\"thresholds\":[],\"timeRegions\":[],\"title\":\"TRACE logs\",\"tooltip\":{\"shared\":true,\"sort\":0,\"value_type\":\"individual\"},\"type\":\"graph\",\"xaxis\":{\"mode\":\"time\",\"show\":true,\"values\":[]},\"yaxes\":[{\"format\":\"none\",\"logBase\":1,\"show\":true},{\"format\":\"short\",\"logBase\":1,\"show\":true}],\"yaxis\":{\"align\":false}}],\"refresh\":\"\",\"schemaVersion\":38,\"style\":\"dark\",\"tags\":[],\"templating\":{\"list\":[{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"\",\"hide\":0,\"includeAll\":false,\"label\":\"Application\",\"multi\":false,\"name\":\"application\",\"options\":[],\"query\":\"label_values(application)\",\"refresh\":2,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":0,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"allFormat\":\"glob\",\"allValue\":\".*\",\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"\",\"hide\":0,\"includeAll\":true,\"label\":\"Instance\",\"multi\":false,\"multiFormat\":\"glob\",\"name\":\"instance\",\"options\":[],\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\"}, instance)\",\"refresh\":2,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":0,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(hikaricp_connections{application=\\\"$application\\\"}, pool)\",\"hide\":2,\"includeAll\":false,\"label\":\"HikariCP-Pool\",\"multi\":false,\"name\":\"hikaricp\",\"options\":[],\"query\":\"label_values(hikaricp_connections{application=\\\"$application\\\"}, pool)\",\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"heap\\\"},id)\",\"hide\":0,\"includeAll\":true,\"label\":\"Memory Pool (heap)\",\"multi\":false,\"name\":\"memory_pool_heap\",\"options\":[],\"query\":{\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"heap\\\"},id)\",\"refId\":\"PrometheusVariableQueryEditor-VariableQuery\"},\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false},{\"current\":{},\"datasource\":{\"type\":\"prometheus\",\"uid\":\"PBFA97CFB590B2093\"},\"definition\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"nonheap\\\"},id)\",\"hide\":0,\"includeAll\":true,\"label\":\"Memory Pool (nonheap)\",\"multi\":false,\"name\":\"memory_pool_nonheap\",\"options\":[],\"query\":\"label_values(jvm_memory_used_bytes{application=\\\"$application\\\", area=\\\"nonheap\\\"},id)\",\"refresh\":1,\"regex\":\"\",\"skipUrlSync\":false,\"sort\":1,\"tagValuesQuery\":\"\",\"tagsQuery\":\"\",\"type\":\"query\",\"useTags\":false}]},\"time\":{\"from\":\"now-6h\",\"to\":\"now\"},\"timepicker\":{\"refresh_intervals\":[\"10s\",\"30s\",\"1m\",\"5m\",\"15m\",\"30m\",\"1h\",\"2h\",\"1d\"],\"time_options\":[\"5m\",\"15m\",\"1h\",\"6h\",\"12h\",\"24h\",\"2d\",\"7d\",\"30d\"]},\"timezone\":\"\",\"title\":\"SpringBoot APM Dashboard（中文版本）\",\"uid\":\"sbapmwalker\",\"version\":1,\"weekStart\":\"\"}');

/*!40000 ALTER TABLE `dashboard_version` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 data_keys
# ------------------------------------------------------------

DROP TABLE IF EXISTS `data_keys`;

CREATE TABLE `data_keys` (
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `active` tinyint(1) NOT NULL,
  `scope` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_data` blob NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `data_keys` WRITE;
/*!40000 ALTER TABLE `data_keys` DISABLE KEYS */;

INSERT INTO `data_keys` (`name`, `active`, `scope`, `provider`, `encrypted_data`, `created`, `updated`, `label`)
VALUES
	('a7a7b592-c23d-4161-9677-9be82e1277fc',1,'root','secretKey.v1',X'2A5957567A4C574E6D59672A4F385035523730667AA24234616E81092D00D8C7EC84C92A4AAAFF57BEBC66A9C0C0380FDB9951F0','2025-07-26 11:06:07','2025-07-26 11:06:07','2025-07-26/root@secretKey.v1'),
	('b5eb347d-cd58-4f7c-a67c-3dc4ef33029d',1,'root','secretKey.v1',X'2A5957567A4C574E6D59672A327030686F4E62795B1F5536D0D2D6DDD8C3016C4EAB9FA69D1154804D0D689FF4A584CE5FF75F51','2023-10-28 04:06:57','2023-10-28 04:06:57','2023-10-28/root@secretKey.v1'),
	('b6b2e892-48ac-41dd-b3d0-2a3fcd832254',1,'root','secretKey.v1',X'2A5957567A4C574E6D59672A36574D36383475729833F09399C09CBFB8A08DBFF08BE2E28897BDD3F0B9E31A45E9C58C42F21737','2025-06-15 02:23:21','2025-06-15 02:23:21','2025-06-15/root@secretKey.v1'),
	('ba72b9b2-b78e-4185-bb4f-c671e1a85056',1,'root','secretKey.v1',X'2A5957567A4C574E6D59672A6178577A33334947783E5B3DD7D5550E413181DB3E6B19D085311F7B04335BF358EF3323F2BE5571','2025-08-16 00:26:35','2025-08-16 00:26:35','2025-08-16/root@secretKey.v1');

/*!40000 ALTER TABLE `data_keys` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 data_source
# ------------------------------------------------------------

DROP TABLE IF EXISTS `data_source`;

CREATE TABLE `data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `version` int NOT NULL,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `access` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `database` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `basic_auth` tinyint(1) NOT NULL,
  `basic_auth_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `basic_auth_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL,
  `json_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `with_credentials` tinyint(1) NOT NULL DEFAULT '0',
  `secure_json_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `read_only` tinyint(1) DEFAULT NULL,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_data_source_org_id_name` (`org_id`,`name`),
  UNIQUE KEY `UQE_data_source_org_id_uid` (`org_id`,`uid`),
  KEY `IDX_data_source_org_id` (`org_id`),
  KEY `IDX_data_source_org_id_is_default` (`org_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `data_source` WRITE;
/*!40000 ALTER TABLE `data_source` DISABLE KEYS */;

INSERT INTO `data_source` (`id`, `org_id`, `version`, `type`, `name`, `access`, `url`, `password`, `user`, `database`, `basic_auth`, `basic_auth_user`, `basic_auth_password`, `is_default`, `json_data`, `created`, `updated`, `with_credentials`, `secure_json_data`, `read_only`, `uid`)
VALUES
	(1,1,1,'prometheus','Prometheus','proxy','http://prometheus:9090','','','',0,'','',1,'{}','2023-10-28 04:06:57','2025-08-16 01:00:02',0,'{}',1,'PBFA97CFB590B2093'),
	(2,1,2,'elasticsearch','Elasticsearch','proxy','http://127.0.0.1:9200','','','',0,'','',0,'{\"includeFrozen\":false,\"logLevelField\":\"\",\"logMessageField\":\"\",\"maxConcurrentShardRequests\":5,\"timeField\":\"@timestamp\"}','2025-08-16 00:51:30','2025-08-16 00:52:02',0,'{}',0,'dbdef136-59da-44ba-bcd2-24d009b1208a'),
	(3,1,1,'mysql','MySQL','proxy','','','','',0,'','',0,'{}','2025-08-16 00:52:30','2025-08-16 00:52:30',0,'{}',0,'a44321fb-1490-4f36-ac62-0476715980bb'),
	(4,1,1,'prometheus','Prometheus-1','proxy','','','','',0,'','',0,'{}','2025-08-16 01:01:08','2025-08-16 01:01:08',0,'{}',0,'dd6c30be-c21a-4727-99ab-6668e99c2e40');

/*!40000 ALTER TABLE `data_source` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 entity_event
# ------------------------------------------------------------

DROP TABLE IF EXISTS `entity_event`;

CREATE TABLE `entity_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_id` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 file
# ------------------------------------------------------------

DROP TABLE IF EXISTS `file`;

CREATE TABLE `file` (
  `path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `path_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_folder_path_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `contents` mediumblob,
  `etag` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `cache_control` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_disposition` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated` datetime NOT NULL,
  `created` datetime NOT NULL,
  `size` bigint NOT NULL,
  `mime_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UQE_file_path_hash` (`path_hash`),
  KEY `IDX_file_parent_folder_path_hash` (`parent_folder_path_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 file_meta
# ------------------------------------------------------------

DROP TABLE IF EXISTS `file_meta`;

CREATE TABLE `file_meta` (
  `path_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UQE_file_meta_path_hash_key` (`path_hash`,`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 folder
# ------------------------------------------------------------

DROP TABLE IF EXISTS `folder`;

CREATE TABLE `folder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `title` varchar(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parent_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_folder_uid_org_id` (`uid`,`org_id`),
  UNIQUE KEY `UQE_folder_title_parent_uid_org_id` (`title`,`parent_uid`,`org_id`),
  KEY `IDX_folder_parent_uid_org_id` (`parent_uid`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 kv_store
# ------------------------------------------------------------

DROP TABLE IF EXISTS `kv_store`;

CREATE TABLE `kv_store` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `namespace` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_kv_store_org_id_namespace_key` (`org_id`,`namespace`,`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `kv_store` WRITE;
/*!40000 ALTER TABLE `kv_store` DISABLE KEYS */;

INSERT INTO `kv_store` (`id`, `org_id`, `namespace`, `key`, `value`, `created`, `updated`)
VALUES
	(1,0,'ngalert.migration','migrated','true','2023-10-28 04:06:55','2023-10-28 04:06:58'),
	(2,1,'ngalert.migration','stateKey','{\"orgId\":1,\"createdFolders\":[]}','2023-10-28 04:06:57','2023-10-28 04:06:57'),
	(3,0,'datasource','secretMigrationStatus','compatible','2023-10-28 04:06:57','2023-10-28 04:06:57'),
	(4,0,'plugin.publickeys','key-7e4d0c6a708866e7','-----BEGIN PGP PUBLIC KEY BLOCK-----\r\nVersion: OpenPGP.js v4.10.1\r\nComment: https://openpgpjs.org\r\n\r\nxpMEXpTXXxMFK4EEACMEIwQBiOUQhvGbDLvndE0fEXaR0908wXzPGFpf0P0Z\r\nHJ06tsq+0higIYHp7WTNJVEZtcwoYLcPRGaa9OQqbUU63BEyZdgAkPTz3RFd\r\n5+TkDWZizDcaVFhzbDd500yTwexrpIrdInwC/jrgs7Zy/15h8KA59XXUkdmT\r\nYB6TR+OA9RKME+dCJozNGUdyYWZhbmEgPGVuZ0BncmFmYW5hLmNvbT7CvAQQ\r\nEwoAIAUCXpTXXwYLCQcIAwIEFQgKAgQWAgEAAhkBAhsDAh4BAAoJEH5NDGpw\r\niGbnaWoCCQGQ3SQnCkRWrG6XrMkXOKfDTX2ow9fuoErN46BeKmLM4f1EkDZQ\r\nTpq3SE8+My8B5BIH3SOcBeKzi3S57JHGBdFA+wIJAYWMrJNIvw8GeXne+oUo\r\nNzzACdvfqXAZEp/HFMQhCKfEoWGJE8d2YmwY2+3GufVRTI5lQnZOHLE8L/Vc\r\n1S5MXESjzpcEXpTXXxIFK4EEACMEIwQBtHX/SD5Qm3v4V92qpaIZQgtTX0sT\r\ncFPjYWAHqsQ1iENrYN/vg1wU3ADlYATvydOQYvkTyT/tbDvx2Fse8PL84MQA\r\nYKKQ6AJ3gLVvmeouZdU03YoV4MYaT8KbnJUkZQZkqdz2riOlySNI9CG3oYmv\r\nomjUAtzgAgnCcurfGLZkkMxlmY8DAQoJwqQEGBMKAAkFAl6U118CGwwACgkQ\r\nfk0ManCIZuc0jAIJAVw2xdLr4ZQqPUhubrUyFcqlWoW8dQoQagwO8s8ubmby\r\nKuLA9FWJkfuuRQr+O9gHkDVCez3aism7zmJBqIOi38aNAgjJ3bo6leSS2jR/\r\nx5NqiKVi83tiXDPncDQYPymOnMhW0l7CVA7wj75HrFvvlRI/4MArlbsZ2tBn\r\nN1c5v9v/4h6qeA==\r\n=DNbR\r\n-----END PGP PUBLIC KEY BLOCK-----\r\n','2023-10-28 04:06:58','2023-10-28 04:06:58'),
	(5,0,'plugin.publickeys','last_updated','2025-08-16T00:26:36Z','2023-10-28 04:06:58','2025-08-16 00:26:36'),
	(6,1,'alertmanager','notifications','','2023-10-28 04:28:01','2023-10-28 04:28:01'),
	(7,1,'alertmanager','silences','','2023-10-28 04:28:01','2023-10-28 04:28:01'),
	(8,0,'infra.usagestats','anonymous_id','c05bbfeb-c993-416b-a542-b21484367cd8','2023-10-29 04:12:00','2023-10-29 04:12:00'),
	(9,0,'infra.usagestats','last_sent','2025-08-16T00:28:36Z','2023-10-29 04:12:01','2025-08-16 00:28:36');

/*!40000 ALTER TABLE `kv_store` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 library_element
# ------------------------------------------------------------

DROP TABLE IF EXISTS `library_element`;

CREATE TABLE `library_element` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `folder_id` bigint NOT NULL,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` bigint NOT NULL,
  `type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `created_by` bigint NOT NULL,
  `updated` datetime NOT NULL,
  `updated_by` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_library_element_org_id_folder_id_name_kind` (`org_id`,`folder_id`,`name`,`kind`),
  UNIQUE KEY `UQE_library_element_org_id_uid` (`org_id`,`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 library_element_connection
# ------------------------------------------------------------

DROP TABLE IF EXISTS `library_element_connection`;

CREATE TABLE `library_element_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `element_id` bigint NOT NULL,
  `kind` bigint NOT NULL,
  `connection_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `created_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_library_element_connection_element_id_kind_connection_id` (`element_id`,`kind`,`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 login_attempt
# ------------------------------------------------------------

DROP TABLE IF EXISTS `login_attempt`;

CREATE TABLE `login_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `IDX_login_attempt_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 migration_log
# ------------------------------------------------------------

DROP TABLE IF EXISTS `migration_log`;

CREATE TABLE `migration_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `migration_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sql` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `success` tinyint(1) NOT NULL,
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `migration_log` WRITE;
/*!40000 ALTER TABLE `migration_log` DISABLE KEYS */;

INSERT INTO `migration_log` (`id`, `migration_id`, `sql`, `success`, `error`, `timestamp`)
VALUES
	(1,'create migration_log table','CREATE TABLE IF NOT EXISTS `migration_log` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `migration_id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `sql` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `success` TINYINT(1) NOT NULL\n, `error` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `timestamp` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:28'),
	(2,'create user table','CREATE TABLE IF NOT EXISTS `user` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` INT NOT NULL\n, `login` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `salt` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `rands` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `company` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `account_id` BIGINT(20) NOT NULL\n, `is_admin` TINYINT(1) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:28'),
	(3,'add unique index user.login','CREATE UNIQUE INDEX `UQE_user_login` ON `user` (`login`);',1,'','2023-10-28 04:02:28'),
	(4,'add unique index user.email','CREATE UNIQUE INDEX `UQE_user_email` ON `user` (`email`);',1,'','2023-10-28 04:02:28'),
	(5,'drop index UQE_user_login - v1','DROP INDEX `UQE_user_login` ON `user`',1,'','2023-10-28 04:02:28'),
	(6,'drop index UQE_user_email - v1','DROP INDEX `UQE_user_email` ON `user`',1,'','2023-10-28 04:02:28'),
	(7,'Rename table user to user_v1 - v1','ALTER TABLE `user` RENAME TO `user_v1`',1,'','2023-10-28 04:02:28'),
	(8,'create user table v2','CREATE TABLE IF NOT EXISTS `user` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` INT NOT NULL\n, `login` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `salt` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `rands` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `company` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `org_id` BIGINT(20) NOT NULL\n, `is_admin` TINYINT(1) NOT NULL\n, `email_verified` TINYINT(1) NULL\n, `theme` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:28'),
	(9,'create index UQE_user_login - v2','CREATE UNIQUE INDEX `UQE_user_login` ON `user` (`login`);',1,'','2023-10-28 04:02:28'),
	(10,'create index UQE_user_email - v2','CREATE UNIQUE INDEX `UQE_user_email` ON `user` (`email`);',1,'','2023-10-28 04:02:28'),
	(11,'copy data_source v1 to v2','INSERT INTO `user` (`id`\n, `login`\n, `rands`\n, `updated`\n, `company`\n, `org_id`\n, `is_admin`\n, `version`\n, `email`\n, `name`\n, `password`\n, `salt`\n, `created`) SELECT `id`\n, `login`\n, `rands`\n, `updated`\n, `company`\n, `account_id`\n, `is_admin`\n, `version`\n, `email`\n, `name`\n, `password`\n, `salt`\n, `created` FROM `user_v1`',1,'','2023-10-28 04:02:28'),
	(12,'Drop old table user_v1','DROP TABLE IF EXISTS `user_v1`',1,'','2023-10-28 04:02:28'),
	(13,'Add column help_flags1 to user table','alter table `user` ADD COLUMN `help_flags1` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:28'),
	(14,'Update user table charset','ALTER TABLE `user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `login` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `salt` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `rands` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `company` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `theme` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:28'),
	(15,'Add last_seen_at column to user','alter table `user` ADD COLUMN `last_seen_at` DATETIME NULL ',1,'','2023-10-28 04:02:28'),
	(16,'Add missing user data','code migration',1,'','2023-10-28 04:02:28'),
	(17,'Add is_disabled column to user','alter table `user` ADD COLUMN `is_disabled` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:28'),
	(18,'Add index user.login/user.email','CREATE INDEX `IDX_user_login_email` ON `user` (`login`,`email`);',1,'','2023-10-28 04:02:28'),
	(19,'Add is_service_account column to user','alter table `user` ADD COLUMN `is_service_account` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:28'),
	(20,'Update is_service_account column to nullable','ALTER TABLE user MODIFY is_service_account BOOLEAN DEFAULT 0;',1,'','2023-10-28 04:02:28'),
	(21,'create temp user table v1-7','CREATE TABLE IF NOT EXISTS `temp_user` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `version` INT NOT NULL\n, `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `code` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `status` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `invited_by_user_id` BIGINT(20) NULL\n, `email_sent` TINYINT(1) NOT NULL\n, `email_sent_on` DATETIME NULL\n, `remote_addr` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:28'),
	(22,'create index IDX_temp_user_email - v1-7','CREATE INDEX `IDX_temp_user_email` ON `temp_user` (`email`);',1,'','2023-10-28 04:02:28'),
	(23,'create index IDX_temp_user_org_id - v1-7','CREATE INDEX `IDX_temp_user_org_id` ON `temp_user` (`org_id`);',1,'','2023-10-28 04:02:28'),
	(24,'create index IDX_temp_user_code - v1-7','CREATE INDEX `IDX_temp_user_code` ON `temp_user` (`code`);',1,'','2023-10-28 04:02:29'),
	(25,'create index IDX_temp_user_status - v1-7','CREATE INDEX `IDX_temp_user_status` ON `temp_user` (`status`);',1,'','2023-10-28 04:02:29'),
	(26,'Update temp_user table charset','ALTER TABLE `temp_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `code` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `status` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `remote_addr` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:29'),
	(27,'drop index IDX_temp_user_email - v1','DROP INDEX `IDX_temp_user_email` ON `temp_user`',1,'','2023-10-28 04:02:29'),
	(28,'drop index IDX_temp_user_org_id - v1','DROP INDEX `IDX_temp_user_org_id` ON `temp_user`',1,'','2023-10-28 04:02:29'),
	(29,'drop index IDX_temp_user_code - v1','DROP INDEX `IDX_temp_user_code` ON `temp_user`',1,'','2023-10-28 04:02:29'),
	(30,'drop index IDX_temp_user_status - v1','DROP INDEX `IDX_temp_user_status` ON `temp_user`',1,'','2023-10-28 04:02:29'),
	(31,'Rename table temp_user to temp_user_tmp_qwerty - v1','ALTER TABLE `temp_user` RENAME TO `temp_user_tmp_qwerty`',1,'','2023-10-28 04:02:29'),
	(32,'create temp_user v2','CREATE TABLE IF NOT EXISTS `temp_user` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `version` INT NOT NULL\n, `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `code` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `status` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `invited_by_user_id` BIGINT(20) NULL\n, `email_sent` TINYINT(1) NOT NULL\n, `email_sent_on` DATETIME NULL\n, `remote_addr` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` INT NOT NULL DEFAULT 0\n, `updated` INT NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(33,'create index IDX_temp_user_email - v2','CREATE INDEX `IDX_temp_user_email` ON `temp_user` (`email`);',1,'','2023-10-28 04:02:29'),
	(34,'create index IDX_temp_user_org_id - v2','CREATE INDEX `IDX_temp_user_org_id` ON `temp_user` (`org_id`);',1,'','2023-10-28 04:02:29'),
	(35,'create index IDX_temp_user_code - v2','CREATE INDEX `IDX_temp_user_code` ON `temp_user` (`code`);',1,'','2023-10-28 04:02:29'),
	(36,'create index IDX_temp_user_status - v2','CREATE INDEX `IDX_temp_user_status` ON `temp_user` (`status`);',1,'','2023-10-28 04:02:29'),
	(37,'copy temp_user v1 to v2','INSERT INTO `temp_user` (`status`\n, `invited_by_user_id`\n, `email_sent`\n, `remote_addr`\n, `version`\n, `email`\n, `name`\n, `role`\n, `id`\n, `org_id`\n, `code`\n, `email_sent_on`) SELECT `status`\n, `invited_by_user_id`\n, `email_sent`\n, `remote_addr`\n, `version`\n, `email`\n, `name`\n, `role`\n, `id`\n, `org_id`\n, `code`\n, `email_sent_on` FROM `temp_user_tmp_qwerty`',1,'','2023-10-28 04:02:29'),
	(38,'drop temp_user_tmp_qwerty','DROP TABLE IF EXISTS `temp_user_tmp_qwerty`',1,'','2023-10-28 04:02:29'),
	(39,'Set created for temp users that will otherwise prematurely expire','code migration',1,'','2023-10-28 04:02:29'),
	(40,'create star table','CREATE TABLE IF NOT EXISTS `star` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `dashboard_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(41,'add unique index star.user_id_dashboard_id','CREATE UNIQUE INDEX `UQE_star_user_id_dashboard_id` ON `star` (`user_id`,`dashboard_id`);',1,'','2023-10-28 04:02:29'),
	(42,'create org table v1','CREATE TABLE IF NOT EXISTS `org` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` INT NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `address1` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `address2` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `city` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `state` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `zip_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `country` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `billing_email` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(43,'create index UQE_org_name - v1','CREATE UNIQUE INDEX `UQE_org_name` ON `org` (`name`);',1,'','2023-10-28 04:02:29'),
	(44,'create org_user table v1','CREATE TABLE IF NOT EXISTS `org_user` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(45,'create index IDX_org_user_org_id - v1','CREATE INDEX `IDX_org_user_org_id` ON `org_user` (`org_id`);',1,'','2023-10-28 04:02:29'),
	(46,'create index UQE_org_user_org_id_user_id - v1','CREATE UNIQUE INDEX `UQE_org_user_org_id_user_id` ON `org_user` (`org_id`,`user_id`);',1,'','2023-10-28 04:02:29'),
	(47,'create index IDX_org_user_user_id - v1','CREATE INDEX `IDX_org_user_user_id` ON `org_user` (`user_id`);',1,'','2023-10-28 04:02:29'),
	(48,'Update org table charset','ALTER TABLE `org` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `address1` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `address2` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `city` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `state` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `zip_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `country` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `billing_email` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:29'),
	(49,'Update org_user table charset','ALTER TABLE `org_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:29'),
	(50,'Migrate all Read Only Viewers to Viewers','UPDATE org_user SET role = \'Viewer\' WHERE role = \'Read Only Editor\'',1,'','2023-10-28 04:02:29'),
	(51,'create dashboard table','CREATE TABLE IF NOT EXISTS `dashboard` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` INT NOT NULL\n, `slug` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `account_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(52,'add index dashboard.account_id','CREATE INDEX `IDX_dashboard_account_id` ON `dashboard` (`account_id`);',1,'','2023-10-28 04:02:29'),
	(53,'add unique index dashboard_account_id_slug','CREATE UNIQUE INDEX `UQE_dashboard_account_id_slug` ON `dashboard` (`account_id`,`slug`);',1,'','2023-10-28 04:02:29'),
	(54,'create dashboard_tag table','CREATE TABLE IF NOT EXISTS `dashboard_tag` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `dashboard_id` BIGINT(20) NOT NULL\n, `term` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(55,'add unique index dashboard_tag.dasboard_id_term','CREATE UNIQUE INDEX `UQE_dashboard_tag_dashboard_id_term` ON `dashboard_tag` (`dashboard_id`,`term`);',1,'','2023-10-28 04:02:29'),
	(56,'drop index UQE_dashboard_tag_dashboard_id_term - v1','DROP INDEX `UQE_dashboard_tag_dashboard_id_term` ON `dashboard_tag`',1,'','2023-10-28 04:02:29'),
	(57,'Rename table dashboard to dashboard_v1 - v1','ALTER TABLE `dashboard` RENAME TO `dashboard_v1`',1,'','2023-10-28 04:02:29'),
	(58,'create dashboard v2','CREATE TABLE IF NOT EXISTS `dashboard` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` INT NOT NULL\n, `slug` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:29'),
	(59,'create index IDX_dashboard_org_id - v2','CREATE INDEX `IDX_dashboard_org_id` ON `dashboard` (`org_id`);',1,'','2023-10-28 04:02:29'),
	(60,'create index UQE_dashboard_org_id_slug - v2','CREATE UNIQUE INDEX `UQE_dashboard_org_id_slug` ON `dashboard` (`org_id`,`slug`);',1,'','2023-10-28 04:02:29'),
	(61,'copy dashboard v1 to v2','INSERT INTO `dashboard` (`slug`\n, `title`\n, `data`\n, `org_id`\n, `created`\n, `updated`\n, `id`\n, `version`) SELECT `slug`\n, `title`\n, `data`\n, `account_id`\n, `created`\n, `updated`\n, `id`\n, `version` FROM `dashboard_v1`',1,'','2023-10-28 04:02:30'),
	(62,'drop table dashboard_v1','DROP TABLE IF EXISTS `dashboard_v1`',1,'','2023-10-28 04:02:30'),
	(63,'alter dashboard.data to mediumtext v1','ALTER TABLE dashboard MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:30'),
	(64,'Add column updated_by in dashboard - v2','alter table `dashboard` ADD COLUMN `updated_by` INT NULL ',1,'','2023-10-28 04:02:30'),
	(65,'Add column created_by in dashboard - v2','alter table `dashboard` ADD COLUMN `created_by` INT NULL ',1,'','2023-10-28 04:02:30'),
	(66,'Add column gnetId in dashboard','alter table `dashboard` ADD COLUMN `gnet_id` BIGINT(20) NULL ',1,'','2023-10-28 04:02:30'),
	(67,'Add index for gnetId in dashboard','CREATE INDEX `IDX_dashboard_gnet_id` ON `dashboard` (`gnet_id`);',1,'','2023-10-28 04:02:30'),
	(68,'Add column plugin_id in dashboard','alter table `dashboard` ADD COLUMN `plugin_id` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:30'),
	(69,'Add index for plugin_id in dashboard','CREATE INDEX `IDX_dashboard_org_id_plugin_id` ON `dashboard` (`org_id`,`plugin_id`);',1,'','2023-10-28 04:02:30'),
	(70,'Add index for dashboard_id in dashboard_tag','CREATE INDEX `IDX_dashboard_tag_dashboard_id` ON `dashboard_tag` (`dashboard_id`);',1,'','2023-10-28 04:02:30'),
	(71,'Update dashboard table charset','ALTER TABLE `dashboard` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `slug` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `plugin_id` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `data` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:30'),
	(72,'Update dashboard_tag table charset','ALTER TABLE `dashboard_tag` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `term` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:30'),
	(73,'Add column folder_id in dashboard','alter table `dashboard` ADD COLUMN `folder_id` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:30'),
	(74,'Add column isFolder in dashboard','alter table `dashboard` ADD COLUMN `is_folder` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:30'),
	(75,'Add column has_acl in dashboard','alter table `dashboard` ADD COLUMN `has_acl` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:30'),
	(76,'Add column uid in dashboard','alter table `dashboard` ADD COLUMN `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:30'),
	(77,'Update uid column values in dashboard','UPDATE dashboard SET uid=lpad(id,9,\'0\') WHERE uid IS NULL;',1,'','2023-10-28 04:02:30'),
	(78,'Add unique index dashboard_org_id_uid','CREATE UNIQUE INDEX `UQE_dashboard_org_id_uid` ON `dashboard` (`org_id`,`uid`);',1,'','2023-10-28 04:02:30'),
	(79,'Remove unique index org_id_slug','DROP INDEX `UQE_dashboard_org_id_slug` ON `dashboard`',1,'','2023-10-28 04:02:30'),
	(80,'Update dashboard title length','ALTER TABLE `dashboard` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `title` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:30'),
	(81,'Add unique index for dashboard_org_id_title_folder_id','CREATE UNIQUE INDEX `UQE_dashboard_org_id_folder_id_title` ON `dashboard` (`org_id`,`folder_id`,`title`);',1,'','2023-10-28 04:02:30'),
	(82,'create dashboard_provisioning','CREATE TABLE IF NOT EXISTS `dashboard_provisioning` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `dashboard_id` BIGINT(20) NULL\n, `name` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `external_id` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:30'),
	(83,'Rename table dashboard_provisioning to dashboard_provisioning_tmp_qwerty - v1','ALTER TABLE `dashboard_provisioning` RENAME TO `dashboard_provisioning_tmp_qwerty`',1,'','2023-10-28 04:02:30'),
	(84,'create dashboard_provisioning v2','CREATE TABLE IF NOT EXISTS `dashboard_provisioning` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `dashboard_id` BIGINT(20) NULL\n, `name` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `external_id` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated` INT NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:30'),
	(85,'create index IDX_dashboard_provisioning_dashboard_id - v2','CREATE INDEX `IDX_dashboard_provisioning_dashboard_id` ON `dashboard_provisioning` (`dashboard_id`);',1,'','2023-10-28 04:02:30'),
	(86,'create index IDX_dashboard_provisioning_dashboard_id_name - v2','CREATE INDEX `IDX_dashboard_provisioning_dashboard_id_name` ON `dashboard_provisioning` (`dashboard_id`,`name`);',1,'','2023-10-28 04:02:30'),
	(87,'copy dashboard_provisioning v1 to v2','INSERT INTO `dashboard_provisioning` (`id`\n, `dashboard_id`\n, `name`\n, `external_id`) SELECT `id`\n, `dashboard_id`\n, `name`\n, `external_id` FROM `dashboard_provisioning_tmp_qwerty`',1,'','2023-10-28 04:02:30'),
	(88,'drop dashboard_provisioning_tmp_qwerty','DROP TABLE IF EXISTS `dashboard_provisioning_tmp_qwerty`',1,'','2023-10-28 04:02:30'),
	(89,'Add check_sum column','alter table `dashboard_provisioning` ADD COLUMN `check_sum` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:30'),
	(90,'Add index for dashboard_title','CREATE INDEX `IDX_dashboard_title` ON `dashboard` (`title`);',1,'','2023-10-28 04:02:30'),
	(91,'delete tags for deleted dashboards','DELETE FROM dashboard_tag WHERE dashboard_id NOT IN (SELECT id FROM dashboard)',1,'','2023-10-28 04:02:30'),
	(92,'delete stars for deleted dashboards','DELETE FROM star WHERE dashboard_id NOT IN (SELECT id FROM dashboard)',1,'','2023-10-28 04:02:30'),
	(93,'Add index for dashboard_is_folder','CREATE INDEX `IDX_dashboard_is_folder` ON `dashboard` (`is_folder`);',1,'','2023-10-28 04:02:30'),
	(94,'Add isPublic for dashboard','alter table `dashboard` ADD COLUMN `is_public` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:30'),
	(95,'create data_source table','CREATE TABLE IF NOT EXISTS `data_source` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `account_id` BIGINT(20) NOT NULL\n, `version` INT NOT NULL\n, `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `access` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `database` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `basic_auth` TINYINT(1) NOT NULL\n, `basic_auth_user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `basic_auth_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `is_default` TINYINT(1) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:31'),
	(96,'add index data_source.account_id','CREATE INDEX `IDX_data_source_account_id` ON `data_source` (`account_id`);',1,'','2023-10-28 04:02:31'),
	(97,'add unique index data_source.account_id_name','CREATE UNIQUE INDEX `UQE_data_source_account_id_name` ON `data_source` (`account_id`,`name`);',1,'','2023-10-28 04:02:31'),
	(98,'drop index IDX_data_source_account_id - v1','DROP INDEX `IDX_data_source_account_id` ON `data_source`',1,'','2023-10-28 04:02:31'),
	(99,'drop index UQE_data_source_account_id_name - v1','DROP INDEX `UQE_data_source_account_id_name` ON `data_source`',1,'','2023-10-28 04:02:31'),
	(100,'Rename table data_source to data_source_v1 - v1','ALTER TABLE `data_source` RENAME TO `data_source_v1`',1,'','2023-10-28 04:02:31'),
	(101,'create data_source table v2','CREATE TABLE IF NOT EXISTS `data_source` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `version` INT NOT NULL\n, `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `access` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `database` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `basic_auth` TINYINT(1) NOT NULL\n, `basic_auth_user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `basic_auth_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `is_default` TINYINT(1) NOT NULL\n, `json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:31'),
	(102,'create index IDX_data_source_org_id - v2','CREATE INDEX `IDX_data_source_org_id` ON `data_source` (`org_id`);',1,'','2023-10-28 04:02:31'),
	(103,'create index UQE_data_source_org_id_name - v2','CREATE UNIQUE INDEX `UQE_data_source_org_id_name` ON `data_source` (`org_id`,`name`);',1,'','2023-10-28 04:02:31'),
	(104,'Drop old table data_source_v1 #2','DROP TABLE IF EXISTS `data_source_v1`',1,'','2023-10-28 04:02:31'),
	(105,'Add column with_credentials','alter table `data_source` ADD COLUMN `with_credentials` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:31'),
	(106,'Add secure json data column','alter table `data_source` ADD COLUMN `secure_json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:31'),
	(107,'Update data_source table charset','ALTER TABLE `data_source` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `access` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `database` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `basic_auth_user` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `basic_auth_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `secure_json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:31'),
	(108,'Update initial version to 1','UPDATE data_source SET version = 1 WHERE version = 0',1,'','2023-10-28 04:02:31'),
	(109,'Add read_only data column','alter table `data_source` ADD COLUMN `read_only` TINYINT(1) NULL ',1,'','2023-10-28 04:02:31'),
	(110,'Migrate logging ds to loki ds','UPDATE data_source SET type = \'loki\' WHERE type = \'logging\'',1,'','2023-10-28 04:02:31'),
	(111,'Update json_data with nulls','UPDATE data_source SET json_data = \'{}\' WHERE json_data is null',1,'','2023-10-28 04:02:31'),
	(112,'Add uid column','alter table `data_source` ADD COLUMN `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:31'),
	(113,'Update uid value','UPDATE data_source SET uid=lpad(id,9,\'0\');',1,'','2023-10-28 04:02:31'),
	(114,'Add unique index datasource_org_id_uid','CREATE UNIQUE INDEX `UQE_data_source_org_id_uid` ON `data_source` (`org_id`,`uid`);',1,'','2023-10-28 04:02:31'),
	(115,'add unique index datasource_org_id_is_default','CREATE INDEX `IDX_data_source_org_id_is_default` ON `data_source` (`org_id`,`is_default`);',1,'','2023-10-28 04:02:31'),
	(116,'create api_key table','CREATE TABLE IF NOT EXISTS `api_key` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `account_id` BIGINT(20) NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `role` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:31'),
	(117,'add index api_key.account_id','CREATE INDEX `IDX_api_key_account_id` ON `api_key` (`account_id`);',1,'','2023-10-28 04:02:31'),
	(118,'add index api_key.key','CREATE UNIQUE INDEX `UQE_api_key_key` ON `api_key` (`key`);',1,'','2023-10-28 04:02:31'),
	(119,'add index api_key.account_id_name','CREATE UNIQUE INDEX `UQE_api_key_account_id_name` ON `api_key` (`account_id`,`name`);',1,'','2023-10-28 04:02:31'),
	(120,'drop index IDX_api_key_account_id - v1','DROP INDEX `IDX_api_key_account_id` ON `api_key`',1,'','2023-10-28 04:02:31'),
	(121,'drop index UQE_api_key_key - v1','DROP INDEX `UQE_api_key_key` ON `api_key`',1,'','2023-10-28 04:02:31'),
	(122,'drop index UQE_api_key_account_id_name - v1','DROP INDEX `UQE_api_key_account_id_name` ON `api_key`',1,'','2023-10-28 04:02:31'),
	(123,'Rename table api_key to api_key_v1 - v1','ALTER TABLE `api_key` RENAME TO `api_key_v1`',1,'','2023-10-28 04:02:31'),
	(124,'create api_key table v2','CREATE TABLE IF NOT EXISTS `api_key` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `role` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:31'),
	(125,'create index IDX_api_key_org_id - v2','CREATE INDEX `IDX_api_key_org_id` ON `api_key` (`org_id`);',1,'','2023-10-28 04:02:31'),
	(126,'create index UQE_api_key_key - v2','CREATE UNIQUE INDEX `UQE_api_key_key` ON `api_key` (`key`);',1,'','2023-10-28 04:02:31'),
	(127,'create index UQE_api_key_org_id_name - v2','CREATE UNIQUE INDEX `UQE_api_key_org_id_name` ON `api_key` (`org_id`,`name`);',1,'','2023-10-28 04:02:31'),
	(128,'copy api_key v1 to v2','INSERT INTO `api_key` (`org_id`\n, `name`\n, `key`\n, `role`\n, `created`\n, `updated`\n, `id`) SELECT `account_id`\n, `name`\n, `key`\n, `role`\n, `created`\n, `updated`\n, `id` FROM `api_key_v1`',1,'','2023-10-28 04:02:31'),
	(129,'Drop old table api_key_v1','DROP TABLE IF EXISTS `api_key_v1`',1,'','2023-10-28 04:02:31'),
	(130,'Update api_key table charset','ALTER TABLE `api_key` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `role` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:31'),
	(131,'Add expires to api_key table','alter table `api_key` ADD COLUMN `expires` BIGINT(20) NULL ',1,'','2023-10-28 04:02:31'),
	(132,'Add service account foreign key','alter table `api_key` ADD COLUMN `service_account_id` BIGINT(20) NULL ',1,'','2023-10-28 04:02:32'),
	(133,'set service account foreign key to nil if 0','UPDATE api_key SET service_account_id = NULL WHERE service_account_id = 0;',1,'','2023-10-28 04:02:32'),
	(134,'Add last_used_at to api_key table','alter table `api_key` ADD COLUMN `last_used_at` DATETIME NULL ',1,'','2023-10-28 04:02:32'),
	(135,'Add is_revoked column to api_key table','alter table `api_key` ADD COLUMN `is_revoked` TINYINT(1) NULL DEFAULT 0 ',1,'','2023-10-28 04:02:32'),
	(136,'create dashboard_snapshot table v4','CREATE TABLE IF NOT EXISTS `dashboard_snapshot` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `dashboard` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `expires` DATETIME NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(137,'drop table dashboard_snapshot_v4 #1','DROP TABLE IF EXISTS `dashboard_snapshot`',1,'','2023-10-28 04:02:32'),
	(138,'create dashboard_snapshot table v5 #2','CREATE TABLE IF NOT EXISTS `dashboard_snapshot` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `delete_key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `external` TINYINT(1) NOT NULL\n, `external_url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `dashboard` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `expires` DATETIME NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(139,'create index UQE_dashboard_snapshot_key - v5','CREATE UNIQUE INDEX `UQE_dashboard_snapshot_key` ON `dashboard_snapshot` (`key`);',1,'','2023-10-28 04:02:32'),
	(140,'create index UQE_dashboard_snapshot_delete_key - v5','CREATE UNIQUE INDEX `UQE_dashboard_snapshot_delete_key` ON `dashboard_snapshot` (`delete_key`);',1,'','2023-10-28 04:02:32'),
	(141,'create index IDX_dashboard_snapshot_user_id - v5','CREATE INDEX `IDX_dashboard_snapshot_user_id` ON `dashboard_snapshot` (`user_id`);',1,'','2023-10-28 04:02:32'),
	(142,'alter dashboard_snapshot to mediumtext v2','ALTER TABLE dashboard_snapshot MODIFY dashboard MEDIUMTEXT;',1,'','2023-10-28 04:02:32'),
	(143,'Update dashboard_snapshot table charset','ALTER TABLE `dashboard_snapshot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `delete_key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `external_url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `dashboard` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:32'),
	(144,'Add column external_delete_url to dashboard_snapshots table','alter table `dashboard_snapshot` ADD COLUMN `external_delete_url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:32'),
	(145,'Add encrypted dashboard json column','alter table `dashboard_snapshot` ADD COLUMN `dashboard_encrypted` BLOB NULL ',1,'','2023-10-28 04:02:32'),
	(146,'Change dashboard_encrypted column to MEDIUMBLOB','ALTER TABLE dashboard_snapshot MODIFY dashboard_encrypted MEDIUMBLOB;',1,'','2023-10-28 04:02:32'),
	(147,'create quota table v1','CREATE TABLE IF NOT EXISTS `quota` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NULL\n, `user_id` BIGINT(20) NULL\n, `target` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `limit` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(148,'create index UQE_quota_org_id_user_id_target - v1','CREATE UNIQUE INDEX `UQE_quota_org_id_user_id_target` ON `quota` (`org_id`,`user_id`,`target`);',1,'','2023-10-28 04:02:32'),
	(149,'Update quota table charset','ALTER TABLE `quota` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `target` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:32'),
	(150,'create plugin_setting table','CREATE TABLE IF NOT EXISTS `plugin_setting` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NULL\n, `plugin_id` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `enabled` TINYINT(1) NOT NULL\n, `pinned` TINYINT(1) NOT NULL\n, `json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `secure_json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(151,'create index UQE_plugin_setting_org_id_plugin_id - v1','CREATE UNIQUE INDEX `UQE_plugin_setting_org_id_plugin_id` ON `plugin_setting` (`org_id`,`plugin_id`);',1,'','2023-10-28 04:02:32'),
	(152,'Add column plugin_version to plugin_settings','alter table `plugin_setting` ADD COLUMN `plugin_version` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:32'),
	(153,'Update plugin_setting table charset','ALTER TABLE `plugin_setting` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `plugin_id` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `secure_json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `plugin_version` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:32'),
	(154,'create session table','CREATE TABLE IF NOT EXISTS `session` (\n`key` CHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `data` BLOB NOT NULL\n, `expiry` INTEGER(255) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(155,'Drop old table playlist table','DROP TABLE IF EXISTS `playlist`',1,'','2023-10-28 04:02:32'),
	(156,'Drop old table playlist_item table','DROP TABLE IF EXISTS `playlist_item`',1,'','2023-10-28 04:02:32'),
	(157,'create playlist table v2','CREATE TABLE IF NOT EXISTS `playlist` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `interval` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(158,'create playlist item table v2','CREATE TABLE IF NOT EXISTS `playlist_item` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `playlist_id` BIGINT(20) NOT NULL\n, `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `value` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `title` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `order` INT NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:32'),
	(159,'Update playlist table charset','ALTER TABLE `playlist` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `interval` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:32'),
	(160,'Update playlist_item table charset','ALTER TABLE `playlist_item` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `value` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `title` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:32'),
	(161,'Add playlist column created_at','alter table `playlist` ADD COLUMN `created_at` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:32'),
	(162,'Add playlist column updated_at','alter table `playlist` ADD COLUMN `updated_at` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:32'),
	(163,'drop preferences table v2','DROP TABLE IF EXISTS `preferences`',1,'','2023-10-28 04:02:33'),
	(164,'drop preferences table v3','DROP TABLE IF EXISTS `preferences`',1,'','2023-10-28 04:02:33'),
	(165,'create preferences table v3','CREATE TABLE IF NOT EXISTS `preferences` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `version` INT NOT NULL\n, `home_dashboard_id` BIGINT(20) NOT NULL\n, `timezone` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `theme` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(166,'Update preferences table charset','ALTER TABLE `preferences` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `timezone` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `theme` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:33'),
	(167,'Add column team_id in preferences','alter table `preferences` ADD COLUMN `team_id` BIGINT(20) NULL ',1,'','2023-10-28 04:02:33'),
	(168,'Update team_id column values in preferences','UPDATE preferences SET team_id=0 WHERE team_id IS NULL;',1,'','2023-10-28 04:02:33'),
	(169,'Add column week_start in preferences','alter table `preferences` ADD COLUMN `week_start` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:33'),
	(170,'Add column preferences.json_data','alter table `preferences` ADD COLUMN `json_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:33'),
	(171,'alter preferences.json_data to mediumtext v1','ALTER TABLE preferences MODIFY json_data MEDIUMTEXT;',1,'','2023-10-28 04:02:33'),
	(172,'Add preferences index org_id','CREATE INDEX `IDX_preferences_org_id` ON `preferences` (`org_id`);',1,'','2023-10-28 04:02:33'),
	(173,'Add preferences index user_id','CREATE INDEX `IDX_preferences_user_id` ON `preferences` (`user_id`);',1,'','2023-10-28 04:02:33'),
	(174,'create alert table v1','CREATE TABLE IF NOT EXISTS `alert` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `version` BIGINT(20) NOT NULL\n, `dashboard_id` BIGINT(20) NOT NULL\n, `panel_id` BIGINT(20) NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `state` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `frequency` BIGINT(20) NOT NULL\n, `handler` BIGINT(20) NOT NULL\n, `severity` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `silenced` TINYINT(1) NOT NULL\n, `execution_error` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `eval_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `eval_date` DATETIME NULL\n, `new_state_date` DATETIME NOT NULL\n, `state_changes` INT NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(175,'add index alert org_id & id ','CREATE INDEX `IDX_alert_org_id_id` ON `alert` (`org_id`,`id`);',1,'','2023-10-28 04:02:33'),
	(176,'add index alert state','CREATE INDEX `IDX_alert_state` ON `alert` (`state`);',1,'','2023-10-28 04:02:33'),
	(177,'add index alert dashboard_id','CREATE INDEX `IDX_alert_dashboard_id` ON `alert` (`dashboard_id`);',1,'','2023-10-28 04:02:33'),
	(178,'Create alert_rule_tag table v1','CREATE TABLE IF NOT EXISTS `alert_rule_tag` (\n`alert_id` BIGINT(20) NOT NULL\n, `tag_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(179,'Add unique index alert_rule_tag.alert_id_tag_id','CREATE UNIQUE INDEX `UQE_alert_rule_tag_alert_id_tag_id` ON `alert_rule_tag` (`alert_id`,`tag_id`);',1,'','2023-10-28 04:02:33'),
	(180,'drop index UQE_alert_rule_tag_alert_id_tag_id - v1','DROP INDEX `UQE_alert_rule_tag_alert_id_tag_id` ON `alert_rule_tag`',1,'','2023-10-28 04:02:33'),
	(181,'Rename table alert_rule_tag to alert_rule_tag_v1 - v1','ALTER TABLE `alert_rule_tag` RENAME TO `alert_rule_tag_v1`',1,'','2023-10-28 04:02:33'),
	(182,'Create alert_rule_tag table v2','CREATE TABLE IF NOT EXISTS `alert_rule_tag` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `alert_id` BIGINT(20) NOT NULL\n, `tag_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(183,'create index UQE_alert_rule_tag_alert_id_tag_id - Add unique index alert_rule_tag.alert_id_tag_id V2','CREATE UNIQUE INDEX `UQE_alert_rule_tag_alert_id_tag_id` ON `alert_rule_tag` (`alert_id`,`tag_id`);',1,'','2023-10-28 04:02:33'),
	(184,'copy alert_rule_tag v1 to v2','INSERT INTO `alert_rule_tag` (`alert_id`\n, `tag_id`) SELECT `alert_id`\n, `tag_id` FROM `alert_rule_tag_v1`',1,'','2023-10-28 04:02:33'),
	(185,'drop table alert_rule_tag_v1','DROP TABLE IF EXISTS `alert_rule_tag_v1`',1,'','2023-10-28 04:02:33'),
	(186,'create alert_notification table v1','CREATE TABLE IF NOT EXISTS `alert_notification` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(187,'Add column is_default','alter table `alert_notification` ADD COLUMN `is_default` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:33'),
	(188,'Add column frequency','alter table `alert_notification` ADD COLUMN `frequency` BIGINT(20) NULL ',1,'','2023-10-28 04:02:33'),
	(189,'Add column send_reminder','alter table `alert_notification` ADD COLUMN `send_reminder` TINYINT(1) NULL DEFAULT 0 ',1,'','2023-10-28 04:02:33'),
	(190,'Add column disable_resolve_message','alter table `alert_notification` ADD COLUMN `disable_resolve_message` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:33'),
	(191,'add index alert_notification org_id & name','CREATE UNIQUE INDEX `UQE_alert_notification_org_id_name` ON `alert_notification` (`org_id`,`name`);',1,'','2023-10-28 04:02:33'),
	(192,'Update alert table charset','ALTER TABLE `alert` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `state` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `severity` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `execution_error` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `eval_data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ;',1,'','2023-10-28 04:02:33'),
	(193,'Update alert_notification table charset','ALTER TABLE `alert_notification` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:33'),
	(194,'create notification_journal table v1','CREATE TABLE IF NOT EXISTS `alert_notification_journal` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `alert_id` BIGINT(20) NOT NULL\n, `notifier_id` BIGINT(20) NOT NULL\n, `sent_at` BIGINT(20) NOT NULL\n, `success` TINYINT(1) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(195,'add index notification_journal org_id & alert_id & notifier_id','CREATE INDEX `IDX_alert_notification_journal_org_id_alert_id_notifier_id` ON `alert_notification_journal` (`org_id`,`alert_id`,`notifier_id`);',1,'','2023-10-28 04:02:33'),
	(196,'drop alert_notification_journal','DROP TABLE IF EXISTS `alert_notification_journal`',1,'','2023-10-28 04:02:33'),
	(197,'create alert_notification_state table v1','CREATE TABLE IF NOT EXISTS `alert_notification_state` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `alert_id` BIGINT(20) NOT NULL\n, `notifier_id` BIGINT(20) NOT NULL\n, `state` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `version` BIGINT(20) NOT NULL\n, `updated_at` BIGINT(20) NOT NULL\n, `alert_rule_state_updated_version` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:33'),
	(198,'add index alert_notification_state org_id & alert_id & notifier_id','CREATE UNIQUE INDEX `UQE_alert_notification_state_org_id_alert_id_notifier_id` ON `alert_notification_state` (`org_id`,`alert_id`,`notifier_id`);',1,'','2023-10-28 04:02:34'),
	(199,'Add for to alert table','alter table `alert` ADD COLUMN `for` BIGINT(20) NULL ',1,'','2023-10-28 04:02:34'),
	(200,'Add column uid in alert_notification','alter table `alert_notification` ADD COLUMN `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:34'),
	(201,'Update uid column values in alert_notification','UPDATE alert_notification SET uid=lpad(id,9,\'0\') WHERE uid IS NULL;',1,'','2023-10-28 04:02:34'),
	(202,'Add unique index alert_notification_org_id_uid','CREATE UNIQUE INDEX `UQE_alert_notification_org_id_uid` ON `alert_notification` (`org_id`,`uid`);',1,'','2023-10-28 04:02:34'),
	(203,'Remove unique index org_id_name','DROP INDEX `UQE_alert_notification_org_id_name` ON `alert_notification`',1,'','2023-10-28 04:02:34'),
	(204,'Add column secure_settings in alert_notification','alter table `alert_notification` ADD COLUMN `secure_settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:34'),
	(205,'alter alert.settings to mediumtext','ALTER TABLE alert MODIFY settings MEDIUMTEXT;',1,'','2023-10-28 04:02:34'),
	(206,'Add non-unique index alert_notification_state_alert_id','CREATE INDEX `IDX_alert_notification_state_alert_id` ON `alert_notification_state` (`alert_id`);',1,'','2023-10-28 04:02:34'),
	(207,'Add non-unique index alert_rule_tag_alert_id','CREATE INDEX `IDX_alert_rule_tag_alert_id` ON `alert_rule_tag` (`alert_id`);',1,'','2023-10-28 04:02:34'),
	(208,'Drop old annotation table v4','DROP TABLE IF EXISTS `annotation`',1,'','2023-10-28 04:02:34'),
	(209,'create annotation table v5','CREATE TABLE IF NOT EXISTS `annotation` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `alert_id` BIGINT(20) NULL\n, `user_id` BIGINT(20) NULL\n, `dashboard_id` BIGINT(20) NULL\n, `panel_id` BIGINT(20) NULL\n, `category_id` BIGINT(20) NULL\n, `type` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `title` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `text` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `metric` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `prev_state` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `new_state` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `epoch` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:34'),
	(210,'add index annotation 0 v3','CREATE INDEX `IDX_annotation_org_id_alert_id` ON `annotation` (`org_id`,`alert_id`);',1,'','2023-10-28 04:02:34'),
	(211,'add index annotation 1 v3','CREATE INDEX `IDX_annotation_org_id_type` ON `annotation` (`org_id`,`type`);',1,'','2023-10-28 04:02:34'),
	(212,'add index annotation 2 v3','CREATE INDEX `IDX_annotation_org_id_category_id` ON `annotation` (`org_id`,`category_id`);',1,'','2023-10-28 04:02:34'),
	(213,'add index annotation 3 v3','CREATE INDEX `IDX_annotation_org_id_dashboard_id_panel_id_epoch` ON `annotation` (`org_id`,`dashboard_id`,`panel_id`,`epoch`);',1,'','2023-10-28 04:02:34'),
	(214,'add index annotation 4 v3','CREATE INDEX `IDX_annotation_org_id_epoch` ON `annotation` (`org_id`,`epoch`);',1,'','2023-10-28 04:02:34'),
	(215,'Update annotation table charset','ALTER TABLE `annotation` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `type` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `title` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `text` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `metric` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL , MODIFY `prev_state` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `new_state` VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL , MODIFY `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:34'),
	(216,'Add column region_id to annotation table','alter table `annotation` ADD COLUMN `region_id` BIGINT(20) NULL DEFAULT 0 ',1,'','2023-10-28 04:02:34'),
	(217,'Drop category_id index','DROP INDEX `IDX_annotation_org_id_category_id` ON `annotation`',1,'','2023-10-28 04:02:34'),
	(218,'Add column tags to annotation table','alter table `annotation` ADD COLUMN `tags` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:34'),
	(219,'Create annotation_tag table v2','CREATE TABLE IF NOT EXISTS `annotation_tag` (\n`annotation_id` BIGINT(20) NOT NULL\n, `tag_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:34'),
	(220,'Add unique index annotation_tag.annotation_id_tag_id','CREATE UNIQUE INDEX `UQE_annotation_tag_annotation_id_tag_id` ON `annotation_tag` (`annotation_id`,`tag_id`);',1,'','2023-10-28 04:02:34'),
	(221,'drop index UQE_annotation_tag_annotation_id_tag_id - v2','DROP INDEX `UQE_annotation_tag_annotation_id_tag_id` ON `annotation_tag`',1,'','2023-10-28 04:02:34'),
	(222,'Rename table annotation_tag to annotation_tag_v2 - v2','ALTER TABLE `annotation_tag` RENAME TO `annotation_tag_v2`',1,'','2023-10-28 04:02:34'),
	(223,'Create annotation_tag table v3','CREATE TABLE IF NOT EXISTS `annotation_tag` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `annotation_id` BIGINT(20) NOT NULL\n, `tag_id` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:34'),
	(224,'create index UQE_annotation_tag_annotation_id_tag_id - Add unique index annotation_tag.annotation_id_tag_id V3','CREATE UNIQUE INDEX `UQE_annotation_tag_annotation_id_tag_id` ON `annotation_tag` (`annotation_id`,`tag_id`);',1,'','2023-10-28 04:02:34'),
	(225,'copy annotation_tag v2 to v3','INSERT INTO `annotation_tag` (`tag_id`\n, `annotation_id`) SELECT `tag_id`\n, `annotation_id` FROM `annotation_tag_v2`',1,'','2023-10-28 04:02:34'),
	(226,'drop table annotation_tag_v2','DROP TABLE IF EXISTS `annotation_tag_v2`',1,'','2023-10-28 04:02:34'),
	(227,'Update alert annotations and set TEXT to empty','UPDATE annotation SET TEXT = \'\' WHERE alert_id > 0',1,'','2023-10-28 04:02:35'),
	(228,'Add created time to annotation table','alter table `annotation` ADD COLUMN `created` BIGINT(20) NULL DEFAULT 0 ',1,'','2023-10-28 04:02:35'),
	(229,'Add updated time to annotation table','alter table `annotation` ADD COLUMN `updated` BIGINT(20) NULL DEFAULT 0 ',1,'','2023-10-28 04:02:35'),
	(230,'Add index for created in annotation table','CREATE INDEX `IDX_annotation_org_id_created` ON `annotation` (`org_id`,`created`);',1,'','2023-10-28 04:02:35'),
	(231,'Add index for updated in annotation table','CREATE INDEX `IDX_annotation_org_id_updated` ON `annotation` (`org_id`,`updated`);',1,'','2023-10-28 04:02:35'),
	(232,'Convert existing annotations from seconds to milliseconds','UPDATE annotation SET epoch = (epoch*1000) where epoch < 9999999999',1,'','2023-10-28 04:02:35'),
	(233,'Add epoch_end column','alter table `annotation` ADD COLUMN `epoch_end` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:35'),
	(234,'Add index for epoch_end','CREATE INDEX `IDX_annotation_org_id_epoch_epoch_end` ON `annotation` (`org_id`,`epoch`,`epoch_end`);',1,'','2023-10-28 04:02:35'),
	(235,'Make epoch_end the same as epoch','UPDATE annotation SET epoch_end = epoch',1,'','2023-10-28 04:02:35'),
	(236,'Move region to single row','code migration',1,'','2023-10-28 04:02:35'),
	(237,'Remove index org_id_epoch from annotation table','DROP INDEX `IDX_annotation_org_id_epoch` ON `annotation`',1,'','2023-10-28 04:02:35'),
	(238,'Remove index org_id_dashboard_id_panel_id_epoch from annotation table','DROP INDEX `IDX_annotation_org_id_dashboard_id_panel_id_epoch` ON `annotation`',1,'','2023-10-28 04:02:35'),
	(239,'Add index for org_id_dashboard_id_epoch_end_epoch on annotation table','CREATE INDEX `IDX_annotation_org_id_dashboard_id_epoch_end_epoch` ON `annotation` (`org_id`,`dashboard_id`,`epoch_end`,`epoch`);',1,'','2023-10-28 04:02:35'),
	(240,'Add index for org_id_epoch_end_epoch on annotation table','CREATE INDEX `IDX_annotation_org_id_epoch_end_epoch` ON `annotation` (`org_id`,`epoch_end`,`epoch`);',1,'','2023-10-28 04:02:35'),
	(241,'Remove index org_id_epoch_epoch_end from annotation table','DROP INDEX `IDX_annotation_org_id_epoch_epoch_end` ON `annotation`',1,'','2023-10-28 04:02:35'),
	(242,'Add index for alert_id on annotation table','CREATE INDEX `IDX_annotation_alert_id` ON `annotation` (`alert_id`);',1,'','2023-10-28 04:02:35'),
	(243,'Increase tags column to length 4096','ALTER TABLE annotation MODIFY tags VARCHAR(4096);',1,'','2023-10-28 04:02:35'),
	(244,'create test_data table','CREATE TABLE IF NOT EXISTS `test_data` (\n`id` INT PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `metric1` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `metric2` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `value_big_int` BIGINT(20) NULL\n, `value_double` DOUBLE NULL\n, `value_float` FLOAT NULL\n, `value_int` INT NULL\n, `time_epoch` BIGINT(20) NOT NULL\n, `time_date_time` DATETIME NOT NULL\n, `time_time_stamp` TIMESTAMP NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:35'),
	(245,'create dashboard_version table v1','CREATE TABLE IF NOT EXISTS `dashboard_version` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `dashboard_id` BIGINT(20) NOT NULL\n, `parent_version` INT NOT NULL\n, `restored_from` INT NOT NULL\n, `version` INT NOT NULL\n, `created` DATETIME NOT NULL\n, `created_by` BIGINT(20) NOT NULL\n, `message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:35'),
	(246,'add index dashboard_version.dashboard_id','CREATE INDEX `IDX_dashboard_version_dashboard_id` ON `dashboard_version` (`dashboard_id`);',1,'','2023-10-28 04:02:35'),
	(247,'add unique index dashboard_version.dashboard_id and dashboard_version.version','CREATE UNIQUE INDEX `UQE_dashboard_version_dashboard_id_version` ON `dashboard_version` (`dashboard_id`,`version`);',1,'','2023-10-28 04:02:35'),
	(248,'Set dashboard version to 1 where 0','UPDATE dashboard SET version = 1 WHERE version = 0',1,'','2023-10-28 04:02:35'),
	(249,'save existing dashboard data in dashboard_version table v1','INSERT INTO dashboard_version\n(\n	dashboard_id,\n	version,\n	parent_version,\n	restored_from,\n	created,\n	created_by,\n	message,\n	data\n)\nSELECT\n	dashboard.id,\n	dashboard.version,\n	dashboard.version,\n	dashboard.version,\n	dashboard.updated,\n	COALESCE(dashboard.updated_by, -1),\n	\'\',\n	dashboard.data\nFROM dashboard;',1,'','2023-10-28 04:02:35'),
	(250,'alter dashboard_version.data to mediumtext v1','ALTER TABLE dashboard_version MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:35'),
	(251,'create team table','CREATE TABLE IF NOT EXISTS `team` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:35'),
	(252,'add index team.org_id','CREATE INDEX `IDX_team_org_id` ON `team` (`org_id`);',1,'','2023-10-28 04:02:35'),
	(253,'add unique index team_org_id_name','CREATE UNIQUE INDEX `UQE_team_org_id_name` ON `team` (`org_id`,`name`);',1,'','2023-10-28 04:02:35'),
	(254,'Add column uid in team','alter table `team` ADD COLUMN `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:35'),
	(255,'Update uid column values in team','UPDATE team SET uid=concat(\'t\',lpad(id,9,\'0\')) WHERE uid IS NULL;',1,'','2023-10-28 04:02:35'),
	(256,'Add unique index team_org_id_uid','CREATE UNIQUE INDEX `UQE_team_org_id_uid` ON `team` (`org_id`,`uid`);',1,'','2023-10-28 04:02:35'),
	(257,'create team member table','CREATE TABLE IF NOT EXISTS `team_member` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `team_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:35'),
	(258,'add index team_member.org_id','CREATE INDEX `IDX_team_member_org_id` ON `team_member` (`org_id`);',1,'','2023-10-28 04:02:35'),
	(259,'add unique index team_member_org_id_team_id_user_id','CREATE UNIQUE INDEX `UQE_team_member_org_id_team_id_user_id` ON `team_member` (`org_id`,`team_id`,`user_id`);',1,'','2023-10-28 04:02:35'),
	(260,'add index team_member.team_id','CREATE INDEX `IDX_team_member_team_id` ON `team_member` (`team_id`);',1,'','2023-10-28 04:02:35'),
	(261,'Add column email to team table','alter table `team` ADD COLUMN `email` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:35'),
	(262,'Add column external to team_member table','alter table `team_member` ADD COLUMN `external` TINYINT(1) NULL ',1,'','2023-10-28 04:02:35'),
	(263,'Add column permission to team_member table','alter table `team_member` ADD COLUMN `permission` SMALLINT NULL ',1,'','2023-10-28 04:02:35'),
	(264,'create dashboard acl table','CREATE TABLE IF NOT EXISTS `dashboard_acl` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `dashboard_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NULL\n, `team_id` BIGINT(20) NULL\n, `permission` SMALLINT NOT NULL DEFAULT 4\n, `role` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:35'),
	(265,'add index dashboard_acl_dashboard_id','CREATE INDEX `IDX_dashboard_acl_dashboard_id` ON `dashboard_acl` (`dashboard_id`);',1,'','2023-10-28 04:02:36'),
	(266,'add unique index dashboard_acl_dashboard_id_user_id','CREATE UNIQUE INDEX `UQE_dashboard_acl_dashboard_id_user_id` ON `dashboard_acl` (`dashboard_id`,`user_id`);',1,'','2023-10-28 04:02:36'),
	(267,'add unique index dashboard_acl_dashboard_id_team_id','CREATE UNIQUE INDEX `UQE_dashboard_acl_dashboard_id_team_id` ON `dashboard_acl` (`dashboard_id`,`team_id`);',1,'','2023-10-28 04:02:36'),
	(268,'add index dashboard_acl_user_id','CREATE INDEX `IDX_dashboard_acl_user_id` ON `dashboard_acl` (`user_id`);',1,'','2023-10-28 04:02:36'),
	(269,'add index dashboard_acl_team_id','CREATE INDEX `IDX_dashboard_acl_team_id` ON `dashboard_acl` (`team_id`);',1,'','2023-10-28 04:02:36'),
	(270,'add index dashboard_acl_org_id_role','CREATE INDEX `IDX_dashboard_acl_org_id_role` ON `dashboard_acl` (`org_id`,`role`);',1,'','2023-10-28 04:02:36'),
	(271,'add index dashboard_permission','CREATE INDEX `IDX_dashboard_acl_permission` ON `dashboard_acl` (`permission`);',1,'','2023-10-28 04:02:36'),
	(272,'save default acl rules in dashboard_acl table','\nINSERT INTO dashboard_acl\n	(\n		org_id,\n		dashboard_id,\n		permission,\n		role,\n		created,\n		updated\n	)\n	VALUES\n		(-1,-1, 1,\'Viewer\',\'2017-06-20\',\'2017-06-20\'),\n		(-1,-1, 2,\'Editor\',\'2017-06-20\',\'2017-06-20\')\n	',1,'','2023-10-28 04:02:36'),
	(273,'delete acl rules for deleted dashboards and folders','DELETE FROM dashboard_acl WHERE dashboard_id NOT IN (SELECT id FROM dashboard) AND dashboard_id != -1',1,'','2023-10-28 04:02:36'),
	(274,'create tag table','CREATE TABLE IF NOT EXISTS `tag` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `key` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `value` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(275,'add index tag.key_value','CREATE UNIQUE INDEX `UQE_tag_key_value` ON `tag` (`key`,`value`);',1,'','2023-10-28 04:02:36'),
	(276,'create login attempt table','CREATE TABLE IF NOT EXISTS `login_attempt` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `username` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `ip_address` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(277,'add index login_attempt.username','CREATE INDEX `IDX_login_attempt_username` ON `login_attempt` (`username`);',1,'','2023-10-28 04:02:36'),
	(278,'drop index IDX_login_attempt_username - v1','DROP INDEX `IDX_login_attempt_username` ON `login_attempt`',1,'','2023-10-28 04:02:36'),
	(279,'Rename table login_attempt to login_attempt_tmp_qwerty - v1','ALTER TABLE `login_attempt` RENAME TO `login_attempt_tmp_qwerty`',1,'','2023-10-28 04:02:36'),
	(280,'create login_attempt v2','CREATE TABLE IF NOT EXISTS `login_attempt` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `username` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `ip_address` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` INT NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(281,'create index IDX_login_attempt_username - v2','CREATE INDEX `IDX_login_attempt_username` ON `login_attempt` (`username`);',1,'','2023-10-28 04:02:36'),
	(282,'copy login_attempt v1 to v2','INSERT INTO `login_attempt` (`ip_address`\n, `id`\n, `username`) SELECT `ip_address`\n, `id`\n, `username` FROM `login_attempt_tmp_qwerty`',1,'','2023-10-28 04:02:36'),
	(283,'drop login_attempt_tmp_qwerty','DROP TABLE IF EXISTS `login_attempt_tmp_qwerty`',1,'','2023-10-28 04:02:36'),
	(284,'create user auth table','CREATE TABLE IF NOT EXISTS `user_auth` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `auth_module` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `auth_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(285,'create index IDX_user_auth_auth_module_auth_id - v1','CREATE INDEX `IDX_user_auth_auth_module_auth_id` ON `user_auth` (`auth_module`,`auth_id`);',1,'','2023-10-28 04:02:36'),
	(286,'alter user_auth.auth_id to length 190','ALTER TABLE user_auth MODIFY auth_id VARCHAR(190);',1,'','2023-10-28 04:02:36'),
	(287,'Add OAuth access token to user_auth','alter table `user_auth` ADD COLUMN `o_auth_access_token` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:36'),
	(288,'Add OAuth refresh token to user_auth','alter table `user_auth` ADD COLUMN `o_auth_refresh_token` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:36'),
	(289,'Add OAuth token type to user_auth','alter table `user_auth` ADD COLUMN `o_auth_token_type` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:36'),
	(290,'Add OAuth expiry to user_auth','alter table `user_auth` ADD COLUMN `o_auth_expiry` DATETIME NULL ',1,'','2023-10-28 04:02:36'),
	(291,'Add index to user_id column in user_auth','CREATE INDEX `IDX_user_auth_user_id` ON `user_auth` (`user_id`);',1,'','2023-10-28 04:02:36'),
	(292,'Add OAuth ID token to user_auth','alter table `user_auth` ADD COLUMN `o_auth_id_token` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:36'),
	(293,'create server_lock table','CREATE TABLE IF NOT EXISTS `server_lock` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `operation_uid` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `version` BIGINT(20) NOT NULL\n, `last_execution` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(294,'add index server_lock.operation_uid','CREATE UNIQUE INDEX `UQE_server_lock_operation_uid` ON `server_lock` (`operation_uid`);',1,'','2023-10-28 04:02:36'),
	(295,'create user auth token table','CREATE TABLE IF NOT EXISTS `user_auth_token` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `auth_token` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `prev_auth_token` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `user_agent` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `client_ip` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `auth_token_seen` TINYINT(1) NOT NULL\n, `seen_at` INT NULL\n, `rotated_at` INT NOT NULL\n, `created_at` INT NOT NULL\n, `updated_at` INT NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:36'),
	(296,'add unique index user_auth_token.auth_token','CREATE UNIQUE INDEX `UQE_user_auth_token_auth_token` ON `user_auth_token` (`auth_token`);',1,'','2023-10-28 04:02:36'),
	(297,'add unique index user_auth_token.prev_auth_token','CREATE UNIQUE INDEX `UQE_user_auth_token_prev_auth_token` ON `user_auth_token` (`prev_auth_token`);',1,'','2023-10-28 04:02:36'),
	(298,'add index user_auth_token.user_id','CREATE INDEX `IDX_user_auth_token_user_id` ON `user_auth_token` (`user_id`);',1,'','2023-10-28 04:02:36'),
	(299,'Add revoked_at to the user auth token','alter table `user_auth_token` ADD COLUMN `revoked_at` INT NULL ',1,'','2023-10-28 04:02:37'),
	(300,'add index user_auth_token.revoked_at','CREATE INDEX `IDX_user_auth_token_revoked_at` ON `user_auth_token` (`revoked_at`);',1,'','2023-10-28 04:02:37'),
	(301,'create cache_data table','CREATE TABLE IF NOT EXISTS `cache_data` (\n`cache_key` VARCHAR(168) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `data` BLOB NOT NULL\n, `expires` INTEGER(255) NOT NULL\n, `created_at` INTEGER(255) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:37'),
	(302,'add unique index cache_data.cache_key','CREATE UNIQUE INDEX `UQE_cache_data_cache_key` ON `cache_data` (`cache_key`);',1,'','2023-10-28 04:02:37'),
	(303,'create short_url table v1','CREATE TABLE IF NOT EXISTS `short_url` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `path` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_by` INT NOT NULL\n, `created_at` INT NOT NULL\n, `last_seen_at` INT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:37'),
	(304,'add index short_url.org_id-uid','CREATE UNIQUE INDEX `UQE_short_url_org_id_uid` ON `short_url` (`org_id`,`uid`);',1,'','2023-10-28 04:02:37'),
	(305,'alter table short_url alter column created_by type to bigint','ALTER TABLE short_url MODIFY created_by BIGINT;',1,'','2023-10-28 04:02:37'),
	(306,'delete alert_definition table','DROP TABLE IF EXISTS `alert_definition`',1,'','2023-10-28 04:02:37'),
	(307,'recreate alert_definition table','CREATE TABLE IF NOT EXISTS `alert_definition` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `title` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `condition` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated` DATETIME NOT NULL\n, `interval_seconds` BIGINT(20) NOT NULL DEFAULT 60\n, `version` INT NOT NULL DEFAULT 0\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:37'),
	(308,'add index in alert_definition on org_id and title columns','CREATE INDEX `IDX_alert_definition_org_id_title` ON `alert_definition` (`org_id`,`title`);',1,'','2023-10-28 04:02:37'),
	(309,'add index in alert_definition on org_id and uid columns','CREATE INDEX `IDX_alert_definition_org_id_uid` ON `alert_definition` (`org_id`,`uid`);',1,'','2023-10-28 04:02:37'),
	(310,'alter alert_definition table data column to mediumtext in mysql','ALTER TABLE alert_definition MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:37'),
	(311,'drop index in alert_definition on org_id and title columns','DROP INDEX `IDX_alert_definition_org_id_title` ON `alert_definition`',1,'','2023-10-28 04:02:37'),
	(312,'drop index in alert_definition on org_id and uid columns','DROP INDEX `IDX_alert_definition_org_id_uid` ON `alert_definition`',1,'','2023-10-28 04:02:37'),
	(313,'add unique index in alert_definition on org_id and title columns','CREATE UNIQUE INDEX `UQE_alert_definition_org_id_title` ON `alert_definition` (`org_id`,`title`);',1,'','2023-10-28 04:02:37'),
	(314,'add unique index in alert_definition on org_id and uid columns','CREATE UNIQUE INDEX `UQE_alert_definition_org_id_uid` ON `alert_definition` (`org_id`,`uid`);',1,'','2023-10-28 04:02:37'),
	(315,'Add column paused in alert_definition','alter table `alert_definition` ADD COLUMN `paused` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:37'),
	(316,'drop alert_definition table','DROP TABLE IF EXISTS `alert_definition`',1,'','2023-10-28 04:02:37'),
	(317,'delete alert_definition_version table','DROP TABLE IF EXISTS `alert_definition_version`',1,'','2023-10-28 04:02:37'),
	(318,'recreate alert_definition_version table','CREATE TABLE IF NOT EXISTS `alert_definition_version` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `alert_definition_id` BIGINT(20) NOT NULL\n, `alert_definition_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0\n, `parent_version` INT NOT NULL\n, `restored_from` INT NOT NULL\n, `version` INT NOT NULL\n, `created` DATETIME NOT NULL\n, `title` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `condition` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `interval_seconds` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:37'),
	(319,'add index in alert_definition_version table on alert_definition_id and version columns','CREATE UNIQUE INDEX `UQE_alert_definition_version_alert_definition_id_version` ON `alert_definition_version` (`alert_definition_id`,`version`);',1,'','2023-10-28 04:02:37'),
	(320,'add index in alert_definition_version table on alert_definition_uid and version columns','CREATE UNIQUE INDEX `UQE_alert_definition_version_alert_definition_uid_version` ON `alert_definition_version` (`alert_definition_uid`,`version`);',1,'','2023-10-28 04:02:37'),
	(321,'alter alert_definition_version table data column to mediumtext in mysql','ALTER TABLE alert_definition_version MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:37'),
	(322,'drop alert_definition_version table','DROP TABLE IF EXISTS `alert_definition_version`',1,'','2023-10-28 04:02:37'),
	(323,'create alert_instance table','CREATE TABLE IF NOT EXISTS `alert_instance` (\n`def_org_id` BIGINT(20) NOT NULL\n, `def_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0\n, `labels` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `labels_hash` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `current_state` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `current_state_since` BIGINT(20) NOT NULL\n, `last_eval_time` BIGINT(20) NOT NULL\n, PRIMARY KEY ( `def_org_id`,`def_uid`,`labels_hash` )) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:37'),
	(324,'add index in alert_instance table on def_org_id, def_uid and current_state columns','CREATE INDEX `IDX_alert_instance_def_org_id_def_uid_current_state` ON `alert_instance` (`def_org_id`,`def_uid`,`current_state`);',1,'','2023-10-28 04:02:37'),
	(325,'add index in alert_instance table on def_org_id, current_state columns','CREATE INDEX `IDX_alert_instance_def_org_id_current_state` ON `alert_instance` (`def_org_id`,`current_state`);',1,'','2023-10-28 04:02:37'),
	(326,'add column current_state_end to alert_instance','alter table `alert_instance` ADD COLUMN `current_state_end` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:37'),
	(327,'remove index def_org_id, def_uid, current_state on alert_instance','DROP INDEX `IDX_alert_instance_def_org_id_def_uid_current_state` ON `alert_instance`',1,'','2023-10-28 04:02:37'),
	(328,'remove index def_org_id, current_state on alert_instance','DROP INDEX `IDX_alert_instance_def_org_id_current_state` ON `alert_instance`',1,'','2023-10-28 04:02:37'),
	(329,'rename def_org_id to rule_org_id in alert_instance','ALTER TABLE alert_instance CHANGE def_org_id rule_org_id BIGINT;',1,'','2023-10-28 04:02:37'),
	(330,'rename def_uid to rule_uid in alert_instance','ALTER TABLE alert_instance CHANGE def_uid rule_uid VARCHAR(40);',1,'','2023-10-28 04:02:37'),
	(331,'add index rule_org_id, rule_uid, current_state on alert_instance','CREATE INDEX `IDX_alert_instance_rule_org_id_rule_uid_current_state` ON `alert_instance` (`rule_org_id`,`rule_uid`,`current_state`);',1,'','2023-10-28 04:02:37'),
	(332,'add index rule_org_id, current_state on alert_instance','CREATE INDEX `IDX_alert_instance_rule_org_id_current_state` ON `alert_instance` (`rule_org_id`,`current_state`);',1,'','2023-10-28 04:02:38'),
	(333,'add current_reason column related to current_state','alter table `alert_instance` ADD COLUMN `current_reason` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(334,'create alert_rule table','CREATE TABLE IF NOT EXISTS `alert_rule` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `title` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `condition` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated` DATETIME NOT NULL\n, `interval_seconds` BIGINT(20) NOT NULL DEFAULT 60\n, `version` INT NOT NULL DEFAULT 0\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0\n, `namespace_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `rule_group` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `no_data_state` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'NoData\'\n, `exec_err_state` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'Alerting\'\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:38'),
	(335,'add index in alert_rule on org_id and title columns','CREATE UNIQUE INDEX `UQE_alert_rule_org_id_title` ON `alert_rule` (`org_id`,`title`);',1,'','2023-10-28 04:02:38'),
	(336,'add index in alert_rule on org_id and uid columns','CREATE UNIQUE INDEX `UQE_alert_rule_org_id_uid` ON `alert_rule` (`org_id`,`uid`);',1,'','2023-10-28 04:02:38'),
	(337,'add index in alert_rule on org_id, namespace_uid, group_uid columns','CREATE INDEX `IDX_alert_rule_org_id_namespace_uid_rule_group` ON `alert_rule` (`org_id`,`namespace_uid`,`rule_group`);',1,'','2023-10-28 04:02:38'),
	(338,'alter alert_rule table data column to mediumtext in mysql','ALTER TABLE alert_rule MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:38'),
	(339,'add column for to alert_rule','alter table `alert_rule` ADD COLUMN `for` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:38'),
	(340,'add column annotations to alert_rule','alter table `alert_rule` ADD COLUMN `annotations` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(341,'add column labels to alert_rule','alter table `alert_rule` ADD COLUMN `labels` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(342,'remove unique index from alert_rule on org_id, title columns','DROP INDEX `UQE_alert_rule_org_id_title` ON `alert_rule`',1,'','2023-10-28 04:02:38'),
	(343,'add index in alert_rule on org_id, namespase_uid and title columns','CREATE UNIQUE INDEX `UQE_alert_rule_org_id_namespace_uid_title` ON `alert_rule` (`org_id`,`namespace_uid`,`title`);',1,'','2023-10-28 04:02:38'),
	(344,'add dashboard_uid column to alert_rule','alter table `alert_rule` ADD COLUMN `dashboard_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(345,'add panel_id column to alert_rule','alter table `alert_rule` ADD COLUMN `panel_id` BIGINT(20) NULL ',1,'','2023-10-28 04:02:38'),
	(346,'add index in alert_rule on org_id, dashboard_uid and panel_id columns','CREATE INDEX `IDX_alert_rule_org_id_dashboard_uid_panel_id` ON `alert_rule` (`org_id`,`dashboard_uid`,`panel_id`);',1,'','2023-10-28 04:02:38'),
	(347,'add rule_group_idx column to alert_rule','alter table `alert_rule` ADD COLUMN `rule_group_idx` INT NOT NULL DEFAULT 1 ',1,'','2023-10-28 04:02:38'),
	(348,'add is_paused column to alert_rule table','alter table `alert_rule` ADD COLUMN `is_paused` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:38'),
	(349,'fix is_paused column for alert_rule table','SELECT 0;',1,'','2023-10-28 04:02:38'),
	(350,'create alert_rule_version table','CREATE TABLE IF NOT EXISTS `alert_rule_version` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `rule_org_id` BIGINT(20) NOT NULL\n, `rule_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0\n, `rule_namespace_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `rule_group` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `parent_version` INT NOT NULL\n, `restored_from` INT NOT NULL\n, `version` INT NOT NULL\n, `created` DATETIME NOT NULL\n, `title` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `condition` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `data` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `interval_seconds` BIGINT(20) NOT NULL\n, `no_data_state` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'NoData\'\n, `exec_err_state` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'Alerting\'\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:38'),
	(351,'add index in alert_rule_version table on rule_org_id, rule_uid and version columns','CREATE UNIQUE INDEX `UQE_alert_rule_version_rule_org_id_rule_uid_version` ON `alert_rule_version` (`rule_org_id`,`rule_uid`,`version`);',1,'','2023-10-28 04:02:38'),
	(352,'add index in alert_rule_version table on rule_org_id, rule_namespace_uid and rule_group columns','CREATE INDEX `IDX_alert_rule_version_rule_org_id_rule_namespace_uid_rule_group` ON `alert_rule_version` (`rule_org_id`,`rule_namespace_uid`,`rule_group`);',1,'','2023-10-28 04:02:38'),
	(353,'alter alert_rule_version table data column to mediumtext in mysql','ALTER TABLE alert_rule_version MODIFY data MEDIUMTEXT;',1,'','2023-10-28 04:02:38'),
	(354,'add column for to alert_rule_version','alter table `alert_rule_version` ADD COLUMN `for` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:38'),
	(355,'add column annotations to alert_rule_version','alter table `alert_rule_version` ADD COLUMN `annotations` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(356,'add column labels to alert_rule_version','alter table `alert_rule_version` ADD COLUMN `labels` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:38'),
	(357,'add rule_group_idx column to alert_rule_version','alter table `alert_rule_version` ADD COLUMN `rule_group_idx` INT NOT NULL DEFAULT 1 ',1,'','2023-10-28 04:02:38'),
	(358,'add is_paused column to alert_rule_versions table','alter table `alert_rule_version` ADD COLUMN `is_paused` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:38'),
	(359,'fix is_paused column for alert_rule_version table','SELECT 0;',1,'','2023-10-28 04:02:39'),
	(360,'create_alert_configuration_table','CREATE TABLE IF NOT EXISTS `alert_configuration` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `alertmanager_configuration` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `configuration_version` VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_at` INT NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(361,'Add column default in alert_configuration','alter table `alert_configuration` ADD COLUMN `default` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:39'),
	(362,'alert alert_configuration alertmanager_configuration column from TEXT to MEDIUMTEXT if mysql','ALTER TABLE alert_configuration MODIFY alertmanager_configuration MEDIUMTEXT;',1,'','2023-10-28 04:02:39'),
	(363,'add column org_id in alert_configuration','alter table `alert_configuration` ADD COLUMN `org_id` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:39'),
	(364,'add index in alert_configuration table on org_id column','CREATE INDEX `IDX_alert_configuration_org_id` ON `alert_configuration` (`org_id`);',1,'','2023-10-28 04:02:39'),
	(365,'add configuration_hash column to alert_configuration','alter table `alert_configuration` ADD COLUMN `configuration_hash` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'not-yet-calculated\' ',1,'','2023-10-28 04:02:39'),
	(366,'create_ngalert_configuration_table','CREATE TABLE IF NOT EXISTS `ngalert_configuration` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `alertmanagers` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created_at` INT NOT NULL\n, `updated_at` INT NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(367,'add index in ngalert_configuration on org_id column','CREATE UNIQUE INDEX `UQE_ngalert_configuration_org_id` ON `ngalert_configuration` (`org_id`);',1,'','2023-10-28 04:02:39'),
	(368,'add column send_alerts_to in ngalert_configuration','alter table `ngalert_configuration` ADD COLUMN `send_alerts_to` SMALLINT NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:39'),
	(369,'create provenance_type table','CREATE TABLE IF NOT EXISTS `provenance_type` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `record_key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `record_type` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `provenance` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(370,'add index to uniquify (record_key, record_type, org_id) columns','CREATE UNIQUE INDEX `UQE_provenance_type_record_type_record_key_org_id` ON `provenance_type` (`record_type`,`record_key`,`org_id`);',1,'','2023-10-28 04:02:39'),
	(371,'create alert_image table','CREATE TABLE IF NOT EXISTS `alert_image` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `token` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `path` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `url` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_at` DATETIME NOT NULL\n, `expires_at` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(372,'add unique index on token to alert_image table','CREATE UNIQUE INDEX `UQE_alert_image_token` ON `alert_image` (`token`);',1,'','2023-10-28 04:02:39'),
	(373,'support longer URLs in alert_image table','ALTER TABLE alert_image MODIFY url VARCHAR(2048) NOT NULL;',1,'','2023-10-28 04:02:39'),
	(374,'create_alert_configuration_history_table','CREATE TABLE IF NOT EXISTS `alert_configuration_history` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL DEFAULT 0\n, `alertmanager_configuration` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `configuration_hash` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'not-yet-calculated\'\n, `configuration_version` VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_at` INT NOT NULL\n, `default` TINYINT(1) NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(375,'drop non-unique orgID index on alert_configuration','DROP INDEX `IDX_alert_configuration_org_id` ON `alert_configuration`',1,'','2023-10-28 04:02:39'),
	(376,'drop unique orgID index on alert_configuration if exists','DROP INDEX `UQE_alert_configuration_org_id` ON `alert_configuration`',1,'','2023-10-28 04:02:39'),
	(377,'extract alertmanager configuration history to separate table','code migration',1,'','2023-10-28 04:02:39'),
	(378,'add unique index on orgID to alert_configuration','CREATE UNIQUE INDEX `UQE_alert_configuration_org_id` ON `alert_configuration` (`org_id`);',1,'','2023-10-28 04:02:39'),
	(379,'add last_applied column to alert_configuration_history','alter table `alert_configuration_history` ADD COLUMN `last_applied` INT NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:39'),
	(380,'create library_element table v1','CREATE TABLE IF NOT EXISTS `library_element` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `folder_id` BIGINT(20) NOT NULL\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `name` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `kind` BIGINT(20) NOT NULL\n, `type` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `model` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `created_by` BIGINT(20) NOT NULL\n, `updated` DATETIME NOT NULL\n, `updated_by` BIGINT(20) NOT NULL\n, `version` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(381,'add index library_element org_id-folder_id-name-kind','CREATE UNIQUE INDEX `UQE_library_element_org_id_folder_id_name_kind` ON `library_element` (`org_id`,`folder_id`,`name`,`kind`);',1,'','2023-10-28 04:02:39'),
	(382,'create library_element_connection table v1','CREATE TABLE IF NOT EXISTS `library_element_connection` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `element_id` BIGINT(20) NOT NULL\n, `kind` BIGINT(20) NOT NULL\n, `connection_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `created_by` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(383,'add index library_element_connection element_id-kind-connection_id','CREATE UNIQUE INDEX `UQE_library_element_connection_element_id_kind_connection_id` ON `library_element_connection` (`element_id`,`kind`,`connection_id`);',1,'','2023-10-28 04:02:39'),
	(384,'add unique index library_element org_id_uid','CREATE UNIQUE INDEX `UQE_library_element_org_id_uid` ON `library_element` (`org_id`,`uid`);',1,'','2023-10-28 04:02:39'),
	(385,'increase max description length to 2048','ALTER TABLE `library_element` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `description` VARCHAR(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:39'),
	(386,'alter library_element model to mediumtext','ALTER TABLE library_element MODIFY model MEDIUMTEXT NOT NULL;',1,'','2023-10-28 04:02:39'),
	(387,'clone move dashboard alerts to unified alerting','code migration',1,'','2023-10-28 04:02:39'),
	(388,'create data_keys table','CREATE TABLE IF NOT EXISTS `data_keys` (\n`name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `active` TINYINT(1) NOT NULL\n, `scope` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `provider` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `encrypted_data` BLOB NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(389,'create secrets table','CREATE TABLE IF NOT EXISTS `secrets` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `namespace` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `value` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:39'),
	(390,'rename data_keys name column to id','ALTER TABLE `data_keys` CHANGE `name` `id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',1,'','2023-10-28 04:02:39'),
	(391,'add name column into data_keys','alter table `data_keys` ADD COLUMN `name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'\' ',1,'','2023-10-28 04:02:40'),
	(392,'copy data_keys id column values into name','UPDATE data_keys SET name = id',1,'','2023-10-28 04:02:40'),
	(393,'rename data_keys name column to label','ALTER TABLE `data_keys` CHANGE `name` `label` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',1,'','2023-10-28 04:02:40'),
	(394,'rename data_keys id column back to name','ALTER TABLE `data_keys` CHANGE `id` `name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',1,'','2023-10-28 04:02:40'),
	(395,'create kv_store table v1','CREATE TABLE IF NOT EXISTS `kv_store` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `namespace` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `value` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(396,'add index kv_store.org_id-namespace-key','CREATE UNIQUE INDEX `UQE_kv_store_org_id_namespace_key` ON `kv_store` (`org_id`,`namespace`,`key`);',1,'','2023-10-28 04:02:40'),
	(397,'update dashboard_uid and panel_id from existing annotations','set dashboard_uid and panel_id migration',1,'','2023-10-28 04:02:40'),
	(398,'create permission table','CREATE TABLE IF NOT EXISTS `permission` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `role_id` BIGINT(20) NOT NULL\n, `action` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `scope` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(399,'add unique index permission.role_id','CREATE INDEX `IDX_permission_role_id` ON `permission` (`role_id`);',1,'','2023-10-28 04:02:40'),
	(400,'add unique index role_id_action_scope','CREATE UNIQUE INDEX `UQE_permission_role_id_action_scope` ON `permission` (`role_id`,`action`,`scope`);',1,'','2023-10-28 04:02:40'),
	(401,'create role table','CREATE TABLE IF NOT EXISTS `role` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `version` BIGINT(20) NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(402,'add column display_name','alter table `role` ADD COLUMN `display_name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:40'),
	(403,'add column group_name','alter table `role` ADD COLUMN `group_name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:40'),
	(404,'add index role.org_id','CREATE INDEX `IDX_role_org_id` ON `role` (`org_id`);',1,'','2023-10-28 04:02:40'),
	(405,'add unique index role_org_id_name','CREATE UNIQUE INDEX `UQE_role_org_id_name` ON `role` (`org_id`,`name`);',1,'','2023-10-28 04:02:40'),
	(406,'add index role_org_id_uid','CREATE UNIQUE INDEX `UQE_role_org_id_uid` ON `role` (`org_id`,`uid`);',1,'','2023-10-28 04:02:40'),
	(407,'create team role table','CREATE TABLE IF NOT EXISTS `team_role` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `team_id` BIGINT(20) NOT NULL\n, `role_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(408,'add index team_role.org_id','CREATE INDEX `IDX_team_role_org_id` ON `team_role` (`org_id`);',1,'','2023-10-28 04:02:40'),
	(409,'add unique index team_role_org_id_team_id_role_id','CREATE UNIQUE INDEX `UQE_team_role_org_id_team_id_role_id` ON `team_role` (`org_id`,`team_id`,`role_id`);',1,'','2023-10-28 04:02:40'),
	(410,'add index team_role.team_id','CREATE INDEX `IDX_team_role_team_id` ON `team_role` (`team_id`);',1,'','2023-10-28 04:02:40'),
	(411,'create user role table','CREATE TABLE IF NOT EXISTS `user_role` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `user_id` BIGINT(20) NOT NULL\n, `role_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(412,'add index user_role.org_id','CREATE INDEX `IDX_user_role_org_id` ON `user_role` (`org_id`);',1,'','2023-10-28 04:02:40'),
	(413,'add unique index user_role_org_id_user_id_role_id','CREATE UNIQUE INDEX `UQE_user_role_org_id_user_id_role_id` ON `user_role` (`org_id`,`user_id`,`role_id`);',1,'','2023-10-28 04:02:40'),
	(414,'add index user_role.user_id','CREATE INDEX `IDX_user_role_user_id` ON `user_role` (`user_id`);',1,'','2023-10-28 04:02:40'),
	(415,'create builtin role table','CREATE TABLE IF NOT EXISTS `builtin_role` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `role` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `role_id` BIGINT(20) NOT NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(416,'add index builtin_role.role_id','CREATE INDEX `IDX_builtin_role_role_id` ON `builtin_role` (`role_id`);',1,'','2023-10-28 04:02:40'),
	(417,'add index builtin_role.name','CREATE INDEX `IDX_builtin_role_role` ON `builtin_role` (`role`);',1,'','2023-10-28 04:02:40'),
	(418,'Add column org_id to builtin_role table','alter table `builtin_role` ADD COLUMN `org_id` BIGINT(20) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:40'),
	(419,'add index builtin_role.org_id','CREATE INDEX `IDX_builtin_role_org_id` ON `builtin_role` (`org_id`);',1,'','2023-10-28 04:02:40'),
	(420,'add unique index builtin_role_org_id_role_id_role','CREATE UNIQUE INDEX `UQE_builtin_role_org_id_role_id_role` ON `builtin_role` (`org_id`,`role_id`,`role`);',1,'','2023-10-28 04:02:40'),
	(421,'Remove unique index role_org_id_uid','DROP INDEX `UQE_role_org_id_uid` ON `role`',1,'','2023-10-28 04:02:40'),
	(422,'add unique index role.uid','CREATE UNIQUE INDEX `UQE_role_uid` ON `role` (`uid`);',1,'','2023-10-28 04:02:40'),
	(423,'create seed assignment table','CREATE TABLE IF NOT EXISTS `seed_assignment` (\n`builtin_role` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `role_name` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:40'),
	(424,'add unique index builtin_role_role_name','CREATE UNIQUE INDEX `UQE_seed_assignment_builtin_role_role_name` ON `seed_assignment` (`builtin_role`,`role_name`);',1,'','2023-10-28 04:02:40'),
	(425,'add column hidden to role table','alter table `role` ADD COLUMN `hidden` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:41'),
	(426,'permission kind migration','alter table `permission` ADD COLUMN `kind` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'\' ',1,'','2023-10-28 04:02:41'),
	(427,'permission attribute migration','alter table `permission` ADD COLUMN `attribute` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'\' ',1,'','2023-10-28 04:02:41'),
	(428,'permission identifier migration','alter table `permission` ADD COLUMN `identifier` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'\' ',1,'','2023-10-28 04:02:41'),
	(429,'add permission identifier index','CREATE INDEX `IDX_permission_identifier` ON `permission` (`identifier`);',1,'','2023-10-28 04:02:41'),
	(430,'create query_history table v1','CREATE TABLE IF NOT EXISTS `query_history` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `datasource_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_by` INT NOT NULL\n, `created_at` INT NOT NULL\n, `comment` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `queries` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(431,'add index query_history.org_id-created_by-datasource_uid','CREATE INDEX `IDX_query_history_org_id_created_by_datasource_uid` ON `query_history` (`org_id`,`created_by`,`datasource_uid`);',1,'','2023-10-28 04:02:41'),
	(432,'alter table query_history alter column created_by type to bigint','ALTER TABLE query_history MODIFY created_by BIGINT;',1,'','2023-10-28 04:02:41'),
	(433,'rbac disabled migrator','code migration',1,'','2023-10-28 04:02:41'),
	(434,'teams permissions migration','code migration',1,'','2023-10-28 04:02:41'),
	(435,'dashboard permissions','code migration',1,'','2023-10-28 04:02:41'),
	(436,'dashboard permissions uid scopes','code migration',1,'','2023-10-28 04:02:41'),
	(437,'drop managed folder create actions','code migration',1,'','2023-10-28 04:02:41'),
	(438,'alerting notification permissions','code migration',1,'','2023-10-28 04:02:41'),
	(439,'create query_history_star table v1','CREATE TABLE IF NOT EXISTS `query_history_star` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `query_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `user_id` INT NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(440,'add index query_history.user_id-query_uid','CREATE UNIQUE INDEX `UQE_query_history_star_user_id_query_uid` ON `query_history_star` (`user_id`,`query_uid`);',1,'','2023-10-28 04:02:41'),
	(441,'add column org_id in query_history_star','alter table `query_history_star` ADD COLUMN `org_id` BIGINT(20) NOT NULL DEFAULT 1 ',1,'','2023-10-28 04:02:41'),
	(442,'alter table query_history_star_mig column user_id type to bigint','ALTER TABLE query_history_star MODIFY user_id BIGINT;',1,'','2023-10-28 04:02:41'),
	(443,'create correlation table v1','CREATE TABLE IF NOT EXISTS `correlation` (\n`uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `source_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `target_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `label` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, PRIMARY KEY ( `uid`,`source_uid` )) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(444,'add index correlations.uid','CREATE INDEX `IDX_correlation_uid` ON `correlation` (`uid`);',1,'','2023-10-28 04:02:41'),
	(445,'add index correlations.source_uid','CREATE INDEX `IDX_correlation_source_uid` ON `correlation` (`source_uid`);',1,'','2023-10-28 04:02:41'),
	(446,'add correlation config column','alter table `correlation` ADD COLUMN `config` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:41'),
	(447,'drop index IDX_correlation_uid - v1','DROP INDEX `IDX_correlation_uid` ON `correlation`',1,'','2023-10-28 04:02:41'),
	(448,'drop index IDX_correlation_source_uid - v1','DROP INDEX `IDX_correlation_source_uid` ON `correlation`',1,'','2023-10-28 04:02:41'),
	(449,'Rename table correlation to correlation_tmp_qwerty - v1','ALTER TABLE `correlation` RENAME TO `correlation_tmp_qwerty`',1,'','2023-10-28 04:02:41'),
	(450,'create correlation v2','CREATE TABLE IF NOT EXISTS `correlation` (\n`uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL DEFAULT 0\n, `source_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `target_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `label` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `config` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, PRIMARY KEY ( `uid`,`org_id`,`source_uid` )) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(451,'create index IDX_correlation_uid - v2','CREATE INDEX `IDX_correlation_uid` ON `correlation` (`uid`);',1,'','2023-10-28 04:02:41'),
	(452,'create index IDX_correlation_source_uid - v2','CREATE INDEX `IDX_correlation_source_uid` ON `correlation` (`source_uid`);',1,'','2023-10-28 04:02:41'),
	(453,'create index IDX_correlation_org_id - v2','CREATE INDEX `IDX_correlation_org_id` ON `correlation` (`org_id`);',1,'','2023-10-28 04:02:41'),
	(454,'copy correlation v1 to v2','INSERT INTO `correlation` (`description`\n, `config`\n, `uid`\n, `source_uid`\n, `target_uid`\n, `label`) SELECT `description`\n, `config`\n, `uid`\n, `source_uid`\n, `target_uid`\n, `label` FROM `correlation_tmp_qwerty`',1,'','2023-10-28 04:02:41'),
	(455,'drop correlation_tmp_qwerty','DROP TABLE IF EXISTS `correlation_tmp_qwerty`',1,'','2023-10-28 04:02:41'),
	(456,'add provisioning column','alter table `correlation` ADD COLUMN `provisioned` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:41'),
	(457,'create entity_events table','CREATE TABLE IF NOT EXISTS `entity_event` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `entity_id` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `event_type` VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created` BIGINT(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(458,'create dashboard public config v1','CREATE TABLE IF NOT EXISTS `dashboard_public_config` (\n`uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `dashboard_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `time_settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `refresh_rate` INT NOT NULL DEFAULT 30\n, `template_variables` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(459,'drop index UQE_dashboard_public_config_uid - v1','DROP INDEX `UQE_dashboard_public_config_uid` ON `dashboard_public_config`',1,'','2023-10-28 04:02:41'),
	(460,'drop index IDX_dashboard_public_config_org_id_dashboard_uid - v1','DROP INDEX `IDX_dashboard_public_config_org_id_dashboard_uid` ON `dashboard_public_config`',1,'','2023-10-28 04:02:41'),
	(461,'Drop old dashboard public config table','DROP TABLE IF EXISTS `dashboard_public_config`',1,'','2023-10-28 04:02:41'),
	(462,'recreate dashboard public config v1','CREATE TABLE IF NOT EXISTS `dashboard_public_config` (\n`uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `dashboard_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `time_settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `refresh_rate` INT NOT NULL DEFAULT 30\n, `template_variables` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:41'),
	(463,'create index UQE_dashboard_public_config_uid - v1','CREATE UNIQUE INDEX `UQE_dashboard_public_config_uid` ON `dashboard_public_config` (`uid`);',1,'','2023-10-28 04:02:41'),
	(464,'create index IDX_dashboard_public_config_org_id_dashboard_uid - v1','CREATE INDEX `IDX_dashboard_public_config_org_id_dashboard_uid` ON `dashboard_public_config` (`org_id`,`dashboard_uid`);',1,'','2023-10-28 04:02:41'),
	(465,'drop index UQE_dashboard_public_config_uid - v2','DROP INDEX `UQE_dashboard_public_config_uid` ON `dashboard_public_config`',1,'','2023-10-28 04:02:42'),
	(466,'drop index IDX_dashboard_public_config_org_id_dashboard_uid - v2','DROP INDEX `IDX_dashboard_public_config_org_id_dashboard_uid` ON `dashboard_public_config`',1,'','2023-10-28 04:02:42'),
	(467,'Drop public config table','DROP TABLE IF EXISTS `dashboard_public_config`',1,'','2023-10-28 04:02:42'),
	(468,'Recreate dashboard public config v2','CREATE TABLE IF NOT EXISTS `dashboard_public_config` (\n`uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY NOT NULL\n, `dashboard_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `time_settings` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `template_variables` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `access_token` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_by` INT NOT NULL\n, `updated_by` INT NULL\n, `created_at` DATETIME NOT NULL\n, `updated_at` DATETIME NULL\n, `is_enabled` TINYINT(1) NOT NULL DEFAULT 0\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:42'),
	(469,'create index UQE_dashboard_public_config_uid - v2','CREATE UNIQUE INDEX `UQE_dashboard_public_config_uid` ON `dashboard_public_config` (`uid`);',1,'','2023-10-28 04:02:42'),
	(470,'create index IDX_dashboard_public_config_org_id_dashboard_uid - v2','CREATE INDEX `IDX_dashboard_public_config_org_id_dashboard_uid` ON `dashboard_public_config` (`org_id`,`dashboard_uid`);',1,'','2023-10-28 04:02:42'),
	(471,'create index UQE_dashboard_public_config_access_token - v2','CREATE UNIQUE INDEX `UQE_dashboard_public_config_access_token` ON `dashboard_public_config` (`access_token`);',1,'','2023-10-28 04:02:42'),
	(472,'Rename table dashboard_public_config to dashboard_public - v2','ALTER TABLE `dashboard_public_config` RENAME TO `dashboard_public`',1,'','2023-10-28 04:02:42'),
	(473,'add annotations_enabled column','alter table `dashboard_public` ADD COLUMN `annotations_enabled` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:42'),
	(474,'add time_selection_enabled column','alter table `dashboard_public` ADD COLUMN `time_selection_enabled` TINYINT(1) NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:42'),
	(475,'delete orphaned public dashboards','DELETE FROM dashboard_public WHERE dashboard_uid NOT IN (SELECT uid FROM dashboard)',1,'','2023-10-28 04:02:42'),
	(476,'add share column','alter table `dashboard_public` ADD COLUMN `share` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT \'public\' ',1,'','2023-10-28 04:02:42'),
	(477,'backfill empty share column fields with default of public','UPDATE dashboard_public SET share=\'public\' WHERE share=\'\'',1,'','2023-10-28 04:02:42'),
	(478,'create file table','CREATE TABLE IF NOT EXISTS `file` (\n`path` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `path_hash` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `parent_folder_path_hash` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `contents` BLOB NOT NULL\n, `etag` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `cache_control` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `content_disposition` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated` DATETIME NOT NULL\n, `created` DATETIME NOT NULL\n, `size` BIGINT(20) NOT NULL\n, `mime_type` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:42'),
	(479,'file table idx: path natural pk','CREATE UNIQUE INDEX `UQE_file_path_hash` ON `file` (`path_hash`);',1,'','2023-10-28 04:02:42'),
	(480,'file table idx: parent_folder_path_hash fast folder retrieval','CREATE INDEX `IDX_file_parent_folder_path_hash` ON `file` (`parent_folder_path_hash`);',1,'','2023-10-28 04:02:42'),
	(481,'create file_meta table','CREATE TABLE IF NOT EXISTS `file_meta` (\n`path_hash` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `key` VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `value` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:42'),
	(482,'file table idx: path key','CREATE UNIQUE INDEX `UQE_file_meta_path_hash_key` ON `file_meta` (`path_hash`,`key`);',1,'','2023-10-28 04:02:42'),
	(483,'set path collation in file table','SELECT 0;',1,'','2023-10-28 04:02:42'),
	(484,'migrate contents column to mediumblob for MySQL','ALTER TABLE file MODIFY contents MEDIUMBLOB;',1,'','2023-10-28 04:02:42'),
	(485,'managed permissions migration','code migration',1,'','2023-10-28 04:02:42'),
	(486,'managed folder permissions alert actions migration','code migration',1,'','2023-10-28 04:02:42'),
	(487,'RBAC action name migrator','code migration',1,'','2023-10-28 04:02:42'),
	(488,'Add UID column to playlist','alter table `playlist` ADD COLUMN `uid` VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 0 ',1,'','2023-10-28 04:02:42'),
	(489,'Update uid column values in playlist','UPDATE playlist SET uid=id;',1,'','2023-10-28 04:02:42'),
	(490,'Add index for uid in playlist','CREATE UNIQUE INDEX `UQE_playlist_org_id_uid` ON `playlist` (`org_id`,`uid`);',1,'','2023-10-28 04:02:42'),
	(491,'update group index for alert rules','code migration',1,'','2023-10-28 04:02:42'),
	(492,'managed folder permissions alert actions repeated migration','code migration',1,'','2023-10-28 04:02:42'),
	(493,'admin only folder/dashboard permission','code migration',1,'','2023-10-28 04:02:42'),
	(494,'add action column to seed_assignment','alter table `seed_assignment` ADD COLUMN `action` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:42'),
	(495,'add scope column to seed_assignment','alter table `seed_assignment` ADD COLUMN `scope` VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL ',1,'','2023-10-28 04:02:42'),
	(496,'remove unique index builtin_role_role_name before nullable update','DROP INDEX `UQE_seed_assignment_builtin_role_role_name` ON `seed_assignment`',1,'','2023-10-28 04:02:42'),
	(497,'update seed_assignment role_name column to nullable','ALTER TABLE seed_assignment MODIFY role_name VARCHAR(190) DEFAULT NULL;',1,'','2023-10-28 04:02:42'),
	(498,'add unique index builtin_role_name back','CREATE UNIQUE INDEX `UQE_seed_assignment_builtin_role_role_name` ON `seed_assignment` (`builtin_role`,`role_name`);',1,'','2023-10-28 04:02:42'),
	(499,'add unique index builtin_role_action_scope','CREATE UNIQUE INDEX `UQE_seed_assignment_builtin_role_action_scope` ON `seed_assignment` (`builtin_role`,`action`,`scope`);',1,'','2023-10-28 04:02:42'),
	(500,'add primary key to seed_assigment','code migration',1,'','2023-10-28 04:02:43'),
	(501,'managed folder permissions alert actions repeated fixed migration','code migration',1,'','2023-10-28 04:02:43'),
	(502,'managed folder permissions library panel actions migration','code migration',1,'','2023-10-28 04:02:43'),
	(503,'migrate external alertmanagers to datsourcse','migrate external alertmanagers to datasource',1,'','2023-10-28 04:02:43'),
	(504,'create folder table','CREATE TABLE IF NOT EXISTS `folder` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `org_id` BIGINT(20) NOT NULL\n, `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `parent_uid` VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL\n, `created` DATETIME NOT NULL\n, `updated` DATETIME NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:43'),
	(505,'Add index for parent_uid','CREATE INDEX `IDX_folder_parent_uid_org_id` ON `folder` (`parent_uid`,`org_id`);',1,'','2023-10-28 04:02:43'),
	(506,'Add unique index for folder.uid and folder.org_id','CREATE UNIQUE INDEX `UQE_folder_uid_org_id` ON `folder` (`uid`,`org_id`);',1,'','2023-10-28 04:02:43'),
	(507,'Update folder title length','ALTER TABLE `folder` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY `title` VARCHAR(189) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL ;',1,'','2023-10-28 04:02:43'),
	(508,'Add unique index for folder.title and folder.parent_uid','CREATE UNIQUE INDEX `UQE_folder_title_parent_uid` ON `folder` (`title`,`parent_uid`);',1,'','2023-10-28 04:02:43'),
	(509,'Remove unique index for folder.title and folder.parent_uid','DROP INDEX `UQE_folder_title_parent_uid` ON `folder`',1,'','2023-10-28 04:02:43'),
	(510,'Add unique index for title, parent_uid, and org_id','CREATE UNIQUE INDEX `UQE_folder_title_parent_uid_org_id` ON `folder` (`title`,`parent_uid`,`org_id`);',1,'','2023-10-28 04:02:43'),
	(511,'create anon_device table','CREATE TABLE IF NOT EXISTS `anon_device` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `client_ip` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `created_at` DATETIME NOT NULL\n, `device_id` VARCHAR(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `updated_at` DATETIME NOT NULL\n, `user_agent` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:43'),
	(512,'add unique index anon_device.device_id','CREATE UNIQUE INDEX `UQE_anon_device_device_id` ON `anon_device` (`device_id`);',1,'','2023-10-28 04:02:43'),
	(513,'add index anon_device.updated_at','CREATE INDEX `IDX_anon_device_updated_at` ON `anon_device` (`updated_at`);',1,'','2023-10-28 04:02:43'),
	(514,'create signing_key table','CREATE TABLE IF NOT EXISTS `signing_key` (\n`id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT NOT NULL\n, `key_id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `private_key` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n, `added_at` DATETIME NOT NULL\n, `expires_at` DATETIME NULL\n, `alg` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;',1,'','2023-10-28 04:02:43'),
	(515,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:43'),
	(516,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:44'),
	(517,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:45'),
	(518,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:47'),
	(519,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:48'),
	(520,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:51'),
	(521,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:02:55'),
	(522,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:03:02'),
	(523,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:03:16'),
	(524,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:03:42'),
	(525,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:24'),
	(526,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:25'),
	(527,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:27'),
	(528,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:28'),
	(529,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:30'),
	(530,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:32'),
	(531,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:36'),
	(532,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:44'),
	(533,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:04:57'),
	(534,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:05:23'),
	(535,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:23'),
	(536,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:24'),
	(537,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:25'),
	(538,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:26'),
	(539,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:28'),
	(540,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:30'),
	(541,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:34'),
	(542,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',0,'Error 1071 (42000): Specified key was too long; max key length is 767 bytes','2023-10-28 04:06:41'),
	(543,'add unique index signing_key.key_id','CREATE UNIQUE INDEX `UQE_signing_key_key_id` ON `signing_key` (`key_id`);',1,'','2023-10-28 04:06:55'),
	(544,'set legacy alert migration status in kvstore','code migration',1,'','2023-10-28 04:06:55'),
	(545,'migrate record of created folders during legacy migration to kvstore','code migration',1,'','2023-10-28 04:06:55');

/*!40000 ALTER TABLE `migration_log` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 ngalert_configuration
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ngalert_configuration`;

CREATE TABLE `ngalert_configuration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `alertmanagers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` int NOT NULL,
  `updated_at` int NOT NULL,
  `send_alerts_to` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_ngalert_configuration_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 org
# ------------------------------------------------------------

DROP TABLE IF EXISTS `org`;

CREATE TABLE `org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` int NOT NULL,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `address1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `zip_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `country` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_org_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `org` WRITE;
/*!40000 ALTER TABLE `org` DISABLE KEYS */;

INSERT INTO `org` (`id`, `version`, `name`, `address1`, `address2`, `city`, `state`, `zip_code`, `country`, `billing_email`, `created`, `updated`)
VALUES
	(1,0,'Main Org.','','','','','','',NULL,'2023-10-28 04:06:55','2023-10-28 04:06:55');

/*!40000 ALTER TABLE `org` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 org_user
# ------------------------------------------------------------

DROP TABLE IF EXISTS `org_user`;

CREATE TABLE `org_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_org_user_org_id_user_id` (`org_id`,`user_id`),
  KEY `IDX_org_user_org_id` (`org_id`),
  KEY `IDX_org_user_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `org_user` WRITE;
/*!40000 ALTER TABLE `org_user` DISABLE KEYS */;

INSERT INTO `org_user` (`id`, `org_id`, `user_id`, `role`, `created`, `updated`)
VALUES
	(1,1,1,'Admin','2023-10-28 04:06:55','2023-10-28 04:06:55'),
	(2,1,2,'Admin','2025-06-15 02:24:57','2025-06-15 02:24:57'),
	(3,1,3,'Admin','2025-06-15 05:04:47','2025-06-15 05:04:47');

/*!40000 ALTER TABLE `org_user` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 permission
# ------------------------------------------------------------

DROP TABLE IF EXISTS `permission`;

CREATE TABLE `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `action` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `kind` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `attribute` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `identifier` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_permission_role_id_action_scope` (`role_id`,`action`,`scope`),
  KEY `IDX_permission_role_id` (`role_id`),
  KEY `IDX_permission_identifier` (`identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `permission` WRITE;
/*!40000 ALTER TABLE `permission` DISABLE KEYS */;

INSERT INTO `permission` (`id`, `role_id`, `action`, `scope`, `created`, `updated`, `kind`, `attribute`, `identifier`)
VALUES
	(55,1,'serviceaccounts:read','serviceaccounts:id:2','2025-06-15 02:24:57','2025-06-15 02:24:57','','',''),
	(56,1,'serviceaccounts:write','serviceaccounts:id:2','2025-06-15 02:24:57','2025-06-15 02:24:57','','',''),
	(57,1,'serviceaccounts:delete','serviceaccounts:id:2','2025-06-15 02:24:57','2025-06-15 02:24:57','','',''),
	(58,1,'serviceaccounts.permissions:read','serviceaccounts:id:2','2025-06-15 02:24:57','2025-06-15 02:24:57','','',''),
	(59,1,'serviceaccounts.permissions:write','serviceaccounts:id:2','2025-06-15 02:24:57','2025-06-15 02:24:57','','',''),
	(69,1,'serviceaccounts:read','serviceaccounts:id:3','2025-06-15 05:04:47','2025-06-15 05:04:47','','',''),
	(70,1,'serviceaccounts:write','serviceaccounts:id:3','2025-06-15 05:04:47','2025-06-15 05:04:47','','',''),
	(71,1,'serviceaccounts:delete','serviceaccounts:id:3','2025-06-15 05:04:47','2025-06-15 05:04:47','','',''),
	(72,1,'serviceaccounts.permissions:read','serviceaccounts:id:3','2025-06-15 05:04:47','2025-06-15 05:04:47','','',''),
	(73,1,'serviceaccounts.permissions:write','serviceaccounts:id:3','2025-06-15 05:04:47','2025-06-15 05:04:47','','',''),
	(74,1,'dashboards:delete','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(75,1,'dashboards.permissions:read','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(76,1,'dashboards.permissions:write','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(77,1,'dashboards:read','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(78,1,'dashboards:write','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(79,2,'dashboards:read','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(80,2,'dashboards:write','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(81,2,'dashboards:delete','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','',''),
	(82,3,'dashboards:read','dashboards:uid:sbapmwalker','2025-08-16 00:53:34','2025-08-16 00:53:34','','','');

/*!40000 ALTER TABLE `permission` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 playlist
# ------------------------------------------------------------

DROP TABLE IF EXISTS `playlist`;

CREATE TABLE `playlist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `interval` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `created_at` bigint NOT NULL DEFAULT '0',
  `updated_at` bigint NOT NULL DEFAULT '0',
  `uid` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_playlist_org_id_uid` (`org_id`,`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 playlist_item
# ------------------------------------------------------------

DROP TABLE IF EXISTS `playlist_item`;

CREATE TABLE `playlist_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `playlist_id` bigint NOT NULL,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 plugin_setting
# ------------------------------------------------------------

DROP TABLE IF EXISTS `plugin_setting`;

CREATE TABLE `plugin_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint DEFAULT NULL,
  `plugin_id` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `pinned` tinyint(1) NOT NULL,
  `json_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `secure_json_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `plugin_version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_plugin_setting_org_id_plugin_id` (`org_id`,`plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 preferences
# ------------------------------------------------------------

DROP TABLE IF EXISTS `preferences`;

CREATE TABLE `preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `version` int NOT NULL,
  `home_dashboard_id` bigint NOT NULL,
  `timezone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `theme` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `team_id` bigint DEFAULT NULL,
  `week_start` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `json_data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `IDX_preferences_org_id` (`org_id`),
  KEY `IDX_preferences_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `preferences` WRITE;
/*!40000 ALTER TABLE `preferences` DISABLE KEYS */;

INSERT INTO `preferences` (`id`, `org_id`, `user_id`, `version`, `home_dashboard_id`, `timezone`, `theme`, `created`, `updated`, `team_id`, `week_start`, `json_data`)
VALUES
	(1,1,1,2,0,'','dark','2023-10-28 04:09:59','2023-10-28 09:33:15',0,'','{\"language\":\"zh-Hans\",\"queryHistory\":{\"homeTab\":\"\"},\"cookiePreferences\":null}');

/*!40000 ALTER TABLE `preferences` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 provenance_type
# ------------------------------------------------------------

DROP TABLE IF EXISTS `provenance_type`;

CREATE TABLE `provenance_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `record_key` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_type` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provenance` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_provenance_type_record_type_record_key_org_id` (`record_type`,`record_key`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 query_history
# ------------------------------------------------------------

DROP TABLE IF EXISTS `query_history`;

CREATE TABLE `query_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `datasource_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` int NOT NULL,
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `queries` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_query_history_org_id_created_by_datasource_uid` (`org_id`,`created_by`,`datasource_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 query_history_star
# ------------------------------------------------------------

DROP TABLE IF EXISTS `query_history_star`;

CREATE TABLE `query_history_star` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `query_uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `org_id` bigint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_query_history_star_user_id_query_uid` (`user_id`,`query_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 quota
# ------------------------------------------------------------

DROP TABLE IF EXISTS `quota`;

CREATE TABLE `quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `target` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `limit` bigint NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_quota_org_id_user_id_target` (`org_id`,`user_id`,`target`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 role
# ------------------------------------------------------------

DROP TABLE IF EXISTS `role`;

CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `version` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `display_name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `group_name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hidden` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_role_org_id_name` (`org_id`,`name`),
  UNIQUE KEY `UQE_role_uid` (`uid`),
  KEY `IDX_role_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;

INSERT INTO `role` (`id`, `name`, `description`, `version`, `org_id`, `uid`, `created`, `updated`, `display_name`, `group_name`, `hidden`)
VALUES
	(1,'managed:users:1:permissions','',0,1,'e63bf381-05ae-43e6-9441-e4788a3cfbe9','2023-10-28 04:10:39','2023-10-28 04:10:39','','',0),
	(2,'managed:builtins:editor:permissions','',0,1,'db7a16aa-4a4b-4fb5-94fc-61140c78103c','2023-10-28 04:10:39','2023-10-28 04:10:39','','',0),
	(3,'managed:builtins:viewer:permissions','',0,1,'b143a274-9484-45f8-af2c-d27c8d09c209','2023-10-28 04:10:39','2023-10-28 04:10:39','','',0);

/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 secrets
# ------------------------------------------------------------

DROP TABLE IF EXISTS `secrets`;

CREATE TABLE `secrets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `namespace` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `secrets` WRITE;
/*!40000 ALTER TABLE `secrets` DISABLE KEYS */;

INSERT INTO `secrets` (`id`, `org_id`, `namespace`, `type`, `value`, `created`, `updated`)
VALUES
	(1,1,'Prometheus','datasource','I1ltRTNNbUk1WWpJdFlqYzRaUzAwTVRnMUxXSmlOR1l0WXpZM01XVXhZVGcxTURVMiMqWVdWekxXTm1ZZypxd2dqaG1hZU0JiMNFY02lNL3wrvwgu2AHtg','2023-10-28 04:06:57','2025-08-16 01:00:02'),
	(2,1,'Elasticsearch','datasource','I1ltRTNNbUk1WWpJdFlqYzRaUzAwTVRnMUxXSmlOR1l0WXpZM01XVXhZVGcxTURVMiMqWVdWekxXTm1ZZypMcmRIdWxnSHwhmXjLI31ulxIbu5dSzqGSvA','2025-08-16 00:51:30','2025-08-16 00:52:02'),
	(3,1,'MySQL','datasource','I1ltRTNNbUk1WWpJdFlqYzRaUzAwTVRnMUxXSmlOR1l0WXpZM01XVXhZVGcxTURVMiMqWVdWekxXTm1ZZypDV0hLSmpOUBv8+vcQJCe3jd6K9dX6is7PZ/Ga','2025-08-16 00:52:30','2025-08-16 00:52:30'),
	(4,1,'Prometheus-1','datasource','I1ltRTNNbUk1WWpJdFlqYzRaUzAwTVRnMUxXSmlOR1l0WXpZM01XVXhZVGcxTURVMiMqWVdWekxXTm1ZZypYazFtNEZaaSN6KyHguQ0+KOWSu79MVClzfuEg','2025-08-16 01:01:08','2025-08-16 01:01:08');

/*!40000 ALTER TABLE `secrets` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 seed_assignment
# ------------------------------------------------------------

DROP TABLE IF EXISTS `seed_assignment`;

CREATE TABLE `seed_assignment` (
  `id` int NOT NULL AUTO_INCREMENT,
  `builtin_role` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scope` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_seed_assignment_builtin_role_role_name` (`builtin_role`,`role_name`),
  UNIQUE KEY `UQE_seed_assignment_builtin_role_action_scope` (`builtin_role`,`action`,`scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 server_lock
# ------------------------------------------------------------

DROP TABLE IF EXISTS `server_lock`;

CREATE TABLE `server_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_uid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` bigint NOT NULL,
  `last_execution` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_server_lock_operation_uid` (`operation_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `server_lock` WRITE;
/*!40000 ALTER TABLE `server_lock` DISABLE KEYS */;

INSERT INTO `server_lock` (`id`, `operation_uid`, `version`, `last_execution`)
VALUES
	(3,'cleanup expired auth tokens',6,1755303995),
	(6,'delete old login attempts',118,1755309002),
	(7,'cleanup old anon devices',4,1749970901);

/*!40000 ALTER TABLE `server_lock` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 session
# ------------------------------------------------------------

DROP TABLE IF EXISTS `session`;

CREATE TABLE `session` (
  `key` char(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data` blob NOT NULL,
  `expiry` int NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 short_url
# ------------------------------------------------------------

DROP TABLE IF EXISTS `short_url`;

CREATE TABLE `short_url` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` int NOT NULL,
  `last_seen_at` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_short_url_org_id_uid` (`org_id`,`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 signing_key
# ------------------------------------------------------------

DROP TABLE IF EXISTS `signing_key`;

CREATE TABLE `signing_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `private_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `added_at` datetime NOT NULL,
  `expires_at` datetime DEFAULT NULL,
  `alg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_signing_key_key_id` (`key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 star
# ------------------------------------------------------------

DROP TABLE IF EXISTS `star`;

CREATE TABLE `star` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_star_user_id_dashboard_id` (`user_id`,`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `star` WRITE;
/*!40000 ALTER TABLE `star` DISABLE KEYS */;

INSERT INTO `star` (`id`, `user_id`, `dashboard_id`)
VALUES
	(3,1,8);

/*!40000 ALTER TABLE `star` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 tag
# ------------------------------------------------------------

DROP TABLE IF EXISTS `tag`;

CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_tag_key_value` (`key`,`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 team
# ------------------------------------------------------------

DROP TABLE IF EXISTS `team`;

CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `org_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `uid` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_team_org_id_name` (`org_id`,`name`),
  UNIQUE KEY `UQE_team_org_id_uid` (`org_id`,`uid`),
  KEY `IDX_team_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 team_member
# ------------------------------------------------------------

DROP TABLE IF EXISTS `team_member`;

CREATE TABLE `team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `external` tinyint(1) DEFAULT NULL,
  `permission` smallint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_team_member_org_id_team_id_user_id` (`org_id`,`team_id`,`user_id`),
  KEY `IDX_team_member_org_id` (`org_id`),
  KEY `IDX_team_member_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 team_role
# ------------------------------------------------------------

DROP TABLE IF EXISTS `team_role`;

CREATE TABLE `team_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_team_role_org_id_team_id_role_id` (`org_id`,`team_id`,`role_id`),
  KEY `IDX_team_role_org_id` (`org_id`),
  KEY `IDX_team_role_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 temp_user
# ------------------------------------------------------------

DROP TABLE IF EXISTS `temp_user`;

CREATE TABLE `temp_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `version` int NOT NULL,
  `email` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `code` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `invited_by_user_id` bigint DEFAULT NULL,
  `email_sent` tinyint(1) NOT NULL,
  `email_sent_on` datetime DEFAULT NULL,
  `remote_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` int NOT NULL DEFAULT '0',
  `updated` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `IDX_temp_user_email` (`email`),
  KEY `IDX_temp_user_org_id` (`org_id`),
  KEY `IDX_temp_user_code` (`code`),
  KEY `IDX_temp_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 test_data
# ------------------------------------------------------------

DROP TABLE IF EXISTS `test_data`;

CREATE TABLE `test_data` (
  `id` int NOT NULL AUTO_INCREMENT,
  `metric1` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metric2` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `value_big_int` bigint DEFAULT NULL,
  `value_double` double DEFAULT NULL,
  `value_float` float DEFAULT NULL,
  `value_int` int DEFAULT NULL,
  `time_epoch` bigint NOT NULL,
  `time_date_time` datetime NOT NULL,
  `time_time_stamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 user
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` int NOT NULL,
  `login` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salt` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rands` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `org_id` bigint NOT NULL,
  `is_admin` tinyint(1) NOT NULL,
  `email_verified` tinyint(1) DEFAULT NULL,
  `theme` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` datetime NOT NULL,
  `updated` datetime NOT NULL,
  `help_flags1` bigint NOT NULL DEFAULT '0',
  `last_seen_at` datetime DEFAULT NULL,
  `is_disabled` tinyint(1) NOT NULL DEFAULT '0',
  `is_service_account` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_user_login` (`login`),
  UNIQUE KEY `UQE_user_email` (`email`),
  KEY `IDX_user_login_email` (`login`,`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;

INSERT INTO `user` (`id`, `version`, `login`, `email`, `name`, `password`, `salt`, `rands`, `company`, `org_id`, `is_admin`, `email_verified`, `theme`, `created`, `updated`, `help_flags1`, `last_seen_at`, `is_disabled`, `is_service_account`)
VALUES
	(1,0,'admin','admin@localhost','','f545134fe73d4887de015e2ab3bfb812fc419cf235aac075879c47c4af2c0200648e60053ad7af6846d9435f0c8a6c9c7882','zXHXdHVHuX','sgj0vgomEM','',1,1,0,'','2023-10-28 04:06:55','2025-08-16 00:26:59',0,'2025-08-16 01:29:29',0,0),
	(2,0,'sa-xiaofuge','sa-xiaofuge','xiaofuge','','R5bSuSmurD','2PnXuoxPXC','',1,0,0,'','2025-06-15 02:24:57','2025-06-15 02:24:57',0,'2025-08-16 01:33:41',0,1),
	(3,0,'sa-liergou','sa-liergou','liergou','','sk5rsTior7','ngbsSWxfeD','',1,0,0,'','2025-06-15 05:04:47','2025-06-15 05:04:47',0,'2015-06-15 05:04:47',0,1);

/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 user_auth
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_auth`;

CREATE TABLE `user_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `auth_module` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `auth_id` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created` datetime NOT NULL,
  `o_auth_access_token` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `o_auth_refresh_token` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `o_auth_token_type` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `o_auth_expiry` datetime DEFAULT NULL,
  `o_auth_id_token` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `IDX_user_auth_auth_module_auth_id` (`auth_module`,`auth_id`),
  KEY `IDX_user_auth_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



# 转储表 user_auth_token
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_auth_token`;

CREATE TABLE `user_auth_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `auth_token` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `prev_auth_token` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `client_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `auth_token_seen` tinyint(1) NOT NULL,
  `seen_at` int DEFAULT NULL,
  `rotated_at` int NOT NULL,
  `created_at` int NOT NULL,
  `updated_at` int NOT NULL,
  `revoked_at` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_user_auth_token_auth_token` (`auth_token`),
  UNIQUE KEY `UQE_user_auth_token_prev_auth_token` (`prev_auth_token`),
  KEY `IDX_user_auth_token_user_id` (`user_id`),
  KEY `IDX_user_auth_token_revoked_at` (`revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `user_auth_token` WRITE;
/*!40000 ALTER TABLE `user_auth_token` DISABLE KEYS */;

INSERT INTO `user_auth_token` (`id`, `user_id`, `auth_token`, `prev_auth_token`, `user_agent`, `client_ip`, `auth_token_seen`, `seen_at`, `rotated_at`, `created_at`, `updated_at`, `revoked_at`)
VALUES
	(4,1,'c9de15d07af959c78d37635f770b91e93e726285f13a1ff116d73b0c96057cc9','ce73d16707e3e1f4a4427679bd0ab2078248c4e70652678aa0251d38d39dfd0e','Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36','192.168.65.1',1,1755307299,1755307299,1755304011,1755304011,0);

/*!40000 ALTER TABLE `user_auth_token` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 user_role
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_role`;

CREATE TABLE `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UQE_user_role_org_id_user_id_role_id` (`org_id`,`user_id`,`role_id`),
  KEY `IDX_user_role_org_id` (`org_id`),
  KEY `IDX_user_role_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

LOCK TABLES `user_role` WRITE;
/*!40000 ALTER TABLE `user_role` DISABLE KEYS */;

INSERT INTO `user_role` (`id`, `org_id`, `user_id`, `role_id`, `created`)
VALUES
	(1,1,1,1,'2023-10-28 04:10:39');

/*!40000 ALTER TABLE `user_role` ENABLE KEYS */;
UNLOCK TABLES;



/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
