/**
 * Advanced Sonos Controls
 */

definition(
    name: "Advanced Sonos Controls",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Ultimate commercial-grade Sonos manager. Features Live Diagnostics, Smart Power Automation, ROI Tracking, Intercom TTS, Auto-Resume, and Virtual Switch Favorites.",
    category: "Audio",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Sonos Controls</b>", install: true, uninstall: true) {
        
        // ========================================================
        // REPORTING & CONTROL DASHBOARDS
        // ========================================================
        
        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, top-down view of your entire Sonos network. Extracts active track data, album art, smart power states, and volume levels.</div>"
            
            def hasZones = false
            def activeZoneOptions = [:]
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; vertical-align: middle; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 20%; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Zone Name</th><th>Main Power</th><th>Status</th><th>Volume</th><th>Now Playing</th></tr></thead>
                <tbody>
            """

            for (int i = 1; i <= 6; i++) {
                if (settings["enableZ${i}"] && settings["z${i}Speaker"]) {
                    hasZones = true
                    def spk = settings["z${i}Speaker"]
                    def sw = settings["z${i}Switch"]
                    
                    activeZoneOptions["${i}"] = spk.label ?: "Zone ${i}"
                    
                    // Power & Play Status
                    def pwrStatus = sw ? (sw.currentValue("switch") == "on" ? "<span style='color:green;'>ON</span>" : "<span style='color:red;'>OFF</span>") : "N/A"
                    def playStatus = spk.currentValue("status")?.toUpperCase() ?: "UNKNOWN"
                    def isMuted = spk.currentValue("mute") == "muted"
                    
                    def statusColor = playStatus == "PLAYING" ? "blue" : (playStatus == "PAUSED" ? "orange" : "gray")
                    def muteIndicator = isMuted ? " <span style='color:red;' title='Muted'>🔇</span>" : ""
                    def vol = spk.currentValue("volume") ?: "--"
                    
                    // Album Art & Track Parsing
                    def trackTitle = spk.currentValue("trackDescription") ?: "Idle / Unknown"
                    def trackData = spk.currentValue("trackData")
                    def artUrl = ""
                    if (trackData) {
                        try {
                            def json = new groovy.json.JsonSlurper().parseText(trackData)
                            artUrl = json.albumArtUrl ?: (json.audioItem?.images?.first()?.url ?: "")
                        } catch (e) {}
                    }
                    
                    def artHtml = artUrl ? "<img src='${artUrl}' style='width:45px; height:45px; border-radius:5px; float:left; margin-right:10px; border:1px solid #ddd;'>" : "<div style='width:45px; height:45px; background:#f4f4f4; border-radius:5px; float:left; margin-right:10px; display:flex; align-items:center; justify-content:center; color:#ccc; border:1px solid #ddd;'>🎵</div>"
                    
                    dashHTML += "<tr><td class='dash-hl'>${spk.label}</td><td><b>${pwrStatus}</b></td><td style='color:${statusColor};'><b>${playStatus}</b>${muteIndicator}</td><td>${vol}%</td><td style='text-align:left;'>${artHtml}<div style='font-weight:bold; font-size:13px; padding-top:4px;'>${trackTitle}</div></td></tr>"
                }
            }
            dashHTML += "</tbody></table>"
            
            if (hasZones) {
                paragraph dashHTML
                input "refreshDash", "button", title: "Refresh Dashboard Data"
            } else {
                paragraph "<i>Please configure your Sonos zones below to populate the dashboard.</i>"
            }
        }

        if (hasZones) {
            section("<b>Active Control Panel</b>") {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Select a zone to remotely control it directly from this app, broadcast TTS messages, or capture its current track to a Virtual Switch.</div>"
                input "activeZoneControl", "enum", title: "Select Zone to Control", options: activeZoneOptions, submitOnChange: true
                
                if (activeZoneControl) {
                    paragraph "<b>Basic Transport</b>"
                    input "btnPlay", "button", title: "▶️ Play", width: 2
                    input "btnPause", "button", title: "⏸ Pause", width: 2
                    input "btnPrev", "button", title: "⏮ Prev", width: 2
                    input "btnNext", "button", title: "⏭ Next", width: 2
                    input "btnVolDown", "button", title: "🔉 Vol -5%", width: 2
                    input "btnVolUp", "button", title: "🔊 Vol +5%", width: 2
                    
                    paragraph "<b>Advanced Controls</b>"
                    input "btnShuffle", "button", title: "🔀 Toggle Shuffle", width: 3
                    input "btnRepeat", "button", title: "🔁 Toggle Repeat", width: 3
                    input "btnNightMode", "button", title: "🌙 Toggle Night Mode", width: 3
                    input "btnSpeechEnhance", "button", title: "🗣️ Toggle Speech Enhance", width: 3
                    
                    paragraph "<b>Intercom Broadcast (TTS)</b>"
                    input "ttsMessage", "text", title: "Message to Broadcast", required: false, width: 6
                    input "ttsVolume", "number", title: "TTS Volume (%)", required: false, defaultValue: 40, width: 3
                    input "btnTTS", "button", title: "📢 Send TTS", width: 3
                    
                    paragraph "<b>Sleep Timer</b>"
                    input "sleepTimerMins", "number", title: "Minutes until Pause", required: false, width: 9
                    input "btnSleep", "button", title: "⏳ Start Timer", width: 3
                    if (state.sleepTimers && state.sleepTimers[activeZoneControl]) {
                        paragraph "<span style='color:orange;'><i>Sleep Timer currently active for this zone.</i></span>"
                        input "btnCancelSleep", "button", title: "Cancel Timer"
                    }

                    paragraph "<hr>"
                    paragraph "<div style='font-size:13px; color:#555;'>This saves the current Track URI <b>and current volume</b> to a switch.</div>"
                    input "btnSaveFav", "button", title: "⭐ Save Current Track as Virtual Switch"
                }
            }
        }

        section("<b>Energy Cost & ROI Savings Tracking</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks the exact runtime and idle time of your smart plugs to estimate utility costs. It automatically calculates the 'Avoided Idle Cost' for the hours your speakers were physically powered down.</div>"
            input "enableCostTracker", "bool", title: "<b>Enable Energy Tracking</b>", defaultValue: true, submitOnChange: true
            if (enableCostTracker) {
                input "costPerKwh", "decimal", title: "Utility Rate (USD per kWh)", required: false, defaultValue: 0.15
                
                if (state.runHistory) {
                    paragraph "<b>7-Day Energy Cost & Savings Estimate</b>"
                    paragraph renderCostDashboard()
                }
                
                input "resetHistory", "button", title: "Clear Tracking History"
            }
        }

        section("<b>Recent Action History</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a rolling log of commands, mode swaps, and virtual switches created by this engine.</div>"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        // ========================================================
        // SYSTEM CONFIGURATION
        // ========================================================

        section("<b>1. Zone Setup & Power Profiles</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Pair your Sonos speakers with their smart plugs and identify the hardware model so the engine can accurately calculate wattage.</div>"
            
            for (int i = 1; i <= 6; i++) {
                input "enableZ${i}", "bool", title: "Enable Zone ${i}", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    input "z${i}Speaker", "capability.musicPlayer", title: "Select Sonos Speaker", required: true
                    input "z${i}Switch", "capability.switch", title: "Select Smart Power Plug", required: false
                    input "z${i}Type", "enum", title: "Speaker Hardware Type", options: [
                        "Sonos Era 100", "Sonos Era 300", 
                        "Sonos One / One SL / Play:1", "Sonos Play:3", "Sonos Five / Play:5", 
                        "Sonos Beam (Gen 1/2)", "Sonos Arc / Playbar / Playbase", "Sonos Ray", 
                        "Sonos Sub / Sub Mini", "Sonos Amp / Connect:Amp", "Sonos Port / Connect", 
                        "Sonos Move / Roam (Docked)", "IKEA SYMFONISK"
                    ], required: true, defaultValue: "Sonos One / One SL / Play:1"
                    
                    input "z${i}StartVol", "number", title: "Default Startup Volume (%) - Leave blank to ignore", required: false, range: "1..100"
                    input "z${i}AutoResume", "bool", title: "Auto-Resume Playback on Boot", defaultValue: false
                    paragraph "<hr>"
                }
            }
        }

        section("<b>2. Automated Power Management</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Eliminates phantom power drain. Select which modes boot up your speakers. <b>Failsafe:</b> The engine automatically intercepts 'Turn Off' events and issues a PAUSE command to the speakers first to prevent jarring audio drops.</div>"
            
            input "turnOnModes", "mode", title: "Modes to Power ON Speakers (e.g., Home, Morning)", multiple: true, required: false
            input "turnOffModes", "mode", title: "Modes to Power OFF Speakers (e.g., Away, Night)", multiple: true, required: false
        }

        section("<b>3. Virtual Switch Housekeeping</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically purges old Favorite Virtual Switches to keep your Hubitat database clean and lightweight.</div>"
            input "enableAutoPurge", "bool", title: "<b>Enable Auto-Purge</b>", defaultValue: true, submitOnChange: true
            if (enableAutoPurge) {
                input "purgeDays", "number", title: "Delete favorites older than (Days)", defaultValue: 30
            }
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
    if (!state.runHistory) state.runHistory = [:]
    if (!state.savedFavorites) state.savedFavorites = [:]
    if (!state.sleepTimers) state.sleepTimers = [:]
    
    subscribe(location, "mode", modeChangeHandler)
    
    getChildDevices().each { child -> subscribe(child, "switch", childSwitchHandler) }

    if (enableCostTracker) {
        runEvery15Minutes(calculateEnergy)
    }

    // Schedule the Housekeeping purge at 3:00 AM every day
    schedule("0 0 3 ? * *", purgeOldFavorites)

    logAction("App Initialized. Advanced Sonos Engine Ready.")
}

// --- BUTTON & DASHBOARD HANDLERS ---

def appButtonHandler(btn) {
    if (btn == "refreshDash") return
    if (btn == "resetHistory") {
        state.runHistory = [:]
        logAction("Energy Cost Tracking history cleared.")
        return
    }

    if (activeZoneControl && btn.startsWith("btn")) {
        def zNum = activeZoneControl
        def spk = settings["z${zNum}Speaker"]
        if (!spk) return

        // Basic Transport
        if (btn == "btnPlay") { spk.play(); logAction("Command -> Sent PLAY to ${spk.label}") }
        if (btn == "btnPause") { spk.pause(); logAction("Command -> Sent PAUSE to ${spk.label}") }
        if (btn == "btnNext") { spk.nextTrack(); logAction("Command -> Sent NEXT TRACK to ${spk.label}") }
        if (btn == "btnPrev") { spk.previousTrack(); logAction("Command -> Sent PREV TRACK to ${spk.label}") }
        if (btn == "btnVolUp") { def v = (spk.currentValue("volume") ?: 0) + 5; spk.setLevel(v > 100 ? 100 : v); logAction("Command -> Vol UP on ${spk.label}") }
        if (btn == "btnVolDown") { def v = (spk.currentValue("volume") ?: 0) - 5; spk.setLevel(v < 0 ? 0 : v); logAction("Command -> Vol DOWN on ${spk.label}") }
        
        // Advanced Transport & Home Theater
        if (btn == "btnShuffle") { 
            if (spk.hasCommand("setShuffle")) { spk.setShuffle(true); logAction("Command -> Shuffle toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support setShuffle.")
        }
        if (btn == "btnRepeat") { 
            if (spk.hasCommand("setRepeat")) { spk.setRepeat(true); logAction("Command -> Repeat toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support setRepeat.")
        }
        if (btn == "btnNightMode") { 
            if (spk.hasCommand("setNightMode")) { spk.setNightMode(true); logAction("Command -> Night Mode toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support native setNightMode.")
        }
        if (btn == "btnSpeechEnhance") { 
            if (spk.hasCommand("setSpeechEnhancement")) { spk.setSpeechEnhancement(true); logAction("Command -> Speech Enhance toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support native setSpeechEnhancement.")
        }

        // TTS & Sleep Timer
        if (btn == "btnTTS" && ttsMessage) {
            def curVol = spk.currentValue("volume") ?: 20
            def targetVol = ttsVolume ?: 40
            spk.setLevel(targetVol)
            spk.speak(ttsMessage)
            logAction("Command -> Broadcasted TTS to ${spk.label}: '${ttsMessage}' at ${targetVol}%. Auto-restoring volume in 10s.")
            
            // Wait 10 seconds for the TTS to finish, then restore the original volume
            runIn(10, restoreVolume, [data: [spkId: spk.id, vol: curVol]])
        }
        if (btn == "btnSleep" && sleepTimerMins) {
            state.sleepTimers[zNum] = true
            runIn((sleepTimerMins * 60).toInteger(), executeSleepTimer, [data: [spkId: spk.id, zNum: zNum]])
            logAction("Command -> Sleep Timer started for ${spk.label} (${sleepTimerMins} mins).")
        }
        if (btn == "btnCancelSleep") {
            state.sleepTimers[zNum] = false
            logAction("Command -> Sleep Timer cancelled for ${spk.label}.")
        }

        // Favorites
        if (btn == "btnSaveFav") createFavoriteVirtualSwitch(spk)
    }
}

def restoreVolume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.setLevel(data.vol)
        logAction("TTS Complete -> Restored ${spk.label} volume to ${data.vol}%.")
    }
}

def executeSleepTimer(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk && state.sleepTimers[data.zNum]) {
        spk.pause()
        logAction("Sleep Timer Executed: Paused ${spk.label}.")
    }
    state.sleepTimers[data.zNum] = false
}

// --- FAVORITES & VIRTUAL SWITCH GENERATOR ---

def createFavoriteVirtualSwitch(speaker) {
    def trackUri = speaker.currentValue("trackUri")
    def trackName = speaker.currentValue("trackDescription") ?: "Unknown Track"
    def curVol = speaker.currentValue("volume") ?: 20
    
    if (!trackUri) {
        logAction("ERROR: Cannot create favorite. No track URI detected on ${speaker.label}.")
        return
    }

    def safeName = trackName.replaceAll("[^a-zA-Z0-9 ]", "").trim()
    if (safeName.length() > 30) safeName = safeName.substring(0, 30)

    def dni = "SONOS_FAV_${app.id}_${now()}"
    def label = "Sonos Fav - ${safeName}"
    
    try {
        def child = addChildDevice("hubitat", "Virtual Switch", dni, [label: label, name: label, isComponent: false])
        if (!state.savedFavorites) state.savedFavorites = [:]
        
        // Save the volume and timestamp for the auto-purge routine
        state.savedFavorites[dni] = [uri: trackUri, speakerId: speaker.id, name: trackName, vol: curVol, timestamp: now()]
        subscribe(child, "switch", childSwitchHandler)
        logAction("SUCCESS: Created Virtual Switch [${label}] to recall current track at ${curVol}%.")
    } catch (e) { logAction("ERROR: Failed to create virtual switch. Check hub logs.") }
}

def childSwitchHandler(evt) {
    if (evt.value == "on") {
        def dni = evt.device.deviceNetworkId
        def favData = state.savedFavorites[dni]
        
        if (favData) {
            def speaker = getSpeakerById(favData.speakerId)
            if (speaker) {
                logAction("Triggered Favorite via Switch. Setting volume to ${favData.vol}% and playing [${favData.name}] on ${speaker.label}")
                if (favData.vol != null) {
                    speaker.setLevel(favData.vol)
                }
                speaker.setTrack(favData.uri)
                runIn(2, triggerPlayOnFav, [data: [spkId: speaker.id]])
            }
        }
        runIn(3, turnOffChild, [data: [dni: dni]])
    }
}

def triggerPlayOnFav(data) { getSpeakerById(data.spkId)?.play() }
def turnOffChild(data) { getChildDevice(data.dni)?.off() }
def getSpeakerById(id) { for (int i = 1; i <= 6; i++) { if (settings["z${i}Speaker"]?.id == id) return settings["z${i}Speaker"] }; return null }

// --- HOUSEKEEPING ---

def purgeOldFavorites() {
    if (!enableAutoPurge) return
    
    def threshold = now() - ((purgeDays ?: 30) * 86400000L)
    def children = getChildDevices()
    
    children.each { child ->
        def dni = child.deviceNetworkId
        def favData = state.savedFavorites[dni]
        
        if (favData && favData.timestamp && favData.timestamp < threshold) {
            deleteChildDevice(dni)
            state.savedFavorites.remove(dni)
            logAction("Housekeeping: Auto-Purged old favorite switch [${child.label}]")
        }
    }
}

// --- POWER MANAGEMENT LOGIC ---

def modeChangeHandler(evt) {
    def currentMode = evt.value
    def isTurnOn = turnOnModes ? (turnOnModes as List).contains(currentMode) : false
    def isTurnOff = turnOffModes ? (turnOffModes as List).contains(currentMode) : false

    if (isTurnOn) {
        logAction("Mode changed to ${currentMode}. Initiating Sonos Startup sequence.")
        powerUpSpeakers()
    } else if (isTurnOff) {
        logAction("Mode changed to ${currentMode}. Initiating Failsafe Pause & Shutdown sequence.")
        gracefulShutdown()
    }
}

def powerUpSpeakers() {
    for (int i = 1; i <= 6; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Switch"]) {
            settings["z${i}Switch"].on()
            
            // Apply Startup Volume Normalization if configured
            if (settings["z${i}StartVol"] && settings["z${i}Speaker"]) {
                def targetVol = settings["z${i}StartVol"]
                runIn(5, setStartupVolume, [data: [spkId: settings["z${i}Speaker"].id, vol: targetVol]])
            }
            
            // Apply Auto-Resume Playback
            if (settings["z${i}AutoResume"] && settings["z${i}Speaker"]) {
                // Wait 60 seconds to ensure the speaker has fully connected to WiFi
                runIn(60, triggerAutoResume, [data: [spkId: settings["z${i}Speaker"].id]])
            }
        }
    }
    logAction("Master power switches turned ON.")
}

def setStartupVolume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.setLevel(data.vol)
        logAction("Startup Volume Normalization: Set ${spk.label} to ${data.vol}%")
    }
}

def triggerAutoResume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.play()
        logAction("Auto-Resume: Resumed playback on ${spk.label} after boot up.")
    }
}

def gracefulShutdown() {
    def commandsSent = false
    for (int i = 1; i <= 6; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Speaker"] && settings["z${i}Switch"]) {
            if (settings["z${i}Switch"].currentValue("switch") == "on" && settings["z${i}Speaker"].currentValue("status") == "playing") {
                settings["z${i}Speaker"].pause()
                commandsSent = true
                logAction("Failsafe: Paused ${settings['z${i}Speaker'].label} prior to power cut.")
            }
        }
    }
    runIn(commandsSent ? 5 : 1, executePowerCut)
}

def executePowerCut() {
    for (int i = 1; i <= 6; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Switch"]) settings["z${i}Switch"].off()
    }
    logAction("Master power switches successfully powered OFF.")
}

// --- ENERGY & ROI TRACKING ---

def getPowerProfiles(type) {
    switch(type) {
        case "Sonos Era 100": return [idle: 2.0, play: 10.0]
        case "Sonos Era 300": return [idle: 2.0, play: 22.5]
        case "Sonos One / One SL / Play:1": return [idle: 3.8, play: 15.0]
        case "Sonos Play:3": return [idle: 4.0, play: 15.0]
        case "Sonos Five / Play:5": return [idle: 2.0, play: 20.0]
        case "Sonos Beam (Gen 1/2)": return [idle: 6.3, play: 15.0]
        case "Sonos Arc / Playbar / Playbase": return [idle: 4.0, play: 20.0]
        case "Sonos Ray": return [idle: 3.0, play: 12.0]
        case "Sonos Sub / Sub Mini": return [idle: 4.0, play: 15.0]
        case "Sonos Amp / Connect:Amp": return [idle: 7.3, play: 30.0]
        case "Sonos Port / Connect": return [idle: 3.0, play: 5.0]
        case "Sonos Move / Roam (Docked)": return [idle: 2.0, play: 8.0]
        case "IKEA SYMFONISK": return [idle: 4.0, play: 15.0]
        default: return [idle: 3.5, play: 15.0]
    }
}

def calculateEnergy() {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    if (!state.runHistory[today]) state.runHistory[today] = [usedWh: 0.0, savedWh: 0.0]

    def fractionOfHour = 0.25 

    for (int i = 1; i <= 6; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Speaker"] && settings["z${i}Type"]) {
            def sw = settings["z${i}Switch"]
            def profile = getPowerProfiles(settings["z${i}Type"])
            
            if (sw ? (sw.currentValue("switch") == "on") : true) {
                if (settings["z${i}Speaker"].currentValue("status") == "playing") state.runHistory[today].usedWh += (profile.play * fractionOfHour)
                else state.runHistory[today].usedWh += (profile.idle * fractionOfHour)
            } else {
                state.runHistory[today].savedWh += (profile.idle * fractionOfHour)
            }
        }
    }
    
    def keys = state.runHistory.keySet().sort().reverse()
    if (keys.size() > 7) state.runHistory = state.runHistory.subMap(keys[0..6])
}

def renderCostDashboard() {
    def html = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Est. Power Used (kWh)</th><th>Avoided Power (kWh)</th><th>Est. Cost</th><th>Est. Savings</th></tr></thead><tbody>"
    def totalCost = 0.0; def totalSavings = 0.0
    
    state.runHistory.keySet().sort().reverse().each { date ->
        def data = state.runHistory[date]
        def usedKwh = (data.usedWh ?: 0.0) / 1000.0; def savedKwh = (data.savedWh ?: 0.0) / 1000.0
        def dayCost = usedKwh * (costPerKwh ?: 0.15); def daySave = savedKwh * (costPerKwh ?: 0.15)
        
        totalCost += dayCost; totalSavings += daySave
        def saveStyle = daySave > 0 ? "color:green; font-weight:bold;" : "color:gray;"
        
        html += "<tr><td>${date}</td><td>${String.format('%.3f', usedKwh)}</td><td>${String.format('%.3f', savedKwh)}</td><td>&#36;${String.format('%.2f', dayCost)}</td><td style='${saveStyle}'>+&#36;${String.format('%.2f', daySave)}</td></tr>"
    }
    html += "<tr><td colspan='3' style='text-align:right;'><b>7-Day Totals:</b></td><td><b>&#36;${String.format('%.2f', totalCost)}</b></td><td style='color:green;'><b>+&#36;${String.format('%.2f', totalSavings)}</b></td></tr></tbody></table>"
    return html
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
