/**
 * Advanced Climate Controller
 */

definition(
    name: "Advanced Climate Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade BMS app with Live Diagnostics, Maintenance Tracking, Service Contacts, ROI Savings, Auto-Swap, Free Cooling Economizer, Failsafes, and Cycle Tracking.",
    category: "Comfort",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Climate Controller</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, top-down view of your entire HVAC system, including active setpoints, dynamic averages, and the current logic state of the BMS engine.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            if (thermostat) {
                // Gather Core Metrics
                def tstatTemp = thermostat.currentValue("temperature") ?: "--"
                def tstatHum = thermostat.currentValue("humidity") ?: "--"
                def tstatCool = thermostat.currentValue("coolingSetpoint") ?: "--"
                def tstatHeat = thermostat.currentValue("heatingSetpoint") ?: "--"
                def tstatMode = thermostat.currentValue("thermostatMode")?.toUpperCase() ?: "UNKNOWN"
                def tstatState = thermostat.currentValue("thermostatOperatingState")?.toUpperCase() ?: "IDLE"
                
                def stateColor = "black"
                if (tstatState == "COOLING") stateColor = "blue"
                if (tstatState == "HEATING") stateColor = "#d9534f" 
                if (tstatState.contains("AUX") || tstatState.contains("EMERGENCY")) stateColor = "red" 
                
                def avgTemp = getAverageTemp()
                def avgHum = getAverageHumidity()
                
                // Gather Diagnostics
                def currentLocMode = location.mode ?: "Unknown"
                def fcRemaining = state.freeCoolState == "pending" && state.freeCoolTargetTime ? Math.max(0, Math.round((state.freeCoolTargetTime - now()) / 60000)) : 0
                def fcStatusStr = state.freeCoolState == "pending" ? "<span style='color:orange;'>Pending (${fcRemaining} mins remaining)</span>" : (state.freeCoolState == "active" ? "<span style='color:green;'>Active</span>" : state.freeCoolState.capitalize())
                
                def swapText = "N/A (Disabled)"
                if (enableAutoSwap && !(state.freeCoolState in ["pending", "active"])) {
                    def db = autoSwapDeadband ?: 1.0
                    def distToCool = tstatCool != "--" ? Math.round(( (tstatCool.toBigDecimal() + db) - avgTemp.toBigDecimal() ) * 10) / 10.0 : 0
                    def distToHeat = tstatHeat != "--" ? Math.round(( avgTemp.toBigDecimal() - (tstatHeat.toBigDecimal() - db) ) * 10) / 10.0 : 0
                    
                    if (tstatMode == "HEAT") swapText = "<span style='color:blue;'>↑ ${distToCool}° until Swap to COOL</span>"
                    else if (tstatMode == "COOL") swapText = "<span style='color:red;'>↓ ${distToHeat}° until Swap to HEAT</span>"
                    else swapText = "Thermostat not in Heat/Cool mode"
                }

                def deltaTStr = "N/A (Disabled or Missing Sensors)"
                if (enableDeltaT && returnSensor && dischargeSensor) {
                    def retT = returnSensor.currentValue("temperature")
                    def disT = dischargeSensor.currentValue("temperature")
                    if (retT != null && disT != null) {
                        def dT = 0.0
                        if (tstatState == "COOLING") dT = (retT - disT).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        else if (tstatState == "HEATING") dT = (disT - retT).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        else dT = Math.abs(retT - disT).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) 
                        
                        def health = ""
                        if (tstatState == "COOLING" && dT < (minCoolingDeltaT ?: 12.0)) health = " <span style='color:red;'>(Warning: Low)</span>"
                        else if (tstatState == "HEATING" && dT < (minHeatingDeltaT ?: 15.0)) health = " <span style='color:red;'>(Warning: Low)</span>"
                        else if (tstatState in ["COOLING", "HEATING"]) health = " <span style='color:green;'>(Good)</span>"
                        else health = " <span style='color:gray;'>(System Idle)</span>"
                        
                        deltaTStr = "${dT}°F (Return: ${retT}° | Supply: ${disT}°)${health}"
                    } else {
                        deltaTStr = "Waiting for sensor data..."
                    }
                }

                // NEW: Calculated Deadband Metric
                def currentDeadbandStr = "N/A"
                if (tstatCool != "--" && tstatHeat != "--") {
                    def gap = (tstatCool.toBigDecimal() - tstatHeat.toBigDecimal()).setScale(1, BigDecimal.ROUND_HALF_UP)
                    if (gap < 3.0) {
                        currentDeadbandStr = "<span style='color:red;'><b>${gap}° (Violation - Conflict Detected)</b></span>"
                    } else if (gap == 3.0) {
                        currentDeadbandStr = "<span style='color:orange;'><b>${gap}° (Minimum Enforced)</b></span>"
                    } else {
                        currentDeadbandStr = "<span style='color:green;'>${gap}° (Healthy Gap)</span>"
                    }
                }

                // Gather Maintenance
                def filterLifeStr = "Disabled"
                if (enableFilterTracker) {
                    def maxMins = (maxFilterHours ?: 300) * 60
                    def usedMins = state.filterRunMinutes ?: 0.0
                    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    filterLifeStr = "${percentLeft}%"
                    if (percentLeft < 10.0) filterLifeStr = "<span style='color:red; font-weight:bold;'>${percentLeft}% (Change Soon)</span>"
                }
                
                def hvacContactStr = "Not Configured"
                if (hvacCompanyName || hvacCompanyPhone) {
                    hvacContactStr = "${hvacCompanyName ?: 'N/A'} ${hvacCompanyPhone ? '(' + hvacCompanyPhone + ')' : ''}"
                }
                
                // Unified Dashboard HTML
                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Calculated Average (Rooms)</th><th>Thermostat Sensor</th><th>Target Setpoint</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Temperature</td><td><b>${avgTemp}°</b></td><td>${tstatTemp}°</td><td>Cool: ${tstatCool}° | Heat: ${tstatHeat}°</td></tr>
                        <tr><td class="dash-hl">Humidity</td><td><b>${avgHum}%</b></td><td>${tstatHum}%</td><td>Limit: ${maxHumidity ?: '--'}%</td></tr>
                        <tr><td class="dash-hl">HVAC State</td><td><b>Mode: ${tstatMode}</b></td><td colspan="2" style="color:${stateColor};"><b>Status: ${tstatState}</b></td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">Internal Diagnostics</td></tr>
                        <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val">${currentLocMode}</td></tr>
                        <tr><td class="dash-hl">Economizer Status</td><td colspan="3" class="dash-val">${fcStatusStr}</td></tr>
                        <tr><td class="dash-hl">Auto-Swap Distance</td><td colspan="3" class="dash-val">${swapText}</td></tr>
                        <tr><td class="dash-hl">Calculated Deadband</td><td colspan="3" class="dash-val">${currentDeadbandStr}</td></tr>
                        <tr><td class="dash-hl">Live Delta-T</td><td colspan="3" class="dash-val">${deltaTStr}</td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">Maintenance & Service</td></tr>
                        <tr><td class="dash-hl">Filter Life Remaining</td><td colspan="3" class="dash-val">${filterLifeStr}</td></tr>
                        <tr><td class="dash-hl">Last Filter Change</td><td colspan="3" class="dash-val">${state.lastFilterDate ?: "Not Recorded"}</td></tr>
                        <tr><td class="dash-hl">Last HVAC Service</td><td colspan="3" class="dash-val">${state.lastServiceDate ?: "Not Recorded"}</td></tr>
                        <tr><td class="dash-hl">Service Contact</td><td colspan="3" class="dash-val">${hvacContactStr}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                if (enableCostTracker && state.runHistory) {
                    paragraph "<b>7-Day Energy Cost & Savings Estimate</b>"
                    paragraph renderCostDashboard()
                }
            } else {
                paragraph "<i>Please select a thermostat below to populate the dashboard.</i>"
            }
        }

        section("<b>Zone Breakdown</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Displays real-time data from all configured room sensors. Shows which rooms are actively factoring into the home's average temperature based on recent motion.</div>"
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>Temp</th><th>Humidity</th><th>Occupied?</th><th>Status</th></tr></thead><tbody>"
            def timeoutMs = (occupancyTimeout ?: 60) * 60000
            def hasZones = false
            
            for (int i = 1; i <= 12; i++) {
                if (settings["enableZ${i}"] && settings["z${i}Temp"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    def zTemp = settings["z${i}Temp"].currentValue("temperature") ?: "--"
                    def zHum = settings["z${i}Hum"] ? (settings["z${i}Hum"].currentValue("humidity") ?: "--") : "N/A"
                    def zMotion = settings["z${i}Motion"]
                    
                    def isOccupied = "N/A"
                    def zStatus = "<span style='color:green;'>Averaging</span>"
                    
                    if (enableOccupancy && zMotion) {
                        def lastActive = state.zoneLastActive ? state.zoneLastActive[zMotion.id] : null
                        if (lastActive && (now() - lastActive) < timeoutMs) {
                            isOccupied = "Yes"
                        } else {
                            isOccupied = "No"
                            zStatus = "<span style='color:gray;'>Ignored (Empty)</span>"
                        }
                    }
                    zoneHTML += "<tr><td><b>${zName}</b></td><td>${zTemp}°</td><td>${zHum}%</td><td>${isOccupied}</td><td>${zStatus}</td></tr>"
                }
            }
            zoneHTML += "</tbody></table>"
            if (hasZones) paragraph zoneHTML else paragraph "<i>No zones configured yet.</i>"
        }

        section("<b>Recent Action History</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a transparent, rolling log of every command the BMS sends to your thermostat, including mode swaps, failsafe triggers, and setpoint adjustments.</div>"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        section("<b>Last 10 Compressor Cycles</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Displays the exact duration of your last 10 heating or cooling cycles to help verify that the Minimum Run Time protections are functioning correctly.</div>"
            if (state.recentCycles) {
                def cycleStr = state.recentCycles.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${cycleStr}</span>"
            } else {
                paragraph "<i>No completed cycles logged yet.</i>"
            }
            input "resetCycles", "button", title: "Clear Cycle History"
        }

        section("<b>App Control & Main HVAC System</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The core connection to your physical HVAC hardware. Allows you to assign your main thermostat and provides a master kill-switch to quickly bypass all app automation.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "thermostat", "capability.thermostat", title: "Select Main Thermostat", required: false, multiple: false
            if (state.manualHold) input "releaseHold", "button", title: "Release Manual Hold"
        }

        section("<b>1. App-Driven Auto Changeover</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Takes control of deciding whether your house needs Heating or Cooling away from the thermostat. It automatically swaps modes based on your home's <i>Calculated Average Temperature</i> rather than the single wall sensor.</div>"
            input "enableAutoSwap", "bool", title: "<b>Enable App-Driven Mode Swapping</b>", defaultValue: false, submitOnChange: true
            if (enableAutoSwap) {
                input "autoSwapDeadband", "decimal", title: "Changeover Deadband (°F) - Prevents rapid mode swapping", required: false, defaultValue: 1.0
            }
        }

        section("<b>2. Zones & Dynamic Occupancy</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connects motion sensors to temperature sensors. If a room has no motion for the set timeout, it is mathematically dropped from the home's average temperature to stop wasting energy on empty rooms.</div>"
            input "enableOccupancy", "bool", title: "<b>Enable Dynamic Occupancy Weighting</b>", defaultValue: false, submitOnChange: true
            if (enableOccupancy) {
                input "occupancyTimeout", "number", title: "Minutes of no motion before dropping room", required: false, defaultValue: 60
            }
            for (int i = 1; i <= 12; i++) {
                input "enableZ${i}", "bool", title: "Enable Zone ${i}", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Zone ${i} Name", required: false, defaultValue: "Zone ${i}"
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temp Sensor", required: false
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional)", required: false
                    input "z${i}Motion", "capability.motionSensor", title: "Motion Sensor (Optional)", required: false
                    paragraph "<hr>"
                }
            }
        }

        section("<b>2b. Dynamic Setpoint Alignment</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> If your thermostat is in a cool hallway but the bedrooms are hot, the AC will shut off early. This automatically shifts the physical thermostat's setpoint to force it to keep running until the <i>Average Temperature</i> reaches your true target.</div>"
            input "enableAverageSync", "bool", title: "<b>Enable Dynamic Setpoint Alignment</b>", defaultValue: false, submitOnChange: true
            if (enableAverageSync) {
                input "maxSyncOffset", "decimal", title: "Maximum Allowed Shift (°F) - Safety limit to prevent freezing/overheating", required: false, defaultValue: 3.0
            }
        }

        section("<b>3. The Economizer (Free Cooling Advisor)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Suspends the AC and alerts you to open windows if outdoor weather is favorable. <b>Failsafe:</b> If windows are not opened within the timeout period, it aborts Free Cooling and resumes normal AC to prevent the house from getting hot.</div>"
            input "enableFreeCooling", "bool", title: "<b>Enable Free Cooling</b>", defaultValue: true, submitOnChange: true
            if (enableFreeCooling) {
                input "freeCoolModes", "mode", title: "Modes to ALLOW Free Cooling", multiple: true, required: false
                input "outdoorSensor", "capability.temperatureMeasurement", title: "Outdoor Temp/Humidity Sensor", required: false
                input "freeCoolTempDelta", "decimal", title: "Minimum Temp Difference (°F)", required: false, defaultValue: 3.0
                input "freeCoolMaxHumidity", "decimal", title: "Maximum Outdoor Humidity Allowed (%)", required: false, defaultValue: 60.0
                input "freeCoolTimeout", "number", title: "Minutes to wait for windows to open before aborting", required: false, defaultValue: 15
                input "freeCoolNotify", "capability.notification", title: "Send Push Notification", required: false, multiple: true
                input "freeCoolSwitch", "capability.switch", title: "Trigger Virtual Switch", required: false, multiple: false
            }
        }

        section("<b>4. Energy Cost & ROI Savings Tracking</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks exact compressor and Aux heat runtimes to estimate your HVAC utility costs. It also calculates the runtime you <i>avoided</i> while using Free Cooling to prove your Return on Investment (ROI).</div>"
            input "enableCostTracker", "bool", title: "<b>Enable 7-Day Energy Tracking</b>", defaultValue: true, submitOnChange: true
            if (enableCostTracker) {
                input "costPerKwh", "decimal", title: "Utility Rate (USD per kWh)", required: false, defaultValue: 0.15
                input "coolingKw", "decimal", title: "Cooling Power Draw (kW)", required: false, defaultValue: 4.6
                input "heatingKw", "decimal", title: "Heat Pump Power Draw (kW)", required: false, defaultValue: 4.6
                input "auxHeatingKw", "decimal", title: "Aux/Emergency Heat Power Draw (kW)", required: false, defaultValue: 15.0
                input "resetHistory", "button", title: "Clear Tracking History"
            }
        }

        section("<b>5. Predictive Pre-Conditioning (Thermal Battery)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Checks tomorrow's weather forecast. If extreme heat is predicted, it sub-cools your house during the early morning when electricity is cheap to build a 'Thermal Battery' to coast through the hot afternoon.</div>"
            input "enablePreCondition", "bool", title: "<b>Enable Predictive Pre-Conditioning</b>", defaultValue: false, submitOnChange: true
            if (enablePreCondition) {
                input "weatherDevice", "capability.sensor", title: "Select Weather Device", required: false
                input "heatwaveThreshold", "decimal", title: "Forecast High threshold (°F)", required: false, defaultValue: 90.0
                input "preCoolOffset", "decimal", title: "Degrees to DROP setpoint during Pre-Cool", required: false, defaultValue: 3.0
                input "preCoolStartTime", "time", title: "Pre-Cool Start Time", required: false
                input "preCoolEndTime", "time", title: "Pre-Cool End Time", required: false
            }
        }

        section("<b>6. Adaptive Recovery (Smart Start)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Eliminates guesswork. You input what time you will get home, and the app calculates exactly when to start the HVAC based on how fast your specific unit heats or cools.</div>"
            input "enableAdaptive", "bool", title: "<b>Enable Adaptive Recovery</b>", defaultValue: false, submitOnChange: true
            if (enableAdaptive) {
                input "expectedReturnTime", "time", title: "Expected Return Time", required: false
                input "coolingGlide", "decimal", title: "Degrees Cooled per Hour", required: false, defaultValue: 2.0
                input "heatingGlide", "decimal", title: "Degrees Heated per Hour", required: false, defaultValue: 3.0
            }
        }

        section("<b>7. Open Window / Door Defeat</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically intercepts and shuts off the HVAC if a monitored door or window is left open past the delay threshold. Restores normal operation once closed.</div>"
            input "enableWindowDefeat", "bool", title: "<b>Enable Window/Door Defeat</b>", defaultValue: true, submitOnChange: true
            if (enableWindowDefeat) {
                input "contactSensors", "capability.contactSensor", title: "Select Perimeter Contact Sensors", required: false, multiple: true
                input "contactDelay", "number", title: "Minutes to wait before shutting off HVAC", required: false, defaultValue: 3
            }
        }

        section("<b>8. Multi-Stage Dehumidification</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Prioritizes indoor air quality. Stage 1 turns on standalone dehumidifier plugs. Stage 2 slightly overcools the house with the main AC to force the compressor to wring excess moisture out of the air.</div>"
            input "enableDehumidification", "bool", title: "<b>Enable Dehumidification Logic</b>", defaultValue: true, submitOnChange: true
            if (enableDehumidification) {
                input "maxHumidity", "decimal", title: "Maximum Acceptable Humidity (%)", required: false, defaultValue: 55.0
                input "dehumidifierPlugs", "capability.switch", title: "Stage 1: Standalone Dehumidifier Plugs", required: false, multiple: true
                input "dehumidifierTimeout", "number", title: "Minutes to let plugs run before falling back to AC", required: false, defaultValue: 45
                input "acDehumidifyOffset", "decimal", title: "Stage 2: Degrees to drop AC setpoint", required: false, defaultValue: 2.0
            }
        }

        section("<b>9. Time-of-Use (Peak Shaving)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically drifts your target temperatures up or down during expensive utility Time-of-Use (TOU) hours to reduce peak demand charges.</div>"
            input "enablePeakShaving", "bool", title: "<b>Enable Peak Shaving</b>", defaultValue: true, submitOnChange: true
            if (enablePeakShaving) {
                input "peakStartTime", "time", title: "Peak Rates Start Time", required: false
                input "peakEndTime", "time", title: "Peak Rates End Time", required: false
                input "peakCoolingOffset", "decimal", title: "Degrees to RAISE cooling setpoint during Peak", required: false, defaultValue: 3.0
                input "peakHeatingOffset", "decimal", title: "Degrees to LOWER heating setpoint during Peak", required: false, defaultValue: 3.0
            }
        }

        section("<b>10. Smart Filter & Maintenance Tracking</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks exact blower runtime multiplied by air quality dust conditions to accurately predict filter life, logs physical service dates, and stores your HVAC technician's contact info.</div>"
            input "enableFilterTracker", "bool", title: "<b>Enable Maintenance Tracking Logic</b>", defaultValue: true, submitOnChange: true
            if (enableFilterTracker) {
                input "filterSize", "text", title: "Filter Size", required: false
                input "maxFilterHours", "number", title: "Baseline Filter Life (Fan Run Hours)", required: false, defaultValue: 300
                input "indoorIAQ", "capability.airQuality", title: "Indoor AQI Sensor", required: false
                input "outdoorIAQ", "capability.airQuality", title: "Outdoor AQI Sensor", required: false
                
                if (state.filterRunMinutes != null) {
                    def maxMins = (maxFilterHours ?: 300) * 60
                    def usedMins = state.filterRunMinutes ?: 0.0
                    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                
                    paragraph "<b>Filter Life Remaining:</b> ${percentLeft}%"
                    paragraph "<b>Last Filter Change:</b> ${state.lastFilterDate ?: 'Not Recorded'}"
                    input "resetFilter", "button", title: "Record Filter Change (Resets Life to 100%)"
                }
                
                paragraph "<hr>"
                paragraph "<b>HVAC System Service Tracking</b>"
                input "hvacCompanyName", "text", title: "HVAC Company Name", required: false
                input "hvacCompanyPhone", "text", title: "HVAC Company Phone Number", required: false
                paragraph "<b>Last HVAC Service:</b> ${state.lastServiceDate ?: 'Not Recorded'}"
                input "resetService", "button", title: "Record HVAC Service Today"
            }
        }

        section("<b>11. Delta-T Efficiency Monitoring & Run Time Protection</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> <b>1) Delta-T:</b> Monitors system health by measuring the temperature drop across your HVAC coil. <b>2) Run Time Protection:</b> Protects oversized compressors from damaging short-cycles by artificially dropping the setpoint to force a minimum, safe runtime.</div>"
            input "enableDeltaT", "bool", title: "<b>Enable Delta-T Logic</b>", defaultValue: true, submitOnChange: true
            if (enableDeltaT) {
                input "returnSensor", "capability.temperatureMeasurement", title: "Return Air Sensor", required: false
                input "dischargeSensor", "capability.temperatureMeasurement", title: "Discharge (Supply) Air Sensor", required: false
                
                // Default delay changed to 30 to accommodate 15-minute sensor polling
                input "deltaTCheckDelay", "number", title: "Minutes before checking Delta-T", required: false, defaultValue: 30
                input "minCoolingDeltaT", "decimal", title: "Min Cooling Delta-T (°F)", required: false, defaultValue: 12.0
                input "minHeatingDeltaT", "decimal", title: "Min Heating Delta-T (°F)", required: false, defaultValue: 15.0
                input "emergencyShutoff", "bool", title: "Emergency Shutoff if Delta-T fails", defaultValue: false
            }
            
            paragraph "<b>Oversized Unit Protection</b>"
            input "enableMinRuntime", "bool", title: "<b>Enable Min Run Time Protection</b>", defaultValue: true, submitOnChange: true
            if (enableMinRuntime) {
                input "minRunTime", "number", title: "Minimum Run Time (minutes)", required: false, defaultValue: 10
                input "tempDropThreshold", "decimal", title: "Max Temp Drop per Min", required: false, defaultValue: 0.5
                input "setpointBuffer", "decimal", title: "Temporary Setpoint Buffer (°F)", required: false, defaultValue: 2.0
                input "shortCycleThreshold", "decimal", title: "Short-Cycle Degree Threshold (°F)", required: false, defaultValue: 1.0
            }
        }

        section("<b>12. Routine Setpoint Enforcement</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Acts as a Self-Healing loop. Periodically wakes up and re-transmits the correct setpoints to the thermostat to ensure no wireless commands were dropped by your Z-Wave/Zigbee mesh network.</div>"
            input "enableEnforcement", "bool", title: "<b>Enable Routine Enforcement</b>", defaultValue: false, submitOnChange: true
            if (enableEnforcement) {
                input "enforcementInterval", "enum", title: "Check Interval", options: ["15":"Every 15 Minutes", "30":"Every 30 Minutes", "60":"Every 1 Hour"], required: false, defaultValue: "30"
            }
        }

        section("<b>Base Operating Modes & Ranges</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The foundation of the BMS. Sets your default targets based on Hubitat Location Modes. <i>Note: 'Good Night' mode strictly locks these temperatures for maximum comfort, bypassing economy features.</i></div>"
            paragraph "<i>Leave 'Allowed Modes (Overall App)' BLANK to allow the app to run 24/7. Otherwise, make sure to select every single mode you want the app to function in.</i>"
            input "allowedModes", "mode", title: "Allowed Modes (Overall App) [Master Override]", multiple: true, required: false
            
            paragraph "<b>Home</b>"
            input "homeCoolingSetpoint", "decimal", title: "Home Cooling Setpoint", required: false, defaultValue: 74
            input "homeHeatingSetpoint", "decimal", title: "Home Heating Setpoint", required: false, defaultValue: 68
            
            paragraph "<b>Away</b>"
            input "awayModes", "mode", title: "Select 'Away' modes", multiple: true, required: false
            input "awayCoolingSetpoint", "decimal", title: "Away Cooling Setpoint", required: false, defaultValue: 78
            input "awayHeatingSetpoint", "decimal", title: "Away Heating Setpoint", required: false, defaultValue: 62
            
            paragraph "<b>Good Night (Strict)</b>"
            input "nightModes", "mode", title: "Select 'Good Night' modes", multiple: true, required: false
            input "nightCoolingSetpoint", "decimal", title: "Good Night Cooling Setpoint", required: false, defaultValue: 70
            input "nightHeatingSetpoint", "decimal", title: "Good Night Heating Setpoint", required: false, defaultValue: 66
        }
        
        section("<b>Disclaimer</b>") {
            paragraph "<div style='font-size:12px; color:#888;'><b>Legal Disclaimer:</b> ShaneAllen is not responsible for any damage or liability with the use of this application. This is a user customer application, use at your own discretion.</div>"
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.recentCycles) state.recentCycles = []
    if (state.filterRunMinutes == null) state.filterRunMinutes = 0.0
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.runHistory) state.runHistory = [:]
    
    if (!state.lastFilterDate) state.lastFilterDate = "Not Recorded"
    if (!state.lastServiceDate) state.lastServiceDate = "Not Recorded"
    
    state.isBuffering = false; state.cycleStartTime = null; state.currentAction = "idle"
    state.manualHold = false; state.windowOpenHold = false; state.dehumidifyingStage = 0 
    state.isPeakHours = false; state.isPreConditioning = false; state.isAdaptiveRecovering = false
    
    state.freeCoolState = "idle" 
    state.freeCoolTargetTime = null
    state.fcStartTime = null
    
    state.expectedCool = null; state.expectedHeat = null
    
    if (thermostat) {
        subscribe(thermostat, "thermostatOperatingState", hvacStateHandler)
        subscribe(thermostat, "coolingSetpoint", setpointHandler)
        subscribe(thermostat, "heatingSetpoint", setpointHandler)
    }
    
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    
    if (enableFreeCooling && outdoorSensor) {
        subscribe(outdoorSensor, "temperature", outdoorSensorHandler)
        subscribe(outdoorSensor, "humidity", outdoorSensorHandler)
    }
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", humidityHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
        }
    }
    
    if (enableWindowDefeat && contactSensors) subscribe(contactSensors, "contact", contactHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    
    schedulePeakTimes()
    schedulePreConditioning()
    scheduleAdaptiveRecoveryCheck()
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes(routineSweep)
        else if (interval == "30") runEvery30Minutes(routineSweep)
        else if (interval == "60") runEvery1Hour(routineSweep)
    }
    
    logAction("App Initialized. Modular BMS Engine Ready.")
    evaluateSystem()
}

