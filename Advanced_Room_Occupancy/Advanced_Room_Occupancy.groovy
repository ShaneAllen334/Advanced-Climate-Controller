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
    page(name: "roomConfigPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Room Occupancy</b>", install: true, uninstall: true) {
        
        section("<b>Live Occupancy & ROI Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your configured rooms, active triggers, power profiles, and exact financial savings.</div>"
            
            def hasZones = false
            def rate = kwhCost ?: 0.13
            def totalAppSavings = 0.0

            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room</th><th style='padding: 8px;'>State & Timers</th><th style='padding: 8px;'>Sensors</th><th style='padding: 8px;'>Devices & Power</th><th style='padding: 8px;'>Est. Savings</th></tr>"

            for (int i = 1; i <= 12; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zKey = "z${i}".toString()
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    
                    def isOccupied = getRoomOccupancyState(i)
                    def mDevs = settings["z${i}Motion"]
                    def vDevs = settings["z${i}Vibration"]
                    def pDevs = settings["z${i}Presence"]
                    def devs = settings["z${i}Switches"]
                    def softDevs = settings["z${i}SoftKillDevices"]
                    def pMonitor = settings["z${i}PowerMonitor"]
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    
                    def onTriggers = settings["z${i}TurnOnTriggers"] ?: []
                    def offTriggers = settings["z${i}TurnOffTriggers"] ?: []

                    def showPresence = onTriggers.contains("Presence Sensor")
                    def showMotion = onTriggers.contains("Motion Hit Count") || onTriggers.contains("Continuous Motion") || offTriggers.contains("Motion/Vibe Timeout")
                    def showVibe = onTriggers.contains("Vibration") || offTriggers.contains("Motion/Vibe Timeout")
                    def showSwitch = onTriggers.contains("Virtual Switch") || offTriggers.contains("Virtual Switch OFF")
                    
                    def statusAdditions = []
                    def sensorDetails = []
                    def maxRemainingMs = 0
                    def isHardActive = false

                    // Check Presence Sensors
                    if (pDevs && showPresence) {
                        pDevs.each { dev ->
                            def val = dev.currentValue("presence")
                            def color = val == "present" ? "blue" : "gray"
                            def fw = val == "present" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>👤 ${dev.displayName}: ${val}</span>"
                            if (val == "present") isHardActive = true
                        }
                    }

                    // Check Motion Sensors & Timers
                    if (mDevs && showMotion) {
                        mDevs.each { dev ->
                            def val = dev.currentValue("motion")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>🏃 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def mTimeout = (settings["z${i}Timeout"] ?: 15) * 60000
                        def mLast = state.zoneLastActive ? state.zoneLastActive[zKey] : null
                        def mReqHits = settings["z${i}MotionActivationHits"] ?: 1
                        def mHits = state.motionHitCount ? (state.motionHitCount[zKey] ?: 0) : 0
                        
                        if (offTriggers.contains("Motion/Vibe Timeout")) {
                            if (mLast && !mDevs.any{it.currentValue("motion") == "active"}) {
                                def mLeft = mTimeout - (now() - mLast)
                                if (mLeft > maxRemainingMs) maxRemainingMs = mLeft
                            }
                        }
                        
                        // Hit Counter Window Timer
                        if (onTriggers.contains("Motion Hit Count") && mReqHits > 1 && !isOccupied && mHits > 0 && mLast) {
                            def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                            def windowLeft = (mLast + windowMs) - now()
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Motion Hits: ${mHits}/${mReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                        
                        // Continuous Motion Display
                        if (onTriggers.contains("Continuous Motion")) {
                            def activeSince = state.motionActiveSince ? state.motionActiveSince[zKey] : null
                            if (!isOccupied && activeSince) {
                                def reqMins = settings["z${i}MotionContinuousDuration"] ?: 3
                                def left = (activeSince + (reqMins * 60000)) - now()
                                if (left > 0) {
                                    def secsLeft = Math.ceil(left / 1000).toInteger()
                                    statusAdditions << "<span style='color:teal; font-size:11px;'>Continuous Motion: ${secsLeft}s left</span>"
                                }
                            }
                        }
                    }

                    // Check Vibration Sensors & Timers
                    if (vDevs && showVibe) {
                        vDevs.each { dev ->
                            def val = dev.currentValue("acceleration")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>📳 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def vTimeout = (settings["z${i}VibeTimeout"] ?: 5) * 60000
                        def vLast = state.vibeLastActive ? state.vibeLastActive[zKey] : null
                        def vReqHits = settings["z${i}VibeActivationHits"] ?: 1
                        def vHits = state.vibeHitCount ? (state.vibeHitCount[zKey] ?: 0) : 0

                        if (offTriggers.contains("Motion/Vibe Timeout")) {
                            if (vLast && !vDevs.any{it.currentValue("acceleration") == "active"}) {
                                def vLeft = vTimeout - (now() - vLast)
                                if (vLeft > maxRemainingMs) maxRemainingMs = vLeft
                            }
                        }
                        
                        // Hit Counter Window Timer
                        if (onTriggers.contains("Vibration") && vReqHits > 1 && !isOccupied && vHits > 0 && vLast) {
                            def windowMs = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                            def windowLeft = (vLast + windowMs) - now()
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Vibe Hits: ${vHits}/${vReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                    }
                    
                    if (oSwitch && showSwitch && oSwitch.currentValue("switch") == "on") {
                        sensorDetails << "<span style='color:orange; font-weight:bold; font-size:11px;'>🔘 Override Switch: ON</span>"
                    }

                    // Check Wattage Failsafe
                    if (pMonitor) {
                        def currentDraw = pMonitor.currentValue("power") ?: 0.0
                        def safeThresh = settings["z${i}ActiveWattageThreshold"] ?: 15.0
                        if (currentDraw > safeThresh) {
                            statusAdditions << "<span style='color:red; font-size:11px; font-weight:bold;'>Power Lock Active (${currentDraw}W)</span>"
                            isHardActive = true
                        }
                    }

                    // --- Override Switch Timeout Display ---
                    if (oSwitch && oSwitch.currentValue("switch") == "on" && onTriggers.contains("Virtual Switch")) {
                        def oTimeout = settings["z${i}OverrideTimeout"]
                        if (oTimeout && oTimeout > 0 && !isHardActive) {
                            def lastM = state.zoneLastActive ? state.zoneLastActive[zKey] : 0
                            def lastV = state.vibeLastActive ? state.vibeLastActive[zKey] : 0
                            def maxLast = Math.max(lastM ?: 0, lastV ?: 0)
                            if (maxLast > 0) {
                                def timeLeft = (oTimeout * 60000) - (now() - maxLast)
                                if (timeLeft > 0) {
                                    def minsLeft = Math.ceil(timeLeft / 60000).toInteger()
                                    statusAdditions << "<span style='color:blue; font-size:11px;'>Override Auto-Off: ~${minsLeft}m</span>"
                                }
                            }
                        }
                    }

                    // Precision Empty Countdown Timer
                    if (isOccupied && !isHardActive && maxRemainingMs > 0) {
                        def mins = Math.floor(maxRemainingMs / 60000).toInteger()
                        def secs = Math.floor((maxRemainingMs % 60000) / 1000).toInteger()
                        def timeStr = mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
                        statusAdditions << "<span style='color:#888; font-size:11px;'>Unoccupied in: ${timeStr}</span>"
                    }

                    if (!isOccupied && state.shutdownDelayActive?.contains(i)) {
                         statusAdditions << "<span style='color:orange; font-size:11px;'>Safe Shutdown Sequence...</span>"
                    }

                    def remainingMinsDisplay = statusAdditions ? "<br>" + statusAdditions.join("<br>") : ""
                    def restrictionReason = getRoomRestrictionReason(i)
                    def stateColor = restrictionReason ? "orange" : (isOccupied ? "green" : "black")
                    def stateLabel = restrictionReason ? "PAUSED (${restrictionReason})" : (isOccupied ? "OCCUPIED" : "EMPTY")
                    
                    // Display Reason for State
                    def reasonText = state.roomReasons ? (state.roomReasons[zKey] ?: "Unknown") : "Unknown"
                    if (restrictionReason) reasonText = restrictionReason
                    def reasonColor = isOccupied ? "#0066cc" : "#888888"
                    def reasonDisplay = "<br><span style='font-size:11px; color:${reasonColor};'><i>Reason: ${reasonText}</i></span>"

                    def isOccupiedDisplay = "<b><span style='color:${stateColor};'>${stateLabel}</span></b>${reasonDisplay}${remainingMinsDisplay}"
                    
                    def sensorListDisplay = sensorDetails ? sensorDetails.join("<br>") : "<span style='color:gray; font-size:11px;'>None Configured/Active</span>"

                    // --- Calculate Dynamic Wattage & Devices ---
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
                            devNames << "<span style='font-size:11px;'>⚡ ${dev.displayName} (${devState})</span>"
                        }
                    }
                    
                    if (softDevs) {
                        softDevs.each { dev ->
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "<span style='font-size:11px;'>💻 ${dev.displayName} (${devState})</span>"
                        }
                    }
                    
                    def devListDisplay = devNames ? devNames.join("<br>") : "<span style='color:gray; font-size:11px;'>None Configured</span>"
                    def powerDisplay = "<span style='font-size:11px;'><b>Active:</b> ${totalActive}W<br><b>Standby:</b> ${totalStandby}W</span>"
                    def fullDeviceColumn = "${devListDisplay}<br><br>${powerDisplay}"

                    // --- Savings Calculation ---
                    def stats = state.roomStats ? state.roomStats[zKey] : [totalSecondsOff: 0, unoccupiedSince: null]
                    def secondsOff = stats?.totalSecondsOff ?: 0
                    if (stats?.unoccupiedSince != null && !restrictionReason) {
                        secondsOff += ((now() - stats.unoccupiedSince) / 1000).toLong()
                    }
                    
                    def savedHours = secondsOff / 3600.0
                    def wastedKw = (totalActive + totalStandby) / 1000.0
                    def roomSavings = savedHours * wastedKw * rate
                    totalAppSavings += roomSavings
                    
                    def formattedSavings = String.format('$%.2f', roomSavings)

                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${zName}</b></td><td style='padding: 8px;'>${isOccupiedDisplay}</td><td style='padding: 8px;'>${sensorListDisplay}</td><td style='padding: 8px;'>${fullDeviceColumn}</td><td style='padding: 8px; color:#008800; font-weight:bold;'>${formattedSavings}</td></tr>"
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
                paragraph "<i>No rooms configured yet. Click a room below to begin.</i>"
            }
        }

        section("<b>Room Management</b>") {
            paragraph "Click a room below to configure its sensors, smart plugs, timeouts, and rules."
            for (int i = 1; i <= 12; i++) {
                def zName = settings["z${i}Name"] ?: "Room ${i}"
                def statusTag = settings["enableZ${i}"] ? " <span style='color:green;'>(Active)</span>" : ""
                href(name: "roomPage${i}", page: "roomConfigPage", params: [roomId: i], title: "▶ Configure ${zName}${statusTag}", description: "")
            }
        }

        section("<b>Global Configuration & Restrictions</b>") {
            input "kwhCost", "decimal", title: 'Electricity Cost ($ per kWh)', required: true, defaultValue: 0.13
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<b>Global Mode Overrides</b>"
            input "restrictedModes", "mode", title: "Restricted Modes (Pause all occupancy rules)", multiple: true, required: false
            input "forceOffModes", "mode", title: "Force OFF Modes (Immediately turns off all rooms when entering these modes)", multiple: true, required: false
            
            paragraph "<b>Manual System Overrides</b>"
            
            input "forceAllOccupied", "bool", title: "⚡ Force ALL Rooms OCCUPIED", defaultValue: false, submitOnChange: true
            if (settings["forceAllOccupied"]) {
                forceAllRoomsOccupied()
                app.updateSetting("forceAllOccupied", false)
            }
            
            input "forceAllEmpty", "bool", title: "🛑 Force ALL Rooms EMPTY", defaultValue: false, submitOnChange: true
            if (settings["forceAllEmpty"]) {
                forceAllRoomsEmpty()
                app.updateSetting("forceAllEmpty", false)
            }
            
            input "forceSync", "bool", title: "🔄 Manually Force Hardware Sync", defaultValue: false, submitOnChange: true
            if (settings["forceSync"]) {
                logAction("MANUAL OVERRIDE: Forcing hardware sync...")
                evaluateRooms(true)
                app.updateSetting("forceSync", false)
            }
            
            input "resetSavings", "bool", title: "🗑️ Reset All Savings Data to Zero", defaultValue: false, submitOnChange: true
            if (settings["resetSavings"]) {
                resetAllSavings()
                app.updateSetting("resetSavings", false)
            }
        }

        section("<b>Action History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
            }
        }
    }
}

def roomConfigPage(params) {
    if (params?.roomId) state.editingRoom = params.roomId
    def i = state.editingRoom ?: 1
    def zName = settings["z${i}Name"] ?: "Room ${i}"

    dynamicPage(name: "roomConfigPage", title: "<b>Configuration: ${zName}</b>", install: false, uninstall: false) {
        section("<b>▶ Setup</b>") {
            input "enableZ${i}", "bool", title: "<b>Enable this Room</b>", submitOnChange: true
            
            if (settings["enableZ${i}"]) {
                input "z${i}Name", "text", title: "Room Name", required: false, defaultValue: "Room ${i}", submitOnChange: true
                
                paragraph "<b>Triggers & Sensors</b>"
                input "z${i}TurnOnTriggers", "enum", title: "Turn ON Triggers (What makes the room occupied?)", options: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration", "Presence Sensor"], multiple: true, required: true, defaultValue: ["Virtual Switch", "Motion Hit Count", "Vibration", "Presence Sensor"], submitOnChange: true
                input "z${i}TurnOffTriggers", "enum", title: "Turn OFF Triggers (What makes the room empty?)", options: ["Motion/Vibe Timeout", "Virtual Switch OFF"], multiple: true, required: true, defaultValue: ["Motion/Vibe Timeout", "Virtual Switch OFF"], submitOnChange: true

                input "z${i}OverrideSwitch", "capability.switch", title: "Virtual Override Switch", required: false, submitOnChange: true
                if (settings["z${i}OverrideSwitch"] && settings["z${i}TurnOnTriggers"]?.contains("Virtual Switch")) {
                    input "z${i}OverrideTimeout", "number", title: "↳ Auto-Off Timeout (Minutes of NO movement before turning switch OFF)", required: false, defaultValue: 120
                }
                
                input "z${i}Button", "capability.pushableButton", title: "Physical Button Link (Toggles Override Switch)", required: false, submitOnChange: true
                if (settings["z${i}Button"]) {
                    input "z${i}ButtonNumber", "number", title: "↳ Button Number", required: true, defaultValue: 1
                }
                
                input "z${i}Motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Motion"]) {
                    if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count")) {
                        input "z${i}MotionActivationWindow", "number", title: "↳ Hit Count Window (Minutes)", required: true, defaultValue: 1
                        input "z${i}MotionActivationHits", "number", title: "↳ Required Active Hits", required: true, defaultValue: 1
                    }
                    if (settings["z${i}TurnOnTriggers"]?.contains("Continuous Motion")) {
                        input "z${i}MotionContinuousDuration", "number", title: "↳ Continuous Motion Duration (Minutes before turning ON)", required: true, defaultValue: 3
                    }
                    if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                        input "z${i}Timeout", "number", title: "↳ Motion Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 15
                    }
                }
                
                input "z${i}Vibration", "capability.accelerationSensor", title: "Vibration Sensors (e.g. Chair/Bed)", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Vibration"]) {
                    if (settings["z${i}TurnOnTriggers"]?.contains("Vibration")) {
                        input "z${i}VibeActivationWindow", "number", title: "↳ Activation Window (Minutes to count hits)", required: true, defaultValue: 1
                        input "z${i}VibeActivationHits", "number", title: "↳ Required Active Hits to trigger Occupied", required: true, defaultValue: 1
                    }
                    if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                        input "z${i}VibeTimeout", "number", title: "↳ Vibration Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 5
                    }
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
                
                paragraph "<b>Room Scheduling & Mode Restrictions</b>"
                input "z${i}OperatingModes", "mode", title: "Active Modes (Leave blank for all modes)", multiple: true, required: false
                input "z${i}ActiveDays", "enum", title: "Active Days (Leave blank for all days)", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "z${i}StartTime", "time", title: "Active Start Time (Optional)", required: false
                input "z${i}EndTime", "time", title: "Active End Time (Optional)", required: false
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
    if (!state.motionHitCount) state.motionHitCount = [:]
    if (!state.vibeHitCount) state.vibeHitCount = [:]
    if (!state.motionActiveSince) state.motionActiveSince = [:]
    if (!state.roomStats) state.roomStats = [:]
    if (!state.currentRoomStates) state.currentRoomStates = [:]
    if (!state.shutdownDelayActive) state.shutdownDelayActive = []
    if (!state.roomReasons) state.roomReasons = [:]
    
    for (int i = 1; i <= 12; i++) {
        def zKey = "z${i}".toString()
        if (!state.roomStats[zKey]) state.roomStats[zKey] = [totalSecondsOff: 0, unoccupiedSince: null]
        if (!state.currentRoomStates[zKey]) state.currentRoomStates[zKey] = "unknown"
        state.motionHitCount[zKey] = 0
        state.vibeHitCount[zKey] = 0
    }
    
    // Core Subscriptions
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", sensorHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}OverrideSwitch"]) subscribe(settings["z${i}OverrideSwitch"], "switch", sensorHandler)
            if (settings["z${i}Button"]) subscribe(settings["z${i}Button"], "pushed", buttonHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}Vibration"]) subscribe(settings["z${i}Vibration"], "acceleration", vibrationHandler)
            if (settings["z${i}Presence"]) subscribe(settings["z${i}Presence"], "presence", sensorHandler)
        }
    }
    
    // Explicitly quoted schedule to prevent Hubitat String.call() evaluation errors
    runEvery5Minutes("evaluateRooms")
    
    logAction("Advanced Room Occupancy Initialized (Event-Driven Mode Active).")
    evaluateRooms(false)
}

