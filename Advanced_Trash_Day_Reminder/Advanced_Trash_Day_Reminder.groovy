/**
 * Advanced Trash Day Reminder
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Trash Day Reminder",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced trash day scheduling with predictive AI, omni-axis spatial tracking, Utility Financial Auditing, and Bi-Weekly Recycling logic.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Trash Day Reminder</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            checkSensorHealth()
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${statusExplanation}</div>"

            def nPickup = state.nextPickupStr ?: "Not Calculated"
            
            def isRecycleWeek = checkRecyclingWeek()
            def recycleStr = isRecycleWeek ? "<b style='color:#2980b9;'>YES (Trash + Recycling)</b>" : "No (Trash Only)"
            
            def physicalBinState = "<b>At House</b>"
            if (state.binStatus == "curb_full") physicalBinState = "<b style='color:#e67e22;'>At Curb (Awaiting Truck)</b>"
            else if (state.binStatus == "curb_emptied") physicalBinState = "<b style='color:#27ae60;'>At Curb (Emptied, Awaiting Return)</b>"
            else if (state.binStatus == "curb_missed") physicalBinState = "<b style='color:#c0392b;'>At Curb (MISSED PICKUP)</b>"
            
            def tStatus = (state.isNagging || (trashSwitch && trashSwitch.currentValue("switch") == "on")) ? "<b style='color:red;'>PENDING</b>" : "<b style='color:green;'>CLEAR</b>"
            
            def holidayStatus = "Normal Schedule"
            if (state.autoHolidayTriggered) holidayStatus = "<b style='color:#2980b9;'>AUTOMATED (+1 Day Shift)</b>"
            else if (state.holidayShift) holidayStatus = "<b style='color:#8e44ad;'>MANUAL OVERRIDE (+1 Day Shift)</b>"
            
            def schedMode = state.predictiveActive ? "<b style='color:#8e44ad;'>Predictive (AI Calibrated)</b>" : "Static (User Defined)"
            
            def battLvl = binMultiSensor ? binMultiSensor.currentValue("battery") : null
            def battDisplay = battLvl != null ? (battLvl <= 15 ? "<b style='color:red;'>${battLvl}% (LOW)</b>" : "${battLvl}%") : "N/A"

            def sensorHealthStr = state.isSensorDead ? "<b style='color:red;'>OFFLINE (Dumb Fallback Mode Active)</b>" : "<b style='color:green;'>ONLINE & TRACKING</b>"

            def fillDisplay = "${getFillPct()}% (${state.lidOpens ?: 0} of ${settings.estimatedMaxOpens ?: 8} bags)"
            if (getFillPct() >= 100) fillDisplay = "<b style='color:red;'>100% (FULL)</b>"

            def ecoScore = getEcoScoreBadge()

            def missedStr = "N/A (On Time)"
            if (state.binStatus == "curb_missed" && state.missedTime) {
                long hrsMissed = (now() - state.missedTime) / 3600000
                missedStr = "<b style='color:red;'>ACTIVE - Missed by ${hrsMissed} hours</b>"
            } else if (state.lastMissedDuration) {
                missedStr = "Last cycle was late by ${state.lastMissedDuration} hours."
            }
            
            // Financial Math
            def refundStr = calculateRefundOwed()
            
            def avgPutOut = getAverageTimeStr("historyPutOut")
            def avgEmptied = getAverageTimeStr("historyEmptied")
            def avgReturned = getAverageTimeStr("historyReturned")
            
            def baselineStatus = (state.activeAxis && state.baselineValue != null) ? "<b style='color:green;'>Calibrated (${state.activeAxis.toUpperCase()}: ${state.baselineValue})</b>" : "<b style='color:red;'>Requires Calibration</b>"

            def hygStatus = state.hygieneStatus ?: "Clean ✨"
            int daysWashed = state.lastWashed ? ((now() - state.lastWashed) / 86400000) as Integer : 0
            def maxT = state.maxTempSinceWash ?: "N/A"

            input "btnRefreshData", "button", title: "🔄 Refresh Dashboard Data"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
                .dash-sub { background-color: #e9ecef; font-weight: bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                <tbody>
                    <tr><td colspan="4" class="dash-sub">Schedule & Status</td></tr>
                    <tr><td class="dash-hl">Next Collection Target</td><td colspan="3" class="dash-val" style="font-size:16px;"><b>${nPickup}</b></td></tr>
                    <tr><td class="dash-hl">Includes Recycling?</td><td colspan="3" class="dash-val">${recycleStr}</td></tr>
                    <tr><td class="dash-hl">Scheduling Mode</td><td colspan="3" class="dash-val">${schedMode}</td></tr>
                    <tr><td class="dash-hl">Task Status</td><td colspan="3" class="dash-val">${tStatus}</td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Utility Audit & Financial Failsafe</td></tr>
                    <tr><td class="dash-hl">Current Tracker Status</td><td colspan="3" class="dash-val">${missedStr}</td></tr>
                    <tr><td class="dash-hl">Total Time Missed (Lifetime)</td><td colspan="3" class="dash-val">${state.lifetimeMissedHours ?: 0} Hours</td></tr>
                    <tr><td class="dash-hl">Prorated Refund Owed</td><td colspan="3" class="dash-val" style="color:red; font-size:16px;"><b>\$${refundStr}</b></td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Hygiene & Environmental Impact</td></tr>
                    <tr><td class="dash-hl">Household Waste Rating</td><td colspan="3" class="dash-val" style="font-size:16px;">${ecoScore}</td></tr>
                    <tr><td class="dash-hl">Estimated Current Fullness</td><td colspan="3" class="dash-val">${fillDisplay}</td></tr>
                    <tr><td class="dash-hl">Current Hygiene Status</td><td colspan="3" class="dash-val"><b>${hygStatus}</b></td></tr>
                    <tr><td class="dash-hl">Time Since Sanitized</td><td colspan="3" class="dash-val">${daysWashed} Days (Peak Temp: ${maxT}°F)</td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Physical Hardware Telemetry</td></tr>
                    <tr><td class="dash-hl">Hardware Connection</td><td colspan="3" class="dash-val">${sensorHealthStr}</td></tr>
                    <tr><td class="dash-hl">Physical Bin Location</td><td colspan="3" class="dash-val">${physicalBinState}</td></tr>
                    <tr><td class="dash-hl">Sensor Battery</td><td colspan="3" class="dash-val"><b>${battDisplay}</b></td></tr>
                    <tr><td class="dash-hl">Spatial Calibration</td><td colspan="3" class="dash-val">${baselineStatus}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Manual System Overrides</b></div>"
            input "btnSetHouse", "button", title: "Set Bin: At House"
            input "btnSetCurbFull", "button", title: "Set Bin: At Curb (Full)"
            input "btnSetCurbEmptied", "button", title: "Set Bin: At Curb (Emptied)"
            input "btnSetMissed", "button", title: "Set Bin: Missed Pickup"
            
            paragraph "<br>"
            input "btnCalibrate", "button", title: "Calibrate Spatial Baseline (Close Lid First)"
            input "btnResetFinance", "button", title: "💳 Reset Financial Audit Ledger to \$0.00"
            input "btnHoliday", "button", title: state.holidayShift ? "Cancel Manual Holiday Shift" : "Force Manual Holiday Shift (+1 Day)"
        }
        
        section("<b>Utility Financial Auditor</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Enter your quarterly waste management cost. The app will calculate prorated refunds for missed collection days.</div>"
            input "quarterlyCost", "decimal", title: "Quarterly Trash Bill Cost (\$)", description: "e.g., 90.00", required: false, submitOnChange: true
        }

        section("<b>Predictive AI Scheduling & Recycling</b>") {
            input "usePredictiveTiming", "bool", title: "Enable Predictive Smart Schedule", defaultValue: true, submitOnChange: true
            
            input "enableRecycling", "bool", title: "Enable Bi-Weekly Recycling Logic", defaultValue: false, submitOnChange: true
            if (enableRecycling) {
                input "recycleWeek", "enum", title: "Recycling is collected on:", options: ["Even Weeks", "Odd Weeks"], required: true
                input "ttsRecycleText", "text", title: "Recycling Announcement Text", defaultValue: "Reminder, it is time to take the trash and the recycling out to the road."
            }
        }

        section("<b>Automated Physical Tracking (Samsung Multi-Sensor)</b>") {
            input "binMultiSensor", "capability.threeAxis", title: "Samsung Multipurpose Sensor", required: true
            
            input "drivewaySurface", "enum", title: "Driveway Surface Profile", options: ["Smooth Pavement", "Mixed / Patchy", "Fine Gravel", "Chunky / Rough Gravel", "Custom (Manual Overrides)"], defaultValue: "Smooth Pavement", required: true, submitOnChange: true
            if (drivewaySurface == "Custom (Manual Overrides)") {
                input "transitMinTime", "number", title: "Minimum Transit Duration (Seconds)", defaultValue: 10, required: true
                input "transitTiltThreshold", "number", title: "Transit Tilt Threshold", defaultValue: 150, required: true
                input "lidOpenThreshold", "number", title: "Lid Open Tilt Threshold", defaultValue: 600, required: true
            }
            input "estimatedMaxOpens", "number", title: "Bag Capacity (Opens to Full)", defaultValue: 8, required: true
            
            input "enableFallenBin", "bool", title: "Enable Severe Weather Failsafe (Fallen Bin Alerts)", defaultValue: true
        }

        section("<b>HOA Compliance (Return Nag)</b>") {
            input "enableHoaNag", "bool", title: "Enable HOA Return Nag", defaultValue: false, submitOnChange: true
            if (enableHoaNag) {
                input "hoaNagTime", "number", title: "Hours to wait after truck dumps before nagging:", defaultValue: 4, required: true
                input "ttsHoaText", "text", title: "HOA Return Announcement Text", defaultValue: "The garbage truck has collected the trash. Please return the bin to the house."
            }
        }

        section("<b>Automated Holiday Tracking</b>") {
            input "autoHoliday", "bool", title: "Enable Automatic Holiday Shifting", defaultValue: true, submitOnChange: true
            if (autoHoliday) {
                input "selectedHolidays", "enum", title: "Recognized Waste Management Holidays", options: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"], multiple: true, required: true, defaultValue: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"]
            }
        }

        section("<b>Collection Schedule & Offset</b>") {
            input "trashDays", "enum", title: "Trash Collection Day(s)", options: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"], multiple: true, required: true, submitOnChange: true
            input "pickupTime", "time", title: "Baseline Estimated Time (Used if Predictive is off)", required: true, submitOnChange: true
            input "reminderOffset", "decimal", title: "Reminder Offset (Hours before pickup)", required: true, defaultValue: 12.0, submitOnChange: true
        }

        section("<b>Acknowledgment & Nags</b>") {
            input "trashSwitch", "capability.switch", title: "Trash Status Switch", required: false
            input "enableNag", "bool", title: "Nag every hour until acknowledged?", defaultValue: false
        }
        
        section("<b>Operating Modes (Granular Restrictions)</b>") {
            input "pushModes", "mode", title: "Modes allowing Push Notifications", multiple: true, required: false
            input "audioModes", "mode", title: "Modes allowing Audio/Zooz Announcements", multiple: true, required: false
            input "nagModes", "mode", title: "Modes allowing Hourly Nags", multiple: true, required: false
            input "trackingModes", "mode", title: "Modes allowing Physical Tracking (The Raccoon Filter)", multiple: true, required: false
        }

        section("<b>Audio Announcements & Notifications</b>") {
            input "notifyDevice", "capability.notification", title: "Push Notification Devices", required: false, multiple: true
            input "ttsSpeakers", "capability.speechSynthesis", title: "Smart Speakers", multiple: true, required: false
            
            input "ttsReminderText", "text", title: "Standard Reminder Text", defaultValue: "Reminder, it is time to take the trash out to the road."
            input "ttsEmptiedText", "text", title: "Bin Emptied Text", defaultValue: "The garbage truck has emptied your bin."
            input "ttsReturnedText", "text", title: "Bin Returned Text", defaultValue: "The trash bin has been returned to the house."
            
            paragraph "<b>Zooz Siren & Chime</b>"
            input "zoozChimes", "capability.chime", title: "Zooz Chime Devices", multiple: true, required: false, submitOnChange: true
            if (zoozChimes) {
                input "zoozSoundReminder", "number", title: "Sound File #: Trash Reminder", required: false
                input "zoozSoundEmptied", "number", title: "Sound File #: Bin Emptied", required: false
                input "zoozSoundReturned", "number", title: "Sound File #: Bin Returned", required: false
            }
        }

        section("<b>Global Audio Room Mapping</b>") {
            input "audioMotionTimeout", "number", title: "Audio Motion Timeout (Minutes)", defaultValue: 5
            input "alwaysOnRoom", "enum", title: "Select ONE room to ALWAYS announce (Ignores motion)", options: ["1": "Room 1", "2": "Room 2", "3": "Room 3", "4": "Room 4", "5": "Room 5", "6": "Room 6", "7": "Room 7"], required: false
            
            input "room1Speaker", "capability.actuator", title: "Room 1 Speaker/Chime(s)", required: false, multiple: true
            input "room1Motion", "capability.motionSensor", title: "Room 1 Motion Sensor(s)", required: false, multiple: true
            input "room2Speaker", "capability.actuator", title: "Room 2 Speaker/Chime(s)", required: false, multiple: true
            input "room2Motion", "capability.motionSensor", title: "Room 2 Motion Sensor(s)", required: false, multiple: true
            input "room3Speaker", "capability.actuator", title: "Room 3 Speaker/Chime(s)", required: false, multiple: true
            input "room3Motion", "capability.motionSensor", title: "Room 3 Motion Sensor(s)", required: false, multiple: true
            input "room4Speaker", "capability.actuator", title: "Room 4 Speaker/Chime(s)", required: false, multiple: true
            input "room4Motion", "capability.motionSensor", title: "Room 4 Motion Sensor(s)", required: false, multiple: true
            input "room5Speaker", "capability.actuator", title: "Room 5 Speaker/Chime(s)", required: false, multiple: true
            input "room5Motion", "capability.motionSensor", title: "Room 5 Motion Sensor(s)", required: false, multiple: true
        }

        section("<b>Recent Action History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
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
    if (!state.binStatus) state.binStatus = "house"
    if (state.holidayShift == null) state.holidayShift = false
    if (state.autoHolidayTriggered == null) state.autoHolidayTriggered = false
    if (!state.lidOpens) state.lidOpens = 0
    if (!state.lastWashed) state.lastWashed = now()
    if (!state.maxTempSinceWash) state.maxTempSinceWash = 70
    if (state.isSensorDead == null) state.isSensorDead = false
    if (!state.lifetimeMissedHours) state.lifetimeMissedHours = 0.0
    
    if (!state.historyPutOut) state.historyPutOut = []
    if (!state.historyEmptied) state.historyEmptied = []
    if (!state.historyReturned) state.historyReturned = []
    if (!state.historyFullness) state.historyFullness = []
    
    if (settings.room1Motion) subscribe(settings.room1Motion, "motion.active", "room1MotionHandler")
    if (settings.room2Motion) subscribe(settings.room2Motion, "motion.active", "room2MotionHandler")
    if (settings.room3Motion) subscribe(settings.room3Motion, "motion.active", "room3MotionHandler")
    if (settings.room4Motion) subscribe(settings.room4Motion, "motion.active", "room4MotionHandler")
    if (settings.room5Motion) subscribe(settings.room5Motion, "motion.active", "room5MotionHandler")
    
    if (trashSwitch) subscribe(trashSwitch, "switch.off", ackHandler)
    
    if (binMultiSensor) {
        subscribe(binMultiSensor, "threeAxis", axisSpatialHandler)
        subscribe(binMultiSensor, "acceleration.active", binMoveActiveHandler)
        subscribe(binMultiSensor, "acceleration.inactive", binMoveInactiveHandler)
        subscribe(binMultiSensor, "temperature", tempHandler)
    }
    
    schedule("0 5 0 * * ?", updateSchedule) 
    runEvery1Hour("nagCheck")
    updateSchedule()
    updateHygiene()
    checkSensorHealth()
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        checkSensorHealth()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnSetHouse") {
        state.binStatus = "house"
        state.lidOpens = 0
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'House'.")
    } else if (btn == "btnSetCurbFull") {
        state.binStatus = "curb_full"
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Full)'.")
    } else if (btn == "btnSetCurbEmptied") {
        state.binStatus = "curb_emptied"
        state.lidOpens = 0
        if (enableHoaNag && hoaNagTime) runIn((hoaNagTime as Integer) * 3600, "hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Emptied)'.")
    } else if (btn == "btnSetMissed") {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'Missed Pickup'.")
    } else if (btn == "btnResetFinance") {
        state.lifetimeMissedHours = 0.0
        logAction("FINANCIAL AUDIT: Ledger reset to \$0.00.")
    } else if (btn == "btnRecalculate") {
        updateSchedule()
    } else if (btn == "btnHoliday") {
        state.holidayShift = !state.holidayShift
        logAction("MANUAL: Holiday Shift " + (state.holidayShift ? "Activated" : "Deactivated"))
        updateSchedule()
    } else if (btn == "btnCalibrate") {
        calibrateSpatialBaseline()
    } else if (btn == "btnMarkWashed") {
        state.lastWashed = now()
        state.maxTempSinceWash = binMultiSensor?.currentValue("temperature") ?: 70
        state.hygieneStatus = "Clean ✨"
        logAction("SYSTEM: Bin marked as washed and sanitized. Timers reset.")
    } 
}

// ------------------------------------------------------------------------------
// FINANCIAL MATH & RECYCLING
// ------------------------------------------------------------------------------

boolean checkRecyclingWeek() {
    if (!settings.enableRecycling || !settings.recycleWeek) return false
    def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
    int weekNum = cal.get(Calendar.WEEK_OF_YEAR)
    boolean isEven = (weekNum % 2 == 0)
    if (settings.recycleWeek == "Even Weeks" && isEven) return true
    if (settings.recycleWeek == "Odd Weeks" && !isEven) return true
    return false
}

String calculateRefundOwed() {
    if (!settings.quarterlyCost || !state.lifetimeMissedHours) return "0.00"
    def qCost = settings.quarterlyCost.toDouble()
    def dailyRate = qCost / 91.25
    def hourlyRate = dailyRate / 24.0
    def refund = (state.lifetimeMissedHours.toDouble() * hourlyRate)
    return String.format("%.2f", refund)
}

def hoaNagHandler() {
    if (state.binStatus == "curb_emptied") {
        logAction("HOA COMPLIANCE: Sending return nag.")
        def msg = settings.ttsHoaText ?: "The garbage truck has collected the trash. Please return the bin to the house."
        sendAlert("HOA ALERT: " + msg)
        if (ttsSpeakers) playTTS(ttsSpeakers, msg)
        if (zoozChimes && zoozSoundReturned != null) playZoozChime(zoozSoundReturned)
    }
}

// ------------------------------------------------------------------------------
// MODE HELPERS 
// ------------------------------------------------------------------------------

boolean isPushAllowed() {
    if (!settings.pushModes) return true
    return (settings.pushModes as List).contains(location.mode)
}
boolean isAudioAllowed() {
    if (!settings.audioModes) return true
    return (settings.audioModes as List).contains(location.mode)
}
boolean isNagAllowed() {
    if (!settings.nagModes) return true
    return (settings.nagModes as List).contains(location.mode)
}
boolean isTrackingAllowed() {
    if (!settings.trackingModes) return true
    return (settings.trackingModes as List).contains(location.mode)
}

int getFillPct() {
    int maxOpens = settings.estimatedMaxOpens ?: 8
    int currentOpens = state.lidOpens ?: 0
    return Math.min(Math.round((currentOpens / maxOpens) * 100), 100)
}

String getEcoScoreBadgeText() {
    def hist = state.historyFullness ?: []
    if (hist.size() == 0) return "Pending"
    int avgFill = Math.round(hist.sum() / hist.size()) as Integer
    if (avgFill <= 25) return "A+"
    if (avgFill <= 50) return "A"
    if (avgFill <= 75) return "B"
    if (avgFill <= 99) return "C"
    return "F"
}

def checkSensorHealth() {
    boolean isDead = false
    if (binMultiSensor) {
        def lastEvt = binMultiSensor.currentState("temperature")?.date
        if (lastEvt) {
            long hrsSince = (now() - lastEvt.time) / 3600000
            if (hrsSince > 48) isDead = true
        } else { isDead = true }
    }
    state.isSensorDead = isDead
}

def tempHandler(evt) {
    state.isSensorDead = false 
    def t = evt.numericValue
    if (t != null && t > (state.maxTempSinceWash ?: 0)) state.maxTempSinceWash = t
}

def updateHygiene() {
    long daysSince = state.lastWashed ? ((now() - state.lastWashed) / 86400000) as Integer : 0
    int maxT = state.maxTempSinceWash ?: 70
    String status = "Clean ✨"
    if (daysSince > 30 || (maxT > 90 && daysSince > 14)) status = "Bio-Hazard ☣️"
    else if (daysSince > 21 || (maxT > 85 && daysSince > 10)) status = "Gross 🤢"
    else if (daysSince > 14 || (maxT > 80 && daysSince > 7)) status = "Needs Washing 🧽"
    state.hygieneStatus = status
}

def recordTelemetryTime(String stateKey) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    cal.setTime(new Date())
    int minutes = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    def list = state[stateKey] ?: []
    list.add(0, minutes)
    if (list.size() > 10) list = list[0..9]
    state[stateKey] = list
}

String getAverageTimeStr(String stateKey) {
    def list = state[stateKey]
    if (!list || list.size() == 0) return "<span style='color:#888;'>Insufficient Data</span>"
    def sum = list.sum()
    int avgMins = Math.round(sum / list.size()) as Integer
    int h = avgMins.intdiv(60)
    int m = avgMins % 60
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    cal.set(Calendar.HOUR_OF_DAY, h)
    cal.set(Calendar.MINUTE, m)
    return cal.getTime().format("h:mm a", tz)
}

String getEcoScoreBadge() {
    def hist = state.historyFullness ?: []
    if (hist.size() == 0) return "<span style='color:grey;'>Pending Truck Dump Data</span>"
    int avgFill = Math.round(hist.sum() / hist.size()) as Integer
    if (avgFill <= 25) return "<b style='color:#27ae60;'>A+ (Eco-Warrior)</b>"
    if (avgFill <= 50) return "<b style='color:#2ecc71;'>A (Great)</b>"
    if (avgFill <= 75) return "<b style='color:#f1c40f;'>B (Average)</b>"
    if (avgFill <= 99) return "<b style='color:#e67e22;'>C (High)</b>"
    return "<b style='color:#c0392b;'>D/F (Max)</b>"
}

// ------------------------------------------------------------------------------
// STRICT ONE-WAY STATE MACHINE & TRUE 3D TRACKING
// ------------------------------------------------------------------------------

def calibrateSpatialBaseline() {
    if (!binMultiSensor) return
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (xyz) {
        def axes = ["x": xyz.x, "y": xyz.y, "z": xyz.z]
        def dominantAxis = axes.max { Math.abs(it.value as Integer) }.key
        state.activeAxis = dominantAxis
        state.baselineValue = axes[dominantAxis]
        state.baselineX = xyz.x as Integer
        state.baselineY = xyz.y as Integer
        state.baselineZ = xyz.z as Integer
        state.isSensorDead = false 
        logAction("SYSTEM: 3D Calibration complete. Locked to global [${dominantAxis.toUpperCase()}] axis for Truck Dumps.")
    }
}

def getSurfaceProfile() {
    def profile = [transitTime: 10, transitTilt: 150, lidTilt: 600]
    if (settings.drivewaySurface == "Smooth Pavement") {
        profile = [transitTime: 8, transitTilt: 120, lidTilt: 600]
    } else if (settings.drivewaySurface == "Mixed / Patchy") {
        profile = [transitTime: 12, transitTilt: 150, lidTilt: 600]
    } else if (settings.drivewaySurface == "Fine Gravel") {
        profile = [transitTime: 15, transitTilt: 180, lidTilt: 600]
    } else if (settings.drivewaySurface == "Chunky / Rough Gravel") {
        profile = [transitTime: 20, transitTilt: 220, lidTilt: 600]
    } else if (settings.drivewaySurface == "Custom (Manual Overrides)") {
        profile.transitTime = settings.transitMinTime ?: 10
        profile.transitTilt = settings.transitTiltThreshold ?: 150
        profile.lidTilt = settings.lidOpenThreshold ?: 600
    }
    return profile
}

def binMoveActiveHandler(evt) {
    state.isSensorDead = false
    state.motionStartTime = now()
    state.maxRelativeDevDuringMotion = 0
    state.wasFlippedDuringMotion = false
    
    if (state.lastKnownXYZ) {
        state.restX = state.lastKnownXYZ.x
        state.restY = state.lastKnownXYZ.y
        state.restZ = state.lastKnownXYZ.z
    }
}

def axisSpatialHandler(evt) {
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (!xyz) return
    
    if (!state.motionStartTime) {
        state.lastKnownXYZ = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
        return
    }
    
    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer
    
    int rX = state.restX != null ? state.restX : (state.baselineX ?: 0)
    int rY = state.restY != null ? state.restY : (state.baselineY ?: 0)
    int rZ = state.restZ != null ? state.restZ : (state.baselineZ ?: 0)
    
    int devX = Math.abs(curX - rX)
    int devY = Math.abs(curY - rY)
    int devZ = Math.abs(curZ - rZ)
    int maxRelative = Math.max(devX, Math.max(devY, devZ))
    
    if (maxRelative > (state.maxRelativeDevDuringMotion ?: 0)) {
        state.maxRelativeDevDuringMotion = maxRelative
    }

    boolean isFlipped = false
    if (state.activeAxis && state.baselineValue != null) {
        int currentDom = xyz[state.activeAxis] as Integer
        int baselineDom = state.baselineValue as Integer
        isFlipped = (baselineDom > 0 && currentDom < -500) || (baselineDom < 0 && currentDom > 500)
        if (isFlipped) state.wasFlippedDuringMotion = true
    }

    if (isFlipped && (state.binStatus == "curb_full" || state.binStatus == "curb_missed")) {
        if (!state.isCurrentlyDumped) {
            state.isCurrentlyDumped = true
            processTruckDump()
        }
    } else if (!isFlipped) {
        state.isCurrentlyDumped = false
    }
}

def binMoveInactiveHandler(evt) {
    if (settings.enableFallenBin) runIn(300, "fallenBinCheck", [overwrite: true])

    if (!state.motionStartTime) return
    
    long durationMs = now() - state.motionStartTime
    long durationSec = durationMs / 1000
    
    def profile = getSurfaceProfile()
    int reqTransitTime = profile.transitTime
    int reqTransitTilt = profile.transitTilt
    int lidThresh = profile.lidTilt
    
    int maxTilt = state.maxRelativeDevDuringMotion ?: 0
    boolean isFlipped = state.wasFlippedDuringMotion ?: false
    
    state.motionStartTime = null 
    
    logAction("Motion Event Ended: Duration: ${durationSec}s | Max Relative Tilt: ${maxTilt} | Flipped: ${isFlipped}")
    
    if (isFlipped && (state.binStatus == "curb_full" || state.binStatus == "curb_missed")) return
    
    if (maxTilt >= lidThresh) {
        if (isTrackingAllowed()) {
            int opens = (state.lidOpens ?: 0) + 1
            state.lidOpens = opens
            logAction("Action Processed: Trash Toss (Lid Flipped 90°). Capacity updated to (${opens}).")
        } else {
            logAction("Action Processed: Trash Toss detected, but ignored (Raccoon Filter).")
        }
        return
    }

    if (durationSec >= reqTransitTime && maxTilt >= reqTransitTilt) {
        if (isTrackingAllowed()) {
            logAction("Action Processed: Sustained Transit to/from Curb.")
            processValidTransit()
        } else {
            logAction("Action Processed: Transit detected, but ignored (Raccoon Filter).")
        }
        return
    }
    
    logAction("Action Processed: Ignored bump/wind. (Relative tilt of ${maxTilt} did not meet thresholds).")
}

def fallenBinCheck() {
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (!xyz || !state.baselineValue || !state.activeAxis) return
    
    int curDom = xyz[state.activeAxis] as Integer
    
    // If the dominant axis is reading near 0, gravity is pulling sideways (It fell over 90 degrees)
    if (Math.abs(curDom) < 400 && !state.isCurrentlyDumped) {
        logAction("SEVERE WEATHER ALERT: Bin has fallen over!")
        sendAlert("SEVERE WEATHER ALERT: Your outdoor trash bin has been knocked over.")
    }
}

def processTruckDump() {
    logAction("AUTOMATION: 180-Degree Flip Detected. Trash Dumped!")
    
    if (state.binStatus == "curb_missed" && state.missedTime) {
        long hrsLate = (now() - state.missedTime) / 3600000
        state.lastMissedDuration = hrsLate
        def oldMissed = state.lifetimeMissedHours ?: 0.0
        state.lifetimeMissedHours = oldMissed + hrsLate
        sendAlert("FINANCIAL TRACKER: Your missed trash was finally collected ${hrsLate} hours late. Ledger updated.")
        state.missedTime = null
    }
    
    int maxOpens = settings.estimatedMaxOpens ?: 8
    int currentOpens = state.lidOpens ?: 0
    int fillPct = Math.min(Math.round((currentOpens / maxOpens) * 100), 100)
    def histFull = state.historyFullness ?: []
    histFull.add(0, fillPct)
    if (histFull.size() > 10) histFull = histFull[0..9]
    state.historyFullness = histFull
    logAction("Eco-Telemetry: Bin was dumped at ${fillPct}% capacity.")
    
    recordTelemetryTime("historyEmptied")
    state.binStatus = "curb_emptied"
    
    if (enableHoaNag && hoaNagTime) runIn((hoaNagTime as Integer) * 3600, "hoaNagHandler")
    
    updateSchedule()
    
    def msg = ttsEmptiedText ?: "The garbage truck has emptied your bin!"
    sendAlert(msg)
    if (ttsSpeakers && ttsEmptiedText) playTTS(ttsSpeakers, ttsEmptiedText)
    if (zoozChimes && zoozSoundEmptied != null) playZoozChime(zoozSoundEmptied)
}

def processValidTransit() {
    if (state.binStatus == "house") {
        logAction("AUTOMATION: Bin taken to curb.")
        recordTelemetryTime("historyPutOut")
        
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        state.binStatus = "curb_full" 
        
        sendAlert("Trash movement detected. Logged at curb.")
    }
    else if (state.binStatus == "curb_full") {
        logAction("AUTOMATION: Bin shifted at curb. Ignored to prevent false return state.")
    }
    else if (state.binStatus == "curb_emptied" || state.binStatus == "curb_missed") {
        logAction("AUTOMATION: Bin returned to house.")
        recordTelemetryTime("historyReturned")
        state.binStatus = "house" 
        state.lidOpens = 0 
        unschedule("hoaNagHandler")
        
        def msg = ttsReturnedText ?: "The trash bin has been returned to the house."
        sendAlert(msg)
        if (ttsSpeakers && ttsReturnedText) playTTS(ttsSpeakers, ttsReturnedText)
        if (zoozChimes && zoozSoundReturned != null) playZoozChime(zoozSoundReturned)
    }
}

def ackHandler(evt) {
    if (state.isNagging) {
        state.isNagging = false
        state.binStatus = "curb_full"
        logAction("System Acknowledged: Switch turned OFF.")
    }
}

String getHumanReadableStatus() {
    if (!trashDays || !pickupTime || reminderOffset == null) return "Waiting for schedule configuration."
    if (trashSwitch && trashSwitch.currentValue("switch") == "on" || state.isNagging) return "<span style='color:red;'><b>Pending:</b></span> Trash needs to be taken out."
    if (state.binStatus == "curb_full") return "<span style='color:#e67e22;'><b>At Curb:</b></span> Waiting for garbage truck."
    if (state.binStatus == "curb_emptied") return "<span style='color:#27ae60;'><b>Emptied:</b></span> Waiting to be returned to house."
    if (state.binStatus == "curb_missed") return "<span style='color:#c0392b;'><b>MISSED PICKUP:</b></span> Truck did not arrive."
    return "Idle. Waiting for next scheduled reminder."
}

// ------------------------------------------------------------------------------
// PREDICTIVE SCHEDULING ENGINE
// ------------------------------------------------------------------------------

boolean isHolidayShiftRequired(Calendar targetPickup) {
    if (!settings.autoHoliday || !settings.selectedHolidays) return false
    
    int year = targetPickup.get(Calendar.YEAR)
    def tz = location.timeZone ?: TimeZone.getDefault()
    def validHolidays = settings.selectedHolidays as List
    
    def holidayDates = []
    
    if (validHolidays.contains("New Year's Day")) {
        def ny = Calendar.getInstance(tz); ny.set(year, Calendar.JANUARY, 1, 0, 0, 0); ny.set(Calendar.MILLISECOND, 0)
        holidayDates << ny
    }
    
    if (validHolidays.contains("Memorial Day")) {
        def mem = Calendar.getInstance(tz); mem.set(year, Calendar.MAY, 31, 0, 0, 0); mem.set(Calendar.MILLISECOND, 0)
        while(mem.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { mem.add(Calendar.DAY_OF_MONTH, -1) }
        holidayDates << mem
    }
    
    if (validHolidays.contains("Independence Day")) {
        def ind = Calendar.getInstance(tz); ind.set(year, Calendar.JULY, 4, 0, 0, 0); ind.set(Calendar.MILLISECOND, 0)
        holidayDates << ind
    }
    
    if (validHolidays.contains("Labor Day")) {
        def lab = Calendar.getInstance(tz); lab.set(year, Calendar.SEPTEMBER, 1, 0, 0, 0); lab.set(Calendar.MILLISECOND, 0)
        while(lab.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { lab.add(Calendar.DAY_OF_MONTH, 1) }
        holidayDates << lab
    }
    
    if (validHolidays.contains("Thanksgiving")) {
        def tg = Calendar.getInstance(tz); tg.set(year, Calendar.NOVEMBER, 1, 0, 0, 0); tg.set(Calendar.MILLISECOND, 0)
        int thursdays = 0
        while(thursdays < 4) {
            if (tg.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) thursdays++
            if (thursdays < 4) tg.add(Calendar.DAY_OF_MONTH, 1)
        }
        holidayDates << tg
    }
    
    if (validHolidays.contains("Christmas")) {
        def xmas = Calendar.getInstance(tz); xmas.set(year, Calendar.DECEMBER, 25, 0, 0, 0); xmas.set(Calendar.MILLISECOND, 0)
        holidayDates << xmas
    }
    
    def weekStart = targetPickup.clone()
    while(weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        weekStart.add(Calendar.DAY_OF_MONTH, -1)
    }
    weekStart.set(Calendar.HOUR_OF_DAY, 0)
    weekStart.set(Calendar.MINUTE, 0)
    weekStart.set(Calendar.SECOND, 0)
    
    def targetEnd = targetPickup.clone()
    targetEnd.set(Calendar.HOUR_OF_DAY, 23)
    targetEnd.set(Calendar.MINUTE, 59)
    
    for (cal in holidayDates) {
        if (cal.getTimeInMillis() >= weekStart.getTimeInMillis() && cal.getTimeInMillis() <= targetEnd.getTimeInMillis()) {
            return true
        }
    }
    return false
}

def updateSchedule() {
    checkSensorHealth()
    if (!trashDays || !pickupTime || reminderOffset == null) return
    
    try {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def now = new Date()
        
        def cal = Calendar.getInstance(tz)
        cal.setTime(now)
        int currentDayNum = cal.get(Calendar.DAY_OF_WEEK) 
        
        def parsedTime = timeToday(pickupTime, tz)
        def pCal = Calendar.getInstance(tz)
        pCal.setTime(parsedTime)
        
        int pHour = pCal.get(Calendar.HOUR_OF_DAY)
        int pMin = pCal.get(Calendar.MINUTE)
        
        if (settings.usePredictiveTiming && state.historyEmptied && state.historyEmptied.size() >= 2 && !state.isSensorDead) {
            def avgMins = Math.round(state.historyEmptied.sum() / state.historyEmptied.size()) as Integer
            pHour = avgMins.intdiv(60)
            pMin = avgMins % 60
            state.predictiveActive = true
            logAction("AI Timing: Adjusted target to ${pHour}:${pMin} based on telemetry.")
        } else {
            state.predictiveActive = false
        }
        
        def dayMap = ["Sunday":1, "Monday":2, "Tuesday":3, "Wednesday":4, "Thursday":5, "Friday":6, "Saturday":7]
        long nextPickupMs = Long.MAX_VALUE
        boolean shiftApplied = false
        
        trashDays.each { dayName ->
            int targetDay = dayMap[dayName]
            int daysToAdd = targetDay - currentDayNum
            if (daysToAdd < 0) daysToAdd += 7
            
            def testCal = Calendar.getInstance(tz)
            testCal.setTime(now)
            testCal.add(Calendar.DAY_OF_YEAR, daysToAdd)
            testCal.set(Calendar.HOUR_OF_DAY, pHour)
            testCal.set(Calendar.MINUTE, pMin)
            testCal.set(Calendar.SECOND, 0)
            
            if (daysToAdd == 0 && testCal.getTimeInMillis() <= now.time) {
                testCal.add(Calendar.DAY_OF_YEAR, 7)
            }
            
            if (settings.autoHoliday && isHolidayShiftRequired(testCal)) {
                testCal.add(Calendar.DAY_OF_YEAR, 1)
                shiftApplied = true
            } else if (state.holidayShift) {
                testCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            if (testCal.getTimeInMillis() < nextPickupMs) {
                nextPickupMs = testCal.getTimeInMillis()
            }
        }
        
        state.autoHolidayTriggered = shiftApplied
        
        def pickupDate = new Date(nextPickupMs)
        def offsetMs = (reminderOffset.toDouble() * 3600000).toLong()
        def reminderDate = new Date(nextPickupMs - offsetMs)
        
        state.nextPickupStr = pickupDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextReminderStr = reminderDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextPickupMs = nextPickupMs
        
        unschedule("triggerReminder")
        unschedule("autoResetHandler")
        
        if (reminderDate.time > now.time) {
            runOnce(reminderDate, "triggerReminder", [overwrite: true])
        }
        
        def resetDate = new Date(nextPickupMs + 7200000) 
        runOnce(resetDate, "autoResetHandler", [overwrite: true])
        
    } catch (e) {
        log.error "Schedule Calculation Error: ${e}"
    }
}

def triggerReminder() {
    checkSensorHealth()

    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        logAction("Reminder skipped: Bin was already taken out early.")
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        return
    }

    if (state.isSensorDead) {
        logAction("SENSOR OFFLINE: Bypassing spatial checks. Firing dumb reminder.")
    }

    if (trashSwitch) trashSwitch.on()
    state.isNagging = true
    if (state.binStatus != "curb_missed") state.binStatus = "house" 
    
    String finalMsg = ttsReminderText ?: "Reminder, it is time to take the trash out to the road."
    if (checkRecyclingWeek()) {
        finalMsg = settings.ttsRecycleText ?: "Reminder, it is time to take the trash and the recycling out to the road."
    }
    
    sendAlert(finalMsg)
    if (ttsSpeakers) playTTS(ttsSpeakers, finalMsg)
    if (zoozChimes && zoozSoundReminder != null) playZoozChime(zoozSoundReminder)
}

def nagCheck() {
    checkSensorHealth()

    if (binMultiSensor && binMultiSensor.currentValue("battery") != null) {
        int batt = binMultiSensor.currentValue("battery") as Integer
        if (batt <= 15) {
            def tz = location.timeZone ?: TimeZone.getDefault()
            def todayStr = new Date().format("yyyy-MM-dd", tz)
            if (state.lastBatteryWarningDate != todayStr) {
                sendAlert("WARNING: Your outdoor trash bin sensor battery is very low (${batt}%).")
                logAction("Sent daily low battery warning.")
                state.lastBatteryWarningDate = todayStr
            }
        } else {
            if (state.lastBatteryWarningDate != null) state.lastBatteryWarningDate = null
        }
    }

    updateHygiene()

    if (!enableNag || !state.isNagging) return
    if (trashSwitch && trashSwitch.currentValue("switch") == "off") {
        state.isNagging = false
        return
    }
    
    if (!isNagAllowed()) {
        logAction("Nag skipped: Current mode restricted.")
        return
    }
    
    String finalMsg = ttsReminderText ?: "Don't forget the trash!"
    if (checkRecyclingWeek()) {
        finalMsg = settings.ttsRecycleText ?: "Don't forget the trash and the recycling!"
    }
    
    sendAlert("NAG: " + finalMsg)
    if (ttsSpeakers) playTTS(ttsSpeakers, "Nag. " + finalMsg)
    if (zoozChimes && zoozSoundReminder != null) playZoozChime(zoozSoundReminder)
}

def autoResetHandler() {
    if (trashSwitch) trashSwitch.off()
    state.isNagging = false
    
    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        logAction("ALERT: Missed Pickup detected.")
        sendAlert("ALERT: The garbage truck missed your scheduled pickup!")
    } else {
        state.binStatus = "house" 
        state.lidOpens = 0
    }
    
    if (state.holidayShift) {
        state.holidayShift = false
    }
    
    updateSchedule()
}

// ------------------------------------------------------------------------------
// AUDIO & 1-TO-1 MOTION HELPER ENGINE
// ------------------------------------------------------------------------------

def room1MotionHandler(evt) { state.lastMotionRoom1 = now() }
def room2MotionHandler(evt) { state.lastMotionRoom2 = now() }
def room3MotionHandler(evt) { state.lastMotionRoom3 = now() }
def room4MotionHandler(evt) { state.lastMotionRoom4 = now() }
def room5MotionHandler(evt) { state.lastMotionRoom5 = now() }

def isSpeakerMotionActive(speaker) {
    boolean isMapped = false
    boolean hasMotion = false
    
    for (int i = 1; i <= 7; i++) {
        def mappedSpeaker = settings["room${i}Speaker"]
        if (mappedSpeaker) {
            def mappedList = mappedSpeaker instanceof List ? mappedSpeaker : [mappedSpeaker]
            if (mappedList.any { it.id == speaker.id }) {
                isMapped = true
                if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == i.toString()) {
                    hasMotion = true
                }
                if (!hasMotion) {
                    def motion = settings["room${i}Motion"]
                    if (!motion) {
                        hasMotion = true 
                    } else {
                        def mList = motion instanceof List ? motion : [motion]
                        if (mList.any { it?.currentValue("motion") == "active" }) {
                            state."lastMotionRoom${i}" = now()
                            hasMotion = true
                        } else {
                            def lastTime = state."lastMotionRoom${i}"
                            if (lastTime) {
                                long timeoutMillis = (settings.audioMotionTimeout ?: 5) * 60 * 1000
                                if ((now() - lastTime) <= timeoutMillis) {
                                    hasMotion = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (!isMapped) return true 
    return hasMotion
}

def playTTS(speakers, msg, forcePlay = false) {
    if (!speakers || !msg) return
    if (!forcePlay && !isAudioAllowed()) {
        logInfo("Skipping TTS: Current mode restricts audio announcements.")
        return
    }
    def msgList = msg.split(",").collect{ it.trim() }
    def selectedMsg = msgList[new Random().nextInt(msgList.size())]

    def devList = speakers instanceof List ? speakers : [speakers]
    devList.each { dev ->
        if (forcePlay || isSpeakerMotionActive(dev)) {
            try { 
                dev.speak(selectedMsg) 
                logInfo("Played TTS on ${dev.displayName}: ${selectedMsg}")
            } catch (e) { log.error "Failed to play TTS: ${e}" }
        } else {
            logInfo("Skipping TTS on ${dev.displayName}: No recent motion.")
        }
    }
}

def playZoozChime(soundNum, forcePlay = false) {
    if (!settings.zoozChimes || soundNum == null) return
    if (!forcePlay && !isAudioAllowed()) {
        logInfo("Skipping Zooz Chime: Current mode restricts audio announcements.")
        return
    }
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    int playCount = 0
    settings.zoozChimes.each { chime ->
        if (forcePlay || isSpeakerMotionActive(chime)) {
            if (playCount > 0) pauseExecution(1000)
            try {
                if (chime.hasCommand("playSound") && trackNum != null) {
                    chime.playSound(trackNum)
                } else if (chime.hasCommand("playTrack")) {
                    chime.playTrack(soundNum.toString())
                } else if (chime.hasCommand("chime") && trackNum != null) {
                    chime.chime(trackNum)
                } else {
                    log.error "${chime.displayName} does not support standard audio commands."
                }
                playCount++
            } catch (e) {
                log.error "${chime.displayName} failed to play sound: ${e.message ?: e}"
            }
        } else {
            logInfo("Skipping Zooz Chime on ${chime.displayName}: No recent motion.")
        }
    }
}

def sendAlert(msg) {
    if (notifyDevice && isPushAllowed()) {
        notifyDevice*.deviceNotification(msg)
    } else if (!isPushAllowed()) {
        logInfo("Skipping Push Notification: Current mode restricts push alerts.")
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
