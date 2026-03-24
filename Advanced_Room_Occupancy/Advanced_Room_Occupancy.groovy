/**
 * Advanced Room Occupancy
 */

definition(
    name: "Advanced Room Occupancy",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-zone occupancy controller with System Boot Recovery, Active Wattage Failsafes, Two-Stage Shutdowns, and Collapsible UI.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Room Occupancy</b>", install: true, uninstall: true) {
        
        section("<b>Live Occupancy & ROI Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your configured rooms, active triggers, power profiles, and exact financial savings.</div>"
            
            def hasZones = false
            def rate = kwhCost ?: 0.13
            def totalAppSavings = 0.0

            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room</th><th style='padding: 8px;'>Occupancy State</th><th style='padding: 8px;'>Managed Devices</th><th style='padding: 8px;'>Power Profile</th><th style='padding: 8px;'>Est. Savings</th></tr>"

            for (int i = 1; i <= 12; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    
                    def isOccupied = getRoomOccupancyState(i)
                    def mDevs = settings["z${i}Motion"]
                    def vDevs = settings["z${i}Vibration"]
                    def pDevs = settings["z${i}Presence"]
                    def devs = settings["z${i}Switches"]
                    def softDevs = settings["z${i}SoftKillDevices"]
                    def pMonitor = settings["z${i}PowerMonitor"]
                    
                    def statusAdditions = []
                    def maxRemainingMs = 0

                    def isHardActive = (pDevs && pDevs.any{it.currentValue("presence") == "present"}) ||
                                       (vDevs && vDevs.any{it.currentValue("acceleration") == "active"}) ||
                                       (mDevs && mDevs.any{it.currentValue("motion") == "active"})

                    // Check Wattage Failsafe
                    if (pMonitor) {
                        def currentDraw = pMonitor.currentValue("power") ?: 0.0
                        def safeThresh = settings["z${i}ActiveWattageThreshold"] ?: 15.0
                        if (currentDraw > safeThresh) {
                            statusAdditions << "<span style='color:red; font-weight:bold;'>Power Lock Active (${currentDraw}W)</span>"
                            isHardActive = true
                        }
                    }

                    // --- Motion & Vibe Timeouts & Hit Counters ---
                    if (mDevs) {
                        def mTimeout = (settings["z${i}Timeout"] ?: 15) * 60000
                        def mLast = state.zoneLastActive ? state.zoneLastActive["z${i}"] : null
                        if (mLast && !mDevs.any{it.currentValue("motion") == "active"}) {
                            def mLeft = mTimeout - (now() - mLast)
                            if (mLeft > maxRemainingMs) maxRemainingMs = mLeft
                        }
                        
                        def mReqHits = settings["z${i}MotionActivationHits"] ?: 1
                        if (mReqHits > 1 && !isOccupied) {
                            def mWindow = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                            def mHits = state.motionHits ? (state.motionHits["z${i}"] ?: []) : []
                            mHits = mHits.findAll { (now() - it) <= mWindow }
                            if (mHits.size() > 0) statusAdditions << "<span style='color:purple;'>Motion Hits: ${mHits.size()}/${mReqHits}</span>"
                        }
                    }

                    if (vDevs) {
                        def vTimeout = (settings["z${i}VibeTimeout"] ?: 5) * 60000
                        def vLast = state.vibeLastActive ? state.vibeLastActive["z${i}"] : null
                        if (vLast && !vDevs.any{it.currentValue("acceleration") == "active"}) {
                            def vLeft = vTimeout - (now() - vLast)
                            if (vLeft > maxRemainingMs) maxRemainingMs = vLeft
                        }
                        
                        def vReqHits = settings["z${i}VibeActivationHits"] ?: 1
                        if (vReqHits > 1 && !isOccupied) {
                            def vWindow = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                            def vHits = state.vibeHits ? (state.vibeHits["z${i}"] ?: []) : []
                            vHits = vHits.findAll { (now() - it) <= vWindow }
                            if (vHits.size() > 0) statusAdditions << "<span style='color:purple;'>Vibe Hits: ${vHits.size()}/${vReqHits}</span>"
                        }
                    }

                    // --- Override Switch Timeout Display ---
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
                        def oTimeout = settings["z${i}OverrideTimeout"]
                        if (oTimeout && oTimeout > 0 && !isHardActive) {
                            def lastM = state.zoneLastActive ? state.zoneLastActive["z${i}"] : 0
                            def lastV = state.vibeLastActive ? state.vibeLastActive["z${i}"] : 0
                            def maxLast = Math.max(lastM ?: 0, lastV ?: 0)
                            if (maxLast > 0) {
                                def timeLeft = (oTimeout * 60000) - (now() - maxLast)
                                if (timeLeft > 0) {
                                    def minsLeft = Math.ceil(timeLeft / 60000).toInteger()
                                    statusAdditions << "<span style='color:blue;'>Override Auto-Off: ~${minsLeft}m</span>"
                                }
                            }
                        }
                    }

                    if (isOccupied && !isHardActive && maxRemainingMs > 0) {
                        def remainingMins = Math.ceil(maxRemainingMs / 60000).toInteger()
                        statusAdditions << "<span style='color:#888;'>Clearing in ~${remainingMins}m</span>"
                    }

                    if (!isOccupied && state.shutdownDelayActive?.contains(i)) {
                         statusAdditions << "<span style='color:orange;'>Safe Shutdown Sequence...</span>"
                    }

                    def remainingMinsDisplay = statusAdditions ? "<br><span style='font-size:11px;'>" + statusAdditions.join("<br>") + "</span>" : ""
                    def restrictionReason = getRoomRestrictionReason(i)
                    def stateColor = restrictionReason ? "orange" : (isOccupied ? "green" : "black")
                    def stateLabel = restrictionReason ? "PAUSED (${restrictionReason})" : (isOccupied ? "OCCUPIED" : "EMPTY")
                    def isOccupiedDisplay = "<b><span style='color:${stateColor};'>${stateLabel}</span></b>${remainingMinsDisplay}"
                    
                    // --- Calculate Dynamic Wattage ---
                    def totalActive = 0.0
                    def totalStandby = 0.0
                    def devNames = []
                    
                    if (devs) {
                        devs.each { dev ->
                            def activeW = settings["z${i}_${dev.id}_active"] ?: 0.0
                            def standbyW = settings["z${i}_${dev.id}_standby"] ?: 0.0
                            totalActive += activeW
                            totalStandby += standbyW
                            
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "⚡ ${dev.displayName} (${devState})"
                        }
                    }
                    if (softDevs) {
                        softDevs.each { dev ->
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "💻 ${dev.displayName} (${devState})"
                        }
                    }
                    
                    def devListDisplay = devNames ? devNames.join("<br>") : "<span style='color:gray;'>None</span>"
                    def powerDisplay = "<b>Active:</b> ${totalActive}W<br><b>Standby:</b> ${totalStandby}W"
                    
                    // --- Savings Calculation ---
                    def stats = state.roomStats ? state.roomStats["z${i}"] : [totalSecondsOff: 0, unoccupiedSince: null]
                    def secondsOff = stats?.totalSecondsOff ?: 0
                    if (stats?.unoccupiedSince != null && !restrictionReason) {
                        secondsOff += ((now() - stats.unoccupiedSince) / 1000).toLong()
                    }
                    
                    def savedHours = secondsOff / 3600.0
                    def wastedKw = (totalActive + totalStandby) / 1000.0
                    def roomSavings = savedHours * wastedKw * rate
                    totalAppSavings += roomSavings
                    
                    def formattedSavings = String.format('$%.2f', roomSavings)

                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td style='padding: 8px;'>${isOccupiedDisplay}</td><td style='padding: 8px; font-size:11px; color:#555;'>${devListDisplay}</td><td style='padding: 8px; font-size:11px;'>${powerDisplay}</td><td style='padding: 8px; color:#008800; font-weight:bold;'>${formattedSavings}</td></tr>"
                }
            }
            
            statusText += "</table>"
            
            if (hasZones) {
                def globalStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Global System Mode:</b> ${globalStatus}</div>"
                statusText += "</div>"
                
                def formattedTotal = String.format('$%.2f', totalAppSavings)
                statusText += "<div style='margin-top: 5px; padding: 10px; background: #e9f5ff; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #add8e6;'>"
                statusText += "<div><b>Estimated Financial ROI:</b> <span style='color: #008800; font-weight:bold;'>${formattedTotal}</span> <span style='color:#555; font-size:11px;'>(Calculated by eliminated Active & Standby wattage while rooms are empty)</span></div>"
                statusText += "</div>"
                
                paragraph statusText
            } else {
                paragraph "<i>No rooms configured yet.</i>"
            }
        }

        section("<b>Action History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
            }
        }

        section("<b>Global Configuration & Restrictions</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>ROI Settings:</b> Enter your current electricity rate to calculate how much money turning these devices off is saving you over time.</div>"
            input "kwhCost", "decimal", title: 'Electricity Cost ($ per kWh)', required: true, defaultValue: 0.13
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<div style='font-size:13px; color:#555; margin-top:10px;'><b>Global Mode Restrictions:</b> Select modes where the entire app should pause its automation.</div>"
            input "restrictedModes", "mode", title: "Restricted Modes (Pause all occupancy rules)", multiple: true, required: false
            
            input "resetSavings", "bool", title: "Reset All Savings Data to Zero", defaultValue: false, submitOnChange: true
            if (settings["resetSavings"]) {
                resetAllSavings()
                app.updateSetting("resetSavings", false)
            }
            
            input "forceSync", "bool", title: "Manually Force Hardware Sync (Pushes ON/OFF commands immediately)", defaultValue: false, submitOnChange: true
            if (settings["forceSync"]) {
                logAction("MANUAL OVERRIDE: Forcing hardware sync...")
                evaluateRooms(true)
                app.updateSetting("forceSync", false)
            }
        }

        // --- COLLAPSIBLE ROOM CONFIGURATION SECTIONS ---
        for (int i = 1; i <= 12; i++) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def statusTag = settings["enableZ${i}"] ? " <span style='color:green;'>(Active)</span>" : ""
            
            section("<b>▶ ${zName} Configuration</b>${statusTag}", hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable this Room</b>", submitOnChange: true
                
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Room Name", required: false, defaultValue: "Room ${i}", submitOnChange: true
                    
                    paragraph "<b>Triggers & Sensors</b>"
                    
                    input "z${i}OverrideSwitch", "capability.switch", title: "Virtual Override Switch (ON = Force Occupied)", required: false, submitOnChange: true
                    if (settings["z${i}OverrideSwitch"]) {
                        input "z${i}OverrideTimeout", "number", title: "↳ Auto-Off Timeout (Minutes of NO movement before turning switch OFF)", required: false, defaultValue: 120
                    }
                    
                    input "z${i}Motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                    if (settings["z${i}Motion"]) {
                        input "z${i}MotionActivationWindow", "number", title: "↳ Activation Window (Minutes to count hits)", required: true, defaultValue: 1
                        input "z${i}MotionActivationHits", "number", title: "↳ Required Active Hits to trigger Occupied", required: true, defaultValue: 1
                        input "z${i}Timeout", "number", title: "↳ Motion Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 15
                    }
                    
                    input "z${i}Vibration", "capability.accelerationSensor", title: "Vibration Sensors (e.g. Chair/Bed)", multiple: true, required: false, submitOnChange: true
                    if (settings["z${i}Vibration"]) {
                        input "z${i}VibeActivationWindow", "number", title: "↳ Activation Window (Minutes to count hits)", required: true, defaultValue: 1
                        input "z${i}VibeActivationHits", "number", title: "↳ Required Active Hits to trigger Occupied", required: true, defaultValue: 1
                        input "z${i}VibeTimeout", "number", title: "↳ Vibration Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 5
                    }
                    
                    input "z${i}Presence", "capability.presenceSensor", title: "mmWave / Presence Sensors (Instant Occupied)", multiple: true, required: false
                    
                    paragraph "<b>Actuators & Device Power Profiles</b>"
                    input "z${i}Switches", "capability.switch", title: "Hard Power Relays (Smart Plugs, Wall Switches)", multiple: true, required: false, submitOnChange: true
                    
                    if (settings["z${i}Switches"]) {
                        settings["z${i}Switches"].each { dev ->
                            input "z${i}_${dev.id}_active", "decimal", title: "↳ ${dev.displayName} - Active/Idle Watts", required: true, defaultValue: 50.0
                            input "z${i}_${dev.id}_standby", "decimal", title: "↳ ${dev.displayName} - Standby/Phantom Watts", required: true, defaultValue: 5.0
                        }
                    }

                    paragraph "<b>Two-Stage Graceful Shutdown & Power Failsafe</b>"
                    input "z${i}SoftKillDevices", "capability.switch", title: "Network Devices (PCs, TVs, APIs) for Graceful Shutdown", multiple: true, required: false, submitOnChange: true
                    if (settings["z${i}SoftKillDevices"]) {
                        input "z${i}HardKillDelay", "number", title: "Delay before cutting Hard Power (Seconds)", defaultValue: 60, required: true, description: "Provides time for operating systems to shut down and screens to run pixel refresh."
                    }
                    
                    input "z${i}PowerMonitor", "capability.powerMeter", title: "Failsafe Power Monitor (Prevents shutdown if device is active)", required: false, submitOnChange: true
                    if (settings["z${i}PowerMonitor"]) {
                        input "z${i}ActiveWattageThreshold", "decimal", title: "↳ Protection Threshold (Watts)", required: true, defaultValue: 15.0, description: "If the monitor reads above this wattage, the room will ignore empty timeouts and stay ON."
                    }
                    
                    paragraph "<b>Room Scheduling Restrictions</b>"
                    input "z${i}ActiveDays", "enum", title: "Active Days (Leave blank for all days)", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                    input "z${i}StartTime", "time", title: "Active Start Time (Optional)", required: false
                    input "z${i}EndTime", "time", title: "Active End Time (Optional)", required: false
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.vibeLastActive) state.vibeLastActive = [:]
    if (!state.motionHits) state.motionHits = [:]
    if (!state.vibeHits) state.vibeHits = [:]
    if (!state.roomStats) state.roomStats = [:]
    if (!state.currentRoomStates) state.currentRoomStates = [:]
    if (!state.shutdownDelayActive) state.shutdownDelayActive = []
    
    for (int i = 1; i <= 12; i++) {
        if (!state.roomStats["z${i}"]) state.roomStats["z${i}"] = [totalSecondsOff: 0, unoccupiedSince: null]
        if (!state.currentRoomStates["z${i}"]) state.currentRoomStates["z${i}"] = "unknown"
    }
    
    // Core Subscriptions
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", sensorHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}OverrideSwitch"]) subscribe(settings["z${i}OverrideSwitch"], "switch", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}Vibration"]) subscribe(settings["z${i}Vibration"], "acceleration", vibrationHandler)
            if (settings["z${i}Presence"]) subscribe(settings["z${i}Presence"], "presence", sensorHandler)
        }
    }
    
    runEvery5Minutes(evaluateRoomsLoop)
    logAction("Advanced Room Occupancy Initialized.")
    evaluateRooms(false)
}

