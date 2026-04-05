/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a detailed meteorologist report combining live local weather station data with macro-forecast APIs. Features dynamic phrasing to prevent repetition.",
    category: "Weather",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Meteorologist Report</b>", install: true, uninstall: true) {
        
        section("<b>Live Weather & Forecast Dashboard</b>") {
            input "btnRefresh", "button", title: "🔄 Refresh API & Local Data"
            
            def reportStatus = state.lastReportTime ? "Last script generated at ${state.lastReportTime}" : "Waiting for initial data..."
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${reportStatus}</div>"

            if (localStation) {
                def lTemp = localStation.currentValue("temperature") ?: "--"
                def lHum = localStation.currentValue("humidity") ?: "--"
                def lWind = localStation.currentValue("windSpeed") ?: "--"
                
                def apiTemp = state.apiCurrentTemp ?: "--"
                def apparentTemp = state.apiApparentTemp ?: "--"
                def windGust = state.apiWindGust ?: "--"
                def apiCondition = state.apiConditionDesc ?: "Unknown"
                def pollenDisplay = state.pollenIndex ? "${state.pollenIndex} (${state.pollenCategory})" : "Disabled/NA"
                def moonDisplay = getMoonPhase()

                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: center !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                    .day-box { font-size: 12px; line-height: 1.4; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Local (Your Station)</th><th>Macro (API)</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Current Temp</td><td><b>${lTemp}°</b></td><td>${apiTemp}° (Feels: ${apparentTemp}°)</td></tr>
                        <tr><td class="dash-hl">Humidity / Wind</td><td><b>${lHum}% / ${lWind} mph</b></td><td>Gusts: ${windGust} mph</td></tr>
                        <tr><td class="dash-hl">Conditions</td><td colspan="2" class="dash-val"><b>${apiCondition}</b></td></tr>
                        <tr><td class="dash-hl">Pollen / Moon</td><td colspan="2" class="dash-val">🌸 ${pollenDisplay} | 🌔 ${moonDisplay}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                if (state.apiDailyDates && state.apiDailyDates.size() > 0) {
                    def forecastHTML = "<table class='dash-table'><thead><tr><td colspan='7' class='dash-subhead'>7-Day Outlook</td></tr><tr>"
                    state.apiDailyDates.each { dStr -> forecastHTML += "<th>${getDayOfWeek(dStr)}</th>" }
                    forecastHTML += "</tr></thead><tbody><tr>"
                    
                    state.apiDailyDates.eachWithIndex { dStr, i ->
                        def h = state.apiDailyHighs ? state.apiDailyHighs[i] : "--"
                        def l = state.apiDailyLows ? state.apiDailyLows[i] : "--"
                        def r = state.apiDailyRain ? state.apiDailyRain[i] : "0"
                        def c = state.apiDailyConditions ? state.apiDailyConditions[i]?.capitalize() : "Unknown"
                        forecastHTML += "<td class='day-box'><span style='color:#d9534f; font-weight:bold;'>H: ${h}°</span><br><span style='color:#007bff; font-weight:bold;'>L: ${l}°</span><br><span style='color:#17a2b8;'>💧 ${r}\"</span><br><i>${c}</i></td>"
                    }
                    forecastHTML += "</tr></tbody></table>"
                    paragraph forecastHTML
                }

                if (state.latestScript) {
                    paragraph "<b>📝 Latest Script (Dashboard Anchor):</b>"
                    paragraph "<div style='font-size: 14px; font-style: italic; background-color: #fdfd96; padding: 10px; border-radius: 5px; border: 1px solid #e1e182;'>\"${state.latestScript}\"</div>"
                }
            } else {
                paragraph "<i>Please select your personal weather station below to populate the dashboard.</i>"
            }
        }

        section(title: "<b>1. Data Sources & Settings</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> Connect your local weather sensor to provide accurate micro-climate data. Polling every 30 minutes is highly recommended.</div>"
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", required: true, multiple: false
            input "zipCode", "text", title: "Zip Code (Required for Pollen)", required: false
            input "pollingInterval", "enum", title: "Background Sync Interval", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
            input "reportPersona", "enum", title: "Select Default Dashboard Personality", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional", required: true
        }

        section(title: "<b>2. Morning Report Profile</b>", hideable: true, hidden: true) {
            input "morningStartTime", "time", title: "Morning Window Start", required: false
            input "morningEndTime", "time", title: "Morning Window End", required: false
            input "m_includeGreeting", "bool", title: "Include Greeting", defaultValue: true
            input "m_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "m_includeDaily", "bool", title: "Include Today's Highs/Lows/Conditions", defaultValue: true
            input "m_includeFeelsLike", "bool", title: "Include 'Feels Like' & Wind Gusts", defaultValue: true
            input "m_includeRain", "bool", title: "Include Expected Rain Amount", defaultValue: true
            input "m_includeUV", "bool", title: "Include Peak UV Index", defaultValue: true
            input "m_includePollen", "bool", title: "Include Pollen Forecast", defaultValue: false
            input "m_includeClothing", "bool", title: "Include Clothing Recommendation", defaultValue: false
            input "m_includeMoon", "bool", title: "Include Moon Phase", defaultValue: false
            input "m_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: false
        }
        
        section(title: "<b>3. Evening/Night Report Profile</b>", hideable: true, hidden: true) {
            input "eveningStartTime", "time", title: "Evening Window Start", required: false
            input "eveningEndTime", "time", title: "Evening Window End", required: false
            input "e_includeGreeting", "bool", title: "Include Greeting", defaultValue: true
            input "e_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "e_includeDaily", "bool", title: "Include Tonight's Low & Tomorrow's Forecast", defaultValue: true
            input "e_includeFeelsLike", "bool", title: "Include 'Feels Like' & Wind Gusts", defaultValue: false
            input "e_includeRain", "bool", title: "Include Tomorrow's Rain Amount", defaultValue: true
            input "e_includePollen", "bool", title: "Include Tomorrow's Pollen Forecast", defaultValue: false
            input "e_includeClothing", "bool", title: "Include Clothing Recommendation", defaultValue: false
            input "e_includeMoon", "bool", title: "Include Tonight's Moon Phase", defaultValue: true
            input "e_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: true
        }

        section(title: "<b>4. Informational Insights</b>", hideable: true, hidden: true) {
            input "infoHistorical", "bool", title: "Include Historical Context", defaultValue: false
            input "infoPlanning", "bool", title: "Include Dry Planning Windows", defaultValue: false
            input "infoStargazing", "bool", title: "Include Stargazing / Night Sky Conditions", defaultValue: false
        }

        section(title: "<b>5. Broadcast Triggers</b>", hideable: true, hidden: true) {
            paragraph "<b>🕒 Scheduled Time Triggers</b>"
            input "enableTime1", "bool", title: "Enable Time Trigger 1", submitOnChange: true, defaultValue: false
            if (enableTime1) {
                input "timeTrigger1", "time", title: "Time Trigger 1", required: true
                input "timeDays1", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "timeModes1", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "timeNotifyTarget1", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "timeAudio1", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
            }
            paragraph "<hr>"
            input "enableTime2", "bool", title: "Enable Time Trigger 2", submitOnChange: true, defaultValue: false
            if (enableTime2) {
                input "timeTrigger2", "time", title: "Time Trigger 2", required: true
                input "timeDays2", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "timeModes2", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "timeNotifyTarget2", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "timeAudio2", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
            }
            paragraph "<hr>"
            input "enableTime3", "bool", title: "Enable Time Trigger 3", submitOnChange: true, defaultValue: false
            if (enableTime3) {
                input "timeTrigger3", "time", title: "Time Trigger 3", required: true
                input "timeDays3", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "timeModes3", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "timeNotifyTarget3", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "timeAudio3", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
            }

            paragraph "<hr><b>🏠 Mode-Based Triggers</b>"
            input "enableMode1", "bool", title: "Enable Mode Trigger 1", submitOnChange: true, defaultValue: false
            if (enableMode1) {
                input "triggerMode1", "mode", title: "Mode Change To:", required: true, multiple: false
                input "t1StartTime", "time", title: "Allowable Start Time", required: false
                input "t1EndTime", "time", title: "Allowable End Time", required: false
                input "modeNotifyTarget1", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "modeAudio1", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
                input "modeDelay1", "number", title: "Delay Audio Broadcast (Seconds)", required: false, defaultValue: 0
            }
            paragraph "<hr>"
            input "enableMode2", "bool", title: "Enable Mode Trigger 2", submitOnChange: true, defaultValue: false
            if (enableMode2) {
                input "triggerMode2", "mode", title: "Mode Change To:", required: true, multiple: false
                input "t2StartTime", "time", title: "Allowable Start Time", required: false
                input "t2EndTime", "time", title: "Allowable End Time", required: false
                input "modeNotifyTarget2", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "modeAudio2", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
                input "modeDelay2", "number", title: "Delay Audio Broadcast (Seconds)", required: false, defaultValue: 0
            }

            paragraph "<hr><b>🔘 Manual Switch Triggers</b>"
            input "enableSwitch1", "bool", title: "Enable Switch Trigger 1", submitOnChange: true, defaultValue: false
            if (enableSwitch1) {
                input "triggerSwitch1", "capability.switch", title: "Switch Trigger 1", required: true, multiple: false
                input "sw1AutoOff", "bool", title: "Auto-turn off switch after 2 seconds?", defaultValue: false
                input "sw1StartTime", "time", title: "Allowable Start Time", required: false
                input "sw1EndTime", "time", title: "Allowable End Time", required: false
                input "sw1Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "sw1NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "sw1Audio", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: false
            }
            paragraph "<hr>"
            input "enableSwitch2", "bool", title: "Enable Switch Trigger 2", submitOnChange: true, defaultValue: false
            if (enableSwitch2) {
                input "triggerSwitch2", "capability.switch", title: "Switch Trigger 2", required: true, multiple: false
                input "sw2AutoOff", "bool", title: "Auto-turn off switch after 2 seconds?", defaultValue: false
                input "sw2StartTime", "time", title: "Allowable Start Time", required: false
                input "sw2EndTime", "time", title: "Allowable End Time", required: false
                input "sw2Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "sw2NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "sw2Audio", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: false
            }
            paragraph "<hr>"
            input "enableSwitch3", "bool", title: "Enable Switch Trigger 3", submitOnChange: true, defaultValue: false
            if (enableSwitch3) {
                input "triggerSwitch3", "capability.switch", title: "Switch Trigger 3", required: true, multiple: false
                input "sw3AutoOff", "bool", title: "Auto-turn off switch after 2 seconds?", defaultValue: false
                input "sw3StartTime", "time", title: "Allowable Start Time", required: false
                input "sw3EndTime", "time", title: "Allowable End Time", required: false
                input "sw3Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
                input "sw3NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
                input "sw3Audio", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: false
            }
        }

        section(title: "<b>6. Outputs & Advanced Devices</b>", hideable: true, hidden: true) {
            paragraph "<b>Audio Announcements</b>"
            input "ttsPersona", "enum", title: "TTS Audio Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            input "ttsSpeakers", "capability.speechSynthesis", title: "Standard TTS Speakers", required: false, multiple: true
            input "advAudio", "capability.audioVolume", title: "Advanced Speakers", required: false, multiple: true
            input "advAudioVol", "number", title: "Advanced Speaker Broadcast Volume (1-100)", required: false, range: "1..100"
            
            paragraph "<hr><b>Personalized Push Notifications (Up to 4 Profiles)</b>"
            input "notifyUser1", "capability.notification", title: "User 1 Device(s)", required: false, multiple: true
            input "personaUser1", "enum", title: "User 1 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            input "notifyUser2", "capability.notification", title: "User 2 Device(s)", required: false, multiple: true
            input "personaUser2", "enum", title: "User 2 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            input "notifyUser3", "capability.notification", title: "User 3 Device(s)", required: false, multiple: true
            input "personaUser3", "enum", title: "User 3 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            input "notifyUser4", "capability.notification", title: "User 4 Device(s)", required: false, multiple: true
            input "personaUser4", "enum", title: "User 4 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            
            paragraph "<hr><b>Visual Indicators</b>"
            input "visualIndicators", "capability.colorControl", title: "Visual Weather Indicators", required: false, multiple: true
            
            paragraph "<hr><b>System Actions</b>"
            input "btnTestTTS", "button", title: "🔊 Test Audio Broadcast"
            input "btnTestNotify", "button", title: "📱 Test Push Notifications (All Users)"
            input "btnCreateDevice", "button", title: "⚙️ Create & Sync Child Device"
        }

        section(title: "<b>7. Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "btnClearHistory", "button", title: "Clear History"
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    createChildDevice()
    if (!state.actionHistory) state.actionHistory = []
    
    subscribe(location, "mode", modeChangeHandler)
    
    if (enableSwitch1 && triggerSwitch1) subscribe(triggerSwitch1, "switch.on", switchTrigger1Handler)
    if (enableSwitch2 && triggerSwitch2) subscribe(triggerSwitch2, "switch.on", switchTrigger2Handler)
    if (enableSwitch3 && triggerSwitch3) subscribe(triggerSwitch3, "switch.on", switchTrigger3Handler)
    
    if (enableTime1 && timeTrigger1) schedule(timeTrigger1, timeTrigger1Handler)
    if (enableTime2 && timeTrigger2) schedule(timeTrigger2, timeTrigger2Handler)
    if (enableTime3 && timeTrigger3) schedule(timeTrigger3, timeTrigger3Handler)

    def interval = pollingInterval ?: "30"
    if (interval == "15") runEvery15Minutes(routineSync)
    else if (interval == "30") runEvery30Minutes(routineSync)
    else if (interval == "60") runEvery1Hour(routineSync)

    routineSync()
    logAction("App Initialized. Advanced Meteorologist Report Ready.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") { state.lastErrorTime = null; routineSync(); logAction("Data manually refreshed.") }
    else if (btn == "btnClearHistory") { state.actionHistory = []; log.info "History cleared." }
    else if (btn == "btnTestTTS") { executeTargetedBroadcast("No Push Notification", true); logAction("Test Audio Broadcast Triggered.") }
    else if (btn == "btnTestNotify") { executeTargetedBroadcast("All Profiles", false); logAction("Test Notify Triggered for All Users.") }
    else if (btn == "btnCreateDevice") { createChildDevice(); routineSync(); logAction("Child device created.") }
}

// ----------------- HELPER FUNCTIONS -----------------

def getRandomPhrase(List phrases) {
    if (!phrases) return ""
    return phrases[new Random().nextInt(phrases.size())]
}

def checkDays(daysList) {
    if (!daysList) return true 
    def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location.timeZone)
    def day = df.format(new Date())
    return daysList.contains(day)
}

