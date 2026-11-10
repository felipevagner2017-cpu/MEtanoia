import fs from "node:fs";

const web = fs.readFileSync("web/index.html", "utf8");
const androidPath = "app/src/main/java/br/com/metanoia/LifeDimensionPages.kt";
const android = fs.existsSync(androidPath) ? fs.readFileSync(androidPath, "utf8") : null;
const prompt = fs.readFileSync("worker/index.ts", "utf8");

for (const [label, source] of [["Web", web], ...(android ? [["Android", android]] : [])]) {
  for (const phrase of ["Atos 2:37", "John Wesley", "Billy Graham", "John Piper"]) {
    if (!source.includes(phrase)) throw new Error(label + " pastoral study is missing: " + phrase);
  }
}
for (const url of [
  "wesley.nnu.edu/john-wesley",
  "search.worldcat.org/title/The-Holy-Spirit",
  "desiringgod.org/books/let-the-nations-be-glad",
]) {
  if (!web.includes(url) && !(android || "").includes(url)) throw new Error("Bibliographic source is missing: " + url);
}
for (const phrase of [
  "Bíblia como fundamento e autoridade central",
  "John Wesley, Billy Graham e John Piper",
  "nunca invente frases, páginas ou referências",
  "referências bibliográficas verificáveis",
]) {
  if (!prompt.includes(phrase)) throw new Error("Contextual AI pastoral instruction is missing: " + phrase);
}
console.log("Pastoral study checks passed: Scripture-first structure, Wesley/Graham/Piper, verifiable bibliography and AI source-integrity rules.");
