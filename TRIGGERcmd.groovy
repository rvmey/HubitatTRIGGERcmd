/**
 *  TRIGGERcmd V2
 *
 *  Copyright 2017 VanderMey Consulting, LLC
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
 *  Updated for Hubitat Elevation (HE) by Royski 13/09/2019
 *
 */

import java.security.MessageDigest;

private apiUrl() { "https://www.triggercmd.com" }

definition(
	name: "TRIGGERcmd",
	namespace: "vandermeyconsulting",
	author: "VanderMey Consulting, LLC",
	description: "Run commands on your computers. You must create a login acct at TRIGGERcmd.com.",
	category: "SmartThings Labs",
	iconUrl: "http://s3.amazonaws.com/triggercmdagents/icon60.jpg",
	iconX2Url: "http://s3.amazonaws.com/triggercmdagents/icon120.jpg",
    iconX3Url: "http://s3.amazonaws.com/triggercmdagents/icon550.jpg",
	singleInstance: true    
)

preferences {
	def msg = """Tap 'Next' after you have entered your TRIGGERcmd credentials.
	

Once your credentials are accepted, Hubitat will scan your TRIGGERcmd account for commands."""

	page(name: "selectDevices", title: "Connect your TRIGGERcmd commands to Hubitat", install: false, uninstall: true, nextPage: "chooseTriggers") {
		section(){paragraph "<img src='https://images-na.ssl-images-amazon.com/images/I/517cuXm4+7L.png' width='100' height='100'</img> Version: $state.version <br>"}
		section("TRIGGERcmd credentials") {
			input "username", "text", title: "Enter TRIGGERcmd Email/UserName", required: true
			input "password", "password", title: "Enter TRIGGERcmd Password", required: true
			paragraph msg
		}
		//section("Logging Options") 
        section(){paragraph "<hr><br><b>Logging Options:</b>&nbsp &nbsp<font size='-1'>(Please revisit this page to change the logging manually)</font> <br><br> <b>DEBUG & INFO:</b> Will drop to <i>INFO</i> after one hour, for a 24 hour period. <br> <b>INFO:</b> Will drop to <i>NONE</i>, after 24 hours.<br> <b>NONE</b> Will do just that :)"}
        section(){input "logLevel", "enum", title: "Set Logging Level", required:true, defaultValue: "INFO", options: ["NONE", "INFO", "DEBUG & INFO"],submitOnChange: true}
		
	}

	page(name: "chooseTriggers", title: "Choose commands to control with Hubitat", content: "initialize")
}

def installed() {
	log.info "Initialised with settings: ${settings}"

	unschedule()
	unsubscribe()
	setVersion()
	setupBulbs()    
	logCheck()     
}

def updated() {

	unschedule()
	setVersion()
	setupBulbs()    
	log.info "Initialised with settings: ${settings}"
	logCheck()   
}

def uninstalled()
{
	unschedule() //in case we have hanging runIn()'s
}

private removeChildDevices(delete) {
	LOGDEBUG("deleting ${delete.size()} bulbs")
	LOGDEBUG("deleting ${delete}")
	delete.each {
		deleteChildDevice(it.device.deviceNetworkId)
	}
}

def uninstallFromChildDevice(childDevice)
{
	def errorMsg = "uninstallFromChildDevice was called and "
	if (!settings.selectedBulbs) {
		LOGDEBUG( errorMsg += "had empty list passed in")
		return
	}

	def dni = childDevice.device.deviceNetworkId

	if ( !dni ) {
		LOGDEBUG( errorMsg += "could not find dni of device")
		return
	}

	def newDeviceList = settings.selectedBulbs - dni
	app.updateSetting("selectedBulbs", newDeviceList)

	LOGDEBUG( errorMsg += "completed succesfully")
}


