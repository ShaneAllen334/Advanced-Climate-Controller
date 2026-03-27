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
            input "btnRefresh", "button", title: "Refresh Data"
            
            if (motionSensors || triggerSwitches) {
                def mState = isPrimaryActive() ? "<span style='color: blue; font-weight: bold;'>ACTIVE</span>" : "<span style='color: grey;'>INACTIVE</span>"
                def lState = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" }) ? "<span style='color: green; font-weight: bold;'>ON</span>" : "OFF"
                
                def wState = state.warningPhase ? "<span style='color: orange; font-weight: bold;'> (WARNING PHASE)</span>" : ""
                def manualLock = state.manuallyTurnedOn ? "<span style='color: #0055aa; font-weight: bold;'> (MANUAL OVERRIDE)</span>" : ""
                def arrivalLock = state.arrivalActive ? "<span style='color: #800080; font-weight: bold;'> (ARRIVAL OVERRIDE)</span>" : ""
                def modeInfo = location.mode
                
                def overrides = []
                if (state.partyLock) overrides << "<span style='color: magenta; font-weight: bold;'>PARTY LOCK ON</span>"
                if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: red;'>ON Disabled</span>"
                if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: darkred;'>OFF Disabled</span>"
                if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") overrides << "<span style='color: darkblue; font-weight: bold;'>Nap Lock Active</span>"
                if (enableOccupancyLock && occupancyLockContact?.currentValue("contact") == "closed") overrides << "<span style='color: #a52a2a; font-weight: bold;'>Occupancy Locked ON</span>"
                
                if (isKeepAliveActive()) {
                    overrides << "<span style='color: #00aadd; font-weight: bold;'>Keep-Alive Active</span>"
                }
                
                def timeBlocked = false
                if (restrictByTime && startTimeType && endTimeType) {
                    def sTime = resolveTime(startTimeType, startTime, startOffset)
                    def eTime = resolveTime(endTimeType, endTime, endOffset)
                    if (sTime && eTime) {
                        def isInside = isTimeInWindow(sTime, eTime)
                        timeBlocked = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
                    }
                }
                if (timeBlocked) overrides << "<span style='color: orange;'>Blocked by Time Window</span>"
                
                if (contactSensors?.any { it.currentValue("contact") == "open" }) {
                    def luxOverrideActive = false
                    if (useLuxContactOverride && luxSensor) {
                        def curLux = luxSensor.currentValue("illuminance") ?: 0
                        def targetLux = luxContactThreshold ?: 0
                        if (luxContactVar) {
                            def hVar = getGlobalVar(luxContactVar)
                            if (hVar != null) targetLux = hVar.value.toInteger()
                        }
                        if (curLux < targetLux) luxOverrideActive = true
                    }
                    
                    if ((overcastSwitch && overcastSwitch.currentValue("switch") == "on") || luxOverrideActive) {
                        overrides << "<span style='color: purple;'>Contact Override Active (Overcast/Lux)</span>"
                    } else {
                        overrides << "<span style='color: orange;'>Blocked by Open Window/Door</span>"
                    }
                }
                def oText = overrides ? overrides.join(" | ") : "<span style='color: green;'>Clear</span>"

                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Primary Trigger</th><th style='padding: 8px;'>Light State</th><th style='padding: 8px;'>Current Mode</th><th style='padding: 8px;'>Active Blocks/Overrides</th></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${mState}</td><td style='padding: 8px;'>${lState}${wState}${manualLock}${arrivalLock}</td><td style='padding: 8px;'>${modeInfo}</td><td style='padding: 8px;'>${oText}</td></tr>"
                statusText += "</table>"
                
                if (enableTriggerTracking && state.lastTriggerSource) {
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #e3f2fd; border: 1px solid #bbdefb; font-size: 12px;'><b>Last Activated By:</b> ${state.lastTriggerSource}</div>"
                }
                
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
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>2. Triggers & Intelligence</div>", hideable: true, hidden: false) {
            input "motionSensors", "capability.motionSensor", title: "Primary Motion Sensors", multiple: true, required: false
            input "triggerSwitches", "capability.switch", title: "Primary Trigger Switches (Virtual Occupancy)", multiple: true, required: false, description: "<b>WARNING:</b> DO NOT select the lights you are controlling here!"
            input "enableTriggerTracking", "bool", title: "Enable 'Last Triggered By' Intelligence?", defaultValue: false
            input "motionDebounceSeconds", "number", title: "Debounce (Seconds of continuous motion required)", defaultValue: 0
            input "luxSensor", "capability.illuminanceMeasurement", title: "Illuminance (Lux) Sensor", required: false
            input "enableActiveLuxPolling", "bool", title: "Enable Active Lux Polling (Sunset Fade-In)?", defaultValue: false, description: "If the room is occupied but lights are off due to Lux, this will continually check and turn them on when the sun goes down."
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>3. Turn ON & Transitions</div>", hideable: true, hidden: true) {
            input "useModeSettings", "bool", title: "Use specific settings per mode?", defaultValue: false, submitOnChange: true
            input "enableSoftStart", "bool", title: "Enable Soft-Start (Transition Ramping)?", defaultValue: false, submitOnChange: true
            if (enableSoftStart) {
                input "softStartTime", "number", title: "Ramp Duration (Seconds)", defaultValue: 3
            }
            
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
            input "manualTimeoutMinutes", "number", title: "Manual Override Motion Shutoff Timer (Minutes)", defaultValue: 30, description: "If a light is manually turned on, wait this long after motion stops before turning off."
            
            input "enableWarningDim", "bool", title: "Enable Warning Dim Phase?", defaultValue: false, submitOnChange: true
            if (enableWarningDim && lightType != "Simple On/Off") {
                input "warningDimLevel", "number", title: "Warning Dim Level (%)", defaultValue: 10
                input "warningDimSeconds", "number", title: "Warning Duration (Seconds)", defaultValue: 30
            }
            
            input "dimBeforeOff", "bool", title: "Dim to 1% before turning off?", defaultValue: true, description: "Disable this for bulbs that don't need a forced soft-fade."
            input "turnOffOnModes", "mode", title: "Force OFF when Hub enters these Modes", multiple: true
            
            if (isSmartBulbOnRelay) {
                input "turnOffRelay", "bool", title: "Turn off Relay Power when lights turn off?", defaultValue: true
            }
            
            paragraph "<b>Absolute Force-Off (Sweep)</b>"
            input "enableForceOff", "bool", title: "Enable Absolute Force-Off?", defaultValue: false, submitOnChange: true
            if (enableForceOff) input "forceOffMinutes", "number", title: "Sweep Delay (Minutes)", defaultValue: 60
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>5. Advanced Restrictions & True Occupancy</div>", hideable: true, hidden: true) {
            input "disableOnSwitches", "capability.switch", title: "Switches to Disable Turning ON", multiple: true, required: false
            input "disableOffSwitches", "capability.switch", title: "Switches to Disable Turning OFF", multiple: true, required: false
            input "goodNightSwitch", "capability.switch", title: "Nap Time / Hard-Lock Switch", required: false
            
            input "enableOccupancyLock", "bool", title: "Enable True Occupancy (Door Lock)?", defaultValue: false, submitOnChange: true
            if (enableOccupancyLock) {
                input "occupancyLockContact", "capability.contactSensor", title: "Lock Lights ON when this Contact is CLOSED"
            }
            
            paragraph "<b>Operational Time Window</b>"
            input "restrictByTime", "bool", title: "Enable Time-Based Gate?", defaultValue: false, submitOnChange: true
            if (restrictByTime) {
                input "timeLogic", "enum", title: "Window Logic", options: ["Only run DURING this window", "Block execution DURING this window"], defaultValue: "Only run DURING this window", submitOnChange: true
                
                input "startTimeType", "enum", title: "Start Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                if (startTimeType == "Specific Time") {
                    input "startTime", "time", title: "Select Start Time", required: true
                } else {
                    input "startOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                }
                
                input "endTimeType", "enum", title: "End Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                if (endTimeType == "Specific Time") {
                    input "endTime", "time", title: "Select End Time", required: true
                } else {
                    input "endOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                }
            }
            
            paragraph "<b>Custom Contact & Lux Logic</b>"
            input "contactSensors", "capability.contactSensor", title: "Do NOT turn on if these contacts are OPEN", multiple: true, required: false
            input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Ignores open contacts)", required: false
            
            input "useLuxContactOverride", "bool", title: "Enable Lux Override for Open Contacts?", defaultValue: false, submitOnChange: true
            if (useLuxContactOverride) {
                input "luxContactVar", "string", title: "Hub Variable for Lux Threshold (Optional)", required: false
                input "luxContactThreshold", "number", title: "Static Lux Threshold", required: false
            }
            
            paragraph "<b>Keep-Alive / Overrides (Keeps lights ON, won't trigger ON)</b>"
            input "keepAliveMotionSensors", "capability.motionSensor", title: "Secondary Motion Sensors", multiple: true, required: false
            input "keepAliveSwitches", "capability.switch", title: "Keep-Alive Switches (e.g., TV is ON)", multiple: true, required: false
            input "keepAlivePower", "capability.powerMeter", title: "Keep-Alive Power Meters", multiple: true, required: false
            input "keepAlivePowerThreshold", "number", title: "Power Threshold for Keep-Alive (Watts)", defaultValue: 10
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>6. Telemetry & Health</div>", hideable: true, hidden: true) {
            input "enableHealthWatchdog", "bool", title: "Report Battery & Health to Parent?", defaultValue: false
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
    state.warningPhase = false
    
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
    
    if (enableOccupancyLock && occupancyLockContact) {
        subscribe(occupancyLockContact, "contact", restrictionHandler)
    }
    
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
        def randomStagger = new Random().nextInt(5000) + 500
        runInMillis(randomStagger, "startTurnOffTimer")
    }
}

