/**
 * Advanced Room Good Night
 */
definition(
    name: "Advanced Room Good Night",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Ultimate Good Night controller with Live Sleep Dashboard, Variable Speed Ceiling Fans, Auto-Sleep Quality Tracking, Power-Failure Recovery, Periodic State Enforcement, Hourly Fan Wiggle, and Command History.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Room Good Night</b>", install: true, uninstall: true) {
        
        section("<b>Global Sync & Override Status</b>") {
            paragraph "<div style='font-size:12px; color:#555;'><b>House Sleep Status:</b> Live indicator of active blocking devices and lead room countdowns.</div>"
            
            if (settings.enableGlobalMode) {
                def syncHtml = "<div style='background-color:#fdfdfd; border: 1px solid #ccc; border-radius: 5px; padding: 6px; margin-bottom: 6px; font-size:12px;'>"
                
                // Check blocking devices
                def activeBlockers = []
                if (settings.blockingSwitches) {
                    settings.blockingSwitches.each { bSw ->
                        if (bSw.currentValue("switch") == "on") {
                            activeBlockers << bSw.displayName
                        }
                    }
                }
                
                if (activeBlockers.size() > 0) {
                    syncHtml += "<div style='color:#d9534f; font-weight:bold; margin-bottom:2px;'>⚠️ Night Mode Blocked By:</div>"
                    syncHtml += "<ul style='margin-top:0; padding-left:20px; color:#d9534f; margin-bottom:4px;'>"
                    activeBlockers.each { syncHtml += "<li>${it}</li>" }
                    syncHtml += "</ul>"
                } else {
                    syncHtml += "<div style='color:#28a745; font-weight:bold; margin-bottom:4px;'>✅ No Blocking Devices Active</div>"
                }
                
                // Check Lead Room Countdown
                if (settings.enableLeadRoomOverride && state.overrideScheduledTime) {
                    def timeLeft = Math.round((state.overrideScheduledTime - now()) / 60000.0)
                    if (timeLeft > 0) {
                        syncHtml += "<div style='color:#f39c12; font-weight:bold; border-top: 1px solid #eee; padding-top:4px;'>⏳ Lead Room Timer Active: ~${timeLeft} min(s) until forced Good Night evaluation.</div>"
                    } else {
                        syncHtml += "<div style='color:#f39c12; font-weight:bold; border-top: 1px solid #eee; padding-top:4px;'>⏳ Lead Room Timer: Evaluation Pending...</div>"
                    }
                } else if (settings.enableLeadRoomOverride) {
                    syncHtml += "<div style='color:#6c757d; border-top: 1px solid #eee; padding-top:4px;'>⏳ Lead Room Timer: Not Active</div>"
                }
                
                syncHtml += "</div>"
                paragraph syncHtml
            } else {
                paragraph "<i>Global Mode Sync is currently disabled. Enable it below to use these features.</i>"
            }
        }

        section("<b>Live Room Status & Diagnostics</b>") {
            def hasConfiguredRooms = false
            for (int i = 1; i <= 4; i++) {
                if (settings["enableRoom${i}"]) {
                    hasConfiguredRooms = true
                    def rName = settings["roomName${i}"] ?: "Room ${i}"
                    
                    // --- LIVE SENSOR DATA ---
                    def tSensor = settings["tempSensor${i}"]
                    def hSensor = settings["humSensor${i}"]
                    def cTemp = tSensor ? tSensor.currentValue("temperature") : null
                    def cHum = hSensor ? hSensor.currentValue("humidity") : null
                    def sleepQuality = calculateSleepSuitability(cTemp, cHum)
                    def tonightTrack = state."nextUri${i}" ?: "Not Generated Yet"
                    
                    // --- EXPECTED STATES MATH ---
                    def sw = settings["roomSwitch${i}"]
                    def isAsleep = sw?.currentValue("switch") == "on"
                    
                    def expCeiling = "App Released"
                    def expStdFan = "App Released"
                    def expLights = "App Released"
                    def expAudio = "App Released"
                    
                    if (isAsleep) {
                        expLights = "OFF"
                        expAudio = "PLAYING (Unless Timer Ended)"
                        
                        if (cTemp != null) {
                            def stdSet = settings["fanSetpoint${i}"]
                            if (stdSet) {
                                expStdFan = cTemp >= stdSet ? "ON" : "OFF"
                            } else {
                                expStdFan = "Not Configured"
                            }
                            
                            def cSet = settings["ceilingFanSetpoint${i}"]
                            def delta = settings["fanSpeedDelta${i}"] ?: 1.0
                            def fType = settings["fanType${i}"] ?: "3_speed"
                            
                            if (cSet && settings["ceilingFanSwitch${i}"]) {
                                if (fType == "on_off") {
                                    expCeiling = "Power: ON | Speed: N/A"
                                } else {
                                    def tSpeed = calculateTargetSpeed(cTemp, cSet, delta, fType).toUpperCase()
                                    expCeiling = "Power: ON | Speed: ${tSpeed}"
                                }
                            } else {
                                expCeiling = "Not Configured"
                            }
                        } else {
                            expStdFan = "Awaiting Temp Data"
                            expCeiling = "Awaiting Temp Data"
                        }
                    }
                    
                    // --- 7-DAY HISTORY TABLE ---
                    def histList = state."sleepHistory${i}" ?: []
                    def histHtml = ""
                    if (histList.size() > 0) {
                        histHtml = "<table style='width:100%; font-size: 11px; border-collapse: collapse; border: 1px solid #ccc; margin-top:2px;'>"
                        histHtml += "<tr style='background-color: #eee; border-bottom: 1px solid #ccc; text-align: left;'><th style='padding: 2px 4px;'>Date</th><th style='padding: 2px 4px;'>Duration</th></tr>"
                        histList.each { entry ->
                            histHtml += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 2px 4px;'>${entry.date}</td><td style='padding: 2px 4px;'><b>${entry.duration}</b></td></tr>"
                        }
                        histHtml += "</table>"
                    } else {
                        histHtml = "<div style='font-size:11px; color:#888;'><i>No history recorded yet.</i></div>"
                    }

                    // --- COMBINED HTML CARD ---
                    def dashHTML = """
                    <div style='background-color:#fdfdfd; border: 1px solid #ccc; border-radius: 5px; padding: 6px; margin-bottom: 6px;'>
                        <div style='font-size:14px; font-weight:bold; border-bottom: 2px solid #007bff; margin-bottom:4px; padding-bottom:2px;'>
                            ${rName} - ${isAsleep ? '🌙 ASLEEP' : '☀️ AWAKE'}
                        </div>
                        
                        <table style='width:100%; border-collapse: collapse; font-size: 11px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom:6px; line-height: 1.2;'>
                            <tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th colspan='2' style='padding: 3px 4px;'>Live Environment</th></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='width:35%; padding: 2px 4px; white-space: nowrap;'><b>Current Temp:</b></td><td style='padding: 2px 4px;'>${cTemp != null ? cTemp + '°F' : '--'}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Humidity:</b></td><td style='padding: 2px 4px;'>${cHum != null ? cHum + '%' : '--'}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Environment:</b></td><td style='padding: 2px 4px;'>${sleepQuality}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Tonight's Audio:</b></td><td style='padding: 2px 4px;'><span style='font-size:10px; font-family:monospace; word-break:break-all;'>${tonightTrack}</span></td></tr>
                            
                            <tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th colspan='2' style='padding: 3px 4px;'>Expected States</th></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Ceiling Fan:</b></td><td style='padding: 2px 4px;'>${expCeiling}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Standard Fans:</b></td><td style='padding: 2px 4px;'>${expStdFan}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Lights/Shades:</b></td><td style='padding: 2px 4px;'>${expLights}</td></tr>
                            <tr style='border-bottom: 1px solid #ddd;'><td style='padding: 2px 4px; white-space: nowrap;'><b>Audio Track:</b></td><td style='padding: 2px 4px;'>${expAudio}</td></tr>
                        </table>
                        
                        <div style='font-weight:bold; font-size:12px; border-bottom:1px solid #eee; margin-bottom:2px;'>7-Day Sleep History:</div>
                        ${histHtml}
                    </div>
                    """
                    paragraph dashHTML
                }
            }
            if (!hasConfiguredRooms) paragraph "<i>Please enable and configure a room below to populate the dashboard.</i>"
        }
        
        section("<b>Command History (Last 20)</b>") {
            def logList = state.eventLog ?: []
            if (logList.size() > 0) {
                def logHtml = "<div style='background-color:#fdfdfd; color:#333; border: 1px solid #ccc; padding:6px; border-radius:5px; font-family:monospace; font-size:11px; overflow-y:auto; max-height:200px; line-height:1.2;'>"
                logList.each { entry ->
                    logHtml += "${entry}<br><div style='border-bottom:1px solid #eee; margin:2px 0;'></div>"
                }
                logHtml += "</div>"
                paragraph logHtml
            } else {
                paragraph "<i>No commands logged yet. Turn a Good Night switch on to begin tracking.</i>"
            }
        }

        section("<b>Global Settings & Logs</b>") {
            input "enablePeriodicEnforcement", "bool", title: "<b>Enable Periodic State Enforcement</b><br><i>(Checks every 10 mins to ensure lights are off, fans are correct, and mode is synced)</i>", defaultValue: true
            input "enableWiggle", "bool", title: "<b>Enable Hourly Fan Wiggle (Self-Healing)</b><br><i>(Hourly routine to drop active RF fan speeds by one tier and bump them back to verify sync)</i>", defaultValue: true
            input "txtLogEnable", "bool", title: "Enable Action Logging", defaultValue: true
        }
        
        section("<b>Global Handshake: Mode Synchronization</b>") {
            input "enableGlobalMode", "bool", title: "<b>Enable Global Mode Sync</b>", submitOnChange: true
            if (enableGlobalMode) {
                paragraph "<b>Entering Night Mode</b>"
                input "allowedNightModes", "mode", title: "Safety Whitelist: ONLY allow entering Night Mode if house is currently in these Modes", multiple: true, required: false
                input "syncRooms", "enum", title: "Require these rooms to be Asleep", options: ["1":"Room 1", "2":"Room 2", "3":"Room 3", "4":"Room 4"], multiple: true, required: true
                input "syncMotion", "capability.motionSensor", title: "Require NO motion on these sensors", multiple: true, required: false
                input "blockingSwitches", "capability.switch", title: "Blocking Devices: Prevent Night Mode if ANY of these are ON (e.g. TV, Pinball)", multiple: true, required: false
                input "nightStartTime", "time", title: "Between Start Time", required: true
                input "nightEndTime", "time", title: "And End Time", required: true
                input "targetNightMode", "mode", title: "Change House Mode to", required: true
                input "nightModeDelay", "number", title: "Delay before entering Night Mode (seconds)", defaultValue: 60, required: false
                
                paragraph "<b>Morning Wake-Up Mode</b>"
                input "wakeStartTime", "time", title: "Between Start Time", required: true
                input "wakeEndTime", "time", title: "And End Time", required: true
                input "requireNightMode", "mode", title: "Only if currently in this Mode", required: true
                input "targetWakeMode", "mode", title: "Change House Mode to", required: true
                input "wakeModeDelay", "number", title: "Delay before entering Wake Mode (seconds)", defaultValue: 60, required: false

                paragraph "<b>Lead Room Good Night Override</b>"
                input "enableLeadRoomOverride", "bool", title: "<b>Enable Lead Room Override</b>", submitOnChange: true
                if (enableLeadRoomOverride) {
                    input "leadRoom", "enum", title: "Select Lead Room", options: ["1":"Room 1", "2":"Room 2", "3":"Room 3", "4":"Room 4"], required: true
                    input "leadRoomTimeout", "number", title: "Wait time (minutes) for other rooms to go to sleep", defaultValue: 30, required: true
                    input "leadRoomMotionSensors", "capability.motionSensor", title: "Require NO motion on these sensors to force Good Night", multiple: true, required: true
                    input "targetOverrideMode", "mode", title: "Change House Mode to (Good Night)", required: true
                }
            }
        }
        
        for (int i = 1; i <= 4; i++) {
            section("<b>Room ${i} Configuration</b>") {
                input "enableRoom${i}", "bool", title: "<b>Enable Room ${i}</b>", submitOnChange: true
                
                if (settings["enableRoom${i}"]) {
                    input "roomName${i}", "text", title: "Custom Room Name", defaultValue: "Room ${i}"
                    input "roomSwitch${i}", "capability.switch", title: "Room ${i} Good Night Virtual Switch", required: true
                    
                    paragraph "<b>1. Climate & Environment</b>"
                    input "tempSensor${i}", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "humSensor${i}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional - for sleep rating)", required: false
                    
                    paragraph "<b>Standard ON/OFF Fans</b>"
                    input "fanSetpoint${i}", "decimal", title: "Turn ON Standard Fans if Temp reaches (°F)", required: false
                    input "roomFans${i}", "capability.switch", title: "Standard Fans (Select up to 2)", multiple: true, required: false
                    
                    paragraph "<b>Dynamic Ceiling Fan</b>"
                    paragraph "<i>Select your fan capability. The routine will automatically scale the speed commands as the room heats up.</i>"
                    
                    input "fanType${i}", "enum", title: "Ceiling Fan Type", options: ["on_off": "Simple On/Off", "3_speed": "3-Speed", "6_speed": "5/6-Speed"], defaultValue: "3_speed", required: true
                    input "ceilingFanSwitch${i}", "capability.switch", title: "Ceiling Fan Power Switch", required: false
                    input "ceilingFanSpeed${i}", "capability.fanControl", title: "Ceiling Fan Speed Control", required: false
                    input "ceilingFanSetpoint${i}", "decimal", title: "Ceiling Fan Base Setpoint (°F)", required: false
                    input "fanSpeedDelta${i}", "decimal", title: "Degrees above setpoint to step up speed (Default: 1.0)", required: false, defaultValue: 1.0
                    
                    paragraph "<b>2. Lighting & Shades</b>"
                    input "roomLights${i}", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
                    input "shadeContact${i}", "capability.contactSensor", title: "Shade Open/Close Contact Sensor", required: false
                    input "roomShade${i}", "capability.windowShade", title: "Window Shade to Close", required: false
                    input "shadeHoldRelease${i}", "capability.switch", title: "Manual Hold Release Switch (Signals Shade Controller)", required: false
                    
                    paragraph "<b>3. Sonos Audio Polish</b>"
                    input "roomSpeaker${i}", "capability.audioNotification", title: "Sonos Speaker", required: false
                    input "audioVolume${i}", "number", title: "Fixed Nighttime Volume (1-100)", required: false, defaultValue: 15
                    input "audioTimer${i}", "number", title: "Sleep Timer: Stop audio after X minutes (Leave blank for continuous)", required: false
                    
                    input "audioUri${i}_1", "text", title: "Audio URI 1", required: false
                    input "audioUri${i}_2", "text", title: "Audio URI 2", required: false
                    input "audioUri${i}_3", "text", title: "Audio URI 3", required: false
                    input "audioUri${i}_4", "text", title: "Audio URI 4", required: false
                    input "audioUri${i}_5", "text", title: "Audio URI 5", required: false
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getSpeedLevels() { return ["off": 0, "low": 1, "medium-low": 2, "medium": 3, "medium-high": 4, "high": 5] }
def getLevelSpeeds() { return [0: "off", 1: "low", 2: "medium-low", 3: "medium", 4: "medium-high", 5: "high"] }

def calculateTargetSpeed(currentTemp, setpoint, delta, fanType) {
    def diff = currentTemp - setpoint
    if (fanType == "3_speed") {
        if (diff >= (delta * 2)) return "high"
        if (diff >= delta) return "medium"
        return "low"
    } else if (fanType == "6_speed") {
        if (diff >= (delta * 4)) return "high"
        if (diff >= (delta * 3)) return "medium-high"
        if (diff >= (delta * 2)) return "medium"
        if (diff >= delta) return "medium-low"
        return "low"
    }
    return "low" 
}

def getDropSpeed(current, fanType) {
    if (fanType == "3_speed") {
        if (current == "high") return "medium"
        if (current == "medium") return "low"
        if (current == "low") return "off"
    } else if (fanType == "6_speed") {
        if (current == "high") return "medium-high"
        if (current == "medium-high") return "medium"
        if (current == "medium") return "medium-low"
        if (current == "medium-low") return "low"
        if (current == "low") return "off"
    }
    return null
}

def installed() {
    logInfo("Installed and initialized.")
    initialize()
}

def updated() {
    logInfo("Updated. Re-initializing.")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!state.eventLog) state.eventLog = []
    
    // Subscribe to hub system events for power failure recovery
    subscribe(location, "systemStart", hubRebootHandler)
    
    // Schedule Periodic Enforcement
    if (enablePeriodicEnforcement) {
        runEvery10Minutes("periodicEnforcementHandler")
    }

    // Schedule Hourly Wiggle
    if (enableWiggle) {
        runEvery1Hour("doHourlyWiggle")
    }
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
            if (settings["roomSwitch${i}"]) {
                subscribe(settings["roomSwitch${i}"], "switch", roomSwitchHandler)
            }
            if (settings["tempSensor${i}"]) {
                subscribe(settings["tempSensor${i}"], "temperature", tempHandler)
            }
            if (!state."sleepHistory${i}") state."sleepHistory${i}" = []
            prepNextUri(i)
        }
    }
    
    if (enableGlobalMode) {
        if (syncMotion) {
            subscribe(syncMotion, "motion", globalMotionHandler)
        }
        if (blockingSwitches) {
            subscribe(blockingSwitches, "switch", blockingSwitchHandler)
        }
    }
}

// --- HOURLY FAN WIGGLE (SELF-HEALING) ---
def doHourlyWiggle() {
    logInfo("Executing Hourly RF Fan Wiggle to verify speeds...")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.currentValue("switch") == "on") {
            def cFanSpeed = settings["ceilingFanSpeed${i}"]
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            def fType = settings["fanType${i}"] ?: "3_speed"
            
            if (cFanSpeed && fType != "on_off") {
                def current = cFanSpeed.currentValue("speed") ?: "off"
                def dropSpeed = getDropSpeed(current, fType)
                
                if (dropSpeed && dropSpeed != "off") {
                    logInfo("${rName}: Wiggle - Dropping ceiling fan to ${dropSpeed.toUpperCase()} temporarily.")
                    cFanSpeed.setSpeed(dropSpeed)
                }
            }
        }
    }
    
    // Wait 10 seconds, then evaluate all sleeping rooms to bump speeds back
    runIn(10, "evaluateAllSleepingFans")
}

