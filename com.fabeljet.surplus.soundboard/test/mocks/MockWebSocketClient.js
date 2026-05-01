class MockWebSocketClient {
  constructor() {
    this._connected = false;
    this._sentMessages = [];
    this._onMessageCallback = null;
    this._onConnectCallback = null;
    this._onDisconnectCallback = null;
  }

  connect() {
    this._connected = true;
    if (this._onConnectCallback) {
      this._onConnectCallback();
    }
  }

  disconnect() {
    this._connected = false;
    if (this._onDisconnectCallback) {
      this._onDisconnectCallback();
    }
  }

  send(message) {
    if (!this._connected) {
      throw new Error('Not connected');
    }
    this._sentMessages.push(message);
  }

  simulateMessage(message) {
    if (this._onMessageCallback) {
      this._onMessageCallback(message);
    }
  }

  simulateDisconnect() {
    this.disconnect();
  }

  simulateConnect() {
    this.connect();
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

  onMessage(callback) {
    this._onMessageCallback = callback;
  }

  onConnect(callback) {
    this._onConnectCallback = callback;
  }

  onDisconnect(callback) {
    this._onDisconnectCallback = callback;
  }
}

module.exports = MockWebSocketClient;