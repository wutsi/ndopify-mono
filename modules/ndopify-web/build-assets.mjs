import { mkdir, readFile, writeFile } from "node:fs/promises";
import { transform } from "esbuild";

const CSS = ["styles", "listing", "proposal", "visit"];
const JS = ["main", "listing", "proposal", "visit"];
const out = process.argv[2] || "target/classes";

const read = (p) => readFile(p, "utf8");
const concat = async (dir, names) =>
  (await Promise.all(names.map((n) => read(`src/main/assets/${dir}/${n}.${dir}`))))
    .join("\n");

const min = async (code, loader) =>
  (await transform(code, { loader, minify: true, legalComments: "none" })).code;

await mkdir(`${out}/public/assets/dist`, { recursive: true });
await writeFile(`${out}/public/assets/dist/app.min.css`, await min(await concat("css", CSS), "css"));
await writeFile(`${out}/public/assets/dist/app.min.js`, await min(await concat("js", JS), "js"));
