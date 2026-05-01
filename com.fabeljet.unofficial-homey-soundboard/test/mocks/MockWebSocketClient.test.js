const MockWebSocketClient = require('./MockWebSocketClient');

describe('MockWebSocketClient', () => {
  let mock;

  beforeEach(() => {
    mock = new MockWebSocketClient();
  });

  describe('connection', () => {
    test('is disconnected by default', () => {
      expect(mock.isConnected()).toBe(false);
    });

    test('isConnected returns true after connect', () => {
      mock.connect();
      expect(mock.isConnected()).toBe(true);
    });

    test('isConnected returns false after disconnect', () => {
      mock.connect();
      mock.disconnect();
      expect(mock.isConnected()).toBe(false);
    });
  });

  describe('messages', () => {
    test('send throws when not connected', () => {
      expect(() => mock.send({})).toThrow('Not connected');
    });

    test('send adds message to sentMessages', () => {
      mock.connect();
      mock.send({ type: 'ping' });
      expect(mock.getSentMessages()).toHaveLength(1);
      expect(mock.getLastSentMessage()).toEqual({ type: 'ping' });
    });

    test('getSentMessages returns all messages', () => {
      mock.connect();
      mock.send({ type: 'ping' });
      mock.send({ type: 'hello' });
      expect(mock.getSentMessages()).toHaveLength(2);
    });
  });

  describe('callbacks', () => {
    test('onMessage triggers callback on simulateMessage', () => {
      const callback = jest.fn();
      mock.onMessage(callback);
      mock.simulateMessage({ type: 'pong' });
      expect(callback).toHaveBeenCalledWith({ type: 'pong' });
    });

    test('onConnect triggers callback on connect', () => {
      const callback = jest.fn();
      mock.onConnect(callback);
      mock.connect();
      expect(callback).toHaveBeenCalled();
    });

    test('onDisconnect triggers callback on disconnect', () => {
      const callback = jest.fn();
      mock.onDisconnect(callback);
      mock.connect();
      mock.disconnect();
      expect(callback).toHaveBeenCalled();
    });
  });
});