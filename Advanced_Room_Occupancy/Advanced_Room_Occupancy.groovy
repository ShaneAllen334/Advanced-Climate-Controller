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
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
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
                        def mLast = state."zoneLastActive_z${i}" ?: null
                        def mReqHits = settings["z${i}MotionActivationHits"] ?: 1
                        def mHits = state."motionHitCount_z${i}" ?: 0
                     
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
                            def activeSince = state."motionActiveSince_z${i}" ?: null
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
                        def vLast = state."vibeLastActive_z${i}" ?: null
                        def vReqHits = settings["z${i}VibeActivationHits"] ?: 1
                        def vHits = state."vibeHitCount_z${i}" ?: 0

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
                        def switchMode = state."switchIsManual_z${i}" ? "Manual" : "Auto-Sync"
                        sensorDetails << "<span style='color:orange; font-weight:bold; font-size:11px;'>🔘 Override Switch: ON (${switchMode})</span>"
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

                    // Wait For Clear Lockout Display
                    if (!isOccupied && state."waitForClear_z${i}") {
                        statusAdditions << "<span style='color:purple; font-size:11px; font-weight:bold;'>Force Off: Waiting for sensors to clear...</span>"
                    }

                    // --- Unoccupied Cooldown UI Display ---
                    if (!isOccupied && state."currentRoomStates_z${i}" == "empty") {
                        def cooldownMins = settings["z${i}UnoccupiedCooldown"] != null ? settings["z${i}UnoccupiedCooldown"].toInteger() : 5
                        if (cooldownMins > 0 && state."unoccupiedLockoutTime_z${i}") {
                            def lockedTime = state."unoccupiedLockoutTime_z${i}"
                            def timeLeft = (lockedTime + (cooldownMins * 60000)) - now()
                            if (timeLeft > 0) {
                                def minsLeft = Math.ceil(timeLeft / 60000).toInteger()
                                if (minsLeft < 1) minsLeft = 1
                                statusAdditions << "<span style='color:brown; font-size:11px; font-weight:bold;'>⏳ Cooldown: Auto-triggers blocked for ~${minsLeft}m</span>"
                            }
                        }
                    }

                    // --- Override Switch Timeout Display ---
                    if (oSwitch && oSwitch.currentValue("switch") == "on" && state."switchIsManual_z${i}") {
                        def oTimeout = settings["z${i}OverrideTimeout"]
                        if (oTimeout && oTimeout > 0 && !isHardActive) {
                            def lastM = state."zoneLastActive_z${i}" ?: 0
                            def lastV = state."vibeLastActive_z${i}" ?: 0
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
                    
                    def restrictionReason = getRoomRestrictionReason(i)

                    // --- Sweeper UI Display ---
                    if (!isOccupied && settings["z${i}Sweeper"]) {
                        def anyOn = false
                        if (devs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (softDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
                        if (anyOn) {
                            def lastAct = state."zoneLastActive_z${i}" ?: now()
                            def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                            def sweepMinsLeft = Math.ceil(((lastAct + timeoutMs) - now()) / 60000).toInteger()
                            if (sweepMinsLeft > 0) {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: ~${sweepMinsLeft}m until auto-off</span>"
                            } else {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: Pending shutdown...</span>"
                            }
                        }
                    }

                    // --- Absolute Sweeper UI Display (Runs during Restrictions) ---
                    if (restrictionReason && settings["z${i}AbsoluteSweeper"]) {
                        def anyOn = false
                        if (devs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (softDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
                        if (anyOn) {
                            def pActive = pDevs && pDevs.any { it.currentValue("presence") == "present" }
                            if (!pActive) {
                                def lastM = state."zoneLastActive_z${i}" ?: 0
                                def lastV = state."vibeLastActive_z${i}" ?: 0
                                def lastAct = Math.max(lastM ?: 0, lastV ?: 0)
                                if (lastAct == 0) lastAct = now()
                                
                                def timeoutMs = (settings["z${i}AbsoluteSweeperTimeout"] ?: 120) * 60000
                                def sweepMinsLeft = Math.ceil(((lastAct + timeoutMs) - now()) / 60000).toInteger()
                                if (sweepMinsLeft > 0) {
                                    statusAdditions << "<span style='color:red; font-size:11px;'>🚨 Absolute Sweeper: ~${sweepMinsLeft}m until auto-off</span>"
                                } else {
                                    statusAdditions << "<span style='color:red; font-size:11px;'>🚨 Absolute Sweeper: Pending shutdown...</span>"
                                }
                            } else {
                                statusAdditions << "<span style='color:red; font-size:11px;'>🚨 Absolute Sweeper: Paused (Presence Detected)</span>"
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

                    if (!isOccupied && state."shutdownDelayActive_z${i}") {
                         statusAdditions << "<span style='color:orange; font-size:11px;'>Safe Shutdown Sequence...</span>"
                    }

                    def remainingMinsDisplay = statusAdditions ? "<br>" + statusAdditions.join("<br>") : ""
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
                    def secondsOff = state."roomStatsTotalSecs_z${i}" ?: 0
                    def unoccSince = state."roomStatsUnoccupiedSince_z${i}"
                    if (unoccSince != null && !restrictionReason) {
                        secondsOff += ((now() - unoccSince) / 1000).toLong()
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

        section("<b>Individual Room Overrides</b>") {
            paragraph "Manually force individual rooms to instantly switch states."
            def hasActive = false
            for (int i = 1; i <= 12; i++) {
                if (settings["enableZ${i}"]) {
                    hasActive = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    input "forceEmptyBtn_${i}", "button", title: "⏏️ Force EMPTY: ${zName}"
                    input "forceOccBtn_${i}", "button", title: "⚡ Force OCCUPIED: ${zName}"
                }
            }
            if (!hasActive) paragraph "<i>No active rooms available.</i>"
        }

        section("<b>Global Configuration & Restrictions</b>") {
            input "kwhCost", "decimal", title: 'Electricity Cost ($ per kWh)', required: true, defaultValue: 0.13
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<b>Global Mode Overrides</b>"
            input "restrictedModes", "mode", title: "Restricted Modes (Pause all occupancy rules)", multiple: true, required: false
            input "forceOffModes", "mode", title: "Force OFF Modes (Immediately turns off all rooms when entering these modes)", multiple: true, required: false
            
            paragraph "<b>Global Manual Controls</b>"
            input "forceAllOccupiedBtn", "button", title: "Force All Rooms Occupied"
            input "forceAllEmptyBtn", "button", title: "Force All Rooms Unoccupied"
            input "clearAllStatesBtn", "button", title: "⚠ EMERGENCY: Clear All States & Reset App"
            
            paragraph "<b>Data Management</b>"
            input "resetSavings", "bool", title: "Reset All Savings Data to Zero", defaultValue: false, submitOnChange: true
            if (settings["resetSavings"]) {
                resetAllSavings()
                app.updateSetting("resetSavings", false)
            }
            
            input "forceSync", "bool", title: "Manually Force Hardware Sync (Pushes ON/OFF commands immediately)", defaultValue: false, submitOnChange: true
            if (settings["forceSync"]) {
                if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
                    log.warn "Force Sync aborted: Master Switch is OFF."
                } else {
                    logAction("MANUAL OVERRIDE: Forcing hardware sync...")
                    evaluateRooms(true)
                }
                app.updateSetting("forceSync", false)
            }
        }

        section("<b>Action History & Debugging</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging (Auto-disables after 30 min)", defaultValue: false, submitOnChange: true
            
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

                input "z${i}UnoccupiedCooldown", "number", title: "Unoccupied Cooldown (Minutes room must stay empty before it can be automatically occupied again)", required: true, defaultValue: 5

                input "z${i}GoodNightSwitch", "capability.switch", title: "Room Good Night Switch (Locks room as Occupied & ignores Mode changes)", required: false, submitOnChange: true

                input "z${i}OverrideSwitch", "capability.switch", title: "Virtual Override Switch", required: false, submitOnChange: true
                if (settings["z${i}OverrideSwitch"] && settings["z${i}TurnOnTriggers"]?.contains("Virtual Switch")) {
                    input "z${i}OverrideTimeout", "number", title: "↳ Auto-Off Timeout (Minutes of NO movement before turning switch OFF)", required: false, defaultValue: 120
                    input "z${i}OverrideButton", "capability.pushableButton", title: "↳ Toggle Button (Trigger to toggle the Virtual Switch)", required: false, submitOnChange: true
                    if (settings["z${i}OverrideButton"]) {
                        input "z${i}OverrideButtonNum", "number", title: "↳ Button Number", required: true, defaultValue: 1
                        input "z${i}OverrideButtonAction", "enum", title: "↳ Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                    }
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
                
                input "z${i}AbsoluteSweeper", "bool", title: "Enable Absolute Sweeper (Works even when room rules are PAUSED/Restricted)", defaultValue: false, submitOnChange: true
                if (settings["z${i}AbsoluteSweeper"]) {
                    input "z${i}AbsoluteSweeperTimeout", "number", title: "↳ Absolute Sweeper Timeout (Minutes of NO motion/vibe/presence before turning OFF)", required: true, defaultValue: 120
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
def updated() { 
    logInfo("Updated"); 
    unsubscribe(); 
    unschedule(); 
    initialize();
    if (debugEnable) {
        log.info "Debug logging enabled for 30 minutes."
        runIn(1800, "disableDebugLog")
    }
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    for (int i = 1; i <= 12; i++) {
        if (!state."currentRoomStates_z${i}") state."currentRoomStates_z${i}" = "unknown"
        if (!state."roomStatsTotalSecs_z${i}") state."roomStatsTotalSecs_z${i}" = 0
        state."motionHitCount_z${i}" = 0
        state."vibeHitCount_z${i}" = 0
    }
    
    // Core Subscriptions
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", masterSwitchHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}OverrideSwitch"]) subscribe(settings["z${i}OverrideSwitch"], "switch", sensorHandler)
            if (settings["z${i}GoodNightSwitch"]) subscribe(settings["z${i}GoodNightSwitch"], "switch", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}Vibration"]) subscribe(settings["z${i}Vibration"], "acceleration", vibrationHandler)
            if (settings["z${i}Presence"]) subscribe(settings["z${i}Presence"], "presence", sensorHandler)
            
            // Toggle Button Listener
            if (settings["z${i}OverrideButton"]) {
                def btnAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
                subscribe(settings["z${i}OverrideButton"], btnAction, buttonHandler)
            }
            
            // Sweeper Event Listeners
            if (settings["z${i}Sweeper"] || settings["z${i}AbsoluteSweeper"]) {
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

// Master Kill Switch Check Function
def isMasterEnabled() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def masterSwitchHandler(evt) {
    logAction("Master Enable Switch turned ${evt.value}.")
    if (evt.value == "on") {
        evaluateRooms(true) // Wake the system back up
    } else {
        unschedule() // Instantly kill all pending shutdown delays and timeouts
    }
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logDebug("Dashboard manually refreshed by user.")
        return
    }

    if (btn == "clearAllStatesBtn") {
        log.warn "EMERGENCY RESET: Purging all application states, trackers, and timers."
        state.clear()
        unschedule()
        initialize()
        return
    } 
    
    if (!isMasterEnabled()) {
        log.warn "Master Switch is OFF. Manual app overrides are blocked."
        return
    }
    
    if (btn.startsWith("forceEmptyBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to EMPTY.")
        
        def oSwitch = settings["z${i}OverrideSwitch"]
        if (oSwitch) {
            state."expectedSwitchBehavior_z${i}" = "manual_off"
            state."expectedSwitchBehaviorTime_z${i}" = now()
            safeOff(oSwitch)
        }
        
        state."switchIsManual_z${i}" = false
        state."waitForClear_z${i}" = true
        
        state."motionHitCount_z${i}" = 0
        state."vibeHitCount_z${i}" = 0
        state."zoneLastActive_z${i}" = null
        state."vibeLastActive_z${i}" = null
        state."motionActiveSince_z${i}" = null
        
        def hardDevs = settings["z${i}Switches"]
        if (hardDevs) hardDevs.each { safeOff(it) }
        def softDevs = settings["z${i}SoftKillDevices"]
        if (softDevs) softDevs.each { safeOff(it) }
        
        evaluateRooms(true)
        return
    }
    
    if (btn.startsWith("forceOccBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to OCCUPIED.")
        
        def oSwitch = settings["z${i}OverrideSwitch"]
        if (oSwitch) {
            state."expectedSwitchBehavior_z${i}" = "manual_on"
            state."expectedSwitchBehaviorTime_z${i}" = now()
            safeOn(oSwitch)
        }
        
        state."switchIsManual_z${i}" = true
        state."zoneLastActive_z${i}" = now()
        
        def hardDevs = settings["z${i}Switches"]
        if (hardDevs) hardDevs.each { safeOn(it) }
        def softDevs = settings["z${i}SoftKillDevices"]
        if (softDevs) softDevs.each { safeOn(it) }
        
        evaluateRooms(true)
        return
    }

    if (btn == "forceAllOccupiedBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to OCCUPIED.")
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch) {
                    state."expectedSwitchBehavior_z${i}" = "manual_on"
                    state."expectedSwitchBehaviorTime_z${i}" = now()
                    safeOn(oSwitch)
                }
                
                state."switchIsManual_z${i}" = true
                state."zoneLastActive_z${i}" = now()
                
                // BRUTE FORCE HARDWARE ON
                def hardDevs = settings["z${i}Switches"]
                if (hardDevs) hardDevs.each { safeOn(it) }
                def softDevs = settings["z${i}SoftKillDevices"]
                if (softDevs) softDevs.each { safeOn(it) }
            }
        }
        evaluateRooms(true)
        
    } else if (btn == "forceAllEmptyBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to EMPTY.")
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch) {
                    state."expectedSwitchBehavior_z${i}" = "manual_off"
                    state."expectedSwitchBehaviorTime_z${i}" = now()
                    safeOff(oSwitch)
                }
                
                state."switchIsManual_z${i}" = false
                state."waitForClear_z${i}" = true
                
                state."motionHitCount_z${i}" = 0
                state."vibeHitCount_z${i}" = 0
                state."zoneLastActive_z${i}" = null
                state."vibeLastActive_z${i}" = null
                state."motionActiveSince_z${i}" = null
                
                // BRUTE FORCE HARDWARE OFF
                def hardDevs = settings["z${i}Switches"]
                if (hardDevs) hardDevs.each { safeOff(it) }
                def softDevs = settings["z${i}SoftKillDevices"]
                if (softDevs) softDevs.each { safeOff(it) }
            }
        }
        evaluateRooms(true)
    }
}

def hubRestartHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("SYSTEM REBOOT DETECTED: Clearing corrupted shutdown timers and forcing a hardware sync to correct physical device desyncs.")
    for (int i = 1; i <= 12; i++) { state."shutdownDelayActive_z${i}" = false }
    evaluateRooms(true) 
}

def modeChangeHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("Location mode changed to: ${evt.value}")
    
    def isForceOff = forceOffModes && (forceOffModes as List).contains(evt.value)
    if (isForceOff) {
        logAction("GLOBAL FORCE OFF TRIGGERED: Mode changed to ${evt.value}.")
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && state."currentRoomStates_z${i}" != "empty") {
                // If the Good Night Switch is on, it ignores global mode changes
                def gnSwitch = settings["z${i}GoodNightSwitch"]
                if (!(gnSwitch && gnSwitch.currentValue("switch") == "on")) {
                    state."currentRoomStates_z${i}" = "empty"
                    initiateRoomShutdown(i)
                }
            }
        }
    }
    
    evaluateRooms(false)
}

