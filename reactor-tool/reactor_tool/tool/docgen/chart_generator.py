# -*- coding: utf-8 -*-
"""Standalone chart generator (matplotlib) for chart_generator tool."""
from __future__ import annotations

from pathlib import Path
from typing import Any

CHART_THEMES: dict[str, dict[str, Any]] = {
    "presentation": {
        "figsize": (12, 7),
        "dpi": 150,
        "font_family": "sans-serif",
        "font_size": 14,
        "title_size": 20,
        "palette": ["#2563EB", "#DC2626", "#16A34A", "#CA8A04", "#9333EA", "#0891B2"],
        "bg_color": "#FFFFFF",
        "grid_alpha": 0.3,
        "spine_visible": False,
    },
    "report": {
        "figsize": (8, 5),
        "dpi": 200,
        "font_family": "serif",
        "font_size": 11,
        "title_size": 14,
        "palette": ["#1F4E79", "#2E75B6", "#9DC3E6", "#ED7D31", "#A5A5A5", "#FFC000"],
        "bg_color": "#FFFFFF",
        "grid_alpha": 0.2,
        "spine_visible": True,
    },
    "dashboard": {
        "figsize": (10, 6),
        "dpi": 150,
        "font_family": "sans-serif",
        "font_size": 12,
        "title_size": 16,
        "palette": ["#6366F1", "#EC4899", "#14B8A6", "#F59E0B", "#8B5CF6", "#EF4444"],
        "bg_color": "#F8FAFC",
        "grid_alpha": 0.15,
        "spine_visible": False,
    },
    "minimal": {
        "figsize": (8, 5),
        "dpi": 150,
        "font_family": "sans-serif",
        "font_size": 11,
        "title_size": 13,
        "palette": ["#111111", "#555555", "#999999", "#CCCCCC", "#E5E5E5", "#F5F5F5"],
        "bg_color": "#FFFFFF",
        "grid_alpha": 0.1,
        "spine_visible": False,
    },
}


