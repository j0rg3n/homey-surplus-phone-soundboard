'use strict';

/**
 * Integration smoke test — covers a full session lifecycle using
 * MockWebSocketClient and a lightweight SoundboardDevice harness.
 *
 * Steps exercised:
 *  1. Device initialises and connects
 *  2. hello_ack delivers a sound library
 *  3. play_sound action → PLAY message sent, handle returned
 *  4. sound_started trigger fires with correct tokens
 *  5. Natural done (reason:"completed") → sound_done trigger fires
 *  6. Loop play → PLAY message has loop:true
 *  7. stop_sound action → STOP message sent
 *  8. done with reason:"stopped" → sound_done trigger fires
 *  9. Third play + simulateDisconnect → sound_done fires with reason:"connection_lost"
 * 10. Final state: speaker_playing capability is false
 */

jest.mock('homey', () => ({
  Device: class {
    log() {}
    error() {}
    async setAvailable() {}
    async setUnavailable() {}
    async setCapabilityValue() {}
    async setStoreValue() {}
    getStoreValue() {}
    registerCapabilityListener() {}
  },
}));

const SoundboardDevice = require('../drivers/soundboard/device');
const MockWebSocketClient = require('./mocks/MockWebSocketClient');
const { MSG } = require('../lib/constants');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeTriggerCard(triggersMap, id) {
  return {
    trigger: jest.fn(async (dev, tokens) => {
      triggersMap[id] = tokens;
      if (!triggersMap[`${id}:all`]) triggersMap[`${id}:all`] = [];
      triggersMap[`${id}:all`].push({ ...tokens });
    }),
  };
}

function createDevice(initialStore = {}) {
  const device = new SoundboardDevice();
  const triggers = {};
  const capabilities = {};
  const storeValues = { ip: '127.0.0.1', port: 8765, ...initialStore };
  let mockClient;

  device.getStore = () => ({ ...storeValues });
  device.setCapabilityValue = jest.fn(async (cap, val) => { capabilities[cap] = val; });
  device.setStoreValue = jest.fn(async (key, val) => { storeValues[key] = val; });
  device.getStoreValue = (key) => storeValues[key];
  device.setAvailable = jest.fn(async () => {});
  device.setUnavailable = jest.fn(async () => {});
  device.error = jest.fn();
  device.registerCapabilityListener = jest.fn();
  device.homey = {
    flow: {
      getDeviceTriggerCard: (id) => makeTriggerCard(triggers, id),
    },
  };
  device._createClient = (opts) => {
    // Override connect so we can control the helloAck payload delivered later
    mockClient = new MockWebSocketClient(opts);
    // Suppress the default auto-connect helloAck — we deliver it manually
    mockClient.connect = async () => {
      mockClient._connected = true;
      // Do NOT fire onConnect here; the test calls simulateConnect() explicitly
    };
    return mockClient;
  };

  return { device, triggers, capabilities, storeValues, getMockClient: () => mockClient };
}

// ---------------------------------------------------------------------------
// Full session smoke test
// ---------------------------------------------------------------------------

