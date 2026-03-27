/**
 * Advanced Television Application
 */
definition(
    name: "Advanced Television Application",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Predictive TV engine with watch-time tracking, Acoustic Management, TV Shows, and automatic safety interruptions.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
    page(name: "tvPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard", hideable: true, hidden: false) {
            if (numTVs > 0) {
                
                input "btnRefreshData", "button", title: "<i class='fa fa-refresh fa-spin' style='color: #007bff;'></i> Refresh Dashboard Data", description: "Click here to immediately fetch the latest states and metrics for the data table below."
                
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-top: 10px;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px; width: 20%;'>Television</th><th style='padding: 8px; width: 35%;'>State & Acoustics</th><th style='padding: 8px;'>Watch Time</th><th style='padding: 8px;'>Top Media</th><th style='padding: 8px;'>Cost Today</th></tr>"
          
                for (int i = 1; i <= (numTVs as Integer); i++) {
                    def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                    def tv = getPrimaryDevice(i)
                    
                    if (!tv) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${tvName}</b></td><td style='padding: 8px; color: #888;' colspan='4'>Not Configured</td></tr>"
                        continue
                    }
               
                    def isTrulyOn = isTvActuallyOn(tv, i)
                    def powerState = isTrulyOn ? "ON" : "STANDBY / OFF"
                    def pwrColor = isTrulyOn ? "green" : "red"
                    
                    def currentApp = "Unknown"
                    if (isTrulyOn) {
                        if (settings["isAvrOnly_${i}"]) {
                            def rawInput = tv.currentValue("mediaInputSource") ?: "Unknown"
                            currentApp = getMappedAppName(i, rawInput)
                        } else {
                            currentApp = tv.currentValue("application") ?: "Unknown"
                        }
                    } else {
                        currentApp = "Screen Off"
                    }
                    
                    // --- Acoustic Management Live Data ---
                    def acousticText = ""
                    if (settings["enableAcousticMgmt_${i}"]) {
                        def activeAcoustics = []
                        
                        def thermo = settings["mainThermostat_${i}"]
                        if (thermo) {
                            def tState = thermo.currentValue("thermostatOperatingState")
                            if (tState in ["heating", "cooling", "fan only"]) activeAcoustics << "HVAC (${tState.capitalize()}): +${settings["hvacVolumeBoost_${i}"] ?: 3}"
                            else activeAcoustics << "HVAC (Idle)"
                        }
                        
                        def dish = settings["dishwasher_${i}"]
                        if (dish) {
                            if (dish.currentValue("switch") == "on") activeAcoustics << "Dishwasher (ON): +${settings["dishwasherBoost_${i}"] ?: 4}"
                            else activeAcoustics << "Dishwasher (OFF)"
                        }
                        
                        def vac = settings["vacuum_${i}"]
                        if (vac) {
                            if (vac.currentValue("switch") == "on") activeAcoustics << "Vacuum (ON): +${settings["vacuumBoost_${i}"] ?: 10}"
                            else activeAcoustics << "Vacuum (OFF)"
                        }
                        
                        def ap = settings["airPurifier_${i}"]
                        if (ap) {
                            if (ap.currentValue("switch") == "on") activeAcoustics << "Purifier (ON): +${settings["airPurifierBoost_${i}"] ?: 2}"
                            else activeAcoustics << "Purifier (OFF)"
                        }
                        
                        if (activeAcoustics.size() > 0) {
                            def currentBoost = state.currentVolumeBoost?."${i}" ?: 0
                            def boostDisplay = isTrulyOn ? "<strong style='color:#c0392b;'>Active Volume Boost: +${currentBoost}</strong>" : "<span style='color:#7f8c8d;'>(TV is OFF - Boost Suspended)</span>"
                            acousticText = "<div style='margin-top: 6px; padding-top: 6px; border-top: 1px dotted #ccc; font-size:11px; color:#555;'><b>Acoustics:</b> ${activeAcoustics.join(' | ')}<br>${boostDisplay}</div>"
                        }
                    }
                    
                    def watchMins = state.watchTimeToday?."${i}" ?: 0
                    def watchDisplay = "${(watchMins / 60).toInteger()}h ${watchMins % 60}m"
                    
                    def topApp = "None"
                    def topTime = 0
                    if (state.appStats?."${i}") {
                        state.appStats["${i}"].each { app, time ->
                            if (time > topTime) {
                                topApp = app
                                topTime = time
                            }
                        }
                    }
                    
                    def cost = "\$" + (state.costToday?."${i}" ?: 0.00).setScale(2, BigDecimal.ROUND_HALF_UP)
                 
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${tvName}</b></td><td style='padding: 8px;'><span style='color: ${pwrColor}; font-weight:bold;'>${powerState}</span><br><span style='font-size:11px; color:#555;'>${currentApp}</span>${acousticText}</td><td style='padding: 8px;'>${watchDisplay}</td><td style='padding: 8px;'>${topApp}</td><td style='padding: 8px;'>${cost}</td></tr>"
                    
                    // --- ACTIVE MACRO BANNER INJECTION ---
                    def activeMacro = state.activeMacro?."${i}"
                    if (activeMacro) {
                        def modeTitle = activeMacro == "movie" ? "🎬 MOVIE MODE ACTIVE" : "🎮 GAMING MODE ACTIVE"
                        def color = activeMacro == "movie" ? "#e8f4f8" : "#f4e8f8"
                        def border = activeMacro == "movie" ? "#3498db" : "#9b59b6"
                        def controlledList = state."macroControlledList_${i}" ?: "Environment locked."
                        
                        statusText += "<tr style='background-color: ${color}; border-left: 4px solid ${border};'><td colspan='5' style='padding: 10px; font-size: 12px; color: #333;'>"
                        statusText += "<strong style='color: ${border};'>${modeTitle}</strong><br>"
                        statusText += "<i>System is orchestrating the room. Snapshot captured.</i><br>"
                        statusText += "<b>Actively Controlling:</b> ${controlledList}<br>"
                        if (bmsPriorityLock && bmsPriorityLock.currentValue("switch") == "on") {
                            statusText += "<span style='color:#c0392b;'><b>🔒 Global Priority Lock is ON:</b> Protecting this room from other app automations.</span>"
                        }
                        statusText += "</td></tr>"
                    }
                }
                statusText += "</table>"
                
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                
                def totalHouseCost = 0.0
                if (state.costToday) { state.costToday.each { k, v -> totalHouseCost += v } }
                def totalDisplay = "\$" + totalHouseCost.setScale(2, BigDecimal.ROUND_HALF_UP)
        
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>System:</b> ${globalStatus}</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>Total Entertainment Cost Today:</b> <span style='color: #aa0000;'>${totalDisplay}</span></div>"
                statusText += "</div>"

                paragraph statusText
                
            } else {
                paragraph "<i>Configure televisions below to see live system status.</i>"
            }
        }
        
        section("Application History (Last 20 Events)", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. Logs will appear as the system takes action.</i>"
            }
        }
        
        section("BMS Integrity & System Robustness", hideable: true, hidden: true) {
            paragraph "These features elevate the application from simple convenience to a robust Building Management Engine, ensuring commands land safely and preventing apps from fighting each other."
            input "bmsPriorityLock", "capability.switch", title: "Global Priority Lock Switch", required: false, description: "Turns ON automatically when Movie, Gaming, or Weather modes are active. Use this switch as a 'Restriction' in your other apps to prevent them from turning off the lights or TV while you are watching."
            input "bmsMeshJitter", "bool", title: "Enable Mesh Optimization (Surge Staggering)", defaultValue: false, description: "Adds a random 500ms-2000ms delay between device commands during global events (like weather alerts) to prevent Zigbee/Z-Wave network flooding and electrical power surges."
            input "bmsHeartbeat", "bool", title: "Enable Device Heartbeat & Retries", defaultValue: false, description: "Actively verifies if TV/AVR power commands actually executed. It will retry the command up to 3 times if the device dropped off the network, and log a Comms Failure if it completely fails."
            input "bmsNightlyMaintenance", "bool", title: "Enable Nightly Driver Maintenance", defaultValue: false, description: "Forcefully calls refresh() and initialize() on all linked TV and AVR drivers every night at 3:00 AM to prevent connection zombie-states."
        }
        
        section("Global Settings & Modes", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "Select a virtual switch to act as a global pause. If the switch is OFF, the entire TV application will halt all automation and routines."
            input "numTVs", "number", title: "Number of Televisions to Configure (1-10)", required: true, defaultValue: 1, range: "1..10", submitOnChange: true, description: "Enter the number of TVs or Home Theaters you want to manage in your home. Save to reveal their setup menus below."
            input "elecRate", "decimal", title: "Electricity Rate (per kWh)", defaultValue: 0.14, required: true, description: "Your local utility rate (e.g., 0.14 for 14 cents per kWh). This is used to accurately calculate your daily entertainment costs on the dashboard."
        }

        section("Safety & Security Interruption (Auto-Mute)", hideable: true, hidden: true) {
            input "enableSafetyMute", "bool", title: "Enable Security/Doorbell Auto-Mute", defaultValue: false, submitOnChange: true, description: "Automatically mutes any active TV when a monitored safety contact opens or a doorbell is pressed, ensuring you hear important activity."
            if (enableSafetyMute) {
                input "muteContacts", "capability.contactSensor", title: "Safety Contacts", multiple: true, required: false, description: "If any of these doors/windows open while a TV is on, the TV will instantly mute. It restores volume when closed."
                input "doorbellButtons", "capability.pushableButton", title: "Doorbell Buttons", multiple: true, required: false, description: "If these doorbells are pressed, active TVs will instantly mute to ensure you hear the chime."
                input "doorbellMuteTime", "number", title: "Doorbell Mute Duration (Seconds)", defaultValue: 60, required: true, description: "How long to keep the TVs muted after a doorbell is pressed before automatically restoring the volume."
            }
        }
        
        section("Severe Weather & Emergency Override", hideable: true, hidden: true) {
            input "enableWeatherAlert", "bool", title: "Enable Severe Weather Overrides", defaultValue: false, submitOnChange: true, description: "Forces configured TVs to power on and tune to a specific broadcast channel or streaming app during a severe weather alert."
            if (enableWeatherAlert) {
                input "weatherSwitch", "capability.switch", title: "Virtual Storm / Weather Alert Switch", required: false, description: "When this switch turns ON (e.g., triggered by a NOAA severe weather app), it initiates the emergency sequence on all enabled TVs."
                input "weatherChannel", "text", title: "Emergency Broadcast Channel (OTA)", required: false, description: "The local OTA channel to force the TV to (e.g., 8.1 or 12) when a weather alert occurs."
                input "weatherAppSwitch", "capability.switch", title: "OR Emergency App Switch", required: false, description: "Turns on a virtual app switch (e.g., a Roku Netflix or Apple TV switch) instead of tuning to an OTA channel."
                input "weatherTimeout", "number", title: "Auto-Restore Timeout (Minutes)", defaultValue: 0, description: "How long until the TV automatically shuts back off. Set to 0 to keep the TV on indefinitely until the alert switch turns off."
                
                input "testStormBtn", "button", title: "Test Storm TV Alert (ON)", description: "Simulates a weather alert right now to verify TVs turn on and tune correctly."
                input "testStormOffBtn", "button", title: "Test Storm TV Alert (OFF)", description: "Ends the simulated weather alert and restores previous TV states."
            }
        }
        
        if (numTVs > 0 && numTVs <= 10) {
            for (int i = 1; i <= (numTVs as Integer); i++) {
                def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                section("${tvName}") {
                    href(name: "tvHref${i}", page: "tvPage", params: [tvNum: i], title: "Configure ${tvName}", description: "Click to set up routines, soundbars, acoustic management, and tracking for this screen.")
                }
            }
        }
    }
}

