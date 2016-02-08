/*
 *  Activate police light pattern on switch activation
 *
 *  Author: Michael Hudson
 */
definition(
    name: 		"Fibaro police pattern trigger",
    namespace: 		"SmartThingsPublic",
    author: 		"motley74",
    description: 	"Turn Fibaro Controller to Police lights program when a switch, real or virtual, is turned on.",
    category:		"My Apps",
    iconUrl: 		"https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet.png",
    iconX2Url: 		"https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet@2x.png"
)
preferences {
  section("When a Switch is turned on..."){
    input "switch1", "capability.switch", title: "Which?"
  }
  section("Turn on this/these Fibaro Police Light(s)..."){
    input "switches", "capability.switch", multiple: true
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
  switches.police()
}
def switchOffHandler(evt) {
  log.trace "Turning on switches: $switches"
  switches.off()
}