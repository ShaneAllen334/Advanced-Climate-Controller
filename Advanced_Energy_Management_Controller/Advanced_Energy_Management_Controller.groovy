/**
 * Advanced Energy Management Controller
 */
definition(
    name: "Advanced Energy Management Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "An eye-opening energy dashboard that tracks cost, detects power spikes, and warns of creeping averages indicating hardware failure.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "clearDataPage")
    page(name: "maintenancePage")
    page(name: "doResetPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Energy Management Core", install: true, uninstall: true) {
        
        section("Dashboard Actions") {
            href(name: "refreshPage", title: "🔄 Refresh Data", page: "mainPage")
            href(name: "maintenancePage", title: "🛠️ Hardware Maintenance Reset", description: "Clear health warnings after servicing an appliance.", page: "maintenancePage")
            href(name: "clearDataPage", title: "🗑️ Clear All Data", description: "Reset all history, baselines, and counters.", page: "clearDataPage")
        }

        section("Live Financial & Health Dashboard") {
            def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.13
            def statusText = "<b>Appliance Status & Analytics</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>State (Reason)</th><th style='padding: 8px;'>Current Power</th><th style='padding: 8px;'>7-Day Cost</th><th style='padding: 8px;'>Health Status</th></tr>"
            
            def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
            
            appliances.each { key, name ->
                def sw = settings["${key}Switch"]
                def pMeter = settings["${key}Power"]
                def eMeter = settings["${key}Energy"]
                
                if (pMeter && eMeter) {
                    def currentPower = pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0
                    def currentEnergy = eMeter.currentValue('energy')?.toString()?.toDouble() ?: 0.0
                    
                    // Switch State & Context
                    def switchState = sw ? (sw.currentValue("switch") == "on" ? "<span style='color: green; font-weight: bold;'>ON</span>" : "<span style='color: red; font-weight: bold;'>OFF</span>") : "<span style='color: gray;'>N/A</span>"
                    def contextMsg = state["${key}_context"] ?: "Normal"
                    def stateDisplay = "${switchState}<br><span style='font-size: 10px; color: gray;'>${contextMsg}</span>"

                    // Cost Math
                    def baselineEnergy = state["${key}_startEnergy"] ?: currentEnergy
                    def usedKwh = currentEnergy - baselineEnergy
                    if (usedKwh < 0) usedKwh = 0 
                    def estCost = usedKwh * kwhRate
                    def costStr = String.format("\$%.2f", estCost)
                    
                    // Health Check & Compressor Status
                    def health = "<span style='color: green;'>GOOD</span>"
                    def struggle = state["${key}_struggleCount"] ?: 0
                    
                    if (state["${key}_creepWarning"]) health = "<span style='color: orange;'>CREEPING WATTS</span>"
                    if (struggle >= 3 && struggle < 6) health = "<span style='color: orange;'>CLEAN COILS</span>"
                    if (struggle >= 6) health = "<span style='color: red;'>STRUGGLING</span>"
                    if (state["${key}_spikeWarning"]) health = "<span style='color: red;'>SPIKE DETECTED</span>"
                    if (state["${key}_tempWarningActive"]) health = "<span style='color: red;'>HIGH TEMP ALERT</span>"
                    if (state["${key}_tempCreepWarning"]) health = "<span style='color: orange;'>TEMP CREEPING</span>"
                    
                    def powerColor = currentPower > 10 ? "blue" : "black"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px;'>${stateDisplay}</td><td style='padding: 8px; color: ${powerColor};'>${currentPower} W</td><td style='padding: 8px; color: red;'>${costStr}</td><td style='padding: 8px; font-weight: bold;'>${health}</td></tr>"
                }
            }
            statusText += "</table>"
            
            // Active Cycles & Usage (7-Day)
            statusText += "<b>Active Cycles & Usage Stats</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #f4f8ff; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #dbeaff; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>State</th><th style='padding: 8px;'>Last Run</th><th style='padding: 8px;'>7-Day Runs</th></tr>"
            
            def cycleAppliances = ["hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
            
            cycleAppliances.each { key, name ->
                def isRunning = state["${key}_isRunning"] ? "<span style='color: blue; font-weight: bold;'>RUNNING</span>" : "<span style='color: gray;'>IDLE</span>"
                if (state["${key}_startPending"]) isRunning = "<span style='color: #8a6d3b; font-weight: bold;'>STARTING...</span>"
                if (state["${key}_stopPending"]) isRunning = "<span style='color: orange; font-weight: bold;'>PAUSED</span>"
                
                def lastRunStr = "N/A"
                if (state["${key}_isRunning"] && state["${key}_cycleStartTime"]) {
                    def currentRunMs = now() - state["${key}_cycleStartTime"]
                    def currentRunMins = Math.round(currentRunMs / 60000.0)
                    lastRunStr = "Running (${currentRunMins} min)"
                } else if (state["${key}_lastRunLengthMins"]) {
                    lastRunStr = "${Math.round(state["${key}_lastRunLengthMins"])} min"
                }
                
                def runCount = state["${key}_7DayRunCount"] ?: 0
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px;'>${isRunning}</td><td style='padding: 8px;'>${lastRunStr}</td><td style='padding: 8px;'>${runCount}</td></tr>"
            }
            statusText += "</table>"

            // Compressor Cycle Stats (7-Day & Today)
            statusText += "<b>Compressor Cycle Stats</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcf8e3; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #faebcc; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>State</th><th style='padding: 8px;'>Avg Cycle</th><th style='padding: 8px;'>Runs (Today/7D)</th><th style='padding: 8px;'>Temps (Room Avg / Out Max)</th></tr>"
            
            def compressorAppliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer"]
            
            compressorAppliances.each { key, name ->
                def isRunning = state["${key}_isRunning"] ? "<span style='color: #d58512; font-weight: bold;'>COOLING</span>" : "<span style='color: gray;'>IDLE</span>"
                if (state["${key}_startPending"]) isRunning = "<span style='color: #8a6d3b; font-weight: bold;'>STARTING...</span>"
                if (state["${key}_stopPending"]) isRunning = "<span style='color: #d58512; font-weight: bold;'>COOLING</span>"
                
                def runCount7D = state["${key}_7DayRunCount"] ?: 0
                def runCountToday = state["${key}_todayRunCount"] ?: 0
                def totalMins = state["${key}_7DayTotalCycleMins"] ?: 0.0
                def avgCycleStr = runCount7D > 0 ? "${Math.round(totalMins / runCount7D)} min" : "N/A"
             
                def rTempAvg = state["${key}_dailyAvgRoomTemp"]
                def oTempMax = state["${key}_dailyMaxOutsideTemp"]
                
                def roomStr = (rTempAvg && rTempAvg != 0.0) ? "${Math.round(rTempAvg?.toString()?.toDouble())}°" : "--"
                def outStr = (oTempMax && oTempMax != -100.0) ? "${Math.round(oTempMax?.toString()?.toDouble())}°" : "--"
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px;'>${isRunning}</td><td style='padding: 8px;'>${avgCycleStr}</td><td style='padding: 8px;'>${runCountToday} / ${runCount7D}</td><td style='padding: 8px;'>${roomStr} / ${outStr}</td></tr>"
            }
            statusText += "</table>"
            
            // ROI Table for Scheduled Savings & Maintenance
            statusText += "<b>Financial Savings (ROI)</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #eef9f0; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #dcedc8; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>Sch. Savings</th><th style='padding: 8px;'>Maint. Savings</th><th style='padding: 8px;'>Total (7D)</th></tr>"
            
            def totalSavings = 0.0
            appliances.each { key, name ->
                def schSavings = state["${key}_roiSavings"] ?: 0.0
                def maintSavings = state["${key}_maintRoiSavings"] ?: 0.0
                def appTotal = schSavings + maintSavings
                totalSavings += appTotal
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px; color: green;'>\$${String.format("%.2f", schSavings)}</td><td style='padding: 8px; color: green;'>\$${String.format("%.2f", maintSavings)}</td><td style='padding: 8px; color: green; font-weight: bold;'>\$${String.format("%.2f", appTotal)}</td></tr>"
            }
            statusText += "<tr style='background-color: #c5e1a5;'><td style='padding: 8px;' colspan='3'><b>Total Saved</b></td><td style='padding: 8px; color: green; font-weight: bold;'>\$${String.format("%.2f", totalSavings)}</td></tr>"
            statusText += "</table>"
            
            paragraph statusText
        }

        section("Global Core Settings") {
            input "costPerKwh", "decimal", title: "Electricity Cost (\$ per kWh)", required: true, defaultValue: 0.13, description: "Your default rate is \$0.13."
            input "enableMaintenanceRoi", "bool", title: "Track ROI for Maintenance (Coil Cleaning, etc.)?", defaultValue: true, required: false, description: "Calculates savings when an appliance runs more efficiently after a health reset."
        }

        section("Appliance Scheduling & Shutdown") {
            input "scheduleStart", "time", title: "Time to turn appliances OFF", required: false
            input "scheduleEnd", "time", title: "Time to turn appliances ON", required: false
            input "safeShutdownThreshold", "number", title: "Safe Shutdown Power Threshold (Watts)", defaultValue: 15, required: true, description: "Appliance stays on if drawing more than this."
            paragraph "Note: Refrigerator and Chest Freezer are permanently excluded from time-based schedule shutdowns."
        }
        
        section("Manual Override Button") {
            input "overrideButton", "capability.pushableButton", title: "Override Button(s)", required: false, multiple: true
            input "legacyOverrideButton", "capability.button", title: "Legacy Button Devices (Use this if your button didn't show in the list above)", required: false, multiple: true
            input "buttonNumber", "text", title: "Button Number(s) (Comma separated, e.g., 1, 2)", defaultValue: "1", required: false, description: "Leave blank to allow all buttons on the device."
            input "buttonAction", "enum", title: "Button Action(s)", options: ["pushed", "held", "doubleTapped", "released", "tapped", "multiTapped"], defaultValue: "pushed", required: false, multiple: true
            input "overrideModes", "mode", title: "Only allow override in specific modes?", required: false, multiple: true
        }
        
        section("Refrigerator", hideable: true, hidden: true) {
            input "refrigeratorSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "refrigeratorPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "refrigeratorEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "refrigeratorTemp", "capability.temperatureMeasurement", title: "Internal Temperature Sensor", required: false
            input "refrigeratorRoomTemp", "capability.temperatureMeasurement", title: "Room Temperature Sensor", required: false
            input "refrigeratorOutsideTemp", "capability.temperatureMeasurement", title: "Outside Air Temperature Sensor", required: false
            input "refrigeratorTempThreshold", "number", title: "Max Internal Temperature Alert Threshold (°F/°C)", defaultValue: 42, required: false
            input "refrigeratorSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 800, required: false
            input "refrigeratorRunWatts", "number", title: "Compressor Running Threshold (Watts)", defaultValue: 80, required: false, description: "Watts required to consider the compressor 'running'."
            input "refrigeratorStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "refrigeratorDebounce", "number", title: "Cycle Pause/Debounce Time (Minutes)", defaultValue: 5, required: false, description: "Wait this long after power drops before declaring cooling cycle complete."
            input "refrigeratorMaintenanceHours", "number", title: "Maintenance Alert Interval (Total Run Hours)", defaultValue: 2000, required: false
            
            paragraph "Active Protection: If this switch is ever turned OFF manually or by another app, it will be instantly forced back ON. (Mode-based shutdown has been disabled for safety)."
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "refrigeratorAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "refrigeratorAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "refrigeratorAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "refrigeratorPushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "refrigeratorPushEvents", "enum", title: "Send these events to Push:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false

            input "refrigeratorTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "refrigeratorTtsEvents", "enum", title: "Send these events to TTS:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false

            input "refrigeratorAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "refrigeratorAudioEvents", "enum", title: "Play track for these events:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false
            input "refrigeratorAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }
     
        section("Chest Freezer", hideable: true, hidden: true) {
            input "chestFreezerSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "chestFreezerPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "chestFreezerEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "chestFreezerTemp", "capability.temperatureMeasurement", title: "Internal Temperature Sensor", required: false
            input "chestFreezerRoomTemp", "capability.temperatureMeasurement", title: "Room Temperature Sensor", required: false
            input "chestFreezerOutsideTemp", "capability.temperatureMeasurement", title: "Outside Air Temperature Sensor", required: false
            input "chestFreezerTempThreshold", "number", title: "Max Internal Temperature Alert Threshold (°F/°C)", defaultValue: 15, required: false
            input "chestFreezerSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 800, required: false
            input "chestFreezerRunWatts", "number", title: "Compressor Running Threshold (Watts)", defaultValue: 80, required: false, description: "Watts required to consider the compressor 'running'."
            input "chestFreezerStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "chestFreezerDebounce", "number", title: "Cycle Pause/Debounce Time (Minutes)", defaultValue: 5, required: false, description: "Wait this long after power drops before declaring cooling cycle complete."
            input "chestFreezerMaintenanceHours", "number", title: "Maintenance Alert Interval (Total Run Hours)", defaultValue: 2000, required: false
            
            paragraph "Active Protection: If this switch is ever turned OFF manually or by another app, it will be instantly forced back ON. (Mode-based shutdown has been disabled for safety)."
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "chestFreezerAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "chestFreezerAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "chestFreezerAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "chestFreezerPushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "chestFreezerPushEvents", "enum", title: "Send these events to Push:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false

            input "chestFreezerTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "chestFreezerTtsEvents", "enum", title: "Send these events to TTS:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false

            input "chestFreezerAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "chestFreezerAudioEvents", "enum", title: "Play track for these events:", options: ["temp": "Temperature Alerts", "spike": "Power Spikes", "health": "Health & Maintenance", "protection": "Protection Force-ON"], multiple: true, required: false
            input "chestFreezerAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }
     
        section("Hot Water Heater", hideable: true, hidden: true) {
            input "hotWaterHeaterSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "hotWaterHeaterPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "hotWaterHeaterEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "hotWaterHeaterSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 6000, required: false
            input "hotWaterHeaterRunWatts", "number", title: "Heating Threshold (Watts)", defaultValue: 1000, required: false
            input "hotWaterHeaterStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "hotWaterHeaterDebounce", "number", title: "Heating Pause/Debounce Time (Minutes)", defaultValue: 5, required: false, description: "Wait this long after power drops before declaring cycle complete."
            
            paragraph "Mode-Based Power Control"
            input "hotWaterHeaterTurnOffModes", "mode", title: "Turn OFF switch when entering these modes:", required: false, multiple: true
            input "hotWaterHeaterTurnOnModes", "mode", title: "Turn ON switch when entering these modes:", required: false, multiple: true
            
            paragraph "⚠️ Cool Down / Dry Out Protection\nHigh-heat or water-based appliances need time after running to dissipate heat and run internal moisture-reduction fans. Cutting power immediately after a cycle can cause mold growth or overheat internal components. Adjust this duration carefully."
            input "hotWaterHeaterEnableCoolDown", "bool", title: "Enable Post-Cycle Cool Down/Dry Out?", defaultValue: true, required: false
            input "hotWaterHeaterCoolDownMins", "number", title: "Cool Down Duration (Minutes)", defaultValue: 120, required: false
            
            paragraph "Auto-Off Settings"
            input "hotWaterHeaterAutoOff", "bool", title: "Turn OFF switch automatically when heating cycle finishes?", defaultValue: false, required: false
            input "hotWaterHeaterAutoOffModes", "mode", title: "Only Auto-Off during these modes? (Leave blank for all)", required: false, multiple: true
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "hotWaterHeaterAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "hotWaterHeaterAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "hotWaterHeaterAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "hotWaterHeaterPushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "hotWaterHeaterPushEvents", "enum", title: "Send these events to Push:", options: ["cycle": "Heating Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "hotWaterHeaterTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "hotWaterHeaterTtsEvents", "enum", title: "Send these events to TTS:", options: ["cycle": "Heating Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "hotWaterHeaterAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "hotWaterHeaterAudioEvents", "enum", title: "Play track for these events:", options: ["cycle": "Heating Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false
            input "hotWaterHeaterAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }

        section("Washer / Dryer Combo", hideable: true, hidden: true) {
            input "washerDryerSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "washerDryerPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "washerDryerEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "washerDryerSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 3000, required: false
            input "washerDryerRunWatts", "number", title: "Cycle Running Threshold (Watts)", defaultValue: 20, required: false
            input "washerDryerStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "washerDryerDebounce", "number", title: "Cycle Pause/Debounce Time (Minutes)", defaultValue: 15, required: false, description: "Wait this long after power drops before declaring cycle complete."
            
            paragraph "Mode-Based Power Control"
            input "washerDryerTurnOffModes", "mode", title: "Turn OFF switch when entering these modes:", required: false, multiple: true
            input "washerDryerTurnOnModes", "mode", title: "Turn ON switch when entering these modes:", required: false, multiple: true
            
            paragraph "⚠️ Cool Down / Dry Out Protection\nHigh-heat or water-based appliances need time after running to dissipate heat and run internal moisture-reduction fans. Cutting power immediately after a cycle can cause mold growth or overheat internal components. Adjust this duration carefully."
            input "washerDryerEnableCoolDown", "bool", title: "Enable Post-Cycle Cool Down/Dry Out?", defaultValue: true, required: false
            input "washerDryerCoolDownMins", "number", title: "Cool Down Duration (Minutes)", defaultValue: 120, required: false
            
            paragraph "Auto-Off Settings"
            input "washerDryerAutoOff", "bool", title: "Turn OFF switch automatically when cycle finishes?", defaultValue: false, required: false
            input "washerDryerAutoOffModes", "mode", title: "Only Auto-Off during these modes? (Leave blank for all)", required: false, multiple: true
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "washerDryerAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "washerDryerAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "washerDryerAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "washerDryerPushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "washerDryerPushEvents", "enum", title: "Send these events to Push:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "washerDryerTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "washerDryerTtsEvents", "enum", title: "Send these events to TTS:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "washerDryerAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "washerDryerAudioEvents", "enum", title: "Play track for these events:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false
            input "washerDryerAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }
        
        section("Dishwasher", hideable: true, hidden: true) {
            input "dishwasherSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "dishwasherPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "dishwasherEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "dishwasherSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 1800, required: false
            input "dishwasherRunWatts", "number", title: "Cycle Running Threshold (Watts)", defaultValue: 15, required: false
            input "dishwasherStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "dishwasherDebounce", "number", title: "Cycle Pause/Debounce Time (Minutes)", defaultValue: 15, required: false, description: "Wait this long after power drops before declaring cycle complete."
            
            paragraph "Mode-Based Power Control"
            input "dishwasherTurnOffModes", "mode", title: "Turn OFF switch when entering these modes:", required: false, multiple: true
            input "dishwasherTurnOnModes", "mode", title: "Turn ON switch when entering these modes:", required: false, multiple: true
            
            paragraph "⚠️ Cool Down / Dry Out Protection\nHigh-heat or water-based appliances need time after running to dissipate heat and run internal moisture-reduction fans. Cutting power immediately after a cycle can cause mold growth or overheat internal components. Adjust this duration carefully."
            input "dishwasherEnableCoolDown", "bool", title: "Enable Post-Cycle Cool Down/Dry Out?", defaultValue: true, required: false
            input "dishwasherCoolDownMins", "number", title: "Cool Down Duration (Minutes)", defaultValue: 120, required: false
            
            paragraph "Auto-Off Settings"
            input "dishwasherAutoOff", "bool", title: "Turn OFF switch automatically when cycle finishes?", defaultValue: false, required: false
            input "dishwasherAutoOffModes", "mode", title: "Only Auto-Off during these modes? (Leave blank for all)", required: false, multiple: true
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "dishwasherAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "dishwasherAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "dishwasherAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "dishwasherPushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "dishwasherPushEvents", "enum", title: "Send these events to Push:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "dishwasherTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "dishwasherTtsEvents", "enum", title: "Send these events to TTS:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "dishwasherAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "dishwasherAudioEvents", "enum", title: "Play track for these events:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false
            input "dishwasherAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }
        
        section("Microwave", hideable: true, hidden: true) {
            input "microwaveSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "microwavePower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "microwaveEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "microwaveSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 2000, required: false
            input "microwaveRunWatts", "number", title: "Cycle Running Threshold (Watts)", defaultValue: 15, required: false
            input "microwaveStartDelay", "number", title: "Cycle Start Delay (Minutes)", defaultValue: 1, required: false, description: "Wait this long above the running threshold before logging a cycle."
            input "microwaveDebounce", "number", title: "Cycle Pause/Debounce Time (Minutes)", defaultValue: 1, required: false, description: "Wait this long after power drops before declaring cycle complete."
            
            paragraph "Mode-Based Power Control"
            input "microwaveTurnOffModes", "mode", title: "Turn OFF switch when entering these modes:", required: false, multiple: true
            input "microwaveTurnOnModes", "mode", title: "Turn ON switch when entering these modes:", required: false, multiple: true
            
            paragraph "⚠️ Cool Down / Dry Out Protection\nHigh-heat or water-based appliances need time after running to dissipate heat and run internal moisture-reduction fans. Cutting power immediately after a cycle can cause mold growth or overheat internal components. Adjust this duration carefully."
            input "microwaveEnableCoolDown", "bool", title: "Enable Post-Cycle Cool Down/Dry Out?", defaultValue: true, required: false
            input "microwaveCoolDownMins", "number", title: "Cool Down Duration (Minutes)", defaultValue: 120, required: false
            
            paragraph "Auto-Off Settings"
            input "microwaveAutoOff", "bool", title: "Turn OFF switch automatically when cycle finishes?", defaultValue: false, required: false
            input "microwaveAutoOffModes", "mode", title: "Only Auto-Off during these modes? (Leave blank for all)", required: false, multiple: true
            
            paragraph "Alert Gatekeeping (Time & Modes)"
            input "microwaveAlertModes", "mode", title: "Only send alerts during these modes? (Leave blank for all)", required: false, multiple: true
            input "microwaveAlertStartTime", "time", title: "Only send alerts after this time?", required: false
            input "microwaveAlertEndTime", "time", title: "Only send alerts before this time?", required: false
            
            paragraph "Granular Device Alerts"
            input "microwavePushNotification", "capability.notification", title: "Push Notification Device", required: false, multiple: true
            input "microwavePushEvents", "enum", title: "Send these events to Push:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "microwaveTtsDevice", "capability.speechSynthesis", title: "Sonos / TTS Device", required: false, multiple: true
            input "microwaveTtsEvents", "enum", title: "Send these events to TTS:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false

            input "microwaveAudioDevice", "capability.audioNotification", title: "Zooz / Audio Siren Device", required: false, multiple: true
            input "microwaveAudioEvents", "enum", title: "Play track for these events:", options: ["cycle": "Cycle Complete", "spike": "Power Spikes", "health": "Health & Maintenance"], multiple: true, required: false
            input "microwaveAudioTrack", "number", title: "Audio Track Number (For Sirens/Chimes)", defaultValue: 1, required: false
        }
    }
}

def maintenancePage() {
    dynamicPage(name: "maintenancePage", title: "Hardware Maintenance", install: false, uninstall: false) {
        def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
        
        section("Reset Appliance Health") {
            paragraph "Select an appliance to immediately reset its health warnings (e.g., 'Clean Coils', 'Creeping Watts', 'High Temp'). This will recalculate baselines but will keep your 7-Day run counts and financial savings intact."
            appliances.each { key, name ->
                href(name: "reset_${key}", title: "🛠️ Reset ${name}", description: "Clear all health & maintenance warnings for this appliance.", page: "doResetPage", params: [applianceKey: key, applianceName: name])
            }
        }
    }
}

def doResetPage(params) {
    if (params?.applianceKey) {
        def key = params.applianceKey
        def name = params.applianceName
        
        // Clear Warning States
        state.remove("${key}_struggleCount")
        state.remove("${key}_creepWarning")
        state.remove("${key}_spikeWarning")
        state.remove("${key}_spikePending")
        state.remove("${key}_tempWarningActive")
        state.remove("${key}_tempCreepWarning")
        state.remove("${key}_totalRunHours")
   
        // Capture inefficient state before reset for ROI tracking
        if (state["${key}_baselineAvg"] && state["${key}_avgPower"]) {
            def currentAvg = state["${key}_avgPower"].toString().toDouble()
            def baseline = state["${key}_baselineAvg"].toString().toDouble()
            if (currentAvg > baseline) {
                state["${key}_inefficientPowerMark"] = currentAvg
            }
        }

        // Recalibrate Baselines
        state.remove("${key}_baselineAvg")
        state.remove("${key}_idlePowerAvg")
        state.remove("${key}_baselineCycleMins")
        state.remove("${key}_baselineTemp")
        
        state["${key}_context"] = "Maintenance Reset"
        
        log.info "Maintenance reset performed for ${name}."
        
        dynamicPage(name: "doResetPage", title: "${name} Reset Complete", nextPage: "mainPage") {
            section() {
                paragraph "✅ The health and maintenance warnings for your ${name} have been successfully cleared.\n\nThe system has refreshed the baselines and will begin tracking its normal operations anew."
            }
        }
    } else {
        dynamicPage(name: "doResetPage", title: "Error", nextPage: "mainPage") {
            section() { paragraph "Appliance not found. Please try again." }
        }
    }
}

def clearDataPage() {
    clearAllData()
    dynamicPage(name: "clearDataPage", title: "Data Successfully Cleared", nextPage: "mainPage") {
        section() {
            paragraph "All stored baselines, historical run times, health statuses, and ROI savings have been permanently deleted.\n\nThe system has been re-initialized and is pulling fresh starting points for your hardware."
        }
    }
}

def clearAllData() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        state.remove("${key}_startEnergy")
        state.remove("${key}_avgPower")
        state.remove("${key}_idlePowerAvg")
        state.remove("${key}_roiSavings")
        state.remove("${key}_maintRoiSavings") // Cleared new maintenance ROI
        state.remove("${key}_inefficientPowerMark") // Cleared inefficient mark
        state.remove("${key}_totalRunHours")
        state.remove("${key}_struggleCount")
        state.remove("${key}_7DayRunCount")
        state.remove("${key}_todayRunCount")
        state.remove("${key}_7DayTotalCycleMins")
        state.remove("${key}_lastRunLengthMins")
        state.remove("${key}_stopPending")
        state.remove("${key}_startPending")
        state.remove("${key}_tentativeStartTime")
        state.remove("${key}_baselineAvg")
        state.remove("${key}_baselineCycleMins")
        state.remove("${key}_creepWarning")
        state.remove("${key}_spikeWarning")
        state.remove("${key}_spikePending")
        state.remove("${key}_isRunning")
        state.remove("${key}_cycleStartTime")
        state.remove("${key}_cycleEndTime")
        state.remove("${key}_offTimeStart")
        state.remove("${key}_context")
        state.remove("${key}_avgTemp")
        state.remove("${key}_baselineTemp")
        state.remove("${key}_tempWarningActive")
        state.remove("${key}_tempCreepWarning")
        state.remove("${key}_dailyAvgRoomTemp")
        state.remove("${key}_dailyMaxOutsideTemp")
    }
    
    log.info "All Energy Management Controller data has been reset by the user."
    initialize()
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        def pMeter = settings["${key}Power"]
        def eMeter = settings["${key}Energy"]
        def sw = settings["${key}Switch"]
        
        if (!state["${key}_startEnergy"] && eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
        if (!state["${key}_avgPower"]) state["${key}_avgPower"] = 0.0
        if (!state["${key}_idlePowerAvg"]) state["${key}_idlePowerAvg"] = 0.0
        if (!state["${key}_roiSavings"]) state["${key}_roiSavings"] = 0.0
        if (!state["${key}_maintRoiSavings"]) state["${key}_maintRoiSavings"] = 0.0
        if (!state["${key}_totalRunHours"]) state["${key}_totalRunHours"] = 0.0
        if (!state["${key}_struggleCount"]) state["${key}_struggleCount"] = 0
        if (!state["${key}_7DayRunCount"]) state["${key}_7DayRunCount"] = 0
        if (!state["${key}_todayRunCount"]) state["${key}_todayRunCount"] = 0
        if (!state["${key}_7DayTotalCycleMins"]) state["${key}_7DayTotalCycleMins"] = 0.0
        if (!state["${key}_lastRunLengthMins"]) state["${key}_lastRunLengthMins"] = 0.0
        if (!state["${key}_context"]) state["${key}_context"] = "Normal"
        if (!state["${key}_cycleEndTime"]) state["${key}_cycleEndTime"] = 0
        
        // Force-fetch current temperatures on startup instead of waiting for events
        if (state["${key}_dailyAvgRoomTemp"] == null || state["${key}_dailyAvgRoomTemp"] == 0.0) {
            def rSens = settings["${key}RoomTemp"]
            state["${key}_dailyAvgRoomTemp"] = rSens ? (rSens.currentValue("temperature")?.toString()?.toDouble() ?: 0.0) : 0.0
        }
        if (state["${key}_dailyMaxOutsideTemp"] == null || state["${key}_dailyMaxOutsideTemp"] == -100.0) {
            def oSens = settings["${key}OutsideTemp"]
            state["${key}_dailyMaxOutsideTemp"] = oSens ? (oSens.currentValue("temperature")?.toString()?.toDouble() ?: -100.0) : -100.0
        }
        
        state["${key}_stopPending"] = false
        state["${key}_startPending"] = false
        state["${key}_spikePending"] = false
        
        if (pMeter) subscribe(pMeter, "power", powerHandler)
        if (sw) subscribe(sw, "switch", universalSwitchHandler)
    }

    // Always-On Protection for Critical Hardware
    if (settings["refrigeratorSwitch"]) {
        subscribe(settings["refrigeratorSwitch"], "switch", alwaysOnProtectionHandler)
    }
    if (settings["chestFreezerSwitch"]) {
        subscribe(settings["chestFreezerSwitch"], "switch", alwaysOnProtectionHandler)
    }
    
    // Internal Temperature Subscriptions
    if (settings["refrigeratorTemp"]) {
        subscribe(settings["refrigeratorTemp"], "temperature", tempHandler)
    }
    if (settings["chestFreezerTemp"]) {
        subscribe(settings["chestFreezerTemp"], "temperature", tempHandler)
    }
    
    // External/Environmental Temperature Subscriptions
    if (settings["refrigeratorRoomTemp"]) subscribe(settings["refrigeratorRoomTemp"], "temperature", roomTempHandler)
    if (settings["refrigeratorOutsideTemp"]) subscribe(settings["refrigeratorOutsideTemp"], "temperature", outsideTempHandler)
    if (settings["chestFreezerRoomTemp"]) subscribe(settings["chestFreezerRoomTemp"], "temperature", roomTempHandler)
    if (settings["chestFreezerOutsideTemp"]) subscribe(settings["chestFreezerOutsideTemp"], "temperature", outsideTempHandler)
    
    // Scheduling
    if (scheduleStart) schedule(scheduleStart, triggerTurnOff)
    if (scheduleEnd) schedule(scheduleEnd, triggerTurnOn)
    
    // Per-Appliance Mode Tracking 
    subscribe(location, "mode", modeChangeHandler)
    
    // Multi-Button / Multi-Action Override
    def allOverrideButtons = []
    if (overrideButton) allOverrideButtons += overrideButton
    if (legacyOverrideButton) allOverrideButtons += legacyOverrideButton

    if (allOverrideButtons) {
        def actions = buttonAction ?: ["pushed"]
        if (!(actions instanceof List)) actions = [actions]
       
        actions.each { action ->
            subscribe(allOverrideButtons, action, buttonHandler)
            // Support for legacy ST event formats
            subscribe(allOverrideButtons, "button.${action}", buttonHandler)
        }
    }
    
    schedule("0 0 0 * * ?", resetDailyCounters)
    schedule("0 5 0 ? * SUN", resetWeeklyCounters)
    schedule("0 0 2 * * ?", dailyHealthCheck) 
}

def roomTempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator", "chestFreezer"]
    
    targets.each { key ->
        if (settings["${key}RoomTemp"]?.id == deviceId) {
            def avg = state["${key}_dailyAvgRoomTemp"]?.toString()?.toDouble() ?: 0.0
            if (avg == 0.0) {
                  state["${key}_dailyAvgRoomTemp"] = currentTemp
            } else {
                state["${key}_dailyAvgRoomTemp"] = (avg * 0.95) + (currentTemp * 0.05)
            }
        }
    }
}

def outsideTempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator", "chestFreezer"]
  
    targets.each { key ->
        if (settings["${key}OutsideTemp"]?.id == deviceId) {
            def currentMax = state["${key}_dailyMaxOutsideTemp"]?.toString()?.toDouble() ?: -100.0
            if (currentTemp > currentMax) {
                state["${key}_dailyMaxOutsideTemp"] = currentTemp
            }
        }
    }
}

def resetDailyCounters() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        state["${key}_todayRunCount"] = 0
        
        // Reset max outside temp to current value (if sensor exists) or default -100
        def outSensor = settings["${key}OutsideTemp"]
        state["${key}_dailyMaxOutsideTemp"] = outSensor ? (outSensor.currentValue("temperature")?.toString()?.toDouble() ?: -100.0) : -100.0
        
        // Let room temp continue rolling average, or snap it to current to start fresh for the day
        def roomSensor = settings["${key}RoomTemp"]
        if (roomSensor) {
            state["${key}_dailyAvgRoomTemp"] = roomSensor.currentValue("temperature")?.toString()?.toDouble() ?: 0.0
        }
    }
}

def universalSwitchHandler(evt) {
    def deviceId = evt.device.id
    def evtValue = evt.value
    def isPhysical = evt.isPhysical()
    
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        if (settings["${key}Switch"]?.id == deviceId) {
            if (state["${key}_systemActionPending"]) {
                state["${key}_systemActionPending"] = false
            } else {
                state["${key}_context"] = isPhysical ? "Physical Switch" : "External App / Manual"
            }
        }
    }
}

def tempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer"]
    
    targets.each { key, name ->
        if (settings["${key}Temp"]?.id == deviceId) {
            
            // 1. Cross-Threshold Warning
            def threshold = settings["${key}TempThreshold"]?.toString()?.toDouble()
            if (threshold != null && currentTemp >= threshold) {
                if (!state["${key}_tempWarningActive"]) {
                    sendAlert("🚨 ${name} TEMPERATURE ALERT: Current temp is ${currentTemp}°, which exceeds the safe threshold of ${threshold}°!", key, "temp")
                    state["${key}_tempWarningActive"] = true
                }
            } else if (threshold != null && currentTemp < (threshold - 1.0)) {
                state["${key}_tempWarningActive"] = false
            }
            
            // 2. Gradual Creeping Warning
            def avgTemp = state["${key}_avgTemp"]?.toString()?.toDouble() ?: currentTemp
            def baselineTemp = state["${key}_baselineTemp"]?.toString()?.toDouble() ?: avgTemp
            
            // Update slow moving average
            state["${key}_avgTemp"] = (avgTemp * 0.95) + (currentTemp * 0.05)
            
            if (currentTemp > (baselineTemp + 5.0)) {
                if (!state["${key}_tempCreepWarning"]) {
                    sendAlert("⚠️ ${name} TEMPERATURE CREEP: Baseline is ${Math.round(baselineTemp)}°, but average is creeping up (currently ${currentTemp}°). Check door seal or condenser coils.", key, "temp")
                    state["${key}_tempCreepWarning"] = true
                }
            } else if (currentTemp <= (baselineTemp + 2.0)) {
                state["${key}_tempCreepWarning"] = false
            }
        }
    }
}

