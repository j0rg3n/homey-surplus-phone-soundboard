'use strict';

const SoundboardWidgetApi = require('../lib/SoundboardWidgetApi');
const { WIDGET_POLL_INTERVAL_MS } = require('../lib/constants');

const SOUNDS = [
  { id: 's-thunder', name: 'thunder', durationMs: 8000, loop: false },
  { id: 's-applause', name: 'Applause', durationMs: 5400, loop: false },
  { id: 's-rain', name: 'Rain', durationMs: 90000, loop: true },
  { id: 's-bell', name: 'Doorbell', durationMs: 2400, loop: false },
];

function fakeDevice({ id = '10.0.0.5:8765', name = 'Living room phone', available = true, store = {}, handles = [] } = {}) {
  const storeValues = { sounds: SOUNDS, ...store };
  return {
    storeValues,
    handles,
    getData: () => ({ id }),
    getName: () => name,
    getAvailable: () => available,
    getStoreValue: (key) => storeValues[key],
    setStoreValue: jest.fn(async (key, value) => { storeValues[key] = value; }),
    getPlayingHandles: () => handles,
    playSound: jest.fn(async () => 'new-handle'),
    stopSound: jest.fn(async () => {}),
    stopAll: jest.fn(async () => {}),
  };
}

describe('SoundboardWidgetApi', () => {
  describe('formatDuration', () => {
    it('formats milliseconds as m:ss', () => {
      expect(SoundboardWidgetApi.formatDuration(2400)).toBe('0:02');
      expect(SoundboardWidgetApi.formatDuration(90000)).toBe('1:30');
      expect(SoundboardWidgetApi.formatDuration(605000)).toBe('10:05');
    });

    it('rounds down to whole seconds, like the Android Library screen', () => {
      expect(SoundboardWidgetApi.formatDuration(2999)).toBe('0:02');
      expect(SoundboardWidgetApi.formatDuration(5400)).toBe('0:05');
    });

    it('treats a missing duration as 0:00', () => {
      expect(SoundboardWidgetApi.formatDuration(undefined)).toBe('0:00');
    });
  });

  describe('buildSoundList', () => {
    it('sorts alphabetically, ignoring case', () => {
      const list = SoundboardWidgetApi.buildSoundList(SOUNDS, [], []);
      expect(list.map((s) => s.name)).toEqual(['Applause', 'Doorbell', 'Rain', 'thunder']);
    });

    it('lists favorites first, each group sorted alphabetically', () => {
      const list = SoundboardWidgetApi.buildSoundList(SOUNDS, ['s-thunder', 's-bell'], []);
      expect(list.map((s) => s.name)).toEqual(['Doorbell', 'thunder', 'Applause', 'Rain']);
      expect(list.map((s) => s.favorite)).toEqual([true, true, false, false]);
    });

    it('marks sounds with an in-flight handle as playing', () => {
      const list = SoundboardWidgetApi.buildSoundList(SOUNDS, [], ['s-rain']);
      expect(list.find((s) => s.id === 's-rain').playing).toBe(true);
      expect(list.filter((s) => s.playing)).toHaveLength(1);
    });

    it('returns the row fields the widget renders', () => {
      const [rain] = SoundboardWidgetApi.buildSoundList([SOUNDS[2]], [], []);
      expect(rain).toEqual({
        id: 's-rain', name: 'Rain', duration: '1:30', loop: true, favorite: false, playing: false,
      });
    });

    it('ignores favorite IDs of sounds no longer in the library', () => {
      const list = SoundboardWidgetApi.buildSoundList(SOUNDS, ['gone'], []);
      expect(list).toHaveLength(4);
      expect(list.some((s) => s.favorite)).toBe(false);
    });
  });

  describe('findDevice', () => {
    it('finds a device by its data id', () => {
      const a = fakeDevice({ id: 'a' });
      const b = fakeDevice({ id: 'b' });
      const api = new SoundboardWidgetApi(() => [a, b]);
      expect(api.findDevice('b')).toBe(b);
    });

    it('falls back to the first device when no id is given', () => {
      const a = fakeDevice({ id: 'a' });
      const api = new SoundboardWidgetApi(() => [a, fakeDevice({ id: 'b' })]);
      expect(api.findDevice(undefined)).toBe(a);
    });

    it('throws when the device does not exist', () => {
      const api = new SoundboardWidgetApi(() => [fakeDevice({ id: 'a' })]);
      expect(() => api.findDevice('missing')).toThrow('No soundboard device found');
    });
  });

  describe('getState', () => {
    it('returns device info, poll interval and the sorted sound list', () => {
      const device = fakeDevice({
        store: { favorites: ['s-rain'] },
        handles: [{ handle: 'h1', soundId: 's-bell' }],
      });
      const state = new SoundboardWidgetApi(() => [device]).getState('10.0.0.5:8765');
      expect(state.deviceId).toBe('10.0.0.5:8765');
      expect(state.deviceName).toBe('Living room phone');
      expect(state.available).toBe(true);
      expect(state.pollIntervalMs).toBe(WIDGET_POLL_INTERVAL_MS);
      expect(state.sounds[0]).toMatchObject({ id: 's-rain', favorite: true });
      expect(state.sounds.find((s) => s.id === 's-bell').playing).toBe(true);
      expect(state.error).toBeUndefined();
    });

    it('returns an empty list when the device has no stored sounds', () => {
      const device = fakeDevice({ store: { sounds: undefined } });
      expect(new SoundboardWidgetApi(() => [device]).getState().sounds).toEqual([]);
    });

    it('reports offline devices as unavailable', () => {
      const device = fakeDevice({ available: false });
      expect(new SoundboardWidgetApi(() => [device]).getState().available).toBe(false);
    });

    it('returns an empty state with an error instead of throwing when no device exists', () => {
      const state = new SoundboardWidgetApi(() => []).getState();
      expect(state).toEqual({
        deviceId: null,
        deviceName: null,
        available: false,
        pollIntervalMs: WIDGET_POLL_INTERVAL_MS,
        sounds: [],
        error: 'No soundboard device found',
      });
    });
  });

  describe('toggleSound', () => {
    it('plays the sound when it is not playing', async () => {
      const device = fakeDevice();
      const result = await new SoundboardWidgetApi(() => [device]).toggleSound(undefined, 's-bell');
      expect(device.playSound).toHaveBeenCalledWith('s-bell');
      expect(device.stopSound).not.toHaveBeenCalled();
      expect(result).toEqual({ playing: true });
    });

    it('stops every in-flight handle of the sound when it is playing', async () => {
      const device = fakeDevice({
        handles: [
          { handle: 'h1', soundId: 's-bell' },
          { handle: 'h2', soundId: 's-rain' },
          { handle: 'h3', soundId: 's-bell' },
        ],
      });
      const result = await new SoundboardWidgetApi(() => [device]).toggleSound(undefined, 's-bell');
      expect(device.stopSound.mock.calls).toEqual([['h1'], ['h3']]);
      expect(device.playSound).not.toHaveBeenCalled();
      expect(result).toEqual({ playing: false });
    });

    it('refuses when the device is offline', async () => {
      const device = fakeDevice({ available: false });
      await expect(new SoundboardWidgetApi(() => [device]).toggleSound(undefined, 's-bell'))
        .rejects.toThrow('Soundboard is offline');
      expect(device.playSound).not.toHaveBeenCalled();
    });

    it('refuses when no device exists', async () => {
      await expect(new SoundboardWidgetApi(() => []).toggleSound(undefined, 's-bell'))
        .rejects.toThrow('No soundboard device found');
    });
  });

  describe('stopAll', () => {
    it('sends stop_all through the device', async () => {
      const device = fakeDevice();
      await expect(new SoundboardWidgetApi(() => [device]).stopAll()).resolves.toEqual({});
      expect(device.stopAll).toHaveBeenCalledTimes(1);
    });

    it('refuses when the device is offline', async () => {
      const device = fakeDevice({ available: false });
      await expect(new SoundboardWidgetApi(() => [device]).stopAll()).rejects.toThrow('Soundboard is offline');
      expect(device.stopAll).not.toHaveBeenCalled();
    });
  });

  describe('setFavorite', () => {
    it('adds a favorite to the device store', async () => {
      const device = fakeDevice();
      const result = await new SoundboardWidgetApi(() => [device]).setFavorite(undefined, 's-rain', true);
      expect(device.setStoreValue).toHaveBeenCalledWith('favorites', ['s-rain']);
      expect(result).toEqual({ favorites: ['s-rain'] });
    });

    it('removes a favorite from the device store', async () => {
      const device = fakeDevice({ store: { favorites: ['s-rain', 's-bell'] } });
      await new SoundboardWidgetApi(() => [device]).setFavorite(undefined, 's-rain', false);
      expect(device.storeValues.favorites).toEqual(['s-bell']);
    });

    it('does not add the same favorite twice', async () => {
      const device = fakeDevice({ store: { favorites: ['s-rain'] } });
      await new SoundboardWidgetApi(() => [device]).setFavorite(undefined, 's-rain', true);
      expect(device.storeValues.favorites).toEqual(['s-rain']);
    });

    it('works while the device is offline', async () => {
      const device = fakeDevice({ available: false });
      await new SoundboardWidgetApi(() => [device]).setFavorite(undefined, 's-bell', true);
      expect(device.storeValues.favorites).toEqual(['s-bell']);
    });
  });
});
