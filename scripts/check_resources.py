#!/usr/bin/env python3
"""Validate complete EN/RU resources and matching Android formatting arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BASES = (
    "mothlight-compose/src/commonMain/composeResources",
    "sample-desktop/src/main/composeResources",
    "sample-android/src/main/res",
)

def read(path):
    result = {}
    for item in ET.parse(path).getroot():
        name = item.attrib["name"]
        if name in result:
            raise ValueError("Duplicate resource: " + name)
        text = "".join(item.itertext()).strip()
        if not text:
            raise ValueError("Empty resource: " + name)
        result[name] = text
    return result

for base in BASES:
    english = read(ROOT / base / "values/strings.xml")
    russian = read(ROOT / base / "values-ru/strings.xml")
    if english.keys() != russian.keys():
        raise ValueError("Locale key mismatch: " + base)
    for key in english:
        pattern = r"%[0-9]+\$[a-z]"
        if sorted(re.findall(pattern, english[key])) != sorted(re.findall(pattern, russian[key])):
            raise ValueError("Format mismatch: " + base + ": " + key)
    print(base + ": " + str(len(english)) + " EN/RU strings OK")