def tvPage(params) {
    def tNum = params?.tvNum ?: state.currentTV ?: 1
    state.currentTV = tNum
    def currentName = settings["tvName_${tNum}"] ?: "TV ${tNum}"
    
    dynamicPage(name: "tvPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Identification", hideable: true, hidden: true) {
            input "tvName_${tNum}", "text", title: "Custom TV Name", required: false, defaultValue: "TV ${tNum}", submitOnChange: true, description: "A friendly name for this setup to easily identify it on the main dashboard and inside history logs."
        }
        
        section("Control Devices", hideable: true, hidden: true) {
            input "isAvrOnly_${tNum}", "bool", title: "Bypass Smart TV (AVR / Receiver Only Mode)", defaultValue: false, submitOnChange: true, description: "Check this if you run your setup entirely through an AV Receiver. The app will track power and HDMI inputs via the Receiver instead of relying on Smart TV application states."
            
            if (settings["isAvrOnly_${tNum}"]) {
                input "avrType_${tNum}", "enum", title: "AVR Brand / Protocol", options: ["Denon / Marantz", "Yamaha", "Onkyo / Pioneer", "Sony", "Generic / Other"], defaultValue: "Generic / Other", required: true, description: "Select the brand or protocol of your AVR to ensure volume commands are formatted perfectly."
                input "avr_${tNum}", "capability.audioVolume", title: "AVR / Receiver Device", required: true, description: "Select the AV Receiver device to monitor and control."
                input "tvPlug_${tNum}", "capability.switch", title: "Smart Plug Powering Display (Optional)", required: false, description: "If a smart plug powers your projector or screen, select it here. The app will turn it on for routines and kill power when finished."
                
                paragraph "Map your AVR's media input sources to friendly names so the dashboard tracks exactly what you are doing."
                for (int h = 1; h <= 5; h++) {
                    input "hdmiSource_${tNum}_${h}", "text", title: "AVR Input ${h} (e.g., HDMI 1)", required: false, description: "Enter the exact raw input string reported by the AVR (e.g., HDMI 1, SAT/CBL)."
                    input "hdmiName_${tNum}_${h}", "text", title: "Friendly Name (e.g., DVD Player)", required: false, description: "Enter a clean, friendly name for this input to display on the dashboard."
                }
            } else {
                input "tvType_${tNum}", "enum", title: "Television Brand / Ecosystem", options: ["Roku TV", "LG WebOS", "Samsung Smart TV", "Sony Bravia", "Android TV / Google TV", "Apple TV", "Generic / Other"], defaultValue: "Roku TV", required: true, description: "Select the brand of your Smart TV so the app knows exactly how to detect idle states and screen savers."
                input "tv_${tNum}", "capability.switch", title: "Television Device", required: true, description: "Select the primary Smart TV device to monitor and control."
                input "tvPlug_${tNum}", "capability.switch", title: "Smart Plug Powering TV (Optional)", required: false, description: "If a smart plug powers this TV, select it here. The app will turn it on for routines and kill power when finished."
                
                input "tvAudio_${tNum}", "capability.audioVolume", title: "Dedicated Audio/Soundbar (Optional)", required: false, description: "Select this ONLY if your TV relies on an external, smart-controlled soundbar (like Sonos) for volume instead of native speakers."
                input "audioType_${tNum}", "enum", title: "Audio Control Protocol", options: ["Standard Soundbar (Volume Clicks)", "Network AVR / Absolute (SetLevel 0-100)", "Onkyo / Pioneer Protocol", "Sonos / Wi-Fi Speaker"], defaultValue: "Standard Soundbar (Volume Clicks)", required: false, description: "Select the protocol used by your external audio device to ensure volume normalization works correctly."
            }
        }
        
        section("Dedicated Movie Mode (Macro / Scene)", hideable: true, hidden: true) {
            input "enableMovieMode_${tNum}", "bool", title: "Enable Movie Mode", defaultValue: false, submitOnChange: true, description: "Triggers a full home theater macro from a single virtual switch. Orchestrates TV power, inputs, volume, lights, shades, locks, fans, and HVAC in one smooth sequence."
            if (settings["enableMovieMode_${tNum}"]) {
                input "movieSwitch_${tNum}", "capability.switch", title: "Movie Mode Trigger Switch", required: true, description: "Select the virtual switch that will trigger this entire macro when turned ON, and turn the TV off when turned OFF."
                
                paragraph "<b>Execution Constraints:</b> Define exactly when this mode is allowed to run."
                input "movieModes_${tNum}", "mode", title: "Allowed Hub Modes", multiple: true, required: false, description: "Only allow Movie Mode to run if the house is in one of these modes."
                input "movieTimeStart_${tNum}", "time", title: "Allowed Start Time", required: false, description: "Earliest time of day Movie Mode is allowed to execute."
                input "movieTimeEnd_${tNum}", "time", title: "Allowed End Time", required: false, description: "Latest time of day Movie Mode is allowed to execute."
                
                paragraph "<b>Media & Audio:</b>"
                if (settings["isAvrOnly_${tNum}"]) {
                    input "movieTarget_${tNum}", "text", title: "Target AVR Input (e.g., HDMI 1)", required: false, description: "The exact AVR input string to switch to for Movie Mode."
                } else {
                    input "movieTarget_${tNum}", "text", title: "Target TV Channel or Input", required: false, description: "The OTA channel or Input string to automatically tune to."
                    input "movieAppSwitch_${tNum}", "capability.switch", title: "OR Target App Switch", required: false, description: "Turns on a virtual app switch (e.g., a Roku Netflix device switch) instead of tuning a channel."
                }
                input "movieVol_${tNum}", "number", title: "Target Volume", required: false, description: "The volume level to set. NOTE: If using an AVR, it sets this absolute number (0-100). If using a Soundbar, it will send this exact number of 'Volume Up' clicks sequentially."
                
                paragraph "<b>Environmental Control:</b>"
                input "movieLightsOff_${tNum}", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false, description: "Select any lights that should immediately turn off to darken the room."
                input "movieLightsOn_${tNum}", "capability.switchLevel", title: "Lights to Turn ON / Dim", multiple: true, required: false, description: "Select lights (like bias lighting or lamps) to turn on for ambiance."
                input "movieLightsLevel_${tNum}", "number", title: "Ambiance Dim Level (%)", required: false, range: "1..100", description: "The exact brightness percentage for the ambiance lights."
                input "movieShades_${tNum}", "capability.windowShade", title: "Shades / Blinds to Close", multiple: true, required: false, description: "Select up to 3 motorized shades or blinds to automatically close."
                input "movieLocks_${tNum}", "capability.lock", title: "Doors to Lock", multiple: true, required: false, description: "Ensure the house is secure by automatically locking these doors when the movie starts."
                input "movieFans_${tNum}", "capability.fanControl", title: "Ceiling Fans to Adjust (Bond RF / Smart)", multiple: true, required: false, description: "Select ceiling fans to automatically adjust for room comfort."
                input "movieFanSpeed_${tNum}", "enum", title: "Fan Speed", options: ["low", "medium-low", "medium", "medium-high", "high", "on", "off"], required: false, description: "The target speed to set the chosen ceiling fans to."
                
                paragraph "<b>Thermodynamics & HVAC:</b>"
                input "movieThermostat_${tNum}", "capability.thermostat", title: "Thermostat to Adjust", required: false, description: "Select the room's thermostat to push a temporary climate hold during the movie."
                input "movieHeatSetpoint_${tNum}", "number", title: "Heating Setpoint", required: false, description: "The target heating temperature."
                input "movieCoolSetpoint_${tNum}", "number", title: "Cooling Setpoint", required: false, description: "The target cooling temperature."
                input "moviePreCool_${tNum}", "number", title: "Thermodynamic Pre-Cool Offset (Degrees)", required: false, description: "Automatically drops the cooling setpoint by this many extra degrees for the first 45 minutes to counter the heat load generated by occupants and A/V equipment."
                
                paragraph "<b>Snapshot & Smart Restore:</b>"
                input "movieRestore_${tNum}", "bool", title: "Enable Smart Restore (Snap-Back)", defaultValue: false, submitOnChange: true, description: "If enabled, takes a snapshot of your lights, blinds, fans, and HVAC before the movie starts, and snaps them back to those exact states when the movie ends."
                if (settings["movieRestore_${tNum}"]) {
                    input "movieRestoreModes_${tNum}", "mode", title: "Only Restore in these Modes", multiple: true, required: false, description: "Prevent snap-back if the house mode has changed (e.g., don't turn lights back on if the house has gone to 'Night' mode)."
                    input "movieRestoreTimeStart_${tNum}", "time", title: "Restore Time Window Start", required: false
                    input "movieRestoreTimeEnd_${tNum}", "time", title: "Restore Time Window End", required: false
                }
            }
        }
        
        section("Dedicated Gaming Mode (Macro / Scene)", hideable: true, hidden: true) {
            input "enableGamingMode_${tNum}", "bool", title: "Enable Gaming Mode", defaultValue: false, submitOnChange: true, description: "A secondary macro designed for gaming consoles. Captures the room, adjusts environment, and intelligently restores when you're done."
            if (settings["enableGamingMode_${tNum}"]) {
                input "gamingSwitch_${tNum}", "capability.switch", title: "Gaming Mode Trigger Switch", required: true, description: "Select the virtual switch that will trigger this entire macro when turned ON."
                
                paragraph "<b>Execution Constraints:</b> Define exactly when this mode is allowed to run."
                input "gamingModes_${tNum}", "mode", title: "Allowed Hub Modes", multiple: true, required: false, description: "Only allow Gaming Mode to run if the house is in one of these modes."
                input "gamingTimeStart_${tNum}", "time", title: "Allowed Start Time", required: false, description: "Earliest time of day Gaming Mode is allowed to execute."
                input "gamingTimeEnd_${tNum}", "time", title: "Allowed End Time", required: false, description: "Latest time of day Gaming Mode is allowed to execute."
                
                paragraph "<b>Media & Audio:</b>"
                if (settings["isAvrOnly_${tNum}"]) {
                    input "gamingTarget_${tNum}", "text", title: "Target AVR Input (e.g., HDMI 2)", required: false, description: "The exact AVR input string to switch to for the gaming console."
                } else {
                    input "gamingTarget_${tNum}", "text", title: "Target TV Channel or Input", required: false, description: "The OTA channel or Input string to automatically tune to."
                    input "gamingAppSwitch_${tNum}", "capability.switch", title: "OR Target App Switch", required: false, description: "Turns on a virtual app switch instead of tuning a channel."
                }
                input "gamingVol_${tNum}", "number", title: "Target Volume", required: false, description: "The volume level to set."
                
                paragraph "<b>Environmental Control:</b>"
                input "gamingLightsOff_${tNum}", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false, description: "Select any lights that should immediately turn off to reduce screen glare."
                input "gamingLightsOn_${tNum}", "capability.switchLevel", title: "Lights to Turn ON / Dim", multiple: true, required: false, description: "Select lights (like LED strips behind the TV) to turn on for gaming ambiance."
                input "gamingLightsLevel_${tNum}", "number", title: "Ambiance Dim Level (%)", required: false, range: "1..100", description: "The exact brightness percentage for the ambiance lights."
                input "gamingShades_${tNum}", "capability.windowShade", title: "Shades / Blinds to Close", multiple: true, required: false, description: "Select up to 3 motorized shades or blinds to automatically close to eliminate glare."
                input "gamingLocks_${tNum}", "capability.lock", title: "Doors to Lock", multiple: true, required: false, description: "Lock these doors when gaming starts."
                input "gamingFans_${tNum}", "capability.fanControl", title: "Ceiling Fans to Adjust", multiple: true, required: false, description: "Select ceiling fans to automatically adjust for room comfort."
                input "gamingFanSpeed_${tNum}", "enum", title: "Fan Speed", options: ["low", "medium-low", "medium", "medium-high", "high", "on", "off"], required: false, description: "The target speed to set the chosen ceiling fans to."
                
                paragraph "<b>Thermodynamics & HVAC:</b>"
                input "gamingThermostat_${tNum}", "capability.thermostat", title: "Thermostat to Adjust", required: false, description: "Select the room's thermostat to push a temporary climate hold during gaming."
                input "gamingHeatSetpoint_${tNum}", "number", title: "Heating Setpoint", required: false, description: "The target heating temperature."
                input "gamingCoolSetpoint_${tNum}", "number", title: "Cooling Setpoint", required: false, description: "The target cooling temperature."
                input "gamingPreCool_${tNum}", "number", title: "Thermodynamic Pre-Cool Offset (Degrees)", required: false, description: "Automatically drops the cooling setpoint by this many extra degrees for the first 45 minutes to counter console heat."
                
                paragraph "<b>Snapshot & Smart Restore:</b>"
                input "gamingRestore_${tNum}", "bool", title: "Enable Smart Restore (Snap-Back)", defaultValue: false, submitOnChange: true, description: "If enabled, takes a snapshot of your lights, blinds, fans, and HVAC before gaming starts, and snaps them back to those exact states when finished."
                if (settings["gamingRestore_${tNum}"]) {
                    input "gamingRestoreModes_${tNum}", "mode", title: "Only Restore in these Modes", multiple: true, required: false, description: "Prevent snap-back if the house mode has changed (e.g., don't turn lights back on if the house has gone to 'Night' mode)."
                    input "gamingRestoreTimeStart_${tNum}", "time", title: "Restore Time Window Start", required: false
                    input "gamingRestoreTimeEnd_${tNum}", "time", title: "Restore Time Window End", required: false
                }
            }
        }
        
        section("Severe Weather Response Override", hideable: true, hidden: true) {
            if (settings["isAvrOnly_${tNum}"]) {
                input "weatherHdmi_${tNum}", "text", title: "Emergency Weather AVR Input Source", required: false, description: "If a global Weather Alert fires, switch to this exact HDMI input string."
            } else {
                paragraph "<i>Weather configurations are set globally on the main page for Smart TV ecosystems.</i>"
            }
        }

        section("TV Show Favorites (Auto-Tune & Turn Off)", hideable: true, hidden: true) {
            paragraph "Schedule up to 2 shows to automatically power on the TV, tune to the channel, and turn the TV off when the show ends."
            for (int s = 1; s <= 2; s++) {
                input "enableShow_${tNum}_${s}", "bool", title: "Enable TV Show ${s}", defaultValue: false, submitOnChange: true, description: "Enable a schedule to auto-launch a specific show or channel."
                if (settings["enableShow_${tNum}_${s}"]) {
                    input "showName_${tNum}_${s}", "text", title: "Show Name (For Logging)", required: false, description: "Enter a friendly name for this show to appear in the history logs."
                    
                    if (settings["isAvrOnly_${tNum}"]) {
                        input "showHdmi_${tNum}_${s}", "text", title: "AVR Input (e.g., HDMI 1)", required: true, description: "The exact AVR input to switch to when this show schedule starts."
                    } else {
                        input "showChannel_${tNum}_${s}", "text", title: "Channel (e.g., 8.1)", required: true, description: "The OTA channel to tune to when this show schedule starts."
                    }
                    
                    input "showTimeStart_${tNum}_${s}", "time", title: "Show Start Time", required: true, description: "The time the TV should automatically turn on and tune."
                    input "showTimeEnd_${tNum}_${s}", "time", title: "Show End Time", required: true, description: "The time the TV should automatically turn off when the show concludes."
                    input "showModes_${tNum}_${s}", "mode", title: "Only Run in These Modes", multiple: true, required: false, description: "Optional: Only execute this schedule if the hub is in one of these modes."
                    input "showDays_${tNum}_${s}", "enum", title: "Days of the Week", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false, description: "Optional: Only execute this schedule on these specific days."
                    
                    input "testShowBtn_${tNum}_${s}", "button", title: "Test Start Show ${s} Now", description: "Force starts the show routine right now to verify power and tuning sequence."
                }
            }
        }
        
        section("Morning Dashboard / Routine", hideable: true, hidden: true) {
            input "enableMorningRoutine_${tNum}", "bool", title: "Enable Morning Routine", defaultValue: false, submitOnChange: true, description: "Automatically fires up the TV to a specific news/weather channel when motion is detected in the morning."
            if (settings["enableMorningRoutine_${tNum}"]) {
                input "morningMotion_${tNum}", "capability.motionSensor", title: "Morning Trigger Motion Sensor", required: false, description: "The very first time this sensor detects motion within the allowed time window, the TV powers on."
                input "morningTimeStart_${tNum}", "time", title: "Routine Allowed Start Time", required: false, description: "The earliest time the morning routine is allowed to trigger."
                input "morningTimeEnd_${tNum}", "time", title: "Routine Allowed End Time", required: false, description: "The latest time the morning routine is allowed to trigger."
                input "morningDays_${tNum}", "enum", title: "Allowed Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false, description: "Optional: Only allow the morning routine to trigger on these days."
                input "morningModes_${tNum}", "mode", title: "Allowed Modes", multiple: true, required: false, description: "Optional: Only allow the morning routine to trigger if the hub is in one of these modes."
                
                if (settings["isAvrOnly_${tNum}"]) {
                    input "morningHdmi_${tNum}", "text", title: "Morning AVR Input Source (e.g., HDMI 1)", required: false, description: "The exact AVR input to switch to for the morning routine."
                } else {
                    input "morningChannel_${tNum}", "text", title: "Morning News/Weather Channel (OTA)", required: false, description: "Forces the TV to this OTA channel (e.g., 8.1 or 12) for morning news."
                    input "morningAppSwitch_${tNum}", "capability.switch", title: "OR Morning App Switch", required: false, description: "Turns on a virtual app switch (e.g., a Roku app switch) instead of tuning a channel."
                }

                input "morningDuration_${tNum}", "number", title: "Routine Duration (Minutes)", required: false, description: "Automatically turns the TV off after this many minutes. Leave blank to stay on indefinitely."
                input "testMorningBtn_${tNum}", "button", title: "Test Morning Routine Now", description: "Forces the morning routine to run right now to verify."
                input "testMorningOffBtn_${tNum}", "button", title: "Stop Morning Routine Test", description: "Forces the morning routine to end and powers down the setup."
            }
        }

        section("Volume Normalization & Safety", hideable: true, hidden: true) {
            input "enableVolumeMgmt_${tNum}", "bool", title: "Enable Volume Management", defaultValue: false, submitOnChange: true, description: "Protects against loud wake-ups by automatically adjusting the volume on startup or tapering it down on shutdown."
            if (settings["enableVolumeMgmt_${tNum}"]) {
                input "startupVolume_${tNum}", "number", title: "Target Startup Volume (0-100)", required: false, description: "Forces this exact absolute volume number every time the display turns on. (Best for AVRs or Network Soundbars)."
                input "shutdownVolumeReduction_${tNum}", "number", title: "Shutdown Volume Reduction (Clicks)", required: false, description: "Lowers the volume by this many clicks exactly when the TV turns off, preventing loud surprises the next time you turn it on."
            }
        }
        
        section("Acoustic Management (Environmental Sync)", hideable: true, hidden: true) {
            input "enableAcousticMgmt_${tNum}", "bool", title: "Enable Smart Acoustic Management", defaultValue: false, submitOnChange: true, description: "Mutes background appliances or dynamically boosts the TV volume when noisy appliances run."
            if (settings["enableAcousticMgmt_${tNum}"]) {
                input "tvNoiseSwitches_${tNum}", "capability.switch", title: "FORCE OFF: Appliances to disable when TV runs", multiple: true, required: false, description: "Select noisy appliances (like air purifiers or fans) that should automatically turn OFF when the TV turns ON, and restore when the TV turns off."
                
                paragraph "<b>Dynamic Volume Boost Engine</b><br>Intelligently calculates the maximum needed volume boost based on active appliances, ensuring clear audio without stacking volumes blindly."
                
                input "mainThermostat_${tNum}", "capability.thermostat", title: "Room Thermostat", required: false, description: "Select the thermostat to monitor for HVAC acoustic boosts."
                
                input "pollThermostat_${tNum}", "bool", title: "Enable Thermostat Polling (Optional)", defaultValue: false, submitOnChange: true, description: "Enable this if your thermostat is slow to report state changes to Hubitat and needs to be actively polled to catch heating/cooling cycles."
                if (settings["pollThermostat_${tNum}"]) {
                    input "pollInterval_${tNum}", "number", title: "Polling Interval (Minutes)", defaultValue: 5, range: "1..10", required: true, description: "How often (in minutes) the app will forcefully ask the thermostat for its current state."
                }
                
                input "hvacVolumeBoost_${tNum}", "number", title: "HVAC Volume Boost (Units)", defaultValue: 3, description: "How many volume units to increase when the HVAC starts running."
                
                input "dishwasher_${tNum}", "capability.switch", title: "Dishwasher Switch / Power State", required: false, description: "Select the smart plug or switch monitoring the dishwasher."
                input "dishwasherBoost_${tNum}", "number", title: "Dishwasher Volume Boost (Units)", defaultValue: 4, description: "How many volume units to increase when the dishwasher is running."
                
                input "vacuum_${tNum}", "capability.switch", title: "Robot Vacuum Switch / Power State", required: false, description: "Select the robot vacuum switch to monitor."
                input "vacuumBoost_${tNum}", "number", title: "Vacuum Volume Boost (Units)", defaultValue: 10, description: "How many volume units to increase when the vacuum is running."
                
                input "airPurifier_${tNum}", "capability.switch", title: "Air Purifier Switch / Power State", required: false, description: "Select the air purifier switch to monitor."
                input "airPurifierBoost_${tNum}", "number", title: "Air Purifier Volume Boost (Units)", defaultValue: 2, description: "How many volume units to increase when the air purifier is running."
            }
        }
        
        section("Lighting & Environmental Sync (Auto-Sync)", hideable: true, hidden: true) {
            input "enableLightingSync_${tNum}", "bool", title: "Enable Environmental Sync", defaultValue: false, submitOnChange: true, description: "Automatically turns off designated lights when you start watching TV, and evaluates blinds before restoring them."
            if (settings["enableLightingSync_${tNum}"]) {
                input "tvLights_${tNum}", "capability.switch", title: "Target Lights", multiple: true, required: false, description: "These lights will automatically turn OFF when the TV turns ON."
                input "tvBlinds_${tNum}", "capability.contactSensor", title: "Room Blinds Evaluator (Contact)", required: false, description: "If selected, the app will ONLY restore the lights upon TV shutdown if these blinds are closed (preventing lights turning on during a sunny day)."
                input "lightRestoreTimeStart_${tNum}", "time", title: "Light Restore Start Time", required: false, description: "Earliest time of day lights are allowed to automatically turn back on."
                input "lightRestoreTimeEnd_${tNum}", "time", title: "Light Restore End Time", required: false, description: "Latest time of day lights are allowed to automatically turn back on."
                input "evaluateRoomBtn_${tNum}", "button", title: "Evaluate Room (Force OFF Lights & Appliances if TV is ON)", description: "Manually triggers the room evaluation to enforce lighting and acoustic sync rules immediately."
            }
        }

        section("Accent & Fireplace Sync (Cozy Mode)", hideable: true, hidden: true) {
            input "enableCozyMode_${tNum}", "bool", title: "Enable Cozy Mode", defaultValue: false, submitOnChange: true, description: "Turns ON specific accent lights (like a fireplace) to a desired level and color temp when the TV turns on, if conditions are right."
            if (settings["enableCozyMode_${tNum}"]) {
                input "cozyLights_${tNum}", "capability.colorTemperature", title: "Accent Lights (e.g., Fireplace)", multiple: true, required: false, description: "Select the color or dimmable lights to use for Cozy Mode."
                input "cozyLevel_${tNum}", "number", title: "Target Dim Level (%)", defaultValue: 50, required: true, range: "1..100", description: "The brightness percentage to set the Cozy Mode lights to."
                input "cozyCTVar_${tNum}", "string", title: "Hub Variable Name for Color Temp (Optional)", required: false, description: "Exact text of the Hub Variable holding your desired Color Temperature (e.g., 'FireplaceCT')."
                input "cozyOvercast_${tNum}", "capability.switch", title: "Overcast Virtual Switch", required: false, description: "Select a virtual switch that indicates cloudy/overcast weather to trigger Cozy Mode during the daytime."
                input "cozyBlinds_${tNum}", "capability.contactSensor", title: "Room Blinds (Closed = Active)", multiple: true, required: false, description: "Select contact sensors on room blinds. If any are closed, Cozy Mode will activate."
                input "cozyOffWithTv_${tNum}", "bool", title: "Turn OFF these lights when TV turns off?", defaultValue: true, description: "If enabled, turns the Cozy Mode lights back off when the TV shuts down."
            }
        }

        section("Auto-Sweeper (Motion Bypass)", hideable: true, hidden: true) {
            input "enableSweeper_${tNum}", "bool", title: "Enable Active Room Sweeper", defaultValue: false, submitOnChange: true, description: "Links specific adjacent lights to specific motion sensors. If a light is ON but the adjacent room is empty, it turns the light off while the TV is running to reduce glare."
            if (settings["enableSweeper_${tNum}"]) {
                paragraph "Configure up to 5 individual lights and their corresponding bypass sensors. If the linked sensor is ACTIVE, the light will not be turned off."
                for (int l = 1; l <= 5; l++) {
                    input "sweepLight_${tNum}_${l}", "capability.switch", title: "Sweeper Light ${l}", required: false, description: "Select a light to monitor. If it is on but its assigned motion sensor is inactive, it will turn off."
                    input "sweepMotion_${tNum}_${l}", "capability.motionSensor", title: "Bypass Motion Sensor ${l}", required: false, description: "Select the motion sensor that must remain active to keep this specific light on."
                }
            }
        }
        
        section("Music & Audio Sync (Sonos)", hideable: true, hidden: true) {
            input "enableMusicSync_${tNum}", "bool", title: "Enable Music Sync", defaultValue: false, submitOnChange: true, description: "Automatically pauses whole-house or background music when you start watching TV, and resumes when done."
            if (settings["enableMusicSync_${tNum}"]) {
                input "sonos_${tNum}", "capability.musicPlayer", title: "Room Music Player (Sonos)", required: false, description: "This player will pause when the TV turns on, and auto-resume when the TV turns off."
                input "sonosResumeModes_${tNum}", "mode", title: "Allowed Modes for Auto-Resume", multiple: true, required: false, description: "Only resume music after TV shutdown if the house is in one of these modes."
                input "sonosResumeTimeStart_${tNum}", "time", title: "Auto-Resume Start Time", required: false, description: "The earliest time background music is allowed to auto-resume."
                input "sonosResumeTimeEnd_${tNum}", "time", title: "Auto-Resume End Time", required: false, description: "The latest time background music is allowed to auto-resume."
            }
        }
        
        section("Power Management & Motion Timeout", hideable: true, hidden: true) {
            input "enableMotionTimeout_${tNum}", "bool", title: "Enable Inactivity Timeout", defaultValue: false, submitOnChange: true, description: "Automatically shuts off the TV if the room is empty to save power."
            if (settings["enableMotionTimeout_${tNum}"]) {
                input "motionSensor_${tNum}", "capability.motionSensor", title: "Room Motion Sensor", required: false, description: "The primary sensor to determine if anyone is actively watching."
                input "motionTimeout_${tNum}", "number", title: "Timeout Delay (Minutes)", required: false, description: "Wait this long after motion stops before completely killing the TV power."
            }
        }
        
        section("Energy & Telemetry", hideable: true, hidden: true) {
            input "tvWattage_${tNum}", "number", title: "Average Wattage of Screen + Audio", defaultValue: 150, required: true, description: "Find the average active power draw of your setup. This is used to calculate financial ROI and daily usage cost on the dashboard."
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.watchTimeToday = state.watchTimeToday ?: [:]
    state.costToday = state.costToday ?: [:]
    state.appStats = state.appStats ?: [:]
    state.historyLog = state.historyLog ?: []
    state.lastMotionTime = state.lastMotionTime ?: [:]
    state.morningRoutineRunDate = state.morningRoutineRunDate ?: [:]
    state.tvWasOffBeforeWeather = [:]
    state.weatherAlertActive = false
    state.evaluatedPowerState = state.evaluatedPowerState ?: [:]
    state.pausedSonos = state.pausedSonos ?: [:]
    state.lightsPausedByTv = state.lightsPausedByTv ?: [:]
    state.noiseSwitchesPaused = state.noiseSwitchesPaused ?: [:]
    state.currentVolumeBoost = state.currentVolumeBoost ?: [:]
    state.cozyLightsActivatedByTv = state.cozyLightsActivatedByTv ?: [:]
    state.activeMacro = state.activeMacro ?: [:]
    state.macroControlledList = state.macroControlledList ?: [:]
    
    // Power State Trackers
    state.plugWasOffBeforeShow = state.plugWasOffBeforeShow ?: [:]
    state.plugWasOffBeforeMorning = state.plugWasOffBeforeMorning ?: [:]
    state.plugWasOffBeforeWeather = state.plugWasOffBeforeWeather ?: [:]
    state.plugWasOffBeforeMacro = state.plugWasOffBeforeMacro ?: [:]
    
    unschedule("trackUsageStep")
    unschedule("pollThermostats")
    unschedule("nightlyMaintenance")
    trackUsageStep()
    schedule("0 0/3 * * * ?", "refreshTVs") 
    schedule("0 0 0 * * ?", "midnightReset")
    schedule("0 * * * * ?", "checkTvShows")
    
    if (settings["bmsNightlyMaintenance"]) {
        schedule("0 0 3 * * ?", "nightlyMaintenance")
    }
    
    def needsPolling = false
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableAcousticMgmt_${i}"] && settings["pollThermostat_${i}"] && settings["mainThermostat_${i}"]) {
            needsPolling = true
        }
        if (settings["enableMovieMode_${i}"] && settings["movieSwitch_${i}"]) {
            subscribe(settings["movieSwitch_${i}"], "switch", macroModeHandler)
        }
        if (settings["enableGamingMode_${i}"] && settings["gamingSwitch_${i}"]) {
            subscribe(settings["gamingSwitch_${i}"], "switch", macroModeHandler)
        }
    }
    if (needsPolling) schedule("0 * * * * ?", "pollThermostats") 
    
    if (settings["enableSafetyMute"]) {
        if (muteContacts) subscribe(muteContacts, "contact", contactHandler)
        if (doorbellButtons) subscribe(doorbellButtons, "pushed", buttonHandler)
    }
    
    if (settings["enableWeatherAlert"] && weatherSwitch) {
        subscribe(weatherSwitch, "switch", weatherSwitchHandler)
    }
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def isAvr = settings["isAvrOnly_${i}"]
        def tv = getPrimaryDevice(i)
        
        if (tv) {
            subscribe(tv, "switch", tvPowerEvaluator)
            subscribe(tv, "power", tvPowerEvaluator)
            subscribe(tv, "mediaInputSource", tvAppHandler)
            if (!isAvr) subscribe(tv, "application", tvAppHandler)
        }
        
        if (settings["enableMotionTimeout_${i}"] && settings["motionSensor_${i}"]) {
            subscribe(settings["motionSensor_${i}"], "motion", tvMotionHandler)
        }
        
        if (settings["enableMorningRoutine_${i}"] && settings["morningMotion_${i}"]) {
            subscribe(settings["morningMotion_${i}"], "motion", morningMotionHandler)
        }
        
        if (settings["enableAcousticMgmt_${i}"]) {
            if (settings["mainThermostat_${i}"]) subscribe(settings["mainThermostat_${i}"], "thermostatOperatingState", acousticDeviceHandler)
            if (settings["dishwasher_${i}"]) subscribe(settings["dishwasher_${i}"], "switch", acousticDeviceHandler)
            if (settings["vacuum_${i}"]) subscribe(settings["vacuum_${i}"], "switch", acousticDeviceHandler)
            if (settings["airPurifier_${i}"]) subscribe(settings["airPurifier_${i}"], "switch", acousticDeviceHandler)
        }
    }
}

