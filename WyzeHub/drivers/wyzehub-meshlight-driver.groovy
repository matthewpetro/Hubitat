/*
 * Import URL: https://raw.githubusercontent.com/jakelehner/Hubitat/master/WyzeHub/drivers/wyzehub-meshlight-driver.groovy
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
import hubitat.helper.ColorUtils

public static String version() { return 'v1.4'  }

public String deviceModel() { return device.getDataValue('product_model') ?: 'WLPA19C' }

@Field static final String wyze_property_power = 'P3'
@Field static final String wyze_property_device_online = 'P5'
@Field static final String wyze_property_brightness = 'P1501'
@Field static final String wyze_property_color_temp = 'P1502'
@Field static final String wyze_property_rssi = 'P1504'
@Field static final String wyze_property_remaing_time = 'P1505'
@Field static final String wyze_property_vacation_mode = 'P1506'
@Field static final String wyze_property_color = 'P1507'
@Field static final String wyze_property_color_mode = 'P1508'
@Field static final String wyze_property_power_loss_recovery = 'P1509'
@Field static final String wyze_property_delay_off = 'P1510'

@Field static final String wyze_property_power_value_on = '1'
@Field static final String wyze_property_power_value_off = '0'
@Field static final String wyze_property_device_online_value_true = '1'
@Field static final String wyze_property_device_online_value_false = '0'
@Field static final String wyze_property_device_vacation_mode_value_true = '1'
@Field static final String wyze_property_device_vacation_mode_value_false = '0'
@Field static final String wyze_property_color_mode_value_ct = '2'
@Field static final String wyze_property_color_mode_value_rgb = '1'

import groovy.transform.Field

metadata {
  definition(
    name: 'WyzeHub Color Bulb',
    namespace: 'jakelehner',
    author: 'Jake Lehner',
    importUrl: 'https://raw.githubusercontent.com/jakelehner/Hubitat/master/WyzeHub/drivers/wyzehub-meshlight-driver.groovy'
  ) {
      capability 'Light'
      capability 'SwitchLevel'
      capability 'ColorTemperature'
      capability 'ColorControl'
      capability 'ColorMode'
      capability 'Refresh'
      capability 'Switch'
      // capability "LightEffects"

      command(
        'setColorHEX',
        [
          [
            'name': 'HEX Color*',
            'type': 'STRING',
            'description': 'Color in HEX no #'
          ]
        ]
      )
      // command "toggleVacationMode"
      // command "flashOnce"

      attribute 'vacationMode', 'bool'
      attribute 'online', 'bool'
      attribute 'rssi', 'number'
      // attrubute "lastRefreshed", "date"

      preferences {
        input name: 'refreshInterval', type: 'number', title: 'Refresh Interval', description: 'Number of minutes between automatic refreshes of device state. 0 means no automatic refresh', required: false, defaultValue: 5, range: 0..60
      }
  }
}

void installed() {
  logDebug('installed()')
  initialize()
  refresh()
}

void uninstalled() {
  log.debug 'uninstalled()'
  unschedule('refresh')
}

void updated() {
  logDebug('updated()')
  initialize()
}

void initialize() {
  logDebug('initialize()')
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
  actions = [
    [
      'pid': wyze_property_power,
      'pvalue': wyze_property_power_value_on
    ]
  ]

  app.apiRunActionList(device.deviceNetworkId, deviceModel(), actions)
}

def off() {
  app = getApp()
  logInfo("'Off' Pressed")
  actions = [
    [
      'pid': wyze_property_power,
      'pvalue': wyze_property_power_value_off
    ]
  ]

  app.apiRunActionList(device.deviceNetworkId, deviceModel(), actions)
}

def setLevel(level, durationSecs = null) {
  app = getApp()
  logInfo('setLevel() Pressed')

  level = level.min(100).max(0)

  actions = [
    [
      'pid': wyze_property_brightness,
      'pvalue': level.toString()
    ]
  ]

  app.apiRunActionList(device.deviceNetworkId, deviceModel(), actions)
}

def setColorTemperature(colorTemperature, level = null, durationSecs = null) {
  app = getApp()
  logInfo('setColorTemperature() Pressed')

  // Valid range 1800-6500
  colorTemperature = colorTemperature.min(6500).max(1800)

  actions = [
    [
      'pid': wyze_property_color_temp,
      'pvalue': colorTemperature.toString()
    ]
  ]

  if (level) {
    actions << [
      'pid': wyze_property_brightness,
      'pvalue': level.toString()
    ]
  }

  logDebug(actions)

  app.apiRunActionList(device.deviceNetworkId, deviceModel(), actions)
}

def setColor(colormap) {
  logInfo('setColor() Pressed')
  setWyzeColor(hsvToHexNoHash(colormap.hue, colormap.saturation, colormap.level))
}

def setColorHEX(String hexColor) {
  logInfo('setColorHEX() Pressed')
  setWyzeColor(hexColor)
}

def setHue(hue) {
  logInfo('setHue() Pressed')

  // Must be between 0 and 100
  newHue = hue.min(100).max(0)
  currentHsv = hexToHsv(device.currentValue('color'))
  currentSaturation = currentHsv[1]
  currentLevel = currentHsv[2]

  setWyzeColor(hsvToHexNoHash(newHue, currentSaturation, currentLevel))
}

def setSaturation(saturation) {
  logInfo('setSaturation() Pressed')

  // Must be between 0 and 100
  newSaturation = saturation.min(100).max(0)
  currentHsv = hexToHsv(device.currentValue('color'))
  currentHue = currentHsv[0]
  currentLevel = currentHsv[2]

  setWyzeColor(hsvToHexNoHash(currentHue, newSaturation, currentLevel))
}

private def setWyzeColor(String hexColor) {
  app = getApp()

  logDebug('Setting color to HEX ' + hexColor)

  actions = [
    [
      'pid': wyze_property_color,
      'pvalue': hexColor
    ]
  ]

  app.apiRunActionList(device.deviceNetworkId, deviceModel(), actions)
}

void createDeviceEventsFromPropertyList(List propertyList) {
  app = getApp()
  logDebug('createEventsFromPropertyList()')

  String eventName, eventUnit, deviceColorMode
  def eventValue // could be String or number

  property = propertyList.find { property -> property.pid == wyze_property_color_mode }
  if (null != property) {
    propertyValue = property.value ?: property.pvalue ?: null
    deviceColorMode = (propertyValue == '1' ? 'RGB' : 'CT')

    if (device.hasCapability('ColorMode')) {
      eventName = 'colorMode'
      eventUnit = null
      eventValue = deviceColorMode

      if (device.currentValue(eventName) != eventValue) {
        logInfo("Updating Property 'colorMode' to ${eventValue}")
        app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
      }
    }
  }

  propertyList.each { property ->
    propertyValue = property.value ?: property.pvalue ?: null
    if (property.pid == wyze_property_color && deviceColorMode == 'RGB') {
      // Set color
      sendDeviceEvent(app, 'color', propertyValue, null)

      hsv = hexToHsv(propertyValue)
      hue = hsv[0]
      saturation = hsv[1]

      // Set Hue
      sendDeviceEvent(app, 'hue', hue, null)

      // Set Saturation
      sendDeviceEvent(app, 'saturation', saturation, null)
    }
    switch (property.pid) {
      // Switch State
      case wyze_property_power:
        eventName = 'switch'
        eventUnit = null
        eventValue = propertyValue == wyze_property_power_value_on ? 'on' : 'off'
        break

      // Device Online
      case wyze_property_device_online:
        eventName = 'online'
        eventUnit = null
        eventValue = propertyValue == wyze_property_device_online_value_true ? 'true' : 'false'
        break

      // Brightness
      case wyze_property_brightness:
        eventName = 'level'
        eventUnit = '%'
        eventValue = propertyValue
        break

      // Color Temp
      case wyze_property_color_temp:
        if (deviceColorMode == 'CT') {
          // Set Temperature
          eventName = 'colorTemperature'
          eventUnit = '°K'
          eventValue = propertyValue
        }
        break

      // RSSI
      case wyze_property_rssi:
        eventName = 'rssi'
        eventUnit = 'db'
        eventValue = propertyValue
        break

      // Vacation Mode
      case wyze_property_vacation_mode:
        eventName = 'vacationMode'
        eventUnit = null
        eventValue = propertyValue == wyze_property_device_vacation_mode_true ? 'true' : 'false'
        break
    }
    sendDeviceEvent(app, eventName, eventValue, eventUnit)
  }
}

private sendDeviceEvent(app, eventName, eventValue, eventUnit) {
  if (device.currentValue(eventName) != eventValue) {
    logDebug("Updating Property '${eventName}' to ${eventValue}")
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
  }
}

private def hexToHsv(String hex) {
  if (hex[0] != '#') {
    hex = '#' + hex
  }
  rgb = hubitat.helper.ColorUtils.hexToRGB(hex)
  hsv = hubitat.helper.ColorUtils.rgbToHSV(rgb)
  return hsv
}

private def String hsvToHexNoHash(hue, saturation, level) {
  rgb = hubitat.helper.ColorUtils.hsvToRGB([hue, saturation, level])
  return hubitat.helper.ColorUtils.rgbToHEX(rgb).substring(1)
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