def checkModes(modesList) {
    if (!modesList) return true
    return modesList.contains(location.mode)
}

def checkTime(start, end) {
    if (!start || !end) return true
    return timeOfDayIsBetween(toDateTime(start), toDateTime(end), new Date(), location.timeZone)
}

def hasInternet() {
    if (state.lastErrorTime) {
        long elapsed = (now() - state.lastErrorTime) / 60000
        if (elapsed < 15) return false
    }
    return true
}

// ----------------- CORE LOGIC -----------------

def routineSync() {
    if (!hasInternet()) {
        state.apiFailed = true
        generateScript() 
        updateChildDevice() 
        return 
    }
    
    state.apiFailed = false
    fetchApiData()
    if (infoHistorical) fetchHistoricalData()
    fetchPollenData()
    generateScript() 
    updateVisualIndicators()
    updateChildDevice()
    logAction("Routine Background Sync Complete.")
}

def executeTargetedBroadcast(target, playAudio = true) {
    routineSync() 
    
    if (playAudio) {
        def audioPersona = settings["ttsPersona"] ?: "Professional"
        def audioScript = generateScript(audioPersona)
        
        if (ttsSpeakers) ttsSpeakers.speak(audioScript)
        
        if (advAudio) {
            advAudio.each { speaker ->
                if (advAudioVol) {
                    if (speaker.hasCommand("playTextAndRestore")) speaker.playTextAndRestore(audioScript, advAudioVol)
                    else if (speaker.hasCommand("setVolumeSpeakAndRestore")) speaker.setVolumeSpeakAndRestore(advAudioVol, audioScript)
                    else speaker.speak(audioScript)
                } else {
                    speaker.speak(audioScript)
                }
            }
        }
    }
    
    if (target == "All Profiles") {
        sendUserPush(1); sendUserPush(2); sendUserPush(3); sendUserPush(4)
    } else if (target == "User 1") sendUserPush(1)
      else if (target == "User 2") sendUserPush(2)
      else if (target == "User 3") sendUserPush(3)
      else if (target == "User 4") sendUserPush(4)
}

