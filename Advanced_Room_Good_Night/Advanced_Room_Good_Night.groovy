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
        
        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, top-down view of your home's sleep status, blocking devices, and individual room environments.</div>"
            input "refreshDataBtn", "button", title: "🔄 Refresh Data"
            
            // --- INJECT CLIMATE APP CSS STYLING ---
            def cssHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 15px; }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
            </style>
            """
            
            // --- GLOBAL SYNC STATUS PANEL ---
            def syncExplanation = "Global Mode Sync is currently <b>Disabled</b>."
            if (settings.enableGlobalMode) {
                def activeBlockers = []
                if (settings.blockingSwitches) {
                    settings.blockingSwitches.each { bSw ->
                        if (bSw.currentValue("switch") == "on") {
                            activeBlockers << bSw.displayName
                        }
                    }
                }
                
                if (activeBlockers.size() > 0) {
                    def bList = activeBlockers.join(", ")
                    syncExplanation = "<span style='color:#d9534f;'><b>⚠️ Night Mode Blocked By:</b> ${bList}</span>"
                } else {
                    syncExplanation = "<span style='color:#28a745;'><b>✅ No Blocking Devices Active</b></span>"
                }
                
                // Timer 1: Global Night Mode Sync
                if (state.nightModeScheduledTime) {
                    def globalTimeSecs = Math.round((state.nightModeScheduledTime - now()) / 1000.0)
                    if (globalTimeSecs > 60) {
                        syncExplanation += "<br><span style='color:#007bff; margin-top:4px; display:inline-block;'><b>⏳ Global Night Mode Countdown:</b> ~${Math.round(globalTimeSecs / 60.0)} min(s) until Good Night.</span>"
                    } else if (globalTimeSecs > 0) {
                        syncExplanation += "<br><span style='color:#007bff; margin-top:4px; display:inline-block;'><b>⏳ Global Night Mode Countdown:</b> ${globalTimeSecs} sec(s) until Good Night.</span>"
                    } else {
                        syncExplanation += "<br><span style='color:#007bff; margin-top:4px; display:inline-block;'><b>⏳ Global Night Mode Countdown:</b> Executing...</span>"
                    }
                }

                // Timer 2: Lead Room(s) Override
                if (settings.enableLeadRoomOverride && state.overrideScheduledTime) {
                    def timeLeft = Math.round((state.overrideScheduledTime - now()) / 60000.0)
                    if (timeLeft > 0) {
                        syncExplanation += "<br><span style='color:#f39c12; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer Active:</b> ~${timeLeft} min(s) until forced Good Night evaluation.</span>"
                    } else {
                        syncExplanation += "<br><span style='color:#f39c12; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer:</b> Evaluation Pending...</span>"
                    }
                } else if (settings.enableLeadRoomOverride) {
                    syncExplanation += "<br><span style='color:#6c757d; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer:</b> Not Active</span>"
                }
            }
            
            def dashHTML = cssHTML + "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-bottom: 15px; font-size: 13px;'><b>Global Sleep Sync:</b><br>${syncExplanation}</div>"
            
            // --- INDIVIDUAL ROOM PANELS ---
            def hasConfiguredRooms = false
            for (int i = 1; i <= 4; i++) {
                if (settings["enableRoom${i}"]) {
                    hasConfiguredRooms = true
                    def rName = settings["roomName${i}"] ?: "Room ${i}"
                    
                    def tSensor = settings["tempSensor${i}"]
                    def hSensor = settings["humSensor${i}"]
                    def cTemp = tSensor ? tSensor.currentValue("temperature") : null
                    def cHum = hSensor ? hSensor.currentValue("humidity") : null
                    def sleepQuality = calculateSleepSuitability(cTemp, cHum)
                    
                    // TONIGHT'S TRACK LOGIC
                    def tonightTrack = "Not Generated Yet"
                    def aType = settings["audioSourceType${i}"] ?: "uri"
                    if (aType == "uri" && state."nextUri${i}") {
                        tonightTrack = state."nextUri${i}"
                    } else if (aType == "switch" && state."nextSwitchId${i}") {
                        def nId = state."nextSwitchId${i}"
                        for(int u = 1; u <= 5; u++) {
                            def s = settings["audioSwitch${i}_${u}"]
                            if (s?.id == nId) tonightTrack = s.displayName
                        }
                    }
                    
                    def sw = settings["roomSwitch${i}"]
                    def isAsleep = sw?.currentValue("switch") == "on"
                    def titleColor = isAsleep ? "#2e154f" : "#007bff"
                    
                    def expCeiling = "App Released"
                    def expStdFan = "App Released"
                    def expLights = "App Released"
                    def expAudio = "App Released"
                    def targetSpeedDisp = "N/A"
                    
                    if (isAsleep) {
                        def hasOnLights = settings["roomLightsOn${i}"] ? true : false
                        expLights = hasOnLights ? "OFF / Bedtime Plugs ON" : "OFF"
                        expAudio = "PLAYING (Unless Timer Ended)"
                        
                        if (cTemp != null) {
                            def stdSet = settings["fanSetpoint${i}"]
                            expStdFan = stdSet ? (cTemp >= stdSet ? "ON" : "OFF") : "Not Configured"
                            
                            def cSet = settings["ceilingFanSetpoint${i}"]
                            def delta = settings["fanSpeedDelta${i}"] ?: 1.0
                            def fType = settings["fanType${i}"] ?: "3_speed"
                            
                            if (cSet && settings["ceilingFanSwitch${i}"]) {
                                if (fType == "on_off") {
                                    expCeiling = "Power: ON | Speed: N/A"
                                    targetSpeedDisp = "N/A (On/Off)"
                                } else {
                                    def tSpeed = calculateTargetSpeed(cTemp, cSet, delta, fType).toUpperCase()
                                    targetSpeedDisp = tSpeed
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
                    
                    dashHTML += """
                    <div style='background-color:${titleColor}; color:white; padding: 8px; font-weight:bold; font-size: 14px; border-radius: 4px 4px 0 0;'>
                        ${rName} - ${isAsleep ? '🌙 ASLEEP' : '☀️ AWAKE'}
                    </div>
                    <table class="dash-table" style="margin-top: 0; margin-bottom: 5px;">
                        <tr><td colspan='2' class="dash-subhead">Live Environment</td></tr>
                        <tr><td class="dash-hl">Current Temp</td><td class="dash-val">${cTemp != null ? cTemp + '°F' : '--'}</td></tr>
                        <tr><td class="dash-hl">Humidity</td><td class="dash-val">${cHum != null ? cHum + '%' : '--'}</td></tr>
                        <tr><td class="dash-hl">Environment</td><td class="dash-val">${sleepQuality}</td></tr>
                        <tr><td class="dash-hl">Tonight's Audio</td><td class="dash-val"><span style='font-size:10px; font-family:monospace; word-break:break-all;'>${tonightTrack}</span></td></tr>
                        
                        <tr><td colspan='2' class="dash-subhead">Expected States</td></tr>
                        <tr><td class="dash-hl">Calculated Target Speed</td><td class="dash-val" style="color:#007bff; font-weight:bold;">${targetSpeedDisp}</td></tr>
                        <tr><td class="dash-hl">Ceiling Fan Hardware</td><td class="dash-val">${expCeiling}</td></tr>
                        <tr><td class="dash-hl">Standard Fans</td><td class="dash-val">${expStdFan}</td></tr>
                        <tr><td class="dash-hl">Lights/Shades</td><td class="dash-val">${expLights}</td></tr>
                        <tr><td class="dash-hl">Audio Track</td><td class="dash-val">${expAudio}</td></tr>
                    </table>
                    
                    <table class="dash-table" style="margin-top: 0;">
                        <thead><tr><th>Date</th><th>Recorded Sleep Duration</th></tr></thead>
                        <tbody>
                    """
                    
                    def histList = state."sleepHistory${i}" ?: []
                    if (histList.size() > 0) {
                        histList.each { entry ->
                            dashHTML += "<tr><td>${entry.date}</td><td><b>${entry.duration}</b></td></tr>"
                        }
                    } else {
                        dashHTML += "<tr><td colspan='2' style='color:#888;'><i>No history recorded yet.</i></td></tr>"
                    }
                    dashHTML += "</tbody></table>"
                }
            }
            
            if (hasConfiguredRooms) {
                paragraph dashHTML
            } else {
                paragraph dashHTML + "<i>Please enable and configure a room below to populate the dashboard.</i>"
            }
        }
        
        section("<b>Command History (Last 20)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a transparent, rolling log of every command the system evaluates and sends.</div>"
            def logList = state.eventLog ?: []
            if (logList.size() > 0) {
                def logHtml = logList.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${logHtml}</span>"
            } else {
                paragraph "<i>No commands logged yet. Turn a Good Night switch on to begin tracking.</i>"
            }
            input "clearLogBtn", "button", title: "Clear Command History"
        }

        section("<b>Global Settings & Logs</b>", hideable: true, hidden: true) {
            input "enablePeriodicEnforcement", "bool", title: "<b>Enable Periodic State Enforcement</b><br><i>(Checks every 10 mins to ensure lights are off, fans are correct, and mode is synced)</i>", defaultValue: true
            input "enableWiggle", "bool", title: "<b>Master Enable: Hourly Fan Wiggle (Self-Healing)</b><br><i>(Global toggle to allow room fans to run their configured Wiggle routines)</i>", defaultValue: true
            input "txtLogEnable", "bool", title: "Enable Action Logging", defaultValue: true
        }
        
        section("<b>Global Handshake: Mode Synchronization</b>", hideable: true, hidden: true) {
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

                paragraph "<b>Lead Room(s) Good Night Override</b>"
                input "enableLeadRoomOverride", "bool", title: "<b>Enable Lead Room Override</b>", submitOnChange: true
                if (enableLeadRoomOverride) {
                    input "leadRooms", "enum", title: "Select Lead Rooms (All selected must be asleep to trigger override)", options: ["1":"Room 1", "2":"Room 2", "3":"Room 3", "4":"Room 4"], multiple: true, required: true
                    input "leadRoomTimeout", "number", title: "Wait time (minutes) for other rooms to go to sleep", defaultValue: 30, required: true
                    input "leadRoomMotionSensors", "capability.motionSensor", title: "Require NO motion on these sensors to force Good Night", multiple: true, required: true
                    input "targetOverrideMode", "mode", title: "Change House Mode to (Good Night)", required: true
                }
            }
        }
        
        for (int i = 1; i <= 4; i++) {
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            
            section("<b>${rName} Configuration</b>", hideable: true, hidden: true) {
                input "enableRoom${i}", "bool", title: "<b>Enable ${rName}</b>", submitOnChange: true
                
                if (settings["enableRoom${i}"]) {
                    input "roomName${i}", "text", title: "Custom Room Name", defaultValue: "Room ${i}", submitOnChange: true
                    input "roomSwitch${i}", "capability.switch", title: "${rName} Good Night Virtual Switch", required: true
                    
                    paragraph "<b>Good Night Toggle Button</b>"
                    input "gnButton${i}", "capability.pushableButton", title: "Toggle Button Device", required: false
                    input "gnButtonNum${i}", "number", title: "Button Number", required: false, defaultValue: 1
                    input "gnButtonAction${i}", "enum", title: "Button Action", options: ["pushed":"Pushed", "doubleTapped":"Double Tapped", "held":"Held", "released":"Released"], required: false, defaultValue: "pushed"
                    input "gnButtonModes${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    
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
                    input "enableWiggle${i}", "bool", title: "Enable Hourly Wiggle for this specific fan (Requires Master Wiggle enabled globally)", defaultValue: true
                    
                    paragraph "<b>2. Lighting & Shades</b>"
                    input "roomLights${i}", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
                    input "roomLightsOn${i}", "capability.switch", title: "Lights/Plugs to Turn ON (Turns OFF when waking)", multiple: true, required: false
                    input "pauseLightingEnforcement${i}", "capability.switch", title: "Pause Lighting Enforcement Switch (Syncs with Sunrise App)", required: false
                    input "shadeContact${i}", "capability.contactSensor", title: "Shade Open/Close Contact Sensor", required: false
                    input "roomShade${i}", "capability.windowShade", title: "Window Shade to Close", required: false
                    input "shadeHoldRelease${i}", "capability.switch", title: "Manual Hold Release Switch (Signals Shade Controller)", required: false
                    
                    paragraph "<b>Reading Light 1</b>"
                    input "enableReadingLight1_${i}", "bool", title: "Enable Reading Light 1?", submitOnChange: true
                    if (settings["enableReadingLight1_${i}"]) {
                        input "readingLight1_${i}", "capability.switchLevel", title: "Reading Light 1 (Dimmer)", required: false
                        input "readingButton1_${i}", "capability.pushableButton", title: "Button for Light 1", required: false
                        input "readingButtonNum1_${i}", "number", title: "Button Number", required: false, defaultValue: 1
                        input "readingLevel1_${i}", "number", title: "Dim Level (%)", required: false, defaultValue: 30
                        input "readingTimeout1_${i}", "number", title: "Timeout (Minutes)", required: false, defaultValue: 60
                        input "readingModes1_${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    }

                    paragraph "<b>Reading Light 2</b>"
                    input "enableReadingLight2_${i}", "bool", title: "Enable Reading Light 2?", submitOnChange: true
                    if (settings["enableReadingLight2_${i}"]) {
                        input "readingLight2_${i}", "capability.switchLevel", title: "Reading Light 2 (Dimmer)", required: false
                        input "readingButton2_${i}", "capability.pushableButton", title: "Button for Light 2", required: false
                        input "readingButtonNum2_${i}", "number", title: "Button Number", required: false, defaultValue: 1
                        input "readingLevel2_${i}", "number", title: "Dim Level (%)", required: false, defaultValue: 30
                        input "readingTimeout2_${i}", "number", title: "Timeout (Minutes)", required: false, defaultValue: 60
                        input "readingModes2_${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    }
                    
                    paragraph "<b>3. Sonos Audio Polish</b>"
                    input "roomSpeakerPower${i}", "capability.switch", title: "Sonos Speaker Power Plug (Optional)", required: false
                    input "roomSpeaker${i}", "capability.musicPlayer", title: "Sonos Speaker", required: false
                    input "audioVolume${i}", "number", title: "Fixed Nighttime Volume (1-100)", required: false, defaultValue: 15
                    input "audioTimer${i}", "number", title: "Sleep Timer: Stop audio after X minutes (Leave blank for continuous)", required: false
                    
                    input "audioSourceType${i}", "enum", title: "Audio Source Type", options: ["uri":"Direct Audio URIs", "switch":"Sonos Favorite Virtual Switches"], defaultValue: "uri", submitOnChange: true
                    
                    if ((settings["audioSourceType${i}"] ?: "uri") == "uri") {
                        input "audioUri${i}_1", "text", title: "Audio URI 1", required: false
                        input "audioUri${i}_2", "text", title: "Audio URI 2", required: false
                        input "audioUri${i}_3", "text", title: "Audio URI 3", required: false
                        input "audioUri${i}_4", "text", title: "Audio URI 4", required: false
                        input "audioUri${i}_5", "text", title: "Audio URI 5", required: false
                    } else {
                        input "audioSwitch${i}_1", "capability.switch", title: "Favorite Switch 1", required: false
                        input "audioSwitch${i}_2", "capability.switch", title: "Favorite Switch 2", required: false
                        input "audioSwitch${i}_3", "capability.switch", title: "Favorite Switch 3", required: false
                        input "audioSwitch${i}_4", "capability.switch", title: "Favorite Switch 4", required: false
                        input "audioSwitch${i}_5", "capability.switch", title: "Favorite Switch 5", required: false
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def appButtonHandler(btn) {
    if (btn == "refreshDataBtn") {
        logInfo("Manual UI Data Refresh Triggered.")
    } else if (btn == "clearLogBtn") {
        state.eventLog = []
        logInfo("User manually cleared the command history log.")
    }
}

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
    if (settings.enableWiggle) {
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
            
            // Good Night Toggle Button Subscription
            if (settings["gnButton${i}"]) {
                def action = settings["gnButtonAction${i}"] ?: "pushed"
                subscribe(settings["gnButton${i}"], action, goodNightButtonHandler)
            }
            
            // Reading Buttons Subscription
            if (settings["enableReadingLight1_${i}"] && settings["readingButton1_${i}"]) {
                subscribe(settings["readingButton1_${i}"], "pushed", readingButtonHandler)
            }
            if (settings["enableReadingLight2_${i}"] && settings["readingButton2_${i}"]) {
                subscribe(settings["readingButton2_${i}"], "pushed", readingButtonHandler)
            }
            
            if (!state."sleepHistory${i}") state."sleepHistory${i}" = []
            prepNextAudio(i)
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

// --- GOOD NIGHT BUTTON TOGGLE ENGINE ---
def goodNightButtonHandler(evt) {
    def btnId = evt.device.id
    def btnNum = evt.value

    for (int i = 1; i <= 4; i++) {
        if (!settings["enableRoom${i}"]) continue

        def confBtn = settings["gnButton${i}"]
        def confNum = settings["gnButtonNum${i}"]?.toString() ?: "1"
        def confAction = settings["gnButtonAction${i}"] ?: "pushed"
        def rName = settings["roomName${i}"] ?: "Room ${i}"

        if (confBtn && confBtn.id == btnId && evt.name == confAction && btnNum == confNum) {
            def rModes = settings["gnButtonModes${i}"]
            if (rModes && !(rModes as List).contains(location.mode)) {
                logInfo("${rName}: Good Night button pressed, but not in an allowed mode.")
                return
            }

            def sw = settings["roomSwitch${i}"]
            if (sw) {
                if (sw.currentValue("switch") == "on") {
                    logInfo("${rName}: Toggle button pressed. Turning Good Night OFF.")
                    sw.off()
                } else {
                    logInfo("${rName}: Toggle button pressed. Turning Good Night ON.")
                    sw.on()
                }
            }
        }
    }
}

// --- READING LIGHT BUTTON ENGINE ---
def readingButtonHandler(evt) {
    def btnId = evt.device.id
    def btnNum = evt.value

    for (int i = 1; i <= 4; i++) {
        if (!settings["enableRoom${i}"]) continue

        for (int l = 1; l <= 2; l++) {
            if (!settings["enableReadingLight${l}_${i}"]) continue
            
            def confBtn = settings["readingButton${l}_${i}"]
            def confNum = settings["readingButtonNum${l}_${i}"]?.toString() ?: "1"

            if (confBtn && confBtn.id == btnId && btnNum == confNum) {
                toggleReadingMode(i, l)
                return
            }
        }
    }
}

def toggleReadingMode(roomNum, lightNum) {
    if (!settings["enableReadingLight${lightNum}_${roomNum}"]) return

    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    def rLevel = settings["readingLevel${lightNum}_${roomNum}"] ?: 30
    def rTimeout = settings["readingTimeout${lightNum}_${roomNum}"] ?: 60
    def rModes = settings["readingModes${lightNum}_${roomNum}"]

    if (!rLight) return

    if (rModes && !(rModes as List).contains(location.mode)) {
        logInfo("${rName}: Reading button pushed, but not in an allowed mode.")
        return
    }

    def isActive = state."readingModeActive_${roomNum}_${lightNum}"

    if (isActive) {
        logInfo("${rName}: Reading Light ${lightNum} OFF (Toggled manually).")
        endReadingMode(roomNum, lightNum)
    } else {
        logInfo("${rName}: Reading Light ${lightNum} ON. Level: ${rLevel}%, Timer: ${rTimeout}m.")
        state."readingModeActive_${roomNum}_${lightNum}" = true
        rLight.setLevel(rLevel)
        runIn(rTimeout * 60, "readingTimeoutRoom${roomNum}Light${lightNum}")
    }
}

def readingTimeoutRoom1Light1() { endReadingMode(1, 1) }
def readingTimeoutRoom1Light2() { endReadingMode(1, 2) }
def readingTimeoutRoom2Light1() { endReadingMode(2, 1) }
def readingTimeoutRoom2Light2() { endReadingMode(2, 2) }
def readingTimeoutRoom3Light1() { endReadingMode(3, 1) }
def readingTimeoutRoom3Light2() { endReadingMode(3, 2) }
def readingTimeoutRoom4Light1() { endReadingMode(4, 1) }
def readingTimeoutRoom4Light2() { endReadingMode(4, 2) }

def endReadingMode(roomNum, lightNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    
    logInfo("${rName}: Reading Mode for Light ${lightNum} ended. Turning off.")
    state."readingModeActive_${roomNum}_${lightNum}" = false
    unschedule("readingTimeoutRoom${roomNum}Light${lightNum}")
    
    if (rLight) rLight.off()
}

def isReadingLightActive(roomNum, lightDeviceId) {
    for (int l = 1; l <= 2; l++) {
        if (!settings["enableReadingLight${l}_${roomNum}"]) continue
        
        if (state."readingModeActive_${roomNum}_${l}") {
            def rLight = settings["readingLight${l}_${roomNum}"]
            if (rLight && rLight.id == lightDeviceId) return true
        }
    }
    return false
}

// --- UPDATED HOURLY FAN WIGGLE (TARGET ENFORCEMENT) ---
def doHourlyWiggle() {
    if (!settings.enableWiggle) return 
    
    logInfo("Executing Hourly RF Fan Wiggle to actively enforce target speeds...")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.currentValue("switch") == "on" && settings["enableWiggle${i}"]) {
            def cFanSpeed = settings["ceilingFanSpeed${i}"]
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            def fType = settings["fanType${i}"] ?: "3_speed"
            
            if (cFanSpeed && fType != "on_off") {
                // Determine what the fan SHOULD be at based on current temperature
                def sensor = settings["tempSensor${i}"]
                def currentTemp = sensor ? sensor.currentValue("temperature") : null
                def cFanSetpoint = settings["ceilingFanSetpoint${i}"]
                def delta = settings["fanSpeedDelta${i}"] ?: 1.0
                
                def targetSpeed = "off"
                if (cFanSetpoint && currentTemp != null) {
                    targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                }
                
                if (targetSpeed != "off") {
                    def dropSpeed = getDropSpeed(targetSpeed, fType)
                    if (dropSpeed) {
                        logInfo("${rName}: Wiggle - Target speed is ${targetSpeed.toUpperCase()}. Dropping to ${dropSpeed.toUpperCase()} temporarily to force resync.")
                        cFanSpeed.setSpeed(dropSpeed)
                    }
                } else {
                    // NEW LOW-OFF WIGGLE LOGIC FOR BOND BRIDGES
                    logInfo("${rName}: Wiggle - Target speed is OFF. Bumping to LOW to force Bond Bridge reset.")
                    cFanSpeed.setSpeed("low")
                }
            }
        }
    }
    
    // Evaluate runs 10 seconds later, notices the fan is now below its calculated target, and securely bumps it back to what it should be.
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
    def anyoneAsleep = false
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.currentValue("switch") == "on") {
            anyoneAsleep = true
            break
        }
    }
    
    if (anyoneAsleep) {
        if (txtLogEnable) log.debug "PERIODIC ENFORCEMENT: Waking up to verify system state..."
        
        evaluateGlobalMode(null)
        
        for (int i = 1; i <= 4; i++) {
            if (settings["enableRoom${i}"]) {
                def sw = settings["roomSwitch${i}"]
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                
                if (sw && sw.currentValue("switch") == "on") {
                    
                    // --- UPDATED AND FORTIFIED PAUSE CHECK ---
                    def pauseEnforce = settings["pauseLightingEnforcement${i}"]
                    def pauseState = pauseEnforce ? pauseEnforce.currentValue("switch") : "unconfigured"
                    def isPaused = (pauseState == "on")
                    
                    if (!isPaused) {
                        if (txtLogEnable) {
                            def pName = pauseEnforce ? pauseEnforce.displayName : "No Switch Configured"
                            log.debug "ENFORCEMENT: Pause switch [${pName}] is currently [${pauseState}]. Proceeding with forced light shutdown."
                        }
                        
                        def lights = settings["roomLights${i}"]
                        if (lights) {
                            lights.each { lgt -> 
                                if (lgt.currentValue("switch") == "on") {
                                    if (isReadingLightActive(i, lgt.id)) {
                                        if (txtLogEnable) log.debug "ENFORCEMENT: Skipping [${lgt.displayName}] as Reading Mode is active."
                                        return 
                                    }
                                    if (lgt.hasCommand("setLevel")) {
                                        lgt.setLevel(1)
                                        pauseExecution(400)
                                    }
                                    lgt.off()
                                    logInfo("ENFORCEMENT: ${rName} is asleep but light [${lgt.displayName}] was ON. Forced OFF.")
                                }
                            }
                        }
                        
                        def lightsOn = settings["roomLightsOn${i}"]
                        if (lightsOn) {
                            lightsOn.each { lgt ->
                                if (lgt.currentValue("switch") == "off") {
                                    lgt.on()
                                    logInfo("ENFORCEMENT: ${rName} is asleep but bedtime light/plug [${lgt.displayName}] was OFF. Forced ON.")
                                }
                            }
                        }
                        
                    } else {
                        if (txtLogEnable) log.debug "ENFORCEMENT: Lighting checks successfully paused for ${rName} (Sunrise Active)."
                    }
                    
                    def shadeContact = settings["shadeContact${i}"]
                    def shade = settings["roomShade${i}"]
                    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
                        shade.close()
                        logInfo("ENFORCEMENT: ${rName} is asleep but shade contact was OPEN. Forced CLOSE.")
                    }

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

def areAllLeadRoomsAsleep() {
    if (!settings.leadRooms) return false
    def allAsleep = true
    def roomsList = settings.leadRooms as List
    roomsList.each { rNum ->
        def sw = settings["roomSwitch${rNum}"]
        if (!sw || sw.currentValue("switch") != "on") {
            allAsleep = false
        }
    }
    return allAsleep
}

def globalMotionHandler(evt) {
    if (evt.value == "inactive") {
        if (areRequiredRoomsAsleep()) {
            logInfo("GLOBAL SYNC: Motion stopped on ${evt.device.displayName}. Re-evaluating sleep criteria...")
            evaluateGlobalMode(evt)
        }
    }
}

def blockingSwitchHandler(evt) {
    if (evt.value == "off") {
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
        
        if (settings.enableLeadRoomOverride && settings.leadRooms?.contains(roomNum.toString())) {
            if (areAllLeadRoomsAsleep()) {
                def delaySecs = (settings.leadRoomTimeout ?: 30) * 60
                state.overrideScheduledTime = now() + (delaySecs * 1000)
                logInfo("GOOD NIGHT OVERRIDE: All selected Lead Rooms are asleep. Waiting ${settings.leadRoomTimeout} minutes for other rooms.")
                runIn(delaySecs, "evaluateLeadRoomOverride")
            }
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
        
        if (settings.enableLeadRoomOverride && settings.leadRooms?.contains(roomNum.toString())) {
            unschedule("evaluateLeadRoomOverride")
            state.remove("overrideScheduledTime")
            logInfo("GOOD NIGHT OVERRIDE: A Lead Room (${roomNum}) woke up. Canceled evaluation.")
        }
    }
    evaluateGlobalMode(evt)
}

def evaluateLeadRoomOverride() {
    state.remove("overrideScheduledTime") 
    
    if (!settings.enableLeadRoomOverride || !settings.targetOverrideMode) return

    logInfo("GOOD NIGHT OVERRIDE: Timeout reached. Checking other rooms and motion.")

    def otherRoomAwake = false
    def lRooms = settings.leadRooms as List
    
    for (int i = 1; i <= 4; i++) {
        if (!lRooms.contains(i.toString()) && settings["enableRoom${i}"]) {
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
    
    if (nightStartTime && nightEndTime && targetNightMode) {
        def nightStart = timeToday(nightStartTime, location.timeZone)
        def nightEnd = timeToday(nightEndTime, location.timeZone)
        
        def isNightWindow = false
        if (nightStart.time <= nightEnd.time) {
            isNightWindow = (now.time >= nightStart.time && now.time <= nightEnd.time)
        } else {
            isNightWindow = (now.time >= nightStart.time || now.time <= nightEnd.time)
        }
        
        if (isNightWindow && currentMode != targetNightMode) {
            def isAllowedMode = true
            if (allowedNightModes) isAllowedMode = (allowedNightModes as List).contains(currentMode)
            
            if (isAllowedMode) {
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
                        if (!state.nightModeScheduledTime) {
                            state.nightModeScheduledTime = now() + (nDelay * 1000)
                            logInfo("GLOBAL SYNC: Required rooms asleep, house is quiet, and no blocking devices are ON. Scheduling Night Mode (${targetNightMode}) in ${nDelay} seconds.")
                            runIn(nDelay, "executeNightModeChange")
                        }
                    } else {
                        if (state.nightModeScheduledTime) {
                            logInfo("GLOBAL SYNC: Waiting on motion sensors to clear or blocking devices to turn OFF before shifting mode. (Timer Paused)")
                            unschedule("executeNightModeChange")
                            state.remove("nightModeScheduledTime")
                        }
                    }
                } else {
                    if (state.nightModeScheduledTime) {
                        logInfo("GLOBAL SYNC: A required room woke up. (Timer Canceled)")
                        unschedule("executeNightModeChange")
                        state.remove("nightModeScheduledTime")
                    }
                }
            }
        } else {
            if (state.nightModeScheduledTime) {
                 unschedule("executeNightModeChange")
                 state.remove("nightModeScheduledTime")
            }
        }
    }
    
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
            if (isReadingLightActive(roomNum, lgt.id)) {
                logInfo("${rName}: Skipping light [${lgt.displayName}] during Good Night sweep (Reading Mode active).")
                return // skip
            }
            if (lgt.hasCommand("setLevel")) {
                lgt.setLevel(1)
                pauseExecution(400)
            }
            lgt.off()
        }
        logInfo("${rName}: Lights turned OFF (w/ 1% flashbang protection if applicable).") 
    }
    
    def lightsOn = settings["roomLightsOn${roomNum}"]
    if (lightsOn) {
        lightsOn.each { lgt ->
            if (lgt.currentValue("switch") != "on") lgt.on()
        }
        logInfo("${rName}: Bedtime Lights/Plugs turned ON.")
    }
    
    def shadeContact = settings["shadeContact${roomNum}"]
    def shade = settings["roomShade${roomNum}"]
    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
        shade.close()
        logInfo("${rName}: Shade contact is open. Closing shade.")
    }
    
    // --- UPDATED AUDIO EXECUTION LOGIC (WITH SMART PLUG DELAY) ---
    def speakerPower = settings["roomSpeakerPower${roomNum}"]
    def speaker = settings["roomSpeaker${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    
    if (speaker || audioType == "switch") {
        if (speakerPower) {
            if (speakerPower.hasCommand("refresh")) {
                speakerPower.refresh()
                logInfo("${rName}: Refreshed speaker power plug state.")
                pauseExecution(1000) 
            }
            if (speakerPower.currentValue("switch") == "off") {
                logInfo("${rName}: Speaker power plug is OFF. Turning ON and waiting 120s before initiating audio play.")
                speakerPower.on()
                runIn(120, "playDelayedAudioRoom${roomNum}")
            } else {
                executeAudioPlay(roomNum)
            }
        } else {
            executeAudioPlay(roomNum)
        }
    }
    
    evaluateFans(roomNum)
}

// --- DELAYED AUDIO WRAPPERS ---
def playDelayedAudioRoom1() { executeAudioPlay(1) }
def playDelayedAudioRoom2() { executeAudioPlay(2) }
def playDelayedAudioRoom3() { executeAudioPlay(3) }
def playDelayedAudioRoom4() { executeAudioPlay(4) }

def executeAudioPlay(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"

    if (speaker) {
        def setVol = settings["audioVolume${roomNum}"]
        if (setVol != null) {
            speaker.setVolume(setVol)
            logInfo("${rName}: Speaker volume forced to ${setVol}%.")
        }

        if (audioType == "uri") {
            def trackToPlay = state."nextUri${roomNum}"
            if (trackToPlay) {
                speaker.playTrack(trackToPlay)
                logInfo("${rName}: Playing tonight's Sonos URI (${trackToPlay}).")
                state."lastUri${roomNum}" = trackToPlay
            }
        } else if (audioType == "switch") {
            def switchToTurnOnId = state."nextSwitchId${roomNum}"
            if (switchToTurnOnId) {
                def targetSw = null
                for(int u = 1; u <= 5; u++) {
                    def sw = settings["audioSwitch${roomNum}_${u}"]
                    if (sw?.id == switchToTurnOnId) {
                        targetSw = sw
                        break
                    }
                }
                
                if (targetSw) {
                    targetSw.on()
                    logInfo("${rName}: Triggered Sonos Favorite Virtual Switch (${targetSw.displayName}).")
                    state."lastSwitchId${roomNum}" = switchToTurnOnId
                    
                    if (setVol != null) {
                        runIn(30, "applyDelayedVolumeRoom${roomNum}")
                    }
                }
            }
        }
        
        def sTimer = settings["audioTimer${roomNum}"]
        if (sTimer) {
            logInfo("${rName}: Sleep timer set for ${sTimer} minutes.")
            runIn(sTimer * 60, "stopAudioRoom${roomNum}") 
        }
    }
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

def applyDelayedVolumeRoom1() { executeDelayedVolume(1) }
def applyDelayedVolumeRoom2() { executeDelayedVolume(2) }
def applyDelayedVolumeRoom3() { executeDelayedVolume(3) }
def applyDelayedVolumeRoom4() { executeDelayedVolume(4) }

def executeDelayedVolume(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def setVol = settings["audioVolume${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (speaker && setVol != null) {
        speaker.setVolume(setVol)
        logInfo("${rName}: Applied delayed volume enforcement (${setVol}%) 30 seconds after Sonos Favorite start.")
    }
}

def endRoomGoodNight(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    logInfo("${rName}: Executing Wake-Up routine (shutting down fans and audio).")
    
    unschedule("playDelayedAudioRoom${roomNum}")
    unschedule("stopAudioRoom${roomNum}")
    unschedule("applyDelayedFanSpeedRoom${roomNum}")
    unschedule("applyDelayedVolumeRoom${roomNum}")
    state.remove("pendingFanSpeed${roomNum}")
    
    def stdFans = settings["roomFans${roomNum}"]
    if (stdFans) stdFans.off()
    
    def lightsOn = settings["roomLightsOn${roomNum}"]
    if (lightsOn) {
        lightsOn.each { lgt ->
            if (lgt.currentValue("switch") != "off") lgt.off()
        }
        logInfo("${rName}: Bedtime Lights/Plugs turned OFF (Wake-up).")
    }
    
    // --- 3x REDUNDANT CEILING FAN SHUTDOWN (W/ LOW-OFF WIGGLE FIX) ---
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") {
        cFanSpeed.setSpeed("low") // Pulse 1 (Wiggle trigger)
        logInfo("${rName}: Applying Low-Off Wiggle fix. Initiating 3x redundant shutdown sequence before cutting power.")
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
    
    prepNextAudio(roomNum) 
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

// --- UPDATED AUDIO ROTATION ENGINE ---
def prepNextAudio(roomNum) {
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    
    if (audioType == "uri") {
        state.remove("nextSwitchId${roomNum}")
        
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
        
    } else if (audioType == "switch") {
        state.remove("nextUri${roomNum}")
        
        def switches = []
        for(int u = 1; u <= 5; u++) {
            def sw = settings["audioSwitch${roomNum}_${u}"]
            if (sw) switches << sw.id
        }
        
        if (switches.size() > 0) {
            if (switches.size() == 1) {
                state."nextSwitchId${roomNum}" = switches[0]
            } else {
                def lastPlayed = state."lastSwitchId${roomNum}"
                def availableSwitches = switches.findAll { it != lastPlayed }
                if (availableSwitches.size() == 0) availableSwitches = switches 
                def chosen = availableSwitches[new Random().nextInt(availableSwitches.size())]
                state."nextSwitchId${roomNum}" = chosen
            }
        } else {
            state.remove("nextSwitchId${roomNum}")
        }
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
    state.remove("nightModeScheduledTime")
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
