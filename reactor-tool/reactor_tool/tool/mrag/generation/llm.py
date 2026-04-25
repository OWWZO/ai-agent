import os

import dotenv
from openai import OpenAI
from reactor_tool.util.log_util import logger
dotenv.load_dotenv()

OPENAI_COMPAT_DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) "
    "Gecko/20100101 Firefox/148.0"
)


def _normalize_openai_compatible_base_url(base_url: str | None) -> str | None:
    """规范化 OpenAI 兼容网关地址，兼容填写到具体接口路径的场景。"""
    if not base_url:
        return base_url

    normalized = base_url.strip().rstrip("/")
    suffixes = (
        "/v1/chat/completions",
        "/chat/completions",
        "/v1/completions",
        "/completions",
        "/v1/responses",
        "/responses",
    )

    lowered = normalized.lower()
    changed = True
    while changed:
        changed = False
        for suffix in suffixes:
            if lowered.endswith(suffix):
                normalized = normalized[: -len(suffix)].rstrip("/")
                lowered = normalized.lower()
                changed = True
                break

    if not lowered.endswith("/v1"):
        normalized = f"{normalized}/v1"
    return normalized


def _build_openai_compatible_headers() -> dict[str, str]:
    """补齐第三方 OpenAI 兼容网关要求的默认请求头。"""
    return {
        "User-Agent": os.getenv("OPENAI_COMPAT_USER_AGENT", OPENAI_COMPAT_DEFAULT_USER_AGENT)
    }


class LLMClient:
    """大模型客户端类"""

    # 配置环境变量
    # API_KEY llm 大模型apikey
    # LLM_MODEL_NAME 大模型名称
    # LLM_MODEL_BASE_URL 大模型地址
    def __init__(self):
        self.api_key = os.getenv("LLM_API_KEY")
        self.model_name = os.getenv("LLM_MODEL_NAME")
        # 兼容把 base url 误填成 /v1/chat/completions 的配置，避免切换到 OpenAI 兼容网关时拼接出错。
        self.model_base_url = _normalize_openai_compatible_base_url(os.getenv("LLM_MODEL_BASE_URL"))
        self.client = OpenAI(
            api_key=self.api_key,
            base_url=self.model_base_url,
            default_headers=_build_openai_compatible_headers(),
        )
        logger.info("init LLM client, {base_url}".format(base_url=self.model_base_url))

    @staticmethod
    def convert_messages(prompt):
        return [{"role": "user", "content": prompt}]

    def _build_extra_body(self):
        """仅对 Qwen / DashScope 文本模型透传专属参数，避免污染 OpenAI 兼容请求。"""
        model_name = (self.model_name or "").lower()
        if model_name.startswith("qwen") or model_name.startswith("dashscope/"):
            return {
                "enable_thinking": False,
                "chat_template_kwargs": {
                    "enable_thinking": False
                }
            }
        return None

    def completions(self, messages, max_tokens=8192, temperature=0, stream=False):
        logger.info(f"chat completion\n{self.model_name}, {messages}")
        request_kwargs = {
            "model": self.model_name,
            "messages": messages,
            "temperature": temperature,
            "stream": stream,
            "max_tokens": max_tokens,
        }
        extra_body = self._build_extra_body()
        if extra_body:
            request_kwargs["extra_body"] = extra_body

        completion = self.client.chat.completions.create(
            **request_kwargs
        )
        if stream:
            return completion
        return completion.choices[0].message.content

    def chat(self, prompt, image_url):
        messages = self.convert_messages(prompt)
        return self.completions(messages)


if __name__ == '__main__':
    os.environ.setdefault("LLM_API_KEY", os.getenv("OPENAI_API_KEY", ""))
    os.environ.setdefault("LLM_MODEL_NAME", "gpt-5.2")
    os.environ.setdefault("LLM_MODEL_BASE_URL", os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"))
    llm = LLMClient()
    print(llm.completions([{"role": "user", "content": "你好"}]))
