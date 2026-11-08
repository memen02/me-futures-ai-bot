'use strict';

const { loadConfig, describeConfigForLog } = require('./core/config');
const { createLogger } = require('./core/logger');
const { eventBus } = require('./core/events');

function main() {
  const isSelftest = process.argv.includes('--selftest');
  const config = loadConfig();
  const logger = createLogger('bootstrap', { level: config.logLevel });

  logger.info('ME FUTURES AI BOT - backend starting (Fase 1: Project Foundation)');
  logger.info(describeConfigForLog(config), 'Konfigurasi dimuat');
  logger.warn('Fase 1: fondasi saja. Belum ada koneksi Binance atau logika trading aktif di build ini.');

  eventBus.once('app:ready', () => logger.debug('event bus terbukti aktif (app:ready diterima)'));
  eventBus.emit('app:ready', { phase: 1 });

  if (isSelftest) {
    logger.info('Selftest selesai, semua komponen inti berhasil dimuat. Keluar dengan exit code 0.');
    process.exit(0);
    return;
  }

  function heartbeat() {
    const mem = process.memoryUsage();
    logger.info({ rssMB: Math.round(mem.rss / 1024 / 1024), heapUsedMB: Math.round(mem.heapUsed / 1024 / 1024) }, 'heartbeat');
  }
  const heartbeatTimer = setInterval(heartbeat, 60_000);
  heartbeatTimer.unref();

  function shutdown(signal) {
    logger.info(`${signal} diterima, berhenti dengan rapi...`);
    process.exit(0);
  }
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  logger.info('Bot berjalan dan menunggu sinyal berhenti (Ctrl+C). Tidak ada aksi trading di Fase 1.');
}

main();
