// The installed Nest TypeORM adapter is ESM; the application and Jest tests use CommonJS.
// Convert module syntax without mocking its dependency-injection or database behavior.
const { transformSync } = require("esbuild");
const { pathToFileURL } = require("node:url");

module.exports = {
  process(source, filename) {
    return transformSync(source, {
      format: "cjs",
      target: "node22",
      sourcefile: filename,
      sourcemap: "inline",
      define: {
        "import.meta.url": JSON.stringify(pathToFileURL(filename).href),
      },
    });
  },
};
