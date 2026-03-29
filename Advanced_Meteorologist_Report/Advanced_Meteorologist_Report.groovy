/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a detailed meteorologist report combining live local weather station data with macro-forecast APIs. Features 5 distinct broadcasting personas, historical context, dry planning windows, and stargazing conditions.",
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
                    paragraph "<b>📝 Latest Script (Default Anchor):</b>"
                    paragraph "<div style='font-size: 14px; font-style: italic; background-color: #fdfd96; padding: 10px; border-radius: 5px; border: 1px solid #e1e182;'>\"${state.latestScript}\"</div>"
                }
            } else {
                paragraph "<i>Please select your personal weather station below to populate the dashboard.</i>"
            }
        }

        section(title: "<b>1. Data Sources & Settings</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> Connect your local weather sensor to provide accurate micro-climate data (what's actually happening on your porch). The app handles the rest by fetching macro-forecasts from the free Open-Meteo API. <b>Polling every 30 minutes</b> is highly recommended to keep data fresh without overloading the API.</div>"
            
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", description: "Your local outdoor temperature, humidity, and wind sensor.", required: true, multiple: false
            input "zipCode", "text", title: "Zip Code (Required for Pollen)", description: "Enter a valid US Zip Code to enable daily pollen index forecasting.", required: false
            input "pollingInterval", "enum", title: "Background Sync Interval", description: "How often the app fetches new data.", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
            input "reportPersona", "enum", title: "Select Default Anchor Personality", description: "This controls the tone/attitude of the default weather report sent to standard audio speakers.", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional", required: true
        }

        section(title: "<b>2. Morning Report Profile</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> This profile defines what data is included in reports generated during the morning. <b>Less is usually more with TTS audio.</b> Try limiting this to just Temp, Rain, and Clothing recommendations so the broadcast is punchy and useful.</div>"
            
            input "morningStartTime", "time", title: "Morning Window Start", description: "E.g., 5:00 AM", required: false
            input "morningEndTime", "time", title: "Morning Window End", description: "E.g., 11:00 AM", required: false
            input "m_includeGreeting", "bool", title: "Include Greeting", description: "Starts with 'Good Morning'.", defaultValue: true
            input "m_includeMicro", "bool", title: "Include Local Station Data", description: "Announces the exact temperature right outside your door.", defaultValue: true
            input "m_includeDaily", "bool", title: "Include Today's Highs/Lows/Conditions", description: "The broad forecast for the day ahead.", defaultValue: true
            input "m_includeFeelsLike", "bool", title: "Include 'Feels Like' & Wind Gusts", description: "Only speaks if wind gusts are high or the heat index significantly differs from the actual temp.", defaultValue: true
            input "m_includeRain", "bool", title: "Include Expected Rain Amount", description: "Announces total expected accumulation.", defaultValue: true
            input "m_includeUV", "bool", title: "Include Peak UV Index", description: "Warns you to wear sunscreen if UV > 5.", defaultValue: true
            input "m_includePollen", "bool", title: "Include Pollen Forecast", defaultValue: false
            input "m_includeClothing", "bool", title: "Include Clothing Recommendation", description: "Suggests a coat, sweater, or short sleeves based on the high.", defaultValue: false
            input "m_includeMoon", "bool", title: "Include Moon Phase", defaultValue: false
            input "m_includeWeekly", "bool", title: "Include Upcoming Week Teaser", description: "Gives a brief teaser of the weather 3-4 days out.", defaultValue: false
        }
        
        section(title: "<b>3. Evening/Night Report Profile</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> This profile takes over in the evening. It automatically shifts the phrasing to focus on <i>Tomorrow's</i> weather rather than today's. Dial this in for your 'Goodnight' routines.</div>"
            
            input "eveningStartTime", "time", title: "Evening Window Start", description: "E.g., 6:00 PM", required: false
            input "eveningEndTime", "time", title: "Evening Window End", description: "E.g., 11:59 PM", required: false
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
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> These toggle dynamic 'smart' features. The script will only include them if the conditions are met (e.g., it only talks about dry windows if it's going to rain, keeping the script brief on sunny days).</div>"
            
            input "infoHistorical", "bool", title: "Include Historical Context", description: "Compares today's high to the exact same date last year (e.g., 'It is 5 degrees warmer than this day last year').", defaultValue: false
            input "infoPlanning", "bool", title: "Include Dry Planning Windows", description: "If rain is expected today, scans the hourly forecast to find a block of 3+ dry hours for outdoor planning.", defaultValue: false
            input "infoStargazing", "bool", title: "Include Stargazing / Night Sky Conditions", description: "Checks cloud cover between 8 PM and midnight. Alerts you if skies are perfectly clear.", defaultValue: false
        }

        section(title: "<b>5. Broadcast Triggers</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> Choose how reports are generated. <b>Manual Virtual Switches are highly recommended.</b> You can create a virtual switch in Hubitat, select it here, and then use Hubitat's Rule Machine to turn that switch on when a motion sensor triggers in the bathroom, or when a bedside button is pressed.</div>"
            
            paragraph "<b>🕒 Scheduled Time Triggers</b>"
            input "timeTrigger1", "time", title: "Time Trigger 1", required: false
            input "timeDays1", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
            input "timeModes1", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "timeNotifyTarget1", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "timeAudio1", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
            
            paragraph "<hr>"
            input "timeTrigger2", "time", title: "Time Trigger 2", required: false
            input "timeDays2", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
            input "timeModes2", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "timeNotifyTarget2", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "timeAudio2", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true
            
            paragraph "<hr>"
            input "timeTrigger3", "time", title: "Time Trigger 3", required: false
            input "timeDays3", "enum", title: "Days to run", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
            input "timeModes3", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "timeNotifyTarget3", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "timeAudio3", "bool", title: "Play Audio Broadcast (TTS)?", defaultValue: true

            paragraph "<hr><b>🏠 Mode-Based Triggers</b>"
            input "triggerMode1", "mode", title: "Mode Change To:", description: "E.g., Trigger when mode changes to 'Awake'.", required: false, multiple: false
            input "t1StartTime", "time", title: "Allowable Start Time", required: false
            input "t1EndTime", "time", title: "Allowable End Time", required: false
            
            input "triggerMode2", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t2StartTime", "time", title: "Allowable Start Time", required: false
            input "t2EndTime", "time", title: "Allowable End Time", required: false
            
            paragraph "<hr><b>🔘 Manual Switch Triggers</b>"
            input "triggerSwitch1", "capability.switch", title: "Switch Trigger 1", description: "Turns off automatically after 2 seconds.", required: false, multiple: false
            input "sw1StartTime", "time", title: "Allowable Start Time", required: false
            input "sw1EndTime", "time", title: "Allowable End Time", required: false
            input "sw1Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "sw1NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "sw1Audio", "bool", title: "Play Audio Broadcast (TTS)?", description: "Turn this OFF if you want a silent push notification so you don't wake the house.", defaultValue: false
            
            paragraph "<hr>"
            input "triggerSwitch2", "capability.switch", title: "Switch Trigger 2", required: false, multiple: false
            input "sw2StartTime", "time", title: "Allowable Start Time", required: false
            input "sw2EndTime", "time", title: "Allowable End Time", required: false
            input "sw2Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "sw2NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "sw2Audio", "bool", title: "Play Audio Broadcast (TTS)?", description: "Turn this OFF if you want a silent push notification so you don't wake the house.", defaultValue: false
            
            paragraph "<hr>"
            input "triggerSwitch3", "capability.switch", title: "Switch Trigger 3", required: false, multiple: false
            input "sw3StartTime", "time", title: "Allowable Start Time", required: false
            input "sw3EndTime", "time", title: "Allowable End Time", required: false
            input "sw3Modes", "mode", title: "Only when in Mode(s)", multiple: true, required: false
            input "sw3NotifyTarget", "enum", title: "Who gets the Push Notification?", options: ["User 1", "User 2", "User 3", "User 4", "All Profiles", "No Push Notification"], defaultValue: "All Profiles"
            input "sw3Audio", "bool", title: "Play Audio Broadcast (TTS)?", description: "Turn this OFF if you want a silent push notification so you don't wake the house.", defaultValue: false
        }

        section(title: "<b>6. Outputs & Advanced Devices</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> Audio broadcasts will always use the default 'Anchor Persona' chosen in Section 1. However, you can assign different, personalized personas to individual family members' push notification devices here.</div>"
            
            paragraph "<b>Audio Announcements (Default Persona)</b>"
            input "ttsSpeakers", "capability.speechSynthesis", title: "Standard TTS Speakers", description: "Standard devices like Echo Dots or Google Homes.", required: false, multiple: true
            input "advAudio", "capability.audioVolume", title: "Advanced Speakers", description: "Speakers that support volume restoration (like Sonos).", required: false, multiple: true
            input "advAudioVol", "number", title: "Advanced Speaker Broadcast Volume (1-100)", required: false, range: "1..100"
            
            paragraph "<hr><b>Personalized Push Notifications (Up to 4 Profiles)</b>"
            input "notifyUser1", "capability.notification", title: "User 1 Device(s)", description: "Select the Hubitat mobile app device for User 1.", required: false, multiple: true
            input "personaUser1", "enum", title: "User 1 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            
            input "notifyUser2", "capability.notification", title: "User 2 Device(s)", required: false, multiple: true
            input "personaUser2", "enum", title: "User 2 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            
            input "notifyUser3", "capability.notification", title: "User 3 Device(s)", required: false, multiple: true
            input "personaUser3", "enum", title: "User 3 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            
            input "notifyUser4", "capability.notification", title: "User 4 Device(s)", required: false, multiple: true
            input "personaUser4", "enum", title: "User 4 Persona", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional"
            
            paragraph "<hr><b>Visual Indicators</b>"
            input "visualIndicators", "capability.colorControl", title: "Visual Weather Indicators", description: "Select RGB smart bulbs/switches. They will turn Blue for rain, Red for extreme heat, Cyan for freezing cold, or Green for moderate weather.", required: false, multiple: true
            
            paragraph "<hr><b>System Actions</b>"
            input "btnTestTTS", "button", title: "🔊 Test Audio Broadcast"
            input "btnTestNotify", "button", title: "📱 Test Push Notifications (All Users)"
            input "btnCreateDevice", "button", title: "⚙️ Create & Sync Child Device"
        }

        section(title: "<b>7. Recent Action History</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:12px; color:#555;'><b>Setup Tip:</b> Review this log if your triggers aren't firing to see if the network failed or a mode constraint blocked it.</div>"
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
    
    if (triggerSwitch1) subscribe(triggerSwitch1, "switch.on", switchTrigger1Handler)
    if (triggerSwitch2) subscribe(triggerSwitch2, "switch.on", switchTrigger2Handler)
    if (triggerSwitch3) subscribe(triggerSwitch3, "switch.on", switchTrigger3Handler)
    
    if (timeTrigger1) schedule(timeTrigger1, timeTrigger1Handler)
    if (timeTrigger2) schedule(timeTrigger2, timeTrigger2Handler)
    if (timeTrigger3) schedule(timeTrigger3, timeTrigger3Handler)

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
        if (elapsed < 15) { 
            log.warn "Advanced Meteorologist: Network in 15-min cooldown."
            return false
        }
    }
    return true
}

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
        def defaultScript = state.latestScript 
        if (ttsSpeakers) ttsSpeakers.speak(defaultScript)
        
        if (advAudio) {
            advAudio.each { speaker ->
                if (advAudioVol) {
                    if (speaker.hasCommand("playTextAndRestore")) speaker.playTextAndRestore(defaultScript, advAudioVol)
                    else if (speaker.hasCommand("setVolumeSpeakAndRestore")) speaker.setVolumeSpeakAndRestore(advAudioVol, defaultScript)
                    else speaker.speak(defaultScript)
                } else {
                    speaker.speak(defaultScript)
                }
            }
        }
    }
    
    if (target == "All Profiles") {
        sendUserPush(1); sendUserPush(2); sendUserPush(3); sendUserPush(4)
    } else if (target == "User 1") {
        sendUserPush(1)
    } else if (target == "User 2") {
        sendUserPush(2)
    } else if (target == "User 3") {
        sendUserPush(3)
    } else if (target == "User 4") {
        sendUserPush(4)
    }
}

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
    if (sentences.size() <= 3) {
        devices.deviceNotification(text)
    } else {
        def mid = Math.ceil(sentences.size() / 2).toInteger()
        def part1 = sentences[0..mid-1].join(" ")
        def part2 = sentences[mid..-1].join(" ")
        devices.deviceNotification("Weather Part 1: " + part1)
        devices.deviceNotification("Weather Part 2: " + part2)
    }
}