def recordTurnOnTime() { if (!state.onTimeStart) state.onTimeStart = now() }
def recordTurnOffTime() { if (state.onTimeStart) { state.todayOnMillis = (state.todayOnMillis ?: 0) + (now() - state.onTimeStart); state.onTimeStart = null } }

// RESTORED: Calculating ROI Savings
def calculateLiveSavings() {
    def currentMillis = (state.todayOnMillis ?: 0) + (state.onTimeStart ? (now() - state.onTimeStart) : 0)
    def hoursOn = currentMillis / 3600000.0
    def savedKwh = ((totalWattage ?: 0) * ((baselineHours ?: 8.0) - hoursOn)) / 1000.0
    def todaySaved = (savedKwh > 0 ? savedKwh : 0.0) * (kwhRate ?: 0.14)
    return [today: todaySaved.toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP), lifetime: (state.lifetimeSavings ?: 0.0).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)]
}

// RESTORED: Midnight Reset for Analytics
def midnightReset() {
    state.lifetimeSavings = (state.lifetimeSavings ?: 0.0) + calculateLiveSavings().today.toBigDecimal()
    state.todayOnMillis = 0
    if (state.onTimeStart) {
        state.onTimeStart = now()
    }
}

def triggerHandler(evt) {
    if (evt.value == "active" || evt.value == "on") {
        if (state.gracePeriodEnd && now() < state.gracePeriodEnd) return
        
        if (enableTriggerTracking) {
            state.lastTriggerSource = evt.displayName
        }
        
        // Interrupt the Warning Dim Phase if motion is detected
        if (state.warningPhase) {
            state.warningPhase = false
            cancelAllTurnOffTimers()
            applyLightingSettings() 
            startTurnOffTimer() 
            return
        }
        
        if (state.manuallyTurnedOn) {
            startTurnOffTimer() 
        } else {
            cancelAllTurnOffTimers()
            if (motionDebounceSeconds && !state.appTurnedOn) runIn(motionDebounceSeconds, "evaluateTurnOn")
            else evaluateTurnOn()
        }
    } else {
        runIn(1, "evaluatePrimaryOff")
    }
}