def routineSweep() {
    // Keep logs clean by aborting the sweep silently if the system is paused
    if (state.manualHold || state.windowOpenHold || state.isBuffering) return 
    
    logAction("Running routine setpoint enforcement sweep.")
    evaluateSystem()
}

def hubRestartHandler(evt) {
    logAction("CRITICAL: Hub reboot detected. Executing BMS Failsafe Recovery.")
    state.isBuffering = false; state.windowOpenHold = false; state.dehumidifyingStage = 0
    state.isPreConditioning = false; state.isAdaptiveRecovering = false; state.freeCoolState = "idle"
    state.cycleStartTime = null; state.currentAction = "idle"
    if (state.savedPlugStates) restorePlugs() 
    unschedule()
    schedulePeakTimes(); schedulePreConditioning(); scheduleAdaptiveRecoveryCheck()
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes(routineSweep)
        else if (interval == "30") runEvery30Minutes(routineSweep)
        else if (interval == "60") runEvery1Hour(routineSweep)
    }
    
    evaluateSystem()
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is disabled via the Master Switch."
    
    if (allowedModes && !(allowedModes as List).contains(location.mode)) {
        return "<span style='color:orange;'><b>App Disabled by Mode:</b></span> The current location mode (<b>${location.mode}</b>) is not selected in your 'Allowed Modes' setting."
    }
    
    if (state.windowOpenHold) return "<span style='color:red;'><b>HVAC is OFF</b></span> because a monitored perimeter window or door is open."
    if (state.manualHold) return "<span style='color:orange;'><b>Automation Paused</b></span> because someone manually adjusted the physical thermostat."
    if (state.isBuffering) return "The compressor is locked <span style='color:blue;'><b>ON</b></span> to satisfy the Minimum Run Time buffer and prevent hardware damage."
    
    def mode = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase()
    if (mode?.contains("aux") || mode?.contains("emergency")) return "<span style='color:red;'><b>WARNING: Auxiliary Heat is Active.</b></span> The system is currently running the high-power resistance heat strips."
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    if (isNight) return "Good Night mode is active. Setpoints strictly locked."
    if (state.freeCoolState == "pending") return "<span style='color:orange;'><b>Free Cooling Pending:</b></span> Favorable weather detected! Waiting for you to open the windows before the timeout aborts the cycle."
    if (state.freeCoolState == "active") return "<span style='color:green;'><b>Free Cooling Active:</b></span> AC is suspended because outdoor conditions are favorable and windows are open."
    if (state.dehumidifyingStage == 1) return "Running smart-plug dehumidifiers to reduce high indoor humidity."
    if (state.dehumidifyingStage == 2) return "AC is actively overcooling the house to extract excess humidity."
    if (state.isAdaptiveRecovering) return "Starting the HVAC early to ensure the house reaches the target temperature."
    if (state.isPeakHours) return "Saving money by shifting target temperatures during expensive Peak Utility hours."
    if (state.isPreConditioning) return "Pre-cooling the house to build a thermal battery ahead of extreme heat."
    if (mode == "cooling" || mode == "heating") return "Operating normally. Currently ${mode} to satisfy the average room requirements."
    return "System is IDLE. Averaged zone temperatures are currently within the comfort range."
}

