# -*- coding: utf-8 -*-
"""pytest 全局装配。

``tests/test_deepsearch_query_process.py`` 在模块导入期把
``sys.modules["reactor_tool.util.llm_util"]`` 换成最小桩并且不还原，同一 pytest
会话里后导入的模块只会看到那个桩（unittest 按文件名分批跑时看不到这个问题）。
这里在会话开始/结束时各快照并恢复一次 sys.modules，让这类导入期副作用不外溢。
"""

import sys

_ORIGINAL_MODULES: dict = {}


def pytest_configure(config):
    _ORIGINAL_MODULES.clear()
    _ORIGINAL_MODULES.update(sys.modules)


def pytest_unconfigure(config):
    stubbed = {
        name: module
        for name, module in sys.modules.items()
        if name not in _ORIGINAL_MODULES
    }
    for name in stubbed:
        del sys.modules[name]
    sys.modules.update(_ORIGINAL_MODULES)
