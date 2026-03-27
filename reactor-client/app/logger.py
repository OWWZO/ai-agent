"""
日志配置模块

提供统一的日志配置功能，支持控制台输出和文件轮转，
通过环境变量进行灵活配置。
"""

import logging
import os
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Optional


class LogConfig:
    """日志配置类，用于管理所有日志相关的配置项"""

    def __init__(self):
        # 基础配置
        self.name = os.getenv("LOGGER_NAME")
        self.level = os.getenv("LOG_LEVEL", "debug").upper()

        # 文件配置
        self.log_dir = Path(os.getenv("LOG_DIR", "Logs"))
        self.log_filename = os.getenv("RUNTIME_LOG_FILENAME", "runtime.log")
        self.error_log_filename = os.getenv("ERROR_LOG_FILENAME", "error.log")

        # 轮转配置
        self.max_bytes = int(os.getenv("OG_MAX_BYTES", str(256 * 1024 * 1024)))  # 默认256MB
        self.backup_count = int(os.getenv("LOG_BACKUP_COUNT", "5"))

        # 格式配置
        self.log_format = os.getenv(
            "LOG_FORMAT",
            "%(asctime)s - %(name)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s"
        )
        self.date_format = os.getenv("LOG_DATE_FORMAT", "%Y-%m-%d %H:%M:%S")

    def validate_level(self) -> int:
        """验证并返回日志级别"""
        try:
            return getattr(logging, self.level)
        except AttributeError:
            return logging.INFO


def setup_logger(name: Optional[str] = None) -> logging.Logger:
    """
    设置并返回配置好的日志器

    Args:
        name: 日志器名称，如果为None则使用环境变量配置或默认值

    Returns:
        logging.Logger: 配置好的日志器实例

    Raises:
        OSError: 当无法创建日志目录时抛出
        ValueError: 当配置参数无效时抛出
    """
    config = LogConfig()
    logger_name = name or config.name

    # 获取或创建日志器
    logger = logging.getLogger(logger_name)

    # 避免重复配置
    if logger.handlers:
        return logger

    # 设置日志级别
    log_level = config.validate_level()
    if log_level == logging.INFO and config.level != "INFO":
        print(f"警告: 无效的日志级别 '{config.level}', 使用默认级别 INFO", file=sys.stderr)

    logger.setLevel(log_level)

    # 创建格式化器
    formatter = logging.Formatter(
        fmt=config.log_format,
        datefmt=config.date_format
    )

    # 添加处理器
    _add_console_handler(logger, formatter)
    _add_file_handlers(logger, formatter, config)

    logger.info(f"日志器 '{logger_name}' 初始化完成，级别: {config.level}")
    return logger


def _add_console_handler(logger: logging.Logger, formatter: logging.Formatter) -> None:
    """添加控制台处理器"""
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    console_handler.setLevel(logging.DEBUG)
    logger.addHandler(console_handler)


def _add_file_handlers(logger: logging.Logger, formatter: logging.Formatter, config: LogConfig) -> None:
    """添加文件处理器"""
    try:
        # 确保日志目录存在
        config.log_dir.mkdir(parents=True, exist_ok=True)

        # 普通日志文件处理器 (INFO及以下级别)
        info_handler = RotatingFileHandler(
            config.log_dir / config.log_filename,
            maxBytes=config.max_bytes,
            backupCount=config.backup_count,
            encoding="utf-8"
        )
        info_handler.setFormatter(formatter)
        info_handler.setLevel(logging.DEBUG)
        info_handler.addFilter(lambda record: record.levelno < logging.WARNING)
        logger.addHandler(info_handler)

        # 错误日志文件处理器 (WARNING及以上级别)
        error_handler = RotatingFileHandler(
            config.log_dir / config.error_log_filename,
            maxBytes=config.max_bytes,
            backupCount=config.backup_count,
            encoding="utf-8"
        )
        error_handler.setFormatter(formatter)
        error_handler.setLevel(logging.WARNING)
        logger.addHandler(error_handler)

    except OSError as e:
        logger.error(f"无法创建日志文件处理器: {e}")
        raise


def get_logger(name: Optional[str] = None) -> logging.Logger:
    """
    获取日志器实例的便捷方法

    Args:
        name: 日志器名称

    Returns:
        logging.Logger: 日志器实例
    """
    return setup_logger(name)


# 创建默认日志器实例
default_logger = setup_logger()

# 导出主要接口
__all__ = ["setup_logger", "get_logger", "default_logger", "LogConfig"]