def delayedAudioOnlyBroadcast() { executeTargetedBroadcast("No Push Notification", true) }

def sendUserPush(userNum) {
    def dev = settings["notifyUser${userNum}"]
    def persona = settings["personaUser${userNum}"] ?: "Professional"
    if (dev) {
        def customScript = generateScript(persona)
        sendSplitNotification(customScript, dev)
    }
}

def updateVisualIndicators() {
    if (!visualIndicators) return
    def rainAmt = (state.apiDailyRain && state.apiDailyRain.size() > 0) ? state.apiDailyRain[0] : 0
    def tHigh = (state.apiDailyHighs && state.apiDailyHighs.size() > 0) ? state.apiDailyHighs[0] : 70
    
    def hue = 33 
    if (rainAmt > 0.1) hue = 65 
    else if (tHigh >= 90) hue = 0 
    else if (tHigh <= 40) hue = 50 
    
    visualIndicators.each { dev -> if (dev.hasCommand("setColor")) dev.setColor([hue: hue, saturation: 100, level: 100]) }
}

def sendSplitNotification(text, devices) {
    if (!devices || !text) return
    def sentences = text.split(/(?<=[.?!])\s+/)
    def maxSentences = 3
    
    if (sentences.size() <= maxSentences) {
        devices.deviceNotification(text)
    } else {
        def parts = []
        for (int i = 0; i < sentences.size(); i += maxSentences) {
            int end = Math.min(i + maxSentences - 1, sentences.size() - 1)
            parts << sentences[i..end].join(" ")
        }
        parts.eachWithIndex { part, index -> devices.deviceNotification("Part ${index + 1}/${parts.size()}: ${part}") }
    }
}

