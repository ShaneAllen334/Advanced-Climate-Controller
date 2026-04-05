/**
 * Advanced Room Occupancy
 */

definition(
    name: "Advanced Room Occupancy",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-zone occupancy controller with System Boot Recovery, Active Wattage Failsafes, Two-Stage Shutdowns, Collapsible UI, and Partial Occupancy.",
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
                    def currentState = state."currentRoomStates_z${i}"
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

                    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                        sensorDetails << "<span style='color:purple; font-weight:bold; font-size:11px;'>🌙 Good Night: ON (Locked)</span>"
                        isHardActive = true
                    }

                    if (pDevs) {
                        pDevs.each { dev ->
                            def val = dev.currentValue("presence")
                            def color = val == "present" ? "blue" : "gray"
                            def fw = val == "present" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>👤 ${dev.displayName}: ${val}</span>"
                            if (val == "present") isHardActive = true
                        }
                    }

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
                        
                        if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count") && mReqHits > 1 && mHits > 0) {
                            def windowMs = (settings["z${i}MotionActivationWindow"] ?: 3) * 60000
                            def hitStart = state."motionHitStartTime_z${i}" ?: (mLast ?: now())
                            def windowLeft = (hitStart + windowMs) - now()
                            
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Motion Hits: ${mHits}/${mReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                        
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
                        
                        if (settings["z${i}TurnOnTriggers"]?.contains("Vibration") && vReqHits > 1 && vHits > 0) {
                            def windowMs = (settings["z${i}VibeActivationWindow"] ?: 3) * 60000
                            def hitStart = state."vibeHitStartTime_z${i}" ?: (vLast ?: now())
                            def windowLeft = (hitStart + windowMs) - now()
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

                    if (pMonitor) {
                        def currentDraw = pMonitor.currentValue("power") ?: 0.0
                        def safeThresh = settings["z${i}ActiveWattageThreshold"] ?: 15.0
                        if (currentDraw > safeThresh) {
                            statusAdditions << "<span style='color:red; font-size:11px; font-weight:bold;'>Power Lock Active (${currentDraw}W)</span>"
                            isHardActive = true
                        }
                    }

                    if (!isOccupied && state."waitForClear_z${i}") {
                        statusAdditions << "<span style='color:purple; font-size:11px; font-weight:bold;'>Force Off: Waiting for sensors to clear...</span>"
                    }

                    if (!isOccupied && currentState == "empty") {
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

                    if (!isOccupied && settings["z${i}Sweeper"]) {
                        def allHard = (devs ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                        def allSoft = (softDevs ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                        
                        def anyOn = false
                        if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
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

                    if (restrictionReason && settings["z${i}AbsoluteSweeper"]) {
                        def allHard = (devs ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                        def allSoft = (softDevs ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                        
                        def anyOn = false
                        if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
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

                    if (isOccupied && !isHardActive && maxRemainingMs > 0) {
                        def mins = Math.floor(maxRemainingMs / 60000).toInteger()
                        def secs = Math.floor((maxRemainingMs % 60000) / 1000).toInteger()
                        def timeStr = mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
                        statusAdditions << "<span style='color:#888; font-size:11px;'>Unoccupied in: ${timeStr}</span>"
                    }

                    if (!isOccupied && state."shutdownDelayActive_z${i}") {
                         statusAdditions << "<span style='color:orange; font-size:11px;'>Safe Shutdown Sequence...</span>"
                    }

                    def dailyStats = state."dailyStats_z${i}" ?: [:]
                    def todayStr = getTodayDateString()
                    def tStats = dailyStats[todayStr] ?: [occTime: 0, unoccTime: 0, count: 0]
                    
                    def lastUpd = state."lastStatUpdate_z${i}" ?: now()
                    def liveDelta = ((now() - lastUpd) / 1000).toLong()
                    if (liveDelta > 600) liveDelta = 0 
                    
                    def liveTodayOcc = tStats.occTime + (isOccupied ? liveDelta : 0)
                    def liveTodayUnocc = tStats.unoccTime + (!isOccupied ? liveDelta : 0)

                    def weeklyOcc = isOccupied ? liveDelta : 0
                    def weeklyUnocc = !isOccupied ? liveDelta : 0
                    def weeklyCount = 0

                    dailyStats.each { k, v ->
                        weeklyOcc += v.occTime
                        weeklyUnocc += v.unoccTime
                        weeklyCount += v.count
                    }

                    def statHtml = "<div style='margin-top: 8px; padding-top: 5px; border-top: 1px dotted #ccc; font-size:11px; color:#444;'>"
                    statHtml += "<table style='width:100%; border:none; font-size:11px; line-height:1.2;'>"
                    statHtml += "<tr><td style='width: 30%;'></td><td style='width: 25%;'><b>Occupied</b></td><td style='width: 25%;'><b>Empty</b></td><td style='width: 20%;'><b>Visits</b></td></tr>"
                    statHtml += "<tr><td><b>Today:</b></td><td>${formatDuration(liveTodayOcc)}</td><td>${formatDuration(liveTodayUnocc)}</td><td>${tStats.count}x</td></tr>"
                    statHtml += "<tr><td><b>7-Day:</b></td><td>${formatDuration(weeklyOcc)}</td><td>${formatDuration(weeklyUnocc)}</td><td>${weeklyCount}x</td></tr>"
                    statHtml += "</table></div>"

                    def stateColor = restrictionReason ? "orange" : (currentState == "partial" ? "#D2691E" : (isOccupied ? "green" : "black"))
                    def stateLabel = restrictionReason ? "PAUSED (${restrictionReason})" : (currentState == "partial" ? "PARTIAL OCC" : (isOccupied ? "OCCUPIED" : "EMPTY"))
                    def isOccupiedDisplay = "<b><span style='color:${stateColor};'>${stateLabel}</span></b>${statHtml}"
                    
                    def sensorListDisplay = sensorDetails ? sensorDetails.join("<br>") : "<span style='color:gray; font-size:11px;'>None Monitored</span>"

                    def totalActive = 0.0
                    def totalStandby = 0.0
                    def devNames = []
                    
                    def allHardDevs = (devs ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                    allHardDevs = allHardDevs.unique { it.id }
                    
                    def allSoftDevs = (softDevs ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                    allSoftDevs = allSoftDevs.unique { it.id }
                    
                    if (allHardDevs) {
                        allHardDevs.each { dev ->
                            def activeW = settings["z${i}_${dev.id}_active"] ?: 0.0
                            def standbyW = settings["z${i}_${dev.id}_standby"] ?: 0.0
                            totalActive += activeW
                            totalStandby += standbyW
                            
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "<span style='font-size:11px;'>⚡ ${dev.displayName} (${devState})</span>"
                        }
                    }

                    if (allSoftDevs) {
                        allSoftDevs.each { dev ->
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "<span style='font-size:11px;'>💻 ${dev.displayName} (${devState})</span>"
                        }
                    }

                    def devListDisplay = devNames ? devNames.join("<br>") : "<span style='color:gray; font-size:11px;'>None Configured</span>"
                    def powerDisplay = "<span style='font-size:11px;'><b>Active:</b> ${totalActive}W<br><b>Standby:</b> ${totalStandby}W</span>"
                    def fullDeviceColumn = "${devListDisplay}<br><br>${powerDisplay}"

                    def secondsOff = state."lifetimeUnoccSecs_z${i}" ?: 0
                    secondsOff += (!isOccupied && !restrictionReason ? liveDelta : 0)
                    
                    def savedHours = secondsOff / 3600.0
                    def wastedKw = (totalActive + totalStandby) / 1000.0
                    def roomSavings = savedHours * wastedKw * rate
                    totalAppSavings += roomSavings
                    
                    def formattedSavings = String.format('$%.2f', roomSavings)

                    def rowBorder = statusAdditions ? "none" : "2px solid #ccc"
                    
                    statusText += "<tr style='border-bottom: ${rowBorder};'>"
                    statusText += "<td style='padding: 8px; vertical-align: top;'><b>${zName}</b></td>"
                    statusText += "<td style='padding: 8px; vertical-align: top;'>${isOccupiedDisplay}</td>"
                    statusText += "<td style='padding: 8px; vertical-align: top;'>${sensorListDisplay}</td>"
                    statusText += "<td style='padding: 8px; vertical-align: top;'>${fullDeviceColumn}</td>"
                    statusText += "<td style='padding: 8px; vertical-align: top; color:#008800; font-weight:bold;'>${formattedSavings}</td>"
                    statusText += "</tr>"

                    if (statusAdditions) {
                        statusText += "<tr style='border-bottom: 2px solid #ccc; background-color: #f9f9f9;'>"
                        statusText += "<td colspan='5' style='padding: 6px 8px;'>"
                        statusText += "<table style='width:100%; border:none; font-size:11px;'><tr><td style='padding:4px;'>"
                        statusText += "<b>Active Conditions & Timers:</b><br>"
                        statusText += statusAdditions.join(" &nbsp;|&nbsp; ")
                        statusText += "</td></tr></table>"
                        statusText += "</td></tr>"
                    }
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
                    input "forceOccBtn_${i}", "button", title: "⚡ Force FULL OCC: ${zName}"
                    input "forcePartBtn_${i}", "button", title: "💡 Force PARTIAL OCC: ${zName}"
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
            input "forceAllOccupiedBtn", "button", title: "Force All Rooms FULL Occupied"
            input "forceAllEmptyBtn", "button", title: "Force All Rooms Unoccupied"
            input "clearAllStatesBtn", "button", title: "⚠ EMERGENCY: Clear All States & Reset App"
            
            paragraph "<b>Data Management</b>"
            input "resetSavings", "bool", title: "Reset All Savings & Occupancy Data to Zero", defaultValue: false, submitOnChange: true
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
                    input "z${i}OverrideButton", "capability.pushableButton", title: "↳ Toggle Button (Physical Trigger to switch states)", required: false, submitOnChange: true
                    if (settings["z${i}OverrideButton"]) {
                        input "z${i}OverrideButtonNum", "number", title: "↳ Button Number", required: true, defaultValue: 1
                        input "z${i}OverrideButtonAction", "enum", title: "↳ FULL Occupancy Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                        input "z${i}PartialOverrideButtonAction", "enum", title: "↳ PARTIAL Occupancy Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "doubleTapped"
                    }
                }
                
                input "z${i}Motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Motion"]) {
                    input "z${i}MotionGracePeriod", "number", title: "↳ Motion Inactive Grace Period (Seconds before officially confirming empty)", required: true, defaultValue: 15
                    
                    if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count")) {
                        input "z${i}MotionActivationWindow", "number", title: "↳ Hit Count Window (Minutes)", required: true, defaultValue: 3
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
                        input "z${i}VibeActivationWindow", "number", title: "↳ Activation Window (Minutes to count hits)", required: true, defaultValue: 3
                        input "z${i}VibeActivationHits", "number", title: "↳ Required Active Hits to trigger Occupied", required: true, defaultValue: 1
                    }
                    if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                        input "z${i}VibeTimeout", "number", title: "↳ Vibration Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 5
                    }
                }
                
                input "z${i}Presence", "capability.presenceSensor", title: "mmWave / Presence Sensors (Instant Occupied)", multiple: true, required: false
               
                paragraph "<b>FULL Occupancy Devices</b>"
                input "z${i}Switches", "capability.switch", title: "Hard Power Relays (Smart Plugs, Wall Switches)", multiple: true, required: false, submitOnChange: true
                input "z${i}SoftKillDevices", "capability.switch", title: "Network Devices (PCs, TVs, APIs) for Graceful Shutdown", multiple: true, required: false, submitOnChange: true
                
                paragraph "<b>PARTIAL Occupancy Devices</b> (Triggered via Double Tap)"
                input "z${i}PartialSwitches", "capability.switch", title: "Partial Mode Hard Power Relays", multiple: true, required: false, submitOnChange: true
                input "z${i}PartialSoftKillDevices", "capability.switch", title: "Partial Mode Network Devices", multiple: true, required: false, submitOnChange: true
                
                paragraph "<b>Auto-Partial Occupancy (Night Mode)</b>"
                input "z${i}AutoPartialModes", "mode", title: "Auto-Partial Modes (Sensors trigger Partial instead of Full)", multiple: true, required: false
                input "z${i}AutoPartialStart", "time", title: "Auto-Partial Start Time (Optional)", required: false
                input "z${i}AutoPartialEnd", "time", title: "Auto-Partial End Time (Optional)", required: false

                def allHardDevs = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                allHardDevs = allHardDevs.unique { it.id }

                if (allHardDevs) {
                    paragraph "<b>Device Power Profiles</b>"
                    allHardDevs.each { dev ->
                        input "z${i}_${dev.id}_active", "decimal", title: "↳ ${dev.displayName} - Active/Idle Watts", required: true, defaultValue: 50.0
                        input "z${i}_${dev.id}_standby", "decimal", title: "↳ ${dev.displayName} - Standby/Phantom Watts", required: true, defaultValue: 5.0
                    }
                }

                def allSoftDevs = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                if (allSoftDevs) {
                    paragraph "<b>Two-Stage Graceful Shutdown</b>"
                    input "z${i}HardKillDelay", "number", title: "Delay before cutting Hard Power (Seconds)", defaultValue: 60, required: true, description: "Provides time for operating systems to shut down and screens to run pixel refresh."
                }
                
                paragraph "<b>Power Failsafe Override</b>"
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
        if (!state."dailyStats_z${i}") state."dailyStats_z${i}" = [:]
        
        if (state."roomStatsTotalSecs_z${i}" != null && state."lifetimeUnoccSecs_z${i}" == null) {
            state."lifetimeUnoccSecs_z${i}" = state."roomStatsTotalSecs_z${i}"
        } else if (!state."lifetimeUnoccSecs_z${i}") {
            state."lifetimeUnoccSecs_z${i}" = 0
        }
        
        state."lastStatUpdate_z${i}" = now()
        state."motionHitCount_z${i}" = 0
        state."vibeHitCount_z${i}" = 0
        
        state.remove("occStats_z${i}")
        state.remove("roomStatsOccupiedSince_z${i}")
        state.remove("roomStatsUnoccupiedSince_z${i}")
        state.remove("roomStatsTotalSecs_z${i}")
    }
    
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
            
            if (settings["z${i}OverrideButton"]) {
                def fullAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
                def partialAction = settings["z${i}PartialOverrideButtonAction"] ?: "doubleTapped"
                subscribe(settings["z${i}OverrideButton"], fullAction, buttonHandler)
                if (fullAction != partialAction) {
                    subscribe(settings["z${i}OverrideButton"], partialAction, buttonHandler)
                }
            }
            
            if (settings["z${i}Sweeper"] || settings["z${i}AbsoluteSweeper"]) {
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                allHard = allHard.unique { it.id }
                if (allHard) subscribe(allHard, "switch", physicalSwitchHandler)
                
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                allSoft = allSoft.unique { it.id }
                if (allSoft) subscribe(allSoft, "switch", physicalSwitchHandler)
            }
        }
    }
    
    runEvery5Minutes("evaluateRooms")
    
    logAction("Advanced Room Occupancy Initialized (Event-Driven Mode Active).")
    evaluateRooms(false)
}

def isMasterEnabled() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def masterSwitchHandler(evt) {
    logAction("Master Enable Switch turned ${evt.value}.")
    if (evt.value == "on") {
        evaluateRooms(true)
    } else {
        unschedule() 
    }
}

// === CENTRALIZED MANUAL STATE FORCERS ===
def forceRoomEmpty(i) {
    def oSwitch = settings["z${i}OverrideSwitch"]
    if (oSwitch) {
        state."expectedSwitchBehavior_z${i}" = "manual_off"
        state."expectedSwitchBehaviorTime_z${i}" = now()
        safeOff(oSwitch)
    }
    
    state."switchIsManual_z${i}" = false
    state."isPartial_z${i}" = false
    state."waitForClear_z${i}" = true
    state."unoccupiedLockoutTime_z${i}" = now()

    state."motionHitCount_z${i}" = 0
    state."vibeHitCount_z${i}" = 0
    state."motionHitStartTime_z${i}" = null
    state."vibeHitStartTime_z${i}" = null
    state."zoneLastActive_z${i}" = null
    state."vibeLastActive_z${i}" = null
    state."motionActiveSince_z${i}" = null
    
    // Set state to empty BEFORE initiating shutdown to pass safety checks
    state."currentRoomStates_z${i}" = "empty"
    initiateRoomShutdown(i)
}

def forceRoomState(i, targetState) {
    def oSwitch = settings["z${i}OverrideSwitch"]
    if (oSwitch) {
        state."expectedSwitchBehavior_z${i}" = "manual_on"
        state."expectedSwitchBehaviorTime_z${i}" = now()
        safeOn(oSwitch)
    }
    
    state."switchIsManual_z${i}" = true
    state."isPartial_z${i}" = (targetState == "partial")
    state."zoneLastActive_z${i}" = now()
    state."unoccupiedLockoutTime_z${i}" = null
    
    if (targetState == "partial") {
        turnRoomPartialDevicesOn(i)
        turnOffNonPartialDevices(i) 
    } else {
        turnRoomDevicesOn(i)
    }
    
    state."currentRoomStates_z${i}" = targetState
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
        forceRoomEmpty(i)
        evaluateRooms(true)
        return
    }
 
    if (btn.startsWith("forceOccBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to FULL OCCUPIED.")
        forceRoomState(i, "occupied")
        evaluateRooms(true)
        return
    }
    
    if (btn.startsWith("forcePartBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to PARTIAL OCCUPIED.")
        forceRoomState(i, "partial")
        evaluateRooms(true)
        return
    }

    if (btn == "forceAllOccupiedBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to FULL OCCUPIED.")
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                forceRoomState(i, "occupied")
            }
        }
        evaluateRooms(true)
    } else if (btn == "forceAllEmptyBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to EMPTY.")
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                forceRoomEmpty(i)
            }
        }
        evaluateRooms(true)
    }
}