// --- BMS Integrity Engines ---

def nightlyMaintenance() {
    log.info "BMS: Executing Nightly Driver Maintenance."
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (tv) {
            if (tv.hasCommand("refresh")) tv.refresh()
            pauseExecution(1000)
            if (tv.hasCommand("initialize")) tv.initialize()
        }
        def audio = getAudioDevice(i)
        if (audio && audio.id != tv?.id) {
            if (audio.hasCommand("refresh")) audio.refresh()
            pauseExecution(1000)
            if (audio.hasCommand("initialize")) audio.initialize()
        }
    }
}

def verifyPowerState(data) {
    def i = data.tvNum
    def action = data.action
    def attempt = data.attempt
    def tv = getPrimaryDevice(i)
    
    def isOn = isTvActuallyOn(tv, i)
    def success = (action == "on" && isOn) || (action == "off" && !isOn)
    
    if (!success) {
        if (attempt < 3) {
            addToHistory("⚠️ ${getTvName(i)}: Heartbeat verification failed (${action}). Retrying attempt ${attempt + 1}...")
            issuePowerCommand(i, action, attempt + 1)
        } else {
            addToHistory("❌ ${getTvName(i)}: Comms Failure. Device did not respond to ${action} command after 3 attempts. Check network connection.")
        }
    } else {
        if (attempt > 1) addToHistory("✅ ${getTvName(i)}: Connection recovered on attempt ${attempt}.")
    }
}

