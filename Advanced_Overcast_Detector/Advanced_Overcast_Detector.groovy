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
        
        section("Live System Dashboard") {
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Environment</th><th style='padding: 8px;'>System Evaluation</th><th style='padding: 8px;'>Outputs</th></tr>"
            
            def currentLux = luxSensor ? "${luxSensor.currentValue('illuminance')} lx" : "-- lx"
            def sState = state.currentCondition ?: "Awaiting Sync..."
            
            def sColor = "orange"
            if (sState == "Overcast") sColor = "blue"
            if (sState == "Clear") sColor = "green"
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
            
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${currentLux}</b></td><td style='padding: 8px; color: ${sColor}; font-weight: bold;'>${sState}${pendingMsg}</td><td style='padding: 8px;'>${outputsDisplay}</td></tr>"
            statusText += "</table>"
           
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
                    tableHtml += "<div style='margin-top: 8px; font-size: 12px; color: blue; font-weight: bold;'>☁️ Event currently in progress...</div>"
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
        
        section("Graph Calibration (Solar Baseline)") {
            input "peakClearLux", "number", title: "Expected Peak Clear-Sky Brightness (Lux)", defaultValue: 10000, required: true,
                description: "Set this to whatever your sensor typically reads at Solar Noon on a perfectly clear day. This scales the theoretical sun curve on your graph."
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Sensor & Control Targets") {
            input "luxSensor", "capability.illuminanceMeasurement", title: "Outdoor Master Lux Sensor", required: true
            
            paragraph "<b>Control Targets:</b> Select one or both. The Virtual Switch handles binary logic (ON/OFF). The Virtual Dimmer scales brightness based on storm severity."
            input "targetSwitch", "capability.switch", title: "Virtual Switch (ON = Overcast/Dark)", required: false
            input "targetDimmer", "capability.switchLevel", title: "Virtual Dimmer (Proportional Brightness)", required: false
            
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            input "activeModes", "mode", title: "Active Modes (App only runs in these)", multiple: true, required: false
        }
        
        section("Proportional Dimming Setup") {
            paragraph "Maps the virtual dimmer level between your Overcast Threshold and Heavy Storm Limit. Only applies if Virtual Dimmer is selected above."
            input "heavyStormLux", "number", title: "Heavy Storm Limit (Lux)", defaultValue: 500, required: true,
                description: "If lux drops to this level, the dimmer hits Max Brightness."
            input "maxDimLevel", "number", title: "Max Brightness Level (%)", defaultValue: 100, required: true, range: "1..100"
            input "minDimLevel", "number", title: "Min Brightness Level (%)", defaultValue: 20, required: true, range: "1..100",
                description: "The starting brightness when it just barely crosses the Overcast threshold."
            input "nightDimLevel", "number", title: "Nighttime Brightness Level (%)", defaultValue: 100, required: true, range: "1..100"
        }
        
        section("Hysteresis & Thresholds (The Deadband)") {
            input "overcastThreshold", "number", title: "Overcast Drop Threshold (Lux)", defaultValue: 2000, required: true,
                description: "If lux drops below this, start the Overcast timer."
                
            input "clearThreshold", "number", title: "Clear Sky Recovery Threshold (Lux)", defaultValue: 4000, required: true,
                description: "If lux rises above this, start the Clear Sky timer. Keep this higher than the Overcast threshold to prevent yo-yoing."
                
            input "debounceTime", "number", title: "Anti-Yo-Yo Debounce Time (Minutes)", defaultValue: 10, required: true,
                description: "How long the sky must stay below/above the threshold before flipping the virtual outputs."
        }
        
        section("Universal Darkness (Nighttime Logic)") {
            input "useAstro", "bool", title: "Apply Nighttime Logic?", defaultValue: true, submitOnChange: true
            
            if (useAstro) {
                input "nightAction", "enum", title: "When the sun sets, force the virtual outputs:", options: ["Turn OFF (Clear/Night)", "Turn ON (Dark/Overcast)", "Do Nothing (Leave as is)"], defaultValue: "Turn ON (Dark/Overcast)", required: true,
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
    state.activeCloudEvent = null
    state.currentCondition = "Evaluating..."
    state.pendingOvercast = false
    state.pendingClear = false
    state.isNight = false
    state.lastLuxCheckTime = now()
    state.lastLuxValue = null
    state.dipReason = null
    
    if (luxSensor) subscribe(luxSensor, "illuminance", luxHandler)
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

// --- UTILITY: CLOUD EVENT LOGGER ---
def closeActiveCloudEvent() {
    if (!state.activeCloudEvent) return
    
    def endTime = now()
    def durationSecs = (endTime - state.activeCloudEvent.startTime) / 1000
    def durationStr = ""
    
    if (durationSecs < 60) {
        durationStr = "${durationSecs.toInteger()} sec"
    } else {
        def mins = (durationSecs / 60).toInteger()
        def secs = (durationSecs % 60).toInteger()
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
    
    if (state.cloudHistory.size() > 15) {
        state.cloudHistory = state.cloudHistory.take(15)
    }
    state.activeCloudEvent = null
}

// --- UTILITY: HISTORY LOGGER ---
def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

// --- UTILITY: GRAPHING & SOLAR BASELINE ---
def logGraphData() {
    if (!luxSensor) return
    
    def currentLux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
    def timestamp = new Date().format("HH:mm", location.timeZone)
    def nowTime = now()
    
    def expectedLux = 0
    def sunInfo = getSunriseAndSunset()
    
    // Calculate theoretical sun brightness based on time of day (Sine Wave Curve)
    if (sunInfo && sunInfo.sunrise && sunInfo.sunset) {
        def sr = sunInfo.sunrise.time
        def ss = sunInfo.sunset.time
        
        if (nowTime >= sr && nowTime <= ss) {
            def fraction = (nowTime - sr) / (ss - sr)
            def peak = peakClearLux ?: 10000
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

    def overLimit = overcastThreshold ?: 2000
    def stormLimit = heavyStormLux ?: 500
    def maxLvl = maxDimLevel ?: 100
    def minLvl = minDimLevel ?: 20

    def targetLevel = minLvl
    
    if (currentLux <= stormLimit) {
        targetLevel = maxLvl
    } else if (currentLux >= overLimit) {
        targetLevel = minLvl
    } else {
        def luxRange = overLimit - stormLimit
        def levelRange = maxLvl - minLvl
        def luxDrop = overLimit - currentLux
        
        def calcLevel = minLvl + ((luxDrop / luxRange) * levelRange)
        targetLevel = calcLevel.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
    }

    def currentDimmerLevel = targetDimmer.currentValue("level")?.toInteger() ?: 0
    def currentDimmerState = targetDimmer.currentValue("switch")

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
    if (!luxSensor || isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    def lux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
    def overLimit = overcastThreshold ?: 2000
    def clearLimit = clearThreshold ?: 4000
    
    if (lux <= overLimit) {
        state.currentCondition = "Overcast"
        if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
        if (targetDimmer) updateDimmerLevel(lux)
    } else if (lux >= clearLimit) {
        state.currentCondition = "Clear"
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    } else {
        state.currentCondition = "Deadband (Waiting for shift)"
    }
}

def evaluateLuxCondition() {
    if (isSystemPaused() || !isModeAllowed()) return
    if (state.isNight && useAstro) return 
    
    if (!luxSensor) return
    
    def lux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
    def overLimit = overcastThreshold ?: 2000
    def clearLimit = clearThreshold ?: 4000
    def debounceSecs = (debounceTime ?: 10) * 60
    
    def timeNow = now()
    if (state.lastLuxValue != null && state.lastLuxCheckTime != null) {
        def timeDeltaMins = (timeNow - state.lastLuxCheckTime) / 60000
        def luxDrop = state.lastLuxValue - lux
        
        // Calculate the percentage of the drop
        def dropPercentage = state.lastLuxValue > 0 ? (luxDrop / state.lastLuxValue) : 0
        
        // Trigger a cloud event if lux drops by 30% OR drops by more than 15,000 lux rapidly
        if ((dropPercentage >= 0.30 || luxDrop > 15000) && timeDeltaMins <= 10) {
            if (!state.activeCloudEvent) {
                state.activeCloudEvent = [startTime: timeNow, startLux: state.lastLuxValue, minLux: lux]
                state.dipReason = "Sudden Drop"
                addToHistory("ANALYSIS: Sharp lux plunge detected. Tracking as active weather event.")
            } else if (lux < state.activeCloudEvent.minLux) {
                state.activeCloudEvent.minLux = lux 
            }
        } else if (luxDrop > 0 && timeDeltaMins > 15 && lux <= overLimit && !state.pendingOvercast) {
            state.dipReason = "Gradual Fade"
        }
    }
    
    // Close the cloud event if it recovers by at least 50% of what it dropped, or exceeds the start lux
    if (state.activeCloudEvent) {
        def recoveryAmount = lux - state.activeCloudEvent.minLux
        def totalDrop = state.activeCloudEvent.startLux - state.activeCloudEvent.minLux
        
        if (lux >= state.activeCloudEvent.startLux || recoveryAmount >= (totalDrop * 0.50)) {
            closeActiveCloudEvent()
            addToHistory("ANALYSIS: Cloud/Weather event passed and logged to history.")
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
            
            if (state.dipReason == "Sudden Drop") {
                addToHistory("Sky darkened back to ${lux} lx rapidly. Passing cloud verified. Canceled Clear.")
            } else {
                addToHistory("Sky darkened back to ${lux} lx. Canceled Clear Verification.")
            }
        }
        
        if (!state.pendingOvercast) {
            state.pendingOvercast = true
            runIn(debounceSecs, "triggerOvercast", [overwrite: true])
            
            def causeStr = (state.dipReason == "Sudden Drop") ? "Monitoring for Storm vs Cloud..." : "Monitoring for Overcast..."
            addToHistory("Lux dropped to ${lux}. Starting ${(debounceSecs/60).toInteger()}m verification. ${causeStr}")
        }
    } 
    else if (lux >= clearLimit && state.currentCondition != "Clear") {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            
            if (state.dipReason == "Sudden Drop") {
                addToHistory("Sky brightened to ${lux} lx rapidly. Logged as PASSING CLOUD. Canceled Overcast Verification.")
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
    
    if (state.dipReason == "Sudden Drop") {
        addToHistory("CONFIRMED: Lux remained low. Logged as STORM or HEAVY OVERCAST. Activating targets.")
    } else {
        addToHistory("CONFIRMED: Conditions remained Overcast. Activating targets.")
    }
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
    
    if (targetDimmer && luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
        updateDimmerLevel(currentLux)
    }
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
        enforceNightAction()
    } else {
        state.isNight = false
    }
}

def executeSunset() {
    if (!useAstro) return
  
    if (state.activeCloudEvent) closeActiveCloudEvent()
    
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
