"""Regression tests for the data_clean fill_missing contract."""

from __future__ import annotations

from reactor_tool.tool.dataprep.data_clean import DataCleanTool
from reactor_tool.tool.docread._compat import ToolContext


def test_fill_missing_schema_exposes_fill_value_not_value():
    properties = DataCleanTool().parameters["properties"]
    operation_properties = properties["operations"]["items"]["properties"]

    assert "fill_value" in operation_properties
    assert "fill_strategy" in operation_properties
    assert "value" not in operation_properties


def test_fill_missing_uses_fill_value_for_literal_values():
    result = DataCleanTool().execute_sync(
        {
            "data": [{"value": 1}, {"value": None}],
            "operations": [
                {"type": "fill_missing", "columns": ["value"], "fill_value": 0}
            ],
        },
        ToolContext(),
    )

    assert result["data"] == [{"value": 1.0}, {"value": 0.0}]
    assert result["operations_applied"][0]["values_filled"] == 1


def test_fill_missing_rejects_value_alias_without_fill_value():
    try:
        DataCleanTool().execute_sync(
            {
                "data": [{"value": None}],
                "operations": [
                    {"type": "fill_missing", "columns": ["value"], "value": 0}
                ],
            },
            ToolContext(),
        )
    except ValueError as exc:
        assert "requires 'fill_value'" in str(exc)
    else:
        raise AssertionError("expected fill_value contract error")
