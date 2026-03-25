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
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
             
                    def isOccupied = getRoomOccupancyState(i)
                    def mDevs = settings["z${i}Motion"]
                    def vDevs = settings["z${i}Vibration"]
                    def pDevs = settings["z${i}Presence"]
                    def devs = settings["z${i}Switches"]
                    def softDevs = settings["z${i}SoftKillDevices"]
                    def pMonitor = settings["z${i}PowerMonitor"]
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    def gnSwitch = settings["z${i}GoodNightSwitch"]
         
                    def statusAdditions = []
                    def sensorDetails = []
                    def maxRemainingMs = 0
                    def isHardActive = false

                    // Check Good Night Switch
                    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                        sensorDetails << "<span style='color:purple; font-weight:bold; font-size:11px;'>🌙 Good Night: ON (Locked)</span>"
                        isHardActive = true
                    }

                    // Check Presence Sensors
                    if (pDevs) {
                        pDevs.each { dev ->
                            def val = dev.currentValue("presence")
                            def color = val == "present" ? "blue" : "gray"
                            def fw = val == "present" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>👤 ${dev.displayName}: ${val}</span>"
                            if (val == "present") isHardActive = true
                        }
                    }

                    // Check Motion Sensors & Timers
                    if (mDevs) {
                        mDevs.each { dev ->
                            def val = dev.currentValue("motion")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>🏃 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def mTimeout = (settings["z${i}Timeout"] ?: 15) * 60000
                        def mLast = state.zoneLastActive ? state.zoneLastActive["z${i}"] : null
                        def mReqHits = settings["z${i}MotionActivationHits"] ?: 1
                        def mHits = state.motionHitCount ? (state.motionHitCount["z${i}"] ?: 0) : 0
                        
                        if (mLast && !mDevs.any{it.currentValue("motion") == "active"}) {
                            def mLeft = mTimeout - (now() - mLast)
                            if (mLeft > maxRemainingMs) maxRemainingMs = mLeft
                        }
                        
                        // Hit Counter Window Timer (Displays even when occupied for tracking)
                        if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count") && mReqHits > 1 && mHits > 0 && mLast) {
                            def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                            def windowLeft = (mLast + windowMs) - now()
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Motion Hits: ${mHits}/${mReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                        
                        // Continuous Motion Display (Displays active duration when occupied)
                        if (settings["z${i}TurnOnTriggers"]?.contains("Continuous Motion")) {
                            def activeSince = state.motionActiveSince ? state.motionActiveSince["z${i}"] : null
                            if (activeSince) {
                                def reqMins = settings["z${i}MotionContinuousDuration"] ?: 3
                                def left = (activeSince + (reqMins * 60000)) - now()
                                if (left > 0) {
                                    def secsLeft = Math.ceil(left / 1000).toInteger()
                                    statusAdditions << "<span style='color:teal; font-size:11px;'>Continuous Motion: ${secsLeft}s left</span>"
                                } else {
                                    def activeMins = Math.floor((now() - activeSince) / 60000).toInteger()
                                    statusAdditions << "<span style='color:teal; font-size:11px;'>Continuous Motion: Active for ${activeMins}m</span>"
                                }
                            }
                        }
                    }

                    // Check Vibration Sensors & Timers
                    if (vDevs) {
                        vDevs.each { dev ->
                            def val = dev.currentValue("acceleration")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>📳 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def vTimeout = (settings["z${i}VibeTimeout"] ?: 5) * 60000
                        def vLast = state.vibeLastActive ? state.vibeLastActive["z${i}"] : null
                        def vReqHits = settings["z${i}VibeActivationHits"] ?: 1
                        def vHits = state.vibeHitCount ? (state.vibeHitCount["z${i}"] ?: 0) : 0

                        if (vLast && !vDevs.any{it.currentValue("acceleration") == "active"}) {
                            def vLeft = vTimeout - (now() - vLast)
                            if (vLeft > maxRemainingMs) maxRemainingMs = vLeft
                        }
                        
                        // Hit Counter Window Timer (Displays even when occupied for tracking)
                        if (settings["z${i}TurnOnTriggers"]?.contains("Vibration") && vReqHits > 1 && vHits > 0 && vLast) {
                            def windowMs = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                            def windowLeft = (vLast + windowMs) - now()
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Vibe Hits: ${vHits}/${vReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                    }
                    
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
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
                                    statusAdditions << "<span style='color:blue; font-size:11px;'>Override Auto-Off: ~${minsLeft}m</span>"
                                }
                            }
                        }
                    }

                    // --- Sweeper UI Display ---
                    if (!isOccupied && settings["z${i}Sweeper"]) {
                        def anyOn = false
                        if (devs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (softDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
                        if (anyOn) {
                            def lastAct = state.zoneLastActive["z${i}"] ?: now()
                            def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                            def sweepMinsLeft = Math.ceil(((lastAct + timeoutMs) - now()) / 60000).toInteger()
                            if (sweepMinsLeft > 0) {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: ~${sweepMinsLeft}m until auto-off</span>"
                            } else {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: Pending shutdown...</span>"
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
                    def isOccupiedDisplay = "<b><span style='color:${stateColor};'>${stateLabel}</span></b>${remainingMinsDisplay}"
                    
                    def sensorListDisplay = sensorDetails ? sensorDetails.join("<br>") : "<span style='color:gray; font-size:11px;'>None Monitored</span>"

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

                input "z${i}GoodNightSwitch", "capability.switch", title: "Room Good Night Switch (Locks room as Occupied & ignores Mode changes)", required: false, submitOnChange: true

                input "z${i}OverrideSwitch", "capability.switch", title: "Virtual Override Switch", required: false, submitOnChange: true
                if (settings["z${i}OverrideSwitch"] && settings["z${i}TurnOnTriggers"]?.contains("Virtual Switch")) {
                    input "z${i}OverrideTimeout", "number", title: "↳ Auto-Off Timeout (Minutes of NO movement before turning switch OFF)", required: false, defaultValue: 120
                }
                
                input "z${i}Motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Motion"]) {
                    input "z${i}MotionGracePeriod", "number", title: "↳ Motion Inactive Grace Period (Seconds before officially confirming empty)", required: true, defaultValue: 15
                    
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

                paragraph "<b>Manual Mode / Unoccupied Sweeper</b>"
                input "z${i}Sweeper", "bool", title: "Enable Unoccupied Sweeper (Turns off stranded devices left ON in empty rooms)", defaultValue: false, submitOnChange: true
                if (settings["z${i}Sweeper"]) {
                    input "z${i}SweeperTimeout", "number", title: "↳ Sweeper Timeout (Minutes of NO motion before turning OFF)", required: true, defaultValue: 60
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
    
    for (int i = 1; i <= 12; i++) {
        if (!state.roomStats["z${i}"]) state.roomStats["z${i}"] = [totalSecondsOff: 0, unoccupiedSince: null]
        if (!state.currentRoomStates["z${i}"]) state.currentRoomStates["z${i}"] = "unknown"
        state.motionHitCount["z${i}"] = 0
        state.vibeHitCount["z${i}"] = 0
    }
    
    // Core Subscriptions
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", sensorHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}OverrideSwitch"]) subscribe(settings["z${i}OverrideSwitch"], "switch", sensorHandler)
            if (settings["z${i}GoodNightSwitch"]) subscribe(settings["z${i}GoodNightSwitch"], "switch", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}Vibration"]) subscribe(settings["z${i}Vibration"], "acceleration", vibrationHandler)
            if (settings["z${i}Presence"]) subscribe(settings["z${i}Presence"], "presence", sensorHandler)
            
            // Sweeper Event Listeners
            if (settings["z${i}Sweeper"]) {
                if (settings["z${i}Switches"]) subscribe(settings["z${i}Switches"], "switch", physicalSwitchHandler)
                if (settings["z${i}SoftKillDevices"]) subscribe(settings["z${i}SoftKillDevices"], "switch", physicalSwitchHandler)
            }
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
            if (settings["enableZ${i}"] && state.currentRoomStates["z${i}"] != "empty") {
                // If the Good Night Switch is on, it ignores global mode changes
                def gnSwitch = settings["z${i}GoodNightSwitch"]
                if (!(gnSwitch && gnSwitch.currentValue("switch") == "on")) {
                    state.currentRoomStates["z${i}"] = "empty"
                    initiateRoomShutdown(i)
                }
            }
        }
    }
    
    evaluateRooms(false)
}

def physicalSwitchHandler(evt) {
    if (evt.value == "on") {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Sweeper"]) {
                def hardDevs = settings["z${i}Switches"]
                def softDevs = settings["z${i}SoftKillDevices"]
                if ((hardDevs && hardDevs.find { it.id == evt.device.id }) || (softDevs && softDevs.find { it.id == evt.device.id })) {
                    if (!state.zoneLastActive) state.zoneLastActive = [:]
                    state.zoneLastActive["z${i}"] = now()
                }
            }
        }
    }
}