def hubRestartHandler(evt) {
    logAction("SYSTEM REBOOT DETECTED: Clearing corrupted shutdown timers and forcing a hardware sync to correct physical device desyncs.")
    state.shutdownDelayActive = [] 
    evaluateRooms(true) 
}

def modeChangeHandler(evt) {
    logAction("Location mode changed to: ${evt.value}")
    
    def isForceOff = forceOffModes && (forceOffModes as List).contains(evt.value)
    if (isForceOff) {
        logAction("GLOBAL FORCE OFF TRIGGERED: Mode changed to ${evt.value}.")
        for (int i = 1; i <= 12; i++) {
            def zKey = "z${i}".toString()
            if (settings["enableZ${i}"] && state.currentRoomStates[zKey] != "empty") {
                def tStates = state.currentRoomStates ? state.currentRoomStates.clone() : [:]
                tStates[zKey] = "empty"
                state.currentRoomStates = tStates
                initiateRoomShutdown(i)
            }
        }
    }
    
    evaluateRooms(false)
}

def buttonHandler(evt) {
    def btnPushed = evt.value
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Button"]?.id == evt.device.id) {
            def configuredBtn = settings["z${i}ButtonNumber"]?.toString()
            if (configuredBtn == btnPushed?.toString()) {
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch) {
                    if (oSwitch.currentValue("switch") == "on") {
                        logAction("Button Link: Toggling Override Switch OFF for Room ${i}")
                        oSwitch.off()
                    } else {
                        logAction("Button Link: Toggling Override Switch ON for Room ${i}")
                        oSwitch.on()
                    }
                }
            }
        }
    }
}

