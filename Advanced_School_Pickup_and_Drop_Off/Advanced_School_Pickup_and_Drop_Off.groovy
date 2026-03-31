/**
 * Advanced School Pickup and Drop Off
 */
definition(
    name: "Advanced School Pickup and Drop Off",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced bus indicator with weather warnings, safe arrival, departure tracking, full-day sick mode, mode restrictions, smart iCal automation, and Master Kill Switch.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Advanced School Pickup & Drop Off", install: true, uninstall: true) {
        
     
        section("Live Tracker & Averages") {
            def amCountdown = getCountdownText(amStage3, false)
            def pmCountdown = getCountdownText(pmStage3, true)
            
            def amAvg = (state.amDoorCount && state.amDoorCount > 0) ?
"${Math.round(state.amTotalDoorTime / state.amDoorCount / 60)}m ${Math.round(state.amTotalDoorTime / state.amDoorCount % 60)}s" : "N/A"
            def pmAvg = (state.pmDoorCount && state.pmDoorCount > 0) ?
"${Math.round(state.pmTotalDoorTime / state.pmDoorCount / 60)}m ${Math.round(state.pmTotalDoorTime / state.pmDoorCount % 60)}s" : "N/A"
            
            def dashText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            dashText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Event Status</th><th style='padding: 8px;'>Stage 3 Countdown</th><th style='padding: 8px;'>Avg Door Time</th></tr>"
            dashText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Morning Pickup</b></td><td style='padding: 8px; color:#aa0000;'><b>${amCountdown}</b></td><td style='padding: 8px;'>${amAvg}</td></tr>"
            dashText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Afternoon Drop-off</b></td><td style='padding: 8px; color:#aa0000;'><b>${pmCountdown}</b></td><td style='padding: 8px;'>${pmAvg}</td></tr>"
            dashText += "</table>"
            
            def sysStatus = "ACTIVE"
            def statusColor = "green"
        
            if (!isSystemEnabled()) { sysStatus = "PAUSED (Master Switch OFF)";
statusColor = "red" }
            else if (!isSchoolDay()) { sysStatus = "NO SCHOOL TODAY";
statusColor = "#888" }
            else if (isSickDay()) { sysStatus = "SICK DAY (Skipping Routines)";
statusColor = "#ff8c00" }

            dashText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            dashText += "<b>System State:</b> <span style='color: ${statusColor}; font-weight: bold;'>${sysStatus}</span></div>"

            // --- UI 3-Day Forecast Engine ---
            if (state.calendarForecastHtml) {
                dashText += state.calendarForecastHtml
            }

            // --- UI Logging Engine ---
            def historyText = "<div style='margin-top: 10px; padding: 10px; background: #fff; border-radius: 4px; font-size: 12px; border: 1px solid #ccc; max-height: 180px; overflow-y: auto;'>"
            historyText += "<h4 style='margin-top: 0px; margin-bottom: 5px; color: #333;'>System Log & History</h4>"
            if (state.actionHistory && state.actionHistory.size() > 0) {
                historyText += "<ul style='margin: 0; padding-left: 20px; color: #555;'>"
                state.actionHistory.each { entry -> historyText += "<li style='margin-bottom: 4px;'>${entry}</li>" }
                historyText += "</ul>"
            } else {
               
                historyText += "<i style='color: #888;'>No recent activity logged. Waiting for events...</i>"
            }
            historyText += "</div>"
            dashText += historyText
            // -----------------------

            paragraph dashText
            
           
            input "forceSyncBtn", "button", title: "Force Sync Calendar Now"
            input "resetStatsBtn", "button", title: "Reset Average Statistics"
            input "clearLogBtn", "button", title: "Clear System Log"
        }

        section("1. Master Control & Conditions") {
            paragraph "MASTER SWITCH: If this switch is selected and turned OFF, the entire application is completely disabled."
       
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false

            input "schoolDaySwitch", "capability.switch", title: "School Day Virtual Switch", required: true
            input "enableCalendar", "bool", title: "Auto-update switch via calendar?", defaultValue: true
            
            input "iCalUrl", "text", title: "iCal Feed URL (https://...)", defaultValue: "https://www.elmoreco.com/sys/calendar/export?widgetId=2e3db4a9b8694ac78b296c1ee5c72bab&type=1", required: false
         
            input "holidayKeywords", "text", title: "Keywords indicating NO school", defaultValue: "spring break, teacher workday, juneteenth, no school, no students, holiday, closed, staff development, professional development, weather, e-learning, elearning, thanksgiving, christmas, winter break, fall break, presidents day, mlk", required: false

            input "allowedModes", "mode", title: "Allowed Location Modes", multiple: true, required: false
            input "sickDaySwitch", "capability.switch", title: "Sick Day Virtual Switch", required: false
            input "busLight", "capability.colorControl", title: "Select Hue Light", required: true
        }

        section("2. Weather & Umbrella Warning") {
            input "weatherDevice", "capability.sensor", title: "Weather Sensor (e.g., OpenWeatherMap)", required: false
            input "rainCondition", "text", title: "Weather condition indicating rain", defaultValue: "Rain"
            input "rainSwitch", "capability.switch", title: "Rain Virtual Switch", required: false
            input "sprinklingSwitch", "capability.switch", title: "Sprinkling Virtual Switch", required: false
        }

      
        section("3. Notifications & Audio") {
            input "notifyPhones", "capability.notification", title: "Send Push Alerts to Phones", multiple: true, required: false
            input "ttsDevice", "capability.speechSynthesis", title: "Audio Announcement Device (Echo/Sonos)", required: false
            input "zoozSirens", "capability.actuator", title: "Zooz Speakers and Sirens", multiple: true, required: false, description: "Select devices to play numbered sound files at each stage."
            input "audioAllowedModes", "mode", title: "Modes for Audio Announcements", multiple: true, required: false
        }

        section("4. Safe Arrival & Departure (Sensors)") {
            input "doorSensor", "capability.contactSensor", title: "Front Door Sensor", required: false
            input "motionSensor", "capability.motionSensor", title: "Outside Drop Location Motion Sensor (Optional)", required: false
            input "ignorePmMotion", "bool", title: "Ignore motion requirement in the afternoon?", defaultValue: false, description: "Enable this if your sensor only reports motion in the dark."
            input "departureTimeout", "number", title: "Minutes after AM Stage 3 before 'Missed Bus' alert", defaultValue: 5
            input "arrivalTimeout", "number", title: "Minutes to wait for door/motion before afternoon alert", defaultValue: 15
        }

        section("5. Early Dismissal Override") {
            input "earlyDismissalSwitch", "capability.switch", title: "Early Dismissal Virtual Switch", required: false
            input "earlyOffset", "number", title: "Minutes to shift afternoon schedule earlier", defaultValue: 120
  
        }

        section("6. Dynamic Lighting Effects") {
            input "enableBlinking", "bool", title: "Enable Proximity Blinking", defaultValue: false, description: "If enabled, the selected Hue light will blink, increasing in speed as the next stage time approaches."
        }

        section("7. System Testing") {
            paragraph "Use these buttons to force a simulated rapid run of the routines. Stages will cycle every 30 seconds."
            input "testAmBtn", "button", title: "Test Morning Pickup Routine"
            input "testPmBtn", "button", title: "Test Afternoon Drop-Off Routine"
            input "stopTestBtn", "button", title: "Stop Active Test & Turn Off Light"
        }
        
        section("8. Integration & External Overrides") {
      
            paragraph "<b>Freeze other applications during sequence</b><br>If you are using shared lights for your bus indicator (such as a porch light managed by a motion lighting app), those other apps might try to turn the light off while the bus notification is active.<br><br>Select a Virtual Switch here. This app will turn it ON during an active bus sequence. You can use that switch in your other apps to 'freeze' or 'disable' them until the sequence completes."
            input "overrideSwitch", "capability.switch", title: "State Override Switch (Freezes external apps)", required: false
        }

        section("Morning Pickup Schedule") {
            input "amStage1", "time", title: "Time for Stage 1 (Get Ready)", required: false
            input "amColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "amSound1", "number", title: "Stage 1 Sound File # (Optional)", required: false
            input "testAmSound1Btn", "button", title: "Test AM Stage 1 Sound"
            
            input "amStage2", "time", title: "Time for Stage 2 (Almost Here)", required: false
            input "amColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "amSound2", "number", title: "Stage 2 Sound File # (Optional)", required: false
            input "testAmSound2Btn", "button", title: "Test AM Stage 2 Sound"
            
            input "amStage3", "time", title: "Time for Stage 3 (Bus Imminent)", required: false
            input "amColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "amSound3", "number", title: "Stage 3 Sound File # (Optional)", required: false
            input "testAmSound3Btn", "button", title: "Test AM Stage 3 Sound"
            
            input "amClear", "time", title: "Time to turn light OFF", required: false
        }

        section("Afternoon Drop-Off Schedule") {
            input "pmStage1", "time", title: "Time for Stage 1 (Bus left school)", required: false
            input "pmColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "pmSound1", "number", title: "Stage 1 Sound File # (Optional)", required: false
            input "testPmSound1Btn", "button", title: "Test PM Stage 1 Sound"
            
            input "pmStage2", "time", title: "Time for Stage 2 (Approaching neighborhood)", required: false
            input "pmColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "pmSound2", "number", title: "Stage 2 Sound File # (Optional)", required: false
            input "testPmSound2Btn", "button", title: "Test PM Stage 2 Sound"
            
            input "pmStage3", "time", title: "Time for Stage 3 (At the stop)", required: false
            input "pmColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "pmSound3", "number", title: "Stage 3 Sound File # (Optional)", required: false
            input "testPmSound3Btn", "button", title: "Test PM Stage 3 Sound"
            
            input "pmClear", "time", title: "Time to turn light OFF", required: false
        }
    }
}

