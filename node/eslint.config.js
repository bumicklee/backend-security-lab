import js from "@eslint/js";
import globals from "globals";

export default [
  { ignores: ["node_modules/", "data/", "coverage/"] },
  js.configs.recommended,
  {
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: "module",
      globals: { ...globals.node },
    },
    rules: {
      "no-unused-vars": ["error", { argsIgnorePattern: "^(req|res|next|_)" }],
    },
  },
  {
    files: ["tests/**/*.js"],
    languageOptions: { globals: { ...globals.jest } },
  },
];