def evaluatePrimaryOff() {
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        startTurnOffTimer()
    }
}

def keepAliveHandler(evt) {
    def isActiveEvent = (evt.value == "active" || evt.value == "on" || (evt.name == "power" && evt.value.toFloat() >= (keepAlivePowerThreshold ?: 10)))
    
    if (isActiveEvent) {
        if (state.manuallyTurnedOn) {
            startTurnOffTimer()
        } else if (state.appTurnedOn) {
            cancelAllTurnOffTimers()
        }
    } else {
        runIn(1, "evaluateKeepAliveOff")
    }
}

def evaluateKeepAliveOff() {
    if (state.appTurnedOn && !isPrimaryActive() && !isKeepAliveActive()) {
        startTurnOffTimer()
    }
}

def restrictionHandler(evt) {
    if (isPrimaryActive()) evaluateTurnOn()
}

def startTurnOffTimer() {
    if (state.arrivalActive) return 
    def delay = state.manuallyTurnedOn ? (manualTimeoutMinutes ?: 30) : (getModeSetting("delay") ?: (defaultDelay ?: 5))
    state.stdTaskTime = now() + (delay * 60000)
    runIn(delay * 60, "processTurnOff")
}

def cancelAllTurnOffTimers() { unschedule("processTurnOff"); state.stdTaskTime = null }

