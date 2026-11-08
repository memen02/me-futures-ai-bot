'use strict';

const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };

function createLogger(moduleName, options = {}) {
  const minLevel = LEVELS[options.level] ?? LEVELS.info;
  function write(level, msgOrFields, maybeMsg) {
    if (LEVELS[level] < minLevel) return;
    let fields = {};
    let msg = '';
    if (typeof msgOrFields === 'string') {
      msg = msgOrFields;
    } else {
      fields = msgOrFields || {};
      msg = maybeMsg || '';
    }
    const entry = { time: new Date().toISOString(), level, module: moduleName, msg, ...fields };
    const line = JSON.stringify(entry);
    if (level === 'error' || level === 'warn') process.stderr.write(line + '\n');
    else process.stdout.write(line + '\n');
  }
  return {
    debug: (a, b) => write('debug', a, b),
    info: (a, b) => write('info', a, b),
    warn: (a, b) => write('warn', a, b),
    error: (a, b) => write('error', a, b),
    child: (childName) => createLogger(`${moduleName}:${childName}`, options),
  };
}

module.exports = { createLogger, LEVELS };
