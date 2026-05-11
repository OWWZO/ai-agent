import json
import os
from pathlib import Path


def main():
    arguments = json.loads(os.environ.get("SKILL_ARGUMENTS_JSON", "{}"))
    output_dir = Path("output")
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_file = output_dir / "summary.md"
    summary_file.write_text(
        "# Summary\n\n"
        f"- table: {arguments.get('table', 'unknown')}\n"
        f"- limit: {arguments.get('limit', 'n/a')}\n",
        encoding="utf-8",
    )
    print("summary generated")


if __name__ == "__main__":
    main()
