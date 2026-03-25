/**
 * Advanced Lighting Circadian Rhythm
 */

definition(
    name: "Advanced Lighting Circadian Rhythm",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Circadian Rhythm engine. Calculates natural solar color temperature based on local sunrise/sunset and outputs to a Hub Variable. Features live diagnostics, Daytime Storm Compensation (Lux tracking), and manual overrides. This is free-use code.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Lighting Circadian Rhythm</b>", install: true, uninstall: true) {
        
        section("<b>Live Circadian Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of the sun's position, outdoor lux, and the exact color temperature (Kelvin) the engine is transmitting to your Hub Variable.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #f39c12;'>" +
                      "<b>Engine Status:</b> ${statusExplanation}</div>"

            // Gather Core Metrics
            def currentLux = luxSensor ? luxSensor.currentValue("illuminance") ?: "--" : "N/A"
            def currentCT = state.calculatedCT ?: "--"
            def overrideModeStr = overrideMode ?: "Normal"
            
            // Sun Position Math for Dashboard
            def sunData = getSunriseAndSunset()
            def riseTime = sunData.sunrise ? sunData.sunrise.format("h:mm a", location.timeZone) : "--"
            def setTime = sunData.sunset ? sunData.sunset.format("h:mm a", location.timeZone) : "--"
            
            def phase = "Nighttime"
            if (state.stormModeActive) {
                phase = "<span style='color:#d35400;'><b>Daytime Storm (Cozy Mode Active)</b></span>"
            } else if (sunData.sunrise && sunData.sunset) {
                def nowMs = now()
                def riseMs = sunData.sunrise.time
                def setMs = sunData.sunset.time
                def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
                
                if (nowMs >= riseMs && nowMs < solarNoonMs) phase = "Morning (Ramping Up)"
                else if (nowMs >= solarNoonMs && nowMs < setMs) phase = "Afternoon (Ramping Down)"
                else if (nowMs >= setMs) phase = "Post-Sunset (Locked Warm)"
                else phase = "Pre-Sunrise (Locked Warm)"
            }

            // Unified Dashboard HTML
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Lighting Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Calculated Color Temp</td><td class="dash-val" style="color:#e67e22; font-size: 16px;">${currentCT}K</td></tr>
                    <tr><td class="dash-hl">Outdoor Illuminance (Lux)</td><td class="dash-val">${currentLux}</td></tr>
                    <tr><td class="dash-hl">Active Override Mode</td><td class="dash-val">${overrideModeStr}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">Solar Positioning & Weather</td></tr>
                    <tr><td class="dash-hl">Current Solar Phase</td><td class="dash-val">${phase}</td></tr>
                    <tr><td class="dash-hl">Local Sunrise</td><td class="dash-val">${riseTime}</td></tr>
                    <tr><td class="dash-hl">Local Sunset</td><td class="dash-val">${setTime}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">System Connections</td></tr>
                    <tr><td class="dash-hl">Target Hub Variable</td><td class="dash-val">${ctVariable ?: "<span style='color:red;'>Not Configured</span>"}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>App Control & Master Kill Switch</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The master toggle for the application. If disabled, the app stops updating the Hub Variable completely, allowing you to manually control your lights without interference.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }

        section("<b>1. Manual Color Overrides</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Instantly locks the color temperature to a specific value, bypassing the sun logic entirely. Useful for tasks requiring bright white light at night, or forcing cozy lighting during a dark storm.</div>"
            input "overrideMode", "enum", title: "<b>Operating Mode</b>", options: ["Normal (Track Sun)", "Force Cool (6500K)", "Force Warm (2500K)"], required: true, defaultValue: "Normal (Track Sun)", submitOnChange: true
        }

        section("<b>2. Environmental Sensors (Storm Compensation)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connect an outdoor Lux (Illuminance) sensor. If it gets unusually dark during the middle of the day (like a heavy rainstorm), the app will temporarily drop the color temperature to a cozy, warm setting until the sun comes back out.</div>"
            input "luxSensor", "capability.illuminanceMeasurement", title: "Outdoor Lux Sensor (Optional)", required: false, submitOnChange: true
            
            if (luxSensor) {
                input "enableLuxOverride", "bool", title: "<b>Enable Daytime Storm Compensation</b>", defaultValue: false, submitOnChange: true
                if (enableLuxOverride) {
                    input "luxThreshold", "number", title: "Lux Threshold (Drop CT when Lux falls below this number)", required: false, defaultValue: 1000
                    input "luxTargetCT", "number", title: "Cozy Color Temp for Storms (Kelvin)", required: false, defaultValue: 3000
                }
            }
        }

        section("<b>3. Global Variable Output</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The app continuously calculates the perfect Kelvin value and injects it into this Hubitat Hub Variable. <b>Important:</b> You must create a 'Number' variable in your Hubitat Settings -> Hub Variables menu first!</div>"
            input "ctVariable", "text", title: "Exact Name of Hub Variable (e.g., 'HomeColorTemp')", required: true
        }

        section("<b>4. Circadian Rhythm Boundaries</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines the absolute floor and ceiling for your color temperature. Some smart bulbs cannot go below 2700K or above 6000K. Set these to match the physical limits of your bulbs.</div>"
            input "minCT", "number", title: "Minimum Warmth (Kelvin) - Used at Night", required: true, defaultValue: 2500
            input "maxCT", "number", title: "Maximum Coolness (Kelvin) - Used at Solar Noon", required: true, defaultValue: 6500
        }

        section("<b>5. Update Frequency</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> How often the app recalculates the sun's position and updates the Hub Variable. 15 minutes provides a smooth, unnoticeable transition throughout the day.</div>"
            input "updateInterval", "enum", title: "Calculation Interval", options: ["1":"Every 1 Minute", "5":"Every 5 Minutes", "15":"Every 15 Minutes", "30":"Every 30 Minutes"], required: false, defaultValue: "15"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.calculatedCT = null
    state.stormModeActive = false
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    if (luxSensor) subscribe(luxSensor, "illuminance", luxHandler)
    
    // Schedule the heartbeat sweep
    def interval = updateInterval ?: "15"
    if (interval == "1") runEvery1Minute(routineSweep)
    else if (interval == "5") runEvery5Minutes(routineSweep)
    else if (interval == "15") runEvery15Minutes(routineSweep)
    else if (interval == "30") runEvery30Minutes(routineSweep)
    
    logAction("Circadian Engine Initialized. Sun tracking active.")
    evaluateSystem()
}

def enableSwitchHandler(evt) { 
    if (evt.value == "off") {
        logAction("Circadian App Paused via Master Switch.")
        state.stormModeActive = false
    } else {
        evaluateSystem() 
    }
}

def luxHandler(evt) {
    if (txtEnable) log.info "${app.label}: Outdoor Lux updated to ${evt.value}"
    
    // If Lux override is enabled, evaluate instantly when brightness changes rapidly
    if (enableLuxOverride) {
        evaluateSystem()
    }
}

def routineSweep() {
    evaluateSystem()
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is suspended via the Master Switch."
    if (overrideMode == "Force Cool (6500K)") return "<span style='color:blue;'><b>Manual Override:</b></span> Locked to 6500K (Cool White)."
    if (overrideMode == "Force Warm (2500K)") return "<span style='color:#d35400;'><b>Manual Override:</b></span> Locked to 2500K (Warm/Orange)."
    if (state.stormModeActive) return "Tracking normally, but <span style='color:#d35400;'><b>Storm Compensation</b></span> has engaged to warm up the house due to low outdoor light."
    
    return "Tracking normally. Calculating color curve based on solar position."
}

def evaluateSystem() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (!ctVariable) return
    
    def targetCT = 2700 // Fallback default
    state.stormModeActive = false // Reset prior to logic check
    
    // 1. Handle Manual Overrides
    if (overrideMode == "Force Cool (6500K)") {
        targetCT = 6500
        if (txtEnable && state.calculatedCT != targetCT) logAction("Override Active: Forcing 6500K.")
    } 
    else if (overrideMode == "Force Warm (2500K)") {
        targetCT = 2500
        if (txtEnable && state.calculatedCT != targetCT) logAction("Override Active: Forcing 2500K.")
    } 
    // 2. Handle Normal Sun Tracking & Lux Override
    else {
        targetCT = calculateNaturalCT()
        
        // Storm Compensation Override Logic
        if (luxSensor && enableLuxOverride) {
            def currentLux = luxSensor.currentValue("illuminance")
            def luxThresh = luxThreshold ?: 1000
            
            if (currentLux != null && currentLux <= luxThresh) {
                def sunData = getSunriseAndSunset()
                def nowMs = now()
                
                // Only engage Storm Compensation if it is actually daytime
                if (sunData.sunrise && sunData.sunset && nowMs > sunData.sunrise.time && nowMs < sunData.sunset.time) {
                    def stormCT = luxTargetCT ?: 3000
                    
                    // We only want to override if the storm CT is *warmer* than what the sun curve expects right now
                    if (stormCT < targetCT) {
                        targetCT = stormCT
                        state.stormModeActive = true
                    }
                }
            }
        }
    }
    
    // 3. Push to Hub Variable
    if (state.calculatedCT != targetCT) {
        state.calculatedCT = targetCT
        def success = setGlobalVar(ctVariable, targetCT)
        if (success) {
            logAction("BMS Command -> Hub Variable '${ctVariable}' updated to ${targetCT}K" + (state.stormModeActive ? " (Storm Mode)" : ""))
        } else {
            logAction("ERROR: Hubitat rejected the update for '${ctVariable}'. Please ensure it is spelled exactly as it appears in Hub Variables and is set to a Number type.")
        }
    }
}