def motionHandler(evt) {
    def tZoneLast = state.zoneLastActive ? state.zoneLastActive.clone() : [:]
    def tMotionHit = state.motionHitCount ? state.motionHitCount.clone() : [:]
    def tMotionSince = state.motionActiveSince ? state.motionActiveSince.clone() : [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zKey = "z${i}".toString()
            def mDevs = settings["z${i}Motion"]
            if (mDevs && mDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    tZoneLast[zKey] = now()
                    
                    if (!tMotionSince[zKey]) {
                        tMotionSince[zKey] = now()
                        def contMins = settings["z${i}MotionContinuousDuration"] ?: 3
                        runIn(contMins * 60, "evalR${i}")
                    }
                    
                    def count = (tMotionHit[zKey] ?: 0) + 1
                    tMotionHit[zKey] = count
                    
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                    if (count == 1 && windowMs > 0) {
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetMotionZ${i}")
                    }
                    
                    // Cancel any pending timeout shutdown for this specific room
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}") // re-eval quickly
                } else {
                    // Only schedule the empty timeout if ALL motion sensors in the room are inactive
                    if (!mDevs.any { it.currentValue("motion") == "active" }) {
                        tMotionSince.remove(zKey)
                        
                        if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                            def timeoutSecs = (settings["z${i}Timeout"] ?: 15) * 60
                            runIn(timeoutSecs, "evalR${i}")
                        } else {
                            runIn(1, "evalR${i}")
                        }
                    }
                }
            }
        }
    }
    
    state.zoneLastActive = tZoneLast
    state.motionHitCount = tMotionHit
    state.motionActiveSince = tMotionSince
}

