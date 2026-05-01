'use strict';

const MessageProtocol = require('../lib/MessageProtocol');

describe('MessageProtocol.serialize', () => {
  test('returns JSON string of object', () => {
    const msg = { type: 'ping' };
    expect(MessageProtocol.serialize(msg)).toBe('{"type":"ping"}');
  });

  test('round-trips a play message', () => {
    const msg = { type: 'play', soundId: 'abc', volume: 100, handle: 'h1' };
    expect(JSON.parse(MessageProtocol.serialize(msg))).toEqual(msg);
  });
});

describe('MessageProtocol.deserialize', () => {
  test('returns parsed object for valid JSON with type', () => {
    const result = MessageProtocol.deserialize('{"type":"ping"}');
    expect(result).toEqual({ type: 'ping' });
  });

  test('throws on invalid JSON', () => {
    expect(() => MessageProtocol.deserialize('not json')).toThrow('Invalid JSON');
  });

  test('throws on valid JSON missing type', () => {
    expect(() => MessageProtocol.deserialize('{"foo":"bar"}')).toThrow('type');
  });

  test('throws on non-string type', () => {
    expect(() => MessageProtocol.deserialize('{"type":42}')).toThrow('type');
  });

  test('round-trips hello_ack with sounds array', () => {
    const msg = {
      type: 'hello_ack',
      deviceName: 'Galaxy A8',
      version: '1.0',
      sounds: [{ id: 'u1', name: 'Doorbell', durationMs: 2400, loop: false, loopStartMs: 0, loopEndMs: 2400, defaultVolume: 100 }],
    };
    expect(MessageProtocol.deserialize(MessageProtocol.serialize(msg))).toEqual(msg);
  });
});

describe('MessageProtocol.validate', () => {
  test('returns invalid when type is missing', () => {
    const r = MessageProtocol.validate({});
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: type');
  });

  test('returns invalid for unknown message type', () => {
    const r = MessageProtocol.validate({ type: 'bogus' });
    expect(r.valid).toBe(false);
    expect(r.errors[0]).toMatch(/Unknown message type/);
  });

  test.each([
    ['ping',  { type: 'ping' }],
    ['pong',  { type: 'pong' }],
    ['stop_all', { type: 'stop_all' }],
  ])('valid for %s with no extra fields', (_, msg) => {
    expect(MessageProtocol.validate(msg).valid).toBe(true);
  });

  test('valid play message', () => {
    const r = MessageProtocol.validate({ type: 'play', soundId: 'u1', volume: 100, handle: 'h1' });
    expect(r.valid).toBe(true);
  });

  test('invalid play missing handle', () => {
    const r = MessageProtocol.validate({ type: 'play', soundId: 'u1', volume: 100 });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: handle');
  });

  test('invalid play missing soundId', () => {
    const r = MessageProtocol.validate({ type: 'play', volume: 100, handle: 'h1' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: soundId');
  });

  test('valid stop message', () => {
    expect(MessageProtocol.validate({ type: 'stop', handle: 'h1' }).valid).toBe(true);
  });

  test('invalid stop missing handle', () => {
    const r = MessageProtocol.validate({ type: 'stop' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: handle');
  });

  test('valid hello', () => {
    expect(MessageProtocol.validate({ type: 'hello', version: '1.0' }).valid).toBe(true);
  });

  test('invalid hello missing version', () => {
    const r = MessageProtocol.validate({ type: 'hello' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: version');
  });

  test('valid hello_ack', () => {
    const r = MessageProtocol.validate({ type: 'hello_ack', deviceName: 'A8', version: '1.0', sounds: [] });
    expect(r.valid).toBe(true);
  });

  test('invalid hello_ack missing sounds', () => {
    const r = MessageProtocol.validate({ type: 'hello_ack', deviceName: 'A8', version: '1.0' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: sounds');
  });

  test('valid library_update', () => {
    expect(MessageProtocol.validate({ type: 'library_update', sounds: [] }).valid).toBe(true);
  });

  test('valid started', () => {
    const r = MessageProtocol.validate({ type: 'started', handle: 'h1', soundId: 'u1', soundName: 'Beep', durationMs: 1000 });
    expect(r.valid).toBe(true);
  });

  test('invalid started missing durationMs', () => {
    const r = MessageProtocol.validate({ type: 'started', handle: 'h1', soundId: 'u1', soundName: 'Beep' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: durationMs');
  });

  test('valid done', () => {
    const r = MessageProtocol.validate({ type: 'done', handle: 'h1', soundName: 'Beep', reason: 'completed' });
    expect(r.valid).toBe(true);
  });

  test('invalid done missing reason', () => {
    const r = MessageProtocol.validate({ type: 'done', handle: 'h1', soundName: 'Beep' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: reason');
  });

  test('valid error with null handle', () => {
    const r = MessageProtocol.validate({ type: 'error', handle: null, code: 'SOUND_NOT_FOUND', message: 'Not found' });
    expect(r.valid).toBe(true);
  });

  test('invalid error missing code', () => {
    const r = MessageProtocol.validate({ type: 'error', handle: null, message: 'oops' });
    expect(r.valid).toBe(false);
    expect(r.errors).toContain('Missing required field: code');
  });
});
