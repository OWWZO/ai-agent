from starlette.datastructures import Headers
from typing import Optional


class HeaderEntity:
    """
    HTTP头部实体类，用于管理和处理HTTP请求头信息

    主要功能：
    - 解析和存储Cookie信息
    - 管理超时配置
    - 处理自定义服务器密钥头部
    """

    # 默认配置常量
    DEFAULT_TIMEOUT = 5000
    DEFAULT_SSE_READ_TIMEOUT = 600 * 500  # 5分钟
    MAX_TIMEOUT_MINUTES = 15000
    TIMEOUT_MULTIPLIER = 6000

    HEADER_COOKIE = "Cookie"
    HEADER_TIMEOUT = "Timeout"
    HEADER_SERVER_KEYS = "X-Server-Keys"

    def __init__(self, headers: Optional[Headers] = None):
        """
        初始化HeaderEntity实例

        Args:
            headers: Starlette Headers对象，包含HTTP请求头信息
        """
        # 初始化实例变量
        self.cookies: Optional[str] = None
        self.headers: dict[str, str] = {}
        self.timeout: int = self.DEFAULT_TIMEOUT
        self.sse_read_timeout: int = self.DEFAULT_SSE_READ_TIMEOUT
        if headers is not None:
            self.add_headers(headers)

    def add_headers(self, headers: Headers) -> None:
        """
        解析并添加HTTP头部信息

        Args:
            headers: Starlette Headers对象

        处理的头部包括：
        - Cookie: 存储Cookie信息
        - Timeout: 设置超时时间
        - X-Server-Keys: 解析服务器密钥列表并提取对应的头部值
        """
        if headers is not None:
            self._extract_cookies(headers)
            self._set_timeout_config(headers)
            self._process_server_keys(headers)

    def _extract_cookies(self, headers: Headers) -> None:
        """
        从headers中提取Cookie信息

        Args:
            headers: HTTP头部对象
        """
        cookie_value = headers.get(self.HEADER_COOKIE)
        if cookie_value:
            self.cookies = cookie_value

    def _set_timeout_config(self, headers: Headers) -> None:
        """
        设置超时配置

        Args:
            headers: HTTP头部对象

        根据Timeout头部值设置：
        - timeout: 基础超时时间
        - sse_read_timeout: SSE读取超时时间（最大15分钟）
        """
        timeout_header = headers.get(self.HEADER_TIMEOUT)
        if timeout_header is not None:
            try:
                self.timeout = int(timeout_header)
                timeout_minutes = min(self.timeout, self.MAX_TIMEOUT_MINUTES)
                self.sse_read_timeout = self.TIMEOUT_MULTIPLIER * timeout_minutes
            except (ValueError, TypeError) as e:
                print(f"警告: 超时参数解析失败，使用默认值 {self.timeout} 分钟。错误: {e}")

    def _process_server_keys(self, headers: Headers) -> None:
        """
        提取指定的服务器密钥

        Args:
            headers: HTTP头部对象

        - 格式: "key1,key2,key3", 会提取headers中对应key1、key2、key3的值
        """
        server_keys_header = headers.get(self.HEADER_SERVER_KEYS)
        if server_keys_header is not None:
            # 分割密钥列表并处理每个密钥
            key_list = [key.strip() for key in server_keys_header.split(",")]
            for key in key_list:
                if key:
                    key_value = headers.get(key)
                    if key_value is not None:
                        self.headers[key] = key_value

    def append_cookie(self, cookie: str) -> None:
        """
        追加Cookie字符串

        Args:
            cookie: 要追加的Cookie字符串
        """
        if not cookie:
            return

        if self.cookies is None:
            self.cookies = cookie
        else:
            self.cookies += f"; {cookie}"

    def get_cookie_dict(self) -> dict[str, str]:
        """
        将Cookie字符串解析为字典格式

        Returns:
            dict: Cookie键值对字典
        """
        if not self.cookies:
            return {}

        cookie_dict = {}
        for item in self.cookies.split(';'):
            item = item.strip()
            if '=' in item:
                key, value = item.split('=', 1)
                cookie_dict[key.strip()] = value.strip()

        return cookie_dict

    def __str__(self) -> str:
        """
        返回对象的字符串表示

        Returns:
            str: 包含主要属性的字符串描述
        """
        return (f"HeaderEntity(cookies={self.cookies}, "
                f"timeout={self.timeout}, "
                f"sse_read_timeout={self.sse_read_timeout}, "
                f"headers_count={len(self.headers)})")

    def __repr__(self) -> str:
        """
        返回对象的详细字符串表示

        Returns:
            str: 用于调试的详细字符串描述
        """
        return self.__str__()
