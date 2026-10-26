module.exports = {
  preset: "ts-jest",
  transform: {
    "^.+\\.tsx?$": "ts-jest",
    "^.+\\.js$": "<rootDir>/test/esm-transformer.cjs",
  },
  transformIgnorePatterns: [
    "node_modules[/\\\\](?!@nestjs[/\\\\]typeorm[/\\\\]|typeorm[/\\\\])",
  ],
  testEnvironment: "node",
  testRegex: "src[/\\\\].*\\.spec\\.ts$",
  setupFiles: ["<rootDir>/test/setup-env.js"],
  clearMocks: true,
  restoreMocks: true,
  collectCoverageFrom: [
    "src/auth.service.ts",
    "src/auth.controller.ts",
    "src/session/session.service.ts",
    "src/refresh-token/refresh-token.service.ts",
  ],
  coverageReporters: ["text", "lcov", "json-summary"],
};
