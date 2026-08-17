import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const sourcePath = "C:/Users/jacar/Downloads/Origin mark list (Shiny).xlsx";
const outputDir = "C:/Users/jacar/Documents/Pokémon-home-OCR/Home-OCR/outputs/019ff2dc-e55d-7882-82fa-7d9527bf0fd4";
const outputPath = path.join(outputDir, "Origin mark list (Shiny) - verificado.xlsx");
const sourceUrl = "https://bulbapedia.bulbagarden.net/wiki/List_of_unobtainable_Shiny_Pok%C3%A9mon";

await fs.mkdir(outputDir, { recursive: true });
const input = await FileBlob.load(sourcePath);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheet = workbook.worksheets.getItem("Sheet1");

const before = await workbook.inspect({
  kind: "region,formula",
  sheetId: "Sheet1",
  range: "A1:V42",
  maxChars: 12000,
  tableMaxRows: 42,
  tableMaxCols: 22,
  options: { maxResults: 120 },
});
console.log("BEFORE");
console.log(before.ndjson);

const corrections = [
  { cell: "C7", pokemon: "Venusaur", value: "Venusaur", reason: "Corrección ortográfica del nombre." },
  { cell: "C613", pokemon: "Simipour", value: "Simipour", reason: "Corrección ortográfica del nombre." },
  { cell: "A916", pokemon: "Cutiefly", value: "#742", reason: "Corrección del número de la Pokédex Nacional." },
  { cell: "C1129", pokemon: "Arctozolt", value: "Arctozolt", reason: "Corrección ortográfica del nombre." },
  { cell: "C1165", pokemon: "Quaxly", value: "Quaxly", reason: "Corrección ortográfica del nombre." },
  { cell: "C1280", pokemon: "Archaludon", value: "Archaludon", reason: "Corrección ortográfica del nombre." },

  { cell: "E332", pokemon: "Celebi", value: null, reason: "Gen III, IV y V figuran como ✘; el shiny indicado procede de Gen II/Consola Virtual." },
  { cell: "L589", pokemon: "Arceus", value: null, reason: "En Gen VIII la obtención shiny indicada es BDSP, no Legends: Arceus." },
  { cell: "N589", pokemon: "Arceus", value: null, reason: "Gen IX figura como transferencia (~), no como origen de Legends: Z-A." },
  { cell: "H590", pokemon: "Victini", value: null, reason: "Victini continúa shiny-locked; no hay obtención shiny en GO indicada por la fuente." },
  { cell: "H766", pokemon: "Keldeo", value: false, reason: "La nota 16 confirma una distribución shiny en Pokémon GO." },
  { cell: "M767", pokemon: "Meloetta", value: false, reason: "La nota 17 confirma el regalo de HOME por completar las Pokédex de Scarlet/Violet, con origen Paldea." },
  { cell: "F788", pokemon: "Vivillon Fancy", value: null, reason: "El patrón Fancy figura como ✘ en Gen VI; su obtención shiny actual es de Scarlet/Violet." },
  { cell: "G788", pokemon: "Vivillon Fancy", value: null, reason: "El patrón Fancy figura como ✘ en Gen VII; su obtención shiny actual es de Scarlet/Violet." },
  { cell: "H888", pokemon: "Zygarde", value: null, reason: "La fuente no indica una obtención shiny en GO; Gen IX solo admite transferencia (~)." },
  { cell: "H890", pokemon: "Hoopa", value: null, reason: "Hoopa continúa shiny-locked; no hay obtención shiny en GO indicada por la fuente." },
  { cell: "N891", pokemon: "Volcanion", value: false, reason: "La nota 20 confirma el regalo shiny de HOME con Legends: Z-A como origen (27-04-2026)." },
  { cell: "H971", pokemon: "Cosmog", value: null, reason: "Cosmog figura como ✘ en las generaciones VII, VIII y IX." },
  { cell: "H972", pokemon: "Cosmoem", value: null, reason: "Cosmoem figura como ✘ en las generaciones VII, VIII y IX." },
  { cell: "H985", pokemon: "Marshadow", value: null, reason: "Marshadow figura como ✘ en las generaciones VII, VIII y IX." },
  { cell: "I991", pokemon: "Meltan", value: false, reason: "La nota 21 confirma el regalo shiny de HOME por completar la Pokédex de LGPE." },
  { cell: "H1139", pokemon: "Kubfu", value: null, reason: "Kubfu figura como ✘ en las generaciones VIII y IX." },
  { cell: "H1140", pokemon: "Urshifu Single Strike", value: null, reason: "Urshifu figura como ✘ en las generaciones VIII y IX." },
  { cell: "H1141", pokemon: "Urshifu Rapid Strike", value: null, reason: "Urshifu figura como ✘ en las generaciones VIII y IX." },
  { cell: "H1142", pokemon: "Zarude", value: null, reason: "Zarude figura como ✘ en las generaciones VIII y IX." },
  { cell: "H1157", pokemon: "Enamorus", value: null, reason: "La fuente solo confirma el regalo shiny de HOME con origen Hisui, no GO." },
  { cell: "P1152", pokemon: "Ursaluna Bloodmoon", value: null, reason: "La forma Bloodmoon no puede proceder de Colosseum/XD y además figura como ✘ en Gen IX." },
  { cell: "M1271", pokemon: "Walking Wake", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1272", pokemon: "Iron Leaves", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1276", pokemon: "Okidogi", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1277", pokemon: "Munkidori", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1278", pokemon: "Fezandipiti", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1279", pokemon: "Ogerpon", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1282", pokemon: "Gouging Fire", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1283", pokemon: "Raging Bolt", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1284", pokemon: "Iron Boulder", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1285", pokemon: "Iron Crown", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1286", pokemon: "Terapagos", value: null, reason: "Figura como ✘ en Gen IX." },
  { cell: "M1287", pokemon: "Pecharunt", value: null, reason: "Figura como ✘ en Gen IX." },
];

