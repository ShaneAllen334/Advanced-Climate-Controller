/**
 * Advanced Ceiling Fan Climate Control
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Ceiling Fan Climate Control",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-zone ceiling fan controller supporting 3-Speed, 5-Speed, 6-Speed, and Simple fans, with Wet/Dry Bulb options, configurable speed thresholds, dynamic occupancy, and Smart Lighting Relay overrides.",
    category: "Comfort",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Ceiling Fan Climate Control</b>", install: true, uninstall: true) {
  
        section("<b>Live Fan Dashboard</b>") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your ceiling fans, comparing actual states to target states, alongside Occupancy and Temperature data.</div>"
            
            def appStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? 
                "<span style='color:red;'><b>DISABLED</b> (Master Switch is OFF)</span>" : 
                "<span style='color:green;'><b>ACTIVE</b></span>"
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>App Status:</b> ${appStatus}</div>"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 6px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Room</th><th>Type</th><th>Occupied?</th><th>Dry Bulb</th><th>Wet Bulb</th><th>Target</th><th>Actual State</th><th>Master Relay</th><th>Status</th></tr></thead>
                <tbody>
            """
            
            def hasZones = false
            
            def isAwayDash = awayModes ? (awayModes as List).contains(location.mode) : false
            def isNightDash = nightModes ? (nightModes as List).contains(location.mode) : false
            
            for (int i = 1; i <= 8; i++) {
                def rawFanType = settings["z${i}FanType"] ?: "speed3"
                def fanType = (rawFanType == "speed") ? "speed3" : rawFanType // Backward compatibility
                def hasDevice = (fanType.startsWith("speed") && settings["z${i}Fan"]) || (fanType == "switch" && settings["z${i}SimpleFan"])
                
                if (settings["enableZ${i}"] && hasDevice) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    def zTimeoutMs = (settings["z${i}OccupancyTimeout"] ?: 30) * 60000

                    def tDev = settings["z${i}Temp"]
                    def hDev = settings["z${i}Hum"]
                    def cMethod = settings["z${i}ControlMethod"] ?: "wetBulb"
                    
                    def zTemp = tDev ? (tDev.currentValue("temperature") ?: "--") : "--"
                    def zHum = hDev ? (hDev.currentValue("humidity") ?: "--") : "--"
                    
                    def zWetBulb = "--"
                    if (tDev && hDev && zTemp != "--" && zHum != "--") {
                        zWetBulb = getWetBulbF(zTemp.toBigDecimal(), zHum.toBigDecimal())
                    }
                    
                    def isOccupiedFlag = getRoomOccupancy(i, zTimeoutMs)
                    def actSwitch = settings["z${i}ActivitySwitch"]
                    def isActivityOverride = (actSwitch && actSwitch.currentValue("switch") == "on")
                    
                    def isOccupiedDisplay = "No"
                    if (isActivityOverride) {
                        isOccupiedDisplay = "<span style='color:blue;'>Yes (Override)</span>"
                    } else if (isOccupiedFlag) {
                        isOccupiedDisplay = "Yes"
                    }
                
                    def emptyOverride = (!isOccupiedFlag && settings["enableOccupancy"])
                    def typeDisplay = ""
                    def targetDisplay = "<b>${settings["z${i}Setpoint"]}° (${cMethod == 'dryBulb' ? 'DB' : 'WB'})</b>"
                    def actualDisplay = ""
                    def relayDisplay = ""
                    def gnStatus = ""
                    
                    if (fanType.startsWith("speed")) {
                        if (fanType == "speed6") typeDisplay = "6-Speed Fan"
                        else if (fanType == "speed5") typeDisplay = "5-Speed Fan"
                        else typeDisplay = "3-Speed Fan"
                        
                        def fDev = settings["z${i}Fan"]
                        def pDev = settings["z${i}Power"]
                        def zSpeed = fDev.currentValue("speed") ?: "unknown"
                        def zTargetSpeed = state["z${i}Target"] ?: "off"
                        
                        actualDisplay = zSpeed.capitalize()
                        def pwrState = pDev ? pDev.currentValue("switch") : "N/A"
                        relayDisplay = pDev ? pwrState?.toUpperCase() : "<span style='color:gray;'>NONE</span>"
                        
                        if (settings["z${i}GnSwitch"] && settings["z${i}GnSwitch"].currentValue("switch") == "on") {
                            gnStatus = "<span style='color:orange;'>Isolated (GN)</span>"
                        } else if (settings["z${i}RelayAlwaysOn"] && zSpeed == "off" && zTargetSpeed == "off" && pwrState == "on") {
                            gnStatus = "<span style='color:purple;'>Relay 24/7 Active</span>"
                        } else if (zSpeed == "off" && zTargetSpeed == "off" && pwrState == "on") {
                            gnStatus = "<span style='color:purple;'>Lighting Override (Relay ON)</span>"
                        } else if (emptyOverride) {
                            gnStatus = "<span style='color:gray;'>Empty (Ignored)</span>"
                        } else if (zSpeed != zTargetSpeed) {
                            gnStatus = "<span style='color:blue;'>Stepping to ${zTargetSpeed.capitalize()}...</span>"
                        } else {
                            gnStatus = "<span style='color:green;'>Target Reached</span>"
                        }
                    } else {
                        typeDisplay = "On/Off Relay"
                        def sDev = settings["z${i}SimpleFan"]
                        def sState = sDev.currentValue("switch")?.toUpperCase() ?: "UNKNOWN"
                        
                        actualDisplay = "<span style='color:gray;'>N/A</span>"
                        relayDisplay = sState
                        
                        if (settings["z${i}GnSwitch"] && settings["z${i}GnSwitch"].currentValue("switch") == "on") {
                            gnStatus = "<span style='color:orange;'>Isolated (GN)</span>"
                        } else if (emptyOverride) {
                            gnStatus = "<span style='color:gray;'>Empty (Ignored)</span>"
                        } else {
                            gnStatus = "<span style='color:green;'>Auto Tracking</span>"
                        }
                    }

                    def dbDisplay = (cMethod == "dryBulb") ? "<b>${zTemp}°</b>" : "${zTemp}°"
                    def wbDisplay = (cMethod == "wetBulb") ? "<b>${zWetBulb}°</b>" : "${zWetBulb}°"

                    dashHTML += "<tr><td class='dash-hl'>${zName}</td><td>${typeDisplay}</td><td>${isOccupiedDisplay}</td><td>${dbDisplay}</td><td>${wbDisplay}</td><td>${targetDisplay}</td><td>${actualDisplay}</td><td><b>${relayDisplay}</b></td><td>${gnStatus}</td></tr>"
                }
            }
            
            dashHTML += "</tbody></table>"
            if (hasZones) paragraph dashHTML else paragraph "<i>No rooms configured yet.</i>"
        }

        section("<b>Action History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
            }
        }

        section("<b>App Control</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> A master kill-switch to quickly bypass all app automation.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }
        
        section("<b>Smart Lighting Support (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Ensures your Master Power Relays stay ON (even if the fan blades are OFF) so your ceiling fan light kits continue to work when the room needs artificial light.</div>"
            input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Keeps relays ON if Occupied)", required: false
        }

        section("<b>3-Speed Fan Thresholds (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines how far the room temperature must rise above the target setpoint to trigger each multi-speed fan step. (Anything above your Medium threshold will trigger High speed).</div>"
            input "thresholdLow", "decimal", title: "Delta for Low Speed (+°F)", required: true, defaultValue: 1.5
            input "thresholdMed", "decimal", title: "Delta for Medium Speed (+°F)", required: true, defaultValue: 3.0
        }
        
        section("<b>5-Speed Fan Thresholds (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Specific temperature thresholds for 5-Speed fans.</div>"
            input "t5S1", "decimal", title: "Speed 1: Low (+°F)", required: true, defaultValue: 0.5
            input "t5S2", "decimal", title: "Speed 2: Med-Low (+°F)", required: true, defaultValue: 1.0
            input "t5S3", "decimal", title: "Speed 3: Medium (+°F)", required: true, defaultValue: 1.5
            input "t5S4", "decimal", title: "Speed 4: Med-High (+°F)", required: true, defaultValue: 2.0
            paragraph "<i>Anything above Speed 4 triggers Speed 5 (High).</i>"
        }

        section("<b>6-Speed Fan Thresholds (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Specific temperature thresholds for 6-Speed fans.</div>"
            input "t6S1", "decimal", title: "Speed 1: Low (+°F)", required: true, defaultValue: 0.5
            input "t6S2", "decimal", title: "Speed 2: Med-Low (+°F)", required: true, defaultValue: 1.0
            input "t6S3", "decimal", title: "Speed 3: Medium (+°F)", required: true, defaultValue: 1.5
            input "t6S4", "decimal", title: "Speed 4: Med-High (+°F)", required: true, defaultValue: 2.0
            input "t6S5", "decimal", title: "Speed 5: High (+°F)", required: true, defaultValue: 2.5
            paragraph "<i>Anything above Speed 5 triggers Speed 6 (Auto/Max).</i>"
        }

        section("<b>RF / Bond Fan Reliability & Relays</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> <b>1) Stepping:</b> Prevents RF fans from missing commands.<br><b>2) Wiggle:</b> Hourly routine to drop the fan one speed and bump it back.<br><b>3) Spin-Down:</b> Delays power relay shutoff until blades stop. <i>(These settings only apply to Multi-Speed fans)</i></div>"
            input "rfStepDelay", "number", title: "Seconds between sequential fan speed steps", required: true, defaultValue: 3
            input "enableWiggle", "bool", title: "Enable Hourly Fan Wiggle (Self-Healing)", defaultValue: true
            input "relaySpinDown", "number", title: "Seconds to wait before killing power relay (Spin-down delay)", required: true, defaultValue: 15
        }

        section("<b>Dynamic Occupancy</b>") {
            input "enableOccupancy", "bool", title: "<b>Enable Dynamic Occupancy</b>", defaultValue: false, submitOnChange: true
            if (enableOccupancy) {
                paragraph "<i>Occupancy timeouts are now configured individually inside each room's settings. Default is 30 minutes.</i>"
            }
        }

        section("<b>Operating Modes Configuration</b>") {
            input "awayModes", "mode", title: "<b>Away Modes</b> (All fans step to OFF)", multiple: true, required: false
            input "homeModes", "mode", title: "<b>Active Modes</b> (Home, Morning, Arrival - Uses Room Setpoints)", multiple: true, required: false
            input "nightModes", "mode", title: "<b>Good Night Modes</b> (Turns off fans unless GN override is active)", multiple: true, required: false
        }

        section("<b>Room Fan Configurations</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Click on a room below to expand its settings.</div>"
        }

        for (int i = 1; i <= 8; i++) {
            def currentRoomName = settings["z${i}Name"] ?: "Room ${i}"
            
            // Setting hidden: true forces the accordion to be collapsed by default
            section("<b>⚙️ ${currentRoomName}</b>", hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Room ${i}</b>", submitOnChange: true
                
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Room Name", required: false, defaultValue: "Room ${i}"
                    input "z${i}FanType", "enum", title: "Fan Control Type", options: ["speed3":"3-Speed Fan", "speed5":"5-Speed Fan", "speed6":"6-Speed Fan", "switch":"Simple On/Off Switch"], required: true, defaultValue: "speed3", submitOnChange: true
                    
                    def currentFanType = settings["z${i}FanType"] ?: "speed3"
                    
                    if (currentFanType == "switch") {
                        input "z${i}SimpleFan", "capability.switch", title: "Fan Power Switch", required: true
                    } else {
                        input "z${i}Fan", "capability.fanControl", title: "Ceiling Fan Device", required: true
                        input "z${i}Power", "capability.switch", title: "Master Power Relay for Fan (Optional)", required: false, submitOnChange: true
                        
                        if (settings["z${i}Power"]) {
                            input "z${i}RelayAlwaysOn", "bool", title: "Keep power relay ON 24/7 (Prevents fan light from defaulting to ON after power loss)", defaultValue: false, submitOnChange: true
                            if (!settings["z${i}RelayAlwaysOn"]) {
                                input "z${i}KeepRelayUnoccupied", "bool", title: "Keep power relay ON when Unoccupied (Only turn off blades)", defaultValue: false
                                input "z${i}SmartRelay", "bool", title: "Keep power relay ON for Lighting Needs (Evaluates Sunset, Overcast, and Shades)", defaultValue: true, submitOnChange: true
                                if (settings["z${i}SmartRelay"]) {
                                    input "z${i}ShadeContact", "capability.contactSensor", title: "Shade Contact Sensor (Closed = Room is Dark)", required: false
                                }
                            }
                        }
                    }
                    
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional)", required: false
                    
                    input "z${i}Motion", "capability.motionSensor", title: "Motion Sensor (Optional)", required: false, submitOnChange: true
                    if (settings["enableOccupancy"] && settings["z${i}Motion"]) {
                        input "z${i}OccupancyTimeout", "number", title: "Minutes of no motion before turning off fan", required: false, defaultValue: 30
                    }
                    
                    input "z${i}ActivitySwitch", "capability.switch", title: "Activity Override Switch (e.g., Roku TV) - Keeps room 'Occupied'", required: false
                    input "z${i}ControlMethod", "enum", title: "Control Method", options: ["wetBulb":"Wet Bulb (Feels Like)", "dryBulb":"Dry Bulb (Standard Temp)"], required: true, defaultValue: "wetBulb"
                    input "z${i}Setpoint", "decimal", title: "Target Setpoint (°F)", required: true, defaultValue: 72.0
                    input "z${i}GnSwitch", "capability.switch", title: "Good Night Override Switch (Optional)", required: false
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getSpeedLevels(fanType) {
    if (fanType == "speed6") {
        return ["off": 0, "low": 1, "medium-low": 2, "medium": 3, "medium-high": 4, "high": 5, "auto": 6]
    } else if (fanType == "speed5") {
        return ["off": 0, "low": 1, "medium-low": 2, "medium": 3, "medium-high": 4, "high": 5]
    }
    // Default 3-Speed
    return ["off": 0, "low": 1, "medium": 2, "high": 3]
}

def getLevelSpeeds(fanType) {
    if (fanType == "speed6") {
        return [0: "off", 1: "low", 2: "medium-low", 3: "medium", 4: "medium-high", 5: "high", 6: "auto"]
    } else if (fanType == "speed5") {
        return [0: "off", 1: "low", 2: "medium-low", 3: "medium", 4: "medium-high", 5: "high"]
    }
    // Default 3-Speed
    return [0: "off", 1: "low", 2: "medium", 3: "high"]
}

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    
    for (int i = 1; i <= 8; i++) {
        if (!state["z${i}Target"]) state["z${i}Target"] = "off"
    }
    
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "sunset", sensorHandler)
    subscribe(location, "sunrise", sensorHandler)
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", sensorHandler)
    if (overcastSwitch) subscribe(overcastSwitch, "switch", sensorHandler)
    
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}GnSwitch"]) subscribe(settings["z${i}GnSwitch"], "switch", sensorHandler)
            if (settings["z${i}ShadeContact"]) subscribe(settings["z${i}ShadeContact"], "contact", sensorHandler)
            if (settings["z${i}ActivitySwitch"]) subscribe(settings["z${i}ActivitySwitch"], "switch", sensorHandler)
        }
    }
    
    if (enableWiggle) {
        runEvery1Hour("doHourlyWiggle")
    }
    
    logAction("Ceiling Fan Engine Initialized. Smart Lighting Tracking Active.")
    evaluateFans()
}

// Handle UI button presses
def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
        // The dynamic page will automatically reload the UI
    }
}

def modeChangeHandler(evt) {
    logAction("Location mode changed to: ${evt.value}")
    evaluateFans()
}

def motionHandler(evt) {
    if (evt.value == "active") {
        if (!state.zoneLastActive) state.zoneLastActive = [:]
        state.zoneLastActive[evt.device.id] = now()
        evaluateFans()
    }
}

def sensorHandler(evt) {
    runInMillis(2000, "evaluateFans")
}

def getRoomOccupancy(roomId, timeoutMs) {
    def actSwitch = settings["z${roomId}ActivitySwitch"]
    if (actSwitch && actSwitch.currentValue("switch") == "on") {
        return true
    }

    def mDev = settings["z${roomId}Motion"]
    if (!settings["enableOccupancy"] || !mDev) return true
    
    def lastActive = state.zoneLastActive ? state.zoneLastActive[mDev.id] : null
    if (!lastActive || (now() - lastActive) > timeoutMs) return false
    
    return true
}

def shouldKeepRelayOn(roomId, isOccupied, isAway, isNight) {
    def alwaysOn = settings["z${roomId}RelayAlwaysOn"]
    if (alwaysOn) return true

    if (isAway || isNight) return false
    
    def pDev = settings["z${roomId}Power"]
    if (!pDev) return false
    
    def keepUnocc = settings["z${roomId}KeepRelayUnoccupied"]
    if (!isOccupied && keepUnocc) return true
    
    def smartRelay = settings["z${roomId}SmartRelay"]
    if (smartRelay) {
        def overcast = overcastSwitch?.currentValue("switch") == "on"
        def shade = settings["z${roomId}ShadeContact"]
        def shadeClosed = (shade && shade.currentValue("contact") == "closed")
        
        def s = getSunriseAndSunset()
        def nowTime = new Date().time
        def isDarkTime = s.sunset ? (nowTime > s.sunset.time || nowTime < s.sunrise.time) : false
        
        if (isDarkTime || overcast || shadeClosed) return true
    }
    
    return false
}

def evaluateFans() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return

    def currentMode = location.mode
    def isAway = awayModes ? (awayModes as List).contains(currentMode) : false
    def isNight = nightModes ? (nightModes as List).contains(currentMode) : false
    def isActive = homeModes ? (homeModes as List).contains(currentMode) : (!isAway && !isNight)

    for (int i = 1; i <= 8; i++) {
        def rawFanType = settings["z${i}FanType"] ?: "speed3"
        def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
        def hasDevice = (fanType.startsWith("speed") && settings["z${i}Fan"]) || (fanType == "switch" && settings["z${i}SimpleFan"])
        
        if (settings["enableZ${i}"] && hasDevice) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def gnSwitch = settings["z${i}GnSwitch"]
            
            // Absolute Isolation Check
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") continue
            
            def zTimeoutMs = (settings["z${i}OccupancyTimeout"] ?: 30) * 60000
            def isOccupied = getRoomOccupancy(i, zTimeoutMs)
            
            // 1. AWAY MODE OR UNOCCUPIED LOGIC
            if (isAway || !isOccupied) {
                def reason = isAway ? "Away Mode" : "Unoccupied"
                turnRoomOff(i, fanType, zName, reason)
            }
            // 2. GOOD NIGHT MODE LOGIC
            else if (isNight) {
                turnRoomOff(i, fanType, zName, "Good Night Mode (Not Isolated)")
            }
            // 3. ACTIVE MODE LOGIC
            else if (isActive && isOccupied) {
                def tDev = settings["z${i}Temp"]
                def hDev = settings["z${i}Hum"]
                def setpoint = settings["z${i}Setpoint"]
                def cMethod = settings["z${i}ControlMethod"] ?: "wetBulb"
                
                if (tDev && setpoint != null) {
                    def tVal = tDev.currentValue("temperature")
                    def hVal = hDev ? hDev.currentValue("humidity") : null
                    
                    if (tVal != null) {
                        def controlTemp = tVal.toBigDecimal()
                        def methodLabel = "DB"
                        
                        if (cMethod == "wetBulb") {
                            if (hVal != null) {
                                controlTemp = getWetBulbF(tVal.toBigDecimal(), hVal.toBigDecimal())
                                methodLabel = "WB"
                            } else {
                                logInfo("Warning: ${zName} is set to Wet Bulb but has no humidity data. Using Dry Bulb fallback.")
                            }
                        }
                       
                        if (fanType.startsWith("speed")) {
                            def fDev = settings["z${i}Fan"]
                            def pDev = settings["z${i}Power"]
                            def targetSpeed = calculateSpeedLevel(controlTemp, setpoint.toBigDecimal(), fanType)
                            def delta = (controlTemp - setpoint.toBigDecimal()).setScale(1, BigDecimal.ROUND_HALF_UP)
                        
                            setFanTarget(i, fDev, pDev, zName, targetSpeed, "${methodLabel} Temp: ${controlTemp}°, Target: ${setpoint}°, Delta: +${delta}°")
                        } else {
                           def sDev = settings["z${i}SimpleFan"]
                            evaluateSimpleFan(i, sDev, zName, controlTemp, setpoint.toBigDecimal(), methodLabel)
                        }
                    }
                }
            }
            
            // 4. SMART LIGHTING RELAY PROACTIVE ENGAGEMENT & CLEANUP
            if (fanType.startsWith("speed")) {
                def fDev = settings["z${i}Fan"]
                def pDev = settings["z${i}Power"]
                
                if (pDev) {
                    def targetSpeed = state["z${i}Target"] ?: "off"
                    def pwrState = pDev.currentValue("switch")
                    def keepOn = shouldKeepRelayOn(i, isOccupied, isAway, isNight)
                    
                    if (targetSpeed == "off") {
                        if (keepOn && pwrState != "on") {
                            logAction("Smart Lighting Needed: Proactively powering ON relay for ${zName}.")
                            pDev.on()
                            runIn(5, "refreshSwitch", [data: [room: i, type: "power"], overwrite: false])
                        } 
                        else if (!keepOn && pwrState != "off" && fDev.currentValue("speed") == "off") {
                            logAction("Smart Lighting conditions cleared. Sweeping power relay OFF for ${zName}.")
                            pDev.off()
                            runIn(5, "refreshSwitch", [data: [room: i, type: "power"], overwrite: false])
                        }
                    }
                }
            }
        }
    }
}

// Bypassed data.id to prevent Hubitat object serialization errors in runIn.
def refreshSwitch(data) {
    if (!data || !data.room || !data.type) return
    
    def rNum = data.room
    
    if (data.type == "power") {
        def pDev = settings["z${rNum}Power"]
        if (pDev && pDev.hasCommand("refresh")) {
            pDev.refresh()
        }
    } else if (data.type == "simple") {
        def sDev = settings["z${rNum}SimpleFan"]
        if (sDev && sDev.hasCommand("refresh")) {
            sDev.refresh()
        }
    }
}

def turnRoomOff(roomId, fanType, roomName, reason) {
    if (fanType.startsWith("speed")) {
        def fDev = settings["z${roomId}Fan"]
        def pDev = settings["z${roomId}Power"]
        setFanTarget(roomId, fDev, pDev, roomName, "off", reason)
    } else {
        def sDev = settings["z${roomId}SimpleFan"]
        if (sDev && sDev.currentValue("switch") != "off") {
            logAction("Stopping ${roomName} simple fan relay. (${reason})")
            sDev.off()
            runIn(5, "refreshSwitch", [data: [room: roomId, type: "simple"], overwrite: false])
        }
    }
}

// === SIMPLE FAN LOGIC ===
def evaluateSimpleFan(roomId, switchDevice, roomName, currentTemp, targetSetpoint, label) {
    if (!switchDevice) return
    def delta = (currentTemp - targetSetpoint).setScale(1, BigDecimal.ROUND_HALF_UP)
    
    if (currentTemp >= targetSetpoint) {
        if (switchDevice.currentValue("switch") != "on") {
            logAction("Starting ${roomName} relay. (${label} Temp: ${currentTemp}°, Target: ${targetSetpoint}°, Delta: +${delta}°)")
            switchDevice.on()
            runIn(5, "refreshSwitch", [data: [room: roomId, type: "simple"], overwrite: false])
        }
    } else if (currentTemp <= (targetSetpoint - 0.5)) {
        if (switchDevice.currentValue("switch") != "off") {
            logAction("Stopping ${roomName} relay. (Deadband satisfied: ${currentTemp}° <= ${targetSetpoint - 0.5}°)")
            switchDevice.off()
            runIn(5, "refreshSwitch", [data: [room: roomId, type: "simple"], overwrite: false])
        }
    }
}

// === MULTI-SPEED FAN LOGIC ===
def calculateSpeedLevel(currentTemp, targetSetpoint, fanType) {
    def delta = currentTemp - targetSetpoint
    if (delta <= 0) return "off"
    
    if (fanType == "speed3") {
        def tLow = settings.thresholdLow != null ? settings.thresholdLow.toBigDecimal() : 1.5
        def tMed = settings.thresholdMed != null ? settings.thresholdMed.toBigDecimal() : 3.0
        
        if (delta <= tLow) return "low"
        if (delta <= tMed) return "medium"
        return "high"
    } 
    else if (fanType == "speed5") {
        def t1 = settings.t5S1 != null ? settings.t5S1.toBigDecimal() : 0.5
        def t2 = settings.t5S2 != null ? settings.t5S2.toBigDecimal() : 1.0
        def t3 = settings.t5S3 != null ? settings.t5S3.toBigDecimal() : 1.5
        def t4 = settings.t5S4 != null ? settings.t5S4.toBigDecimal() : 2.0
        
        if (delta <= t1) return "low"
        if (delta <= t2) return "medium-low"
        if (delta <= t3) return "medium"
        if (delta <= t4) return "medium-high"
        return "high" 
    }
    else if (fanType == "speed6") {
        def t1 = settings.t6S1 != null ? settings.t6S1.toBigDecimal() : 0.5
        def t2 = settings.t6S2 != null ? settings.t6S2.toBigDecimal() : 1.0
        def t3 = settings.t6S3 != null ? settings.t6S3.toBigDecimal() : 1.5
        def t4 = settings.t6S4 != null ? settings.t6S4.toBigDecimal() : 2.0
        def t5 = settings.t6S5 != null ? settings.t6S5.toBigDecimal() : 2.5
        
        if (delta <= t1) return "low"
        if (delta <= t2) return "medium-low"
        if (delta <= t3) return "medium"
        if (delta <= t4) return "medium-high"
        if (delta <= t5) return "high"
        return "auto" 
    }
    
    return "off" // Fallback safety
}

def setFanTarget(roomId, fDev, pDev, roomName, newTargetSpeed, reason) {
    def currentTarget = state["z${roomId}Target"] ?: "off"
    
    def rawFanType = settings["z${roomId}FanType"] ?: "speed3"
    def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
    
    if (currentTarget != newTargetSpeed) {
        def sMap = getSpeedLevels(fanType)
        def currInt = sMap[currentTarget] ?: 0
        def newInt = sMap[newTargetSpeed] ?: 0
        
        def actionStr = "Setting"
        if (newTargetSpeed == "off") actionStr = "Stopping"
        else if (currInt == 0) actionStr = "Starting"
        else if (newInt > currInt) actionStr = "Increasing"
        else actionStr = "Decreasing"
        
        logAction("${actionStr} ${roomName} to ${newTargetSpeed.toUpperCase()}. (${reason})")
        state["z${roomId}Target"] = newTargetSpeed
    }

    if (newTargetSpeed != "off" && pDev && pDev.currentValue("switch") != "on") {
        logAction("Powering ON ${roomName} relay for active cooling.")
        pDev.on()
        runIn(5, "refreshSwitch", [data: [room: roomId, type: "power"], overwrite: false])
        runInMillis(1500, "stepFanTrigger", [data: [room: roomId]])
        return
    }
    
    stepFan(roomId)
}

def stepFanTrigger(data) { stepFan(data.room) }

def stepFan(roomId) {
    def fDev = settings["z${roomId}Fan"]
    if (!fDev) return
    
    def gnSwitch = settings["z${roomId}GnSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") return
    
    def targetSpeed = state["z${roomId}Target"] ?: "off"
    def currentSpeed = fDev.currentValue("speed") ?: "off"
    
    def rawFanType = settings["z${roomId}FanType"] ?: "speed3"
    def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
    
    def sMap = getSpeedLevels(fanType)
    def lMap = getLevelSpeeds(fanType)
    
    def targetInt = sMap[targetSpeed] != null ? sMap[targetSpeed] : 0
    def currentInt = sMap[currentSpeed] != null ? sMap[currentSpeed] : 0
    def delay = rfStepDelay ?: 3
    
    if (currentInt < targetInt) {
        def nextSpeed = lMap[currentInt + 1]
        logAction("Stepping ${settings["z${roomId}Name"]} UP to ${nextSpeed.toUpperCase()}...")
        fDev.setSpeed(nextSpeed)
        runIn(delay, "stepFanTrigger", [data: [room: roomId]])
    } 
    else if (currentInt > targetInt) {
        def nextSpeed = lMap[currentInt - 1]
        logAction("Stepping ${settings["z${roomId}Name"]} DOWN to ${nextSpeed.toUpperCase()}...")
        fDev.setSpeed(nextSpeed)
        runIn(delay, "stepFanTrigger", [data: [room: roomId]])
    } 
    else if (currentInt == 0 && targetInt == 0) {
        def pDev = settings["z${roomId}Power"]
        if (pDev && pDev.currentValue("switch") != "off") {
            def spinDelay = relaySpinDown ?: 15
            logAction("${settings["z${roomId}Name"]} blades are OFF. Scheduling power relay check in ${spinDelay} seconds.")
            runIn(spinDelay, "killPowerRelay", [data: [room: roomId]])
        }
    }
}

def killPowerRelay(data) {
    def roomId = data.room
    def pDev = settings["z${roomId}Power"]
    def currentTarget = state["z${roomId}Target"]
    
    def gnSwitch = settings["z${roomId}GnSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") return
    
    if (currentTarget == "off" && pDev && pDev.currentValue("switch") != "off") {
        def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
        def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
        
        def timeoutMs = (settings["z${roomId}OccupancyTimeout"] ?: 30) * 60000
        def isOccupied = getRoomOccupancy(roomId, timeoutMs)
        
        if (shouldKeepRelayOn(roomId, isOccupied, isAway, isNight)) {
            logAction("Lighting Override Active: Blades stopped, but keeping Master Power Relay ON for Room ${roomId}.")
            return
        }
        
        logAction("Spin-down delay complete. No lighting overrides active. Killing power relay for Room ${roomId}.")
        pDev.off()
        runIn(5, "refreshSwitch", [data: [room: roomId, type: "power"], overwrite: false])
    }
}

def doHourlyWiggle() {
    logAction("Executing Hourly RF Fan Wiggle to verify speeds...")
    
    for (int i = 1; i <= 8; i++) {
        def rawFanType = settings["z${i}FanType"] ?: "speed3"
        def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
        
        if (settings["enableZ${i}"] && fanType.startsWith("speed") && settings["z${i}Fan"]) {
            def gnSwitch = settings["z${i}GnSwitch"]
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") continue 
            
            def sMap = getSpeedLevels(fanType)
            def lMap = getLevelSpeeds(fanType)
            
            def fDev = settings["z${i}Fan"]
            def current = fDev.currentValue("speed") ?: "off"
            def currentInt = sMap[current] != null ? sMap[current] : 0
            
            // Fetch the target speed to know what state the app ACTUALLY wants
            def targetSpeed = state["z${i}Target"] ?: "off"
            
            if (currentInt > 0) {
                def dropSpeed = lMap[currentInt - 1]
                logAction("Wiggle: Dropping ${settings["z${i}Name"]} to ${dropSpeed.toUpperCase()} temporarily.")
                fDev.setSpeed(dropSpeed)
            } 
            // NEW LOW-OFF WIGGLE LOGIC FOR BOND BRIDGES
            else if (currentInt == 0 && targetSpeed == "off") {
                logAction("Wiggle: Bumping ${settings["z${i}Name"]} to LOW to force Bond Bridge reset.")
                fDev.setSpeed("low")
            }
        }
    }
    
    // EvaluateFans runs 10 seconds later. It will see the fan on "low" but target "off" and send the true OFF command.
    runIn(10, "evaluateFans")
}

def getWetBulbF(tempF, rh) {
    if (tempF == null || rh == null) return null
    def tC = (tempF - 32.0) * 5.0 / 9.0
    def twC = tC * Math.atan(0.151977 * Math.pow(rh + 8.313659, 0.5)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331) + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035
    def twF = (twC * 9.0 / 5.0) + 32.0
    
    return twF.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
