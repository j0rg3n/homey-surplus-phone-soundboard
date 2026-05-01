'use strict';

module.exports = class MyApp extends require('homey').App {

  async onInit() {
    this.log('Homey Soundboard app has been initialized');
  }

};