def evaluateAllSleepingFans() {
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.currentValue("switch") == "on") {
            evaluateFans(i)
        }
    }
}

// --- PERIODIC STATE ENFORCEMENT ---
def periodicEnforcementHandler() {
    // FAST FAIL: Only log and evaluate if at least one room is asleep
    def anyoneAsleep = false
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.currentValue("switch") == "on") {
            anyoneAsleep = true
            break
        }
    }
    
    if (anyoneAsleep) {
        if (txtLogEnable) log.debug "PERIODIC ENFORCEMENT: Waking up to verify system state..."
        
        // 1. Re-evaluate Global Mode Requirements
        evaluateGlobalMode(null)
        
        // 2. Loop through all rooms and enforce state
        for (int i = 1; i <= 4; i++) {
            if (settings["enableRoom${i}"]) {
                def sw = settings["roomSwitch${i}"]
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                
                if (sw && sw.currentValue("switch") == "on") {
                    // Enforce Lights OFF with Flashbang Fix
                    def lights = settings["roomLights${i}"]
                    if (lights) {
                        lights.each { lgt -> 
                            if (lgt.currentValue("switch") == "on") {
                                if (lgt.hasCommand("setLevel")) {
                                    lgt.setLevel(1)
                                    pauseExecution(400)
                                }
                                lgt.off()
                                logInfo("ENFORCEMENT: ${rName} is asleep but light [${lgt.displayName}] was ON. Forced OFF.")
                            }
                        }
                    }
                    
                    // Enforce Shades CLOSED
                    def shadeContact = settings["shadeContact${i}"]
                    def shade = settings["roomShade${i}"]
                    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
                        shade.close()
                        logInfo("ENFORCEMENT: ${rName} is asleep but shade contact was OPEN. Forced CLOSE.")
                    }

                    // Enforce Fans
                    evaluateFans(i)
                }
            }
        }
    }
}

