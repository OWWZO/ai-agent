# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""从 reactor_tool.prompt 包加载 YAML 提示词模板。"""
import importlib

import yaml


def get_prompt(prompt_file):
    """按文件名（不含扩展名）读取 prompt/*.yaml，返回解析后的 dict。

    优先 UTF-8；Windows 历史文件可能是 GBK，解码失败时回退。
    """
    try:
        return yaml.safe_load(importlib.resources.files("reactor_tool.prompt").joinpath(f"{prompt_file}.yaml").read_text(encoding='utf-8'))
    except UnicodeDecodeError as e:
        # UTF-8 解码失败，尝试 GBK 编码作为备选
        print(f"UTF-8解码失败，尝试GBK编码: {e}")
        return yaml.safe_load(importlib.resources.files("reactor_tool.prompt").joinpath(f"{prompt_file}.yaml").read_text(encoding='gbk'))
    except Exception as e:
        # 避免异常对象直接进入 JSON 序列化
        error_msg = f"读取提示词文件失败: {str(e)}"
        print(error_msg)
        return {"error": error_msg}
