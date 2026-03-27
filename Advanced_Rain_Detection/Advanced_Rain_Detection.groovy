/**
 * Advanced Rain Detection
 */

definition(
    name: "Advanced Rain Detection",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-sensor weather logic engine calculating VPD, Dew Point, Pressure Trends, Rapid Cooling, Solar Drops, and Evaporation to predict and track precipitation.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "configPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Rain Detection</b>", install: true, uninstall: true) {
        
        section("<b>Live Weather & Logic Dashboard</b>") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Analyzes real-time environmental data (VPD, Dew Point Spread, Temp/Lux Deltas) to predict rain probability, detect active states, and estimate clearing & drying times.</div>"
            
            if (sensorTemp && sensorHum && sensorPress) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Current Environment</th><th style='padding: 8px;'>Calculated Metrics & Trends</th><th style='padding: 8px;'>System State & Logic</th></tr>"

                // Fetch raw data
                def t = sensorTemp.currentValue("temperature")?.toFloat() ?: 0.0
                def h = sensorHum.currentValue("humidity")?.toFloat() ?: 0.0
                def p = sensorPress.currentValue("pressure")?.toFloat() ?: 0.0
                def r = sensorRain?.currentValue("rainRate")?.toFloat() ?: 0.0
                def lux = sensorLux?.currentValue("illuminance") ?: "N/A"
                def wind = sensorWind?.currentValue("windSpeed") ?: "N/A"
                def rainDay = sensorRainDaily?.currentValue("rainDaily") ?: (sensorRainDaily?.currentValue("water") ?: "0.0")
                def rainWeek = sensorRainWeekly?.currentValue("rainWeekly") ?: "0.0"
                
                // Fetch calculated data
                def vpd = state.currentVPD ?: 0.0
                def dp = state.currentDewPoint ?: 0.0
                def dpSpread = state.dewPointSpread ?: 0.0
                def pTrend = state.pressureTrendStr ?: "Stable"
                def tTrend = state.tempTrendStr ?: "Stable"
                def luxTrend = state.luxTrendStr ?: "N/A"
                def windTrend = state.windTrendStr ?: "N/A"
                def dryingRate = state.dryingPotential ?: "N/A"
                
                def prob = state.rainProbability ?: 0
                def activeState = state.weatherState ?: "Clear"
                def clearTime = state.expectedClearTime ?: "N/A"
                def reasoning = state.logicReasoning ?: "Waiting for initial sensor readings..."

                // Formatting
                def envDisplay = "<b>Temp:</b> ${t}°<br><b>Humidity:</b> ${h}%<br><b>Pressure:</b> ${p}<br><b>Rain Rate:</b> ${r}/hr"
                if (sensorLux) envDisplay += "<br><b>Solar/Lux:</b> ${lux}"
                if (sensorWind) envDisplay += "<br><b>Wind:</b> ${wind} mph"
                
                def vpdColor = vpd < 0.5 ? "red" : (vpd < 1.0 ? "orange" : "green")
                def spreadColor = dpSpread < 3.0 ? "red" : (dpSpread < 6.0 ? "orange" : "green")
                def calcDisplay = "<b>VPD:</b> <span style='color:${vpdColor};'>${String.format('%.2f', vpd)} kPa</span><br>"
                calcDisplay += "<b>Dew Point:</b> ${String.format('%.1f', dp)}° <span style='color:${spreadColor}; font-size:11px;'>(Spread: ${String.format('%.1f', dpSpread)}°)</span><br>"
                calcDisplay += "<b>Drying Rate:</b> ${dryingRate}<br><br>"
                calcDisplay += "<span style='font-size:11px;'><b>P-Trend:</b> ${pTrend}<br><b>T-Trend:</b> ${tTrend}"
                if (sensorLux) calcDisplay += "<br><b>L-Trend:</b> ${luxTrend}"
                if (sensorWind) calcDisplay += "<br><b>W-Trend:</b> ${windTrend}"
                calcDisplay += "</span>"
                
                def probColor = prob > 70 ? "red" : (prob > 40 ? "orange" : "black")
                def stateColor = activeState == "Clear" ? "green" : "blue"
                def stateDisplay = "<b>State: <span style='color:${stateColor};'>${activeState.toUpperCase()}</span></b><br>"
                stateDisplay += "<b>Rain Chance:</b> <span style='color:${probColor}; font-weight:bold;'>${prob}%</span><br>"
                stateDisplay += "<b>Est. Clear:</b> ${clearTime}<br><br>"
                stateDisplay += "<span style='font-size:11px; color:#555;'><i>${reasoning}</i></span>"

                statusText += "<tr><td style='padding: 8px; vertical-align:top; border-right:1px solid #ddd;'>${envDisplay}</td><td style='padding: 8px; vertical-align:top; border-right:1px solid #ddd;'>${calcDisplay}</td><td style='padding: 8px; vertical-align:top;'>${stateDisplay}</td></tr>"
                
                // --- Rainfall History, Graph & Record Banner ---
                def recordInfo = state.recordRain ?: [date: "None", amount: 0.0]
                def sevenDayList = state.sevenDayRain ?: []
                
                def historyDisplay = "<div><b>🏆 All-Time Record:</b> <span style='color:blue; font-weight:bold; font-size: 15px;'>${recordInfo.amount}</span> <i style='color:#555;'>(${recordInfo.date})</i></div>"
                historyDisplay += "<div style='margin-top:5px;'><b>Current Week:</b> ${rainWeek} | <b>Today's Total:</b> ${state.currentDayRain ?: 0.0}</div>"
                
                // Render Dynamic CSS Bar Graph
                if (sevenDayList.size() > 0) {
                    def maxRain = 0.5 // Default baseline scaling
                    sevenDayList.each { if (it.amount > maxRain) maxRain = it.amount }
                    
                    historyDisplay += "<div style='margin-top:15px; font-weight:bold;'>7-Day History:</div>"
                    historyDisplay += "<div style='display:flex; align-items:flex-end; height:100px; gap:8px; margin-top:5px; border-bottom:2px solid #aaa; padding-bottom:2px;'>"
                    
                    // Reverse list to display oldest on the left, newest on the right
                    sevenDayList.reverse().each { item ->
                        def barHeight = (item.amount / maxRain) * 80 // Max 80px visual height
                        if (item.amount > 0 && barHeight < 2) barHeight = 2 // Ensure trace amounts are visible blips
                        
                        def dateSplit = item.date.split("-")
                        def shortDate = dateSplit.size() == 3 ? "${dateSplit[1]}/${dateSplit[2]}" : item.date
                        
                        historyDisplay += "<div style='display:flex; flex-direction:column; align-items:center; flex:1;'>"
                        historyDisplay += "<div style='font-size:10px; color:#555; margin-bottom:2px;'>${item.amount}</div>"
                        historyDisplay += "<div style='width:80%; max-width:35px; background-color:#4a90e2; height:${barHeight}px; border-radius:3px 3px 0 0;'></div>"
                        historyDisplay += "<div style='font-size:10px; margin-top:2px;'>${shortDate}</div>"
                        historyDisplay += "</div>"
                    }
                    historyDisplay += "</div>"
                } else {
                    historyDisplay += "<div style='margin-top:15px; font-size:11px; color:#888;'><i>7-Day Graph will generate after the first midnight rollover...</i></div>"
                }
                
                statusText += "<tr style='border-top: 1px solid #ccc; background-color: #e6f2ff;'><td colspan='3' style='padding: 15px;'>${historyDisplay}</td></tr>"
                statusText += "</table>"
                
                // Switch Status
                def rainSw = switchRaining?.currentValue("switch") == "on" ? "<span style='color:blue; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def sprinkSw = switchSprinkling?.currentValue("switch") == "on" ? "<span style='color:blue; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Virtual Switches:</b> Heavy Rain: [${rainSw}] | Sprinkling: [${sprinkSw}]</div>"
                statusText += "</div>"

                paragraph statusText
            } else {
                paragraph "<i>Primary sensors missing. Click configuration below to assign Ecowitt devices.</i>"
            }
        }

        section("<b>System Configuration</b>") {
            href(name: "configPageLink", page: "configPage", title: "▶ Configure Sensors & Switches", description: "Set up Ecowitt sensors, virtual outputs, and thresholds.")
        }
        
        section("<b>Global Actions & Overrides</b>") {
            input "forceEvalBtn", "button", title: "⚙️ Force Logic Evaluation"
            input "resetRecordBtn", "button", title: "🗑️ Reset All-Time Rain Record"
            input "clearStateBtn", "button", title: "⚠ Reset Internal State & History"
        }

        section("<b>Action History & Debugging</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
            
            if (state.actionHistory) {
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
            }
        }
    }
}

