'use strict';

class PlaybackHandleStore {
  constructor() {
    this._store = new Map();
  }

  add(handle, { soundId, soundName, startedAt, durationMs }) {
    this._store.set(handle, { handle, soundId, soundName, startedAt, durationMs });
  }

  get(handle) {
    return this._store.get(handle) ?? null;
  }

  getAll() {
    return Array.from(this._store.values());
  }

  remove(handle) {
    const entry = this._store.get(handle);
    if (entry === undefined) return null;
    this._store.delete(handle);
    return entry;
  }

  clear() {
    const all = this.getAll();
    this._store.clear();
    return all;
  }

  isPlaying(soundName) {
    for (const entry of this._store.values()) {
      if (entry.soundName === soundName) return true;
    }
    return false;
  }
}

module.exports = PlaybackHandleStore;
