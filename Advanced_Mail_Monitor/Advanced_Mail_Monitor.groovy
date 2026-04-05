/**
 * Advanced Mail Monitor
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Mail Monitor",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced mailbox state tracking with historical averages, audio announcements, nag reminders, and live telemetry dashboard.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            if (mailSensors && mailSwitch) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Metric</th><th style='padding: 8px;'>Today</th><th style='padding: 8px;'>Historical Average</th></tr>"
                
                def tDelivery = state.todayDeliveryTime ?: "--:-- --"
                def tRetrieval = state.todayRetrievalTime ?: "--:-- --"
                def tWalk = state.lastRetrievalWalkTime ? formatSeconds(state.lastRetrievalWalkTime) : "--"
                
                def avgDel = state.avgDeliveryTime ? minutesToTimeStr(state.avgDeliveryTime) : "--:-- --"
                def avgRet = state.avgRetrievalTime ? minutesToTimeStr(state.avgRetrievalTime) : "--:-- --"
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Mail Delivery</b></td><td style='padding: 8px; color: green;'>${tDelivery}</td><td style='padding: 8px;'>${avgDel}</td></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Mail Retrieval</b></td><td style='padding: 8px; color: blue;'>${tRetrieval}</td><td style='padding: 8px;'>${avgRet}</td></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Last Retrieval Trip</b></td><td style='padding: 8px; color: purple;'>${tWalk}</td><td style='padding: 8px;'>--</td></tr>"
                statusText += "</table>"
 
                def batteryHtml = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-top: 10px;'>"
                batteryHtml += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Mailbox Sensor</th><th style='padding: 8px;'>Current State</th><th style='padding: 8px;'>Battery Health</th></tr>"
         
                mailSensors.each { sensor ->
                    def contactState = sensor.currentValue("contact")?.toUpperCase() ?: "UNKNOWN"
                    def contactColor = (contactState == "OPEN") ? "red" : "green"
                    
                    def batt = sensor.currentValue("battery") ?: "N/A"
                    def battColor = "green"
                    if (batt != "N/A") {
                        if (batt.toInteger() <= 15) battColor = "red"
                        else if (batt.toInteger() <= 50) battColor = "orange"
                        batt = "${batt}%"
                    }
                    batteryHtml += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${sensor.displayName}</td><td style='padding: 8px; color: ${contactColor}; font-weight: bold;'>${contactState}</td><td style='padding: 8px; color: ${battColor}; font-weight: bold;'>${batt}</td></tr>"
                }
                batteryHtml += "</table>"
                statusText += batteryHtml
       
                if (tempSensor || outsideTempSensor) {
                    def currentTemp
                    if (tempSensor) {
                        currentTemp = tempSensor.currentValue("temperature")
                    } else {
                        def outTemp = outsideTempSensor.currentValue("temperature")
                        currentTemp = outTemp ? (outTemp.toDouble() + (tempOffset ?: 20)) : null
                    }
                    
                    if (currentTemp) {
                        statusText += "<div style='margin-top: 10px; padding: 10px; background: #fff3e0; border: 1px solid #ffcc80; border-radius: 4px; font-size: 13px; color: #e65100;'><b>Mailbox Internal Temperature:</b> ${currentTemp}° ${outsideTempSensor && !tempSensor ? '(Estimated)' : ''}</div>"
                    }
                }
                
                def switchState = mailSwitch.currentValue("switch")?.toUpperCase() ?: "UNKNOWN"
                def switchColor = (switchState == "ON") ? "red" : "green"
                def indicatorText = (switchState == "ON") ? "MAIL WAITING" : "EMPTY / WAITING FOR DELIVERY"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Indicator Switch:</b> <span style='color: ${switchColor}; font-weight: bold;'>${switchState}</span></div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>Current State:</b> <b>${indicatorText}</b></div>"
                statusText += "</div>"

                paragraph statusText
                
                input "btnClearMail", "button", title: "Manually Clear Mail Status & Lights"
                
            } else {
                paragraph "<i>Configure devices below to see live system status.</i>"
            }
        }
    
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as events occur.</i>"
            }
        }
        
        section("Device Configuration") {
            input "mailSensors", "capability.contactSensor", title: "Mailbox Door Sensor(s)", multiple: true, required: true
            input "tempSensor", "capability.temperatureMeasurement", title: "Internal Mailbox Temperature Sensor (Preferred)", required: false
            input "outsideTempSensor", "capability.temperatureMeasurement", title: "OR Outside Air Temperature Sensor (Fallback)", required: false
            input "tempOffset", "number", title: "Estimated Mailbox Heat Offset (° added to outside temp)", defaultValue: 20, required: false
            input "tempThreshold", "number", title: "Temperature Alert Threshold (°)", defaultValue: 90, required: false
            input "mailSwitch", "capability.switch", title: "Virtual Mail Indicator Switch", required: true
            input "deliveryLockout", "number", title: "State Change Lockout (Minutes)", defaultValue: 2, required: true
        }

        section("Delivery Time Restrictions") {
            input "enableDeliveryWindow", "bool", title: "Restrict Delivery Detection to a Time Window?", defaultValue: false, submitOnChange: true
            if (enableDeliveryWindow) {
                paragraph "Only allow 'Mail Delivered' events between these times. This prevents false deliveries when you drop off outgoing mail in the morning or evening."
                input "deliveryStartTime", "time", title: "Delivery Window Start Time", required: true
                input "deliveryEndTime", "time", title: "Delivery Window End Time", required: true
            }
        }

        section("Home Activity Tracking (Doors & Arrivals)") {
            paragraph "These sensors are used to calculate your 'Retrieval Trip Time' and are optional. They can also be used to prevent false mail deliveries."
            input "exteriorDoors", "capability.contactSensor", title: "Exterior Doors (Front, Garage, etc.)", multiple: true, required: false
            input "arrivalSensors", "capability.presenceSensor", title: "Arrival Sensors (Mobile Phones)", multiple: true, required: false
            
            input "enableSecondaryCheck", "bool", title: "Enable False Retrieval Protection? (Requires sensors above)", defaultValue: false, submitOnChange: true
            if (enableSecondaryCheck) {
                input "activityTimeWindow", "number", title: "Activity Time Window (Minutes)", defaultValue: 10, required: true
            }
        }

        section("Visual Indicators (Colored Lights)") {
            input "indicatorLight", "capability.colorControl", title: "Standard RGB Lights", required: false, multiple: true
            
            input "inovelliSwitches", "capability.pushableButton", title: "Inovelli Red Series Switches", required: false, multiple: true, submitOnChange: true
            if (inovelliSwitches) {
                input "inovelliTarget", "enum", title: "Inovelli Target LEDs", required: true, defaultValue: "All", options: [
                    "All":"All LEDs", "7":"LED 7 (Top)", "6":"LED 6", "5":"LED 5", "4":"LED 4 (Middle)", "3":"LED 3", "2":"LED 2", "1":"LED 1 (Bottom)"
                ]
            }

            input "deliveryColor", "enum", title: "Color when Mail is Delivered", required: false, defaultValue: "Green", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"]
            input "lightLevel", "number", title: "Indicator Light Level (%)", defaultValue: 100, required: false, range: "1..100"
            input "retrievalLightAction", "enum", title: "Action when Mail is Retrieved", required: false, defaultValue: "Turn Off", options: ["Turn Off", "Leave On"]
        }
        
        section("Integration & External Overrides") {
            paragraph "<b>Freeze other applications during delivery</b><br>If you are using shared lights for your mail notification (such as a living room lamp managed by a motion lighting app), those other apps might try to turn the light off while the mail notification is active.<br><br>Select a Virtual Switch here. This app will turn it ON during a delivery. You can use that switch in your other apps to 'freeze' or 'disable' them until the mail is retrieved."
            input "overrideSwitch", "capability.switch", title: "State Override Switch (Freezes external apps)", required: false
            
            paragraph "<b>Yield to Higher Priority Applications</b><br>If another app (like the School Bus sequence) is actively using the lights, select its active switch here. Mail deliveries will be tracked, but the lights will wait to turn on until the priority app is finished."
            input "priorityYieldSwitch", "capability.switch", title: "Priority Yield Switch (e.g., School Active Switch)", required: false
        }

        section("Audio Announcements & Notifications") {
            paragraph "<b>Smart Speakers (TTS)</b>"
            input "ttsSpeakers", "capability.speechSynthesis", title: "Smart Speakers", multiple: true, required: false
            input "ttsDeliveryText", "text", title: "Delivery Announcement Text", defaultValue: "The mail has arrived"
            input "ttsRetrievalText", "text", title: "Retrieval Announcement Text", defaultValue: "The mail has been retrieved"
            
            paragraph "<b>Zooz Siren & Chime</b>"
            input "zoozChimes", "capability.chime", title: "Zooz Chime Devices", multiple: true, required: false, submitOnChange: true
            if (zoozChimes) {
                input "zoozSoundDelivery", "number", title: "Sound File #: Mail Arrived", required: false
                input "zoozSoundMore", "number", title: "Sound File #: More Mail Arrived", required: false
                input "zoozSoundRetrieval", "number", title: "Sound File #: Mail Retrieved", required: false
            }

            paragraph "<b>Push Notifications</b>"
            input "pushDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false
            input "sendPushDelivery", "bool", title: "Push on Delivery?", defaultValue: false
            input "sendPushRetrieval", "bool", title: "Push on Retrieval?", defaultValue: false
        }
       
        section("Security & Nags") {
            input "securityStartTime", "time", title: "Security Start", required: false
            input "securityEndTime", "time", title: "Security End", required: false
            
            // Single-time Daily Nag
            input "enableNag", "bool", title: "Enable Single Daily Reminder?", defaultValue: false, submitOnChange: true
            if (enableNag) {
                input "nagTime", "time", title: "Time to check", required: true
                input "nagMessage", "text", title: "Nag Message", defaultValue: "Don't forget the mail!", required: true
            }
            
            paragraph "<hr>"
            
            // Hourly Mode-Restricted Nag
            input "enableHourlyNag", "bool", title: "Enable Hourly Mode-Restricted Reminder?", defaultValue: false, submitOnChange: true
            if (enableHourlyNag) {
                paragraph "If the mail is active and the hub is in one of the selected modes, this will run every hour with its own dedicated sound/TTS instead of repeating the 'Mail Arrived' alerts."
                input "hourlyNagModes", "mode", title: "Only run in these Modes:", multiple: true, required: true
                input "ttsHourlyText", "text", title: "Hourly Nag TTS Announcement Text", defaultValue: "The mail is still waiting to be retrieved", required: false
                if (zoozChimes) {
                    input "zoozSoundHourly", "number", title: "Sound File #: Hourly Nag Reminder", required: false
                }
            }
        }

        section("System Settings") {
            input "activeModes", "mode", title: "Active Modes", multiple: true, required: false
            input "btnForceReset", "button", title: "Reset Historical Averages & Logs"
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.historyLog = state.historyLog ?: []
    subscribe(mailSensors, "contact.open", sensorOpenHandler)
    
    if (tempSensor) {
        subscribe(tempSensor, "temperature", tempHandler)
    } else if (outsideTempSensor) {
        subscribe(outsideTempSensor, "temperature", tempHandler)
    }
    
    if (exteriorDoors) subscribe(exteriorDoors, "contact.open", homeActivityHandler)
    if (arrivalSensors) subscribe(arrivalSensors, "presence.present", homeActivityHandler)
    if (priorityYieldSwitch) subscribe(priorityYieldSwitch, "switch", priorityYieldHandler)
    
    if (inovelliSwitches) subscribe(inovelliSwitches, "switch.off", inovelliMailSwitchOffHandler)
    
    schedule("0 0 0 * * ?", "midnightReset")
 
    if (enableNag && nagTime) schedule(nagTime, "nagHandler")
    
    if (enableHourlyNag) runEvery1Hour("hourlyNagHandler")
}

def inovelliMailSwitchOffHandler(evt) {
    if (mailSwitch && mailSwitch.currentValue("switch") == "on") {
        log.info "Switch '${evt.device.displayName}' turned off, but Mail is still waiting. Re-applying Mail LED indicator."
        setLightColor([evt.device], deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
    }
}

def priorityYieldHandler(evt) {
    if (evt.value == "off") {
        if (mailSwitch.currentValue("switch") == "on") {
            log.info "Priority sequence ended. Mail is waiting. Activating indicators."
            addToHistory("RESUMING: Priority ended. Turning on mail lights.")
            
            if (indicatorLight) captureLightState(indicatorLight)
            
            if (overrideSwitch && overrideSwitch.currentValue("switch") != "on") {
                overrideSwitch.on()
            }
            
            if (indicatorLight) setLightColor(indicatorLight, deliveryColor, lightLevel ?: 100, "All")
            if (inovelliSwitches) setLightColor(inovelliSwitches, deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnClearMail") {
        log.info "Manually clearing mail status..."
        if (mailSwitch) mailSwitch.off()
      
        if (indicatorLight) {
            if (retrievalLightAction == "Turn Off") restoreLightState(indicatorLight)
        }
        if (inovelliSwitches) {
            inovelliSwitches.each { device -> 
                def target = inovelliTarget ?: "All"
                if (target == "All") {
                    if (device.hasCommand("ledEffectAll")) device.ledEffectAll(255, 0, 0, 0)
                } else {
                    if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 255, 0, 0, 0)
                }
            }
        }
        if (overrideSwitch) overrideSwitch.off()
        state.lastValidStateChange = new Date().time 
        addToHistory("MANUAL CLEAR: System reset via app dashboard.")
        
    } else if (btn == "btnForceReset") {
        log.info "Resetting historical data..."
        state.historyLog = []
        state.avgDeliveryTime = null
        state.avgRetrievalTime = null
        state.deliveryCount = 0
        state.retrievalCount = 0
        state.todayDeliveryTime = null
        state.todayRetrievalTime = null
        state.lastRetrievalWalkTime = null
        addToHistory("SYSTEM WIPE: Historical data reset.")
    }
}

def homeActivityHandler(evt) { state.lastHomeActivity = new Date().time }

def sensorOpenHandler(evt) {
    def now = new Date().time
    def switchState = mailSwitch.currentValue("switch")
    def tz = location.timeZone ?: TimeZone.getDefault()
    
    // 1. HARD LOCKOUT CHECK FIRST
    def lastStateChange = state.lastValidStateChange ?: 0
    def lockoutMillis = (deliveryLockout != null ? deliveryLockout.toInteger() : 2) * 60000
    
    if ((now - lastStateChange) < lockoutMillis) {
        log.debug "Ignored: Sensor opened during the ${deliveryLockout} minute lockout window."
        return
    }

    // 2. DELIVERY WINDOW RESTRICTION CHECK
    if (switchState != "on" && enableDeliveryWindow && deliveryStartTime && deliveryEndTime) {
        def isWithinWindow = timeOfDayIsBetween(deliveryStartTime, deliveryEndTime, new Date(), tz)
        if (!isWithinWindow) {
            log.info "Mailbox opened outside delivery window. Ignoring delivery event."
            addToHistory("IGNORED: Opened outside of delivery window.")
            return 
        }
    }

    // 3. CONCURRENCY DEBOUNCE LAST (Only updates if event is valid)
    def lastEvt = atomicState.lastSensorEvent ?: 0
    if ((now - lastEvt) < 5000) return 
    atomicState.lastSensorEvent = now

    // --- PAST ALL GATES: PROCESS THE EVENT ---
    def currentTimeStr = new Date().format("h:mm a", tz)
    def currentMinutes = getMinutesSinceMidnight(new Date(), tz)

    if (switchState == "on") {
        // --- MAIL RETRIEVAL LOGIC ---
        if (enableSecondaryCheck && (exteriorDoors || arrivalSensors)) {
            def lastActivity = state.lastHomeActivity ?: 0
            def window = (activityTimeWindow ?: 10) * 60000
            
            if ((now - lastActivity) > window) {
                state.lastValidStateChange = now
                
                if (sendPushDelivery) sendMessage("📫 More mail was delivered!")
                
                if (settings.zoozChimes && settings.zoozSoundMore != null) {
                    playZoozChime(settings.zoozSoundMore)
                }
                
                addToHistory("SECONDARY DELIVERY: No home activity detected.")
                return
            }
        }

        def tripTimeStr = ""
        if ((exteriorDoors || arrivalSensors) && state.lastHomeActivity) {
            def timeDiff = now - state.lastHomeActivity
            if (timeDiff <= 900000) { 
                def totalSecs = Math.round(timeDiff / 1000).toInteger()
                state.lastRetrievalWalkTime = totalSecs
                tripTimeStr = " (Trip Time: ${formatSeconds(totalSecs)})"
            }
        }

        mailSwitch.off()
        state.lastValidStateChange = now
        state.todayRetrievalTime = currentTimeStr
        updateAverage("retrieval", currentMinutes)
        addToHistory("RETRIEVAL DETECTED.${tripTimeStr}")
  
        if (retrievalLightAction == "Turn Off") {
            if (indicatorLight) restoreLightState(indicatorLight)
            if (inovelliSwitches) {
                inovelliSwitches.each { device -> 
                    def target = inovelliTarget ?: "All"
                    if (target == "All") {
                        if (device.hasCommand("ledEffectAll")) device.ledEffectAll(255, 0, 0, 0)
                    } else {
                        if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 255, 0, 0, 0)
                    }
                }
            }
        }
        
        if (overrideSwitch) overrideSwitch.off()
  
        if (sendPushRetrieval) sendMessage("📬 Mail retrieved!")
        if (ttsSpeakers && ttsRetrievalText) ttsSpeakers.speak(ttsRetrievalText)
        if (settings.zoozChimes && settings.zoozSoundRetrieval != null) {
            playZoozChime(settings.zoozSoundRetrieval)
        }
 
    } else {
        // --- MAIL DELIVERY LOGIC ---
        mailSwitch.on()
        
        state.lastValidStateChange = now
        state.todayDeliveryTime = currentTimeStr
        updateAverage("delivery", currentMinutes)
        addToHistory("DELIVERY DETECTED.")
        
        if (sendPushDelivery) sendMessage("📫 Mail delivered!")
        if (ttsSpeakers && ttsDeliveryText) ttsSpeakers.speak(ttsDeliveryText)
        
        if (settings.zoozChimes && settings.zoozSoundDelivery != null) {
            playZoozChime(settings.zoozSoundDelivery)
        }

        if (priorityYieldSwitch && priorityYieldSwitch.currentValue("switch") == "on") {
            addToHistory("YIELD: Priority sequence active. Delaying lights.")
            return 
        }
        
        if (indicatorLight) captureLightState(indicatorLight)
 
        if (overrideSwitch && overrideSwitch.currentValue("switch") != "on") {
            overrideSwitch.on()
        }
        
        if (indicatorLight) setLightColor(indicatorLight, deliveryColor, lightLevel ?: 100, "All")
        if (inovelliSwitches) setLightColor(inovelliSwitches, deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
    }
}

// === STATE CAPTURE ENGINE ---
def captureLightState(devices) {
    if (!state.savedLightStates) state.savedLightStates = [:]
    
    devices.each { dev ->
        state.savedLightStates[dev.id] = [
            switch: dev.currentValue("switch"),
            hue: dev.currentValue("hue"),
            saturation: dev.currentValue("saturation"),
            level: dev.currentValue("level"),
            colorTemperature: dev.currentValue("colorTemperature")
        ]
        log.info "Captured previous state for ${dev.displayName}: ${state.savedLightStates[dev.id]}"
    }
}

def restoreLightState(devices) {
    if (!state.savedLightStates) return
    
    devices.each { dev ->
        def saved = state.savedLightStates[dev.id]
        if (saved) {
            if (saved.switch == "on") {
                if (saved.colorTemperature) {
                    dev.setColorTemperature(saved.colorTemperature, saved.level)
                } else if (saved.hue != null && saved.saturation != null) {
                    dev.setColor([hue: saved.hue, saturation: saved.saturation, level: saved.level])
                } else {
                    dev.on()
                    if (saved.level) dev.setLevel(saved.level)
                }
                log.info "Restored ${dev.displayName} to ON state."
            } else {
                dev.off()
                log.info "Restored ${dev.displayName} to OFF state."
            }
        } else {
            dev.off() 
        }
    }
    state.savedLightStates = [:] 
}

def setLightColor(devices, colorName, level, target = "All") {
    def inovelliHue = 0 
    def standardHue = 0
    def standardSat = 100
    
    switch(colorName) {
        case "White": inovelliHue = 255; standardSat = 0; break 
        case "Red": inovelliHue = 0; standardHue = 0; break 
        case "Green": inovelliHue = 85; standardHue = 33; break 
        case "Blue": inovelliHue = 170; standardHue = 66; break 
        case "Yellow": inovelliHue = 42; standardHue = 16; break 
        case "Orange": inovelliHue = 14; standardHue = 10; break 
        case "Purple": inovelliHue = 191; standardHue = 75; break 
        case "Pink": inovelliHue = 234; standardHue = 83; break 
    }
    
    devices.each { device -> 
        if (device.hasCommand("ledEffectAll") || device.hasCommand("ledEffectOne")) {
            if (target == "All") {
                if (device.hasCommand("ledEffectAll")) device.ledEffectAll(1, inovelliHue, level as Integer, 255) 
            } else {
                if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 1, inovelliHue, level as Integer, 255)
            }
        } else {
            device.on() 
            device.setColor([hue: standardHue, saturation: standardSat, level: level as Integer])
        }
    }
}

def tempHandler(evt) {
    def currentTemp = evt.numericValue ?: evt.value.toDouble()
    if (evt.device.id == outsideTempSensor?.id) currentTemp += (tempOffset ?: 20)

    if (mailSwitch.currentValue("switch") == "on" && currentTemp >= (tempThreshold ?: 90)) {
        def today = new Date().format("yyyy-MM-dd", location.timeZone ?: TimeZone.getDefault())
        if (state.lastTempAlertDate != today) {
            sendMessage("🌡️ Warning: Box is estimated to be ${currentTemp}°. Get mail soon!")
            state.lastTempAlertDate = today
        }
    }
}

// === AUDIO PLAYBACK LOGIC ===
def playZoozChime(soundNum) {
    if (!settings.zoozChimes || soundNum == null) return
    
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    settings.zoozChimes.eachWithIndex { chime, index ->
        if (index > 0) pauseExecution(1000)
        try {
            if (chime.hasCommand("playSound") && trackNum != null) {
                chime.playSound(trackNum)
            } else if (chime.hasCommand("playTrack")) {
                chime.playTrack(soundNum.toString())
            } else if (chime.hasCommand("chime") && trackNum != null) {
                chime.chime(trackNum)
            } else {
                log.error "${chime.displayName} does not support standard audio/siren commands (playSound, playTrack, or chime)."
            }
        } catch (e) {
            log.error "${chime.displayName} failed to play sound: ${e.message ?: e}"
        }
    }
}

def nagHandler() {
    if (enableNag && mailSwitch?.currentValue("switch") == "on") {
        log.info "Nag scheduled task running: Mail has not been retrieved."
        def msg = nagMessage ?: "Don't forget the mail!"
        sendMessage(msg)
        if (ttsSpeakers) ttsSpeakers.speak(msg)
        addToHistory("NAG ALERT: Reminder sent.")
    }
}

def hourlyNagHandler() {
    if (enableHourlyNag && mailSwitch?.currentValue("switch") == "on") {
        def currentMode = location.mode
        def allowedModes = hourlyNagModes ?: []
        
        if (allowedModes.contains(currentMode)) {
            log.info "Hourly Mode-Restricted Nag executing in mode: ${currentMode}"
            
            if (ttsSpeakers && ttsHourlyText) {
                ttsSpeakers.speak(ttsHourlyText)
            }
            if (settings.zoozChimes && settings.zoozSoundHourly != null) {
                playZoozChime(settings.zoozSoundHourly)
            }
            
            addToHistory("HOURLY NAG ALERT: Announcement triggered.")
        } else {
            log.info "Skipping hourly nag because current mode '${currentMode}' is not selected."
        }
    }
}

def sendMessage(msg) { settings.pushDevices ? settings.pushDevices*.deviceNotification(msg) : sendPush(msg) }

def updateAverage(type, currentMinutes) {
    def count = state."${type}Count" ?: 0
    def currentAvg = state."avg${type.capitalize()}Time" ?: currentMinutes
    state."avg${type.capitalize()}Time" = ((currentAvg * count) + currentMinutes) / (count + 1)
    state."${type}Count" = count + 1
}

def getMinutesSinceMidnight(date, tz) {
    return (new Date(date.time).format("H", tz).toInteger() * 60) + new Date(date.time).format("m", tz).toInteger()
}

def minutesToTimeStr(minutesNum) {
    if (!minutesNum) return "--:-- --"
    int totalMins = Math.round(minutesNum.toDouble()).toInteger() 
    int h = (totalMins / 60).toInteger()
    int m = totalMins % 60
  
    def ampm = h >= 12 ? "PM" : "AM"
    h = h % 12 ?: 12
    return "${h}:${m < 10 ? '0'+m : m} ${ampm}"
}

def formatSeconds(totalSecs) {
    if (!totalSecs) return "--"
    int m = (totalSecs / 60).toInteger()
    int s = totalSecs % 60
    if (m > 0) return "${m}m ${s}s"
    return "${s}s"
}

def addToHistory(msg) {
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone ?: TimeZone.getDefault())
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
}

def midnightReset() {
    state.todayDeliveryTime = state.todayRetrievalTime = state.lastTempAlertDate = null
    state.lastRetrievalWalkTime = null 
    if (mailSwitch.currentValue("switch") == "on") {
        addToHistory("SYSTEM RESET: Mail left overnight.")
    }
}
