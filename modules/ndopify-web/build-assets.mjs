import { createHash } from "node:crypto";
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

// The worker's VERSION follows the two precached files: an edited offline page or logo ships a new cache.
const offline = await readFile("src/main/resources/public/offline.html");
const logo = await readFile("src/main/resources/public/assets/images/logo.png");
const version = createHash("sha256").update(offline).update(logo).digest("hex").slice(0, 12);
const sw = (await readFile("src/main/assets/sw.js", "utf8")).replace("__VERSION__", version);
await writeFile(`${out}/public/sw.js`, sw);
