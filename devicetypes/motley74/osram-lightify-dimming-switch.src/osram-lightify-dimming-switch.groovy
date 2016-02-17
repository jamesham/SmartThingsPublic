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

    fingerprint profileId: "0104", deviceId: "0001", inClusters: "0000, 0001, 0003, 0020, 0402, 0B05", outClusters: "0003, 0006, 0008, 0019", /*manufacturer: "OSRAM", model: "Lightify 2.4GHZZB/SWITCH/LFY", */deviceJoinName: "OSRAM Lightify Dimming Switch"
  }

  simulator {
    // TODO: define status and reply messages here
  }
  
  tiles(scale: 2) {
    standardTile("button", "device.button", width: 6, height: 4) {
      state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
    }
    valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
      state "battery", label:'${currentValue}% battery'
    }
    standardTile("refresh", "device.button", decoration: "flat", width: 2, height: 2) {
      state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    main "button"
    details(["button", "battery", "refresh"])
  }
}

def parse(String description) {
  Map map = [:]
  log.debug "parse description: $description"
  // Call proper method based on command received
  if (description?.startsWith('catchall:')) {
    map = parseCatchAllMessage(description)
  } else if (description?.startsWith('read')) {
    map = parseReadMessage(description)
  } else {
    log.debug "Unknown message received, parsed message: $msg"
  }
  return map ? createEvent(map) : null
}

private Map parseReadMessage(String description) {
  Map resultMap = [:]
  // Create a map from the message description to make parsing more intuitive
  def msg = zigbee.parseDescriptionAsMap(description)
  if (msg.clusterInt==1 && msg.attrInt==32) {
    resultMap = getBatteryResult(Integer.parseInt(msg.value, 16))
  } else {
    log.debug "Unknown read message received, parsed message: $msg"
  }
  return resultMap
}

private Map parseCatchAllMessage(String description) {
  Map resultMap = [:]
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  log.debug msg
  switch(msg.clusterId) {
    case 1:
      log.debug 'BATTERY MESSAGE'
      resultMap = getBatteryResult(Integer.parseInt(msg.value, 16))
      break
    case [6, 8]:
      log.debug 'CONTROL MESSAGE'
      resultMap = lightEvent(msg.command, msg.data)
      break
    default:
      log.debug "Unknown catchall message received! $msg"
  }
  return resultMap
}

def configure() {
  log.debug "Executing 'configure'"

  def configCmds = [	
    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}",

    // Bind the outgoing level cluster from remote to hub, so the hub receives messages when Dim Up/Down buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}",
    
    // Bind the incoming battery info cluster from remote to hub, so the hub receives battery updates
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}",
  ]
  return configCmds
}

def refresh() {
  return zigbee.readAttribute(0x0001, 0x0020)
}

private lightEvent(command, data) {
  Map resultMap = [:]
  switch(command) {
    case 0: //off command
      //log.debug 'Creating POWER OFF event'
      resultMap = [
        name: 'button',
        value: 'pressed',
        data: [buttonNumber: 1],
        descriptionText: "$device.displayName button 1 was pressed"
      ]
    break
    case 1: //on and brightness decrease command
      if (data) {
        //log.debug 'Creating BRIGHTNESS DECREASE event'
        resultMap = [
          name: 'button',
          value: 'held',
          data: [buttonNumber: 1, levelData: data],
          descriptionText: "$device.displayName button 1 was held"
        ]
      } else {
        //log.debug 'Creating POWER ON event'
        resultMap = [
          name: 'button',
          value: 'pressed',
          data: [buttonNumber: 2],
          descriptionText: "$device.displayName button 2 was pressed"
        ]
      }
    break
    case 3: //brightness change stop command
      //log.debug 'Creating BRIGHTNESS STOP event'
      resultMap = [
        name: 'button',
        value: 'released',
        data: [buttonNumber: [1, 2]],
        descriptionText: "$device.displayName button 1 or 2 was released"
      ]
    break
    case 5: //brightness increase command
      //log.debug 'Creating BRIGHTNESS INCREASE event'
      resultMap = [
        name: 'button',
        value: 'held',
        data: [buttonNumber: 2, levelData: data],
        descriptionText: "$device.displayName button 2 was pressed"
      ]
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