/**
 * Advanced Outdoor Security Lighting
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Outdoor Security Lighting",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Enterprise exterior security lighting controller using Solar Geometry, Mode Blacklisting, Motion Overrides, and Cloud-Sync.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Outdoor Security Lighting", install: true, uninstall: true) {
        
        section("Live Environmental Dashboard") {
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Solar Position</th><th style='padding: 8px;'>Current Values</th><th style='padding: 8px;'>System Status</th></tr>"
            
            def sElev = state.solarElevation != null ? "${state.solarElevation}°" : "Calculating..."
            def sAzim = state.solarAzimuth != null ? "${state.solarAzimuth}°" : "Calculating..."
            
            def sColor = (state.solarElevation != null && state.solarElevation < 0) ? "purple" : "orange"
            def sDesc = (state.solarElevation != null && state.solarElevation < 0) ? "Below Horizon (Night)" : "Above Horizon (Day)"
            
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Elevation:</b><br><b>Azimuth:</b></td><td style='padding: 8px; color: ${sColor}; font-weight: bold;'>${sElev}<br>${sAzim}</td><td style='padding: 8px;'>${sDesc}</td></tr>"
            statusText += "</table>"
            
            statusText += "<b>Lighting Zones</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone Name</th><th style='padding: 8px;'>Hardware State</th><th style='padding: 8px;'>Active Trigger Reason</th></tr>"
            
            def zoneCount = settings["numZones"] ?: 1
            if (zoneCount > 0) {
                for (int i = 1; i <= (zoneCount as Integer); i++) {
                    def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
                    def switches = settings["zoneLights_${i}"]
                    
                    if (!switches) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td style='padding: 8px; color: #888;'>Unconfigured</td><td style='padding: 8px;'>-</td></tr>"
                        continue
                    }
                    
                    def anyOn = switches.any { it.currentValue("switch") == "on" }
                    def hState = anyOn ? "ON" : "OFF"
                    def hColor = anyOn ? "green" : "black"
                    def tReason = state.zoneReason?."${i}" ?: "Waiting for event..."
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td style='padding: 8px; color: ${hColor}; font-weight: bold;'>${hState}</td><td style='padding: 8px;'>${tReason}</td></tr>"
                }
            }
            statusText += "</table>"
            
            def globalStatus = isSystemPaused() ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            statusText += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>System Core:</b> ${globalStatus}</div>"

            paragraph statusText
        }

        section("💰 Financial ROI & Energy Savings") {
            def roiText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            roiText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone</th><th style='padding: 8px;'>Actual On Cost</th><th style='padding: 8px;'>Off Savings</th></tr>"

            def costRate = settings["kwhCost"] != null ? settings["kwhCost"].toBigDecimal() : 0.13
            def installTime = state.installTime ?: new Date().time
            def daysInstalled = Math.max(1.0, (new Date().time - installTime) / 86400000.0)
            def sysTotalSavings = 0.0

            def zoneCount = settings["numZones"] ?: 1
            if (zoneCount > 0) {
                for (int i = 1; i <= (zoneCount as Integer); i++) {
                    def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
                    def zWatts = settings["zoneWatts_${i}"] ?: 0
                    
                    if (zWatts == 0) {
                        roiText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td colspan='2' style='padding: 8px; color: #888; font-style: italic;'>Enter total zone wattage below to enable ROI tracking.</td></tr>"
                        continue
                    }

                    def kw = zWatts / 1000.0
                    def baselineHours = daysInstalled * 12.0 // Assuming standard 12 hour dusk-to-dawn runtime

                    def switches = settings["zoneLights_${i}"]
                    def anyOn = switches ? switches.any { it.currentValue("switch") == "on" } : false
                    def currentRunMs = (anyOn && state."trackOnTime_${i}") ? (new Date().time - state."trackOnTime_${i}") : 0
                    def totalRuntimeMs = (state."lifetimeRuntimeMs_${i}" ?: 0) + currentRunMs
                    def actualHours = totalRuntimeMs / 3600000.0
                    
                    // Safely calculate off hours (preventing negatives if someone manually left lights on for 24+ hours)
                    def offHours = Math.max(0.0, baselineHours - actualHours)

                    def actualCost = kw * actualHours * costRate
                    def saved = kw * offHours * costRate
                    sysTotalSavings += saved

                    def fmtAct = String.format('$%.2f', actualCost)
                    def fmtSave = String.format('$%.2f', saved)

                    roiText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td style='padding: 8px; color: #d9534f;'>${fmtAct}</td><td style='padding: 8px; color: #5cb85c; font-weight: bold;'>+ ${fmtSave}</td></tr>"
                }
            }
            roiText += "</table>"
            def fmtTotal = String.format('$%.2f', sysTotalSavings)
            roiText += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 14px;'><b>Total System ROI (Money Saved):</b> <span style='color: #5cb85c; font-weight: bold;'>+ ${fmtTotal}</span></div>"
            
            paragraph roiText
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            }
        }
        
        section("Global Core Settings") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            input "numZones", "number", title: "Number of Lighting Zones to Configure (1-10)", required: true, defaultValue: 1, range: "1..10", submitOnChange: true
            input "kwhCost", "decimal", title: 'Electricity Rate ($ per kWh) for ROI Tracking', defaultValue: 0.13, required: true
            
            paragraph "<b>Location Fallback:</b> If your hub's GPS coordinates are missing, select your state to auto-configure solar geometry settings."
            input "userState", "enum", title: "Select your US State", required: false, options: [
                "AL":"Alabama", "AK":"Alaska", "AZ":"Arizona", "AR":"Arkansas", "CA":"California", "CO":"Colorado", "CT":"Connecticut", "DE":"Delaware", "FL":"Florida", "GA":"Georgia", "HI":"Hawaii", "ID":"Idaho", "IL":"Illinois", "IN":"Indiana", "IA":"Iowa", "KS":"Kansas", "KY":"Kentucky", "LA":"Louisiana", "ME":"Maine", "MD":"Maryland", "MA":"Massachusetts", "MI":"Michigan", "MN":"Minnesota", "MS":"Mississippi", "MO":"Missouri", "MT":"Montana", "NE":"Nebraska", "NV":"Nevada", "NH":"New Hampshire", "NJ":"New Jersey", "NM":"New Mexico", "NY":"New York", "NC":"North Carolina", "ND":"North Dakota", "OH":"Ohio", "OK":"Oklahoma", "OR":"Oregon", "PA":"Pennsylvania", "RI":"Rhode Island", "SC":"South Carolina", "SD":"South Dakota", "TN":"Tennessee", "TX":"Texas", "UT":"Utah", "VT":"Vermont", "VA":"Virginia", "WA":"Washington", "WV":"West Virginia", "WI":"Wisconsin", "WY":"Wyoming"
            ]

            paragraph "<b>Cloud/Weather Integration:</b> Select the Virtual Switch created by your 'Advanced Overcast Detector' app. If this is ON, the lighting zones can activate early regardless of sun position."
            input "overcastSwitch", "capability.switch", title: "Overcast/Darkness Virtual Switch", required: false
        }
        
        def zoneCount = settings["numZones"] ?: 1
        if (zoneCount > 0 && zoneCount <= 10) {
            
            section("<b>Lighting Zone Configurations</b>") {
                paragraph "<div style='font-size:13px; color:#555;'>Click on a zone below to expand its configuration settings.</div>"
            }
            
            for (int i = 1; i <= (zoneCount as Integer); i++) {
                def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
                
                section("<b>⚙️ ${zName} Configuration</b>", hideable: true, hidden: true) {
                    input "zoneName_${i}", "text", title: "Custom Zone Name", required: false, defaultValue: "Zone ${i}", submitOnChange: true
                    input "zoneLights_${i}", "capability.switch", title: "Select Lights for this Zone", multiple: true, required: true
                    input "zoneWatts_${i}", "number", title: "Total Wattage of Lights in this Zone (For ROI Tracking)", required: false, defaultValue: 0
                    
                    paragraph "<b>Outdoor Motion Triggers</b>"
                    input "motionSensor_${i}", "capability.motionSensor", title: "Outdoor Motion Sensor(s)", multiple: true, required: false
                    input "motionTimeout_${i}", "number", title: "Turn OFF after X minutes of no motion", defaultValue: 5, required: false
                    input "motionStuckTimeout_${i}", "number", title: "Ignore motion sensor if stuck 'Active' for X minutes", defaultValue: 60, required: false
                    input "motionOverridesMode_${i}", "bool", title: "SECURITY OVERRIDE: Allow Motion/Exit to turn ON lights even if Mode is Blacklisted (e.g., 'Night')?", defaultValue: true

                    paragraph "<b>Exit Lighting (Door Open Override)</b>"
                    input "enableExitLighting_${i}", "bool", title: "Enable Exit Lighting (Predictive turn-on when exiting)?", defaultValue: false, submitOnChange: true
                    if (settings["enableExitLighting_${i}"]) {
                        input "doorContact_${i}", "capability.contactSensor", title: "Door Contact Sensor(s)", multiple: true, required: true
                        input "insideMotion_${i}", "capability.motionSensor", title: "Inside Motion Sensor(s) (Next to door)", multiple: true, required: true
                        input "insideMotionWindow_${i}", "number", title: "Require inside motion within last X minutes", defaultValue: 2, required: true
                        input "exitModes_${i}", "mode", title: "Exit Lighting Whitelist: ONLY enable in these Modes (Leave blank for all modes)", multiple: true, required: false
                    }

                    paragraph "<b>Primary ON Triggers (Select any combination)</b>"
                    input "triggerSunset_${i}", "bool", title: "Turn ON continuously at Official Sunset?", defaultValue: false
                    input "triggerOvercast_${i}", "bool", title: "Turn ON continuously if Overcast Switch is Active?", defaultValue: false
                    
                    input "triggerElevation_${i}", "number", title: "Turn ON continuously when Sun Elevation Drops Below (°)", defaultValue: 0, required: false, description: "0° is Sunset. -4° is Dusk."
                    input "triggerAzimuthStart_${i}", "number", title: "Azimuth Filter Start (°)", required: false, description: "Leave blank unless tying to house orientation."
                    input "triggerAzimuthEnd_${i}", "number", title: "Azimuth Filter End (°)", required: false, description: "0=N, 90=E, 180=S, 270=W"
                    
                    paragraph "<b>Primary OFF Triggers</b>"
                    input "triggerSunrise_${i}", "bool", title: "Turn OFF at Official Sunrise?", defaultValue: true
                    input "hardOffTime_${i}", "time", title: "Hard OFF Time (e.g., 11:00 PM)", required: false, description: "Forces lights off to save energy."
                    
                    paragraph "<b>Mode Restrictions & Blacklists</b>"
                    input "zoneModes_${i}", "mode", title: "Whitelist: ONLY operate in these Modes", multiple: true, required: false
                    input "disableModes_${i}", "mode", title: "Blacklist: Turn OFF and disable in these Modes", multiple: true, required: false
                }
            }
        }
    }
}

def installed() {
    log.info "Advanced Outdoor Security Lighting Installed."
    initialize()
}

def updated() {
    log.info "Advanced Outdoor Security Lighting Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.zoneReason = state.zoneReason ?: [:]
    state.installTime = state.installTime ?: new Date().time
    
    // Evaluate solar position and rules every 5 minutes
    schedule("0 0/5 * * * ?", evaluateSystem)
    
    // Hard Time Schedules & Subscriptions
    def zoneCount = settings["numZones"] ?: 1
    for (int i = 1; i <= (zoneCount as Integer); i++) {
        def offTime = settings["hardOffTime_${i}"]
        if (offTime) {
            schedule(offTime, "executeHardOff", [data: [zoneId: i]])
        }

        def switches = settings["zoneLights_${i}"]
        if (switches) subscribe(switches, "switch", switchHandler)

        def mSensors = settings["motionSensor_${i}"]
        if (mSensors) subscribe(mSensors, "motion", motionHandler)
        
        if (settings["enableExitLighting_${i}"]) {
            def contacts = settings["doorContact_${i}"]
            if (contacts) subscribe(contacts, "contact", doorContactHandler)

            def insideSensors = settings["insideMotion_${i}"]
            if (insideSensors) subscribe(insideSensors, "motion", insideMotionHandler)
        }
    }
    
    if (overcastSwitch) {
        subscribe(overcastSwitch, "switch", overcastHandler)
    }
    
    subscribe(location, "mode", modeHandler)
    
    // Initial Run
    runIn(2, "evaluateSystem")
}

// --- UTILITY: LOGGER ---
def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    log.info "HISTORY: " + msg.replaceAll("\\<.*?\\>", "")
}

def isSystemPaused() {
    return (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
}

def modeHandler(evt) {
    addToHistory("SYSTEM: Hub mode changed to ${evt.value}. Re-evaluating lighting zones.")
    evaluateSystem()
}

def overcastHandler(evt) {
    addToHistory("WEATHER OVERRIDE: Overcast state changed to ${evt.value.toUpperCase()}. Re-evaluating lighting zones.")
    evaluateSystem()
}

def insideMotionHandler(evt) {
    if (isSystemPaused()) return
    
    if (evt.value == "active") {
        def zoneCount = settings["numZones"] ?: 1
        for (int i = 1; i <= (zoneCount as Integer); i++) {
            def mSensors = settings["insideMotion_${i}"]
            if (mSensors && mSensors.any { it.id.toString() == evt.device.id.toString() }) {
                state."lastInsideMotion_${i}" = new Date().time
            }
        }
    }
}

def doorContactHandler(evt) {
    if (isSystemPaused()) return
    
    def zoneCount = settings["numZones"] ?: 1
    for (int i = 1; i <= (zoneCount as Integer); i++) {
        def contacts = settings["doorContact_${i}"]
        if (contacts && contacts.any { it.id.toString() == evt.device.id.toString() }) {
            def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
            if (evt.value == "open") {
                addToHistory("EXIT LIGHTING: Door opened for ${zName}. Evaluating predictive exit conditions.")
            } else {
                addToHistory("EXIT LIGHTING: Door closed for ${zName}. Re-evaluating system.")
            }
            evaluateSystem()
        }
    }
}

def switchHandler(evt) {
    if (isSystemPaused()) return
    
    def zoneCount = settings["numZones"] ?: 1
    for (int i = 1; i <= (zoneCount as Integer); i++) {
        def switches = settings["zoneLights_${i}"]
        if (switches && switches.any { it.id.toString() == evt.device.id.toString() }) {
            def lastCmd = state."appCommandTime_${i}" ?: 0
            def now = new Date().time
            
            // If the app hasn't issued a command to this zone in the last 10 seconds, it's a manual override
            if ((now - lastCmd) > 10000) {
                def currentMode = location.mode
                def disabledModes = settings["disableModes_${i}"]
                def isRestricted = (disabledModes && disabledModes.contains(currentMode))

                // ONLY apply the override if the hub is currently in a Blacklisted Mode
                if (evt.value == "off" && isRestricted) {
                    state."manualOffOverride_${i}" = true
                    def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
                    addToHistory("SECURITY SUPPRESSION: ${evt.device.displayName} manually turned OFF in ${zName}. Suppressing motion triggers until next fresh event.")
                    evaluateSystem()
                }
            }
        }
    }
}

def motionHandler(evt) {
    if (isSystemPaused()) return
    
    def zoneCount = settings["numZones"] ?: 1
    for (int i = 1; i <= (zoneCount as Integer); i++) {
        def mSensors = settings["motionSensor_${i}"]
        
        if (mSensors && mSensors.any { it.id.toString() == evt.device.id.toString() }) {
            
            if (evt.value == "active") {
                def deviceId = evt.device.id.toString()
                
                // It is considered a "fresh" trigger if the zone was completely inactive OR if a completely different sensor fired
                def isFreshTrigger = (!state."motionActive_${i}" || state."lastActiveDevice_${i}" != deviceId)
                
                if (isFreshTrigger) {
                    state."motionActiveTime_${i}" = new Date().time 
                    state."lastActiveDevice_${i}" = deviceId
                    
                    // Clear the manual override on a fresh trigger
                    if (state."manualOffOverride_${i}") {
                        state.remove("manualOffOverride_${i}")
                        def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
                        addToHistory("OVERRIDE CLEARED: Fresh motion detected by ${evt.device.displayName} in ${zName}.")
                    }
                }
                
                state."motionActive_${i}" = true
                state.remove("motionOffTime_${i}") 

            } else {
                def anyActive = mSensors.any { it.currentValue("motion") == "active" }
                if (!anyActive) {
                    state."motionActive_${i}" = false 
                    def timeout = settings["motionTimeout_${i}"] ?: 5
                    state."motionOffTime_${i}" = new Date().time + (timeout * 60000)
                }
            }
            evaluateSystem() 
        }
    }
}

def executeHardOff(data) {
    if (isSystemPaused()) return
    def zId = data.zoneId
    def zName = settings["zoneName_${zId}"] ?: "Zone ${zId}"
    def switches = settings["zoneLights_${zId}"]
    
    addToHistory("SCHEDULE: Hard OFF Time reached for ${zName}. Shutting down zone.")
    state.zoneReason["${zId}"] = "Hard OFF Time Reached"
    
    state."appCommandTime_${zId}" = new Date().time
    switches?.each { if (it.currentValue("switch") != "off") it.off() }
    runInMillis(500, "refreshZone", [data: [zoneId: zId]])
}

def refreshZone(data) {
    def zId = data.zoneId
    def switches = settings["zoneLights_${zId}"]
    switches?.each { light ->
        try {
            light.refresh()
        } catch (e) {
            // Failsafe: Ignore if device driver does not natively support the refresh() capability
        }
    }
}

// --- CORE SYSTEM LOOP ---
def evaluateSystem() {
    if (isSystemPaused()) return
    
    calculateSolarPosition()
    
    def zoneCount = settings["numZones"] ?: 1
    def now = new Date()
    
    def isNight = (state.solarElevation != null && state.solarElevation < 0)
    def currentMode = location.mode
    
    for (int i = 1; i <= (zoneCount as Integer); i++) {
        def zName = settings["zoneName_${i}"] ?: "Zone ${i}"
        def switches = settings["zoneLights_${i}"]
        if (!switches) continue
        
        // Failsafe: Automatically clear the manual override if the hub is no longer in a Blacklisted mode
        def disabledModes = settings["disableModes_${i}"]
        def isRestricted = (disabledModes && disabledModes.contains(currentMode))
        if (!isRestricted && state."manualOffOverride_${i}") {
             state.remove("manualOffOverride_${i}")
             addToHistory("OVERRIDE CLEARED: Mode restriction lifted for ${zName}.")
        }

        def shouldBeOn = false
        def triggerReason = ""

        // --- 1. OUTDOOR MOTION & EXIT LIGHTING (Highest Priority) ---
        def isDark = isNight || (settings["triggerOvercast_${i}"] && overcastSwitch && overcastSwitch.currentValue("switch") == "on")
        def motionWantsOn = false
        
        // A. Outdoor Motion Evaluation
        if (settings["motionSensor_${i}"] && isDark) {
            if (state."manualOffOverride_${i}") {
                motionWantsOn = false // Suppress motion trigger due to security manual override
            }
            else if (state."motionActive_${i}") {
                def activeSince = state."motionActiveTime_${i}" ?: now.time
                def stuckMin = settings["motionStuckTimeout_${i}"] ?: 60
                
                // Check if sensor has been active longer than the user-defined stuck timeout
                if (stuckMin > 0 && (now.time - activeSince) >= (stuckMin * 60000)) {
                    if (state.zoneReason["${i}"] != "Motion Sensor Stuck") {
                        addToHistory("WARNING: Motion sensor in ${zName} stuck active for ${stuckMin}+ mins. Ignoring.")
                    }
                    motionWantsOn = false // Ignore the motion trigger
                } else {
                    motionWantsOn = true
                    triggerReason = "Motion Detected (Security Override)"
                }
            } else {
                def offTime = state."motionOffTime_${i}"
                if (offTime) {
                    if (now.time < offTime) {
                        motionWantsOn = true
                        triggerReason = "Motion Timeout Pending"
                        def remainingSecs = Math.ceil((offTime - now.time) / 1000.0).toInteger()
                        if (remainingSecs > 0) runIn(remainingSecs + 2, "evaluateSystem")
                    } else {
                        state.remove("motionOffTime_${i}") 
                    }
                }
            }
        }

        // B. Predictive Exit Lighting Evaluation
        if (settings["enableExitLighting_${i}"] && isDark) {
            def exitAllowedModes = settings["exitModes_${i}"]
            def isExitModeAllowed = (!exitAllowedModes || exitAllowedModes.contains(currentMode))

            if (isExitModeAllowed) {
                def contacts = settings["doorContact_${i}"]
                def doorOpen = contacts ? contacts.any { it.currentValue("contact") == "open" } : false

                if (doorOpen) {
                    def insideTime = state."lastInsideMotion_${i}" ?: 0
                    def windowMs = (settings["insideMotionWindow_${i}"] ?: 2) * 60000
                    def recentInsideMotion = (now.time - insideTime) <= windowMs
                    def outsideMotionActive = state."motionActive_${i}" ?: false

                    if (state.zoneReason["${i}"] == "Exit Lighting (Door Open)" || (recentInsideMotion && !outsideMotionActive)) {
                        motionWantsOn = true
                        triggerReason = "Exit Lighting (Door Open)"
                    }
                }
            }
        }

        // --- 2. Mode Check (Whitelist & Blacklist) ---
        def allowedModes = settings["zoneModes_${i}"]
        
        if (allowedModes && !allowedModes.contains(currentMode)) isRestricted = true
        // isRestricted already evaluated earlier for the failsafe clear logic
        
        if (isRestricted) {
            def overrideAllowed = settings["motionOverridesMode_${i}"] != false
            if (motionWantsOn && overrideAllowed) {
                shouldBeOn = true 
            } else {
                def restReason = "Mode Restriction (Forced OFF)"
                if (switches.any { it.currentValue("switch") == "on" } || state.zoneReason["${i}"] != restReason) {
                    addToHistory("MODE RESTRICTION: Turning off ${zName} (Hub in restricted mode).")
                    state.zoneReason["${i}"] = restReason
                    state."appCommandTime_${i}" = now.time
                    switches.each { it.off() }
                    runInMillis(500, "refreshZone", [data: [zoneId: i]])
                }
                
                // Ensure tracker captures the turn off event if it was on
                if (state."trackOnTime_${i}") {
                    state."lifetimeRuntimeMs_${i}" = (state."lifetimeRuntimeMs_${i}" ?: 0) + (now.time - state."trackOnTime_${i}")
                    state.remove("trackOnTime_${i}")
                }
                
                continue 
            }
        }

        // --- 3. Normal Environmental Logic ---
        if (!shouldBeOn && motionWantsOn) {
            shouldBeOn = true
        } else if (!shouldBeOn) {
            if (settings["triggerSunset_${i}"] && isNight) {
                shouldBeOn = true
                triggerReason = "Official Astro Nighttime"
            }
            
            if (!shouldBeOn && settings["triggerOvercast_${i}"] && overcastSwitch && overcastSwitch.currentValue("switch") == "on") {
                shouldBeOn = true
                triggerReason = "Weather/Overcast Override"
            }
            
            if (!shouldBeOn && settings["triggerElevation_${i}"] != null) {
                def targetElev = settings["triggerElevation_${i}"].toBigDecimal()
                def currElev = state.solarElevation
                
                if (currElev != null && currElev <= targetElev) {
                    def azStart = settings["triggerAzimuthStart_${i}"]
                    def azEnd = settings["triggerAzimuthEnd_${i}"]
                    
                    if (azStart != null && azEnd != null) {
                        def currAz = state.solarAzimuth
                        if (currAz >= azStart.toBigDecimal() && currAz <= azEnd.toBigDecimal()) {
                            shouldBeOn = true
                            triggerReason = "Solar Geometry (Elevation ${currElev}°, Azimuth ${currAz}°)"
                        }
                    } else {
                        shouldBeOn = true
                        triggerReason = "Solar Elevation (${currElev}°)"
                    }
                }
            }
        }
        
        // --- 4. Safety Check ---
        def hardOff = settings["hardOffTime_${i}"]
        if (shouldBeOn && hardOff && !motionWantsOn) { 
            def offDate = timeToday(hardOff, location.timeZone)
            if (now.after(offDate) && isNight) {
                shouldBeOn = false
                triggerReason = "Hard OFF Time Enforced"
            }
        }
        
        // --- 5. Execution & ROI Tracking ---
        def currentlyOn = switches.any { it.currentValue("switch") == "on" }
        def reasonChanged = (state.zoneReason["${i}"] != triggerReason)
        
        if (shouldBeOn && (!currentlyOn || reasonChanged)) {
            if (!currentlyOn) {
                state."trackOnTime_${i}" = now.time // Start the ROI timer
            }
            
            addToHistory("LIGHTING: Activating ${zName}. Reason: ${triggerReason}")
            state.zoneReason["${i}"] = triggerReason
            state."appCommandTime_${i}" = now.time
            switches.each { it.on() }
            
            // Wait 500ms then request hardware status refresh
            runInMillis(500, "refreshZone", [data: [zoneId: i]])
        } 
        else if (!shouldBeOn) {
            def offReason = isNight ? "Hard OFF / Timeout Enforced" : "Daylight / Clear Requirements Met"
            if (state.zoneReason["${i}"] == "Motion Sensor Stuck") offReason = "Motion Sensor Stuck"
            
            def offReasonChanged = (state.zoneReason["${i}"] != offReason)
            
            if (currentlyOn || offReasonChanged) {
                if (currentlyOn && state."trackOnTime_${i}") {
                    // Calculate exact runtime and add to lifetime ROI tracker before turning off
                    state."lifetimeRuntimeMs_${i}" = (state."lifetimeRuntimeMs_${i}" ?: 0) + (now.time - state."trackOnTime_${i}")
                    state.remove("trackOnTime_${i}")
                }
                
                addToHistory("LIGHTING: Deactivating ${zName}. Reason: ${offReason}")
                state.zoneReason["${i}"] = offReason
                state."appCommandTime_${i}" = now.time
                switches.each { it.off() }
                
                // Wait 500ms then request hardware status refresh
                runInMillis(500, "refreshZone", [data: [zoneId: i]])
            }
        }
    }
}

// --- MATHEMATICAL SOLAR ENGINE ---
def calculateSolarPosition() {
    def lat = location.latitude
    def lon = location.longitude
    
    if (!lat || !lon) {
        if (settings["userState"]) {
            def coords = getStateCoordinates(settings["userState"])
            lat = coords.lat
            lon = coords.lon
        }
    }

    if (!lat || !lon) {
        log.warn "Advanced Outdoor Security Lighting: Hub Latitude, Longitude, or State is not set!"
        return
    }
    
    def now = new Date()
    def tzOffset = location.timeZone.getOffset(now.time) / 3600000.0
    def year = now.format("yyyy", TimeZone.getTimeZone("UTC")).toInteger()
    def month = now.format("MM", TimeZone.getTimeZone("UTC")).toInteger()
    def day = now.format("dd", TimeZone.getTimeZone("UTC")).toInteger()
    def hour = now.format("HH", TimeZone.getTimeZone("UTC")).toInteger()
    def minute = now.format("mm", TimeZone.getTimeZone("UTC")).toInteger()
    def second = now.format("ss", TimeZone.getTimeZone("UTC")).toInteger()
    
    if (month <= 2) {
        year -= 1
        month += 12
    }
    
    def a = Math.floor(year / 100)
    def b = 2 - a + Math.floor(a / 4)
    def jd = Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5
    def jdTime = jd + ((hour + (minute / 60.0) + (second / 3600.0)) / 24.0)
    def d = jdTime - 2451545.0
    def w = 282.9404 + 4.70935E-5 * d
    def e = 0.016709 - 1.151E-9 * d
    def M = (356.0470 + 0.9856002585 * d) % 360.0
    if (M < 0) M += 360.0
    def L = (w + M) % 360.0
    def MRad = Math.toRadians(M)
    def E = M + (180 / Math.PI) * e * Math.sin(MRad) * (1 + e * Math.cos(MRad))
    def ERad = Math.toRadians(E)
    def x = Math.cos(ERad) - e
    def y = Math.sin(ERad) * Math.sqrt(1 - e * e)
    def v = Math.toDegrees(Math.atan2(y, x))
    def lonSun = (v + w) % 360.0
    def lonSunRad = Math.toRadians(lonSun)
    def obl = 23.4393 - 3.563E-7 * d
    def oblRad = Math.toRadians(obl)
    def declRad = Math.asin(Math.sin(oblRad) * Math.sin(lonSunRad))
    def decl = Math.toDegrees(declRad)
    def raRad = Math.atan2(Math.cos(oblRad) * Math.sin(lonSunRad), Math.cos(lonSunRad))
    def ra = Math.toDegrees(raRad)
    def gmst0 = (L + 180) % 360.0 / 15.0
    def utHour = hour + (minute / 60.0) + (second / 3600.0)
    def lst = (gmst0 + utHour + (lon / 15.0)) % 24.0
    if (lst < 0) lst += 24.0
    def ha = (lst * 15.0) - ra
    if (ha < -180) ha += 360.0
    if (ha > 180) ha -= 360.0
    def haRad = Math.toRadians(ha)
    def latRad = Math.toRadians(lat)
    def elRad = Math.asin(Math.sin(declRad) * Math.sin(latRad) + Math.cos(declRad) * Math.cos(latRad) * Math.cos(haRad))
    def elevation = Math.toDegrees(elRad)
    def azRad = Math.acos((Math.sin(declRad) - Math.sin(elRad) * Math.sin(latRad)) / (Math.cos(elRad) * Math.cos(latRad)))
    def azimuth = Math.toDegrees(azRad)
    if (Math.sin(haRad) > 0) azimuth = 360.0 - azimuth
    
    state.solarElevation = elevation.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    state.solarAzimuth = azimuth.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
}

def getStateCoordinates(stateCode) {
    def coords = [
        "AL": [lat: 32.806671, lon: -86.791130], "AK": [lat: 61.370716, lon: -152.404419], "AZ": [lat: 33.729759, lon: -111.431221],
        "AR": [lat: 34.969704, lon: -92.373123], "CA": [lat: 36.116203, lon: -119.681564], "CO": [lat: 39.059811, lon: -105.311104],
        "CT": [lat: 41.597782, lon: -72.755371], "DE": [lat: 39.318523, lon: -75.507141], "FL": [lat: 27.766279, lon: -81.686783],
        "GA": [lat: 33.040619, lon: -83.643074], "HI": [lat: 21.094318, lon: -157.498337], "ID": [lat: 44.240459, lon: -114.478828],
        "IL": [lat: 40.349457, lon: -88.986137], "IN": [lat: 39.849426, lon: -86.258278], "IA": [lat: 42.011539, lon: -93.210526],
        "KS": [lat: 38.526600, lon: -96.726486], "KY": [lat: 37.668140, lon: -84.670067], "LA": [lat: 31.169546, lon: -91.867805],
        "ME": [lat: 44.693947, lon: -69.381927], "MD": [lat: 39.063946, lon: -76.802101], "MA": [lat: 42.230171, lon: -71.530106],
        "MI": [lat: 43.326618, lon: -84.536095], "MN": [lat: 45.694454, lon: -93.900192], "MS": [lat: 32.741646, lon: -89.678696],
        "MO": [lat: 38.456085, lon: -92.288368], "MT": [lat: 46.921925, lon: -110.454353], "NE": [lat: 41.125370, lon: -98.268082],
        "NV": [lat: 38.313515, lon: -117.055374], "NH": [lat: 43.452492, lon: -71.563896], "NJ": [lat: 40.298904, lon: -74.521011],
        "NM": [lat: 34.840515, lon: -106.248482], "NY": [lat: 42.165726, lon: -74.948051], "NC": [lat: 35.630066, lon: -79.806419],
        "ND": [lat: 47.528912, lon: -99.784012], "OH": [lat: 40.388783, lon: -82.764915], "OK": [lat: 35.565342, lon: -96.928917],
        "OR": [lat: 44.572021, lon: -122.070938], "PA": [lat: 40.590752, lon: -77.209755], "RI": [lat: 41.680893, lon: -71.511780],
        "SC": [lat: 33.856892, lon: -80.945007], "SD": [lat: 44.299782, lon: -99.438828], "TN": [lat: 35.747845, lon: -86.692345],
        "TX": [lat: 31.054487, lon: -97.563461], "UT": [lat: 40.150032, lon: -111.862434], "VT": [lat: 44.045876, lon: -72.710686],
        "VA": [lat: 37.769337, lon: -78.169968], "WA": [lat: 47.400902, lon: -121.490494], "WV": [lat: 38.491226, lon: -80.954453],
        "WI": [lat: 44.268543, lon: -89.616508], "WY": [lat: 42.755966, lon: -107.302490]
    ]
    return coords[stateCode] ?: [lat: null, lon: null]
}