// ----------------- EVENT HANDLERS -----------------

def timeTrigger1Handler() { 
    if (!checkDays(timeDays1)) return
    if (!checkModes(timeModes1)) return
    boolean doAudio = (timeAudio1 != null) ? timeAudio1 : true
    executeTargetedBroadcast(timeNotifyTarget1 ?: "All Profiles", doAudio) 
}

def timeTrigger2Handler() { 
    if (!checkDays(timeDays2)) return
    if (!checkModes(timeModes2)) return
    boolean doAudio = (timeAudio2 != null) ? timeAudio2 : true
    executeTargetedBroadcast(timeNotifyTarget2 ?: "All Profiles", doAudio) 
}

def timeTrigger3Handler() { 
    if (!checkDays(timeDays3)) return
    if (!checkModes(timeModes3)) return
    boolean doAudio = (timeAudio3 != null) ? timeAudio3 : true
    executeTargetedBroadcast(timeNotifyTarget3 ?: "All Profiles", doAudio) 
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    def nowTime = new Date()

    if (enableMode1 && triggerMode1 && newMode == triggerMode1) {
        if (!t1StartTime || !t1EndTime || timeOfDayIsBetween(toDateTime(t1StartTime), toDateTime(t1EndTime), nowTime, location.timeZone)) {
            boolean doAudio = (modeAudio1 != null) ? modeAudio1 : true
            int delay = modeDelay1 ?: 0
            def target = modeNotifyTarget1 ?: "All Profiles"
            
            if (delay > 0) {
                if (target != "No Push Notification") executeTargetedBroadcast(target, false)
                if (doAudio) runIn(delay, "delayedAudioOnlyBroadcast")
            } else {
                executeTargetedBroadcast(target, doAudio)
            }
        }
    }
    
    if (enableMode2 && triggerMode2 && newMode == triggerMode2) {
        if (!t2StartTime || !t2EndTime || timeOfDayIsBetween(toDateTime(t2StartTime), toDateTime(t2EndTime), nowTime, location.timeZone)) {
            boolean doAudio = (modeAudio2 != null) ? modeAudio2 : true
            int delay = modeDelay2 ?: 0
            def target = modeNotifyTarget2 ?: "All Profiles"
            
            if (delay > 0) {
                if (target != "No Push Notification") executeTargetedBroadcast(target, false)
                if (doAudio) runIn(delay, "delayedAudioOnlyBroadcast")
            } else {
                executeTargetedBroadcast(target, doAudio)
            }
        }
    }
}

