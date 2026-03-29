/**
 * Advanced Mold & Dehumidification Manager
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Mold & Dehumidification Manager",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "BMS-grade mold engine featuring Free Dehumidification, Time-of-Use Financials, Window Interlocks, Advanced Tank Diagnostics, and ALL original core protections.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "zoneConfigPage")
    page(name: "globalLogicPage")
    page(name: "alertsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Mold & Dehumidification Manager</b>", install: true, uninstall: true) {
        
        section("<b>Live Performance & Risk Dashboard</b>") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"

            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 12px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone & Status</th><th style='padding: 8px;'>Environment / Goal</th><th style='padding: 8px;'>ASHRAE Risk</th><th style='padding: 8px;'>Active Run</th><th style='padding: 8px;'>Financials</th></tr>"
            
            def hasZones = false
            for (int i = 1; i <= 8; i++) {
                if (settings["z${i}Name"]) {
                    hasZones = true
                    def zData = calculateZoneState(i)
                    def riskColor = getRiskColor(zData.riskScore)
                    def spend = state["z${i}TotalSpend"] ?: 0.0
                    def save = state["z${i}TotalSave"] ?: 0.0
                    
                    def filterDisplay = zData.filterLife <= 5 ? "<span style='color:red; font-weight:bold;'>Filter: ${zData.filterLife}%</span>" : "<span style='color:#555;'>Filter: ${zData.filterLife}%</span>"
                    def ashraeFlag = zData.riskScore > 50 ? "<div style='margin-top:4px;'><span style='background:#c0392b; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>⚠️ ASHRAE WARNING</span></div>" : ""
                    
                    // High-Visibility Badges
                    def pwrBadge = ""
                    if (zData.isLeaking) { pwrBadge = "<span style='background:#c0392b; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>EMERGENCY (WET)</span>" }
                    else if (zData.windowOpen) { pwrBadge = "<span style='background:#34495e; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>PAUSED (WINDOW)</span>" }
                    else if (zData.tankFull) { pwrBadge = "<span style='background:#e74c3c; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>TANK FULL</span>" }
                    else if (zData.freeDehumOn) { pwrBadge = "<span style='background:#1abc9c; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (FREE DEHUM)</span>" }
                    else if (zData.hardwareOn) {
                        if (zData.status == "Externally Controlled") pwrBadge = "<span style='background:#8e44ad; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (EXTERNAL)</span>"
                        else if (zData.status.contains("Preemptive")) pwrBadge = "<span style='background:#e67e22; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (PREEMPTIVE)</span>"
                        else if (zData.status.contains("Compressor")) pwrBadge = "<span style='background:#f39c12; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (PROTECT)</span>"
                        else pwrBadge = "<span style='background:#27ae60; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (ACTIVE)</span>"
                    } 
                    else { pwrBadge = "<span style='background:#95a5a6; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>OFF (STANDBY)</span>" }
                    
                    def logicDisplay = zData.status == "Externally Controlled" ? "Yielding to external app" : zData.status
                    
                    def envDisplay = "<b>${zData.currHum}% RH</b>"
                    if (zData.currTemp) {
                        envDisplay += " / ${zData.currTemp}°F"
                        if (zData.dewPoint != null) {
                            def spreadColor = zData.spread <= 3.0 ? "#cc0000" : (zData.spread <= 6.0 ? "#e67e22" : "#27ae60")
                            envDisplay += "<br><small style='color:#555;'>Dew Pt: ${zData.dewPoint}°F<br>Spread: <span style='color:${spreadColor}; font-weight:bold;'>+${zData.spread}°F</span> away</small>"
                        }
                    }

                    // Render Tank Reset Button if Full
                    def actionHtml = ""
                    if (zData.tankFull) {
                        actionHtml = "<br><a href='#' onclick='return false;' style='color:#e74c3c; font-weight:bold;'>Need Reset (API)</a>"
                        input "btnResetTank_${i}", "button", title: "💧 Clear Zone ${i} Tank"
                    }
                    
                    statusText += "<tr style='border-bottom: 1px solid #eee;'>"
                    statusText += "<td style='padding: 8px;'><b>${settings["z${i}Name"]}</b><br><div style='margin-top:4px; margin-bottom:4px;'>${pwrBadge}</div><small style='color:#555;'>${logicDisplay}</small>${ashraeFlag}</td>"
                    statusText += "<td style='padding: 8px;'>${envDisplay}<br><small style='color:#666;'>Target: ${zData.target}%</small></td>"
                    statusText += "<td style='padding: 8px; color:${riskColor}; font-weight:bold;'>${zData.riskLevel}<br><small>${zData.riskScore}%</small></td>"
                    statusText += "<td style='padding: 8px;'>${zData.duration}<br><small>${filterDisplay}</small>${actionHtml}</td>"
                    statusText += "<td style='padding: 8px;'><span style='color:red;'>Spend: &#36;${String.format("%.2f", spend)}</span><br><span style='color:green;'>Saved: &#36;${String.format("%.2f", save)}</span></td>"
                    statusText += "</tr>"
                }
            }
            if (!hasZones) statusText += "<tr><td colspan='5' style='padding:20px; text-align:center;'>No zones configured.</td></tr>"
            statusText += "</table>"
            paragraph statusText
            
            // Global Mode Indicators
            def globalAvg = getHouseAverageHum()
            def outTemp = outdoorTempSensor?.currentValue("temperature")
            def outHum = outdoorHumSensor?.currentValue("humidity")
            def outDp = calculateDewPoint(outTemp, outHum)
            def outDpDisplay = outDp != null ? "${outDp}°F" : "N/A"
            
            def isPeak = isTOUPeak()
            def touMode = isPeak ? "<span style='color:#e74c3c;'><b>PEAK TIER</b></span>" : "<span style='color:#27ae60;'><b>OFF-PEAK</b></span>"
            def curRate = isPeak ? (settings.peakRate ?: 0.20) : (settings.offPeakRate ?: 0.10)
            def winterMode = isWinterShieldActive() ? "<span style='color:blue;'><b>ACTIVE</b></span>" : "Inactive"
            def houseMode = isWholeHouseAveragingTriggered(globalAvg) ? "<span style='color:red;'><b>TRIGGERED</b></span>" : "Stable"
            def energyMode = isEnergySaverActive() ? "<span style='color:#8e44ad;'><b>FORCED SAVER</b></span>" : "Standard"
            
            paragraph "<div style='font-size: 12px; background: #f0f4f7; padding: 10px; border-radius: 5px; border: 1px solid #d1d9e1; margin-bottom: 15px;'>" +
                      "<b>Outdoor Dew Point:</b> ${outDpDisplay} | <b>House Avg:</b> ${globalAvg}% | <b>Winter Shield:</b> ${winterMode} | <b>Averaging:</b> ${houseMode}<br>" + 
                      "<b>Utility Status:</b> ${touMode} (&#36;${curRate}/kWh) | <b>Energy Mode (Switch):</b> ${energyMode}</div>"
                      
            // Event History
            paragraph "<b>Event History (Last 25 Events)</b>"
            def hist = state.eventLog ?: []
            paragraph "<div style='font-size: 11px; color: #444; max-height: 150px; overflow-y: auto; background: #f9f9f9; padding: 8px; border: 1px solid #ccc; border-radius: 4px;'>${hist.join("<br>")}</div>"
        }

        section("<b>Configuration</b>") {
            href "zoneConfigPage", title: "1. Zone Detail Configuration", description: "Sensors, Targets, Windows, Fans, Tank Logic, TV Offsets, Rapid Cool."
            href "globalLogicPage", title: "2. Global Advanced Logic", description: "Time-of-Use Financials, Averaging System, Winter Shield."
            href "alertsPage", title: "3. Maintenance & Notifications", description: "Granular Time-Based Alerts, Filter tracking, Tank notifications."
            input "appEnableSwitch", "capability.switch", title: "Master Enable Switch (Turn off to halt logic safely)", required: false
            input "txtEnable", "bool", title: "Enable verbose logging", defaultValue: true
        }
    }
}

def zoneConfigPage() {
    dynamicPage(name: "zoneConfigPage", title: "Zone Configuration") {
        for (int i = 1; i <= 8; i++) {
            section("Zone ${i}: ${settings["z${i}Name"] ?: "New Zone"}", hideable: true, hidden: (i > 1 && !settings["z${i}Name"])) {
                input "z${i}Name", "text", title: "Zone Name", submitOnChange: true
                if (settings["z${i}Name"]) {
                    
                    paragraph "<b>Primary Hardware</b>"
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: true
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "z${i}Dehum", "capability.switch", title: "Dehumidifier(s)", multiple: true, required: true
                    input "z${i}Watts", "number", title: "Dehumidifier Watts", defaultValue: 500
                    
                    paragraph "<b>Interlocks & Context</b>"
                    input "z${i}Leak", "capability.waterSensor", title: "Emergency Water Leak Sensors", multiple: true, required: false
                    input "z${i}Window", "capability.contactSensor", title: "Window/Door Interlock", multiple: true, required: false
                    input "z${i}TV", "capability.switch", title: "TV / Media Switch (Raises setpoint)", multiple: true, required: false, submitOnChange: true
                    if (settings["z${i}TV"]) {
                        input "z${i}TVOffset", "number", title: "↳ TV ON Setpoint Increase (%)", defaultValue: 5
                    }
                    input "z${i}Modes", "mode", title: "Allowed Modes (Leave blank for all)", multiple: true, required: false
                    input "z${i}ExternalCtrl", "bool", title: "Shared App Control (Yield to external apps)", defaultValue: false

                    paragraph "<b>Free Dehumidification & Prediction</b>"
                    input "z${i}ExhaustFan", "capability.switch", title: "Exhaust Fan / ERV", multiple: true, required: false
                    input "z${i}EnableRapidCool", "bool", title: "Enable Rapid Cooling Prediction", defaultValue: false, submitOnChange: true
                    if (settings["z${i}EnableRapidCool"]) {
                        input "z${i}Thermostat", "capability.thermostat", title: "↳ HVAC Thermostat (To detect active cooling)", required: true
                        input "z${i}TempDrop", "decimal", title: "↳ Temp Drop Threshold (°F)", defaultValue: 3.0
                        input "z${i}TempTime", "number", title: "↳ Drop Time Window (Minutes)", defaultValue: 60
                    }

                    paragraph "<b>Tank Full Diagnostics</b>"
                    input "z${i}TankMethod", "enum", title: "Detection Method", options: ["None", "Power Meter", "Humidity Stall"], submitOnChange: true
                    if (settings["z${i}TankMethod"] == "Power Meter") {
                        input "z${i}Power", "capability.powerMeter", title: "↳ Smart Plug with Power Meter", multiple: true, required: true
                        input "z${i}ActiveWatts", "number", title: "↳ Active Compressor Watts (Threshold)", defaultValue: 100
                    } else if (settings["z${i}TankMethod"] == "Humidity Stall") {
                        if (!settings["z${i}Thermostat"] && !settings["z${i}EnableRapidCool"]) {
                            input "z${i}Thermostat", "capability.thermostat", title: "↳ HVAC Thermostat (Needed for HVAC Awareness)", required: true
                        }
                        input "z${i}StallMins", "number", title: "↳ Stall Time Limit (Mins)", defaultValue: 90
                        input "z${i}StallDrop", "number", title: "↳ Required Drop (%)", defaultValue: 5
                    }

                    paragraph "<b>Setpoints & Protections</b>"
                    input "z${i}EnableSaving", "bool", title: "Enable Savings Setpoint", defaultValue: true, submitOnChange: true
                    if (settings["z${i}EnableSaving"]) {
                        input "z${i}SavePoint", "number", title: "↳ Savings Setpoint (%)", defaultValue: 60
                        input "z${i}SaveDB", "number", title: "↳ Savings Deadband (%)", defaultValue: 5
                    }

                    input "z${i}EnableComfort", "bool", title: "Enable Comfort Setpoint", defaultValue: false, submitOnChange: true
                    if (settings["z${i}EnableComfort"]) {
                        input "z${i}ComfortPoint", "number", title: "↳ Comfort Setpoint (%)", defaultValue: 45
                        input "z${i}ComfortDB", "number", title: "↳ Comfort Deadband (%)", defaultValue: 3
                    }
                    
                    input "z${i}CompProtect", "bool", title: "Enable Compressor Protection (Min Run Time)", defaultValue: true, submitOnChange: true
                    if (settings["z${i}CompProtect"]) {
                        input "z${i}MinRun", "number", title: "↳ Minimum Run Time (Minutes)", defaultValue: 15
                    }
                }
            }
        }
    }
}

def globalLogicPage() {
    dynamicPage(name: "globalLogicPage", title: "Global Advanced Logic") {
        
        section("<b>Time-of-Use Financial Manager (Option 4)</b>") {
            input "enableTOU", "bool", title: "Enable Time-of-Use logic", defaultValue: false, submitOnChange: true
            if (enableTOU) {
                input "peakStartTime", "time", title: "Peak Rate Start Time", required: true
                input "peakEndTime", "time", title: "Peak Rate End Time", required: true
                input "peakRate", "decimal", title: "Peak Rate (per kWh)", defaultValue: 0.20
                input "offPeakRate", "decimal", title: "Off-Peak Rate (per kWh)", defaultValue: 0.10
                input "forceSaveOnPeak", "bool", title: "Force 'Savings' setpoints during Peak hours?", defaultValue: true
            } else {
                input "offPeakRate", "decimal", title: "Standard Rate (per kWh)", defaultValue: 0.14
            }
            input "energyModeSwitch", "capability.switch", title: "Energy Saver Override Switch (Force Savings Mode instantly)", required: false
        }
        
        section("<b>Averaging System (Whole-House Mode)</b>") {
            input "enableAvgSystem", "bool", title: "Enable Averaging System", defaultValue: false, submitOnChange: true
            if (enableAvgSystem) {
                input "avgHumThreshold", "number", title: "House Average Humidity Threshold (%)", defaultValue: 65
                input "avgHumDeadband", "number", title: "Averaging Deadband (%)", defaultValue: 5
            }
        }

        section("<b>Global Tank Full Output</b>") {
            input "globalTankSwitch", "capability.switch", title: "Global Tank Full Switch", required: false
        }
        
        section("<b>Outdoor Weather & Winter Shield</b>") {
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor (Required for Enthalpy/Shield)", required: true
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor (Required for Enthalpy)", required: true
            
            input "enableWinterShield", "bool", title: "Enable Winter Shield", defaultValue: false, submitOnChange: true
            if (enableWinterShield) {
                input "winterTempThreshold", "number", title: "Outdoor Cold Threshold (°F)", defaultValue: 35
                input "winterHumSetpoint", "number", title: "Winter Humidity Limit (%)", defaultValue: 35
                input "winterShieldDB", "number", title: "Winter Deadband (%)", defaultValue: 3
            }
        }
    }
}

def alertsPage() {
    dynamicPage(name: "alertsPage", title: "Maintenance & Granular Alerts") {
        
        section("<b>Notification Routing & Scheduling</b>") {
            input "leakNotifyDevices", "capability.notification", title: "🚨 Emergency Leak Notification Devices", multiple: true, required: false
            input "tankNotifyDevices", "capability.notification", title: "🚰 Tank Full Notification Devices", multiple: true, required: false
            input "humNotifyDevices", "capability.notification", title: "💧 Prolonged Humidity Notification Devices", multiple: true, required: false
            input "filterNotifyDevices", "capability.notification", title: "⚙️ Filter Maintenance Notification Devices", multiple: true, required: false
            
            input "notifyStartTime", "time", title: "Quiet Window Start (Non-Emergencies)", required: false
            input "notifyEndTime", "time", title: "Quiet Window End (Non-Emergencies)", required: false
        }

        section("<b>Global House Humidity Alerts</b>") {
            input "enableGlobalAlert", "bool", title: "Alert on Prolonged High House Average?", defaultValue: false, submitOnChange: true
            if (enableGlobalAlert) {
                input "globalAlertThresh", "number", title: "↳ House Average Humidity Threshold (%)", defaultValue: 65
                input "globalAlertMins", "number", title: "↳ Time Required Above Threshold (Minutes)", defaultValue: 120
            }
        }
        
        for (int i = 1; i <= 8; i++) {
            if (settings["z${i}Name"]) {
                section("<b>Zone ${i}: ${settings["z${i}Name"]} Alerts & Limits</b>") {
                    input "z${i}EnableAlert", "bool", title: "Enable Room-Specific Humidity Alert?", defaultValue: false, submitOnChange: true
                    if (settings["z${i}EnableAlert"]) {
                        input "z${i}AlertThresh", "number", title: "↳ Alert Threshold (%)", defaultValue: 70
                        input "z${i}AlertMins", "number", title: "↳ Time Required Above Threshold (Minutes)", defaultValue: 60
                    }
                    input "z${i}FilterLimit", "number", title: "Filter Run-Time Limit (Hours)", defaultValue: 360
                    input "btnResetFilter_${i}", "button", title: "🔄 Reset Filter Counter"
                }
            }
        }
    }
}

// Button Handler
def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard manual refresh triggered by user.")
        evaluateZones()
    } else {
        for (int i = 1; i <= 8; i++) {
            if (btn == "btnResetFilter_${i}") {
                state["z${i}FilterRunHours"] = 0.0
                state["z${i}FilterNotified"] = false
                logAction("MAINTENANCE: Zone ${i} filter manually reset.")
            }
            if (btn == "btnResetTank_${i}") {
                resetTankFullFlag(i, "Manual Dashboard Reset")
            }
        }
    }
}

// --- MATH & LOGIC ENGINE ---
def calculateDewPoint(tempF, hum) {
    if (tempF == null || hum == null) return null
    def tempC = (tempF - 32.0) * 5.0 / 9.0
    def a = 17.625
    def b = 243.04
    def alpha = Math.log(hum / 100.0) + ((a * tempC) / (b + tempC))
    def dpC = (b * alpha) / (a - alpha)
    def dpF = (dpC * 9.0 / 5.0) + 32.0
    return Math.round(dpF * 10.0) / 10.0
}

def getHouseAverageHum() {
    def total = 0.0; def count = 0
    for (int i = 1; i <= 8; i++) {
        if (settings["z${i}Name"] && settings["z${i}Hum"]) {
            def h = settings["z${i}Hum"].currentValue("humidity")
            if (h != null) { total += h; count++ }
        }
    }
    return count > 0 ? (Math.round((total / count) * 10.0) / 10.0) : 0
}

def isTOUPeak() {
    if (!settings.enableTOU || !settings.peakStartTime || !settings.peakEndTime) return false
    return timeOfDayIsBetween(settings.peakStartTime, settings.peakEndTime, new Date(), location.timeZone)
}

def isEnergySaverActive() {
    return (energyModeSwitch && energyModeSwitch.currentValue("switch") == "on")
}

def isWinterShieldActive() {
    if (!enableWinterShield || !outdoorTempSensor) return false
    def outT = outdoorTempSensor.currentValue("temperature")
    return (outT != null && outT <= winterTempThreshold)
}

def isWholeHouseAveragingTriggered(currentAvg) {
    if (!enableAvgSystem) return false
    def wasTriggered = (state.averagingActive == true)
    def threshold = avgHumThreshold ?: 65
    def db = avgHumDeadband ?: 5
    if (currentAvg >= threshold) return true
    if (wasTriggered && currentAvg > (threshold - db)) return true
    return false
}

def resetTankFullFlag(i, reason) {
    state["z${i}TankFull"] = false
    state["z${i}StallBaseRH"] = null
    state["z${i}TankMaxRH"] = null
    state["z${i}TankNotified"] = false
    logAction("ZONE ${i}: Tank Full flag CLEARED. Reason: ${reason}")
    evaluateZones()
}

def evaluateZones() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return

    def globalAvg = getHouseAverageHum()
    def avgTrigger = isWholeHouseAveragingTriggered(globalAvg)
    state.averagingActive = avgTrigger
    def winterActive = isWinterShieldActive()
    def saverActive = isEnergySaverActive()
    
    def isPeak = isTOUPeak()
    def outT = outdoorTempSensor?.currentValue("temperature")
    def outH = outdoorHumSensor?.currentValue("humidity")
    def outDP = calculateDewPoint(outT, outH)
    def anyTankFull = false
    
    // --- Global Humidity Alerts ---
    if (settings.enableGlobalAlert) {
        def thresh = settings.globalAlertThresh ?: 65
        def reqMins = settings.globalAlertMins ?: 120
        if (globalAvg >= thresh) {
            if (!state.globalHumAlertStart) state.globalHumAlertStart = now()
            else if (now() - state.globalHumAlertStart >= (reqMins * 60000) && !state.globalHumAlertNotified) {
                sendNotification("ENVIRONMENT ALERT: House Average Humidity has been at or above ${thresh}% for over ${reqMins} minutes.", "humidity")
                state.globalHumAlertNotified = true
            }
        } else {
            state.globalHumAlertStart = null
            state.globalHumAlertNotified = false
        }
    }

    for (int i = 1; i <= 8; i++) {
        if (!settings["z${i}Name"]) continue
        
        // Multi-Device Array Safeties
        def h = settings["z${i}Hum"]?.currentValue("humidity")
        def t = settings["z${i}Temp"]?.currentValue("temperature")
        
        def dehum = settings["z${i}Dehum"]
        def isHardwareOn = dehum ? dehum.any { it.currentValue("switch") == "on" } : false
        
        def exFan = settings["z${i}ExhaustFan"]
        def isFanOn = exFan ? exFan.any { it.currentValue("switch") == "on" } : false
        
        def leakSensors = settings["z${i}Leak"]
        def isLeaking = leakSensors ? leakSensors.any { it.currentValue("water") == "wet" } : false
        
        def windows = settings["z${i}Window"]
        def windowOpen = windows ? windows.any { it.currentValue("contact") == "open" } : false
        
        def tvs = settings["z${i}TV"]
        def isTvOn = tvs ? tvs.any { it.currentValue("switch") == "on" } : false
        
        def isCooling = settings["z${i}Thermostat"]?.currentValue("thermostatOperatingState")?.toLowerCase()?.contains("cool") ?: false
        def inDP = calculateDewPoint(t, h)
        def isAppControlled = state["z${i}AppControlled"] ?: false
        def isShared = settings["z${i}ExternalCtrl"]
        
        // Mode Check
        def allowedModes = settings["z${i}Modes"]
        def modeRestricted = allowedModes ? !allowedModes.contains(location.mode) : false

        // --- Zone Specific Humidity Alerts ---
        if (settings["z${i}EnableAlert"] && h != null) {
            def zThresh = settings["z${i}AlertThresh"] ?: 70
            def zReqMins = settings["z${i}AlertMins"] ?: 60
            if (h >= zThresh) {
                if (!state["z${i}HumAlertStart"]) state["z${i}HumAlertStart"] = now()
                else if (now() - state["z${i}HumAlertStart"] >= (zReqMins * 60000) && !state["z${i}HumAlertNotified"]) {
                    sendNotification("ENVIRONMENT ALERT: ${settings["z${i}Name"]} humidity has been at or above ${zThresh}% for over ${zReqMins} minutes.", "humidity")
                    state["z${i}HumAlertNotified"] = true
                }
            } else {
                state["z${i}HumAlertStart"] = null
                state["z${i}HumAlertNotified"] = false
            }
        }

        // --- Shared App Control Override ---
        if (isHardwareOn && !isAppControlled && isShared && !isLeaking) {
            if (state["z${i}LogicNote"] != "Externally Controlled") logAction("ZONE ${i}: Dehumidifier is ON. Triggered by external app. Suspending local overrides.")
            state["z${i}LogicNote"] = "Externally Controlled"
            calculateFinancials(i, true)
            state["z${i}RunStart"] = now()
            continue
        }

        // 1. ASHRAE Tracking
        if (h > 70) state["z${i}RiskScore"] = Math.min(100, (state["z${i}RiskScore"] ?: 0) + 2)
        else if (h < 50) state["z${i}RiskScore"] = Math.max(0, (state["z${i}RiskScore"] ?: 0) - 5)

        // 2. Rapid Cooling Calculation
        def rapidCoolTrigger = false
        if (settings["z${i}EnableRapidCool"] && t != null) {
            def maxT = state["z${i}MaxTemp"]
            def maxTTime = state["z${i}MaxTempTime"]
            def windowMs = (settings["z${i}TempTime"] ?: 60) * 60000
            def dropThresh = settings["z${i}TempDrop"] ?: 3.0
            
            if (maxT == null || maxTTime == null || (now() - maxTTime) > windowMs || t > maxT) {
                state["z${i}MaxTemp"] = t
                state["z${i}MaxTempTime"] = now()
                maxT = t
            }
            if ((maxT - t) >= dropThresh && !isCooling) rapidCoolTrigger = true 
        }

        // 3. TANK FULL DIAGNOSTICS
        def method = settings["z${i}TankMethod"]
        if (method == "Power Meter" && isHardwareOn) {
            def powerMeters = settings["z${i}Power"]
            def watts = powerMeters ? powerMeters.sum { it.currentValue("power") ?: 0 } : 0
            def threshold = settings["z${i}ActiveWatts"] ?: 100
            
            if (watts < threshold) {
                if (!state["z${i}LowPowerStart"]) state["z${i}LowPowerStart"] = now()
                else if ((now() - state["z${i}LowPowerStart"]) > 300000) { // 5 mins low power
                    state["z${i}TankFull"] = true
                }
            } else {
                state["z${i}LowPowerStart"] = null
                if (state["z${i}TankFull"]) resetTankFullFlag(i, "Power Meter Spike (Compressor On)")
            }
        } 
        else if (method == "Humidity Stall") {
            if (isHardwareOn && !state["z${i}TankFull"]) {
                if (!state["z${i}StallStart"]) {
                    state["z${i}StallStart"] = now()
                    state["z${i}StallBaseRH"] = h
                } else if (!isCooling) {
                    def baseRH = state["z${i}StallBaseRH"]
                    def reqDrop = settings["z${i}StallDrop"] ?: 5
                    def limitMins = settings["z${i}StallMins"] ?: 90
                    
                    if ((baseRH - h) >= reqDrop) {
                        state["z${i}StallStart"] = now()
                        state["z${i}StallBaseRH"] = h // Progress made, reset timer
                    } else if ((now() - state["z${i}StallStart"]) > (limitMins * 60000)) {
                        state["z${i}TankFull"] = true
                        state["z${i}TankMaxRH"] = h 
                    }
                }
            } else if (!isHardwareOn) {
                 state["z${i}StallStart"] = null
            }
            
            // Auto-Reset Logic
            if (state["z${i}TankFull"]) {
                def maxRH = state["z${i}TankMaxRH"] ?: h
                if (h > maxRH) state["z${i}TankMaxRH"] = h // Track the spike
                else if (h <= (maxRH - 3) && !isCooling) {
                    resetTankFullFlag(i, "Humidity Drop (AC Off) - Auto Clear")
                }
            }
        }
        
        if (state["z${i}TankFull"]) {
            anyTankFull = true
            if (!state["z${i}TankNotified"]) {
                sendNotification("🚰 TANK FULL: Zone ${i} (${settings["z${i}Name"]}) requires emptying.", "tank")
                state["z${i}TankNotified"] = true
            }
        }

        // 4. LOGIC CASCADE
        def shouldRun = false
        def reason = "All targets met."
        def useFreeDehum = false
        
        if (isLeaking) {
            shouldRun = true
            reason = "EMERGENCY: Water Leak Detected."
            if (!state["z${i}LeakNotified"]) {
                sendNotification("CRITICAL ALERT: Water leak detected in Zone ${i} (${settings["z${i}Name"]}). Dehumidifier forced ON.", "leak")
                state["z${i}LeakNotified"] = true
            }
        } else {
            state["z${i}LeakNotified"] = false
            
            if (windowOpen) {
                shouldRun = false
                reason = "Window/Door Open."
            } else if (state["z${i}TankFull"]) {
                shouldRun = true // Keeps smart plug on for auto-reset detection
                reason = "TANK FULL (Idling pending empty)."
            } else if (modeRestricted) {
                shouldRun = false
                reason = "Location Mode Restricted."
            } else {
                
                // Targets
                if (rapidCoolTrigger) {
                    shouldRun = true
                    reason = "Preemptive: Rapid temp drop detected."
                }
                else if (!shouldRun && winterActive) {
                    def wPoint = winterHumSetpoint ?: 35
                    def wDB = winterShieldDB ?: 3
                    if (h >= wPoint) { shouldRun = true; reason = "Winter Window Shield Active (${wPoint}% goal)." }
                    else if (isHardwareOn && h > (wPoint - wDB)) { shouldRun = true; reason = "Maintaining Winter Shield Deadband." }
                }
                else if (!shouldRun && avgTrigger) {
                    shouldRun = true
                    reason = "Averaging System Triggered (House Avg: ${globalAvg}%)."
                }
                else if (!shouldRun) {
                    def tvOffset = isTvOn ? (settings["z${i}TVOffset"] ?: 5) : 0
                    def forceSave = saverActive || (settings.enableTOU && isPeak && settings.forceSaveOnPeak)
                    def useComfort = !forceSave && settings["z${i}EnableComfort"]
                    def useSaving = settings["z${i}EnableSaving"] || forceSave
                    
                    if (useComfort) {
                        def cp = (settings["z${i}ComfortPoint"] ?: 45) + tvOffset
                        def cdb = settings["z${i}ComfortDB"] ?: 3
                        if (h >= cp) { shouldRun = true; reason = "Comfort limit (${cp}%) exceeded" + (isTvOn ? " (TV Active)." : ".") }
                        else if (isHardwareOn && h > (cp - cdb)) { shouldRun = true; reason = "Comfort deadband." }
                    } 
                    else if (useSaving) {
                        def sp = (settings["z${i}SavePoint"] ?: 60) + tvOffset
                        def sdb = settings["z${i}SaveDB"] ?: 5
                        if (h >= sp) { shouldRun = true; reason = "Savings limit (${sp}%) exceeded" + (isTvOn ? " (TV Active)." : ".") }
                        else if (isHardwareOn && h > (sp - sdb)) { shouldRun = true; reason = "Savings deadband." }
                    }
                }
                
                // ENTHALPY / FREE DEHUMIDIFICATION CHECK
                if (shouldRun && exFan && outDP != null && inDP != null) {
                    if (outDP <= (inDP - 2.0)) { 
                        useFreeDehum = true
                        reason += " (Free Dehum via Enthalpy: Out DP ${outDP}° < In DP ${inDP}°)"
                    }
                }
            }
        }

        // --- COMPRESSOR PROTECTION (Minimum Run Time) ---
        if (!shouldRun && isHardwareOn && isAppControlled && settings["z${i}CompProtect"] && !isLeaking && !windowOpen) {
            def minRunMs = (settings["z${i}MinRun"] ?: 15) * 60000
            def runTimeMs = state["z${i}RunStart"] ? (now() - state["z${i}RunStart"]) : 0
            if (runTimeMs < minRunMs) {
                shouldRun = true
                useFreeDehum = false // Force compressor to finish its cycle
                def minsRemaining = Math.round((minRunMs - runTimeMs) / 60000.0)
                reason = "Compressor Protection: Forcing completion (${minsRemaining}m remaining)."
            }
        }

        // 5. EXECUTION & ROI
        if (shouldRun) {
            if (state["z${i}TankFull"]) {
                // Keep power to plug, but it's not actually running
                if (!isHardwareOn) dehum?.each { it.on() }
                if (isFanOn) exFan?.each { it.off() }
                state["z${i}LogicNote"] = "Tank Full (Waiting)"
                state["z${i}FreeDehumOn"] = false
            } else if (useFreeDehum) {
                if (isHardwareOn) dehum?.each { it.off() }
                if (!isFanOn) exFan?.each { it.on() }
                state["z${i}LogicNote"] = "Free Dehum Active"
                state["z${i}FreeDehumOn"] = true
                state["z${i}AppControlled"] = true
            } else {
                if (!isHardwareOn) dehum?.each { it.on() }
                if (isFanOn) exFan?.each { it.off() }
                state["z${i}LogicNote"] = "Active: ${reason}"
                state["z${i}FreeDehumOn"] = false
                state["z${i}AppControlled"] = true
            }
            
            if (!state["z${i}RunStart"]) state["z${i}RunStart"] = now()
            calculateFinancials(i, true)
        } else {
            if (isHardwareOn || isFanOn) {
                if (isAppControlled || (!isShared && !isLeaking)) {
                    if (isHardwareOn) dehum?.each { it.off() }
                    if (isFanOn) exFan?.each { it.off() }
                    state["z${i}AppControlled"] = false
                    calculateFinancials(i, true)
                    state["z${i}RunStart"] = now()
                    state["z${i}LogicNote"] = "Idle"
                    state["z${i}FreeDehumOn"] = false
                }
            } else {
                calculateFinancials(i, false)
                state["z${i}RunStart"] = now()
                state["z${i}LogicNote"] = "Idle"
            }
        }
        
        // 6. Filter Evaluation
        def runLimit = settings["z${i}FilterLimit"] ?: 360
        def currentHours = state["z${i}FilterRunHours"] ?: 0.0
        if (currentHours >= runLimit && !state["z${i}FilterNotified"]) {
            sendNotification("Maintenance Alert: Zone ${i} (${settings["z${i}Name"]}) requires filter cleaning.", "filter")
            state["z${i}FilterNotified"] = true
        }
    }
    
    // Sync Global Tank Switch
    if (globalTankSwitch) {
        if (anyTankFull && globalTankSwitch.currentValue("switch") == "off") globalTankSwitch.on()
        else if (!anyTankFull && globalTankSwitch.currentValue("switch") == "on") globalTankSwitch.off()
    }
}

def calculateFinancials(zone, isEndingCycle) {
    if (!state["z${zone}RunStart"]) return
    def durationHours = (now() - state["z${zone}RunStart"]) / 3600000.0
    def watts = settings["z${zone}Watts"] ?: 500
    
    // TOU Support
    def isPeak = isTOUPeak()
    def rate = isPeak ? (settings.peakRate ?: 0.20) : (settings.offPeakRate ?: 0.10)
    def actualWatts = state["z${zone}FreeDehumOn"] ? 30.0 : watts
    def amt = (actualWatts / 1000.0) * durationHours * rate
    
    if (isEndingCycle && !state["z${zone}TankFull"]) {
        state["z${zone}TotalSpend"] = (state["z${zone}TotalSpend"] ?: 0.0) + amt
        state["z${zone}FilterRunHours"] = (state["z${zone}FilterRunHours"] ?: 0.0) + durationHours
    } else if (!isEndingCycle) {
        state["z${zone}TotalSave"] = (state["z${zone}TotalSave"] ?: 0.0) + amt
    }
}

def calculateZoneState(zone) {
    def currHum = settings["z${zone}Hum"]?.currentValue("humidity") ?: 0
    def currTemp = settings["z${zone}Temp"]?.currentValue("temperature")
    
    def dehum = settings["z${zone}Dehum"]
    def isHardwareOn = dehum ? dehum.any { it.currentValue("switch") == "on" } : false
    
    def leakSensors = settings["z${zone}Leak"]
    def isLeaking = leakSensors ? leakSensors.any { it.currentValue("water") == "wet" } : false
    
    def windows = settings["z${zone}Window"]
    def windowOpen = windows ? windows.any { it.currentValue("contact") == "open" } : false
    
    def tvs = settings["z${zone}TV"]
    def isTvOn = tvs ? tvs.any { it.currentValue("switch") == "on" } : false
    
    def inDP = calculateDewPoint(currTemp, currHum)
    
    def tvOffset = isTvOn ? (settings["z${zone}TVOffset"] ?: 5) : 0
    def forceSave = isEnergySaverActive() || (settings.enableTOU && isTOUPeak() && settings.forceSaveOnPeak)
    
    def target = 100
    if (isWinterShieldActive()) target = winterHumSetpoint ?: 35
    else if (forceSave || settings["z${zone}EnableSaving"]) target = (settings["z${zone}SavePoint"] ?: 60) + tvOffset
    else if (settings["z${zone}EnableComfort"]) target = (settings["z${zone}ComfortPoint"] ?: 45) + tvOffset
    
    def rScore = state["z${zone}RiskScore"] ?: 0
    def dur = state["z${zone}RunStart"] ? "${Math.round((now() - state["z${zone}RunStart"]) / 60000)} mins" : "Idle"
    
    def fLimit = settings["z${zone}FilterLimit"] ?: 360
    def fCurrent = state["z${zone}FilterRunHours"] ?: 0.0
    
    def allowedModes = settings["z${zone}Modes"]
    def modeRestricted = allowedModes ? !allowedModes.contains(location.mode) : false
    def statusNote = state["z${zone}LogicNote"] ?: "Standby"
    if (!isHardwareOn && modeRestricted) statusNote = "Mode Restricted"

    return [
        currHum: currHum,
        currTemp: currTemp,
        dewPoint: inDP,
        spread: (currTemp != null && inDP != null) ? (Math.round((currTemp - inDP) * 10.0) / 10.0) : null,
        target: target,
        riskScore: rScore, 
        riskLevel: (rScore > 80 ? "DANGER" : rScore > 50 ? "WARNING" : "SAFE"), 
        duration: dur, 
        status: statusNote, 
        filterLife: Math.max(0, Math.round(100 - ((fCurrent / fLimit) * 100))),
        hardwareOn: isHardwareOn,
        freeDehumOn: state["z${zone}FreeDehumOn"] ?: false,
        tankFull: state["z${zone}TankFull"] ?: false,
        windowOpen: windowOpen,
        isLeaking: isLeaking
    ]
}

def sendNotification(msg, type = "general") {
    def shouldSend = true
    if (type != "leak" && settings.notifyStartTime && settings.notifyEndTime) {
        shouldSend = timeOfDayIsBetween(settings.notifyStartTime, settings.notifyEndTime, new Date(), location.timeZone)
    }

    if (shouldSend) {
        def devices = []
        if (type == "leak") devices = settings.leakNotifyDevices
        else if (type == "tank") devices = settings.tankNotifyDevices
        else if (type == "humidity") devices = settings.humNotifyDevices
        else if (type == "filter") devices = settings.filterNotifyDevices
        
        if (devices) devices.each { it.deviceNotification(msg) }
    }
    logAction(msg)
}

def logAction(msg) {
    if (txtEnable) log.info "${app.label}: ${msg}"
    def hist = state.eventLog ?: []
    hist.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    state.eventLog = hist.take(25)
}

def getRiskColor(score) { return score > 80 ? "#cc0000" : score > 50 ? "#e67e22" : "#27ae60" }

def installed() { initialize() }
def updated() { initialize() }

def hubRebootHandler(evt) {
    logAction("SYSTEM ALERT: Hub reboot detected. Re-initializing.")
    initialize()
    evaluateZones()
}

def handleMasterSwitch(evt) {
    if (evt.value == "off") {
        logAction("Master Enable Switch turned OFF. App suspended. Halting active dehumidifiers...")
        for (int i = 1; i <= 8; i++) {
            if (settings["z${i}Name"] && state["z${i}AppControlled"]) {
                settings["z${i}Dehum"]?.each { it.off() }
                settings["z${i}ExhaustFan"]?.each { it.off() }
                state["z${i}AppControlled"] = false
                state["z${i}LogicNote"] = "Master Disabled"
                calculateFinancials(i, true)
                state["z${i}RunStart"] = now()
            }
        }
    } else {
        logAction("Master Enable Switch turned ON. Resuming operations.")
        evaluateZones()
    }
}

def initialize() {
    unschedule()
    subscribe(location, "systemStart", "hubRebootHandler")
    subscribe(location, "mode", "handleEnvChange")
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "handleMasterSwitch")
    
    for (int i = 1; i <= 8; i++) { 
        if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", "handleEnvChange") 
        if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", "handleEnvChange")
        if (settings["z${i}Leak"]) subscribe(settings["z${i}Leak"], "water", "handleEnvChange")
        if (settings["z${i}Thermostat"]) subscribe(settings["z${i}Thermostat"], "thermostatOperatingState", "handleEnvChange")
        if (settings["z${i}TV"]) subscribe(settings["z${i}TV"], "switch", "handleEnvChange")
        if (settings["z${i}Window"]) subscribe(settings["z${i}Window"], "contact", "handleEnvChange")
        if (settings["z${i}Power"]) subscribe(settings["z${i}Power"], "power", "handleEnvChange")
    }
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", "handleEnvChange")
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", "handleEnvChange")
    if (energyModeSwitch) subscribe(energyModeSwitch, "switch", "handleEnvChange")
    
    runEvery5Minutes("evaluateZones")
}

def handleEnvChange(evt) { evaluateZones() }