def setupBulbs() {
	LOGDEBUG("In setupBulbs")

	def bulbs = state.devices
    
    LOGDEBUG("bulbs: ${bulbs}")
    
	def deviceFile = "TRIGGERcmd Switch"

	if(selectedBulbs instanceof String) {
    	LOGDEBUG("selectedBulbs is a string because only a single command was selected")      
        def did = selectedBulbs
        
        //see if this is a selected bulb and install it if not already
		def d = getChildDevice(did)

		if(!d) {
			def newBulb = bulbs.find { (it.did) == did }
			d = addChildDevice("vandermeyconsulting", deviceFile, did, null, [name: "${newBulb?.name}", label: "${newBulb?.name}", completedSetup: true])

			LOGDEBUG("Added device d: ${d} did: ${did}")
		} else {
			LOGDEBUG("We already added this device d: ${d} did: ${did}")
		}        
    } else {
    	LOGDEBUG("selectedBulbs is not a string")
        
        selectedBulbs.each { did ->
			//see if this is a selected bulb and install it if not already
			def d = getChildDevice(did)

			if(!d) {
				def newBulb = bulbs.find { (it.did) == did }
				d = addChildDevice("vandermeyconsulting", deviceFile, did, null, [name: "${newBulb?.name}", label: "${newBulb?.name}", completedSetup: true])
				LOGDEBUG("Added device d: ${d} did: ${did}")
			} else {
				LOGDEBUG("We already added this device d: ${d} did: ${did}")
			}
    	}
  	}

	// Delete any that are no longer in settings
	def delete = getChildDevices().findAll { !selectedBulbs?.contains(it.deviceNetworkId) }
	removeChildDevices(delete)

    unschedule()
    // def exp = "* 0 * * * ?"    // <- the was a bug.  It meant cleanup every second for the first minute of every hour.  Not good.
    def second = (Math.abs(new Random().nextInt() % 60) + 1).toString()
    def minute = (Math.abs(new Random().nextInt() % 60) + 1).toString()
    def hour = (Math.abs(new Random().nextInt() % 24) + 1).toString()
    def exp = second + " " + minute + " " + hour + " * * ?"  // cleanup at a random time once per day
    debugOut "new schedule: " + exp
    schedule(exp, cleanupTriggers) 
}

def initialize() {

	atomicState.token = ""

	getToken()

	if ( atomicState.token == "error" ) {
		return dynamicPage(name:"chooseBulbs", title:"TCP Login Failed!\r\nTap 'Done' to try again", nextPage:"", install:false, uninstall: false) {
			section("") {}
		}
	} else {
		"we're good to go"
		LOGDEBUG("We have Token.")
	}

	//getGatewayData() //we really don't need anything from the gateway

	deviceDiscovery()

	def options = devicesDiscovered() ?: []

	def msg = """Tap 'Done' after you have selected the desired commands."""

	return dynamicPage(name:"chooseTriggers", title:"TRIGGERcmd and SmartThings Connected!", nextPage:"", install:true, uninstall: true) {
		section("Tap below to view command list") {
			input "selectedBulbs", "enum", required:false, title:"Select commands", multiple:true, options:options
			paragraph msg
		}
	}

}

def cleanupTriggers() {
	LOGDEBUG("Running cleanupTriggers.")
    deviceDiscovery()    
    
    def devices =  state.devices
   
    // LOGDEBUG("devices: ${devices}")
    // LOGDEBUG("selectedBulbs: ${selectedBulbs}")
    
    def founddevice = ""
    def founddevicename = ""
    def selecteddevice = ""    
    def selectedwasfound = ""
    
    selectedBulbs.each { did ->
      selectedwasfound = "false"
      selecteddevice = did     
      // LOGDEBUG("Selecteddevice: ${selecteddevice}")
      devices.each({
        founddevice = it?.did        
        if ( founddevice == selecteddevice ) {
        	// LOGDEBUG("selecteddevice: ${selecteddevice} founddevice: ${founddevice}")
      		selectedwasfound = "true"
        }        
      })
      if ( selectedwasfound == "false" ) {
         LOGDEBUG("Deleting: ${selecteddevice}")
         deleteChildDevice(selecteddevice)
      }
	}    
}