def buttonHandler(evt) {
    if (!isMasterEnabled()) return
    def btnVal = evt.value ? evt.value.toInteger() : 1
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"] && settings["z${i}OverrideButton"]?.id == evt.device.id) {
            def targetBtn = (settings["z${i}OverrideButtonNum"] ?: 1).toInteger()
            def targetAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
            
            if (btnVal == targetBtn && evt.name == targetAction) {
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch) {
                    if (oSwitch.currentValue("switch") == "on") {
                        logAction("${settings["z${i}Name"]}: Override Button ${targetAction}. Turning Virtual Switch OFF.")
                        state."expectedSwitchBehavior_z${i}" = "manual_off"
                        state."expectedSwitchBehaviorTime_z${i}" = now()
                        state."switchIsManual_z${i}" = false
                        safeOff(oSwitch)
                    } else {
                        // Check for restrictions before turning ON
                        def restriction = getRoomRestrictionReason(i)
                        if (restriction) {
                            logAction("${settings["z${i}Name"]}: Override Button ${targetAction}, but ignored. Room is currently PAUSED due to: ${restriction}")
                        } else {
                            logAction("${settings["z${i}Name"]}: Override Button ${targetAction}. Turning Virtual Switch ON.")
                            state."expectedSwitchBehavior_z${i}" = "manual_on"
                            state."expectedSwitchBehaviorTime_z${i}" = now()
                            state."switchIsManual_z${i}" = true
                            safeOn(oSwitch)
                            // Force hardware sync in case room was already occupied by motion but plug desynced
                            turnRoomDevicesOn(i) 
                            runIn(1, "evalR${i}")
                        }
                    }
                }
            }
        }
    }
}