// === ENHANCED RECOVERY LOGIC ===
def hubRestartHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("SYSTEM REBOOT / POWER OUTAGE DETECTED: Recovering previous room states and forcing hardware sync...")
    
    for (int i = 1; i <= 12; i++) { 
        state."shutdownDelayActive_z${i}" = false 
        
        if (settings["enableZ${i}"]) {
            def lastKnownState = state."currentRoomStates_z${i}"
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            
            if (lastKnownState == "occupied") {
                 logAction("RECOVERY: ${zName} was FULL OCCUPIED before outage. Restoring ON state.")
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && state."switchIsManual_z${i}") safeOn(oSwitch)
                turnRoomDevicesOn(i)
            } else if (lastKnownState == "partial") {
                 logAction("RECOVERY: ${zName} was PARTIAL OCCUPIED before outage. Restoring PARTIAL ON state.")
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && state."switchIsManual_z${i}") safeOn(oSwitch)
                turnRoomPartialDevicesOn(i)
            } else if (lastKnownState == "empty") {
                logAction("RECOVERY: ${zName} was EMPTY before outage. Restoring OFF state.")
                forceRoomEmpty(i)
            }
        }
    }
    
    logAction("Pausing live sensor evaluations for 2 minutes to allow Zigbee/Z-Wave meshes to stabilize...")
    unschedule("evaluateRooms")
    runIn(120, "resumeNormalOperations")
}

