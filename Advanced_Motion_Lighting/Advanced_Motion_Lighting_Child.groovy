/**
 * Advanced Motion Lighting Child
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Motion Lighting Child",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Lighting Engine: Multiple Disable Switches, Triple-Check Offs, Boot Recovery, Window Shade (Contact) logic, ROI Telemetry, and Dynamic Variable Tracking.",
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
                
                def wState = atomicState.warningPhase ? "<span style='color: orange; font-weight: bold;'> (WARNING PHASE)</span>" : ""
                def manualLock = atomicState.manuallyTurnedOn ? "<span style='color: #0055aa; font-weight: bold;'> (MANUAL OVERRIDE)</span>" : ""
                def arrivalLock = atomicState.arrivalActive ? "<span style='color: #800080; font-weight: bold;'> (ARRIVAL OVERRIDE)</span>" : ""
                def modeInfo = location.mode
                
                def overrides = []
                if (atomicState.partyLock) overrides << "<span style='color: magenta; font-weight: bold;'>PARTY LOCK ON</span>"
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
                
                def isOpen = (contactSensors?.any { it.currentValue("contact") == "open" } || shadeSensors?.any { it.currentValue("contact") == "open" })
                if (isOpen) {
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
                        overrides << "<span style='color: purple;'>Contact/Shade Override Active (Overcast/Lux)</span>"
                    } else {
                        overrides << "<span style='color: orange;'>Blocked by Open Window/Door/Shades</span>"
                    }
                }
                def oText = overrides ? overrides.join(" | ") : "<span style='color: green;'>Clear</span>"

                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Primary Trigger</th><th style='padding: 8px;'>Light State</th><th style='padding: 8px;'>Current Mode</th><th style='padding: 8px;'>Active Blocks/Overrides</th></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${mState}</td><td style='padding: 8px;'>${lState}${wState}${manualLock}${arrivalLock}</td><td style='padding: 8px;'>${modeInfo}</td><td style='padding: 8px;'>${oText}</td></tr>"
                statusText += "</table>"
                
                if (enableTriggerTracking && atomicState.lastTriggerSource) {
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #e3f2fd; border: 1px solid #bbdefb; font-size: 12px;'><b>Last Activated By:</b> ${atomicState.lastTriggerSource}</div>"
                }
                
                // --- TIMER DASHBOARD ---
                def timerText = ""
                def tz = location.timeZone ?: TimeZone.getDefault()
                def currentTime = now()
                if (atomicState.stdTaskTime && atomicState.stdTaskTime > currentTime) {
                    def diff = atomicState.stdTaskTime - currentTime
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
            
            // FULLY RESTORED DYNAMIC SETTINGS
            input "dynamicAdjust", "bool", title: "Adjust lights dynamically if Hub Variable / Virtual Dimmer changes while ON?", defaultValue: true, submitOnChange: true
            
            input "lightType", "enum", title: "What type of lights are we controlling?", options: ["Simple On/Off", "Adjustable Bulb / Dimmer", "Color / CT Bulb"], required: true, submitOnChange: true
            
            if (dynamicAdjust && lightType == "Color / CT Bulb") {
                input "dynamicTransition", "number", title: "Dynamic Shift Fade Time (Seconds)", defaultValue: 5, description: "How slowly the bulbs will fade to the new setpoint."
            }
            
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
            
            paragraph "<hr>"
            paragraph "<b>Optional Button / Override Control</b>"
            input "optButton", "capability.pushableButton", title: "Select Button Device (Overrides Lights)", required: false, submitOnChange: true
            if (optButton) {
                input "optButtonNum", "number", title: "Button Number", required: true, defaultValue: 1
                input "optButtonAction", "enum", title: "Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                input "optButtonModes", "mode", title: "Allowed Modes for Button", multiple: true, required: false
                input "toggleSyncLights", "capability.switch", title: "Group Toggle Sync (Check these lights before toggling)", multiple: true, required: false, description: "Select the other lights controlled by this button to prevent flip-flopping."
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
                    
                    // FULLY RESTORED HUB VARIABLE DIMMER/LEVEL LOGIC
                    if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                        input "useLevelDimmer_${m.id}", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                        if (settings["useLevelDimmer_${m.id}"]) {
                            input "levelDimmer_${m.id}", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                        } else {
                            input "useLevelVar_${m.id}", "bool", title: "Use a Hub Variable for Level?", defaultValue: false, submitOnChange: true
                            if (settings["useLevelVar_${m.id}"]) {
                                input "levelVarName_${m.id}", "string", title: "Hub Variable Name (Exact text)"
                            } else {
                                input "level_${m.id}", "number", title: "Dim Level (%)", range: "1..100"
                            }
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
                        input "useLevelVar", "bool", title: "Use a Hub Variable for Level?", defaultValue: false, submitOnChange: true
                        if (useLevelVar) {
                            input "levelVarName", "string", title: "Hub Variable Name (Exact text)"
                        } else {
                            input "defaultLevel", "number", title: "Default Dim Level (%)", defaultValue: 100
                        }
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
            
            paragraph "<b>Custom Contact, Shade & Lux Logic</b>"
            input "contactSensors", "capability.contactSensor", title: "Do NOT turn on if these contacts are OPEN", multiple: true, required: false
            input "shadeSensors", "capability.contactSensor", title: "Do NOT turn on if these shades (Contacts) are OPEN", multiple: true, required: false
            input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Ignores open contacts/shades)", required: false
            
            input "useLuxContactOverride", "bool", title: "Enable Lux Override for Open Contacts/Shades?", defaultValue: false, submitOnChange: true
            if (useLuxContactOverride) {
                input "luxContactVar", "string", title: "Hub Variable for Lux Threshold (Optional)", required: false
                input "luxContactThreshold", "number", title: "Static Lux Threshold", required: false
            }
            
            paragraph "<b>Keep-Alive / Overrides (Keeps lights ON, won't trigger ON)</b>"
            input "keepAliveMotionSensors", "capability.motionSensor", title: "Secondary Motion Sensors", multiple: true, required: false
            input "keepAliveSwitches", "capability.switch", title: "Keep-Alive Switches (e.g., TV is ON)", multiple: true, required: false
            input "keepAlivePower", "capability.powerMeter", title: "Keep-Alive Power Meters", multiple: true, required: false
            input "keepAlivePowerThreshold", "number", title: "Power Threshold for Keep-Alive (Watts)", defaultValue: 10
            
            // THE BRIDGE FIX
            paragraph "<b>Override Exceptions</b>"
            input "ignoreOverrideSwitches", "capability.switch", title: "Ignore Manual Overrides when these are ON", multiple: true, required: false, description: "e.g., A virtual switch turned on by a Shower Monitor. Prevents manual holds."
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>6. Telemetry & Health</div>", hideable: true, hidden: true) {
            input "logEnable", "bool", title: "Enable Debug Logging (Auto-disables after 30 min)", defaultValue: false
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
def updated() { 
    unsubscribe()
    unschedule()
    if (logEnable) {
        log.debug "Debug logging enabled for 30 minutes."
        runIn(1800, "logsOff")
    }
    initialize() 
}

def initialize() {
    atomicState.historyLog = atomicState.historyLog ?: []
    atomicState.appTurnedOn = atomicState.appTurnedOn ?: false
    atomicState.manuallyTurnedOn = atomicState.manuallyTurnedOn ?: false
    atomicState.arrivalActive = atomicState.arrivalActive ?: false
    atomicState.offRetryCount = 0
    atomicState.warningPhase = false
    
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
    subscribe(shadeSensors, "contact", restrictionHandler)
    subscribe(overcastSwitch, "switch", restrictionHandler)
    subscribe(goodNightSwitch, "switch", restrictionHandler)
    
    // Optional Button Setup
    if (optButton && optButtonAction) {
        subscribe(optButton, optButtonAction, buttonActionHandler)
    }
    
    if (enableOccupancyLock && occupancyLockContact) {
        subscribe(occupancyLockContact, "contact", restrictionHandler)
    }
    
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", bootHandler)
    
    if (switches) { subscribe(switches, "switch.off", physicalOffHandler); subscribe(switches, "switch.on", physicalOnHandler) }
    if (dimmers) { subscribe(dimmers, "switch.off", physicalOffHandler); subscribe(dimmers, "switch.on", physicalOnHandler) }
    if (colorBulbs) { subscribe(colorBulbs, "switch.off", physicalOffHandler); subscribe(colorBulbs, "switch.on", physicalOnHandler) }
    
    // FULLY RESTORED: Subscribe to Dynamic Variables and Dimmers
    if (dynamicAdjust) {
        def dimmersToSub = []
        def varsToSub = []
        
        if (useModeSettings) {
            location.modes.each { m ->
                if (settings["useLevelDimmer_${m.id}"] && settings["levelDimmer_${m.id}"]) dimmersToSub << settings["levelDimmer_${m.id}"]
                if (settings["useLevelVar_${m.id}"] && settings["levelVarName_${m.id}"]) varsToSub << settings["levelVarName_${m.id}"]
                if (settings["useCTVar_${m.id}"] && settings["ctVarName_${m.id}"]) varsToSub << settings["ctVarName_${m.id}"]
            }
        } else {
            if (useLevelDimmer && levelDimmer) dimmersToSub << levelDimmer
            if (useLevelVar && levelVarName) varsToSub << levelVarName
            if (useCTVar && ctVarName) varsToSub << ctVarName
        }
        
        dimmersToSub.findAll { it }.unique { it.id }.each { dev ->
            subscribe(dev, "level", dynamicAdjustmentHandler)
        }
        varsToSub.findAll { it }.unique().each { vName ->
            subscribe(location, "variable:${vName}", dynamicAdjustmentHandler)
        }
    }
    
    schedule("0 0 0 * * ?", "midnightReset")
    runIn(10, "bootSync")
}

def debugLog(msg) {
    if (logEnable) log.debug msg
}

def logsOff() {
    log.warn "Debug logging automatically disabled."
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

// FULLY RESTORED: DYNAMIC ADJUSTMENT HANDLER
def dynamicAdjustmentHandler(evt) {
    if (atomicState.appTurnedOn) {
        def randomStagger = new Random().nextInt(2000) + 100
        runInMillis(randomStagger, "applyDynamicSettings")
    }
}

def applyDynamicSettings() {
    def trans = dynamicTransition != null ? dynamicTransition : (enableSoftStart ? (softStartTime ?: 3) : 5)
    applyLightingSettings(trans)
}

// --- BUTTON LOGIC ---
def buttonActionHandler(evt) {
    if (optButtonModes && !optButtonModes.contains(location.mode)) return
    if (evt.value == optButtonNum?.toString()) {
        def anyOn = false
        if (lightType == "Simple On/Off") anyOn = switches?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Adjustable Bulb / Dimmer") anyOn = dimmers?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Color / CT Bulb") anyOn = colorBulbs?.any { it.currentValue("switch") == "on" }

        // Check if the opposite side of the bed is ON before toggling
        if (toggleSyncLights?.any { it.currentValue("switch") == "on" }) {
            anyOn = true
        }

        if (anyOn) {
            // Force Lights OFF (Manual Override)
            def gp = (gracePeriod ?: 15)
            atomicState.gracePeriodEnd = now() + (gp * 1000)
            atomicState.appTurnedOn = false
            atomicState.manuallyTurnedOn = false
            atomicState.arrivalActive = false
            atomicState.warningPhase = false
            cancelAllTurnOffTimers()
            recordTurnOffTime()
            sendOffCommands()
        } else {
            // Force Lights ON (Manual Override)
            atomicState.manuallyTurnedOn = true
            atomicState.appTurnedOn = true
            atomicState.warningPhase = false
            recordTurnOnTime()
            startTurnOffTimer()
            
            if (lightType == "Color / CT Bulb" || lightType == "Adjustable Bulb / Dimmer") {
                applyLightingSettings() 
            } else if (lightType == "Simple On/Off") {
                switches?.each { it.on() }
            }
        }
    }
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

// INTELLIGENT BOOT RECOVERY
def bootSync() {
    def anyOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    
    def isOccupied = isPrimaryActive() || isKeepAliveActive()
    def isGoodNight = goodNightSwitch && goodNightSwitch.currentValue("switch") == "on"

    if (anyOn) {
        if (isGoodNight || !isOccupied) {
            // Room is unoccupied or Good Night is active, force lights back OFF
            def randomStagger = new Random().nextInt(5000) + 500
            runInMillis(randomStagger, "sendOffCommands")
        } else {
            // Room is occupied, ensure proper settings
            evaluateTurnOn()
        }
    } else {
        if (isOccupied && !isGoodNight) {
            // Lights are off but room is occupied, turn them back on
            evaluateTurnOn()
        }
    }
}

def recordTurnOnTime() { if (!atomicState.onTimeStart) atomicState.onTimeStart = now() }
def recordTurnOffTime() { if (atomicState.onTimeStart) { atomicState.todayOnMillis = (atomicState.todayOnMillis ?: 0) + (now() - atomicState.onTimeStart); atomicState.onTimeStart = null } }

def calculateLiveSavings() {
    def currentMillis = (atomicState.todayOnMillis ?: 0) + (atomicState.onTimeStart ? (now() - atomicState.onTimeStart) : 0)
    def hoursOn = currentMillis / 3600000.0
    def savedKwh = ((totalWattage ?: 0) * ((baselineHours ?: 8.0) - hoursOn)) / 1000.0
    def todaySaved = (savedKwh > 0 ? savedKwh : 0.0) * (kwhRate ?: 0.14)
    return [today: todaySaved.toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP), lifetime: (atomicState.lifetimeSavings ?: 0.0).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)]
}

def midnightReset() {
    atomicState.lifetimeSavings = (atomicState.lifetimeSavings ?: 0.0) + calculateLiveSavings().today.toBigDecimal()
    atomicState.todayOnMillis = 0
    if (atomicState.onTimeStart) {
        atomicState.onTimeStart = now()
    }
}

def triggerHandler(evt) {
    if (evt.value == "active" || evt.value == "on") {
        if (atomicState.gracePeriodEnd && now() < atomicState.gracePeriodEnd) return
        
        if (enableTriggerTracking) {
            atomicState.lastTriggerSource = evt.displayName
        }
        
        // Interrupt the Warning Dim Phase if motion is detected
        if (atomicState.warningPhase) {
            atomicState.warningPhase = false
            cancelAllTurnOffTimers()
            applyLightingSettings() 
            startTurnOffTimer() 
            return
        }
        
        if (atomicState.manuallyTurnedOn) {
            startTurnOffTimer() 
        } else {
            cancelAllTurnOffTimers()
            if (motionDebounceSeconds && !atomicState.appTurnedOn) runIn(motionDebounceSeconds, "evaluateTurnOn")
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
        if (atomicState.manuallyTurnedOn) {
            startTurnOffTimer()
        } else if (atomicState.appTurnedOn) {
            cancelAllTurnOffTimers()
        }
    } else {
        runIn(1, "evaluateKeepAliveOff")
    }
}

def evaluateKeepAliveOff() {
    if (atomicState.appTurnedOn && !isPrimaryActive() && !isKeepAliveActive()) {
        startTurnOffTimer()
    }
}

def restrictionHandler(evt) {
    // If a shade closes, overcast turns on, or a contact closes while motion is active, evaluate turning the lights on immediately
    if (isPrimaryActive()) evaluateTurnOn()
}

def startTurnOffTimer() {
    if (atomicState.arrivalActive) return 
    def delay = atomicState.manuallyTurnedOn ? (manualTimeoutMinutes ?: 30) : (getModeSetting("delay") ?: (defaultDelay ?: 5))
    atomicState.stdTaskTime = now() + (delay * 60000)
    runIn(delay * 60, "processTurnOff")
    debugLog("Turn off timer started for ${delay} minutes.")
}

def cancelAllTurnOffTimers() { unschedule("processTurnOff"); atomicState.stdTaskTime = null }

// Background poller for when room is active but lights are off due to Lux
def pollLuxWhileActive() {
    if (!isPrimaryActive() && !isKeepAliveActive()) return 
    if (atomicState.appTurnedOn) return 
    
    def shouldTurnOn = false
    if (luxSensor) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && curLux < targetLux) shouldTurnOn = true
        
        def isOpen = (contactSensors?.any { it.currentValue("contact") == "open" } || shadeSensors?.any { it.currentValue("contact") == "open" })
        if (useLuxContactOverride && isOpen) {
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
    atomicState.offRetryCount = 0 
    
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && currentLux >= targetLux) {
            if (enableActiveLuxPolling) runIn(60, "pollLuxWhileActive")
            return
        }
    }
    
    // Disable ON Switches & Goodnight switch
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") return
    
    // Midnight Wrap Time Evaluation
    if (restrictByTime && startTimeType && endTimeType) {
        def sTime = resolveTime(startTimeType, startTime, startOffset)
        def eTime = resolveTime(endTimeType, endTime, endOffset)
        if (sTime && eTime) {
            def isInside = isTimeInWindow(sTime, eTime)
            def shouldBlock = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
            if (shouldBlock) return
        }
    }
    
    // True Overcast, Window Shades, and Lux Override Logic
    def luxOverrideActive = false
    def isOpen = (contactSensors?.any { it.currentValue("contact") == "open" } || shadeSensors?.any { it.currentValue("contact") == "open" })
    
    if (useLuxContactOverride && luxSensor && isOpen) {
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
    
    if (isOpen && overcastSwitch?.currentValue("switch") != "on" && !luxOverrideActive) {
        if (enableActiveLuxPolling && luxSensor) runIn(60, "pollLuxWhileActive")
        return
    }

    atomicState.appTurnedOn = true
    atomicState.warningPhase = false
    atomicState.lastAutoCommand = now()
    recordTurnOnTime()
    applyLightingSettings()
}

// FULLY RESTORED: Level Variable & Virtual Dimmer Grabber with Hub Variable Tracking
def getTargetLevel() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useDimmer = isMode ? settings["useLevelDimmer_${m?.id}"] : useLevelDimmer
    def dimmerDev = isMode ? settings["levelDimmer_${m?.id}"] : levelDimmer
    
    def useVar = isMode ? settings["useLevelVar_${m?.id}"] : useLevelVar
    def varName = isMode ? settings["levelVarName_${m?.id}"] : levelVarName
    
    def staticLvl = isMode ? settings["level_${m?.id}"] : defaultLevel
    
    if (useDimmer && dimmerDev) {
        def curLvl = dimmerDev.currentValue("level")
        if (curLvl != null) return curLvl.toInteger()
    } else if (useVar && varName) {
        def hubVar = getGlobalVar(varName)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    }
    
    return staticLvl != null ? staticLvl : 100
}

// FULLY RESTORED: Color Temp Variable Grabber
def getTargetColorTemp() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useVar = isMode ? settings["useCTVar_${m?.id}"] : useCTVar
    def varName = isMode ? settings["ctVarName_${m?.id}"] : ctVarName
    def staticCT = isMode ? settings["ct_${m?.id}"] : defaultCT
    
    if (useVar && varName) {
        def hubVar = getGlobalVar(varName)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    }
    return staticCT ?: 2700
}

// The transition parameter is optional. If passed, the bulbs will fade over that duration.
def applyLightingSettings(transition = null) {
    atomicState.lastAutoCommand = now() 
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
            atomicState.lastAutoCommand = now() // Reset debounce timer post-boot wait
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
            atomicState.lastAutoCommand = now() // Reset debounce timer post-boot wait
        }
        def lvl = getTargetLevel()
        def ct = getTargetColorTemp()
        
        colorBulbs?.each { 
            if (it.currentValue("switch") != "on" || it.currentValue("level") != lvl || it.currentValue("colorTemperature") != ct) {
                if (t != null) {
                    it.setLevel(lvl, t)
                    pauseExecution(100) // Staggered to prevent popcorn effect
                    it.setColorTemperature(ct, lvl, t)
                } else {
                    it.setLevel(lvl)
                    pauseExecution(100) // Staggered to prevent popcorn effect
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
    // Disable OFF Switches
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    // OCCUPANCY LOCK logic
    if (enableOccupancyLock && occupancyLockContact?.currentValue("contact") == "closed") {
        startTurnOffTimer() 
        return
    }
    
    if (isPrimaryActive() || isKeepAliveActive()) {
        if (atomicState.manuallyTurnedOn) startTurnOffTimer() 
        return 
    }
    
    // Process Warning Dim Phase
    if (enableWarningDim && !atomicState.warningPhase && lightType != "Simple On/Off") {
        atomicState.warningPhase = true
        def wLvl = warningDimLevel ?: 10
        dimmers?.each { it.setLevel(wLvl) }
        colorBulbs?.each { it.setLevel(wLvl) }
        runIn(warningDimSeconds ?: 30, "processTurnOff")
        return
    }
    
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.arrivalActive = false
    atomicState.warningPhase = false
    recordTurnOffTime()
    
    sendOffCommands()
    runIn(10, "verifyTurnOff")
}

def sendOffCommands() {
    atomicState.lastAutoOffCommand = now() 
    def refreshNeeded = false

    switches?.each { 
        if (it.currentValue("switch") != "off") { 
            it.off()
            refreshNeeded = true 
        } 
    }
    dimmers?.each { 
        if (it.currentValue("switch") != "off") { 
            if (dimBeforeOff != false && !atomicState.warningPhase) {
                it.setLevel(1)
                pauseExecution(1000) 
            }
            it.off()
            refreshNeeded = true 
        } 
    }
    colorBulbs?.each { 
        if (it.currentValue("switch") != "off") { 
            if (dimBeforeOff != false && !atomicState.warningPhase) {
                it.setLevel(1)
                pauseExecution(1000) 
            }
            it.off()
            refreshNeeded = true 
        } 
    }
    
    if (isSmartBulbOnRelay && turnOffRelay && relaySwitch?.currentValue("switch") != "off") {
        relaySwitch?.off()
        atomicState.lastAutoOffCommand = now() // Update debounce after relay off command
    }

    if (refreshNeeded) {
        runIn(2, "executeRefresh")
    }
}

// Execute Refresh method
def executeRefresh() {
    atomicState.lastAutoCommand = now() 
    atomicState.lastAutoOffCommand = now() 

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
        anyOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    }
    
    if (isPrimaryActive() || isKeepAliveActive()) {
        atomicState.offRetryCount = 0
        return 
    }

    if (anyOn && atomicState.offRetryCount < 3) {
        atomicState.offRetryCount++
        sendOffCommands()
        runIn(10, "verifyTurnOff")
    } else { 
        atomicState.offRetryCount = 0 
    }
}

def physicalOnHandler(evt) {
    // THE BRIDGE BYPASS
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return

    def debounceTime = isSmartBulbOnRelay ? 10000 : 5000 // Extended debounce for relay-controlled smart bulbs
    def isOutsideAppDebounce = (now() - (atomicState.lastAutoCommand ?: 0) > debounceTime) 
    
    // Ignore events immediately following an app command to prevent boot reports from triggering manual mode
    if (!isOutsideAppDebounce) return
    
    // Ignore physical events if a lock switch is active
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") return
    
    atomicState.manuallyTurnedOn = true
    atomicState.appTurnedOn = true
    atomicState.warningPhase = false
    recordTurnOnTime() 
    startTurnOffTimer() 
    
    // Sync color and level when physically turned on
    if (lightType == "Color / CT Bulb" || lightType == "Adjustable Bulb / Dimmer") {
        // Wait 1 second to ensure the bulb is fully on the mesh network after relay boot
        runIn(1, "syncManualBulbs")
    }
}

def physicalOffHandler(evt) {
    // THE BRIDGE BYPASS
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return

    def debounceTime = isSmartBulbOnRelay ? 10000 : 5000 // Extended debounce for relay-controlled smart bulbs
    def isOutsideAppDebounce = (now() - (atomicState.lastAutoOffCommand ?: 0) > debounceTime) 
    
    // Ignore events immediately following an app command to prevent hub-level physical flags from overriding automation
    if (!isOutsideAppDebounce) return
    
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.arrivalActive = false
    atomicState.warningPhase = false
    def gp = (gracePeriod ?: 15)
    atomicState.gracePeriodEnd = now() + (gp * 1000)
    cancelAllTurnOffTimers()
    recordTurnOffTime() 
}

def syncManualBulbs() {
    atomicState.lastAutoCommand = now() // Update debounce so the commands below don't re-trigger manual overrides
    
    def t = enableSoftStart ? (softStartTime ?: 3) : null
    def lvl = getTargetLevel()

    if (lightType == "Adjustable Bulb / Dimmer") {
        dimmers?.each {
            if (it.currentValue("switch") == "on") {
                if (t != null) {
                    it.setLevel(lvl, t)
                } else {
                    it.setLevel(lvl)
                }
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        def ct = getTargetColorTemp()
        colorBulbs?.each {
            if (it.currentValue("switch") == "on") {
                if (t != null) {
                    it.setLevel(lvl, t)
                    pauseExecution(100) // Staggered to prevent popcorn effect
                    it.setColorTemperature(ct, lvl, t)
                } else {
                    it.setLevel(lvl)
                    pauseExecution(100) // Staggered to prevent popcorn effect
                    it.setColorTemperature(ct, lvl)
                }
            }
        }
    }
}

def modeChangeHandler(evt) {
    def forceOffList = [turnOffOnModes].flatten().findAll { it }
    
    if (forceOffList.contains(evt.value)) {
        cancelAllTurnOffTimers() 
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "processTurnOff")
        
    } else if (adjustOnModeChange && atomicState.appTurnedOn) {
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "applyLightingSettings")
    }
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        debugLog("Live Dashboard Refreshed")
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
    atomicState.arrivalActive = true
    atomicState.appTurnedOn = true
    recordTurnOnTime()
    atomicState.lastAutoCommand = now() 
    
    if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
        def lvl = getTargetLevel()
        colorBulbs?.each { it.setColorTemperature(6500, lvl, 0) }
    } else {
        applyLightingSettings()
    }
}

def revertFromArrival() {
    atomicState.arrivalActive = false
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
    if (atomicState.manuallyTurnedOn) {
        atomicState.manuallyTurnedOn = false
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
                     dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    
    def primaryActive = isPrimaryActive()
    def keepAliveActive = isKeepAliveActive()
    
    def healthData = []
    if (enableHealthWatchdog) {
        def checkList = []
        if (motionSensors) checkList.addAll(motionSensors)
        if (keepAliveMotionSensors) checkList.addAll(keepAliveMotionSensors)
        if (contactSensors) checkList.addAll(contactSensors)
        if (shadeSensors) checkList.addAll(shadeSensors)
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
    if (atomicState.arrivalActive) statusText = "<span style='color: #800080;'>Arrival Override</span>"
    else if (enableOccupancyLock && occupancyLockContact?.currentValue("contact") == "closed") statusText = "<span style='color: #a52a2a;'>Locked (Occupied)</span>"
    else if (isLightOn && atomicState.manuallyTurnedOn) statusText = "<span style='color: #0055aa;'>Manual Override</span>"
    else if (activeModes && !activeModes.contains(location.mode)) statusText = "<span style='color: orange;'>Blocked (Mode: ${location.mode})</span>"
    else if (timeBlocked) statusText = "<span style='color: orange;'>Blocked (Time Window)</span>"
    else if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: red;'>Disabled (ON Block)</span>"
    else if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: darkred;'>Disabled (OFF Block)</span>"
    else if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") statusText = "<span style='color: darkblue;'>Nap Lock Active</span>"
    else {
        def isOpen = (contactSensors?.any { it.currentValue("contact") == "open" } || shadeSensors?.any { it.currentValue("contact") == "open" })
        if (isOpen) {
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
                statusText = "Occupied (Contact/Shade Bypass Active)"
            } else {
                statusText = "<span style='color: orange;'>Blocked (Open Window/Door/Shades)</span>"
            }
        }
        else if (atomicState.warningPhase) statusText = "<span style='color: orange;'>Warning Dim Phase</span>"
        else if (isLightOn && (primaryActive || keepAliveActive)) statusText = "Occupied"
        else if (isLightOn && !primaryActive && !keepAliveActive) statusText = "Counting Down"
        else if (!isLightOn && primaryActive) statusText = "Motion Ignored"
    }
    
    def timerText = "--"
    if (isLightOn && atomicState.stdTaskTime && atomicState.stdTaskTime > now()) {
        def diff = atomicState.stdTaskTime - now()
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
        lastTrigger: enableTriggerTracking ? atomicState.lastTriggerSource : null,
        timer: timerText,
        health: healthData,
        roi: enableTelemetry ? calculateLiveSavings() : null
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

def resetROI() {
    atomicState.lifetimeSavings = 0.0
    atomicState.todayOnMillis = 0
    if (atomicState.onTimeStart) {
        atomicState.onTimeStart = now() 
    }
}