def configPage() {
    dynamicPage(name: "configPage", title: "<b>Configuration</b>", install: false, uninstall: false) {
        section("<b>Primary Environment Sensors (Required)</b>") {
            input "sensorTemp", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor", required: true
            input "sensorPress", "capability.pressureMeasurement", title: "Barometric Pressure Sensor", required: true
        }
        
        section("<b>Advanced Prediction Sensors (Optional)</b>") {
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor (Detects incoming cloud fronts & boosts drying rate)", required: false
            input "sensorWind", "capability.sensor", title: "Wind Speed Sensor (Detects storm gust fronts & boosts drying rate)", required: false
            paragraph "<i>Note: Wind speed should provide the 'windSpeed' attribute.</i>"
        }

        section("<b>Precipitation & Accumulation Sensors (Optional)</b>") {
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor (Ecowitt Rate)", required: false
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: false
            input "sensorRainWeekly", "capability.sensor", title: "Weekly Rain Accumulation Sensor", required: false
        }
        
        section("<b>Virtual Output Switches (Mutually Exclusive)</b>") {
            input "switchSprinkling", "capability.switch", title: "Sprinkling / Light Rain Switch", required: true
            input "switchRaining", "capability.switch", title: "Heavy Rain Switch", required: true
            input "debounceMins", "number", title: "State Debounce Time (Minutes)", required: true, defaultValue: 5, description: "Prevents rapidly flipping back and forth between states. Upgrading to worse weather is instant; downgrading or clearing will wait this long."
            input "heavyRainThreshold", "decimal", title: "Heavy Rain Rate Threshold (in/hr or mm/hr)", required: true, defaultValue: 0.1
        }
        
        section("<b>Notifications</b>") {
            input "notifyDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
            input "notifyProbThreshold", "number", title: "Rain Probability Setpoint (%)", required: true, defaultValue: 75, description: "Send notification when calculated rain probability hits this threshold."
            input "notifyOnSprinkle", "bool", title: "Notify when Sprinkling starts", defaultValue: true
            input "notifyOnRain", "bool", title: "Notify when Heavy Rain starts", defaultValue: true
            input "notifyOnClear", "bool", title: "Notify when weather clears", defaultValue: false
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    // Reset core states if missing
    if (!state.weatherState) state.weatherState = "Clear"
    if (!state.lastStateChange) state.lastStateChange = now()
    
    // Initialize History Maps
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.luxHistory) state.luxHistory = []
    if (!state.windHistory) state.windHistory = []
    
    // Initialize Accumulation Tracking
    if (!state.sevenDayRain) state.sevenDayRain = []
    if (!state.recordRain) state.recordRain = [date: "None", amount: 0.0]
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    // Subscriptions
    if (sensorTemp) subscribe(sensorTemp, "temperature", tempHandler)
    if (sensorHum) subscribe(sensorHum, "humidity", stdHandler)
    if (sensorPress) subscribe(sensorPress, "pressure", pressureHandler)
    if (sensorLux) subscribe(sensorLux, "illuminance", luxHandler)
    if (sensorWind) subscribe(sensorWind, "windSpeed", windHandler)
    if (sensorRain) subscribe(sensorRain, "rainRate", stdHandler)
    if (sensorRainDaily) subscribe(sensorRainDaily, "rainDaily", stdHandler)
    if (sensorRainDaily) subscribe(sensorRainDaily, "water", stdHandler) // Fallback for some drivers
    
    // Scheduled fallback check
    runEvery5Minutes("evaluateWeather")
    
    logAction("Advanced Rain Detection Initialized.")
    evaluateWeather()
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logDebug("Dashboard manually refreshed.")
        return
    }
    if (btn == "forceEvalBtn") {
        logAction("MANUAL OVERRIDE: Forcing logic evaluation.")
        evaluateWeather()
    }
    if (btn == "resetRecordBtn") {
        logAction("MANUAL OVERRIDE: All-Time Rain Record Reset.")
        state.recordRain = [date: "None", amount: 0.0]
        evaluateWeather()
    }
    if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging history, records, and resetting switches.")
        state.weatherState = "Clear"
        state.pressureHistory = []
        state.tempHistory = []
        state.luxHistory = []
        state.windHistory = []
        state.sevenDayRain = []
        state.recordRain = [date: "None", amount: 0.0]
        state.currentDayRain = 0.0
        state.notifiedProb = false
        safeOff(switchSprinkling)
        safeOff(switchRaining)
        evaluateWeather()
    }
}