def vibrationHandler(evt) {
    def tVibeLast = state.vibeLastActive ? state.vibeLastActive.clone() : [:]
    def tVibeHit = state.vibeHitCount ? state.vibeHitCount.clone() : [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zKey = "z${i}".toString()
            def vDevs = settings["z${i}Vibration"]
            if (vDevs && vDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    tVibeLast[zKey] = now()
                    
                    def count = (tVibeHit[zKey] ?: 0) + 1
                    tVibeHit[zKey] = count
                    
                    def windowMs = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                    if (count == 1 && windowMs > 0) {
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetVibeZ${i}")
                    }
                    
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}")
                } else {
                    if (!vDevs.any { it.currentValue("acceleration") == "active" }) {
                        if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                            def timeoutSecs = (settings["z${i}VibeTimeout"] ?: 5) * 60
                            runIn(timeoutSecs, "evalR${i}")
                        } else {
                            runIn(1, "evalR${i}")
                        }
                    }
                }
            }
        }
    }
    
    state.vibeLastActive = tVibeLast
    state.vibeHitCount = tVibeHit
}

def sensorHandler(evt) {
    if (evt.name == "switch") {
        def tZoneLast = state.zoneLastActive ? state.zoneLastActive.clone() : [:]
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}OverrideSwitch"]?.id == evt.device.id) {
                def zKey = "z${i}".toString()
                if (evt.value == "on") {
                    tZoneLast[zKey] = now() 
                }
            }
        }
        state.zoneLastActive = tZoneLast
    }
    runIn(1, "evalR_All")
}