// Background poller for when room is active but lights are off due to Lux
def pollLuxWhileActive() {
    if (!isPrimaryActive() && !isKeepAliveActive()) return 
    if (state.appTurnedOn) return 
    
    def shouldTurnOn = false
    if (luxSensor) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && curLux < targetLux) shouldTurnOn = true
        
        if (useLuxContactOverride && contactSensors?.any { it.currentValue("contact") == "open" }) {
            def overrideLux = luxContactVar ? getGlobalVar(luxContactVar)?.value?.toInteger() : (luxContactThreshold ?: 0)
            if (curLux < overrideLux) shouldTurnOn = true
        }
    }
    
    if (shouldTurnOn) evaluateTurnOn()
    else runIn(60, "pollLuxWhileActive")
}

// --- TIME RESOLUTION HELPER ---
def resolveTime(type, timeVal, offset) {
    if (type == "Specific Time" && timeVal) return toDateTime(timeVal)
    
    def astro = getSunriseAndSunset()
    if (type == "Sunrise") {
        def t = astro.sunrise
        if (offset) t = new Date(t.time + (offset.toInteger() * 60000))
        return t
    }
    if (type == "Sunset") {
        def t = astro.sunset
        if (offset) t = new Date(t.time + (offset.toInteger() * 60000))
        return t
    }
    return null
}

// --- MIDNIGHT WRAP HELPER ---
def isTimeInWindow(sTime, eTime) {
    def n = new Date()
    if (sTime < eTime) {
        return timeOfDayIsBetween(sTime, eTime, n, location.timeZone)
    } else {
        return (n.after(sTime) || n.before(eTime))
    }
}

def evaluateTurnOn() {
    state.offRetryCount = 0 
    
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && currentLux >= targetLux) {
            if (enableActiveLuxPolling) runIn(60, "pollLuxWhileActive")
            return
        }
    }
    
    // RESTORED: Disable ON Switches & Goodnight switch
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") return
    
    // RESTORED: Midnight Wrap Time Evaluation
    if (restrictByTime && startTimeType && endTimeType) {
        def sTime = resolveTime(startTimeType, startTime, startOffset)
        def eTime = resolveTime(endTimeType, endTime, endOffset)
        if (sTime && eTime) {
            def isInside = isTimeInWindow(sTime, eTime)
            def shouldBlock = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
            if (shouldBlock) return
        }
    }
    
    // RESTORED: True Overcast and Lux Override Contact Sensor Logic
    def luxOverrideActive = false
    if (useLuxContactOverride && luxSensor) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = luxContactThreshold ?: 0
        if (luxContactVar) {
            def hVar = getGlobalVar(luxContactVar)
            if (hVar != null) targetLux = hVar.value.toInteger()
        }
        if (curLux < targetLux) {
            luxOverrideActive = true
        }
    }
    
    if (contactSensors?.any { it.currentValue("contact") == "open" } && overcastSwitch?.currentValue("switch") != "on" && !luxOverrideActive) {
        if (enableActiveLuxPolling && luxSensor) runIn(60, "pollLuxWhileActive")
        return
    }

    state.appTurnedOn = true
    state.warningPhase = false
    state.lastAutoCommand = now()
    recordTurnOnTime()
    applyLightingSettings()
}

// RESTORED: Original Level Variable & Virtual Dimmer Grabber
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

// RESTORED: Original Color Temp Variable Grabber
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

