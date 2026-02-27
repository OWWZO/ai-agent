# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/9/8
# =====================
# 定义数据模型
from typing import Any, List, Literal, Optional
import uuid
import pandas as pd
import numpy as np
from pandas.api.types import is_datetime64_any_dtype, is_numeric_dtype, is_float_dtype
from pydantic import BaseModel, ConfigDict, Field, computed_field, field_validator

from genie_tool.util.log_util import timer


class Column(BaseModel):
    name: str = Field(description="列名")
    is_series: bool = Field(False, description="是否序列维度")
    is_number: bool = Field(False, description="是否数值类型")

    def __eq__(self, other: "Column") -> bool:
        return self.name == other.name and self.is_series == other.is_series and self.is_number == other.is_number

    def __str__(self):
        return f"{self.__class__.__name__}(name={self.name}, is_series={self.is_series}, is_number={self.is_number})"


class FilterColumn(BaseModel):
    column: Column
    condition: Literal["=="] = Field("==", description="比较条件")
    value: Any = Field(description="维度的取值")

    def __str__(self):
        return f"{self.column.name}{self.condition}{self.value}"


class Measure(BaseModel):
    name: str = Field(description="度量名")
    column: str = Field(description="列名")
    type: Literal["quantity", "ratio"] = Field(description="度量类型")
    agg: Literal["sum", "mean", "count", "min", "max"] = Field(
        description="聚合函数", validate_default=True)
    extend_type: Literal["original", "rank", "rate", "sub_avg", "increase"] = Field(
        "original", description="是否是扩展指标以及扩展类型")

    @field_validator("agg", mode="before")
    @classmethod
    def validate_agg(cls, val, values):
        if not val:
            val = "sum" if values.data["type"] == "quantity" else "max"
        return val

    def __str__(self):
        return f"{self.__class__.__name__}({self.agg}({self.column}))"


class DataModel(BaseModel):
    id: str = Field(str(uuid.uuid4()), description="")
    measure: Measure = Field(None, description="度量")
    data: pd.DataFrame = Field(exclude=True)
    columns: List[Column] = Field(
        [], description="可分析的维度", validate_default=True)

    model_config = ConfigDict(arbitrary_types_allowed=True)

    def __len__(self):
        return len(self.data)

    @field_validator("data", mode="before")
    @classmethod
    def validate_data(cls, data: pd.DataFrame, values) -> pd.DataFrame:
        measure = values.data["measure"]
        data = data.dropna(subset=[measure.column])
        if len(data) == 0:
            raise ValueError(f"The dataframe {data} is empty after dropna.")
        for col in data.columns:
            if len(data[col].unique()) == 1:
                data = data.drop(col, axis=1)
        return data

    @field_validator("columns", mode="before")
    @classmethod
    def validate_columns(cls, val, values) -> List[Column]:
        data = values.data["data"]
        measure = values.data["measure"]
        if not val:
            val = [Column(name=c,
                          is_series=is_datetime64_any_dtype(data[c]),
                          is_number=is_numeric_dtype(data[c]),
                          ) for c in data.columns if c != measure.column]
        if val and isinstance(val, list) and isinstance(val[0], str):
            val = [c for c in val if c in data.columns] or data.columns
            val = [Column(name=c,
                          is_series=is_datetime64_any_dtype(data[c]),
                          is_number=is_numeric_dtype(data[c]),
                          ) for c in val if c != measure.column]
        return val

    @timer(key="DataModel")
    def get_data(self):
        return self.data

    def __eq__(self, other: "DataModel") -> bool:
        return self.id == other.id and str(self.measure) == str(other.measure) and str(self.columns) == str(other.columns)

    def __hash__(self):
        return f"{self.id}_{hash(str(self.measure) + str(self.columns))}"

    def __str__(self):
        return f"{self.__class__.__name__}(id={self.id}, measure={self.measure}, columns={self.columns})"


class SiblingGroup(BaseModel):
    data: DataModel
    filters: List[FilterColumn] = Field([], description="")
    breakdown: Column
    ud_impact: Optional[float] = Field(
        None, description="主动传入的重要度", exclude=True)

    @computed_field
    def impact(self) -> float:
        if self.ud_impact is not None:
            return self.ud_impact
        if self.filters:
            return 1.0
        else:
            y = self.data.get_data()[self.measure.column].values
            y_min = np.min(y)
            y_norm = y - y_min
            if y_norm.sum() == 0:
                return 0.0
            y_sub = self.get_data()[self.measure.column].values
            y_sub_norm = y_sub - y_min
            return float(y_sub_norm.sum() / y_norm.sum())

    @field_validator("filters", mode="before")
    @classmethod
    def validate_filters(cls, val) -> List[FilterColumn]:
        if val is None:
            return []
        if isinstance(val, FilterColumn):
            return [val]
        return val

    @property
    def measure(self):
        return self.data.measure

    @timer(key="SiblingGroup")
    def get_data(self):
        df = self.data.get_data()
        for f in self.filters:
            if f.column.name in df.columns:
                df = df[df[f.column.name] == f.value]
        if not self.breakdown.is_number and df[self.breakdown.name].size > len(df[self.breakdown.name].unique()):
            df = df.groupby(self.breakdown.name).agg(agg_name_temp=(self.data.measure.column, self.data.measure.agg))\
                .rename(columns={"agg_name_temp": self.data.measure.column}).reset_index()
        return df

    def __eq__(self, other: "DataModel") -> bool:
        return self.data == other.data and self.filters == other.filters and self.breakdown == other.breakdown

    def __str__(self):
        return f"{self.__class__.__name__}(data={str(self.data)}, filters={self.filters}, breakdown={self.breakdown})"


class SiblingGroupContainer(list):
    def __init__(self, sgs:  SiblingGroup | List[SiblingGroup] = [], threshold: float = 0.01):
        if isinstance(sgs, SiblingGroup):
            sgs = [sgs]
        sgs = [sg for sg in sgs if sg.impact >= threshold]
        super().__init__(sgs)
        self.sort(key=lambda x: x.impact, reverse=True)
        self.threshold = threshold

    def append(self, sg: SiblingGroup):
        if sg.impact >= self.threshold:
            super().append(sg)
            self.sort(key=lambda x: x.impact, reverse=True)

    def extend(self, sgs: List[SiblingGroup] | "SiblingGroupContainer"):
        for sg in sgs:
            if sg.impact >= self.threshold:
                super().append(sg)
        self.sort(key=lambda x: x.impact, reverse=True)

    @classmethod
    @timer()
    def constract_from_data_model(cls, data_model: DataModel) -> "SiblingGroupContainer":
        subspaces = []
        for c1 in data_model.columns:
            subspaces.append(SiblingGroup(data=data_model, breakdown=c1))
        for i, c1 in enumerate(data_model.columns):
            for j, c2 in enumerate(data_model.columns):
                if i == j or is_float_dtype(data_model.data[c1.name]):
                    continue
                if len(vals := data_model.data[c1.name].unique()) > 1:
                    subspaces.extend([SiblingGroup(data=data_model, filters=[FilterColumn(
                        column=c1, value=val)], breakdown=c2) for val in vals])
        return cls(subspaces)


if __name__ == "__main__":
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
    df["Year"] = pd.to_datetime(df.Year, format="%Y")
    # df["Year"] = pd.to_numeric(df.Year, downcast="integer")

    data_model = DataModel(
        data=df,
        measure=Measure(name="销售额", column="Sale", agg="sum", type="quantity"),
    )

    subspaces = SiblingGroupContainer.constract_from_data_model(
        data_model=data_model)

    subspaces[0].get_data()
