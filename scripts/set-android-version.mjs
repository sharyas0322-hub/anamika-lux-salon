import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const packageJson = JSON.parse(await readFile(path.join(root, "package.json"), "utf8"));
const version = packageJson.version;
const versionCode = Number(packageJson.androidVersionCode);
if (!/^\\d+\\.\\d+\\.\\d+$/.test(version) || !Number.isInteger(versionCode) || versionCode < 1) {
  throw new Error("package.json must define a semantic version and positive androidVersionCode.");
}
const gradlePath = path.join(root, "android", "app", "build.gradle");
let gradle = await readFile(gradlePath, "utf8");
if (!/versionCode\\s+\\d+/.test(gradle) || !/versionName\\s+["'][^"']+["']/.test(gradle)) {
  throw new Error("Could not find Android versionCode/versionName in app/build.gradle.");
}
gradle = gradle.replace(/versionCode\\s+\\d+/, `versionCode ${versionCode}`);
gradle = gradle.replace(/versionName\\s+["'][^"']+["']/, `versionName "${version}"`);
await writeFile(gradlePath, gradle);
console.log(`Android version set to ${version} (code ${versionCode}).`);
