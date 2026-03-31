/**
 * Work Day & Holiday Schedule
 */

definition(
    name: "Work Day & Holiday Schedule",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Automates a virtual switch based on a standard work week, hours, standard Federal holidays, and a custom date manager.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "manageDatesPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Work Day & Holiday Schedule</b>", install: true, uninstall: true) {
        
        section("<b>1. Virtual Switch Target</b>") {
            paragraph "Select the virtual switch that will turn ON during work hours and OFF during holidays/off-hours."
            input "workSwitch", "capability.switch", title: "Work Mode Switch", required: true, multiple: false
        }
        
        section("<b>2. Standard Work Week</b>") {
            input "workDays", "enum", title: "Active Work Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: true, defaultValue: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
            
            input "allDayWork", "bool", title: "Enable All Day (Ignore Start/End Times)", defaultValue: false, submitOnChange: true
            
            if (!settings.allDayWork) {
                input "startTime", "time", title: "Work Day Start Time", required: true
                input "endTime", "time", title: "Work Day End Time", required: true
            } else {
                paragraph "<i>Switch will remain ON for the entire 24-hour period on active work days.</i>"
            }
        }

        section("<b>3. Standard US Holidays</b>") {
            paragraph "<i>Select the holidays your workplace observes. The system automatically calculates their exact dates each year.</i>"
            input "observedHolidays", "enum", title: "<b>Select Holidays to Observe</b>", options: [
                "New Year's Day", 
                "Martin Luther King Jr. Day", 
                "Presidents' Day", 
                "Memorial Day", 
                "Juneteenth", 
                "Independence Day", 
                "Labor Day", 
                "Columbus Day", 
                "Veterans Day", 
                "Thanksgiving Day", 
                "Day After Thanksgiving (Black Friday)",
                "Christmas Eve",
                "Christmas Day"
            ], multiple: true, required: false
        }

        section("<b>4. Vacation Dates (PTO)</b>") {
            def sCount = state.customSpecific ? state.customSpecific.size() : 0
            
            paragraph "You currently have <b>${sCount}</b> Vacation Dates configured. <i>(Past dates are automatically removed overnight)</i>."
            
            href(name: "manageDatesLink", page: "manageDatesPage", title: "🗓️ Manage Vacation Dates", description: "Click here to add or remove specific PTO dates using the calendar.")
        }
        
        section("<b>System & Logging</b>") {
            input "logEnable", "bool", title: "Enable Info Logging", defaultValue: true
            input "forceSync", "button", title: "Evaluate Schedule Now"
        }
    }
}