def getAverageTemp() {
    def total = 0.0; def count = 0
    def timeoutMs = (occupancyTimeout ?: 60) * 60000
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Temp"]) {
            def tempDev = settings["z${i}Temp"]; def motionDev = settings["z${i}Motion"]
            if (tempDev.currentValue("temperature") != null) {
                if (!enableOccupancy || !motionDev || (state.zoneLastActive && state.zoneLastActive[motionDev.id] && (now() - state.zoneLastActive[motionDev.id]) < timeoutMs)) {
                    total += tempDev.currentValue("temperature"); count++
                }
            }
        }
    }
    if (count == 0 && thermostat?.currentValue("temperature") != null) return thermostat.currentValue("temperature").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
}

def getAverageHumidity() {
    def total = 0.0; def count = 0
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Hum"]) {
            def humDev = settings["z${i}Hum"]
            if (humDev.currentValue("humidity") != null) { total += humDev.currentValue("humidity"); count++ }
        }
    }
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
}

def motionHandler(evt) { if (evt.value == "active") { if (!state.zoneLastActive) state.zoneLastActive = [:]; state.zoneLastActive[evt.device.id] = now(); evaluateSystem() } }

def appButtonHandler(btn) {
    def todayStr = new Date().format("MM/dd/yyyy", location.timeZone)
    if (btn == "resetFilter") { 
        state.filterRunMinutes = 0.0
        state.lastFilterDate = todayStr
        logAction("Filter logged as changed. Life reset to 100%.") 
    } 
    else if (btn == "resetService") {
        state.lastServiceDate = todayStr
        logAction("HVAC Service recorded for today.")
    }
    else if (btn == "releaseHold") { 
        state.manualHold = false
        logAction("Manual Hold released by user.")
        evaluateSystem() 
    } 
    else if (btn == "resetHistory") { 
        state.runHistory = [:]
        logAction("Energy Cost Tracking history cleared.") 
    }
    else if (btn == "resetCycles") {
        state.recentCycles = []
        logAction("Compressor cycle history cleared.")
    }
}