// --- SYSTEM BOOT RECOVERY ENGINE ---
def hubRebootHandler(evt) {
    logInfo("SYSTEM BOOT: Hub reboot or power failure detected. Running nighttime recovery scan...")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
            def sw = settings["roomSwitch${i}"]
            if (sw && sw.currentValue("switch") == "on") {
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                logInfo("RECOVERY: ${rName} is still ASLEEP. Re-applying Good Night environment...")
                executeRoomGoodNight(i)
            }
        }
    }
    evaluateGlobalMode(null)
}

// --- HELPER: SLEEP SUITABILITY CALCULATOR ---
def calculateSleepSuitability(cTemp, cHum) {
    if (cTemp == null) return "<span style='color:gray;'>Awaiting Sensor Data...</span>"
    
    def tempStatus = ""
    def humStatus = ""
    def color = "green"
    
    if (cTemp < 60.0) { tempStatus = "Too Cold"; color = "blue" }
    else if (cTemp >= 60.0 && cTemp <= 69.0) { tempStatus = "Optimal Temp" }
    else { tempStatus = "Too Warm"; color = "red" }
    
    if (cHum != null) {
        if (cHum < 30.0) humStatus = " & Dry"
        else if (cHum >= 30.0 && cHum <= 50.0) humStatus = " & Ideal Humidity"
        else { humStatus = " & Humid"; color = "orange" }
    }
    
    def finalStatus = tempStatus + humStatus
    if (finalStatus.contains("Optimal Temp") && (humStatus == "" || humStatus.contains("Ideal"))) {
        return "<span style='color:green; font-weight:bold;'>Perfect 🌙</span>"
    }
    return "<span style='color:${color};'>${finalStatus}</span>"
}

