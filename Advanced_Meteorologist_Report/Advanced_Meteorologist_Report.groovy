/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a TV-anchor style meteorologist report combining live local weather station data with macro-forecast APIs. Features Pollen, Moon Phase, Rain amounts, offline protection, and granular triggers.",
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
                    paragraph "<b>📝 Latest TV-Anchor Script Generated:</b>"
                    paragraph "<div style='font-size: 14px; font-style: italic; background-color: #fdfd96; padding: 10px; border-radius: 5px; border: 1px solid #e1e182;'>\"${state.latestScript}\"</div>"
                }
            } else {
                paragraph "<i>Please select your personal weather station below to populate the dashboard.</i>"
            }
        }

        section(title: "<b>1. Data Sources & Refresh</b>", hideable: true, hidden: true) {
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", required: true, multiple: false
            input "zipCode", "text", title: "Zip Code (Required for Pollen)", required: false
            input "pollingInterval", "enum", title: "Background Sync Interval (Updates Device)", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
        }

        section(title: "<b>2. Morning Report Profile</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Configures what the anchor talks about during the morning hours. Usually focused on the day ahead.</div>"
            input "morningStartTime", "time", title: "Morning Window Start", required: false
            input "morningEndTime", "time", title: "Morning Window End", required: false
            input "m_includeGreeting", "bool", title: "Include Greeting (Good Morning)", defaultValue: true
            input "m_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "m_includeDaily", "bool", title: "Include Today's Highs/Lows/Conditions", defaultValue: true
            input "m_includeFeelsLike", "bool", title: "Include 'Feels Like' & Wind Gusts", defaultValue: true
            input "m_includeRain", "bool", title: "Include Expected Rain Amount", defaultValue: true
            input "m_includeUV", "bool", title: "Include Peak UV Index", defaultValue: true
            input "m_includePollen", "bool", title: "Include Pollen Forecast", defaultValue: false
            input "m_includeMoon", "bool", title: "Include Moon Phase", defaultValue: false
            input "m_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: false
        }
        
        section(title: "<b>3. Evening/Night Report Profile</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Configures what the anchor talks about during the evening. Usually focused on overnight lows and tomorrow.</div>"
            input "eveningStartTime", "time", title: "Evening Window Start", required: false
            input "eveningEndTime", "time", title: "Evening Window End", required: false
            input "e_includeGreeting", "bool", title: "Include Greeting (Good Evening)", defaultValue: true
            input "e_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "e_includeDaily", "bool", title: "Include Tonight's Low & Tomorrow's Forecast", defaultValue: true
            input "e_includeFeelsLike", "bool", title: "Include 'Feels Like' & Wind Gusts", defaultValue: false
            input "e_includeRain", "bool", title: "Include Tomorrow's Rain Amount", defaultValue: true
            input "e_includePollen", "bool", title: "Include Tomorrow's Pollen Forecast", defaultValue: false
            input "e_includeMoon", "bool", title: "Include Tonight's Moon Phase", defaultValue: true
            input "e_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: true
        }

        section(title: "<b>4. Broadcast Triggers</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>When should the report be announced automatically? All are optional.</div>"
            
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
            
            input "triggerMode3", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t3StartTime", "time", title: "Allowable Start Time", required: false
            input "t3EndTime", "time", title: "Allowable End Time", required: false
            
            paragraph "<hr><b>🔘 Manual Switch Trigger</b>"
            input "triggerSwitch", "capability.switch", title: "Trigger via Virtual Switch (Turns off automatically)", required: false, multiple: false
        }

        section(title: "<b>5. Outputs (Speakers & Notifications)</b>", hideable: true, hidden: true) {
            input "ttsSpeakers", "capability.speechSynthesis", title: "Select TTS Speakers", required: false, multiple: true
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", required: false, multiple: true
            input "btnTestTTS", "button", title: "🔊 Test TTS Broadcast"
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
    else if (btn == "btnTestTTS") { routineSync(); if (ttsSpeakers) ttsSpeakers.speak(state.latestScript); logAction("Test TTS Triggered.") }
    else if (btn == "btnTestNotify") { routineSync(); if (notifyDevices) notifyDevices.deviceNotification(state.latestScript); logAction("Test Notify Triggered.") }
    else if (btn == "btnCreateDevice") { createChildDevice(); routineSync(); logAction("Child device created.") }
}

