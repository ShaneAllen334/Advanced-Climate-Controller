/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen (Updated by Gemini)
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a detailed meteorologist report combining live local weather station data with macro-forecast APIs. Features 5 distinct broadcasting personas, including Gen Z and Disgruntled.",
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
                    paragraph "<b>📝 Latest Script (${reportPersona ?: 'Professional'}):</b>"
                    paragraph "<div style='font-size: 14px; font-style: italic; background-color: #fdfd96; padding: 10px; border-radius: 5px; border: 1px solid #e1e182;'>\"${state.latestScript}\"</div>"
                }
            } else {
                paragraph "<i>Please select your personal weather station below to populate the dashboard.</i>"
            }
        }

        section(title: "<b>1. Data Sources & Settings</b>", hideable: true, hidden: true) {
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", required: true, multiple: false
            input "zipCode", "text", title: "Zip Code (Required for Pollen)", required: false
            input "pollingInterval", "enum", title: "Background Sync Interval (Updates Device)", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
            input "reportPersona", "enum", title: "Select Anchor Personality", options: ["Professional", "Casual", "Technical", "Disgruntled", "GenZ"], defaultValue: "Professional", required: true
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

        section(title: "<b>4. Broadcast Triggers</b>", hideable: true, hidden: true) {
            paragraph "<b>🕒 Scheduled Time Triggers</b>"
            input "timeTrigger1", "time", title: "Time Trigger 1", required: false
            input "timeTrigger2", "time", title: "Time Trigger 2", required: false
            input "timeTrigger3", "time", title: "Time Trigger 3", required: false

            paragraph "<hr><b>🏠 Mode-Based Triggers</b>"
            input "triggerMode1", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t1StartTime", "time", title: "Allowable Start Time", required: false
            input "t1EndTime", "time", title: "Allowable End Time", required: false
            
            input "triggerMode2", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t2StartTime", "time", title: "Allowable Start Time", required: false
            input "t2EndTime", "time", title: "Allowable End Time", required: false
            
            paragraph "<hr><b>🔘 Manual Switch Trigger</b>"
            input "triggerSwitch", "capability.switch", title: "Trigger via Virtual Switch (Turns off automatically)", required: false, multiple: false
        }

        section(title: "<b>5. Outputs & Advanced Devices</b>", hideable: true, hidden: true) {
            paragraph "<b>Audio Announcements</b>"
            input "ttsSpeakers", "capability.speechSynthesis", title: "Standard TTS Speakers", required: false, multiple: true
            input "advAudio", "capability.audioVolume", title: "Advanced Speakers (Volume Control/Restore Support)", required: false, multiple: true
            input "advAudioVol", "number", title: "Advanced Speaker Broadcast Volume (1-100)", required: false, range: "1..100"
            
            paragraph "<hr><b>Visual & Push Notifications</b>"
            input "visualIndicators", "capability.colorControl", title: "Visual Weather Indicators (RGB Switches/Bulbs)", required: false, multiple: true
            input "notifyDevices", "capability.notification", title: "Push Notification Devices", required: false, multiple: true
            
            paragraph "<hr><b>System Actions</b>"
            input "btnTestTTS", "button", title: "🔊 Test Audio Broadcast"
            input "btnTestNotify", "button", title: "📱 Test Push Notification"
            input "btnCreateDevice", "button", title: "⚙️ Create & Sync Child Device"
        }

        section(title: "<b>6. Recent Action History</b>", hideable: true, hidden: true) {
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
    if (triggerSwitch) subscribe(triggerSwitch, "switch.on", switchTriggerHandler)
    
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
    else if (btn == "btnTestTTS") { routineSync(); executeBroadcast(); logAction("Test Broadcast Triggered.") }
    else if (btn == "btnTestNotify") { routineSync(); sendSplitNotification(state.latestScript); logAction("Test Notify Triggered.") }
    else if (btn == "btnCreateDevice") { createChildDevice(); routineSync(); logAction("Child device created.") }
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
    fetchPollenData()
    generateScript()
    updateVisualIndicators()
    updateChildDevice()
    logAction("Routine Background Sync Complete.")
}

def executeBroadcast() {
    routineSync()
    if (ttsSpeakers) ttsSpeakers.speak(state.latestScript)
    
    if (advAudio) {
        advAudio.each { speaker ->
            if (advAudioVol && speaker.hasCommand("setVolumeSpeakAndRestore")) {
                speaker.setVolumeSpeakAndRestore(advAudioVol, state.latestScript)
            } else {
                speaker.speak(state.latestScript)
            }
        }
    }
    sendSplitNotification(state.latestScript)
}

def updateVisualIndicators() {
    if (!visualIndicators) return
    def rainAmt = (state.apiDailyRain && state.apiDailyRain.size() > 0) ? state.apiDailyRain[0] : 0
    def tHigh = (state.apiDailyHighs && state.apiDailyHighs.size() > 0) ? state.apiDailyHighs[0] : 70
    
    def hue = 33 // Default Green
    if (rainAmt > 0.1) hue = 65 // Blue
    else if (tHigh >= 90) hue = 0 // Red
    else if (tHigh <= 40) hue = 50 // Cyan
    
    visualIndicators.each { dev ->
        if (dev.hasCommand("setColor")) {
            dev.setColor([hue: hue, saturation: 100, level: 100])
        }
    }
}

def sendSplitNotification(text) {
    if (!notifyDevices || !text) return
    def sentences = text.split(/(?<=[.?!])\s+/)
    if (sentences.size() <= 3) {
        notifyDevices.deviceNotification(text)
    } else {
        def mid = Math.ceil(sentences.size() / 2).toInteger()
        def part1 = sentences[0..mid-1].join(" ")
        def part2 = sentences[mid..-1].join(" ")
        notifyDevices.deviceNotification("Weather Part 1: " + part1)
        notifyDevices.deviceNotification("Weather Part 2: " + part2)
    }
}

def timeTrigger1Handler() { logAction("Time Trigger 1 activated."); executeBroadcast() }
def timeTrigger2Handler() { logAction("Time Trigger 2 activated."); executeBroadcast() }
def timeTrigger3Handler() { logAction("Time Trigger 3 activated."); executeBroadcast() }

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
        executeBroadcast()
    }
}

