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

import groovy.json.JsonBuilder
import groovy.transform.Field
import java.security.MessageDigest


public static String version() {  return 'v1.4'  }

public String deviceModel() { return device.getDataValue('product_model') ?: 'YD.LO1' }

@Field static final String hubitat_device_value_locked = 'locked'
@Field static final String hubitat_device_value_unlocked = 'unlocked'

@Field static final String wyze_property_lock_state = 'P3'
@Field static final String wyze_property_device_online = 'P5'

@Field static final String wyze_property_lock_state_value_unlocked = '1'
@Field static final String wyze_property_lock_state_value_locked = '0'
@Field static final String wyze_property_device_online_value_true = '1'
@Field static final String wyze_property_device_online_value_false = '0'

String wyzeLockBaseUrl() { return 'https://yd-saas-toc.wyzecam.com' }
String wyzeLockControlPath() { return '/openapi/lock/v1/control' }
String appKey() { return '275965684684dbdaf29a0ed9' }
String appSecret() { return '4deekof1ba311c5c33a9cb8e12787e8c' }

metadata {
  definition(
    name: 'WyzeHub Lock',
    namespace: 'jakelehner',
    author: 'Matthew Petro',
    importUrl: 'https://raw.githubusercontent.com/jakelehner/Hubitat/master/WyzeHub/drivers/wyzehub-lock-driver.groovy'
  ) {
    capability 'Lock'
    capability 'Refresh'

    attribute 'online', 'bool'
  }

  preferences {
    input name: 'refreshInterval', type: 'number', title: 'Refresh Interval', description: 'Number of minutes between automatic refreshes of device state. 0 means no automatic refresh', required: false, defaultValue: 5, range: 0..60
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

def lock() {
  app = getApp()
  logInfo("'Lock' Pressed")

  // Lock the lock
  body = [
    'access_token': app.getAccessToken(),
    'action': 'remoteLock',
    'key': appKey(),
    'timestamp': (new Date()).getTime(),
    'uuid': getDeviceUuid(device.deviceNetworkId, deviceModel)
  ]

  // Sign the request
  body.sign = createRequestSignature(body, wyzeLockControlPath(), appSecret())

  bodyJson = (new JsonBuilder(body)).toString()

  params = [
    'uri'  : wyzeLockBaseUrl(),
    'path' : wyzeLockControlPath(),
    'body' : bodyJson
  ]

  try {
    httpPostJson(params) { pollForLockUpdates(hubitat_device_value_locked) }
  } catch (Exception e) {
    logError("API Call to ${params.uri}${params.path} failed with Exception: ${e}")
  }
}

def unlock() {
  app = getApp()
  logInfo("'Unlock' Pressed")

  // Unlock the lock
  body = [
    'access_token': app.getAccessToken(),
    'action': 'remoteUnlock',
    'key': appKey(),
    'timestamp': (new Date()).getTime(),
    'uuid': getDeviceUuid(device.deviceNetworkId, deviceModel)
  ]

  // Sign the request
  body.sign = createRequestSignature(body, wyzeLockControlPath(), appSecret())

  bodyJson = (new JsonBuilder(body)).toString()

  params = [
    'uri'  : wyzeLockBaseUrl(),
    'path' : wyzeLockControlPath(),
    'body' : bodyJson
  ]

  try {
    httpPostJson(params) { pollForLockUpdates(hubitat_device_value_unlocked) }
  } catch (Exception e) {
    logError("API Call to ${params.uri}${params.path} failed with Exception: ${e}")
  }
}

private void pollForLockUpdates(targetValue) {
  interval = 2000
  maxAttempts = 10
  numAttempts = 0
  while ((device.currentValue('lock', true) != targetValue) && (numAttempts < maxAttempts)) {
    pauseExecution(interval)
    refresh()
    ++numAttempts
  }
}

private String createRequestSignature(Map body, String requestPath, String appSecret) {
  // The request signature needs to include the request body represented similarly to
  // a URL query string. The keys and values should be separated by '=' signs and
  // each key/value pair should be separated by '&'. For instance, a body of
  //   ['key1': 'value1', 'key2': 'value2']
  // would be stringifed as:
  //   'key1=value1&key2=value2'
  // The example I saw also sorted the request body keys alphabetically, so I did that
  // here. I'm not sure if that is required, though.
  stringifiedBody = body.sort().collect {key, value -> "${key}=${value}"}.join('&')

  // The signature should include the HTTP verb, the path of the request, the stringified
  // body and the application secret concatenated together. The concatenated string
  // should be MD5 hashed and converted into a hex string.
  unencodedSignature = "post${requestPath}${stringifiedBody}${appSecret}"
  urlEncodedSignature = URLEncoder.encode(unencodedSignature, 'UTF-8')

  signatureDigest = MessageDigest.getInstance('MD5').digest(urlEncodedSignature.getBytes('UTF-8'))
  finalSignature = signatureDigest.encodeHex().toString()
  return finalSignature
}

private String getDeviceUuid(mac, model) {
  return mac?.replace("${model}.", '')
}

void createDeviceEventsFromPropertyList(List propertyList) {
  app = getApp()
  logDebug('createEventsFromPropertyList()')

  def eventValue // could be String or number

  propertyList.each { property ->
    propertyValue = property.value ?: property.pvalue ?: null
    switch (property.pid) {
      // Locked State
      case wyze_property_lock_state:
        eventValue = propertyValue == wyze_property_lock_state_value_locked ? hubitat_device_value_locked : hubitat_device_value_unlocked
        sendDeviceEvent(app, 'lock', eventValue, null)
        break

      // Device Online
      case wyze_property_device_online:
        eventValue = propertyValue == wyze_property_device_online_value_true ? 'true' : 'false'
        sendDeviceEvent(app, 'online', eventValue, null)
        break
    }
  }
}

private sendDeviceEvent(app, eventName, eventValue, eventUnit) {
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