// --- HELPER: ARE ALL REQUIRED ROOMS ASLEEP? ---
def areRequiredRoomsAsleep() {
    if (!settings.syncRooms) return false
    def allAsleep = true
    def roomsChecked = 0
    for (int i = 1; i <= 4; i++) {
        if (settings.syncRooms.contains(i.toString())) {
            roomsChecked++
            def sw = settings["roomSwitch${i}"]
            if (!sw || sw.currentValue("switch") != "on") {
                allAsleep = false
                break
            }
        }
    }
    return (roomsChecked > 0 && allAsleep)
}

// --- GLOBAL MOTION RE-EVALUATION ---
def globalMotionHandler(evt) {
    if (evt.value == "inactive") {
        // FAST FAIL: Don't evaluate unless required rooms are asleep
        if (areRequiredRoomsAsleep()) {
            logInfo("GLOBAL SYNC: Motion stopped on ${evt.device.displayName}. Re-evaluating sleep criteria...")
            evaluateGlobalMode(evt)
        }
    }
}

// --- BLOCKING DEVICE RE-EVALUATION ---
def blockingSwitchHandler(evt) {
    if (evt.value == "off") {
        // FAST FAIL: Don't evaluate unless required rooms are asleep
        if (areRequiredRoomsAsleep()) {
            logInfo("GLOBAL SYNC: Blocking device [${evt.device.displayName}] turned OFF. Re-evaluating sleep criteria...")
            evaluateGlobalMode(evt)
        }
    }
}