def switchTrigger1Handler(evt) { handleSwitchTrigger(1, triggerSwitch1, sw1StartTime, sw1EndTime, sw1Modes, sw1NotifyTarget, sw1Audio, sw1AutoOff) }
def switchTrigger2Handler(evt) { handleSwitchTrigger(2, triggerSwitch2, sw2StartTime, sw2EndTime, sw2Modes, sw2NotifyTarget, sw2Audio, sw2AutoOff) }
def switchTrigger3Handler(evt) { handleSwitchTrigger(3, triggerSwitch3, sw3StartTime, sw3EndTime, sw3Modes, sw3NotifyTarget, sw3Audio, sw3AutoOff) }

def handleSwitchTrigger(num, sw, start, end, modes, target, playAudio, autoOff) {
    if (!checkTime(start, end)) return 
    if (!checkModes(modes)) return 
    
    boolean doAudio = (playAudio != null) ? playAudio : false
    executeTargetedBroadcast(target ?: "All Profiles", doAudio)
    if (autoOff) runIn(2, "turnOffSwitch${num}")
}

def turnOffSwitch1() { if (triggerSwitch1) triggerSwitch1.off() }
def turnOffSwitch2() { if (triggerSwitch2) triggerSwitch2.off() }
def turnOffSwitch3() { if (triggerSwitch3) triggerSwitch3.off() }

// ----------------- DATA FETCHING -----------------

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    if (!lat || !lon) { state.apiFailed = true; return }

    def url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,apparent_temperature,weather_code,wind_gusts_10m&hourly=precipitation,cloudcover&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,uv_index_max&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=auto"
    
    try {
        httpGet([uri: url, timeout: 10]) { resp ->
            if (resp.success) {
                state.lastErrorTime = null 
                def data = resp.data
                state.apiCurrentTemp = data.current?.temperature_2m?.toBigDecimal()?.setScale(0, BigDecimal.ROUND_HALF_UP)
                state.apiApparentTemp = data.current?.apparent_temperature?.toBigDecimal()?.setScale(0, BigDecimal.ROUND_HALF_UP)
                state.apiWindGust = data.current?.wind_gusts_10m?.toBigDecimal()?.setScale(0, BigDecimal.ROUND_HALF_UP)
                state.apiConditionDesc = getWeatherDescription(data.current?.weather_code ?: 0)
                
                state.apiDailyDates = data.daily?.time ?: []
                state.apiDailyHighs = data.daily?.temperature_2m_max?.collect { it.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP) } ?: []
                state.apiDailyLows = data.daily?.temperature_2m_min?.collect { it.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP) } ?: []
                state.apiDailyRain = data.daily?.precipitation_sum ?: []
                state.apiDailyUV = data.daily?.uv_index_max ?: []
                state.apiDailyConditions = data.daily?.weather_code?.collect { getWeatherDescription(it) } ?: []
                
                state.apiHourlyRain = data.hourly?.precipitation ?: []
                state.apiHourlyClouds = data.hourly?.cloudcover ?: []
            } else { 
                state.apiFailed = true; state.lastErrorTime = now() 
            }
        }
    } catch (e) { state.apiFailed = true; state.lastErrorTime = now() }
}

def fetchHistoricalData() {
    def lat = location.latitude
    def lon = location.longitude
    def dateLastYear = new Date() - 365
    def dateStr = dateLastYear.format("yyyy-MM-dd", location.timeZone)
    def url = "https://archive-api.open-meteo.com/v1/archive?latitude=${lat}&longitude=${lon}&start_date=${dateStr}&end_date=${dateStr}&daily=temperature_2m_max&temperature_unit=fahrenheit&timezone=auto"
    
    try {
        httpGet([uri: url, timeout: 8]) { resp ->
            if (resp.success && resp.data?.daily?.temperature_2m_max) {
                state.historyHigh = resp.data.daily.temperature_2m_max[0]?.toBigDecimal()?.setScale(0, BigDecimal.ROUND_HALF_UP)
            }
        }
    } catch (e) { state.historyHigh = null }
}

def fetchPollenData() {
    if (!zipCode) { state.pollenIndex = null; return }
    try {
        def params = [ uri: "https://www.pollen.com/api/forecast/current/pollen/${zipCode}", headers: ["Referer": "https://www.pollen.com", "Accept": "application/json"], timeout: 10 ]
        httpGet(params) { resp ->
            if (resp.status == 200 && resp.data?.Location?.periods) {
                def pData = resp.data.Location.periods.find { it.Type == "Today" } ?: resp.data.Location.periods[1]
                if (pData && pData.Index != null) {
                    state.pollenIndex = pData.Index
                    def idx = pData.Index.toFloat()
                    if (idx < 2.4) state.pollenCategory = "low"
                    else if (idx < 4.8) state.pollenCategory = "low to medium"
                    else if (idx < 7.2) state.pollenCategory = "medium"
                    else if (idx < 9.6) state.pollenCategory = "medium to high"
                    else state.pollenCategory = "high"
                }
            } 
        }
    } catch (e) { state.pollenIndex = null }
}

// ----------------- DYNAMIC SCRIPT GENERATOR -----------------