def hubRestartHandler(evt) {
    logAction("SYSTEM REBOOT DETECTED: Clearing corrupted shutdown timers and forcing a hardware sync to correct physical device desyncs.")
    state.shutdownDelayActive = [] // Clear any timers lost in RAM during reboot
    evaluateRooms(true) // Force physical sync
}

def modeChangeHandler(evt) {
    logAction("Location mode changed to: ${evt.value}")
    evaluateRooms(false)
}

def evaluateRoomsLoop() {
    evaluateRooms(false)
}

def motionHandler(evt) {
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.motionHits) state.motionHits = [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def mDevs = settings["z${i}Motion"]
            if (mDevs && mDevs.find { it.id == evt.device.id }) {
                state.zoneLastActive["z${i}"] = now()
                
                if (isActive) {
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                    def hits = state.motionHits["z${i}"] ?: []
                    hits.add(now())
                    if (windowMs > 0) {
                        hits = hits.findAll { (now() - it) <= windowMs }
                    }
                    state.motionHits["z${i}"] = hits
                }
            }
        }
    }
    evaluateRooms(false)
}

def vibrationHandler(evt) {
    if (!state.vibeLastActive) state.vibeLastActive = [:]
    if (!state.vibeHits) state.vibeHits = [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def vDevs = settings["z${i}Vibration"]
            if (vDevs && vDevs.find { it.id == evt.device.id }) {
                state.vibeLastActive["z${i}"] = now()
                
                if (isActive) {
                    def windowMs = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                    def hits = state.vibeHits["z${i}"] ?: []
                    hits.add(now())
                    if (windowMs > 0) {
                        hits = hits.findAll { (now() - it) <= windowMs }
                    }
                    state.vibeHits["z${i}"] = hits
                }
            }
        }
    }
    evaluateRooms(false)
}