def roomSwitchHandler(evt) {
    def roomNum = null
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.id == evt.device.id) {
            roomNum = i
            break
        }
    }
    if (!roomNum) return
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (evt.value == "on") {
        state."sleepStartTime${roomNum}" = now()
        logInfo("${rName}: Good Night Switch ON. Engaging Routine.")
        executeRoomGoodNight(roomNum)
        
        // --- LEAD ROOM OVERRIDE CHECK ---
        if (settings.enableLeadRoomOverride && settings.leadRoom == roomNum.toString()) {
            def delaySecs = (settings.leadRoomTimeout ?: 30) * 60
            state.overrideScheduledTime = now() + (delaySecs * 1000)
            logInfo("GOOD NIGHT OVERRIDE: Lead Room (${roomNum}) is asleep. Waiting ${settings.leadRoomTimeout} minutes for other rooms.")
            runIn(delaySecs, "evaluateLeadRoomOverride")
        }
        
    } else {
        def startTime = state."sleepStartTime${roomNum}"
        if (startTime) {
            def totalMins = Math.round((now() - startTime) / 60000.0).toInteger()
            def hours = (totalMins / 60).toInteger()
            def mins = totalMins % 60
            
            logInfo("${rName}: Good Night Switch OFF. Sleep Duration Logged: ${hours}h ${mins}m.")
            
            def hist = state."sleepHistory${roomNum}" ?: []
            def todayDate = new Date().format("MM/dd", location.timeZone)
            hist.add(0, [date: todayDate, duration: "${hours}h ${mins}m"])
            if (hist.size() > 7) hist = hist.take(7)
            state."sleepHistory${roomNum}" = hist
            
            state.remove("sleepStartTime${roomNum}")
        }
        endRoomGoodNight(roomNum)
        
        // --- LEAD ROOM OVERRIDE CANCEL ---
        if (settings.enableLeadRoomOverride && settings.leadRoom == roomNum.toString()) {
            unschedule("evaluateLeadRoomOverride")
            state.remove("overrideScheduledTime")
            logInfo("GOOD NIGHT OVERRIDE: Lead Room (${roomNum}) woke up. Canceled evaluation.")
        }
    }
    evaluateGlobalMode(evt)
}

