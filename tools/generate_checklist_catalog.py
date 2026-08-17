"""Build the compact Android checklist catalog from Home Checklist data."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from pathlib import Path


MARK_CODES = {
    "Sin marca": "NO_MARK",
    "GB": "GB",
    "P": "P",
    "USUM": "USUM",
    "LGPE": "LGPE",
    "SwSh": "SWSH",
    "LA": "LA",
    "BDSP": "BDSP",
    "SV": "SV",
    "LZA": "LZA",
}
MARK_ORDER = {code: index for index, code in enumerate((*MARK_CODES.values(), "GO"))}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("home_checklist", type=Path, help="Path to the Home Checklist project")
    parser.add_argument("output", type=Path, help="Destination checklist_catalog.csv")
    return parser.parse_args()


def load_entries(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)["entries"]


def id_part(value: str) -> str:
    if value == "!":
        return "exclamation"
    if value == "?":
        return "question"
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-") or "standard"


def add_variant(
    rows: dict[tuple[str, int, str, bool], dict],
    entry: dict,
    mark: str,
    shiny: bool,
) -> None:
    form = entry.get("form") or ""
    key = (mark, int(entry["dex"]), form.casefold(), shiny)
    rows.setdefault(
        key,
        {
            "id": f"{mark.lower()}:{int(entry['dex']):04d}:{id_part(form)}:"
            f"{'shiny' if shiny else 'normal'}",
            "national_number": int(entry["dex"]),
            "pokemon": entry["name"],
            "form": form,
            "mark": mark,
            "shiny": "1" if shiny else "0",
        },
    )


def build_catalog(home_checklist: Path) -> list[dict]:
    rows: dict[tuple[str, int, str, bool], dict] = {}
    base_entries = load_entries(home_checklist / "public" / "data" / "pokemon-lite.json")
    species_numbers: dict[str, int] = {}
    for entry in base_entries:
        if int(entry.get("dex") or 0) > 0:
            species_numbers.setdefault(entry["name"].casefold(), int(entry["dex"]))
    for entry in base_entries:
        mark = MARK_CODES.get(entry.get("mark"))
        if mark is None or entry.get("availability") == "excluded":
            continue
        if int(entry.get("dex") or 0) <= 0:
            resolved = species_numbers.get(entry["name"].casefold())
            if resolved is None:
                raise ValueError(f"Missing National Pokédex number for {entry['name']}")
            entry = {**entry, "dex": resolved}
        if entry.get("normalEligible", True):
            add_variant(rows, entry, mark, False)
        if entry.get("shinyEligible", False):
            add_variant(rows, entry, mark, True)

    special_entries = load_entries(
        home_checklist / "public" / "data" / "special-collections.json"
    )
    for entry in special_entries:
        if entry.get("collection") != "go" or entry.get("availability") == "excluded":
            continue
        add_variant(rows, entry, "GO", False)
        if entry.get("shinyEligible", False):
            add_variant(rows, entry, "GO", True)

    return sorted(
        rows.values(),
        key=lambda row: (
            MARK_ORDER[row["mark"]],
            row["national_number"],
            row["form"].casefold(),
            row["shiny"] == "1",
        ),
    )


def main() -> None:
    args = parse_args()
    rows = build_catalog(args.home_checklist)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=("id", "national_number", "pokemon", "form", "mark", "shiny"),
            lineterminator="\n",
            quoting=csv.QUOTE_ALL,
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} checklist entries to {args.output}")


if __name__ == "__main__":
    main()
