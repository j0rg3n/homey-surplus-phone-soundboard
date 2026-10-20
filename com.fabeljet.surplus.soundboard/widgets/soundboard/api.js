'use strict';

const SoundboardWidgetApi = require('../../lib/SoundboardWidgetApi');

function widgetApi(homey) {
  return new SoundboardWidgetApi(() => homey.drivers.getDriver('soundboard').getDevices());
}

module.exports = {
  async getState({ homey, query }) {
    return widgetApi(homey).getState((query || {}).deviceId);
  },

  async toggleSound({ homey, body }) {
    return widgetApi(homey).toggleSound(body.deviceId, body.soundId);
  },

  async stopAll({ homey, body }) {
    return widgetApi(homey).stopAll(body.deviceId);
  },

  async setFavorite({ homey, body }) {
    return widgetApi(homey).setFavorite(body.deviceId, body.soundId, Boolean(body.favorite));
  },
};