// ==============================================================================
// CIRCUIT BREAKER & SYNC
// ==============================================================================

def hasInternet() {
    if (state.lastErrorTime) {
        long elapsed = (now() - state.lastErrorTime) / 60000
        if (elapsed < 15) { 
            log.warn "Advanced Meteorologist: Network in 15-min cooldown."
            return false
        }
    }
    return true // We now let the individual API calls handle their own failure checks
}

def routineSync() {
    if (!hasInternet()) {
        state.apiFailed = true
        generateScript() // Generate offline script
        updateChildDevice() 
        return 
    }
    
    state.apiFailed = false
    fetchApiData()
    fetchPollenData()
    generateScript()
    updateChildDevice()
    logAction("Routine Background Sync Complete.")
}

// ==============================================================================
// TRIGGERS
// ==============================================================================

def timeTrigger1Handler() { logAction("Scheduled Time Trigger 1 activated."); executeBroadcast() }
def timeTrigger2Handler() { logAction("Scheduled Time Trigger 2 activated."); executeBroadcast() }
def timeTrigger3Handler() { logAction("Scheduled Time Trigger 3 activated."); executeBroadcast() }

def modeChangeHandler(evt) {
    def newMode = evt.value
    def nowTime = new Date()
    def shouldTrigger = false
    def triggeredBy = ""
    
    if (triggerMode1 && newMode == triggerMode1) {
        if (!t1StartTime || !t1EndTime || timeOfDayIsBetween(toDateTime(t1StartTime), toDateTime(t1EndTime), nowTime, location.timeZone)) {
            shouldTrigger = true; triggeredBy = "Trigger 1 (${newMode})"
        }
    }
    else if (triggerMode2 && newMode == triggerMode2) {
        if (!t2StartTime || !t2EndTime || timeOfDayIsBetween(toDateTime(t2StartTime), toDateTime(t2EndTime), nowTime, location.timeZone)) {
            shouldTrigger = true; triggeredBy = "Trigger 2 (${newMode})"
        }
    }
    else if (triggerMode3 && newMode == triggerMode3) {
        if (!t3StartTime || !t3EndTime || timeOfDayIsBetween(toDateTime(t3StartTime), toDateTime(t3EndTime), nowTime, location.timeZone)) {
            shouldTrigger = true; triggeredBy = "Trigger 3 (${newMode})"
        }
    }
    
    if (shouldTrigger) {
        logAction("Valid mode change detected. Broadcasting via ${triggeredBy}.")
        executeBroadcast()
    }
}

def switchTriggerHandler(evt) { logAction("Virtual Switch triggered."); executeBroadcast(); runIn(2, turnOffSwitch) }
def turnOffSwitch() { if (triggerSwitch) triggerSwitch.off() }

def executeBroadcast() {
    routineSync()
    if (ttsSpeakers) ttsSpeakers.speak(state.latestScript)
    if (notifyDevices) notifyDevices.deviceNotification(state.latestScript)
}

