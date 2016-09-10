/**
 *  Copyright 2015 SmartThings
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
 *  This code adapted from Change Bulb Color With Button Remote by Jim Worley noname4444
 * 
 *
 *  Author: Matthew Lawrence
 *  *
 *  Date: 2016-09-09
 */
 
import groovy.json.JsonSlurper
 
definition(
    name: "Bulb Color Switch",
    namespace: "mattlary",
    author: "Matt Lawrence",
    description: "Choose a switch or button and button action (press or hold) to set a number of lights to any color/brightness with option to revert back to original value",
    category: "My Apps",
    iconUrl: "http://www.iconsdb.com/icons/download/royal-blue/idea-32.png",
    iconX2Url: "http://www.iconsdb.com/icons/download/royal-blue/idea-64.png"
)

preferences {
	section("Select the button/remote...") {
		input "button1", "capability.button", title: "Button Press", required:false, multiple:false
	}
    section("Select switch..."){
    	input "switch1", "capability.switch", title: "Switch actuate", required:false, multiple: false
    }
    section("Choose button and press type..."){
      input "buttonNumber","enum",title: "Button Number (defaults to 1)",required:false,
        options: ["1","2","3","4"]
      input "buttonAction","enum",title: "Button Action (defaults to 'Push')",required:false,
        options: [["pushed":"Push"],["held":"Hold"]]
    }
    section("Pause and revert?"){
    	input "revert","bool",title: "Revert after change",required:false
    }
    section("Control these bulbs...") {
			input "hues", "capability.colorControl", title: "Which Color Bulbs?", required:true, multiple:true
		}
    section("Choose light effects...")
    {
        input "color", "enum", title: "Hue Color?", required: false, multiple:false, options: [
            ["Soft White":"Soft White - Default"],
            ["White":"White - Concentrate"],
            ["Daylight":"Daylight - Energize"],
            ["Warm White":"Warm White - Relax"],
            "Red","Green","Blue","Yellow","Orange","Purple","Pink"]
        input "lightLevel", "enum", title: "Light Level?", required: false, options: [[10:"10%"],[20:"20%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]
    }
    section("More options", hideable: true, hidden: false) {
        input "modes", "mode", title: "Only when mode is", multiple: true, required: false
    }


}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents()
}

def subscribeToEvents() {
    subscribe(app, appTouchHandler)
	subscribe(button1, "button", eventHandler)
    subscribe(switch1, "switch", eventHandler)
}

def eventHandler(evt) {
	log.trace "Executing Mood Lighting"
    def btnNum = buttonNumber as Integer ?: 1
    def btnAct = buttonAction ?: "pushed"
       
    def slurper = new JsonSlurper().parseText(evt.data)
    log.debug "evt action ${evt.data}"
    log.debug "evt value ${evt.value}"
    def inButtonNumber = slurper.buttonNumber
    def inValue = evt.value
    

    //log.debug "Install button: ${btnNum} and Installed action: ${btnAct}"
    log.debug "Found button: $inButtonNumber and found action: $inValue"
   
	if (modeOk && ((inButtonNumber == btnNum && inValue == btnAct) || inValue == "on" )) {
		log.trace "ModeOk"
		takeAction(evt)
	}
    else {
        log.debug "Not taking action because we are not in an approved mode or wrong button press"
    }
}

def appTouchHandler(evt) {
	takeAction(evt)
}

private takeAction(evt) {
	def hueColor = 0
	def saturation = 100
    def colorTemperature = 0
    
    if( switch1 )
    {
    	switch1.off()
    }
    
	switch(color) {
		case "White":
			hueColor = 52
			saturation = 19
            colorTemperature = 6494
			break;
		case "Daylight":
			hueColor = 53
			saturation = 91
            colorTemperature = 6494
			break;
		case "Soft White":
			hueColor = 23
			saturation = 56
            colorTemperature=5025
			break;
		case "Warm White":
			hueColor = 20
			saturation = 80 //83
            colorTemperature=3097
			break;
		case "Blue":
			hueColor = 67
            saturation = 96
			break;
		case "Green":
			hueColor = 31
            saturation = 93
			break;
		case "Yellow":
			hueColor = 25
			break;
		case "Orange":
			//hueColor = 10
            hueColor = 2.96
			break;
		case "Purple":
			hueColor = 75
			break;
		case "Pink":
			hueColor = 83
			break;
		case "Red":
			hueColor = 100
			break;
	}

	state.previous = [:]

	hues.each {
        def oldColorTemp = 0
        try{
          oldColorTemp = it.currentValue("colorTemperature")
        }catch(Exception e){}
        log.debug "oldColorTemp: ${oldColorTemp}"
		state.previous[it.id] = [
			"switch": it.currentValue("switch"),
			"level" : it.currentValue("level"),
			"hue": it.currentValue("hue"),
			"saturation": it.currentValue("saturation"),
            "colorTemperature" : oldColorTemp
		]
	}

	log.debug "current values = $state.previous"

	def newValue = [hue: hueColor, saturation: saturation, level: lightLevel as Integer ?: 100,colorTemperature: colorTemperature]
	log.debug "new value = $newValue"

	
    if( colorTemperature == 0 )
		hues*.setColor(newValue)
    else
        hues*.setColorTemperature(colorTemperature)
    hues*.on()
    def thisRev = revert ?: false    
    if( thisRev ){
       hues.each{
       	def ps = state.previous[it.id]
        log.debug "ps: ${ps}" 
        if( ps["switch"].equals("off")){
        	log.debug "Setting to previous state of off"
        	it.off([delay:5000])
        }
        if( ps["colorTemperature"] == 0 ){
            log.debug "Setting to a previous color"
        	it.setColor(ps, [delay:5000])
        }else{
            log.debug "Setting to a previous colorTemp"
        	it.setColorTemperature(ps["colorTemperature"], [delay:5000])
        }                
       }
       // Pause and revert to orig
       log.debug "In revert"
    }
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	log.trace "modeOk = $result"
	result
}