def issuePowerCommand(i, action, attempt = 1) {
    def tv = getPrimaryDevice(i)
    if (!tv) return
    
    if (settings["bmsMeshJitter"]) pauseExecution(new Random().nextInt(2000) + 500)
    
    if (action == "on") tv.on() else tv.off()
    
    if (settings["bmsHeartbeat"]) {
        runIn(10, "verifyPowerState", [data: [tvNum: i, action: action, attempt: attempt]])
    }
}

// --- Device Helpers ---

def getPrimaryDevice(i) {
    if (settings["isAvrOnly_${i}"]) return settings["avr_${i}"]
    return settings["tv_${i}"]
}

def getAudioDevice(i) {
    if (settings["isAvrOnly_${i}"]) return settings["avr_${i}"]
    if (settings["tvAudio_${i}"]) return settings["tvAudio_${i}"]
    return settings["tv_${i}"]
}

// --- Thermostat Polling Engine ---

def pollThermostats() {
    if (isSystemPaused()) return
    def now = new Date().time
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableAcousticMgmt_${i}"] && settings["pollThermostat_${i}"] && settings["mainThermostat_${i}"]) {
            def interval = settings["pollInterval_${i}"] ?: 5
            def lastPoll = state."lastThermoPoll_${i}" ?: 0
            
            if ((now - lastPoll) >= ((interval * 60000) - 2000)) { 
                def thermo = settings["mainThermostat_${i}"]
                if (thermo.hasCommand("refresh")) {
                    thermo.refresh()
                    state."lastThermoPoll_${i}" = now
                }
            }
        }
    }
}

