/**
 *  OSRAM 4 Button Switch Handler
 *
 *  Copyright 2017 Andy Jenkinson
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
 * Modified from code written by  motley74, sticks18 and AnotherUser.
 * Original source: https://github.com/motley74/SmartThingsPublic/blob/master/devicetypes/motley74/osram-lightify-dimming-switch.src/osram-lightify-dimming-switch.groovy
 */

metadata {
  definition (name: "OSRAM 4 Button Switch Classic", namespace: "andyjenkinson", author: "Andy Jenkinson") {

    capability "Actuator"
    capability "Battery"
    capability "Button"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"

    attribute "zMessage", "String"
    fingerprint profileId: "0104", deviceId: "0810", inClusters: "0000, 0001, 0020, 1000, FD00", outClusters: "0003, 0004, 0005, 0006, 0008, 0019, 0300, 1000", manufacturer: "OSRAM", model: "Switch 4x EU-LIGHTIFY", deviceJoinName: "OSRAM 4x Switch"
  }

  simulator {
    // Nothing to see here
  }

  tiles(scale: 2) {
    standardTile("button", "device.button", width: 6, height: 4) {
      state "default", label: "", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
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

def configure() {
  log.debug "Configuring Reporting and Bindings."

  // Register for battery updates 1-6 hours
  // 0x0020 is battery voltage, 0x0021 is % remaining
  configureReporting(0x0001, 0x0020, DataType.UINT8, 3600, 21600, 0x01)
  // this device doesn't support 0021
  // configureReporting(0x0001, 0x0021, DataType.UINT8, 3600, 21600, 0x01)

  // outClusters: "0003, 0004, 0005, 0006, 0008, 0019, 0300, 1000"
  // cluster 0x0003: identify
  // cluster 0x0004: groups
  // cluster 0x0005: scenes
  // cluster 0x0006: on/off (commands 0-off, 1-on, 2-toggle)
  // cluster 0x0008: level
  // cluster 0x0300: colour control
  // cluster 0x1000: touchlink commissioning

  // zdo bind {Network ID} {Source Endpoint} {Dest Endpoint} {Cluster} {ZigBee ID (“IEEE Id”)}

  def configCmds = []
  // The switch has 4 endpoints (1 per button) and 8 output clusters
  for (endpoint=1; endpoint<=4; endpoint++) {
    def list = ["0006", "0008", "0300"]
    // the other clusters are scene and group configuration - the lightify gateway sends
    // the config to the switch
    for (item in list) {
      configCmds.add("zdo bind 0x${device.deviceNetworkId} 0x0${endpoint} 0x01 0x${cluster} {${device.zigbeeId}} {}")
    }
  }
  return configCmds
}

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
  // TODO: handle 'numberOfButtons' attribute
  // Parse incoming device messages to generate events

  Map map = [:]
  if (description?.startsWith('catchall:')) {
    // call parseCatchAllMessage to parse the catchall message received
    map = parseCatchAllMessage(description)
  } else if (description?.startsWith('read')) {
    // call parseReadMessage to parse the read message received
    map = parseReadMessage(description)
  } else {
    log.debug "Unknown message received: $description"
  }
  //return event unless map is not set
  if (map) {
    log.debug  "Parsed event: ${map?.descriptionText}"
    return map
  }

  return null
}

def refresh() {
  // read battery level attributes
  zigbee.readAttribute(0x0001, 0x0020)
  // this device doesn't support 0021
  // zigbee.readAttribute(0x0001, 0x0021)
}

private Map parseReadMessage(String description) {
  // Create a map from the message description to make parsing more intuitive
  def msg = zigbee.parseDescriptionAsMap(description)

  if (msg.clusterInt==1) {
    // 0x0020 (battery voltage) is 32 in decimal
    if (msg.attrInt==32) {
      // call getBatteryResult method to parse battery message into event map
      return getBatteryResult(Integer.parseInt(msg.value, 16))
    }
    // 0x0021 (battery %) is 33 in decimal
    else if (msg.attrInt==33) {
      def linkText = getLinkText(device)
      def value = Integer.parseInt(msg.value, 16)
      descriptionText = "${linkText} battery was ${result.value}%"
      return [
        name: 'battery',
        value: value,
        descriptionText: descriptionText
      ]
    }
  }

  log.debug "Unknown read message received, parsed message: $msg"
}

private Map parseCatchAllMessage(String description) {
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  log.debug "message cluster: $msg.clusterId"
  log.debug "message endpoint: $msg.sourceEndpoint"
  log.debug "message command: $msg.command"
  log.debug "message data: $msg.data"

  Map result = [:]
  def buttonNumber = 0

  switch(msg.sourceEndpoint) {
    //Endpoint numbering runs top left, top right, lower left, lower right.

    case 1:
    case 4: //"physical button 1"
      buttonNumber = msg.sourceEndpoint
      break
    case 2: //"physical button 1"
      buttonNumber = 3
      break
    case 3: //"physical button 1"
      buttonNumber = 2
      break
  }

  // on/off
  if (msg.clusterId == 0x0006) {
    if (msg.command == 1) {
      result = [
        name: 'button',
        value: 'on',
        data: [buttonNumber: buttonNumber],
        descriptionText: "$device.displayName button $buttonNumber on",
        isStateChange: true
      ]
    }
    else if (msg.command == 0) {
      result = [
        name: 'button',
        value: 'off',
        data: [buttonNumber: buttonNumber],
        descriptionText: "$device.displayName button $buttonNumber off",
        isStateChange: true
      ]
    }
  }

  // dimming
  else if (msg.clusterId == 0x0008) {
    if (msg.command==05) {
      result = [
        name: 'button',
        value: 'brighten',
        data: [buttonNumber: 1],
        descriptionText: "$device.displayName button 1 brighten",
        isStateChange: true
      ]
    }
    else if (msg.command==01) {
      result = [
        name: 'button',
        value: 'darken',
        data: [buttonNumber: 1],
        descriptionText: "$device.displayName button 1 darken",
        isStateChange: true
      ]
    }
    else if (msg.command==03) {
      result = [
        name: 'button',
        value: 'stop',
        data: [buttonNumber: 1],
        descriptionText: "$device.displayName button 1 stop",
        isStateChange: true
      ]
    }
  }

      if (msg.command==01) {
        result = [
          name: 'button',
          value: 'pushed',
          data: [buttonNumber: 1],
          descriptionText: "$device.displayName button 1 was pushed",
          isStateChange: true
        ]
      }
      else if (msg.command==05) {
        result = [
          name: 'button',
          value: 'held',
          data: [buttonNumber: 1],
          descriptionText: "$device.displayName button 1 was held",
          isStateChange: true
        ]
      }
      else if (msg.command==03) {
        result = [
          name: 'button',
          value: 'stophold',
          data: [buttonNumber: 1],
          descriptionText: "$device.displayName button 1 stopped being held",
          isStateChange: true
        ]
      }

      return result

    case 2: // physical button 3

      if (msg.command==76) {
        result = [
          name: 'button',
          value: 'pushed',
          data: [buttonNumber: 3],
          descriptionText: "$device.displayName button 3 was pushed",
          isStateChange: true
         ]
      }
      else if (msg.command==03) {
        result = [
          name: 'button',
          value: 'held',
          data: [buttonNumber: 3],
          descriptionText: "$device.displayName button 3 started to be held",
          isStateChange: true
         ]
      }
      else if (msg.command==01) {
         if (msg.data[0]==1) {
          result = [
            name: 'button',
            value: 'longhold',
            data: [buttonNumber: 3],
            descriptionText: "$device.displayName button 3 continued to be held",
            isStateChange: true
           ]
        }
        else if (msg.data[0]==0){
          result = [
            name: 'button',
            value: 'stophold',
            data: [buttonNumber: 3],
            descriptionText: "$device.displayName button 3 stopped being held",
            isStateChange: true
           ]
         }
        else {
          result =[
            descriptionText: "$device.displayName button 3 unknown data in command 01",
          ]
        }
      }
      else {
        result = [
          descriptionText: "$device.displayName button 3 unknown command",
        ]
      }

      return result

    case 3: //physical button 2

      if (msg.command==0) {
        // data will be 0
        result = [
          name: 'button',
          value: 'pushed',
          data: [buttonNumber: 2],
          descriptionText: "$device.displayName button 2 was pushed",
          isStateChange: true
        ]
      }
      else if (msg.command==1) {
        // data will be nonzero
        result = [
          name: 'button',
          value: 'held',
          data: [buttonNumber: 2],
          descriptionText: "$device.displayName button 2 started to be held",
          isStateChange: true
        ]
      }
      else if (msg.command==3) {
        // data will be 0
        result = [
          name: 'button',
          value: 'stophold',
          data: [buttonNumber: 2],
          descriptionText: "$device.displayName button 2 stopped being held",
          isStateChange: true
        ]
      }

      return result

    case 4: //physical button 4

      if (msg.command==76) {
        result = [
          name: 'button',
          value: 'pushed',
          data: [buttonNumber: 4],
          descriptionText: "$device.displayName button 4 was pushed",
          isStateChange: true
         ]
      }
      else if (msg.command==03) {
        result = [
          name: 'button',
          value: 'held',
          data: [buttonNumber: 4],
          descriptionText: "$device.displayName button 4 started to be held",
          isStateChange: true
         ]
      }
      else if (msg.command==01) {
         if (msg.data[0]==3) {
          result = [
            name: 'button',
            value: 'longhold',
            data: [buttonNumber: 4],
            descriptionText: "$device.displayName button 4 continued to be held",
            isStateChange: true
           ]
        }
        else if (msg.data[0]==0){
          result = [
            name: 'button',
            value: 'stophold',
            data: [buttonNumber: 3],
            descriptionText: "$device.displayName button 4 stopped being held",
            isStateChange: true
           ]
         }
        else {
          result = [
            descriptionText: "$device.displayName button 4 unknown data in command 01",
          ]
        }
      }
      else {
        result = [
          descriptionText: "$device.displayName button 4 unknown command",
        ]
      }

      return result
  }

}

//Motley obtained from other examples, converts battery message into event map.
//AN: I don't think this is working yet.
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
  log.debug "Parse returned ${result?.descriptionText}"
  return result
}
