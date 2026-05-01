'use strict';

const PlaybackHandleStore = require('../lib/PlaybackHandleStore');

const ENTRY_A = { soundId: 'snd-1', soundName: 'beep', startedAt: 1000, durationMs: 500 };
const ENTRY_B = { soundId: 'snd-2', soundName: 'beep', startedAt: 2000, durationMs: 800 };
const ENTRY_C = { soundId: 'snd-3', soundName: 'boop', startedAt: 3000, durationMs: 200 };

function makeStore() {
  return new PlaybackHandleStore();
}

describe('PlaybackHandleStore.get', () => {
  test('returns the entry after add', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    expect(store.get('h1')).toEqual({ handle: 'h1', ...ENTRY_A });
  });

  test('returns null for an unknown handle', () => {
    const store = makeStore();
    expect(store.get('unknown')).toBeNull();
  });
});

describe('PlaybackHandleStore.getAll', () => {
  test('returns empty array when store is empty', () => {
    const store = makeStore();
    expect(store.getAll()).toEqual([]);
  });

  test('returns all added entries', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    store.add('h2', ENTRY_B);
    store.add('h3', ENTRY_C);
    const all = store.getAll();
    expect(all).toHaveLength(3);
    expect(all).toEqual(expect.arrayContaining([
      { handle: 'h1', ...ENTRY_A },
      { handle: 'h2', ...ENTRY_B },
      { handle: 'h3', ...ENTRY_C },
    ]));
  });
});

describe('PlaybackHandleStore.remove', () => {
  test('returns the removed entry and it is no longer accessible via get', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    const removed = store.remove('h1');
    expect(removed).toEqual({ handle: 'h1', ...ENTRY_A });
    expect(store.get('h1')).toBeNull();
  });

  test('returns null when removing an unknown handle', () => {
    const store = makeStore();
    expect(store.remove('nonexistent')).toBeNull();
  });

  test('does not affect other entries when one is removed', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    store.add('h2', ENTRY_C);
    store.remove('h1');
    expect(store.get('h2')).toEqual({ handle: 'h2', ...ENTRY_C });
  });
});

describe('PlaybackHandleStore.clear', () => {
  test('returns all entries and leaves the store empty', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    store.add('h2', ENTRY_C);
    const cleared = store.clear();
    expect(cleared).toHaveLength(2);
    expect(cleared).toEqual(expect.arrayContaining([
      { handle: 'h1', ...ENTRY_A },
      { handle: 'h2', ...ENTRY_C },
    ]));
    expect(store.getAll()).toEqual([]);
  });

  test('returns empty array and store stays empty when cleared while already empty', () => {
    const store = makeStore();
    expect(store.clear()).toEqual([]);
    expect(store.getAll()).toEqual([]);
  });
});

describe('PlaybackHandleStore.isPlaying', () => {
  test('returns true when any handle for that soundName exists', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    expect(store.isPlaying('beep')).toBe(true);
  });

  test('returns false when no handle for that soundName exists', () => {
    const store = makeStore();
    store.add('h1', ENTRY_C);
    expect(store.isPlaying('beep')).toBe(false);
  });

  test('returns false after the last handle for that soundName is removed', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    store.remove('h1');
    expect(store.isPlaying('beep')).toBe(false);
  });

  test('stays true until all handles for a soundName are removed', () => {
    const store = makeStore();
    store.add('h1', ENTRY_A);
    store.add('h2', ENTRY_B); // both are soundName 'beep'
    store.remove('h1');
    expect(store.isPlaying('beep')).toBe(true);
    store.remove('h2');
    expect(store.isPlaying('beep')).toBe(false);
  });

  test('returns false on an empty store', () => {
    const store = makeStore();
    expect(store.isPlaying('beep')).toBe(false);
  });
});
