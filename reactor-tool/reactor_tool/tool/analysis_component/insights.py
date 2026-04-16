# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/9/8
# =====================
# Insight 定义
from functools import total_ordering
import math
import os
import shutil
import tempfile
from loguru import logger
import numpy as np
import pandas as pd
from pydantic import BaseModel, Field, computed_field, field_validator
from functools import partial
from typing import Callable, Dict, List, Optional

from scipy.optimize import curve_fit
from scipy.stats import norm, t, logistic, linregress, pearsonr
from scipy.signal import find_peaks

from reactor_tool.tool.analysis_component.data_model import DataModel, Measure, SiblingGroup, SiblingGroupContainer
from reactor_tool.util.log_util import timer


def np_type_trans(val):
    if isinstance(val, (int, float, complex, str, bool)):
        return val
    if isinstance(val, np.integer, np.int8, np.int16, np.int32, np.int64):
        return int(val)
    if isinstance(val, (np.floating, np.float16, np.float32, np.float64)):
        return float(val)
    return str(val)


"""参考 tki 实现"""


def power_dist(arr: np.ndarray, alpha: float, beta: float) -> np.ndarray:
    """Power distribution"""
    return alpha * np.power(arr, -beta)


def power_dist_fix_beta(
        beta: float = 0.7) -> Callable[[np.ndarray, float], np.ndarray]:
    """Power distribution with fixed beta"""
    return partial(power_dist, beta=beta)


def linear_dist(arr: np.ndarray, alpha: float, beta: float) -> np.ndarray:
    """Linear distribution"""
    return alpha * arr + beta


def quadratic_dist(arr: np.ndarray, alpha: float, beta: float) -> np.ndarray:
    """Quadratic distribution"""
    return alpha + beta * np.power(arr, 2)


def cubic_dist(arr: np.ndarray, alpha: float,
               beta: float, gamma: float) -> np.ndarray:
    """Cubic distribution"""
    return alpha + beta * np.power(arr, 2) + gamma * np.power(arr, 3)


@total_ordering
class InsightType(BaseModel):
    """https://www.microsoft.com/en-us/research/wp-content/uploads/2016/12/Insight-Types-Specification.pdf"""
    type: str
    insight: Dict = Field({}, description="洞察")
    impact: float = Field(0.0, description="影响度")
    significance: float = Field(0.0, description="显著度")
    data: Optional[List[Dict]] = None
    
    @field_validator("significance", mode="before")
    @classmethod
    def validate_significance(cls, val):
        if val is None:
            val = 0.0
        return np_type_trans(val)

    @computed_field
    def score(self) -> float:
        return self.impact * self.significance
    
    @field_validator("data", mode="before")
    @classmethod
    def validator_data(cls, val) -> List[Dict]:
        if isinstance(val, pd.DataFrame):
            val =  cls.df_to_list(val)
        return val

    def __eq__(self, other: "InsightType") -> bool:
        return self.score == other.score

    def __lt__(self, other: "InsightType") -> bool:
        return self.score < other.score

    @classmethod
    def from_data(cls, data: SiblingGroup, threshold: float = 0.01, **kwargs) -> "InsightType":
        """通过数据计算生成 InsightType 对象"""
        insight = None
        if cls._check(data, **kwargs):
            insight = cls._from_data(data, **kwargs)
            if insight is None:
                return None
            if insight and insight.score < threshold:
                logger.warning(f"{insight.type} {insight.insight} insight.score < {threshold}")
                insight = None
            if insight and (insight.significance is None or insight.significance < threshold):
                # 显著性不明显
                logger.warning(f"{insight.type} {insight.insight} significance < {threshold}")
                insight = None
        else:
            logger.warning(f"{cls.__name__} for {data} not support.")
        if insight:
            insight.insight["breakdown"] = data.breakdown.name
            insight.insight["filters"] = data.filters
        return insight
    
    @staticmethod
    def df_to_list(df: pd.DataFrame) -> List[Dict]:
        df = df.reset_index()
        if "index" in df.columns:
            df = df.drop("index", axis=1)
        for col in df.columns:
            if pd.api.types.is_datetime64_any_dtype(df[col]):
                df[col] = df[col].dt.strftime("%Y-%m-%d")
            elif isinstance(df[col].dtype, pd.PeriodDtype):
                df[col] = df[col].astype(str)
        return [row[1].to_dict() for row in df.iterrows()]
    
    @staticmethod
    def df_to_csv(df: pd.DataFrame) -> str:
        df = df.reset_index()
        if "index" in df.columns:
            df = df.drop("index", axis=1)
        
        work_dir = tempfile.mkdtemp()
        try:
            df.to_csv(os.path.join(work_dir, "data.csv"), index=False, encoding='utf-8')
            try:
                with open(os.path.join(work_dir, "data.csv"), "r", encoding='utf-8') as rf:
                    return "".join(rf.readlines())
            except UnicodeDecodeError:
                # UTF-8失败时尝试GBK
                with open(os.path.join(work_dir, "data.csv"), "r", encoding='gbk') as rf:
                    return "".join(rf.readlines())
        finally:
            shutil.rmtree(work_dir)
    
    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        return True

    @classmethod
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        """通过数据计算生成 InsightType 对象、"""
        raise NotImplementedError()