def generateScript(overridePersona = null) {
    def script = ""
    def nowTime = new Date()
    def persona = overridePersona ?: (reportPersona ?: "Professional")
    
    def isMorning = morningStartTime && morningEndTime && timeOfDayIsBetween(toDateTime(morningStartTime), toDateTime(morningEndTime), nowTime, location.timeZone)
    def isEvening = eveningStartTime && eveningEndTime && timeOfDayIsBetween(toDateTime(eveningStartTime), toDateTime(eveningEndTime), nowTime, location.timeZone)
    
    def useGreeting = true; def useMicro = true; def useDaily = true; def useWeekly = false
    def useRain = false; def usePollen = false; def useMoon = false; def useUV = false; def useFeels = false
    def useClothing = false; def activeProfileName = "Standard"
    
    if (isMorning) {
        useGreeting = m_includeGreeting; useMicro = m_includeMicro; useDaily = m_includeDaily; useWeekly = m_includeWeekly
        useRain = m_includeRain; usePollen = m_includePollen; useMoon = m_includeMoon; useUV = m_includeUV; useFeels = m_includeFeelsLike
        useClothing = m_includeClothing; activeProfileName = "Morning"
    } else if (isEvening) {
        useGreeting = e_includeGreeting; useMicro = e_includeMicro; useDaily = e_includeDaily; useWeekly = e_includeWeekly
        useRain = e_includeRain; usePollen = e_includePollen; useMoon = e_includeMoon; useFeels = e_includeFeelsLike
        useClothing = e_includeClothing; activeProfileName = "Evening"
    }
    
    if (state.apiFailed) {
        if (persona == "Technical") script += getRandomPhrase(["Warning: Primary weather API connection failed. ", "Error: Weather dataset is incomplete. "])
        else if (persona == "Disgruntled") script += getRandomPhrase(["Of course the weather service is down. ", "The weather API is broken, typical. "])
        else if (persona == "GenZ") script += getRandomPhrase(["Bruh, the weather API is lowkey buggin'. ", "Weather service is taking a massive L right now. "])
        else if (persona == "Casual") script += getRandomPhrase(["Oops, looks like my connection to the weather service is down right now. ", "I'm having trouble pulling the full forecast right now. "])
        else script += getRandomPhrase(["I currently cannot connect to the weather service. ", "Part of your report is missing due to a connection issue. "])
    }
    
    if (useGreeting) {
        def hour = nowTime.format("HH", location.timeZone).toInteger()
        def greetingWord = "Good evening"
        if (hour >= 4 && hour < 12) greetingWord = "Good morning"
        else if (hour >= 12 && hour < 17) greetingWord = "Good afternoon"
        
        if (persona == "Technical") script += getRandomPhrase(["System initialized. ${greetingWord}. ", "Telemetry active. ${greetingWord}. ", "Beginning atmospheric briefing. ${greetingWord}. "])
        else if (persona == "Disgruntled") script += getRandomPhrase(["Ugh, ${greetingWord.toLowerCase()}. Is it Friday yet? ", "Here we go again. ${greetingWord}. ", "Why am I awake? Anyway, ${greetingWord.toLowerCase()}. "])
        else if (persona == "GenZ") script += getRandomPhrase(["Yo chat, ${greetingWord.toLowerCase()}. Let's do a quick vibe check on the weather. ", "What's good. ${greetingWord}. ", "Woke up and chose weather. ${greetingWord}. "])
        else if (persona == "Casual") script += getRandomPhrase(["Hey there! ${greetingWord} to you! ", "Hi! ${greetingWord}! Let's check the weather. ", "Hope you're having a good day! ${greetingWord}. "])
        else script += getRandomPhrase(["${greetingWord}. ", "Here is your weather update. ${greetingWord}. ", "${greetingWord}. Let's look at the forecast. "])
    }
    
    if (useMicro && localStation) {
        def lTemp = localStation.currentValue("temperature") ?: "unknown"
        def lHum = localStation.currentValue("humidity") ?: "unknown"
        def lWind = localStation.currentValue("windSpeed")
        
        if (persona == "Technical") {
            script += getRandomPhrase([
                "Local sensor telemetry indicates an ambient temperature of ${lTemp} degrees Fahrenheit with ${lHum} percent relative humidity. ",
                "Current micro-climate data reads ${lTemp} degrees and ${lHum} percent humidity. ",
                "On-site thermal metrics show ${lTemp} degrees with a humidity index of ${lHum} percent. "
            ])
            if (lWind != null && lWind > 5) script += "Anemometer readings show localized wind speeds of ${lWind} miles per hour. "
        } else if (persona == "Casual") {
            script += getRandomPhrase([
                "Stepping right outside your door, it's sitting at ${lTemp} degrees, and humidity is around ${lHum} percent. ",
                "Right now, your porch sensor is reading ${lTemp} degrees with ${lHum} percent humidity. ",
                "Locally, it's currently ${lTemp} degrees out there with a humidity of ${lHum} percent. "
            ])
            if (lWind != null && lWind > 5) script += "We've got a nice little breeze out there at ${lWind} miles per hour. "
        } else {
            script += getRandomPhrase([
                "Right now at your house, it is currently ${lTemp} degrees with a humidity of ${lHum} percent. ",
                "Your local sensor indicates it is ${lTemp} degrees and ${lHum} percent humidity. ",
                "Outside your door, the temperature is ${lTemp} degrees with ${lHum} percent humidity. "
            ])
            if (lWind != null && lWind > 5) script += "Winds are currently blowing at ${lWind} miles per hour. "
        }
    }
    
    def safeGet = { list, index, defaultVal -> (list && list.size() > index) ? list[index] : defaultVal }
    
    if (useDaily && state.apiDailyDates && state.apiDailyDates.size() > 1) {
        def tIndex = activeProfileName == "Evening" ? 1 : 0 
        def dayContext = activeProfileName == "Evening" ? "Tomorrow" : "Today"
        
        def cond = safeGet(state.apiDailyConditions, tIndex, "varying conditions")
        def tHigh = safeGet(state.apiDailyHighs, tIndex, "unknown")
        def tLow = safeGet(state.apiDailyLows, 0, "unknown") 
        def rainAmt = safeGet(state.apiDailyRain, tIndex, 0)
        def uvIndex = safeGet(state.apiDailyUV, tIndex, 0)

        if (activeProfileName == "Evening") {
            if (persona == "Casual") {
                script += getRandomPhrase([
                    "Overnight, we'll cool down to around ${tLow}. Looking ahead to tomorrow, it's shaping up to be ${cond} with temperatures topping out near ${tHigh}. ",
                    "Expect temperatures to drop to ${tLow} tonight. For tomorrow, we are looking at ${cond} and a high of ${tHigh}. ",
                    "Tonight's low will hit ${tLow}. Tomorrow's outlook calls for ${cond} with a high around ${tHigh}. "
                ])
            } else {
                script += getRandomPhrase([
                    "Overnight, expect temperatures to drop to a low of ${tLow}. Looking ahead to tomorrow, we will see ${cond} with a high of ${tHigh}. ",
                    "Tonight will see a low of ${tLow}. Tomorrow's forecast indicates ${cond} and a high of ${tHigh}. "
                ])
            }
        } else {
            if (persona == "Casual") {
                script += getRandomPhrase([
                    "For the rest of the day, it's looking like ${cond}. We're aiming for a high of ${tHigh}, and cooling down to ${tLow} later tonight. ",
                    "Today's overall forecast calls for ${cond} with temperatures reaching ${tHigh}. Tonight, we'll drop to ${tLow}. ",
                    "Moving to the big picture, expect ${cond} today with a peak of ${tHigh}. Tonight's low will be ${tLow}. "
                ])
            } else {
                script += getRandomPhrase([
                    "Looking at the broader forecast, expect ${cond} today. We will reach a high of ${tHigh}, and drop to a low of ${tLow} tonight. ",
                    "Today's macro forecast indicates ${cond} and a high of ${tHigh}. The overnight low will be ${tLow}. "
                ])
            }
        }
        
        if (infoHistorical && state.historyHigh != null && tHigh != "unknown" && activeProfileName != "Evening") {
            int diff = tHigh.toInteger() - state.historyHigh.toInteger()
            if (Math.abs(diff) >= 3) {
                def dir = diff > 0 ? "warmer" : "colder"
                script += getRandomPhrase([
                    "Fun fact! Today's high is ${Math.abs(diff)} degrees ${dir} than this exact date last year. ",
                    "Looking at historical data, today is actually ${Math.abs(diff)} degrees ${dir} compared to this time last year. "
                ])
            }
        }
        
        if (useClothing && tHigh != "unknown") {
            def tempInt = tHigh.toInteger()
            if (tempInt < 40) script += getRandomPhrase(["It's going to be freezing out there, so definitely grab a heavy coat. ", "Bundle up, a winter coat is necessary today. "])
            else if (tempInt < 60) script += getRandomPhrase(["It's a bit chilly, so a light jacket or sweater is recommended. ", "You might want to grab a sweater or light jacket before heading out. "])
            else if (tempInt < 80) script += getRandomPhrase(["It's very comfortable out, short sleeves should be perfectly fine. ", "The temperature is pleasant, a t-shirt will do just fine. "])
            else script += getRandomPhrase(["It's going to be hot, so dress lightly to stay cool. ", "With the heat today, short sleeves and light clothing are recommended. "])
        }
        
        if (useFeels && state.apiApparentTemp && state.apiCurrentTemp) {
            if (Math.abs(state.apiCurrentTemp - state.apiApparentTemp) >= 4) {
                script += getRandomPhrase([
                    "Just a heads up though, it actually feels closer to ${state.apiApparentTemp} out there. ",
                    "Factoring in the conditions, it feels more like ${state.apiApparentTemp}. "
                ])
            }
        }
        
        if (useRain && rainAmt > 0) {
            script += getRandomPhrase([
                "Looks like we'll be getting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}. ",
                "Expect roughly ${rainAmt} inches of precipitation ${dayContext.toLowerCase()}. ",
                "Keep an umbrella handy, we are tracking ${rainAmt} inches of rain ${dayContext.toLowerCase()}. "
            ])
            
            if (infoPlanning && activeProfileName != "Evening") {
                def currentHourIndex = new Date().format("HH", location.timeZone).toInteger()
                def dryStartHour = -1
                def maxDryLength = 0
                def currentDryLength = 0
                def currentDryStart = -1

                if (state.apiHourlyRain && state.apiHourlyRain.size() >= 24) {
                    for (int i = currentHourIndex; i <= 23; i++) {
                        if (state.apiHourlyRain[i] == 0) {
                            if (currentDryLength == 0) currentDryStart = i
                            currentDryLength++
                        } else {
                            if (currentDryLength > maxDryLength) {
                                maxDryLength = currentDryLength
                                dryStartHour = currentDryStart
                            }
                            currentDryLength = 0
                        }
                    }
                    if (currentDryLength > maxDryLength) {
                        maxDryLength = currentDryLength
                        dryStartHour = currentDryStart
                    }
                }
                
                if (maxDryLength >= 3 && dryStartHour != -1) {
                    def displayHour = dryStartHour > 12 ? "${dryStartHour - 12} PM" : (dryStartHour == 12 ? "12 PM" : "${dryStartHour} AM")
                    script += getRandomPhrase([
                        "Even with the rain, it looks like you'll have a nice dry window of about ${maxDryLength} hours starting around ${displayHour}. ",
                        "If you need to plan outdoor activities, our models show a dry gap of approximately ${maxDryLength} hours beginning at ${displayHour}. "
                    ])
                }
            }
        }
        
        if (useUV && uvIndex > 5) script += getRandomPhrase(["The UV index will peak at ${uvIndex}, so be sure to wear sunscreen. ", "Sun intensity is high today with a UV index of ${uvIndex}. "])
    }
    
    if (infoStargazing && state.apiHourlyClouds) {
        def eveningClouds = 0; def count = 0
        for (int i = 20; i <= 23; i++) { if(state.apiHourlyClouds.size() > i) { eveningClouds += state.apiHourlyClouds[i]; count++ } }
        def avgClouds = count > 0 ? (eveningClouds / count) : 100
        
        if (avgClouds < 20) script += getRandomPhrase(["With barely any clouds tonight, it's going to be a perfect evening to step outside and do some stargazing! ", "Expect minimal cloud cover this evening, creating excellent conditions for viewing the night sky. "])
    }
    
    if (usePollen && state.pollenIndex) script += getRandomPhrase(["The pollen count is currently ${state.pollenIndex}, which is considered ${state.pollenCategory}. ", "For allergy trackers, pollen levels are sitting at ${state.pollenIndex} today. "])
    
    if (useMoon) script += getRandomPhrase(["Tonight's moon phase will be a ${getMoonPhase()}. ", "If you look up tonight, you'll see a ${getMoonPhase()}. "])
    
    if (useWeekly && state.apiDailyDates && state.apiDailyDates.size() >= 5) {
        def day4Name = getDayOfWeek(state.apiDailyDates[3])
        def day4High = safeGet(state.apiDailyHighs, 3, "unknown")
        def day4Cond = safeGet(state.apiDailyConditions, 3, "varying conditions")
        script += getRandomPhrase([
            "Peeking ahead at the rest of the week, ${day4Name} is looking ${day4Cond} with a high around ${day4High}. ",
            "Looking forward, expect ${day4Cond} and temperatures near ${day4High} on ${day4Name}. "
        ])
    }

    if (!overridePersona) {
        state.latestScript = script
        state.lastReportTime = nowTime.format("MM/dd hh:mm a", location.timeZone)
    }
    
    return script
}

