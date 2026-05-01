'use strict';

const WebSocket = require('ws');
const MessageProtocol = require('./MessageProtocol');
const { MSG, PROTOCOL_VERSION, HELLO_TIMEOUT_MS } = require('./constants');

class WebSocketClient {

  constructor({ ip, port, onMessage, onConnect, onDisconnect }) {
    this._ip = ip;
    this._port = port;
    this._onMessage = onMessage;
    this._onConnect = onConnect;
    this._onDisconnect = onDisconnect;
    this._ws = null;
    this._connected = false;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(`ws://${this._ip}:${this._port}`);
      this._ws = ws;

      const timer = setTimeout(() => {
        ws.terminate();
        reject(new Error('hello_ack timeout'));
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
          if (this._onConnect) this._onConnect(msg);
          resolve(msg);
        }

        if (this._onMessage) this._onMessage(msg);
      });

      ws.on('close', () => {
        this._connected = false;
        if (this._onDisconnect) this._onDisconnect();
      });

      ws.on('error', (err) => {
        clearTimeout(timer);
        if (!this._connected) reject(err);
      });
    });
  }

  async disconnect() {
    if (this._ws) {
      this._ws.close();
      this._ws = null;
      this._connected = false;
    }
  }

  async send(messageObject) {
    if (this._ws && this._connected) {
      this._ws.send(MessageProtocol.serialize(messageObject));
    }
  }

  isConnected() {
    return this._connected;
  }

}

module.exports = WebSocketClient;