def physicalSwitchHandler(evt) {
    if (!isMasterEnabled()) return
    if (evt.value == "on") {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && (settings["z${i}Sweeper"] || settings["z${i}AbsoluteSweeper"])) {
                def hardDevs = settings["z${i}Switches"]
                def softDevs = settings["z${i}SoftKillDevices"]
                if ((hardDevs && hardDevs.find { it.id == evt.device.id }) || (softDevs && softDevs.find { it.id == evt.device.id })) {
                    state."zoneLastActive_z${i}" = now()
                }
            }
        }
    }
}

def motionHandler(evt) {
    if (!isMasterEnabled()) return
    def isActive = (evt.value == "active")
    logDebug("Motion event received from ${evt.device.displayName}: ${evt.value}")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def mDevs = settings["z${i}Motion"]
            if (mDevs && mDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    unschedule("verifyInactiveR${i}") // Cancel grace period if motion returns
                    state."zoneLastActive_z${i}" = now()
                    
                    if (!state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = now()
                        def contMins = settings["z${i}MotionContinuousDuration"] ?: 3
                        runIn(contMins * 60, "evalR${i}")
                    }
                    
                    def count = (state."motionHitCount_z${i}" ?: 0) + 1
                    state."motionHitCount_z${i}" = count
                    
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 1) * 60000
                    if (count == 1 && windowMs > 0) {
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetMotionZ${i}")
                    }
                    
                    // Cancel any pending timeout shutdown for this specific room
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}") // re-eval quickly
                } else {
                    state."zoneLastActive_z${i}" = now() // Update timestamp to the moment motion STOPS

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
    if (!isMasterEnabled()) return
    def mDevs = settings["z${roomId}Motion"]
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }

        // We update the timestamp AGAIN just in case the 15s grace period slightly delayed it
        state."zoneLastActive_z${roomId}" = now()
        
        if (settings["z${roomId}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
            def timeoutSecs = (settings["z${roomId}Timeout"] ?: 15) * 60
            runIn(timeoutSecs, "evalR${roomId}")
        } else {
            runIn(1, "evalR${roomId}")
        }
    }
}

