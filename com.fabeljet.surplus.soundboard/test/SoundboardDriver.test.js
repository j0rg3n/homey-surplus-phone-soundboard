'use strict';

jest.mock('homey', () => ({
  Driver: class {
    log() {}
    error() {}
  },
}));

const SoundboardDriver = require('../drivers/soundboard/driver');

function fakeCard() {
  const argument = { registerAutocompleteListener: jest.fn() };
  return {
    registerRunListener: jest.fn(),
    getArgument: jest.fn(() => argument),
  };
}

function createDriver({ getWidget } = {}) {
  const driver = new SoundboardDriver();
  const widget = { registerSettingAutocompleteListener: jest.fn() };
  driver.error = jest.fn();
  driver.getDevices = () => [
    { getName: () => 'Kitchen phone', getData: () => ({ id: '10.0.0.5:8765' }) },
    { getName: () => 'Hallway tablet', getData: () => ({ id: '10.0.0.6:8765' }) },
  ];
  driver.homey = {
    flow: {
      getActionCard: () => fakeCard(),
      getConditionCard: () => fakeCard(),
      getDeviceTriggerCard: () => fakeCard(),
    },
    dashboards: {
      getWidget: jest.fn(getWidget || (() => widget)),
    },
  };
  return { driver, widget };
}

describe('SoundboardDriver widget registration', () => {
  it('registers a device autocomplete for the soundboard widget', async () => {
    const { driver, widget } = createDriver();
    await driver.onInit();
    expect(driver.homey.dashboards.getWidget).toHaveBeenCalledWith('soundboard');
    expect(widget.registerSettingAutocompleteListener).toHaveBeenCalledWith('device', expect.any(Function));
  });

  it('autocomplete lists all soundboard devices for an empty query', async () => {
    const { driver, widget } = createDriver();
    await driver.onInit();
    const listener = widget.registerSettingAutocompleteListener.mock.calls[0][1];
    await expect(listener('')).resolves.toEqual([
      { id: '10.0.0.5:8765', name: 'Kitchen phone' },
      { id: '10.0.0.6:8765', name: 'Hallway tablet' },
    ]);
  });

  it('autocomplete filters devices by name, ignoring case', async () => {
    const { driver, widget } = createDriver();
    await driver.onInit();
    const listener = widget.registerSettingAutocompleteListener.mock.calls[0][1];
    await expect(listener('HALL')).resolves.toEqual([{ id: '10.0.0.6:8765', name: 'Hallway tablet' }]);
  });

  it('logs and carries on when the widget cannot be registered', async () => {
    const { driver } = createDriver({ getWidget: () => { throw new Error('Invalid widget'); } });
    await expect(driver.onInit()).resolves.toBeUndefined();
    expect(driver.error).toHaveBeenCalledWith('Could not register the soundboard widget:', 'Invalid widget');
  });
});