def sensorHandler(evt) {
    if (evt.name == "switch" && evt.value == "on") {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}OverrideSwitch"]?.id == evt.device.id) {
                if (!state.zoneLastActive) state.zoneLastActive = [:]
                state.zoneLastActive["z${i}"] = now() 
            }
        }
    }
    runInMillis(1000, "evaluateRoomsLoop")
}

def getRoomOccupancyState(roomId) {
    def isOccupied = false
    def mDevs = settings["z${roomId}Motion"]
    def vDevs = settings["z${roomId}Vibration"]
    def pDevs = settings["z${roomId}Presence"]
    def oSwitch = settings["z${roomId}OverrideSwitch"]
    def pMonitor = settings["z${roomId}PowerMonitor"]
    
    // 1. Check Active Wattage Failsafe (Ultimate Override)
    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
        if (currentDraw > safeThresh) {
            // Keep timers refreshed so it doesn't instantly die when power drops
            if (!state.zoneLastActive) state.zoneLastActive = [:]
            state.zoneLastActive["z${roomId}"] = now()
            return true
        }
    }
    
    def isHardActive = false
    if (pDevs && pDevs.any { it.currentValue("presence") == "present" }) isHardActive = true
    if (mDevs && mDevs.any { it.currentValue("motion") == "active" }) isHardActive = true
    if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isHardActive = true

    // 2. Check Virtual Override Switch (With Auto-Off Logic)
    if (oSwitch && oSwitch.currentValue("switch") == "on") {
        def oTimeout = settings["z${roomId}OverrideTimeout"]
        if (oTimeout && oTimeout > 0) {
            if (!isHardActive) {
                def lastM = state.zoneLastActive ? state.zoneLastActive["z${roomId}"] : 0
                def lastV = state.vibeLastActive ? state.vibeLastActive["z${roomId}"] : 0
                def maxLast = Math.max(lastM ?: 0, lastV ?: 0)
                
                if (maxLast == 0) {
                    state.zoneLastActive["z${roomId}"] = now()
                    maxLast = now()
                }
                
                if ((now() - maxLast) > (oTimeout * 60000)) {
                    logAction("${settings["z${roomId}Name"]}: Virtual Override Switch timed out due to inactivity. Turning OFF.")
                    oSwitch.off()
                } else {
                    return true
                }
            } else {
                return true 
            }
        } else {
            return true 
        }
    }
    
    // 3. Check mmWave Presence (Instant Hard Presence)
    if (pDevs && pDevs.any { it.currentValue("presence") == "present" }) return true
    
    def wasAlreadyOccupied = (state.currentRoomStates["z${roomId}"] == "occupied")
    
    // 4. Check Vibration with Hit Counter
    if (vDevs) {
        def vTimeoutMs = (settings["z${roomId}VibeTimeout"] ?: 5) * 60000
        def vLastActive = state.vibeLastActive ? state.vibeLastActive["z${roomId}"] : null
        
        def windowMs = (settings["z${roomId}VibeActivationWindow"] ?: 1) * 60000
        def reqHits = settings["z${roomId}VibeActivationHits"] ?: 1
        def hits = state.vibeHits ? (state.vibeHits["z${roomId}"] ?: []) : []
        if (windowMs > 0) hits = hits.findAll { (now() - it) <= windowMs }
        
        if (hits.size() >= reqHits || (wasAlreadyOccupied && vDevs.any { it.currentValue("acceleration") == "active" })) {
            isOccupied = true
        } else if (wasAlreadyOccupied && vLastActive && (now() - vLastActive) < vTimeoutMs) {
            isOccupied = true
        }
    }
    
    // 5. Check Motion with Hit Counter
    if (mDevs && !isOccupied) { 
        def mTimeoutMs = (settings["z${roomId}Timeout"] ?: 15) * 60000
        def mLastActive = state.zoneLastActive ? state.zoneLastActive["z${roomId}"] : null
        
        def windowMs = (settings["z${roomId}MotionActivationWindow"] ?: 1) * 60000
        def reqHits = settings["z${roomId}MotionActivationHits"] ?: 1
        def hits = state.motionHits ? (state.motionHits["z${roomId}"] ?: []) : []
        if (windowMs > 0) hits = hits.findAll { (now() - it) <= windowMs }
        
        if (hits.size() >= reqHits || (wasAlreadyOccupied && mDevs.any { it.currentValue("motion") == "active" })) {
            isOccupied = true
        } else if (wasAlreadyOccupied && mLastActive && (now() - mLastActive) < mTimeoutMs) {
            isOccupied = true
        }
    }
    
    return isOccupied
}

