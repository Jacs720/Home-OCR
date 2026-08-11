import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "C:/Users/jacar/Downloads/Origin mark list (Shiny).xlsx";
const outputDir = "C:/Users/jacar/Documents/Pokémon-home-OCR/Home-OCR/.sheet_work/previews";

await fs.mkdir(outputDir, { recursive: true });
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

const overview = await workbook.inspect({
  kind: "workbook,sheet,table",
  maxChars: 30000,
  tableMaxRows: 12,
  tableMaxCols: 18,
  tableMaxCellChars: 120,
});
console.log("OVERVIEW");
console.log(overview.ndjson);

const sheets = workbook.worksheets.items;
for (const sheet of sheets) {
  const usedRange = sheet.getUsedRange(true);
  console.log(`SHEET ${sheet.name}`);
  if (usedRange) {
    const region = await workbook.inspect({
      kind: "region,formula,computedStyle,drawing",
      sheetId: sheet.name,
      range: usedRange.address,
      maxChars: 16000,
      tableMaxRows: 20,
      tableMaxCols: 24,
      options: { maxResults: 100 },
    });
    console.log(region.ndjson);
  }

  const safeName = sheet.name.replace(/[<>:"/\\|?*]/g, "_");
  const values = usedRange ? usedRange.values : [];
  await fs.writeFile(
    path.join(outputDir, `${safeName}.json`),
    JSON.stringify(values),
    "utf8",
  );

  const previewRanges = ["A1:V42", "A1245:V1287"];
  for (let index = 0; index < previewRanges.length; index += 1) {
    const preview = await workbook.render({
      sheetName: sheet.name,
      range: previewRanges[index],
      scale: 1,
      format: "png",
    });
    await fs.writeFile(
      path.join(outputDir, `${safeName}-${index + 1}.png`),
      new Uint8Array(await preview.arrayBuffer()),
    );
  }
}