def resumeNormalOperations() {
    logAction("Mesh stabilization complete. Resuming standard occupancy evaluations.")
    runEvery5Minutes("evaluateRooms")
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
                def gnSwitch = settings["z${i}GoodNightSwitch"]
                if (!(gnSwitch && gnSwitch.currentValue("switch") == "on")) {
                    forceRoomEmpty(i)
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
            def fullAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
            def partialAction = settings["z${i}PartialOverrideButtonAction"] ?: "doubleTapped"
            
            if (btnVal == targetBtn) {
                def currentState = state."currentRoomStates_z${i}"
                def zName = settings["z${i}Name"] ?: "Room ${i}"
                def restriction = getRoomRestrictionReason(i)
                
                if (restriction) {
                    logAction("${zName}: Override Button pressed, but ignored. Room is currently PAUSED due to: ${restriction}")
                    return
                }

                // If room is ON in any capacity, ANY button action will instantly shut it down
                if (currentState == "occupied" || currentState == "partial") {
                    logAction("${zName}: Override Button Action (Toggle OFF). Forcing EMPTY.")
                    forceRoomEmpty(i)
                    runIn(1, "evalR${i}")
                } else {
                    // Room is OFF. Turn ON according to the physical action pressed
                    if (evt.name == fullAction) {
                        logAction("${zName}: Override Button FULL Action. Forcing FULL Occupancy.")
                        forceRoomState(i, "occupied")
                        runIn(1, "evalR${i}")
                    } 
                    else if (evt.name == partialAction) {
                        logAction("${zName}: Override Button PARTIAL Action. Forcing PARTIAL Occupancy.")
                        forceRoomState(i, "partial")
                        runIn(1, "evalR${i}")
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
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                
                if ((allHard.find { it.id == evt.device.id }) || (allSoft.find { it.id == evt.device.id })) {
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
                    unschedule("verifyInactiveR${i}") 
                    state."zoneLastActive_z${i}" = now()
                    
                    if (!state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = now()
                        def contMins = settings["z${i}MotionContinuousDuration"] ?: 3
                        runIn(contMins * 60, "evalR${i}")
                    }
                 
                    def count = (state."motionHitCount_z${i}" ?: 0) + 1
                    state."motionHitCount_z${i}" = count
                    
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 3) * 60000
                    if (count == 1 && windowMs > 0) {
                        state."motionHitStartTime_z${i}" = now()
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetMotionZ${i}")
                    }
          
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}") 
                } else {
                    state."zoneLastActive_z${i}" = now()

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

def processMotionInactive(roomId) {
    if (!isMasterEnabled()) return
    def mDevs = settings["z${roomId}Motion"]
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }

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
         
                    def windowMs = (settings["z${i}VibeActivationWindow"] ?: 3) * 60000
                    if (count == 1 && windowMs > 0) {
                        state."vibeHitStartTime_z${i}" = now() 
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetVibeZ${i}")
                    }
                    
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}")
               
                } else {
                    state."vibeLastActive_z${i}" = now() 

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
                    def withinWindow = (now() - expectedTime) < 5000 
                    def isExpectedEcho = false
                    
                    if (withinWindow) {
                        if ((expected == "auto" && evt.value == "off") || (expected == "manual_off" && evt.value == "off")) {
                            isExpectedEcho = true
                        } else if ((expected == "auto" && evt.value == "on") || (expected == "manual_on" && evt.value == "on")) {
                            isExpectedEcho = true
                        }
                    }
                    
                    if (isExpectedEcho) {
                        if (expected == "auto") {
                            state."switchIsManual_z${i}" = false
                        } else if (expected == "manual_on") {
                            state."switchIsManual_z${i}" = true
                        } else if (expected == "manual_off") {
                            state."switchIsManual_z${i}" = false
                        }
                    } else {
                        if (evt.value == "on") {
                            // NEW LOGIC: If the room is already Partial or Occupied (from a button push or sensor), ignore the switch echo entirely.
                            if (state."currentRoomStates_z${i}" == "occupied" || state."currentRoomStates_z${i}" == "partial") {
                                logDebug("${settings["z${i}Name"]}: Ignored Virtual Switch ON event. Room is already active and handling its own state.")
                            } else {
                                logAction("${settings["z${i}Name"]}: Virtual Switch manually turned ON. Forcing FULL Occupancy.")
                                forceRoomState(i, "occupied")
                            }
                        } else {
                            logAction("${settings["z${i}Name"]}: Virtual Switch manually turned OFF externally.")
                            forceRoomEmpty(i)
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
    
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }
    }

    if (state."waitForClear_z${roomId}") {
        def isClear = true
       
        if (mDevs && mDevs.any { it.currentValue("motion") == "active" }) isClear = false
        if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isClear = false
        if (pDevs && pDevs.any { it.currentValue("presence") == "present" }) isClear = false
        
        if (oSwitch && oSwitch.currentValue("switch") == "on") {
            def expected = state."expectedSwitchBehavior_z${roomId}"
            def expectedTime = state."expectedSwitchBehaviorTime_z${roomId}" ?: 0
            if (!(expected == "manual_off" && (now() - expectedTime) < 5000)) {
                state."waitForClear_z${roomId}" = null
                isClear = true
            }
        }

        if (!isClear) {
            return false 
        } else {
            state."waitForClear_z${roomId}" = null
        }
    }

    def isOccupied = false
    
    def cooldownMins = settings["z${roomId}UnoccupiedCooldown"] != null ? settings["z${roomId}UnoccupiedCooldown"].toInteger() : 5
    if (cooldownMins > 0 && state."currentRoomStates_z${roomId}" == "empty") {
        def lockedTime = state."unoccupiedLockoutTime_z${roomId}" ?: 0
        if (lockedTime > 0 && (now() - lockedTime) < (cooldownMins * 60000)) {
            if (pMonitor) {
                def currentDraw = pMonitor.currentValue("power") ?: 0.0
                def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
                if (currentDraw > safeThresh) return true
            }
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") return true
            
            if (oSwitch && oSwitch.currentValue("switch") == "on") {
                def expected = state."expectedSwitchBehavior_z${roomId}"
                def expectedTime = state."expectedSwitchBehaviorTime_z${roomId}" ?: 0
                if (!(expected == "manual_off" && (now() - expectedTime) < 5000)) {
                    return true
                }
            }
            
            return false 
        }
    }

    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
  
        if (currentDraw > safeThresh) {
            state."zoneLastActive_z${roomId}" = now() 
            return true
        }
    }
    
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return true
    }
    
    def isHardActive = false
    def maxStuckTime = 6 * 3600000 
    
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

    def wasAlreadyOccupied = (state."currentRoomStates_z${roomId}" == "occupied" || state."currentRoomStates_z${roomId}" == "partial")
    def onTriggers = settings["z${roomId}TurnOnTriggers"] ?: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration", "Presence Sensor"]
    def offTriggers = settings["z${roomId}TurnOffTriggers"] ?: ["Motion/Vibe Timeout", "Virtual Switch OFF"]

    if (onTriggers.contains("Presence Sensor") && pDevs && pDevs.any { it.currentValue("presence") == "present" }) {
        isOccupied = true
    }
    
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

    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Continuous Motion") && mDevs) {
        def activeSince = state."motionActiveSince_z${roomId}"
        def reqMins = settings["z${roomId}MotionContinuousDuration"] ?: 3
        if (activeSince && (now() - activeSince) >= (reqMins * 60000)) {
            isOccupied = true
        }
    }

    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Motion Hit Count") && mDevs) {
        def reqHits = (settings["z${roomId}MotionActivationHits"] ?: 1).toInteger()
        def hitCount = state."motionHitCount_z${roomId}" ?: 0
    
        if (hitCount >= reqHits) {
            isOccupied = true
        }
    }

    if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Vibration") && vDevs) {
        def reqHits = (settings["z${roomId}VibeActivationHits"] ?: 1).toInteger()
        def hitCount = state."vibeHitCount_z${roomId}" ?: 0
       
        if (hitCount >= reqHits) {
            isOccupied = true
        }
    }

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