def vibrationHandler(evt) {
    if (!isMasterEnabled()) return
    def isActive = (evt.value == "active")
    logDebug("Vibration event received from ${evt.device.displayName}: ${evt.value}")
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def vDevs = settings["z${i}Vibration"]
            if (vDevs && vDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    state."vibeLastActive_z${i}" = now()
                    
                    def count = (state."vibeHitCount_z${i}" ?: 0) + 1
                    state."vibeHitCount_z${i}" = count
                    
                    def windowMs = (settings["z${i}VibeActivationWindow"] ?: 1) * 60000
                    if (count == 1 && windowMs > 0) {
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetVibeZ${i}")
                    }
                    
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}")
                } else {
                    state."vibeLastActive_z${i}" = now() // Update timestamp to the moment vibration STOPS

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
    if (!isMasterEnabled()) return
    if (evt.name == "switch") {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                if (settings["z${i}OverrideSwitch"]?.id == evt.device.id) {
                    
                    def expected = state."expectedSwitchBehavior_z${i}"
                    def expectedTime = state."expectedSwitchBehaviorTime_z${i}" ?: 0
                    
                    // Increased from 5000ms to 15000ms to account for hub latency
                    def withinWindow = (now() - expectedTime) < 15000 
                    
                    if (withinWindow) {
                        // The event arrived within 15 seconds of the app issuing a command.
                        if (expected == "auto") {
                            state."switchIsManual_z${i}" = false
                        } else if (expected == "manual_on") {
                            state."switchIsManual_z${i}" = true
                        } else if (expected == "manual_off") {
                            state."switchIsManual_z${i}" = false
                        }
                    } else {
                        // Event arrived outside the window. 
                        if (evt.value == "on") {
                            // DUPLICATE EVENT GUARD: If the room is already occupied automatically, 
                            // do not let a late/ghost event hijack the room into a manual lock.
                            if (state."currentRoomStates_z${i}" == "occupied" && state."switchIsManual_z${i}" == false) {
                                logDebug("${settings["z${i}Name"]}: Ignored delayed/duplicate ON event for Virtual Switch. Room is already auto-occupied.")
                            } else {
                                logAction("${settings["z${i}Name"]}: Virtual Switch manually turned ON externally.")
                                state."switchIsManual_z${i}" = true
                                state."zoneLastActive_z${i}" = now()
                                turnRoomDevicesOn(i)
                            }
                        } else {
                            logAction("${settings["z${i}Name"]}: Virtual Switch manually turned OFF externally.")
                            state."switchIsManual_z${i}" = false
                            
                            // Engage the "Wait For Clear" lockout
                            state."waitForClear_z${i}" = true
                            
                            // Clear motion history to ensure immediate empty state
                            state."motionHitCount_z${i}" = 0
                            state."vibeHitCount_z${i}" = 0
                            state."zoneLastActive_z${i}" = null
                            state."motionActiveSince_z${i}" = null
                        }
                    }
                }
                
                if (settings["z${i}GoodNightSwitch"]?.id == evt.device.id && evt.value == "on") {
                    turnRoomDevicesOn(i)
                }
            }
        }
    }
    runIn(1, "evalR_All")
}