def getMoonPhase() {
    def date = new Date()
    int year = date.format("yyyy").toInteger(); int month = date.format("MM").toInteger(); int day = date.format("dd").toInteger()
    if (month < 3) { year--; month += 12 }
    ++month
    int c = 365.25 * year; int e = 30.6 * month
    double jd = c + e + day - 694039.09; jd /= 29.5305882 
    int b = Math.round((jd - Math.floor(jd)) * 8)
    if (b >= 8) b = 0
    return ["New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous", "Full Moon", "Waning Gibbous", "Third Quarter", "Waning Crescent"][b]
}

def getWeatherDescription(code) {
    def map = [ 0: "clear skies", 1: "mostly clear skies", 2: "partly cloudy skies", 3: "overcast conditions", 45: "foggy conditions", 48: "depositing rime fog", 51: "light drizzle", 53: "moderate drizzle", 55: "dense drizzle", 61: "slight rain", 63: "moderate rain", 65: "heavy rain", 71: "slight snow fall", 73: "moderate snow fall", 75: "heavy snow fall", 77: "snow grains", 80: "light rain showers", 81: "moderate rain showers", 82: "violent rain showers", 95: "thunderstorms", 96: "thunderstorms with slight hail", 99: "thunderstorms with heavy hail" ]
    return map[code as Integer] ?: "variable conditions"
}

