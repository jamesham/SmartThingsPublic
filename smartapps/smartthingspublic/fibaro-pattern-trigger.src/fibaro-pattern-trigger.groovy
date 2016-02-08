/*
 *  Activate selected Fibaro pattern on switch activation
 *
 *  Author: Michael Hudson
 */
definition(
    name: 		"Fibaro pattern trigger",
    namespace: 		"SmartThingsPublic",
    author: 		"motley74",
    description: 	"Trigger Fibaro Controller to activate selected pattern when a switch, real or virtual, is turned on.",
    category:		"My Apps",
)
preferences {
  section("When a Switch is turned on..."){
    input "switch1", "capability.switch", title: "Which switch?"
  }
  section("Turn on this/these Fibaro Police Light(s)..."){
    input "switches", "capability.switch", multiple: true
  }
  section("Set pattern to the following..."){
    input "pattern1", "enum", title: "Which pattern", description: "Select the pattern to use", options: ["fireplace","storm","deepfade","litefade","police"], multiple: false, required: true
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
  log.trace "Turning on switches $switches with pattern $pattern1"
  switches.pattern1()
}
def switchOffHandler(evt) {
  log.trace "Turning on switches: $switches"
  switches.off()
}