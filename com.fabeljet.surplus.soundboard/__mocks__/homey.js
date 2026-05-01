'use strict';

// Manual mock for the 'homey' Homey SDK package.
// This allows jest.mock('homey', factory) to work when homey is not installed.

class Device {
  log() {}
  error() {}
  async setAvailable() {}
  async setUnavailable() {}
  async setCapabilityValue() {}
  async setStoreValue() {}
  getStoreValue() {}
  registerCapabilityListener() {}
}

class App {
  log() {}
  error() {}
}

module.exports = { Device, App };
