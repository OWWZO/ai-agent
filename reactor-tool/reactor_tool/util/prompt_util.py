# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/7
# =====================
import importlib

import yaml


def get_prompt(prompt_file):
    try:
        return yaml.safe_load(importlib.resources.files("reactor_tool.prompt").joinpath(f"{prompt_file}.yaml").read_text(encoding='utf-8'))
    except UnicodeDecodeError as e:
        # 如果UTF-8解码失败，记录错误并尝试GBK编码作为备选
        print(f"UTF-8解码失败，尝试GBK编码: {e}")
        return yaml.safe_load(importlib.resources.files("reactor_tool.prompt").joinpath(f"{prompt_file}.yaml").read_text(encoding='gbk'))
    except Exception as e:
        # 捕获其他异常，避免JSON序列化错误
        error_msg = f"读取提示词文件失败: {str(e)}"
        print(error_msg)
        return {"error": error_msg}

