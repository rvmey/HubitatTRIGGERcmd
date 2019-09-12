/**
 *  TRIGGERcmdSwitch.groovy
 *
 *  Author: vanmeyconsulting@gmail.com
 *  Date: 2017-04-22
 *
 *
 */
// for the UI
metadata {
	definition (name: "TRIGGERcmd Switch", namespace: "vandermeyconsulting", author: "Russell VanderMey") {
		capability "Switch"

}

	preferences {
		// input "stepsize", "number", title: "Step Size", description: "Dimmer Step Size", defaultValue: 5
	}

}

// parse events into attributes
def parse(description) {
	log.debug "parse() - $description"
	def results = []

	if ( description == "updated" )
		return

	if (description?.name && description?.value)
	{
		results << createEvent(name: "${description?.name}", value: "${description?.value}")
	}
}

// handle commands
def push() {
    sendEvent(name: "switch", value: "on")
    runIn(1,toggleSwitch)
}

def toggleSwitch(){
    sendEvent(name: "switch", value: "off")
}

def on() {
	sendEvent(name:"switch",value:on)
	parent.on(this)
	push()
    // off()
}

def off() {
	sendEvent(name:"switch",value:off)
	parent.off(this)
	push()
}

def poll() {
	log.debug "Executing poll()"
	parent.poll(this)
}

def refresh() {
	log.debug "Executing refresh()"
	parent.poll(this)
}

def installed() {
	initialize()
    // off()
}

def updated() {
	initialize()
	refresh()
}

def initialize() {
	if ( !settings.stepsize )
		state.stepsize = 10 //set the default stepsize
	else
		state.stepsize = settings.stepsize
}

/*******************************************************************************
 Method :uninstalled(args)
 (args) :none
 returns:Nothing
 ERRORS :No error handling is done

 Purpose:This is standard ST method.
 Gets called when "remove" is selected in child device "preferences"
 tile. It also get's called when "deleteChildDevice(child)" is
 called from parent service manager app.
 *******************************************************************************/
def uninstalled() {
	log.debug "Executing 'uninstall' in device type"
	parent.uninstallFromChildDevice(this)
}