// ==============================================================================
// DATA FETCHING
// ==============================================================================

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    if (!lat || !lon) { logAction("ERROR: Hub Lat/Lon missing."); state.apiFailed = true; return }

    def url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,apparent_temperature,weather_code,wind_gusts_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,uv_index_max&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=auto"
    
    try {
        httpGet([uri: url, timeout: 10]) { resp ->
            if (resp.success) {
                state.lastErrorTime = null // Reset error cooldown on successful connect
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
        state.apiFailed = true
        state.lastErrorTime = now()
    }
}

def fetchPollenData() {
    if (!zipCode) { state.pollenIndex = null; return }
    try {
        def params = [
            uri: "https://www.pollen.com/api/forecast/current/pollen/${zipCode}",
            headers: [
                "Referer": "https://www.pollen.com",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept": "application/json"
            ],
            timeout: 10
        ]
        httpGet(params) { resp ->
            if (resp.success && resp.data?.Location?.periods) {
                def todayPollen = resp.data.Location.periods.find { it.Type == "Forecast" }
                if (todayPollen) {
                    state.pollenIndex = todayPollen.Index
                    def idx = todayPollen.Index.toFloat()
                    if (idx < 2.4) state.pollenCategory = "low"
                    else if (idx < 4.8) state.pollenCategory = "low to medium"
                    else if (idx < 7.2) state.pollenCategory = "medium"
                    else if (idx < 9.6) state.pollenCategory = "medium to high"
                    else state.pollenCategory = "high"
                }
            } else { logAction("Pollen API connection issue."); } 
        }
    } catch (e) { logAction("ERROR fetching Pollen API.") }
}

def generateScript() {
    def script = ""
    def nowTime = new Date()
    
    def isMorning = morningStartTime && morningEndTime && timeOfDayIsBetween(toDateTime(morningStartTime), toDateTime(morningEndTime), nowTime, location.timeZone)
    def isEvening = eveningStartTime && eveningEndTime && timeOfDayIsBetween(toDateTime(eveningStartTime), toDateTime(eveningEndTime), nowTime, location.timeZone)
    
    def useGreeting = true; def useMicro = true; def useDaily = true; def useWeekly = false
    def useRain = false; def usePollen = false; def useMoon = false; def useUV = false; def useFeels = false
    def activeProfileName = "Standard"
    
    if (isMorning) {
        useGreeting = m_includeGreeting; useMicro = m_includeMicro; useDaily = m_includeDaily; useWeekly = m_includeWeekly
        useRain = m_includeRain; usePollen = m_includePollen; useMoon = m_includeMoon; useUV = m_includeUV; useFeels = m_includeFeelsLike
        activeProfileName = "Morning"
    } else if (isEvening) {
        useGreeting = e_includeGreeting; useMicro = e_includeMicro; useDaily = e_includeDaily; useWeekly = e_includeWeekly
        useRain = e_includeRain; usePollen = e_includePollen; useMoon = e_includeMoon; useFeels = e_includeFeelsLike
        activeProfileName = "Evening"
    }
    
    if (state.apiFailed) {
        script += "I currently cannot connect to the news desk, so part of your report is missing. "
    }
    
    // 1. Greeting
    if (useGreeting) {
        def hour = nowTime.format("HH", location.timeZone).toInteger()
        def greeting = "Good evening"
        if (hour >= 4 && hour < 12) greeting = "Good morning"
        else if (hour >= 12 && hour < 17) greeting = "Good afternoon"
        script += "${greeting}! "
    }
    
    // 2. Micro-Climate
    if (useMicro && localStation) {
        def lTemp = localStation.currentValue("temperature") ?: "unknown"
        def lHum = localStation.currentValue("humidity") ?: "unknown"
        script += "Right now at your house, it is currently ${lTemp} degrees with a humidity of ${lHum} percent. "
    }
    
    // Null Checks for Arrays to prevent getAt() error
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
            script += "Overnight, expect temperatures to drop to a low of ${tLow}. Looking ahead to tomorrow, we will see ${cond} with a high of ${tHigh}. "
        } else {
            script += "Looking at the broader forecast, expect ${cond} today. We will reach a high of ${tHigh}, and drop to a low of ${tLow} tonight. "
        }
        
        if (useFeels && state.apiApparentTemp && state.apiCurrentTemp) {
            if (Math.abs(state.apiCurrentTemp - state.apiApparentTemp) >= 4) {
                script += "However, it currently feels more like ${state.apiApparentTemp} out there. "
            }
        }
        if (useFeels && state.apiWindGust && state.apiWindGust > 20) {
            script += "Watch out for wind gusts reaching up to ${state.apiWindGust} miles per hour. "
        }
        
        if (useRain && rainAmt > 0) script += "We are expecting about ${rainAmt} inches of rain ${dayContext.toLowerCase()}. "
        if (useUV && uvIndex > 5) script += "The UV index will peak at ${uvIndex}, so be sure to wear sunscreen. "
    }
    
    // 4. Pollen & Moon Phase
    if (usePollen && state.pollenIndex) script += "The pollen count is currently ${state.pollenIndex}, which is considered ${state.pollenCategory}. "
    if (useMoon) script += "Tonight's moon phase will be a ${getMoonPhase()}. "
    
    // 5. Weekly Teaser
    if (useWeekly && state.apiDailyDates && state.apiDailyDates.size() >= 5) {
        def day4Name = getDayOfWeek(state.apiDailyDates[3])
        def day4High = safeGet(state.apiDailyHighs, 3, "unknown")
        def day4Cond = safeGet(state.apiDailyConditions, 3, "varying conditions")
        script += "Keep an eye out as we move through the week, ${day4Name} is looking to be ${day4Cond} with temperatures around ${day4High} degrees."
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
    int b = jd; jd -= b; b = Math.round(jd * 8)
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
