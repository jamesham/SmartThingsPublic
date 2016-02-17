/**
 *  OSRAM Lightify Dimming Switch Binder
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
 */
definition(
    name: "OSRAM Lightify Dimming Switch Binder",
    namespace: "motley74",
    author: "Michael Hudson",
    description: "Used to bind dimmable lights/switches in ST to the buttons on a OSRAM Lightify Dimming Switch",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")



preferences {
  section("Which OSRAM Lightify Dimming Switch..."){
    input "switch1", "capability.button", title: "Which switch?", required: true
  }
  section("Which device(s) to control..."){
    input "targets", "capability.switch", title: "Which Target(s)?", multiple: true, required: true
  }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
  subscribe(switch1, "button.pressed", buttonPressedHandler)
  subscribe(switch1, "button.held", buttonHeldHandler)
  subscribe(switch1, "button.released", buttonReleasedHandler)
}

def buttonPressedHandler(evt) {
  log.debug "buttonPressedHandler invoked with ${evt.descriptionText}"
  log.debug "buttonPressedHandler invoked with ${evt.data}"
  log.debug "buttonPressedHandler invoked with ${evt.data.buttonNumber}"
  //if (evt.data.buttonNumber==1) {
  //  targets.off()
  //} else {
  //  targets.on()
  //}
}

def buttonHeldHandler(evt) {
  log.debug "buttonHeldHandler invoked with ${evt.data}"
}

def buttonReleasedHandler(evt) {
  log.debug "buttonReleasedHandler invoked with ${evt.data}"
}