def motionHandler(evt) {
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.motionHitCount) state.motionHitCount = [:]
    if (!state.motionActiveSince) state.motionActiveSince = [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def mDevs = settings["z${i}Motion"]
            if (mDevs && mDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    unschedule("verifyInactiveR${i}") // Cancel grace period if motion returns
                    state.zoneLastActive["z${i}"] = now()
                    
                    if (!state.motionActiveSince["z${i}"]) {
                        state.motionActiveSince["z${i}"] = now()
                        def contMins = settings["z${i}MotionContinuousDuration"] ?: 3
                        runIn(contMins * 60, "evalR${i}")
                    }
                    
                    def count = (state.motionHitCount["z${i}"] ?: 0) + 1
                    state.motionHitCount["z${i}"] = count
                    
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                    if (count == 1 && windowMs > 0) {
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetMotionZ${i}")
                    }
                    
                    // Cancel any pending timeout shutdown for this specific room
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}") // re-eval quickly
                } else {
                    // Only process empty states if ALL motion sensors in the room are inactive
                    if (!mDevs.any { it.currentValue("motion") == "active" }) {
                        def graceSecs = settings["z${i}MotionGracePeriod"] != null ? settings["z${i}MotionGracePeriod"].toInteger() : 15
                        if (graceSecs > 0) {
                            runIn(graceSecs, "verifyInactiveR${i}")
                        } else {
                            processMotionInactive(i)
                        }
                    }
                }
            }
        }
    }
}