// --- Snapshot & Smart Restore Engine ---

def captureState(i, prefix) {
    def cap = [:]
    def lightsOff = settings["${prefix}LightsOff_${i}"]
    if (lightsOff) cap.lightsOff = lightsOff.collectEntries { [(it.id): it.currentValue("switch")] }
    
    def lightsOn = settings["${prefix}LightsOn_${i}"]
    if (lightsOn) cap.lightsOn = lightsOn.collectEntries { [(it.id): [switch: it.currentValue("switch"), level: it.currentValue("level")]] }
    
    def fans = settings["${prefix}Fans_${i}"]
    if (fans) cap.fans = fans.collectEntries { [(it.id): it.currentValue("speed")] }
    
    def shades = settings["${prefix}Shades_${i}"]
    if (shades) cap.shades = shades.collectEntries { [(it.id): it.currentValue("windowShade")] }
    
    def thermo = settings["${prefix}Thermostat_${i}"]
    if (thermo) cap.thermo = [id: thermo.id, heat: thermo.currentValue("heatingSetpoint"), cool: thermo.currentValue("coolingSetpoint")]
    
    state."captured_${prefix}_${i}" = cap
    addToHistory("${getTvName(i)}: Environment Snapshot Captured.")
}

def restoreState(i, prefix) {
    def cap = state."captured_${prefix}_${i}"
    if (!cap) return
    
    def allowedModes = settings["${prefix}RestoreModes_${i}"]
    if (allowedModes && !allowedModes.contains(location.mode)) {
        addToHistory("${getTvName(i)}: Snap-Back aborted (House Mode changed).")
        return
    }
    
    def startTime = settings["${prefix}RestoreTimeStart_${i}"]
    def endTime = settings["${prefix}RestoreTimeEnd_${i}"]
    if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) {
        addToHistory("${getTvName(i)}: Snap-Back aborted (Outside allowed time window).")
        return
    }
    
    def lightsOff = settings["${prefix}LightsOff_${i}"]
    if (lightsOff && cap.lightsOff) {
        lightsOff.each { if (cap.lightsOff[it.id] == "on") it.on() }
    }
    
    def lightsOn = settings["${prefix}LightsOn_${i}"]
    if (lightsOn && cap.lightsOn) {
        lightsOn.each { 
            def stored = cap.lightsOn[it.id]
            if (stored?.switch == "off") it.off()
            else if (stored?.level != null && it.hasCommand("setLevel")) it.setLevel(stored.level)
        }
    }
    
    def shades = settings["${prefix}Shades_${i}"]
    if (shades && cap.shades) {
        shades.each { if (cap.shades[it.id] == "open" || cap.shades[it.id] == "partially open") it.open() }
    }
    
    def fans = settings["${prefix}Fans_${i}"]
    if (fans && cap.fans) {
        fans.each { if (cap.fans[it.id]) it.setSpeed(cap.fans[it.id]) }
    }
    
    def thermo = settings["${prefix}Thermostat_${i}"]
    if (thermo && cap.thermo) {
        if (cap.thermo.heat) thermo.setHeatingSetpoint(cap.thermo.heat)
        if (cap.thermo.cool) thermo.setCoolingSetpoint(cap.thermo.cool)
    }
    
    addToHistory("${getTvName(i)}: Smart Restore Complete. Room snapped back to original state.")
    state."captured_${prefix}_${i}" = null
}

def revertPreCool(data) {
    def i = data.tvNum
    def prefix = data.macro
    def thermo = settings["${prefix}Thermostat_${i}"]
    def cap = state."captured_${prefix}_${i}"
    if (thermo && cap && cap.thermo?.cool) {
        def target = settings["${prefix}CoolSetpoint_${i}"] ?: cap.thermo.cool
        thermo.setCoolingSetpoint(target)
        addToHistory("${getTvName(i)}: Thermodynamic Pre-Cool duration ended. Reverting offset.")
    }
}

// --- Dedicated Macro Engines (Movie & Gaming) ---

def macroModeHandler(evt) {
    if (isSystemPaused()) return
    def devId = evt.device.id
    def isOn = evt.value == "on"
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def isMovie = settings["enableMovieMode_${i}"] && settings["movieSwitch_${i}"]?.id == devId
        def isGaming = settings["enableGamingMode_${i}"] && settings["gamingSwitch_${i}"]?.id == devId
        
        if (isMovie || isGaming) {
            def prefix = isMovie ? "movie" : "gaming"
            def tvName = getTvName(i)
            
            if (isOn) {
                def allowedModes = settings["${prefix}Modes_${i}"]
                if (allowedModes && !allowedModes.contains(location.mode)) {
                    addToHistory("${tvName}: Macro aborted. Incorrect House Mode.")
                    settings["${prefix}Switch_${i}"].off()
                    return
                }
                def startTime = settings["${prefix}TimeStart_${i}"]
                def endTime = settings["${prefix}TimeEnd_${i}"]
                if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) {
                    addToHistory("${tvName}: Macro aborted. Outside allowed time window.")
                    settings["${prefix}Switch_${i}"].off()
                    return
                }
                
                addToHistory("${tvName}: ${prefix.capitalize()} Mode Initiated!")
                state.activeMacro["${i}"] = prefix
                if (settings["bmsPriorityLock"]) settings["bmsPriorityLock"].on()
                
                if (settings["${prefix}Restore_${i}"]) captureState(i, prefix)
                executeMacroEnvironment(i, prefix)
                triggerRoutine(i, settings["${prefix}Target_${i}"], "macro", prefix)
                
            } else {
                addToHistory("${tvName}: ${prefix.capitalize()} Mode Deactivated. Powering off system.")
                state.activeMacro["${i}"] = null
                endRoutine(i, "macro", prefix)
                
                if (settings["${prefix}Restore_${i}"]) restoreState(i, prefix)
                
                // Unlock global priority if no other TVs are active
                def anyActive = false
                for (int j = 1; j <= (numTVs as Integer); j++) { if (state.activeMacro["${j}"]) anyActive = true }
                if (!anyActive && settings["bmsPriorityLock"]) settings["bmsPriorityLock"].off()
            }
        }
    }
}

def executeMacroEnvironment(i, prefix) {
    def tvName = getTvName(i)
    def actions = []
    
    def lightsOff = settings["${prefix}LightsOff_${i}"]
    if (lightsOff) { lightsOff.each { it.off() }; actions << "Lights Off" }
    
    def lightsOn = settings["${prefix}LightsOn_${i}"]
    if (lightsOn) {
        def lvl = settings["${prefix}LightsLevel_${i}"]
        lightsOn.each { 
            if (lvl != null && it.hasCommand("setLevel")) it.setLevel(lvl)
            else it.on()
        }
        actions << "Ambiance Lights Set"
    }
    
    def shades = settings["${prefix}Shades_${i}"]
    if (shades) { shades.each { it.close() }; actions << "Shades Closed" }
    
    def locks = settings["${prefix}Locks_${i}"]
    if (locks) { locks.each { it.lock() }; actions << "Doors Locked" }
    
    def fans = settings["${prefix}Fans_${i}"]
    def fanSpeed = settings["${prefix}FanSpeed_${i}"]
    if (fans && fanSpeed) { fans.each { it.setSpeed(fanSpeed) }; actions << "Fans Set" }
    
    def thermo = settings["${prefix}Thermostat_${i}"]
    if (thermo) {
        def cool = settings["${prefix}CoolSetpoint_${i}"]
        def heat = settings["${prefix}HeatSetpoint_${i}"]
        def preCool = settings["${prefix}PreCool_${i}"]
        
        if (cool) {
            def targetCool = preCool ? (cool - preCool) : cool
            thermo.setCoolingSetpoint(targetCool)
            if (preCool) runIn(45 * 60, "revertPreCool", [data: [tvNum: i, macro: prefix], overwrite: false])
        }
        if (heat) thermo.setHeatingSetpoint(heat)
        actions << "HVAC Adjusted"
    }
    
    state.macroControlledList["${i}"] = actions.join(", ")
    if (actions.size() > 0) {
        addToHistory("${tvName}: Environment Applied (${actions.join(', ')}).")
    }
}

// --- Central Routine Handlers (Plug + TV Sequencing) ---

def triggerRoutine(i, targetString, source, macroPrefix = null) {
    def plug = settings["tvPlug_${i}"]
    def tv = getPrimaryDevice(i)
    def isPlugOff = plug && plug.currentValue("switch") == "off"
    
    if (isPlugOff) {
        if (source == "weather") state.plugWasOffBeforeWeather["${i}"] = true
        else if (source == "morning") state.plugWasOffBeforeMorning["${i}"] = true
        else if (source == "show") state.plugWasOffBeforeShow["${i}_${macroPrefix}"] = true
        else if (source == "macro") state.plugWasOffBeforeMacro["${i}"] = true
        
        addToHistory("${getTvName(i)}: Powering on smart plug for routine.")
        plug.on()
        
        runIn(20, "executeTvPowerOn", [data: [tvNum: i, channel: targetString, source: source, macroPrefix: macroPrefix], overwrite: false])
    } else {
        if (source == "weather") state.plugWasOffBeforeWeather["${i}"] = false
        else if (source == "morning") state.plugWasOffBeforeMorning["${i}"] = false
        else if (source == "show") state.plugWasOffBeforeShow["${i}_${macroPrefix}"] = false
        else if (source == "macro") state.plugWasOffBeforeMacro["${i}"] = false
        
        executeTvPowerOn([tvNum: i, channel: targetString, source: source, macroPrefix: macroPrefix])
    }
}