def manageDatesPage() {
    dynamicPage(name: "manageDatesPage", title: "<b>Manage Vacation Dates</b>", install: false, uninstall: false) {
        
        if (!state.customSpecific) state.customSpecific = []

        section("<b>Active Vacation Dates</b>") {
            if (state.customSpecific.size() == 0) {
                paragraph "<i>No vacation dates currently configured.</i>"
            } else {
                def table = "<table style='width:100%; border-collapse: collapse; font-size: 14px;'>"
                table += "<tr style='background-color:#eee;'><th style='padding:5px; text-align:left;'>Scheduled PTO (YYYY-MM-DD)</th></tr>"
                state.customSpecific.sort().each { date -> 
                    table += "<tr><td style='padding:5px; border-bottom:1px solid #ccc;'>📅 ${date}</td></tr>" 
                }
                table += "</table>"
                paragraph table
            }
        }

        section("<b>Add a Vacation Date</b>") {
            paragraph "Use the calendar picker to select a date you will be off."
            input "newSpecificDate", "date", title: "Select Date", required: false
            input "addSpecificBtn", "button", title: "➕ Add Vacation Date"
        }

        section("<b>Remove a Date</b>") {
            if (state.customSpecific.size() > 0) {
                input "dateToRemove", "enum", title: "Select a configured date to delete", options: state.customSpecific.sort(), required: false
                input "removeDateBtn", "button", title: "❌ Delete Selected Date"
            } else {
                paragraph "<i>No dates available to remove.</i>"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() {
    logInfo("Installed and initializing schedule.")
    if (!state.customSpecific) state.customSpecific = []
    initialize()
}

def updated() {
    logInfo("Updated configuration. Re-evaluating schedule.")
    unsubscribe()
    unschedule()
    if (!state.customSpecific) state.customSpecific = []
    initialize()
}

def initialize() {
    cleanupOldDates() // Sweep expired dates on boot/update
    
    // Check the schedule every 1 minute to ensure exact start/end times hit
    runEvery1Minute("evaluateSchedule")
    
    // Run the cleanup routine every day at 12:05 AM
    schedule("0 5 0 * * ?", cleanupOldDates)
    
    evaluateSchedule()
}

def appButtonHandler(btn) {
    if (btn == "forceSync") {
        logInfo("Manual evaluation triggered.")
        cleanupOldDates()
        evaluateSchedule()
        
    } else if (btn == "addSpecificBtn") {
        if (settings.newSpecificDate) {
            def list = state.customSpecific ?: []
            if (!list.contains(settings.newSpecificDate)) {
                list << settings.newSpecificDate
                state.customSpecific = list
                logInfo("Added vacation date: ${settings.newSpecificDate}")
            }
            app.updateSetting("newSpecificDate", [type: "date", value: ""])
        }
        
    } else if (btn == "removeDateBtn") {
        if (settings.dateToRemove) {
            def target = settings.dateToRemove
            def sList = state.customSpecific ?: []
            
            if (sList.contains(target)) sList.remove(target)
            
            state.customSpecific = sList
            logInfo("Removed custom date: ${target}")
            
            app.updateSetting("dateToRemove", [type: "enum", value: ""])
        }
    }
}

def cleanupOldDates() {
    if (!state.customSpecific) return
    
    def dfSpecific = new java.text.SimpleDateFormat("yyyy-MM-dd")
    dfSpecific.setTimeZone(location.timeZone)
    def todayStr = dfSpecific.format(new Date())
    
    def initialSize = state.customSpecific.size()
    
    // Lexical string comparison works perfectly for YYYY-MM-DD formats
    // This retains dates that are strictly greater than or equal to today
    def activeDates = state.customSpecific.findAll { it >= todayStr }
    
    if (activeDates.size() != initialSize) {
        state.customSpecific = activeDates
        logInfo("Cleaned up expired vacation dates. Remaining active dates: ${activeDates.size()}")
    }
}

def evaluateSchedule() {
    if (!workSwitch) return

    def dfDay = new java.text.SimpleDateFormat("EEEE")
    dfDay.setTimeZone(location.timeZone)
    def currentDay = dfDay.format(new Date())

    def isWorkDay = workDays?.contains(currentDay)
    def isOffDay = isHoliday()

    def targetState = "off"
    def reason = "Off Hours/Weekend"

    if (isOffDay) {
        targetState = "off"
        reason = "Holiday/Vacation Day"
    } else if (isWorkDay) {
        if (settings.allDayWork) {
            targetState = "on"
            reason = "Active Work Day (All-Day Mode)"
        } else {
            def currTime = now()
            def start = timeToday(startTime, location.timeZone).time
            def end = timeToday(endTime, location.timeZone).time

            if (currTime >= start && currTime <= end) {
                targetState = "on"
                reason = "Active Work Hours"
            } else {
                targetState = "off"
                reason = "Outside Work Hours"
            }
        }
    }

    def currentState = workSwitch.currentValue("switch")
    
    if (currentState != targetState) {
        logInfo("Schedule transition: Turning ${targetState.toUpperCase()} (${reason})")
        if (targetState == "on") {
            workSwitch.on()
        } else {
            workSwitch.off()
        }
    }
}

def isHoliday() {
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    
    int month = cal.get(Calendar.MONTH) // 0-based: Jan=0, Dec=11
    int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2, Thu=5
    
    def hList = settings.observedHolidays ?: []

    // 1. Dynamic US Holidays Matcher
    if (hList.contains("New Year's Day") && month == Calendar.JANUARY && dayOfMonth == 1) return true
    if (hList.contains("Martin Luther King Jr. Day") && month == Calendar.JANUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 15 && dayOfMonth <= 21) return true
    if (hList.contains("Presidents' Day") && month == Calendar.FEBRUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 15 && dayOfMonth <= 21) return true
    if (hList.contains("Memorial Day") && month == Calendar.MAY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 25) return true
    if (hList.contains("Juneteenth") && month == Calendar.JUNE && dayOfMonth == 19) return true
    if (hList.contains("Independence Day") && month == Calendar.JULY && dayOfMonth == 4) return true
    if (hList.contains("Labor Day") && month == Calendar.SEPTEMBER && dayOfWeek == Calendar.MONDAY && dayOfMonth <= 7) return true
    if (hList.contains("Columbus Day") && month == Calendar.OCTOBER && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 8 && dayOfMonth <= 14) return true
    if (hList.contains("Veterans Day") && month == Calendar.NOVEMBER && dayOfMonth == 11) return true
    if (hList.contains("Thanksgiving Day") && month == Calendar.NOVEMBER && dayOfWeek == Calendar.THURSDAY && dayOfMonth >= 22 && dayOfMonth <= 28) return true
    if (hList.contains("Day After Thanksgiving (Black Friday)") && month == Calendar.NOVEMBER && dayOfWeek == Calendar.FRIDAY && dayOfMonth >= 23 && dayOfMonth <= 29) return true
    if (hList.contains("Christmas Eve") && month == Calendar.DECEMBER && dayOfMonth == 24) return true
    if (hList.contains("Christmas Day") && month == Calendar.DECEMBER && dayOfMonth == 25) return true

    // 2. Custom Specific Dates Matcher (YYYY-MM-DD)
    def dfSpecific = new java.text.SimpleDateFormat("yyyy-MM-dd")
    dfSpecific.setTimeZone(location.timeZone)
    def todaySpecific = dfSpecific.format(new Date())
    
    if (state.customSpecific?.contains(todaySpecific)) return true

    return false
}

def logInfo(msg) {
    if (logEnable) log.info "${app.label}: ${msg}"
}
