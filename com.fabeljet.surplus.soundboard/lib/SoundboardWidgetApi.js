'use strict';

const { WIDGET_POLL_INTERVAL_MS } = require('./constants');

const FAVORITES_KEY = 'favorites';

function formatDuration(durationMs) {
  // Rounds down, like the Android Library screen.
  const totalSeconds = Math.floor((Number(durationMs) || 0) / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function compareByName(a, b) {
  return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
}

// Favorites first, then alphabetical within each group (SPEC §8.6).
function buildSoundList(sounds, favoriteIds, playingSoundIds) {
  const favorites = new Set(favoriteIds);
  const playing = new Set(playingSoundIds);
  return sounds
    .map((s) => ({
      id: s.id,
      name: s.name,
      duration: formatDuration(s.durationMs),
      loop: Boolean(s.loop),
      favorite: favorites.has(s.id),
      playing: playing.has(s.id),
    }))
    .sort((a, b) => {
      if (a.favorite !== b.favorite) return a.favorite ? -1 : 1;
      return compareByName(a, b);
    });
}

class SoundboardWidgetApi {

  /**
   * @param {() => object[]} getDevices returns the paired SoundboardDevice instances
   */
  constructor(getDevices) {
    this._getDevices = getDevices;
  }

  findDevice(deviceId) {
    const devices = this._getDevices();
    const device = deviceId
      ? devices.find((d) => d.getData().id === deviceId)
      : devices[0];
    if (!device) throw new Error('No soundboard device found');
    return device;
  }

  getState(deviceId) {
    let device;
    try {
      device = this.findDevice(deviceId);
    } catch (err) {
      // Returned rather than thrown so the widget keeps polling until a device is paired.
      return {
        deviceId: null,
        deviceName: null,
        available: false,
        pollIntervalMs: WIDGET_POLL_INTERVAL_MS,
        sounds: [],
        error: err.message,
      };
    }
    const sounds = device.getStoreValue('sounds') ?? [];
    const favorites = device.getStoreValue(FAVORITES_KEY) ?? [];
    const playingIds = device.getPlayingHandles().map((h) => h.soundId);
    return {
      deviceId: device.getData().id,
      deviceName: device.getName(),
      available: device.getAvailable(),
      pollIntervalMs: WIDGET_POLL_INTERVAL_MS,
      sounds: buildSoundList(sounds, favorites, playingIds),
    };
  }

  async toggleSound(deviceId, soundId) {
    const device = this._findOnlineDevice(deviceId);
    const handles = device.getPlayingHandles().filter((h) => h.soundId === soundId);
    if (handles.length > 0) {
      for (const h of handles) await device.stopSound(h.handle);
      return { playing: false };
    }
    await device.playSound(soundId);
    return { playing: true };
  }

  async stopAll(deviceId) {
    const device = this._findOnlineDevice(deviceId);
    await device.stopAll();
    return {};
  }

  async setFavorite(deviceId, soundId, favorite) {
    const device = this.findDevice(deviceId);
    const current = (device.getStoreValue(FAVORITES_KEY) ?? []).filter((id) => id !== soundId);
    const favorites = favorite ? [...current, soundId] : current;
    await device.setStoreValue(FAVORITES_KEY, favorites);
    return { favorites };
  }

  _findOnlineDevice(deviceId) {
    const device = this.findDevice(deviceId);
    if (!device.getAvailable()) throw new Error('Soundboard is offline');
    return device;
  }

}

SoundboardWidgetApi.formatDuration = formatDuration;
SoundboardWidgetApi.buildSoundList = buildSoundList;

module.exports = SoundboardWidgetApi;
