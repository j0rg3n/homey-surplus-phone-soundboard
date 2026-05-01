'use strict';

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

function makeTriggerCard(triggersMap, id) {
  return {
    trigger: jest.fn(async (dev, tokens) => {
      triggersMap[id] = tokens;
      // Keep a list of all firings for multi-fire assertions
      if (!triggersMap[`${id}:all`]) triggersMap[`${id}:all`] = [];
      triggersMap[`${id}:all`].push(tokens);
    }),
  };
}

function createDevice() {
  const device = new SoundboardDevice();
  const triggers = {};
  const capabilities = {};
  const storeValues = {};
  let mockClient;

  device.getStore = () => ({ ip: '127.0.0.1', port: 8765 });
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
    mockClient = new MockWebSocketClient(opts);
    return mockClient;
  };

  return { device, triggers, capabilities, storeValues, getMockClient: () => mockClient };
}

describe('SoundboardDevice', () => {
  describe('onInit', () => {
    it('connects the WebSocket client on init', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      expect(getMockClient().isConnected()).toBe(true);
    });

    it('calls setAvailable after successful connect', async () => {
      const { device } = createDevice();
      await device.onInit();
      expect(device.setAvailable).toHaveBeenCalled();
    });

    it('stores sounds from hello_ack on connect', async () => {
      const { device, getMockClient, storeValues } = createDevice();
      // Patch connect to deliver a helloAck with sounds
      device._createClient = (opts) => {
        const mc = new MockWebSocketClient(opts);
        mc.connect = async () => {
          mc._connected = true;
          if (mc._onConnectCallback) {
            mc._onConnectCallback({ deviceName: 'TestDevice', sounds: ['drum', 'bell'] });
          }
        };
        return mc;
      };
      await device.onInit();
      expect(device.setStoreValue).toHaveBeenCalledWith('sounds', ['drum', 'bell']);
    });

    it('calls setUnavailable when initial connect fails', async () => {
      const { device } = createDevice();
      device._createClient = (opts) => {
        const mc = new MockWebSocketClient(opts);
        mc.connect = async () => { throw new Error('refused'); };
        return mc;
      };
      await device.onInit();
      expect(device.setUnavailable).toHaveBeenCalledWith('refused');
    });
  });

  describe('MSG.STARTED', () => {
    it('sets speaker_playing to true when a sound starts', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle: 'h1',
        soundId: 'drum',
        soundName: 'Drum',
        durationMs: 2000,
      });
      expect(capabilities['speaker_playing']).toBe(true);
    });

    it('fires sound_started trigger with correct tokens', async () => {
      const { device, triggers, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle: 'h1',
        soundId: 'drum',
        soundName: 'Drum',
        durationMs: 2000,
      });
      expect(triggers['sound_started']).toEqual({
        sound_name: 'Drum',
        handle: 'h1',
        duration_ms: 2000,
      });
    });
  });

  describe('MSG.DONE', () => {
    async function setupWithHandle(handleId = 'h1') {
      const ctx = createDevice();
      await ctx.device.onInit();
      ctx.getMockClient().simulateMessage({
        type: MSG.STARTED,
        handle: handleId,
        soundId: 'drum',
        soundName: 'Drum',
        durationMs: 2000,
      });
      return ctx;
    }

    it('sets speaker_playing to false when last sound finishes', async () => {
      const { device, capabilities, getMockClient } = await setupWithHandle();
      getMockClient().simulateMessage({
        type: MSG.DONE,
        handle: 'h1',
        soundName: 'Drum',
        reason: 'completed',
      });
      expect(capabilities['speaker_playing']).toBe(false);
    });

    it('fires sound_done trigger with correct tokens', async () => {
      const { device, triggers, getMockClient } = await setupWithHandle();
      getMockClient().simulateMessage({
        type: MSG.DONE,
        handle: 'h1',
        soundName: 'Drum',
        reason: 'completed',
      });
      expect(triggers['sound_done']).toEqual({
        sound_name: 'Drum',
        handle: 'h1',
        reason: 'completed',
      });
    });

    it('fires sound_done with reason from message', async () => {
      const { device, triggers, getMockClient } = await setupWithHandle();
      getMockClient().simulateMessage({
        type: MSG.DONE,
        handle: 'h1',
        soundName: 'Drum',
        reason: 'stopped',
      });
      expect(triggers['sound_done'].reason).toBe('stopped');
    });

    it('speaker_playing remains true when other handles still active', async () => {
      const ctx = createDevice();
      await ctx.device.onInit();
      // Start two sounds
      ctx.getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'A', durationMs: 1000 });
      ctx.getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h2', soundId: 'b', soundName: 'B', durationMs: 1000 });
      // Finish one
      ctx.getMockClient().simulateMessage({ type: MSG.DONE, handle: 'h1', soundName: 'A', reason: 'completed' });
      expect(ctx.capabilities['speaker_playing']).toBe(true);
    });
  });

  describe('MSG.LIBRARY_UPDATE', () => {
    it('stores updated sound list', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({
        type: MSG.LIBRARY_UPDATE,
        sounds: ['kick', 'snare', 'hihat'],
      });
      expect(device.setStoreValue).toHaveBeenCalledWith('sounds', ['kick', 'snare', 'hihat']);
    });
  });

  describe('disconnect behaviour', () => {
    it('fires sound_done with reason connection_lost for all in-flight handles on disconnect', async () => {
      const { device, triggers, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'Alpha', durationMs: 1000 });
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h2', soundId: 'b', soundName: 'Beta', durationMs: 2000 });

      getMockClient().simulateDisconnect();

      const fired = triggers['sound_done:all'] || [];
      expect(fired.length).toBe(2);
      expect(fired.every((t) => t.reason === 'connection_lost')).toBe(true);
      const handles = fired.map((t) => t.handle).sort();
      expect(handles).toEqual(['h1', 'h2'].sort());
    });

    it('sets speaker_playing to false on disconnect', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'A', durationMs: 500 });
      getMockClient().simulateDisconnect();
      expect(capabilities['speaker_playing']).toBe(false);
    });

    it('calls setUnavailable with "Connection lost" on disconnect', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateDisconnect();
      expect(device.setUnavailable).toHaveBeenCalledWith('Connection lost');
    });
  });

  describe('playSound', () => {
    it('sends a PLAY message with soundId, volume, and a UUID handle', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      await device.playSound('explosion', 80);
      const msg = getMockClient().getLastSentMessage();
      expect(msg.type).toBe(MSG.PLAY);
      expect(msg.soundId).toBe('explosion');
      expect(msg.volume).toBe(80);
      expect(typeof msg.handle).toBe('string');
      expect(msg.handle.length).toBeGreaterThan(0);
    });

    it('returns the handle UUID', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      const handle = await device.playSound('ding', 50);
      const msg = getMockClient().getLastSentMessage();
      expect(handle).toBe(msg.handle);
    });
  });

  describe('speaker_playing capability tracking', () => {
    it('is true when at least one handle is active, false when all finish', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();

      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'A', durationMs: 1000 });
      expect(capabilities['speaker_playing']).toBe(true);

      getMockClient().simulateMessage({ type: MSG.DONE, handle: 'h1', soundName: 'A', reason: 'completed' });
      expect(capabilities['speaker_playing']).toBe(false);
    });
  });

  describe('onDeleted', () => {
    it('disconnects the client', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      await device.onDeleted();
      expect(getMockClient().isConnected()).toBe(false);
    });
  });

  describe('MSG.HELLO_ACK in _handleMessage', () => {
    it('stores sounds when HELLO_ACK message is received with sounds', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({
        type: MSG.HELLO_ACK,
        sounds: ['boom', 'crack'],
        deviceName: 'TestDevice',
      });
      expect(device.setStoreValue).toHaveBeenCalledWith('sounds', ['boom', 'crack']);
    });

    it('does not call setStoreValue when HELLO_ACK has no sounds', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      const callsBefore = device.setStoreValue.mock.calls.length;
      getMockClient().simulateMessage({ type: MSG.HELLO_ACK, deviceName: 'TestDevice' });
      expect(device.setStoreValue.mock.calls.length).toBe(callsBefore);
    });
  });

  describe('speaker_playing capability listener', () => {
    it('calls playSound when listener is triggered with true', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();

      // Capture the registered listener
      const listenerCalls = device.registerCapabilityListener.mock.calls;
      const speakerListener = listenerCalls.find(([cap]) => cap === 'speaker_playing')?.[1];
      expect(speakerListener).toBeDefined();

      const playSpy = jest.spyOn(device, 'playSound').mockResolvedValue('handle-123');
      await speakerListener(true);
      expect(playSpy).toHaveBeenCalledWith('test', 100);
    });

    it('sends STOP_ALL when listener is triggered with false', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();

      const listenerCalls = device.registerCapabilityListener.mock.calls;
      const speakerListener = listenerCalls.find(([cap]) => cap === 'speaker_playing')?.[1];

      await speakerListener(false);
      const msg = getMockClient().getLastSentMessage();
      expect(msg).toEqual({ type: MSG.STOP_ALL });
    });
  });
});