def deviceDiscovery() {
	def Params = [
		token: "${atomicState.token}",
        uri: "/api/smartthings/commandlist"		
	]

	def triggers = ""

	apiPost(Params) { response ->
        triggers = response.data
	}

	LOGDEBUG("trigger data = ${triggers}")

	def devices = []
	def bulbIndex = 1
	def lastRoomName = null
	def deviceList = []
	
    def roomId = 1
    def roomName = ""
	devices = triggers
	
	if ( devices[1] != null ) {		
		LOGDEBUG("Room Device Data: did:${roomId} roomName:${roomName}")		
		devices.each({
			// LOGDEBUG("Bulb Device Data: did:${it?.did} room:${roomName} BulbName:${it?.name}")
			deviceList += ["name" : "${roomName} ${it?.name}", "did" : "${it?.did}", "type" : "bulb"]
		})
	} else {
		LOGDEBUG("Bulb Device Data: did:${devices?.did} room:${roomName} BulbName:${devices?.name}")
		// deviceList += ["name" : "${roomName} ${devices?.name}", "did" : "${devices?.did}", "type" : "bulb"]  <- this logic doesn't work when there's only 1 command
		// Thanks to mthiel for finding this fix. 
		deviceList += ["name" : "${roomName} ${devices[0].name}", "did" : "${devices[0].did}", "type" : "bulb"]
		
	}

	devices = ["devices" : deviceList]
	state.devices = devices.devices
    LOGDEBUG("state.devices: ${state.devices}")
}

Map devicesDiscovered() {
	def devices =  state.devices
	def map = [:]
	if (devices instanceof java.util.Map) {
		devices.each {
			def value = "${it?.name}"
			def key = it?.did
			map["${key}"] = value
		}
	} else { //backwards compatable
		devices.each {
			def value = "${it?.name}"
			def key = it?.did
			map["${key}"] = value
		}
	}
	map
}

def getToken() {

	atomicState.token = ""

	if (password) {

		def qParams = [			
			email: "${username}",
            password: "${password}"
		]

		apiLogin(qParams) { response ->
			def status = response.status.toString()
			LOGDEBUG("response status: ${status}")  // russ
			//sendNotificationEvent("Get token status ${status}")

			if (status != "200") {//success code = 200
				def errorText = response.data
                LOGDEBUG("error text: ${errorText}")  // russ
				LOGDEBUG("Error logging into TRIGGERcmd. Error = ${errorText}")
				atomicState.token = "error"
			} else {
				atomicState.token = response.data.token                
                LOGDEBUG("response token: ${response.data.token}") // russ
			}
		}
	} else {
		log.warn "Unable to log into TRIGGERcmd. Error = Password is null"
		atomicState.token = "error"
	}
}

def apiLogin(creds, Closure callback) {
	LOGDEBUG("In apiLogin with creds: ${creds}")
	def params = [
		uri: apiUrl() + "/api/auth/authenticate",
		body: creds
	]

	httpPost(params) {
		response ->
        	// LOGDEBUG("response data: ${response.status}")
        	def rc = response.status.toString()

			if ( rc == "200" ) {
				LOGDEBUG("Return Code = ${rc} = Command Succeeded.")
				callback.call(response)

			} else if ( rc == "401" ) {
				LOGDEBUG("Return Code = ${rc} = Error: User not logged in!") //Error code from gateway
				LOGDEBUG("Refreshing Token")
				getToken()
				//callback.call(response) //stubbed out so getToken works (we had race issue)

			} else {
				log.error "APILogin Return Code = ${rc} = Error!" //Error code from gateway
				//sendNotificationEvent("TRIGGERcmd is having Communication Errors. Error code = ${rc}.")
				callback.call(response)
			}
	}
}

def apiPost(data, Closure callback) {
	LOGDEBUG("In apiPost with data: ${data}")
	def params = [
		uri: apiUrl() + data.uri,
		body: data.body,
        headers: [
           'Authorization': "Bearer ${data.token}"
        ],
	]

	httpPost(params) {
		response ->
			def rc = response.status.toString()

			if ( rc == "200" ) {
				LOGDEBUG("Return Code = ${rc} = Command Succeeded.")
				callback.call(response)

			} else if ( rc == "401" ) {
				LOGDEBUG("Return Code = ${rc} = Error: User not logged in!") //Error code from TRIGGERcmd
				LOGDEBUG("Refreshing Token")
				getToken()
				//callback.call(response) //stubbed out so getToken works (had race issue)

			} else {
				log.error "Return Code = ${rc} = Error!" //Error code from gateway
				//sendNotificationEvent("TRIGGERcmd is having communication errors. Error code = ${rc}.")
				callback.call(response)
			}
	}
}


