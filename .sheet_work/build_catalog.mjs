import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const sourcePath = "C:/Users/jacar/Documents/Pokémon-home-OCR/Home-OCR/outputs/019ff2dc-e55d-7882-82fa-7d9527bf0fd4/Origin mark list (Shiny) - verificado.xlsx";
const speciesPath = path.resolve("../android/app/src/main/assets/pokemon_species_names.csv");
const catalogPath = path.resolve("../android/app/src/main/assets/checklist_catalog.csv");
const reportPath = path.resolve("catalog_report.json");

function parseCsvLine(line) {
  const cells = [];
  let value = "";
  let quoted = false;
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    if (char === '"') {
      if (quoted && line[index + 1] === '"') {
        value += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      cells.push(value);
      value = "";
    } else {
      value += char;
    }
  }
  cells.push(value);
  return cells;
}

function csv(value) {
  const text = String(value ?? "");
  return `"${text.replaceAll('"', '""')}"`;
}

function normalize(value) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, " ")
    .trim()
    .toLowerCase();
}

function nationalNumber(value) {
  const match = String(value ?? "").match(/\d+/);
  return match ? Number.parseInt(match[0], 10) : 0;
}

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(sourcePath));
const rows = workbook.worksheets.getItem("Sheet1").getRange("A1:V1287").values;
const speciesLines = (await fs.readFile(speciesPath, "utf8")).split(/\r?\n/);
const englishSpecies = new Map();
for (const line of speciesLines.slice(1)) {
  if (!line.trim()) continue;
  const cells = parseCsvLine(line);
  if (cells[1] === "9") englishSpecies.set(Number(cells[0]), cells[2]);
}

const targets = [
  { column: 3, label: "Consola Virtual", type: "ORIGIN_MARK" },
  { column: 4, label: "Sin marca (Gen 3-5)", type: "ORIGIN_MARK" },
  { column: 5, label: "Kalos", type: "ORIGIN_MARK" },
  { column: 6, label: "Alola", type: "ORIGIN_MARK" },
  { column: 7, label: "Pokémon GO", type: "ORIGIN_MARK" },
  { column: 8, label: "Let's Go", type: "ORIGIN_MARK" },
  { column: 9, label: "Galar", type: "ORIGIN_MARK" },
  { column: 10, label: "Sinnoh (BDSP)", type: "ORIGIN_MARK" },
  { column: 11, label: "Hisui (Legends: Arceus)", type: "ORIGIN_MARK" },
  { column: 12, label: "Paldea", type: "ORIGIN_MARK" },
  { column: 13, label: "Legends: Z-A", type: "ORIGIN_MARK" },
  { column: 14, label: "Dream Ball (V)", type: "DREAM_BALL" },
  { column: 15, label: "Colo/XD", type: "COLO_XD" },
];

const catalog = [];
const rowsByNumber = new Map();
const unmatchedNames = [];
let currentNumber = 0;
for (let index = 4; index < rows.length; index += 1) {
  const row = rows[index];
  const sourceRow = index + 1;
  const explicitNumber = nationalNumber(row[0]);
  if (explicitNumber) currentNumber = explicitNumber;
  const number = currentNumber;
  const pokemon = String(row[2] ?? "").trim();
  if (!number || !pokemon) continue;
  const baseSpecies = englishSpecies.get(number) ?? pokemon;
  const normalizedPokemon = normalize(pokemon);
  const normalizedBase = normalize(baseSpecies);
  let form = "Estándar";
  if (normalizedPokemon !== normalizedBase) {
    if (normalizedPokemon.startsWith(`${normalizedBase} `)) {
      const baseWords = normalizedBase.split(" ").length;
      form = pokemon.split(/\s+/).slice(baseWords).join(" ");
    } else {
      form = pokemon;
      unmatchedNames.push({ row: sourceRow, number, pokemon, baseSpecies });
    }
  }

  const rowInfo = { row: sourceRow, number, pokemon, form };
  if (!rowsByNumber.has(number)) rowsByNumber.set(number, []);
  rowsByNumber.get(number).push(rowInfo);

  catalog.push({
    id: `${number}:${sourceRow}:LIVING_DEX`,
    nationalNumber: number,
    pokemon,
    form,
    originMark: "Living Dex",
    targetType: "LIVING_DEX",
    ownedInitial: row.slice(3, 16).some((state) => state === true),
    sourceRow,
  });

  for (const target of targets) {
    const state = row[target.column];
    if (typeof state !== "boolean") continue;
    catalog.push({
      id: `${number}:${sourceRow}:${target.type}:${normalize(target.label).replaceAll(" ", "-")}`,
      nationalNumber: number,
      pokemon,
      form,
      originMark: target.label,
      targetType: target.type,
      ownedInitial: state,
      sourceRow,
    });
  }
}

const output = [
  ["id", "national_number", "pokemon", "form", "origin_mark", "target_type", "owned_initial", "source_row"],
  ...catalog.map((entry) => [
    entry.id,
    entry.nationalNumber,
    entry.pokemon,
    entry.form,
    entry.originMark,
    entry.targetType,
    entry.ownedInitial ? "1" : "0",
    entry.sourceRow,
  ]),
].map((row) => row.map(csv).join(",")).join("\r\n") + "\r\n";
await fs.writeFile(catalogPath, `\uFEFF${output}`, "utf8");

const numbers = [...rowsByNumber.keys()].sort((a, b) => a - b);
const missingNumbers = [];
for (let number = 1; number <= 1025; number += 1) {
  if (!rowsByNumber.has(number)) missingNumbers.push(number);
}
const reportLabels = ["Living Dex", ...targets.map((target) => target.label)];
const countByTarget = Object.fromEntries(reportLabels.map((label) => [label, 0]));
const ownedByTarget = Object.fromEntries(reportLabels.map((label) => [label, 0]));
for (const entry of catalog) {
  countByTarget[entry.originMark] += 1;
  if (entry.ownedInitial) ownedByTarget[entry.originMark] += 1;
}

const report = {
  worksheetRows: rows.length,
  pokemonRows: [...rowsByNumber.values()].reduce((sum, value) => sum + value.length, 0),
  distinctNationalNumbers: numbers.length,
  firstNumber: numbers[0],
  lastNumber: numbers.at(-1),
  missingNumbers,
  catalogEntries: catalog.length,
  ownedInitial: catalog.filter((entry) => entry.ownedInitial).length,
  unownedInitial: catalog.filter((entry) => !entry.ownedInitial).length,
  countByTarget,
  ownedByTarget,
  unmatchedNames,
  worksheetCorrections: {},
};
await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
console.log(JSON.stringify(report, null, 2));
