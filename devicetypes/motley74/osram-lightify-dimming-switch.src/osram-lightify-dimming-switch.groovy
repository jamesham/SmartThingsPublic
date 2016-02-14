/**
 *  OSRAM Lightify Dimming Switch v2
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
    capability "Button"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"

    fingerprint profileId: "0104", deviceId: "0001", inClusters: "0000, 0001, 0003, 0020, 0402, 0B05", outClusters: "0003, 0006, 0008, 0019" //, manufacturer: "OSRAM", model: "Lightify 2.4GHZZB/SWITCH/LFY", deviceJoinName: "OSRAM Lightify Dimming Switch"
  }

  simulator {
    // TODO: define status and reply messages here
  }

  tiles(scale: 2) {
    standardTile("button", "device.button", width: 6, height: 4) {
      state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
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
    main "button"
    details(["button", "temperature", "refresh"])
  }
}

def parse(String description) {
  log.debug "parse description: $description"
    
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  log.debug msg
  
  if (description?.startsWith('catchall:') && (msg.clusterId == 6 || msg.clusterId == 8)) {
    // Call proper method based on command received
    log.debug "Command is $msg.command and data is $msg.data"
    if ((msg.command==0) || (msg.command==1 && !msg.data)) {
      lightPower(msg.command)
    } else if ((msg.command==1 && msg.data) || (msg.command==5) || (msg.command==3 && msg.clusterId==8)) {
      lightLevel(msg.command, msg.data)
    } else {
      log.warn "Unknown command/data sequence received! Command: $msg.command Data: $msg.data"
    }
  } else if (description?.startsWith('temperature:')) {
    // Log temperature received from switch
    log.debug "Temperature is $description"
  }
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

def lightPower(command) {
  if (command==0) {
    log.debug "Turning light(s) off."
    def result = createEvent(name: "button", value: "pushed", data: [buttonNumber: 1], descriptionText: "$device.displayName button 1 was pushed", isStateChange: true)
    log.debug "Parse returned ${result?.descriptionText}"
  } else {
    log.debug "Turning light(s) on."
    def result = createEvent(name: "button", value: "pushed", data: [buttonNumber: 2], descriptionText: "$device.displayName button 2 was pushed", isStateChange: true)
    log.debug "Parse returned ${result?.descriptionText}"
  }
}

def lightLevel(command, data) {
  if (command==1) {
    log.debug "Decreasing light(s) brightness."
    def result = createEvent(name: "button", value: "held", data: [buttonNumber: 1], descriptionText: "$device.displayName button 1 was held", isStateChange: true)
    log.debug "Parse returned ${result?.descriptionText}"
    return result
  } else if (command==5) {
    log.debug "Increasing light(s) brightness."
    def result = createEvent(name: "button", value: "held", data: [buttonNumber: 2], descriptionText: "$device.displayName button 2 was held", isStateChange: true)
    log.debug "Parse returned ${result?.descriptionText}"
    return result
  } else {
    log.debug "Stopping brightness change."
    def result = createEvent(name: "button", value: "released", data: [buttonNumber: [1, 2]], descriptionText: "$device.displayName button 1 or 2 was released", isStateChange: true)
    log.debug "Parse returned ${result?.descriptionText}"
    return result
  }
}

def refresh() {
  return zigbee.readAttribute(0x0402, 0x0000)
}