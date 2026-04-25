import base64
import os
import tempfile
import uuid
from pathlib import Path

from openai import OpenAI

from .llm import _build_openai_compatible_headers, _normalize_openai_compatible_base_url
from reactor_tool.tool.mrag.utils import download_utils
from reactor_tool.tool.mrag.utils.logger_utils import logger

class VLLMClient:
    """大模型客户端类"""

    # 配置环境变量
    # API_KEY llm 大模型apikey
    # LLM_MODEL_NAME 大模型名称
    # LLM_MODEL_BASE_URL 大模型地址
    def __init__(self, base_url=None, model_name=None, api_key=None):
        self.api_key = api_key or os.getenv("VLM_API_KEY")
        self.model_name = model_name or os.getenv("VLM_MODEL_NAME")
        # 兼容把 base url 写成具体接口路径的场景，切换到 Java 同款供应商配置时自动归一化。
        self.model_base_url = _normalize_openai_compatible_base_url(base_url or os.getenv("VLM_MODEL_BASE_URL"))
        self.client = OpenAI(
            api_key=self.api_key,
            base_url=self.model_base_url,
            default_headers=_build_openai_compatible_headers(),
        )
        logger.info(f"VLM Client {self.model_base_url}")

    @staticmethod
    def convert_messages(prompt, image_url):
        return [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_url
                        }
                    }
                ]
            }
        ]

    @staticmethod
    def convert_messages_with_image_path(prompt, image_path: str):
        image_url = image_path
        if image_path.startswith("http"):
            image_path = image_path.split("/")[-1]
            temp_dir = tempfile.gettempdir()
            uid = uuid.uuid4().hex
            _dir = os.path.join(temp_dir, uid)
            os.makedirs(_dir, exist_ok=True)
            image_path = os.path.join(_dir, image_path)
            download_utils.download_file(image_url, image_path)

        def _encode_image() -> str:
            with open(image_path, "rb") as image_file:
                return base64.b64encode(image_file.read()).decode('utf-8')

        def _get_image_mime_type() -> str:
            suffix = Path(image_path).suffix.lower()
            mime_types = {
                '.jpg': 'image/jpeg',
                '.jpeg': 'image/jpeg',
                '.png': 'image/png',
                '.gif': 'image/gif',
                '.webp': 'image/webp'
            }
            return mime_types.get(suffix, 'image/jpeg')

        base64_image = "data:" + _get_image_mime_type() + ";base64," + _encode_image()

        if image_path.startswith("http"):
            os.remove(image_path)

        return [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": base64_image
                        }
                    }
                ]
            }
        ]

    def _build_extra_body(self):
        """仅对 Qwen / DashScope 视觉模型透传专属参数，避免污染 OpenAI 兼容请求。"""
        model_name = (self.model_name or "").lower()
        if model_name.startswith("qwen") or model_name.startswith("dashscope/"):
            return {"enable_thinking": False}
        return None

    def completions(self, messages, temperature=0, stream=False, max_tokens: int = None):
        # logger.info(f"VLM completions {messages[:1]}")
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

        completion = self.client.chat.completions.create(**request_kwargs)
        if stream:
            return completion
        return completion.choices[0].message.content

    def chat(self, prompt, image_url):
        messages = self.convert_messages(prompt, image_url)
        return self.completions(messages)


if __name__ == '__main__':
    messages = [
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "提取图片中所有内容。\n    输出:\n "
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": "https://autobots.jd.com/autobots/interface/oss/downloadFile?ossName=joyspace-img/fd93bdf1aaf7ac2abea6315950176f2b.png"
                    }
                }
            ]
        }
    ]
    os.environ.setdefault("VLM_API_KEY", os.getenv("OPENAI_API_KEY", ""))
    os.environ.setdefault("VLM_MODEL_NAME", "gpt-5.2")
    os.environ.setdefault("VLM_MODEL_BASE_URL", os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"))
    llm = VLLMClient()
    print(llm.completions(messages))
