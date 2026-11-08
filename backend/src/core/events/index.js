'use strict';

const { EventEmitter } = require('events');

class MeEventBus extends EventEmitter {}

const eventBus = new MeEventBus();
eventBus.setMaxListeners(50);

module.exports = { eventBus, MeEventBus };
