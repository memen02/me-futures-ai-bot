'use strict';

const fs = require('fs');
const path = require('path');

const VALID_LOG_LEVELS = ['debug', 'info', 'warn', 'error'];
const VALID_LOW_RAM_MODES = ['auto', 'true', 'false'];
const VALID_BOT_MODES = ['paper', 'testnet', 'live'];

function loadEnvFile(envPath, target = process.env) {
  if (!fs.existsSync(envPath)) return { loaded: false, path: envPath };
  const raw = fs.readFileSync(envPath, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex === -1) continue;
    const key = trimmed.slice(0, eqIndex).trim();
    let value = trimmed.slice(eqIndex + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (!(key in target)) target[key] = value;
  }
  return { loaded: true, path: envPath };
}

function toInt(value, defaultValue) {
  if (value === undefined || value === '') return defaultValue;
  const n = Number.parseInt(value, 10);
  return Number.isFinite(n) ? n : defaultValue;
}

function validateBotMode(botMode) {
  if (!VALID_BOT_MODES.includes(botMode)) {
    throw new Error(`BOT_MODE tidak valid: "${botMode}". Pilihan yang didukung: ${VALID_BOT_MODES.join(', ')}.`);
  }
  if (botMode === 'live') {
    throw new Error(
      'BOT_MODE=live belum diaktifkan di build ini. LIVE MODE terkunci sampai seluruh checklist ' +
      'Fase 12 (audit, backtest, paper trading, testnet, validasi TP/SL, anti-duplicate, risk ' +
      'management, recovery) dinyatakan lulus - lihat docs/TEST_PLAN.md. Gunakan BOT_MODE=paper ' +
      'atau BOT_MODE=testnet untuk saat ini.'
    );
  }
  return botMode;
}

function loadConfig(opts = {}) {
  const env = opts.env || process.env;
  const envPath = opts.envPath || path.resolve(__dirname, '..', '..', '..', '.env');
  if (!opts.env) loadEnvFile(envPath, env);

  const nodeEnv = env.NODE_ENV || 'development';
  const logLevel = VALID_LOG_LEVELS.includes(env.LOG_LEVEL) ? env.LOG_LEVEL : 'info';
  const lowRamModeRaw = (env.LOW_RAM_MODE || 'auto').toLowerCase();
  const lowRamMode = VALID_LOW_RAM_MODES.includes(lowRamModeRaw) ? lowRamModeRaw : 'auto';
  const botMode = validateBotMode((env.BOT_MODE || 'paper').toLowerCase());
  const appPort = toInt(env.APP_PORT, 8787);

  return Object.freeze({
    nodeEnv,
    logLevel,
    lowRamMode,
    botMode,
    binance: Object.freeze({
      hasApiKey: Boolean(env.BINANCE_API_KEY),
      hasApiSecret: Boolean(env.BINANCE_API_SECRET),
    }),
    app: Object.freeze({
      port: appPort,
      hasLocalAuthToken: Boolean(env.APP_LOCAL_AUTH_TOKEN),
    }),
  });
}

function describeConfigForLog(config) {
  return {
    nodeEnv: config.nodeEnv,
    logLevel: config.logLevel,
    lowRamMode: config.lowRamMode,
    botMode: config.botMode,
    binanceApiKeyConfigured: config.binance.hasApiKey,
    binanceApiSecretConfigured: config.binance.hasApiSecret,
    appPort: config.app.port,
  };
}

module.exports = { loadEnvFile, loadConfig, describeConfigForLog };