def timeTrigger1Handler() { 
    if (!checkDays(timeDays1)) return
    if (!checkModes(timeModes1)) return
    logAction("Time Trigger 1 activated.")
    boolean doAudio = (timeAudio1 != null) ? timeAudio1 : true
    executeTargetedBroadcast(timeNotifyTarget1 ?: "All Profiles", doAudio) 
}

def timeTrigger2Handler() { 
    if (!checkDays(timeDays2)) return
    if (!checkModes(timeModes2)) return
    logAction("Time Trigger 2 activated.")
    boolean doAudio = (timeAudio2 != null) ? timeAudio2 : true
    executeTargetedBroadcast(timeNotifyTarget2 ?: "All Profiles", doAudio) 
}

def timeTrigger3Handler() { 
    if (!checkDays(timeDays3)) return
    if (!checkModes(timeModes3)) return
    logAction("Time Trigger 3 activated.")
    boolean doAudio = (timeAudio3 != null) ? timeAudio3 : true
    executeTargetedBroadcast(timeNotifyTarget3 ?: "All Profiles", doAudio) 
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    def nowTime = new Date()
    def shouldTrigger = false
    
    if (triggerMode1 && newMode == triggerMode1) {
        if (!t1StartTime || !t1EndTime || timeOfDayIsBetween(toDateTime(t1StartTime), toDateTime(t1EndTime), nowTime, location.timeZone)) shouldTrigger = true
    }
    else if (triggerMode2 && newMode == triggerMode2) {
        if (!t2StartTime || !t2EndTime || timeOfDayIsBetween(toDateTime(t2StartTime), toDateTime(t2EndTime), nowTime, location.timeZone)) shouldTrigger = true
    }
    
    if (shouldTrigger) {
        logAction("Valid mode change detected. Broadcasting.")
        executeTargetedBroadcast("All Profiles", true)
    }
}