def enableSwitchHandler(evt) { if (evt.value == "off") logAction("App Disabled."); else evaluateSystem() }
def modeChangeHandler(evt) { state.manualHold = false; state.isAdaptiveRecovering = false; evaluateSystem() }

def setpointHandler(evt) {
    // --- UPDATED: Block hardware bounce-back while buffering ---
    if (state.windowOpenHold || state.isBuffering) return 
    // -----------------------------------------------------------
    def newVal = evt.value.toBigDecimal(); def isManual = false
    if (evt.name == "coolingSetpoint" && state.expectedCool != null && newVal != state.expectedCool) isManual = true
    if (evt.name == "heatingSetpoint" && state.expectedHeat != null && newVal != state.expectedHeat) isManual = true
    if (isManual && !state.manualHold) { state.manualHold = true; logAction("MANUAL OVERRIDE: Physical thermostat changed to ${newVal}°. Automation suspended until mode change.") }
}

def outdoorSensorHandler(evt) { evaluateSystem() }

def checkFreeCooling(currentCoolTarget) {
    if (!enableFreeCooling || !outdoorSensor) return currentCoolTarget
    
    if (freeCoolModes && !(freeCoolModes as List).contains(location.mode)) {
        if (state.freeCoolState != "idle") {
            state.freeCoolState = "idle"
            disengageFreeCooling()
        }
        return currentCoolTarget
    }
    
    def outTemp = outdoorSensor.currentValue("temperature")
    def outHum = outdoorSensor.currentValue("humidity") ?: 0.0
    def currentAvg = getAverageTemp()
    def delta = freeCoolTempDelta ?: 3.0
    def maxHum = freeCoolMaxHumidity ?: 60.0
    def timeoutMins = freeCoolTimeout ?: 15
    
    if (outTemp != null) {
        def isFavorable = (currentAvg >= currentCoolTarget) && (outTemp <= (currentAvg - delta)) && (outHum <= maxHum)
        
        if (isFavorable) {
            if (state.freeCoolState == "idle") {
                def anyOpen = contactSensors ? contactSensors.any { it.currentValue("contact") == "open" } : false
                if (anyOpen) {
                    state.freeCoolState = "active"
                    engageFreeCooling()
                } else {
                    state.freeCoolState = "pending"
                    state.freeCoolTargetTime = now() + (timeoutMins * 60000)
                    logAction("Free Cooling Favorable. Waiting ${timeoutMins} mins for windows to open.")
                    if (freeCoolNotify) freeCoolNotify.deviceNotification("Free Cooling available! Open the windows to save energy. AC suspended for ${timeoutMins} minutes.")
                    runIn(timeoutMins * 60, freeCoolTimeoutHandler)
                }
                return 85.0 
            } else if (state.freeCoolState == "pending" || state.freeCoolState == "active") {
                return 85.0 
            }
        } else {
            if (state.freeCoolState != "idle") {
                state.freeCoolState = "idle"
                state.freeCoolTargetTime = null
                unschedule(freeCoolTimeoutHandler)
                disengageFreeCooling()
            }
        }
    }
    return state.freeCoolState in ["pending", "active"] ? 85.0 : currentCoolTarget
}