def executeTvPowerOn(data) {
    def i = data.tvNum as Integer
    def tv = getPrimaryDevice(i)
    
    if (!isTvActuallyOn(tv, i)) {
        issuePowerCommand(i, "on", 1)
        runIn(18, "executeMediaAction", [data: data, overwrite: false])
    } else {
        runIn(4, "executeMediaAction", [data: data, overwrite: false])
    }
}

def executeMediaAction(data) {
    def i = data.tvNum
    def target = data.channel
    def source = data.source
    def prefix = data.macroPrefix
    
    if (settings["isAvrOnly_${i}"]) {
        def avr = settings["avr_${i}"]
        if (avr && target) {
            addToHistory("${getTvName(i)}: Changing AVR Input to [${target}].")
            if (avr.hasCommand("setInputSource")) avr.setInputSource(target)
            else if (avr.hasCommand("setMediaInputSource")) avr.setMediaInputSource(target)
        }
    } else {
        def appSwitch = null
        if (source == "weather") appSwitch = settings["weatherAppSwitch"]
        else if (source == "morning") appSwitch = settings["morningAppSwitch_${i}"]
        else if (source == "macro") appSwitch = settings["${prefix}AppSwitch_${i}"]
        
        if (appSwitch) {
            addToHistory("${getTvName(i)}: Launching application via switch [${appSwitch.displayName}].")
            appSwitch.on()
        } else if (target) {
            executeSetChannel(data)
        }
    }
    
    if (source == "macro" && prefix) {
        def targetVol = settings["${prefix}Vol_${i}"]
        if (targetVol != null) {
            def audioDev = getAudioDevice(i)
            def audioProtocol = settings["isAvrOnly_${i}"] ? settings["avrType_${i}"] : settings["audioType_${i}"]
            def isAbsolute = audioProtocol in ["Network AVR / Absolute (SetLevel 0-100)", "Onkyo / Pioneer Protocol", "Denon / Marantz", "Yamaha", "Onkyo / Pioneer", "Sony", "Generic / Other"]
            
            if (isAbsolute || (!audioDev.hasCommand("volumeUp") && audioDev.hasCommand("setLevel"))) {
                audioDev.setLevel(targetVol)
            } else {
                adjustVolumeRelative(audioDev, targetVol, "up", audioProtocol)
            }
            addToHistory("${getTvName(i)}: Macro volume command processed.")
        }
    }
}

def endRoutine(i, source, macroPrefix = null) {
    def tv = getPrimaryDevice(i)
    if (tv && isTvActuallyOn(tv, i)) {
        addToHistory("${getTvName(i)}: Routine (${source}) ended. Powering OFF.")
        issuePowerCommand(i, "off", 1)
    }
    runIn(8, "evaluatePlugShutdown", [data: [tvNum: i, source: source, macroPrefix: macroPrefix], overwrite: false])
}

def evaluatePlugShutdown(data) {
    def i = data.tvNum as Integer
    def source = data.source
    def macroPrefix = data.macroPrefix
    def plug = settings["tvPlug_${i}"]
    
    def cutPower = false
    if (source == "weather" && state.plugWasOffBeforeWeather["${i}"]) cutPower = true
    else if (source == "morning" && state.plugWasOffBeforeMorning["${i}"]) cutPower = true
    else if (source == "show" && state.plugWasOffBeforeShow["${i}_${macroPrefix}"]) cutPower = true
    else if (source == "macro" && state.plugWasOffBeforeMacro["${i}"]) cutPower = true
    
    if (cutPower && plug) {
        addToHistory("${getTvName(i)}: Cutting power to smart plug (was off before routine).")
        plug.off()
        
        if (source == "weather") state.plugWasOffBeforeWeather["${i}"] = false
        else if (source == "morning") state.plugWasOffBeforeMorning["${i}"] = false
        else if (source == "show") state.plugWasOffBeforeShow["${i}_${macroPrefix}"] = false
        else if (source == "macro") state.plugWasOffBeforeMacro["${i}"] = false
    }
}

// --- Scheduled TV Shows ---

def checkTvShows() {
    if (isSystemPaused()) return
    def now = new Date()
    def today = now.format("EEEE", location.timeZone)
    def currentTime = now.format("HH:mm", location.timeZone)

    for (int i = 1; i <= (numTVs as Integer); i++) {
        for (int s = 1; s <= 2; s++) {
            if (settings["enableShow_${i}_${s}"]) {
                def days = settings["showDays_${i}_${s}"]
                if (days && !days.contains(today)) continue

                def modes = settings["showModes_${i}_${s}"]
                if (modes && !modes.contains(location.mode)) continue

                def startStr = settings["showTimeStart_${i}_${s}"]
                if (startStr) {
                    def startFormatted = timeToday(startStr, location.timeZone).format("HH:mm", location.timeZone)
                    if (currentTime == startFormatted) {
                        startTvShow(i, s)
                    }
                }

                def endStr = settings["showTimeEnd_${i}_${s}"]
                if (endStr) {
                    def endFormatted = timeToday(endStr, location.timeZone).format("HH:mm", location.timeZone)
                    if (currentTime == endFormatted) {
                        endTvShow(i, s)
                    }
                }
            }
        }
    }
}

def startTvShow(i, s) {
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"
    def target = settings["isAvrOnly_${i}"] ? settings["showHdmi_${i}_${s}"] : settings["showChannel_${i}_${s}"]
    
    addToHistory("${getTvName(i)}: Starting scheduled show [${showName}].")
    triggerRoutine(i, target, "show", s)
}

def endTvShow(i, s) {
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"
    addToHistory("${getTvName(i)}: Scheduled show [${showName}] ended.")
    endRoutine(i, "show", s)
}

// --- TV State & Power Evaluator ---

def refreshTVs() {
    if (isSystemPaused()) return
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (tv && tv.hasCommand("refresh")) tv.refresh()
    }
}

def isTvActuallyOn(tv, i) {
    if (!tv) return false
    def sw = tv.currentValue("switch")
    def pwr = tv.currentValue("power")
    
    if (sw == "off" || pwr in ["PowerOff", "Off", "DisplayOff", "Headless"]) return false
    
    if (settings["isAvrOnly_${i}"]) {
        return sw == "on"
    }
    
    def app = tv.currentValue("application")
    if (app != null) {
        def tvType = settings["tvType_${i}"] ?: "Generic / Other"
        def idleApps = ["none", "Home", "Ambient", "Screen Saver"] 
        
        if (tvType == "Roku TV") idleApps = ["Roku Dynamic Menu", "Backdrops", "Roku Media Player", "Home", "none"]
        else if (tvType == "LG WebOS") idleApps = ["none", "Home", "Screen Saver", "Art Gallery"]
        else if (tvType == "Apple TV") idleApps = ["com.apple.TVIdleScreen", "Home"]
        else if (tvType == "Android TV / Google TV") idleApps = ["Backdrop", "Home", "none"]
        else if (tvType == "Samsung Smart TV") idleApps = ["none", "Home", "Ambient"]
        
        if (idleApps.contains(app)) return false
    }
    
    def transport = tv.currentValue("transportStatus")
    if (sw == "on" && pwr == "Ready" && transport == "stopped") return false
    
    return sw == "on"
}

