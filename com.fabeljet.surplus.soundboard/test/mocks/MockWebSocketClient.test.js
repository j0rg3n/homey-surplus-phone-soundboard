'use strict';

const MockWebSocketClient = require('./MockWebSocketClient');

describe('MockWebSocketClient', () => {
  describe('connection (no callbacks)', () => {
    let mock;

    beforeEach(() => {
      mock = new MockWebSocketClient();
    });

    test('is disconnected by default', () => {
      expect(mock.isConnected()).toBe(false);
    });

    test('isConnected returns true after connect', async () => {
      await mock.connect();
      expect(mock.isConnected()).toBe(true);
    });

    test('isConnected returns false after disconnect', async () => {
      await mock.connect();
      await mock.disconnect();
      expect(mock.isConnected()).toBe(false);
    });
  });

  describe('messages', () => {
    let mock;

    beforeEach(() => {
      mock = new MockWebSocketClient();
    });

    test('send throws when not connected', () => {
      expect(() => mock.send({})).toThrow('Not connected');
    });

    test('send adds message to sentMessages', async () => {
      await mock.connect();
      mock.send({ type: 'ping' });
      expect(mock.getSentMessages()).toHaveLength(1);
      expect(mock.getLastSentMessage()).toEqual({ type: 'ping' });
    });

    test('getSentMessages returns all messages', async () => {
      await mock.connect();
      mock.send({ type: 'ping' });
      mock.send({ type: 'hello' });
      expect(mock.getSentMessages()).toHaveLength(2);
    });
  });

  describe('callbacks via constructor', () => {
    test('onMessage callback is invoked on simulateMessage', async () => {
      const onMessage = jest.fn();
      const mock = new MockWebSocketClient({ onMessage });
      mock.simulateMessage({ type: 'pong' });
      expect(onMessage).toHaveBeenCalledWith({ type: 'pong' });
    });

    test('onConnect callback is invoked on connect', async () => {
      const onConnect = jest.fn();
      const mock = new MockWebSocketClient({ onConnect });
      await mock.connect();
      expect(onConnect).toHaveBeenCalled();
    });

    test('onDisconnect callback is invoked on disconnect', async () => {
      const onDisconnect = jest.fn();
      const mock = new MockWebSocketClient({ onDisconnect });
      await mock.connect();
      await mock.disconnect();
      expect(onDisconnect).toHaveBeenCalled();
    });

    test('simulateDisconnect invokes onDisconnect without going through disconnect()', async () => {
      const onDisconnect = jest.fn();
      const mock = new MockWebSocketClient({ onDisconnect });
      await mock.connect();
      mock.simulateDisconnect();
      expect(onDisconnect).toHaveBeenCalled();
      expect(mock.isConnected()).toBe(false);
    });

    test('simulateConnect invokes onConnect and passes helloAck payload', () => {
      const onConnect = jest.fn();
      const mock = new MockWebSocketClient({ onConnect });
      const ack = { deviceName: 'Test', sounds: ['a', 'b'] };
      mock.simulateConnect(ack);
      expect(onConnect).toHaveBeenCalledWith(ack);
      expect(mock.isConnected()).toBe(true);
    });

    test('works with no callbacks (does not throw)', async () => {
      const mock = new MockWebSocketClient();
      await mock.connect();
      mock.simulateMessage({ type: 'ping' });
      await mock.disconnect();
    });
  });
});