def getRoomOccupancyState(roomId) {
    def zKey = "z${roomId}".toString()
    
    def isOccupied = false
    def reason = "All Conditions Cleared"
    def mDevs = settings["z${roomId}Motion"]
    def vDevs = settings["z${roomId}Vibration"]
    def pDevs = settings["z${roomId}Presence"]
    def oSwitch = settings["z${roomId}OverrideSwitch"]
    def pMonitor = settings["z${roomId}PowerMonitor"]
    
    def onTriggers = settings["z${roomId}TurnOnTriggers"] ?: []
    def offTriggers = settings["z${roomId}TurnOffTriggers"] ?: []
    
    def showPresence = onTriggers.contains("Presence Sensor")
    def showMotion = onTriggers.contains("Motion Hit Count") || onTriggers.contains("Continuous Motion") || offTriggers.contains("Motion/Vibe Timeout")
    def showVibe = onTriggers.contains("Vibration") || offTriggers.contains("Motion/Vibe Timeout")
    
    // 1. Check Active Wattage Failsafe (Ultimate Override)
    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
        if (currentDraw > safeThresh) {
            def tZoneLast = state.zoneLastActive ? state.zoneLastActive.clone() : [:]
            def tReasons = state.roomReasons ? state.roomReasons.clone() : [:]
            
            tZoneLast[zKey] = now()
            tReasons[zKey] = "Power Lock Active (${currentDraw}W)"
            
            state.zoneLastActive = tZoneLast
            state.roomReasons = tReasons
            return true
        }
    }
    
    def isHardActive = false
    if (showPresence && pDevs && pDevs.any { it.currentValue("presence") == "present" }) isHardActive = true
    if (showMotion && mDevs && mDevs.any { it.currentValue("motion") == "active" }) isHardActive = true
    if (showVibe && vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isHardActive = true

    def wasAlreadyOccupied = (state.currentRoomStates && state.currentRoomStates[zKey] == "occupied")

    // --- EVALUATE TURN ON TRIGGERS ---
    
    // A. Presence
    if (onTriggers.contains("Presence Sensor") && pDevs && pDevs.any { it.currentValue("presence") == "present" }) {
        isOccupied = true
        reason = "Presence Detected"
    }
    
    // B. Virtual Switch ON
    else if (!isOccupied && onTriggers.contains("Virtual Switch") && oSwitch && oSwitch.currentValue("switch") == "on") {
        def oTimeout = settings["z${roomId}OverrideTimeout"]
        if (oTimeout && oTimeout > 0 && !isHardActive) {
            def maxLast = Math.max(state.zoneLastActive ? (state.zoneLastActive[zKey] ?: 0) : 0, state.vibeLastActive ? (state.vibeLastActive[zKey] ?: 0) : 0)
            if (maxLast == 0) maxLast = now()
            if ((now() - maxLast) > (oTimeout * 60000)) {
                logAction("${settings["z${roomId}Name"]}: Virtual Override Switch timed out due to inactivity. Turning OFF.")
                oSwitch.off()
            } else {
                isOccupied = true
                reason = "Virtual Switch Enabled"
            }
        } else {
            isOccupied = true 
            reason = "Virtual Switch Enabled"
        }
    }

    // C. Continuous Motion Duration (WITH FAILSAFE)
    else if (!isOccupied && onTriggers.contains("Continuous Motion") && mDevs) {
        def activeSince = state.motionActiveSince ? state.motionActiveSince[zKey] : null
        def reqMins = settings["z${roomId}MotionContinuousDuration"] ?: 3
        def isActuallyMoving = mDevs.any { it.currentValue("motion") == "active" }
        
        if (activeSince && isActuallyMoving && (now() - activeSince) >= (reqMins * 60000)) {
            isOccupied = true
            reason = "Motion Duration Reached"
        } else if (activeSince && !isActuallyMoving) {
            // Memory trapped a ghost timestamp. Force clear it to DB immediately.
            def tSince = state.motionActiveSince ? state.motionActiveSince.clone() : [:]
            tSince.remove(zKey)
            state.motionActiveSince = tSince
        }
    }

    // D. Motion Hit Count
    else if (!isOccupied && onTriggers.contains("Motion Hit Count") && mDevs) {
        def reqHits = settings["z${roomId}MotionActivationHits"] ?: 1
        def hitCount = state.motionHitCount ? (state.motionHitCount[zKey] ?: 0) : 0
        if (hitCount >= reqHits) {
            isOccupied = true
            reason = "Motion Count Reached"
        }
    }

    // E. Vibration Hit Count
    else if (!isOccupied && onTriggers.contains("Vibration") && vDevs) {
        def reqHits = settings["z${roomId}VibeActivationHits"] ?: 1
        def hitCount = state.vibeHitCount ? (state.vibeHitCount[zKey] ?: 0) : 0
        if (hitCount >= reqHits) {
            isOccupied = true
            reason = "Vibration Detected"
        }
    }

    // --- EVALUATE TURN OFF/MAINTAIN CONDITIONS ---
    if (wasAlreadyOccupied && !isOccupied) {
        def holdOccupied = false
        def holdReason = ""
        
        if (offTriggers.contains("Motion/Vibe Timeout")) {
            def mTimeoutMs = (settings["z${roomId}Timeout"] ?: 15) * 60000
            def vTimeoutMs = (settings["z${roomId}VibeTimeout"] ?: 5) * 60000
            def mLastActive = state.zoneLastActive ? (state.zoneLastActive[zKey] ?: 0) : 0
            def vLastActive = state.vibeLastActive ? (state.vibeLastActive[zKey] ?: 0) : 0
            
            if (mDevs && (mDevs.any { it.currentValue("motion") == "active" } || (mLastActive && (now() - mLastActive) < mTimeoutMs))) {
                holdOccupied = true
                holdReason = "Motion Timer Active"
            }
            if (!holdOccupied && vDevs && (vDevs.any { it.currentValue("acceleration") == "active" } || (vLastActive && (now() - vLastActive) < vTimeoutMs))) {
                holdOccupied = true
                holdReason = "Vibration Timer Active"
            }
        }
        
        if (!holdOccupied && offTriggers.contains("Virtual Switch OFF")) {
            if (oSwitch && oSwitch.currentValue("switch") == "on") {
                holdOccupied = true
                holdReason = "Virtual Switch Active"
            }
        }
        
        if (holdOccupied) {
            isOccupied = true
            reason = holdReason
        } else {
            reason = "All Conditions Cleared"
        }
    }
    
    // Force DB update of reason map
    def tReasons = state.roomReasons ? state.roomReasons.clone() : [:]
    tReasons[zKey] = reason
    state.roomReasons = tReasons
    
    return isOccupied
}