def calculateNaturalCT() {
    def sunData = getSunriseAndSunset()
    def minTemp = minCT ?: 2500
    def maxTemp = maxCT ?: 6500
    
    if (!sunData.sunrise || !sunData.sunset) {
        logInfo("Could not retrieve Hubitat solar data. Defaulting to max coolness.")
        return maxTemp
    }
    
    def nowMs = now()
    def riseMs = sunData.sunrise.time
    def setMs = sunData.sunset.time
    
    // Calculate Solar Noon (Midday)
    def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
    
    def calculatedTemp = minTemp
    
    if (nowMs < riseMs) {
        // Pre-Sunrise
        calculatedTemp = minTemp
    } 
    else if (nowMs >= riseMs && nowMs <= solarNoonMs) {
        // Morning to Midday: Ramp UP from minTemp to maxTemp
        def totalPhaseTime = solarNoonMs - riseMs
        def timePassed = nowMs - riseMs
        def percentage = timePassed / totalPhaseTime
        calculatedTemp = minTemp + ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > solarNoonMs && nowMs <= setMs) {
        // Midday to Sunset: Ramp DOWN from maxTemp to minTemp
        def totalPhaseTime = setMs - solarNoonMs
        def timePassed = nowMs - solarNoonMs
        def percentage = timePassed / totalPhaseTime
        calculatedTemp = maxTemp - ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > setMs) {
        // Post-Sunset
        calculatedTemp = minTemp
    }
    
    // Round to nearest 50 Kelvin for clean hardware commands
    return (Math.round(calculatedTemp / 50.0) * 50).toInteger()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