// Wrapper to process motion after the grace period
def processMotionInactive(roomId) {
    def mDevs = settings["z${roomId}Motion"]
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        state.motionActiveSince?.remove("z${roomId}") // Clears continuous motion
        
        if (settings["z${roomId}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
            def timeoutSecs = (settings["z${roomId}Timeout"] ?: 15) * 60
            runIn(timeoutSecs, "evalR${roomId}")
        } else {
            runIn(1, "evalR${roomId}")
        }
    }
}

def vibrationHandler(evt) {
    if (!state.vibeLastActive) state.vibeLastActive = [:]
    if (!state.vibeHitCount) state.vibeHitCount = [:]
    
    def isActive = (evt.value == "active")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def vDevs = settings["z${i}Vibration"]
            if (vDevs && vDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    state.vibeLastActive["z${i}"] = now()
                    
                    def count = (state.vibeHitCount["z${i}"] ?: 0) + 1
                    state.vibeHitCount["z${i}"] = count
                    
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
}

def sensorHandler(evt) {
    if (evt.name == "switch") {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}OverrideSwitch"]?.id == evt.device.id) {
                if (evt.value == "on") {
                    if (!state.zoneLastActive) state.zoneLastActive = [:]
                    state.zoneLastActive["z${i}"] = now() 
                }
            }
        }
    }
    runIn(1, "evalR_All")
}

