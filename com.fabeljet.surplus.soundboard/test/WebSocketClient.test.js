'use strict';

/**
 * WebSocketClient tests.
 *
 * Strategy: spin up a real in-process ws.Server so the WebSocket handshake
 * is genuine, then use Jest fake timers (doNotFake setImmediate/nextTick) to
 * control ping intervals and reconnect backoff without wall-clock delays.
 */

const WebSocket = require('ws');
const WebSocketClient = require('../lib/WebSocketClient');
const MessageProtocol = require('../lib/MessageProtocol');
const {
  MSG,
  PROTOCOL_VERSION,
  PING_INTERVAL_MS,
  PING_TIMEOUT_COUNT,
  RECONNECT_BASE_MS,
  RECONNECT_MAX_MS,
} = require('../lib/constants');

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Creates a ws.Server on a random ephemeral port. */
function createServer() {
  return new Promise((resolve) => {
    const server = new WebSocket.Server({ port: 0 }, () => {
      const { port } = server.address();
      resolve({ server, port });
    });
  });
}

/** Wait for the server to receive its next connection. */
function nextServerSocket(server) {
  return new Promise((resolve) => server.once('connection', resolve));
}

/** Wait for one message on a server-side socket, return parsed object. */
function nextMessage(sock) {
  return new Promise((resolve) => {
    sock.once('message', (raw) => resolve(JSON.parse(raw.toString())));
  });
}

/** Send a hello_ack from a server-side socket. */
function sendHelloAck(serverSocket, extra = {}) {
  serverSocket.send(
    MessageProtocol.serialize({
      type: MSG.HELLO_ACK,
      deviceName: 'TestDevice',
      version: PROTOCOL_VERSION,
      sounds: [],
      ...extra,
    }),
  );
}

/**
 * Drain the micro-task and setImmediate queues several times.
 * This lets pending network callbacks fire without advancing fake timers.
 */
function flushPromises(rounds = 5) {
  let p = Promise.resolve();
  for (let i = 0; i < rounds; i++) {
    p = p
      .then(() => new Promise((r) => setImmediate(r)))
      .then(() => new Promise((r) => setImmediate(r)));
  }
  return p;
}

/**
 * Poll until `predicate()` returns true or we time out (real ms).
 * Advances fake timers by `tickMs` on each poll to unblock timer-driven code.
 */
function waitUntil(predicate, { timeout = 2000, pollMs = 10, tickMs = 0 } = {}) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeout;
    function check() {
      if (predicate()) return resolve();
      if (Date.now() > deadline) return reject(new Error('waitUntil timed out'));
      if (tickMs) jest.advanceTimersByTime(tickMs);
      setImmediate(check);
    }
    check();
  });
}

/**
 * Perform a full client connect: wait for server socket, consume the hello,
 * send hello_ack, wait for the connect promise to resolve.
 * Returns { serverSocket, ack }.
 */
async function performConnect(server, connectPromise) {
  const serverSocket = await nextServerSocket(server);
  const hello = await nextMessage(serverSocket);
  expect(hello.type).toBe(MSG.HELLO);
  sendHelloAck(serverSocket);
  const ack = await connectPromise;
  return { serverSocket, ack };
}

// ─── Test suite ───────────────────────────────────────────────────────────────