def getRoomOccupancyState(roomId) {
    def mDevs = settings["z${roomId}Motion"]
    def vDevs = settings["z${roomId}Vibration"]
    def pDevs = settings["z${roomId}Presence"]
    def oSwitch = settings["z${roomId}OverrideSwitch"]
    def gnSwitch = settings["z${roomId}GoodNightSwitch"]
    def pMonitor = settings["z${roomId}PowerMonitor"]
    
    // 0. SELF-HEALING GHOST TRACKERS
    // If schedules wipe during hub reboots, Continuous Motion can get stuck permanently.
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }
    }

    // 0.25 Enforce "Wait For Clear" Lockout
    // Forces the room to stay empty when manually turned off until all hardware stops sending active signals
    if (state."waitForClear_z${roomId}") {
        def isClear = true
        
        if (mDevs && mDevs.any { it.currentValue("motion") == "active" }) isClear = false
        if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isClear = false
        if (pDevs && pDevs.any { it.currentValue("presence") == "present" }) isClear = false
        
        // BYPASS: If the Virtual Switch is ON, completely drop the "Wait For Clear" lock instantly.
        if (oSwitch && oSwitch.currentValue("switch") == "on") {
            state."waitForClear_z${roomId}" = null
            isClear = true 
        }

        if (!isClear) {
            return false // Force empty until sensors physically clear
        } else {
            state."waitForClear_z${roomId}" = null
        }
    }

    def isOccupied = false
    
    // 0.5 Enforce Unoccupied Cooldown (Software Lockout)
    // Prevents the room from automatically re-triggering occupied for X minutes after becoming empty
    def cooldownMins = settings["z${roomId}UnoccupiedCooldown"] != null ? settings["z${roomId}UnoccupiedCooldown"].toInteger() : 5
    if (cooldownMins > 0 && state."currentRoomStates_z${roomId}" == "empty") {
        def lockedTime = state."unoccupiedLockoutTime_z${roomId}" ?: 0
        if (lockedTime > 0 && (now() - lockedTime) < (cooldownMins * 60000)) {
            // Check ultimate overrides to allow manual bypasses of the cooldown
            if (pMonitor) {
                def currentDraw = pMonitor.currentValue("power") ?: 0.0
                def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
                if (currentDraw > safeThresh) return true
            }
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") return true
            // BYPASS: If Virtual Switch is ON, ignore cooldown.
            if (oSwitch && oSwitch.currentValue("switch") == "on") return true
            
            return false // Room is actively in cooldown, strictly ignore motion and sensors
        }
    }

    // 1. Check Active Wattage Failsafe (Ultimate Override)
    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
        if (currentDraw > safeThresh) {
            state."zoneLastActive_z${roomId}" = now() // Force database save
            return true
        }
    }
    
    // 2. Check Good Night Switch (Ultimate Lock)
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return true
    }
    
    // --- DEADMAN'S SWITCH / STUCK SENSOR PROTECTION ---
    def isHardActive = false
    def maxStuckTime = 6 * 3600000 // 6 hours in milliseconds
    
    if (pDevs) {
        pDevs.each { dev ->
            if (dev.currentValue("presence") == "present") {
                try {
                    def lastEvent = dev.events(max: 1)
                    def lastEventTime = (lastEvent && lastEvent.size() > 0) ? lastEvent[0].date.time : now()
                    if ((now() - lastEventTime) > maxStuckTime) {
                        log.warn "⚠️ DEADMAN SWITCH: Presence sensor ${dev.displayName} in Room ${roomId} has been stuck PRESENT for over 6 hours. Ignoring."
                    } else {
                        isHardActive = true
                    }
                } catch (e) { isHardActive = true }
            }
        }
    }
    
    // Check for the customizable motion grace period & Deadman's Switch
    def graceSecs = settings["z${roomId}MotionGracePeriod"] != null ? settings["z${roomId}MotionGracePeriod"].toInteger() : 15
    def graceMs = graceSecs * 1000
    
    if (mDevs) {
        mDevs.each { dev ->
            if (dev.currentValue("motion") == "active") {
                try {
                    def lastEvent = dev.events(max: 1)
                    def lastEventTime = (lastEvent && lastEvent.size() > 0) ? lastEvent[0].date.time : now()
                    if ((now() - lastEventTime) > maxStuckTime) {
                        log.warn "⚠️ DEADMAN SWITCH: Motion sensor ${dev.displayName} in Room ${roomId} has been stuck ACTIVE for over 6 hours. Ignoring."
                    } else {
                        isHardActive = true
                    }
                } catch (e) { isHardActive = true }
            }
        }
    }
    
    if (!isHardActive && state."zoneLastActive_z${roomId}") {
        if ((now() - state."zoneLastActive_z${roomId}") <= graceMs) isHardActive = true
    }
    
    if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isHardActive = true

    def wasAlreadyOccupied = (state."currentRoomStates_z${roomId}" == "occupied")
    def onTriggers = settings["z${roomId}TurnOnTriggers"] ?: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration", "Presence Sensor"]
    def offTriggers = settings["z${roomId}TurnOffTriggers"] ?: ["Motion/Vibe Timeout", "Virtual Switch OFF"]

    // --- EVALUATE TURN ON TRIGGERS ---
    
    // A. Presence
    if (onTriggers.contains("Presence Sensor") && pDevs && pDevs.any { it.currentValue("presence") == "present" }) {
        isOccupied = true
    }
    
    // B. Virtual Switch ON (Only holds room if manually pressed, ignores auto-synced states)
    if (!isOccupied && onTriggers.contains("Virtual Switch") && oSwitch && oSwitch.currentValue("switch") == "on" && state."switchIsManual_z${roomId}") {
        def oTimeout = settings["z${roomId}OverrideTimeout"]
        if (oTimeout && oTimeout > 0) {
            if (!isHardActive) {
                def maxLast = Math.max(state."zoneLastActive_z${roomId}" ?: 0, state."vibeLastActive_z${roomId}" ?: 0)
                if (maxLast == 0) maxLast = now()
                if ((now() - maxLast) > (oTimeout * 60000)) {
                    logAction("${settings["z${roomId}Name"]}: Virtual Override Switch timed out due to inactivity. Turning OFF.")
                    safeOff(oSwitch)
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
    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Continuous Motion") && mDevs) {
        def activeSince = state."motionActiveSince_z${roomId}"
        def reqMins = settings["z${roomId}MotionContinuousDuration"] ?: 3
        if (activeSince && (now() - activeSince) >= (reqMins * 60000)) {
            isOccupied = true
        }
    }

    // D. Motion Hit Count
    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Motion Hit Count") && mDevs) {
        def reqHits = (settings["z${roomId}MotionActivationHits"] ?: 1).toInteger()
        def hitCount = state."motionHitCount_z${roomId}" ?: 0
        if (hitCount >= reqHits) {
            isOccupied = true
        }
    }

    // E. Vibration Hit Count
    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Vibration") && vDevs) {
        def reqHits = (settings["z${roomId}VibeActivationHits"] ?: 1).toInteger()
        def hitCount = state."vibeHitCount_z${roomId}" ?: 0
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
            def mLastActive = state."zoneLastActive_z${roomId}" ?: 0
            def vLastActive = state."vibeLastActive_z${roomId}" ?: 0
            
            if (mDevs && (mDevs.any { it.currentValue("motion") == "active" } || (mLastActive && (now() - mLastActive) < mTimeoutMs))) holdOccupied = true
            if (vDevs && (vDevs.any { it.currentValue("acceleration") == "active" } || (vLastActive && (now() - vLastActive) < vTimeoutMs))) holdOccupied = true
        }
        
        if (offTriggers.contains("Virtual Switch OFF")) {
            if (oSwitch && oSwitch.currentValue("switch") == "on" && state."switchIsManual_z${roomId}") {
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
    if (!isMasterEnabled()) return
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                stopSavingsTimer(i)
                state."currentRoomStates_z${i}" = "restricted"
                
                // --- ABSOLUTE SWEEPER LOGIC ---
                if (settings["z${i}AbsoluteSweeper"]) {
                    def hardDevs = settings["z${i}Switches"]
                    def softDevs = settings["z${i}SoftKillDevices"]
                    def anyOn = false
                    if (hardDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                    if (softDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                    
                    if (anyOn) {
                        def pDevs = settings["z${i}Presence"]
                        def pActive = pDevs && pDevs.any { it.currentValue("presence") == "present" }
                        
                        if (!pActive) {
                            def lastM = state."zoneLastActive_z${i}" ?: 0
                            def lastV = state."vibeLastActive_z${i}" ?: 0
                            def lastAct = Math.max(lastM ?: 0, lastV ?: 0)
                            
                            if (lastAct == 0) {
                                state."zoneLastActive_z${i}" = now() // Initialize baseline if missing
                            } else {
                                def timeoutMs = (settings["z${i}AbsoluteSweeperTimeout"] ?: 120) * 60000
                                if ((now() - lastAct) >= timeoutMs) {
                                    logAction("${zName}: 🚨 ABSOLUTE SWEEPER activated! Devices left ON in paused room with no motion/presence for ${(settings["z${i}AbsoluteSweeperTimeout"] ?: 120)} minutes. Forcing shutdown.")
                                    initiateRoomShutdown(i)
                                    state."zoneLastActive_z${i}" = now() // Reset to prevent log spamming during the shutdown delay
                                }
                            }
                        } else {
                            // Presence detected, keep refreshing the active timer to hold off the sweeper
                            state."zoneLastActive_z${i}" = now()
                        }
                    }
                }

                // Deep-Clean Reset on Restricted Mode
                state."motionHitCount_z${i}" = 0
                state."vibeHitCount_z${i}" = 0
                state."motionActiveSince_z${i}" = null 
                
                // We only clear the activity trackers if the Absolute Sweeper is NOT relying on them
                if (!settings["z${i}AbsoluteSweeper"]) {
                    state."zoneLastActive_z${i}" = null 
                    state."vibeLastActive_z${i}" = null 
                }
                
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && oSwitch.currentValue("switch") == "on") {
                    state."expectedSwitchBehavior_z${i}" = "auto"
                    state."expectedSwitchBehaviorTime_z${i}" = now()
                    safeOff(oSwitch)
                }
                
                continue
            }

            def isOccupied = getRoomOccupancyState(i)
            def currentState = state."currentRoomStates_z${i}"
            def targetState = isOccupied ? "occupied" : "empty"
            
            if (currentState != targetState || forceSync) {
                state."currentRoomStates_z${i}" = targetState
                
                if (targetState == "occupied") {
                    if (!forceSync) logAction("${zName} is now OCCUPIED. Powering ON devices.")
                    state."shutdownDelayActive_z${i}" = false
                    
                    // Auto-sync virtual switch ON without activating its specific timers
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") != "on") {
                        state."expectedSwitchBehavior_z${i}" = "auto"
                        state."expectedSwitchBehaviorTime_z${i}" = now()
                        safeOn(oSwitch)
                    }
                    
                    turnRoomDevicesOn(i)
                    stopSavingsTimer(i)
                } else {
                    if (!forceSync) logAction("${zName} is now EMPTY. Initiating shutdown sequence.")
                    
                    // Start Unoccupied Cooldown
                    state."unoccupiedLockoutTime_z${i}" = now() 
                    
                    // Annihilate stuck Continuous Motion Trackers just to be safe
                    if (state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = null
                    }
                    
                    // Auto-sync the virtual switch off if the room goes empty via timeouts
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
                        state."expectedSwitchBehavior_z${i}" = "auto"
                        state."expectedSwitchBehaviorTime_z${i}" = now()
                        safeOff(oSwitch)
                    }
                    
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
                    def lastAct = state."zoneLastActive_z${i}" ?: now()
                    def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                    if ((now() - lastAct) >= timeoutMs) {
                        logAction("${zName}: 🧹 Sweeper activated! Stranded devices were left ON with no motion for ${(settings["z${i}SweeperTimeout"] ?: 60)} minutes. Forcing shutdown.")
                        
                        // Force purge continuous motion if sweeper caught it
                        if (state."motionActiveSince_z${i}") {
                            state."motionActiveSince_z${i}" = null
                        }
                        
                        initiateRoomShutdown(i)
                        
                        state."zoneLastActive_z${i}" = now() // Reset to prevent spamming logs during device shutdown delays
                    }
                }
            }
        }
    }
}