class OutstandingFirstInsightType(InsightType):
    """在某个维度上，某个值的指标值是最高的"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type in ["rank"]:
            return False
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values
        return y.size > 3 and y[-1] >= 0 and y[0] > y[1] + y[2] and (0.0 < y[0] / y.sum() < 0.5)

    @classmethod
    @timer(key="OutstandingFirst")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values
        x = range(1, y.size + 1)

        bais = np.min(y)

        hy_dist = power_dist_fix_beta(0.7)

        fit_params, _ = curve_fit(hy_dist, x[1:], y[1:] - bais, maxfev=5000)

        pred = hy_dist(x, *fit_params) + bais

        # 计算 p-value
        residuals = y-pred
        loc, scale = norm.fit(residuals[1:])
        p_value = norm.sf(residuals[0], loc=loc, scale=scale)        
        
        return InsightType(
            type="OutstandingFirst",
            insight={
                "description": f"{data.filters or ''} {data.breakdown.name} = {df.iloc[0][data.breakdown.name]} 时，{data.measure.name} 达到最高值为 {y[0]}",
                "max_value" : {
                    data.measure.name: np_type_trans(y[0]),
                    data.breakdown.name: np_type_trans(df.iloc[0][data.breakdown.name])
                },
            },
            impact=data.impact,
            significance=1 - p_value,
            data=df[[data.breakdown.name, data.measure.column]],
        )


class OutstandingLastInsightType(InsightType):
    """在某个维度上，某个值的指标值是最低的"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type in ["rank"]:
            return False
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values
        return y.size > 3 and y[-1] < 0 and y[-1] < y[-2] + y[-3]

    @classmethod
    @timer(key=f"OutstandingLast")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values
        x = range(1, y.size + 1)

        bais = np.min(y)

        hy_dist = power_dist_fix_beta(0.7)

        fit_params, _ = curve_fit(hy_dist, x[:-1], y[:-1] - bais, maxfev=5000)

        pred = hy_dist(x, *fit_params) + bais

        # 计算 p-value
        residuals = y-pred
        loc, scale = norm.fit(residuals[:-1])
        p_value = norm.sf(residuals[0], loc=loc, scale=scale)
        return InsightType(
            type="OutstandingLast",
            insight={
                "description": f"{data.filters or ''} {data.breakdown.name} = {df.iloc[y.size-1][data.breakdown.name]} 时，{data.measure.name} 最低为 {y[-1]}",
                "min_value" : {
                    data.measure.name: np_type_trans(y[-1]),
                    data.breakdown.name: np_type_trans(df.iloc[0][data.breakdown.name])
                },
            },
            impact=data.impact,
            significance=1 - p_value,
            data=df[[data.breakdown.name, data.measure.column]],
        )


