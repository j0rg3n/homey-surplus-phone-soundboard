'use strict';

const WebSocket = require('ws');
const MessageProtocol = require('./MessageProtocol');
const {
  MSG,
  PROTOCOL_VERSION,
  HELLO_TIMEOUT_MS,
  PING_INTERVAL_MS,
  PING_TIMEOUT_COUNT,
  RECONNECT_BASE_MS,
  RECONNECT_MAX_MS,
} = require('./constants');

class WebSocketClient {

  constructor({ ip, port, onMessage, onConnect, onDisconnect }) {
    this._ip = ip;
    this._port = port;
    this._onMessage = onMessage;
    this._onConnect = onConnect;
    this._onDisconnect = onDisconnect;
    this._ws = null;
    this._connected = false;
    this._destroyed = false;
    this._reconnectDelay = null;   // null until first successful connect
    this._reconnectTimer = null;
    this._pingInterval = null;
    this._missedPongs = 0;
  }

  // ─── Public API ──────────────────────────────────────────────────────────────

  connect() {
    return new Promise((resolve, reject) => {
      this._openSocket(resolve, reject);
    });
  }

  async disconnect() {
    this._destroyed = true;
    this._cancelReconnect();
    this._stopPing();
    if (this._ws) {
      this._ws.close();
      this._ws = null;
    }
    this._connected = false;
  }

  async send(messageObject) {
    if (this._ws && this._connected) {
      this._ws.send(MessageProtocol.serialize(messageObject));
    }
  }

  isConnected() {
    return this._connected;
  }

  // ─── Internal ─────────────────────────────────────────────────────────────

  /**
   * Open a WebSocket connection and wire up all event handlers.
   *
   * @param {Function|null} resolve - Promise resolver for the initial connect() call.
   *                                  null when called from _scheduleReconnect().
   * @param {Function|null} reject  - Promise rejector for the initial connect() call.
   */
  _openSocket(resolve, reject) {
    const ws = new WebSocket(`ws://${this._ip}:${this._port}`);
    this._ws = ws;

    const timer = setTimeout(() => {
      ws.terminate();
      if (reject) reject(new Error('hello_ack timeout'));
    }, HELLO_TIMEOUT_MS);

    ws.once('open', () => {
      ws.send(MessageProtocol.serialize({ type: MSG.HELLO, version: PROTOCOL_VERSION }));
    });

    ws.on('message', (data) => {
      let msg;
      try {
        msg = MessageProtocol.deserialize(data.toString());
      } catch (_) {
        return;
      }

      if (!this._connected && msg.type === MSG.HELLO_ACK) {
        clearTimeout(timer);
        this._connected = true;
        // Reset backoff on every successful connect.
        this._reconnectDelay = RECONNECT_BASE_MS;
        this._missedPongs = 0;
        this._startPing();
        if (this._onConnect) this._onConnect(msg);
        if (resolve) {
          resolve(msg);
          resolve = null; // guard: don't resolve twice
        }
      }

      if (msg.type === MSG.PONG) {
        this._missedPongs = 0;
      }

      if (this._onMessage) this._onMessage(msg);
    });

    ws.on('close', () => {
      clearTimeout(timer);
      this._stopPing();
      const wasConnected = this._connected;
      this._connected = false;
      this._ws = null;

      if (wasConnected && this._onDisconnect) this._onDisconnect();

      // If destroyed, or if hello_ack was never received (reconnectDelay still
      // null, meaning this was a pairing probe that was never fully connected),
      // do not attempt to reconnect.
      if (!this._destroyed && this._reconnectDelay !== null) {
        this._scheduleReconnect();
      }

      // If the initial connect() promise has not resolved yet (no hello_ack),
      // reject it so the caller knows the connection failed.
      if (reject) {
        reject(new Error('WebSocket closed before hello_ack'));
        reject = null;
      }
    });

    ws.on('error', (err) => {
      clearTimeout(timer);
      if (!this._connected && reject) {
        reject(err);
        reject = null;
      }
    });
  }

  _scheduleReconnect() {
    const delay = this._reconnectDelay;
    // Prepare next delay (capped).
    this._reconnectDelay = Math.min(delay * 2, RECONNECT_MAX_MS);

    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      if (!this._destroyed) {
        this._openSocket(null, null);
      }
    }, delay);
  }

  _cancelReconnect() {
    if (this._reconnectTimer !== null) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
  }

  _startPing() {
    this._stopPing(); // clear any previous interval
    this._pingInterval = setInterval(() => {
      this._missedPongs += 1;
      if (this._missedPongs >= PING_TIMEOUT_COUNT) {
        if (this._ws) this._ws.terminate();
      } else {
        this.send({ type: MSG.PING }).catch(() => {});
      }
    }, PING_INTERVAL_MS);
  }

  _stopPing() {
    if (this._pingInterval !== null) {
      clearInterval(this._pingInterval);
      this._pingInterval = null;
    }
  }

}

module.exports = WebSocketClient;