def getRoomRestrictionReason(roomId) {
    def currentMode = location.mode
    if (restrictedModes && (restrictedModes as List).contains(currentMode)) {
        return "Mode"
    }
    
    def activeDays = settings["z${roomId}ActiveDays"]
    if (activeDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        def day = df.format(new Date())
        if (!activeDays.contains(day)) return "Day"
    }
    
    def startTime = settings["z${roomId}StartTime"]
    def endTime = settings["z${roomId}EndTime"]
    if (startTime && endTime) {
        def currTime = now()
        def start = timeToday(startTime, location.timeZone).time
        def end = timeToday(endTime, location.timeZone).time
        
        def isTimeActive = false
        if (start <= end) {
             isTimeActive = (currTime >= start && currTime <= end)
        } else {
            isTimeActive = (currTime >= start || currTime <= end)
        }
        if (!isTimeActive) return "Time"
    }
    
    return null
}

def evaluateRooms(boolean forceSync) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (!state.shutdownDelayActive) state.shutdownDelayActive = []

    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                stopSavingsTimer(i)
                state.currentRoomStates["z${i}"] = "restricted"
                continue
            }

            def isOccupied = getRoomOccupancyState(i)
            def currentState = state.currentRoomStates["z${i}"]
            def targetState = isOccupied ? "occupied" : "empty"
            
            // If the state changed naturally, OR if we called a Force Sync to fix hardware after a reboot
            if (currentState != targetState || forceSync) {
                state.currentRoomStates["z${i}"] = targetState
                
                if (targetState == "occupied") {
                    if (!forceSync) logAction("${zName} is now OCCUPIED. Powering ON devices.")
                    state.shutdownDelayActive.remove((Object)i) 
                    turnRoomDevicesOn(i)
                    stopSavingsTimer(i)
                } else {
                    if (!forceSync) logAction("${zName} is now EMPTY. Initiating shutdown sequence.")
                    initiateRoomShutdown(i)
                }
            }
        }
    }
}