def freeCoolTimeoutHandler() {
    if (state.freeCoolState == "pending") {
        logAction("Free Cooling Aborted: Windows were not opened in time. Resuming standard AC.")
        if (freeCoolNotify) freeCoolNotify.deviceNotification("Windows not opened. Free Cooling aborted and standard AC resumed.")
        state.freeCoolState = "lockedOut"
        state.freeCoolTargetTime = null
        evaluateSystem()
    }
}

def engageFreeCooling() {
    logAction("Free Cooling ACTIVE: AC suspended.")
    state.fcStartTime = now()
    if (freeCoolSwitch) freeCoolSwitch.on()
}

def disengageFreeCooling() {
    logAction("Free Cooling disabled or weather reset. Restoring AC.")
    if (state.fcStartTime) trackFreeCoolingSavings()
    if (freeCoolSwitch) freeCoolSwitch.off()
    if (freeCoolNotify) freeCoolNotify.deviceNotification("Free Cooling ended. Please close the windows.")
}

def trackFreeCoolingSavings() {
    if (!state.fcStartTime) return
    def fcMins = (now() - state.fcStartTime) / 60000.0
    state.fcStartTime = null 
    
    def estimatedSavedRunMins = fcMins * 0.30 
    
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    if (!state.runHistory[today]) state.runHistory[today] = [cool: 0.0, heat: 0.0, aux: 0.0, fcSavedMins: 0.0]
    
    state.runHistory[today].fcSavedMins = (state.runHistory[today].fcSavedMins ?: 0.0) + estimatedSavedRunMins
    logAction("ROI: Logged ${String.format('%.1f', estimatedSavedRunMins)} minutes of estimated avoided compressor runtime via Free Cooling.")
}