def getRoomOccupancyState(roomId) {
    if (!state.currentRoomStates) state.currentRoomStates = [:]
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.vibeLastActive) state.vibeLastActive = [:]
    if (!state.motionHitCount) state.motionHitCount = [:]
    if (!state.vibeHitCount) state.vibeHitCount = [:]
    if (!state.motionActiveSince) state.motionActiveSince = [:]

    def isOccupied = false
    def mDevs = settings["z${roomId}Motion"]
    def vDevs = settings["z${roomId}Vibration"]
    def pDevs = settings["z${roomId}Presence"]
    def oSwitch = settings["z${roomId}OverrideSwitch"]
    def gnSwitch = settings["z${roomId}GoodNightSwitch"]
    def pMonitor = settings["z${roomId}PowerMonitor"]
    
    // 1. Check Active Wattage Failsafe (Ultimate Override)
    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
        if (currentDraw > safeThresh) {
            state.zoneLastActive["z${roomId}"] = now()
            return true
        }
    }
    
    // 2. Check Good Night Switch (Ultimate Lock)
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return true
    }
    
    def isHardActive = false
    if (pDevs && pDevs.any { it.currentValue("presence") == "present" }) isHardActive = true
    
    // Check for the customizable motion grace period
    def graceSecs = settings["z${roomId}MotionGracePeriod"] != null ? settings["z${roomId}MotionGracePeriod"].toInteger() : 15
    def graceMs = graceSecs * 1000
    if (mDevs && (mDevs.any { it.currentValue("motion") == "active" } || (state.zoneLastActive["z${roomId}"] && (now() - state.zoneLastActive["z${roomId}"] <= graceMs)))) isHardActive = true
    
    if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isHardActive = true

    def wasAlreadyOccupied = (state.currentRoomStates["z${roomId}"] == "occupied")
    def onTriggers = settings["z${roomId}TurnOnTriggers"] ?: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration", "Presence Sensor"]
    def offTriggers = settings["z${roomId}TurnOffTriggers"] ?: ["Motion/Vibe Timeout", "Virtual Switch OFF"]

    // --- EVALUATE TURN ON TRIGGERS ---
    
    // A. Presence
    if (onTriggers.contains("Presence Sensor") && pDevs && pDevs.any { it.currentValue("presence") == "present" }) {
        isOccupied = true
    }
    
    // B. Virtual Switch ON
    if (!isOccupied && onTriggers.contains("Virtual Switch") && oSwitch && oSwitch.currentValue("switch") == "on") {
        def oTimeout = settings["z${roomId}OverrideTimeout"]
        if (oTimeout && oTimeout > 0) {
            if (!isHardActive) {
                def maxLast = Math.max(state.zoneLastActive["z${roomId}"] ?: 0, state.vibeLastActive["z${roomId}"] ?: 0)
                if (maxLast == 0) maxLast = now()
                if ((now() - maxLast) > (oTimeout * 60000)) {
                    logAction("${settings["z${roomId}Name"]}: Virtual Override Switch timed out due to inactivity. Turning OFF.")
                    oSwitch.off()
                } else {
                    isOccupied = true
                }
            } else {
                isOccupied = true 
            }
        } else {
            isOccupied = true 
        }
    }

    // C. Continuous Motion Duration
    if (!isOccupied && onTriggers.contains("Continuous Motion") && mDevs) {
        def activeSince = state.motionActiveSince["z${roomId}"]
        def reqMins = settings["z${roomId}MotionContinuousDuration"] ?: 3
        if (activeSince && (now() - activeSince) >= (reqMins * 60000)) {
            isOccupied = true
        }
    }

    // D. Motion Hit Count
    if (!isOccupied && onTriggers.contains("Motion Hit Count") && mDevs) {
        def reqHits = (settings["z${roomId}MotionActivationHits"] ?: 1).toInteger()
        def hitCount = state.motionHitCount["z${roomId}"] ?: 0
        if (hitCount >= reqHits) {
            isOccupied = true
        }
    }

    // E. Vibration Hit Count
    if (!isOccupied && onTriggers.contains("Vibration") && vDevs) {
        def reqHits = (settings["z${roomId}VibeActivationHits"] ?: 1).toInteger()
        def hitCount = state.vibeHitCount["z${roomId}"] ?: 0
        if (hitCount >= reqHits) {
            isOccupied = true
        }
    }

    // --- EVALUATE TURN OFF/MAINTAIN CONDITIONS ---
    if (wasAlreadyOccupied && !isOccupied) {
        def holdOccupied = false
        
        if (offTriggers.contains("Motion/Vibe Timeout")) {
            def mTimeoutMs = (settings["z${roomId}Timeout"] ?: 15) * 60000
            def vTimeoutMs = (settings["z${roomId}VibeTimeout"] ?: 5) * 60000
            def mLastActive = state.zoneLastActive["z${roomId}"] ?: 0
            def vLastActive = state.vibeLastActive["z${roomId}"] ?: 0
            
            if (mDevs && (mDevs.any { it.currentValue("motion") == "active" } || (mLastActive && (now() - mLastActive) < mTimeoutMs))) holdOccupied = true
            if (vDevs && (vDevs.any { it.currentValue("acceleration") == "active" } || (vLastActive && (now() - vLastActive) < vTimeoutMs))) holdOccupied = true
        }
        
        if (offTriggers.contains("Virtual Switch OFF")) {
            if (oSwitch && oSwitch.currentValue("switch") == "on") {
                holdOccupied = true
            }
        }
        
        if (holdOccupied) {
            isOccupied = true
        }
    }
    
    return isOccupied
}