def installed() {
    log.debug "Installed Advanced School Pickup and Drop Off"
    initialize()
}

def updated() {
    log.debug "Updated Advanced School Pickup and Drop Off"
    unsubscribe()
    unschedule()
    
    initialize()
}

def initialize() {
    state.amTotalDoorTime = state.amTotalDoorTime ?: 0
    state.amDoorCount = state.amDoorCount ?: 0
    state.pmTotalDoorTime = state.pmTotalDoorTime ?: 0
    state.pmDoorCount = state.pmDoorCount ?: 0
    
    state.isBlinking = false
    state.isTesting = false
    state.lightActiveByApp = state.lightActiveByApp ?: false 
    state.actionHistory = state.actionHistory ?: []
    state.calendarForecastHtml = state.calendarForecastHtml ?: ""

    logAction("System Initialized/Updated.")

    if (doorSensor) {
        subscribe(doorSensor, "contact.open", doorOpenedHandler)
    
    }
    
    if (motionSensor) {
        subscribe(motionSensor, "motion.active", motionActiveHandler)
    }

    if (enableCalendar) {
        runEvery30Minutes("checkSchoolCalendar")
        runIn(5, "checkSchoolCalendar")
    }

    if (amStage1) schedule(amStage1, "amSetStage1")
    if (amStage2) schedule(amStage2, "amSetStage2")
    if (amStage3) schedule(amStage3, "amSetStage3")
    if (amClear) schedule(amClear, "turnLightOff")

    schedule("0 1 0 ? * *", "scheduleDailyPMEvents")
    scheduleDailyPMEvents() 
}

