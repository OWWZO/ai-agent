# -*- coding: utf-8 -*-
"""简单耗时装饰器（print 输出秒数）。"""


def time_it(func):
    """同步函数耗时统计。"""
    import time

    def wrapper(*args, **kwargs):
        start = time.time()
        result = func(*args, **kwargs)
        end = time.time()
        print(f"{func.__name__} took {end - start} seconds")
        return result

    return wrapper