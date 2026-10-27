"""Generate the slim, attributed Ciqual 2025 migration from the official French XLSX.

Download: https://entrepot.recherche.data.gouv.fr/api/access/datafile/666260
Usage: python scripts/import-ciqual-2025.py .local/ciqual-2025.xlsx
Only unambiguous numeric values are imported; '<', '>', '-' and blanks stay NULL.
"""
import hashlib
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

EXPECTED_MD5 = "0d9758ce23f3f13dd63a005bc1bb4f2c"
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "services/nutrition-service/src/main/resources/db/migration/V2__ciqual_2025.sql"


def value(cell, strings):
    element = cell.find("m:v", NS)
    raw = "" if element is None else element.text or ""
    return strings[int(raw)] if raw and cell.get("t") == "s" else raw


def number(raw):
    raw = raw.strip().replace(",", ".")
    return raw if re.fullmatch(r"\d+(?:\.\d+)?", raw) else "NULL"


def quote(raw):
    return "'" + re.sub(r"\s+", " ", raw.strip()).replace("'", "''") + "'"


def main(source):
    raw = Path(source).read_bytes()
    digest = hashlib.md5(raw).hexdigest()
    if digest != EXPECTED_MD5:
        raise SystemExit(f"Unexpected source MD5: {digest}")
    with zipfile.ZipFile(source) as archive:
        strings = ["".join(node.itertext()) for node in ET.fromstring(archive.read("xl/sharedStrings.xml")).findall("m:si", NS)]
        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        rows = sheet.findall(".//m:sheetData/m:row", NS)
        result = []
        for row in rows[1:]:
            cells = {re.match(r"[A-Z]+", cell.get("r")).group(): value(cell, strings) for cell in row}
            code, name = cells.get("G", "").strip(), cells.get("H", "").strip()
            if not code or not name:
                continue
            result.append("(" + ",".join([quote(code), quote(name), number(cells.get("K", "")), number(cells.get("O", "")), number(cells.get("Q", "")), number(cells.get("R", ""))]) + ")")
    sql = [
        "-- Anses. 2025. Table de composition nutritionnelle des aliments Ciqual. DOI: 10.57745/RDMHWY",
        "-- Source: https://entrepot.recherche.data.gouv.fr/api/access/datafile/666260 (MD5 " + EXPECTED_MD5 + ")",
        "CREATE TABLE ciqual_food (code VARCHAR(20) PRIMARY KEY, name VARCHAR(300) NOT NULL, kcal_100g NUMERIC(12,3), protein_g_100g NUMERIC(12,3), carbohydrate_g_100g NUMERIC(12,3), fat_g_100g NUMERIC(12,3));",
        "CREATE INDEX ciqual_food_name_idx ON ciqual_food(name);",
    ]
    for start in range(0, len(result), 100):
        sql.append("INSERT INTO ciqual_food(code,name,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g) VALUES\n" + ",\n".join(result[start:start + 100]) + ";")
    OUT.write_text("\n".join(sql) + "\n", encoding="utf-8")
    print(f"Wrote {len(result)} foods to {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