def turnRoomDevicesOn(roomId) {
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) {
        hardDevs.each { dev ->
            if (dev.currentValue("switch") != "on") dev.on()
        }
    }
    
    def softDevs = settings["z${roomId}SoftKillDevices"]
    if (softDevs) {
        runIn(2, "executeSoftBoot", [data: [room: roomId], overwrite: false])
    }
}

def executeSoftBoot(data) {
    def roomId = data.room
    if (state.currentRoomStates["z${roomId}"] == "occupied") {
        def softDevs = settings["z${roomId}SoftKillDevices"]
        softDevs?.each { dev -> 
            if (dev.currentValue("switch") != "on") dev.on() 
        }
    }
}

def initiateRoomShutdown(roomId) {
    def softDevs = settings["z${roomId}SoftKillDevices"]
    def hardDevs = settings["z${roomId}Switches"]
    def delaySecs = settings["z${roomId}HardKillDelay"] ?: 0

    if (softDevs && softDevs.any { it.currentValue("switch") != "off" }) {
        logAction("Sending Graceful Shutdown commands to sensitive electronics in Room ${roomId}.")
        softDevs.each { dev -> 
            if (dev.currentValue("switch") != "off") dev.off() 
        }
        
        if (hardDevs && delaySecs > 0) {
            logAction("Waiting ${delaySecs} seconds for sensitive devices to shut down before cutting hard power.")
            
            if (!state.shutdownDelayActive) state.shutdownDelayActive = []
            if (!state.shutdownDelayActive.contains(roomId)) state.shutdownDelayActive.add(roomId)
            
            runIn(delaySecs, "executeHardKill", [data: [room: roomId], overwrite: false])
            return 
        } else if (hardDevs) {
            executeHardKill([room: roomId])
            return
        }
    } else {
        executeHardKill([room: roomId])
        return
    }
}