def alwaysOnProtectionHandler(evt) {
    if (evt.value == "off") {
        def deviceId = evt.device.id
        
        if (settings["refrigeratorSwitch"]?.id == deviceId) {
            log.warn "Protection Triggered: Refrigerator turned off. Forcing ON."
            state["refrigerator_systemActionPending"] = true
            state["refrigerator_context"] = "Protection Force-ON"
            settings["refrigeratorSwitch"]?.on()
            sendAlert("🚨 CRITICAL PROTECTION: Your Refrigerator switch was turned OFF! The system has automatically forced it back ON to prevent food spoilage.", "refrigerator", "protection")
        } else if (settings["chestFreezerSwitch"]?.id == deviceId) {
            log.warn "Protection Triggered: Chest Freezer turned off. Forcing ON."
            state["chestFreezer_systemActionPending"] = true
            state["chestFreezer_context"] = "Protection Force-ON"
            settings["chestFreezerSwitch"]?.on()
            sendAlert("🚨 CRITICAL PROTECTION: Your Chest Freezer switch was turned OFF! The system has automatically forced it back ON to prevent food spoilage.", "chestFreezer", "protection")
        }
    }
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    // Refrigerator and Chest Freezer explicitly excluded from mode changes here
    def appliances = ["hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.13
    
    def actionCount = 0 // Track how many actual commands we send to stagger them
    
    appliances.each { key ->
        def offModes = settings["${key}TurnOffModes"]
        def onModes = settings["${key}TurnOnModes"]
        def sw = settings["${key}Switch"]
        
        if (sw) {
            // Process Turn ON logic first
            if (onModes && onModes.contains(newMode)) {
                if (sw.currentValue("switch") != "on") {
                    if (actionCount > 0) pauseExecution(1000) // Prevent mesh flooding
                    actionCount++
                    
                    state["${key}_systemActionPending"] = true
                    state["${key}_context"] = "Mode Restore"
                    sw.on()
                    
                    if (state["${key}_offTimeStart"]) {
                        def offTimeMs = now() - state["${key}_offTimeStart"]
                        def offTimeHours = offTimeMs / 3600000.0
                        def idleAvgPower = state["${key}_idlePowerAvg"]?.toString()?.toDouble() ?: 0.0
                        def savingsThisCycle = (idleAvgPower / 1000.0) * offTimeHours * kwhRate
                        state["${key}_roiSavings"] = (state["${key}_roiSavings"] ?: 0.0) + savingsThisCycle
                        state["${key}_offTimeStart"] = null
                    }
                    log.info "${key} turned ON due to mode changing to ${newMode}"
                }
            } 
            // Then process Turn OFF logic
            else if (offModes && offModes.contains(newMode)) {
                if (actionCount > 0) pauseExecution(1000) // Prevent mesh flooding
                actionCount++
                executeApplianceShutdown(key)
            }
        }
    }
}

// Dedicated retry wrappers for delayed mode shutdown 
def retryModeShutdown_hotWaterHeater() { executeApplianceShutdown("hotWaterHeater") }
def retryModeShutdown_washerDryer()    { executeApplianceShutdown("washerDryer") }
def retryModeShutdown_dishwasher()     { executeApplianceShutdown("dishwasher") }
def retryModeShutdown_microwave()      { executeApplianceShutdown("microwave") }

// Dedicated cool-down completion wrappers
def endCoolDownShutdown_hotWaterHeater() { finalizeShutdown("hotWaterHeater") }
def endCoolDownShutdown_washerDryer()    { finalizeShutdown("washerDryer") }
def endCoolDownShutdown_dishwasher()     { finalizeShutdown("dishwasher") }
def endCoolDownShutdown_microwave()      { finalizeShutdown("microwave") }


def executeApplianceShutdown(key) {
    def sw = settings["${key}Switch"]
    def pMeter = settings["${key}Power"]
    def safeThreshold = settings["safeShutdownThreshold"]?.toString()?.toDouble() ?: 15.0
    def allowCoolDown = settings["${key}EnableCoolDown"] != false // Default to true
    
    def coolDownMins = settings["${key}CoolDownMins"]?.toString()?.toInteger() ?: 120
    
    // Verify we are still in a mode that dictates shutdown before proceeding
    if (sw && settings["${key}TurnOffModes"]?.contains(location.mode)) {
        def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
        
        // CONDITION 1: Appliance is actively running right now
        if (currentPower > safeThreshold || state["${key}_isRunning"]) {
            log.info "${key} is actively running (${currentPower}W). Delaying Mode Shutdown."
            state["${key}_context"] = "Delayed Shutdown (Running)"
            runIn(900, "retryModeShutdown_${key}") // Check again in 15 minutes
        } 
        else {
            // CONDITION 2: Appliance is off, but recently finished a cycle (and Cool Down is enabled)
            def lastRunEndMs = state["${key}_cycleEndTime"] ?: 0
            def timeSinceLastRunMs = now() - lastRunEndMs
            def thirtyMinsMs = 30 * 60000
            
            if (allowCoolDown && timeSinceLastRunMs < thirtyMinsMs) {
                // Calculate how much of the cool down is remaining based on when the cycle ended
                def coolDownWindowMs = coolDownMins * 60000
                def remainingCoolDownMs = coolDownWindowMs - timeSinceLastRunMs
                
                if (remainingCoolDownMs > 0) {
                    def remainingCoolDownSecs = Math.round(remainingCoolDownMs / 1000)
                    def remainingCoolDownMinsCalc = Math.round(remainingCoolDownSecs / 60)
                    
                    log.info "${key} recently ran. Applying Cool Down period. Postponing shutdown for ${remainingCoolDownMinsCalc} minutes."
                    state["${key}_context"] = "Cooling Down (${remainingCoolDownMinsCalc}m remaining)"
                    
                    runIn(remainingCoolDownSecs, "endCoolDownShutdown_${key}")
                } else {
                    finalizeShutdown(key)
                }
            }
            // CONDITION 3: Appliance is completely safe to kill now
            else {
                finalizeShutdown(key)
            }
        }
    }
}

def finalizeShutdown(key) {
    def sw = settings["${key}Switch"]
    // Final check that we are still in an "off" mode before killing power
    if (sw && settings["${key}TurnOffModes"]?.contains(location.mode)) {
         if (sw.currentValue("switch") != "off") {
            state["${key}_systemActionPending"] = true
            state["${key}_context"] = "Mode Shutdown"
            sw.off()
            state["${key}_offTimeStart"] = now()
            log.info "${key} turned OFF via Mode Shutdown."
        }
    }
}


def buttonHandler(evt) {
    if (overrideModes && !overrideModes.contains(location.mode)) return
    
    def btnNumberStr = "1"
    if (evt.name == "button") {
        btnNumberStr = evt.jsonData?.buttonNumber?.toString() ?: "1"
    } else {
        btnNumberStr = evt.value?.toString() ?: "1"
    }
    
    // If the user specified button numbers, strictly enforce them. If left completely blank, allow any button.
    if (settings.buttonNumber) {
        def allowedNumbers = settings.buttonNumber.toString().split(",").collect { it.trim() }
        if (!allowedNumbers.contains(btnNumberStr)) return
    }
    
    log.info "Override activated by button ${btnNumberStr} (${evt.name})! Turning all appliances ON for 2 hours."
    def appliances = ["hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        state["${key}_context"] = "Manual Override"
    }
    
    triggerTurnOn()
    
    runIn(7200, endOverrideAndCheckSchedule)
}

def endOverrideAndCheckSchedule() {
    def currTime = now()
    def start = timeToday(scheduleStart).time
    def end = timeToday(scheduleEnd).time
    
    if (currTime >= start && currTime < end) {
        triggerTurnOff()
    }
}

def triggerTurnOff() {
    def appliances = ["hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    def safeThreshold = settings["safeShutdownThreshold"]?.toString()?.toDouble() ?: 15.0
    def needsRetry = false
    def actionCount = 0
    
    appliances.each { key ->
        def sw = settings["${key}Switch"]
        def pMeter = settings["${key}Power"]
        
        if (sw) {
            def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
            
            if (currentPower > safeThreshold) {
                log.info "${key} is currently running (${currentPower}W). Delaying schedule shutdown."
                needsRetry = true
            } else {
                if (sw.currentValue("switch") != "off") {
                    if (actionCount > 0) pauseExecution(1000) // Prevent mesh flooding
                    actionCount++
                    
                    state["${key}_systemActionPending"] = true
                    state["${key}_context"] = "Scheduled Shutdown"
                    sw.off()
                    state["${key}_offTimeStart"] = now()
                }
            }
        }
    }
    
    if (needsRetry) {
        runIn(900, "triggerTurnOff") // Fixed missing quotes around method name
    }
}

def triggerTurnOn() {
    def appliances = ["hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.13
    def actionCount = 0
    
    appliances.each { key ->
        def sw = settings["${key}Switch"]
        if (sw) {
            // Check state before firing to avoid redundant ON commands
            if (sw.currentValue("switch") != "on") { 
                if (actionCount > 0) pauseExecution(1000) // Prevent mesh flooding
                actionCount++
                
                state["${key}_systemActionPending"] = true
                if (state["${key}_context"] != "Manual Override") {
                    state["${key}_context"] = "Scheduled Restore"
                }
                sw.on()
                
                if (state["${key}_offTimeStart"]) {
                    def offTimeMs = now() - state["${key}_offTimeStart"]
                    def offTimeHours = offTimeMs / 3600000.0
                    def idleAvgPower = state["${key}_idlePowerAvg"]?.toString()?.toDouble() ?: 0.0
                    
                    def savingsThisCycle = (idleAvgPower / 1000.0) * offTimeHours * kwhRate
                    state["${key}_roiSavings"] = (state["${key}_roiSavings"] ?: 0.0) + savingsThisCycle
                    state["${key}_offTimeStart"] = null
                }
            }
        }
    }
}

def powerHandler(evt) {
    def meterId = evt.device.id
    def currentPower = evt.value.toString().toDouble()
    def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        if (settings["${key}Power"]?.id == meterId) {
            
            // 1. Spike Detection (60 Second Delay)
            def spikeThreshold = settings["${key}Spike"]?.toString()?.toDouble() ?: 5000
            if (currentPower > spikeThreshold) {
                if (!state["${key}_spikePending"]) {
                    state["${key}_spikePending"] = true
                    runIn(60, "confirmSpike_${key}")
                }
            } else if (currentPower < (spikeThreshold * 0.8)) {
                state["${key}_spikePending"] = false
                state["${key}_spikeWarning"] = false 
                unschedule("confirmSpike_${key}")
            }
            
            // 2. State Tracking with Start Delay & Debounced Cycle Completion 
            def runThreshold = settings["${key}RunWatts"]?.toString()?.toDouble() ?: 15.0
            def isCurrentlyRunning = (currentPower > runThreshold)
            def wasRunning = state["${key}_isRunning"] ?: false
            
            // --- Learn Idle Power ---
            // If the appliance is below the run threshold but actually drawing some power (ignoring 0.0W when physically off)
            if (!isCurrentlyRunning && currentPower > 0.0) {
                def currentIdle = state["${key}_idlePowerAvg"]?.toString()?.toDouble() ?: currentPower
                if (currentIdle == 0.0) {
                     state["${key}_idlePowerAvg"] = currentPower
                } else {
                    state["${key}_idlePowerAvg"] = (currentIdle * 0.95) + (currentPower * 0.05)
                }
            }
            // -----------------------------
            
            if (isCurrentlyRunning) {
                // Cancel any pending stop/completion countdowns because we surged back over threshold
                if (state["${key}_stopPending"]) {
                    state["${key}_stopPending"] = false
                    unschedule("checkCycleComplete_${key}")
                }
                
                // If we are not fully "running" and haven't started a delayed confirmation yet
                if (!wasRunning && !state["${key}_startPending"]) {
                    state["${key}_startPending"] = true
                    state["${key}_tentativeStartTime"] = now()
                    
                    def startDelayMins = settings["${key}StartDelay"]?.toString()?.toInteger() ?: 1
                    def startDelaySecs = startDelayMins * 60
             
                    if (startDelaySecs > 0) {
                        runIn(startDelaySecs, "confirmCycleStart_${key}")
                    } else {
                        startCycle(key) // Immediate start if user set delay to 0
                    }
                }
            } else {
                // Dropped below threshold
                
                // If we were just waiting to start, it was a false spike (e.g., fridge door opened)
                if (state["${key}_startPending"]) {
                    state["${key}_startPending"] = false
                    unschedule("confirmCycleStart_${key}")
                    state.remove("${key}_tentativeStartTime")
                }
                
                // If we were officially running, start the debounce/pause countdown for completion
                if (wasRunning && !state["${key}_stopPending"]) {
                    state["${key}_stopPending"] = true
                    
                    def debounceMins = settings["${key}Debounce"]?.toString()?.toInteger() ?: 15
                    def debounceSecs = debounceMins * 60
                    runIn(debounceSecs, "checkCycleComplete_${key}")
                }
            }
            
            // 3. Power Averaging
            if (currentPower > 10) {
                def currentAvg = state["${key}_avgPower"]?.toString()?.toDouble() ?: currentPower
                state["${key}_avgPower"] = (currentAvg * 0.95) + (currentPower * 0.05)
            }
        }
    }
}

// Dedicated cycle start confirmation methods for each appliance
def confirmCycleStart_refrigerator() { startCycle("refrigerator") }
def confirmCycleStart_chestFreezer() { startCycle("chestFreezer") }
def confirmCycleStart_hotWaterHeater() { startCycle("hotWaterHeater") }
def confirmCycleStart_washerDryer()  { startCycle("washerDryer") }
def confirmCycleStart_dishwasher()   { startCycle("dishwasher") }
def confirmCycleStart_microwave()    { startCycle("microwave") }

def startCycle(key) {
    if (state["${key}_startPending"]) {
        state["${key}_startPending"] = false
        state["${key}_isRunning"] = true
        // Log the start time accurately from when it first crossed the threshold
        state["${key}_cycleStartTime"] = state["${key}_tentativeStartTime"] ?: now()
    }
}

// Dedicated spike confirmation methods for each appliance
def confirmSpike_refrigerator() { executeSpikeAlert("refrigerator", "Refrigerator") }
def confirmSpike_chestFreezer() { executeSpikeAlert("chestFreezer", "Chest Freezer") }
def confirmSpike_hotWaterHeater() { executeSpikeAlert("hotWaterHeater", "Hot Water Heater") }
def confirmSpike_washerDryer()  { executeSpikeAlert("washerDryer", "Washer/Dryer") }
def confirmSpike_dishwasher()   { executeSpikeAlert("dishwasher", "Dishwasher") }
def confirmSpike_microwave()    { executeSpikeAlert("microwave", "Microwave") }

def executeSpikeAlert(key, name) {
    if (state["${key}_spikePending"]) {
        state["${key}_spikePending"] = false
        if (!state["${key}_spikeWarning"]) {
            state["${key}_spikeWarning"] = true
            sendAlert("⚡ ${name} power spike has persisted for over 60 seconds! Check for failing components.", key, "spike")
        }
    }
}

// Separate methods to ensure safe execution in the SmartThings/Hubitat architecture without runIn overwrites
def checkCycleComplete_refrigerator() { finishCycle("refrigerator", "Refrigerator") }
def checkCycleComplete_chestFreezer() { finishCycle("chestFreezer", "Chest Freezer") }
def checkCycleComplete_hotWaterHeater() { finishCycle("hotWaterHeater", "Hot Water Heater") }
def checkCycleComplete_washerDryer()  { finishCycle("washerDryer", "Washer/Dryer") }
def checkCycleComplete_dishwasher()   { finishCycle("dishwasher", "Dishwasher") }
def checkCycleComplete_microwave()    { finishCycle("microwave", "Microwave") }

def finishCycle(key, name) {
    if (state["${key}_stopPending"] && state["${key}_isRunning"]) {
        state["${key}_isRunning"] = false
        state["${key}_stopPending"] = false
        
        // Track exact moment cycle finished for cool down logic
        state["${key}_cycleEndTime"] = now()
        
        if (state["${key}_cycleStartTime"]) {
            def debounceMins = settings["${key}Debounce"]?.toString()?.toInteger() ?: 15
            def debounceMs = debounceMins * 60000
            
            // Subtract the dynamic debounce window from the total run time for accuracy
            def cycleDurationMs = now() - state["${key}_cycleStartTime"] - debounceMs 
            if (cycleDurationMs < 0) cycleDurationMs = 0
            
            def cycleDurationHours = cycleDurationMs / 3600000.0
            def cycleDurationMins = cycleDurationMs / 60000.0
            
            // Log final cycle stats
            state["${key}_lastRunLengthMins"] = cycleDurationMins
            state["${key}_7DayRunCount"] = (state["${key}_7DayRunCount"] ?: 0) + 1
            state["${key}_todayRunCount"] = (state["${key}_todayRunCount"] ?: 0) + 1
            state["${key}_7DayTotalCycleMins"] = (state["${key}_7DayTotalCycleMins"] ?: 0.0) + cycleDurationMins
            
            state["${key}_totalRunHours"] = (state["${key}_totalRunHours"] ?: 0.0) + cycleDurationHours
            
            if (cycleDurationMins > 1) {
                sendAlert("✅ Your ${name} cycle is complete! (Ran for ${Math.round(cycleDurationMins)} mins)", key, "cycle")
            }
            
            if (key == "refrigerator" || key == "chestFreezer") {
                trackCompressorHealth(key, name, cycleDurationMins)
            }
            
            // Handle Auto-Off Feature
            if (settings["${key}AutoOff"]) {
                def allowedModes = settings["${key}AutoOffModes"]
                
                // Proceed if no modes are restricted OR if the current mode matches an allowed mode
                if (!allowedModes || allowedModes.contains(location.mode)) {
                    // Trigger mode shutdown check, which will respect the Cool Down toggle!
                    executeApplianceShutdown(key)
                } else {
                    log.info "${name} cycle finished, but Auto-Off was skipped due to mode restrictions."
                }
            }
        }
    }
}

def trackCompressorHealth(key, name, cycleDurationMins) {
    if (cycleDurationMins < 5) return
    
    def baselineDuration = state["${key}_baselineCycleMins"]?.toString()?.toDouble() ?: cycleDurationMins
    
    if (baselineDuration == 0.0 || baselineDuration == cycleDurationMins) {
        state["${key}_baselineCycleMins"] = cycleDurationMins
    } else {
        if (cycleDurationMins > (baselineDuration * 1.30)) {
            state["${key}_struggleCount"] = (state["${key}_struggleCount"] ?: 0) + 1
            
            if (state["${key}_struggleCount"] == 3) {
                sendAlert("🧹 ${name} Maintenance: Compressor is running 30% longer than normal. Please clean the condenser coils and check airflow to prevent failure.", key, "health")
            } else if (state["${key}_struggleCount"] >= 7) {
                sendAlert("⚠️ ${name} CRITICAL Warning: Unit is severely struggling to cool. Compressor cycles are continuously extended. Hardware failure may be imminent.", key, "health")
                state["${key}_struggleCount"] = 0 
            }
        } else {
            state["${key}_baselineCycleMins"] = (baselineDuration * 0.98) + (cycleDurationMins * 0.02)
            if (state["${key}_struggleCount"] > 0) {
                state["${key}_struggleCount"] = state["${key}_struggleCount"] - 1
            }
        }
    }
}

def dailyHealthCheck() {
    def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.13
    def allowMaintRoi = settings["enableMaintenanceRoi"] != false // Defaults to true

    appliances.each { key, name ->
        def avgPower = state["${key}_avgPower"]?.toString()?.toDouble() ?: 0.0
        
        // --- NEW ROI CALCULATION FOR MAINTENANCE SAVINGS ---
        if (allowMaintRoi && state["${key}_inefficientPowerMark"]) {
            def badWatts = state["${key}_inefficientPowerMark"].toString().toDouble()
            if (avgPower < badWatts && avgPower > 0) {
                def savedKw = (badWatts - avgPower) / 1000.0
                
                // Estimate daily run hours (use 7-day average if available, otherwise assume 24h standby)
                def totalWeeklyMins = state["${key}_7DayTotalCycleMins"]?.toString()?.toDouble() ?: 0.0
                def dailyRunHours = totalWeeklyMins > 0 ? (totalWeeklyMins / 7.0 / 60.0) : 24.0
                
                def dailySavings = savedKw * dailyRunHours * kwhRate
                state["${key}_maintRoiSavings"] = (state["${key}_maintRoiSavings"] ?: 0.0) + dailySavings
            } else if (avgPower >= badWatts) {
                // If it creeps back up to the bad mark, stop calculating savings until the next reset
                state.remove("${key}_inefficientPowerMark")
            }
        }
        
        // Existing creep warning logic
        def baselineAvg = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: avgPower
        
        if (baselineAvg == 0.0) {
            state["${key}_baselineAvg"] = avgPower
        } else {
            if (avgPower > (baselineAvg * 1.20)) {
                state["${key}_creepWarning"] = true
                sendAlert("⚠️ ${name} average power is creeping up (${Math.round(avgPower)}W vs baseline ${Math.round(baselineAvg)}W). A motor/vent check is recommended.", key, "health")
            } else {
                state["${key}_creepWarning"] = false
                state["${key}_baselineAvg"] = (baselineAvg * 0.98) + (avgPower * 0.02)
            }
        }
        
        // Temperature Daily Recalibration for Compressor units
        if (key == "refrigerator" || key == "chestFreezer") {
            def totalHours = state["${key}_totalRunHours"]?.toString()?.toDouble() ?: 0.0
            def maintenanceInterval = settings["${key}MaintenanceHours"]?.toString()?.toDouble() ?: 2000.0
            
            if (totalHours > maintenanceInterval) {
                sendAlert("🔧 Routine Maintenance: Your ${name} has reached ${Math.round(totalHours)} run hours. Consider scheduling a preventative maintenance check.", key, "health")
                settings["${key}MaintenanceHours"] = maintenanceInterval + 2000.0 
            }
            
            // Adjust temp baseline daily 
            if (state["${key}_avgTemp"]) {
                def currentAvgTemp = state["${key}_avgTemp"].toString().toDouble()
                def tempBaseline = state["${key}_baselineTemp"]?.toString()?.toDouble() ?: currentAvgTemp
                state["${key}_baselineTemp"] = (tempBaseline * 0.90) + (currentAvgTemp * 0.10)
            }
        }
    }
}

def resetWeeklyCounters() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        def eMeter = settings["${key}Energy"]
        if (eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
        state["${key}_roiSavings"] = 0.0
        state["${key}_maintRoiSavings"] = 0.0
        state["${key}_7DayRunCount"] = 0
        state["${key}_7DayTotalCycleMins"] = 0.0
    }
}

def sendAlert(msg, key, alertType) {
    // 1. Time & Mode Gatekeeping (Per Appliance)
    def aModes = settings["${key}AlertModes"]
    if (aModes && !aModes.contains(location.mode)) return
    
    def aStart = settings["${key}AlertStartTime"]
    def aEnd = settings["${key}AlertEndTime"]
    
    if (aStart && aEnd) {
        def currTime = now()
        def start = timeToday(aStart).time
        def end = timeToday(aEnd).time
        
        if (start <= end) {
            if (currTime < start || currTime > end) return
        } else {
            // Handles wrap-around midnight
            if (currTime < start && currTime > end) return
        }
    }

    // 2. Fetch specific notification targets and allowed events for this appliance
    def pushDev = settings["${key}PushNotification"]
    def pushEvents = settings["${key}PushEvents"] ?: []
    
    def ttsDev = settings["${key}TtsDevice"]
    def ttsEvents = settings["${key}TtsEvents"] ?: []
    
    def audioDev = settings["${key}AudioDevice"]
    def audioEvents = settings["${key}AudioEvents"] ?: []
    def audioTrack = settings["${key}AudioTrack"]?.toString() ?: "1"

    // 3. Execute Allowed Notifications
    if (pushDev && pushEvents.contains(alertType)) {
        pushDev.deviceNotification(msg)
    }
    
    if (ttsDev && ttsEvents.contains(alertType)) {
        ttsDev.speak(msg)
    }
    
    // FIX APPLIED: Routing audio through the safe Zooz Helper
    if (audioDev && audioEvents.contains(alertType)) {
        playZoozSound(audioDev, audioTrack)
    }
}

// FIX APPLIED: New safe play function handling playSound, playTrack, and chime with mesh protection
def playZoozSound(devices, sound) {
    if (!devices || sound == null) return
    def soundInt = sound.toInteger()
    
    // Handle single device or list of devices
    def devList = devices instanceof List ? devices : [devices]
    
    devList.eachWithIndex { dev, index ->
        // Add a 1000ms (1 second) delay before subsequent devices to prevent Z-Wave mesh flooding
        if (index > 0) {
            pauseExecution(1000)
        }

        try {
            if (dev.hasCommand("playSound")) {
                dev.playSound(soundInt)
            } else if (dev.hasCommand("playTrack")) {
                dev.playTrack(sound.toString())
            } else if (dev.hasCommand("chime")) {
                dev.chime(soundInt)
            } else {
                log.warn "Advanced Energy Management Controller: Device ${dev.displayName} does not support standard sound commands (playSound, playTrack, or chime)."
            }
        } catch (e) {
            log.error "Failed to play audio on ${dev.displayName}: ${e}"
        }
    }
}