def getRoomRestrictionReason(roomId) {
    // If Good Night Switch is on, immediately reject all restrictions
    def gnSwitch = settings["z${roomId}GoodNightSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return null 
    }

    def currentMode = location.mode
    
    if (forceOffModes && (forceOffModes as List).contains(currentMode)) {
        return "Force Off Mode"
    }
    
    if (restrictedModes && (restrictedModes as List).contains(currentMode)) {
        return "Global Mode"
    }
    
    def roomModes = settings["z${roomId}OperatingModes"]
    if (roomModes && !(roomModes as List).contains(currentMode)) {
        return "Room Mode"
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

def evaluateRooms(boolean forceSync = false) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    if (!state.shutdownDelayActive) state.shutdownDelayActive = []
    if (!state.currentRoomStates) state.currentRoomStates = [:]

    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                stopSavingsTimer(i)
                state.currentRoomStates["z${i}"] = "restricted"
                
                // Deep-Clean Reset on Restricted Mode
                state.motionHitCount["z${i}"] = 0
                state.vibeHitCount["z${i}"] = 0
                if (state.zoneLastActive) state.zoneLastActive.remove("z${i}")
                if (state.vibeLastActive) state.vibeLastActive.remove("z${i}")
                if (state.motionActiveSince) state.motionActiveSince.remove("z${i}")
                
                unschedule("evalR${i}")
                unschedule("resetMotionZ${i}")
                unschedule("resetVibeZ${i}")
                
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && oSwitch.currentValue("switch") == "on") {
                    oSwitch.off()
                }
                
                continue
            }

            def isOccupied = getRoomOccupancyState(i)
            def currentState = state.currentRoomStates["z${i}"]
            def targetState = isOccupied ? "occupied" : "empty"
            
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
            } else if (currentState == "empty" && settings["z${i}Sweeper"]) {
                // --- MANUAL SWEEPER LOGIC ---
                def hardDevs = settings["z${i}Switches"]
                def softDevs = settings["z${i}SoftKillDevices"]
                def anyOn = false
                if (hardDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                if (softDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                
                if (anyOn) {
                    def lastAct = state.zoneLastActive["z${i}"] ?: now()
                    def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                    if ((now() - lastAct) >= timeoutMs) {
                        logAction("${zName}: 🧹 Sweeper activated! Stranded devices were left ON with no motion for ${(settings["z${i}SweeperTimeout"] ?: 60)} minutes. Forcing shutdown.")
                        initiateRoomShutdown(i)
                        state.zoneLastActive["z${i}"] = now() // Reset to prevent spamming logs during device shutdown delays
                    }
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
    if (!state.roomStats) state.roomStats = [:]
    if (!state.currentRoomStates) state.currentRoomStates = [:]
    
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

// ==============================================================================
// SCHEDULING WRAPPERS (Prevents cross-room overwriting in Hubitat memory)
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

def verifyInactiveR1() { processMotionInactive(1) }
def verifyInactiveR2() { processMotionInactive(2) }
def verifyInactiveR3() { processMotionInactive(3) }
def verifyInactiveR4() { processMotionInactive(4) }
def verifyInactiveR5() { processMotionInactive(5) }
def verifyInactiveR6() { processMotionInactive(6) }
def verifyInactiveR7() { processMotionInactive(7) }
def verifyInactiveR8() { processMotionInactive(8) }
def verifyInactiveR9() { processMotionInactive(9) }
def verifyInactiveR10() { processMotionInactive(10) }
def verifyInactiveR11() { processMotionInactive(11) }
def verifyInactiveR12() { processMotionInactive(12) }

def resetMotionZ1() { if (state.motionHitCount) state.motionHitCount["z1"] = 0 }
def resetMotionZ2() { if (state.motionHitCount) state.motionHitCount["z2"] = 0 }
def resetMotionZ3() { if (state.motionHitCount) state.motionHitCount["z3"] = 0 }
def resetMotionZ4() { if (state.motionHitCount) state.motionHitCount["z4"] = 0 }
def resetMotionZ5() { if (state.motionHitCount) state.motionHitCount["z5"] = 0 }
def resetMotionZ6() { if (state.motionHitCount) state.motionHitCount["z6"] = 0 }
def resetMotionZ7() { if (state.motionHitCount) state.motionHitCount["z7"] = 0 }
def resetMotionZ8() { if (state.motionHitCount) state.motionHitCount["z8"] = 0 }
def resetMotionZ9() { if (state.motionHitCount) state.motionHitCount["z9"] = 0 }
def resetMotionZ10() { if (state.motionHitCount) state.motionHitCount["z10"] = 0 }
def resetMotionZ11() { if (state.motionHitCount) state.motionHitCount["z11"] = 0 }
def resetMotionZ12() { if (state.motionHitCount) state.motionHitCount["z12"] = 0 }

def resetVibeZ1() { if (state.vibeHitCount) state.vibeHitCount["z1"] = 0 }
def resetVibeZ2() { if (state.vibeHitCount) state.vibeHitCount["z2"] = 0 }
def resetVibeZ3() { if (state.vibeHitCount) state.vibeHitCount["z3"] = 0 }
def resetVibeZ4() { if (state.vibeHitCount) state.vibeHitCount["z4"] = 0 }
def resetVibeZ5() { if (state.vibeHitCount) state.vibeHitCount["z5"] = 0 }
def resetVibeZ6() { if (state.vibeHitCount) state.vibeHitCount["z6"] = 0 }
def resetVibeZ7() { if (state.vibeHitCount) state.vibeHitCount["z7"] = 0 }
def resetVibeZ8() { if (state.vibeHitCount) state.vibeHitCount["z8"] = 0 }
def resetVibeZ9() { if (state.vibeHitCount) state.vibeHitCount["z9"] = 0 }
def resetVibeZ10() { if (state.vibeHitCount) state.vibeHitCount["z10"] = 0 }
def resetVibeZ11() { if (state.vibeHitCount) state.vibeHitCount["z11"] = 0 }
def resetVibeZ12() { if (state.vibeHitCount) state.vibeHitCount["z12"] = 0 }
