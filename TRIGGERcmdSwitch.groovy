/**
 *  TRIGGERcmdSwitch.groovy
 *
 *  Author: vanmeyconsulting@gmail.com
 *  Date: 2017-04-22
 *
 *  Updated for Hubitat Elevation (HE) by Royski 13/09/2019
 */
// for the UI
metadata {
	definition (name: "TRIGGERcmd Switch", namespace: "vandermeyconsulting", author: "Russell VanderMey", 
	importUrl: "https://raw.githubusercontent.com/rvmey/HubitatTRIGGERcmd/master/TRIGGERcmdSwitch.groovy") {
        capability "Switch"
}

	preferences {
	
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "toggle", type: "bool", title: "Treat as a Momentary switch (on/Off)<br>Leave off if wanting to send Parameters to TRIGGERcmd.", defaultValue: true, submitOnChange: true
		// input "stepsize", "number", title: "Step Size", description: "Dimmer Step Size", defaultValue: 5	
	}

}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
    initialize()
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
def on() {
    if(toggle){
    if (logEnabled) log.debug "Moving to onToggle"
    toggleOn()
 } else {
    if (logEnabled) log.debug "toggle is $toggle.value"
    sendEvent(name:"switch",value:on)    
	parent.on(this)
    log.debug "On command sent"
	}
}

def toggleOn(){
    if (logEnabled) log.debug "toggle is $toggle.value" 
	sendEvent(name:"switch",value:on) 
	parent.on(this)
	runIn(1,toggleSwitch)
    //off()
    if (logEnabled) log.debug "Off Command sent"
    } 

def toggleSwitch(){
    sendEvent(name: "switch", value: "off")
}


def off(){
    if (logEnabled) log.debug  "$device.label Off ..."
	sendEvent(name:"switch", value:off)
	parent.off(this)

}

def installed() {
	initialize()
    // off()
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