// The transition parameter is optional. If passed, the bulbs will fade over that duration.
def applyLightingSettings(transition = null) {
    state.lastAutoCommand = now() 
    def refreshNeeded = false
    def t = (transition != null) ? transition : (enableSoftStart ? (softStartTime ?: 3) : null)

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
                if (t != null) {
                    it.setLevel(lvl, t)
                } else {
                    it.setLevel(lvl)
                }
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
                if (t != null) {
                    it.setColorTemperature(ct, lvl, t)
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
    // RESTORED: Disable OFF Switches
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    // OCCUPANCY LOCK logic
    if (enableOccupancyLock && occupancyLockContact?.currentValue("contact") == "closed") {
        startTurnOffTimer() 
        return
    }
    
    if (isPrimaryActive() || isKeepAliveActive()) {
        if (state.manuallyTurnedOn) startTurnOffTimer() 
        return 
    }
    
    // Process Warning Dim Phase
    if (enableWarningDim && !state.warningPhase && lightType != "Simple On/Off") {
        state.warningPhase = true
        def wLvl = warningDimLevel ?: 10
        dimmers?.each { it.setLevel(wLvl) }
        colorBulbs?.each { it.setLevel(wLvl) }
        runIn(warningDimSeconds ?: 30, "processTurnOff")
        return
    }
    
    state.appTurnedOn = false
    state.manuallyTurnedOn = false
    state.arrivalActive = false
    state.warningPhase = false
    recordTurnOffTime()
    
    sendOffCommands()
    runIn(10, "verifyTurnOff")
}

def sendOffCommands() {
    state.lastAutoOffCommand = now() 
    def refreshNeeded = false

    switches?.each { 
        if (it.currentValue("switch") != "off") { 
            it.off()
            refreshNeeded = true 
        } 
    }
    dimmers?.each { 
        if (it.currentValue("switch") != "off") { 
            if (dimBeforeOff != false && !state.warningPhase) {
                it.setLevel(1)
                pauseExecution(400)
            }
            it.off()
            refreshNeeded = true 
        } 
    }
    colorBulbs?.each { 
        if (it.currentValue("switch") != "off") { 
            if (dimBeforeOff != false && !state.warningPhase) {
                it.setLevel(1)
                pauseExecution(400)
            }
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

// RESTORED: Execute Refresh method
def executeRefresh() {
    state.lastAutoCommand = now() 
    state.lastAutoOffCommand = now() 

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
    if (isSmartBulbOnRelay && turnOffRelay) {
        anyOn = (relaySwitch?.currentValue("switch") == "on")
    } else {
        anyOn = (switches?.any { it.currentValue("switch") == "on" } || 
                 dimmers?.any { it.currentValue("switch") == "on" } || 
                 colorBulbs?.any { it.currentValue("switch") == "on" })
    }
    
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
    def isExplicitlyPhysical = (evt.type == "physical" || evt.isPhysical())
    def isOutsideAppDebounce = (now() - (state.lastAutoOffCommand ?: 0) > 5000) 
    
    if (isExplicitlyPhysical || isOutsideAppDebounce) { 
        def gp = (gracePeriod ?: 15)
        state.gracePeriodEnd = now() + (gp * 1000)
        state.appTurnedOn = false
        state.manuallyTurnedOn = false
        state.arrivalActive = false
        state.warningPhase = false
        cancelAllTurnOffTimers()
        recordTurnOffTime() 
    }
}

def physicalOnHandler(evt) {
    def isExplicitlyPhysical = (evt.type == "physical" || evt.isPhysical())
    def isOutsideAppDebounce = (now() - (state.lastAutoCommand ?: 0) > 5000) 
    
    if (isExplicitlyPhysical || isOutsideAppDebounce) {
        state.manuallyTurnedOn = true
        state.appTurnedOn = true
        state.warningPhase = false
        recordTurnOnTime() 
        startTurnOffTimer() 
    }
}

def modeChangeHandler(evt) {
    def forceOffList = [turnOffOnModes].flatten().findAll { it }
    
    if (forceOffList.contains(evt.value)) {
        cancelAllTurnOffTimers() 
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "processTurnOff")
        
    } else if (adjustOnModeChange && state.appTurnedOn) {
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "applyLightingSettings")
    }
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        log.debug "Live Dashboard Refreshed"
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
        colorBulbs?.each { it.setColorTemperature(6500, lvl, 0) }
    } else {
        applyLightingSettings()
    }
}

def revertFromArrival() {
    state.arrivalActive = false
    if (isPrimaryActive() || isKeepAliveActive()) {
        startTurnOffTimer()
        if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
            applyLightingSettings(arrivalTransitionTime != null ? arrivalTransitionTime : 3)
        }
    } else {
        processTurnOff()
    }
}