// --- Logging & Helper Methods ---

def logAction(String msg) {
    def timeString = new Date().format("MM/dd h:mm a", location.timeZone)
    def entry = "<b>${timeString}</b>: ${msg}"
    def list = state.actionHistory ?: []
    list.add(0, entry)
    if (list.size() > 10) list = list[0..9] 
    state.actionHistory = list
    log.info "UI LOG: ${msg}"
}

def isSystemEnabled() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isSchoolDay() {
    return schoolDaySwitch?.currentValue("switch") == "on"
}

def isSickDay() {
    return sickDaySwitch?.currentValue("switch") == "on"
}

def isAllowedMode() {
    if (!allowedModes) return true
    return allowedModes.contains(location.mode)
}

def isAudioAllowedMode() {
    if (!audioAllowedModes) return true
    return audioAllowedModes.contains(location.mode)
}

def isItRaining() {
    if (rainSwitch && rainSwitch.currentValue("switch") == "on") return true
    if (sprinklingSwitch && sprinklingSwitch.currentValue("switch") == "on") return true
    String currentCondition = weatherDevice?.currentValue("weather") ?: ""
    return currentCondition.contains(rainCondition ?: "Rain")
}

// --- Audio Helper Engine ---

def playSirenSound(soundNum, force = false) {
    if (!zoozSirens || soundNum == null) return
    if (!force && !isAudioAllowedMode()) return
    
    logAction("Playing sound file ${soundNum} on selected Zooz Sirens.")
    zoozSirens.each { siren ->
        try {
            // Attempt standard Chime capability command
            siren.playSound(soundNum as Integer)
        } catch (MissingMethodException e) {
            try {
        
                // Fallback to AudioNotification capability command
                siren.playTrack(soundNum as String)
            } catch (Exception ex) {
                log.error "${siren.displayName} failed to play sound: ${ex.message}"
                logAction("Error playing Zooz sound ${soundNum} on ${siren.displayName}. See logs.")
       
            }
        } catch (Exception e) {
            log.error "${siren.displayName} failed to play sound: ${e.message}"
        }
    }
}

// --- Dynamic PM Scheduling Engine ---

def scheduleDailyPMEvents() {
    scheduleTodayEvent(pmStage1, "pmSetStage1")
    scheduleTodayEvent(pmStage2, "pmSetStage2")
    scheduleTodayEvent(pmStage3, "pmSetStage3")
    scheduleTodayEvent(pmClear, "turnLightOff")
}

