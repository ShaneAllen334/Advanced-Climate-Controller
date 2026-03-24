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
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "tvPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            if (numTVs > 0) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Television</th><th style='padding: 8px;'>Power & App</th><th style='padding: 8px;'>Watch Time Today</th><th style='padding: 8px;'>Top App Today</th><th style='padding: 8px;'>Cost Today</th></tr>"
                
                for (int i = 1; i <= (numTVs as Integer); i++) {
                    def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                    def tv = settings["tv_${i}"]
                    
                    if (!tv) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${tvName}</b></td><td style='padding: 8px; color: #888;'>Not Configured</td><td style='padding: 8px;'>-</td><td style='padding: 8px;'>-</td><td style='padding: 8px;'>-</td></tr>"
                        continue
                    }
                    
                    def isTrulyOn = isTvActuallyOn(tv)
                    def powerState = isTrulyOn ? "ON" : "STANDBY / OFF"
                    def pwrColor = isTrulyOn ? "green" : "red"
                    
                    def currentApp = tv.currentValue("application") ?: "Unknown"
                    if (!isTrulyOn) currentApp = "Screen Off"
                    
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
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${tvName}</b></td><td style='padding: 8px;'><span style='color: ${pwrColor}; font-weight:bold;'>${powerState}</span><br><span style='font-size:11px; color:#555;'>${currentApp}</span></td><td style='padding: 8px;'>${watchDisplay}</td><td style='padding: 8px;'>${topApp}</td><td style='padding: 8px;'>${cost}</td></tr>"
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
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. Logs will appear as the system takes action.</i>"
            }
        }
        
        section("Global Settings & Modes") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "Select a virtual switch to act as a global pause. If the switch is OFF, the entire TV application will halt automation."
            input "numTVs", "number", title: "Number of Televisions to Configure (1-10)", required: true, defaultValue: 1, range: "1..10", submitOnChange: true, description: "Enter the number of TVs you want to manage. Save to refresh the page and reveal configuration sections for each."
            input "elecRate", "decimal", title: "Electricity Rate (per kWh)", defaultValue: 0.14, required: true, description: "Your local utility rate (e.g., 0.14 for 14 cents per kWh). Used to calculate daily entertainment costs on the dashboard."
        }

        section("Safety & Security Interruption (Auto-Mute)") {
            input "enableSafetyMute", "bool", title: "Enable Security/Doorbell Auto-Mute", defaultValue: false, submitOnChange: true
            if (enableSafetyMute) {
                input "muteContacts", "capability.contactSensor", title: "Safety Contacts", multiple: true, required: false, description: "If any of these doors/windows open while a TV is on, the TV will instantly mute. It unmutes when closed."
                input "doorbellButtons", "capability.pushableButton", title: "Doorbell Buttons", multiple: true, required: false, description: "If these doorbells are pressed, active TVs will mute for the configured duration."
                
                input "doorbellMuteTime", "number", title: "Doorbell Mute Duration (Seconds)", defaultValue: 60, required: true, description: "How long to keep the TVs muted after a doorbell is pressed."
            }
        }
        
        section("Severe Weather & Emergency Override") {
            input "enableWeatherAlert", "bool", title: "Enable Severe Weather Overrides", defaultValue: false, submitOnChange: true
            if (enableWeatherAlert) {
                input "weatherSwitch", "capability.switch", title: "Virtual Storm / Weather Alert Switch", required: false, description: "When this switch turns ON (e.g., triggered by a NOAA alert app), it forces all configured TVs on."
                input "weatherChannel", "text", title: "Emergency Broadcast Channel (OTA)", required: false, description: "The channel to force the TV to (e.g., 8.1 or 12) when a weather alert occurs."
                input "weatherTimeout", "number", title: "Auto-Restore Timeout (Minutes)", defaultValue: 0, description: "How long until the TV automatically shuts back off. If 0, it waits indefinitely until the weather switch turns off."
                
                input "testStormBtn", "button", title: "Test Storm TV Alert (ON)"
                input "testStormOffBtn", "button", title: "Test Storm TV Alert (OFF)"
            }
        }
        
        if (numTVs > 0 && numTVs <= 10) {
            for (int i = 1; i <= (numTVs as Integer); i++) {
                def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                section("${tvName}") {
                    href(name: "tvHref${i}", page: "tvPage", params: [tvNum: i], title: "Configure ${tvName}")
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
        section("Identification") {
            input "tvName_${tNum}", "text", title: "Custom TV Name", required: false, defaultValue: "TV ${tNum}", submitOnChange: true, description: "A friendly name for the dashboard and logs."
        }
        
        section("Control Devices") {
            input "tv_${tNum}", "capability.switch", title: "Television Device", required: true, description: "The primary Smart TV device (e.g., Roku)."
            input "tvAudio_${tNum}", "capability.audioVolume", title: "Dedicated Audio/Soundbar (Optional)", required: false, description: "Select this ONLY if your TV uses an external, smart-controlled soundbar (like Sonos or Denon) for volume instead of native speakers."
        }
        
        section("TV Show Favorites (Auto-Tune & Turn Off)") {
            paragraph "Schedule up to 2 shows to automatically power on the TV, tune to the channel, and turn the TV off when the show ends."
            for (int s = 1; s <= 2; s++) {
                input "enableShow_${tNum}_${s}", "bool", title: "Enable TV Show ${s}", defaultValue: false, submitOnChange: true
                if (settings["enableShow_${tNum}_${s}"]) {
                    input "showName_${tNum}_${s}", "text", title: "Show Name (For Logging)", required: false
                    input "showChannel_${tNum}_${s}", "text", title: "Channel (e.g., 8.1)", required: true
                    input "showTimeStart_${tNum}_${s}", "time", title: "Show Start Time", required: true
                    input "showTimeEnd_${tNum}_${s}", "time", title: "Show End Time", required: true
                    input "showModes_${tNum}_${s}", "mode", title: "Only Run in These Modes", multiple: true, required: false
                    input "showDays_${tNum}_${s}", "enum", title: "Days of the Week", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                    
                    input "testShowBtn_${tNum}_${s}", "button", title: "Test Start Show ${s} Now"
                }
            }
        }
        
        section("Morning Dashboard / Routine") {
            input "enableMorningRoutine_${tNum}", "bool", title: "Enable Morning Routine", defaultValue: false, submitOnChange: true, description: "Automatically fires up the TV to a specific news/weather channel when you wake up."
            if (settings["enableMorningRoutine_${tNum}"]) {
                input "morningMotion_${tNum}", "capability.motionSensor", title: "Morning Trigger Motion Sensor", required: false, description: "The first time this trips in the allowed window, the TV powers on."
                input "morningTimeStart_${tNum}", "time", title: "Routine Allowed Start Time", required: false
                input "morningTimeEnd_${tNum}", "time", title: "Routine Allowed End Time", required: false
                input "morningDays_${tNum}", "enum", title: "Allowed Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "morningModes_${tNum}", "mode", title: "Allowed Modes", multiple: true, required: false
                input "morningChannel_${tNum}", "text", title: "Morning News/Weather Channel (OTA)", required: false, description: "Forces the TV to this channel (e.g., 8.1 or 12)."
                input "morningDuration_${tNum}", "number", title: "Routine Duration (Minutes)", required: false, description: "Automatically turns the TV off after this many minutes. Leave blank to stay on indefinitely."
                input "testMorningBtn_${tNum}", "button", title: "Test Morning Routine Now"
                input "testMorningOffBtn_${tNum}", "button", title: "Stop Morning Routine Test"
            }
        }

        section("Volume Normalization & Safety") {
            input "enableVolumeMgmt_${tNum}", "bool", title: "Enable Volume Management", defaultValue: false, submitOnChange: true, description: "Protects against loud wake-ups by adjusting volume on startup or shutdown."
            if (settings["enableVolumeMgmt_${tNum}"]) {
                input "startupVolume_${tNum}", "number", title: "SOUNDBARS ONLY: Target Startup Volume (0-100)", required: false, description: "Requires a dedicated Soundbar. Forces this exact absolute volume number every time the TV turns on."
                input "shutdownVolumeReduction_${tNum}", "number", title: "ROKU SPEAKERS: Shutdown Volume Reduction (Clicks)", required: false, description: "Lowers the volume by this many clicks exactly when the TV turns off, preventing loud wake-ups."
            }
        }
        
        section("Acoustic Management (HVAC & Noise)") {
            input "enableAcousticMgmt_${tNum}", "bool", title: "Enable Acoustic Management", defaultValue: false, submitOnChange: true, description: "Mutes background appliances or boosts TV volume when HVAC runs."
            if (settings["enableAcousticMgmt_${tNum}"]) {
                input "tvNoiseSwitches_${tNum}", "capability.switch", title: "Noisy Appliances (Air Purifiers, Fans)", multiple: true, required: false, description: "These appliances will automatically turn OFF while the TV is ON, and restore when the TV turns off."
                input "mainThermostat_${tNum}", "capability.thermostat", title: "Room Thermostat", required: false, description: "Used to detect when the A/C or Heater is actively blowing."
                input "hvacVolumeBoost_${tNum}", "number", title: "HVAC Volume Boost (Clicks)", defaultValue: 3, required: false, description: "Automatically increases the volume by this many clicks when the selected HVAC runs, and reduces it when idle."
                input "testHvacOnBtn_${tNum}", "button", title: "Test HVAC ON (Boost Volume)"
                input "testHvacOffBtn_${tNum}", "button", title: "Test HVAC OFF (Restore Volume)"
            }
        }
        
        section("Lighting & Environmental Sync (Cinema Mode)") {
            input "enableLightingSync_${tNum}", "bool", title: "Enable Environmental Sync", defaultValue: false, submitOnChange: true, description: "Controls lights and evaluates blinds based on TV power."
            if (settings["enableLightingSync_${tNum}"]) {
                input "tvLights_${tNum}", "capability.switch", title: "Target Lights", multiple: true, required: false, description: "These lights will automatically turn OFF when the TV turns ON."
                input "tvBlinds_${tNum}", "capability.contactSensor", title: "Room Blinds Evaluator (Contact)", required: false, description: "If selected, the app will ONLY restore the lights upon TV shutdown if these blinds are closed."
                input "lightRestoreTimeStart_${tNum}", "time", title: "Light Restore Start Time", required: false, description: "Earliest time of day lights are allowed to automatically turn back on."
                input "lightRestoreTimeEnd_${tNum}", "time", title: "Light Restore End Time", required: false, description: "Latest time of day lights are allowed to automatically turn back on."
            }
        }
        
        section("Music & Audio Sync (Sonos)") {
            input "enableMusicSync_${tNum}", "bool", title: "Enable Music Sync", defaultValue: false, submitOnChange: true, description: "Automatically pauses background music when you start watching TV."
            if (settings["enableMusicSync_${tNum}"]) {
                input "sonos_${tNum}", "capability.musicPlayer", title: "Room Music Player (Sonos)", required: false, description: "This player will pause when the TV turns on, and auto-resume when the TV turns off."
                input "sonosResumeModes_${tNum}", "mode", title: "Allowed Modes for Auto-Resume", multiple: true, required: false, description: "Only resume music after TV shutdown if the house is in one of these modes."
                input "sonosResumeTimeStart_${tNum}", "time", title: "Auto-Resume Start Time", required: false
                input "sonosResumeTimeEnd_${tNum}", "time", title: "Auto-Resume End Time", required: false
            }
        }
        
        section("Power Management & Motion Timeout") {
            input "enableMotionTimeout_${tNum}", "bool", title: "Enable Inactivity Timeout", defaultValue: false, submitOnChange: true, description: "Automatically shuts off the TV if the room is empty."
            if (settings["enableMotionTimeout_${tNum}"]) {
                input "motionSensor_${tNum}", "capability.motionSensor", title: "Room Motion Sensor", required: false, description: "The primary sensor to determine if anyone is watching."
                input "motionTimeout_${tNum}", "number", title: "Timeout Delay (Minutes)", required: false, description: "Wait this long after motion stops before killing the TV power."
            }
        }
        
        section("Energy & Telemetry") {
            input "tvWattage_${tNum}", "number", title: "Average Wattage of TV + Soundbar", defaultValue: 150, required: true, description: "Find the average active power draw of your screen. Used to calculate financial ROI and usage cost."
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
    state.hvacVolumeBoosted = state.hvacVolumeBoosted ?: [:]
    
    unschedule("trackUsageStep")
    trackUsageStep()
    schedule("0 0/3 * * * ?", "refreshTVs") 
    schedule("0 0 0 * * ?", "midnightReset")
    
    schedule("0 * * * * ?", "checkTvShows")
    
    if (settings["enableSafetyMute"]) {
        if (muteContacts) subscribe(muteContacts, "contact", contactHandler)
        if (doorbellButtons) subscribe(doorbellButtons, "pushed", buttonHandler)
    }
    
    if (settings["enableWeatherAlert"] && weatherSwitch) {
        subscribe(weatherSwitch, "switch", weatherSwitchHandler)
    }
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = settings["tv_${i}"]
        if (tv) {
            subscribe(tv, "switch", tvPowerEvaluator)
            subscribe(tv, "power", tvPowerEvaluator)
            subscribe(tv, "application", tvPowerEvaluator) 
            subscribe(tv, "mediaInputSource", tvPowerEvaluator)
            subscribe(tv, "application", tvAppHandler) 
        }
        
        if (settings["enableMotionTimeout_${i}"] && settings["motionSensor_${i}"]) {
            subscribe(settings["motionSensor_${i}"], "motion", tvMotionHandler)
        }
        
        if (settings["enableMorningRoutine_${i}"] && settings["morningMotion_${i}"]) {
            subscribe(settings["morningMotion_${i}"], "motion", morningMotionHandler)
        }
        
        if (settings["enableAcousticMgmt_${i}"] && settings["mainThermostat_${i}"]) {
            subscribe(settings["mainThermostat_${i}"], "thermostatOperatingState", hvacStateHandler)
        }
    }
}

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
                    // FIX APPLIED: Used timeToday instead of toDateTime
                    def startFormatted = timeToday(startStr, location.timeZone).format("HH:mm", location.timeZone)
                    if (currentTime == startFormatted) {
                        startTvShow(i, s)
                    }
                }

                def endStr = settings["showTimeEnd_${i}_${s}"]
                if (endStr) {
                    // FIX APPLIED: Used timeToday instead of toDateTime
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
    def tv = settings["tv_${i}"]
    def channel = settings["showChannel_${i}_${s}"]
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"

    if (tv) {
        addToHistory("${getTvName(i)}: Starting scheduled show [${showName}].")
        if (!isTvActuallyOn(tv)) {
            tv.on()
            if (channel) runIn(18, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
        } else {
            if (channel) runIn(4, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
        }
    }
}

def endTvShow(i, s) {
    def tv = settings["tv_${i}"]
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"

    if (tv && isTvActuallyOn(tv)) {
        addToHistory("${getTvName(i)}: Scheduled show [${showName}] ended. Powering OFF.")
        tv.off()
    }
}

def refreshTVs() {
    if (isSystemPaused()) return
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = settings["tv_${i}"]
        if (tv && tv.hasCommand("refresh")) tv.refresh()
    }
}

def isTvActuallyOn(tv) {
    if (!tv) return false
    def pwr = tv.currentValue("power")
    def sw = tv.currentValue("switch")
    def app = tv.currentValue("application") ?: "Home"
    def transport = tv.currentValue("transportStatus")
    
    if (sw == "off" || pwr in ["PowerOff", "Off", "DisplayOff", "Headless"]) return false
    
    def idleApps = ["Roku Dynamic Menu", "Backdrops", "Roku Media Player", "Home", "none", null]
    if (sw == "on" && !idleApps.contains(app)) return true
    if (sw == "on" && pwr == "Ready" && transport == "stopped") return false
    
    return sw == "on"
}

def tvPowerEvaluator(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["tv_${i}"]?.id == deviceId) {
             def tvName = getTvName(i)
            def tv = settings["tv_${i}"]
            def isTrulyOn = isTvActuallyOn(tv)
            def lastEvaluatedState = state.evaluatedPowerState["${i}"] ?: false
            
            if (isTrulyOn && !lastEvaluatedState) {
                state.evaluatedPowerState["${i}"] = true
                addToHistory("${tvName}: Power State changed to ON.")
                
                if (tv.hasCommand("refresh")) tv.refresh()
                
                if (settings["enableVolumeMgmt_${i}"]) {
                    def targetVol = settings["startupVolume_${i}"]
                    def audioDevice = settings["tvAudio_${i}"]
                    if (targetVol != null && audioDevice && audioDevice.hasCommand("setVolume")) {
                        audioDevice.setVolume(targetVol)
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
                }
                
                if (settings["enableLightingSync_${i}"]) {
                    def lights = settings["tvLights_${i}"]
                    if (lights) {
                         def activeLights = lights.findAll { it.currentValue("switch") == "on" }
                        if (activeLights) {
                            addToHistory("${tvName}: Environment sync. Turning OFF lights.")
                            activeLights.each { it.off() } 
                            state.lightsPausedByTv["${i}"] = true
                        } else {
                            state.lightsPausedByTv["${i}"] = false
                        }
                    }
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
                state.hvacVolumeBoosted["${i}"] = false 
                addToHistory("${tvName}: Power State changed to OFF.")
                
                if (settings["enableVolumeMgmt_${i}"]) {
                    def reduceClicks = settings["shutdownVolumeReduction_${i}"]
                    if (reduceClicks && reduceClicks > 0) {
                        def audioDev = settings["tvAudio_${i}"] ?: tv
                        addToHistory("${tvName}: Tapering volume down by ${reduceClicks} clicks for quiet startup.")
                        adjustVolumeRelative(audioDev, reduceClicks, "down")
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
                        
                        // FIX APPLIED: Used timeToday instead of toDateTime
                        if (startTime && endTime) timeOk = timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)
                        
                        if (isBlindClosed && timeOk) {
                            addToHistory("${tvName}: Conditions met. Restoring lights.")
                             lights.each { it.on() } 
                        }
                    }
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
                        
                        // FIX APPLIED: Used timeToday instead of toDateTime
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

def hvacStateHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def opState = evt.value 
    def isRunning = (opState == "cooling" || opState == "heating" || opState == "fan only")
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableAcousticMgmt_${i}"] && settings["mainThermostat_${i}"]?.id == deviceId) {
            def tv = settings["tv_${i}"]
            
            if (isTvActuallyOn(tv)) {
                def audioDevice = settings["tvAudio_${i}"] ?: tv
                def boostAmount = settings["hvacVolumeBoost_${i}"] ?: 3
                 def tvName = getTvName(i)
                
                if (isRunning && !state.hvacVolumeBoosted["${i}"]) {
                    addToHistory("${tvName}: HVAC started (${opState}). Boosting volume by ${boostAmount} ticks.")
                    state.hvacVolumeBoosted["${i}"] = true
                    adjustVolumeRelative(audioDevice, boostAmount, "up")
                } else if (!isRunning && state.hvacVolumeBoosted["${i}"]) {
                    addToHistory("${tvName}: HVAC stopped. Reducing volume by ${boostAmount} ticks.")
                    state.hvacVolumeBoosted["${i}"] = false
                    adjustVolumeRelative(audioDevice, boostAmount, "down")
                }
            } else {
                state.hvacVolumeBoosted["${i}"] = false
             }
        }
    }
}

def adjustVolumeRelative(audioDevice, amount, direction) {
    if (!audioDevice) return
    for (int j = 0; j < amount; j++) {
        if (direction == "up") {
            if (audioDevice.hasCommand("volumeUp")) audioDevice.volumeUp()
        } else {
            if (audioDevice.hasCommand("volumeDown")) audioDevice.volumeDown()
        }
        pauseExecution(300) 
    }
}

def trackUsageStep() {
    if (isSystemPaused()) return
    def rate = settings["elecRate"] ?: 0.14
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = settings["tv_${i}"]
        if (isTvActuallyOn(tv)) {
            def wattage = settings["tvWattage_${i}"] ?: 150
            def costPerMin = (wattage / 1000.0) * rate / 60.0
            state.watchTimeToday["${i}"] = (state.watchTimeToday["${i}"] ?: 0) + 5
            state.costToday["${i}"] = (state.costToday["${i}"] ?: 0.0) + (costPerMin * 5)
            def currentApp = tv.currentValue("application") ?: "Unknown/Home"
            if (!state.appStats["${i}"]) state.appStats["${i}"] = [:]
            state.appStats["${i}"][currentApp] = (state.appStats["${i}"][currentApp] ?: 0) + 5
        }
    }
    runIn(300, "trackUsageStep") 
}

def midnightReset() {
    state.watchTimeToday = [:]
    state.costToday = [:]
    state.appStats = [:]
}

def tvAppHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def appName = evt.value
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["tv_${i}"]?.id == deviceId && isTvActuallyOn(settings["tv_${i}"])) {
            addToHistory("${getTvName(i)}: Application changed to [${appName}].")
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
    
    def tv = settings["tv_${i}"]
    def timeout = settings["motionTimeout_${i}"]
    if (!tv || !timeout || !isTvActuallyOn(tv)) return
    def lastMotion = state.lastMotionTime["${i}"] ?: 0
    def now = new Date().time
    if ((now - lastMotion) >= (timeout * 60000) - 2000) {
        addToHistory("${getTvName(i)}: No motion detected. Powering OFF.")
        tv.off()
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
            
            // FIX APPLIED: Used timeToday instead of toDateTime
            if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) continue
            
            state.morningRoutineRunDate["${i}"] = today
            def tv = settings["tv_${i}"]
             def channel = settings["morningChannel_${i}"]
             def duration = settings["morningDuration_${i}"]
             
            if (!isTvActuallyOn(tv)) {
                tv.on()
                if (channel) runIn(18, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
            } else {
                if (channel) runIn(4, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
            }
            if (duration) runIn((duration * 60) + 18, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
        }
    }
}

def endMorningRoutine(data) {
    def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv && isTvActuallyOn(tv)) {
        addToHistory("${getTvName(i)}: Morning routine duration met. Powering OFF.")
        tv.off()
    }
}

def weatherSwitchHandler(evt) {
    if (isSystemPaused() || !settings["enableWeatherAlert"]) return
    def isOn = evt.value == "on"
    if (isOn) {
        state.weatherAlertActive = true
        state.tvWasOffBeforeWeather = [:]
        def channel = settings["weatherChannel"]
        for (int i = 1; i <= (numTVs as Integer); i++) {
            def tv = settings["tv_${i}"]
            if (tv) {
                 if (!isTvActuallyOn(tv)) {
                    state.tvWasOffBeforeWeather["${i}"] = true
                    tv.on()
                    if (channel) runIn(18, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
                } else {
                    if (channel) runIn(4, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
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
        def tv = settings["tv_${i}"]
        if (tv && state.tvWasOffBeforeWeather["${i}"]) tv.off()
    }
    state.tvWasOffBeforeWeather = [:]
}

def executeSetChannel(data) {
    def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv) {
        def currentInput = tv.currentValue("mediaInputSource")
        if (currentInput != "Antenna TV" && currentInput != "InputTuner" && currentInput != "Tuner") {
            if (tv.hasCommand("input_Tuner")) tv.input_Tuner()
            else if (tv.hasCommand("keyPress")) tv.keyPress("InputTuner")
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
        def tv = settings["tv_${i}"]
        if (isTvActuallyOn(tv)) {
            def audioDevice = settings["tvAudio_${i}"] ?: tv
            if (audioDevice.hasCommand("mute")) audioDevice.mute()
        }
    }
}

def unmuteActiveTVs() {
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = settings["tv_${i}"]
        if (isTvActuallyOn(tv)) {
            def audioDevice = settings["tvAudio_${i}"] ?: tv
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
    if (btn == "testStormBtn") {
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
    } else if (btn?.startsWith("testHvacOnBtn_")) {
        def tNum = btn.split("_")[1] as Integer
        log.info "Test HVAC ON triggered for TV ${tNum}"
        testHvacBoost(tNum, true)
    } else if (btn?.startsWith("testHvacOffBtn_")) {
        def tNum = btn.split("_")[1] as Integer
        log.info "Test HVAC OFF triggered for TV ${tNum}"
        testHvacBoost(tNum, false)
    } else if (btn?.startsWith("testShowBtn_")) {
        def parts = btn.split("_")
        def tNum = parts[1] as Integer
        def sNum = parts[2] as Integer
        log.info "Test Show ${sNum} ON triggered for TV ${tNum}"
        startTvShow(tNum, sNum)
    }
}

def testMorningRoutine(i) {
    def tv = settings["tv_${i}"]
    def channel = settings["morningChannel_${i}"]
    def duration = settings["morningDuration_${i}"]
    
    if (tv) {
        addToHistory("${getTvName(i)}: Morning routine TEST initiated via button.")
        if (!isTvActuallyOn(tv)) {
            tv.on()
            if (channel) runIn(18, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
        } else {
            if (channel) runIn(4, "executeSetChannel", [data: [tvNum: i, channel: channel], overwrite: false])
        }
        if (duration) {
            runIn((duration * 60) + 18, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
        }
    }
}

def stopMorningRoutineTest(i) {
    def tv = settings["tv_${i}"]
    if (tv) {
        addToHistory("${getTvName(i)}: Morning routine TEST stopped via button. Powering OFF.")
        tv.off()
    }
}

def testHvacBoost(i, isRunning) {
    def tv = settings["tv_${i}"]
    if (isTvActuallyOn(tv)) {
        def audioDevice = settings["tvAudio_${i}"] ?: tv
        def boostAmount = settings["hvacVolumeBoost_${i}"] ?: 3
        def tvName = getTvName(i)
        
        if (isRunning && !state.hvacVolumeBoosted["${i}"]) {
            addToHistory("${tvName}: TEST HVAC started. Boosting volume by ${boostAmount} ticks.")
            state.hvacVolumeBoosted["${i}"] = true
            adjustVolumeRelative(audioDevice, boostAmount, "up")
        } else if (!isRunning && state.hvacVolumeBoosted["${i}"]) {
            addToHistory("${tvName}: TEST HVAC stopped. Reducing volume by ${boostAmount} ticks.")
            state.hvacVolumeBoosted["${i}"] = false
            adjustVolumeRelative(audioDevice, boostAmount, "down")
        } else {
            addToHistory("${tvName}: TEST HVAC ignored (already in requested state).")
        }
    } else {
        addToHistory("${getTvName(i)}: TEST HVAC ignored (TV is not ON).")
    }
}