class AttributionInsightType(InsightType):
    """在某个维度上，某个值的指标值是占据主导地位，具有绝对的影响力"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type in ["rank"]:
            return False
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values

        # 最后一个条件表示占比类的指标不使用这个分析
        return y.size > 2 and np.all(y >= 0) and np.max(y) / y.sum() >= 0.5 and int(np.max(y) * 100) != int(np.max(y) / y.sum() * 100)
    
    @classmethod
    @timer(key=f"Attribution")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values
        x = range(1, y.size + 1)

        bais = np.min(y)

        hy_dist = power_dist_fix_beta(0.7)

        fit_params, _ = curve_fit(hy_dist, x[1:], y[1:] - bais, maxfev=5000)

        pred = hy_dist(x, *fit_params) + bais

        # 计算 p-value
        residuals = y-pred
        loc, scale = norm.fit(residuals[1:])
        p_value = norm.sf(residuals[0], loc=loc, scale=scale)
        if np.isnan(p_value):
            p_value = 0.05
        return InsightType(
            type="Attribution",
            insight={
                "description": f"{data.filters or ''}{data.breakdown.name} = {df.iloc[0][data.breakdown.name]} 的 {data.measure.name} 为 {y[0]}，占比超 {y[0] / y.sum() * 100:.2f}%",
                "max_value" : {
                    data.measure.name: np_type_trans(y[0]),
                    data.breakdown.name: np_type_trans(df.iloc[0][data.breakdown.name]),
                    "rate": y[0] / y.sum()
                },
            },
            impact=data.impact,
            significance=1 - p_value,
            data=df[[data.breakdown.name, data.measure.column]],
        )


class EvennessInsightType(InsightType):
    """在某个维度上，某个值的指标值是均匀的"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type not in ["original"]:
            return False
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values

        return y.size > 2 and (np.all(y >= 0) or np.all(y < 0)) and y.sum() != 0

    @classmethod
    @timer(key=f"Evenness")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        df = df.sort_values(by=data.measure.column, ascending=False)
        y = df[data.measure.column].values

        y_p = y / y.sum()
        shannon = -np.sum(y_p * np.log(y_p, out=np.zeros_like(y_p),
                          where=(y_p != 0))) / np.log(y.size)

        if shannon >= 1:
            shannon = 1.0
            test = np.inf
        else:
            test = shannon * math.sqrt(0.001 / (1 - shannon**2))

        p_value = t.sf(test, y.size - 2) * 2

        return InsightType(
            type="Evenness",
            insight={"description": f"{data.filters or ''} 在{data.breakdown.name}下 {data.measure.name} 分布较为均匀"},
            impact=data.impact,
            significance=1 - p_value,
            data=df[[data.breakdown.name, data.measure.column]],
        )


class TrendInsightType(InsightType):
    """在某个维度上，某个值的指标值是呈现某种趋势"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type in ["rank"]:
            return False
        y = data.get_data()[data.measure.column].values
        return y.size > 2 and data.breakdown.is_series

    @classmethod
    @timer(key=f"Trend")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        y = df[data.measure.column].values

        # Fit linear regression on the data
        fit_line = linregress(x=range(y.size), y=y)
        slope = fit_line.slope
        intercept = fit_line.intercept
        r_value = fit_line.rvalue
        
        if abs(slope) < 0.02:
            return None

        p_value = logistic.sf(abs(slope), loc=0.2, scale=2.0)

        return InsightType(
            type="Trend",
            insight={
                "description": f"{data.filters or ''} {data.breakdown.name} 维度的 {data.measure.name} 指标呈现{'上升' if slope > 0 else '下降'}趋势，趋势为 {slope:.4f} * x + {intercept:.4f}",
                "trend": "上升" if slope > 0 else "下降",
                "slope": np_type_trans(slope),
                "intercept": np_type_trans(fit_line.intercept)
            },
            impact=data.impact,
            significance=(1 - p_value) * r_value**2,
            data=df[[data.breakdown.name, data.measure.column]],
        )


class ChangePointInsightType(InsightType):
    """在某个维度上，某个值的指标值的转折点"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        if kwargs.get("debug", False):
            return True
        if data.measure.extend_type in ["rank"]:
            return False
        y = data.get_data()[data.measure.column].values
        return y.size > 4 and data.breakdown.is_series

    @classmethod
    @timer(key=f"ChangePoint")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()
        y = df[data.measure.column].values
        
        peaks, _ = find_peaks(y)

        if peaks.size == 0:
            return None

        max_peak_idx = None
        max_peak = None
        p_value = 1
        for p in peaks:
            y_left_mean = np.mean(y[:p])
            y_right_mean = np.mean(y[p:])
            std = math.sqrt((np.sum(y ** 2) / (2 * y.size) - (np.sum(y) / (2 * y.size)) ** 2) / y.size)
            k_mean = abs(y_left_mean - y_right_mean) / std
            
            tmp_p_value = 2 * (1 - norm.cdf(k_mean))
            if tmp_p_value <= p_value:
                p_value = tmp_p_value
                max_peak_idx = int(p)
                max_peak = y[p]

        return InsightType(
            type="ChangePoint",
            insight={
                "description": f"{data.filters or ''} {data.measure.name} 指标在 {data.breakdown.name}={df.iloc[max_peak_idx][data.breakdown.name]} 处发生转折，转折点值为 {max_peak}",
                "change_point": f"{data.breakdown.name}={df.iloc[max_peak_idx][data.breakdown.name]}",
                "change_value": np_type_trans(max_peak),
            },
            impact=data.impact,
            significance=(1 - p_value),
            data=df[[data.breakdown.name, data.measure.column]],
        )


