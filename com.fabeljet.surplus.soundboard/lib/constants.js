const DEFAULT_PORT = 8765;
const PING_INTERVAL_MS = 15000;
const PING_TIMEOUT_COUNT = 3;
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 30000;
const HELLO_TIMEOUT_MS = 5000;
const PROTOCOL_VERSION = '1.0';

const MSG = {
  PLAY: 'play',
  STOP: 'stop',
  STOP_ALL: 'stop_all',
  MUTE: 'mute',
  UNMUTE: 'unmute',
  HELLO: 'hello',
  HELLO_ACK: 'hello_ack',
  LIBRARY_UPDATE: 'library_update',
  STARTED: 'started',
  DONE: 'done',
  MUTE_STATE: 'mute_state',
  PING: 'ping',
  PONG: 'pong',
  ERROR: 'error',
};

const DONE_REASON = {
  COMPLETED: 'completed',
  STOPPED: 'stopped',
  CONNECTION_LOST: 'connection_lost',
};

const ERROR_CODE = {
  SOUND_NOT_FOUND: 'SOUND_NOT_FOUND',
  HANDLE_NOT_FOUND: 'HANDLE_NOT_FOUND',
  PLAYBACK_FAILED: 'PLAYBACK_FAILED',
  UNKNOWN_MESSAGE: 'UNKNOWN_MESSAGE',
};

module.exports = {
  DEFAULT_PORT,
  PING_INTERVAL_MS,
  PING_TIMEOUT_COUNT,
  RECONNECT_BASE_MS,
  RECONNECT_MAX_MS,
  HELLO_TIMEOUT_MS,
  PROTOCOL_VERSION,
  MSG,
  DONE_REASON,
  ERROR_CODE,
};