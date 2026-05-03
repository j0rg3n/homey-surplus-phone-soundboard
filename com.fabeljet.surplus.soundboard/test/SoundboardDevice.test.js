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

  device.getStore = () => ({ ip: '127.0.0.1', port: 8765, ...storeValues });
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

    it('uses globalVolume from store when no volume arg is given', async () => {
      const { device, getMockClient, storeValues } = createDevice();
      await device.onInit();
      storeValues.globalVolume = 200;
      await device.playSound('bell');
      const msg = getMockClient().getLastSentMessage();
      expect(msg.volume).toBe(200);
    });

    it('uses 100 as fallback when no globalVolume stored', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      await device.playSound('bell');
      const msg = getMockClient().getLastSentMessage();
      expect(msg.volume).toBe(100);
    });
  });

  describe('stopSound', () => {
    it('sends a STOP message with the given handle', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      await device.stopSound('my-handle-123');
      const msg = getMockClient().getLastSentMessage();
      expect(msg).toEqual({ type: MSG.STOP, handle: 'my-handle-123' });
    });
  });

  describe('stopAll', () => {
    it('sends a STOP_ALL message', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      await device.stopAll();
      const msg = getMockClient().getLastSentMessage();
      expect(msg).toEqual({ type: MSG.STOP_ALL });
    });
  });

  describe('setGlobalVolume', () => {
    it('stores the volume in device store', async () => {
      const { device } = createDevice();
      await device.onInit();
      await device.setGlobalVolume(250);
      expect(device.setStoreValue).toHaveBeenCalledWith('globalVolume', 250);
    });
  });

  describe('isAnySoundPlaying', () => {
    it('returns false when no sounds are active', async () => {
      const { device } = createDevice();
      await device.onInit();
      expect(device.isAnySoundPlaying()).toBe(false);
    });

    it('returns true when at least one sound is active', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'A', durationMs: 1000 });
      expect(device.isAnySoundPlaying()).toBe(true);
    });

    it('returns false after all sounds finish', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'a', soundName: 'A', durationMs: 1000 });
      getMockClient().simulateMessage({ type: MSG.DONE, handle: 'h1', soundName: 'A', reason: 'completed' });
      expect(device.isAnySoundPlaying()).toBe(false);
    });
  });

  describe('isSoundPlaying', () => {
    it('returns false when the named sound is not active', async () => {
      const { device } = createDevice();
      await device.onInit();
      expect(device.isSoundPlaying('Drum')).toBe(false);
    });

    it('returns true when the named sound is active', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'drum', soundName: 'Drum', durationMs: 2000 });
      expect(device.isSoundPlaying('Drum')).toBe(true);
    });

    it('returns false after sound finishes', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.STARTED, handle: 'h1', soundId: 'drum', soundName: 'Drum', durationMs: 2000 });
      getMockClient().simulateMessage({ type: MSG.DONE, handle: 'h1', soundName: 'Drum', reason: 'completed' });
      expect(device.isSoundPlaying('Drum')).toBe(false);
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

  describe('volume_mute capability listener', () => {
    function getMuteListener(device) {
      return device.registerCapabilityListener.mock.calls
        .find(([cap]) => cap === 'volume_mute')?.[1];
    }

    it('sends MUTE when listener is triggered with true', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      const listener = getMuteListener(device);
      expect(listener).toBeDefined();
      await listener(true);
      expect(getMockClient().getLastSentMessage()).toEqual({ type: MSG.MUTE });
    });

    it('sends UNMUTE when listener is triggered with false', async () => {
      const { device, getMockClient } = createDevice();
      await device.onInit();
      const listener = getMuteListener(device);
      await listener(false);
      expect(getMockClient().getLastSentMessage()).toEqual({ type: MSG.UNMUTE });
    });
  });

  describe('MSG.MUTE_STATE', () => {
    it('sets volume_mute capability to muted value', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.MUTE_STATE, muted: true });
      expect(capabilities['volume_mute']).toBe(true);
    });

    it('clears volume_mute capability when muted is false', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateMessage({ type: MSG.MUTE_STATE, muted: true });
      getMockClient().simulateMessage({ type: MSG.MUTE_STATE, muted: false });
      expect(capabilities['volume_mute']).toBe(false);
    });
  });

  describe('onConnect muted field', () => {
    it('sets volume_mute from hello_ack muted field on connect', async () => {
      const { device, capabilities, getMockClient } = createDevice();
      await device.onInit();
      getMockClient().simulateConnect({ muted: true, deviceName: 'Test', sounds: [] });
      expect(capabilities['volume_mute']).toBe(true);
    });
  });
});