// === HISTORY TRACKING WRAPPERS ===
def updateHistory(historyName, val, maxAgeMs) {
    if (val == null) return
    def hist = state."${historyName}" ?: []
    hist.add([time: now(), value: val.toFloat()])
    def cutoff = now() - maxAgeMs
    hist = hist.findAll { it.time >= cutoff }
    state."${historyName}" = hist
}

// Legacy Catch for Ghost Subscriptions
def sensorHandler(evt) { stdHandler(evt) }

def stdHandler(evt) { runIn(2, "evaluateWeather") }

def tempHandler(evt) {
    updateHistory("tempHistory", evt.value, 3600000) // 1 hour
    runIn(2, "evaluateWeather")
}

def pressureHandler(evt) {
    updateHistory("pressureHistory", evt.value, 10800000) // 3 hours
    runIn(2, "evaluateWeather")
}

def luxHandler(evt) {
    updateHistory("luxHistory", evt.value, 3600000) // 1 hour
    runIn(2, "evaluateWeather")
}

def windHandler(evt) {
    updateHistory("windHistory", evt.value, 3600000) // 1 hour
    runIn(2, "evaluateWeather")
}

// === METEOROLOGICAL CALCULATIONS ===
def calculateVPD(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def calculateDewPoint(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    // Magnus-Tetens formula
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    def dpF = (dpC * (9.0 / 5.0)) + 32.0
    return dpF
}

// Generic Trend Calculator
def getTrendData(hist, minTimeHr, label) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    
    def oldest = hist.first()
    def newest = hist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    
    if (timeSpanHr < minTimeHr) return [rate: 0.0, diff: diff, str: "Stable (<${Math.round(minTimeHr*60)}m data)"]
    
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def evaluateWeather() {
    // --- Midnight Rollover & Daily Accumulation Engine ---
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.currentDateStr) state.currentDateStr = todayStr
    
    // Check if the calendar day has flipped
    if (state.currentDateStr != todayStr) {
        def yesterdayTotal = state.currentDayRain ?: 0.0
        
        // Push to 7-Day History
        def hist = state.sevenDayRain ?: []
        hist.add(0, [date: state.currentDateStr, amount: yesterdayTotal])
        if (hist.size() > 7) hist = hist[0..6]
        state.sevenDayRain = hist
        
        // Check for All-Time Record
        def record = state.recordRain ?: [date: "None", amount: 0.0]
        if (yesterdayTotal > (record.amount ?: 0.0)) {
            state.recordRain = [date: state.currentDateStr, amount: yesterdayTotal]
            logAction("🏆 New All-Time Record Rainfall! ${yesterdayTotal} on ${state.currentDateStr}")
        }
        
        // Reset for the new day
        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
    
    // Keep track of the highest rain total seen today (Protects against sensor drops/reboots)
    def currentDailyStr = sensorRainDaily?.currentValue("rainDaily") ?: (sensorRainDaily?.currentValue("water") ?: "0.0")
    def currentDaily = currentDailyStr.toFloat()
    if (currentDaily > (state.currentDayRain ?: 0.0)) {
        state.currentDayRain = currentDaily
    }

    if (!sensorTemp || !sensorHum || !sensorPress) {
        logDebug("Missing primary sensors. Cannot evaluate predictions.")
        return
    }

    def t = sensorTemp.currentValue("temperature")?.toFloat() ?: 0.0
    def h = sensorHum.currentValue("humidity")?.toFloat() ?: 0.0
    def p = sensorPress.currentValue("pressure")?.toFloat() ?: 0.0
    def r = sensorRain?.currentValue("rainRate")?.toFloat() ?: 0.0
    def luxVal = sensorLux?.currentValue("illuminance")?.toFloat() ?: 0.0
    def windVal = sensorWind?.currentValue("windSpeed")?.toFloat() ?: 0.0
    
    // --- Complex Math & Trends ---
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    
    def dp = calculateDewPoint(t, h)
    state.currentDewPoint = dp
    
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    state.dewPointSpread = dpSpread
    
    // Trends (P: 15 min min, T/L/W: 10 min min)
    def pTrendData = getTrendData(state.pressureHistory, 0.25, "Pressure")
    def tTrendData = getTrendData(state.tempHistory, 0.16, "Temp")
    def lTrendData = getTrendData(state.luxHistory, 0.16, "Lux")
    def wTrendData = getTrendData(state.windHistory, 0.16, "Wind")
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.luxTrendStr = sensorLux ? lTrendData.str : "N/A"
    state.windTrendStr = sensorWind ? wTrendData.str : "N/A"
    
    // --- Calculate Evapotranspiration / Drying Potential ---
    // Start with VPD (base moisture capacity of the air)
    def evapIndex = vpd
    // Wind significantly increases evaporation by moving saturated air away
    if (sensorWind) evapIndex += (windVal * 0.03) 
    // Solar radiation provides the latent heat needed for evaporation
    if (sensorLux) evapIndex += (luxVal / 80000.0)
    
    if (r > 0) {
        state.dryingPotential = "<span style='color:blue;'>Raining (No Drying)</span>"
    } else if (evapIndex < 0.3) {
        state.dryingPotential = "<span style='color:red;'>Very Low (Ground stays wet)</span>"
    } else if (evapIndex < 0.8) {
        state.dryingPotential = "<span style='color:orange;'>Moderate (Slow drying)</span>"
    } else if (evapIndex < 1.5) {
        state.dryingPotential = "<span style='color:green;'>High (Good drying conditions)</span>"
    } else {
        state.dryingPotential = "<span style='color:#008800; font-weight:bold;'>Very High (Rapid evaporation)</span>"
    }
    
    // --- Predictor Logic & Probability ---
    def probability = 0
    def reasoning = []
    
    // 1. Dew Point Spread (Heavy Weight)
    if (dpSpread <= 2.0) { probability += 40; reasoning << "Critical: Dew Point spread near 0° (Air saturated)" }
    else if (dpSpread <= 5.0) { probability += 20; reasoning << "Dew Point spread tightening (<5°)" }
    
    // 2. VPD Factor
    if (vpd < 0.2) { probability += 20; reasoning << "VPD extremely low" }
    else if (vpd > 1.0) { probability -= 20; reasoning << "VPD High (Dry air)" }
    
    // 3. Pressure Drops
    if (pTrendData.rate <= -0.04) { probability += 30; reasoning << "Pressure dropping rapidly" }
    else if (pTrendData.rate <= -0.02) { probability += 15; reasoning << "Pressure falling" }
    else if (pTrendData.rate > 0.03) { probability -= 30; reasoning << "Pressure rising strongly (Clearing)" }
    
    // 4. Rapid Cooling (Thunderstorm indicator)
    if (tTrendData.rate <= -6.0) { probability += 25; reasoning << "Rapid temperature drop detected (Storm front)" }
    
    // 5. Cloud Fronts / Solar Drops
    if (sensorLux && lTrendData.diff < 0) {
        def oldestLux = state.luxHistory.first()?.value ?: 0.0
        if (oldestLux > 2000) { // Only track if it was actually daytime
            def dropPercentage = Math.abs(lTrendData.diff) / oldestLux
            if (dropPercentage >= 0.60) { probability += 20; reasoning << "Solar radiation plummeted >60% (Heavy cloud cover)" }
            else if (dropPercentage >= 0.40) { probability += 10; reasoning << "Significant solar drop" }
        }
    }
    
    // 6. Wind Gust Fronts
    if (sensorWind && wTrendData.diff >= 10.0 && state.windHistory.last()?.value > 15.0) {
        probability += 15; reasoning << "Sudden wind gust/speed increase detected"
    }
    
    // Cap limits
    if (probability < 0) probability = 0
    if (probability > 100) probability = 100
    
    // Absolute Override
    if (r > 0) {
        probability = 100
        reasoning << "Active physical precipitation detected"
    }
    
    if (probability == 0 && r == 0) reasoning << "Conditions are stable and dry."
    
    state.rainProbability = probability
    
    // --- Determine Active Weather State ---
    def targetState = "Clear"
    def threshold = heavyRainThreshold ?: 0.1
    
    if (r >= threshold) {
        targetState = "Raining"
        reasoning << "Rain Rate (${r}) meets Heavy Rain threshold."
    } else if (r > 0) {
        targetState = "Sprinkling"
        reasoning << "Rain Rate (${r}) indicates Sprinkling."
    } else if (probability >= 90 && dpSpread <= 1.5) {
        targetState = "Sprinkling"
        reasoning << "Predictive Active: Total saturation and pressure drop indicate mist/drizzle before bucket tip."
    }
    
    // --- Expected Clear Time Logic ---
    if (targetState != "Clear") {
        if (pTrendData.rate > 0.02 || vpd > 0.4 || dpSpread > 4.0) {
            state.expectedClearTime = "~15-30 mins (Trends improving rapidly)"
        } else if (pTrendData.rate < -0.01 || dpSpread < 1.0) {
            state.expectedClearTime = "1+ Hour (Conditions worsening/stagnant)"
        } else {
            state.expectedClearTime = "~45 mins (Stable rain profile)"
        }
    } else {
        state.expectedClearTime = "Already Clear"
    }

    state.logicReasoning = reasoning.join(" | ")

    // --- State Transition & Debounce Logic ---
    def currentState = state.weatherState
    def debounceMs = (debounceMins ?: 5) * 60000
    def timeSinceChange = now() - (state.lastStateChange ?: 0)
    
    def allowTransition = false
    
    if (currentState != targetState) {
        // Upgrade logic (Instant)
        if (currentState == "Clear" && (targetState == "Sprinkling" || targetState == "Raining")) { allowTransition = true }
        else if (currentState == "Sprinkling" && targetState == "Raining") { allowTransition = true }
        // Downgrade logic (Debounced)
        else if (timeSinceChange >= debounceMs) { allowTransition = true }
        else {
            state.logicReasoning += " [Downgrade to ${targetState} delayed by Debounce timer: ${Math.ceil((debounceMs - timeSinceChange)/60000)}m remaining]"
        }
    }
    
    if (allowTransition) {
        logAction("State changed from ${currentState} to ${targetState}.")
        state.weatherState = targetState
        state.lastStateChange = now()
        
        if (targetState == "Raining") {
            safeOff(switchSprinkling)
            safeOn(switchRaining)
            if (notifyOnRain) sendNotification("Weather Update: Heavy Rain detected. Probability: ${probability}%")
        } 
        else if (targetState == "Sprinkling") {
            safeOff(switchRaining)
            safeOn(switchSprinkling)
            if (notifyOnSprinkle) sendNotification("Weather Update: Sprinkling detected. Probability: ${probability}%")
        } 
        else if (targetState == "Clear") {
            safeOff(switchRaining)
            safeOff(switchSprinkling)
            if (notifyOnClear) sendNotification("Weather Update: Conditions have cleared.")
        }
    }
    
    // --- Probability Setpoint Notification ---
    def probThreshold = notifyProbThreshold ?: 75
    if (probability >= probThreshold && !state.notifiedProb) {
        logAction("Probability threshold (${probThreshold}%) reached.")
        sendNotification("Weather Alert: Rain probability has reached ${probability}%.")
        state.notifiedProb = true
    } else if (probability < (probThreshold - 15) && state.notifiedProb) {
        state.notifiedProb = false
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

def sendNotification(msg) {
    if (notifyDevices) {
        notifyDevices.each { it.deviceNotification(msg) }
        logAction("Notification Sent: ${msg}")
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

def logDebug(msg) {
    if (debugEnable) log.debug "${app.label}: ${msg}"
}