def getRoomRestrictionReason(roomId) {
    def currentMode = location.mode
    
    if (forceOffModes && (forceOffModes as List).contains(currentMode)) {
        return "Force Off Mode"
    }
    
    if (restrictedModes && (restrictedModes as List).contains(currentMode)) {
        return "Global Restricted Mode"
    }
    
    def roomModes = settings["z${roomId}OperatingModes"]
    if (roomModes && !(roomModes as List).contains(currentMode)) {
        return "Room Restricted Mode"
    }
    
    def activeDays = settings["z${roomId}ActiveDays"]
    if (activeDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        def day = df.format(new Date())
        if (!activeDays.contains(day)) return "Restricted Day"
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
        if (!isTimeActive) return "Restricted Time"
    }
    
    return null
}

def evaluateRooms(boolean forceSync = false) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    // Setup Clones to guarantee Database Updates
    def tMotionSince = state.motionActiveSince ? state.motionActiveSince.clone() : [:]
    def tZoneLast = state.zoneLastActive ? state.zoneLastActive.clone() : [:]
    def tVibeLast = state.vibeLastActive ? state.vibeLastActive.clone() : [:]
    def tMotionHit = state.motionHitCount ? state.motionHitCount.clone() : [:]
    def tVibeHit = state.vibeHitCount ? state.vibeHitCount.clone() : [:]
    def tStates = state.currentRoomStates ? state.currentRoomStates.clone() : [:]
    def tReasons = state.roomReasons ? state.roomReasons.clone() : [:]

    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zKey = "z${i}".toString()
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def mDevs = settings["z${i}Motion"]
            
            // Failsafe Sweeper
            if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
                if (tMotionSince[zKey]) tMotionSince.remove(zKey)
            }
            
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                stopSavingsTimer(i)
                tStates[zKey] = "restricted"
                tReasons[zKey] = restriction
                
                // Deep-Clean Reset on Restricted Mode
                tMotionHit[zKey] = 0
                tVibeHit[zKey] = 0
                tZoneLast.remove(zKey)
                tVibeLast.remove(zKey)
                tMotionSince.remove(zKey)
                
                unschedule("evalR${i}")
                unschedule("resetMotionZ${i}")
                unschedule("resetVibeZ${i}")
                
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && oSwitch.currentValue("switch") == "on") {
                    oSwitch.off()
                }
                
                continue
            }

            // Sync clones to state so getRoomOccupancyState sees fresh data
            state.motionActiveSince = tMotionSince
            state.zoneLastActive = tZoneLast
            state.vibeLastActive = tVibeLast
            state.motionHitCount = tMotionHit
            state.vibeHitCount = tVibeHit

            def isOccupied = getRoomOccupancyState(i)
            def currentState = tStates[zKey]
            def targetState = isOccupied ? "occupied" : "empty"
            
            if (currentState != targetState || forceSync) {
                tStates[zKey] = targetState
                
                if (targetState == "occupied") {
                    if (!forceSync) logAction("${zName} is now OCCUPIED (${state.roomReasons[zKey]}). Powering ON devices.")
                    state.shutdownDelayActive?.remove((Object)i) 
                    turnRoomDevicesOn(i)
                    stopSavingsTimer(i)
                } else {
                    if (!forceSync) logAction("${zName} is now EMPTY (${state.roomReasons[zKey]}). Initiating shutdown sequence.")
                    initiateRoomShutdown(i)
                }
            }
        }
    }
    
    // Final Database Commit
    state.currentRoomStates = tStates
    state.roomReasons = state.roomReasons // Just forcing dirty flag
}

