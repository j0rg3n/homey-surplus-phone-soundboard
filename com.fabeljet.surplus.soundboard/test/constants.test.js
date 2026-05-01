const {
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
} = require('../lib/constants');

describe('constants', () => {
  describe('defaults', () => {
    test('DEFAULT_PORT is 8765', () => {
      expect(DEFAULT_PORT).toBe(8765);
    });

    test('PING_INTERVAL_MS is 15000', () => {
      expect(PING_INTERVAL_MS).toBe(15000);
    });

    test('PING_TIMEOUT_COUNT is 3', () => {
      expect(PING_TIMEOUT_COUNT).toBe(3);
    });

    test('RECONNECT_BASE_MS is 1000', () => {
      expect(RECONNECT_BASE_MS).toBe(1000);
    });

    test('RECONNECT_MAX_MS is 30000', () => {
      expect(RECONNECT_MAX_MS).toBe(30000);
    });

    test('HELLO_TIMEOUT_MS is 5000', () => {
      expect(HELLO_TIMEOUT_MS).toBe(5000);
    });

    test('PROTOCOL_VERSION is 1.0', () => {
      expect(PROTOCOL_VERSION).toBe('1.0');
    });
  });

  describe('MSG', () => {
    test('PLAY is play', () => {
      expect(MSG.PLAY).toBe('play');
    });

    test('STOP is stop', () => {
      expect(MSG.STOP).toBe('stop');
    });

    test('STOP_ALL is stop_all', () => {
      expect(MSG.STOP_ALL).toBe('stop_all');
    });

    test('HELLO is hello', () => {
      expect(MSG.HELLO).toBe('hello');
    });

    test('HELLO_ACK is hello_ack', () => {
      expect(MSG.HELLO_ACK).toBe('hello_ack');
    });

    test('LIBRARY_UPDATE is library_update', () => {
      expect(MSG.LIBRARY_UPDATE).toBe('library_update');
    });

    test('STARTED is started', () => {
      expect(MSG.STARTED).toBe('started');
    });

    test('DONE is done', () => {
      expect(MSG.DONE).toBe('done');
    });

    test('PING is ping', () => {
      expect(MSG.PING).toBe('ping');
    });

    test('PONG is pong', () => {
      expect(MSG.PONG).toBe('pong');
    });

    test('ERROR is error', () => {
      expect(MSG.ERROR).toBe('error');
    });
  });

  describe('DONE_REASON', () => {
    test('COMPLETED is completed', () => {
      expect(DONE_REASON.COMPLETED).toBe('completed');
    });

    test('STOPPED is stopped', () => {
      expect(DONE_REASON.STOPPED).toBe('stopped');
    });

    test('CONNECTION_LOST is connection_lost', () => {
      expect(DONE_REASON.CONNECTION_LOST).toBe('connection_lost');
    });
  });

  describe('ERROR_CODE', () => {
    test('SOUND_NOT_FOUND is SOUND_NOT_FOUND', () => {
      expect(ERROR_CODE.SOUND_NOT_FOUND).toBe('SOUND_NOT_FOUND');
    });

    test('HANDLE_NOT_FOUND is HANDLE_NOT_FOUND', () => {
      expect(ERROR_CODE.HANDLE_NOT_FOUND).toBe('HANDLE_NOT_FOUND');
    });

    test('PLAYBACK_FAILED is PLAYBACK_FAILED', () => {
      expect(ERROR_CODE.PLAYBACK_FAILED).toBe('PLAYBACK_FAILED');
    });

    test('UNKNOWN_MESSAGE is UNKNOWN_MESSAGE', () => {
      expect(ERROR_CODE.UNKNOWN_MESSAGE).toBe('UNKNOWN_MESSAGE');
    });
  });
});