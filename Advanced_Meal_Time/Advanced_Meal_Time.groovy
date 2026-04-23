/**
 * Advanced Meal Time
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Meal Time",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Automates meal time by monitoring seat sensors. Features 3D Spatial Vectoring, Kick Accumulators, Chef's Chair Interlock, and Family Lifestyle Metrics.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Meal Time</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            def sysStatus = state.mealTimeActive ? "<b style='color:#27ae60;'>ACTIVE (${state.activeMealType?.capitalize()} in Progress)</b>" : "<b style='color:#555;'>IDLE (Waiting for occupants)</b>"
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${sysStatus}</div>"

            def reqChairs = settings.minChairs ?: 2
            def reqVibes = settings.minTotalVibes ?: 5
            def occCount = state.occupiedCount ?: 0
            def totalVibes = state.totalVibes ?: 0
            
            def chairStr = state.mealTimeActive ? "<b style='color:green;'>${occCount} Occupied</b>" : "${occCount} Occupied (Requires ${reqChairs})"
            def vibeStr = state.mealTimeActive ? "<b style='color:green;'>${totalVibes} Human Movements</b>" : "${totalVibes} Human Movements (Requires ${reqVibes})"
            
            def modeStatus = isModeAllowed() ? "<b style='color:green;'>Allowed</b>" : "<b style='color:red;'>Restricted</b>"
            
            def currentWin = getCurrentTimeWindow()
            def timeStatus = currentWin != "none" ? "<b style='color:green;'>${currentWin.capitalize()} Window Active</b>" : "<b style='color:#e67e22;'>Outside Meal Windows</b>"
            
            def guestActive = (guestSwitch && guestSwitch.currentValue("switch") == "on")
            def timeoutMins = guestActive ? (settings.guestTimeout ?: 45) : (settings.inactiveTimeout ?: 5)
            def guestStr = guestActive ? "<b style='color:#8e44ad;'>ACTIVE (${timeoutMins} min timeout)</b>" : "Inactive (${timeoutMins} min timeout)"
            
            def chefStatusStr = "<span style='color:#555;'>Disabled (Anyone can trigger)</span>"
            if (settings.chefChairSelect && settings.chefChairSelect != "None") {
                if (state.chefIsSeated) chefStatusStr = "<b style='color:green;'>Seated (${settings.chefChairSelect})</b>"
                else chefStatusStr = "<b style='color:#e67e22;'>Waiting for Chef (${settings.chefChairSelect})</b>"
            }
            
            def countdownStr = state.shutoffPending ? "<b style='color:#c0392b;'>Ending Soon (Timer Running)</b>" : "Cleared"
            def liveStats = state.dashboardSeatStats ?: "No recent data"
            def calibStr = state.chairBaselines ? "<b style='color:green;'>3D Spatial Vectors Locked</b>" : "<span style='color:#555;'>No 3D Data / Standard Sensors</span>"

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
                    <tr><td colspan="4" class="dash-sub">Trigger Conditions & Overrides</td></tr>
                    <tr><td class="dash-hl">Operating Mode</td><td colspan="3" class="dash-val">${modeStatus}</td></tr>
                    <tr><td class="dash-hl">Current Meal Window</td><td colspan="3" class="dash-val">${timeStatus}</td></tr>
                    <tr><td class="dash-hl">Guest Mode Override</td><td colspan="3" class="dash-val">${guestStr}</td></tr>
                    <tr><td class="dash-hl">Chef's Chair Interlock</td><td colspan="3" class="dash-val">${chefStatusStr}</td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Family Lifestyle Metrics (Resets Sundays)</td></tr>
                    <tr><td class="dash-hl">Meals Recorded This Week</td><td colspan="3" class="dash-val" style="font-size:16px;"><b>${state.weeklyMealCount ?: 0}</b></td></tr>
                    <tr><td class="dash-hl">Average Meal Duration</td><td colspan="3" class="dash-val" style="font-size:16px; color:#2980b9;"><b>${getAverageMealDurationStr()}</b></td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Physical Tracking (Rolling Window)</td></tr>
                    <tr><td class="dash-hl">Current Seat Status</td><td colspan="3" class="dash-val">${chairStr}</td></tr>
                    <tr><td class="dash-hl">Accumulated Energy</td><td colspan="3" class="dash-val">${vibeStr}</td></tr>
                    <tr><td class="dash-hl">Live Sensor Data</td><td colspan="3" class="dash-val" style="font-family:monospace; font-size:12px;">${liveStats}</td></tr>
                    <tr><td class="dash-hl">Hardware State</td><td colspan="3" class="dash-val">${calibStr}</td></tr>
                    <tr><td class="dash-hl">Auto-End Countdown</td><td colspan="3" class="dash-val">${countdownStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            paragraph "<br>"
            input "btnCalibrateChairs", "button", title: "🪑 Calibrate Empty Chairs (For Samsung 3D Sensors)"
            input "btnResetStats", "button", title: "📊 Reset Weekly Meal Stats"
            input "btnForceBreakfast", "button", title: "▶️ Force Start Breakfast"
            input "btnForceDinner", "button", title: "▶️ Force Start Dinner"
            input "btnForceEnd", "button", title: "⏹️ Force End Meal Time"
        }

        section("<b>Seat Monitoring Hardware</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Select up to 4 sensors mounted to your dining chairs. The app will automatically route them to the 3D physics engine if they support it.</div>"
            input "chair1", "capability.accelerationSensor", title: "Chair Sensor 1", required: false
            input "chair2", "capability.accelerationSensor", title: "Chair Sensor 2", required: false
            input "chair3", "capability.accelerationSensor", title: "Chair Sensor 3", required: false
            input "chair4", "capability.accelerationSensor", title: "Chair Sensor 4", required: false
        }
        
        section("<b>The 'Chef's Chair' Interlock</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Homework Filter:</b> Prevent the meal from starting until a specific chair (e.g., Mom or Dad) is occupied. Kids can sit at the table all afternoon without triggering the house.</div>"
            input "chefChairSelect", "enum", title: "Designate the Chef's Chair", options: ["None", "Chair 1", "Chair 2", "Chair 3", "Chair 4"], defaultValue: "None", required: true, submitOnChange: true
        }
        
        section("<b>Anti-False Alarm Filters (Physics & Accumulation)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Samsung 3D Mode (The 'Roomba' Filter):</b> If using Samsung Multi-Sensors, the app checks the 3D spatial shift. Micro-vibrations are ignored.</div>"
            input "spatialThreshold", "number", title: "Samsung 3D Tilt Threshold", description: "Minimum spatial shift required to count as human weight (Default: 30).", defaultValue: 30, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Accumulator (The 'Kick' Filter):</b> Prevents accidental bumps from triggering a meal by counting multiple valid movements over a rolling window.</div>"
            input "vibeWindow", "number", title: "Tracking Window (Minutes)", defaultValue: 5, required: true
            input "minChairs", "number", title: "Minimum Occupied Chairs to Trigger", defaultValue: 2, required: true
            input "minVibesPerChair", "number", title: "Min Events per Chair", description: "How many times must a chair register weight/movement to be 'Occupied'? (Default: 2)", defaultValue: 2, required: true
            input "minTotalVibes", "number", title: "Min Total Events Combined", description: "Total valid events required across all occupied chairs. (Default: 5)", defaultValue: 5, required: true
        }

        section("<b>Timeout & Guest Override</b>") {
            input "inactiveTimeout", "number", title: "Standard Empty Table Timeout (Minutes)", description: "How long must ALL chairs remain still before Meal Time ends? (Default: 5)", defaultValue: 5, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Guest Mode Filter:</b> Extends the timeout so lights don't shut off on guests getting dessert.</div>"
            input "guestSwitch", "capability.switch", title: "Guest Mode Virtual Switch", required: false
            input "guestTimeout", "number", title: "Guest Mode Empty Table Timeout (Minutes)", description: "Extended timeout duration (Default: 45)", defaultValue: 45, required: true
        }
        
        section("<b>Global Mode Restrictions</b>") {
            input "allowedModes", "mode", title: "Allowed Modes for Automation (Leave blank for all)", multiple: true, required: false
        }

        section("<b>🍳 BREAKFAST Configuration</b>") {
            input "bfastStartTime", "time", title: "Breakfast Start Time", required: false
            input "bfastEndTime", "time", title: "Breakfast End Time", required: false
            
            paragraph "<b>Breakfast Actions</b>"
            input "bfastMealSwitch", "capability.switch", title: "Virtual Breakfast Switch (Turns ON)", required: false
            input "bfastDndSwitch", "capability.switch", title: "Do Not Disturb Switch (Turns ON)", required: false
            input "bfastOnLights", "capability.switch", title: "Lights to Turn ON", multiple: true, required: false
            input "bfastOffLights", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
            input "bfastLockDoors", "capability.lock", title: "Doors to Lock", multiple: true, required: false
            input "bfastPauseSpeakers", "capability.musicPlayer", title: "Speakers/Media to Pause", multiple: true, required: false
        }
        
        section("<b>🍽️ DINNER Configuration</b>") {
            input "dinnerStartTime", "time", title: "Dinner Start Time", required: false
            input "dinnerEndTime", "time", title: "Dinner End Time", required: false
            
            paragraph "<b>Dinner Actions</b>"
            input "dinnerMealSwitch", "capability.switch", title: "Virtual Dinner Switch (Turns ON)", required: false
            input "dinnerDndSwitch", "capability.switch", title: "Do Not Disturb Switch (Turns ON)", required: false
            input "dinnerOnLights", "capability.switch", title: "Lights to Turn ON", multiple: true, required: false
            input "dinnerOffLights", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
            input "dinnerLockDoors", "capability.lock", title: "Doors to Lock", multiple: true, required: false
            input "dinnerPauseSpeakers", "capability.musicPlayer", title: "Speakers/Media to Pause", multiple: true, required: false
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
    if (state.mealTimeActive == null) state.mealTimeActive = false
    if (state.shutoffPending == null) state.shutoffPending = false
    if (state.activeMealType == null) state.activeMealType = "none"
    if (state.chefIsSeated == null) state.chefIsSeated = false
    if (!state.seatHistory) state.seatHistory = [:]
    if (!state.chairBaselines) state.chairBaselines = [:]
    
    // Lifestyle Metrics Initialization
    if (state.weeklyMealCount == null) state.weeklyMealCount = 0
    if (state.weeklyMealDurationMs == null) state.weeklyMealDurationMs = 0
    
    // Schedule Auto-Reset for Sunday at Midnight
    schedule("0 0 0 ? * SUN", resetWeeklyStats)
    
    // Auto-Detect and Subscribe based on capabilities
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c) {
            // We always subscribe to acceleration to monitor the LIVE state for keeping meals active
            subscribe(c, "acceleration", liveStateHandler)
            
            if (c.hasAttribute("threeAxis")) {
                subscribe(c, "threeAxis", chairAxisHandler)
                logAction("HARDWARE: ${c.displayName} auto-detected as Samsung 3D Sensor.")
            } else {
                subscribe(c, "acceleration", standardSeatHandler)
                logAction("HARDWARE: ${c.displayName} auto-detected as Standard Vibration Sensor.")
            }
        }
    }
    
    evaluateSeats() 
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        evaluateSeats()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnCalibrateChairs") {
        calibrateChairs()
    } else if (btn == "btnResetStats") {
        resetWeeklyStats()
    } else if (btn == "btnForceBreakfast") {
        logAction("MANUAL OVERRIDE: Breakfast Forced ON.")
        startMealTime("breakfast")
    } else if (btn == "btnForceDinner") {
        logAction("MANUAL OVERRIDE: Dinner Forced ON.")
        startMealTime("dinner")
    } else if (btn == "btnForceEnd") {
        logAction("MANUAL OVERRIDE: Meal Time Forced OFF.")
        endMealTime()
    }
}

// ------------------------------------------------------------------------------
// HARDWARE CALIBRATION
// ------------------------------------------------------------------------------

def calibrateChairs() {
    def baselines = [:]
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c && c.hasAttribute("threeAxis")) {
            def xyz = c.currentValue("threeAxis")
            if (xyz) {
                baselines[c.id] = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
                logAction("Calibrated 3D baseline for ${c.displayName}: ${xyz}")
            }
        }
    }
    state.chairBaselines = baselines
    logAction("SYSTEM: All 3D spatial baselines locked in.")
}

// ------------------------------------------------------------------------------
// LIFESTYLE METRICS HELPERS
// ------------------------------------------------------------------------------

def resetWeeklyStats() {
    state.weeklyMealCount = 0
    state.weeklyMealDurationMs = 0
    logAction("SYSTEM: Weekly Family Lifestyle metrics have been reset.")
}

String getAverageMealDurationStr() {
    if (!state.weeklyMealCount || state.weeklyMealCount == 0) return "N/A"
    long avgMs = (state.weeklyMealDurationMs ?: 0) / state.weeklyMealCount
    long totalMins = avgMs / 60000
    long hrs = totalMins / 60
    long mins = totalMins % 60
    if (hrs > 0) return "${hrs}h ${mins}m"
    return "${mins}m"
}

// ------------------------------------------------------------------------------
// TIME & MODE HELPERS
// ------------------------------------------------------------------------------

boolean isModeAllowed() {
    if (!settings.allowedModes) return true
    return (settings.allowedModes as List).contains(location.mode)
}

boolean isTimeInWindow(startTimeStr, endTimeStr) {
    if (!startTimeStr || !endTimeStr) return false
    
    def t0 = timeToday(startTimeStr, location.timeZone)
    def t1 = timeToday(endTimeStr, location.timeZone)
    def now = new Date()
    
    if (t1.time < t0.time) { 
        return (now.time >= t0.time || now.time <= t1.time)
    } else {
        return (now.time >= t0.time && now.time <= t1.time)
    }
}

String getCurrentTimeWindow() {
    if (isTimeInWindow(settings.bfastStartTime, settings.bfastEndTime)) return "breakfast"
    if (isTimeInWindow(settings.dinnerStartTime, settings.dinnerEndTime)) return "dinner"
    return "none"
}

int getTimeoutSeconds() {
    if (settings.guestSwitch && settings.guestSwitch.currentValue("switch") == "on") {
        return (settings.guestTimeout ?: 45) * 60
    }
    return (settings.inactiveTimeout ?: 5) * 60
}

// ------------------------------------------------------------------------------
// SENSOR PHYSICS & TRACKING
// ------------------------------------------------------------------------------

def liveStateHandler(evt) {
    // A fallback check just in case it goes inactive, to trigger end-timers
    if (evt.value == "inactive") evaluateSeats()
}

def standardSeatHandler(evt) {
    // Only used if NOT a Samsung Sensor
    if (evt.value == "active") {
        recordValidEvent(evt.device.id)
    }
}

def chairAxisHandler(evt) {
    def c = evt.device
    def xyz = c.currentValue("threeAxis")
    if (!xyz) return

    def baselines = state.chairBaselines ?: [:]
    def base = baselines[c.id]
    if (!base) return // Need user to hit calibrate button

    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer

    int devX = Math.abs(curX - base.x)
    int devY = Math.abs(curY - base.y)
    int devZ = Math.abs(curZ - base.z)
    int maxDev = Math.max(devX, Math.max(devY, devZ))

    int thresh = settings.spatialThreshold ?: 30

    // Only count it if the spatial flex is large enough (ignoring roombas/walking)
    if (maxDev >= thresh) {
        recordValidEvent(c.id)
    }
}

def recordValidEvent(devId) {
    def history = state.seatHistory ?: [:]
    def devEvents = history[devId] ?: []
    
    long nowMs = now()
    
    // 2-Second Debounce: Prevents flooding the accumulator if they squirm heavily in one moment
    if (devEvents.size() > 0 && (nowMs - devEvents.last()) < 2000) return
    
    devEvents << nowMs
    
    if (devEvents.size() > 50) devEvents = devEvents.drop(devEvents.size() - 50)
    
    history[devId] = devEvents
    state.seatHistory = history
    
    evaluateSeats()
}

// ------------------------------------------------------------------------------
// MEAL LOGIC EVALUATION
// ------------------------------------------------------------------------------

def evaluateSeats() {
    def history = state.seatHistory ?: [:]
    long cutoff = now() - ((settings.vibeWindow ?: 5) * 60 * 1000)
    
    int occupiedChairsCount = 0
    int totalValidVibes = 0
    int liveActiveCount = 0
    
    boolean chefSeated = false
    def targetedChefChair = null
    
    // Identify the Chef's chair if configured
    if (settings.chefChairSelect == "Chair 1") targetedChefChair = chair1
    else if (settings.chefChairSelect == "Chair 2") targetedChefChair = chair2
    else if (settings.chefChairSelect == "Chair 3") targetedChefChair = chair3
    else if (settings.chefChairSelect == "Chair 4") targetedChefChair = chair4
    
    // If the feature is off (or the specific chair input is empty), bypass the interlock
    if (!targetedChefChair || settings.chefChairSelect == "None") {
        chefSeated = true 
    }
    
    def chairList = [chair1, chair2, chair3, chair4]
    def debugStrings = []
    
    chairList.each { chair ->
        if (chair) {
            // Live status checks if someone is CURRENTLY physically shaking the chair
            if (chair.currentValue("acceleration") == "active") {
                liveActiveCount++
            }
            
            // Rolling history calculates macro-events over the 5 minute window
            def devId = chair.id
            def events = history[devId] ?: []
            events = events.findAll { it >= cutoff } 
            history[devId] = events 
            
            int vibeCount = events.size()
            int reqPerChair = settings.minVibesPerChair ?: 2
            boolean thisChairOccupied = false
            
            if (vibeCount >= reqPerChair) {
                occupiedChairsCount++
                totalValidVibes += vibeCount
                thisChairOccupied = true
            }
            
            // If this specific chair is the Chef's chair and it is occupied, flag it
            if (targetedChefChair && chair.id == targetedChefChair.id && thisChairOccupied) {
                chefSeated = true
            }
            
            debugStrings << "${chair.displayName}: ${vibeCount}"
        }
    }
    
    state.seatHistory = history 
    state.dashboardSeatStats = debugStrings.join(" | ")
    state.occupiedCount = occupiedChairsCount
    state.totalVibes = totalValidVibes
    state.chefIsSeated = chefSeated
    
    // 1. CANCEL SHUTOFF IF LIVE MOVEMENT
    if (liveActiveCount > 0 && state.shutoffPending) {
        logAction("Table activity detected. Canceling auto-end timer.")
        unschedule("endMealTime")
        state.shutoffPending = false
    }
    
    // 2. TRIGGER NEW MEAL LOGIC
    if (!state.mealTimeActive) {
        int reqChairs = settings.minChairs ?: 2
        int reqTotal = settings.minTotalVibes ?: 5
        
        if (occupiedChairsCount >= reqChairs && totalValidVibes >= reqTotal) {
            if (!chefSeated) {
                logAction("Seats occupied, but waiting for Chef's Chair to trigger meal.")
            } else {
                String win = getCurrentTimeWindow()
                if (win != "none" && isModeAllowed()) {
                    logAction("TRIGGER: ${occupiedChairsCount} chairs occupied, Chef is seated. Initiating ${win.capitalize()}.")
                    startMealTime(win)
                } else {
                    if (!isModeAllowed()) logAction("Seats occupied, but ignored due to Mode restrictions.")
                    else logAction("Seats occupied, but currently outside of Breakfast/Dinner windows.")
                }
            }
        }
    } 
    // 3. END MEAL LOGIC
    else if (state.mealTimeActive && liveActiveCount == 0 && !state.shutoffPending) {
        int delaySeconds = getTimeoutSeconds()
        int delayMinutes = delaySeconds / 60
        String guestTag = (settings.guestSwitch?.currentValue("switch") == "on") ? " (Guest Mode Active)" : ""
        
        logAction("All chairs are currently still. Scheduling ${state.activeMealType?.capitalize()} end in ${delayMinutes} minutes${guestTag}.")
        state.shutoffPending = true
        runIn(delaySeconds, "endMealTime", [overwrite: true])
    }
}

def startMealTime(String mealType) {
    state.mealTimeActive = true
    state.activeMealType = mealType
    state.shutoffPending = false
    state.mealStartTime = now() // Start the hidden stopwatch
    
    logAction("AUTOMATION: Executing ${mealType.capitalize()} ON routines.")
    
    if (mealType == "breakfast") {
        if (bfastMealSwitch) bfastMealSwitch.on()
        if (bfastDndSwitch) bfastDndSwitch.on()
        if (bfastOnLights) bfastOnLights.on()
        if (bfastOffLights) bfastOffLights.off()
        if (bfastLockDoors) bfastLockDoors.lock()
        pauseAudio(bfastPauseSpeakers)
    } else if (mealType == "dinner") {
        if (dinnerMealSwitch) dinnerMealSwitch.on()
        if (dinnerDndSwitch) dinnerDndSwitch.on()
        if (dinnerOnLights) dinnerOnLights.on()
        if (dinnerOffLights) dinnerOffLights.off()
        if (dinnerLockDoors) dinnerLockDoors.lock()
        pauseAudio(dinnerPauseSpeakers)
    }
}

def pauseAudio(speakers) {
    if (!speakers) return
    speakers.each { speaker ->
        try {
            if (speaker.hasCommand("pause")) speaker.pause()
        } catch (e) { log.error "Failed to pause speaker ${speaker.displayName}: ${e}" }
    }
}

def endMealTime() {
    String mealType = state.activeMealType ?: "none"
    state.mealTimeActive = false
    state.activeMealType = "none"
    state.shutoffPending = false
    
    // Stop the watch and process the lifestyle metrics
    if (state.mealStartTime) {
        long durationMs = now() - state.mealStartTime
        state.weeklyMealDurationMs = (state.weeklyMealDurationMs ?: 0) + durationMs
        state.weeklyMealCount = (state.weeklyMealCount ?: 0) + 1
        state.mealStartTime = null
        logAction("Meal Metrics Logged: Duration was ${Math.round(durationMs / 60000)} minutes.")
    }
    
    if (mealType != "none") {
        logAction("AUTOMATION: ${mealType.capitalize()} has concluded. Executing OFF routines.")
    } else {
        logAction("AUTOMATION: Meal Time manually forced off.")
    }
    
    if (mealType == "breakfast") {
        if (bfastMealSwitch) bfastMealSwitch.off()
        if (bfastDndSwitch) bfastDndSwitch.off()
    } else if (mealType == "dinner") {
        if (dinnerMealSwitch) dinnerMealSwitch.off()
        if (dinnerDndSwitch) dinnerDndSwitch.off()
    } else {
        if (bfastMealSwitch) bfastMealSwitch.off()
        if (bfastDndSwitch) bfastDndSwitch.off()
        if (dinnerMealSwitch) dinnerMealSwitch.off()
        if (dinnerDndSwitch) dinnerDndSwitch.off()
    }
    
    // Clear history to prevent instant re-triggering upon a single bump
    state.seatHistory = [:]
    evaluateSeats()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