def generateSha256(String s) {

	MessageDigest digest = MessageDigest.getInstance("SHA-256")
	digest.update(s.bytes)
	new BigInteger(1, digest.digest()).toString(16).padLeft(40, '0')
}

def generateMD5(String s) {
	MessageDigest digest = MessageDigest.getInstance("MD5")
	digest.update(s.bytes);
	new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def syncronizeDevices() {
	LOGDEBUG("In syncronizeDevices")

	def update = getChildDevices().findAll { selectedBulbs?.contains(it.deviceNetworkId) }
		logCheck()
	update.each {
		def dni = getChildDevice( it.deviceNetworkId )
		LOGDEBUG("dni = ${dni}")
		logCheck()

		if (isRoom(dni)) {
			pollRoom(dni)
		} else {
			poll(dni)
		}
		logCheck()
	}
}

boolean isRoom(dni) {
	def device = state.devices.find() {(( it.type == 'room') && (it.did == "${dni}"))}
}

boolean isBulb(dni) {
	def device = state.devices.find() {(( it.type == 'bulb') && (it.did == "${dni}"))}
}

def debugEvent(message, displayEvent) {

	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	LOGDEBUG("Generating AppDebug Event: ${results}")
	sendEvent (results)

}

//def LOGDEBUG((msg) {
//	LOGDEBUG(msg
//	//sendNotificationEvent(msg) //Uncomment this for troubleshooting only
//}


/**************************************************************************
 Child Device Call In Methods
 **************************************************************************/
def on(childDevice) {
	LOGINFO("On request from child device ${childDevice}")

	def dni = childDevice.device.deviceNetworkId

	//Russ
	def Params = [
		token: "${atomicState.token}",
        uri: "/api/smartthings/triggerBase64",
        body: "trigger=${dni}&params=on"
	]

	apiPost(Params) { response ->
		LOGINFO("ON result: ${response.data}")
	}

}

def off(childDevice) {
	LOGINFO("Off request from child device")

    def dni = childDevice.device.deviceNetworkId

	//Russ
	def Params = [
		token: "${atomicState.token}",
        uri: "/api/smartthings/triggerBase64",
        body: "trigger=${dni}&params=off"
	]

	apiPost(Params) { response ->
		LOGINFO("OFF result: ${response.data}")
	}
}

def logCheck(){
    state.checkLog = logLevel
	if(state.checkLog == "INFO"){log.info "Informational Logging Enabled"}
	if(state.checkLog == "DEBUG & INFO"){log.info "Debug & Info Logging Enabled"}
	if(state.checkLog == "NONE"){log.info "Further Logging Disabled"}
	if(logLevel == "DEBUG & INFO") runIn(3600,logsDown)
    if(logLevel == "INFO") runIn(86400,logsDownAgain)
}

def logsDown(){
    log.warn "Debug logging disabled... Info logging enabled"
    app.updateSetting("logLevel",[value:"INFO",type:"enum"])
	if(logLevel == "INFO") runIn(86400,logsDownAgain) 
}
def logsDownAgain(){
    log.warn "Info logging disabled."
    app.updateSetting("logLevel",[value:"NONE",type:"enum"])	
}



def LOGDEBUG(txt){
	if(state.checkLog == "DEBUG & INFO"){
    try {
    	log.debug("${app.label.replace(" ","_").toUpperCase()}  (App Version: ${state.version}) - ${txt}")
				 
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
  }
}

def LOGINFO(txt){
	if(state.checkLog == "INFO" || state.checkLog == "DEBUG & INFO"){
    try {
     log.info("${app.label.replace(" ","_").toUpperCase()}  (App Version: ${state.version}) - ${txt}")
    } catch(ex) {
    	log.error("LOGINFO unable to output requested data!")
    }
  }
}

def setVersion(){
		state.version = "1.0.0"	 
		state.InternalName = "TRIGGERcmd"

}
