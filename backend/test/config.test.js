'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadConfig, describeConfigForLog } = require('../src/core/config');

test('mode paper (default): tidak butuh API key, default terisi dengan benar', () => {
  const cfg = loadConfig({ env: {} });
  assert.equal(cfg.botMode, 'paper');
  assert.equal(cfg.binance.hasApiKey, false);
  assert.equal(cfg.logLevel, 'info');
  assert.equal(cfg.lowRamMode, 'auto');
  assert.equal(cfg.app.port, 8787);
});

test('mode testnet dengan API key terisi: hasApiKey/hasApiSecret true', () => {
  const cfg = loadConfig({
    env: { BOT_MODE: 'testnet', BINANCE_API_KEY: 'dummy-key', BINANCE_API_SECRET: 'dummy-secret' },
  });
  assert.equal(cfg.botMode, 'testnet');
  assert.equal(cfg.binance.hasApiKey, true);
  assert.equal(cfg.binance.hasApiSecret, true);
});

test('BOT_MODE=live diblokir total di Fase 1 (LIVE MODE terkunci secara default)', () => {
  assert.throws(() => loadConfig({ env: { BOT_MODE: 'live' } }), /belum diaktifkan di build ini/);
});

test('BOT_MODE tidak dikenal harus ditolak (fail-safe, bukan ditebak)', () => {
  assert.throws(() => loadConfig({ env: { BOT_MODE: 'yolo' } }), /tidak valid/);
});

test('LOG_LEVEL tidak dikenal jatuh ke default "info" (bukan error keras)', () => {
  const cfg = loadConfig({ env: { LOG_LEVEL: 'super-verbose' } });
  assert.equal(cfg.logLevel, 'info');
});

test('config yang dikembalikan sudah dibekukan (Object.freeze)', () => {
  const cfg = loadConfig({ env: {} });
  assert.throws(() => { cfg.botMode = 'testnet'; }, TypeError);
});

test('describeConfigForLog tidak pernah membocorkan nilai API key/secret', () => {
  const cfg = loadConfig({ env: { BINANCE_API_KEY: 'super-secret-key', BINANCE_API_SECRET: 'super-secret-value' } });
  const described = JSON.stringify(describeConfigForLog(cfg));
  assert.ok(!described.includes('super-secret-key'));
  assert.ok(!described.includes('super-secret-value'));
});