function displayValue(value) {
  if (value === null || value === undefined || value === "") return "Vacío";
  if (value === true) return "TRUE";
  if (value === false) return "FALSE";
  return String(value);
}

const auditRows = [];
for (const correction of corrections) {
  const target = sheet.getRange(correction.cell);
  const oldValue = target.values?.[0]?.[0] ?? null;
  target.values = [[correction.value]];
  auditRows.push([
    correction.cell,
    correction.pokemon,
    correction.reason.startsWith("Corrección") ? "Dato" : "Disponibilidad",
    displayValue(oldValue),
    displayValue(correction.value),
    correction.reason,
    "Bulbapedia",
  ]);
}

// Rebuild the summary so it covers every origin mark, both bonuses, and all rows.
sheet.getRange("Q5:T32").unmerge();
sheet.getRange("Q5:T32").values = Array.from({ length: 28 }, () => [null, null, null, null]);
sheet.getRange("U4:V32").values = Array.from({ length: 29 }, () => [null, null]);

sheet.getRange("Q5:T5").values = [["Marca de origen", "Conseguidos", "Posibles", "Completado"]];
const originColumns = [
  ["Gameboy", "D"],
  ["Sin marca (Gen 3-5)", "E"],
  ["Pentágono / Kalos", "F"],
  ["Trébol / Alola", "G"],
  ["Pokémon GO", "H"],
  ["Let's Go", "I"],
  ["Galar", "J"],
  ["Sinnoh (BDSP)", "K"],
  ["Hisui (Legends: Arceus)", "L"],
  ["Paldea", "M"],
  ["Legends: Z-A", "N"],
];
originColumns.forEach(([label, column], index) => {
  const row = 6 + index;
  sheet.getRange(`Q${row}`).values = [[label]];
  sheet.getRange(`R${row}`).formulas = [[`=SUMPRODUCT(--(${column}5:${column}1287=TRUE))`]];
  sheet.getRange(`S${row}`).formulas = [[`=COUNTA(${column}5:${column}1287)`]];
  sheet.getRange(`T${row}`).formulas = [[`=IFERROR(R${row}/S${row},0)`]];
});
sheet.getRange("Q17").values = [["Total marcas de origen"]];
sheet.getRange("R17").formulas = [["=SUM(R6:R16)"]];
sheet.getRange("S17").formulas = [["=SUM(S6:S16)"]];
sheet.getRange("T17").formulas = [["=IFERROR(R17/S17,0)"]];

sheet.getRange("Q19:T19").values = [["Bonus", "Conseguidos", "Posibles", "Completado"]];
sheet.getRange("Q20").values = [["Colosseum / XD"]];
sheet.getRange("R20").formulas = [["=SUMPRODUCT(--(P5:P1287=TRUE))"]];
sheet.getRange("S20").formulas = [["=COUNTA(P5:P1287)"]];
sheet.getRange("T20").formulas = [["=IFERROR(R20/S20,0)"]];
sheet.getRange("Q21").values = [["Dream Ball (Gen V)"]];
sheet.getRange("R21").formulas = [["=SUMPRODUCT(--(O5:O1287=TRUE))"]];
sheet.getRange("S21").formulas = [["=COUNTA(O5:O1287)"]];
sheet.getRange("T21").formulas = [["=IFERROR(R21/S21,0)"]];
sheet.getRange("Q22").values = [["Total general"]];
sheet.getRange("R22").formulas = [["=R17+R20+R21"]];
sheet.getRange("S22").formulas = [["=S17+S20+S21"]];
sheet.getRange("T22").formulas = [["=IFERROR(R22/S22,0)"]];

sheet.getRange("T2").values = [["Completado"]];
sheet.getRange("T3").formulas = [["=IFERROR(R22/S22,0)"]];
sheet.getRange("U2:V2").values = [["Total conseguido", "Total pendiente"]];
sheet.getRange("U3").formulas = [["=R22"]];
sheet.getRange("V3").formulas = [["=S22-R22"]];

