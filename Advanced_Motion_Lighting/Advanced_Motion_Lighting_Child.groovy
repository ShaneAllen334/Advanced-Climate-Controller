/**
 * Advanced Motion Lighting Child
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Motion Lighting Child",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Lighting Engine: Multiple Disable Switches, Triple-Check Offs, Boot Recovery, and ROI Telemetry.",
    category: "Convenience",
    parent: "ShaneAllen:Advanced Motion Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Motion Lighting Rule</b>", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            if (motionSensors || triggerSwitches) {
                def mState = isPrimaryActive() ? "<span style='color: blue; font-weight: bold;'>ACTIVE</span>" : "<span style='color: grey;'>INACTIVE</span>"
                def lState = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" }) ? "<span style='color: green; font-weight: bold;'>ON</span>" : "OFF"
                
                def wState = state.warningPhase ? "<span style='color: orange; font-weight: bold;'> (WARNING PHASE)</span>" : ""
                def manualLock = state.manuallyTurnedOn ? "<span style='color: #0055aa; font-weight: bold;'> (MANUAL TIMEOUT)</span>" : ""
                def arrivalLock = state.arrivalActive ? "<span style='color: #800080; font-weight: bold;'> (ARRIVAL OVERRIDE)</span>" : ""
                def modeInfo = location.mode
                
                def overrides = []
                if (state.partyLock) overrides << "<span style='color: magenta; font-weight: bold;'>PARTY LOCK ON</span>"
                if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: red;'>ON Disabled</span>"
                if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: darkred;'>OFF Disabled</span>"
                if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") overrides << "<span style='color: darkblue; font-weight: bold;'>Nap Lock Active</span>"
                
                if (isKeepAliveActive()) {
                    overrides << "<span style='color: #00aadd; font-weight: bold;'>Keep-Alive Active</span>"
                }
                
                if (contactSensors?.any { it.currentValue("contact") == "open" }) {
                    if (overcastSwitch && overcastSwitch.currentValue("switch") == "on") {
                        overrides << "<span style='color: purple;'>Overcast Override Active</span>"
                    } else {
                        overrides << "<span style='color: orange;'>Blocked by Open Window/Door</span>"
                    }
                }
                def oText = overrides ? overrides.join(" | ") : "<span style='color: green;'>Clear</span>"

                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Primary Trigger</th><th style='padding: 8px;'>Light State</th><th style='padding: 8px;'>Current Mode</th><th style='padding: 8px;'>Active Blocks/Overrides</th></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${mState}</td><td style='padding: 8px;'>${lState}${wState}${manualLock}${arrivalLock}</td><td style='padding: 8px;'>${modeInfo}</td><td style='padding: 8px;'>${oText}</td></tr>"
                statusText += "</table>"
                
                // --- TIMER DASHBOARD ---
                def timerText = ""
                def tz = location.timeZone ?: TimeZone.getDefault()
                def currentTime = now()
                if (state.stdTaskTime && state.stdTaskTime > currentTime) {
                    def diff = state.stdTaskTime - currentTime
                    timerText += "<b>Standard Timeout:</b> ${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
                }
                if (timerText) {
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #fff3e0; border: 1px solid #ffcc80; font-size: 12px;'><b>Active Timers:</b> ${timerText}</div>"
                }

                if (luxSensor) {
                    def curLux = luxSensor.currentValue("illuminance") ?: 0
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #fdf5e6; border: 1px solid #ffebcd; font-size: 12px;'><b>Current Light Level:</b> ${curLux} lx</div>"
                }
                
                if (enableTelemetry) {
                    def liveCalc = calculateLiveSavings()
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #eef9ee; border: 1px solid #aaddaa; font-size: 12px;'><b>Predictive ROI:</b> Today: &#36;${liveCalc.today} | Total: &#36;${liveCalc.lifetime}</div>"
                }
                
                paragraph statusText
            } else {
                paragraph "<i>Configure sensors below to see the live dashboard.</i>"
            }
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>1. Core Configuration</div>", hideable: true, hidden: false) {
            label title: "Rule Name", required: true
            input "activeModes", "mode", title: "Active Modes (Allowed to turn ON)", multiple: true, required: false
            input "adjustOnModeChange", "bool", title: "Adjust lights dynamically on Mode change?", defaultValue: true
            
            input "lightType", "enum", title: "What type of lights are we controlling?", options: ["Simple On/Off", "Adjustable Bulb / Dimmer", "Color / CT Bulb"], required: true, submitOnChange: true
            
            if (lightType == "Simple On/Off") {
                input "switches", "capability.switch", title: "Switches to control", multiple: true, required: true
            } else if (lightType == "Adjustable Bulb / Dimmer") {
                input "dimmers", "capability.switchLevel", title: "Dimmers to control", multiple: true, required: true
            } else if (lightType == "Color / CT Bulb") {
                input "colorBulbs", "capability.colorTemperature", title: "Color/CT Bulbs to control", multiple: true, required: true
            }
            
            if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                input "isSmartBulbOnRelay", "bool", title: "Are these smart bulbs on a power-cutting Smart Switch?", defaultValue: false, submitOnChange: true
                if (isSmartBulbOnRelay) {
                    input "relaySwitch", "capability.switch", title: "The Relay providing power", required: true
                    input "relayDelay", "number", title: "Boot Delay (ms)", defaultValue: 1500
                }
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>2. Triggers & Debounce</div>", hideable: true, hidden: false) {
            input "motionSensors", "capability.motionSensor", title: "Primary Motion Sensors", multiple: true, required: false
            input "triggerSwitches", "capability.switch", title: "Primary Trigger Switches (Virtual Occupancy)", multiple: true, required: false, description: "<b>WARNING:</b> DO NOT select the lights you are controlling here!"
            input "motionDebounceSeconds", "number", title: "Debounce (Seconds of continuous motion required)", defaultValue: 0
            input "luxSensor", "capability.illuminanceMeasurement", title: "Illuminance (Lux) Sensor", required: false
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>3. Turn ON Settings</div>", hideable: true, hidden: true) {
            input "useModeSettings", "bool", title: "Use specific settings per mode?", defaultValue: false, submitOnChange: true
            if (useModeSettings) {
                location.modes.each { m ->
                    paragraph "<b>${m.name} Settings</b>"
                    
                    if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                        input "useLevelDimmer_${m.id}", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                        if (settings["useLevelDimmer_${m.id}"]) {
                            input "levelDimmer_${m.id}", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                        } else {
                            input "level_${m.id}", "number", title: "Dim Level (%)", range: "1..100"
                        }
                    }
                    
                    if (lightType == "Color / CT Bulb") {
                        input "useCTVar_${m.id}", "bool", title: "Use a Hub Variable for Color Temp?", defaultValue: false, submitOnChange: true
                        if (settings["useCTVar_${m.id}"]) {
                            input "ctVarName_${m.id}", "string", title: "Hub Variable Name (Exact text)"
                        } else {
                            input "ct_${m.id}", "number", title: "Color Temp (K)", range: "2000..7000"
                        }
                    }
                    input "delay_${m.id}", "number", title: "Turn Off Delay (Minutes)"
                    input "lux_${m.id}", "number", title: "Lux Threshold (Only ON if below this)"
                }
            } else {
                if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                    input "useLevelDimmer", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                    if (useLevelDimmer) {
                        input "levelDimmer", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                    } else {
                        input "defaultLevel", "number", title: "Default Dim Level (%)", defaultValue: 100
                    }
                }
                
                if (lightType == "Color / CT Bulb") {
                    input "useCTVar", "bool", title: "Use a Hub Variable for Color Temp?", defaultValue: false, submitOnChange: true
                    if (useCTVar) {
                        input "ctVarName", "string", title: "Hub Variable Name (Exact text)"
                    } else {
                        input "defaultCT", "number", title: "Default Color Temp (K)", defaultValue: 2700
                    }
                }
                input "defaultDelay", "number", title: "Standard Turn Off Delay (Minutes)", defaultValue: 5, required: true
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>4. Turn OFF Behaviors</div>", hideable: true, hidden: true) {
            input "gracePeriod", "number", title: "Grace Period after Manual Off (Seconds)", defaultValue: 15
            input "manualTimeoutMinutes", "number", title: "Manual Override Timeout (Minutes)", defaultValue: 30
            input "turnOffOnModes", "mode", title: "Force OFF when Hub enters these Modes", multiple: true
            
            if (isSmartBulbOnRelay) {
                input "turnOffRelay", "bool", title: "Turn off Relay Power when lights turn off?", defaultValue: true
            }
            
            paragraph "<b>Absolute Force-Off (Sweep)</b>"
            input "enableForceOff", "bool", title: "Enable Absolute Force-Off?", defaultValue: false, submitOnChange: true
            if (enableForceOff) input "forceOffMinutes", "number", title: "Sweep Delay (Minutes)", defaultValue: 60
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>5. Advanced Restrictions</div>", hideable: true, hidden: true) {
            input "disableOnSwitches", "capability.switch", title: "Switches to Disable Turning ON", multiple: true, required: false, description: "If ANY of these are ON, the light will not turn on automatically."
            input "disableOffSwitches", "capability.switch", title: "Switches to Disable Turning OFF", multiple: true, required: false, description: "If ANY of these are ON, the automation will not turn the light off."
            
            input "goodNightSwitch", "capability.switch", title: "Nap Time / Hard-Lock Switch", required: false
            input "startTime", "time", title: "Only active AFTER", required: false
            input "endTime", "time", title: "Only active BEFORE", required: false
            
            paragraph "<b>Custom Contact Logic</b>"
            input "contactSensors", "capability.contactSensor", title: "Do NOT turn on if these contacts are OPEN", multiple: true, required: false
            input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Ignores open contacts)", required: false
            
            paragraph "<b>Keep-Alive / Overrides (Keeps lights ON, won't trigger ON)</b>"
            input "keepAliveMotionSensors", "capability.motionSensor", title: "Secondary Motion Sensors", multiple: true, required: false
            input "keepAliveSwitches", "capability.switch", title: "Keep-Alive Switches (e.g., TV is ON)", multiple: true, required: false
            input "keepAlivePower", "capability.powerMeter", title: "Keep-Alive Power Meters", multiple: true, required: false
            input "keepAlivePowerThreshold", "number", title: "Power Threshold for Keep-Alive (Watts)", defaultValue: 10
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>6. Telemetry & ROI</div>", hideable: true, hidden: true) {
            input "enableTelemetry", "bool", title: "Enable Energy Tracking?", defaultValue: false, submitOnChange: true
            if (enableTelemetry) {
                input "totalWattage", "decimal", title: 'Total Wattage (e.g. 60.5)', required: true
                input "kwhRate", "decimal", title: 'Electricity Rate ($ per kWh)', defaultValue: 0.14, required: true
                input "baselineHours", "decimal", title: "Baseline ON Hours (Assumed daily without automation)", defaultValue: 8.0
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>7. Arrival Lighting Strategy</div>", hideable: true, hidden: false) {
            input "enableArrivalLighting", "bool", title: "Enable Full Arrival Lighting?", defaultValue: false, submitOnChange: true, description: "If enabled, the parent app will force these lights ON when the Arrival scenario is triggered."
            
            if (enableArrivalLighting && lightType == "Color / CT Bulb") {
                input "arrivalColorOverride", "bool", title: "Override color to 6500K during Arrival?", defaultValue: true, submitOnChange: true, description: "Lights will revert to their assigned color temp when the arrival timer ends."
                
                if (arrivalColorOverride) {
                    input "arrivalTransitionTime", "number", title: "Revert Transition Time (Seconds)", defaultValue: 3, description: "How gradually the lights shift back from 6500K to standard color."
                }
            }
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.appTurnedOn = state.appTurnedOn ?: false
    state.manuallyTurnedOn = state.manuallyTurnedOn ?: false
    state.arrivalActive = state.arrivalActive ?: false
    state.offRetryCount = 0
    
    // Primary Triggers
    subscribe(motionSensors, "motion", triggerHandler)
    subscribe(triggerSwitches, "switch", triggerHandler)
    
    // Keep-Alives
    subscribe(keepAliveMotionSensors, "motion", keepAliveHandler)
    subscribe(keepAliveSwitches, "switch", keepAliveHandler)
    subscribe(keepAlivePower, "power", keepAliveHandler)
    
    // Restrictions & System
    subscribe(disableOnSwitches, "switch", restrictionHandler)
    subscribe(disableOffSwitches, "switch", restrictionHandler)
    subscribe(contactSensors, "contact", restrictionHandler)
    subscribe(overcastSwitch, "switch", restrictionHandler)
    subscribe(goodNightSwitch, "switch", restrictionHandler)
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", bootHandler)
    
    if (switches) { subscribe(switches, "switch.off", physicalOffHandler); subscribe(switches, "switch.on", physicalOnHandler) }
    if (dimmers) { subscribe(dimmers, "switch.off", physicalOffHandler); subscribe(dimmers, "switch.on", physicalOnHandler) }
    if (colorBulbs) { subscribe(colorBulbs, "switch.off", physicalOffHandler); subscribe(colorBulbs, "switch.on", physicalOnHandler) }
    
    schedule("0 0 0 * * ?", "midnightReset")
    runIn(10, "bootSync")
}

// --- STATE HELPERS ---
def isPrimaryActive() {
    return (motionSensors?.any { it.currentValue("motion") == "active" } || triggerSwitches?.any { it.currentValue("switch") == "on" })
}

def isKeepAliveActive() {
    def mActive = keepAliveMotionSensors?.any { it.currentValue("motion") == "active" }
    def sActive = keepAliveSwitches?.any { it.currentValue("switch") == "on" }
    def pActive = keepAlivePower?.any { it.currentValue("power")?.toBigDecimal() >= (keepAlivePowerThreshold ?: 10) }
    return (mActive || sActive || pActive)
}

def bootHandler(evt) { runIn(30, "bootSync", [overwrite: true]) }

def bootSync() {
    def anyOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    if (anyOn && !isPrimaryActive() && !isKeepAliveActive() && !state.arrivalActive) {
        // Apply random stagger to boot sequences to prevent startup network storms
        def randomStagger = new Random().nextInt(5000) + 500
        runInMillis(randomStagger, "startTurnOffTimer")
    }
}

def recordTurnOnTime() { if (!state.onTimeStart) state.onTimeStart = now() }
def recordTurnOffTime() { if (state.onTimeStart) { state.todayOnMillis = (state.todayOnMillis ?: 0) + (now() - state.onTimeStart); state.onTimeStart = null } }

def calculateLiveSavings() {
    def currentMillis = (state.todayOnMillis ?: 0) + (state.onTimeStart ? (now() - state.onTimeStart) : 0)
    def hoursOn = currentMillis / 3600000.0
    def savedKwh = ((totalWattage ?: 0) * ((baselineHours ?: 8.0) - hoursOn)) / 1000.0
    def todaySaved = (savedKwh > 0 ? savedKwh : 0.0) * (kwhRate ?: 0.14)
    return [today: todaySaved.toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP), lifetime: (state.lifetimeSavings ?: 0.0).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)]
}

def midnightReset() {
    state.lifetimeSavings = (state.lifetimeSavings ?: 0.0) + calculateLiveSavings().today.toBigDecimal()
    state.todayOnMillis = 0
    
    // If the light is currently ON across midnight, restart the clock for the new day
    if (state.onTimeStart) {
        state.onTimeStart = now()
    }
}

def triggerHandler(evt) {
    if (evt.value == "active" || evt.value == "on") {
        if (state.gracePeriodEnd && now() < state.gracePeriodEnd) return
        cancelAllTurnOffTimers()
        if (motionDebounceSeconds && !state.appTurnedOn) runIn(motionDebounceSeconds, "evaluateTurnOn")
        else evaluateTurnOn()
    } else {
        if (!isPrimaryActive() && !isKeepAliveActive()) {
            startTurnOffTimer()
        }
    }
}

def keepAliveHandler(evt) {
    if (isKeepAliveActive()) {
        if (state.appTurnedOn) cancelAllTurnOffTimers()
    } else {
        if (state.appTurnedOn && !isPrimaryActive()) {
            startTurnOffTimer()
        }
    }
}

def restrictionHandler(evt) {
    if (isPrimaryActive()) evaluateTurnOn()
}

def startTurnOffTimer() {
    if (state.arrivalActive) return // Block auto-off if the parent app is managing the arrival timer
    def delay = state.manuallyTurnedOn ? (manualTimeoutMinutes ?: 30) : (getModeSetting("delay") ?: (defaultDelay ?: 5))
    state.stdTaskTime = now() + (delay * 60000)
    runIn(delay * 60, "processTurnOff")
}

def cancelAllTurnOffTimers() { unschedule("processTurnOff"); state.stdTaskTime = null }

def evaluateTurnOn() {
    state.offRetryCount = 0 // Reset retry logic to ensure a clean slate upon fresh motion
    
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && currentLux >= targetLux) return
    }
    
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") return
    if (startTime && endTime && !timeOfDayIsBetween(startTime, endTime, new Date(), location.timeZone)) return
    
    if (contactSensors?.any { it.currentValue("contact") == "open" } && overcastSwitch?.currentValue("switch") != "on") return

    state.appTurnedOn = true
    state.lastAutoCommand = now()
    recordTurnOnTime()
    applyLightingSettings()
}

// Helper to grab Dynamic Level vs Static Level
def getTargetLevel() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useDimmer = isMode ? settings["useLevelDimmer_${m?.id}"] : useLevelDimmer
    def dimmerDev = isMode ? settings["levelDimmer_${m?.id}"] : levelDimmer
    def staticLvl = isMode ? settings["level_${m?.id}"] : defaultLevel
    
    if (useDimmer && dimmerDev) {
        def curLvl = dimmerDev.currentValue("level")
        if (curLvl != null) return curLvl.toInteger()
    }
    return staticLvl != null ? staticLvl : 100
}

def getTargetColorTemp() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useVar = isMode ? settings["useCTVar_${m?.id}"] : useCTVar
    def varName = isMode ? settings["ctVarName_${m?.id}"] : ctVarName
    def staticCT = isMode ? settings["ct_${m?.id}"] : defaultCT
    
    if (useVar && varName) {
        def hubVar = getGlobalVar(varName)
        if (hubVar != null) return hubVar.value.toInteger()
    }
    return staticCT ?: 2700
}

// The transition parameter is optional. If passed, the color bulbs will fade over that duration.
def applyLightingSettings(transition = null) {
    state.lastAutoCommand = now() 
    def refreshNeeded = false

    if (lightType == "Simple On/Off") {
        switches?.each { 
            if (it.currentValue("switch") != "on") {
                it.on()
                refreshNeeded = true
            }
        }
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        if (isSmartBulbOnRelay && relaySwitch?.currentValue("switch") != "on") { 
            relaySwitch.on()
            pauseExecution(relayDelay ?: 1500) 
        }
        def lvl = getTargetLevel()
        dimmers?.each { 
            if (it.currentValue("switch") != "on" || it.currentValue("level") != lvl) {
                it.setLevel(lvl)
                refreshNeeded = true
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        if (isSmartBulbOnRelay && relaySwitch?.currentValue("switch") != "on") { 
            relaySwitch.on()
            pauseExecution(relayDelay ?: 1500) 
        }
        def lvl = getTargetLevel()
        def ct = getTargetColorTemp()
        
        colorBulbs?.each { 
            if (it.currentValue("switch") != "on" || it.currentValue("level") != lvl || it.currentValue("colorTemperature") != ct) {
                if (transition != null) {
                    it.setColorTemperature(ct, lvl, transition)
                } else {
                    it.setColorTemperature(ct, lvl)
                }
                refreshNeeded = true
            }
        }
    }

    if (refreshNeeded) {
        runIn(2, "executeRefresh")
    }
}

def processTurnOff() {
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    // Final safety check in case a timer survived somehow
    if (isPrimaryActive() || isKeepAliveActive()) return 
    
    state.appTurnedOn = false
    state.manuallyTurnedOn = false
    state.arrivalActive = false
    recordTurnOffTime()
    
    sendOffCommands()
    runIn(10, "verifyTurnOff")
}

def sendOffCommands() {
    state.lastAutoOffCommand = now() // ADDED: timestamp for OFF commands
    def refreshNeeded = false

    switches?.each { 
        if (it.currentValue("switch") != "off") { 
            it.off()
            refreshNeeded = true 
        } 
    }
    dimmers?.each { 
        if (it.currentValue("switch") != "off") { 
            it.setLevel(1)
            pauseExecution(400)
            it.off()
            refreshNeeded = true 
        } 
    }
    colorBulbs?.each { 
        if (it.currentValue("switch") != "off") { 
            it.setLevel(1)
            pauseExecution(400)
            it.off()
            refreshNeeded = true 
        } 
    }
    
    if (isSmartBulbOnRelay && turnOffRelay && relaySwitch?.currentValue("switch") != "off") {
        relaySwitch?.off()
    }

    if (refreshNeeded) {
        runIn(2, "executeRefresh")
    }
}

def executeRefresh() {
    state.lastAutoCommand = now() 
    state.lastAutoOffCommand = now() // ADDED: Reset the off debounce timer as well

    if (lightType == "Simple On/Off") {
        switches?.each { if (it.hasCommand("refresh")) it.refresh() }
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        dimmers?.each { if (it.hasCommand("refresh")) it.refresh() }
    } else if (lightType == "Color / CT Bulb") {
        colorBulbs?.each { if (it.hasCommand("refresh")) it.refresh() }
    }
}

def verifyTurnOff() {
    def anyOn = false
    
    // If we killed a physical relay that powers smart bulbs, the smart bulbs will drop offline 
    // and might freeze in the "ON" state in the hub. We should only verify the relay in this scenario.
    if (isSmartBulbOnRelay && turnOffRelay) {
        anyOn = (relaySwitch?.currentValue("switch") == "on")
    } else {
        anyOn = (switches?.any { it.currentValue("switch") == "on" } || 
                 dimmers?.any { it.currentValue("switch") == "on" } || 
                 colorBulbs?.any { it.currentValue("switch") == "on" })
    }
    
    // Abort verification completely if the room became active again during the 10-second wait
    if (isPrimaryActive() || isKeepAliveActive()) {
        state.offRetryCount = 0
        return 
    }

    if (anyOn && state.offRetryCount < 3) {
        state.offRetryCount++
        sendOffCommands()
        runIn(10, "verifyTurnOff")
    } else { 
        state.offRetryCount = 0 
    }
}

def physicalOffHandler(evt) {
    if (now() - (state.lastAutoOffCommand ?: 0) > 3000) { // ADDED: Debounce check for OFF events
        def gp = (gracePeriod ?: 15)
        state.gracePeriodEnd = now() + (gp * 1000)
        state.appTurnedOn = false
        state.manuallyTurnedOn = false
        state.arrivalActive = false // Break arrival lock on physical interaction
        cancelAllTurnOffTimers()
        recordTurnOffTime() 
    }
}

def physicalOnHandler(evt) {
    if (now() - (state.lastAutoCommand ?: 0) > 3000) {
        state.manuallyTurnedOn = true
        state.appTurnedOn = true
        cancelAllTurnOffTimers()
        recordTurnOnTime() 
    }
}

def modeChangeHandler(evt) {
    // Explicitly cast to a flattened list to ensure reliable matching
    def forceOffList = [turnOffOnModes].flatten().findAll { it }
    
    if (forceOffList.contains(evt.value)) {
        cancelAllTurnOffTimers() 
        
        // Generate a random stagger between 100ms and 4000ms to protect the mesh
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "processTurnOff")
        
    } else if (adjustOnModeChange && state.appTurnedOn) {
        // Apply the same mesh-protection stagger for lights adjusting to a new mode
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "applyLightingSettings")
    }
}

def getModeSetting(type) {
    if (!useModeSettings) return null
    def m = location.modes.find { it.name == location.mode }
    return settings["${type}_${m.id}"]
}

// --- PARENT CALLED METHODS ---

def isArrivalEnabled() {
    return enableArrivalLighting == true
}

def turnOnArrival() {
    state.arrivalActive = true
    state.appTurnedOn = true
    recordTurnOnTime()
    state.lastAutoCommand = now() 
    
    if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
        def lvl = getTargetLevel()
        // Force 6500k DayLight for arrival immediately (0 second transition)
        colorBulbs?.each { it.setColorTemperature(6500, lvl, 0) }
    } else {
        applyLightingSettings()
    }
}

def revertFromArrival() {
    state.arrivalActive = false
    if (isPrimaryActive() || isKeepAliveActive()) {
        // Room is currently occupied. Kick off standard timeout rather than sweeping it instantly.
        startTurnOffTimer()
        
        // Revert lights back to their respected Hub Variable/Static color using the Transition Time
        if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
            applyLightingSettings(arrivalTransitionTime != null ? arrivalTransitionTime : 3)
        }
    } else {
        // Room is empty. Execute off.
        processTurnOff()
    }
}

// Accepts the stagger delay from the parent, defaults to 0 if called locally
def executeParentSweep(delayMs = 0) {
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        if (delayMs > 0) {
            runInMillis(delayMs, "processTurnOff")
        } else {
            processTurnOff()
        }
    }
}

// ADDED: Clears manual override if it exists, and resumes normal auto-off countdown if room is empty
def clearManualOverride() {
    if (state.manuallyTurnedOn) {
        state.manuallyTurnedOn = false
        if (!isPrimaryActive() && !isKeepAliveActive()) {
            startTurnOffTimer()
        }
        return true
    }
    return false
}

// --- DASHBOARD EXPORT FOR PARENT ---

def getZoneStatus() {
    def isLightOn = (switches?.any { it.currentValue("switch") == "on" } || 
                     dimmers?.any { it.currentValue("switch") == "on" } || 
                     colorBulbs?.any { it.currentValue("switch") == "on" })
    
    def primaryActive = isPrimaryActive()
    def keepAliveActive = isKeepAliveActive()
    
    def statusText = "Standby"
    if (state.arrivalActive) statusText = "<span style='color: #800080;'>Arrival Override</span>"
    else if (state.manuallyTurnedOn) statusText = "<span style='color: #0055aa;'>Manual Override</span>"
    else if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: red;'>Disabled (ON Block)</span>"
    else if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: darkred;'>Disabled (OFF Block)</span>"
    else if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") statusText = "<span style='color: darkblue;'>Nap Lock Active</span>"
    else if (contactSensors?.any { it.currentValue("contact") == "open" } && overcastSwitch?.currentValue("switch") != "on") statusText = "<span style='color: orange;'>Blocked (Open Contact)</span>"
    else if (isLightOn && (primaryActive || keepAliveActive)) statusText = "Occupied"
    else if (isLightOn && !primaryActive && !keepAliveActive) statusText = "Counting Down"
    else if (!isLightOn && primaryActive) statusText = "Motion Ignored"
    
    def timerText = "--"
    if (isLightOn && !primaryActive && !keepAliveActive && state.stdTaskTime && state.stdTaskTime > now()) {
        def diff = state.stdTaskTime - now()
        timerText = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
    }
    
    // Add Brightness and Color Temperature details if applicable
    def lightDetails = isLightOn ? "ON" : "OFF"
    
    if (isLightOn) {
        if (lightType == "Adjustable Bulb / Dimmer") {
            def activeDev = dimmers?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                if (lvl != null) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(${lvl}%)</span>"
            }
        } else if (lightType == "Color / CT Bulb") {
            def activeDev = colorBulbs?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                def ct = activeDev.currentValue("colorTemperature")
                def extras = []
                if (lvl != null) extras << "${lvl}%"
                if (ct != null) extras << "${ct}K"
                if (extras) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(" + extras.join(" @ ") + ")</span>"
            }
        }
    }
    
    return [
        name: app.label ?: "Unnamed Zone",
        light: lightDetails,
        motion: primaryActive ? "ACTIVE" : (keepAliveActive ? "KEEP-ALIVE" : "INACTIVE"),
        status: statusText,
        timer: timerText
    ]
}