def isAutoPartialActive(roomId) {
    def isPartialTime = false
    def pStart = settings["z${roomId}AutoPartialStart"]
    def pEnd = settings["z${roomId}AutoPartialEnd"]
    
    if (pStart && pEnd) {
        def currTime = now()
        def start = timeToday(pStart, location.timeZone).time
        def end = timeToday(pEnd, location.timeZone).time
        
        if (start <= end) {
            isPartialTime = (currTime >= start && currTime <= end)
        } else {
            isPartialTime = (currTime >= start || currTime <= end)
        }
    }
    
    def pModes = settings["z${roomId}AutoPartialModes"]
    def isPartialMode = pModes && (pModes as List).contains(location.mode)
    
    return isPartialTime || isPartialMode
}

def getRoomRestrictionReason(roomId) {
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
                updateRoomStats(i, false)
                state."currentRoomStates_z${i}" = "restricted"
                state."isPartial_z${i}" = false
 
                if (settings["z${i}AbsoluteSweeper"]) {
                    def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                    def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                    
                    def anyOn = false
                    if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                    if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                  
                    if (anyOn) {
                        def pDevs = settings["z${i}Presence"]
                        def pActive = pDevs && pDevs.any { it.currentValue("presence") == "present" }
               
                        if (!pActive) {
                            def lastM = state."zoneLastActive_z${i}" ?: 0
                            def lastV = state."vibeLastActive_z${i}" ?: 0
                            def lastAct = Math.max(lastM ?: 0, lastV ?: 0)
                            
                            if (lastAct == 0) {
                                state."zoneLastActive_z${i}" = now() 
                            } else {
                                def timeoutMs = (settings["z${i}AbsoluteSweeperTimeout"] ?: 120) * 60000
 
                                if ((now() - lastAct) >= timeoutMs) {
                                    logAction("${zName}: 🚨 ABSOLUTE SWEEPER activated! Devices left ON in paused room with no motion/presence for ${(settings["z${i}AbsoluteSweeperTimeout"] ?: 120)} minutes. Forcing shutdown.")
                                    initiateRoomShutdown(i)
                                    state."zoneLastActive_z${i}" = now() 
                                }
                            }
                        } else {
                            state."zoneLastActive_z${i}" = now()
                        }
                    }
                }

                state."motionHitCount_z${i}" = 0
                state."vibeHitCount_z${i}" = 0
                state."motionHitStartTime_z${i}" = null
                state."vibeHitStartTime_z${i}" = null
                state."motionActiveSince_z${i}" = null 
                
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
            
            updateRoomStats(i, (currentState == "occupied" || currentState == "partial"))
            
            def targetState = "empty"
            if (isOccupied) {
                // If the room is waking up organically (via sensors, not a manual button push)
                if (currentState == "empty" && !state."switchIsManual_z${i}") {
                    state."isPartial_z${i}" = isAutoPartialActive(i)
                }
                targetState = state."isPartial_z${i}" ? "partial" : "occupied"
            }
            
            def stateChanged = (currentState != targetState)

            if (stateChanged || forceSync) {
                state."currentRoomStates_z${i}" = targetState
                
                if (targetState == "occupied" || targetState == "partial") {
                    if (stateChanged) {
                        def today = getTodayDateString()
                        def stats = state."dailyStats_z${i}" ?: [:]
                        def daily = stats[today] ?: [occTime: 0, unoccTime: 0, count: 0]
                        daily.count += 1
                        stats[today] = daily
                        state."dailyStats_z${i}" = stats
                    }

                    if (!forceSync) {
                        def modeName = targetState == "partial" ? "PARTIAL OCCUPANCY" : "FULL OCCUPANCY"
                        logAction("${zName} is now in ${modeName}. Powering ON designated devices.")
                    }
                    
                    state."shutdownDelayActive_z${i}" = false
                    
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") != "on") {
                        state."expectedSwitchBehavior_z${i}" = "auto"
                        state."expectedSwitchBehaviorTime_z${i}" = now()
                        runIn(1, "syncVirtualSwitchOn", [data: [room: i]])
                    }
                    
                    if (targetState == "partial") {
                        turnRoomPartialDevicesOn(i)
                        if (!forceSync) turnOffNonPartialDevices(i)
                    } else {
                        turnRoomDevicesOn(i)
                    }

                } else {
                    if (!forceSync) logAction("${zName} is now EMPTY. Initiating shutdown sequence.")
                    
                    state."unoccupiedLockoutTime_z${i}" = now() 
                    state."isPartial_z${i}" = false
                    
                    if (state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = null
                    }
               
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
                        state."expectedSwitchBehavior_z${i}" = "auto"
                        state."expectedSwitchBehaviorTime_z${i}" = now()
                        runIn(1, "syncVirtualSwitchOff", [data: [room: i]])
                    }
              
                    initiateRoomShutdown(i)
                }
            } else if (currentState == "empty" && settings["z${i}Sweeper"]) {
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                
                def anyOn = false
                if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
            
                if (anyOn) {
                    def lastAct = state."zoneLastActive_z${i}" ?: now()
                    def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                    if ((now() - lastAct) >= timeoutMs) {
                        logAction("${zName}: 🧹 Sweeper activated! Stranded devices were left ON with no motion for ${(settings["z${i}SweeperTimeout"] ?: 60)} minutes. Forcing shutdown.")
                        
                        if (state."motionActiveSince_z${i}") {
                            state."motionActiveSince_z${i}" = null
                        }
                        
                        initiateRoomShutdown(i)
                        state."zoneLastActive_z${i}" = now() 
                    }
                }
            }
        }
    }
}