sheet.getRange("Q5:T5").format = {
  fill: "#17324D",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
};
sheet.getRange("Q6:T16").format = {
  fill: "#E8F3EE",
  font: { color: "#20313F" },
};
sheet.getRange("Q17:T17").format = {
  fill: "#B8DED1",
  font: { bold: true, color: "#17324D" },
};
sheet.getRange("Q19:T19").format = {
  fill: "#F0C84B",
  font: { bold: true, color: "#17324D" },
  horizontalAlignment: "center",
};
sheet.getRange("Q20:T21").format = {
  fill: "#FFF7D6",
  font: { color: "#20313F" },
};
sheet.getRange("Q22:T22").format = {
  fill: "#17324D",
  font: { bold: true, color: "#FFFFFF" },
};
sheet.getRange("Q23:T32").format = { fill: "#FFFFFF", font: { color: "#20313F" } };
sheet.getRange("R6:S22").format.numberFormat = "0";
sheet.getRange("T3:T22").format.numberFormat = "0.00%";
sheet.getRange("Q5:Q22").format.columnWidth = 24;
sheet.getRange("R5:S22").format.columnWidth = 12;
sheet.getRange("T2:T22").format.columnWidth = 14;
sheet.getRange("U2:V3").format.columnWidth = 16;

const audit = workbook.worksheets.add("Verificación");
audit.getRange("A1:G1").merge();
audit.getRange("A1").values = [["Verificación del catálogo shiny"]];
audit.getRange("A2:G2").merge();
audit.getRange("A2").values = [["Actualizado el 11 de agosto de 2026. Se preservaron las casillas de propiedad salvo combinaciones imposibles confirmadas por la fuente."]];
audit.getRange("A3:G3").merge();
audit.getRange("A3").values = [[sourceUrl]];
audit.getRange("A5:G5").merge();
audit.getRange("A5").values = [["Criterio: ✔ = obtención directa; ~ = evento o transferencia; ✘ = imposible en esa generación. La tabla de Bulbapedia se declara no exhaustiva, por lo que se conservaron combinaciones legales no contradichas por ella."]];
audit.getRange("A7:G7").values = [["Celda", "Pokémon", "Tipo", "Antes", "Después", "Motivo", "Fuente"]];
audit.getRange(`A8:G${7 + auditRows.length}`).values = auditRows;

audit.getRange("A1:G1").format = {
  fill: "#17324D",
  font: { bold: true, color: "#FFFFFF", size: 18 },
  horizontalAlignment: "left",
  rowHeight: 32,
};
audit.getRange("A2:G3").format = {
  fill: "#E8F3EE",
  font: { color: "#20313F" },
  wrapText: true,
};
audit.getRange("A5:G5").format = {
  fill: "#FFF7D6",
  font: { italic: true, color: "#20313F" },
  wrapText: true,
  rowHeight: 44,
};
audit.getRange("A7:G7").format = {
  fill: "#2F806F",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
};
audit.getRange(`A8:G${7 + auditRows.length}`).format = {
  font: { color: "#20313F" },
  wrapText: true,
  verticalAlignment: "top",
};
audit.getRange("A1:A48").format.columnWidth = 10;
audit.getRange("B1:B48").format.columnWidth = 22;
audit.getRange("C1:C48").format.columnWidth = 15;
audit.getRange("D1:E48").format.columnWidth = 12;
audit.getRange("F1:F48").format.columnWidth = 54;
audit.getRange("G1:G48").format.columnWidth = 36;
audit.freezePanes.freezeRows(7);

const after = await workbook.inspect({
  kind: "region,formula",
  sheetId: "Sheet1",
  range: "Q2:V22",
  maxChars: 12000,
  tableMaxRows: 24,
  tableMaxCols: 8,
  options: { maxResults: 160 },
});
console.log("AFTER SUMMARY");
console.log(after.ndjson);

const corrected = await workbook.inspect({
  kind: "region",
  sheetId: "Verificación",
  range: `A1:G${7 + auditRows.length}`,
  maxChars: 18000,
  tableMaxRows: 50,
  tableMaxCols: 7,
});
console.log("AUDIT");
console.log(corrected.ndjson);

const formulas = await workbook.inspect({
  kind: "formula",
  sheetId: "Sheet1",
  range: "A1:V1287",
  maxChars: 24000,
  options: { maxResults: 500 },
});
const formulaText = formulas.ndjson ?? "";
const formulaErrors = ["#REF!", "#DIV/0!", "#VALUE!", "#NAME?", "#N/A"].filter((token) => formulaText.includes(token));
if (formulaErrors.length) throw new Error(`Formula errors remain: ${formulaErrors.join(", ")}`);

const previews = [
  ["Sheet1", "A1:V42", "Sheet1-top.png"],
  ["Sheet1", "A1245:V1287", "Sheet1-bottom.png"],
  ["Verificación", `A1:G${7 + auditRows.length}`, "Verificacion.png"],
];
for (const [sheetName, range, filename] of previews) {
  const preview = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  await fs.writeFile(path.join(outputDir, filename), new Uint8Array(await preview.arrayBuffer()));
}

const exported = await SpreadsheetFile.exportXlsx(workbook);
await exported.save(outputPath);
console.log(JSON.stringify({ outputPath, corrections: corrections.length, auditRows: auditRows.length }, null, 2));
