/**
 * Advanced Ceiling Fan Climate Control
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Ceiling Fan Climate Control",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-zone ceiling fan controller supporting 3-Speed, 5-Speed, 6-Speed, and Simple fans, with Wet/Dry Bulb options, configurable speed thresholds, dynamic occupancy, Smart Lighting Relay overrides, and Button Controllers.",
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
            input "btnResetOverrides", "button", title: "❌ Clear All Overrides"
            input "btnClearROI", "button", title: "🗑️ Clear ROI Data"
            
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your ceiling fans, comparing actual states to target states, alongside Occupancy and Temperature data.</div>"
            
            def appStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? "<span style='color:red;'><b>DISABLED</b> (Master Switch is OFF)</span>" : "<span style='color:green;'><b>ACTIVE</b></span>"
            
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
            
            def roiHTML = """
            <table class="dash-table" style="margin-top: 15px;">
                <thead><tr><th style='background-color:#28a745;'>Room</th><th style='background-color:#28a745;'>Current Draw</th><th style='background-color:#28a745;'>Today's Cost</th><th style='background-color:#28a745;'>7-Day Avg Cost</th><th style='background-color:#17a2b8;'>Today Saved (Off)</th><th style='background-color:#17a2b8;'>7-Day Avg Saved</th></tr></thead>
                <tbody>
            """
            
            def hasZones = false
            def hasRoi = false
            
            def isAwayDash = awayModes ? (awayModes as List).contains(location.mode) : false
            def isNightDash = nightModes ? (nightModes as List).contains(location.mode) : false
            
            for (int i = 1; i <= 8; i++) {
                def rawFanType = settings["z${i}FanType"] ?: "speed3"
                def fanType = (rawFanType == "speed") ? "speed3" : rawFanType 
                def hasDevice = ((fanType.startsWith("speed") || fanType == "fixed") && settings["z${i}Fan"]) || (fanType == "switch" && settings["z${i}SimpleFan"])
                
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
                    
                    // --- OCCUPANCY COUNTDOWN LOGIC ---
                    def actSwitch = settings["z${i}ActivitySwitch"]
                    def isActivityOverride = (actSwitch && actSwitch.currentValue("switch") == "on")
                    def mDevs = settings["z${i}Motion"]
                    def isOccupiedFlag = false
                    def lastMotionTime = 0L
                    def isActivelyMoving = false

                    if (settings["enableOccupancy"] && mDevs) {
                        for (mDev in mDevs) {
                            if (mDev.currentValue("motion") == "active") {
                                isOccupiedFlag = true
                                isActivelyMoving = true
                                lastMotionTime = now()
                            } else {
                                def mTime = state.zoneLastActive ? state.zoneLastActive[mDev.id] : null
                                if (mTime) {
                                    def t = mTime.toLong()
                                    if (t > lastMotionTime) lastMotionTime = t
                                }
                            }
                        }
                    }

                    if (!isOccupiedFlag && lastMotionTime > 0) {
                        if ((now() - lastMotionTime) <= zTimeoutMs) isOccupiedFlag = true
                    } else if (!settings["enableOccupancy"] || !mDevs) {
                        isOccupiedFlag = true 
                    }
                    
                    def isOccupiedDisplay = "No"
     
                    if (isActivityOverride) {
                        isOccupiedDisplay = "<span style='color:blue;'>Yes (Override)</span>"
                    } else if (isOccupiedFlag) {
                        if (settings["enableOccupancy"] && mDevs && lastMotionTime > 0) {
                            if (isActivelyMoving) {
                                isOccupiedDisplay = "Yes <br><span style='font-size:11px;color:green;'>(Motion Active)</span>"
                            } else {
                                def elapsed = now() - lastMotionTime
                                def remainMs = zTimeoutMs - elapsed
                                if (remainMs > 0) {
                                    def remainMins = Math.ceil(remainMs / 60000).toInteger()
                                    isOccupiedDisplay = "Yes <br><span style='font-size:11px;color:gray;'>(Clear in ${remainMins}m)</span>"
                                } else {
                                    isOccupiedDisplay = "Yes"
                                }
                            }
                        } else {
                            isOccupiedDisplay = "Yes"
                        }
                    }
                    
                    // --- OVERRIDE COUNTDOWN LOGIC ---
                    def expireTime = state.overrideUntil ? state.overrideUntil[i.toString()] : null
                    def isManualOverrideActive = false
                    def overrideRemainStr = ""
                    
                    if (expireTime && now() < expireTime.toLong()) {
                        isManualOverrideActive = true
                        def remainMs = expireTime.toLong() - now()
                        def rMins = Math.ceil(remainMs / 60000).toInteger()
                        def rHrs = Math.floor(rMins / 60).toInteger()
                        def rMinRem = rMins % 60
                        overrideRemainStr = rHrs > 0 ? "${rHrs}h ${rMinRem}m" : "${rMins}m"
                    }
                
                    def emptyOverride = (!isOccupiedFlag && settings["enableOccupancy"])
                    def typeDisplay = ""
                    def targetDisplay = "<b>${settings["z${i}Setpoint"]}° (${cMethod == 'dryBulb' ? 'DB' : 'WB'})</b>"
                    def actualDisplay = ""
                    def relayDisplay = ""
                    def gnStatus = ""
                    def currentSpeedStr = "off"
                    
                    if (fanType.startsWith("speed") || fanType == "fixed") {
                        if (fanType == "fixed") typeDisplay = "Non-Controllable Fan"
                        else if (fanType == "speed6") typeDisplay = "6-Speed Fan"
                        else if (fanType == "speed5") typeDisplay = "5-Speed Fan"
                        else typeDisplay = "3-Speed Fan"
                      
                        def fDev = settings["z${i}Fan"]
                        def pDev = settings["z${i}Power"]
                        def zSpeed = fDev.currentValue("speed") ?: "unknown"
                        def zTargetSpeed = state["z${i}Target"] ?: "off"
                        def pwrState = pDev ? pDev.currentValue("switch") : "N/A"
                        
                        if (pwrState == "off") {
                            currentSpeedStr = "off"
                            actualDisplay = "<span style='color:gray;'>Off (No Pwr)</span>"
                        } else {
                            currentSpeedStr = zSpeed
                            actualDisplay = zSpeed.capitalize()
                        }
                        
                        relayDisplay = pDev ? pwrState?.toUpperCase() : "<span style='color:gray;'>NONE</span>"
                        
                        if (isManualOverrideActive) {
                            gnStatus = "<span style='color:red;'><b>Override Active</b><br><span style='font-size:11px;'>(${overrideRemainStr} left)</span></span>"
                        } else if (settings["z${i}GnSwitch"] && settings["z${i}GnSwitch"].currentValue("switch") == "on") {
                            gnStatus = "<span style='color:orange;'>Isolated (GN)</span>"
                        } else if (settings["z${i}RelayAlwaysOn"] && currentSpeedStr == "off" && zTargetSpeed == "off" && pwrState == "on") {
                            gnStatus = "<span style='color:purple;'>Relay 24/7 Active</span>"
                        } else if (currentSpeedStr == "off" && zTargetSpeed == "off" && pwrState == "on") {
                            gnStatus = "<span style='color:purple;'>Lighting Override (Relay ON)</span>"
                        } else if (emptyOverride) {
                            gnStatus = "<span style='color:gray;'>Empty (Ignored)</span>"
                        } else if (currentSpeedStr != zTargetSpeed && fanType != "fixed") {
                            gnStatus = "<span style='color:blue;'>Stepping to ${zTargetSpeed.capitalize()}...</span>"
                        } else if (currentSpeedStr != zTargetSpeed && fanType == "fixed") {
                            gnStatus = "<span style='color:blue;'>Commanding to ${zTargetSpeed.capitalize()}...</span>"
                        } else {
                            gnStatus = "<span style='color:green;'>Target Reached</span>"
                        }
                    } else {
                        typeDisplay = "On/Off Relay"
                        def sDev = settings["z${i}SimpleFan"]
                        def sState = sDev.currentValue("switch")?.toUpperCase() ?: "UNKNOWN"
                        currentSpeedStr = sState == "ON" ? "high" : "off"
                        
                        actualDisplay = "<span style='color:gray;'>N/A</span>"
                        relayDisplay = sState
              
                        if (isManualOverrideActive) {
                            gnStatus = "<span style='color:red;'><b>Override Active</b><br><span style='font-size:11px;'>(${overrideRemainStr} left)</span></span>"
                        } else if (settings["z${i}GnSwitch"] && settings["z${i}GnSwitch"].currentValue("switch") == "on") {
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
                    
                    // ROI Data Gathering
                    if (settings["z${i}WattMax"]) {
                        hasRoi = true
                        def maxW = settings["z${i}WattMax"].toBigDecimal()
                        def minW = (settings["z${i}WattMin"] ?: (maxW / 3.0)).toBigDecimal()
                        def cWatts = getWattsForSpeed(currentSpeedStr, fanType, minW, maxW)
                        
                        def todayC = state.dailyCost ? (state.dailyCost[i.toString()] ?: 0.0) : 0.0
                        def todayS = state.dailySaved ? (state.dailySaved[i.toString()] ?: 0.0) : 0.0
                        
                        def avgC = 0.0
                        if (state.historyCost && state.historyCost[i.toString()]) {
                            def hist = state.historyCost[i.toString()]
                            if (hist.size() > 0) avgC = hist.sum() / hist.size()
                        }
                        
                        def avgS = 0.0
                        if (state.historySaved && state.historySaved[i.toString()]) {
                            def hist = state.historySaved[i.toString()]
                            if (hist.size() > 0) avgS = hist.sum() / hist.size()
                        }
                        
                        def cWattsDisplay = cWatts > 0 ? "<b>${cWatts.setScale(1, BigDecimal.ROUND_HALF_UP)} W</b>" : "0 W"
                        
                        roiHTML += "<tr><td class='dash-hl'>${zName}</td><td>${cWattsDisplay}</td><td>&#36;${todayC.setScale(2, BigDecimal.ROUND_HALF_UP)}</td><td>&#36;${avgC.setScale(2, BigDecimal.ROUND_HALF_UP)}</td><td style='color:green;'><b>&#36;${todayS.setScale(2, BigDecimal.ROUND_HALF_UP)}</b></td><td style='color:green;'><b>&#36;${avgS.setScale(2, BigDecimal.ROUND_HALF_UP)}</b></td></tr>"
                    }
                }
            }
            
            dashHTML += "</tbody></table>"
            roiHTML += "</tbody></table>"
            
            if (hasZones) paragraph dashHTML else paragraph "<i>No rooms configured yet.</i>"
            
            if (hasRoi) {
                paragraph "<br><div style='font-size:14px; color:#555;'><b>Live Energy & ROI Dashboard</b></div>"
                paragraph roiHTML
            }
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
        
        section("<b>Energy & ROI Tracking (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically tracks power consumption and translates it into daily cost and savings estimates. Savings are calculated when a room is vacant and the fan turns off.</div>"
            input "energyRate", "decimal", title: 'Electricity Rate ($ per kWh)', required: true, defaultValue: 0.12
            input "calcSavingsAtMax", "bool", title: "Calculate Savings using Max Watts (Assumes the fan would have been left on High)", defaultValue: true
        }

        section("<b>Manual Overrides (Global)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Pauses automation for a specific room if a user manually changes the fan via a wall switch or dashboard. Resets automatically upon Mode change or after the defined timeout.</div>"
            input "overrideTimeoutHours", "decimal", title: "Global Manual Override Timeout (Hours)", required: true, defaultValue: 2.0
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
            input "t6S1", "decimal", title: "Speed 1: Very-Low (+°F)", required: true, defaultValue: 0.5
            input "t6S2", "decimal", title: "Speed 2: Low (+°F)", required: true, defaultValue: 1.0
            input "t6S3", "decimal", title: "Speed 3: Med-Low (+°F)", required: true, defaultValue: 1.5
            input "t6S4", "decimal", title: "Speed 4: Medium (+°F)", required: true, defaultValue: 2.0
            input "t6S5", "decimal", title: "Speed 5: Med-High (+°F)", required: true, defaultValue: 2.5
            paragraph "<i>Anything above Speed 5 triggers Speed 6 (High).</i>"
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
            
            section("<b>⚙️ ${currentRoomName}</b>", hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Room ${i}</b>", submitOnChange: true
                
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Room Name", required: false, defaultValue: "Room ${i}"
                    input "z${i}FanType", "enum", title: "Fan Control Type", options: ["speed3":"3-Speed Fan", "speed5":"5-Speed Fan", "speed6":"6-Speed Fan", "fixed":"Non-Controllable (Fixed Speed)", "switch":"Simple On/Off Switch"], required: true, defaultValue: "speed3", submitOnChange: true
                    
                    def currentFanType = settings["z${i}FanType"] ?: "speed3"
                    
                    if (currentFanType == "switch") {
                        input "z${i}SimpleFan", "capability.switch", title: "Fan Power Switch", required: true
                    } else {
                        input "z${i}Fan", "capability.fanControl", title: "Ceiling Fan Device", required: true
                        
                        if (currentFanType == "fixed") {
                            input "z${i}FixedSpeed", "enum", title: "Fixed Run Speed (When Cooling)", options: ["low":"Low", "medium-low":"Medium-Low", "medium":"Medium", "medium-high":"Medium-High", "high":"High", "on":"On"], required: true, defaultValue: "high"
                        }
                        
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
                    
                    input "z${i}WattMin", "number", title: "Minimum Power Draw (Watts on Low)", required: false
                    input "z${i}WattMax", "number", title: "Maximum Power Draw (Watts on High)", required: false
                    
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional)", required: false
                    
                    input "z${i}Motion", "capability.motionSensor", title: "Motion Sensor(s) (Optional)", required: false, multiple: true, submitOnChange: true
                    
                    if (settings["enableOccupancy"] && settings["z${i}Motion"]) {
                        input "z${i}OccupancyTimeout", "number", title: "Minutes of no motion before turning off fan", required: false, defaultValue: 30
                    }
                    
                    input "z${i}ActivitySwitch", "capability.switch", title: "Activity Override Switch (e.g., Roku TV) - Keeps room 'Occupied'", required: false
                    input "z${i}ControlMethod", "enum", title: "Control Method", options: ["wetBulb":"Wet Bulb (Feels Like)", "dryBulb":"Dry Bulb (Standard Temp)"], required: true, defaultValue: "wetBulb"
                    input "z${i}Setpoint", "decimal", title: "Target Setpoint (°F)", required: true, defaultValue: 72.0
                    input "z${i}GnSwitch", "capability.switch", title: "Good Night Override Switch (Optional)", required: false

                    // --- NEW BUTTON CONTROLLER SECTION ---
                    paragraph "<hr><b>Button Controller Overrides</b>"
                    input "z${i}Button", "capability.pushableButton", title: "Optional Button Controller", required: false, submitOnChange: true
                    if (settings["z${i}Button"]) {
                        input "z${i}BtnNum", "number", title: "Button Number to watch", required: true, defaultValue: 1
                        input "z${i}BtnOverrideHours", "decimal", title: "Button Override Duration (Hours)", required: false, defaultValue: 2.0
                        input "z${i}BtnAllowedModes", "mode", title: "Modes to allow Button Control (Leave blank for all)", multiple: true, required: false
                        
                        def speedOpts = ["off", "low", "medium-low", "medium", "medium-high", "high", "on"]
                        input "z${i}BtnPush", "enum", title: "Action: Pushed", options: speedOpts, required: false
                        input "z${i}BtnHold", "enum", title: "Action: Held", options: speedOpts, required: false
                        input "z${i}BtnDouble", "enum", title: "Action: Double Tapped", options: speedOpts, required: false
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getSpeedLevels(fanType) {
    if (fanType == "speed6") return ["off": 0, "very-low": 1, "low": 2, "medium-low": 3, "medium": 4, "medium-high": 5, "high": 6, "auto": 6]
    if (fanType == "speed5") return ["off": 0, "low": 1, "medium-low": 2, "medium": 3, "medium-high": 4, "high": 5]
    return ["off": 0, "low": 1, "medium": 2, "high": 3]
}

def getLevelSpeeds(fanType) {
    if (fanType == "speed6") return [0: "off", 1: "very-low", 2: "low", 3: "medium-low", 4: "medium", 5: "medium-high", 6: "high"]
    if (fanType == "speed5") return [0: "off", 1: "low", 2: "medium-low", 3: "medium", 4: "medium-high", 5: "high"]
    return [0: "off", 1: "low", 2: "medium", 3: "high"]
}

def getWattsForSpeed(speedStr, fanType, minW, maxW) {
    if (speedStr == "off" || speedStr == "unknown") return 0.0
    if (fanType == "fixed") return maxW
    if (speedStr == "high" || speedStr == "auto" || speedStr == "on") return maxW
    if (speedStr == "low" || speedStr == "very-low") return minW
    
    def sMap = getSpeedLevels(fanType)
    def lvl = sMap[speedStr] ?: 0
    if (lvl == 0) return 0.0
    
    def maxLvl = fanType == "speed6" ? 6 : (fanType == "speed5" ? 5 : 3)
    if (lvl >= maxLvl) return maxW
    
    def stepWatts = (maxW - minW) / (maxLvl - 1)
    return minW + (stepWatts * (lvl - 1))
}

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.overrideUntil) state.overrideUntil = [:]
    if (!state.ignoreOverridesUntil) state.ignoreOverridesUntil = [:]
    if (!state.wiggleResetTime) state.wiggleResetTime = [:]
    if (!state.wasOccupied) state.wasOccupied = [:]
    
    if (!state.dailyCost) state.dailyCost = [:]
    if (!state.dailySaved) state.dailySaved = [:]
    if (!state.historyCost) state.historyCost = [:]
    if (!state.historySaved) state.historySaved = [:]
    
    subscribe(location, "systemStart", hubRestartHandler)
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "sunset", sensorHandler)
    subscribe(location, "sunrise", sensorHandler)
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", sensorHandler)
    if (overcastSwitch) subscribe(overcastSwitch, "switch", sensorHandler)
    
    for (int i = 1; i <= 8; i++) {
        if (!state["z${i}Target"]) state["z${i}Target"] = "off"
        
        if (settings["enableZ${i}"]) {
            def rawFanType = settings["z${i}FanType"] ?: "speed3"
            def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
            
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler) 
            if (settings["z${i}GnSwitch"]) subscribe(settings["z${i}GnSwitch"], "switch", sensorHandler)
            if (settings["z${i}ShadeContact"]) subscribe(settings["z${i}ShadeContact"], "contact", sensorHandler)
            if (settings["z${i}ActivitySwitch"]) subscribe(settings["z${i}ActivitySwitch"], "switch", sensorHandler)
            
            if (settings["z${i}Button"]) {
                subscribe(settings["z${i}Button"], "pushed", buttonHandler)
                subscribe(settings["z${i}Button"], "held", buttonHandler)
                subscribe(settings["z${i}Button"], "doubleTapped", buttonHandler)
            }
            
            if ((fanType.startsWith("speed") || fanType == "fixed") && settings["z${i}Fan"]) {
                state["z${i}ExpectedSpeed"] = settings["z${i}Fan"].currentValue("speed") ?: "off"
                state["z${i}PreviousSpeed"] = state["z${i}ExpectedSpeed"]
                subscribe(settings["z${i}Fan"], "speed", fanSpeedHandler)
            } else if (fanType == "switch" && settings["z${i}SimpleFan"]) {
                state["z${i}ExpectedSimple"] = settings["z${i}SimpleFan"].currentValue("switch") ?: "off"
                state["z${i}PreviousSimple"] = state["z${i}ExpectedSimple"]
                subscribe(settings["z${i}SimpleFan"], "switch", simpleFanHandler)
            }
    
            if (settings["z${i}Power"]) {
                state["z${i}ExpectedPower"] = settings["z${i}Power"].currentValue("switch") ?: "off"
                state["z${i}PreviousPower"] = state["z${i}ExpectedPower"]
                subscribe(settings["z${i}Power"], "switch", powerRelayHandler)
            }
        }
    }
    
    if (enableWiggle) runEvery1Hour("doHourlyWiggle")
    
    schedule("0 0 0 * * ?", dailyEnergyRollover)
    runEvery5Minutes("calculateEnergyData")
    
    logAction("Ceiling Fan Engine Initialized. Overrides Active.")
    evaluateFans()
}

// === BUTTON HANDLER ===
def buttonHandler(evt) {
    def btnDev = evt.device.id
    def btnNum = evt.value
    def eventType = evt.name 
    
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Button"]?.id == btnDev) {
            if (settings["z${i}BtnNum"]?.toString() == btnNum?.toString()) {
                
                def allowedModes = settings["z${i}BtnAllowedModes"]
                if (allowedModes && !(allowedModes as List).contains(location.mode)) {
                    logInfo("Button command ignored for Room ${i} due to mode restriction.")
                    return
                }
                
                def requestedSpeed = null
                if (eventType == "pushed") requestedSpeed = settings["z${i}BtnPush"]
                else if (eventType == "held") requestedSpeed = settings["z${i}BtnHold"]
                else if (eventType == "doubleTapped") requestedSpeed = settings["z${i}BtnDouble"]
                
                if (requestedSpeed) {
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    def rawFanType = settings["z${i}FanType"] ?: "speed3"
                    def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
                    
                    def currentTarget = state["z${i}Target"] ?: "off"
                    def isOverridden = isOverrideActive(i)
                    
                    // TOGGLE LOGIC: Release override if button pressed matches current override state
                    if (isOverridden && currentTarget == requestedSpeed) {
                        logAction("Button toggle detected for ${zName}. Releasing manual override.")
                        def map = state.overrideUntil ?: [:]
                        map.remove(i.toString())
                        state.overrideUntil = map
                        evaluateFans()
                    } else {
                        logAction("Button ${btnNum} ${eventType} on ${evt.device.displayName}. Setting ${zName} to ${requestedSpeed.toUpperCase()}.")
                        
                        // Pass specific button timeout length
                        def customHrs = settings["z${i}BtnOverrideHours"]
                        setManualOverride(i, "Button ${requestedSpeed.toUpperCase()}", customHrs)
                        
                        if (fanType == "switch") {
                            def sDev = settings["z${i}SimpleFan"]
                            if (sDev) {
                                pauseOverrideDetection(i, 30) // Setup Blind Spot
                                if (requestedSpeed == "off") {
                                    setExpectedState(i, "simpleFan", "off")
                                    sDev.off()
                                } else {
                                    setExpectedState(i, "simpleFan", "on")
                                    sDev.on()
                                }
                                runIn(5, "refreshSwitch", [data: [room: i, type: "simple"], overwrite: false])
                            }
                        } else {
                            def fDev = settings["z${i}Fan"]
                            def pDev = settings["z${i}Power"]
                            
                            if (state["z${i}Target"] != requestedSpeed) {
                                state["z${i}Target"] = requestedSpeed
                                updateWiggleReset(i)
                            }
                            
                            // If relay is off, trigger it to ON, wait, then step fan
                            if (requestedSpeed != "off" && pDev && pDev.currentValue("switch") != "on") {
                                pauseOverrideDetection(i, 30) // Setup Blind Spot
                                setExpectedState(i, "power", "on")
                                pDev.on()
                                runIn(5, "refreshSwitch", [data: [room: i, type: "power"], overwrite: false])
                                runInMillis(1500, "stepFanTrigger", [data: [room: i], overwrite: false])
                            } else {
                                stepFan(i)
                            }
                        }
                    }
                }
            }
        }
    }
}

// === ENERGY ROI TRACKING ===

def calculateEnergyData() {
    def rate = settings.energyRate != null ? settings.energyRate.toBigDecimal() : 0.12
    def useMaxForSavings = settings.calcSavingsAtMax != false
    def hours = 5.0 / 60.0 
    
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"] && settings["z${i}WattMax"]) {
            def maxW = settings["z${i}WattMax"].toBigDecimal()
            def minW = (settings["z${i}WattMin"] ?: (maxW / 3.0)).toBigDecimal()
            def rawFanType = settings["z${i}FanType"] ?: "speed3"
            def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
            
            def fDev = settings["z${i}Fan"]
            def sDev = settings["z${i}SimpleFan"]
            def pDev = settings["z${i}Power"]
            def currentSpeedStr = "off"
            
            if ((fanType.startsWith("speed") || fanType == "fixed") && fDev) {
                currentSpeedStr = fDev.currentValue("speed") ?: "off"
                // Ensure wattage evaluates to 0 if the master relay is killed
                if (pDev && pDev.currentValue("switch") == "off") {
                    currentSpeedStr = "off"
                }
            }
            else if (fanType == "switch" && sDev) currentSpeedStr = sDev.currentValue("switch") == "on" ? "high" : "off"
            
            def cWatts = getWattsForSpeed(currentSpeedStr, fanType, minW, maxW)
            
            if (cWatts > 0) {
                def kwh = (cWatts * hours) / 1000.0
                def cost = kwh * rate
                def dCostMap = state.dailyCost ?: [:]
                dCostMap[i.toString()] = (dCostMap[i.toString()] ?: 0.0) + cost
                state.dailyCost = dCostMap
            }
            
            def zTimeoutMs = (settings["z${i}OccupancyTimeout"] ?: 30) * 60000
            def isOccupied = getRoomOccupancy(i, zTimeoutMs)
            def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
            
            if ((!isOccupied || isAway) && currentSpeedStr == "off") {
                def savingsWatts = useMaxForSavings ? maxW : ((minW + maxW) / 2.0)
                def kwhSaved = (savingsWatts * hours) / 1000.0
                def savedCost = kwhSaved * rate
                def dSavedMap = state.dailySaved ?: [:]
                dSavedMap[i.toString()] = (dSavedMap[i.toString()] ?: 0.0) + savedCost
                state.dailySaved = dSavedMap
            }
        }
    }
}

def dailyEnergyRollover() {
    logAction("Running daily ROI Rollover...")
    
    def hCost = state.historyCost ?: [:]
    def hSaved = state.historySaved ?: [:]
    def dCost = state.dailyCost ?: [:]
    def dSaved = state.dailySaved ?: [:]
    
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"] && settings["z${i}WattMax"]) {
            def iStr = i.toString()
            def listCost = hCost[iStr] ?: []
            listCost.add(dCost[iStr] ?: 0.0)
            if (listCost.size() > 7) listCost = listCost.takeRight(7)
            hCost[iStr] = listCost
         
            def listSaved = hSaved[iStr] ?: []
            listSaved.add(dSaved[iStr] ?: 0.0)
            if (listSaved.size() > 7) listSaved = listSaved.takeRight(7)
            hSaved[iStr] = listSaved
            
            dCost[iStr] = 0.0
            dSaved[iStr] = 0.0
        }
    }
    
    state.historyCost = hCost
    state.historySaved = hSaved
    state.dailyCost = dCost
    state.dailySaved = dSaved
}

def hubRestartHandler(evt) {
    logAction("System Startup/Reboot detected. Waiting 60 seconds for mesh network to settle before syncing fans...")
    runIn(60, "evaluateFans")
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "btnResetOverrides") {
        state.overrideUntil = [:]
        state.ignoreOverridesUntil = [:] 
        logAction("User manually cleared all overrides.")
        evaluateFans()
    } else if (btn == "btnClearROI") {
        state.dailyCost = [:]
        state.dailySaved = [:]
        state.historyCost = [:]
        state.historySaved = [:]
        logAction("User manually cleared all ROI energy data.")
    }
}

def modeChangeHandler(evt) {
    logAction("Location mode changed to: ${evt.value}. Clearing all manual overrides.")
    state.overrideUntil = [:]
    state.ignoreOverridesUntil = [:]
    
    def isNight = nightModes ? (nightModes as List).contains(evt.value) : false
    
    if (!isNight) {
        logAction("Mode transition to ${evt.value}. Enforcing a 10-minute manual override lockout to allow mesh to settle.")
        for (int i = 1; i <= 8; i++) {
            if (settings["enableZ${i}"]) {
                pauseOverrideDetection(i, 600)
            }
        }
    }

    evaluateFans()
}

def motionHandler(evt) {
    if (evt.value == "active") {
        def map = state.zoneLastActive ?: [:]
        map[evt.device.id] = now()
        state.zoneLastActive = map
        evaluateFans()
    }
}

def sensorHandler(evt) {
    runInMillis(2000, "evaluateFans")
}

// ==============================================================================
// OVERRIDE & STATE HELPERS
// ==============================================================================

def updateWiggleReset(roomId) {
    def map = state.wiggleResetTime ?: [:]
    map[roomId.toString()] = now()
    state.wiggleResetTime = map
}

def pauseOverrideDetection(roomId, seconds = 15) {
    def map = state.ignoreOverridesUntil ?: [:]
    map[roomId.toString()] = now() + (seconds * 1000)
    state.ignoreOverridesUntil = map
}

def isDetectionPaused(roomId) {
    def map = state.ignoreOverridesUntil ?: [:]
    def expireTime = map[roomId.toString()]
    if (expireTime && now() < expireTime.toLong()) return true
    return false
}

def getRoomIdFromDevice(deviceId, devType) {
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"]) {
            if (devType == "fan" && settings["z${i}Fan"]?.id == deviceId) return i
            if (devType == "simpleFan" && settings["z${i}SimpleFan"]?.id == deviceId) return i
            if (devType == "power" && settings["z${i}Power"]?.id == deviceId) return i
        }
    }
    return null
}

def setExpectedState(roomId, type, val) {
    if (type == "fan") {
        def currentE = state["z${roomId}ExpectedSpeed"]
        if (currentE && currentE != val) state["z${roomId}PreviousSpeed"] = currentE
        state["z${roomId}ExpectedSpeed"] = val
    }
    if (type == "simpleFan") {
        def currentE = state["z${roomId}ExpectedSimple"]
        if (currentE && currentE != val) state["z${roomId}PreviousSimple"] = currentE
        state["z${roomId}ExpectedSimple"] = val
    }
    
    if (type == "power") {
        def currentE = state["z${roomId}ExpectedPower"]
        if (currentE && currentE != val) state["z${roomId}PreviousPower"] = currentE
        state["z${roomId}ExpectedPower"] = val
    }
}

def setManualOverride(roomId, actionVal, customHours = null) {
    def timeoutHrs = customHours != null ? customHours.toBigDecimal() : (settings.overrideTimeoutHours != null ? settings.overrideTimeoutHours.toBigDecimal() : 2.0)
    def ms = (timeoutHrs * 3600000).toLong()
    
    def map = state.overrideUntil ?: [:]
    map[roomId.toString()] = now() + ms
    state.overrideUntil = map
    
    logAction("Manual override detected for Room ${roomId} (Set to ${actionVal.toUpperCase()}). Automation paused for ${timeoutHrs} hours.")
    
    def delaySecs = (ms / 1000).toInteger() + 5
    runIn(delaySecs, "evaluateFans", [overwrite: false])
}

def isOverrideActive(roomId) {
    def map = state.overrideUntil ?: [:]
    def expireTime = map[roomId.toString()]
 
    if (expireTime) {
        if (now() < expireTime.toLong()) {
            return true
        } else {
            map.remove(roomId.toString())
            state.overrideUntil = map
            logAction("Manual override expired for Room ${roomId}. Resuming automation.")
            return false
        }
    }
    return false
}

def fanSpeedHandler(evt) {
    def roomId = getRoomIdFromDevice(evt.device.id, "fan")
    if (!roomId) return
    def expected = state["z${roomId}ExpectedSpeed"]
    def target = state["z${roomId}Target"]
    def prev = state["z${roomId}PreviousSpeed"]
    
    if (evt.value == target) {
        state["z${roomId}ExpectedSpeed"] = evt.value
        return
    }

    if (evt.value == expected) return

    // TRANSITION LOCKOUT: If the app is actively stepping to a target, ignore all events to prevent false overrides
    if (expected != target) {
        logInfo("Room ${roomId} is transitioning. Ignoring ${evt.value} to prevent false override.")
        return
    }

    if (isDetectionPaused(roomId)) return
    
    if (evt.value == prev && prev != expected) {
        logInfo("Room ${roomId} reported previous speed (${evt.value}). Ignoring to prevent false override (likely stale polling or missed RF command).")
        return
    }
    
    if (evt.value != "unknown") {
        def gnSwitch = settings["z${roomId}GnSwitch"]
        if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
            if (state["z${roomId}Target"] != evt.value) updateWiggleReset(roomId)
            state["z${roomId}ExpectedSpeed"] = evt.value
            state["z${roomId}Target"] = evt.value
        } else {
            setManualOverride(roomId, evt.value)
            if (state["z${roomId}Target"] != evt.value) updateWiggleReset(roomId)
            state["z${roomId}ExpectedSpeed"] = evt.value
            state["z${roomId}Target"] = evt.value 
        }
    }
}

def simpleFanHandler(evt) {
    def roomId = getRoomIdFromDevice(evt.device.id, "simpleFan")
    if (!roomId) return
    def expected = state["z${roomId}ExpectedSimple"]
    def prev = state["z${roomId}PreviousSimple"]
    
    if (evt.value == expected) return
    if (isDetectionPaused(roomId)) return
    
    if (evt.value == prev && prev != expected) return 
    
    def gnSwitch = settings["z${roomId}GnSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        state["z${roomId}ExpectedSimple"] = evt.value
    } else {
        setManualOverride(roomId, "Relay ${evt.value}")
        state["z${roomId}ExpectedSimple"] = evt.value
    }
}

def powerRelayHandler(evt) {
    def roomId = getRoomIdFromDevice(evt.device.id, "power")
    if (!roomId) return
    def expected = state["z${roomId}ExpectedPower"]
    def prev = state["z${roomId}PreviousPower"]
    
    if (evt.value == expected) return
    if (isDetectionPaused(roomId)) return
    
    if (evt.value == prev && prev != expected) return 
    
    def gnSwitch = settings["z${roomId}GnSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        state["z${roomId}ExpectedPower"] = evt.value
    } else {
        setManualOverride(roomId, "Master Relay ${evt.value}")
        state["z${roomId}ExpectedPower"] = evt.value
    }
}

// ==============================================================================
// CORE LOGIC
// ==============================================================================

def getRoomOccupancy(roomId, timeoutMs) {
    def actSwitch = settings["z${roomId}ActivitySwitch"]
    if (actSwitch && actSwitch.currentValue("switch") == "on") return true

    def mDevs = settings["z${roomId}Motion"]
    if (!settings["enableOccupancy"] || !mDevs) return true
    
    for (mDev in mDevs) {
        if (mDev.currentValue("motion") == "active") return true
        
        def lastActive = state.zoneLastActive ? state.zoneLastActive[mDev.id] : null
        if (lastActive && (now() - lastActive.toLong()) <= timeoutMs) {
            return true
        }
    }
    
    return false
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
        def hasDevice = ((fanType.startsWith("speed") || fanType == "fixed") && settings["z${i}Fan"]) || (fanType == "switch" && settings["z${i}SimpleFan"])
        
        if (settings["enableZ${i}"] && hasDevice) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def gnSwitch = settings["z${i}GnSwitch"]
            
            // Absolute Isolation Check
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") continue
            
            def zTimeoutMs = (settings["z${i}OccupancyTimeout"] ?: 30) * 60000
            def isOccupied = getRoomOccupancy(i, zTimeoutMs)
            
            // TRACK OCCUPANCY CHANGES FOR WIGGLE RESET
            def prevOccMap = state.wasOccupied ?: [:]
            if (prevOccMap[i.toString()] != isOccupied) {
                updateWiggleReset(i)
                prevOccMap[i.toString()] = isOccupied
                state.wasOccupied = prevOccMap
            }
            
            // IF NOT OVERRIDDEN BY USER, PROCEED WITH AUTOMATION
            if (!isOverrideActive(i)) {
                
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
                            
                            if (fanType.startsWith("speed") || fanType == "fixed") {
                                def fDev = settings["z${i}Fan"]
                                def pDev = settings["z${i}Power"]
                                def targetSpeed = calculateSpeedLevel(controlTemp, setpoint.toBigDecimal(), fanType, i)
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
                if (fanType.startsWith("speed") || fanType == "fixed") {
                    def fDev = settings["z${i}Fan"]
                    def pDev = settings["z${i}Power"]
                    
                    if (pDev) {
                        def targetSpeed = state["z${i}Target"] ?: "off"
                        def pwrState = pDev.currentValue("switch")
                        def keepOn = shouldKeepRelayOn(i, isOccupied, isAway, isNight)
                        
                        if (targetSpeed == "off") {
                            if (keepOn && pwrState != "on") {
                                logAction("Smart Lighting Needed: Proactively powering ON relay for ${zName}.")
                                pauseOverrideDetection(i, 30) // Setup Blind Spot
                                setExpectedState(i, "power", "on")
                                pDev.on()
                                runIn(5, "refreshSwitch", [data: [room: i, type: "power"], overwrite: false])
                            } 
                            else if (!keepOn && pwrState != "off" && fDev.currentValue("speed") == "off") {
                                logAction("Smart Lighting conditions cleared. Sweeping power relay OFF for ${zName}.")
                                pauseOverrideDetection(i, 30) // Setup Blind Spot
                                setExpectedState(i, "power", "off")
                                pDev.off()
                                runIn(5, "refreshSwitch", [data: [room: i, type: "power"], overwrite: false])
                            }
                        }
                    }
                }
            } 
        }
    }
}

def refreshSwitch(data) {
    if (!data || !data.room || !data.type) return
    def rNum = data.room
    if (data.type == "power") {
        def pDev = settings["z${rNum}Power"]
        if (pDev && pDev.hasCommand("refresh")) pDev.refresh()
    } else if (data.type == "simple") {
        def sDev = settings["z${rNum}SimpleFan"]
        if (sDev && sDev.hasCommand("refresh")) sDev.refresh()
    }
}

def turnRoomOff(roomId, fanType, roomName, reason) {
    if (fanType.startsWith("speed") || fanType == "fixed") {
        def fDev = settings["z${roomId}Fan"]
        def pDev = settings["z${roomId}Power"]
        
        def alwaysOn = settings["z${roomId}RelayAlwaysOn"]
        def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
        def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
        
        // Skip fan commands if Away/Night and no 24/7 relay requirement, to save network resources
        if (pDev && !alwaysOn && (isAway || isNight)) {
            if (state["z${roomId}Target"] != "off") {
                logAction("Mode is ${isAway ? 'Away' : 'Good Night'}. Killing ${roomName} Master Relay directly (skipping fan speed commands to save hub resources).")
                state["z${roomId}Target"] = "off"
                updateWiggleReset(roomId)
                setExpectedState(roomId, "fan", "off")
            }
            
            if (pDev.currentValue("switch") != "off") {
                pauseOverrideDetection(roomId, 30) // Setup Blind Spot
                setExpectedState(roomId, "power", "off")
                pDev.off()
                runIn(5, "refreshSwitch", [data: [room: roomId, type: "power"], overwrite: false])
            }
        } else {
            setFanTarget(roomId, fDev, pDev, roomName, "off", reason)
        }
    } else {
        def sDev = settings["z${roomId}SimpleFan"]
        if (sDev && sDev.currentValue("switch") != "off") {
            logAction("Stopping ${roomName} simple fan relay. (${reason})")
            pauseOverrideDetection(roomId, 30) // Setup Blind Spot
            setExpectedState(roomId, "simpleFan", "off")
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
            pauseOverrideDetection(roomId, 30) // Setup Blind Spot
            setExpectedState(roomId, "simpleFan", "on")
            switchDevice.on()
            runIn(5, "refreshSwitch", [data: [room: roomId, type: "simple"], overwrite: false])
        }
    } else if (currentTemp <= (targetSetpoint - 0.5)) {
        if (switchDevice.currentValue("switch") != "off") {
            logAction("Stopping ${roomName} relay. (Deadband satisfied: ${currentTemp}° <= ${targetSetpoint - 0.5}°)")
            pauseOverrideDetection(roomId, 30) // Setup Blind Spot
            setExpectedState(roomId, "simpleFan", "off")
            switchDevice.off()
            runIn(5, "refreshSwitch", [data: [room: roomId, type: "simple"], overwrite: false])
        }
    }
}

// === MULTI-SPEED FAN LOGIC ===
def calculateSpeedLevel(currentTemp, targetSetpoint, fanType, roomId) {
    def delta = currentTemp - targetSetpoint
    if (delta <= 0) return "off"
    
    if (fanType == "fixed") {
        return settings["z${roomId}FixedSpeed"] ?: "high"
    }
    
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
    return "off" 
}

def setFanTarget(roomId, fDev, pDev, roomName, newTargetSpeed, reason) {
    def currentTarget = state["z${roomId}Target"] ?: "off"
    def rawFanType = settings["z${roomId}FanType"] ?: "speed3"
    def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
    
    // FIX: Stop processing if the fan is already exactly where it needs to be.
    def currentSpeed = fDev ? (fDev.currentValue("speed") ?: "off") : "off"
    if (currentTarget == newTargetSpeed && currentSpeed == newTargetSpeed) return

    if (currentTarget != newTargetSpeed) {
        if (fanType == "fixed") {
            def actionStr = newTargetSpeed == "off" ? "Stopping" : "Starting"
            logAction("${actionStr} ${roomName} directly to ${newTargetSpeed.toUpperCase()}. (${reason})")
        } else {
            def sMap = getSpeedLevels(fanType)
            def currInt = sMap[currentTarget] ?: 0
            def newInt = sMap[newTargetSpeed] ?: 0
            
            def actionStr = "Setting"
            if (newTargetSpeed == "off") actionStr = "Stopping"
            else if (currInt == 0) actionStr = "Starting"
            else if (newInt > currInt) actionStr = "Increasing"
            else actionStr = "Decreasing"
            
            logAction("${actionStr} ${roomName} to ${newTargetSpeed.toUpperCase()}. (${reason})")
        }
        
        state["z${roomId}Target"] = newTargetSpeed
        updateWiggleReset(roomId)
    }

    if (newTargetSpeed != "off" && pDev && pDev.currentValue("switch") != "on") {
        logAction("Powering ON ${roomName} relay for active cooling.")
        pauseOverrideDetection(roomId, 30) // Setup Blind Spot
        setExpectedState(roomId, "power", "on")
        pDev.on()
        runIn(5, "refreshSwitch", [data: [room: roomId, type: "power"], overwrite: false])
        runInMillis(1500, "stepFanTrigger", [data: [room: roomId], overwrite: false])
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
    
    def pDev = settings["z${roomId}Power"]
    def alwaysOn = settings["z${roomId}RelayAlwaysOn"]
    def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    
    // Abort RF commands if power is cut during Away/Night to save resources
    if (pDev && !alwaysOn && (isAway || isNight)) {
        if (pDev.currentValue("switch") == "off") return
    }
    
    def targetSpeed = state["z${roomId}Target"] ?: "off"
    def currentSpeed = fDev.currentValue("speed") ?: "off"
    
    def rawFanType = settings["z${roomId}FanType"] ?: "speed3"
    def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
    
    // Bypass stepping entirely for Fixed fans
    if (fanType == "fixed") {
        if (currentSpeed != targetSpeed) {
            logAction("Commanding ${settings["z${roomId}Name"]} directly to ${targetSpeed.toUpperCase()}.")
            pauseOverrideDetection(roomId, 60)
            setExpectedState(roomId, "fan", targetSpeed)
            fDev.setSpeed(targetSpeed)
            
            // Re-trigger to check if it reached target or needs power killed
            runIn(rfStepDelay ?: 3, "stepFanTrigger", [data: [room: roomId], overwrite: false])
        } else if (currentSpeed == "off" && targetSpeed == "off") {
            if (pDev && pDev.currentValue("switch") != "off") {
                // FIX: Check Smart Lighting requirements before scheduling a kill task
                def timeoutMs = (settings["z${roomId}OccupancyTimeout"] ?: 30) * 60000
                def isOccupied = getRoomOccupancy(roomId, timeoutMs)
                if (shouldKeepRelayOn(roomId, isOccupied, isAway, isNight)) return 

                def spinDelay = relaySpinDown ?: 15
                logAction("${settings["z${roomId}Name"]} blades are OFF. Scheduling power relay check in ${spinDelay} seconds.")
                runIn(spinDelay, "killPowerRelay", [data: [room: roomId], overwrite: true])
            }
        }
        return
    }
    
    def sMap = getSpeedLevels(fanType)
    def lMap = getLevelSpeeds(fanType)
    
    def targetInt = sMap[targetSpeed] != null ? sMap[targetSpeed] : 0
    def currentInt = sMap[currentSpeed] != null ? sMap[currentSpeed] : 0
    def delay = rfStepDelay ?: 3
    
    if (currentInt < targetInt) {
        def nextSpeed = lMap[currentInt + 1]
        logAction("Stepping ${settings["z${roomId}Name"]} UP to ${nextSpeed.toUpperCase()}...")
        pauseOverrideDetection(roomId, 60) // HUGE Blind Spot for sluggish cloud hubs
        setExpectedState(roomId, "fan", nextSpeed)
        fDev.setSpeed(nextSpeed)
        runIn(delay, "stepFanTrigger", [data: [room: roomId], overwrite: false])
    } 
    else if (currentInt > targetInt) {
        def nextSpeed = lMap[currentInt - 1]
        logAction("Stepping ${settings["z${roomId}Name"]} DOWN to ${nextSpeed.toUpperCase()}...")
        pauseOverrideDetection(roomId, 60) // HUGE Blind Spot for sluggish cloud hubs
        setExpectedState(roomId, "fan", nextSpeed)
        fDev.setSpeed(nextSpeed)
        runIn(delay, "stepFanTrigger", [data: [room: roomId], overwrite: false])
    } 
    else if (currentInt == 0 && targetInt == 0) {
        if (pDev && pDev.currentValue("switch") != "off") {
            // FIX: Check Smart Lighting requirements before scheduling a kill task
            def timeoutMs = (settings["z${roomId}OccupancyTimeout"] ?: 30) * 60000
            def isOccupied = getRoomOccupancy(roomId, timeoutMs)
            if (shouldKeepRelayOn(roomId, isOccupied, isAway, isNight)) return 

            def spinDelay = relaySpinDown ?: 15
            logAction("${settings["z${roomId}Name"]} blades are OFF. Scheduling power relay check in ${spinDelay} seconds.")
            runIn(spinDelay, "killPowerRelay", [data: [room: roomId], overwrite: true])
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
        pauseOverrideDetection(roomId, 30) // Setup Blind Spot
        setExpectedState(roomId, "power", "off")
        pDev.off()
        runIn(5, "refreshSwitch", [data: [room: roomId, type: "power"], overwrite: false])
    }
}

def doHourlyWiggle() {
    logAction("Executing Hourly RF Fan Wiggle to verify speeds...")
    
    for (int i = 1; i <= 8; i++) {
        def rawFanType = settings["z${i}FanType"] ?: "speed3"
        def fanType = (rawFanType == "speed") ? "speed3" : rawFanType
        
        // Skip Wiggle for Fixed Speed fans
        if (settings["enableZ${i}"] && fanType.startsWith("speed") && settings["z${i}Fan"]) {
            def gnSwitch = settings["z${i}GnSwitch"]
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") continue 
            
            def pDev = settings["z${i}Power"]
            if (pDev && pDev.currentValue("switch") == "off") continue // Skip if power is cut
            
            // --- NEW STABILITY CHECK ---
            def zTimeoutMs = (settings["z${i}OccupancyTimeout"] ?: 30) * 60000
            def isOccupied = getRoomOccupancy(i, zTimeoutMs)
            def resetTime = state.wiggleResetTime ? state.wiggleResetTime[i.toString()] : null

            if (isOccupied && resetTime) {
                def elapsedHrs = (now() - resetTime.toLong()) / 3600000.0
                if (elapsedHrs >= 2.0) {
                    logInfo("Skipping wiggle for ${settings["z${i}Name"]}. Occupancy and target speed have been stable for ${elapsedHrs.setScale(1, BigDecimal.ROUND_HALF_UP)} hours.")
                    continue
                }
            }
            // ---------------------------
            
            def sMap = getSpeedLevels(fanType)
            def lMap = getLevelSpeeds(fanType)
            
            def fDev = settings["z${i}Fan"]
            def current = fDev.currentValue("speed") ?: "off"
            def currentInt = sMap[current] != null ? sMap[current] : 0
            def targetSpeed = state["z${i}Target"] ?: "off"
            
            if (currentInt > 0) {
                def dropSpeed = lMap[currentInt - 1]
                logAction("Wiggle: Dropping ${settings["z${i}Name"]} to ${dropSpeed.toUpperCase()} temporarily.")
                pauseOverrideDetection(i, 60) 
                setExpectedState(i, "fan", dropSpeed)
                fDev.setSpeed(dropSpeed)
            } 
            else if (currentInt == 0 && targetSpeed == "off") {
                logAction("Wiggle: Bumping ${settings["z${i}Name"]} to LOW to force Bond Bridge reset.")
                pauseOverrideDetection(i, 60) 
                setExpectedState(i, "fan", "low")
                fDev.setSpeed("low")
            }
        }
    }
    
    runIn(10, "restoreWiggleSpeeds")
}

def restoreWiggleSpeeds() {
    logInfo("Wiggle delay complete. Restoring fan speeds...")
    evaluateFans()
    
    // Explicitly restore speeds for overridden zones since evaluateFans ignores them
    for (int i = 1; i <= 8; i++) {
        if (settings["enableZ${i}"] && isOverrideActive(i)) {
            stepFan(i) 
        }
    }
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