def evaluateLeadRoomOverride() {
    state.remove("overrideScheduledTime") 
    
    if (!settings.enableLeadRoomOverride || !settings.targetOverrideMode) return

    logInfo("GOOD NIGHT OVERRIDE: Timeout reached. Checking other rooms and motion.")

    def otherRoomAwake = false
    for (int i = 1; i <= 4; i++) {
        if (i.toString() != settings.leadRoom && settings["enableRoom${i}"]) {
            def sw = settings["roomSwitch${i}"]
            if (sw && sw.currentValue("switch") != "on") {
                otherRoomAwake = true
                break
            }
        }
    }

    if (otherRoomAwake) {
        def motionActive = false
        if (settings.leadRoomMotionSensors) {
            settings.leadRoomMotionSensors.each { mSens ->
                if (mSens.currentValue("motion") == "active") {
                    motionActive = true
                }
            }
        }

        if (!motionActive) {
            logInfo("GOOD NIGHT OVERRIDE: Other rooms are awake but no motion detected. Forcing mode to ${settings.targetOverrideMode}.")
            setLocationMode(settings.targetOverrideMode)
        } else {
            logInfo("GOOD NIGHT OVERRIDE: Other rooms awake and motion detected. Skipping forced mode change, falling back to standard rules.")
        }
    } else {
        logInfo("GOOD NIGHT OVERRIDE: All enabled rooms are actually asleep. Letting standard Night mode logic handle it.")
    }
}

def evaluateGlobalMode(evt = null) {
    if (!enableGlobalMode) return
    def now = new Date()
    def currentMode = location.mode
    
    // --- NIGHT MODE LOGIC ---
    if (nightStartTime && nightEndTime && targetNightMode) {
        def nightStart = timeToday(nightStartTime, location.timeZone)
        def nightEnd = timeToday(nightEndTime, location.timeZone)
        
        def isNightWindow = false
        if (nightStart.time <= nightEnd.time) {
            isNightWindow = (now.time >= nightStart.time && now.time <= nightEnd.time)
        } else {
            isNightWindow = (now.time >= nightStart.time || now.time <= nightEnd.time)
        }
        
        // FAST FAIL: Only proceed if we are in the time window and not already in Target Night Mode
        if (isNightWindow && currentMode != targetNightMode) {
            def isAllowedMode = true
            if (allowedNightModes) isAllowedMode = (allowedNightModes as List).contains(currentMode)
            
            if (isAllowedMode) {
                // FAST FAIL: Check if rooms are asleep FIRST before looping through motion and switches
                def allAsleep = true
                def roomsChecked = 0
                for (int i = 1; i <= 4; i++) {
                    if (syncRooms && syncRooms.contains(i.toString())) {
                        roomsChecked++
                        def sw = settings["roomSwitch${i}"]
                        if (sw) {
                            def swVal = (evt && evt.device.id == sw.id) ? evt.value : sw.currentValue("switch")
                            if (swVal != "on") allAsleep = false
                        } else {
                            allAsleep = false
                        }
                    }
                }
                if (roomsChecked == 0) allAsleep = false
                
                // If the rooms aren't asleep, silently exit this block. No log spam.
                if (allAsleep) {
                    if (txtLogEnable) log.debug "EVALUATING NIGHT MODE: Required Rooms Asleep. Checking Motion & Blocking Devices..."
                    
                    def noMotion = true
                    if (syncMotion) {
                        syncMotion.each { mSens ->
                            def mVal = (evt && evt.device.id == mSens.id) ? evt.value : mSens.currentValue("motion")
                            if (txtLogEnable) log.debug "EVALUATING NIGHT MODE: Motion Sensor [${mSens.displayName}] is [${mVal}]"
                            if (mVal == "active") noMotion = false
                        }
                    }
                    
                    def noBlockingDevices = true
                    if (blockingSwitches) {
                        blockingSwitches.each { bSw ->
                            def bVal = (evt && evt.device.id == bSw.id) ? evt.value : bSw.currentValue("switch")
                            if (txtLogEnable) log.debug "EVALUATING NIGHT MODE: Blocking Switch [${bSw.displayName}] is [${bVal}]"
                            if (bVal == "on") noBlockingDevices = false
                        }
                    }
                    
                    if (txtLogEnable) log.debug "EVALUATING NIGHT MODE: AllAsleep=${allAsleep} | NoMotion=${noMotion} | NoBlockingDevices=${noBlockingDevices}"
                    
                    if (noMotion && noBlockingDevices) {
                        def nDelay = settings.nightModeDelay != null ? settings.nightModeDelay : 60
                        logInfo("GLOBAL SYNC: Required rooms asleep, house is quiet, and no blocking devices are ON. Scheduling Night Mode (${targetNightMode}) in ${nDelay} seconds.")
                        runIn(nDelay, "executeNightModeChange")
                    } else {
                        logInfo("GLOBAL SYNC: Waiting on motion sensors to clear or blocking devices to turn OFF before shifting mode.")
                        unschedule("executeNightModeChange")
                    }
                }
            }
        }
    }
    
    // --- WAKE MODE LOGIC ---
    if (wakeStartTime && wakeEndTime && requireNightMode && targetWakeMode) {
        def wakeStart = timeToday(wakeStartTime, location.timeZone)
        def wakeEnd = timeToday(wakeEndTime, location.timeZone)
        
        def isWakeWindow = false
        if (wakeStart.time <= wakeEnd.time) {
            isWakeWindow = (now.time >= wakeStart.time && now.time <= wakeEnd.time)
        } else {
            isWakeWindow = (now.time >= wakeStart.time || now.time <= wakeEnd.time)
        }
        
        if (isWakeWindow && currentMode == requireNightMode) {
            def allAwake = true
            def anyRoomConfigured = false
            for (int i = 1; i <= 4; i++) {
                if (settings["enableRoom${i}"]) {
                    anyRoomConfigured = true
                    def sw = settings["roomSwitch${i}"]
                    if (sw) {
                        def swVal = (evt && evt.device.id == sw.id) ? evt.value : sw.currentValue("switch")
                        if (swVal == "on") allAwake = false
                    }
                }
            }
            if (anyRoomConfigured && allAwake) {
                def wDelay = settings.wakeModeDelay != null ? settings.wakeModeDelay : 60
                logInfo("GLOBAL SYNC: All Good Night switches are OFF. Scheduling Wake Mode (${targetWakeMode}) in ${wDelay} seconds.")
                runIn(wDelay, "executeWakeModeChange")
            } else {
                unschedule("executeWakeModeChange")
            }
        }
    }
}

