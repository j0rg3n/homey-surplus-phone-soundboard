'use strict';

/**
 * Dashboard widget (SPEC §8.6) — widgets/soundboard/api.js driven against a real
 * SoundboardDevice backed by MockWebSocketClient.
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
const widgetApi = require('../widgets/soundboard/api');
const { MSG, DONE_REASON } = require('../lib/constants');

const SOUNDS = [
  { id: 's-bell', name: 'Doorbell', durationMs: 2400, loop: false },
  { id: 's-rain', name: 'Rain', durationMs: 90000, loop: true },
];

async function createSession({ id = '10.0.0.5:8765', name = 'Living room phone' } = {}) {
  const device = new SoundboardDevice();
  const storeValues = { ip: '10.0.0.5', port: 8765, sounds: SOUNDS };
  let available = true;
  let client;

  device.getStore = () => storeValues;
  device.getStoreValue = (key) => storeValues[key];
  device.setStoreValue = jest.fn(async (key, value) => { storeValues[key] = value; });
  device.setCapabilityValue = jest.fn(async () => {});
  device.setAvailable = jest.fn(async () => { available = true; });
  device.setUnavailable = jest.fn(async () => { available = false; });
  device.getAvailable = () => available;
  device.getData = () => ({ id });
  device.getName = () => name;
  device.error = jest.fn();
  device.registerCapabilityListener = jest.fn();
  device.homey = {
    flow: { getDeviceTriggerCard: () => ({ trigger: jest.fn(async () => {}) }) },
  };
  device._createClient = (opts) => {
    client = new MockWebSocketClient(opts);
    return client;
  };

  await device.onInit();
  // onConnect from the mock delivers an empty library; restore the test library.
  storeValues.sounds = SOUNDS;
  return { device, storeValues, getClient: () => client };
}

function homeyWith(devices) {
  return {
    drivers: {
      getDriver: jest.fn((driverId) => {
        if (driverId !== 'soundboard') throw new Error(`unexpected driver ${driverId}`);
        return { getDevices: () => devices };
      }),
    },
  };
}

describe('soundboard widget API', () => {
  it('getState returns the device library with nothing playing', async () => {
    const { device } = await createSession();
    const state = await widgetApi.getState({ homey: homeyWith([device]), query: {} });
    expect(state.deviceName).toBe('Living room phone');
    expect(state.available).toBe(true);
    expect(state.sounds.map((s) => [s.name, s.duration, s.loop, s.playing]))
      .toEqual([['Doorbell', '0:02', false, false], ['Rain', '1:30', true, false]]);
  });

  it('getState works without a query object', async () => {
    const { device } = await createSession();
    const state = await widgetApi.getState({ homey: homeyWith([device]) });
    expect(state.deviceId).toBe('10.0.0.5:8765');
  });

  it('getState picks the device named in the query', async () => {
    const first = await createSession({ id: 'a', name: 'Kitchen' });
    const second = await createSession({ id: 'b', name: 'Hallway' });
    const state = await widgetApi.getState({
      homey: homeyWith([first.device, second.device]),
      query: { deviceId: 'b' },
    });
    expect(state.deviceName).toBe('Hallway');
  });

  it('first tap sends play, started marks the row playing, second tap sends stop for that handle', async () => {
    const { device, getClient } = await createSession();
    const homey = homeyWith([device]);

    await expect(widgetApi.toggleSound({ homey, body: { soundId: 's-bell' } }))
      .resolves.toEqual({ playing: true });
    const play = getClient().getLastSentMessage();
    expect(play.type).toBe(MSG.PLAY);
    expect(play.soundId).toBe('s-bell');
    expect(play.volume).toBe(100);

    getClient().simulateMessage({
      type: MSG.STARTED, handle: play.handle, soundId: 's-bell', soundName: 'Doorbell', durationMs: 2400,
    });
    const state = await widgetApi.getState({ homey, query: {} });
    expect(state.sounds.find((s) => s.id === 's-bell').playing).toBe(true);

    await expect(widgetApi.toggleSound({ homey, body: { soundId: 's-bell' } }))
      .resolves.toEqual({ playing: false });
    expect(getClient().getLastSentMessage()).toEqual({ type: MSG.STOP, handle: play.handle });
  });

  it('a row stops showing as playing once done arrives', async () => {
    const { device, getClient } = await createSession();
    const homey = homeyWith([device]);
    getClient().simulateMessage({
      type: MSG.STARTED, handle: 'h1', soundId: 's-rain', soundName: 'Rain', durationMs: 0,
    });
    getClient().simulateMessage({
      type: MSG.DONE, handle: 'h1', soundName: 'Rain', reason: DONE_REASON.COMPLETED,
    });
    const state = await widgetApi.getState({ homey, query: {} });
    expect(state.sounds.some((s) => s.playing)).toBe(false);
  });

  it('stopAll sends stop_all', async () => {
    const { device, getClient } = await createSession();
    await widgetApi.stopAll({ homey: homeyWith([device]), body: {} });
    expect(getClient().getLastSentMessage()).toEqual({ type: MSG.STOP_ALL });
  });

  it('setFavorite persists favorites in the device store and lists them first', async () => {
    const { device, storeValues } = await createSession();
    const homey = homeyWith([device]);
    await widgetApi.setFavorite({ homey, body: { soundId: 's-rain', favorite: true } });
    expect(storeValues.favorites).toEqual(['s-rain']);
    const state = await widgetApi.getState({ homey, query: {} });
    expect(state.sounds.map((s) => s.id)).toEqual(['s-rain', 's-bell']);

    await widgetApi.setFavorite({ homey, body: { soundId: 's-rain' } });
    expect(storeValues.favorites).toEqual([]);
  });

  it('refuses to play after the connection drops, without sending anything', async () => {
    const { device, getClient } = await createSession();
    getClient().simulateDisconnect();
    const sentBefore = getClient().getSentMessages().length;
    await expect(widgetApi.toggleSound({ homey: homeyWith([device]), body: { soundId: 's-bell' } }))
      .rejects.toThrow('Soundboard is offline');
    expect(getClient().getSentMessages()).toHaveLength(sentBefore);
  });
});
