/**
 * Advanced Sonos Controls
 */

definition(
    name: "Advanced Sonos Controls",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Ultimate commercial-grade Sonos BMS. Features Live Diagnostics, Master Kills-Switches, Event Muting, Hardware Protection, and Dynamic Favorites.",
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
        
        // Null-safe module checks (Defaults to true on first install)
        def optControlPanel = settings.enableControlPanel == null ? true : settings.enableControlPanel
        def optPowerManagement = settings.enablePowerManagement == null ? true : settings.enablePowerManagement
        def optCostTracker = settings.enableCostTracker == null ? true : settings.enableCostTracker
        def optFavorites = settings.enableFavorites == null ? true : settings.enableFavorites
        def optAutoPurge = settings.enableAutoPurge == null ? true : settings.enableAutoPurge
        
        def appIsDisabled = (settings.appEnableSwitch && settings.appEnableSwitch.currentValue("switch") == "off")

        // Declare UI state variables globally for the page so all sections can see them
        def hasZones = false
        def activeZoneOptions = [:]

        // ========================================================
        // REPORTING & CONTROL DASHBOARDS
        // ========================================================
 
        if (appIsDisabled) {
            paragraph "<div style='padding: 12px; background-color: #f8d7da; border-left: 5px solid #dc3545; border-radius: 5px; color: #721c24; margin-bottom: 15px;'><b>⚠️ SYSTEM DISABLED:</b> The Master Application Switch is currently OFF. All background automations and overrides are suspended.</div>"
        }

        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, top-down view of your entire Sonos network. Extracts active track data, smart power states, and override locks.</div>"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; vertical-align: middle; }
                .dash-table th { background-color: #343a40; color: white; padding: 10px; text-transform: uppercase; letter-spacing: 1px; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 25%; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Zone Name</th><th>Main Power</th><th>Status</th><th>Volume</th><th>Now Playing</th></tr></thead>
                <tbody>
            """

            for (int i = 1; i <= 10; i++) {
                if (settings["enableZ${i}"] && settings["z${i}Speaker"]) {
                    hasZones = true
                    def spk = settings["z${i}Speaker"]
                    def sw = settings["z${i}Switch"]
          
                    def gnLock = settings["z${i}GoodNightSwitch"]
                    def customName = settings["z${i}Name"]
                    
                    def resolvedName = customName ?: (spk.label ?: "Zone ${i}")
                    activeZoneOptions["${i}"] = resolvedName
                    
                    // Zone State & Locks
                    def isLocked = gnLock && (gnLock.currentValue("switch") == "on")
                    def isEventMuted = (state.doorbellMutedSpks?.contains(spk.id)) || (state.doorOpenMutedSpks?.contains(spk.id))
                    
                    def zoneNameStr = resolvedName
   
                    if (isLocked) {
                        zoneNameStr += "<br><span style='color:purple; font-size:10px; text-transform:uppercase;'>🌙 GN Override Active</span>"
                    }

                    // Power & Play Status Logic
                    def isPoweredOn = sw ? (sw.currentValue("switch") == "on") : true
                    def pwrStatus = sw ? (isPoweredOn ? "<span style='color:green;'>ON</span>" : "<span style='color:red;'>OFF</span>") : "N/A"
                    
                    def playStatus = spk.currentValue("status")?.toUpperCase() ?: "UNKNOWN"
                    def isMuted = spk.currentValue("mute") == "muted"
                    
                    def statusIcon = "⚪"
                    def statusText = playStatus
                    def statusColor = "gray"
                    
                    if (!isPoweredOn) {
                        statusIcon = "🔌❌"
                        statusText = "POWER CUT"
                        statusColor = "red"
                    } else if (isEventMuted) {
                        statusIcon = "🔇"
                        statusText = "EVENT MUTE"
                        statusColor = "red"
                    } else if (isMuted) {
                        statusIcon = "🔇"
                        statusText = "MUTED"
                        statusColor = "orange"
                    } else if (playStatus == "PLAYING") {
                        statusIcon = "▶️"
                        statusColor = "blue"
                    } else if (playStatus == "PAUSED") {
                        statusIcon = "⏸️"
                        statusColor = "orange"
                    } else if (playStatus == "STOPPED") {
                        statusIcon = "⏹️"
                        statusColor = "gray"
                    }
                    
                    def vol = spk.currentValue("volume") ?: "--"
                    def trackTitle = spk.currentValue("trackDescription")
                    if (!trackTitle || trackTitle.trim() == "") trackTitle = "Idle / Streaming"
                    
                    dashHTML += "<tr><td class='dash-hl'>${zoneNameStr}</td><td><b>${pwrStatus}</b></td><td style='color:${statusColor}; font-weight:bold;'>${statusIcon} ${statusText}</td><td>${vol}%</td><td style='text-align:left; font-weight:bold; font-size:13px;'>${trackTitle}</td></tr>"
                }
            }
            dashHTML += "</tbody></table>"
            
            if (hasZones) {
                input "refreshDash", "button", title: "🔄 Refresh Dashboard Data"
                paragraph dashHTML
            } else {
                paragraph "<i>Please configure your Sonos zones below to populate the dashboard.</i>"
            }
        }

        if (hasZones && optControlPanel) {
            section("<b>Active Control Panel</b>") {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Remotely control your zones, broadcast TTS, or manage Virtual Favorites.</div>"
                
                paragraph "<div style='background-color:#dc3545; color:white; padding:8px 10px; font-weight:bold; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px; margin-bottom:5px;'>🚨 Global Emergency Override</div>"
                input "btnPauseAll", "button", title: "🛑 PAUSE ALL ZONES INSTANTLY", width: 12

                input "activeZoneControl", "enum", title: "Select Zone to Control", options: activeZoneOptions, submitOnChange: true
                
                if (activeZoneControl) {
                    def targetSpk = settings["z${activeZoneControl}Speaker"]
                    def targetSw = settings["z${activeZoneControl}Switch"]
                    def targetName = settings["z${activeZoneControl}Name"] ?: (targetSpk ? targetSpk.label : "Zone ${activeZoneControl}")
                    
                    if (targetSpk) {
                        def cpPwr = targetSw ? (targetSw.currentValue("switch") == "on" ? "ON" : "OFF") : "N/A"
                        def cpState = targetSpk.currentValue("status")?.toUpperCase() ?: "UNKNOWN"
                        def cpVol = targetSpk.currentValue("volume") ?: "--"
                        def cpTrack = targetSpk.currentValue("trackDescription") ?: "Idle / Unknown"
                        
                        def cpHTML = """
                        <style>
                            .cp-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-bottom: 5px; border: 1px solid #ccc; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                            .cp-table th { background-color: #343a40; color: white; padding: 10px; text-align: center; text-transform: uppercase; letter-spacing: 1px; }
                            .cp-table td { background-color: white; padding: 12px; text-align: center; border: 1px solid #ccc; }
                            .cp-lbl { font-weight: bold; font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 4px; }
                            .cp-val { font-weight: bold; font-size: 16px; color: #333; }
                        </style>
                        <table class="cp-table">
                            <thead><tr><th colspan="4">🎯 Active Target: ${targetName}</th></tr></thead>
                            <tbody>
                                <tr>
                                    <td width="20%"><div class="cp-lbl">Main Power</div><div class="cp-val" style="color:${cpPwr=='ON'?'green':'red'};">${cpPwr}</div></td>
                                    <td width="20%"><div class="cp-lbl">Status</div><div class="cp-val" style="color:${cpState=='PLAYING'?'blue':(cpState=='PAUSED'?'orange':'gray')};">${cpState}</div></td>
                                    <td width="20%"><div class="cp-lbl">Volume</div><div class="cp-val">${cpVol}%</div></td>
                                    <td width="40%"><div class="cp-lbl">Now Playing</div><div class="cp-val" style="font-size:12px; line-height:1.2;">${cpTrack}</div></td>
                                </tr>
                            </tbody>
                        </table>
                        """
                        paragraph cpHTML
                    }

                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>🎛️ Basic Transport</div>"
                    input "btnPlay", "button", title: "▶️ Play", width: 2
                    input "btnPause", "button", title: "⏸ Pause", width: 2
                    input "btnPrev", "button", title: "⏮ Prev", width: 2
                    input "btnNext", "button", title: "⏭ Next", width: 2
                    input "btnVolDown", "button", title: "🔉 Vol -5%", width: 2
                    input "btnVolUp", "button", title: "🔊 Vol +5%", width: 2
                    
                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>🎚️ Advanced Controls</div>"
                    input "btnShuffle", "button", title: "🔀 Toggle Shuffle", width: 3
                    input "btnRepeat", "button", title: "🔁 Toggle Repeat", width: 3
                    input "btnNightMode", "button", title: "🌙 Toggle Night Mode", width: 3
                    input "btnSpeechEnhance", "button", title: "🗣️ Toggle Speech Enhance", width: 3
                    
                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>📢 Intercom Broadcast (TTS)</div>"
                    input "ttsMessage", "text", title: "Message to Broadcast", required: false, width: 4
                    input "ttsVolume", "number", title: "Vol (%)", required: false, defaultValue: 40, width: 2
                    input "ttsPriority", "bool", title: "Emergency Override", defaultValue: false, width: 3
                    input "btnTTS", "button", title: "📢 Send TTS", width: 3
                    
                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>⏳ Sleep Timer</div>"
                    input "sleepTimerMins", "number", title: "Minutes until Pause", required: false, width: 6
                    input "sleepTimerFade", "number", title: "Fade-Out Duration (Sec)", required: false, width: 3
                    input "btnSleep", "button", title: "⏳ Start Timer", width: 3
                    if (state.sleepTimers && state.sleepTimers[activeZoneControl]) {
                        paragraph "<span style='color:orange;'><i>Sleep Timer currently active for this zone.</i></span>"
                        input "btnCancelSleep", "button", title: "Cancel Timer"
                    }

                    if (optFavorites) {
                        paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>⭐ Favorites Management</div>"
                        paragraph "<div style='font-size:13px; color:#555;'>Save the current Track URI and volume to a Virtual Switch, or manage existing ones.</div>"
                        input "btnSaveFav", "button", title: "➕ Save Current Track as Virtual Switch", width: 4
                        
                        def favOptions = [:]
                        if (state.savedFavorites) {
                            state.savedFavorites.each { dni, data -> favOptions[dni] = data.name }
                        }
                        
                        if (favOptions) {
                            input "favToDelete", "enum", title: "Select Favorite to Delete", options: favOptions, required: false, width: 5
                            input "btnDeleteFav", "button", title: "🗑️ Delete Selected", width: 3
                        }
                    }
                } else {
                    paragraph "<div style='padding: 10px; background-color: #e9ecef; border-left: 5px solid #007bff; border-radius: 5px;'><b>Note:</b> Select a zone from the dropdown above to reveal the transport controls, TTS broadcast, and Favorites Virtual Switch generator.</div>"
                }
            }
        }

        if (optCostTracker) {
            section("<b>Energy Cost & ROI Savings Tracking</b>") {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks the exact runtime and idle time of your smart plugs to estimate utility costs.</div>"
                input "costPerKwh", "decimal", title: "Utility Rate (USD per kWh)", required: false, defaultValue: 0.15
                
                if (state.runHistory) {
                    paragraph "<b>7-Day Energy Cost & Savings Estimate</b>"
                    paragraph renderCostDashboard()
                }
                
                input "resetHistory", "button", title: "Clear Tracking History"
            }
        }

        section("<b>Recent Action History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        // ========================================================
        // GLOBAL CONTROLS & MODULE TOGGLES
        // ========================================================
        section("<b>Global Controls & Module Toggles</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Manage global permissions and enable or disable major app features. Disabling a feature removes its configuration options and halts its background processing.</div>"
            input "appEnableSwitch", "capability.switch", title: "<b>Master App Enable/Disable Switch</b> (Blocks Automations when OFF)", required: false
            paragraph "<hr>"
            input "enableControlPanel", "bool", title: "Enable Active Control Panel & TTS", defaultValue: true, submitOnChange: true
            input "enablePowerManagement", "bool", title: "Enable Smart Power Automation", defaultValue: true, submitOnChange: true
            input "enableSelfHealing", "bool", title: "Enable Self-Healing on Hub Reboot", defaultValue: true, submitOnChange: true
            input "enableCostTracker", "bool", title: "Enable Energy Cost Tracking", defaultValue: true, submitOnChange: true
            input "enableFavorites", "bool", title: "Enable Virtual Switch Favorites", defaultValue: true, submitOnChange: true
            
            if (optFavorites) {
                input "enableAutoPurge", "bool", title: "Enable Auto-Purge for Favorites (Housekeeping)", defaultValue: true, submitOnChange: true
            }
        }

        // ========================================================
        // SYSTEM CONFIGURATION
        // ========================================================

        for (int i = 1; i <= 10; i++) {
            def secTitle = settings["z${i}Name"] ? "<b>${settings["z${i}Name"]} Configuration</b>" : "<b>Zone ${i} Configuration</b>"
            section(secTitle, hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone</b>", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Custom Zone Name", required: false, submitOnChange: true
                    input "z${i}Speaker", "capability.musicPlayer", title: "Select Sonos Speaker", required: true
                    
                    if (optPowerManagement || optCostTracker) {
                        input "z${i}Switch", "capability.switch", title: "Select Smart Power Plug", required: false
                        input "z${i}Type", "enum", title: "Speaker Hardware Type", options: [
                            "Sonos Era 100", "Sonos Era 300", 
                            "Sonos One / One SL / Play:1", "Sonos Play:3", "Sonos Five / Play:5", 
                            "Sonos Beam (Gen 1/2)", "Sonos Arc / Playbar / Playbase", "Sonos Ray", 
                            "Sonos Sub / Sub Mini", "Sonos Amp / Connect:Amp", "Sonos Port / Connect", 
                            "Sonos Move / Roam (Docked)", "IKEA SYMFONISK"
                        ], required: true, defaultValue: "Sonos One / One SL / Play:1"
                    }
                    
                    paragraph "<b>Protection & Overrides</b>"
                    input "z${i}GoodNightSwitch", "capability.switch", title: "Good Night Override Switch (Aborts all automations when ON)", required: false
                    input "z${i}MaxVol", "number", title: "Maximum Volume Cap (%) - Hardware Protection", required: false, range: "1..100"

                    if (optPowerManagement) {
                        paragraph "<b>Power Automation & Startup Settings</b>"
                        input "z${i}TurnOnModes", "mode", title: "Modes to Power ON this Zone", multiple: true, required: false
                        input "z${i}TurnOffModes", "mode", title: "Modes to Power OFF this Zone", multiple: true, required: false
                        
                        input "z${i}StartVol", "number", title: "Default Target Startup Volume (%)", required: false, range: "1..100"
                        input "z${i}FadeIn", "number", title: "Fade-In Duration (Seconds) - Ramps volume slowly", required: false
                        input "z${i}AutoResume", "bool", title: "Auto-Resume Playback on Boot", defaultValue: false
                        
                        paragraph "<b>Mode-Based Music Routine</b>"
                        input "z${i}ModeRoutineEnabled", "bool", title: "Enable Mode-Based Routine", defaultValue: false, submitOnChange: true
                        if (settings["z${i}ModeRoutineEnabled"]) {
                            input "z${i}RoutineMode", "mode", title: "Select Mode to Trigger Routine", required: false
                            input "z${i}RoutineDelay", "number", title: "Delay before playing (seconds)", defaultValue: 10, required: false
                            
                            def favList = [:]
                            if (state.savedFavorites) {
                                state.savedFavorites.each { dni, data -> favList[dni] = data.name }
                            }
                            if (favList) {
                                input "z${i}RoutineFavorite", "enum", title: "Select Favorite to Play", options: favList, required: false
                            } else {
                                paragraph "<i>No Virtual Favorites saved yet. Use the Control Panel to save a favorite first.</i>"
                            }
                        }
                    }

                    paragraph "<b>Follow-Me Audio Automation</b>"
                    input "z${i}FollowMeEnabled", "bool", title: "Enable Follow-Me for this Zone", defaultValue: false, submitOnChange: true
                    if (settings["z${i}FollowMeEnabled"]) {
                        input "z${i}FollowMeMotion", "capability.motionSensor", title: "When Motion is Active Here...", required: false, multiple: true
                        
                        def followMeOpts = [:]
                        for (int j = 1; j <= 10; j++) {
                            if (j != i && settings["enableZ${j}"] && settings["z${j}Speaker"]) {
                                def optName = settings["z${j}Name"] ?: (settings["z${j}Speaker"].label ?: "Zone ${j}")
                                followMeOpts["${j}"] = optName
                            }
                        }
                        input "z${i}FollowMeSource", "enum", title: "...Pull Music FROM this Zone", options: followMeOpts, required: false
                    }
                }
            }
        }

        section("<b>Event Overrides & Muting</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Temporarily mutes playing speakers during real-world events. Zones locked by Good Night switches will be ignored to preserve privacy.</div>"
            input "enableEventOverrides", "bool", title: "<b>Enable Event Muting</b>", defaultValue: false, submitOnChange: true
            if (enableEventOverrides) {
                input "doorbellButton", "capability.pushableButton", title: "Doorbell Button", required: false
                input "doorbellButtonNum", "number", title: "Doorbell Button Number", required: false, defaultValue: 1
                input "doorbellMuteTime", "number", title: "Seconds to Mute for Doorbell", required: false, defaultValue: 30
                paragraph "<hr>"
                input "doorSensors", "capability.contactSensor", title: "Perimeter Doors (Mutes when Open)", required: false, multiple: true
            }
        }

        section("<b>Automated Night-Time Sweeps</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Sweeps through the house at a specific time and lowers all speaker volumes to a safe level to prevent late-night jump scares.</div>"
            input "enableNightSweep", "bool", title: "<b>Enable Night Sweeps</b>", defaultValue: false, submitOnChange: true
            if (enableNightSweep) {
                input "nightSweepTime", "time", title: "Time to Execute Sweep", required: false
                input "nightSweepVol", "number", title: "Safe Night Volume (%)", required: false, defaultValue: 15, range: "0..100"
            }
        }

        if (optFavorites && optAutoPurge) {
            section("<b>Virtual Switch Housekeeping</b>") {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically purges old Favorite Virtual Switches to keep your Hubitat database clean.</div>"
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

def isAppEnabled() {
    if (settings.appEnableSwitch && settings.appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isZoneLocked(zNum) {
    def sw = settings["z${zNum}GoodNightSwitch"]
    return (sw && sw.currentValue("switch") == "on")
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.runHistory) state.runHistory = [:]
    if (!state.savedFavorites) state.savedFavorites = [:]
    if (!state.sleepTimers) state.sleepTimers = [:]
    if (!state.doorbellMutedSpks) state.doorbellMutedSpks = []
    if (!state.doorOpenMutedSpks) state.doorOpenMutedSpks = []
    if (!state.ttsQueue) state.ttsQueue = []
    
    // Default last follow me time
    if (!state.lastFollowMeTime) state.lastFollowMeTime = 0
    
    state.isSpeaking = false
    
    if (settings.enablePowerManagement != false) {
        subscribe(location, "mode", modeChangeHandler)
        runEvery1Hour(hourlyStateEnforcement)
    }

    if (settings.enableSelfHealing != false) {
        subscribe(location, "systemStart", systemStartHandler)
    }
    
    if (settings.enableFavorites != false) {
        getChildDevices().each { child -> subscribe(child, "switch", childSwitchHandler) }
        if (settings.enableAutoPurge != false) {
            schedule("0 0 3 ? * *", purgeOldFavorites)
        }
    }

    if (settings.enableCostTracker != false) {
        runEvery15Minutes(calculateEnergy)
    }

    if (settings.enableEventOverrides) {
        if (settings.doorbellButton) subscribe(settings.doorbellButton, "pushed", doorbellHandler)
        if (settings.doorSensors) subscribe(settings.doorSensors, "contact", doorHandler)
    }

    if (settings.enableNightSweep && settings.nightSweepTime) {
        schedule(settings.nightSweepTime, executeNightSweep)
    }

    // Hardware Volume Protection & Follow-Me Subscriptions
    for (int i = 1; i <= 10; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Speaker"] && settings["z${i}MaxVol"]) {
                subscribe(settings["z${i}Speaker"], "volume", volumeHandler)
            }
            if (settings["z${i}FollowMeEnabled"] && settings["z${i}FollowMeMotion"]) {
                subscribe(settings["z${i}FollowMeMotion"], "motion", followMeMotionHandler)
            }
        }
    }

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

    if (btn == "btnPauseAll") {
        logAction("🚨 EMERGENCY PAUSE ALL TRIGGERED 🚨")
        for (int i = 1; i <= 10; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Speaker"]) settings["z${i}Speaker"].pause()
        }
        return
    }

    if (!isAppEnabled()) {
        logAction("Master Switch is OFF. Control Panel actions ignored.")
        return
    }

    if (settings.enableControlPanel == false) return

    if (activeZoneControl && btn.startsWith("btn")) {
        def zNum = activeZoneControl
        def spk = settings["z${zNum}Speaker"]
        if (!spk) return

        if (btn == "btnPlay") { spk.play(); logAction("Command -> Sent PLAY to ${spk.label}") }
        if (btn == "btnPause") { spk.pause(); logAction("Command -> Sent PAUSE to ${spk.label}") }
        if (btn == "btnNext") { spk.nextTrack(); logAction("Command -> Sent NEXT TRACK to ${spk.label}") }
        if (btn == "btnPrev") { spk.previousTrack(); logAction("Command -> Sent PREV TRACK to ${spk.label}") }
        if (btn == "btnVolUp") { def v = (spk.currentValue("volume") ?: 0) + 5; spk.setLevel(v > 100 ? 100 : v); logAction("Command -> Vol UP on ${spk.label}") }
        if (btn == "btnVolDown") { def v = (spk.currentValue("volume") ?: 0) - 5; spk.setLevel(v < 0 ? 0 : v); logAction("Command -> Vol DOWN on ${spk.label}") }
        
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

        if (btn == "btnTTS" && ttsMessage) {
            def curVol = spk.currentValue("volume") ?: 20
            def targetVol = ttsVolume ?: 40
            def priority = ttsPriority ?: false
            queueTTS(spk.id, ttsMessage, targetVol, curVol, priority)
        }
        
        if (btn == "btnSleep" && sleepTimerMins) {
            state.sleepTimers[zNum] = true
            def fadeDur = sleepTimerFade ?: 0
            runIn((sleepTimerMins * 60).toInteger(), executeSleepTimer, [data: [spkId: spk.id, zNum: zNum, fade: fadeDur]])
            logAction("Command -> Sleep Timer started for ${spk.label} (${sleepTimerMins} mins, ${fadeDur}s fade).")
        }
        
        if (btn == "btnCancelSleep") {
            state.sleepTimers[zNum] = false
            logAction("Command -> Sleep Timer cancelled for ${spk.label}.")
        }

        if (settings.enableFavorites != false) {
            if (btn == "btnSaveFav") createFavoriteVirtualSwitch(spk)
            if (btn == "btnDeleteFav" && settings.favToDelete) {
                def dni = settings.favToDelete
                def favName = state.savedFavorites[dni]?.name ?: "Unknown"
                try { deleteChildDevice(dni) } catch (e) { }
                state.savedFavorites.remove(dni)
                app.removeSetting("favToDelete")
                logAction("Command -> Deleted Favorite Virtual Switch: [${favName}]")
            }
        }
    }
}

// --- QUEUED TTS ENGINE ---

def queueTTS(spkId, message, vol, origVol, priority) {
    if (!state.ttsQueue) state.ttsQueue = []
    def payload = [spkId: spkId, msg: message, vol: vol, origVol: origVol, id: now()]
    
    if (priority) {
        state.ttsQueue.add(0, payload) 
        logAction("Emergency TTS Override placed at front of queue.")
        processTTSQueue(true) 
    } else {
        state.ttsQueue << payload
        if (!state.isSpeaking) {
            processTTSQueue(false)
        }
    }
}

def processTTSQueue(force = false) {
    if (!state.ttsQueue || state.ttsQueue.size() == 0) {
        state.isSpeaking = false
        return
    }
    if (state.isSpeaking && !force) return 
    
    state.isSpeaking = true
    def payload = state.ttsQueue.remove(0)
    def spk = getSpeakerById(payload.spkId)
    
    if (spk) {
        spk.setLevel(payload.vol)
        spk.speak(payload.msg)
        logAction("TTS Broadcast: '${payload.msg}' on ${spk.label}")
        
        def waitTime = Math.max((payload.msg.length() / 15).toInteger(), 6)
        runIn(waitTime, restoreVolumeAndContinueTTS, [data: [spkId: spk.id, origVol: payload.origVol]])
    } else {
        processTTSQueue(false)
    }
}

def restoreVolumeAndContinueTTS(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) spk.setLevel(data.origVol)
    state.isSpeaking = false
    runIn(1, processTTSQueue)
}

// --- VOLUME FADING ENGINE ---

def startVolumeFadeWrapper(data) {
    startVolumeFade(data.spkId, data.targetVol, data.durationSec, data.isFadeOut)
}

def startVolumeFade(spkId, targetVol, durationSec, isFadeOut = false) {
    def spk = getSpeakerById(spkId)
    if (!spk) return
    def curVol = spk.currentValue("volume") ?: 0
    if (curVol == targetVol) return
    
    // Check for native hardware fade support first (Fix #2)
    def hasNativeFade = false
    if (spk.hasCommand("setLevel")) {
        def setLevelCmd = spk.getSupportedCommands().find { it.name == "setLevel" }
        if (setLevelCmd && setLevelCmd.arguments?.size() > 1) {
            hasNativeFade = true
        }
    }
    
    if (hasNativeFade) {
        logAction("Using native hardware volume fade for ${spk.label}")
        spk.setLevel(targetVol, durationSec)
        if (isFadeOut) {
            runIn(durationSec + 1, finalizeNativeFadeOut, [data: [spkId: spk.id, origVol: curVol]])
        }
        return
    }
    
    // Software fallback logic adjusted to prevent queue flooding (Fix #2)
    def stepDelay = 3 // Minimum delay between steps to protect hub scheduler
    def steps = Math.max((durationSec / stepDelay).toInteger(), 1)
    
    // Cap steps to a maximum of 5 executions per fade process
    if (steps > 5) {
        steps = 5
        stepDelay = (durationSec / steps).toInteger()
    }
    
    def volDiff = targetVol - curVol
    def stepAmount = volDiff / steps

    state["fade_${spkId}"] = [
        current: curVol, target: targetVol, stepAmt: stepAmount, 
        stepsLeft: steps, isFadeOut: isFadeOut, origVol: curVol
    ]
    
    runIn(stepDelay, processVolumeFade, [data: [spkId: spkId, delay: stepDelay]])
}

def finalizeNativeFadeOut(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.pause()
        logAction("Fade-out complete for ${spk.label}. Paused.")
        runIn(2, restoreVolume, [data: [spkId: data.spkId, vol: data.origVol]])
    }
}

def processVolumeFade(data) {
    def spkId = data.spkId
    def spk = getSpeakerById(spkId)
    def fadeData = state["fade_${spkId}"]
    if (!spk || !fadeData) return
    
    fadeData.stepsLeft = fadeData.stepsLeft - 1
    fadeData.current = fadeData.current + fadeData.stepAmt
    
    def newVol = fadeData.current.toInteger()
    if (newVol < 0) newVol = 0
    if (newVol > 100) newVol = 100
    
    spk.setLevel(newVol)
    
    if (fadeData.stepsLeft > 0) {
        state["fade_${spkId}"] = fadeData
        runIn(data.delay, processVolumeFade, [data: [spkId: spkId, delay: data.delay]])
    } else {
        spk.setLevel(fadeData.target)
        if (fadeData.isFadeOut) {
            spk.pause()
            logAction("Software Fade-out complete for ${spk.label}. Paused.")
            runIn(2, restoreVolume, [data: [spkId: spkId, vol: fadeData.origVol]]) 
        } else {
            logAction("Software Fade-in complete for ${spk.label}.")
        }
        state.remove("fade_${spkId}")
    }
}

// --- FOLLOW ME AUDIO ENGINE ---

def followMeMotionHandler(evt) {
    if (!isAppEnabled() || evt.value != "active") return
    
    // Follow-Me Debouncing Logic (Fix #4)
    def nowTime = now()
    if (state.lastFollowMeTime && (nowTime - state.lastFollowMeTime) < 5000) {
        logAction("Follow-Me Audio: Ignored concurrent motion trigger (debounced).")
        return
    }
    state.lastFollowMeTime = nowTime

    def motionId = evt.device.id
    
    for (int i = 1; i <= 10; i++) {
        def motions = settings["z${i}FollowMeMotion"]
        if (motions && motions.find { it.id == motionId }) {
            def sourceZoneNum = settings["z${i}FollowMeSource"]
            if (!sourceZoneNum || isZoneLocked(i)) continue
            
            def srcSpk = settings["z${sourceZoneNum}Speaker"]
            def targetSpk = settings["z${i}Speaker"]
            
            if (srcSpk && targetSpk && srcSpk.currentValue("status") == "playing") {
                logAction("Follow-Me Triggered: Moving audio from ${srcSpk.label} to ${targetSpk.label}")
                
                def trackUri = srcSpk.currentValue("trackUri") ?: srcSpk.currentValue("uri")
                def curVol = srcSpk.currentValue("volume") ?: 15
                
                if (trackUri) {
                    targetSpk.setLevel(curVol)
                    targetSpk.setTrack(trackUri)
                    runIn(1, triggerPlayOnFav, [data: [spkId: targetSpk.id]])
                    srcSpk.pause()
                }
            }
        }
    }
}

// --- BMS HARDWARE PROTECTION (VOLUME CLAMPING) ---

def volumeHandler(evt) {
    if (!isAppEnabled()) return
    def spk = evt.device
    def vol = evt.value.toInteger()
    
    for (int i = 1; i <= 10; i++) {
        if (settings["z${i}Speaker"]?.id == spk.id) {
            def maxVol = settings["z${i}MaxVol"]
            if (maxVol && vol > maxVol) {
                logAction("Hardware Protection Activated! Reduced ${spk.label} from ${vol}% to ${maxVol}%.")
                spk.setLevel(maxVol)
            }
            break
        }
    }
}

// --- EVENT OVERRIDES (DOORBELL & DOORS) ---

def doorbellHandler(evt) {
    if (!isAppEnabled() || !settings.enableEventOverrides) return
    def btnNum = settings.doorbellButtonNum ?: 1
    
    if (evt.value == btnNum.toString()) {
        logAction("Doorbell rang! Muting applicable playing speakers.")
        def mutedSpks = []
        for(int i = 1; i <= 10; i++) {
            def spk = settings["z${i}Speaker"]
            if (spk && !isZoneLocked(i)) {
                if (spk.currentValue("status") == "playing" && spk.currentValue("mute") != "muted") {
                    spk.mute()
                    mutedSpks << spk.id
                }
            }
        }
        state.doorbellMutedSpks = mutedSpks
        runIn(settings.doorbellMuteTime ?: 30, restoreDoorbellMute)
    }
}

def restoreDoorbellMute() {
    state.doorbellMutedSpks?.each { id -> getSpeakerById(id)?.unmute() }
    state.doorbellMutedSpks = []
    logAction("Doorbell mute timeout finished. Restoring volumes.")
}

def doorHandler(evt) {
    if (!isAppEnabled() || !settings.enableEventOverrides) return
    def anyOpen = settings.doorSensors.any { it.currentValue("contact") == "open" }
    
    if (anyOpen) {
        if (!state.doorOpenMuted) {
            logAction("Monitored door opened! Muting applicable speakers.")
            def mutedSpks = []
            for(int i = 1; i <= 10; i++) {
                def spk = settings["z${i}Speaker"]
                if (spk && !isZoneLocked(i)) {
                    if (spk.currentValue("status") == "playing" && spk.currentValue("mute") != "muted") {
                        spk.mute()
                        mutedSpks << spk.id
                    }
                }
            }
            state.doorOpenMuted = true
            state.doorOpenMutedSpks = mutedSpks
        }
    } else {
        if (state.doorOpenMuted) {
            logAction("Doors closed. Restoring volumes.")
            state.doorOpenMutedSpks?.each { id -> getSpeakerById(id)?.unmute() }
            state.doorOpenMuted = false
            state.doorOpenMutedSpks = []
        }
    }
}

// --- NIGHT-TIME SWEEPS ---

def executeNightSweep() {
    if (!isAppEnabled() || !settings.enableNightSweep) return
    logAction("Executing Automated Night-Time Volume Sweep.")
    
    for (int i = 1; i <= 10; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Speaker"]) {
            if (isZoneLocked(i)) continue
            def spk = settings["z${i}Speaker"]
            def targetVol = settings.nightSweepVol ?: 15
            spk.setLevel(targetVol)
        }
    }
}

def restoreVolume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) spk.setLevel(data.vol)
}

def executeSleepTimer(data) {
    if (!state.sleepTimers[data.zNum]) return
    
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        if (data.fade && data.fade > 0) {
            logAction("Sleep Timer Executed: Initiating Fade-Out for ${spk.label}.")
            startVolumeFade(spk.id, 0, data.fade, true)
        } else {
            spk.pause()
            logAction("Sleep Timer Executed: Paused ${spk.label}.")
        }
    }
    state.sleepTimers[data.zNum] = false
}

// --- FAVORITES & VIRTUAL SWITCH GENERATOR ---

def createFavoriteVirtualSwitch(speaker) {
    if (settings.enableFavorites == false) return

    def trackUri = speaker.currentValue("trackUri")
    def trackDataStr = speaker.currentValue("trackData")
    
    if (!trackUri && trackDataStr) {
        try {
            def json = new groovy.json.JsonSlurper().parseText(trackDataStr)
            trackUri = json?.trackUri ?: json?.enqueuedUri ?: json?.uri
        } catch (e) { }
    }
    
    if (!trackUri) trackUri = speaker.currentValue("uri")
    if (!trackUri) trackUri = speaker.currentValue("enqueuedUri")

    def trackName = speaker.currentValue("trackDescription")
    if (!trackName || trackName.trim() == "") trackName = speaker.currentValue("name")
    if (!trackName || trackName.trim() == "") {
        if (trackDataStr) {
            try {
                def json = new groovy.json.JsonSlurper().parseText(trackDataStr)
                trackName = json?.station ?: json?.title ?: ""
            } catch (e) { }
        }
    }
    if (!trackName || trackName.trim() == "") trackName = "Custom Stream (${now().toString().substring(8)})"
    
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
        
        state.savedFavorites[dni] = [uri: trackUri, speakerId: speaker.id, name: trackName, vol: curVol, timestamp: now()]
        subscribe(child, "switch", childSwitchHandler)
        logAction("SUCCESS: Created Virtual Switch [${label}] to recall current track at ${curVol}%.")
    } catch (e) { logAction("ERROR: Failed to create virtual switch. Check hub logs.") }
}

def childSwitchHandler(evt) {
    if (settings.enableFavorites == false) return

    if (evt.value == "on") {
        def dni = evt.device.deviceNetworkId
        def favData = state.savedFavorites[dni]
        
        if (favData) {
            def speaker = getSpeakerById(favData.speakerId)
            if (speaker) {
                logAction("Triggered Favorite via Switch. Setting volume to ${favData.vol}% and playing [${favData.name}] on ${speaker.label}")
                if (favData.vol != null) speaker.setLevel(favData.vol)
                speaker.setTrack(favData.uri)
                runIn(2, triggerPlayOnFav, [data: [spkId: speaker.id]])
            }
        }
        runIn(3, turnOffChild, [data: [dni: dni]])
    }
}

def triggerPlayOnFav(data) { getSpeakerById(data.spkId)?.play() }
def turnOffChild(data) { getChildDevice(data.dni)?.off() }
def getSpeakerById(id) { for (int i = 1; i <= 10; i++) { if (settings["z${i}Speaker"]?.id == id) return settings["z${i}Speaker"] }; return null }

// --- HOUSEKEEPING ---

def purgeOldFavorites() {
    if (settings.enableFavorites == false || settings.enableAutoPurge == false) return
    
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

// --- POWER MANAGEMENT LOGIC & SELF-HEALING ENGINE ---

def hourlyStateEnforcement() {
    if (!isAppEnabled() || settings.enablePowerManagement == false) return
    def currentMode = location.mode
    def delayMult = 0
    def zonesFixed = false
    
    for (int i = 1; i <= 10; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Switch"]) {
            if (isZoneLocked(i)) continue
            
            def sw = settings["z${i}Switch"]
            def onModes = settings["z${i}TurnOnModes"] as List
            def offModes = settings["z${i}TurnOffModes"] as List
            def currentState = sw.currentValue("switch")
            
            if (onModes && onModes.contains(currentMode) && currentState != "on") {
                def delay = delayMult * 6 + 1
                runIn(delay, enforcePowerState, [data: [zNum: i, state: "on"]])
                delayMult++
                zonesFixed = true
            } else if (offModes && offModes.contains(currentMode) && currentState != "off") {
                def delay = delayMult * 6 + 1
                runIn(delay, enforcePowerState, [data: [zNum: i, state: "off"]])
                delayMult++
                zonesFixed = true
            }
        }
    }
    if (zonesFixed) {
        logAction("Hourly Enforcement: Correcting missing power states...")
    }
}

def enforcePowerState(data) {
    def sw = settings["z${data.zNum}Switch"]
    if (sw) {
        if (data.state == "on") {
            sw.on()
            logAction("Hourly Enforcement: Turned ON Zone ${data.zNum}.")
        } else {
            sw.off()
            logAction("Hourly Enforcement: Turned OFF Zone ${data.zNum}.")
        }
    }
}

def modeChangeHandler(evt) {
    if (!isAppEnabled() || settings.enablePowerManagement == false) return
    syncSystemToMode(evt.value, "Mode Change")
}

def systemStartHandler(evt) {
    if (!isAppEnabled() || settings.enablePowerManagement == false || settings.enableSelfHealing == false) return
    logAction("🔄 Hub Reboot/Power Outage Detected. Waiting 60s for mesh networks to settle before Self-Healing Sync...")
    runIn(60, executeSelfHealingSync)
}

def executeSelfHealingSync() {
    logAction("🔄 Initiating Self-Healing Sync now...")
    syncSystemToMode(location.mode, "Self-Healing")
}

def syncSystemToMode(currentMode, triggerSource) {
    def zonesToTurnOn = []
    def zonesToTurnOff = []
    def zonesToRunRoutine = []

    for (int i = 1; i <= 10; i++) {
        if (settings["enableZ${i}"]) {
            if (isZoneLocked(i)) {
                logAction("${triggerSource} Engine skipping Zone ${i} (Good Night Override Active).")
                continue
            }
            
            def onModes = settings["z${i}TurnOnModes"] as List
            def offModes = settings["z${i}TurnOffModes"] as List
            
            if (onModes && onModes.contains(currentMode)) zonesToTurnOn << i
            else if (offModes && offModes.contains(currentMode)) zonesToTurnOff << i
            
            if (settings["z${i}ModeRoutineEnabled"] && settings["z${i}RoutineMode"] == currentMode) {
                zonesToRunRoutine << i
            }
        }
    }

    if (zonesToTurnOn) {
        logAction("${triggerSource}: Mode is ${currentMode}. Initiating Startup sequence for active zones.")
        powerUpSpecificZones(zonesToTurnOn, zonesToRunRoutine)
    } 
    
    if (zonesToTurnOff) {
        logAction("${triggerSource}: Mode is ${currentMode}. Initiating Failsafe Pause & Shutdown for inactive zones.")
        gracefulShutdownSpecificZones(zonesToTurnOff)
    }
    
    if (zonesToRunRoutine) {
        logAction("${triggerSource}: Mode is ${currentMode}. Initiating Music Routines.")
        def rDelayMult = 0
        zonesToRunRoutine.each { zNum -> 
            def delay = (rDelayMult * 6) + 2
            runIn(delay, executeModeRoutine, [data: [zNum: zNum]]) 
            rDelayMult++
        }
    }
}

def powerUpSpecificZones(zones, routineZones = []) {
    def delayMult = 0
    zones.each { i ->
        if (settings["z${i}Switch"]) {
            def delay = delayMult * 6 + 1
            runIn(delay, executeStaggeredPowerOn, [data: [zNum: i, isRoutine: routineZones.contains(i)]])
            delayMult++
        }
    }
    if(zones) logAction("Master power switches scheduled to turn ON (Staggered).")
}

def executeStaggeredPowerOn(data) {
    def i = data.zNum
    def isRoutine = data.isRoutine
    def sw = settings["z${i}Switch"]
    def spk = settings["z${i}Speaker"]
    
    if (sw) sw.on()
    
    if (settings["z${i}StartVol"] && spk) {
        def targetVol = settings["z${i}StartVol"]
        def fadeDur = settings["z${i}FadeIn"]
        
        if (fadeDur && fadeDur > 0) {
            spk.setLevel(0) 
            runIn(5, startVolumeFadeWrapper, [data: [spkId: spk.id, targetVol: targetVol, durationSec: fadeDur, isFadeOut: false]])
        } else {
            runIn(5, setStartupVolume, [data: [spkId: spk.id, vol: targetVol]])
        }
    }
    
    if (settings["z${i}AutoResume"] && spk && !isRoutine) {
        runIn(60, triggerAutoResume, [data: [spkId: spk.id]])
    }
}

def executeModeRoutine(data) {
    def i = data.zNum
    def spk = settings["z${i}Speaker"]
    def sw = settings["z${i}Switch"]
    def delay = settings["z${i}RoutineDelay"] ?: 10
    def favDni = settings["z${i}RoutineFavorite"]
    
    if (sw && sw.currentValue("switch") != "on") sw.on()
    
    if (favDni && state.savedFavorites && state.savedFavorites[favDni]) {
        runIn(delay, playRoutineFavorite, [data: [spkId: spk.id, favDni: favDni]])
        logAction("Routine scheduled for ${spk.label} in ${delay} seconds.")
    } else {
        logAction("Routine skipped for ${spk.label}: No valid favorite selected.")
    }
}

def playRoutineFavorite(data) {
    def spk = getSpeakerById(data.spkId)
    def favData = state.savedFavorites[data.favDni]
    if (spk && favData) {
        if (favData.vol != null) spk.setLevel(favData.vol)
        spk.setTrack(favData.uri)
        runIn(2, triggerPlayOnFav, [data: [spkId: spk.id]])
        logAction("Routine Triggered: Playing favorite [${favData.name}] on ${spk.label}")
    }
}

def gracefulShutdownSpecificZones(zones) {
    def commandsSent = false
    zones.each { i ->
        if (settings["z${i}Speaker"] && settings["z${i}Switch"]) {
            if (settings["z${i}Switch"].currentValue("switch") == "on" && settings["z${i}Speaker"].currentValue("status") == "playing") {
                settings["z${i}Speaker"].pause()
                commandsSent = true
                logAction("Failsafe: Paused ${settings['z${i}Speaker'].label} prior to power cut.")
            }
        }
    }
    runIn(commandsSent ? 5 : 1, executePowerCutSpecificZones, [data: [zonesToCut: zones]])
}

def executePowerCutSpecificZones(data) {
    def zones = data.zonesToCut
    def delayMult = 0
    zones.each { i ->
        if (settings["z${i}Switch"]) {
            def delay = delayMult * 6 + 1
            runIn(delay, executeStaggeredPowerOff, [data: [zNum: i]])
            delayMult++
        }
    }
    logAction("Master power switches successfully scheduled to turn OFF (Staggered).")
}

def executeStaggeredPowerOff(data) {
    settings["z${data.zNum}Switch"]?.off()
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
    if (settings.enableCostTracker == false) return

    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    if (!state.runHistory[today]) state.runHistory[today] = [usedWh: 0.0, savedWh: 0.0]

    def fractionOfHour = 0.25 

    for (int i = 1; i <= 10; i++) {
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
    
    // Strict Pruning to prevent State Bloat (Fix #3)
    def keys = state.runHistory.keySet().sort().reverse()
    if (keys.size() > 7) {
        def keysToKeep = keys[0..6]
        state.runHistory = state.runHistory.findAll { it.key in keysToKeep }
    }
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
