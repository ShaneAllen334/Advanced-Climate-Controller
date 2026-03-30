/**
 * Advanced Rain Detection
 */

definition(
    name: "Advanced Rain Detection",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Multi-sensor weather logic engine calculating VPD, Wet-Bulb, Dew Point Convergence, Pressure Trends, Synergistic Algorithms, and Evaporation to predict and track precipitation.",
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
            if (app.id) {
                input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            }
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Analyzes real-time environmental thermodynamics (VPD, Wet-Bulb, Spread Convergence Velocity, Synergy Multipliers) to predict precipitation with near-perfect accuracy.</div>"
            
            if (sensorTemp && sensorHum && sensorPress) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Current Environment</th><th style='padding: 8px;'>Calculated Metrics & Trends</th><th style='padding: 8px;'>System State & Logic</th></tr>"

                // Fetch raw data using multi-attribute fallback
                def tP = getFloat(sensorTemp, ["temperature", "tempf"])
                def hP = getFloat(sensorHum, ["humidity"])
                def pP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
                
                def t = tP ?: 0.0
                def h = hP ?: 0.0
                def p = pP ?: 0.0
                def redundancyActive = false
                
                if (settings.enableRedundancy != false) {
                    def tB = getFloat(sensorTempBackup, ["temperature", "tempf"])
                    def hB = getFloat(sensorHumBackup, ["humidity"])
                    def pB = getFloat(sensorPressBackup, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
                    
                    if (tP != null && tB != null) t = (tP + tB) / 2.0
                    else if (tP == null && tB != null) { t = tB; redundancyActive = true }
                    
                    if (hP != null && hB != null) h = (hP + hB) / 2.0
                    else if (hP == null && hB != null) { h = hB; redundancyActive = true }
                    
                    if (pP != null && pB != null) p = (pP + pB) / 2.0
                    else if (pP == null && pB != null) { p = pB; redundancyActive = true }
                }

                // Thermal Smoothing Override
                if (settings.enableThermalSmoothing != false && state.smoothedTemp != null) {
                    t = state.smoothedTemp
                }

                def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
                def lux = getFloat(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "N/A")
                def wind = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], "N/A")
                def windDir = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], "N/A")
                
                def strikes = state.lightningHistory?.size() ?: 0
                def recentLightDist = 999.0
                if (strikes > 0) {
                    state.lightningHistory.each { if (it.value < recentLightDist) recentLightDist = it.value }
                }
                def recentLightDistStr = strikes > 0 ? recentLightDist : "N/A"
                
                def rainDay = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
                def rainWeek = getFloat(sensorRainWeekly, ["rainWeekly", "weeklyrainin", "weeklyWater"], 0.0)
                def leakWet = sensorLeak ? (sensorLeak.currentValue("water") == "wet") : false
                
                // Fetch calculated data
                def vpd = state.currentVPD ?: 0.0
                def dp = state.currentDewPoint ?: 0.0
                def wb = state.currentWetBulb ?: 0.0
                def dpSpread = state.dewPointSpread ?: 0.0
                def pTrend = state.pressureTrendStr ?: "Stable"
                def tTrend = state.tempTrendStr ?: "Stable"
                def sTrend = state.spreadTrendStr ?: "Stable"
                def luxTrend = state.luxTrendStr ?: "N/A"
                def windTrend = state.windTrendStr ?: "N/A"
                def dryingRate = state.dryingPotential ?: "N/A"
                def ttd = state.timeToDryStr ?: "N/A"
                def isStale = state.isStale ?: false
                
                def prob = state.rainProbability ?: 0
                def confScore = state.confidenceScore ?: 0
                def confReason = state.confidenceReasoning ?: "Gathering logic consensus..."
                def activeState = state.weatherState ?: "Clear"
                def clearTime = state.expectedClearTime ?: "N/A"
                def reasoning = state.logicReasoning ?: "Waiting for initial sensor readings..."

                // Formatting
                def envDisplay = "<b>Temp:</b> ${String.format('%.1f', t)}°<br><b>Humidity:</b> ${String.format('%.1f', h)}%<br><b>Pressure:</b> ${String.format('%.2f', p)}<br><b>Rain Rate:</b> ${r}/hr"
                if (sensorLux) envDisplay += "<br><b>Solar/Lux:</b> ${lux}"
                if (sensorWind) envDisplay += "<br><b>Wind:</b> ${wind} mph"
                if (sensorWindDir) envDisplay += " @ ${windDir}°"
                if (sensorLightning && strikes > 0) envDisplay += "<br><b>Lightning:</b> ${strikes} strikes (Closest: ${recentLightDistStr} mi)"
                else if (sensorLightning) envDisplay += "<br><b>Lightning:</b> None recent"
                
                def leakWetStr = "DRY"
                if (sensorLeak) {
                    if (state.dewRejectionActive) leakWetStr = "<span style='color:orange; font-weight:bold;'>DEW/IGNORED</span>"
                    else if (leakWet) leakWetStr = "<span style='color:blue; font-weight:bold;'>WET</span>"
                    else leakWetStr = "DRY"
                }
                if (sensorLeak) envDisplay += "<br><b>Drop Sensor:</b> ${leakWetStr}"
                
                def vpdColor = vpd < 0.5 ? "red" : (vpd < 1.0 ? "orange" : "green")
                def spreadColor = dpSpread < 3.0 ? "red" : (dpSpread < 6.0 ? "orange" : "green")
                def calcDisplay = "<b>VPD:</b> <span style='color:${vpdColor};'>${String.format('%.2f', vpd)} kPa</span><br>"
                calcDisplay += "<b>Wet-Bulb:</b> ${String.format('%.1f', wb)}°<br>"
                calcDisplay += "<b>Dew Point:</b> ${String.format('%.1f', dp)}° <span style='color:${spreadColor}; font-size:11px;'>(Spread: ${String.format('%.1f', dpSpread)}°)</span><br>"
                calcDisplay += "<b>Drying Rate:</b> ${dryingRate}<br>"
                if (settings.enableTimeToDry != false) calcDisplay += "<b>Est. Dry Time:</b> ${ttd}<br>"
                
                calcDisplay += "<br><span style='font-size:11px;'><b>P-Trend:</b> ${pTrend}<br><b>T-Trend:</b> ${tTrend}<br><b>Spread Vel:</b> ${sTrend}"
  
                if (sensorLux) calcDisplay += "<br><b>L-Trend:</b> ${luxTrend}"
                if (sensorWind) calcDisplay += "<br><b>W-Trend:</b> ${windTrend}"
                if (sensorWindDir) calcDisplay += "<br><b>Dir-Shift:</b> ${state.windShiftDetected ? "<span style='color:red;'>Active Front</span>" : "Stable"}"
                calcDisplay += "</span>"
                
                // Active Algorithms List
                def algos = []
                if (settings.enableDPLogic != false) algos << "Convergence"
                if (settings.enableVPDLogic != false) algos << "VPD"
                if (settings.enablePressureLogic != false) algos << "Pressure"
                if (settings.enableWetBulbLogic != false) algos << "Wet-Bulb"
                if (settings.enableSynergyLogic != false) algos << "Synergy"
                if (settings.enableCloudLogic != false) algos << "Clouds"
                if (settings.enableWindLogic != false) algos << "Wind"
                if (settings.enableWindShiftLogic != false && sensorWindDir) algos << "Shift"
                if (settings.enableLightningLogic != false && sensorLightning) algos << "Lightning"
                if (settings.enableThermalSmoothing != false) algos << "Smoothing"
                if (settings.enableRedundancy != false && (sensorTempBackup || sensorHumBackup || sensorPressBackup)) algos << "Redundancy"
                
                calcDisplay += "<br><br><span style='font-size:10px; color:#555;'><b>Active Models:</b> ${algos.join(", ")}</span>"
                
                def probColor = prob > 70 ? "red" : (prob > 40 ? "orange" : "black")
                def confColor = confScore < 50 ? "red" : (confScore < 80 ? "orange" : "green")
                def stateColor = isStale ? "red" : (activeState == "Clear" ? "green" : "blue")
                def displayState = isStale ? "OFFLINE ⚠" : activeState.toUpperCase()
                
                def stateDisplay = "<b>State: <span style='color:${stateColor};'>${displayState}</span></b><br>"
                if (!isStale) {
                    stateDisplay += "<b>Rain Chance:</b> <span style='color:${probColor}; font-weight:bold;'>${prob}%</span><br>"
                    stateDisplay += "<b>Confidence: <span style='color:${confColor};'>${confScore}%</span></b><br>"
                    stateDisplay += "<b>Est. Clear:</b> ${clearTime}<br><br>"
                }
                stateDisplay += "<span style='font-size:11px; color:#555;'><i>${reasoning}</i><br><br><span style='color:#333;'><b>Confidence Reasoning:</b> ${confReason}</span></span>"

                statusText += "<tr><td style='padding: 8px; vertical-align:top; border-right:1px solid #ddd;'>${envDisplay}</td><td style='padding: 8px; vertical-align:top; border-right:1px solid #ddd;'>${calcDisplay}</td><td style='padding: 8px; vertical-align:top;'>${stateDisplay}</td></tr>"
                
                // --- Rainfall History, Graph & Record Banner ---
                def recordInfo = state.recordRain ?: [date: "None", amount: 0.0]
                def sevenDayList = state.sevenDayRain ?: []
               
                def historyDisplay = "<div><b>🏆 All-Time Record:</b> <span style='color:blue; font-weight:bold; font-size: 15px;'>${recordInfo.amount}</span> <i style='color:#555;'>(${recordInfo.date})</i></div>"
                historyDisplay += "<div style='margin-top:5px;'><b>Current Week:</b> ${rainWeek} | <b>Today's Total:</b> ${state.currentDayRain ?: 0.0}</div>"
                
                if (sevenDayList.size() > 0) {
                    def maxRain = 0.5 
                    sevenDayList.each { if (it.amount > maxRain) maxRain = it.amount }
                    
                    historyDisplay += "<div style='margin-top:15px; font-weight:bold;'>7-Day History:</div>"
                    historyDisplay += "<div style='display:flex; align-items:flex-end; height:100px; gap:8px; margin-top:5px; border-bottom:2px solid #aaa; padding-bottom:2px;'>"
                    
                    sevenDayList.reverse().each { item ->
                        def barHeight = (item.amount / maxRain) * 80
                        if (item.amount > 0 && barHeight < 2) barHeight = 2
                        
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
                def probSw = switchProbable?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Virtual Switches:</b> Probable Threat: [${probSw}] | Sprinkling: [${sprinkSw}] | Heavy Rain: [${rainSw}]</div>"
                statusText += "</div>"

                paragraph statusText
            } else {
                paragraph "<i>Primary sensors missing. Click configuration below to assign weather devices.</i>"
            }
        }

        section("<b>System Configuration</b>") {
            href(name: "configPageLink", page: "configPage", title: "▶ Configure Sensors, Logic & Switches", description: "Set up Weather Station sensors, tune predictive logic algorithms, and map outputs.")
        }
        
        section("<b>Child Device Integration</b>") {
            paragraph "<i>Create a single virtual device that exposes all advanced meteorological data and states calculated by this app to your dashboards or rules.</i>"
            if (app.id) {
                input "createDeviceBtn", "button", title: "➕ Create Rain Detector Information Device"
            } else {
                paragraph "<b>⚠ Please click 'Done' to fully install the app before creating the child device.</b>"
            }
            if (getChildDevice("RainDet-${app.id}")) {
                paragraph "<span style='color:green; font-weight:bold;'>✅ Child Device is Active.</span>"
                
                input "enableSmartSync", "bool", title: "Enable Smart Sync (Instantly pushes updates when data changes)", defaultValue: true, submitOnChange: true
                input "enableChildSync", "bool", title: "Enable Scheduled Data Sync (Routine heartbeat backup)", defaultValue: true, submitOnChange: true
                if (enableChildSync != false) {
                    input "childSyncInterval", "enum", title: "Scheduled Sync Interval (Minutes)", options: ["5", "10", "15", "30", "60"], defaultValue: "15", required: true, submitOnChange: true
                }
            }
        }
        
        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                paragraph "<i>Manual controls to force evaluations, reset records, or clear stuck states. Use with caution.</i>"
                input "forceEvalBtn", "button", title: "⚙️ Force Logic Evaluation"
                input "resetRecordBtn", "button", title: "🗑️ Reset All-Time Rain Record"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State & History"
            }
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
        
        section("<b>Primary Environment Sensors (Required)</b>", hideable: true, hidden: true) {
            paragraph "<i>Select the core sensors required for basic weather state and thermodynamic calculations. Temperature, Humidity, and Pressure form the baseline of the prediction engine.</i>"
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor", required: true
            input "sensorPress", "capability.sensor", title: "Barometric Pressure Sensor", required: true
        }

        section("<b>Redundancy & Backup Sensors (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<i>Select secondary sensors to automatically average data or failover if your primary weather station drops offline.</i>"
            input "sensorTempBackup", "capability.sensor", title: "Backup Temperature Sensor", required: false
            input "sensorHumBackup", "capability.sensor", title: "Backup Humidity Sensor", required: false
            input "sensorPressBackup", "capability.sensor", title: "Backup Barometric Pressure Sensor", required: false
        }
        
        section("<b>Algorithm Tuning & Toggles</b>", hideable: true, hidden: true) {
            paragraph "<i>Enable or disable specific mathematical models to fine-tune the engine's sensitivity to your specific microclimate.</i>"
            
            input "enableDPLogic", "bool", title: "Dew Point Convergence Velocity", defaultValue: true, description: "Monitors the gap between air temp and dew point. Rapidly closing spreads indicate imminent atmospheric saturation and rain."
            input "enableVPDLogic", "bool", title: "VPD (Vapor Pressure Deficit)", defaultValue: true, description: "Calculates the drying power of the air. Extremely low VPD means the air cannot hold more moisture, leading to precipitation."
            input "enableWetBulbLogic", "bool", title: "Wet-Bulb Cooling (Rain Shafts)", defaultValue: true, description: "Detects sudden temperature drops toward the Wet-Bulb point, indicating rain is physically falling through the air column above your sensors."
            input "enablePressureLogic", "bool", title: "Barometric Pressure Trends", defaultValue: true, description: "Tracks rapid drops in barometric pressure, a classic indicator of approaching storm fronts and low-pressure systems."
            input "enableSynergyLogic", "bool", title: "Algorithmic Synergy (Multipliers)", defaultValue: true, description: "Multiplies rain probability when multiple critical events (e.g., rapid pressure drop + wind shift) happen simultaneously. Highly recommended."
            input "enableCloudLogic", "bool", title: "Cloud Cover / Solar Drop Logic", defaultValue: true, description: "Monitors sudden plummets in solar radiation (lux) to detect thick cloud cover moving in before rain starts."
            input "enableWindLogic", "bool", title: "Wind Gust Fronts", defaultValue: true, description: "Detects sudden spikes in wind speed, often associated with gust fronts leading a thunderstorm."
            input "enableWindShiftLogic", "bool", title: "Wind Direction Shift Logic", defaultValue: true, description: "Tracks sharp changes in wind direction (45°+), which strongly correlates with frontal passages and squall lines."
            input "enableLightningLogic", "bool", title: "Lightning Proximity Logic", defaultValue: false, description: "Uses lightning strike distance and frequency to predict approaching storms."
            
            input "enableThermalSmoothing", "bool", title: "Thermal Smoothing (Sun-Spike Protection)", defaultValue: true, description: "Applies an Exponentially Weighted Moving Average (EWMA) filter to temperature. Prevents the app from panicking if the sun hits your sensor and artificially spikes the temp."
            input "enableTimeToDry", "bool", title: "Time-to-Dry Estimator", defaultValue: true, description: "Divides daily accumulated rainfall by real-time evapotranspiration physics to generate a live countdown of when the ground will be dry."
            input "enableRedundancy", "bool", title: "Sensor Redundancy & Failover", defaultValue: true, description: "Averages primary and backup sensors, or instantly fails-over to the backup if your primary sensor goes offline."
            input "enableDewRejection", "bool", title: "Dew & Frost Rejection", defaultValue: true, description: "Ignores the instant leak sensor on cold/calm mornings to prevent false 'Sprinkling' states from morning dew."
            input "enableStaleCheck", "bool", title: "Stale Data Protection", defaultValue: true, description: "Flags the system offline and clears active states if sensor data stops updating."
            input "staleDataTimeout", "number", title: "Stale Data Timeout (Minutes)", defaultValue: 30
        }
        
        section("<b>Instant 'First Drop' Sensor</b>", hideable: true, hidden: true) {
            paragraph "<i>Map a standard Z-Wave/Zigbee leak sensor placed outside to bypass tipping-bucket delays. Provides an instant 'Sprinkling' state the moment rain begins.</i>"
            input "sensorLeak", "capability.waterSensor", title: "Instant Rain Sensor (e.g., exposed leak sensor)", required: false
        }

        section("<b>Advanced Prediction Sensors (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<i>Add Solar Radiation, Wind Speed, Wind Direction, and Lightning sensors to unlock advanced storm front detection, increasing prediction accuracy.</i>"
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor (Detects incoming cloud fronts)", required: false
            input "sensorWind", "capability.sensor", title: "Wind Speed Sensor (Detects storm gust fronts)", required: false
            input "sensorWindDir", "capability.sensor", title: "Wind Direction Sensor (Detects frontal passages)", required: false
            input "sensorLightning", "capability.sensor", title: "Lightning Detector", required: false
            if (sensorLightning) {
                input "lightningStrikeThreshold", "number", title: "Minimum Lightning Strikes", defaultValue: 3, description: "Wait for this many strikes within 30 minutes before increasing probability."
            }
        }

        section("<b>Local Polling Override</b>", hideable: true, hidden: true) {
            paragraph "<i>Force a refresh command to your sensors at a set interval. <b>WARNING:</b> Only use this for local LAN devices. Cloud APIs will rate-limit or ban you for polling too fast.</i>"
            input "enablePolling", "bool", title: "Enable Active Device Polling", defaultValue: false, submitOnChange: true
            if (enablePolling) {
                input "pollInterval", "number", title: "Polling Interval (Minutes: 1-59)", required: true, defaultValue: 1
            }
        }

        section("<b>Precipitation & Accumulation Sensors (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<i>Select your physical rain gauges to track daily and weekly accumulation, and to provide hard confirmation when predicting rain.</i>"
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor (in/hr or mm/hr)", required: false
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: false
            input "sensorRainWeekly", "capability.sensor", title: "Weekly Rain Accumulation Sensor", required: false
        }
        
        section("<b>Virtual Output Switches</b>", hideable: true, hidden: true) {
            paragraph "<i>Map the virtual switches the application will turn on/off based on the current weather state. 'Debounce' prevents the switches from rapid-cycling during variable weather.</i>"
            input "switchProbable", "capability.switch", title: "Rain Probable Switch (Turns ON when probability reaches setpoint)", required: false
            input "switchSprinkling", "capability.switch", title: "Sprinkling / Light Rain Switch (Mutually Exclusive)", required: false
            input "switchRaining", "capability.switch", title: "Heavy Rain Switch (Mutually Exclusive)", required: false
            
            input "debounceMins", "number", title: "State Debounce Time (Minutes)", required: true, defaultValue: 5, description: "Prevents rapidly flipping back and forth between states. Upgrading to worse weather is instant; downgrading or clearing will wait this long."
            input "heavyRainThreshold", "decimal", title: "Heavy Rain Rate Threshold (in/hr or mm/hr)", required: true, defaultValue: 0.1
        }
        
        section("<b>Notifications & Setpoints</b>", hideable: true, hidden: true) {
            paragraph "<i>Configure which devices receive alerts and the specific probability thresholds that trigger them.</i>"
            input "notifyDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
            input "notifyProbThreshold", "number", title: "Rain Probability Setpoint (%)", required: true, defaultValue: 75, description: "Turns on the 'Rain Probable' switch and sends a notification when calculated probability hits this threshold."
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
    if (!state.lastHeartbeat) state.lastHeartbeat = now()
    if (!state.lastChildPayload) state.lastChildPayload = ""
    if (!state.confidenceScore) state.confidenceScore = 0
    if (!state.confidenceReasoning) state.confidenceReasoning = "Initializing..."
    if (!state.smoothedTemp) state.smoothedTemp = null
    
    // Initialize History Maps
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.luxHistory) state.luxHistory = []
    if (!state.windHistory) state.windHistory = []
    if (!state.windDirHistory) state.windDirHistory = []
    if (!state.spreadHistory) state.spreadHistory = []
    if (!state.lightningHistory) state.lightningHistory = []
   
    // Initialize Accumulation Tracking
    if (!state.sevenDayRain) state.sevenDayRain = []
    if (!state.recordRain) state.recordRain = [date: "None", amount: 0.0]
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    // Multi-Attribute Subscriptions
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "tempHandler")
    subscribeMulti(sensorHum, ["humidity"], "stdHandler")
    subscribeMulti(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    
    // Backup Subscriptions
    if (settings.enableRedundancy != false) {
        subscribeMulti(sensorTempBackup, ["temperature", "tempf"], "tempHandler")
        subscribeMulti(sensorHumBackup, ["humidity"], "stdHandler")
        subscribeMulti(sensorPressBackup, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    }

    subscribeMulti(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "luxHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "windHandler")
    subscribeMulti(sensorWindDir, ["windDirection", "winddir", "windDir"], "windDirHandler")
    subscribeMulti(sensorLightning, ["lightningDistance", "distance"], "lightningHandler")
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], "stdHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "stdHandler")
    if (sensorLeak) subscribe(sensorLeak, "water", "stdHandler")
    
    // Polling Scheduler
    unschedule("pollSensors")
    if (enablePolling && pollInterval) {
        def safeInterval = pollInterval.toInteger()
        if (safeInterval < 1) safeInterval = 1
        if (safeInterval > 59) safeInterval = 59
        schedule("0 */${safeInterval} * ? * *", "pollSensors")
        logAction("Active polling scheduled every ${safeInterval} minutes.")
    }
    
    runEvery5Minutes("evaluateWeather")
    scheduleChildSync()
    
    logAction("Advanced Rain Detection Initialized.")
    evaluateWeather()
}

def scheduleChildSync() {
    unschedule("updateChildDevice")
    if (getChildDevice("RainDet-${app.id}") && settings.enableChildSync != false) {
        def interval = settings.childSyncInterval ? settings.childSyncInterval.toInteger() : 15
        switch(interval) {
            case 5: runEvery5Minutes("updateChildDevice"); break;
            case 10: runEvery10Minutes("updateChildDevice"); break;
            case 15: runEvery15Minutes("updateChildDevice"); break;
            case 30: runEvery30Minutes("updateChildDevice"); break;
            case 60: runEvery1Hour("updateChildDevice"); break;
            default: runEvery15Minutes("updateChildDevice"); break;
        }
        logAction("Child device periodic sync scheduled every ${interval} minutes.")
    }
}

def subscribeMulti(device, attrs, handler) {
    if (!device) return
    attrs.each { attr -> subscribe(device, attr, handler) }
}

def getFloat(device, attrs, fallbackStr = null) {
    if (!device) return fallbackStr
    for (attr in attrs) {
        def val = device.currentValue(attr)
        if (val != null) {
            try { return val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch (e) {}
        }
    }
    return fallbackStr
}

def pollSensors() {
    [sensorTemp, sensorHum, sensorPress, sensorTempBackup, sensorHumBackup, sensorPressBackup, sensorRain, sensorLux, sensorWind, sensorWindDir, sensorLightning].each { dev ->
        if (dev && dev.hasCommand("refresh")) { try { dev.refresh() } catch (e) {} }
    }
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") return
    if (btn == "createDeviceBtn") { createChildDevice(); return }
    if (btn == "forceEvalBtn") { logAction("MANUAL OVERRIDE: Forcing logic evaluation."); evaluateWeather() }
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
        state.windDirHistory = []
        state.spreadHistory = []
        state.lightningHistory = []
        state.sevenDayRain = []
        state.recordRain = [date: "None", amount: 0.0]
        state.currentDayRain = 0.0
        state.notifiedProb = false
        state.confidenceScore = 0
        state.confidenceReasoning = "System reset."
        state.smoothedTemp = null
     
        safeOff(switchSprinkling)
        safeOff(switchRaining)
        safeOff(switchProbable)
        evaluateWeather()
    }
}

def createChildDevice() {
    def deviceId = "RainDet-${app.id}"
    if (!getChildDevice(deviceId)) {
        try {
            addChildDevice("ShaneAllen", "Advanced Rain Detector Information Device", deviceId, null, [name: "Advanced Rain Detector Information Device", label: "Advanced Rain Detector Information Device", isComponent: false])
            logAction("Child device successfully created.")
            scheduleChildSync()
            updateChildDevice()
        } catch (e) { log.error "Failed to create child device. ${e}" }
    }
}

// === HISTORY & HEARTBEAT WRAPPERS ===
def markActive() { state.lastHeartbeat = now() }

def updateHistory(historyName, val, maxAgeMs) {
    markActive()
    if (val == null) return
    def cleanVal
    try { cleanVal = val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) { return }
    
    def hist = state."${historyName}" ?: []
    hist.add([time: now(), value: cleanVal])
    def cutoff = now() - maxAgeMs
    hist = hist.findAll { it.time >= cutoff }
    if (hist.size() > 60) hist = hist.drop(hist.size() - 60)
    state."${historyName}" = hist
}

def sensorHandler(evt) { stdHandler(evt) }
def stdHandler(evt) { markActive(); runIn(2, "evaluateWeather") }
def tempHandler(evt) { updateHistory("tempHistory", evt.value, 3600000); runIn(2, "evaluateWeather") }
def pressureHandler(evt) { updateHistory("pressureHistory", evt.value, 10800000); runIn(2, "evaluateWeather") }
def luxHandler(evt) { updateHistory("luxHistory", evt.value, 3600000); runIn(2, "evaluateWeather") }
def windHandler(evt) { updateHistory("windHistory", evt.value, 3600000); runIn(2, "evaluateWeather") }
def windDirHandler(evt) { updateHistory("windDirHistory", evt.value, 3600000); runIn(2, "evaluateWeather") }
def lightningHandler(evt) { updateHistory("lightningHistory", evt.value, 1800000); runIn(2, "evaluateWeather") }

// === METEOROLOGICAL CALCULATIONS ===
def calculateVPD(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def calculateDewPoint(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    return (dpC * (9.0 / 5.0)) + 32.0
}

def calculateWetBulb(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def twC = tC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331) + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035
    return (twC * (9.0 / 5.0)) + 32.0
}

def getTrendData(hist, minTimeHr) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    def oldest = hist.first()
    def newest = hist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    if (timeSpanHr < minTimeHr) return [rate: 0.0, diff: diff, str: "Stable (<${Math.round(minTimeHr*60)}m data)"]
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def getAngularDiff(angle1, angle2) {
    def diff = Math.abs(angle1 - angle2) % 360
    return diff > 180 ? 360 - diff : diff
}

def evaluateWeather() {
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.currentDateStr) state.currentDateStr = todayStr
    
    if (state.currentDateStr != todayStr) {
        def yesterdayTotal = state.currentDayRain ?: 0.0
        def hist = state.sevenDayRain ?: []
        hist.add(0, [date: state.currentDateStr, amount: yesterdayTotal])
        if (hist.size() > 7) hist = hist[0..6]
        state.sevenDayRain = hist
        
        def record = state.recordRain ?: [date: "None", amount: 0.0]
        if (yesterdayTotal > (record.amount ?: 0.0)) {
            state.recordRain = [date: state.currentDateStr, amount: yesterdayTotal]
            logAction("🏆 New All-Time Record Rainfall! ${yesterdayTotal} on ${state.currentDateStr}")
        }
        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
    
    def currentDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    if (currentDaily > (state.currentDayRain ?: 0.0)) {
       state.currentDayRain = currentDaily
    }

    if (!sensorTemp || !sensorHum || !sensorPress) return

    def staleMins = settings.staleDataTimeout ?: 30
    def isStale = (settings.enableStaleCheck != false) && ((now() - (state.lastHeartbeat ?: now())) > (staleMins * 60000))
    state.isStale = isStale
    
    def redundancyActive = false
    def tP = getFloat(sensorTemp, ["temperature", "tempf"])
    def hP = getFloat(sensorHum, ["humidity"])
    def pP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
    
    def t = tP
    def h = hP
    def p = pP
    
    // --- Sensor Redundancy Engine ---
    if (settings.enableRedundancy != false) {
        def tB = getFloat(sensorTempBackup, ["temperature", "tempf"])
        def hB = getFloat(sensorHumBackup, ["humidity"])
        def pB = getFloat(sensorPressBackup, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
        
        if (tP != null && tB != null) t = (tP + tB) / 2.0
        else if (tP == null && tB != null) { t = tB; redundancyActive = true }
        
        if (hP != null && hB != null) h = (hP + hB) / 2.0
        else if (hP == null && hB != null) { h = hB; redundancyActive = true }
        
        if (pP != null && pB != null) p = (pP + pB) / 2.0
        else if (pP == null && pB != null) { p = pB; redundancyActive = true }
    }
    
    // Failsafe zeroing if all sensors offline
    if (t == null) t = 0.0
    if (h == null) h = 0.0
    if (p == null) p = 0.0

    // --- Thermal Smoothing (Sun-Spike Protection) ---
    def smoothedAnomaly = false
    if (settings.enableThermalSmoothing != false) {
        def lastT = state.smoothedTemp != null ? state.smoothedTemp : t
        def delta = Math.abs(t - lastT)
        
        // If temperature jumped more than 3 degrees physically instantly, smooth it (30% EWMA)
        if (delta > 3.0 && state.tempHistory?.size() > 0) {
            t = lastT + ((t - lastT) * 0.3)
            smoothedAnomaly = true
        }
        state.smoothedTemp = t
    }

    def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
    def luxVal = getFloat(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], 0.0)
    def windVal = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def windDirVal = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
    def lightDist = getFloat(sensorLightning, ["lightningDistance", "distance"], 999.0)
    def strikeCount = state.lightningHistory?.size() ?: 0
    
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    
    def dp = calculateDewPoint(t, h)
    state.currentDewPoint = dp
    
    def wb = calculateWetBulb(t, h)
    state.currentWetBulb = wb
    
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    state.dewPointSpread = dpSpread
    
    updateHistory("spreadHistory", dpSpread, 3600000)
    
    def pTrendData = getTrendData(state.pressureHistory, 0.25)
    def tTrendData = getTrendData(state.tempHistory, 0.16)
    def sTrendData = getTrendData(state.spreadHistory, 0.16)
    def lTrendData = getTrendData(state.luxHistory, 0.16)
    def wTrendData = getTrendData(state.windHistory, 0.16)
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.spreadTrendStr = sTrendData.str
    state.luxTrendStr = sensorLux ? lTrendData.str : "N/A"
    state.windTrendStr = sensorWind ? wTrendData.str : "N/A"

    state.windShiftDetected = false
    if (sensorWindDir && settings.enableWindShiftLogic != false && state.windDirHistory && state.windDirHistory.size() > 5) {
        def oldestDir = state.windDirHistory.first().value
        def shift = getAngularDiff(oldestDir, windDirVal)
        if (shift >= 45.0) state.windShiftDetected = true
    }
    
    def leakWet = sensorLeak ? (sensorLeak.currentValue("water") == "wet") : false
    def dewRejectionActive = false
    
    if (leakWet && settings.enableDewRejection != false) {
        def checkLux = sensorLux ? (luxVal < 100) : true
        def checkWind = sensorWind ? (windVal < 3.0) : true
        if (checkLux && checkWind && dpSpread <= 3.0) {
            leakWet = false
            dewRejectionActive = true
        }
    }
    state.dewRejectionActive = dewRejectionActive
    
    def evapIndex = vpd
    if (sensorWind) evapIndex += (windVal * 0.03) 
    if (sensorLux) evapIndex += (luxVal / 80000.0)
    
    if (r > 0 || leakWet) state.dryingPotential = "<span style='color:blue;'>Raining (No Drying)</span>"
    else if (evapIndex < 0.3) state.dryingPotential = "<span style='color:red;'>Very Low (Ground stays wet)</span>"
    else if (evapIndex < 0.8) state.dryingPotential = "<span style='color:orange;'>Moderate (Slow drying)</span>"
    else if (evapIndex < 1.5) state.dryingPotential = "<span style='color:green;'>High (Good drying conditions)</span>"
    else state.dryingPotential = "<span style='color:#008800; font-weight:bold;'>Very High (Rapid evaporation)</span>"
    
    // --- Time-to-Dry Estimator ---
    def ttdStr = "Dry"
    if (settings.enableTimeToDry != false) {
        def totalRain = state.currentDayRain ?: 0.0
        if (r > 0 || leakWet) {
            ttdStr = "Raining..."
        } else if (totalRain > 0 && evapIndex > 0) {
            def evapPerHour = evapIndex * 0.02
            if (evapPerHour < 0.01) evapPerHour = 0.01
            def hours = totalRain / evapPerHour
            if (hours > 72) ttdStr = "> 3 Days"
            else if (hours < 0.5) ttdStr = "< 30 mins"
            else ttdStr = "~${String.format('%.1f', hours)} hrs"
        } else if (totalRain > 0) {
            ttdStr = "Stagnant (No Evaporation)"
        }
    }
    state.timeToDryStr = ttdStr

    // --- Advanced Predictor Logic & Synergy ---
    def probability = 0.0
    def reasoning = []
    def activeFactors = 0
    def activeFactorNames = []
    def totalModelsEnabled = 0
    
    if (!isStale) {
        if (redundancyActive) reasoning << "⚠ Primary Sensor Offline: Redundancy Failover Active"
        if (smoothedAnomaly) reasoning << "☼ Thermal Smoothing Active (Filtered Solar Spike)"

        if (settings.enableDPLogic != false) {
            totalModelsEnabled++
            if (dpSpread <= 1.5) { probability += 40; reasoning << "Critical: Dew Point spread near 0° (Air saturated)"; activeFactors++; activeFactorNames << "Dew Point" }
            else if (dpSpread <= 4.0) { probability += 20; reasoning << "Dew Point spread tightening (<4°)"; activeFactors++; activeFactorNames << "Dew Point" }
            if (sTrendData.rate <= -3.0) { probability += 30; reasoning << "Spread Velocity Convergence! Atmosphere saturating rapidly"; activeFactors++; activeFactorNames << "Squeeze Velocity" }
        }
        
        if (settings.enableVPDLogic != false) {
            totalModelsEnabled++
            if (vpd < 0.2) { probability += 20; reasoning << "VPD extremely low"; activeFactors++; activeFactorNames << "VPD" }
            else if (vpd > 1.0) { probability -= 20; reasoning << "VPD High (Dry air)" }
        }
        
        if (settings.enableWetBulbLogic != false) {
            totalModelsEnabled++
            if (tTrendData.rate <= -4.0 && (t - wb) <= 3.0) { probability += 40; reasoning << "Rain Shaft Detected! Temp crashing toward Wet-Bulb"; activeFactors++; activeFactorNames << "Wet-Bulb Cooling" }
        }
        
        if (settings.enablePressureLogic != false) {
            totalModelsEnabled++
            if (pTrendData.rate <= -0.04) { probability += 30; reasoning << "Pressure dropping rapidly"; activeFactors++; activeFactorNames << "Barometric" }
            else if (pTrendData.rate <= -0.02) { probability += 15; reasoning << "Pressure falling"; activeFactors++; activeFactorNames << "Barometric" }
            else if (pTrendData.rate > 0.03) { probability -= 30; reasoning << "Pressure rising strongly (Clearing)" }
        }
        
        if (settings.enableCloudLogic != false && sensorLux) {
            totalModelsEnabled++
            if (lTrendData.diff < 0) {
                def oldestLux = state.luxHistory.first()?.value ?: 0.0
                if (oldestLux > 2000) { 
                    def dropPercentage = Math.abs(lTrendData.diff) / oldestLux
                     if (dropPercentage >= 0.60) { probability += 20; reasoning << "Solar radiation plummeted >60% (Heavy cloud cover)"; activeFactors++; activeFactorNames << "Solar" }
                }
            }
        }
        
        if (settings.enableWindLogic != false && sensorWind) {
            totalModelsEnabled++
            if (wTrendData.diff >= 10.0 && state.windHistory.last()?.value > 15.0) {
                probability += 15; reasoning << "Sudden wind gust detected"; activeFactors++; activeFactorNames << "Wind Gust"
            }
        }

        if (settings.enableWindShiftLogic != false && sensorWindDir) {
            totalModelsEnabled++
            if (state.windShiftDetected) {
                probability += 20; reasoning << "Wind direction shift detected (Frontal Passage)"; activeFactors++; activeFactorNames << "Wind Shift"
            }
        }
        
        if (settings.enableLightningLogic != false && sensorLightning && lightDist != 999.0) {
            totalModelsEnabled++
            def reqStrikes = settings.lightningStrikeThreshold ?: 3
            if (strikeCount >= reqStrikes) {
                if (lightDist <= 10.0) { probability += 50; reasoning << "Critical: Lightning nearby (<= 10 miles)"; activeFactors++; activeFactorNames << "Lightning" }
                else if (lightDist <= 25.0) { probability += 25; reasoning << "Storms approaching (Lightning <= 25 miles)"; activeFactors++; activeFactorNames << "Lightning" }
            }
        }
        
        // SYNERGY MULTIPLIERS
        if (settings.enableSynergyLogic != false) {
            totalModelsEnabled++
            if (settings.enableDPLogic != false && settings.enablePressureLogic != false && sTrendData.rate <= -2.0 && pTrendData.rate <= -0.02) {
                probability *= 1.3
                reasoning << "SYNERGY: Squeeze Velocity + Barometric Drop (1.3x Multiplier)"
            }
            if (settings.enableWetBulbLogic != false && settings.enableWindShiftLogic != false && tTrendData.rate <= -3.0 && sensorWindDir && state.windShiftDetected) {
                probability *= 1.2
                reasoning << "SYNERGY: Temp Drop + Wind Shift (1.2x Multiplier)"
            }
        }

        probability = Math.round(probability)
        if (probability < 0) probability = 0
        if (probability > 100) probability = 100
        
        if (r > 0 || leakWet) {
            probability = 100
            if (leakWet) reasoning << "Instant 'First Drop' detected via Leak Sensor"
            if (r > 0) reasoning << "Active physical precipitation detected"
        }
        
        if (dewRejectionActive) reasoning << "Leak Sensor ignored (Morning Dew/Frost Detected)"
        if (probability == 0 && r == 0 && !leakWet) reasoning << "Conditions are stable and dry."
    } else {
        probability = 0
        reasoning << "⚠ Sensors Stale/Offline (No data received in ${staleMins} mins)"
    }
    
    state.rainProbability = Math.round(probability)
    
    // --- Confidence Engine ---
    def conf = 50 
    def confRes = ""
    
    if (sensorLux) conf += 5
    if (sensorWind) conf += 5
    if (sensorWindDir) conf += 5
    if (sensorLeak) conf += 5
    if (sensorRain) conf += 5
    
    def highAgreementThreshold = (totalModelsEnabled / 2).toInteger()
    if (highAgreementThreshold < 1) highAgreementThreshold = 1
    if (highAgreementThreshold > 3) highAgreementThreshold = 3
    
    if (isStale) {
        conf = 0
        confRes = "Zero confidence due to stale data."
    } else if (r > 0 || leakWet) {
        conf = 100
        if (leakWet && r <= 0) confRes = "100% Confirmed - Physical precipitation registered by instant Leak Sensor."
        else confRes = "100% Confirmed - Physical precipitation registered by Rain Gauge."
    } else {
        if (probability >= 60) {
            if (activeFactors >= highAgreementThreshold) {
                conf += 25
                confRes = "High agreement. Prediction driven by ${activeFactors} converging models (${activeFactorNames.unique().join(', ')})."
            } else if (activeFactors > 0) {
                conf += 5
                confRes = "Moderate agreement. High probability but relying on isolated factors (${activeFactorNames.unique().join(', ')})."
            }
        } else if (probability <= 20) {
            if (activeFactors == 0) {
                conf += 30
                confRes = "Strong agreement. All monitored metrics indicate stable, dry conditions."
            } else {
                conf += 10
                confRes = "Mostly stable. Low probability, but ${activeFactors} model (${activeFactorNames.unique().join(', ')}) shows minor fluctuations."
            }
        } else {
            conf += 10
            confRes = "Unsettled/Transitional environment. Conflicting or mild metrics preventing strong consensus."
        }
    }
    
    if (conf > 100) conf = 100
    state.confidenceScore = conf
    state.confidenceReasoning = confRes
    
    // --- Switches and State ---
    def probThreshold = notifyProbThreshold ?: 75
    if (probability >= probThreshold && !isStale) {
        safeOn(switchProbable)
        if (!state.notifiedProb) {
            logAction("Probability threshold (${probThreshold}%) reached.")
            if (notifyDevices) sendNotification("Weather Alert: Rain probability has reached ${Math.round(probability)}%.")
            state.notifiedProb = true
        }
    } else if (probability < (probThreshold - 15) || isStale) {
        safeOff(switchProbable)
        if (state.notifiedProb) {
            state.notifiedProb = false
        }
    }
    
    def targetState = "Clear"
    def threshold = heavyRainThreshold ?: 0.1
    
    if (!isStale) {
        if (r >= threshold) {
            targetState = "Raining"
            reasoning << "Rain Rate (${r}) meets Heavy Rain threshold."
        } else if (r > 0 || leakWet) {
            targetState = "Sprinkling"
            if (leakWet && r <= 0) reasoning << "Leak Sensor is WET (Instant detection)."
            else reasoning << "Rain Rate (${r}) indicates Sprinkling."
        } else if (probability >= 90 && dpSpread <= 1.5) {
            targetState = "Sprinkling"
            reasoning << "Predictive Active: Total saturation and pressure drop indicate mist/drizzle before bucket tip."
        }
    }
    
    if (isStale) {
        state.expectedClearTime = "Unknown (Sensors Offline)"
    } else if (targetState != "Clear") {
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

    def currentState = state.weatherState
    def debounceMs = (debounceMins ?: 5) * 60000
    def timeSinceChange = now() - (state.lastStateChange ?: 0)
    
    def allowTransition = false
    
    if (isStale && currentState != "Clear") {
        targetState = "Clear"
        allowTransition = true
    } else if (currentState != targetState) {
        if (currentState == "Clear" && (targetState == "Sprinkling" || targetState == "Raining")) { allowTransition = true }
        else if (currentState == "Sprinkling" && targetState == "Raining") { allowTransition = true }
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
            if (notifyOnRain && !isStale) sendNotification("Weather Update: Heavy Rain detected. Probability: ${Math.round(probability)}%")
        } 
        else if (targetState == "Sprinkling") {
            safeOff(switchRaining)
            safeOn(switchSprinkling)
            if (notifyOnSprinkle && !isStale) sendNotification("Weather Update: Sprinkling detected. Probability: ${Math.round(probability)}%")
        } 
        else if (targetState == "Clear") {
            safeOff(switchRaining)
            safeOff(switchSprinkling)
            if (notifyOnClear && !isStale) sendNotification("Weather Update: Conditions have cleared.")
        }
    }

    def currentPayload = [
        ws: state.weatherState,
        rp: state.rainProbability,
        cs: state.confidenceScore,
        ect: state.expectedClearTime,
        dp: state.dryingPotential,
        ttd: state.timeToDryStr,
        vpd: String.format("%.2f", state.currentVPD ?: 0.0),
        wb: String.format("%.1f", state.currentWetBulb ?: 0.0),
        dew: String.format("%.1f", state.currentDewPoint ?: 0.0),
        spread: String.format("%.1f", state.dewPointSpread ?: 0.0),
        pt: state.pressureTrendStr,
        tt: state.tempTrendStr,
        st: state.spreadTrendStr,
        lt: state.luxTrendStr,
        wt: state.windTrendStr,
        cdr: state.currentDayRain,
        rra: state.recordRain?.amount
    ].toString()

    def dataChanged = (state.lastChildPayload != currentPayload)
    if (dataChanged) {
        state.lastChildPayload = currentPayload
    }
    
    if (allowTransition || (dataChanged && settings.enableSmartSync != false)) {
        updateChildDevice()
    }
}

// === CHILD DEVICE SYNCHRONIZATION ===
def updateChildDevice() {
    def child = getChildDevice("RainDet-${app.id}")
    if (child) {
        child.sendEvent(name: "weatherState", value: state.weatherState)
        child.sendEvent(name: "rainProbability", value: state.rainProbability, unit: "%")
        child.sendEvent(name: "confidenceScore", value: state.confidenceScore, unit: "%")
        child.sendEvent(name: "expectedClearTime", value: state.expectedClearTime)
        
        child.sendEvent(name: "sprinkling", value: state.weatherState == "Sprinkling" ? "on" : "off")
        child.sendEvent(name: "raining", value: state.weatherState == "Raining" ? "on" : "off")
        
        def rawDrying = state.dryingPotential?.replaceAll("<[^>]*>", "") ?: "N/A"
        child.sendEvent(name: "dryingPotential", value: rawDrying)
        child.sendEvent(name: "timeToDry", value: state.timeToDryStr ?: "N/A")
        
        child.sendEvent(name: "vpd", value: String.format("%.2f", state.currentVPD ?: 0.0), unit: "kPa")
        child.sendEvent(name: "wetBulb", value: String.format("%.1f", state.currentWetBulb ?: 0.0), unit: "°")
        child.sendEvent(name: "dewPoint", value: String.format("%.1f", state.currentDewPoint ?: 0.0), unit: "°")
        child.sendEvent(name: "dewPointSpread", value: String.format("%.1f", state.dewPointSpread ?: 0.0), unit: "°")
        
        child.sendEvent(name: "pressureTrend", value: state.pressureTrendStr ?: "Stable")
        child.sendEvent(name: "tempTrend", value: state.tempTrendStr ?: "Stable")
        child.sendEvent(name: "spreadTrend", value: state.spreadTrendStr ?: "Stable")
        child.sendEvent(name: "luxTrend", value: state.luxTrendStr ?: "N/A")
        child.sendEvent(name: "windTrend", value: state.windTrendStr ?: "N/A")
        
        if (sensorWindDir) {
            def windDir = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
            child.sendEvent(name: "windDirection", value: windDir, unit: "°")
            child.sendEvent(name: "windShiftDetected", value: state.windShiftDetected ? "Active" : "Stable")
        }
        
        if (sensorLightning) {
            def strikes = state.lightningHistory?.size() ?: 0
            def closest = 999.0
            if (strikes > 0) {
                state.lightningHistory.each { if (it.value < closest) closest = it.value }
            }
            child.sendEvent(name: "lightningStrikeCount", value: strikes)
            child.sendEvent(name: "lightningClosestDistance", value: closest, unit: "mi")
        }
        
        child.sendEvent(name: "currentDayRain", value: state.currentDayRain ?: 0.0)
        def record = state.recordRain ?: [date: "None", amount: 0.0]
        child.sendEvent(name: "recordRainAmount", value: record.amount)
        child.sendEvent(name: "recordRainDate", value: record.date)
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