def executeRoomGoodNight(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    unschedule("turnOffHoldReleaseRoom${roomNum}")
    unschedule("fanOffTwoRoom${roomNum}")
    unschedule("fanOffThreeRoom${roomNum}")
    unschedule("fanPowerOffRoom${roomNum}")

    def lights = settings["roomLights${roomNum}"]
    if (lights) { 
        lights.each { lgt ->
            if (lgt.hasCommand("setLevel")) {
                lgt.setLevel(1)
                pauseExecution(400)
            }
            lgt.off()
        }
        logInfo("${rName}: Lights turned OFF (w/ 1% flashbang protection if applicable).") 
    }
    
    def shadeContact = settings["shadeContact${roomNum}"]
    def shade = settings["roomShade${roomNum}"]
    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
        shade.close();
        logInfo("${rName}: Shade contact is open. Closing shade.")
    }
    
    def speaker = settings["roomSpeaker${roomNum}"]
    def trackToPlay = state."nextUri${roomNum}"
    
    if (speaker && trackToPlay) {
        def setVol = settings["audioVolume${roomNum}"]
        if (setVol != null) {
            speaker.setVolume(setVol)
            logInfo("${rName}: Speaker volume forced to ${setVol}%.")
        }
        
        speaker.playTrack(trackToPlay)
        logInfo("${rName}: Playing tonight's Sonos URI (${trackToPlay}).")
        state."lastUri${roomNum}" = trackToPlay 
        
        def sTimer = settings["audioTimer${roomNum}"]
        if (sTimer) {
            logInfo("${rName}: Sleep timer set for ${sTimer} minutes.")
            runIn(sTimer * 60, "stopAudioRoom${roomNum}") 
        }
    }
    
    evaluateFans(roomNum)
}

def stopAudioRoom1() { executeAudioStop(1) }
def stopAudioRoom2() { executeAudioStop(2) }
def stopAudioRoom3() { executeAudioStop(3) }
def stopAudioRoom4() { executeAudioStop(4) }

def executeAudioStop(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    if (speaker && speaker.hasCommand("stop")) {
        speaker.stop()
        logInfo("${rName}: Sleep timer reached. Audio stopped.")
    }
}

def endRoomGoodNight(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    logInfo("${rName}: Executing Wake-Up routine (shutting down fans and audio).")
    
    unschedule("stopAudioRoom${roomNum}")
    unschedule("applyDelayedFanSpeedRoom${roomNum}")
    state.remove("pendingFanSpeed${roomNum}")
    
    def stdFans = settings["roomFans${roomNum}"]
    if (stdFans) stdFans.off()
    
    // --- 3x REDUNDANT CEILING FAN SHUTDOWN ---
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") {
        cFanSpeed.setSpeed("off") // Pulse 1
        logInfo("${rName}: Ceiling fan speed set to OFF. Initiating 3x redundant shutdown sequence before cutting power.")
        runIn(2, "fanOffTwoRoom${roomNum}") // Cascades to Pulse 2, Pulse 3, then Power OFF
    } else if (cFanSwitch) {
        cFanSwitch.off() // Fallback or direct control for On/Off fans
    }
    
    def speaker = settings["roomSpeaker${roomNum}"]
    if (speaker && speaker.hasCommand("stop")) speaker.stop()
    
    def holdRelease = settings["shadeHoldRelease${roomNum}"]
    if (holdRelease) {
        holdRelease.on()
        logInfo("${rName}: Sent hold release signal to Advanced Shade Controller. Auto-reset scheduled in 30s.")
        runIn(30, "turnOffHoldReleaseRoom${roomNum}")
    }
    
    prepNextUri(roomNum) 
}

def turnOffHoldReleaseRoom1() { executeHoldReleaseOff(1) }
def turnOffHoldReleaseRoom2() { executeHoldReleaseOff(2) }
def turnOffHoldReleaseRoom3() { executeHoldReleaseOff(3) }
def turnOffHoldReleaseRoom4() { executeHoldReleaseOff(4) }

def executeHoldReleaseOff(roomNum) {
    def holdRelease = settings["shadeHoldRelease${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    if (holdRelease && holdRelease.currentValue("switch") != "off") {
        holdRelease.off()
        logInfo("${rName}: Hold release signal automatically reset to OFF.")
    }
}

// --- REDUNDANT FAN SHUTDOWN CASCADES ---
def fanOffTwoRoom1() { executeFanOffTwo(1) }
def fanOffTwoRoom2() { executeFanOffTwo(2) }
def fanOffTwoRoom3() { executeFanOffTwo(3) }
def fanOffTwoRoom4() { executeFanOffTwo(4) }

def executeFanOffTwo(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") cFanSpeed.setSpeed("off") // Pulse 2
    runIn(2, "fanOffThreeRoom${roomNum}")
}

def fanOffThreeRoom1() { executeFanOffThree(1) }
def fanOffThreeRoom2() { executeFanOffThree(2) }
def fanOffThreeRoom3() { executeFanOffThree(3) }
def fanOffThreeRoom4() { executeFanOffThree(4) }

