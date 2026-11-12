const path = require("node:path");
const sharp = require("sharp");

const source = path.resolve("web/icons/metonoia.svg");
const outputs = [
  { size: 192, file: "web/icons/metonoia-192.png" },
  { size: 512, file: "web/icons/metonoia-512.png" },
];

Promise.all(outputs.map(({ size, file }) =>
  sharp(source, { density: 192 })
    .resize(size, size, { fit: "cover", position: "centre" })
    .png({ compressionLevel: 9, adaptiveFiltering: true })
    .toFile(path.resolve(file))
)).then(results => {
  for (let i = 0; i < results.length; i++) {
    if (results[i].width !== outputs[i].size || results[i].height !== outputs[i].size) {
      throw new Error("Dimensão incorreta no ícone: " + outputs[i].file);
    }
    process.stdout.write(outputs[i].file + ": " + results[i].width + "x" + results[i].height + "\n");
  }
}).catch(error => {
  process.stderr.write(String(error) + "\n");
  process.exitCode = 1;
});