def evaluateSystem() {
    if (!thermostat) return
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    if (allowedModes && !(allowedModes as List).contains(location.mode)) return
    
    if (state.windowOpenHold || state.manualHold || state.isBuffering) return 
    
    def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    
    def targetCool = homeCoolingSetpoint ?: 74.0; def targetHeat = homeHeatingSetpoint ?: 68.0
    
    if (isNight) { targetCool = nightCoolingSetpoint ?: 70.0; targetHeat = nightHeatingSetpoint ?: 66.0 } 
    else if (isAway) { targetCool = awayCoolingSetpoint ?: 78.0; targetHeat = awayHeatingSetpoint ?: 62.0 }
    
    if (!isNight) {
        if (state.isAdaptiveRecovering) { targetCool = homeCoolingSetpoint ?: 74.0; targetHeat = homeHeatingSetpoint ?: 68.0 }
        if (enablePeakShaving && state.isPeakHours) { targetCool += (peakCoolingOffset ?: 3.0); targetHeat -= (peakHeatingOffset ?: 3.0) }
        if (enableDehumidification && state.dehumidifyingStage == 2) { targetCool -= (acDehumidifyOffset ?: 2.0) }
        if (enablePreCondition && state.isPreConditioning) { targetCool = (homeCoolingSetpoint ?: 74.0) - (preCoolOffset ?: 3.0) }
        
        targetCool = checkFreeCooling(targetCool)
    } else {
        if (state.freeCoolState != "idle") {
            state.freeCoolState = "idle"
            state.freeCoolTargetTime = null
            unschedule(freeCoolTimeoutHandler)
            disengageFreeCooling()
        }
    }
    
    // --- Dynamic Setpoint Alignment ---
    def syncMessage = ""
    if (enableAverageSync && thermostat.currentValue("temperature") != null) {
        def tstatTemp = thermostat.currentValue("temperature").toBigDecimal()
        def currentAvg = getAverageTemp()
        
        def offset = (currentAvg - tstatTemp)
        def maxShift = maxSyncOffset ?: 3.0
        
        if (offset > maxShift) offset = maxShift
        if (offset < -maxShift) offset = -maxShift
        
        if (offset != 0.0) {
            targetCool = (targetCool - offset).setScale(1, BigDecimal.ROUND_HALF_UP)
            targetHeat = (targetHeat - offset).setScale(1, BigDecimal.ROUND_HALF_UP)
            syncMessage = " [Alignment Active: Shifted by ${String.format('%.1f', -offset)}°]"
        }
    }
    
    // --- Universal Deadband Enforcer ---
    // Ensures modifiers (like Dehum/Pre-Cool/Alignment) never push Cool and Heat too close together
    def hardwareDeadband = 3.0
    if ((targetCool - targetHeat) < hardwareDeadband) {
        // If cooling target was pushed down, shove the heating target down out of the way
        targetHeat = (targetCool - hardwareDeadband).setScale(1, BigDecimal.ROUND_HALF_UP)
        syncMessage += " [Deadband Enforced]"
    }
    // -----------------------------------
    
    if (thermostat.currentValue("coolingSetpoint") != targetCool || thermostat.currentValue("heatingSetpoint") != targetHeat) {
        state.expectedCool = targetCool; state.expectedHeat = targetHeat
        thermostat.setCoolingSetpoint(targetCool); thermostat.setHeatingSetpoint(targetHeat)
        logAction("BMS Command -> Pushing Setpoints to Thermostat: COOL ${targetCool}° | HEAT ${targetHeat}°${syncMessage}")
    }
    
    if (enableAutoSwap && !(state.freeCoolState in ["pending", "active"])) {
        def currentAvg = getAverageTemp()
        def tMode = thermostat.currentValue("thermostatMode")?.toLowerCase()
        def deadband = autoSwapDeadband ?: 1.0
        
        if (tMode == "heat" || tMode == "cool" || tMode == "auto") {
            if (currentAvg >= (targetCool + deadband) && tMode != "cool") {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to COOL mode.")
                thermostat.setThermostatMode("cool")
            } else if (currentAvg <= (targetHeat - deadband) && tMode != "heat") {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to HEAT mode.")
                thermostat.setThermostatMode("heat")
            }
        }
    }
}

def contactHandler(evt) { 
    def anyOpen = contactSensors ? contactSensors.any { it.currentValue("contact") == "open" } : false
    
    if (anyOpen && state.freeCoolState == "pending") {
        logAction("Windows opened by user. Fully engaging Free Cooling.")
        unschedule(freeCoolTimeoutHandler)
        state.freeCoolState = "active"
        state.freeCoolTargetTime = null
        engageFreeCooling()
        evaluateSystem()
    } else if (!anyOpen && state.freeCoolState == "active") {
        logAction("Windows closed by user. Ending Free Cooling.")
        state.freeCoolState = "lockedOut"
        disengageFreeCooling()
        evaluateSystem()
    }
    
    if (anyOpen && !state.windowOpenHold) { 
        runIn((contactDelay ?: 3) * 60, executeWindowDefeat) 
    } else if (!anyOpen && state.windowOpenHold) { 
        logAction("Windows closed. Releasing HVAC Safety Defeat.")
        state.windowOpenHold = false; unschedule(executeWindowDefeat); evaluateSystem() 
    } else if (!anyOpen) unschedule(executeWindowDefeat) 
}

