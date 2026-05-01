'use strict';

const { MSG } = require('./constants');

const REQUIRED_FIELDS = {
  [MSG.PLAY]:           ['soundId', 'volume', 'handle'],
  [MSG.STOP]:           ['handle'],
  [MSG.STOP_ALL]:       [],
  [MSG.HELLO]:          ['version'],
  [MSG.HELLO_ACK]:      ['deviceName', 'version', 'sounds'],
  [MSG.LIBRARY_UPDATE]: ['sounds'],
  [MSG.STARTED]:        ['handle', 'soundId', 'soundName', 'durationMs'],
  [MSG.DONE]:           ['handle', 'soundName', 'reason'],
  [MSG.PING]:           [],
  [MSG.PONG]:           [],
  [MSG.ERROR]:          ['code', 'message'],
};

const MessageProtocol = {
  serialize(obj) {
    return JSON.stringify(obj);
  },

  deserialize(str) {
    let obj;
    try {
      obj = JSON.parse(str);
    } catch (e) {
      throw new Error(`Invalid JSON: ${e.message}`);
    }
    if (!obj || typeof obj.type !== 'string') {
      throw new Error('Message missing required field: type');
    }
    return obj;
  },

  validate(obj) {
    const errors = [];
    if (!obj || typeof obj.type !== 'string') {
      return { valid: false, errors: ['Missing required field: type'] };
    }
    const required = REQUIRED_FIELDS[obj.type];
    if (required === undefined) {
      errors.push(`Unknown message type: ${obj.type}`);
    } else {
      for (const field of required) {
        if (obj[field] === undefined || obj[field] === null) {
          errors.push(`Missing required field: ${field}`);
        }
      }
    }
    return { valid: errors.length === 0, errors };
  },
};

module.exports = MessageProtocol;
