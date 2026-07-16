# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/6/4
# =====================
"""敏感信息脱敏：邮箱、手机号、身份证、银行卡等正则替换。"""
import re


class SensitiveWordsReplace:
    """基于正则的敏感字段打码工具。

    模式参考：
    - 邮箱: https://github.com/cdoco/common-regex
    - 手机号: https://github.com/VincentSit/ChinaMobilePhoneNumberRegex
    """
    EMAIL_PATTERN = r"[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+"

    PHONE_PATTERN = r"(?<![A-Za-z_\d])(1[3-9]\d{9})(?![A-Za-z_\d])"

    ID_PATTERN = r"(?:[^\dA-Za-z_]|^)((?:[1-6][1-7]|50|71|81|82)\d{4}(?:19|20)\d{2}(?:0[1-9]|10|11|12)(?:[0-2][1-9]|10|20|30|31)\d{3}[0-9Xx])(?:[^\dA-Za-z_]|$)"

    BANK_ID_PATTERN = r"(?:[^\dA-Za-z_]|^)(62(?:\d{14}|\d{17}))(?:[^\dA-Za-z_]|$"

    @classmethod
    def replace(cls, content, remove_email=True, remove_phone_number=True,
                remove_id_number=True, remove_bank_id=True,
                replace_word: str = "***", **kwargs):
        """按开关批量脱敏，默认全部开启。"""
        if remove_email:
            content = cls.replace_email(content, replace_word=replace_word)
        if remove_phone_number:
            content = cls.replace_phone_number(content)
        if remove_id_number:
            content = cls.replace_id_number(content)
        if remove_bank_id:
            content = cls.replace_bank_id_number(content)
        return content

    @classmethod
    def replace_email(cls, content: str, replace_word: str = "***"):
        """替换邮箱地址。"""
        return re.sub(cls.EMAIL_PATTERN, replace_word, content)

    @classmethod
    def replace_phone_number(cls, content: str, replace_word: str = "*" * 11):
        """替换大陆手机号。"""
        return re.sub(cls.PHONE_PATTERN, replace_word, content)

    @classmethod
    def replace_id_number(cls, content: str, replace_word: str = "*" * 18):
        """替换 18 位身份证号。"""
        return re.sub(cls.ID_PATTERN, replace_word, content)

    @classmethod
    def replace_bank_id_number(cls, content: str, replace_word: str = "*" * 19):
        """替换银行卡号（当前实现复用 ID_PATTERN，与历史代码一致）。"""
        return re.sub(cls.ID_PATTERN, replace_word, content)
