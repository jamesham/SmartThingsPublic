/*
 *  Activate selected Fibaro pattern on switch activation
 *
 *  Author: Michael Hudson
 *  Based on the Turn-on-Police-Light-When-Switch-Is-On.groovy smartapp by twack
 *  https://github.com/twack/smarthings-apps/blob/master/Turn-on-Police-Light-When-Switch-Is-On.groovy
 *
 */
definition(
    name: 		"Fibaro pattern trigger",
    namespace: 		"SmartThingsPublic",
    author: 		"motley74",
    description: 	"Trigger Fibaro Controller to activate selected pattern when a switch, real or virtual, is turned on.",
    category:		"My Apps",
//    iconUrl: 		"https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet.png",
//    iconX2Url: 		"https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet@2x.png"
)
preferences {
  section("When a Switch is turned on..."){
    input "switch1", "capability.switch", title: "Which switch?"
  }
  section("Activate this/these Fibaro Light(s)..."){
    input "switches", "device.fibaroRGBWController", title: "Which Target(s)?", multiple: true
  }
  section("Set pattern to the following..."){
    input "pattern1", "enum", title: "Which pattern?", description: "Select the pattern to use", options: ["fireplace","storm","deepfade","litefade","police"], multiple: false, required: true
  }
}
def installed() {
  subscribe(switch1, "switch.on", switchOnHandler)
  subscribe(switch1, "switch.off", switchOffHandler)
}
def updated() {
  unsubscribe()
  subscribe(switch1, "switch.on", switchOnHandler)
  subscribe(switch1, "switch.off", switchOffHandler)
}
def switchOnHandler(evt) {
  log.trace "Turning on switches: $switches"
  switches."$pattern1"()
}
def switchOffHandler(evt) {
  log.trace "Turning on switches: $switches"
  switches.off()
}