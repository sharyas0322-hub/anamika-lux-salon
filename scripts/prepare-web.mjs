import { access, copyFile, mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const webDir = path.join(projectRoot, "www");

// Keep the complete existing site experience inside the Android app.
// The original website files remain untouched in the repository root.
const siteFiles = [
  "index.html",
  "booking.html",
  "app.html",
  "admin.html",
  "sw.js",
  "app-icon.svg",
  "the-anamika-lux-logo.png"
];

await rm(webDir, { recursive: true, force: true });
await mkdir(webDir, { recursive: true });

for (const file of siteFiles) {
  const source = path.join(projectRoot, file);
  try {
    await access(source);
  } catch {
    throw new Error(`Required website file is missing: ${file}`);
  }
  await copyFile(source, path.join(webDir, file));
}

console.log(`Prepared ${siteFiles.length} website files in www/ for Capacitor.`);