def turnRoomDevicesOn(roomId) {
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) hardDevs.each { safeOn(it) }
    
    def partHardDevs = settings["z${roomId}PartialSwitches"]
    if (partHardDevs) partHardDevs.each { safeOn(it) }
    
    def allSoft = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
    if (allSoft) runIn(2, "executeSoftBoot", [data: [room: roomId, mode: "full"], overwrite: false])
}

def turnRoomPartialDevicesOn(roomId) {
    def partHardDevs = settings["z${roomId}PartialSwitches"]
    if (partHardDevs) partHardDevs.each { safeOn(it) }
    
    def partSoftDevs = settings["z${roomId}PartialSoftKillDevices"]
    if (partSoftDevs) runIn(2, "executeSoftBoot", [data: [room: roomId, mode: "partial"], overwrite: false])
}

def turnOffNonPartialDevices(roomId) {
    def fullHard = settings["z${roomId}Switches"] ?: []
    def partHard = settings["z${roomId}PartialSwitches"] ?: []
    fullHard.each { dev ->
        if (!partHard.any { it.id == dev.id }) safeOff(dev)
    }
    
    def fullSoft = settings["z${roomId}SoftKillDevices"] ?: []
    def partSoft = settings["z${roomId}PartialSoftKillDevices"] ?: []
    fullSoft.each { dev ->
        if (!partSoft.any { it.id == dev.id }) safeOff(dev)
    }
}