def tvPowerEvaluator(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def primary = getPrimaryDevice(i)
        if (primary?.id == deviceId) {
            def tvName = getTvName(i)
            def isTrulyOn = isTvActuallyOn(primary, i)
            def lastEvaluatedState = state.evaluatedPowerState["${i}"] ?: false
            
            if (isTrulyOn && !lastEvaluatedState) {
                state.evaluatedPowerState["${i}"] = true
                addToHistory("${tvName}: Power State changed to ON.")
                
                if (primary.hasCommand("refresh")) primary.refresh()
                
                if (settings["enableVolumeMgmt_${i}"]) {
                    def targetVol = settings["startupVolume_${i}"]
                    def audioDevice = getAudioDevice(i)
                    if (targetVol != null && audioDevice) {
                        def audioProtocol = settings["isAvrOnly_${i}"] ? settings["avrType_${i}"] : settings["audioType_${i}"]
                        def isAbsolute = audioProtocol in ["Network AVR / Absolute (SetLevel 0-100)", "Onkyo / Pioneer Protocol", "Denon / Marantz", "Yamaha", "Onkyo / Pioneer", "Sony", "Generic / Other"]
                        
                        if (isAbsolute || (!audioDevice.hasCommand("setVolume") && audioDevice.hasCommand("setLevel"))) {
                            audioDevice.setLevel(targetVol)
                        } else if (audioDevice.hasCommand("setVolume")) {
                            audioDevice.setVolume(targetVol)
                        }
                        addToHistory("${tvName}: Startup absolute volume adjusted to ${targetVol}.")
                    }
                }
                
                if (settings["enableAcousticMgmt_${i}"]) {
                    def noiseSwitches = settings["tvNoiseSwitches_${i}"]
                    if (noiseSwitches) {
                        def activeNoise = noiseSwitches.findAll { it.currentValue("switch") == "on" }
                        if (activeNoise) {
                            addToHistory("${tvName}: Background noise detected. Turning OFF: ${activeNoise.join(', ')}")
                            activeNoise.each { it.off() } 
                            state.noiseSwitchesPaused["${i}"] = activeNoise.collect { it.id }
                        } else {
                            state.noiseSwitchesPaused["${i}"] = []
                        }
                    }
                    evaluateAcoustics(i) 
                }
               
                if (settings["enableLightingSync_${i}"]) {
                    def lights = settings["tvLights_${i}"]
                    if (lights) {
                         def activeLights = lights.findAll { it.currentValue("switch") == "on" }
                         if (activeLights) {
                            addToHistory("${tvName}: Environment sync. Delaying 2s to turn OFF lights.")
                            state.lightsPausedByTv["${i}"] = true
                            runIn(2, "delayedLightTurnOff", [data: [tvNum: i], overwrite: false])
                        } else {
                            state.lightsPausedByTv["${i}"] = false
                        }
                    }
                }

                if (settings["enableCozyMode_${i}"]) {
                    def cozyLights = settings["cozyLights_${i}"]
                    if (cozyLights) {
                        def overcast = settings["cozyOvercast_${i}"]
                        def blinds = settings["cozyBlinds_${i}"]
                        def isOvercast = overcast && overcast.currentValue("switch") == "on"
                        def blindsClosed = blinds && blinds.any { it.currentValue("contact") == "closed" }

                        if (isOvercast || blindsClosed) {
                            def targetLevel = settings["cozyLevel_${i}"] ?: 50
                            def ctVarName = settings["cozyCTVar_${i}"]
                            def targetCT = null

                            if (ctVarName) {
                                def hubVar = getGlobalVar(ctVarName)
                                if (hubVar != null && hubVar.value != null) targetCT = hubVar.value.toInteger()
                            }

                            if (targetCT != null) {
                                addToHistory("${tvName}: Cozy Mode conditions met. Setting accent lights to ${targetLevel}% and ${targetCT}K.")
                                cozyLights.each { bulb ->
                                    if (bulb.hasCommand("setColorTemperature")) bulb.setColorTemperature(targetCT, targetLevel)
                                    else bulb.setLevel(targetLevel)
                                }
                            } else {
                                addToHistory("${tvName}: Cozy Mode conditions met. Setting accent lights to ${targetLevel}%.")
                                cozyLights.each { it.setLevel(targetLevel) }
                            }
                            
                            state.cozyLightsActivatedByTv["${i}"] = true
                        } else {
                            state.cozyLightsActivatedByTv["${i}"] = false
                        }
                    }
                }
               
                if (settings["enableSweeper_${i}"]) {
                    runIn(4, "executeSweeperDelay", [data: [tvNum: i, isPeriodic: false], overwrite: false])
                }
                
                if (settings["enableMusicSync_${i}"]) {
                    def sonos = settings["sonos_${i}"]
                    if (sonos) {
                        def sStatus = sonos.currentValue("transportStatus") ?: sonos.currentValue("status")
                        if (sStatus == "playing") {
                            addToHistory("${tvName}: Auto-pausing Sonos for TV audio.")
                            sonos.pause()
                            state.pausedSonos["${i}"] = true
                        } else {
                            state.pausedSonos["${i}"] = false
                        }
                    }
                }
                 
            } else if (!isTrulyOn && lastEvaluatedState) {
                state.evaluatedPowerState["${i}"] = false
                state.currentVolumeBoost["${i}"] = 0 
                addToHistory("${tvName}: Power State changed to OFF.")
                
                if (settings["enableVolumeMgmt_${i}"]) {
                    def reduceClicks = settings["shutdownVolumeReduction_${i}"]
                    if (reduceClicks && reduceClicks > 0) {
                        def audioDev = getAudioDevice(i)
                        def audioProtocol = settings["isAvrOnly_${i}"] ? settings["avrType_${i}"] : settings["audioType_${i}"]
                        addToHistory("${tvName}: Tapering volume down by ${reduceClicks} clicks for quiet startup.")
                        adjustVolumeRelative(audioDev, reduceClicks, "down", audioProtocol)
                     }
                }

                if (settings["enableAcousticMgmt_${i}"]) {
                    def noiseSwitches = settings["tvNoiseSwitches_${i}"]
                    def pausedIds = state.noiseSwitchesPaused["${i}"] ?: []
                    if (noiseSwitches && pausedIds) {
                        def toRestore = noiseSwitches.findAll { pausedIds.contains(it.id) }
                        if (toRestore) {
                             addToHistory("${tvName}: Restoring background appliances: ${toRestore.join(', ')}")
                            toRestore.each { it.on() } 
                        }
                         state.noiseSwitchesPaused["${i}"] = []
                    }
                }

                if (settings["enableLightingSync_${i}"]) {
                    def lights = settings["tvLights_${i}"]
                    if (lights && state.lightsPausedByTv["${i}"]) {
                        state.lightsPausedByTv["${i}"] = false 
                        def blind = settings["tvBlinds_${i}"]
                        def isBlindClosed = blind ? (blind.currentValue("contact") == "closed") : true
                        def startTime = settings["lightRestoreTimeStart_${i}"]
                        def endTime = settings["lightRestoreTimeEnd_${i}"]
                        def timeOk = true
                        
                        if (startTime && endTime) timeOk = timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)
                        
                        if (isBlindClosed && timeOk) {
                             addToHistory("${tvName}: Conditions met. Restoring lights.")
                             lights.each { it.on() } 
                        }
                    }
                }

                if (settings["enableCozyMode_${i}"] && settings["cozyOffWithTv_${i}"] && state.cozyLightsActivatedByTv["${i}"]) {
                    def cozyLights = settings["cozyLights_${i}"]
                    if (cozyLights) {
                        addToHistory("${tvName}: TV shutting down. Turning OFF Cozy Mode lights.")
                        cozyLights.each { it.off() }
                    }
                    state.cozyLightsActivatedByTv["${i}"] = false
                }
                
                if (settings["enableMusicSync_${i}"]) {
                    def sonos = settings["sonos_${i}"]
                    if (sonos && state.pausedSonos["${i}"]) {
                         state.pausedSonos["${i}"] = false
                        def allowedModes = settings["sonosResumeModes_${i}"]
                        def startTime = settings["sonosResumeTimeStart_${i}"]
                        def endTime = settings["sonosResumeTimeEnd_${i}"]
                        def modeOk = !allowedModes || allowedModes.contains(location.mode)
                        def timeOk = true
                        
                        if (startTime && endTime) timeOk = timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)
                        
                        if (modeOk && timeOk) {
                             addToHistory("${tvName}: Conditions met. Auto-resuming Sonos.")
                            sonos.play()
                        }
                    }
                 }
            }
        }
    }
}

// --- Smart Acoustic Management Engine ---

def acousticDeviceHandler(evt) {
    if (isSystemPaused()) return
    def devId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (!settings["enableAcousticMgmt_${i}"]) continue
        
        def isMatch = false
        if (settings["mainThermostat_${i}"]?.id == devId) isMatch = true
        else if (settings["dishwasher_${i}"]?.id == devId) isMatch = true
        else if (settings["vacuum_${i}"]?.id == devId) isMatch = true
        else if (settings["airPurifier_${i}"]?.id == devId) isMatch = true
        
        if (isMatch) evaluateAcoustics(i)
    }
}

def evaluateAcoustics(i) {
    def primary = getPrimaryDevice(i)
    if (!isTvActuallyOn(primary, i)) {
        state.currentVolumeBoost["${i}"] = 0
        return
    }
    
    def maxBoost = 0
    
    def thermo = settings["mainThermostat_${i}"]
    if (thermo && thermo.currentValue("thermostatOperatingState") in ["heating", "cooling", "fan only"]) {
        maxBoost = Math.max(maxBoost, (settings["hvacVolumeBoost_${i}"] ?: 3) as Integer)
    }
    
    def dish = settings["dishwasher_${i}"]
    if (dish && dish.currentValue("switch") == "on") {
        maxBoost = Math.max(maxBoost, (settings["dishwasherBoost_${i}"] ?: 4) as Integer)
    }
    
    def vac = settings["vacuum_${i}"]
    if (vac && vac.currentValue("switch") == "on") {
        maxBoost = Math.max(maxBoost, (settings["vacuumBoost_${i}"] ?: 10) as Integer)
    }
    
    def ap = settings["airPurifier_${i}"]
    if (ap && ap.currentValue("switch") == "on") {
        maxBoost = Math.max(maxBoost, (settings["airPurifierBoost_${i}"] ?: 2) as Integer)
    }
    
    def currentBoost = state.currentVolumeBoost["${i}"] ?: 0
    def diff = maxBoost - currentBoost
    
    if (diff != 0) {
        def audioDev = getAudioDevice(i)
        def audioProtocol = settings["isAvrOnly_${i}"] ? settings["avrType_${i}"] : settings["audioType_${i}"]
        def direction = diff > 0 ? "up" : "down"
        def amount = Math.abs(diff)
        
        def action = direction == "up" ? "Boosting" : "Reducing"
        addToHistory("${getTvName(i)}: Smart Acoustic adjustment. ${action} volume by ${amount} units. (New Maximum Requirement: ${maxBoost})")
        
        adjustVolumeRelative(audioDev, amount, direction, audioProtocol)
        state.currentVolumeBoost["${i}"] = maxBoost
    }
}

// --- Volume Normalization Engine ---

def adjustVolumeRelative(audioDevice, amount, direction, protocol = "") {
    if (!audioDevice) return
    
    def isAbsolute = protocol in ["Network AVR / Absolute (SetLevel 0-100)", "Onkyo / Pioneer Protocol", "Denon / Marantz", "Yamaha", "Onkyo / Pioneer", "Sony", "Generic / Other"]
    
    if (isAbsolute || (!audioDevice.hasCommand("volumeUp") && audioDevice.hasCommand("setLevel"))) {
        def currentLevelObj = audioDevice.currentValue("level") ?: audioDevice.currentValue("volume") ?: 50
        def currentLevel = currentLevelObj as Integer
        def amountInt = amount as Integer
        def newLevel = direction == "up" ? currentLevel + amountInt : currentLevel - amountInt
        
        if (newLevel < 0) newLevel = 0
        if (newLevel > 100) newLevel = 100
        
        if (audioDevice.hasCommand("setLevel")) audioDevice.setLevel(newLevel)
        else if (audioDevice.hasCommand("setVolume")) audioDevice.setVolume(newLevel)
        return
    }
    
    for (int j = 0; j < amount; j++) {
        if (direction == "up") {
            if (audioDevice.hasCommand("volumeUp")) audioDevice.volumeUp()
        } else {
            if (audioDevice.hasCommand("volumeDown")) audioDevice.volumeDown()
        }
        pauseExecution(300) 
    }
}


// --- Secondary Feature Handlers ---

def delayedLightTurnOff(data) {
    def i = data.tvNum
    def lights = settings["tvLights_${i}"]
    if (lights) {
        def activeLights = lights.findAll { it.currentValue("switch") == "on" }
        if (activeLights) {
            activeLights.each { it.off() }
        }
    }
}

def executeSweeperDelay(data) {
    executeSweeper(data.tvNum, data.isPeriodic)
}

def executeSweeper(i, isPeriodic) {
    if (!settings["enableSweeper_${i}"]) return
    def tv = getPrimaryDevice(i)
    if (!isTvActuallyOn(tv, i)) return
    
    def sweptDevices = []
    def bypassedDevices = []
    
    for (int l = 1; l <= 5; l++) {
        def light = settings["sweepLight_${i}_${l}"]
        def motion = settings["sweepMotion_${i}_${l}"]
        
        if (light && light.currentValue("switch") == "on") {
            def hasMotion = motion ? (motion.currentValue("motion") == "active") : false
            
            if (!hasMotion) {
                light.off()
                sweptDevices << light.displayName
            } else {
                bypassedDevices << light.displayName
            }
        }
    }
    
    if (sweptDevices) {
        addToHistory("${getTvName(i)}: Sweeper turned OFF: ${sweptDevices.join(', ')}")
    }
    
    if (bypassedDevices && !isPeriodic) {
        addToHistory("${getTvName(i)}: Sweeper bypassed (Motion Active): ${bypassedDevices.join(', ')}")
    }
}