class CorrelationInsightType(InsightType):
    """在某个维度上和指标值相关性"""

    @classmethod
    def _check(cls, data: SiblingGroup, **kwargs) -> bool:
        return data.breakdown.is_number and len(data.get_data()) > 5

    @classmethod
    @timer(key=f"Correlation")
    def _from_data(cls, data: SiblingGroup, **kwargs) -> "InsightType":
        df = data.get_data()[[data.breakdown.name, data.measure.column]]
        df = df.sort_index(axis=1)

        c1 = df[data.breakdown.name].values
        c2 = df[data.measure.column].values

        r_value, p_value = pearsonr(c1, c2)
        return InsightType(
            type="Correlation",
            insight={
                "description": f"{data.filters or ''} {data.breakdown.name} 和 {data.measure.name} 指标呈现{'正相关' if r_value > 0 else '负相关'}，相关系数为 {r_value:.4f}",
                "correlation": "正相关" if r_value > 0 else "负相关",
                "coefficient": np_type_trans(r_value),
                "columns": [data.breakdown.name, data.measure.column]
            },
            impact=data.impact,
            significance=(1 - p_value),
            data=df[[data.breakdown.name, data.measure.column]],
        )


InsightFactory: List[InsightType] = [
    OutstandingFirstInsightType, 
    OutstandingLastInsightType, 
    AttributionInsightType, 
    EvennessInsightType, 
    TrendInsightType, 
    CorrelationInsightType,
    ChangePointInsightType,
]


InsightFactoryDict: Dict[str, InsightType] = {c.__name__.replace("InsightType", ""): c for c in InsightFactory}


if __name__ == "__main__":
    import json
    
    df = pd.DataFrame(
        [
            ["2010", "H", 40], ["2010", "T", 38], [
                "2010", "F", 13], ["2010", "B", 20],
            ["2011", "H", 35], ["2011", "T", 34], [
                "2011", "F", 10], ["2011", "B", 18],
            ["2012", "H", 36], ["2012", "T", 34], [
                "2012", "F", 14], ["2012", "B", 20],
            ["2013", "H", 43], ["2013", "T", 29], [
                "2013", "F", 23], ["2013", "B", 17],
            ["2014", "H", 58], ["2014", "T", 36], [
                "2014", "F", 27], ["2014", "B", 19],
        ],
        columns=["Year", "Brand", "Sale"],
    )
    # df["Year"] = pd.to_datetime(df.Year, format="%Y")
    df["Year"] = pd.to_numeric(df.Year, downcast="integer")

    data_model = DataModel(
        data=df,
        measure=Measure(name="销售额", column="Sale", agg="sum", type="quantity"),
    )

    subspaces = SiblingGroupContainer.constract_from_data_model(data_model=data_model)

    print(json.dumps(OutstandingFirstInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(OutstandingLastInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(AttributionInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(EvennessInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(TrendInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(CorrelationInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))
    print(json.dumps(ChangePointInsightType.from_data(subspaces[0], threshold=0.0, debug=True).model_dump(), ensure_ascii=False))