def executeSoftBoot(data) {
    if (!isMasterEnabled()) return
    def roomId = data.room
    def stateCheck = state."currentRoomStates_z${roomId}"
    
    if (stateCheck == "occupied" || stateCheck == "partial") {
        if (data.mode == "partial") {
            def softDevs = settings["z${roomId}PartialSoftKillDevices"]
            softDevs?.each { safeOn(it) }
        } else {
            def softDevs = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
            softDevs.unique { it.id }?.each { safeOn(it) }
        }
    }
}

def initiateRoomShutdown(roomId) {
    if (!isMasterEnabled()) return
    def softDevs = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
    softDevs = softDevs.unique { it.id }
    
    def hardDevs = (settings["z${roomId}Switches"] ?: []) + (settings["z${roomId}PartialSwitches"] ?: [])
    hardDevs = hardDevs.unique { it.id }
    
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
    
    def hardDevs = (settings["z${roomId}Switches"] ?: []) + (settings["z${roomId}PartialSwitches"] ?: [])
    hardDevs = hardDevs.unique { it.id }
    
    if (hardDevs) {
        logAction("Cutting hard power to managed relays in Room ${roomId}.")
        hardDevs.each { safeOff(it) }
    }
}

// === ROI SAVINGS TRACKING ===
def resetAllSavings() {
    logAction("MANUAL OVERRIDE: Resetting all ROI Savings Data and Occupancy Stats to zero.")
    for (int i = 1; i <= 12; i++) {
        state."lifetimeUnoccSecs_z${i}" = 0
        state."dailyStats_z${i}" = [:]
        state."lastStatUpdate_z${i}" = now()
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
// OCCUPANCY STATS & TRACKING WRAPPERS
// ==============================================================================

def getTodayDateString() {
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    return df.format(new Date())
}

def formatDuration(long secs) {
    if (secs < 60) return "${secs}s"
    def mins = (secs / 60).toInteger()
    if (mins < 60) return "${mins}m"
    def hrs = (mins / 60).toInteger()
    def remMins = mins % 60
    return "${hrs}h ${remMins}m"
}

def updateRoomStats(roomId, isOccupied) {
    def lastUpdate = state."lastStatUpdate_z${roomId}" ?: now()
    def nowMs = now()
    def deltaSecs = ((nowMs - lastUpdate) / 1000).toLong()
    
    if (deltaSecs > 300) deltaSecs = 300 
    if (deltaSecs < 0) deltaSecs = 0

    def today = getTodayDateString()
    def stats = state."dailyStats_z${roomId}" ?: [:]
    def daily = stats[today] ?: [occTime: 0, unoccTime: 0, count: 0]

    if (isOccupied) {
        daily.occTime += deltaSecs
    } else {
        daily.unoccTime += deltaSecs
        state."lifetimeUnoccSecs_z${roomId}" = (state."lifetimeUnoccSecs_z${roomId}" ?: 0) + deltaSecs
    }

    stats[today] = daily
    
    def keysToRemove = []
    def cal = Calendar.getInstance(location.timeZone)
    cal.add(Calendar.DAY_OF_YEAR, -7)
    def sevenDaysAgoStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.getTime())
    
    stats.each { k, v ->
        if (k < sevenDaysAgoStr) keysToRemove << k
    }
    keysToRemove.each { stats.remove(it) }
  
    state."dailyStats_z${roomId}" = stats
    state."lastStatUpdate_z${roomId}" = nowMs
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

def syncVirtualSwitchOn(data) {
    def dev = settings["z${data.room}OverrideSwitch"]
    if (dev) safeOn(dev)
}

def syncVirtualSwitchOff(data) {
    def dev = settings["z${data.room}OverrideSwitch"]
    if (dev) safeOff(dev)
}
