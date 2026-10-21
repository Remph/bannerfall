import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build, type InlineConfig, type Plugin, type Rollup } from 'vite';
import { defineConfig } from 'vitest/config';

const serviceWorkerEntry = fileURLToPath(new URL('./src/push/serviceWorker.ts', import.meta.url));

/** The push service worker (1.5) as one script without imports: `/sw.js`. */
function serviceWorkerBuild(outDir: string, write: boolean): InlineConfig {
  return {
    configFile: false,
    logLevel: 'warn',
    publicDir: false,
    build: {
      outDir,
      write,
      emptyOutDir: false,
      copyPublicDir: false,
      lib: { entry: serviceWorkerEntry, formats: ['iife'], name: 'bannerfallServiceWorker', fileName: () => 'sw.js' },
    },
  };
}

/**
 * Builds the service worker next to the app (`dist/sw.js`) and serves it as `/sw.js` in development.
 * It is a build of its own because a worker can't share the page's chunks, and its scope is the
 * site's root only from there.
 */
function serviceWorker(): Plugin {
  let outDir = 'dist';
  let building = false;
  return {
    name: 'bannerfall-service-worker',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir);
      building = config.command === 'build';
    },
    async closeBundle() {
      if (building) {
        await build(serviceWorkerBuild(outDir, true));
      }
    },
    configureServer(server) {
      server.middlewares.use('/sw.js', (_request, response, next) => {
        build(serviceWorkerBuild(outDir, false))
          .then((result) => {
            const output = (Array.isArray(result) ? result[0] : result) as Rollup.RollupOutput;
            response.setHeader('Content-Type', 'text/javascript');
            response.end(output.output[0].code);
          })
          .catch(next);
      });
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), serviceWorker()],
  server: {
    // In development the browser talks to Vite (:5173); Vite forwards API and WebSocket calls to Spring (:8080).
    proxy: {
      '/api': 'http://localhost:8080',
      // the login through an account server (1.5): Spring's OAuth2 login, not pages of this app
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  test: {
    environment: 'node',
    // Vitest hands tests empty CSS; these two are read as text by src/theme/palettes.test.ts (1.6)
    css: { include: [/styles\/(tokens|palettes)\.css/] },
  },
});
