class MockHomeyDevice {
  constructor() {
    this._store = {};
    this._capabilities = {};
    this._flowTokens = {};
    this._flowTriggers = {};
    this._settings = {};
  }

  getStore() {
    return this._store;
  }

  setStore(key, value) {
    this._store[key] = value;
  }

  getStoreValue(key) {
    return this._store[key];
  }

  setCapabilityValue(capability, value) {
    this._capabilities[capability] = value;
  }

  getCapabilityValue(capability) {
    return this._capabilities[capability];
  }

  triggerFlowTrigger(triggerId, tokens) {
    const trigger = this._flowTriggers[triggerId];
    if (trigger) {
      trigger(tokens);
    }
  }

  onFlowTrigger(triggerId, callback) {
    this._flowTriggers[triggerId] = callback;
  }

  registerSetting(token) {
    this._settings[token.id] = token;
  }

  getSetting(tokenId) {
    return this._settings[tokenId];
  }

  homey() {
    return {
      flow: {
        getTriggerCard: () => ({ trigger: (cb) => this.onFlowTrigger.bind(this) }),
      },
    };
  }
}

module.exports = MockHomeyDevice;