// === MANUAL SYSTEM OVERRIDES ===

def forceAllRoomsOccupied() {
    logAction("MANUAL OVERRIDE: Forcing ALL rooms to OCCUPIED.")
    def tStates = state.currentRoomStates ? state.currentRoomStates.clone() : [:]
    def tReasons = state.roomReasons ? state.roomReasons.clone() : [:]
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zKey = "z${i}".toString()
            tStates[zKey] = "occupied"
            tReasons[zKey] = "Global Force Occupied"
            state.shutdownDelayActive?.remove((Object)i)
            turnRoomDevicesOn(i)
            stopSavingsTimer(i)
        }
    }
    
    state.currentRoomStates = tStates
    state.roomReasons = tReasons
}

def forceAllRoomsEmpty() {
    logAction("MANUAL OVERRIDE: Forcing ALL rooms to EMPTY.")
    def tStates = state.currentRoomStates ? state.currentRoomStates.clone() : [:]
    def tReasons = state.roomReasons ? state.roomReasons.clone() : [:]
    def tMotionHit = state.motionHitCount ? state.motionHitCount.clone() : [:]
    def tVibeHit = state.vibeHitCount ? state.vibeHitCount.clone() : [:]
    def tZoneLast = state.zoneLastActive ? state.zoneLastActive.clone() : [:]
    def tVibeLast = state.vibeLastActive ? state.vibeLastActive.clone() : [:]
    def tMotionSince = state.motionActiveSince ? state.motionActiveSince.clone() : [:]
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zKey = "z${i}".toString()
            tStates[zKey] = "empty"
            tReasons[zKey] = "Global Force Empty"
            
            // Deep purge of triggers so the room doesn't instantly snap back on
            tMotionHit[zKey] = 0
            tVibeHit[zKey] = 0
            tZoneLast.remove(zKey)
            tVibeLast.remove(zKey)
            tMotionSince.remove(zKey)
            
            def oSwitch = settings["z${i}OverrideSwitch"]
            if (oSwitch && oSwitch.currentValue("switch") == "on") {
                oSwitch.off() 
            }
            
            unschedule("evalR${i}")
            unschedule("resetMotionZ${i}")
            unschedule("resetVibeZ${i}")
            
            initiateRoomShutdown(i)
        }
    }
    
    // Force absolute commit to Database
    state.currentRoomStates = tStates
    state.roomReasons = tReasons
    state.motionHitCount = tMotionHit
    state.vibeHitCount = tVibeHit
    state.zoneLastActive = tZoneLast
    state.vibeLastActive = tVibeLast
    state.motionActiveSince = tMotionSince
}

