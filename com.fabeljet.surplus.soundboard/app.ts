'use strict';

import Homey from 'homey';

class MyApp extends Homey.App {

  async onInit() {
    this.log('Surplus Soundboard app has been initialized');
  }

}

module.exports = new MyApp();