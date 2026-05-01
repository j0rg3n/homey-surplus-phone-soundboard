'use strict';

class MockWebSocketClient {
  constructor({ onMessage, onConnect, onDisconnect } = {}) {
    this._connected = false;
    this._sentMessages = [];
    this._onMessageCallback = onMessage || null;
    this._onConnectCallback = onConnect || null;
    this._onDisconnectCallback = onDisconnect || null;
  }

  connect() {
    this._connected = true;
    if (this._onConnectCallback) {
      this._onConnectCallback({ deviceName: 'MockDevice', sounds: [] });
    }
    return Promise.resolve();
  }

  disconnect() {
    this._connected = false;
    if (this._onDisconnectCallback) {
      this._onDisconnectCallback();
    }
    return Promise.resolve();
  }

  send(message) {
    if (!this._connected) {
      throw new Error('Not connected');
    }
    this._sentMessages.push(message);
    return Promise.resolve();
  }

  simulateMessage(message) {
    if (this._onMessageCallback) {
      this._onMessageCallback(message);
    }
  }

  simulateDisconnect() {
    this._connected = false;
    if (this._onDisconnectCallback) {
      this._onDisconnectCallback();
    }
  }

  simulateConnect(helloAck = { deviceName: 'MockDevice', sounds: [] }) {
    this._connected = true;
    if (this._onConnectCallback) {
      this._onConnectCallback(helloAck);
    }
  }

  getLastSentMessage() {
    return this._sentMessages[this._sentMessages.length - 1] || null;
  }

  getSentMessages() {
    return [...this._sentMessages];
  }

  isConnected() {
    return this._connected;
  }
}

module.exports = MockWebSocketClient;
