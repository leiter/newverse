// Silence Node.js built-in module warnings from browser-incompatible deps
config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...config.resolve.fallback,
    "os": false,
    "path": false,
    "fs": false,
    "crypto": false
};