def scheduleTodayEvent(timeInput, handlerMethod) {
    if (!timeInput) return
    Date scheduledTime = timeToday(timeInput, location.timeZone)
    if (earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = earlyOffset ?: 120
        use(groovy.time.TimeCategory) { scheduledTime = scheduledTime - offset.minutes }
    }
    
    if (scheduledTime.after(new Date())) {
        runOnce(scheduledTime, handlerMethod, [overwrite: true])
    }
}

// ------------------------------------

def getEpochTime(timeStr, isPm = false) {
    if (!timeStr) return new Date().time + 3600000 
    Date target = timeToday(timeStr, location.timeZone)
    if (isPm && earlyDismissalSwitch?.currentValue("switch") == "on") {
       
        int offset = earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    return target.time
}

def getCountdownText(timeStr, isPm = false) {
    if (!timeStr) return "Not Configured"
    def now = new Date()
    def target = timeToday(timeStr, location.timeZone)
    
    if (isPm && earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    
    if (target.before(now)) return "Completed Today"
    
    long diff = target.time - now.time
    long hours = diff / 3600000
    long mins = (diff % 3600000) / 60000
    if (hours > 0) return "${hours}h ${mins}m"
    return "${mins} Minutes"
}

def getHueColor(colorName) {
    switch(colorName) {
        case "Red": return [hue: 100, saturation: 100]
        case "Green": return [hue: 39, saturation: 100]
        case "Blue": return [hue: 65, saturation: 100]
        case "Yellow": return [hue: 16, saturation: 100]
        case "Orange": return [hue: 10, saturation: 100]
        case "Purple": return [hue: 75, saturation: 100]
        case "Pink": return [hue: 90, saturation: 100]
        case "White": return [hue: 0, saturation: 0]
        default: return [hue: 39, saturation: 100]
  
    }
}

def appButtonHandler(btn) {
    if (btn == "forceSyncBtn") {
        logAction("Manual Calendar Sync Triggered.")
        doCalendarSync(true)
    } else if (btn == "resetStatsBtn") {
        logAction("Reset Average Door Statistics")
        state.amTotalDoorTime = 0
        state.amDoorCount = 0
        state.pmTotalDoorTime = 0
        state.pmDoorCount = 0
    } else if (btn == "clearLogBtn") {
        state.actionHistory = []
    } else if (btn == "testAmBtn") {
        logAction("Initiating Morning TEST")
        startAmTest()
    } else if (btn == "testPmBtn") {
        logAction("Initiating Afternoon TEST")
        startPmTest()
    } else if (btn == "stopTestBtn") {
        logAction("Stopped Active TEST")
        turnLightOff()
    } else if (btn == "testAmSound1Btn") {
        if (settings.amSound1 != null) { logAction("Testing AM Stage 1 Sound..."); playSirenSound(settings.amSound1, true) }
        else { logAction("Please enter a sound number for AM Stage 1 first (click outside the box to register it).") }
    } else if (btn == "testAmSound2Btn") {
        if (settings.amSound2 != null) { logAction("Testing AM Stage 2 Sound..."); playSirenSound(settings.amSound2, true) }
        else { logAction("Please enter a sound number for AM Stage 2 first (click outside the box to register it).") }
    } else if (btn == "testAmSound3Btn") {
        if (settings.amSound3 != null) { logAction("Testing AM Stage 3 Sound..."); playSirenSound(settings.amSound3, true) }
        else { logAction("Please enter a sound number for AM Stage 3 first (click outside the box to register it).") }
    } else if (btn == "testPmSound1Btn") {
        if (settings.pmSound1 != null) { logAction("Testing PM Stage 1 Sound..."); playSirenSound(settings.pmSound1, true) }
        else { logAction("Please enter a sound number for PM Stage 1 first (click outside the box to register it).") }
    } else if (btn == "testPmSound2Btn") {
        if (settings.pmSound2 != null) { logAction("Testing PM Stage 2 Sound..."); playSirenSound(settings.pmSound2, true) }
        else { logAction("Please enter a sound number for PM Stage 2 first (click outside the box to register it).") }
    } else if (btn == "testPmSound3Btn") {
        if (settings.pmSound3 != null) { logAction("Testing PM Stage 3 Sound..."); playSirenSound(settings.pmSound3, true) }
        else { logAction("Please enter a sound number for PM Stage 3 first (click outside the box to register it).") }
    }
}

// --- STATE CAPTURE ENGINE ---
def captureLightState(dev) {
    if (!state.savedLightState) state.savedLightState = [:]
    
    state.savedLightState = [
        switch: dev.currentValue("switch"),
        hue: dev.currentValue("hue"),
        saturation: dev.currentValue("saturation"),
        level: dev.currentValue("level"),
        colorTemperature: dev.currentValue("colorTemperature")
    ]
    log.info "Captured previous state for ${dev.displayName}: ${state.savedLightState}"
}

def restoreLightState(dev) {
    if (!state.savedLightState) {
        
        dev.off()
        return
    }
    
    def saved = state.savedLightState
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
    
    state.savedLightState = [:] // Clear saved state
}

// --- Dynamic Sequence & Blink Engine ---

def startLightingSequence(colorName, isRainOverride, nextTargetEpoch, soundNum = null) {
    // FREEZE Motion App & Capture State BEFORE changing lights
    if (overrideSwitch && overrideSwitch.currentValue("switch") != "on") {
        overrideSwitch.on()
        if (busLight) captureLightState(busLight)
    }

    state.lightActiveByApp = true 

    if (soundNum != null) {
        playSirenSound(soundNum)
    
    }

    if (isRainOverride) {
        logAction("Precipitation detected: Showing Blue for 10 seconds before starting timer.")
        def blueMap = getHueColor("Blue")
        if (busLight) busLight.setColor([hue: blueMap.hue, saturation: blueMap.saturation, level: 80])
        runIn(10, "applyNormalStageColor", [data: [color: colorName, target: nextTargetEpoch], overwrite: true])
    } else {
        applyNormalStageColor([color: colorName, target: nextTargetEpoch])
    }
}

def applyNormalStageColor(data) {
    String colorName = data.color
    long nextTargetEpoch = data.target
    
    def colorMap = getHueColor(colorName)
    state.currentStageColor = colorMap
    
    if (busLight) busLight.setColor([hue: colorMap.hue, saturation: colorMap.saturation, level: 80])

    if (settings.enableBlinking) {
        state.isBlinking = true
        state.blinkState = true
        state.targetTime = nextTargetEpoch
        runIn(5, "blinkLoop", [overwrite: true])
    }
}

def blinkLoop() {
    if (!state.isBlinking) return
    
   
    if (!state.isTesting) {
        if (!isSystemEnabled() || isSickDay() || !isSchoolDay() || !isAllowedMode()) {
            state.isBlinking = false
            return
        }
    }

    long now = new Date().time
    long target = state.targetTime ?: now
    long diffSeconds = (target - now) / 1000
    if (diffSeconds < 0) diffSeconds = 0

    int interval = 15 
    if (diffSeconds <= 60) interval = 2 
    
    else if (diffSeconds <= 180) interval = 4 
    else if (diffSeconds <= 300) interval = 8 
    else if (diffSeconds <= 600) interval = 15 
    else interval = 20 

    state.blinkState = !state.blinkState

    if (state.blinkState) {
        if (busLight) busLight.setColor([hue: state.currentStageColor.hue, saturation: state.currentStageColor.saturation, level: 80])
    } else {
        if (busLight) busLight.off()
    }

    runIn(interval, "blinkLoop", [overwrite: true])
}

// --- Testing Routines ---

def startAmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForDeparture = true 
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSound1)
    runIn(30, "testAmStage2", [overwrite: true])
}