// ===================================

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
    def zKey = "z${roomId}".toString()
    if (state.currentRoomStates && state.currentRoomStates[zKey] == "occupied") {
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
    def zKey = "z${roomId}".toString()
    
    if (state.currentRoomStates && state.currentRoomStates[zKey] != "empty") {
        logAction("Hard kill aborted. Room ${roomId} became occupied during the shutdown delay.")
        state.shutdownDelayActive?.remove((Object)roomId)
        return
    }

    state.shutdownDelayActive?.remove((Object)roomId)
    
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
    def zKey = "z${roomId}".toString()
    def tStats = state.roomStats ? state.roomStats.clone() : [:]
    if (!tStats[zKey]) tStats[zKey] = [totalSecondsOff: 0, unoccupiedSince: null]
    
    if (tStats[zKey].unoccupiedSince == null) {
        tStats[zKey].unoccupiedSince = now()
    }
    state.roomStats = tStats
}

def stopSavingsTimer(roomId) {
    def zKey = "z${roomId}".toString()
    def tStats = state.roomStats ? state.roomStats.clone() : [:]
    
    if (tStats[zKey] && tStats[zKey].unoccupiedSince != null) {
        def since = tStats[zKey].unoccupiedSince
        def elapsedSecs = ((now() - since) / 1000).toLong()
        
        tStats[zKey].totalSecondsOff += elapsedSecs
        tStats[zKey].unoccupiedSince = null
    }
    state.roomStats = tStats
}

def resetAllSavings() {
    logAction("MANUAL OVERRIDE: Resetting all ROI Savings Data to zero.")
    def tStats = state.roomStats ? state.roomStats.clone() : [:]
    
    for (int i = 1; i <= 12; i++) {
        def zKey = "z${i}".toString()
        tStats[zKey] = [totalSecondsOff: 0, unoccupiedSince: (state.currentRoomStates && state.currentRoomStates[zKey] == "empty" ? now() : null)]
    }
    state.roomStats = tStats
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

// ==============================================================================
// SCHEDULING WRAPPERS
// ==============================================================================
def evalR_All() { evaluateRooms(false) }

def evalR1() { evaluateRooms(false) }
def evalR2() { evaluateRooms(false) }
def evalR3() { evaluateRooms(false) }
def evalR4() { evaluateRooms(false) }
def evalR5() { evaluateRooms(false) }
def evalR6() { evaluateRooms(false) }
def evalR7() { evaluateRooms(false) }
def evalR8() { evaluateRooms(false) }
def evalR9() { evaluateRooms(false) }
def evalR10() { evaluateRooms(false) }
def evalR11() { evaluateRooms(false) }
def evalR12() { evaluateRooms(false) }

def resetMotionZ1() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z1"] = 0; state.motionHitCount = t }
def resetMotionZ2() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z2"] = 0; state.motionHitCount = t }
def resetMotionZ3() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z3"] = 0; state.motionHitCount = t }
def resetMotionZ4() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z4"] = 0; state.motionHitCount = t }
def resetMotionZ5() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z5"] = 0; state.motionHitCount = t }
def resetMotionZ6() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z6"] = 0; state.motionHitCount = t }
def resetMotionZ7() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z7"] = 0; state.motionHitCount = t }
def resetMotionZ8() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z8"] = 0; state.motionHitCount = t }
def resetMotionZ9() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z9"] = 0; state.motionHitCount = t }
def resetMotionZ10() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z10"] = 0; state.motionHitCount = t }
def resetMotionZ11() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z11"] = 0; state.motionHitCount = t }
def resetMotionZ12() { def t = state.motionHitCount ? state.motionHitCount.clone() : [:]; t["z12"] = 0; state.motionHitCount = t }

def resetVibeZ1() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z1"] = 0; state.vibeHitCount = t }
def resetVibeZ2() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z2"] = 0; state.vibeHitCount = t }
def resetVibeZ3() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z3"] = 0; state.vibeHitCount = t }
def resetVibeZ4() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z4"] = 0; state.vibeHitCount = t }
def resetVibeZ5() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z5"] = 0; state.vibeHitCount = t }
def resetVibeZ6() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z6"] = 0; state.vibeHitCount = t }
def resetVibeZ7() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z7"] = 0; state.vibeHitCount = t }
def resetVibeZ8() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z8"] = 0; state.vibeHitCount = t }
def resetVibeZ9() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z9"] = 0; state.vibeHitCount = t }
def resetVibeZ10() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z10"] = 0; state.vibeHitCount = t }
def resetVibeZ11() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z11"] = 0; state.vibeHitCount = t }
def resetVibeZ12() { def t = state.vibeHitCount ? state.vibeHitCount.clone() : [:]; t["z12"] = 0; state.vibeHitCount = t }
