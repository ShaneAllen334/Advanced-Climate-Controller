/**
 * Advanced Smart Blind Controller
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Smart Blind Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Predictive thermal engine with Financial ROI tracking, Virtual Aggregate Sensor, and Telemetry Dashboards.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            if (numRooms > 0) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room</th><th style='padding: 8px;'>Environment</th><th style='padding: 8px;'>Verified State</th><th style='padding: 8px;'>Target & Reason</th><th style='padding: 8px;'>Active Locks</th></tr>"
                
                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def dir = settings["direction_${i}"] ?: "Unset"
                    def blind = settings["blind_${i}"]
                    
                    def rNameDisplay = "<b>${rName}</b><br><span style='font-size: 11px; color: #555;'>Facing: ${dir}</span>"
                    
                    if (!blind) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${rNameDisplay}</td><td style='padding: 8px; color: #888;'>-</td><td style='padding: 8px; color: #888;'>Not Configured</td><td style='padding: 8px;'>-</td><td style='padding: 8px;'>-</td></tr>"
                        continue
                    }
                    
                    def tSensor = settings["tempSensor_${i}"]
                    def lSensor = settings["luxSensor_${i}"]
                    def rTemp = tSensor ? "${tSensor.currentValue('temperature')}°" : "--°"
                    def rLux = lSensor ? "${lSensor.currentValue('illuminance')} lx" : "-- lx"
                    def envDisplay = "<b>${rTemp}</b><br><span style='font-size: 11px; color: #555;'>${rLux}</span>"
                    
                    def vState = state.verifiedState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def stateColor = (vState == "OPEN") ? "green" : (vState == "CLOSED" ? "blue" : "black")
                 
                    def tState = state.targetState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def tReason = state.targetReason?."${i}" ?: "Awaiting Initial Sync..."
                 
                    def targetDisplay = "<b>${tState}</b><br><span style='font-size: 11px; color: #555;'>${tReason}</span>"
                    
                    def locks = []
                    if (state.manualHold?."${i}") locks << "<span style='color: red; font-weight: bold;'>Manual Hold</span>"
                    if (state.windLock?."${i}") locks << "<span style='color: orange; font-weight: bold;'>Storm Shield</span>"
                    if (state.fortressLocked?."${i}") locks << "<span style='color: purple; font-weight: bold;'>Fortress Lock</span>"
                    if (settings["goodNightSwitch_${i}"]?.currentValue("switch") == "on") locks << "<span style='color: darkblue; font-weight: bold;'>Nap Lock</span>"
                    
                    def lockStr = locks ? locks.join("<br>") : "<span style='color: green;'>Clear</span>"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${rNameDisplay}</td><td style='padding: 8px;'>${envDisplay}</td><td style='padding: 8px; color: ${stateColor}; font-weight: bold;'>${vState}</td><td style='padding: 8px;'>${targetDisplay}</td><td style='padding: 8px;'>${lockStr}</td></tr>"
                }
                statusText += "</table>"
  
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                def outTemp = outdoorTempSensor ? "${outdoorTempSensor.currentValue('temperature')}°" : "--°"
                def outLux = outdoorLuxSensor ? "${outdoorLuxSensor.currentValue('illuminance')} lx" : "-- lx"
                def avgTemp = getAverageIndoorTemp()
                def hvac = mainThermostat ? mainThermostat.currentValue("thermostatOperatingState")?.capitalize() : "--"
                 
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>System:</b> ${globalStatus}</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>Outdoor:</b> ${outTemp} | ${outLux}</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>House Avg:</b> ${avgTemp}°</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>HVAC:</b> ${hvac}</div>"
                statusText += "</div>"
                
                def lifetimeSavings = "\$" + (state.lifetimeSavings ?: 0.00).setScale(2, BigDecimal.ROUND_HALF_UP)
                def todaySavings = "\$" + (state.todaySavings ?: 0.00).setScale(2, BigDecimal.ROUND_HALF_UP)
                
                statusText += "<div style='margin-top: 5px; padding: 10px; background: #e9f5ff; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #add8e6;'>"
                statusText += "<div><b>Estimated ROI:</b> <span style='color: #008800;'>Today: ${todaySavings}</span> | <span style='color: #0055aa;'>Total: ${lifetimeSavings}</span></div>"
                statusText += "</div>"

                paragraph statusText
            } else {
                paragraph "<i>Configure rooms below to see live system status.</i>"
            }
        }
    
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Global Settings & Modes") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false,
                description: "The Global Pause. ON = Application Runs. OFF = Application Paused."
                
            input "numRooms", "number", title: "Number of Rooms to Configure (1-12)", required: true, defaultValue: 1, range: "1..12", submitOnChange: true
            input "retryTimeoutMinutes", "number", title: "Max Sync Retry Duration (Minutes)", defaultValue: 15, required: true, description: "Maximum time to keep retrying commands before giving up."
            
            input "aggregateSensor", "capability.contactSensor", title: "Virtual Contact Sensor (All Blinds Status)", required: false, 
                description: "Select a Virtual Contact Sensor. Turns 'closed' if ALL blinds are closed. Turns 'open' if ANY blind is open."
            
            input "masterBlind", "capability.windowShade", title: "Master Bond Device (For 'Open All' / 'Close All')", required: false
            
            input "activeModes", "mode", title: "Master Active Modes (App only runs in these)", multiple: true, required: false
          
            input "openOnModes", "mode", title: "Modes that trigger Global Open", multiple: true, required: false
            input "closeOnModes", "mode", title: "Modes that trigger Global Close", multiple: true, required: false
            
            input "btnReleaseAllHolds", "button", title: "Release All Manual Holds Now (And Sync House)"
            input "btnForceSync", "button", title: "Force System Re-evaluation & Sync Now"
      
            input "autoReleaseHoldModes", "mode", title: "Modes that Auto-Release Manual Holds", multiple: true, required: false
            input "vacationModes", "mode", title: "Vacation Modes (Triggers random open/close presence)", multiple: true, required: false
        }
        
        section("Time & Solar Settings") {
            input "useSunriseSunset", "bool", title: "Enable Sunrise/Sunset automations?", defaultValue: false, submitOnChange: true
            
            if (useSunriseSunset) {
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunriseModes", "mode", title: "Modes allowed for Auto-Sunrise Open", multiple: true, required: false
                
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
                input "maxCloseTime", "time", title: "Maximum Evening Close Time", required: false
                input "sunsetModes", "mode", title: "Modes allowed for Auto-Sunset/Time Close", multiple: true, required: false
                input "sunsetDeadband", "number", title: "Sunset Deadband / Motor Saver (Minutes)", defaultValue: 30
                input "darkArrivalLockout", "bool", title: "Enable Dark Arrival Lockout?", defaultValue: true
                input "circadianWake", "bool", title: "Enable Circadian Gradual Wakeup?", defaultValue: false
            }
        }
        
        section("Exterior Weather & Master Solar Override") {
            input "windSensor", "capability.sensor", title: "Weather Station / Wind Sensor", required: false
            input "windThreshold", "number", title: "Storm Shield Wind Threshold (mph)", defaultValue: 15
                
            input "outdoorLuxSensor", "capability.illuminanceMeasurement", title: "Master Outdoor Lux Sensor", required: false
            input "highSolarRadiationThreshold", "number", title: "High Solar Radiation Threshold (Lux)", defaultValue: 10000
            input "luxHysteresis", "number", title: "Solar Radiation Hysteresis (Deadband Lux)", defaultValue: 500, description: "Lux must drop this far below the threshold before blinds reopen."
                
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor", required: false
            input "outdoorHighTempThreshold", "number", title: "Outdoor High Temp Lockout (°)", defaultValue: 92
        }
        
        section("Environmental Controls & Predictive ROI") {
            input "mainThermostat", "capability.thermostat", title: "Main Thermostat (Syncs blinds with AC/Heat states)", required: false
            
            input "elecRate", "decimal", title: "Electricity Rate (per kWh)", defaultValue: 0.14, required: true
            input "hvacEfficiency", "decimal", title: "Est. kWh Saved per Hour of Defense", defaultValue: 0.25, required: true,
                description: "Average kWh reduction of your HVAC when blinds are blocking sun. Standard is 0.20 to 0.40."
            
            input "environmentalDebounce", "number", title: "Environmental Anti-Yo-Yo Hold Time (Minutes)", defaultValue: 15, 
                description: "Forces blinds to hold position to prevent constant up/down movements on partly cloudy days."
            input "tempHysteresis", "decimal", title: "Temperature Hysteresis (Deadband °)", defaultValue: 1.0, description: "Temp must change this much past the threshold to revert states."
                
            input "activeCoolingDefense", "bool", title: "Active Cooling Defense (Close sun-facing blinds when AC cools)?", defaultValue: true, submitOnChange: true
                
            input "enableFortressMode", "bool", title: "Enable Unoccupied Fortress Mode?", defaultValue: false, submitOnChange: true
               
            if (enableFortressMode) {
                input "fortressAutoReopen", "bool", title: "Auto-Reopen on Motion?", defaultValue: false
            }
            
            input "summerEnergyMode", "bool", title: "Summer Mode (Close shades to block heat)?", defaultValue: false, submitOnChange: true
            
            if (summerEnergyMode) {
                input "summerTempThreshold", "number", title: "Summer Indoor Temp Threshold (°)", defaultValue: 75
                input "summerOutdoorTempThreshold", "number", title: "Summer Outdoor Temp Trigger (Preemptive °)", defaultValue: 82, required: false
                input "summerAllowedModes", "mode", title: "Modes allowed for Summer Mode", multiple: true, required: false
            }
            
            input "winterHeatingMode", "bool", title: "Winter Mode (Open shades to harvest free solar heat)?", defaultValue: false, submitOnChange: true
            if (winterHeatingMode) {
                input "winterTempThreshold", "number", title: "Winter Indoor Temp Threshold (Open if below this °)", defaultValue: 68
                input "winterOutdoorTempThreshold", "number", title: "Winter Outdoor Temp Trigger (Preemptive °)", defaultValue: 45, required: false
                input "winterMaxOutdoorTemp", "number", title: "Winter Max Outdoor Temp Lockout (°)", defaultValue: 75, required: false, description: "If the outdoor temp is above this, Winter Mode is disabled (prevents heating up the house in summer)."
                input "winterAllowedModes", "mode", title: "Modes allowed for Winter Mode", multiple: true, required: false
            }
        }
        
        if (numRooms > 0 && numRooms <= 12) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def roomNum = i
                def rName = settings["roomName_${i}"] ?: "Room ${i}"
                section("${rName}") {
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${rName}")
                }
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def currentName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
    
    dynamicPage(name: "roomPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Room Identification") {
            input "roomName_${rNum}", "text", title: "Custom Room Name", required: false, defaultValue: "Room ${rNum}", submitOnChange: true
        }
        
        section("Control Devices") {
            input "blind_${rNum}", "capability.windowShade", title: "Blind / Shade Device (Bond)", required: false
            input "blindSensor_${rNum}", "capability.contactSensor", title: "Blind State Sensor (Manual override detection)", required: false
            input "direction_${rNum}", "enum", title: "Window Facing Direction", options: ["North", "South", "East", "West"], required: false
        }
        
        section("Physical Buttons / Remotes") {
            input "roomButton_${rNum}", "capability.pushableButton", title: "Room Button Controller", required: false
            input "buttonNumber_${rNum}", "number", title: "Button Number", defaultValue: 1, required: false
            input "buttonModes_${rNum}", "mode", title: "Allowed Modes for Button", multiple: true, required: false
 
            input "buttonStartTime_${rNum}", "time", title: "Button Active Start Time", required: false
            input "buttonEndTime_${rNum}", "time", title: "Button Active End Time", required: false
        }
        
        section("Sensors & Triggers") {
            input "tempSensor_${rNum}", "capability.temperatureMeasurement", title: "Indoor Temperature Sensor", required: false
            input "luxSensor_${rNum}", "capability.illuminanceMeasurement", title: "Indoor Lux (Light) Sensor", required: false
            input "humiditySensor_${rNum}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (For Privacy Triggers)", required: false
            input "contactSensor_${rNum}", "capability.contactSensor", title: "Window Open/Close Sensor", required: false
            
            if (enableFortressMode) {
                input "motionSensor_${rNum}", "capability.motionSensor", title: "Motion Sensor (For Unoccupied Fortress)", required: false
                input "unoccupiedTimeout_${rNum}", "number", title: "Unoccupied Timeout (Minutes)", defaultValue: 60
            }
        }
        
        section("Overrides (Hard-Locks)") {
            input "goodNightSwitch_${rNum}", "capability.switch", title: "Nap Time / Good Night Hard-Lock Switch", required: false
            input "releaseHoldSwitch_${rNum}", "capability.switch", title: "Switch to Manually Release Control Hold", required: false
            input "privacyHumidityThreshold_${rNum}", "number", title: "Privacy Humidity Threshold (%)", defaultValue: 65
        }
    }
}

def installed() {
    log.info "Smart Blind Controller Installed."
    initialize()
}

def updated() {
    log.info "Smart Blind Controller Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.targetState = state.targetState ?: [:]
    state.targetReason = state.targetReason ?: [:]
    state.verifiedState = state.verifiedState ?: [:]
    state.manualHold = state.manualHold ?: [:]
    state.lastAutoMoveTime = state.lastAutoMoveTime ?: [:] 
    state.windLock = state.windLock ?: [:]
    state.fortressLocked = state.fortressLocked ?: [:]
    state.historyLog = state.historyLog ?: []
    state.roiMinutes = state.roiMinutes ?: 0
    state.commandStartTime = state.commandStartTime ?: [:]
    
    if (useSunriseSunset) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro) 
        if (maxCloseTime) schedule(maxCloseTime, "executeMaxCloseTime")
    }
    
    unschedule("calculateROIStep")
    calculateROIStep()
    schedule("0 0 0 * * ?", "midnightReset") 
    runIn(10, "bootSync", [overwrite: true]) 
    
    subscribe(location, "mode", modeHandler)
    if (mainThermostat) subscribe(mainThermostat, "thermostatOperatingState", hvacHandler)
    
    if (windSensor) subscribe(windSensor, "windSpeed", weatherHandler)
    if (outdoorLuxSensor) subscribe(outdoorLuxSensor, "illuminance", weatherHandler)
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", weatherHandler)
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["tempSensor_${i}"]) subscribe(settings["tempSensor_${i}"], "temperature", tempHandler)
        if (settings["luxSensor_${i}"]) subscribe(settings["luxSensor_${i}"], "illuminance", luxHandler)
        if (settings["humiditySensor_${i}"]) subscribe(settings["humiditySensor_${i}"], "relativeHumidity", humidityHandler)
        if (settings["motionSensor_${i}"]) subscribe(settings["motionSensor_${i}"], "motion", motionHandler)
        if (settings["contactSensor_${i}"]) subscribe(settings["contactSensor_${i}"], "contact", windowContactHandler)
        
        if (settings["roomButton_${i}"]) {
            subscribe(settings["roomButton_${i}"], "pushed", buttonPushedHandler)
            subscribe(settings["roomButton_${i}"], "held", buttonHeldHandler)
        }
        
        if (settings["goodNightSwitch_${i}"]) {
            subscribe(settings["goodNightSwitch_${i}"], "switch.on", hardLockOnHandler)
            subscribe(settings["goodNightSwitch_${i}"], "switch.off", hardLockOffHandler)
        }
        
        if (settings["blindSensor_${i}"]) subscribe(settings["blindSensor_${i}"], "contact", blindSensorHandler)
        if (settings["releaseHoldSwitch_${i}"]) subscribe(settings["releaseHoldSwitch_${i}"], "switch.on", releaseHoldHandler)
    }
}

def updateAggregateSensor() {
    if (!aggregateSensor) return
    
    def anyOpen = false
    def allClosed = true
    def configuredCount = 0
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blind_${i}"]) {
            configuredCount++
            def vState = state.verifiedState?."${i}" ?: state.targetState?."${i}"
            
            if (vState == "open") {
                anyOpen = true
                allClosed = false
            } else if (vState != "closed") {
                allClosed = false
            }
        }
    }
    
    if (configuredCount == 0) return
    
    def currentState = aggregateSensor.currentValue("contact")
    
    if (allClosed && currentState != "closed") {
        addToHistory("SYSTEM: All blinds are verified closed. Updating Virtual Aggregate Sensor.")
        if (aggregateSensor.hasCommand("close")) aggregateSensor.close()
    } else if (anyOpen && currentState != "open") {
        addToHistory("SYSTEM: One or more blinds are open. Updating Virtual Aggregate Sensor.")
        if (aggregateSensor.hasCommand("open")) aggregateSensor.open()
    }
}

def calculateROIStep() {
    if (isSystemPaused()) return
    
    def rate = settings["elecRate"] != null ? settings["elecRate"].toBigDecimal() : 0.14
    def factor = settings["hvacEfficiency"] != null ? settings["hvacEfficiency"].toBigDecimal() : 0.25
    def totalDefenseRooms = 0
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def reason = state.targetReason?."${i}" ?: ""
        if (reason.contains("Summer Mode") || reason.contains("Winter Mode") || reason.contains("Fortress") || reason.contains("High Solar Radiation") || reason.contains("HVAC Active Cooling")) {
            totalDefenseRooms++
        }
    }
    
    if (totalDefenseRooms > 0) {
        def earned = (totalDefenseRooms * (factor / 12)) * rate
        state.todaySavings = (state.todaySavings ?: 0.0) + earned
        state.lifetimeSavings = (state.lifetimeSavings ?: 0.0) + earned
    }
    
    runIn(300, "calculateROIStep") // Run every 5 minutes
}

def midnightReset() {
    state.todaySavings = 0.0
    state.manualHold = [:]
    if (!isSystemPaused()) runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        log.info "Dashboard data manually refreshed by user."
    } else if (btn == "btnReleaseAllHolds") {
        state.manualHold = [:]
        state.fortressLocked = [:]
        addToHistory("GLOBAL: 'Release All Holds' button pressed. Wiping locks and auto-syncing house.")
        if (!isSystemPaused()) runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    } else if (btn == "btnForceSync") {
        addToHistory("GLOBAL: 'Force Sync' button pressed. Re-evaluating and syncing all rooms.")
        if (!isSystemPaused()) runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
    }
}

def executeSyncStaggered(data) {
    syncSingleRoom(data.roomNum, data.ignoreDebounce ?: false)
}

def bootSync() {
    addToHistory("SYSTEM REBOOT: Hub restarted. Auto-syncing the entire house to recover missed events.")
    if (!isSystemPaused()) {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            def bSensor = settings["blindSensor_${i}"]
            if (bSensor) state.verifiedState["${i}"] = bSensor.currentValue("contact")
            else state.verifiedState["${i}"] = state.targetState["${i}"] ?: "unknown"
        }
        runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
        runIn(10, "updateAggregateSensor", [overwrite: true])
    }
}

def windowContactHandler(evt) {
    def deviceId = evt.device.id
    def isClosed = evt.value == "closed"
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["contactSensor_${i}"]?.id == deviceId) {
            if (isClosed) {
                def rName = getRoomName(i)
                addToHistory("${rName}: Physical window was closed. Re-evaluating room state to recover any blocked actions.")
                runIn(5, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
            }
        }
    }
}

def getAverageIndoorTemp() {
    def totalTemp = 0.0
    def count = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def tSensor = settings["tempSensor_${i}"]
        if (tSensor) {
            totalTemp += (tSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0)
            count++
        }
    }
    return count > 0 ? (totalTemp / count).setScale(1, BigDecimal.ROUND_HALF_UP) : 70.0
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def tz = location.timeZone ?: TimeZone.getDefault()
    def timestamp = new Date().format("MM/dd HH:mm:ss", tz)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

def getRoomName(rNum) {
    return settings["roomName_${rNum}"] ?: "Room ${rNum}"
}

def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset != null ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset != null ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def executeMaxCloseTime() {
    if (isSystemPaused()) return
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunsetModes && !sunsetModes.contains(location.mode)) return
    
    def roomsNeedClosing = false
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blind_${i}"] && state.targetState["${i}"] != "close" && !state.manualHold["${i}"]) {
            roomsNeedClosing = true
            break
        }
    }

    if (roomsNeedClosing) {
        addToHistory("GLOBAL: Maximum Evening Close Time reached. Closing eligible blinds.")
        operateAllShades("close", false, "Max Evening Close Time")
    }
}

def weatherHandler(evt) {
    if (isSystemPaused()) return
    def eventName = evt.name
    
    if (eventName == "windSpeed") {
        def currentWind = evt.value?.toBigDecimal() ?: 0.0
        def threshold = windThreshold != null ? windThreshold.toBigDecimal() : 15.0
        
        if (currentWind >= threshold) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def contact = settings["contactSensor_${i}"]
                if (contact && contact.currentValue("contact") == "open" && !state.windLock["${i}"]) {
                    state.windLock["${i}"] = true
                    def rName = getRoomName(i)
                    addToHistory("STORM SHIELD ACTIVE: High wind (${currentWind} mph). Forced ${rName} blind open.")
                    singleBlindAction(i, "open", true, "Storm Shield (Wind: ${currentWind}mph >= ${threshold}mph)", true) 
                }
            }
        } else {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                if (state.windLock["${i}"]) {
                   state.windLock["${i}"] = false
                   addToHistory("${getRoomName(i)}: Storm Shield lock lifted. Restoring room state.")
                   runIn(i * 2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
                }
            }
        }
    }
    
    if (eventName == "illuminance" || eventName == "temperature") {
        runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    }
}

def hvacHandler(evt) {
    if (isSystemPaused()) return
    runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

def luxHandler(evt) {
    if (isSystemPaused()) return
    runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

def tempHandler(evt) {
    if (isSystemPaused()) return
    runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

def motionHandler(evt) {
    if (isSystemPaused()) return
    if (!enableFortressMode) return
  
    def deviceId = evt.device.id
    def isActive = evt.value == "active"
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def mSensor = settings["motionSensor_${i}"]
        if (mSensor && mSensor.id == deviceId) {
            def rName = getRoomName(i)
            if (isActive) {
                unschedule("executeFortressClose_${i}")
                if (fortressAutoReopen && state.fortressLocked["${i}"]) {
                    addToHistory("${rName}: Motion detected. Unlocking Unoccupied Fortress mode.")
                    state.fortressLocked["${i}"] = false
                    runIn(2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: false], overwrite: false])
                }
            } else {
                def timeout = settings["unoccupiedTimeout_${i}"] != null ? settings["unoccupiedTimeout_${i}"].toInteger() : 60
                runIn(timeout * 60, "executeFortressClose", [data: [roomNum: i], overwrite: false])
            }
        }
    }
}

def executeFortressClose(data) {
    if (isSystemPaused()) return
    def rNum = data.roomNum
    addToHistory("${getRoomName(rNum)}: Unoccupied Fortress triggered. Room empty for timeout period. Closing blind.")
    state.fortressLocked["${rNum}"] = true
    singleBlindAction(rNum, "close", false, "Unoccupied Fortress", false)
}

def isButtonAllowed(roomNum) {
    def allowedModes = settings["buttonModes_${roomNum}"]
    if (allowedModes && !allowedModes.contains(location.mode)) {
        addToHistory("${getRoomName(roomNum)}: Physical Button ignored. Hub is not in an allowed mode.")
        return false
    }
    
    def startTime = settings["buttonStartTime_${roomNum}"]
    def endTime = settings["buttonEndTime_${roomNum}"]
    if (startTime && endTime) {
        def between = timeOfDayIsBetween(startTime, endTime, new Date(), location.timeZone)
        if (!between) {
            addToHistory("${getRoomName(roomNum)}: Physical Button ignored. Outside of allowed active time window.")
            return false
        }
    }
    return true
}

def buttonPushedHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            addToHistory("${getRoomName(i)}: Physical Button PUSHED. Opening blind and engaging Manual Hold.")
            state.manualHold["${i}"] = true
            state.fortressLocked["${i}"] = false
            singleBlindAction(i, "open", true, "Physical Button Hold", true) 
        }
    }
}

def buttonHeldHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            addToHistory("${getRoomName(i)}: Physical Button HELD. Closing blind and engaging Manual Hold.")
            state.manualHold["${i}"] = true
            state.fortressLocked["${i}"] = false
            singleBlindAction(i, "close", true, "Physical Button Hold", true) 
        }
    }
}

def modeHandler(evt) {
    def currentMode = evt.value
    
    if (autoReleaseHoldModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.fortressLocked = [:] 
    }
    
    if (isSystemPaused()) return
    
    if (vacationModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Vacation Mode active. Random presence routines engaged.")
        scheduleRandomPresence()
        return
    } else {
        unschedule("triggerRandomBlind")
    }
   
    if (activeModes && !activeModes.contains(currentMode)) return
    
    if (openOnModes?.contains(currentMode)) {
        if (darkArrivalLockout && isDarkOut()) {
            addToHistory("GLOBAL: Mode changed to ${currentMode}, but Dark Arrival Lockout blocked OPEN command.")
        } else if (isExteriorUnsafeToOpen()) {
            // Engine handles block logic silently
        } else {
            addToHistory("GLOBAL: Mode changed to ${currentMode}. Global OPEN routine triggered.")
            operateAllShades("open", false, "Global Open Mode")
        }
    }
    else if (closeOnModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Global CLOSE routine triggered.")
        operateAllShades("close", false, "Global Close Mode")
    }
}

def executeSunrise() {
    if (isSystemPaused()) return
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunriseModes && !sunriseModes.contains(location.mode)) return
    if (isExteriorUnsafeToOpen()) return 
    
    addToHistory("GLOBAL: Sunrise routine triggered.")
    
    if (circadianWake) {
        state.circadianStep = 10
        runCircadianStep()
    } else {
        operateAllShades("open", false, "Sunrise Routine")
    }
}

def runCircadianStep() {
    if (isSystemPaused()) return
    def step = state.circadianStep ?: 10
    if (step > 100) return
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def blind = settings["blind_${i}"]
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        if (blind && (!gnSwitch || gnSwitch.currentValue("switch") != "on") && !state.manualHold["${i}"] && !state.windLock["${i}"]) {
            state.targetState["${i}"] = "open"
            state.targetReason["${i}"] = "Circadian Wakeup Cycle"
            if (blind.hasCommand("setPosition")) blind.setPosition(step)
            else if (step == 100) blind.open()
        }
    }
    state.circadianStep = step + 10
    runIn(300, "runCircadianStep", [overwrite: true])
}

def executeSunset() {
    if (isSystemPaused()) return
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunsetModes && !sunsetModes.contains(location.mode)) return
    
    addToHistory("GLOBAL: Sunset routine triggered. Closing all eligible blinds.")
    operateAllShades("close", false, "Sunset Routine")
}

def humidityHandler(evt) {
    if (isSystemPaused()) return
    def currentHum = evt.value?.toInteger() ?: 0
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def hSensor = settings["humiditySensor_${i}"]
        def threshold = settings["privacyHumidityThreshold_${i}"] != null ? settings["privacyHumidityThreshold_${i}"].toInteger() : 65
        
        if (hSensor && hSensor.id == deviceId) {
            if (currentHum >= threshold && state.targetState["${i}"] != "close") {
                addToHistory("${getRoomName(i)}: Privacy Override. Humidity spiked to ${currentHum}%. Closing blind.")
                singleBlindAction(i, "close", false, "Privacy (High Hum: ${currentHum}% >= ${threshold}%)", false)
            } else if (currentHum < threshold && state.targetReason["${i}"]?.startsWith("Privacy (High Hum")) {
                addToHistory("${getRoomName(i)}: Humidity cleared. Restoring room to normal environment state.")
                runIn(2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
            }
        }
    }
}

// --- DYNAMIC PREDICTIVE THERMAL ENGINE & ORCHESTRATOR ---
def orchestrateHouseSync(data = null) {
    def ignoreDebounce = data?.ignoreDebounce ?: false
    if (isSystemPaused()) return
    
    def houseTargets = [:]
    def allNeedToClose = true
    def eligibleCount = 0
    def allReasons = []
    def actionRequiredCount = 0 // Tracks if any blind actually needs to physically move
    
    // 1. Calculate targets for all rooms without executing
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def roomTarget = determineRoomTarget(i)
        houseTargets[i] = roomTarget
        
        if (settings["blind_${i}"]) {
            if (roomTarget.locked) {
                allNeedToClose = false // A locked room blocks pure Master commands
            } else {
                eligibleCount++
                if (roomTarget.action != "close") {
                    allNeedToClose = false
                }
                if (roomTarget.reason && !allReasons.contains(roomTarget.reason)) {
                    allReasons << roomTarget.reason
                }
                
                // VERIFICATION: Check if the blind is already in the correct physical state
                def currentState = settings["blindSensor_${i}"]?.currentValue("contact") ?: state.verifiedState["${i}"]
                def expectedState = (roomTarget.action == "close") ? "closed" : roomTarget.action
                
                if (currentState != expectedState || state.targetState["${i}"] != roomTarget.action) {
                    actionRequiredCount++ // A blind is out of sync and requires action
                } else if (state.targetReason["${i}"] != roomTarget.reason) {
                    // State matches, but the environment reason changed (e.g., Nighttime to Summer Mode). Silently update reason.
                    state.targetReason["${i}"] = roomTarget.reason
                }
            }
        }
    }
    
    // If no blinds actually need to change state, exit silently to prevent log spam
    if (actionRequiredCount == 0) {
        return
    }
    
    // 2. Decide Execution Strategy
    if (eligibleCount > 0 && allNeedToClose && masterBlind) {
        def combinedReason = allReasons.join(" / ")
        addToHistory("ORCHESTRATOR: Entire house evaluated to CLOSE. Intercepting and routing to Master Blind.")
        operateAllShades("close", false, combinedReason)
    } else {
        def delayMultiplier = 0
        houseTargets.each { rNum, target ->
            if (!target.locked && target.action) {
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: target.action, reason: target.reason, ignoreDebounce: ignoreDebounce], overwrite: false])
                delayMultiplier++
            }
        }
    }
}

def determineRoomTarget(roomNum) {
    def target = [action: null, reason: null, locked: false]
    def blind = settings["blind_${roomNum}"]
    if (!blind) {
        target.locked = true
        return target
    }
    
    if (state.manualHold["${roomNum}"] || state.windLock["${roomNum}"] || state.fortressLocked["${roomNum}"]) {
        target.locked = true
        return target
    }
    
    def gnSwitch = settings["goodNightSwitch_${roomNum}"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        target.locked = true
        return target
    }

    def currentMode = location.mode
    def isNight = isDarkOut() || isPastMaxCloseTime()

    // 1. Time / Mode Hard Overrides
    if (isNight && (!sunsetModes || sunsetModes.contains(currentMode))) {
        target.action = "close"
        target.reason = "Nighttime Secure"
        return target
    }
    if (closeOnModes?.contains(currentMode)) {
        target.action = "close"
        target.reason = "Global Close Mode"
        return target
    }

    // 2. Evaluate Base Daytime State
    def shouldBeOpen = false
    if (openOnModes?.contains(currentMode) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    } else if (useSunriseSunset && !isNight && (!sunriseModes || sunriseModes.contains(currentMode)) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    }

    // 3. Evaluate Environment (Overrides Base State)
    def envTarget = evaluateEnvironmentTarget(roomNum, isNight, currentMode)
    if (envTarget.action) {
        target.action = envTarget.action
        target.reason = envTarget.reason
        return target
    }

    // 4. Fallback to Base State
    if (shouldBeOpen) {
        target.action = "open"
        target.reason = "Normal Daytime Condition"
        return target
    }

    // 5. Maintain Current State if no rules match
    target.action = state.targetState["${roomNum}"] ?: "close" 
    target.reason = state.targetReason["${roomNum}"] ?: "Maintaining State"
   
    return target
}

def evaluateEnvironmentTarget(roomNum, isNight, currentHubMode) {
    def target = [action: null, reason: null]
    
    def dir = settings["direction_${roomNum}"]
    def dirName = dir ? dir : "these"
    
    def tempSensor = settings["tempSensor_${roomNum}"]
    def currentTemp = tempSensor ? (tempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def hvacState = mainThermostat ? (mainThermostat.currentValue("thermostatOperatingState") ?: "idle") : "idle"
    
    // --- HYSTERESIS VARIABLES ---
    def luxHysteresisOffset = settings["luxHysteresis"] != null ? settings["luxHysteresis"].toInteger() : 500
    def tempHysteresisOffset = settings["tempHysteresis"] != null ? settings["tempHysteresis"].toBigDecimal() : 1.0
    def currentRoomReason = state.targetReason["${roomNum}"] ?: ""
    
    // --- LUX EVALUATION WITH HYSTERESIS ---
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def highRadiationLimit = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    def isHighRadiation = false
    if (outdoorLuxSensor) {
        if (currentRoomReason?.startsWith("High Solar")) {
            // Already blocking sun: require lux to drop BELOW deadband to lift the lock
            isHighRadiation = (outLux >= (highRadiationLimit - luxHysteresisOffset))
        } else {
            // Not currently blocking: trigger standard limit
            isHighRadiation = (outLux >= highRadiationLimit)
        }
    }
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def hour = new Date().format("HH", tz).toInteger()
    def isMorning = (hour < 12)
    def isSunFacing = false
    if (dir == "South") isSunFacing = true
    if (dir == "East" && isMorning) isSunFacing = true
    if (dir == "West" && !isMorning) isSunFacing = true

    // --- WINTER MODE EVALUATION WITH HYSTERESIS ---
    def maxWinterOut = settings["winterMaxOutdoorTemp"] != null ? settings["winterMaxOutdoorTemp"].toBigDecimal() : 75.0
    def isActuallyWinter = outdoorTempSensor ? (outTemp <= maxWinterOut) : true
    def winterThresh = settings["winterTempThreshold"] != null ? settings["winterTempThreshold"].toBigDecimal() : 68.0
    def indoorWinterTrigger = currentTemp <= winterThresh
    def winterOutThresh = settings["winterOutdoorTempThreshold"] != null ? settings["winterOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorWinterTrigger = false
    if (winterOutThresh != null) {
        if (currentRoomReason?.startsWith("Winter Mode")) {
            // Already heating: temp must rise ABOVE deadband to stop
            outdoorWinterTrigger = (outTemp <= (winterOutThresh + tempHysteresisOffset))
        } else {
            outdoorWinterTrigger = (outTemp <= winterOutThresh)
        }
    }

    if (winterHeatingMode && !isNight && isActuallyWinter) {
        if (!winterAllowedModes || winterAllowedModes.contains(currentHubMode)) {
            if (indoorWinterTrigger || outdoorWinterTrigger) {
                if (isSunFacing && (hvacState == "heating" || hvacState == "idle")) {
                    target.action = "open"
                    def wReason = indoorWinterTrigger ? "In: ${currentTemp}° <= ${winterThresh}°" : "Out: ${outTemp}° <= ${winterOutThresh}°"
                    target.reason = "Winter Mode: Opening ${dirName} facing blinds to harvest active solar heat [${wReason}]"
                    return target
                }
            }
        }
    }

    if (!isNight && isHighRadiation && isSunFacing) {
        target.action = "close"
        target.reason = "High Solar Radiation: Closing ${dirName} blinds because the sun is currently on this side [Lux: ${outLux} >= ${highRadiationLimit}]"
        return target
    }

    def coolingDefense = settings["activeCoolingDefense"] != null ? settings["activeCoolingDefense"] : true
    if (coolingDefense && hvacState == "cooling" && !isNight && isSunFacing) {
        target.action = "close"
        target.reason = "HVAC Active Cooling: Closing ${dirName} blinds to block direct sun [State: ${hvacState.capitalize()}]"
        return target
    }

    // --- SUMMER MODE EVALUATION WITH HYSTERESIS ---
    def summerThresh = settings["summerTempThreshold"] != null ? settings["summerTempThreshold"].toBigDecimal() : 75.0
    def indoorSummerTrigger = currentTemp >= summerThresh
    def summerOutThresh = settings["summerOutdoorTempThreshold"] != null ? settings["summerOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorSummerTrigger = false
    if (summerOutThresh != null) {
        if (currentRoomReason?.startsWith("Summer Mode")) {
            // Already cooling: temp must drop BELOW deadband to stop
            outdoorSummerTrigger = (outTemp >= (summerOutThresh - tempHysteresisOffset))
        } else {
            outdoorSummerTrigger = (outTemp >= summerOutThresh)
        }
    }

    if (summerEnergyMode && !isNight) {
        if (!summerAllowedModes || summerAllowedModes.contains(currentHubMode)) {
            if (indoorSummerTrigger || outdoorSummerTrigger) {
                def avgTemp = getAverageIndoorTemp()
                def houseIsHot = avgTemp >= summerThresh
                def sReason = indoorSummerTrigger ? "In: ${currentTemp}° >= ${summerThresh}°" : "Out: ${outTemp}° >= ${summerOutThresh}°"

                if (hvacState != "heating") {
                    if (houseIsHot) {
                        target.action = "close"
                        target.reason = "Summer Mode: House is hot, closing ${dirName} blinds to defend against heat [AvgIn: ${avgTemp}° >= ${summerThresh}°]"
                        return target
                    } else if (isSunFacing) {
                        target.action = "close"
                        target.reason = "Summer Mode: Closing ${dirName} blinds because direct sun is heating this side [${sReason}]"
                        return target
                    }
                }
            }
        }
    } 
    
    return target
}

def syncSingleRoom(roomNum, ignoreDebounce = false) {
    def target = determineRoomTarget(roomNum)
    if (!target.locked && target.action) {
        singleBlindAction(roomNum, target.action, false, target.reason, ignoreDebounce)
    }
}

// --- HARD-LOCK OVERRIDE HANDLERS ---
def hardLockOnHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: NAP TIME / HARD-LOCK ENGAGED. Room forced closed.")
            singleBlindAction(i, "close", true, "Nap Time/Hard Lock", true) 
        }
    }
}

def hardLockOffHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: Hard-Lock released. Syncing room state.")
            syncSingleRoom(i, true) 
        }
    }
}

// --- UTILITY & VERIFICATION ---
def isDarkOut() {
    if (!useSunriseSunset) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return false
    def now = new Date()
    return (now >= sunInfo.sunset || now <= sunInfo.sunrise)
}

def isPastMaxCloseTime() {
    if (!maxCloseTime) return false
    def tz = location.timeZone ?: TimeZone.getDefault()
    def maxTime = timeToday(maxCloseTime, tz)
    def now = new Date()
    return (now >= maxTime)
}

def isExteriorUnsafeToOpen() {
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 0.0) : 0.0
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def limitTemp = settings["outdoorHighTempThreshold"] != null ? settings["outdoorHighTempThreshold"].toBigDecimal() : 92.0
    def limitLux = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    if (outdoorTempSensor && (outTemp >= limitTemp)) {
        return true
    }
    if (outdoorLuxSensor && (outLux >= limitLux)) {
        return true
    }
    return false
}

def isWithinSunsetDeadband() {
    if (!useSunriseSunset || !sunsetDeadband) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset) return false
    def now = new Date()
    def deadbandStart = new Date(sunInfo.sunset.time - (sunsetDeadband.toInteger() * 60000))
    return (now >= deadbandStart && now < sunInfo.sunset)
}

def singleBlindAction(roomNum, action, bypassLock = false, reason = "Automated Sync", ignoreDebounce = false) {
    def rName = getRoomName(roomNum)
    def blind = settings["blind_${roomNum}"]
    
    if (!blind) {
        log.warn "${rName}: ABORTED. No Blind Device is selected in the app settings!"
        return
    }
    
    if (state.windLock["${roomNum}"] && action == "close") {
        log.warn "${rName}: ABORTED CLOSE. Storm Shield wind lock is active."
        return
    }
    
    if (!bypassLock && settings["goodNightSwitch_${roomNum}"]?.currentValue("switch") == "on") {
        return
    }
    
    if (action == "open" && !bypassLock && isWithinSunsetDeadband()) {
        return
    }
    
    if (action == "close" && settings["contactSensor_${roomNum}"]?.currentValue("contact") == "open") {
        if (state.targetState["${roomNum}"] != action) addToHistory("${rName}: Aborted CLOSE command. Physical window is OPEN.")
        return
    }

    def currentState = settings["blindSensor_${roomNum}"]?.currentValue("contact") ?: state.verifiedState["${roomNum}"]
    def mappedActionState = (action == "close") ? "closed" : action
    
    if (currentState == mappedActionState && state.targetState["${roomNum}"] == action) {
        if (state.targetReason["${roomNum}"] != reason) {
            state.targetReason["${roomNum}"] = reason
            runIn(2, "updateAggregateSensor", [overwrite: true])
        }
        return 
    }

    state.targetReason["${roomNum}"] = reason

    // --- ANTI-YO-YO DEBOUNCE LOGIC ---
    if (!bypassLock && !ignoreDebounce) {
        def now = new Date().time
        def lastMove = state.lastAutoMoveTime["${roomNum}"] ?: 0
        def debounceMillis = (environmentalDebounce != null ? environmentalDebounce.toInteger() : 15) * 60000
        
        if ((now - lastMove) < debounceMillis) {
            def timeLeft = ((debounceMillis - (now - lastMove)) / 1000).toInteger()
            
            if (state.targetState["${roomNum}"] != action) {
                addToHistory("${rName}: ${action.toUpperCase()} delayed. Anti-Yo-Yo cooldown active (${(timeLeft/60).toInteger()}m left).")
            }
            
            runIn(timeLeft + 2, "executeSyncStaggered", [data: [roomNum: roomNum, ignoreDebounce: false], overwrite: true])
            return
        }
    }
    
    if (state.targetState["${roomNum}"] != action) {
        state.commandStartTime["${roomNum}"] = new Date().time
    }
    
    state.targetState["${roomNum}"] = action
    state.lastAutoMoveTime["${roomNum}"] = new Date().time
    
    addToHistory("${rName}: Executing ${action.toUpperCase()} command. Reason: ${reason}")
    
    if (action == "open") blind.open() else blind.close()
    
    state.retryCount = 0
    runIn(30, "verifyAndRetry", [overwrite: true]) 
}

def blindSensorHandler(evt) {
    def deviceId = evt.device.id
    runIn(8, "evaluateSensorEvent", [data: [deviceId: deviceId], overwrite: false])
}

def evaluateSensorEvent(data) {
    def deviceId = data.deviceId
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def sensor = settings["blindSensor_${i}"]
        if (sensor && sensor.id == deviceId) {
            def actualState = sensor.currentValue("contact")
            def verified = state.verifiedState["${i}"]
            def target = state.targetState["${i}"]
            def rName = getRoomName(i)
            def expectedState = (target == "close") ? "closed" : target

            def now = new Date().time
            def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
            
            if ((now - lastMove) < 90000) {
                if (actualState == expectedState) {
                    state.verifiedState["${i}"] = actualState
                    runIn(2, "updateAggregateSensor", [overwrite: true])
                }
                return 
            }
            
            if (actualState == expectedState) {
                if (state.verifiedState["${i}"] != actualState) {
                    state.verifiedState["${i}"] = actualState
                    runIn(2, "updateAggregateSensor", [overwrite: true])
                }
                return 
            }
            
            if (verified == "closed" && actualState == "open") {
                addToHistory("${rName}: Blind was manually opened. Activating Manual Hold.")
                state.manualHold["${i}"] = true
                state.fortressLocked["${i}"] = false 
                state.targetState["${i}"] = "open"
                state.targetReason["${i}"] = "Manual Physical Override"
                state.verifiedState["${i}"] = actualState 
                runIn(2, "updateAggregateSensor", [overwrite: true])
            } else if (verified == "open" && actualState == "closed") {
                addToHistory("${rName}: Blind was manually closed. Activating Manual Hold.")
                state.manualHold["${i}"] = true
                state.fortressLocked["${i}"] = false
                state.targetState["${i}"] = "close"
                state.targetReason["${i}"] = "Manual Physical Override"
                state.verifiedState["${i}"] = actualState 
                runIn(2, "updateAggregateSensor", [overwrite: true])
            }
        }
    }
}

def releaseHoldHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["releaseHoldSwitch_${i}"]?.id == deviceId) {
            state.manualHold["${i}"] = false
            addToHistory("${getRoomName(i)}: Manual Hold released by user switch. Syncing room state.")
            syncSingleRoom(i, true)
        }
    }
}

def scheduleRandomPresence() {
    def randomMinutes = new Random().nextInt(45) + 15
    runIn(randomMinutes * 60, "triggerRandomBlind", [overwrite: false])
}

def triggerRandomBlind() {
    if (isSystemPaused()) return
    if (!vacationModes?.contains(location.mode)) return
    
    def rNum = new Random().nextInt(numRooms as Integer) + 1
    def action = new Random().nextBoolean() ? "open" : "close"
    
    if (settings["goodNightSwitch_${rNum}"]?.currentValue("switch") == "on" || state.windLock["${rNum}"]) {
        scheduleRandomPresence() 
        return
    }
    
    addToHistory("VACATION PRESENCE: Randomly executing ${action.toUpperCase()} on ${getRoomName(rNum)}.")
    singleBlindAction(rNum, action, false, "Vacation Mode", true)
    scheduleRandomPresence()
}

// --- LUXURY WAVE EXECUTION ---
def executeStaggeredCommand(data) {
    singleBlindAction(data.roomNum, data.action, false, data.reason ?: "Automated Sync", data.ignoreDebounce ?: false)
}

def operateAllShades(action, force = false, reason = "Global Command") {
    if (action == "open" && !force && isWithinSunsetDeadband()) {
        addToHistory("GLOBAL: Aborted global OPEN command (within sunset deadband).")
        return
    }

    def allEligible = true
    def roomsToCommand = []
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def blind = settings["blind_${i}"]
        def windowContact = settings["contactSensor_${i}"]
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        if (!blind) continue
        if (!force && state.manualHold["${i}"]) { allEligible = false; continue }
        if (state.windLock["${i}"]) { allEligible = false; continue } 
        if (gnSwitch && gnSwitch.currentValue("switch") == "on") { allEligible = false; continue }
        if (action == "close" && windowContact && windowContact.currentValue("contact") == "open") { allEligible = false; continue }
        
        if (state.targetState["${i}"] != action) {
            state.commandStartTime["${i}"] = new Date().time
        }
        
        state.targetState["${i}"] = action
        state.targetReason["${i}"] = reason
        roomsToCommand << i
    }
    
    if (roomsToCommand.size() == 0) return
    
    if (allEligible && masterBlind) {
        addToHistory("GLOBAL: Master Blind device triggered. Executing ${action.toUpperCase()} on all rooms.")
        if (action == "open") masterBlind.open() else masterBlind.close()
        
        state.masterRetryCount = 0
        runIn(60, "verifyMasterAndRetry", [data: [action: action, reason: reason], overwrite: true])
    } else {
        def delayMultiplier = 0
        
        roomsToCommand.each { rNum ->
            def delaySec = delayMultiplier * 2
            runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: action, reason: reason, ignoreDebounce: true], overwrite: false])
            delayMultiplier++
        }
        state.retryCount = 0
        runIn((roomsToCommand.size() * 2) + 60, "verifyAndRetry", [overwrite: true])
    }
}

// --- MASTER VERIFY & RETRY LOOP ---
def verifyMasterAndRetry(data) {
    if (isSystemPaused()) return
    
    def action = data.action
    def reason = data.reason
    def needsMasterRetry = false
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        if (!target || state.manualHold["${i}"]) continue
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                needsMasterRetry = true
                break 
            }
        }
    }
    
    if (needsMasterRetry) {
        state.masterRetryCount = (state.masterRetryCount ?: 0) + 1
        
        if (state.masterRetryCount <= 2) {
            addToHistory("GLOBAL: Some blinds failed to sync. Retrying Master ${action.toUpperCase()} command (${state.masterRetryCount + 1}/3).")
            if (action == "open") masterBlind.open() else masterBlind.close()
            runIn(60, "verifyMasterAndRetry", [data: data, overwrite: true])
        } else {
            addToHistory("GLOBAL: Master Blind failed after 3 attempts. Falling back to individual shade sync.")
            
            def delayMultiplier = 0
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def target = state.targetState["${i}"]
                if (!target || state.manualHold["${i}"]) continue
                
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: i, action: target, reason: reason + " (Master Fallback)", ignoreDebounce: true], overwrite: false])
                delayMultiplier++
            }
            
            runIn((delayMultiplier * 2) + 60, "verifyAndRetry", [overwrite: true])
        }
    } else {
        addToHistory("GLOBAL: Master Blind sync verified successfully.")
        runIn(2, "updateAggregateSensor", [overwrite: true])
    }
}

// --- VERIFY & PERSISTENT RETRY LOOP ---
def verifyAndRetry() {
    if (isSystemPaused()) return
    def needsRetry = false
    
    def timeoutMinutes = settings["retryTimeoutMinutes"] != null ? settings["retryTimeoutMinutes"].toInteger() : 15
    def timeoutMillis = timeoutMinutes * 60000
    def now = new Date().time
    
    def delayMultiplier = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        if (!target || state.manualHold["${i}"]) continue
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                
                // 1. Check if we've exceeded the global timeout setting
                def startTime = state.commandStartTime["${i}"] ?: now
        
                if ((now - startTime) >= timeoutMillis) {
                    if (state.targetReason["${i}"] != "TIMEOUT FAILED") {
                        def rName = getRoomName(i)
                        addToHistory("TIMEOUT ERROR: ${rName} failed to reach ${target.toUpperCase()} after ${timeoutMinutes} minutes. Abandoning retries.")
                        state.targetReason["${i}"] = "TIMEOUT FAILED"
                    }
                    continue // Skip retrying this specific blind
                }
  
                needsRetry = true 
                
                // 2. Only send the command if it's been at least 60s since the last RF blast
                def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
      
                if ((now - lastMove) >= 60000) {
                    def delaySec = delayMultiplier * 3 
                    def tReason = state.targetReason["${i}"] ?: "Persistent Retry Sync"
           
                    // THIS IS WHERE THE BI-DIRECTIONAL WIGGLE HAPPENS
                    if (target == "open") {
                        runIn(delaySec, "executeWiggleOpen", [data: [roomNum: i, reason: tReason], overwrite: false])
                    } else if (target == "close") {
                        runIn(delaySec, "executeWiggleClose", [data: [roomNum: i, reason: tReason], overwrite: false])
                    }
                    delayMultiplier++
                }
            } else {
                state.verifiedState["${i}"] = currentState
            }
        } else {
            // DASHBOARD FIX: If no physical sensor is installed, assume the RF command worked to keep the UI clean
            state.verifiedState["${i}"] = target
        }
    }
    
    if (needsRetry) {
        runIn(60, "verifyAndRetry", [overwrite: true]) 
    } else {
        runIn(2, "updateAggregateSensor", [overwrite: true])
    }
}

// --- WIGGLE RECOVERY MANEUVERS ---
def executeWiggleOpen(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing CLOSE, then re-issuing OPEN. Reason: ${data.reason}")
    
    if (blind.hasCommand("close")) blind.close()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleOpen", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleOpen(data) {
    def blind = settings["blind_${data.roomNum}"]
    if (blind && blind.hasCommand("open")) blind.open()
}

def executeWiggleClose(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing OPEN, then re-issuing CLOSE. Reason: ${data.reason}")
    
    if (blind.hasCommand("open")) blind.open()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleClose", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleClose(data) {
    def blind = settings["blind_${data.roomNum}"]
    if (blind && blind.hasCommand("close")) blind.close()
}