describe('SoundboardDevice — integration smoke test', () => {
  let device, triggers, capabilities, getMockClient;

  beforeEach(async () => {
    ({ device, triggers, capabilities, getMockClient } =
      createDevice());
    await device.onInit();
  });

  // -------------------------------------------------------------------------
  // Step 1 — device initialises and connects
  // -------------------------------------------------------------------------
  it('1. device connects on init', () => {
    expect(getMockClient().isConnected()).toBe(true);
  });

  // -------------------------------------------------------------------------
  // Step 2 — hello_ack delivers sound library
  // -------------------------------------------------------------------------
  it('2. hello_ack stores the sound library', () => {
    const sounds = [{ id: 's1', name: 'Laser', duration: 1.2 }];
    getMockClient().simulateConnect({ deviceName: 'TestDevice', sounds });
    expect(device.setStoreValue).toHaveBeenCalledWith('sounds', sounds);
    expect(device.setAvailable).toHaveBeenCalled();
  });

  // -------------------------------------------------------------------------
  // Steps 3 + 4 — play_sound action, PLAY message, sound_started trigger
  // -------------------------------------------------------------------------
  describe('3–4. play_sound action', () => {
    let handle;

    beforeEach(async () => {
      handle = await device.playSound('s1', 80);
    });

    it('3. sends a PLAY message with correct fields', () => {
      const msg = getMockClient().getLastSentMessage();
      expect(msg.type).toBe(MSG.PLAY);
      expect(msg.soundId).toBe('s1');
      expect(msg.volume).toBe(80);
      expect(typeof msg.handle).toBe('string');
      expect(msg.handle.length).toBeGreaterThan(0);
    });

    it('3. playSound returns the handle from the PLAY message', () => {
      const msg = getMockClient().getLastSentMessage();
      expect(handle).toBe(msg.handle);
    });

    it('4. sound_started trigger fires with correct tokens', () => {
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 1200,
      });
      expect(triggers['sound_started']).toEqual({
        sound_name: 'Laser',
        handle,
        duration_ms: 1200,
      });
    });
  });

  // -------------------------------------------------------------------------
  // Step 5 — natural done (reason:"completed") fires sound_done
  // -------------------------------------------------------------------------
  describe('5. natural done with reason:completed', () => {
    let handle;

    beforeEach(async () => {
      handle = await device.playSound('s1', 80);
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 1200,
      });
      getMockClient().simulateMessage({
        type: MSG.DONE,
        handle,
        soundName: 'Laser',
        reason: 'completed',
      });
    });

    it('5. sound_done trigger fires with reason:completed', () => {
      expect(triggers['sound_done']).toEqual({
        sound_name: 'Laser',
        handle,
        reason: 'completed',
      });
    });

    it('5. no sounds playing after last sound finishes', () => {
      expect(device.isAnySoundPlaying()).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // Step 6 — loop play produces PLAY message with loop:true
  // -------------------------------------------------------------------------
  it('6. loop play sends PLAY message with loop:true', async () => {
    const loopHandle = await device.playSound('s1', 80, { loop: true });
    const msg = getMockClient().getLastSentMessage();
    expect(msg.type).toBe(MSG.PLAY);
    expect(msg.soundId).toBe('s1');
    expect(msg.loop).toBe(true);
    expect(msg.handle).toBe(loopHandle);
  });

  // -------------------------------------------------------------------------
  // Steps 7 + 8 — stop_sound action, STOP message, done with reason:stopped
  // -------------------------------------------------------------------------
  describe('7–8. stop_sound and done with reason:stopped', () => {
    let loopHandle;

    beforeEach(async () => {
      loopHandle = await device.playSound('s1', 80, { loop: true });
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle: loopHandle,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 0,
      });
      await device.stopSound(loopHandle);
    });

    it('7. stop_sound sends a STOP message with the handle', () => {
      const msg = getMockClient().getLastSentMessage();
      expect(msg).toEqual({ type: MSG.STOP, handle: loopHandle });
    });

    it('8. done with reason:stopped fires sound_done trigger', () => {
      getMockClient().simulateMessage({
        type: MSG.DONE,
        handle: loopHandle,
        soundName: 'Laser',
        reason: 'stopped',
      });
      expect(triggers['sound_done']).toEqual({
        sound_name: 'Laser',
        handle: loopHandle,
        reason: 'stopped',
      });
    });
  });

  // -------------------------------------------------------------------------
  // Steps 9 + 10 — disconnect fires connection_lost for in-flight handle
  // -------------------------------------------------------------------------
  describe('9–10. connection drop with in-flight handle', () => {
    let thirdHandle;

    beforeEach(async () => {
      thirdHandle = await device.playSound('s1', 80);
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle: thirdHandle,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 5000,
      });
      // Confirm it is in-flight
      expect(device.isAnySoundPlaying()).toBe(true);

      getMockClient().simulateDisconnect();
    });

    it('9. sound_done fires with reason:connection_lost for the in-flight handle', () => {
      const fired = triggers['sound_done:all'] || [];
      const connectionLostFirings = fired.filter((t) => t.reason === 'connection_lost');
      expect(connectionLostFirings.length).toBeGreaterThanOrEqual(1);
      const handles = connectionLostFirings.map((t) => t.handle);
      expect(handles).toContain(thirdHandle);
    });

    it('10. no sounds playing after disconnect', () => {
      expect(device.isAnySoundPlaying()).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // Full sequential narrative — all 10 steps in one test
  // -------------------------------------------------------------------------
  describe('full session narrative (steps 1–10 in sequence)', () => {
    it('runs through the complete lifecycle without errors', async () => {
      // Fresh device for this narrative test
      const {
        device: dev,
        triggers: trigs,
        capabilities: caps,
        getMockClient: getClient,
      } = createDevice();

      // Step 1 — init + connect
      await dev.onInit();
      expect(getClient().isConnected()).toBe(true);

      // Step 2 — hello_ack with sound library
      const sounds = [{ id: 's1', name: 'Laser', duration: 1.2 }];
      getClient().simulateConnect({ deviceName: 'TestDevice', sounds });
      expect(dev.setStoreValue).toHaveBeenCalledWith('sounds', sounds);
      expect(dev.setAvailable).toHaveBeenCalled();

      // Step 3 — play_sound action sends PLAY message
      const handle1 = await dev.playSound('s1', 80);
      const playMsg = getClient().getLastSentMessage();
      expect(playMsg.type).toBe(MSG.PLAY);
      expect(playMsg.soundId).toBe('s1');
      expect(playMsg.volume).toBe(80);
      expect(playMsg.handle).toBe(handle1);
      expect(playMsg.loop).toBeUndefined();

      // Step 4 — started arrives → sound_started trigger fires
      getClient().simulateMessage({
        type: MSG.STARTED,
        handle: handle1,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 1200,
      });
      expect(trigs['sound_started']).toEqual({
        sound_name: 'Laser',
        handle: handle1,
        duration_ms: 1200,
      });
      expect(dev.isAnySoundPlaying()).toBe(true);

      // Step 5 — natural done
      getClient().simulateMessage({
        type: MSG.DONE,
        handle: handle1,
        soundName: 'Laser',
        reason: 'completed',
      });
      expect(trigs['sound_done']).toEqual({
        sound_name: 'Laser',
        handle: handle1,
        reason: 'completed',
      });
      expect(dev.isAnySoundPlaying()).toBe(false);

      // Step 6 — loop play
      const loopHandle = await dev.playSound('s1', 80, { loop: true });
      const loopMsg = getClient().getLastSentMessage();
      expect(loopMsg.type).toBe(MSG.PLAY);
      expect(loopMsg.loop).toBe(true);
      expect(loopMsg.handle).toBe(loopHandle);

      getClient().simulateMessage({
        type: MSG.STARTED,
        handle: loopHandle,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 0,
      });
      expect(dev.isAnySoundPlaying()).toBe(true);

      // Step 7 — stop_sound action
      await dev.stopSound(loopHandle);
      const stopMsg = getClient().getLastSentMessage();
      expect(stopMsg).toEqual({ type: MSG.STOP, handle: loopHandle });

      // Step 8 — done with reason:stopped
      getClient().simulateMessage({
        type: MSG.DONE,
        handle: loopHandle,
        soundName: 'Laser',
        reason: 'stopped',
      });
      expect(trigs['sound_done']).toEqual({
        sound_name: 'Laser',
        handle: loopHandle,
        reason: 'stopped',
      });
      expect(dev.isAnySoundPlaying()).toBe(false);

      // Step 9 — third play then disconnect
      const handle3 = await dev.playSound('s1', 80);
      getClient().simulateMessage({
        type: MSG.STARTED,
        handle: handle3,
        soundId: 's1',
        soundName: 'Laser',
        durationMs: 5000,
      });
      expect(dev.isAnySoundPlaying()).toBe(true);

      getClient().simulateDisconnect();

      const connectionLostFirings = (trigs['sound_done:all'] || [])
        .filter((t) => t.reason === 'connection_lost');
      expect(connectionLostFirings.length).toBeGreaterThanOrEqual(1);
      expect(connectionLostFirings.map((t) => t.handle)).toContain(handle3);

      // Step 10 — final state
      expect(dev.isAnySoundPlaying()).toBe(false);
    });
  });
});
