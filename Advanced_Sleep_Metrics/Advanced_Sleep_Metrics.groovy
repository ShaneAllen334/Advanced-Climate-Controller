/**
 * Advanced Sleep Metrics 
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Sleep Metrics",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "BMS-grade sleep orchestrator with Midnight Wanderer tracking, HTML Dashboards, and Predictive Wake Triggers.",
    category: "Health & Wellness",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "BMS Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            if (numRooms > 0) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room & User</th><th style='padding: 8px;'>Status & Score</th><th style='padding: 8px;'>Session Metrics</th><th style='padding: 8px;'>Nightly Movements</th><th style='padding: 8px;'>Active Timers & Health</th></tr>"
                
                def now = new Date().time
                def watchdogMillis = (sensorWatchdogHours != null ? sensorWatchdogHours.toInteger() : 48) * 3600000
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWkndGlobal = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)

                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def isMaster = (i == 1)
                    def numUsers = isMaster ? 2 : 1
                    
                    for (int u = 1; u <= numUsers; u++) {
                        def uId = "${i}_${u}"
                        def uName = settings["userName_${uId}"] ?: (isMaster ? (u == 1 ? "Left Side" : "Right Side") : "Occupant")
                        def vSensor = settings["vibrationSensor_${uId}"]
                        
                        if (!vSensor) continue
                        
                        // BMS Health Check
                        def lastVibGlobal = state.lastVibrationTime?."${uId}" ?: 0
                        def isOffline = (lastVibGlobal > 0 && (now - lastVibGlobal) > watchdogMillis)
                        
                        def cState = state.sleepState?."${uId}" ?: "EMPTY"
                        def stateColor = (cState == "SLEEPING") ? "#0055aa" : (cState == "IN BED" ? "#8e44ad" : (cState == "PENDING ENTRY" ? "#1abc9c" : (cState == "BATHROOM TRIP" ? "#e67e22" : "#27ae60")))
                        if (isOffline) stateColor = "#c0392b"
                        
                        // Efficiency Score Calculation & AI Data
                        def score = calculateEfficiencyScore(uId)
                        def scoreColor = score >= 85 ? "#27ae60" : (score >= 70 ? "#f39c12" : "#c0392b")
                        
                        def mlEnabled = (settings["enableML_${uId}"] != false) // defaults to true
                        def aiStats = ""
                        def aiTimes = ""
                        def tripDisplay = ""
                        def trips = state.bathroomTrips?."${uId}" ?: 0
                        def tripMins = state.bathroomDuration?."${uId}" ?: 0
                        
                        if (mlEnabled) {
                            def averages = calculateUserAverages(uId)
                            def learningDisplay = averages.daysLearned >= 14 ? "🧠 <b>Learned</b>" : "🧠 Learning (${averages.daysLearned}/14)"
                            aiStats = "<br><span style='font-size: 11px; color: #555;'>${learningDisplay} | 7D Score: <b>${averages.avgScore7 ?: "--"}%</b></span>"
                            def todayAvgOut = isWkndGlobal ? averages.avgOutWe : averages.avgOutWd
                            aiTimes = "<br><span style='font-size: 11px; color: #555;'>Avg In: <b>${formatMinutesFromNoon(averages.avgInBed)}</b> | Avg Out: <b>${formatMinutesFromNoon(todayAvgOut)}</b></span>"
                            tripDisplay = "<br><span style='font-size: 11px; color: #8e44ad;'>Trips: ${trips}x (${tripMins}m) | Avg: <b>${averages.avgTrips ?: "--"}</b></span>"
                        } else {
                            aiStats = "<br><span style='font-size: 11px; color: #888;'><i>🧠 AI Disabled</i></span>"
                            tripDisplay = "<br><span style='font-size: 11px; color: #8e44ad;'>Trips: ${trips}x (${tripMins}m)</span>"
                        }
                        
                        def statusDisplay = "<b><span style='color: ${stateColor}; font-size: 14px;'>${isOffline ? "OFFLINE" : cState}</span></b>"
                        if (score > 0) statusDisplay += "<br><span style='font-size: 12px; font-weight: bold; color: ${scoreColor};'>Sleep Score: ${score}%</span>"
                        statusDisplay += aiStats

                        def inBedTimeStr = state.inBedTime?."${uId}" ? formatTimestamp(state.inBedTime."${uId}") : "--:--"
                        def asleepTimeStr = state.asleepTime?."${uId}" ? formatTimestamp(state.asleepTime."${uId}") : "--:--"
                        
                        // Live Duration Tracking
                        def liveInBed = 0
                        def liveAsleep = 0
                        if (cState == "IN BED" || cState == "SLEEPING" || cState == "PENDING ENTRY") {
                            if (state.inBedTime?."${uId}") liveInBed = ((now - state.inBedTime."${uId}") / 60000).toInteger()
                            if (state.asleepTime?."${uId}") liveAsleep = ((now - state.asleepTime."${uId}") / 60000).toInteger()
                        } else {
                            liveInBed = state.lastSessionInBed?."${uId}" ?: 0
                            liveAsleep = state.lastSessionAsleep?."${uId}" ?: 0
                        }
                        
                        def durDisplay = ""
                        if (cState == "SLEEPING") durDisplay = "<br><span style='color: #0055aa; font-weight: bold;'>Sleep Duration: ${formatDuration(liveAsleep)}</span>"
                        else if (cState == "IN BED" || cState == "PENDING ENTRY") durDisplay = "<br><span style='color: #8e44ad; font-weight: bold;'>Time In Bed: ${formatDuration(liveInBed)}</span>"
                        else if (cState == "BATHROOM TRIP") {
                            def awayTime = state.lastExitTime?."${uId}" ? ((now - state.lastExitTime."${uId}") / 60000).toInteger() : 0
                            durDisplay = "<br><span style='color: #e67e22; font-weight: bold;'>Away: ${awayTime}m</span>"
                        }
                        else if (cState == "EMPTY" && liveInBed > 0) durDisplay = "<br><span style='color: #27ae60; font-weight: bold;'>Last Sleep: ${formatDuration(liveAsleep)}</span>"
                        
                        def metricsDisplay = "Entry: <b>${inBedTimeStr}</b><br>Sleep: <b>${asleepTimeStr}</b>${durDisplay}${tripDisplay}${aiTimes}"
                        
                        def moves = state.movements?."${uId}" ?: 0
                        def movesDisplay = "<span style='color: ${moves > 10 ? "#e67e22" : "#333"}; font-weight: bold;'>${moves} Events</span>"
                        
                        def timers = []
                        if (isOffline) {
                            timers << "<span style='color: red; font-size: 11px;'>⚠️ Sensor Stale</span>"
                        } else {
                            def deafenedUntil = state.deafenedUntil?."${uId}" ?: 0
                            if (now < deafenedUntil) timers << "<span style='color: #e67e22; font-size: 11px;'>🛡️ Kinetic Shield</span>"
                            
                            def lastMove = state.lastVibrationTime?."${uId}" ?: 0
                            if (cState == "IN BED" && (now - lastMove) < ((fallAsleepThreshold ?: 15) * 60000)) {
                                def rem = (((fallAsleepThreshold ?: 15) * 60000) - (now - lastMove)) / 60000
                                timers << "<span style='color: #3498db; font-size: 11px;'>Settling: ${rem.toInteger()}m</span>"
                            }
                            if (cState == "PENDING ENTRY") {
                                def pendingStart = state.pendingEntryTime?."${uId}" ?: now
                                def abWait = state.pendingAntiBounceWait?."${uId}" != null ? state.pendingAntiBounceWait["${uId}"] : (settings.antiBounceWait ?: 3)
                                def rem = ((abWait * 60000) - (now - pendingStart)) / 60000
                                timers << "<span style='color: #1abc9c; font-size: 11px;'>Verifying: ${Math.max(0, rem.toInteger())}m</span>"
                            }
                            if (isSettlingLockActive(uId)) {
                                def lockStart = state.inBedTime?."${uId}" ?: now
                                def rem = (((settlingLockTime ?: 30) * 60000) - (now - lockStart)) / 60000
                                timers << "<span style='color: #e74c3c; font-size: 11px;'>🔒 Locked: ${Math.max(0, rem.toInteger())}m</span>"
                            }
                            
                            if (cState == "EMPTY" && state.roomEmptyTime?."${i}" > 0) {
                                def wakeMins = settings["wakeDelay_${i}"] != null ? settings["wakeDelay_${i}"].toInteger() : 45
                                def wakeMillis = wakeMins * 60000
                                if ((now - state.roomEmptyTime["${i}"]) < wakeMillis) {
                                    def rem = (wakeMillis - (now - state.roomEmptyTime["${i}"])) / 60000
                                    timers << "<span style='color: #8e44ad; font-size: 11px;'>Wake Auth: ${rem.toInteger()}m</span>"
                                }
                            }
                        }

                        def timerStr = timers ? timers.join("<br>") : "<span style='color: #95a5a6; font-size: 11px;'>Monitored</span>"
                        
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${getRoomName(i)}</b><br><span style='font-size: 11px;'>${uName}</span></td><td style='padding: 8px;'>${statusDisplay}</td><td style='padding: 8px;'>${metricsDisplay}</td><td style='padding: 8px;'>${movesDisplay}</td><td style='padding: 8px;'>${timerStr}</td></tr>"
                    }
                }
                statusText += "</table>"
                
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : (isTrackingAllowed() ? "<span style='color: green; font-weight: bold;'>ACTIVE TRACKING</span>" : "<span style='color: #e65100; font-weight: bold;'>RESTRICTED (Out of Bounds)</span>")
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
                statusText += "<b>System Tracking:</b> ${globalStatus}"
                statusText += "</div>"
                
                paragraph statusText
            }
        }

        section("System Event History") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry ->
                    histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>"
                }
                histHtml += "</div>"
                paragraph histHtml
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
        }
        
        section("Global Restrictions (Time & Mode)") {
            input "activeSleepModes", "mode", title: "Allowed Sleep Modes", multiple: true, required: false
            input "sleepStartTime", "time", title: "Tracking Start Time", required: false
            input "sleepEndTime", "time", title: "Tracking End Time", required: false
        }
        
        section("Global BMS Controls") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            input "numRooms", "number", title: "Number of Bedrooms (1-3)", required: true, defaultValue: 1, range: "1..3", submitOnChange: true
            
            paragraph "<b>Smart Logic Configuration</b>"
            input "vibrationsToEnterBed", "number", title: "Vibrations Required to Enter Bed", defaultValue: 1, required: true
            input "fallAsleepThreshold", "number", title: "Fall Asleep Duration (Minutes)", defaultValue: 15
            input "exitBedThreshold", "number", title: "Bed Exit Delay (Minutes)", defaultValue: 5
            input "stitchingWindow", "number", title: "Standard Stitching Window (Minutes)", defaultValue: 15
            input "restlessThreshold", "number", title: "Restless Movement Log Threshold (Events)", defaultValue: 5, required: false
            
            paragraph "<b>Advanced Signal Processing (New)</b>"
            input "enableSettlingLock", "bool", title: "🔒 Enable Settling Lock?", defaultValue: true, submitOnChange: true, description: "Completely locks the user IN BED for a set time after entry to prevent tossing/adjusting from causing false exits."
            if (enableSettlingLock) {
                input "settlingLockTime", "number", title: "Settling Lock Duration (Mins)", defaultValue: 30
            }

            input "enableGhostFilter", "bool", title: "👻 Enable Pre-Emptive Presence Lockout?", defaultValue: true, description: "Ignores bed exits if the room motion sensor was already active BEFORE the bed vibration stopped (blocks parents checking on kids)."
            
            input "enableAntiBounce", "bool", title: "🛏️ Enable Sustained Entry Verification?", defaultValue: true, submitOnChange: true, description: "Wait a few minutes after bed entry to ensure user isn't just folding laundry before committing to IN BED state."
            if (enableAntiBounce) {
                input "antiBounceWait", "number", title: "Verification Wait Time (Mins)", defaultValue: 3
                input "antiBounceMaxMotions", "number", title: "Max Allowed Room Motions During Wait", defaultValue: 2
            }
            
            paragraph "<b>Dynamic Stitching (Time-of-Night Context)</b>"
            input "deepSleepStart", "time", title: "Deep Sleep Window Start", required: false
            input "deepSleepEnd", "time", title: "Deep Sleep Window End", required: false
            input "deepSleepStitchWindow", "number", title: "Deep Sleep Stitching Window (Mins)", defaultValue: 45, description: "Allows much longer bathroom/baby trips during dead of night without breaking the session."

            paragraph "<b>Cross-Talk Cancellation (The Partner Shield)</b>"
            input "enableCrossTalk", "bool", title: "Enable Multi-User Kinetic Shielding?", defaultValue: true, submitOnChange: true
            if (enableCrossTalk) {
                input "crossTalkDeafenTime", "number", title: "Shield Duration (Seconds)", defaultValue: 60, required: true
            }
        }
        
        section("Advanced Features") {
            input "sensorWatchdogHours", "number", title: "Sensor Watchdog (Hours)", defaultValue: 48
            input "enableTelemetryTracking", "bool", title: "Enable Telemetry Dashboard?", defaultValue: true
            input "btnForceReset", "button", title: "🔄 Force Reset All Beds to EMPTY"
        }

        if (numRooms > 0) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                section("${getRoomName(i)}") { href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${getRoomName(i)}") }
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def isMaster = (rNum == 1)
    
    dynamicPage(name: "roomPage", title: "Room Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Identification") {
            input "roomName_${rNum}", "text", title: "Custom Room Name", defaultValue: (isMaster ? "Master Bedroom" : "Bedroom ${rNum}"), submitOnChange: true
        }
        
        section("Room Orchestrator & Controls") {
            input "goodNightSwitch_${rNum}", "capability.switch", title: "Room Good Night Switch", required: false
            input "enableOrchestrator_${rNum}", "bool", title: "Enable Room Orchestrator?", defaultValue: false, submitOnChange: true
            
            if (settings["enableOrchestrator_${rNum}"]) {
                input "goodNightDelay_${rNum}", "number", title: "In-Bed Time to Trigger Switch ON (Mins)", defaultValue: 30
            }
            input "wakeDelay_${rNum}", "number", title: "Wake Confirmation Timeout (Mins)", defaultValue: 45
        }
        
        section("Fallback Room Sensors") {
            input "motionSensor_${rNum}", "capability.motionSensor", title: "Fallback Room Motion Sensor", required: true
            input "bathroomMotion_${rNum}", "capability.motionSensor", title: "Fallback En-Suite Bathroom Motion", required: false
        }
        
        def users = isMaster ? 2 : 1
        for (int u = 1; u <= users; u++) {
            section("User ${u} Settings") {
                input "userName_${rNum}_${u}", "text", title: "Name", defaultValue: (isMaster ? (u == 1 ? "Shane" : "Christy") : "User"), submitOnChange: true
                input "vibrationSensor_${rNum}_${u}", "capability.accelerationSensor", title: "Vibration Sensor", required: true
                
                input "enableML_${rNum}_${u}", "bool", title: "🧠 Enable AI Behavioral Learning?", defaultValue: true, description: "Builds a rolling profile to predict sleep/wake times. Turn OFF for Guest Rooms."

                def dni = "ASM_INFO_${rNum}_${u}"
                if (getChildDevice(dni)) {
                    paragraph "✅ <b>Dashboard Info Device Linked.</b>"
                } else {
                    input "btnCreateInfo_${rNum}_${u}", "button", title: "➕ Create Dashboard Info Device for ${settings["userName_${rNum}_${u}"]}"
                }

                def dniSw = "ASM_SW_${rNum}_${u}"
                if (getChildDevice(dniSw)) {
                    paragraph "✅ <b>User Virtual Switch Linked.</b>"
                } else {
                    input "btnCreateSwitch_${rNum}_${u}", "button", title: "➕ Create User Virtual Switch"
                }
            }
            
            section("User ${u} Advanced Wake Tracking") {
                input "parentalGuard_${rNum}_${u}", "bool", title: "🛡️ Enable Parental Guard (Strict Exit)?", defaultValue: false
                input "kineticDelaySeconds_${rNum}_${u}", "number", title: "⏱️ Kinetic Speed Limit (Seconds)", defaultValue: 3, description: "Minimum physically possible time it takes to travel between Step 1 and Step 2."
                input "wakeMotion1_${rNum}_${u}", "capability.motionSensor", title: "Sequence Step 1: Bedside Motion", required: false
                input "wakeMotion2_${rNum}_${u}", "capability.motionSensor", title: "Sequence Step 2: Room Exit or Hallway Motion", required: false
                input "bathroomPathMotion_${rNum}_${u}", "capability.motionSensor", title: "Alternative Path: Bathroom Motion", required: false
                input "showerMotion_${rNum}_${u}", "capability.motionSensor", title: "Terminal Wake: Shower Motion Sensor", required: false, description: "If triggered within 10 mins of bed exit or bathroom trip, confirms final wake-up."
                
                paragraph "<b>Expected Wake Windows</b>"
                input "weekdayWakeStart_${rNum}_${u}", "time", title: "Weekday Wake Start", required: false
                input "weekdayWakeEnd_${rNum}_${u}", "time", title: "Weekday Wake End", required: false
                input "weekendWakeStart_${rNum}_${u}", "time", title: "Weekend Wake Start", required: false
                input "weekendWakeEnd_${rNum}_${u}", "time", title: "Weekend Wake End", required: false
            }
        }
    }
}

def installed() {
    log.info "Advanced Sleep Metrics Installed."
    initialize()
}

def updated() {
    log.info "Advanced Sleep Metrics Updated."
    unsubscribe()
    unschedule()
    initialize()
}

// --- SELF-HEALING ENGINE ---
def ensureStateMaps() {
    if (state.sleepState == null) state.sleepState = [:]
    if (state.inBedTime == null) state.inBedTime = [:]
    if (state.asleepTime == null) state.asleepTime = [:]
    if (state.lastSessionInBed == null) state.lastSessionInBed = [:]
    if (state.lastSessionAsleep == null) state.lastSessionAsleep = [:]
    if (state.lastVibrationTime == null) state.lastVibrationTime = [:]
    if (state.lastRoomMotionTime == null) state.lastRoomMotionTime = [:]
    if (state.lastValidExitMotion == null) state.lastValidExitMotion = [:]
    if (state.lastExitTime == null) state.lastExitTime = [:]
    if (state.pendingExit == null) state.pendingExit = [:]
    if (state.exitSequenceProgress == null) state.exitSequenceProgress = [:] 
    if (state.sequenceStep1Time == null) state.sequenceStep1Time = [:]
    if (state.movements == null) state.movements = [:]
    if (state.roomEmptyTime == null) state.roomEmptyTime = [:]
    if (state.deafenedUntil == null) state.deafenedUntil = [:]
    if (state.bathroomTrips == null) state.bathroomTrips = [:]
    if (state.bathroomDuration == null) state.bathroomDuration = [:]
    if (state.telemetry == null) state.telemetry = [:]
    if (state.historyLog == null) state.historyLog = []
    if (state.entryVibrationCount == null) state.entryVibrationCount = [:]
    if (state.roomGoodNightTriggered == null) state.roomGoodNightTriggered = [:]
    if (state.pendingEntryTime == null) state.pendingEntryTime = [:]
    if (state.pendingRoomMotions == null) state.pendingRoomMotions = [:]
    if (state.pendingAntiBounceWait == null) state.pendingAntiBounceWait = [:]
    if (state.dailyStats == null) state.dailyStats = [:]
}

def initialize() {
    ensureStateMaps()
    
    for (int i = 1; i <= 3; i++) {
        if (!state.roomEmptyTime["${i}"]) state.roomEmptyTime["${i}"] = 0
        if (!state.lastRoomMotionTime["${i}"]) state.lastRoomMotionTime["${i}"] = 0
        if (!state.roomGoodNightTriggered["${i}"]) state.roomGoodNightTriggered["${i}"] = false
        
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (!state.sleepState["${uId}"]) state.sleepState["${uId}"] = "EMPTY"
            if (!state.lastValidExitMotion["${uId}"]) state.lastValidExitMotion["${uId}"] = 0
            if (!state.deafenedUntil["${uId}"]) state.deafenedUntil["${uId}"] = 0
            if (!state.bathroomTrips["${uId}"]) state.bathroomTrips["${uId}"] = 0
            if (!state.bathroomDuration["${uId}"]) state.bathroomDuration["${uId}"] = 0
            if (!state.entryVibrationCount["${uId}"]) state.entryVibrationCount["${uId}"] = 0
            if (!state.exitSequenceProgress["${uId}"]) state.exitSequenceProgress["${uId}"] = 0
            if (!state.sequenceStep1Time["${uId}"]) state.sequenceStep1Time["${uId}"] = 0
            if (!state.pendingRoomMotions["${uId}"]) state.pendingRoomMotions["${uId}"] = 0
            if (!state.pendingAntiBounceWait["${uId}"]) state.pendingAntiBounceWait["${uId}"] = 0
            if (!state.dailyStats["${uId}"]) state.dailyStats["${uId}"] = []
            if (!state.telemetry["${uId}"] || !state.telemetry["${uId}"].today) {
                state.telemetry["${uId}"] = [today: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0], overall: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]]
            }
            
            if (settings["wakeMotion1_${uId}"]) subscribe(settings["wakeMotion1_${uId}"], "motion", sequenceMotionHandler)
            if (settings["wakeMotion2_${uId}"]) subscribe(settings["wakeMotion2_${uId}"], "motion", sequenceMotionHandler)
            if (settings["bathroomPathMotion_${uId}"]) subscribe(settings["bathroomPathMotion_${uId}"], "motion", sequenceMotionHandler)
            if (settings["showerMotion_${uId}"]) subscribe(settings["showerMotion_${uId}"], "motion", sequenceMotionHandler)
        }
    }
    
    schedule("0 0 12 * * ?", "middayReset")
    schedule("0 0/5 * * * ?", "orchestrateRooms") 
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["motionSensor_${i}"]) subscribe(settings["motionSensor_${i}"], "motion", fallbackMotionHandler)
        if (settings["bathroomMotion_${i}"]) subscribe(settings["bathroomMotion_${i}"], "motion", fallbackBathroomMotionHandler)
        if (settings["goodNightSwitch_${i}"]) {
            subscribe(settings["goodNightSwitch_${i}"], "switch.on", goodNightOnHandler)
            subscribe(settings["goodNightSwitch_${i}"], "switch.off", goodNightOffHandler)
        }
        
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def vSensor = settings["vibrationSensor_${i}_${u}"]
            if (vSensor) subscribe(vSensor, "acceleration", vibrationHandler)
        }
    }
}

// --- AI AVERAGING ENGINE ---
def calculateUserAverages(uId) {
    ensureStateMaps()
    def stats = state.dailyStats?."${uId}" ?: []
    def days = stats.size()
    if (days == 0) return [daysLearned: 0, avgInBed: null, avgOutWd: null, avgOutWe: null, avgScore7: null, avgTrips: null]
    
    def totalIn = 0
    def totalOutWd = 0, wdCount = 0
    def totalOutWe = 0, weCount = 0
    def totalTrips = 0
    
    stats.each { s ->
        totalIn += (s.inBedMins ?: 0)
        totalTrips += (s.trips ?: 0)
        if (s.isWeekend) {
            totalOutWe += (s.outBedMins ?: 0)
            weCount++
        } else {
            totalOutWd += (s.outBedMins ?: 0)
            wdCount++
        }
    }
    
    def score7 = 0
    def score7Count = 0
    def recent7 = stats.reverse().take(7)
    recent7.each { s ->
        score7 += (s.score ?: 0)
        score7Count++
    }
    
    return [
        daysLearned: days,
        avgInBed: (totalIn / days).toInteger(),
        avgOutWd: wdCount > 0 ? (totalOutWd / wdCount).toInteger() : null,
        avgOutWe: weCount > 0 ? (totalOutWe / weCount).toInteger() : null,
        avgScore7: score7Count > 0 ? (score7 / score7Count).toInteger() : 0,
        avgTrips: Math.round((totalTrips / days) * 10) / 10.0
    ]
}

def getMinutesFromNoon(timestamp) {
    if (!timestamp) return 0
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTimeInMillis(timestamp as Long)
    def hours = cal.get(Calendar.HOUR_OF_DAY)
    def mins = cal.get(Calendar.MINUTE)
    def shiftedHours = (hours + 12) % 24
    return (shiftedHours * 60) + mins
}

def formatMinutesFromNoon(mins) {
    if (mins == null) return "--:--"
    def unshiftedHours = (mins.toInteger() / 60).toInteger()
    def actualHours = (unshiftedHours - 12 + 24) % 24
    def actualMins = mins.toInteger() % 60
    
    def ampm = actualHours >= 12 ? "PM" : "AM"
    def displayHours = actualHours % 12
    if (displayHours == 0) displayHours = 12
    
    return "${displayHours}:${actualMins.toString().padLeft(2, '0')} ${ampm}"
}

// --- CORE BMS LOGIC ---

def vibrationHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def uId = getUserIdFromDevice(evt.device.id)
    if (!uId) return
    
    def now = new Date().time
    def rNum = uId.split('_')[0]
    
    def deafened = state.deafenedUntil?."${uId}" ?: 0
    if (now < deafened) return

    if (evt.value == "active") {
        state.lastVibrationTime["${uId}"] = now
        state.pendingExit["${uId}"] = 0
        state.exitSequenceProgress["${uId}"] = 0 
        
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") {
            if (cState == "EMPTY" && !isTrackingAllowed()) return
            
            // --- AI ENTRY PREDICTION ---
            def mlEnabled = (settings["enableML_${uId}"] != false)
            def reqVibs = settings.vibrationsToEnterBed != null ? settings.vibrationsToEnterBed.toInteger() : 1
            def smartEntry = false
            
            if (mlEnabled) {
                def averages = calculateUserAverages(uId)
                if (averages.daysLearned >= 14 && averages.avgInBed != null) {
                    def nowMins = getMinutesFromNoon(now)
                    if (Math.abs(nowMins - averages.avgInBed) <= 60) {
                        reqVibs = 1 // Highly confident it's them based on AI
                        smartEntry = true
                    }
                }
            }
            
            state.entryVibrationCount["${uId}"] = (state.entryVibrationCount["${uId}"] ?: 0) + 1
            
            if (state.entryVibrationCount["${uId}"] < reqVibs) {
                runIn(600, "clearEntryVibrationCount", [data: [uId: uId], overwrite: true])
                return
            }
            
            state.entryVibrationCount["${uId}"] = 0
            def lastExit = state.lastExitTime?."${uId}" ?: 0
            def stitchMillis = getDynamicStitchMillis()
            def uName = getUserName(uId)
            
            if (lastExit > 0 && (now - lastExit) < stitchMillis) {
                def awayMins = ((now - lastExit) / 60000).toInteger()
                if (cState == "BATHROOM TRIP") {
                    state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                    state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + awayMins
                    addToHistory("BATHROOM RETURN: ${uName} returned to bed (Away: ${awayMins}m). Session seamlessly stitched.")
                } else {
                    addToHistory("SESSION STITCHED: ${uName} returned within window (Away: ${awayMins}m). Continuing previous session.")
                }
                state.sleepState["${uId}"] = "IN BED"
                updateVirtualSwitch(uId, "on")
            } else {
                state.inBedTime["${uId}"] = now
                state.movements["${uId}"] = 0
                state.asleepTime["${uId}"] = null
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                
                if (settings["enableAntiBounce"]) {
                    def abWait = settings.antiBounceWait != null ? settings.antiBounceWait.toInteger() : 3
                    if (smartEntry) {
                        abWait = 1 // AI bypass: accelerate verification
                        addToHistory("🧠 AI PREDICTION: ${uName} activity matches learned bedtime. Fast-tracking Anti-Bounce verification.")
                    } else {
                        addToHistory("SUSTAINED ENTRY VERIFICATION: ${uName} met entry threshold. Verifying against room activity for ${abWait} minutes...")
                    }
                    
                    state.sleepState["${uId}"] = "PENDING ENTRY"
                    state.pendingEntryTime["${uId}"] = now
                    state.pendingRoomMotions["${uId}"] = 0
                    state.pendingAntiBounceWait["${uId}"] = abWait
                    runIn(abWait * 60, "verifyBedEntry", [data: [uId: uId, roomNum: rNum], overwrite: true])
                } else {
                    if (smartEntry) addToHistory("🧠 AI PREDICTION: ${uName} activity matches learned bedtime. Instant Entry applied.")
                    else addToHistory("NEW SESSION: ${uName} entered bed.")
                    state.sleepState["${uId}"] = "IN BED"
                    updateVirtualSwitch(uId, "on")
                }
            }
            
            state.roomEmptyTime["${rNum}"] = 0 
            if (settings["enableOrchestrator_${rNum}"] && !state.roomGoodNightTriggered["${rNum}"]) {
                runIn(10, "orchestrateRooms", [overwrite: true])
            }
        } else if (cState == "SLEEPING") {
            state.movements["${uId}"] = (state.movements["${uId}"] ?: 0) + 1
        }
        
        if (state.sleepState["${uId}"] == "IN BED") {
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        }
        updateInfoDevice(uId)
    } else {
        def rMotion = settings["motionSensor_${rNum}"]?.currentValue("motion")
        if (settings["enableGhostFilter"] && rMotion == "active") {
            logTelemetryEvent(uId, "ghostBlocks")
            addToHistory("GHOST FILTER ACTIVE: External presence detected during ${getUserName(uId)}'s bed movement. Exit sequence pre-emptively locked out.")
        } else {
            state.pendingExit["${uId}"] = now
            
            // --- AI WAKE PREDICTION ---
            def mlEnabled = (settings["enableML_${uId}"] != false)
            def actualThresh = exitBedThreshold != null ? exitBedThreshold.toInteger() : 5
            def smartWake = false
            
            if (mlEnabled) {
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                def averages = calculateUserAverages(uId)
                def targetAvgOut = isWknd ? averages.avgOutWe : averages.avgOutWd
                
                if (averages.daysLearned >= 14 && targetAvgOut != null) {
                    def nowMins = getMinutesFromNoon(now)
                    if (Math.abs(nowMins - targetAvgOut) <= 60) {
                        actualThresh = Math.max(1, (actualThresh / 2).toInteger())
                        smartWake = true
                    }
                }
            }
            runIn(actualThresh * 60, "evaluateBedExit", [data: [uId: uId, roomNum: rNum, thresh: actualThresh, ai: smartWake], overwrite: true])
        }
    }
}

// --- NEW ANTI-BOUNCE VERIFICATION ---
def verifyBedEntry(data) {
    ensureStateMaps()
    def uId = data.uId
    def rNum = data.roomNum
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "PENDING ENTRY") {
        def motions = state.pendingRoomMotions["${uId}"] ?: 0
        def maxMotions = settings["antiBounceMaxMotions"] != null ? settings["antiBounceMaxMotions"].toInteger() : 2
        
        if (motions <= maxMotions) {
            addToHistory("VERIFIED ENTRY: Room settled down. Committing ${getUserName(uId)} to IN BED state retroactively.")
            state.sleepState["${uId}"] = "IN BED"
            updateVirtualSwitch(uId, "on")
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        } else {
            addToHistory("ENTRY ABORTED: Excessive room motion detected during Anti-Bounce wait. Assuming ${getUserName(uId)} was awake/active.")
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
        }
        updateInfoDevice(uId)
    }
}

// --- SETTLING LOCK LOGIC ---
def isSettlingLockActive(uId) {
    if (!settings["enableSettlingLock"]) return false
    def inBed = state.inBedTime?."${uId}" ?: 0
    if (inBed == 0) return false
    def lockMillis = (settings["settlingLockTime"] != null ? settings["settlingLockTime"].toInteger() : 30) * 60000
    def now = new Date().time
    return (now - inBed) < lockMillis
}

// --- DYNAMIC STITCHING LOGIC ---
def getDynamicStitchMillis() {
    def baseWindow = (settings["stitchingWindow"] != null ? settings["stitchingWindow"].toInteger() : 15) * 60000
    def deepWindow = (settings["deepSleepStitchWindow"] != null ? settings["deepSleepStitchWindow"].toInteger() : 45) * 60000
    
    if (settings["deepSleepStart"] && settings["deepSleepEnd"]) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def start = timeToday(settings["deepSleepStart"], tz)
        def end = timeToday(settings["deepSleepEnd"], tz)
        def nowTime = new Date().time
        
        def inDeepSleep = false
        if (start.time > end.time) {
            inDeepSleep = (nowTime >= start.time || nowTime <= end.time)
        } else {
            inDeepSleep = (nowTime >= start.time && nowTime <= end.time)
        }
        return inDeepSleep ? deepWindow : baseWindow
    }
    return baseWindow
}

def evaluateBathroomTimeout(data) {
    ensureStateMaps()
    def uId = data.uId
    if (state.sleepState["${uId}"] == "BATHROOM TRIP") {
        addToHistory("BATHROOM TRIP EXPIRED: ${getUserName(uId)} did not return within the stitching window. Assuming terminal wake.")
        // Roll back the trip counter since it was just a final wake
        state.bathroomTrips["${uId}"] = Math.max(0, (state.bathroomTrips["${uId}"] ?: 1) - 1)
        forceBedExit(uId, data.roomNum, true)
    }
}

// --- ADVANCED MOTION SEQUENCE HANDLER ---
def sequenceMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def devId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "EMPTY") continue
            
            if (isSettlingLockActive(uId)) {
                logTelemetryEvent(uId, "settlingLockBlocks")
                continue
            }

            def m1 = settings["wakeMotion1_${uId}"]
            def m2 = settings["wakeMotion2_${uId}"]
            def bPath = settings["bathroomPathMotion_${uId}"]
            def sMotion = settings["showerMotion_${uId}"]

            def pending = state.pendingExit["${uId}"] ?: 0
            def progress = state.exitSequenceProgress["${uId}"] ?: 0

            // --- TERMINAL WAKE OVERRIDE (SHOWER) ---
            if (sMotion?.id == devId) {
                def isRecentExit = false
                if (pending > 0 && (now - pending) <= 600000) isRecentExit = true // Exited bed within 10 mins
                if (cState == "BATHROOM TRIP" && state.lastExitTime["${uId}"] && (now - state.lastExitTime["${uId}"]) <= 600000) isRecentExit = true // On a bathroom trip that started < 10 mins ago
                
                if (isRecentExit) {
                    addToHistory("SHOWER WAKE CONFIRMED: ${getUserName(uId)} entered the shower. Terminal Wake applied.")
                    if (cState == "BATHROOM TRIP") {
                        // Roll back the false bathroom trip counter since this is a final wake
                        state.bathroomTrips["${uId}"] = Math.max(0, (state.bathroomTrips["${uId}"] ?: 1) - 1)
                    }
                    forceBedExit(uId, "${i}")
                    continue
                }
            }

            // Normal Sequence Trackers
            if (pending > 0 && (now - pending) < 300000) {
                if (progress == 0 && m1?.id == devId) {
                    state.exitSequenceProgress["${uId}"] = 1
                    state.sequenceStep1Time["${uId}"] = now
                    addToHistory("SEQUENCE: ${getUserName(uId)} triggered Step 1 (${m1.displayName}). Monitoring path...")
                    handlePartnerShield(uId, "${i}") 
                }
                else if (progress == 1 && m2?.id == devId) {
                    def step1Time = state.sequenceStep1Time["${uId}"] ?: 0
                    def kineticDelay = (settings["kineticDelaySeconds_${uId}"] ?: 3) * 1000
                    
                    if ((now - step1Time) >= kineticDelay) {
                        state.exitSequenceProgress["${uId}"] = 2
                        addToHistory("SEQUENCE CONFIRMED: ${getUserName(uId)} triggered Step 2 (${m2.displayName}). WAKE PATH validated.")
                        forceBedExit(uId, "${i}") 
                    } else {
                        addToHistory("KINETIC FILTER: Step 2 triggered impossibly fast (${now - step1Time}ms). Rejected as false trigger.")
                    }
                }
                else if (progress == 1 && bPath?.id == devId) {
                    state.exitSequenceProgress["${uId}"] = 3
                    state.lastExitTime["${uId}"] = now 
                    addToHistory("SEQUENCE CONFIRMED: ${getUserName(uId)} triggered Alternative (${bPath.displayName}). BATHROOM TRIP validated.")
                    state.sleepState["${uId}"] = "BATHROOM TRIP"
                    updateInfoDevice(uId)
                    
                    def stitchSecs = (getDynamicStitchMillis() / 1000).toInteger()
                    runIn(stitchSecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: rNum], overwrite: true])
                }
            }
        }
    }
}

def fallbackBathroomMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "bathroomMotion")
    if (!rNum) return
    
    def now = new Date().time
    def users = (rNum == "1") ? 2 : 1
    
    for (int u = 1; u <= users; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "IN BED" || cState == "SLEEPING") {
            def pending = state.pendingExit["${uId}"] ?: 0
            if (pending > 0 && !isSettlingLockActive(uId)) {
                state.lastExitTime["${uId}"] = pending 
                state.sleepState["${uId}"] = "BATHROOM TRIP"
                state.pendingExit["${uId}"] = 0
                state.exitSequenceProgress["${uId}"] = 0
                updateVirtualSwitch(uId, "off")
                addToHistory("BATHROOM MOTION DETECTED: ${getUserName(uId)} went straight to the bathroom. Logging trip.")
                updateInfoDevice(uId)
                
                def stitchSecs = (getDynamicStitchMillis() / 1000).toInteger()
                runIn(stitchSecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: rNum], overwrite: true])
            }
        }
    }
}

def fallbackMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "motionSensor")
    if (!rNum) return
    
    def now = new Date().time
    state.lastRoomMotionTime["${rNum}"] = now

    def users = (rNum == "1") ? 2 : 1
    for (int u = 1; u <= users; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "PENDING ENTRY") {
            state.pendingRoomMotions["${uId}"] = (state.pendingRoomMotions["${uId}"] ?: 0) + 1
            continue
        }
        
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") continue
        
        if (isSettlingLockActive(uId)) {
            logTelemetryEvent(uId, "settlingLockBlocks")
            continue
        }
        
        if (settings["parentalGuard_${uId}"]) {
            logTelemetryEvent(uId, "inBedMotionsIgnored")
            continue
        }
        
        def lastVib = state.lastVibrationTime["${uId}"] ?: 0
        if ((now - lastVib) <= 90000) { 
            state.lastValidExitMotion["${uId}"] = now
            handlePartnerShield(uId, rNum)
            
            def pending = state.pendingExit["${uId}"] ?: 0
            if (pending > 0 && (now - pending) >= ((exitBedThreshold ?: 5) * 60000)) {
                // If the standard exit passes via fallback during motion, handle it
                runIn(1, "evaluateBedExit", [data: [uId: uId, roomNum: rNum], overwrite: true])
            }
        } else {
            logTelemetryEvent(uId, "inBedMotionsIgnored")
        }
    }
}

def clearEntryVibrationCount(data) {
    def uId = data.uId
    if (state.sleepState["${uId}"] == "EMPTY" || state.sleepState["${uId}"] == "BATHROOM TRIP") {
        state.entryVibrationCount["${uId}"] = 0
    }
}

def handlePartnerShield(uId, rNum) {
    def enableShield = settings["enableCrossTalk"] != null ? settings["enableCrossTalk"] : true
    if (!enableShield || rNum != "1") return
    
    def partnerId = (uId == "1_1") ? "1_2" : "1_1"
    def now = new Date().time
    def pState = state.sleepState["${partnerId}"] ?: "EMPTY"
    
    if (pState != "EMPTY") {
        state.deafenedUntil["${partnerId}"] = now + ((crossTalkDeafenTime ?: 60) * 1000)
        if (pState == "SLEEPING") {
            state.movements["${partnerId}"] = Math.max(0, (state.movements["${partnerId}"] ?: 1) - 1)
            logTelemetryEvent(partnerId, "crossTalkAvoided")
        }
    }
}

def forceBedExit(uId, rNum, keepExistingTime = false) {
    ensureStateMaps()
    def now = new Date().time
    
    def exitTime = (keepExistingTime && state.lastExitTime["${uId}"]) ? state.lastExitTime["${uId}"] : now
    state.lastExitTime["${uId}"] = exitTime
    
    def inBed = state.inBedTime["${uId}"] ?: exitTime
    def inBedMins = ((exitTime - inBed) / 60000).toInteger()
    state.lastSessionInBed["${uId}"] = inBedMins
    
    if (state.asleepTime["${uId}"]) {
        state.lastSessionAsleep["${uId}"] = ((exitTime - state.asleepTime["${uId}"]) / 60000).toInteger()
    }
    
    def sleepScore = calculateEfficiencyScore(uId)
    addToHistory("BED EXIT: ${getUserName(uId)} verified out of bed. Session Duration: ${formatDuration(inBedMins)}. Sleep Score: ${sleepScore}%")
    
    state.sleepState["${uId}"] = "EMPTY"
    state.pendingExit["${uId}"] = 0
    state.exitSequenceProgress["${uId}"] = 0
    updateVirtualSwitch(uId, "off")
    updateInfoDevice(uId)
    checkRoomEmptyStatus(rNum)
}

def evaluateBedExit(data) {
    ensureStateMaps()
    def uId = data.uId
    def rNum = data.roomNum
    def thresh = data.thresh != null ? data.thresh.toInteger() : (exitBedThreshold != null ? exitBedThreshold.toInteger() : 5)
    def ai = data.ai ?: false
    
    def now = new Date().time
    def pending = state.pendingExit["${uId}"] ?: 0
    def lastValid = state.lastValidExitMotion["${uId}"] ?: 0
    def progress = state.exitSequenceProgress["${uId}"] ?: 0
    
    if (state.sleepState["${uId}"] == "EMPTY") return

    if (progress == 1 && isWithinWakeWindow(uId)) {
        addToHistory("WINDOW EXIT: ${getUserName(uId)} triggered Step 1 inside Expected Wake Window. Assuming true wake.")
        forceBedExit(uId, rNum)
        return
    }

    if (pending > 0 && (now - pending) >= (thresh * 60000)) {
        if (lastValid > pending || isWithinWakeWindow(uId)) {
            if (ai) addToHistory("🧠 AI PREDICTION: ${getUserName(uId)} learned wake pattern detected. Wake sequence accelerated.")
            forceBedExit(uId, rNum)
        }
    }
}

def isWithinWakeWindow(uId) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def isWeekend = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    
    def wStart = isWeekend ? settings["weekendWakeStart_${uId}"] : settings["weekdayWakeStart_${uId}"]
    def wEnd = isWeekend ? settings["weekendWakeEnd_${uId}"] : settings["weekdayWakeEnd_${uId}"]

    if (!wStart || !wEnd) return false
    
    def start = timeToday(wStart, tz)
    def end = timeToday(wEnd, tz)
    def nowTime = new Date().time
    
    if (start.time > end.time) {
        return (nowTime >= start.time || nowTime <= end.time)
    } else {
        return (nowTime >= start.time && nowTime <= end.time)
    }
}

def orchestrateRooms() {
    ensureStateMaps()
    if (isSystemPaused()) return
    def now = new Date().time
    def watchdogMillis = (sensorWatchdogHours ?: 48) * 3600000

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (!settings["enableOrchestrator_${i}"]) continue
        def gnSwitch = settings["goodNightSwitch_${i}"]
        if (!gnSwitch) continue
        
        if (state.roomGoodNightTriggered["${i}"] || gnSwitch.currentValue("switch") == "on") continue

        def requiredMillis = (settings["goodNightDelay_${i}"] ?: 30) * 60000
        def numUsers = (i == 1) ? 2 : 1
        def totalActiveUsers = 0
        def totalUsersReady = 0

        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (settings["vibrationSensor_${uId}"]) {
                def lastVib = state.lastVibrationTime?."${uId}" ?: 0
                if (lastVib > 0 && (now - lastVib) > watchdogMillis) continue 
                
                totalActiveUsers++
                def cState = state.sleepState["${uId}"] ?: "EMPTY"
                def inBedTime = state.inBedTime?."${uId}" ?: 0
                if (cState == "IN BED" || cState == "SLEEPING") {
                    if (inBedTime > 0 && (now - inBedTime) >= requiredMillis) totalUsersReady++
                }
            }
        }

        if (totalActiveUsers > 0 && totalUsersReady >= totalActiveUsers) {
            addToHistory("ROOM ORCHESTRATOR: All active users in ${getRoomName(i)} met In-Bed threshold. Engaging Good Night Switch.")
            state.roomGoodNightTriggered["${i}"] = true
            gnSwitch.on()
        }
    }
}

def goodNightOnHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            state.roomEmptyTime["${i}"] = 0 
            state.roomGoodNightTriggered["${i}"] = true
            def numUsers = (i == 1) ? 2 : 1
            
            for (int u = 1; u <= numUsers; u++) {
                def uId = "${i}_${u}"
                def cState = state.sleepState["${uId}"] ?: "EMPTY"

                if (cState == "EMPTY" || cState == "PENDING ENTRY") {
                    addToHistory("GOOD NIGHT OVERRIDE: Forcing ${getUserName(uId)} to IN BED.")
                    state.sleepState["${uId}"] = "IN BED"
                    state.inBedTime["${uId}"] = now
                    state.movements["${uId}"] = 0
                    state.asleepTime["${uId}"] = null
                    state.lastVibrationTime["${uId}"] = now
                    state.pendingExit["${uId}"] = now 
                    
                    updateVirtualSwitch(uId, "on")
                    updateInfoDevice(uId)
                    runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
                }
            }
            if (settings["enableOrchestrator_${i}"]) runIn(10, "orchestrateRooms", [overwrite: true])
        }
    }
}

def goodNightOffHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            state.roomGoodNightTriggered["${i}"] = false
            def numUsers = (i == 1) ? 2 : 1
            def externalOverrideTriggered = false
            
            for (int u = 1; u <= numUsers; u++) {
                def uId = "${i}_${u}"
                def cState = state.sleepState["${uId}"] ?: "EMPTY"

                if (cState != "EMPTY") {
                    externalOverrideTriggered = true
                    forceBedExit(uId, "${i}")
                }
            }
            
            if (externalOverrideTriggered) {
                state.roomEmptyTime["${i}"] = now
            }
        }
    }
}

def isTrackingAllowed() {
    if (isSystemPaused()) return false
    
    def modeAllowed = true
    if (activeSleepModes && !activeSleepModes.contains(location.mode)) modeAllowed = false
    
    def timeAllowed = true
    if (sleepStartTime && sleepEndTime) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def start = timeToday(sleepStartTime, tz)
        def end = timeToday(sleepEndTime, tz)
        def now = new Date()
        
        if (start.time > end.time) {
            timeAllowed = (now.time >= start.time || now.time <= end.time)
        } else {
            timeAllowed = (now.time >= start.time && now.time <= end.time)
        }
    }
    
    if (activeSleepModes && !modeAllowed) return false
    if (sleepStartTime && sleepEndTime && !timeAllowed) return false
    return true
}

def evaluateSleepState(data) {
    ensureStateMaps()
    def uId = data.uId
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "IN BED") {
        def now = new Date().time
        def lastVib = state.lastVibrationTime["${uId}"] ?: 0
        if ((now - lastVib) >= ((fallAsleepThreshold ?: 15) * 60000)) {
            addToHistory("SLEEP DETECTED: ${getUserName(uId)} marking as SLEEPING.")
            state.sleepState["${uId}"] = "SLEEPING"
            state.asleepTime["${uId}"] = now
            updateInfoDevice(uId)
        }
    }
}

def checkRoomEmptyStatus(rNum) {
    ensureStateMaps()
    def numUsers = (rNum == "1" || rNum == 1) ? 2 : 1
    def roomIsVacantForWake = true
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (settings["vibrationSensor_${uId}"] && cState != "EMPTY") {
            roomIsVacantForWake = false
            break
        }
    }

    if (roomIsVacantForWake) {
        state.roomEmptyTime["${rNum}"] = new Date().time
        def wakeMins = settings["wakeDelay_${rNum}"] != null ? settings["wakeDelay_${rNum}"].toInteger() : 45
        addToHistory("ROOM EMPTY: ${getRoomName(rNum)} vacant. Starting ${wakeMins}m Wake Confirmation buffer.")
        runIn(wakeMins * 60, "evaluateRoomWake", [data: [roomNum: rNum], overwrite: true])
    }
}

def evaluateRoomWake(data) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def rNum = data.roomNum
    def emptyTime = state.roomEmptyTime["${rNum}"] ?: 0

    if (emptyTime > 0) {
        def numUsers = (rNum == "1" || rNum == 1) ? 2 : 1
        def roomIsVacantForWake = true
        
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${rNum}_${u}"
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (settings["vibrationSensor_${uId}"] && cState != "EMPTY") {
                roomIsVacantForWake = false
                break
            }
        }

        if (roomIsVacantForWake) {
            def gnSwitch = settings["goodNightSwitch_${rNum}"]
            if (gnSwitch && gnSwitch.currentValue("switch") == "on" && settings["enableOrchestrator_${rNum}"]) {
                gnSwitch.off()
                addToHistory("WAKE CONFIRMED: ${getRoomName(rNum)} remained empty for the timeout period. Turning OFF Good Night switch.")
            } else {
                addToHistory("WAKE CONFIRMED: ${getRoomName(rNum)} remained empty for the timeout period.")
            }
            state.roomGoodNightTriggered["${rNum}"] = false
        }
    }
}

// --- BMS MATH & SCORING ---
def calculateEfficiencyScore(uId) {
    ensureStateMaps()
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    def inBed = cState == "EMPTY" ? (state.lastSessionInBed["${uId}"] ?: 0) : ((new Date().time - (state.inBedTime?."${uId}" ?: new Date().time)) / 60000)
    def asleep = cState == "EMPTY" ? (state.lastSessionAsleep["${uId}"] ?: 0) : ((new Date().time - (state.asleepTime?."${uId}" ?: new Date().time)) / 60000)
    
    if (!inBed || inBed < 30) return 0
    def efficiency = (asleep / inBed) * 100
    def movementPenalty = (state.movements?."${uId}" ?: 0) * 2
    def finalScore = (efficiency - movementPenalty).toInteger()
    return Math.max(0, Math.min(100, finalScore))
}

// --- DEVICE CREATION & UPDATE ---
def updateInfoDevice(uId) {
    ensureStateMaps()
    def dni = "ASM_INFO_${uId}"
    def dev = getChildDevice(dni)
    if (!dev) return
    
    def score = calculateEfficiencyScore(uId)
    def status = state.sleepState["${uId}"] ?: "EMPTY"
    
    dev.sendEvent(name: "status", value: status)
    dev.sendEvent(name: "sleepScore", value: score)
    dev.sendEvent(name: "html", value: generateHtmlTile(uId))
}

def generateHtmlTile(uId) {
    ensureStateMaps()
    def uName = getUserName(uId)
    def status = state.sleepState?."${uId}" ?: "EMPTY"
    def score = calculateEfficiencyScore(uId)
    def moves = state.movements?."${uId}" ?: 0
    def color = status == "SLEEPING" ? "#3498db" : (status == "IN BED" ? "#9b59b6" : (status == "PENDING ENTRY" ? "#1abc9c" : (status == "BATHROOM TRIP" ? "#e67e22" : "#2ecc71")))
    
    def html = """
    <div style='background: #1a1a1a; color: white; padding: 15px; border-radius: 12px; font-family: sans-serif; border: 1px solid #333;'>
        <div style='display: flex; justify-content: space-between; align-items: center;'>
            <span style='font-size: 18px; font-weight: bold;'>${uName}</span>
            <span style='background: ${color}; padding: 4px 10px; border-radius: 20px; font-size: 12px;'>${status}</span>
        </div>
        <div style='margin-top: 15px; display: flex; gap: 20px;'>
            <div><span style='color: #888; font-size: 11px;'>SLEEP SCORE</span><br><span style='font-size: 24px; font-weight: bold; color: ${score > 80 ? "#2ecc71" : "#f1c40f"}'>${score}%</span></div>
            <div style='border-left: 1px solid #333; padding-left: 20px;'><span style='color: #888; font-size: 11px;'>MOVEMENTS</span><br><span style='font-size: 24px; font-weight: bold;'>${moves}</span></div>
        </div>
        <div style='margin-top: 10px; font-size: 12px; color: #888;'>
            Session: ${formatDuration(state.lastSessionAsleep?."${uId}" ?: 0)} asleep
        </div>
    </div>
    """
    return html
}

// --- BOILERPLATE & HELPERS ---
def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn.startsWith("btnCreateInfo_")) {
        def parts = btn.split("_")
        def dni = "ASM_INFO_${parts[2]}_${parts[3]}"
        def name = "Sleep Info - ${getUserName(parts[2]+'_'+parts[3])}"
        addChildDevice("hubitat", "Virtual Omni Sensor", dni, null, [name: name, label: name])
    } else if (btn.startsWith("btnCreateSwitch_")) {
        def parts = btn.split("_")
        def dni = "ASM_SW_${parts[2]}_${parts[3]}"
        def name = "(Virtual) ${getUserName(parts[2]+'_'+parts[3])} In Bed"
        addChildDevice("hubitat", "Virtual Switch", dni, null, [name: name, label: name])
    } else if (btn == "btnForceReset") {
        forceResetAllBeds()
    } else if (btn == "btnRefresh") {
        log.info "BMS Refresh Triggered."
    }
}

def logTelemetryEvent(uId, eventType) {
    if (!enableTelemetryTracking) return
    ensureStateMaps()
    if (!state.telemetry["${uId}"]) {
        state.telemetry["${uId}"] = [today: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0], overall: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]]
    }
    state.telemetry["${uId}"].today[eventType] = (state.telemetry["${uId}"].today[eventType] ?: 0) + 1
    state.telemetry["${uId}"].overall[eventType] = (state.telemetry["${uId}"].overall[eventType] ?: 0) + 1
}

def forceResetAllBeds() {
    ensureStateMaps()
    def numR = numRooms ? numRooms.toInteger() : 1
    for (int i = 1; i <= numR; i++) {
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
            state.asleepTime["${uId}"] = null
            state.pendingExit["${uId}"] = 0
            state.movements["${uId}"] = 0
            state.lastValidExitMotion["${uId}"] = 0
            state.bathroomTrips["${uId}"] = 0
            state.bathroomDuration["${uId}"] = 0
            state.entryVibrationCount["${uId}"] = 0
            state.exitSequenceProgress["${uId}"] = 0
            state.pendingEntryTime["${uId}"] = 0
            state.pendingRoomMotions["${uId}"] = 0
            state.pendingAntiBounceWait["${uId}"] = 0
            updateVirtualSwitch(uId, "off")
            updateInfoDevice(uId)
        }
        state.roomEmptyTime["${i}"] = 0
        state.roomGoodNightTriggered["${i}"] = false
    }
    addToHistory("SYSTEM: Forced all beds to EMPTY via manual button override.")
}

def middayReset() {
    ensureStateMaps()
    state.lastResetTime = new Date().format("MM/dd HH:mm", location.timeZone)
    
    for (int i = 1; i <= 3; i++) {
        state.roomEmptyTime["${i}"] = 0
        state.roomGoodNightTriggered["${i}"] = false
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (state.telemetry?."${uId}"?.today) {
                state.telemetry["${uId}"].today = [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]
            }
            
            // AI Ledger Logging (Lock in yesterday's data before clearing) - Bypassed if ML is off
            def mlEnabled = (settings["enableML_${uId}"] != false)
            if (mlEnabled) {
                def inBed = state.inBedTime["${uId}"]
                def outBed = state.lastExitTime["${uId}"]
                if (inBed && outBed) {
                    def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                    def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                    
                    def sessionData = [
                        inBedMins: getMinutesFromNoon(inBed),
                        outBedMins: getMinutesFromNoon(outBed),
                        score: calculateEfficiencyScore(uId),
                        trips: state.bathroomTrips["${uId}"] ?: 0,
                        isWeekend: isWknd
                    ]
                    if (state.dailyStats["${uId}"] == null) state.dailyStats["${uId}"] = []
                    state.dailyStats["${uId}"].add(sessionData)
                    while (state.dailyStats["${uId}"].size() > 14) {
                        state.dailyStats["${uId}"].remove(0)
                    }
                }
            }
            
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "EMPTY" || cState == "BATHROOM TRIP") {
                state.inBedTime["${uId}"] = null
                state.asleepTime["${uId}"] = null
                state.movements["${uId}"] = 0
                state.deafenedUntil["${uId}"] = 0
                state.lastValidExitMotion["${uId}"] = 0
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                state.entryVibrationCount["${uId}"] = 0
                state.exitSequenceProgress["${uId}"] = 0 
                state.pendingEntryTime["${uId}"] = 0
                state.pendingRoomMotions["${uId}"] = 0
                state.pendingAntiBounceWait["${uId}"] = 0
                updateVirtualSwitch(uId, "off") 
                state.sleepState["${uId}"] = "EMPTY" 
                updateInfoDevice(uId)
            }
        }
        if (settings["goodNightSwitch_${i}"]?.currentValue("switch") == "on" && settings["enableOrchestrator_${i}"]) {
            settings["goodNightSwitch_${i}"].off()
        }
    }
    
    addToHistory("SYSTEM: Midday Reset completed. AI Ledgers updated. Cleared telemetry and reset Orchestrator locks.")
}

def getUserIdFromDevice(id) {
    for (int i = 1; i <= 3; i++) {
        for (int u = 1; u <= 2; u++) {
            if (settings["vibrationSensor_${i}_${u}"]?.id == id) return "${i}_${u}"
        }
    }
    return null
}

def getRoomNumFromDevice(id, type) {
    for (int i = 1; i <= 3; i++) {
        if (settings["${type}_${i}"]?.id == id) return "${i}"
    }
    return null
}

def getUserName(uId) { return settings["userName_${uId}"] ?: "User" }
def getRoomName(rNum) { return settings["roomName_${rNum}"] ?: "Room ${rNum}" }
def isSystemPaused() { return masterEnableSwitch?.currentValue("switch") == "off" }
def formatTimestamp(ms) { return ms ? new Date(ms as Long).format("h:mm a", location.timeZone) : "--:--" }
def formatDuration(m) { return m >= 60 ? "${(m/60).toInteger()}h ${m%60}m" : "${m}m" }

def updateVirtualSwitch(uId, val) {
    def dev = getChildDevice("ASM_SW_${uId}")
    if (dev) { if (val == "on") dev.on() else dev.off() }
}

def addToHistory(String msg) {
    ensureStateMaps()
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
    log.info "SLEEP HISTORY: [${timestamp}] ${msg}"
}
