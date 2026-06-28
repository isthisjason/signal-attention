import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          // Charts change less often than workbench code and dominate the previous single bundle.
          if (["/recharts/", "/victory-vendor/", "/react-smooth/", "/d3-"].some((name) => id.includes(name))) {
            return "charts";
          }
          if (["/react/", "/react-dom/", "/scheduler/"].some((name) => id.includes(name))) {
            return "react";
          }
          return "vendor";
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
  },
});