def getDayOfWeek(dateString) { try { return Date.parse("yyyy-MM-dd", dateString).format("EEE") } catch (e) { return "N/A" } }

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def createChildDevice() {
    def childNetworkId = "MeteorologistReport_${app.id}"
    def child = getChildDevice(childNetworkId)
    if (!child) {
        log.info "Creating Meteorologist Report Child Device..."
        try { addChildDevice("ShaneAllen", "Advanced Meteorologist Report Device", childNetworkId, [name: "Advanced Meteorologist Report", isComponent: true]) } 
        catch (e) { logAction("ERROR: Failed to create child device. Ensure Driver is installed.") }
    }
}

def updateChildDevice() {
    def childNetworkId = "MeteorologistReport_${app.id}"
    def child = getChildDevice(childNetworkId)
    if (child) {
        child.updateTile([
            script: state.latestScript ?: "Awaiting data...",
            currentTemp: state.apiCurrentTemp,
            currentConditions: state.apiConditionDesc,
            dates: state.apiDailyDates ?: [],
            highs: state.apiDailyHighs ?: [],
            lows: state.apiDailyLows ?: [],
            conditions: state.apiDailyConditions ?: [],
            rain: state.apiDailyRain ?: [],
            uv: state.apiDailyUV ?: [],
            pollen: state.pollenIndex ? "${state.pollenIndex} (${state.pollenCategory})" : "N/A",
            moon: getMoonPhase()
        ])
    }
}
