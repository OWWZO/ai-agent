from contextlib import asynccontextmanager
from typing import Optional, Any, List, Dict
import httpx
from httpx import Headers
from mcp import ClientSession
from mcp.client.sse import sse_client

from app.logger import default_logger as logger
from app.header import HeaderEntity


class SseClient:
    """
    SSE (Server-Sent Events) 客户端类

    用于与支持 SSE 协议的服务器建立连接，执行工具调用等操作。
    支持自定义头部、超时设置、认证等功能。
    """

    # 默认配置常量
    DEFAULT_TIMEOUT = 5  # 默认连接超时时间（秒）
    DEFAULT_SSE_READ_TIMEOUT = 300000  # 默认SSE读取超时时间（秒，5分钟）

    def __init__(self, server_url: str, entity: Optional[HeaderEntity] = None):
        """
        初始化 SSE 客户端

        Args:
            server_url: SSE 服务器地址
            entity: 包含头部信息、超时设置等的实体对象
        """
        self.server_url = self._validate_server_url(server_url)
        self.headers = Headers()
        self.timeout = self.DEFAULT_TIMEOUT
        self.sse_read_timeout = self.DEFAULT_SSE_READ_TIMEOUT

        # 上下文管理器实例，用于资源清理
        self._streams_context: Optional[Any] = None
        self._session_context: Optional[ClientSession] = None

        # 如果提供了头部实体，则设置相应的头部和超时信息
        if entity is not None:
            self._configure_from_entity(entity)

        logger.debug(f"SSE客户端初始化完成 - 服务器: {self.server_url}, 超时: {self.timeout}s")

    @staticmethod
    def _validate_server_url(server_url: str) -> str:
        """
        验证服务器URL的有效性

        Args:
            server_url: 待验证的服务器URL

        Returns:
            验证后的服务器URL

        Raises:
            ValueError: 当URL无效时抛出
        """
        if not server_url or not isinstance(server_url, str):
            raise ValueError("服务器URL不能为空且必须是字符串类型")

        # 简单的URL格式验证
        if not (server_url.startswith('http://') or server_url.startswith('https://')):
            raise ValueError("服务器URL必须以http://或https://开头")

        return server_url.rstrip('/')  # 移除末尾的斜杠

    def _configure_from_entity(self, entity: HeaderEntity) -> None:
        """
        根据 HeaderEntity 配置客户端参数

        Args:
            entity: 包含配置信息的实体对象
        """
        try:
            if entity.timeout is not None:
                self.timeout = max(1, int(entity.timeout))  # 确保超时时间至少为1秒
                logger.debug(f"设置连接超时时间: {self.timeout}s")

            if entity.sse_read_timeout is not None:
                self.sse_read_timeout = max(30000, int(entity.sse_read_timeout))  # 最少30秒
                logger.debug(f"设置SSE读取超时时间: {self.sse_read_timeout}s")

            if entity.cookies is not None:
                self.headers["Cookie"] = str(entity.cookies)
                logger.debug("已设置Cookie头部")

            if entity.headers is not None:
                if isinstance(entity.headers, dict):
                    self.headers.update(entity.headers)
                    logger.debug(f"已更新自定义头部，共 {len(entity.headers)} 个")
                else:
                    logger.warning("实体中的headers不是字典类型，已忽略")

        except (ValueError, TypeError) as e:
            logger.warning(f"配置实体参数时出错: {e}，将使用默认值")

    @asynccontextmanager
    async def _sse_connection(self):
        """
        SSE 连接上下文管理器

        确保连接资源的正确创建和清理，处理认证错误等异常情况。

        Yields:
            ClientSession: 已初始化的客户端会话对象

        Raises:
            Exception: 连接失败或认证失败时抛出相应异常
        """
        session = None
        connection_id = id(self)  # 用于日志追踪

        try:
            logger.info(f"[{connection_id}] 正在连接到SSE服务器: {self.server_url}")

            # 创建SSE客户端连接
            self._streams_context = sse_client(
                url=self.server_url,
                headers=self.headers,
                timeout=self.timeout,
                sse_read_timeout=self.sse_read_timeout
            )

            # 进入流上下文，可能抛出网络连接异常
            streams = await self._streams_context.__aenter__()
            logger.debug(f"[{connection_id}] SSE流连接已建立")

            # 创建客户端会话
            self._session_context = ClientSession(*streams)
            session = await self._session_context.__aenter__()
            logger.debug(f"[{connection_id}] 客户端会话已创建")

            # 初始化会话，可能触发认证验证
            await session.initialize()
            logger.info(f"[{connection_id}] SSE连接建立成功")

            yield session

        except Exception as e:
            # 根据异常类型进行不同的处理
            if self._is_authentication_error(e):
                logger.error(f"[{connection_id}] 认证失败 - 401 未授权")
                raise Exception("认证失败 - 无效的凭据") from e
            elif self._is_network_error(e):
                logger.error(f"[{connection_id}] 网络连接失败: {str(e)}")
                raise Exception(f"网络连接失败: {str(e)}") from e
            else:
                logger.error(f"[{connection_id}] SSE连接失败: {str(e)}")
                raise
        finally:
            # 确保资源被正确清理
            await self._cleanup_connection(connection_id)

    @staticmethod
    def _is_authentication_error(exception: Exception) -> bool:
        """
        检查异常是否为认证错误 (401 Unauthorized)

        Args:
            exception: 待检查的异常对象

        Returns:
            bool: 如果是认证错误返回True，否则返回False
        """
        # 检查 ExceptionGroup 中的异常（Python 3.11+）
        if hasattr(exception, 'exceptions'):
            for exc in exception.exceptions:
                if isinstance(exc, httpx.HTTPStatusError) and exc.response.status_code == 401:
                    return True

        # 检查直接的 HTTPStatusError
        if isinstance(exception, httpx.HTTPStatusError) and exception.response.status_code == 401:
            return True

        # 检查异常消息中的关键词
        error_msg = str(exception).lower()
        auth_keywords = ["401", "unauthorized", "authentication failed", "invalid credentials"]
        return any(keyword in error_msg for keyword in auth_keywords)

    @staticmethod
    def _is_network_error(exception: Exception) -> bool:
        """
        检查异常是否为网络连接错误

        Args:
            exception: 待检查的异常对象

        Returns:
            bool: 如果是网络错误返回True，否则返回False
        """
        network_error_types = (
            httpx.ConnectError,
            httpx.TimeoutException,
            httpx.NetworkError,
            ConnectionError,
            OSError
        )

        return isinstance(exception, network_error_types)

    async def _cleanup_connection(self, connection_id: Optional[int] = None) -> None:
        """
        清理SSE连接资源

        按照正确的顺序清理会话和流连接，确保资源不泄露。

        Args:
            connection_id: 连接ID，用于日志追踪
        """
        conn_id = connection_id or id(self)
        logger.debug(f"[{conn_id}] 开始清理SSE连接资源...")

        if self._session_context:
            try:
                await self._session_context.__aexit__(None, None, None)
                logger.debug(f"[{conn_id}] 会话上下文已清理")
            except Exception as cleanup_error:
                logger.warning(f"[{conn_id}] 清理会话上下文时出错: {cleanup_error}")
            finally:
                self._session_context = None

        if self._streams_context:
            try:
                await self._streams_context.__aexit__(None, None, None)
                logger.debug(f"[{conn_id}] 上下文已清理")
            except Exception as cleanup_error:
                logger.warning(f"[{conn_id}] 清理上下文时出错: {cleanup_error}")
            finally:
                self._streams_context = None

        logger.info(f"[{conn_id}] SSE连接资源清理完成")

    async def cleanup(self) -> None:
        """
        公共清理方法

        提供给外部调用的资源清理接口。
        """
        await self._cleanup_connection()

    async def ping_server(self) -> str:
        """
        向服务器发送ping请求以验证连接

        Returns:
            str: 成功消息

        Raises:
            Exception: 当ping失败时抛出异常
        """
        try:
            async with self._sse_connection() as session:
                logger.info(f"{self.server_url} 正在ping服务器...")
                await session.send_ping()
                success_msg = "服务器ping成功！"
                logger.info(success_msg)
                return success_msg
        except Exception as e:
            error_msg = f"{self.server_url} 服务器ping失败: {str(e)}"
            logger.error(error_msg)
            raise Exception(error_msg) from e

    async def list_tools(self) -> List[Any]:
        """
        获取服务器上可用的工具列表

        Returns:
            List[Any]: 可用工具列表

        Raises:
            Exception: 当获取工具列表失败时抛出异常
        """
        try:
            async with self._sse_connection() as session:
                logger.info(f"{self.server_url} 正在获取工具列表...")
                response = await session.list_tools()
                tools = response.tools if hasattr(response, 'tools') else []

                tool_count = len(tools)
                logger.info(f"成功获取 {tool_count} 个工具")

                # 记录工具名称（如果工具有name属性）
                if tools and hasattr(tools[0], 'name'):
                    tool_names = [tool.name for tool in tools if hasattr(tool, 'name')]
                    logger.debug(f"工具列表: {', '.join(tool_names)}")

                return tools
        except Exception as e:
            error_msg = f"{self.server_url} 获取工具列表失败: {str(e)}"
            logger.error(error_msg)
            raise Exception(error_msg) from e

    async def call_tool(self, name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        """
        调用指定的工具

        Args:
            name: 工具名称
            arguments: 工具参数字典，默认为空字典

        Returns:
            Any: 工具执行结果

        Raises:
            ValueError: 当工具名称无效时抛出
            Exception: 当工具调用失败时抛出异常
        """
        # 参数验证
        if not name or not isinstance(name, str):
            raise ValueError("工具名称不能为空且必须是字符串类型")

        if arguments is None:
            arguments = {}
        elif not isinstance(arguments, dict):
            raise ValueError("工具参数必须是字典类型")

        try:
            async with self._sse_connection() as session:
                logger.info(f"正在调用工具 '{name}'，参数: {arguments}")

                # 调用工具
                response = await session.call_tool(name=name, arguments=arguments)

                logger.info(f"工具 '{name}' 执行成功")
                logger.debug(f"工具 '{name}' 返回结果类型: {type(response).__name__}")

                return response

        except Exception as e:
            error_msg = f"调用工具 '{name}' 失败: {str(e)}"
            logger.error(error_msg)
            raise Exception(error_msg) from e

    def __str__(self) -> str:
        """返回客户端的字符串表示"""
        return f"SseClient(server_url='{self.server_url}', timeout={self.timeout}s)"

    def __repr__(self) -> str:
        """返回客户端的详细字符串表示"""
        return (f"SseClient(server_url='{self.server_url}', "
                f"timeout={self.timeout}, "
                f"sse_read_timeout={self.sse_read_timeout}, "
                f"headers_count={len(self.headers)})")