def switchTrigger1Handler(evt) { handleSwitchTrigger(1, triggerSwitch1, sw1StartTime, sw1EndTime, sw1Modes, sw1NotifyTarget, sw1Audio) }
def switchTrigger2Handler(evt) { handleSwitchTrigger(2, triggerSwitch2, sw2StartTime, sw2EndTime, sw2Modes, sw2NotifyTarget, sw2Audio) }
def switchTrigger3Handler(evt) { handleSwitchTrigger(3, triggerSwitch3, sw3StartTime, sw3EndTime, sw3Modes, sw3NotifyTarget, sw3Audio) }

def handleSwitchTrigger(num, sw, start, end, modes, target, playAudio) {
    logAction("Virtual Switch ${num} triggered.")
    if (!checkTime(start, end)) { logAction("Trigger ignored: Outside allowed time window."); return }
    if (!checkModes(modes)) { logAction("Trigger ignored: Not in allowed mode."); return }
    
    boolean doAudio = (playAudio != null) ? playAudio : false
    executeTargetedBroadcast(target ?: "All Profiles", doAudio)
    runIn(2, "turnOffSwitch${num}")
}

def turnOffSwitch1() { if (triggerSwitch1) triggerSwitch1.off() }
def turnOffSwitch2() { if (triggerSwitch2) triggerSwitch2.off() }
def turnOffSwitch3() { if (triggerSwitch3) triggerSwitch3.off() }

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    if (!lat || !lon) { logAction("ERROR: Hub Lat/Lon missing."); state.apiFailed = true; return }

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
                state.apiFailed = true
                state.lastErrorTime = now() 
            }
        }
    } catch (e) {
        logAction("ERROR fetching weather API: ${e.message}")
        state.apiFailed = true; state.lastErrorTime = now()
    }
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
    } catch (e) { logAction("Failed to fetch historical data: ${e.message}"); state.historyHigh = null }
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
        if (persona == "Technical") script += "Warning: Primary weather API connection failed. Reverting to cached or partial data sets. "
        else if (persona == "Disgruntled") script += "Of course the weather service is down. Why would anything work today? "
        else if (persona == "GenZ") script += "Bruh, the weather API is lowkey buggin' right now, so this report is an L. "
        else if (persona == "Casual") script += "Oops, looks like my connection to the weather service is down right now, so I only have part of your report. "
        else script += "I currently cannot connect to the weather service, so part of your report is missing. "
    }
    
    if (useGreeting) {
        def hour = nowTime.format("HH", location.timeZone).toInteger()
        def greetingWord = "Good evening"
        if (hour >= 4 && hour < 12) greetingWord = "Good morning"
        else if (hour >= 12 && hour < 17) greetingWord = "Good afternoon"
        
        if (persona == "Technical") script += "System initialized. ${greetingWord}. "
        else if (persona == "Disgruntled") script += "Ugh, ${greetingWord.toLowerCase()}. Is it Friday yet? "
        else if (persona == "GenZ") script += "Yo chat, ${greetingWord.toLowerCase()}. Let's do a quick vibe check on the weather. "
        else if (persona == "Casual") script += "Hey there! ${greetingWord} to you! "
        else script += "${greetingWord}. "
    }
    
    if (useMicro && localStation) {
        def lTemp = localStation.currentValue("temperature") ?: "unknown"
        def lHum = localStation.currentValue("humidity") ?: "unknown"
        def lWind = localStation.currentValue("windSpeed")
        
        if (persona == "Technical") {
            script += "Local sensor telemetry indicates an ambient temperature of ${lTemp} degrees Fahrenheit with ${lHum} percent relative humidity. "
            if (lWind != null && lWind > 5) script += "Anemometer readings show localized wind speeds of ${lWind} miles per hour. "
        } else if (persona == "Disgruntled") {
            script += "Right outside, where I'd rather be, it's ${lTemp} degrees with ${lHum} percent humidity. "
            if (lWind != null && lWind > 5) script += "And the wind is blowing at ${lWind} mph. Fantastic. "
        } else if (persona == "GenZ") {
            script += "Right outside your door, it's giving ${lTemp} degrees and ${lHum} percent humidity. "
            if (lWind != null && lWind > 5) script += "We got some wind too, pushing ${lWind} mph. "
        } else if (persona == "Casual") {
            script += "Stepping right outside your door, it's sitting at ${lTemp} degrees, and humidity is around ${lHum} percent. "
            if (lWind != null && lWind > 5) script += "We've got a nice little breeze out there at ${lWind} miles per hour. "
        } else {
            script += "Right now at your house, it is currently ${lTemp} degrees with a humidity of ${lHum} percent. "
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
            if (persona == "Technical") script += "Nocturnal models show thermal lows dropping to ${tLow}. Forward projections for tomorrow indicate ${cond} with a thermal peak of ${tHigh}. "
            else if (persona == "Disgruntled") script += "Overnight it drops to ${tLow}. Tomorrow is just another workday with ${cond} and a high of ${tHigh}. "
            else if (persona == "GenZ") script += "Tonight we're dropping to ${tLow}. Tomorrow's looking like ${cond} with a high of ${tHigh}, totally valid. "
            else if (persona == "Casual") script += "Overnight, we'll cool down to around ${tLow}. Looking ahead to tomorrow, it's shaping up to be ${cond} with temperatures topping out near ${tHigh}. "
            else script += "Overnight, expect temperatures to drop to a low of ${tLow}. Looking ahead to tomorrow, we will see ${cond} with a high of ${tHigh}. "
        } else {
            if (persona == "Technical") script += "Macro-forecast models for today indicate ${cond}. Thermal highs will peak at ${tHigh}, before descending to a nocturnal minimum of ${tLow}. "
            else if (persona == "Disgruntled") script += "Today brings ${cond} and a high of ${tHigh}. Tonight it drops to ${tLow}. Let's just get this over with. "
            else if (persona == "GenZ") script += "Today's main character energy is ${cond} with a high of ${tHigh}. Dropping to ${tLow} tonight. "
            else if (persona == "Casual") script += "For the rest of the day, it's looking like ${cond}. We're aiming for a high of ${tHigh}, and cooling down to ${tLow} later tonight. "
            else script += "Looking at the broader forecast, expect ${cond} today. We will reach a high of ${tHigh}, and drop to a low of ${tLow} tonight. "
        }
        
        if (infoHistorical && state.historyHigh != null && tHigh != "unknown" && activeProfileName != "Evening") {
            int diff = tHigh.toInteger() - state.historyHigh.toInteger()
            if (Math.abs(diff) >= 3) {
                def dir = diff > 0 ? "warmer" : "colder"
                if (persona == "Technical") script += "Historical data indicates today's peak thermal output is ${Math.abs(diff)} degrees ${dir} than this exact date last year. "
                else if (persona == "Disgruntled") script += "For the record, it's ${Math.abs(diff)} degrees ${dir} than exactly a year ago. Time is a flat circle. "
                else if (persona == "GenZ") script += "Wait, it's literally ${Math.abs(diff)} degrees ${dir} than this day last year. Crazy character development. "
                else if (persona == "Casual") script += "Fun fact! Today is actually about ${Math.abs(diff)} degrees ${dir} than this exact same day last year. "
                else script += "Looking at historical data, today's high is ${Math.abs(diff)} degrees ${dir} compared to this exact date last year. "
            }
        }
        
        if (useClothing && tHigh != "unknown") {
            def tempInt = tHigh.toInteger()
            if (tempInt < 40) {
                if (persona == "Disgruntled") script += "It's freezing. Wear a coat. Or don't, whatever. "
                else if (persona == "GenZ") script += "It's literally freezing out, definitely bundle up. No cap. "
                else script += "It's going to be freezing out there, so definitely grab a heavy coat. "
            } else if (tempInt < 60) {
                if (persona == "Disgruntled") script += "It's chilly. Put on a sweater. "
                else if (persona == "GenZ") script += "It's a little brick out, grab a jacket for the fit. "
                else script += "It's a bit chilly, so a light jacket or sweater is recommended. "
            } else if (tempInt < 80) {
                if (persona == "Disgruntled") script += "It's fine out. Short sleeves will do. "
                else if (persona == "GenZ") script += "Weather is bussin', short sleeves are the move today. "
                else script += "It's very comfortable out, short sleeves should be perfectly fine. "
            } else {
                if (persona == "Disgruntled") script += "It's hot. Try not to melt before the weekend. "
                else if (persona == "GenZ") script += "It's cooking out there, dress light and stay hydrated besties. "
                else script += "It's going to be hot, so dress lightly to stay cool. "
            }
        }
        
        if (useFeels && state.apiApparentTemp && state.apiCurrentTemp) {
            if (Math.abs(state.apiCurrentTemp - state.apiApparentTemp) >= 4) {
                if (persona == "Technical") script += "However, apparent temperature algorithms calculate a current heat index of ${state.apiApparentTemp}. "
                else if (persona == "Disgruntled") script += "But it actually feels like ${state.apiApparentTemp}. Go figure. "
                else if (persona == "GenZ") script += "But lowkey, it feels more like ${state.apiApparentTemp} out there. "
                else if (persona == "Casual") script += "Just a heads up though, it actually feels closer to ${state.apiApparentTemp} out there. "
                else script += "However, it currently feels more like ${state.apiApparentTemp} out there. "
            }
        }
        if (useFeels && state.apiWindGust && state.apiWindGust > 20) {
            if (persona == "Technical") script += "Warning: Wind gusts up to ${state.apiWindGust} miles per hour are currently projected. "
            else if (persona == "Disgruntled") script += "Watch out for ${state.apiWindGust} mph wind gusts trying to ruin your day. "
            else if (persona == "GenZ") script += "Wind is acting sus with gusts up to ${state.apiWindGust} mph. "
            else script += "Watch out for sudden wind gusts reaching up to ${state.apiWindGust} miles per hour. "
        }
        
        if (useRain && rainAmt > 0) {
            if (persona == "Technical") script += "Precipitation probability models estimate ${rainAmt} inches of accumulation ${dayContext.toLowerCase()}. "
            else if (persona == "Disgruntled") script += "We're stuck with ${rainAmt} inches of rain ${dayContext.toLowerCase()}. Perfect. "
            else if (persona == "GenZ") script += "We're getting ${rainAmt} inches of rain ${dayContext.toLowerCase()}, massive L. "
            else if (persona == "Casual") script += "Looks like we'll be getting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}, so keep an umbrella handy! "
            else script += "We are expecting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}. "
            
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
                    if (persona == "Technical") script += "Precipitation models indicate a sustained dry window of ${maxDryLength} hours commencing at ${displayHour}. "
                    else if (persona == "Disgruntled") script += "If you have to go outside, you have a dry window of ${maxDryLength} hours starting at ${displayHour}. Good luck. "
                    else if (persona == "GenZ") script += "If you need to touch grass, you've got a solid ${maxDryLength} hour window of zero rain starting at ${displayHour}. W timing. "
                    else if (persona == "Casual") script += "Even with the rain, it looks like you'll have a nice dry window of about ${maxDryLength} hours starting around ${displayHour}. "
                    else script += "If you need to plan outdoor activities, our models show a dry window of approximately ${maxDryLength} hours beginning at ${displayHour}. "
                }
            }
        }
        
        if (useUV && uvIndex > 5) {
            if (persona == "Disgruntled") script += "UV index is at ${uvIndex}, so don't get sunburned on top of everything else. "
            else if (persona == "GenZ") script += "UV index is peaking at ${uvIndex}, so don't forget the sunscreen. "
            else if (persona == "Casual") script += "The sun is going to be pretty strong today with a UV index of ${uvIndex}, so sunscreen is a good idea. "
            else script += "The UV index will peak at ${uvIndex}, so be sure to wear sunscreen. "
        }
    }
    
    if (infoStargazing && state.apiHourlyClouds) {
        def eveningClouds = 0
        def count = 0
        for (int i = 20; i <= 23; i++) {
             if(state.apiHourlyClouds.size() > i) {
                 eveningClouds += state.apiHourlyClouds[i]
                 count++
             }
        }
        def avgClouds = count > 0 ? (eveningClouds / count) : 100
        
        if (avgClouds < 20) {
             if (persona == "Technical") script += "Nocturnal cloud cover is projected below 20 percent, providing optimal atmospheric transparency for astronomical observations. "
             else if (persona == "Disgruntled") script += "Skies are mostly clear tonight if you want to go outside and stare into the void of space. "
             else if (persona == "GenZ") script += "Cloud cover is basically zero tonight, stargazing is gonna be an absolute movie. "
             else if (persona == "Casual") script += "With barely any clouds tonight, it's going to be a perfect evening to step outside and do some stargazing! "
             else script += "Expect minimal cloud cover this evening, creating excellent conditions for stargazing and viewing the night sky. "
        }
    }
    
    if (usePollen && state.pollenIndex) {
        if (persona == "Disgruntled") script += "Pollen is ${state.pollenIndex}. My allergies are already killing me. "
        else if (persona == "GenZ") script += "Pollen is sitting at ${state.pollenIndex}, which is totally not a vibe. "
        else script += "The pollen count is currently ${state.pollenIndex}, which is considered ${state.pollenCategory}. "
    }
    if (useMoon) {
        if (persona == "Disgruntled") script += "Tonight's moon is a ${getMoonPhase()}. Who cares. "
        else if (persona == "GenZ") script += "Tonight's moon is giving ${getMoonPhase()} energy. "
        else script += "Tonight's moon phase will be a ${getMoonPhase()}. "
    }
    
    if (useWeekly && state.apiDailyDates && state.apiDailyDates.size() >= 5) {
        def day4Name = getDayOfWeek(state.apiDailyDates[3])
        def day4High = safeGet(state.apiDailyHighs, 3, "unknown")
        def day4Cond = safeGet(state.apiDailyConditions, 3, "varying conditions")
        
        if (persona == "Technical") script += "Long-term projections indicate ${day4Name} will experience ${day4Cond} with temperatures holding near ${day4High} degrees."
        else if (persona == "Disgruntled") script += "If we survive until ${day4Name}, expect ${day4Cond} and ${day4High} degrees. Can't wait."
        else if (persona == "GenZ") script += "Looking ahead to ${day4Name}, it's gonna be ${day4Cond} with temps around ${day4High}. W weather."
        else if (persona == "Casual") script += "Peeking ahead at the rest of the week, ${day4Name} is looking nice and ${day4Cond} with a high around ${day4High}."
        else script += "Keep an eye out as we move through the week, ${day4Name} is looking to be ${day4Cond} with temperatures around ${day4High} degrees."
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
