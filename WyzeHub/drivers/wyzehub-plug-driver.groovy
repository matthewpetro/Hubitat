/*
 * Import URL: https://raw.githubusercontent.com/jakelehner/Hubitat/master/WyzeHub/drivers/wyzehub-plug-driver.groovy
 *
 * DON'T BE A DICK PUBLIC LICENSE
 *
 * Version 1.1, December 2016
 *
 * Copyright (C) 2021 Jake Lehner
 *
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document.
 *
 * DON'T BE A DICK PUBLIC LICENSE
 * TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 *
 * 1. Do whatever you like with the original work, just don't be a dick.
 *
 *    Being a dick includes - but is not limited to - the following instances:
 *
 *    1a. Outright copyright infringement - Don't just copy this and change the name.
 *    1b. Selling the unmodified original with no work done what-so-ever, that's REALLY being a dick.
 *    1c. Modifying the original work to contain hidden harmful content. That would make you a PROPER dick.
 *
 * 2. If you become rich through modifications, related works/services, or supporting the original work,
 *    share the love. Only a dick would make loads off this work and not buy the original work's
 *    creator(s) a pint.
 *
 * 3. Code is provided with no warranty. Using somebody else's code and bitching when it goes wrong makes
 *    you a DONKEY dick. Fix the problem yourself. A non-dick would submit the fix back.
 *
 */

import groovy.transform.Field

public static String version() {  return 'v1.4'  }

public String deviceModel() { return device.getDataValue('product_model') ?: 'WLPP1CFH' }

@Field static final String wyze_action_power_on = 'power_on'
@Field static final String wyze_action_power_off = 'power_off'

@Field static final String wyze_property_power = 'P3'
@Field static final String wyze_property_device_online = 'P5'
@Field static final String wyze_property_rssi = 'P1612'
@Field static final String wyze_property_vacation_mode = 'P1614'

@Field static final String wyze_property_power_value_on = '1'
@Field static final String wyze_property_power_value_off = '0'
@Field static final String wyze_property_device_online_value_true = '1'
@Field static final String wyze_property_device_online_value_false = '0'
@Field static final String wyze_property_device_vacation_mode_value_true = '1'
@Field static final String wyze_property_device_vacation_mode_value_false = '0'

metadata {
  definition(
    name: 'WyzeHub Plug',
    namespace: 'jakelehner',
    author: 'Jake Lehner',
    importUrl: 'https://raw.githubusercontent.com/jakelehner/Hubitat/master/WyzeHub/drivers/wyzehub-plug-driver.groovy'
  ) {
    capability 'Outlet'
    capability 'Switch'
    capability 'Refresh'

    attribute 'vacationMode', 'bool'
    attribute 'online', 'bool'
    attribute 'rssi', 'number'
  }

  preferences {
    input name: 'refreshInterval', type: 'number', title: 'Refresh Interval', description: 'Number of minutes between automatic refreshes of the device info. 0 means no automatic refresh.', required: false, defaultValue: 5, range: 0..60
  }
}

void installed() {
  log.debug 'installed()'
  initialize()
  refresh()
}

void uninstalled() {
  log.debug 'uninstalled()'
  unschedule('refresh')
}

void updated() {
  log.debug 'updated()'
  initialize()
}

void initialize() {
  log.debug 'initialize()'
  unschedule('refresh')
  if (refreshInterval > 0) {
    schedule("0 0/${refreshInterval} * * * ? *", 'refresh')
  }
}

void parse(String description) {
  log.warn("Running unimplemented parse for: '${description}'")
}

def refresh() {
  app = getApp()
  logInfo('Refresh Device')
  app.apiGetDevicePropertyList(device.deviceNetworkId, deviceModel())
}

def on() {
  app = getApp()
  logInfo("'On' Pressed")

  app.apiSetDeviceProperty(device.deviceNetworkId, deviceModel(), wyze_property_power, wyze_property_power_value_on)
}

def off() {
  app = getApp()
  logInfo("'Off' Pressed")

  app.apiSetDeviceProperty(device.deviceNetworkId, deviceModel(), wyze_property_power, wyze_property_power_value_off)
}

void createDeviceEventsFromPropertyList(List propertyList) {
  app = getApp()
  logDebug('createEventsFromPropertyList()')

  def eventValue // could be String or number

  propertyList.each { property ->
    propertyValue = property.value ?: property.pvalue ?: null
    switch (property.pid) {
      // Switch State
      case wyze_action_power_on:
      case wyze_action_power_off:
      case wyze_property_power:
        eventValue = (propertyValue == wyze_property_power_value_on || propertyValue == wyze_action_power_on) ? 'on' : 'off'
        sendDeviceEvent(app, 'switch', eventValue, null)
        break

      // Device Online
      case wyze_property_device_online:
        eventValue = propertyValue == wyze_property_device_online_value_true ? 'true' : 'false'
        sendDeviceEvent(app, 'online', eventValue, null)
        break

      // RSSI
      case wyze_property_rssi:
        eventValue = propertyValue as int
        sendDeviceEvent(app, 'rssi', eventValue, 'db')
        break

      // Vacation Mode
      case wyze_property_vacation_mode:
        eventValue = propertyValue == wyze_property_device_vacation_mode_value_true ? 'true' : 'false'
        sendDeviceEvent(app, 'vacationMode', eventValue, null)
        break
    }
  }
}

private def sendDeviceEvent(app, eventName, eventValue, eventUnit) {
  if (device.currentValue(eventName) != eventValue) {
    logDebug("Updating Property '${eventName}' to ${eventValue}")
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
  }
}

private getApp() {
  app = getParent()
  while (app && app.name != 'WyzeHub') {
    app = app.getParent()
  }
  return app
}

private void logDebug(message) {
  app = getApp()
  app.logDebug("[${device.label}] " + message)
}

private void logInfo(message) {
  app = getApp()
  app.logInfo("[${device.label}] " + message)
}

private void logWarn(message) {
  app = getApp()
  app.logWarn("[${device.label}] " + message)
}

private void logError(message) {
  app = getApp()
  app.logError("[${device.label}] " + message)
}