def executeWindowDefeat() { state.windowOpenHold = true; if (state.isBuffering) { state.isBuffering = false; unschedule(releaseBuffer) }; logAction("BMS Command -> Safety Defeat Active. Turning HVAC OFF."); thermostat.off() }

def schedulePreConditioning() { if (enablePreCondition && weatherDevice && preCoolStartTime && preCoolEndTime) { schedule(preCoolStartTime, checkWeatherAndPrecool); schedule(preCoolEndTime, endPrecool) } }
def checkWeatherAndPrecool() { def f = weatherDevice.currentValue("forecastHigh") ?: weatherDevice.currentValue("temperature"); if (f != null && f.toBigDecimal() >= (heatwaveThreshold ?: 90.0)) { state.isPreConditioning = true; evaluateSystem() } }
def endPrecool() { if (state.isPreConditioning) { state.isPreConditioning = false; evaluateSystem() } }

def scheduleAdaptiveRecoveryCheck() { if (enableAdaptive && expectedReturnTime) schedule("0 * * * * ?", checkAdaptiveRecovery) }
def checkAdaptiveRecovery() {
    def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
    if (!enableAdaptive || !expectedReturnTime || !isAway) { state.isAdaptiveRecovering = false; return }
    def msUntilReturn = timeToday(expectedReturnTime).time - now()
    if (msUntilReturn > 0 && msUntilReturn < 43200000) {
        def currentAvg = getAverageTemp()
        def isCoolingSeason = (currentAvg > (homeCoolingSetpoint ?: 74.0))
        def delta = isCoolingSeason ? (currentAvg - (homeCoolingSetpoint ?: 74.0)) : ((homeHeatingSetpoint ?: 68.0) - currentAvg)
        if (delta <= 0) return 
        def hoursNeeded = delta / (isCoolingSeason ? (coolingGlide ?: 2.0) : (heatingGlide ?: 3.0))
        if (msUntilReturn <= (hoursNeeded * 3600000).toLong() && !state.isAdaptiveRecovering) { state.isAdaptiveRecovering = true; evaluateSystem() }
    }
}

def hvacStateHandler(evt) {
    def stateVal = evt.value?.toLowerCase() ?: ""
    if (stateVal == "cooling" || stateVal == "heating" || stateVal.contains("aux") || stateVal.contains("emergency")) {
        state.cycleStartTime = now()
        state.startTemp = thermostat.currentValue("temperature")
        if (stateVal.contains("aux") || stateVal.contains("emergency")) { 
            state.currentAction = "auxHeating" 
        } else { 
            state.currentAction = stateVal 
        }
        state.isBuffering = false
        
        def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
        
        // Block buffer shifting if it's Good Night mode
        if (enableMinRuntime && !isNight && state.currentAction in ["cooling", "heating"]) {
            def activeSetpoint = (state.currentAction == "cooling") ? thermostat.currentValue("coolingSetpoint") : thermostat.currentValue("heatingSetpoint")
            def threshold = shortCycleThreshold ?: 1.0
            
            if (activeSetpoint != null && Math.abs(state.startTemp - activeSetpoint) <= threshold) {
                logAction("BMS Command -> Proactive Short-Cycle Prevention: HVAC started within ${threshold}° of setpoint. Forcing minimum run time.")
                engageBuffer(0)
            }
        }
        
        if (enableDeltaT && returnSensor && dischargeSensor) runIn((deltaTCheckDelay ?: 30) * 60, checkDeltaT)
    } else if (stateVal == "idle" || stateVal == "pending cool" || stateVal == "pending heat") {
        unschedule(checkDeltaT)
        if (state.isBuffering) releaseBuffer()
        if (state.cycleStartTime) {
            def runMinutes = (now() - state.cycleStartTime) / 60000.0
            if (enableFilterTracker) processFilterWear(runMinutes)
            if (enableCostTracker && state.currentAction) trackEnergyCost(state.currentAction, runMinutes)
            
            // Log recent cycles
            if (state.currentAction && state.currentAction != "idle") {
                trackRecentCycle(state.currentAction, runMinutes)
            }
        }
        state.cycleStartTime = null; state.currentAction = "idle"
    }
}

def trackEnergyCost(action, runMinutes) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    if (!state.runHistory[today]) state.runHistory[today] = [cool: 0.0, heat: 0.0, aux: 0.0, fcSavedMins: 0.0]
    
    if (action == "cooling") state.runHistory[today].cool += runMinutes
    if (action == "heating") state.runHistory[today].heat += runMinutes
    if (action == "auxHeating") state.runHistory[today].aux += runMinutes
    
    def keys = state.runHistory.keySet().sort().reverse()
    if (keys.size() > 7) { state.runHistory = state.runHistory.subMap(keys[0..6]) }
}

def renderCostDashboard() {
    def liveFCSavingsMins = 0.0
    if (state.fcStartTime) liveFCSavingsMins = ((now() - state.fcStartTime) / 60000.0) * 0.30
    
    def html = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Cooling</th><th>Heating</th><th>Aux Heat</th><th>Est. Cost</th><th>Est. Savings</th></tr></thead><tbody>"
    def totalCost = 0.0; def totalSavings = 0.0
    def keys = state.runHistory.keySet().sort().reverse()
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    
    keys.each { date ->
        def data = state.runHistory[date]
        def cMins = data.cool ?: 0.0; def hMins = data.heat ?: 0.0; def aMins = data.aux ?: 0.0
        def fcSavedMins = (data.fcSavedMins ?: 0.0) + (date == today ? liveFCSavingsMins : 0.0)
    
        def cCost = (cMins / 60.0) * (coolingKw ?: 4.6) * (costPerKwh ?: 0.15)
        def hCost = (hMins / 60.0) * (heatingKw ?: 4.6) * (costPerKwh ?: 0.15)
        def aCost = (aMins / 60.0) * (auxHeatingKw ?: 15.0) * (costPerKwh ?: 0.15)
        def fcSavingsCost = (fcSavedMins / 60.0) * (coolingKw ?: 4.6) * (costPerKwh ?: 0.15)
        
        def dayCost = cCost + hCost + aCost
        totalCost += dayCost
        totalSavings += fcSavingsCost
        
        def auxStyle = aMins > 0 ? "color:red; font-weight:bold;" : ""
        def saveStyle = fcSavingsCost > 0 ? "color:green; font-weight:bold;" : "color:gray;"
        
        html += "<tr><td>${date}</td><td>${String.format('%.1f', cMins/60.0)}h</td><td>${String.format('%.1f', hMins/60.0)}h</td><td style='${auxStyle}'>${String.format('%.1f', aMins/60.0)}h</td><td>&#36;${String.format('%.2f', dayCost)}</td><td style='${saveStyle}'>+&#36;${String.format('%.2f', fcSavingsCost)}</td></tr>"
    }
    html += "<tr><td colspan='4' style='text-align:right;'><b>7-Day Totals:</b></td><td><b>&#36;${String.format('%.2f', totalCost)}</b></td><td style='color:green;'><b>+&#36;${String.format('%.2f', totalSavings)}</b></td></tr>"
    html += "</tbody></table>"
    return html
}

