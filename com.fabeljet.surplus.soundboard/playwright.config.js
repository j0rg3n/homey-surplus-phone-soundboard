'use strict';

const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './test/e2e',
  use: {
    headless: true,
  },
  reporter: 'list',
});