def switchTriggerHandler(evt) { logAction("Virtual Switch triggered."); executeBroadcast(); runIn(2, turnOffSwitch) }
def turnOffSwitch() { if (triggerSwitch) triggerSwitch.off() }

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    if (!lat || !lon) { logAction("ERROR: Hub Lat/Lon missing."); state.apiFailed = true; return }

    def url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,apparent_temperature,weather_code,wind_gusts_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,uv_index_max&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=auto"
    
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

def generateScript() {
    def script = ""
    def nowTime = new Date()
    def persona = reportPersona ?: "Professional"
    
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
    
    // API Failure handling
    if (state.apiFailed) {
        if (persona == "Technical") script += "Warning: Primary weather API connection failed. Reverting to cached or partial data sets. "
        else if (persona == "Disgruntled") script += "Of course the weather service is down. Why would anything work today? "
        else if (persona == "GenZ") script += "Bruh, the weather API is lowkey buggin' right now, so this report is an L. "
        else if (persona == "Casual") script += "Oops, looks like my connection to the weather service is down right now, so I only have part of your report. "
        else script += "I currently cannot connect to the weather service, so part of your report is missing. "
    }
    
    // 1. Greeting
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
    
    // 2. Micro-Climate
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
    
    // 3. Macro Forecast (API)
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
        
        // Clothing Context
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
        
        // Feels Like & Gusts
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
        
        // Rain & UV
        if (useRain && rainAmt > 0) {
            if (persona == "Technical") script += "Precipitation probability models estimate ${rainAmt} inches of accumulation ${dayContext.toLowerCase()}. "
            else if (persona == "Disgruntled") script += "We're stuck with ${rainAmt} inches of rain ${dayContext.toLowerCase()}. Perfect. "
            else if (persona == "GenZ") script += "We're getting ${rainAmt} inches of rain ${dayContext.toLowerCase()}, massive L. "
            else if (persona == "Casual") script += "Looks like we'll be getting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}, so keep an umbrella handy! "
            else script += "We are expecting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}. "
        }
        if (useUV && uvIndex > 5) {
            if (persona == "Disgruntled") script += "UV index is at ${uvIndex}, so don't get sunburned on top of everything else. "
            else if (persona == "GenZ") script += "UV index is peaking at ${uvIndex}, so don't forget the sunscreen. "
            else if (persona == "Casual") script += "The sun is going to be pretty strong today with a UV index of ${uvIndex}, so sunscreen is a good idea. "
            else script += "The UV index will peak at ${uvIndex}, so be sure to wear sunscreen. "
        }
    }
    
    // 4. Pollen & Moon Phase
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
    
    // 5. Weekly Teaser
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

    state.latestScript = script
    state.lastReportTime = nowTime.format("MM/dd hh:mm a", location.timeZone)
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