def evaluateRoomLights(i) {
    def tv = getPrimaryDevice(i)
    if (isTvActuallyOn(tv, i)) {
        def actionTaken = false
        
        if (settings["enableLightingSync_${i}"]) {
            def lights = settings["tvLights_${i}"]
            if (lights) {
                def activeLights = lights.findAll { it.currentValue("switch") == "on" }
                if (activeLights) {
                    addToHistory("${getTvName(i)}: Room Evaluation - Forcing lights OFF.")
                    activeLights.each { it.off() }
                    state.lightsPausedByTv["${i}"] = true
                    actionTaken = true
                }
            }
        }
        
        if (settings["enableAcousticMgmt_${i}"]) {
            def noiseSwitches = settings["tvNoiseSwitches_${i}"]
            if (noiseSwitches) {
                def activeNoise = noiseSwitches.findAll { it.currentValue("switch") == "on" }
                if (activeNoise) {
                    addToHistory("${getTvName(i)}: Room Evaluation - Forcing background appliances OFF.")
                    activeNoise.each { it.off() }
                    
                    def existingPaused = state.noiseSwitchesPaused["${i}"] ?: []
                    def newPaused = activeNoise.collect { it.id }
                    state.noiseSwitchesPaused["${i}"] = (existingPaused + newPaused).unique()
                    
                    actionTaken = true
                }
            }
        }
        
        if (settings["enableSweeper_${i}"]) {
             executeSweeper(i, false)
             actionTaken = true
        }
        
        if (!actionTaken) {
             addToHistory("${getTvName(i)}: Room Evaluation - Assigned devices are already off or sync is disabled.")
        }
        
    } else {
        addToHistory("${getTvName(i)}: Room Evaluation ignored (TV not active).")
    }
}

def trackUsageStep() {
    if (isSystemPaused()) return
    def rate = settings["elecRate"] ?: 0.14
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (isTvActuallyOn(tv, i)) {
            def wattage = settings["tvWattage_${i}"] ?: 150
            def costPerMin = (wattage / 1000.0) * rate / 60.0
            state.watchTimeToday["${i}"] = (state.watchTimeToday["${i}"] ?: 0) + 5
            state.costToday["${i}"] = (state.costToday["${i}"] ?: 0.0) + (costPerMin * 5)
            
            def currentApp = "Unknown"
            if (settings["isAvrOnly_${i}"]) {
                def rawInput = tv.currentValue("mediaInputSource") ?: "Unknown"
                currentApp = getMappedAppName(i, rawInput)
            } else {
                currentApp = tv.currentValue("application") ?: "Unknown/Home"
            }
            
            if (!state.appStats["${i}"]) state.appStats["${i}"] = [:]
            state.appStats["${i}"][currentApp] = (state.appStats["${i}"][currentApp] ?: 0) + 5
            
            if (settings["enableSweeper_${i}"]) {
                executeSweeper(i, true)
            }
        }
    }
    runIn(300, "trackUsageStep") 
}

def midnightReset() {
    state.watchTimeToday = [:]
    state.costToday = [:]
    state.appStats = [:]
}

def getMappedAppName(i, rawName) {
    if (!settings["isAvrOnly_${i}"]) return rawName
    for (int h = 1; h <= 5; h++) {
        if (settings["hdmiSource_${i}_${h}"] == rawName && settings["hdmiName_${i}_${h}"]) {
            return settings["hdmiName_${i}_${h}"]
        }
    }
    return rawName
}

def tvAppHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def rawApp = evt.value
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def primary = getPrimaryDevice(i)
        if (primary?.id == deviceId && isTvActuallyOn(primary, i)) {
            def appName = getMappedAppName(i, rawApp)
            addToHistory("${getTvName(i)}: Content/Input changed to [${appName}].")
        }
    }
}

def tvMotionHandler(evt) {
    def deviceId = evt.device.id
    def isActive = evt.value == "active"
    def now = new Date().time
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableMotionTimeout_${i}"] && settings["motionSensor_${i}"]?.id == deviceId) {
            if (isActive) state.lastMotionTime["${i}"] = now
            else {
                def timeout = settings["motionTimeout_${i}"]
                if (timeout) runIn(timeout * 60, "executeTvTimeout", [data: [tvNum: i], overwrite: false])
            }
        }
    }
}

def executeTvTimeout(data) {
    if (isSystemPaused()) return
    def i = data.tvNum
    if (!settings["enableMotionTimeout_${i}"]) return
    
    def tv = getPrimaryDevice(i)
    def timeout = settings["motionTimeout_${i}"]
    if (!tv || !timeout || !isTvActuallyOn(tv, i)) return
    def lastMotion = state.lastMotionTime["${i}"] ?: 0
    def now = new Date().time
    if ((now - lastMotion) >= (timeout * 60000) - 2000) {
        addToHistory("${getTvName(i)}: No motion detected. Powering OFF.")
        issuePowerCommand(i, "off", 1)
    }
}

def morningMotionHandler(evt) {
    if (evt.value != "active" || isSystemPaused()) return
    def deviceId = evt.device.id
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableMorningRoutine_${i}"] && settings["morningMotion_${i}"]?.id == deviceId) {
            def today = new Date().format("yyyy-MM-dd", location.timeZone)
            if (state.morningRoutineRunDate["${i}"] == today) continue 
            def allowedModes = settings["morningModes_${i}"]
            if (allowedModes && !allowedModes.contains(location.mode)) continue
            def startTime = settings["morningTimeStart_${i}"]
            def endTime = settings["morningTimeEnd_${i}"]
            
            if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) continue
            
            state.morningRoutineRunDate["${i}"] = today
            def target = settings["isAvrOnly_${i}"] ? settings["morningHdmi_${i}"] : settings["morningChannel_${i}"]
            def duration = settings["morningDuration_${i}"]
            
            triggerRoutine(i, target, "morning")
            
            if (duration) {
                runIn((duration * 60) + 15, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
            }
        }
    }
}

def endMorningRoutine(data) {
    def i = data.tvNum as Integer
    addToHistory("${getTvName(i)}: Morning routine duration met.")
    endRoutine(i, "morning")
}

def weatherSwitchHandler(evt) {
    if (isSystemPaused() || !settings["enableWeatherAlert"]) return
    def isOn = evt.value == "on"
    if (isOn) {
        state.weatherAlertActive = true
        state.tvWasOffBeforeWeather = [:]
        if (settings["bmsPriorityLock"]) settings["bmsPriorityLock"].on()
        
        for (int i = 1; i <= (numTVs as Integer); i++) {
            def tv = getPrimaryDevice(i)
            def target = settings["isAvrOnly_${i}"] ? settings["weatherHdmi_${i}"] : settings["weatherChannel"]
            def appSwitch = settings["weatherAppSwitch"]
            
            if (tv) {
                if (!isTvActuallyOn(tv, i)) {
                    state.tvWasOffBeforeWeather["${i}"] = true
                    triggerRoutine(i, target, "weather")
                } else {
                    state.tvWasOffBeforeWeather["${i}"] = false
                    if (target || (!settings["isAvrOnly_${i}"] && appSwitch)) {
                        runIn(4, "executeMediaAction", [data: [tvNum: i, channel: target, source: "weather"], overwrite: false])
                    }
                }
             }
        }
        def timeout = settings["weatherTimeout"] ?: 0
        if (timeout > 0) runIn(timeout * 60, "endWeatherAlert", [overwrite: true])
    } else endWeatherAlert()
}

def endWeatherAlert() {
    if (!state.weatherAlertActive) return
    state.weatherAlertActive = false
    unschedule("endWeatherAlert")
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (tv && state.tvWasOffBeforeWeather["${i}"]) {
            endRoutine(i, "weather")
        }
    }
    state.tvWasOffBeforeWeather = [:]
    
    def anyActive = false
    for (int j = 1; j <= (numTVs as Integer); j++) { if (state.activeMacro["${j}"]) anyActive = true }
    if (!anyActive && settings["bmsPriorityLock"]) settings["bmsPriorityLock"].off()
}

def executeSetChannel(data) {
    def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv) {
        def currentInput = tv.currentValue("mediaInputSource")
        if (currentInput != "Antenna TV" && currentInput != "InputTuner" && currentInput != "Tuner" && currentInput != "TV") {
            if (tv.hasCommand("input_Tuner")) tv.input_Tuner()
            else if (tv.hasCommand("keyPress")) tv.keyPress("InputTuner")
            else if (tv.hasCommand("setInputSource")) tv.setInputSource("TV")
        }
        runIn(6, "finalizeSetChannel", [data: [tvNum: i, channel: data.channel], overwrite: false])
    }
}

def finalizeSetChannel(data) {
     def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv) {
        def cleanChannel = data.channel.toString().trim()
        if (tv.hasCommand("tuneChannel")) {
            tv.tuneChannel(cleanChannel)
        } else if (tv.hasCommand("setChannel")) {
            try {
                tv.setChannel(cleanChannel as Number)
            } catch(e) {
                log.error "Could not set channel: ${e}"
            }
        }
    }
}

def contactHandler(evt) {
    if (isSystemPaused() || !settings["enableSafetyMute"]) return
    if (evt.value == "open") muteActiveTVs()
    else if (evt.value == "closed") unmuteActiveTVs()
}

def buttonHandler(evt) {
    if (isSystemPaused() || !settings["enableSafetyMute"]) return
    muteActiveTVs()
    def muteTime = settings["doorbellMuteTime"] ?: 60
    runIn(muteTime as Integer, "unmuteActiveTVs", [overwrite: true])
}

def muteActiveTVs() {
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (isTvActuallyOn(tv, i)) {
            def audioDevice = getAudioDevice(i)
            if (audioDevice.hasCommand("mute")) audioDevice.mute()
        }
    }
}

def unmuteActiveTVs() {
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (isTvActuallyOn(tv, i)) {
            def audioDevice = getAudioDevice(i)
            if (audioDevice.hasCommand("unmute")) audioDevice.unmute()
        }
    }
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

def getTvName(tNum) {
    return settings["tvName_${tNum}"] ?: "TV ${tNum}"
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        log.info "Manual dashboard refresh triggered."
    } else if (btn == "testStormBtn") {
        log.info "Test Storm Alert triggered via button"
        weatherSwitchHandler([value: "on"])
    } else if (btn == "testStormOffBtn") {
        log.info "Test Storm Alert OFF triggered via button"
        weatherSwitchHandler([value: "off"])
    } else if (btn?.startsWith("testMorningBtn_")) {
        def tNum = btn.split("_")[1] as Integer
        log.info "Test Morning Routine triggered for TV ${tNum}"
        testMorningRoutine(tNum)
    } else if (btn?.startsWith("testMorningOffBtn_")) {
        def tNum = btn.split("_")[1] as Integer
        log.info "Test Morning Routine OFF triggered for TV ${tNum}"
        stopMorningRoutineTest(tNum)
    } else if (btn?.startsWith("testShowBtn_")) {
        def parts = btn.split("_")
        def tNum = parts[1] as Integer
        def sNum = parts[2] as Integer
        log.info "Test Show ${sNum} ON triggered for TV ${tNum}"
        startTvShow(tNum, sNum)
    } else if (btn?.startsWith("evaluateRoomBtn_")) {
        def tNum = btn.split("_")[1] as Integer
        log.info "Evaluate Room triggered for TV ${tNum}"
        evaluateRoomLights(tNum)
    }
}

def testMorningRoutine(i) {
    def target = settings["isAvrOnly_${i}"] ? settings["morningHdmi_${i}"] : settings["morningChannel_${i}"]
    def duration = settings["morningDuration_${i}"]
    
    addToHistory("${getTvName(i)}: Morning routine TEST initiated via button.")
    triggerRoutine(i, target, "morning")
    
    if (duration) {
        runIn((duration * 60) + 15, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
    }
}

def stopMorningRoutineTest(i) {
    addToHistory("${getTvName(i)}: Morning routine TEST stopped via button.")
    endRoutine(i, "morning")
}