def generate_chart(params: dict[str, Any], output_path: Path) -> dict[str, Any]:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np

    try:
        from reactor_tool.docgen.cjk_font_discovery import resolve_cjk_regular_path
        from matplotlib import font_manager

        cjk = resolve_cjk_regular_path()
        if cjk:
            font_manager.fontManager.addfont(str(cjk))
            plt.rcParams["font.sans-serif"] = [font_manager.FontProperties(fname=str(cjk)).get_name(), "DejaVu Sans"]
            plt.rcParams["axes.unicode_minus"] = False
    except Exception:
        pass

    chart_type = str(params.get("chart_type") or "").strip().lower()
    if chart_type not in {
        "bar", "line", "pie", "scatter", "heatmap", "radar", "area", "histogram", "horizontal_bar"
    }:
        raise ValueError(f"Unsupported chart_type: {chart_type}")

    data = params.get("data") or {}
    if not isinstance(data, dict):
        raise ValueError("`data` must be an object")

    title = str(params.get("title") or "")
    x_label = str(params.get("x_label") or "")
    y_label = str(params.get("y_label") or "")
    theme_name = str(params.get("theme") or "presentation")
    output_format = str(params.get("output_format") or "png").lower()
    if output_format not in {"png", "svg", "pdf"}:
        raise ValueError("output_format must be png|svg|pdf")
    show_legend = params.get("show_legend")
    stacked = bool(params.get("stacked", False))
    theme = CHART_THEMES.get(theme_name, CHART_THEMES["presentation"])

    output_path = Path(output_path)
    if output_path.suffix.lower() not in {".png", ".svg", ".pdf"}:
        output_path = output_path.with_suffix(f".{output_format}")
    output_path.parent.mkdir(parents=True, exist_ok=True)

    plt.rcParams["font.family"] = theme["font_family"]
    plt.rcParams["font.size"] = theme["font_size"]
    fig, ax = plt.subplots(figsize=tuple(theme["figsize"]))
    fig.patch.set_facecolor(theme["bg_color"])
    ax.set_facecolor(theme["bg_color"])
    palette = theme["palette"]

    categories = data.get("categories") or []
    series_list = data.get("series") or []
    values = data.get("values") or []
    labels = data.get("labels") or categories

    if chart_type in ("bar", "horizontal_bar"):
        x = np.arange(len(categories))
        width = 0.8 / max(len(series_list), 1)
        for i, s in enumerate(series_list):
            vals = (s.get("values") or [])[: len(categories)]
            offset = (i - len(series_list) / 2 + 0.5) * width
            if stacked:
                bottom = np.zeros(len(categories))
                for j in range(i):
                    bottom += np.array((series_list[j].get("values") or [])[: len(categories)])
                if chart_type == "horizontal_bar":
                    ax.barh(x, vals, width * len(series_list), left=bottom, label=s.get("name", ""), color=palette[i % len(palette)])
                else:
                    ax.bar(x, vals, width * len(series_list), bottom=bottom, label=s.get("name", ""), color=palette[i % len(palette)])
            else:
                if chart_type == "horizontal_bar":
                    ax.barh(x + offset, vals, width, label=s.get("name", ""), color=palette[i % len(palette)])
                else:
                    ax.bar(x + offset, vals, width, label=s.get("name", ""), color=palette[i % len(palette)])
        if chart_type == "horizontal_bar":
            ax.set_yticks(x)
            ax.set_yticklabels(categories)
        else:
            ax.set_xticks(x)
            ax.set_xticklabels(categories, rotation=45 if len(categories) > 6 else 0, ha="right" if len(categories) > 6 else "center")

    elif chart_type in ("line", "area"):
        for i, s in enumerate(series_list):
            vals = (s.get("values") or [])[: len(categories)] if categories else (s.get("values") or [])
            if chart_type == "area":
                ax.fill_between(range(len(vals)), vals, alpha=0.3, color=palette[i % len(palette)])
            ax.plot(range(len(vals)), vals, label=s.get("name", ""), color=palette[i % len(palette)], linewidth=2, marker="o", markersize=4)
        if categories:
            ax.set_xticks(range(len(categories)))
            ax.set_xticklabels(categories, rotation=45 if len(categories) > 6 else 0, ha="right" if len(categories) > 6 else "center")

    elif chart_type == "pie":
        pie_values = values if values else ((series_list[0].get("values") if series_list else []) or [])
        pie_labels = labels if labels else [f"Slice {i + 1}" for i in range(len(pie_values))]
        colors = palette[: max(1, len(pie_values))]
        ax.pie(pie_values, labels=pie_labels, colors=colors, autopct="%1.1f%%", startangle=90)
        ax.set_aspect("equal")

    elif chart_type == "scatter":
        ax.scatter(data.get("x") or [], data.get("y") or [], c=palette[0], alpha=0.7, s=50)

    elif chart_type == "heatmap":
        matrix = np.array(data.get("matrix") or [[]])
        im = ax.imshow(matrix, cmap="YlOrRd", aspect="auto")
        fig.colorbar(im, ax=ax)
        if categories:
            ax.set_xticks(range(len(categories)))
            ax.set_xticklabels(categories, rotation=45, ha="right")
        row_labels = data.get("row_labels") or []
        if row_labels:
            ax.set_yticks(range(len(row_labels)))
            ax.set_yticklabels(row_labels)

    elif chart_type == "histogram":
        hist_values = values if values else ((series_list[0].get("values") if series_list else []) or [])
        ax.hist(hist_values, bins="auto", color=palette[0], edgecolor="white", alpha=0.8)

    elif chart_type == "radar":
        if series_list and categories:
            angles = np.linspace(0, 2 * np.pi, len(categories), endpoint=False).tolist()
            angles += angles[:1]
            ax.remove()
            ax = fig.add_subplot(111, polar=True)
            for i, s in enumerate(series_list):
                vals = list((s.get("values") or [])[: len(categories)])
                vals += vals[:1]
                ax.plot(angles, vals, color=palette[i % len(palette)], linewidth=2, label=s.get("name", ""))
                ax.fill(angles, vals, color=palette[i % len(palette)], alpha=0.1)
            ax.set_xticks(angles[:-1])
            ax.set_xticklabels(categories)

    if title:
        ax.set_title(title, fontsize=theme["title_size"], fontweight="bold", pad=12)
    if x_label:
        ax.set_xlabel(x_label)
    if y_label:
        ax.set_ylabel(y_label)
    if not theme.get("spine_visible", True) and chart_type not in ("pie", "radar"):
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
    if chart_type not in ("pie", "radar", "heatmap"):
        ax.grid(True, alpha=theme.get("grid_alpha", 0.2))
    has_multi = len(series_list) > 1
    if show_legend is True or (show_legend is None and has_multi):
        ax.legend(framealpha=0.9, edgecolor="none")

    plt.tight_layout()
    plt.savefig(
        str(output_path),
        format=output_format,
        dpi=theme["dpi"],
        bbox_inches="tight",
        facecolor=fig.get_facecolor(),
    )
    plt.close()

    return {
        "success": True,
        "output_path": str(output_path),
        "file_size_bytes": output_path.stat().st_size,
        "chart_type": chart_type,
        "format": output_format,
        "theme": theme_name,
        "message": "ok",
    }