def executeHardKill(data) {
    def roomId = data.room
    
    if (state.currentRoomStates["z${roomId}"] != "empty") {
        logAction("Hard kill aborted. Room ${roomId} became occupied during the shutdown delay.")
        state.shutdownDelayActive.remove((Object)roomId)
        return
    }

    state.shutdownDelayActive.remove((Object)roomId)
    
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) {
        logAction("Cutting hard power to managed relays in Room ${roomId}.")
        hardDevs.each { dev ->
            if (dev.currentValue("switch") != "off") {
                dev.off()
            }
        }
    }
    
    startSavingsTimer(roomId)
}

// === ROI SAVINGS TRACKING ===
def startSavingsTimer(roomId) {
    if (!state.roomStats) state.roomStats = [:]
    if (!state.roomStats["z${roomId}"]) state.roomStats["z${roomId}"] = [totalSecondsOff: 0, unoccupiedSince: null]
    
    if (state.roomStats["z${roomId}"].unoccupiedSince == null) {
        state.roomStats["z${roomId}"].unoccupiedSince = now()
    }
}

def stopSavingsTimer(roomId) {
    if (state.roomStats && state.roomStats["z${roomId}"] && state.roomStats["z${roomId}"].unoccupiedSince != null) {
        def since = state.roomStats["z${roomId}"].unoccupiedSince
        def elapsedSecs = ((now() - since) / 1000).toLong()
        
        state.roomStats["z${roomId}"].totalSecondsOff += elapsedSecs
        state.roomStats["z${roomId}"].unoccupiedSince = null
    }
}

def resetAllSavings() {
    logAction("MANUAL OVERRIDE: Resetting all ROI Savings Data to zero.")
    for (int i = 1; i <= 12; i++) {
        state.roomStats["z${i}"] = [totalSecondsOff: 0, unoccupiedSince: (state.currentRoomStates["z${i}"] == "empty" ? now() : null)]
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