def testAmStage2() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSound2)
  
    runIn(30, "testAmStage3", [overwrite: true])
}

def testAmStage3() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSound3)
    String msg = "TEST: The school bus is imminent! Time to head to the door."
    if (notifyPhones) notifyPhones.deviceNotification(msg)
    if (ttsDevice && isAudioAllowedMode()) ttsDevice.speak(msg)
    runIn(30, "turnLightOff", [overwrite: true])
}

def startPmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSound1)
    runIn(30, "testPmStage2", [overwrite: true])
}

def testPmStage2() {
    if (!state.isTesting) return
 
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSound2)
    runIn(30, "testPmStage3", [overwrite: true])
}

def testPmStage3() {
    if (!state.isTesting) return
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSound3)
    String msg = "TEST: The school bus has arrived at the afternoon stop."
    if (notifyPhones) notifyPhones.deviceNotification(msg)
    if (ttsDevice && isAudioAllowedMode()) ttsDevice.speak(msg)
    
    runIn(30, "turnLightOff", [overwrite: true])
}

// --- Morning Routines ---

def amSetStage1() {
    if (!isSystemEnabled()) { logAction("AM Stage 1 Aborted: Master Switch is OFF");
return }
    if (!isSchoolDay()) { logAction("AM Stage 1 Aborted: Today is not a School Day");
return }
    if (isSickDay()) { logAction("AM Stage 1 Aborted: Sick Day is active");
return }
    if (!isAllowedMode()) { logAction("AM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed");
return }

    logAction("AM Stage 1 Started Successfully.")
    state.waitingForDeparture = true
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false

    long nextTarget = getEpochTime(amStage2, false)
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSound1)
}

def amSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 2 Skipped: Student already departed.");
return }
    
    logAction("AM Stage 2 Started.")
    long nextTarget = getEpochTime(amStage3, false)
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSound2)
}

def amSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 3 Skipped: Student already departed.");
return }
    
    logAction("AM Stage 3 Started. Awaiting conditions.")
    state.amTrackTime = new Date().time
    
    if (doorSensor || motionSensor) {
        int delaySeconds = (departureTimeout ?: 5) * 60
        runIn(delaySeconds, "checkMissedBus")
    }

    long nextTarget = getEpochTime(amClear, false)
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSound3)

    String msg = "The school bus is imminent! Time to head to the door."
    if (notifyPhones) notifyPhones.deviceNotification(msg)
    if (ttsDevice && isAudioAllowedMode()) ttsDevice.speak(msg)
}

// --- Afternoon Routines ---

def pmSetStage1() {
    if (!isSystemEnabled()) { logAction("PM Stage 1 Aborted: Master Switch is OFF");
return }
    if (!isSchoolDay()) { logAction("PM Stage 1 Aborted: Not a School Day");
return }
    if (isSickDay()) { logAction("PM Stage 1 Aborted: Sick Day is active");
return }
    if (!isAllowedMode()) { logAction("PM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed");
return }

    logAction("PM Stage 1 Started Successfully.")
    
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false

    long nextTarget = getEpochTime(pmStage2, true)
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSound1)
}

def pmSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived) { logAction("PM Stage 2 Skipped: Student already arrived.");
return }
    
    logAction("PM Stage 2 Started.")
    long nextTarget = getEpochTime(pmStage3, true)
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSound2)
}

def pmSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived) { logAction("PM Stage 3 Skipped: Student already arrived.");
return }
    
    logAction("PM Stage 3 Started. Awaiting conditions.")
    state.pmTrackTime = new Date().time
    
    if (doorSensor || motionSensor) {
        int delaySeconds = (arrivalTimeout ?: 15) * 60
        runIn(delaySeconds, "checkSafeArrival")
    }

    long nextTarget = getEpochTime(pmClear, true)
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSound3)

    String msg = "The school bus has arrived at the afternoon stop."
    if (notifyPhones) notifyPhones.deviceNotification(msg)
    if (ttsDevice && isAudioAllowedMode()) ttsDevice.speak(msg)
}

// --- Door and Security Routines ---

def doorOpenedHandler(evt) {
    if (!isSystemEnabled()) return
    boolean needAmMotion = (motionSensor != null)
    boolean needPmMotion = (motionSensor != null && !ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmDoorTriggered) {
        state.pmDoorTriggered = true
        if (needPmMotion && !state.pmMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Afternoon)...")
     
        }
        checkArrivalDepartureConditions()
    }
    
    if (state.waitingForDeparture && !state.hasDeparted && !state.amDoorTriggered) {
        state.amDoorTriggered = true
        if (needAmMotion && !state.amMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Morning)...")
        }
        checkArrivalDepartureConditions()
    }
}

def motionActiveHandler(evt) {
    if (!isSystemEnabled()) return
  
    boolean needPmMotion = (motionSensor != null && !ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmMotionTriggered) {
        state.pmMotionTriggered = true
        if (needPmMotion && !state.pmDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Afternoon)...")
        }
        checkArrivalDepartureConditions()
    }

    if (state.waitingForDeparture && !state.hasDeparted && !state.amMotionTriggered) {
        state.amMotionTriggered = true
        if (!state.amDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Morning)...")
        }
        checkArrivalDepartureConditions()
    }
}