def sensorHandler(evt) {
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    
    // Abort if Min Runtime is disabled, or if Good Night mode is active
    if (!enableMinRuntime || isNight || state.currentAction == "idle" || state.isBuffering || !state.cycleStartTime) return
    if (state.currentAction == "auxHeating") return
    
    def runMins = (now() - state.cycleStartTime) / 60000.0
    if (runMins < 1.0 || runMins >= (minRunTime ?: 10)) return 
    def dropRate = ((state.currentAction == "cooling" ? state.startTemp - thermostat.currentValue("temperature") : thermostat.currentValue("temperature") - state.startTemp)) / runMins
    if (dropRate >= (tempDropThreshold ?: 0.5)) engageBuffer(runMins)
}

// --- UPDATED: Proactively manage hardware deadbands and protect against string math ---
def engageBuffer(runMins) {
    state.isBuffering = true
    def bufferAmt = setpointBuffer ?: 2.0
    def deadband = 3.0 // Safety gap to prevent thermostat hardware from panicking
    
    if (state.currentAction == "cooling") { 
        def newCool = (thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 72.0) - bufferAmt
        def newHeat = (thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: 68.0)
        
        // Prevent deadband collision
        if ((newCool - newHeat) < deadband) newHeat = newCool - deadband
        
        state.expectedCool = newCool; state.expectedHeat = newHeat
        
        thermostat.setCoolingSetpoint(newCool)
        if (thermostat.currentValue("heatingSetpoint") != newHeat) thermostat.setHeatingSetpoint(newHeat)
        
        logAction("BMS Command -> Equipment Protection Buffer Engaged. Target temporarily shifted to ${newCool}°.")
    } else { 
        def newHeat = (thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: 68.0) + bufferAmt
        def newCool = (thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 72.0)
        
        // Prevent deadband collision
        if ((newCool - newHeat) < deadband) newCool = newHeat + deadband
        
        state.expectedHeat = newHeat; state.expectedCool = newCool
        
        thermostat.setHeatingSetpoint(newHeat)
        if (thermostat.currentValue("coolingSetpoint") != newCool) thermostat.setCoolingSetpoint(newCool)
        
        logAction("BMS Command -> Equipment Protection Buffer Engaged. Target temporarily shifted to ${newHeat}°.")
    }
    
    runIn((((minRunTime ?: 10) - runMins) * 60).toInteger(), releaseBuffer)
}
// --------------------------------------------------------------------------

def releaseBuffer() { state.isBuffering = false; logAction("Equipment Protection Buffer Complete. Restoring normal targets."); evaluateSystem() }

def checkDeltaT() { 
    if (state.currentAction == "idle") return
    def retT = returnSensor.currentValue("temperature")
    def disT = dischargeSensor.currentValue("temperature")
    if (retT == null || disT == null) return
    
    def dT = (state.currentAction == "cooling") ? (retT - disT) : (disT - retT)
    if (dT < (state.currentAction == "cooling" ? (minCoolingDeltaT ?: 12.0) : (minHeatingDeltaT ?: 15.0))) { 
        logAction("HVAC WARNING: Poor efficiency. Delta-T is ${String.format('%.1f', dT)}°F.")
        if (emergencyShutoff) thermostat.off() 
    } else {
        logAction("Delta-T Check Passed: ${String.format('%.1f', dT)}°F.")
    }
    
    // Continuous Monitoring Loop
    runIn((deltaTCheckDelay ?: 30) * 60, checkDeltaT)
}

def processFilterWear(actualRunMinutes) { def ind = indoorIAQ ? (indoorIAQ.currentValue("airQualityIndex") ?: 0) : 0; def out = outdoorIAQ ? (outdoorIAQ.currentValue("airQualityIndex") ?: 0) : 0; state.filterRunMinutes += (actualRunMinutes * (1.0 + (ind * 0.01) + (out * 0.002))) }
def humidityHandler(evt) { if (state.windowOpenHold || state.manualHold || !enableDehumidification) return; def avgHum = getAverageHumidity(); if (avgHum > (maxHumidity ?: 55.0) && state.dehumidifyingStage == 0) { state.dehumidifyingStage = 1; if (dehumidifierPlugs) { state.savedPlugStates = dehumidifierPlugs.collectEntries { [it.id, it.currentValue("switch")] }; dehumidifierPlugs.on() }; runIn((dehumidifierTimeout ?: 45) * 60, checkDehumidifierProgress) } else if (avgHum <= ((maxHumidity ?: 55.0) - 2.0) && state.dehumidifyingStage > 0) { state.dehumidifyingStage = 0; unschedule(checkDehumidifierProgress); restorePlugs(); evaluateSystem() } }
def checkDehumidifierProgress() { if (getAverageHumidity() > (maxHumidity ?: 55.0)) { state.dehumidifyingStage = 2; restorePlugs(); evaluateSystem() } }
def restorePlugs() { if (dehumidifierPlugs && state.savedPlugStates) { dehumidifierPlugs.each { plug -> def orig = state.savedPlugStates[plug.id]; if (orig == "off") plug.off(); else if (orig == "on") plug.on() } }; state.savedPlugStates = null }
def schedulePeakTimes() { if (enablePeakShaving && peakStartTime && peakEndTime) { schedule(peakStartTime, startPeak); schedule(peakEndTime, endPeak); def now = new Date(); if (now >= timeToday(peakStartTime) && now <= timeToday(peakEndTime)) state.isPeakHours = true } }
def startPeak() { state.isPeakHours = true; evaluateSystem() }
def endPeak() { state.isPeakHours = false; evaluateSystem() }

def trackRecentCycle(action, runMinutes) {
    if (!state.recentCycles) state.recentCycles = []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    def formattedTime = String.format("%.1f", runMinutes)
    
    // Format the action name nicely
    def actionName = action.capitalize()
    if (action == "auxHeating") actionName = "Aux Heat"
    
    def cycleLog = "[${timestamp}] ${actionName} ran for <b>${formattedTime} minutes</b>"
    
    state.recentCycles.add(0, cycleLog)
    if (state.recentCycles.size() > 10) {
        state.recentCycles = state.recentCycles[0..9]
    }
}

def logAction(msg) { if(txtEnable) log.info "${app.label}: ${msg}"; def h = state.actionHistory ?: []; h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}"); if(h.size()>30)h=h[0..29]; state.actionHistory=h }
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
