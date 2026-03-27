/**
 * Advanced Overcast Detector
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Overcast Detector",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced environmental lux monitor with Proportional Dimming, Universal Darkness enforcement, Astro Countdowns, Cloud History, and Solar Baseline Graphing.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Overcast Detector", install: true, uninstall: true) {
        
        // --- EXPOSED SECTIONS ---
        
        section("Live System Dashboard") {
            input name: "refreshBtn", type: "button", title: "🔄 Refresh Data"
            
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Environment</th><th style='padding: 8px;'>System Evaluation</th><th style='padding: 8px;'>Outputs</th></tr>"
            
            def currentLux = luxSensors ? "${getAggregateLux()} lx" : "-- lx"
            
            // Environment Display (Showing Dynamic Calculations)
            def envDisplay = "<div style='font-size: 14px; margin-bottom: 5px;'><b>${currentLux}</b></div>"
            
            // Smart Threshold Display Logic
            def oLimit = getSmartOvercastThreshold()
            def oReason = useSmartThresholds ? "Smart Scaled" : "Static"
            envDisplay += "<div style='font-size: 11px; color: #555;'><b>Drop Target:</b> ${oLimit} lx <i>(${oReason})</i></div>"
            
            if (useDynamicClear) {
                def cLimit = getDynamicClearThreshold()
                envDisplay += "<div style='font-size: 11px; color: #2e8b57;'><b>Clear Target:</b> ${cLimit} lx (Auto-Curve)</div>"
            } else {
                def cLimit = getSmartClearThreshold()
                def cReason = useSmartThresholds ? "Smart Scaled" : "Static"
                envDisplay += "<div style='font-size: 11px; color: #2e8b57;'><b>Clear Target:</b> ${cLimit} lx <i>(${cReason})</i></div>"
            }

            // SMART LEARNING DISPLAY
            def epcsb = getExpectedPeakLux()
            def epcsbReason = ""
            if (useSmartLearning) {
                def learnedDays = state.peakLuxHistory ? state.peakLuxHistory.size() : 0
                if (learnedDays >= 30) {
                    epcsbReason = "Learning Active (30-day Avg)"
                } else {
                    epcsbReason = "Collecting Data (${learnedDays}/30)"
                }
            } else {
                epcsbReason = "Manual User Setting"
            }
            def dailyMax = state.dailyMaxLux ?: 0
            
            envDisplay += "<div style='margin-top: 5px; font-size: 11px; color: #555;'><b>Daily Max Lux:</b> ${dailyMax} lx</div>"
            envDisplay += "<div style='font-size: 11px; color: #b8860b;'><b>EPCSB Target:</b> ${epcsb} lx<br><i>(${epcsbReason})</i></div>"
            
            def sState = state.currentCondition ?: "Awaiting Sync..."
            
            def sColor = "orange"
            if (sState == "Overcast") sColor = "blue"
            if (sState == "Clear" || sState == "Assumed Clear (Boot)") sColor = "green"
            if (sState == "Nighttime") sColor = "purple"
            
            def pendingMsg = ""
            if (state.pendingOvercast) pendingMsg = "<br><span style='font-size: 11px; color: #555;'>Verifying Weather Event...</span>"
            if (state.pendingClear) pendingMsg = "<br><span style='font-size: 11px; color: #555;'>Verifying Clear...</span>"
            if (state.isNight) pendingMsg = "<br><span style='font-size: 11px; color: purple;'>Nighttime Hard-Lock Active</span>"

            def vSwitch = targetSwitch ? targetSwitch.currentValue("switch")?.toUpperCase() : "NOT SET"
            def switchColor = (vSwitch == "ON") ? "green" : "black"
            
            def vDim = targetDimmer ? (targetDimmer.currentValue("switch") == "on" ? "${targetDimmer.currentValue('level')}%" : "OFF") : "NOT SET"
            def dimColor = (targetDimmer && targetDimmer.currentValue("switch") == "on") ? "blue" : "black"
            
            def outputsDisplay = "<b>Switch:</b> <span style='color: ${switchColor};'>${vSwitch}</span><br><b>Dimmer:</b> <span style='color: ${dimColor};'>${vDim}</span>"
            
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${envDisplay}</td><td style='padding: 8px; color: ${sColor}; font-weight: bold;'>${sState}${pendingMsg}</td><td style='padding: 8px;'>${outputsDisplay}</td></tr>"
            statusText += "</table>"
            
            // SMART ROOM TARGETS DISPLAY
            if (numRooms && numRooms > 0) {
                statusText += "<div style='margin-top: 15px; font-weight: bold; font-size: 14px;'>Smart Room Darkness Targets</div>"
                statusText += "<table style='width:100%; border-collapse: collapse; font-size: 12px; font-family: sans-serif; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; text-align: left;'><th style='padding: 6px;'>Room</th><th style='padding: 6px;'>Daily Max</th><th style='padding: 6px;'>Learned Setpoint</th><th style='padding: 6px;'>Target Variable</th></tr>"
                
                for (int i = 1; i <= numRooms; i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def rData = state.roomData ? state.roomData["${i}"] : null
                    def dMax = rData?.dailyMax ?: 0
                    def setpt = rData?.currentSetpoint ?: (settings["roomBaseLux_${i}"] ?: 0)
                    def vName = settings["roomVar_${i}"] ?: "Not Configured"
                    
                    statusText += "<tr style='border-bottom: 1px solid #eee;'>"
                    statusText += "<td style='padding: 6px;'><b>${rName}</b></td>"
                    statusText += "<td style='padding: 6px;'>${dMax} lx</td>"
                    statusText += "<td style='padding: 6px; color: #b8860b; font-weight: bold;'>${setpt} lx</td>"
                    statusText += "<td style='padding: 6px; color: #555;'>${vName}</td>"
                    statusText += "</tr>"
                }
                statusText += "</table>"
            }
            
            // System Status Evaluation
            def sysStatus = "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            if (isSystemPaused()) sysStatus = "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>"
            else if (!isModeAllowed()) sysStatus = "<span style='color: orange; font-weight: bold;'>PAUSED (Restricted Mode)</span>"
            
            // Astro Countdown Calculation
            def astroMsg = "Awaiting Astro Data..."
            if (useAstro) {
                def sunInfo = getSunriseAndSunset()
                if (sunInfo && sunInfo.sunset && sunInfo.sunrise) {
                    def now = new Date()
                    def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
                    def actualSunset = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
                    
                    if (now.before(actualSunset) && now.after(sunInfo.sunrise)) {
                        def diffMillis = actualSunset.time - now.time
                        def h = (diffMillis / 3600000).toInteger()
                        def m = ((diffMillis % 3600000) / 60000).toInteger()
                        astroMsg = "<b>Sunset Hard-Lock in:</b> <span style='color: #d2691e;'>${h}h ${m}m</span>"
                    } else {
                        astroMsg = "<b>Astro Status:</b> <span style='color: purple;'>Currently Nighttime</span>"
                    }
                }
            } else {
                astroMsg = "<b>Astro Status:</b> Disabled"
            }
            
            statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
            statusText += "<div><b>System:</b> ${sysStatus}</div>"
            statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'>${astroMsg}</div>"
            statusText += "</div>"

            paragraph statusText
        }
        
        section("Weather Event & Cloud History") {
            if (state.cloudHistory && state.cloudHistory.size() > 0) {
                def tableHtml = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; border: 1px solid #ccc;'>"
                tableHtml += "<tr style='background-color: #e0e0e0; text-align: left;'><th style='padding: 6px; border-bottom: 1px solid #ccc;'>Event Start</th><th style='padding: 6px; border-bottom: 1px solid #ccc;'>Duration</th><th style='padding: 6px; border-bottom: 1px solid #ccc;'>Lux Drop</th><th style='padding: 6px; border-bottom: 1px solid #ccc;'>Lowest Point</th></tr>"
                
                state.cloudHistory.each { event ->
                    tableHtml += "<tr style='border-bottom: 1px solid #eee;'>"
                    tableHtml += "<td style='padding: 6px;'>${event.time}</td>"
                    tableHtml += "<td style='padding: 6px;'><b>${event.duration}</b></td>"
                    tableHtml += "<td style='padding: 6px; color: #d2691e;'>-${event.drop}</td>"
                    tableHtml += "<td style='padding: 6px; color: #555;'>${event.minLux}</td>"
                    tableHtml += "</tr>"
                }
                tableHtml += "</table>"
                
                if (state.activeCloudEvent) {
                    tableHtml += "<div style='margin-top: 8px; font-size: 12px; color: blue; font-weight: bold;'>☁️ Potential Clouding in progress...</div>"
                }
                
                paragraph tableHtml
            } else {
                paragraph "<i>No passing clouds or sudden drops recorded yet.</i>"
            }
        }
        
        section("24-Hour Lux Trend vs. Expected Solar Baseline") {
            if (state.luxHistory && state.luxHistory.size() > 2) {
                def chartUrl = generateChartUrl()
                paragraph "<div style='text-align:center;'><img src='${chartUrl}' width='100%' style='max-width:600px; border: 1px solid #ccc; border-radius: 5px;' /></div>"
            } else {
                paragraph "<i>Collecting data... Graph will appear once enough data points are gathered.</i>"
            }
        }
        
        // --- HIDDEN/COLLAPSIBLE SECTIONS ---
        
        section("Graph Calibration (Solar Baseline)", hideable: true, hidden: true) {
            input "useSmartLearning", "bool", title: "Enable Smart Learning Mode", defaultValue: true, submitOnChange: true,
                description: "Logs the daily max lux for 30 days to automatically set your Expected Peak Clear-Sky Brightness. Automatically rejects bad weather days from the dataset."
                
            input "peakClearLux", "number", title: "Expected Peak Clear-Sky Brightness (Lux)", defaultValue: 10000,
                description: "Manual fallback value. Set this to whatever your sensor typically reads at Solar Noon on a perfectly clear day. This scales the theoretical sun curve on your graph."
        }
        
        section("Smart Room Darkness Detection", hideable: true, hidden: true) {
            paragraph "Configure indoor rooms to dynamically track natural light. The app will calculate a seasonal 'Darkness Threshold' for each room and output it to a Hub Variable for your lighting automations."
            input "numRooms", "number", title: "Number of Smart Rooms to Configure (0-12)", defaultValue: 0, submitOnChange: true, range: "0..12"
            
            if (numRooms && numRooms > 0) {
                for (int i = 1; i <= numRooms; i++) {
                    def rmNum = i
                    paragraph "<hr><b>Room ${rmNum} Configuration</b>"
                    input "roomName_${rmNum}", "string", title: "Room Name"
                    input "roomLux_${rmNum}", "capability.illuminanceMeasurement", title: "Indoor Lux Sensor"
                    input "roomShades_${rmNum}", "capability.contactSensor", title: "Shade Contact Sensor(s) (Closed = Ignored)", multiple: true
                    input "roomLights_${rmNum}", "capability.switch", title: "Room Lights (ON = Ignored)", multiple: true
                    
                    input "roomPeakLux_${rmNum}", "number", title: "Expected Peak Brightness (Lux)", 
                        description: "What does this room's sensor typically read at peak daylight on a clear day?"
                    input "roomBaseLux_${rmNum}", "number", title: "Base Darkness Threshold (Lux)", 
                        description: "The brightness level at which you consider this room 'dark' (The app will scale this ratio automatically)."
                    
                    input "roomVar_${rmNum}", "string", title: "Target Hub Variable Name (Number)", 
                        description: "Exact string name of the Hub Variable. The app will write the daily calculated setpoint here."
                }
            }
        }
        
        section("Application History (Last 20 Events)", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Sensor & Control Targets", hideable: true, hidden: true) {
            input "luxSensors", "capability.illuminanceMeasurement", title: "Outdoor Master Lux Sensor(s)", multiple: true,
                description: "Select multiple to auto-filter outliers and calculate a master average."
            
            input "sensorInterval", "number", title: "Sensor Update Interval (Minutes)", defaultValue: 15,
                description: "How often your sensor reports data. The app dynamically scales its math windows based on this limitation so it doesn't miss sudden drops."
            
            paragraph "<b>Control Targets:</b> Select one or both. The Virtual Switch handles binary logic (ON/OFF). The Virtual Dimmer scales brightness based on storm severity."
            input "targetSwitch", "capability.switch", title: "Virtual Switch (ON = Overcast/Dark)"
            input "targetDimmer", "capability.switchLevel", title: "Virtual Dimmer (Proportional Brightness)"
            
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch"
            input "activeModes", "mode", title: "Active Modes (App only runs in these)", multiple: true
        }
        
        section("Proportional Dimming Setup", hideable: true, hidden: true) {
            paragraph "Maps the virtual dimmer level using a logarithmic curve for natural eye perception."
            input "heavyStormLux", "number", title: "Heavy Storm Limit (Lux)", defaultValue: 500,
                description: "If lux drops to this level, the dimmer hits Max Brightness."
            input "maxDimLevel", "number", title: "Max Brightness Level (%)", defaultValue: 100, range: "1..100"
            input "minDimLevel", "number", title: "Min Brightness Level (%)", defaultValue: 20, range: "1..100",
                description: "The starting brightness when it just barely crosses the Overcast threshold."
            input "nightDimLevel", "number", title: "Nighttime Brightness Level (%)", defaultValue: 100, range: "1..100"
        }
        
        section("Hysteresis & Thresholds (The Deadband)", hideable: true, hidden: true) {
            input "useSmartThresholds", "bool", title: "Enable Smart Threshold Scaling?", defaultValue: true, submitOnChange: true,
                description: "Automatically scales your Overcast and Clear Sky limits proportionally as the Expected Peak Clear-Sky Brightness changes with the seasons."
                
            input "overcastThreshold", "number", title: "Base Overcast Drop Threshold (Lux)", defaultValue: 2000,
                description: "If lux drops below this, start the Overcast timer. (Acts as the baseline ratio if Smart Thresholds are enabled)."
                
            input "clearThreshold", "number", title: "Base Clear Sky Recovery Threshold (Lux)", defaultValue: 4000,
                description: "If lux rises above this, start the Clear Sky timer. (Acts as the baseline ratio if Smart Thresholds are enabled)."
                
            input "debounceTime", "number", title: "Anti-Yo-Yo Debounce Time (Minutes)", defaultValue: 10,
                description: "How long the sky must stay below/above the threshold before flipping the virtual outputs."
                
            input "useDynamicClear", "bool", title: "Enable Automatic Time-of-Year & Time-of-Day Adjustments?", defaultValue: true, submitOnChange: true,
                description: "If enabled, the Clear Sky Recovery Threshold dynamically curves based on solar position and season to prevent evening/winter yo-yoing."
        }
        
        section("Universal Darkness (Nighttime Logic)", hideable: true, hidden: true) {
            input "useAstro", "bool", title: "Apply Nighttime Logic?", defaultValue: true, submitOnChange: true
            
            if (useAstro) {
                input "nightAction", "enum", title: "When the sun sets, force the virtual outputs:", options: ["Turn OFF (Clear/Night)", "Turn ON (Dark/Overcast)", "Do Nothing (Leave as is)"], defaultValue: "Turn ON (Dark/Overcast)",
                    description: "Select 'Turn ON' to ensure your motion lighting automations function properly all night."
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
            }
        }
    }
}

def installed() {
    log.info "Advanced Overcast Detector Installed."
    initialize()
}

def updated() {
    log.info "Advanced Overcast Detector Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.luxHistory = state.luxHistory ?: []
    state.cloudHistory = state.cloudHistory ?: []
    state.peakLuxHistory = state.peakLuxHistory ?: [] 
    state.dailyMaxLux = state.dailyMaxLux ?: 0
    state.activeCloudEvent = null
    state.currentCondition = "Evaluating..."
    state.pendingOvercast = false
    state.pendingClear = false
    state.isNight = false
    state.lastLuxCheckTime = now()
    state.lastLuxValue = null
    state.dipReason = null
    
    // Reset peak trackers on boot
    state.recentPeakLux = null
    state.recentPeakTime = null
    
    // Initialize Smart Room Data Structure
    if (!state.roomData) state.roomData = [:]
    def configuredRooms = numRooms ?: 0
    for (int i = 1; i <= configuredRooms; i++) {
        if (!state.roomData["${i}"]) {
            state.roomData["${i}"] = [dailyMax: 0, peakHistory: [], currentSetpoint: settings["roomBaseLux_${i}"] ?: 0]
        }
        def rLux = settings["roomLux_${i}"]
        if (rLux) subscribe(rLux, "illuminance", roomLuxHandler)
    }
    
    if (luxSensors) subscribe(luxSensors, "illuminance", luxHandler)
    subscribe(location, "mode", modeHandler)
   
    if (useAstro) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro)
        checkInitialAstroState()
    } else {
        state.isNight = false
    }
    
    runEvery15Minutes(logGraphData)
    forceImmediateEvaluation()
}

// --- BUTTON HANDLER ---
def appButtonHandler(btn) {
    if (btn == "refreshBtn") {
        log.info "Manual Data Refresh Requested."
    }
}

// --- UTILITY: SMART LEARNING HELPER ---
def getExpectedPeakLux() {
    if (useSmartLearning && state.peakLuxHistory && state.peakLuxHistory.size() >= 30) {
        return (state.peakLuxHistory.sum() / state.peakLuxHistory.size()).toInteger()
    }
    return peakClearLux ?: 10000
}

// --- UTILITY: SMART THRESHOLDS ---
def getSmartOvercastThreshold() {
    def baseOver = overcastThreshold ?: 2000
    if (!useSmartThresholds) return baseOver
    def basePeak = peakClearLux ?: 10000
    def currentPeak = getExpectedPeakLux()
    def ratio = baseOver / basePeak
    return (currentPeak * ratio).toInteger()
}

def getSmartClearThreshold() {
    def baseClear = clearThreshold ?: 4000
    if (!useSmartThresholds) return baseClear
    def basePeak = peakClearLux ?: 10000
    def currentPeak = getExpectedPeakLux()
    def ratio = baseClear / basePeak
    return (currentPeak * ratio).toInteger()
}

// --- UTILITY: CLOUD EVENT LOGGER ---
def closeActiveCloudEvent() {
    if (!state.activeCloudEvent) return
    
    def endTime = now()
    def durationSecs = ((endTime - state.activeCloudEvent.startTime) / 1000).toInteger() 
    def durationStr = ""
    
    if (durationSecs < 60) {
        durationStr = "${durationSecs} sec"
    } else {
        def mins = (durationSecs / 60).toInteger()
        def secs = durationSecs % 60
        durationStr = "${mins}m ${secs}s"
    }
    
    def maxDrop = state.activeCloudEvent.startLux - state.activeCloudEvent.minLux
    def eventTime = new Date(state.activeCloudEvent.startTime).format("MM/dd HH:mm", location.timeZone)
    
    def newEvent = [
        time: eventTime,
        duration: durationStr,
        drop: "${maxDrop} lx",
        minLux: "${state.activeCloudEvent.minLux} lx"
    ]
   
    if (!state.cloudHistory) state.cloudHistory = []
    state.cloudHistory.add(0, newEvent)
    if (state.cloudHistory.size() > 15) state.cloudHistory = state.cloudHistory.take(15)
    
    state.activeCloudEvent = null
}

// --- UTILITY: HISTORY LOGGER ---
def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

// --- SMART ROOM LUX EVALUATION ---
def roomLuxHandler(evt) {
    // Only learn the room's natural baseline when the sun is shining
    if (state.currentCondition == "Overcast" || state.isNight) return 
    
    def devId = evt.device.id
    def lux = evt.value.toInteger()
    def configuredRooms = numRooms ?: 0
    
    for (int i = 1; i <= configuredRooms; i++) {
        def rLux = settings["roomLux_${i}"]
        if (rLux && rLux.id == devId) {
            
            // Check for exclusions: Shades drawn or Lights on
            def shades = settings["roomShades_${i}"]
            def lights = settings["roomLights_${i}"]
            
            def shadesClosed = shades ? shades.any { it.currentValue("contact") == "closed" } : false
            def lightsOn = lights ? lights.any { it.currentValue("switch") == "on" } : false
            
            if (!shadesClosed && !lightsOn) {
                def rData = state.roomData["${i}"]
                if (rData) {
                    if (lux > (rData.dailyMax ?: 0)) {
                        rData.dailyMax = lux
                    }
                }
            }
            break // Found the sensor, no need to loop further
        }
    }
}

// --- SENSOR AGGREGATION & SOLAR CALCS ---
def getAggregateLux() {
    if (!luxSensors) return 0
    def values = luxSensors.collect { it.currentValue("illuminance")?.toInteger() ?: 0 }
    if (values.size() == 0) return 0
    if (values.size() == 1) return values[0]
    if (values.size() > 2) {
        values.sort()
        values = values[1..-2] // Drop highest and lowest to filter outliers
    }
    return (values.sum() / values.size()).toInteger()
}

// --- DYNAMIC CLEAR SKY CALCULATOR ---
def getDynamicClearThreshold() {
    def baseClear = getSmartClearThreshold()
    def baseOvercast = getSmartOvercastThreshold()
    
    if (!useDynamicClear) return baseClear
    
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunrise || !sunInfo.sunset) return baseClear
    
    def nowTime = new Date().time
    def sunrise = sunInfo.sunrise.time
    def sunset = sunInfo.sunset.time
    
    // Outside daylight hours, return base
    if (nowTime < sunrise || nowTime > sunset) return baseClear
    
    // 1. Calculate Time-of-Day Arc (0.0 to 1.0)
    def totalDaylightMillis = sunset - sunrise
    def currentDaylightMillis = nowTime - sunrise
    def dayPercentage = currentDaylightMillis / totalDaylightMillis
    def timeMultiplier = Math.sin(dayPercentage * Math.PI)
    
    // 2. Calculate Seasonal Arc (Day of Year)
    def cal = Calendar.getInstance()
    def dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    
    // Approx Summer Solstice (172) as Peak, Winter Solstice (355) as Trough
    def seasonalOffset = ((dayOfYear - 172) / 365.0) * (Math.PI * 2)
    def seasonMultiplier = 0.7 + (0.3 * Math.cos(seasonalOffset))
    
    // 3. Combine Math
    def dynamicLimit = (baseClear * timeMultiplier * seasonMultiplier).toInteger()
    
    // Keep a sensible minimum deadband so Clear Sky doesn't dip below the Overcast Threshold
    def safeMinimum = baseOvercast + 500
    
    return Math.max(dynamicLimit, safeMinimum)
}

// --- UTILITY: GRAPHING & SOLAR BASELINE ---
def logGraphData() {
    if (!luxSensors) return
    
    def currentLux = getAggregateLux()
    def timestamp = new Date().format("HH:mm", location.timeZone)
    def nowTime = now()
    
    def expectedLux = 0
    
    // ALWAYS trace standard theoretical curve based on peak calibration for visual reference
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise && sunInfo.sunset) {
        def sr = sunInfo.sunrise.time
        def ss = sunInfo.sunset.time
        
        if (nowTime >= sr && nowTime <= ss) {
            def fraction = (nowTime - sr) / (ss - sr)
            def peak = getExpectedPeakLux()
            expectedLux = (peak * Math.sin(fraction * Math.PI)).toInteger()
        }
    }
    
    if (!state.luxHistory) state.luxHistory = []
    
    // Add new data point including expected baseline
    state.luxHistory.add([time: timestamp, lux: currentLux, expected: expectedLux])
    
    if (state.luxHistory.size() > 96) {
        state.luxHistory = state.luxHistory.drop(1)
    }
}

def generateChartUrl() {
    if (!state.luxHistory) return ""
    
    def labels = []
    def actualData = []
    def expectedData = []
    
    state.luxHistory.each { pt ->
        labels << "'${pt.time}'"
        actualData << (pt.lux ?: 0)
        expectedData << (pt.expected ?: 0) // Handles older data points safely
    }
    
    // Compact JSON string to prevent URL encoding issues
    def chartConfig = "{type:'line',data:{labels:[${labels.join(',')}],datasets:[{label:'Actual Lux',data:[${actualData.join(',')}],fill:true,backgroundColor:'rgba(54,162,235,0.1)',borderColor:'rgba(54,162,235,1)',borderWidth:2,pointRadius:0},{label:'Expected Clear Sky',data:[${expectedData.join(',')}],fill:false,borderColor:'rgba(255,159,64,0.8)',borderWidth:2,borderDash:[5,5],pointRadius:0}]},options:{legend:{display:true,position:'bottom'},scales:{xAxes:[{ticks:{autoSkip:true,maxTicksLimit:8}}]}}}"
    
    def encodedConfig = java.net.URLEncoder.encode(chartConfig, "UTF-8")
    return "https://quickchart.io/chart?c=${encodedConfig}&w=600&h=300"
}

// --- SYSTEM CHECKS ---
def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def isModeAllowed() {
    if (!activeModes) return true
    return activeModes.contains(location.mode)
}

def modeHandler(evt) {
    if (!isModeAllowed()) {
        addToHistory("GLOBAL: Hub entered restricted mode (${evt.value}). Pausing detector.")
        unschedule("triggerOvercast")
        unschedule("triggerClear")
        state.pendingOvercast = false
        state.pendingClear = false
    } else {
        addToHistory("GLOBAL: Hub entered allowed mode (${evt.value}). Resuming detector.")
        evaluateLuxCondition()
    }
}

// --- PROPORTIONAL DIMMING ENGINE ---
def updateDimmerLevel(currentLux) {
    if (!targetDimmer || isSystemPaused() || !isModeAllowed() || state.isNight) return

    def overLimit = getSmartOvercastThreshold()
    def stormLimit = heavyStormLux ?: 500
    def maxLvl = maxDimLevel ?: 100
    def minLvl = minDimLevel ?: 20

    def targetLevel = minLvl
    
    if (currentLux <= stormLimit) {
        targetLevel = maxLvl
    } else if (currentLux >= overLimit) {
        targetLevel = minLvl
    } else {
        // Logarithmic interpolation for natural human eye perception
        def luxRange = overLimit - stormLimit
        def levelRange = maxLvl - minLvl
        def luxDrop = overLimit - currentLux
        
        def curve = Math.log10(1 + 9 * (luxDrop / luxRange))
        def calcLevel = minLvl + (curve * levelRange)
        targetLevel = calcLevel.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
    }

    def currentDimmerLevel = targetDimmer.currentValue("level")?.toInteger() ?: 0
    def currentDimmerState = targetDimmer.currentValue("switch")

    // Mesh Protection
    if (currentDimmerState != "on" || Math.abs(currentDimmerLevel - targetLevel) > 2) {
        addToHistory("DIMMER: Dynamic adjustment to ${targetLevel}% (Lux: ${currentLux}).")
        targetDimmer.setLevel(targetLevel)
    }
}

// --- CORE LUX EVALUATION ---
def luxHandler(evt) {
    evaluateLuxCondition()
}

def forceImmediateEvaluation() {
    if (!luxSensors || isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    def lux = getAggregateLux()
    def overLimit = getSmartOvercastThreshold()
    def clearLimit = getDynamicClearThreshold()
    
    if (lux <= overLimit) {
        state.currentCondition = "Overcast"
        if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
        if (targetDimmer) updateDimmerLevel(lux)
    } else if (lux >= clearLimit) {
        state.currentCondition = "Clear"
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    } else {
        // Fallback baseline for deadband boot up
        state.currentCondition = "Assumed Clear (Boot)"
        addToHistory("SYSTEM BOOT: Booted inside deadband (${lux} lx). Assuming clear state to prevent stuck lights.")
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    }
}

def evaluateLuxCondition() {
    if (isSystemPaused() || !isModeAllowed()) return
    if (state.isNight && useAstro) return 
    
    if (!luxSensors) return
    
    def lux = getAggregateLux()
    def overLimit = getSmartOvercastThreshold()
    def clearLimit = getDynamicClearThreshold()
    def debounceSecs = (debounceTime ?: 10) * 60
    def intervalMins = sensorInterval ?: 15
    
    def timeNow = now()
    def timeDeltaMins = state.lastLuxCheckTime ? (timeNow - state.lastLuxCheckTime) / 60000 : 0
    def luxDrop = state.lastLuxValue ? (state.lastLuxValue - lux) : 0
    
    // --- SMART LEARNING: TRACK OUTDOOR DAILY MAX ---
    if (!state.dailyMaxLux || lux > state.dailyMaxLux) {
        state.dailyMaxLux = lux
    }
    
    // --- DYNAMIC TIMERS BASED ON SENSOR HARDWARE LIMITS ---
    def stalePeakMillis = (intervalMins * 3) * 60000 
    def detectionWindowMins = (intervalMins * 2) 
    
    // --- PEAK TRACKER ---
    if (!state.recentPeakLux) {
        state.recentPeakLux = lux
        state.recentPeakTime = timeNow
    }

    if (!state.activeCloudEvent) {
        if (lux > state.recentPeakLux || (timeNow - state.recentPeakTime > stalePeakMillis)) {
            state.recentPeakLux = lux
            state.recentPeakTime = timeNow
        }
    }

    def dropFromPeak = state.recentPeakLux - lux
    def peakDropPercentage = state.recentPeakLux > 0 ? (dropFromPeak / state.recentPeakLux) : 0
    def timeFromPeakMins = (timeNow - state.recentPeakTime) / 60000

    if ((peakDropPercentage >= 0.30 || dropFromPeak > 15000) && timeFromPeakMins <= detectionWindowMins) {
        if (!state.activeCloudEvent) {
            state.activeCloudEvent = [startTime: timeNow, startLux: state.recentPeakLux, minLux: lux]
            state.dipReason = "Potential Clouding"
            addToHistory("ANALYSIS: Potential Clouding detected. Tracking as active weather event.")
        } else if (lux < state.activeCloudEvent.minLux) {
            state.activeCloudEvent.minLux = lux 
        }
    } else if (luxDrop > 0 && timeDeltaMins > intervalMins && lux <= overLimit && !state.pendingOvercast) {
        state.dipReason = "Gradual Fade"
    }
    
    if (state.activeCloudEvent) {
        def recoveryAmount = lux - state.activeCloudEvent.minLux
        def totalDrop = state.activeCloudEvent.startLux - state.activeCloudEvent.minLux
        
        if (lux >= state.activeCloudEvent.startLux || recoveryAmount >= (totalDrop * 0.50)) {
            closeActiveCloudEvent()
            addToHistory("ANALYSIS: Potential Clouding event passed and logged to history.")
            state.recentPeakLux = lux 
            state.recentPeakTime = timeNow
        }
    }
    
    state.lastLuxValue = lux
    state.lastLuxCheckTime = timeNow

    if (state.currentCondition == "Overcast") {
        updateDimmerLevel(lux)
    }
    
    if (lux <= overLimit && state.currentCondition != "Overcast") {
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            if (state.dipReason == "Potential Clouding") {
                addToHistory("Sky darkened back to ${lux} lx rapidly. Passing cloud verified. Canceled Clear.")
            } else {
                addToHistory("Sky darkened back to ${lux} lx. Canceled Clear Verification.")
            }
        }
        
        if (!state.pendingOvercast) {
            state.pendingOvercast = true
            runIn(debounceSecs, "triggerOvercast", [overwrite: true])
            def causeStr = (state.dipReason == "Potential Clouding") ? "Monitoring for Storm vs Cloud..." : "Monitoring for Overcast..."
            addToHistory("Lux dropped to ${lux}. Starting ${(debounceSecs/60).toInteger()}m verification. ${causeStr}")
        }
    } 
    else if (lux >= clearLimit && state.currentCondition != "Clear" && state.currentCondition != "Assumed Clear (Boot)") {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            if (state.dipReason == "Potential Clouding") {
                addToHistory("Sky brightened to ${lux} lx rapidly. Logged as POTENTIAL CLOUDING. Canceled Overcast Verification.")
            } else {
                addToHistory("Sky brightened back to ${lux} lx. Canceled Overcast Verification.")
            }
        }
        
        if (!state.pendingClear) {
            state.pendingClear = true
            runIn(debounceSecs, "triggerClear", [overwrite: true])
            addToHistory("Lux rose to ${lux}. Starting ${(debounceSecs/60).toInteger()}m Clear verification timer.")
        }
    }
    else if (lux > overLimit && lux < clearLimit) {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            addToHistory("Lux recovered into deadband (${lux} lx). Canceled Overcast verification.")
        }
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            addToHistory("Lux dropped into deadband (${lux} lx). Canceled Clear verification.")
        }
    }
}

def triggerOvercast() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    state.pendingOvercast = false
    state.currentCondition = "Overcast"
    
    if (state.dipReason == "Potential Clouding") {
        addToHistory("CONFIRMED: Lux remained low. Logged as STORM or HEAVY OVERCAST. Activating targets.")
    } else {
        addToHistory("CONFIRMED: Conditions remained Overcast. Activating targets.")
    }
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
    if (targetDimmer && luxSensors) updateDimmerLevel(getAggregateLux())
}

def triggerClear() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    state.pendingClear = false
    state.currentCondition = "Clear"
    
    addToHistory("CONFIRMED: Conditions remained Clear. Deactivating targets.")
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
    if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
}

def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
   
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def checkInitialAstroState() {
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return
    def now = new Date()
    
    def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
    def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
    def actualSunrise = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
    def actualSunset = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
    
    if (now >= actualSunset || now <= actualSunrise) {
        state.isNight = true
        state.currentCondition = "Nighttime"
        addToHistory("ASTRO BOOT: Currently nighttime. Applying Nighttime Logic.")
        enforceNightAction()
    } else {
        state.isNight = false
    }
}

def executeSunset() {
    if (!useAstro) return
  
    if (state.activeCloudEvent) closeActiveCloudEvent()
    
    // --- SMART LEARNING: EVALUATE OUTDOOR DAILY MAX ---
    if (useSmartLearning && state.dailyMaxLux && state.dailyMaxLux > 100) {
        def baseline = state.peakLuxHistory.size() > 0 ? (state.peakLuxHistory.sum() / state.peakLuxHistory.size()) : (peakClearLux ?: 10000)
        def lowerBound = baseline * 0.8 
        
        if (state.peakLuxHistory.size() < 3 || state.dailyMaxLux >= lowerBound) {
            state.peakLuxHistory.add(state.dailyMaxLux)
            if (state.peakLuxHistory.size() > 30) state.peakLuxHistory = state.peakLuxHistory.drop(1)
            log.info "SMART LEARNING (OUTDOOR): Daily max of ${state.dailyMaxLux} lx added."
        } else {
            log.info "SMART LEARNING (OUTDOOR): Daily max of ${state.dailyMaxLux} lx rejected (20% bad weather rule)."
        }
    }
    state.dailyMaxLux = 0
    
    // --- SMART LEARNING: EVALUATE INDOOR ROOMS & EXPORT HUB VARIABLES ---
    def configuredRooms = numRooms ?: 0
    for (int i = 1; i <= configuredRooms; i++) {
        def rData = state.roomData["${i}"]
        // Ensure the room got at least some light before processing to prevent math errors
        if (useSmartLearning && rData && rData.dailyMax && rData.dailyMax > 10) { 
            def basePeak = settings["roomPeakLux_${i}"] ?: 1000
            def baseTarget = settings["roomBaseLux_${i}"] ?: 100
            
            def rBaseline = rData.peakHistory.size() > 0 ? (rData.peakHistory.sum() / rData.peakHistory.size()) : basePeak
            def rLowerBound = rBaseline * 0.8
            
            // Apply bad weather filter
            if (rData.peakHistory.size() < 3 || rData.dailyMax >= rLowerBound) {
                rData.peakHistory.add(rData.dailyMax)
               
                if (rData.peakHistory.size() > 30) rData.peakHistory = rData.peakHistory.drop(1)
                log.info "SMART LEARNING (ROOM ${i}): Daily max of ${rData.dailyMax} lx added."
            } else {
                log.info "SMART LEARNING (ROOM ${i}): Daily max of ${rData.dailyMax} lx rejected (20% rule)."
            }
            
            // Calculate proportional setpoint
            def currentPeak = rData.peakHistory.size() >= 30 ? (rData.peakHistory.sum() / rData.peakHistory.size()) : basePeak
            def ratio = baseTarget / basePeak
            def newSetpoint = (currentPeak * ratio).toInteger()
            rData.currentSetpoint = newSetpoint
            
            // Write to Hub Variable
            def varName = settings["roomVar_${i}"]
            if (varName) {
                try {
                    setGlobalVar(varName, newSetpoint)
                    log.info "ROOM ${i}: Exported new setpoint (${newSetpoint}) to Hub Variable: ${varName}."
                } catch (e) {
                    log.error "ROOM ${i}: Failed to set Hub Variable '${varName}'. Ensure it is created and spelled correctly in your hub settings."
                }
            }
        }
        // Reset room max for tomorrow
        if (rData) rData.dailyMax = 0
    }
    
    state.isNight = true
    state.currentCondition = "Nighttime"
    state.pendingOvercast = false
    state.pendingClear = false
    unschedule("triggerOvercast")
    unschedule("triggerClear")
    
    addToHistory("ASTRO: Sun has set. Suspending lux detection and applying Nighttime Logic.")
    enforceNightAction()
}

def enforceNightAction() {
    if (isModeAllowed() && !isSystemPaused()) {
        def action = nightAction ?: "Turn ON (Dark/Overcast)"
        
        if (action == "Turn OFF (Clear/Night)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
            if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
            addToHistory("ASTRO: Forced Virtual Targets OFF for nighttime.")
        } 
        else if (action == "Turn ON (Dark/Overcast)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
            if (targetDimmer) {
                def nLevel = nightDimLevel ?: 100
                targetDimmer.setLevel(nLevel)
            }
            addToHistory("ASTRO: Forced Virtual Targets ON for nighttime.")
        }
    }
}

def executeSunrise() {
    if (!useAstro) return
    
    state.isNight = false
    addToHistory("ASTRO: Sun has risen. Resuming Overcast detection.")
    forceImmediateEvaluation()
}