describe('WebSocketClient', () => {
  let server;
  let port;
  let client;

  beforeEach(async () => {
    ({ server, port } = await createServer());
    // Fake setTimeout/setInterval but leave setImmediate + nextTick real so
    // network I/O still progresses through the Node.js event loop.
    jest.useFakeTimers({ doNotFake: ['setImmediate', 'nextTick'] });
  });

  afterEach(async () => {
    jest.useRealTimers();
    if (client) {
      await client.disconnect();
      client = null;
    }
    await new Promise((resolve) => server.close(resolve));
  });

  // ── connect() ──────────────────────────────────────────────────────────────

  describe('connect()', () => {
    test('sends hello on open and resolves with hello_ack payload', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const connectPromise = client.connect();
      const { ack } = await performConnect(server, connectPromise);

      expect(ack.type).toBe(MSG.HELLO_ACK);
      expect(ack.deviceName).toBe('TestDevice');
      expect(client.isConnected()).toBe(true);
    });

    test('fires onConnect callback with hello_ack', async () => {
      const onConnect = jest.fn();
      client = new WebSocketClient({ ip: '127.0.0.1', port, onConnect });

      const { ack } = await performConnect(server, client.connect());

      expect(onConnect).toHaveBeenCalledTimes(1);
      expect(onConnect.mock.calls[0][0]).toEqual(ack);
    });

    test('rejects when server closes before hello_ack', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const connectPromise = client.connect();
      const serverSocket = await nextServerSocket(server);
      await nextMessage(serverSocket); // consume hello
      serverSocket.close();

      await expect(connectPromise).rejects.toThrow();
    });

    test('rejects on hello_ack timeout', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const connectPromise = client.connect();
      await nextServerSocket(server); // accept connection but don't reply

      // Fire the HELLO_TIMEOUT_MS timer.
      jest.runAllTimers();

      await expect(connectPromise).rejects.toThrow('hello_ack timeout');
    });
  });

  // ── disconnect() ───────────────────────────────────────────────────────────

  describe('disconnect()', () => {
    test('sets _destroyed=true and isConnected()=false', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });
      await performConnect(server, client.connect());

      await client.disconnect();

      expect(client._destroyed).toBe(true);
      expect(client.isConnected()).toBe(false);
    });

    test('no reconnect fires after disconnect()', async () => {
      const scheduleReconnectSpy = jest.spyOn(WebSocketClient.prototype, '_scheduleReconnect');
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());
      await client.disconnect();

      // Simulate close arriving after destroy.
      serverSocket.close();
      await flushPromises();
      jest.advanceTimersByTime(RECONNECT_MAX_MS * 10);
      await flushPromises();

      expect(scheduleReconnectSpy).not.toHaveBeenCalled();
      scheduleReconnectSpy.mockRestore();
    });
  });

  // ── reconnect + backoff ────────────────────────────────────────────────────

  describe('reconnect with backoff', () => {
    test('fires onDisconnect when connection drops unexpectedly', async () => {
      const onDisconnect = jest.fn();
      client = new WebSocketClient({ ip: '127.0.0.1', port, onDisconnect });

      const { serverSocket } = await performConnect(server, client.connect());

      serverSocket.close();
      await waitUntil(() => onDisconnect.mock.calls.length > 0);

      expect(onDisconnect).toHaveBeenCalledTimes(1);
    });

    test('first retry fires after RECONNECT_BASE_MS', async () => {
      const onConnect = jest.fn();
      client = new WebSocketClient({ ip: '127.0.0.1', port, onConnect });

      const { serverSocket } = await performConnect(server, client.connect());
      expect(onConnect).toHaveBeenCalledTimes(1);

      // Drop the connection — client schedules reconnect at RECONNECT_BASE_MS.
      serverSocket.close();
      await waitUntil(() => !client.isConnected());

      // Fire the reconnect timer.
      jest.advanceTimersByTime(RECONNECT_BASE_MS);

      // Wait for the next server connection and complete handshake.
      const serverSocket2 = await nextServerSocket(server);
      await nextMessage(serverSocket2); // consume hello
      sendHelloAck(serverSocket2);

      await waitUntil(() => onConnect.mock.calls.length >= 2);
      expect(onConnect).toHaveBeenCalledTimes(2);
    });

    test('backoff doubles: 1st retry at BASE, 2nd at 2x, 3rd at 4x', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      // After first hello_ack _reconnectDelay is BASE; next drop will use BASE then double.
      expect(client._reconnectDelay).toBe(RECONNECT_BASE_MS);

      // Drop 1 — schedules retry at BASE, sets next delay to 2*BASE.
      serverSocket.close();
      await waitUntil(() => !client.isConnected());
      expect(client._reconnectDelay).toBe(RECONNECT_BASE_MS * 2);

      // Trigger retry at BASE, but don't send hello_ack so it drops again.
      jest.advanceTimersByTime(RECONNECT_BASE_MS);
      const ss2 = await nextServerSocket(server);
      await nextMessage(ss2); // hello
      ss2.close();
      await waitUntil(() => client._reconnectDelay >= RECONNECT_BASE_MS * 4);
      expect(client._reconnectDelay).toBe(RECONNECT_BASE_MS * 4);

      // Trigger retry at 2*BASE, drop again.
      jest.advanceTimersByTime(RECONNECT_BASE_MS * 2);
      const ss3 = await nextServerSocket(server);
      await nextMessage(ss3); // hello
      ss3.close();
      await waitUntil(() => client._reconnectDelay >= RECONNECT_BASE_MS * 8);
      expect(client._reconnectDelay).toBe(RECONNECT_BASE_MS * 8);
    });

    test('backoff caps at RECONNECT_MAX_MS', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      // Force delay to a value that would double past the cap.
      client._reconnectDelay = RECONNECT_MAX_MS;

      serverSocket.close();
      await waitUntil(() => !client.isConnected());

      // After the drop, next delay is capped at RECONNECT_MAX_MS.
      expect(client._reconnectDelay).toBe(RECONNECT_MAX_MS);
    });

    test('_reconnectDelay resets to RECONNECT_BASE_MS on successful reconnect', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      // Force high delay, then drop.
      client._reconnectDelay = RECONNECT_MAX_MS;
      serverSocket.close();
      await waitUntil(() => !client.isConnected());

      // Reconnect succeeds.
      jest.advanceTimersByTime(RECONNECT_MAX_MS);
      const ss2 = await nextServerSocket(server);
      await nextMessage(ss2);
      sendHelloAck(ss2);

      await waitUntil(() => client.isConnected());
      expect(client._reconnectDelay).toBe(RECONNECT_BASE_MS);
    });

    test('after disconnect(), no reconnect fires even if ws close event fires', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });
      const { serverSocket } = await performConnect(server, client.connect());

      await client.disconnect();
      expect(client._destroyed).toBe(true);

      // Fire close event on the already-replaced socket reference.
      serverSocket.close();
      await flushPromises();
      jest.advanceTimersByTime(RECONNECT_MAX_MS * 10);
      await flushPromises();

      // If _destroyed, _scheduleReconnect is never called.
      expect(client._reconnectTimer).toBeNull();
    });
  });

  // ── ping/pong heartbeat ────────────────────────────────────────────────────

  describe('ping/pong heartbeat', () => {
    test('sends ping after PING_INTERVAL_MS following hello_ack', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      const pingReceived = new Promise((resolve) => {
        serverSocket.on('message', (raw) => {
          const msg = JSON.parse(raw.toString());
          if (msg.type === MSG.PING) resolve(msg);
        });
      });

      jest.advanceTimersByTime(PING_INTERVAL_MS);

      const ping = await pingReceived;
      expect(ping.type).toBe(MSG.PING);
    });

    test('receiving pong resets _missedPongs to 0', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      // One ping tick increments missedPongs.
      jest.advanceTimersByTime(PING_INTERVAL_MS);
      await flushPromises();
      expect(client._missedPongs).toBe(1);

      // Server sends pong.
      serverSocket.send(MessageProtocol.serialize({ type: MSG.PONG }));
      await waitUntil(() => client._missedPongs === 0);

      expect(client._missedPongs).toBe(0);
    });

    test('3 missed pongs terminate the socket and fire onDisconnect', async () => {
      const onDisconnect = jest.fn();
      client = new WebSocketClient({ ip: '127.0.0.1', port, onDisconnect });

      await performConnect(server, client.connect());

      // Advance through PING_TIMEOUT_COUNT intervals without any pong.
      for (let i = 0; i < PING_TIMEOUT_COUNT; i++) {
        jest.advanceTimersByTime(PING_INTERVAL_MS);
        await flushPromises();
      }

      await waitUntil(() => onDisconnect.mock.calls.length > 0);

      expect(onDisconnect).toHaveBeenCalled();
      expect(client.isConnected()).toBe(false);
    });
  });

  // ── send() ─────────────────────────────────────────────────────────────────

  describe('send()', () => {
    test('serializes via MessageProtocol and sends valid JSON', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });

      const { serverSocket } = await performConnect(server, client.connect());

      const receivedPromise = nextMessage(serverSocket);
      await client.send({ type: MSG.STOP_ALL });

      const parsed = await receivedPromise;
      expect(parsed.type).toBe(MSG.STOP_ALL);
    });

    test('does nothing when not connected (no throw)', async () => {
      client = new WebSocketClient({ ip: '127.0.0.1', port });
      await expect(client.send({ type: MSG.PING })).resolves.toBeUndefined();
    });
  });

  // ── onMessage callback ──────────────────────────────────────────────────────

  describe('onMessage callback', () => {
    test('fires for all server messages including hello_ack', async () => {
      const received = [];
      client = new WebSocketClient({
        ip: '127.0.0.1',
        port,
        onMessage: (msg) => received.push(msg),
      });

      const { serverSocket } = await performConnect(server, client.connect());

      expect(received.some((m) => m.type === MSG.HELLO_ACK)).toBe(true);

      // Send a regular message and wait for it.
      serverSocket.send(MessageProtocol.serialize({ type: MSG.PING }));
      await waitUntil(() => received.some((m) => m.type === MSG.PING));

      expect(received.some((m) => m.type === MSG.PING)).toBe(true);
    });

    test('ignores malformed JSON from server without throwing', async () => {
      const received = [];
      client = new WebSocketClient({
        ip: '127.0.0.1',
        port,
        onMessage: (msg) => received.push(msg),
      });

      const { serverSocket } = await performConnect(server, client.connect());

      // Send valid then bad then valid.
      serverSocket.send('not valid json!!!');
      serverSocket.send(MessageProtocol.serialize({ type: MSG.PING }));
      await waitUntil(() => received.some((m) => m.type === MSG.PING));

      // Only valid messages appear in received.
      expect(received.every((m) => typeof m.type === 'string')).toBe(true);
    });
  });
});