def turnRoomDevicesOn(roomId) {
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) {
        hardDevs.each { safeOn(it) }
    }
    
    def softDevs = settings["z${roomId}SoftKillDevices"]
    if (softDevs) {
        runIn(2, "executeSoftBoot", [data: [room: roomId], overwrite: false])
    }
}

def executeSoftBoot(data) {
    if (!isMasterEnabled()) return
    def roomId = data.room
    if (state."currentRoomStates_z${roomId}" == "occupied") {
        def softDevs = settings["z${roomId}SoftKillDevices"]
        softDevs?.each { safeOn(it) }
    }
}

def initiateRoomShutdown(roomId) {
    if (!isMasterEnabled()) return
    def softDevs = settings["z${roomId}SoftKillDevices"]
    def hardDevs = settings["z${roomId}Switches"]
    def delaySecs = settings["z${roomId}HardKillDelay"] ?: 0

    if (softDevs && softDevs.any { it.currentValue("switch") != "off" }) {
        logAction("Sending Graceful Shutdown commands to sensitive electronics in Room ${roomId}.")
        softDevs.each { safeOff(it) }
        
        if (hardDevs && delaySecs > 0) {
            logAction("Waiting ${delaySecs} seconds for sensitive devices to shut down before cutting hard power.")
            state."shutdownDelayActive_z${roomId}" = true 
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
    if (!isMasterEnabled()) return
    def roomId = data.room
    
    if (state."currentRoomStates_z${roomId}" != "empty") {
        logAction("Hard kill aborted. Room ${roomId} became occupied during the shutdown delay.")
        state."shutdownDelayActive_z${roomId}" = false
        return
    }

    state."shutdownDelayActive_z${roomId}" = false
    
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) {
        logAction("Cutting hard power to managed relays in Room ${roomId}.")
        hardDevs.each { safeOff(it) }
    }
    
    startSavingsTimer(roomId)
}

// === ROI SAVINGS TRACKING ===
def startSavingsTimer(roomId) {
    if (state."roomStatsUnoccupiedSince_z${roomId}" == null) {
        state."roomStatsUnoccupiedSince_z${roomId}" = now()
    }
}

def stopSavingsTimer(roomId) {
    if (state."roomStatsUnoccupiedSince_z${roomId}" != null) {
        def since = state."roomStatsUnoccupiedSince_z${roomId}"
        def elapsedSecs = ((now() - since) / 1000).toLong()
        
        state."roomStatsTotalSecs_z${roomId}" = (state."roomStatsTotalSecs_z${roomId}" ?: 0) + elapsedSecs
        state."roomStatsUnoccupiedSince_z${roomId}" = null
    }
}

def resetAllSavings() {
    logAction("MANUAL OVERRIDE: Resetting all ROI Savings Data to zero.")
    for (int i = 1; i <= 12; i++) {
        state."roomStatsTotalSecs_z${i}" = 0
        state."roomStatsUnoccupiedSince_z${i}" = (state."currentRoomStates_z${i}" == "empty" ? now() : null)
    }
}

// === HARDWARE SAFE WRAPPERS ===
def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on") {
        try { dev.on() } catch (e) { log.error "Failed to turn ON ${dev.displayName}: ${e.message}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed to turn OFF ${dev.displayName}: ${e.message}" }
    }
}