def executeFanOffThree(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") cFanSpeed.setSpeed("off") // Pulse 3
    runIn(2, "fanPowerOffRoom${roomNum}")
}

def fanPowerOffRoom1() { executeFanPowerOff(1) }
def fanPowerOffRoom2() { executeFanPowerOff(2) }
def fanPowerOffRoom3() { executeFanPowerOff(3) }
def fanPowerOffRoom4() { executeFanPowerOff(4) }

def executeFanPowerOff(roomNum) {
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    if (cFanSwitch) {
        cFanSwitch.off()
        logInfo("${settings["roomName${roomNum}"] ?: "Room " + roomNum}: Ceiling fan power safely disconnected.")
    }
}

def tempHandler(evt) {
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["tempSensor${i}"]?.id == evt.device.id) {
            if (settings["roomSwitch${i}"]?.currentValue("switch") == "on") {
                evaluateFans(i)
            }
        }
    }
}

def evaluateFans(roomNum) {
    def sensor = settings["tempSensor${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def currentTemp = sensor ? sensor.currentValue("temperature") : null
    
    // 1. Evaluate Standard ON/OFF Fans
    if (currentTemp != null) {
        def stdSetpoint = settings["fanSetpoint${roomNum}"]
        def stdFans = settings["roomFans${roomNum}"]
        if (stdSetpoint && stdFans) {
            if (currentTemp >= stdSetpoint) {
                stdFans.each { if (it.currentValue("switch") != "on") it.on() }
            } else {
                stdFans.each { if (it.currentValue("switch") != "off") it.off() }
            }
        }
    }
    
    // 2. Evaluate Dynamic Ceiling Fan
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def cFanSetpoint = settings["ceilingFanSetpoint${roomNum}"]
    def delta = settings["fanSpeedDelta${roomNum}"] ?: 1.0
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    
    if (cFanSwitch) {
        if (cFanSwitch.currentValue("switch") != "on") {
            cFanSwitch.on()
            logInfo("${rName}: Ceiling fan powered ON.")
            
            if (fType != "on_off" && cFanSpeed && cFanSetpoint && currentTemp != null) {
                def targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                logInfo("${rName}: Waiting 30 seconds before setting speed to ${targetSpeed.toUpperCase()}.")
                state."pendingFanSpeed${roomNum}" = targetSpeed
                runIn(30, "applyDelayedFanSpeedRoom${roomNum}")
            }
        } else {
            if (fType != "on_off" && cFanSpeed && cFanSetpoint && currentTemp != null && !state."pendingFanSpeed${roomNum}") {
                def targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                
                if (cFanSpeed.currentValue("speed") != targetSpeed) {
                    cFanSpeed.setSpeed(targetSpeed)
                    logInfo("${rName}: Ceiling fan dynamically adjusted to ${targetSpeed.toUpperCase()} (Temp: ${currentTemp}°, Setpoint: ${cFanSetpoint}°).")
                }
            }
        }
    }
}

def applyDelayedFanSpeedRoom1() { executeDelayedFanSpeed(1) }
def applyDelayedFanSpeedRoom2() { executeDelayedFanSpeed(2) }
def applyDelayedFanSpeedRoom3() { executeDelayedFanSpeed(3) }
def applyDelayedFanSpeedRoom4() { executeDelayedFanSpeed(4) }

def executeDelayedFanSpeed(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def targetSpeed = state."pendingFanSpeed${roomNum}"
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (cFanSpeed && targetSpeed) {
        cFanSpeed.setSpeed(targetSpeed)
        logInfo("${rName}: 30-second hardware warm-up complete. Ceiling fan speed safely set to ${targetSpeed.toUpperCase()}.")
        state.remove("pendingFanSpeed${roomNum}")
    }
}

// --- HELPER: AUDIO RANDOMIZER ---
def prepNextUri(roomNum) {
    def uris = []
    for(int u = 1; u <= 5; u++) {
        def uri = settings["audioUri${roomNum}_${u}"]
        if (uri) uris << uri
    }
    
    if (uris.size() > 0) {
        if (uris.size() == 1) {
            state."nextUri${roomNum}" = uris[0]
        } else {
            def lastPlayed = state."lastUri${roomNum}"
            def availableUris = uris.findAll { it != lastPlayed }
            if (availableUris.size() == 0) availableUris = uris 
            def chosen = availableUris[new Random().nextInt(availableUris.size())]
            state."nextUri${roomNum}" = chosen
        }
    } else {
        state.remove("nextUri${roomNum}")
    }
}

def logInfo(msg) {
    if (txtLogEnable) log.info "${app.label}: ${msg}"
    
    def hist = state.eventLog ?: []
    def timeStamp = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    hist.add(0, "[${timeStamp}] ${msg}")
    
    if (hist.size() > 20) hist = hist.take(20)
    state.eventLog = hist
}

def executeNightModeChange() {
    if (targetNightMode) {
        logInfo("GLOBAL SYNC: Delay complete. Changing mode to ${targetNightMode}.")
        setLocationMode(targetNightMode)
    }
}

def executeWakeModeChange() {
    if (targetWakeMode) {
        logInfo("GLOBAL SYNC: Delay complete. Changing mode to ${targetWakeMode}.")
        setLocationMode(targetWakeMode)
    }
}