def checkArrivalDepartureConditions() {
    boolean needAmMotion = (motionSensor != null)
    boolean needPmMotion = (motionSensor != null && !ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        boolean pmCondition = needPmMotion ? (state.pmDoorTriggered && state.pmMotionTriggered) : state.pmDoorTriggered
        if (pmCondition) {
            logAction("Arrival conditions met! Safe drop-off recorded.")
            state.waitingForArrival = false
            state.hasArrived = true
     
            state.isBlinking = false 
            
            if (state.pmTrackTime && !state.isTesting) {
                long diff = (new Date().time - state.pmTrackTime) / 1000
                state.pmTotalDoorTime = (state.pmTotalDoorTime ?: 0) + diff
                
                state.pmDoorCount = (state.pmDoorCount ?: 0) + 1
                state.pmTrackTime = null
            }

            if (isAllowedMode() || state.isTesting) {
                if (busLight) busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
        
            runIn(10, "turnLightOff")
            unschedule("checkSafeArrival")
        }
    }

    if (state.waitingForDeparture && !state.hasDeparted) {
        boolean amCondition = needAmMotion ? (state.amDoorTriggered && state.amMotionTriggered) : state.amDoorTriggered
        if (amCondition) {
            logAction("Departure conditions met! Safe pickup recorded.")
            state.waitingForDeparture = false
            state.hasDeparted = true
            state.isBlinking = false 
            
           
            if (state.amTrackTime && !state.isTesting) {
                long diff = (new Date().time - state.amTrackTime) / 1000
                state.amTotalDoorTime = (state.amTotalDoorTime ?: 0) + diff
                state.amDoorCount = (state.amDoorCount ?: 0) + 1
                state.amTrackTime = null
         
            }

            if (isAllowedMode() || state.isTesting) {
                if (busLight) busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
            runIn(5, "turnLightOff")
            unschedule("checkMissedBus")
        }
    }
}

def checkMissedBus() {
    if (!isSystemEnabled()) return
    boolean needAmMotion = (motionSensor != null)

    if (state.waitingForDeparture && !state.hasDeparted) {
        logAction("WARNING: Missed bus alert triggered!")
        String alertMsg = needAmMotion ? "WARNING: The bus arrived, but the door/motion conditions were not met!" : "WARNING: The bus arrived, but the front door never opened!"
        if (notifyPhones) notifyPhones.deviceNotification(alertMsg)
        state.waitingForDeparture = false
        state.amTrackTime = null
    }
}

def checkSafeArrival() {
    if (!isSystemEnabled()) return
    boolean needPmMotion = (motionSensor != null && !ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        logAction("CRITICAL: Missed drop-off alert triggered!")
        String alertMsg = needPmMotion ? "CRITICAL: The bus dropped off, but the door/motion conditions were not met!" : "CRITICAL: The school bus dropped off, but the front door hasn't opened!"
        if (notifyPhones) notifyPhones.deviceNotification(alertMsg)
        state.waitingForArrival = false
        state.pmTrackTime = null
    }
}

def turnLightOff() {
    if (!state.lightActiveByApp && !state.isTesting) {
        return 
    }

    state.lightActiveByApp = false 
    state.isBlinking = false
    state.isTesting = false
    state.waitingForArrival = false
    state.waitingForDeparture = false
    
    // RESTORE STATE & UNFREEZE Motion App
    if (busLight) restoreLightState(busLight)
    if (overrideSwitch) overrideSwitch.off()
    
    unschedule("testAmStage2")
    unschedule("testAmStage3")
    unschedule("testPmStage2")
    unschedule("testPmStage3")
}

// --- Smart Calendar Routines ---

def checkSchoolCalendar() {
    doCalendarSync(false)
}

def doCalendarSync(boolean isForced) {
    if (!isSystemEnabled()) {
        if (isForced) logAction("Sync Aborted: Master Switch is OFF")
        return
    }
    
    if (!enableCalendar && !isForced) return

    boolean onlineSuccess = false

    if (iCalUrl) {
  
        String fetchUrl = iCalUrl.replace("webcal://", "https://")
        def params = [
            uri: fetchUrl,
            headers: ["User-Agent": "Hubitat/2.0"],
            timeout: 15
        ]

        try {
            httpGet(params) { response ->
       
                if (response.status == 200) {
                    onlineSuccess = true
                    String icsData = ""
                    
                    if (response.data == null) {
                        log.warn "iCal fetch returned empty data payload."
                    } else if (response.data instanceof String) {
                        icsData = response.data
                    } else {
                        try {
                     
                            icsData = response.data.text
                        } catch (e) {
                            icsData = response.data.toString()
                        }
          
                    }
                    
                    if (!icsData || !icsData.contains("BEGIN:VCALENDAR")) {
                        state.calendarForecastHtml = "<div style='padding: 10px; font-size: 12px; color: #aa0000;'><b>Parse Error:</b> Downloaded data is not a valid calendar. It may be blocked by the server.</div>"
                        log.warn "iCal fetch succeeded, but payload missing calendar headers. Payload preview: ${icsData?.take(50)}"
                        applyDayResult(determineIfSchoolDayOffline(), "Offline Local Math", isForced)
                        return
            
                    }

                    boolean isSchoolDay = processCalendarData(icsData) 
                    applyDayResult(isSchoolDay, "Online iCal Feed", isForced)
                }
            }
        } catch (e) {
     
            log.warn "Failed to fetch iCal feed. Error: ${e.message}"
        }
    }

    if (!onlineSuccess) {
        state.calendarForecastHtml = "<div style='padding: 10px; font-size: 12px; color: #aa0000;'><b>Forecast Unavailable:</b> Feed offline. Using fallback.</div>"
        boolean isSchoolDay = determineIfSchoolDayOffline()
        applyDayResult(isSchoolDay, "Offline Local Math", isForced)
    }
}

def processCalendarData(String icsData) {
    Calendar localCalendar = Calendar.getInstance(location.timeZone)
    def targetDates = []
    def forecastMap = [:] 
    
    for (int i = 0; i < 3; i++) {
        String dateKey = new Date(localCalendar.timeInMillis).format("yyyyMMdd", location.timeZone)
        String dateLabel = new Date(localCalendar.timeInMillis).format("EEE, MMM d", location.timeZone)
        
        int dayOfWeek = localCalendar.get(Calendar.DAY_OF_WEEK)
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
        
        targetDates << dateKey
        forecastMap[dateKey] = [
            label: dateLabel, 
            events: [], 
   
            status: isWeekend ? "Weekend" : "School Day", 
            color: isWeekend ? "#888" : "green",
            isSchoolDay: !isWeekend
        ]
        localCalendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    String rawKeywords = holidayKeywords ?: "spring break, teacher workday, no school"
    List<String> keywords = rawKeywords.split(',').collect { it.trim().toLowerCase() }.findAll { it.length() > 0 }

    
    def lines = icsData.split(/\r?\n/)
    boolean inEvent = false
    String eventStart = ""
    String eventEnd = ""
    String eventSummary = ""

    for (String line : lines) {
        String upperLine = line.trim().toUpperCase()
        
        if (upperLine.startsWith("BEGIN:VEVENT")) {
            inEvent = true
            eventStart = "";
            eventEnd = ""; eventSummary = ""
        } else if (upperLine.startsWith("END:VEVENT")) {
            if (inEvent && eventStart && eventSummary) {
                if (!eventEnd) eventEnd = eventStart 
                for (String tDate : targetDates) {
                    if (tDate >= eventStart && tDate <= eventEnd) {
                        if (!forecastMap[tDate].events.contains(eventSummary)) {
                            forecastMap[tDate].events.add(eventSummary)
                        }
                 
                        String lowerSummary = eventSummary.toLowerCase()
                        for (String keyword : keywords) {
                            if (lowerSummary.contains(keyword)) {
                               
                                forecastMap[tDate].status = "No School (${keyword})"
                                forecastMap[tDate].color = "#aa0000"
                                forecastMap[tDate].isSchoolDay = false
                           
                                break
                            }
                        }
                    }
                }
       
            }
            inEvent = false
        } else if (inEvent) {
            if (upperLine.startsWith("DTSTART")) {
                int idx = line.indexOf(':')
                if (idx > -1) {
                
                    String val = line.substring(idx+1).replaceAll("[^0-9]", "")
                    if (val.length() >= 8) eventStart = val.substring(0, 8)
                }
            } else if (upperLine.startsWith("DTEND")) {
                int idx = line.indexOf(':')
              
                if (idx > -1) {
                    String val = line.substring(idx+1).replaceAll("[^0-9]", "")
                    if (val.length() >= 8) eventEnd = val.substring(0, 8)
                }
            } else if (upperLine.startsWith("SUMMARY")) {
           
                int idx = line.indexOf(':')
                if (idx > -1) {
                    eventSummary = line.substring(idx+1).trim()
                }
            }
        }
    }

    String html = "<div style='margin-top: 10px; padding: 10px; background: #f9f9f9; border-radius: 4px; font-size: 12px; border: 1px solid #ccc;'>"
    html += "<h4 style='margin-top: 0px; margin-bottom: 5px;'>3-Day Calendar Forecast</h4><table style='width:100%; border-collapse: collapse;'>"
    forecastMap.each { k, v ->
        String evs = v.events.size() > 0 ? v.events.join(", ") : "<i>No events</i>"
        html += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 6px 0; width: 25%;'><b>${v.label}</b></td>"
        html += "<td style='padding: 6px 0; width: 45%; color: #555;'>${evs}</td>"
        html += "<td style='padding: 6px 0; width: 30%; color: ${v.color}; text-align: right;'><b>${v.status}</b></td></tr>"
    }
    html += "</table></div>"
    state.calendarForecastHtml = html
    return forecastMap[targetDates[0]].isSchoolDay
}

def applyDayResult(boolean isSchoolDay, String source, boolean isForced) {
    boolean currentSwitch = schoolDaySwitch?.currentValue("switch") == "on"
    if (isSchoolDay) {
        schoolDaySwitch?.on()
        if (!currentSwitch || isForced) logAction("Calendar Sync: Marked as School Day [${source}]")
    } else {
        schoolDaySwitch?.off()
        if (currentSwitch || isForced) logAction("Calendar Sync: Marked as Off-Day [${source}]")
    }
}

def determineIfSchoolDayOffline() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false 
    return true
}