// === LOGGING ===
def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

def disableDebugLog() {
    log.info "Auto-disabling debug logging."
    app.updateSetting("debugEnable", [value: "false", type: "bool"])
}

def logDebug(msg) {
    if (debugEnable) log.debug "${app.label}: ${msg}"
}

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

def resetMotionZ1() { state."motionHitCount_z1" = 0 }
def resetMotionZ2() { state."motionHitCount_z2" = 0 }
def resetMotionZ3() { state."motionHitCount_z3" = 0 }
def resetMotionZ4() { state."motionHitCount_z4" = 0 }
def resetMotionZ5() { state."motionHitCount_z5" = 0 }
def resetMotionZ6() { state."motionHitCount_z6" = 0 }
def resetMotionZ7() { state."motionHitCount_z7" = 0 }
def resetMotionZ8() { state."motionHitCount_z8" = 0 }
def resetMotionZ9() { state."motionHitCount_z9" = 0 }
def resetMotionZ10() { state."motionHitCount_z10" = 0 }
def resetMotionZ11() { state."motionHitCount_z11" = 0 }
def resetMotionZ12() { state."motionHitCount_z12" = 0 }

def resetVibeZ1() { state."vibeHitCount_z1" = 0 }
def resetVibeZ2() { state."vibeHitCount_z2" = 0 }
def resetVibeZ3() { state."vibeHitCount_z3" = 0 }
def resetVibeZ4() { state."vibeHitCount_z4" = 0 }
def resetVibeZ5() { state."vibeHitCount_z5" = 0 }
def resetVibeZ6() { state."vibeHitCount_z6" = 0 }
def resetVibeZ7() { state."vibeHitCount_z7" = 0 }
def resetVibeZ8() { state."vibeHitCount_z8" = 0 }
def resetVibeZ9() { state."vibeHitCount_z9" = 0 }
def resetVibeZ10() { state."vibeHitCount_z10" = 0 }
def resetVibeZ11() { state."vibeHitCount_z11" = 0 }
def resetVibeZ12() { state."vibeHitCount_z12" = 0 }
