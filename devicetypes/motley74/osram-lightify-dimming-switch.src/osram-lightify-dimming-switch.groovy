/**
 *  OSRAM Lightify Dimming Switch
 *
 *  Copyright 2016 Michael Hudson
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Thanks to @Sticks18 for the Hue Dimmer remote code used as a base for this!
 *
 */
metadata {
  definition (name: "OSRAM Lightify Dimming Switch", namespace: "motley74", author: "Michael Hudson") {
    capability "Actuator"
    capability "Battery"
    capability "Button"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"
    capability "Temperature Measurement"

    fingerprint profileId: "0104", deviceId: "0001", inClusters: "0000, 0001, 0003, 0020, 0402, 0B05", outClusters: "0003, 0006, 0008, 0019" //, manufacturer: "OSRAM", model: "Lightify 2.4GHZZB/SWITCH/LFY", deviceJoinName: "OSRAM Lightify Dimming Switch"
  }

  simulator {
    // TODO: define status and reply messages here
  }
  
  preferences {
    input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by setting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
  }

  tiles(scale: 2) {
    standardTile("button", "device.button", width: 6, height: 4) {
      state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
    }
    valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
      state "battery", label:'${currentValue}% battery'
    }
    valueTile("temperature", "device.temperature", width: 2, height: 2) {
      state("temperature", label:'${currentValue}°',
        backgroundColors:[
          [value: 14, color: "#153591"],
          [value: 20, color: "#165e95"],
          [value: 26, color: "#178998"],
          [value: 32, color: "#189c82"],
          [value: 38, color: "#199f5c"],
          [value: 44, color: "#1aa333"],
          [value: 50, color: "#2da71c"],
          [value: 56, color: "#5baa1d"],
          [value: 62, color: "#8aae1e"],
          [value: 68, color: "#b1a81f"],
          [value: 76, color: "#b57d20"],
          [value: 82, color: "#b85122"],
          [value: 88, color: "#bc2323"],
          [value: 94, color: "#d04e00"],
          [value: 100, color: "#bc2323"]
        ]
      )
    }
    standardTile("refresh", "device.button", decoration: "flat", width: 2, height: 2) {
      state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    main "temperature"
    details(["button", "temperature", "battery", "refresh"])
  }
}

def parse(String description) {
  Map map = [:]
  log.debug "parse description: $description"
  // Call proper method based on command received
  if (description?.startsWith('catchall:')) {
    map = parseCatchAllMessage(description)
  } else if (description?.startsWith('read')) {
    def msg = zigbee.parseDescriptionAsMap(description)
    log.debug msg
    map = getBatteryResult(msg.value)
  } else if (description?.startsWith('temperature:')) {
    map = parseCustomMessage(description)
  } else {
    log.debug "Unknown message received, parsed message: $msg"
  }
  return map
}

private Map parseCatchAllMessage(String description) {
  Map resultMap = [:]
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  //log.debug msg
  switch(msg.clusterId) {
    case [6, 8]:
      log.debug 'CONTROL'
      resultMap = lightEvent(msg.command, msg.data)
      break
    case 32:
      log.debug 'BATTERY'
      resultMap = getBatteryResult(msg.value)
      break
    case 1026:
      log.debug 'TEMPERATURE'
      // temp is last 2 data values. reverse to swap endian
      String temp = msg.data[-2..-1].reverse().collect { msg.hex1(it) }.join()
      def value = getTemperature(temp)
      resultMap = getTemperatureResult(value)
      break
    default:
      log.debug "Unknown catchall message received!\n$msg"
  }
  return resultMap
}

def configure() {
  log.debug "Executing 'configure'"

  def configCmds = [	
    // Bind the outgoing on/off cluster from remote to hub, so remote sends hub messages when On/Off buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}",

    // Bind the outgoing level cluster from remote to hub, so remote sends hub messages when Dim Up/Down buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}",
    
    // Bind the incoming battery info cluster from remote to hub, so the hub receives battery updates
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0020 {${device.zigbeeId}} {}",

    // Bind the incoming temperature cluster from remote to hub, so the hub receives temperature updates
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}",
  ]
  return configCmds
}

def refresh() {
  return zigbee.readAttribute(0x0402, 0x0000)
}

private lightEvent(command, data) {
  Map resultMap = [:]
  switch(command) {
    case 0: //off command
      log.debug 'Creating POWER OFF event'
      resultMap = sendEvent(name: 'button 1', value: 'pressed', data: [buttonNumber: 1], descriptionText: "$device.displayName button 1 was pressed")
    break
    case 1: //on and brightness decrease command
      if (data) {
        log.debug 'Creating BRIGHTNESS DECREASE event'
        resultMap = sendEvent(name: 'button 1', value: 'held', data: [buttonNumber: 1], descriptionText: "$device.displayName button 1 was held")
      } else {
        log.debug 'Creating POWER ON event'
        resultMap = sendEvent(name: 'button 2', value: 'pressed', data: [buttonNumber: 2], descriptionText: "$device.displayName button 2 was pressed")
      }
    break
    case 3: //brightness change stop command
      log.debug 'Creating BRIGHTNESS STOP event'
      resultMap = sendEvent(name: 'button', value: 'released', data: [buttonNumber: [1, 2]], descriptionText: "$device.displayName button 1 or 2 was released")
    break
    case 5: //brightness increase command
      log.debug 'Creating BRIGHTNESS INCREASE event'
      resultMap = sendEvent(name: 'button 2', value: 'held', data: [buttonNumber: 2], descriptionText: "$device.displayName button 2 was pressed")
    break
  }
  return resultMap
}

private Map getBatteryResult(rawValue) {
  def linkText = getLinkText(device)
  def result = [
    name: 'battery',
    value: '--'
  ]
  log.debug rawValue
  def volts = rawValue / 10
  def descriptionText
  if (rawValue == 0) {
  } else {
    if (volts > 3.5) {
      result.descriptionText = "${linkText} battery has too much power (${volts} volts)."
    } else if (volts > 0){
      def minVolts = 2.1
      def maxVolts = 3.0
      def pct = (volts - minVolts) / (maxVolts - minVolts)
      result.value = Math.min(100, (int) pct * 100)
      result.descriptionText = "${linkText} battery was ${result.value}%"
    }
  }
  return result
}

def getTemperature(value) {
  def celsius = Integer.parseInt("$value", 16).shortValue() / 100
  log.debug "Original value is $value and calculated celcius is $celsius"
  if(getTemperatureScale() == "C"){
    return celsius
  } else {
    def fahrenheit = celsiusToFahrenheit(celsius) as Integer
    log.debug "Calculated fahrenheiht is $fahrenheit"
    return fahrenheit
    //return celsiusToFahrenheit(celsius) as Integer
  }
}

private Map getTemperatureResult(value) {
  def linkText = getLinkText(device)
  if (tempOffset) {
    def offset = tempOffset as int
    def v = value as int
    value = v + offset
  }
  def descriptionText = "${linkText} was ${value}°${temperatureScale}"
  return [
    name: 'temperature',
    value: value,
    descriptionText: descriptionText
  ]
}

private Map parseCustomMessage(String description) {
  Map resultMap = [:]
  if (description?.startsWith('temperature: ')) {
    def value = zigbee.parseHATemperatureValue(description, "temperature: ", getTemperatureScale())
    resultMap = getTemperatureResult(value)
  }
  return resultMap
}