def executeParentSweep(delayMs = 0) {
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        if (delayMs > 0) {
            runInMillis(delayMs, "processTurnOff")
        } else {
            processTurnOff()
        }
    }
}

def clearManualOverride() {
    if (state.manuallyTurnedOn) {
        state.manuallyTurnedOn = false
        if (!isPrimaryActive() && !isKeepAliveActive()) {
            startTurnOffTimer()
        } else {
            cancelAllTurnOffTimers()
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
    
    def healthData = []
    if (enableHealthWatchdog) {
        def checkList = []
        if (motionSensors) checkList.addAll(motionSensors)
        if (keepAliveMotionSensors) checkList.addAll(keepAliveMotionSensors)
        if (contactSensors) checkList.addAll(contactSensors)
        if (occupancyLockContact) checkList.add(occupancyLockContact)
        
        checkList.unique().findAll { it }.each { d ->
            healthData << [name: d.displayName, battery: d.currentValue("battery"), lastActivity: d.getLastActivity()?.format("MM-dd HH:mm")]
        }
    }
    
    def timeBlocked = false
    if (restrictByTime && startTimeType && endTimeType) {
        def sTime = resolveTime(startTimeType, startTime, startOffset)
        def eTime = resolveTime(endTimeType, endTime, endOffset)
        if (sTime && eTime) {
            def isInside = isTimeInWindow(sTime, eTime)
            timeBlocked = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
        }
    }

    def statusText = "Standby"
    if (state.arrivalActive) statusText = "<span style='color: #800080;'>Arrival Override</span>"
    else if (enableOccupancyLock && occupancyLockContact?.currentValue("contact") == "closed") statusText = "<span style='color: #a52a2a;'>Locked (Occupied)</span>"
    else if (isLightOn && state.manuallyTurnedOn) statusText = "<span style='color: #0055aa;'>Manual Override</span>"
    else if (activeModes && !activeModes.contains(location.mode)) statusText = "<span style='color: orange;'>Blocked (Mode: ${location.mode})</span>"
    else if (timeBlocked) statusText = "<span style='color: orange;'>Blocked (Time Window)</span>"
    else if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: red;'>Disabled (ON Block)</span>"
    else if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: darkred;'>Disabled (OFF Block)</span>"
    else if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") statusText = "<span style='color: darkblue;'>Nap Lock Active</span>"
    else if (contactSensors?.any { it.currentValue("contact") == "open" }) {
        def overcastActive = (overcastSwitch?.currentValue("switch") == "on")
        def luxOverrideActive = false
        if (useLuxContactOverride && luxSensor) {
            def curLux = luxSensor.currentValue("illuminance") ?: 0
            def targetLux = luxContactThreshold ?: 0
            if (luxContactVar) {
                def hVar = getGlobalVar(luxContactVar)
                if (hVar != null) targetLux = hVar.value.toInteger()
            }
            if (curLux < targetLux) luxOverrideActive = true
        }
        
        if (overcastActive || luxOverrideActive) {
            statusText = "Occupied (Contact Bypass Active)"
        } else {
            statusText = "<span style='color: orange;'>Blocked (Open Contact)</span>"
        }
    }
    else if (state.warningPhase) statusText = "<span style='color: orange;'>Warning Dim Phase</span>"
    else if (isLightOn && (primaryActive || keepAliveActive)) statusText = "Occupied"
    else if (isLightOn && !primaryActive && !keepAliveActive) statusText = "Counting Down"
    else if (!isLightOn && primaryActive) statusText = "Motion Ignored"
    
    def timerText = "--"
    if (isLightOn && state.stdTaskTime && state.stdTaskTime > now()) {
        def diff = state.stdTaskTime - now()
        timerText = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
    }
    
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
        lastTrigger: enableTriggerTracking ? state.lastTriggerSource : null,
        timer: timerText,
        health: healthData
    ]
}

def dynamicCTUpdate(newCT) {
    if (lightType == "Color / CT Bulb") {
        def lvl = getTargetLevel()
        def refreshNeeded = false
        
        colorBulbs?.each { bulb ->
            if (bulb.currentValue("switch") == "on") {
                bulb.setColorTemperature(newCT, lvl)
                refreshNeeded = true
            }
        }
        
        if (refreshNeeded) {
            runIn(2, "executeRefresh")
        }
    }
}
