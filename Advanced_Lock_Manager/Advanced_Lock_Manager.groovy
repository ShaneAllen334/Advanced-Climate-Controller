/**
 * Advanced Lock Manager
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Lock Manager",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Enterprise access control with Ghost Codes, Auto-Locking, Identity Automations, and Predictive Maintenance.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "userPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Lock Manager", install: true, uninstall: true) {
        
        section("Live Access Dashboard") {
            // TABLE 1: Lock Status
            def statusText = "<b>Physical Lock Status</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Door</th><th style='padding: 8px;'>Current State</th><th style='padding: 8px;'>Last Action</th></tr>"
            
            if (masterLocks) {
                masterLocks.each { lock ->
                    def lName = lock.displayName
                    def lState = lock.currentValue("lock")?.toUpperCase() ?: "UNKNOWN"
                    def stateColor = (lState == "UNLOCKED") ? "red" : (lState == "LOCKED" ? "green" : "black")
                    
                    def lastAction = state.lastAction?."${lock.id}" ?: "Awaiting Sync..."
                    def pendingMsg = ""
                    
                    // Countdown Timer Math
                    if (lState == "UNLOCKED") {
                        if (state["pendingAutoLock_${lock.id}"]) {
                            def cSensor = settings["contactSensor_${lock.id}"]
                            if (cSensor && cSensor.currentValue("contact") == "closed") {
                                pendingMsg = "<br><span style='color: orange; font-size: 11px;'><i>Locking in < 10s...</i></span>"
                            } else {
                                pendingMsg = "<br><span style='color: orange; font-size: 11px;'><i>Awaiting Door Close to Auto-Lock</i></span>"
                            }
                        } else {
                            def epoch = state["autoLockEpoch_${lock.id}"]
                            def delayMins = settings["autoLockTime_${lock.id}"] ?: 0
                            if (epoch && delayMins > 0) {
                                def targetTime = epoch + (delayMins * 60 * 1000)
                                def diffMs = targetTime - new Date().time
                  
                                if (diffMs > 0) {
                                    def diffMins = Math.floor(diffMs / 60000).toInteger()
                                    def diffSecs = Math.round((diffMs % 60000) / 1000).toInteger()
                                    pendingMsg = "<br><span style='color: blue; font-size: 11px;'><i>Auto-Locking in ${diffMins}m ${diffSecs}s</i></span>"
                                }
                            }
                        }
                    }
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${lName}</b></td><td style='padding: 8px; color: ${stateColor}; font-weight: bold;'>${lState}</td><td style='padding: 8px;'>${lastAction}${pendingMsg}</td></tr>"
                }
            } else {
                statusText += "<tr><td colspan='3' style='padding: 8px; color: #888;'>No locks configured.</td></tr>"
            }
            statusText += "</table>"
            
            // TABLE 2: Hardware Health & Maintenance
            statusText += "<b>Hardware Health & Maintenance</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Door</th><th style='padding: 8px;'>Battery Level</th><th style='padding: 8px;'>Weekly Door Cycles</th><th style='padding: 8px;'>Weekly Lock Cycles</th><th style='padding: 8px;'>Maintenance Status</th></tr>"
            
            if (masterLocks) {
                masterLocks.each { lock ->
                    def lName = lock.displayName
                    def battery = lock.currentValue("battery")
                    def battStr = battery ? "${battery}%" : "N/A"
                    def battColor = (battery && battery < (settings["lowBatteryThreshold"] ?: 20)) ? "red" : "green"
                    
                    def dCycles = state.weeklyDoorCycles?."${lock.id}" ?: 0
                    def lCycles = state.weeklyLockCycles?."${lock.id}" ?: 0
                    
                    def mStatus = "<span style='color: green;'>Healthy</span>"
                    def highCycles = settings["highCycleWarning"] ?: 50
                    if (battery && battery < (settings["lowBatteryThreshold"] ?: 20)) {
                        mStatus = "<span style='color: red; font-weight: bold;'>Replace Battery</span>"
                    } else if (lCycles >= highCycles) {
                        mStatus = "<span style='color: orange; font-weight: bold;'>High Wear (Lube Recommended)</span>"
                    }
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${lName}</b></td><td style='padding: 8px; color: ${battColor}; font-weight: bold;'>${battStr}</td><td style='padding: 8px;'>${dCycles} Opens</td><td style='padding: 8px;'>${lCycles} Unlocks</td><td style='padding: 8px;'>${mStatus}</td></tr>"
                }
            } else {
                statusText += "<tr><td colspan='5' style='padding: 8px; color: #888;'>No locks configured.</td></tr>"
            }
            statusText += "</table>"
            
            // TABLE 3: User Authorization Status
            statusText += "<b>Dynamic Authorization Engine</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>User Identity</th><th style='padding: 8px;'>Access Rights</th><th style='padding: 8px;'>Lock Programming Status</th></tr>"
            
            def userCount = settings["numUsers"] ?: 1
            if (userCount > 0) {
                for (int i = 1; i <= (userCount as Integer); i++) {
                    def uName = settings["userName_${i}"] ?: "User ${i}"
                    def hasPrimary = settings["userPin_${i}"] ? true : false
                    def hasGhost = settings["userGhostPin_${i}"] ? true : false
                    
                    if (!hasPrimary && !hasGhost) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${uName}</b></td><td style='padding: 8px; color: #888;'>Unconfigured</td><td style='padding: 8px;'>-</td></tr>"
                        continue
                    }
                    
                    def allowedLocks = settings["userLocks_${i}"]
                    def lockNames = allowedLocks ? allowedLocks.collect{it.displayName}.join(", ") : "All Locks"
                    def modes = settings["userModes_${i}"] ? settings["userModes_${i}"].join(", ") : "Always"
                    def tStart = settings["userStartTime_${i}"] ? new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", settings["userStartTime_${i}"]).format("h:mm a") : ""
                    def tEnd = settings["userEndTime_${i}"] ? new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", settings["userEndTime_${i}"]).format("h:mm a") : ""
                    def timeStr = (tStart && tEnd) ? "${tStart} - ${tEnd}" : "24/7"
                    def rightsStr = "<b>Locks:</b> ${lockNames}<br><b>Modes:</b> ${modes}<br><b>Hours:</b> ${timeStr}"
                    
                    def progState = state.userProgrammed?."${i}"
                    def progColor = progState ? "green" : "orange"
                    def ghostStr = hasGhost ? " + Ghost" : ""
                    def progText = progState ? "ACTIVE (Primary${ghostStr})" : "SUSPENDED (Codes Removed)"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${uName}</b></td><td style='padding: 8px; font-size: 11px;'>${rightsStr}</td><td style='padding: 8px; color: ${progColor}; font-weight: bold;'>${progText}</td></tr>"
                }
            } else {
                statusText += "<tr><td colspan='3' style='padding: 8px; color: #888;'>No users configured.</td></tr>"
            }
            statusText += "</table>"
            
            def globalStatus = isSystemPaused() ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            statusText += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>System Core:</b> ${globalStatus}</div>"

            paragraph statusText
        }

        section("Access Audit Log (Last 10 Entries)") {
            if (atomicState.accessLog && atomicState.accessLog.size() > 0) {
                def logText = "<table style='width:100%; font-size: 13px; border-collapse: collapse; border: 1px solid #ccc;'>"
                logText += "<tr style='background-color: #eee; border-bottom: 1px solid #ccc; text-align: left;'><th style='padding: 6px;'>Date & Time</th><th style='padding: 6px;'>Event Details</th></tr>"
                
                atomicState.accessLog.each { entry ->
                    logText += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 6px; width: 35%;'>${entry.time}</td><td style='padding: 6px;'>${entry.event}</td></tr>"
                }
                logText += "</table>"
                paragraph logText
            } else {
                paragraph "<i>No access events recorded yet.</i>"
            }
        }
        
        section("Application History") {
            if (atomicState.historyLog && atomicState.historyLog.size() > 0) {
                def logText = atomicState.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            }
        }
        
        section("Global Core Settings") {
            input "masterLocks", "capability.lock", title: "Select Smart Locks to Manage", multiple: true, required: true, submitOnChange: true
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "Pausing this stops the dynamic removal/injection of codes and disables auto-locking."
            
            input "syncInterval", "enum", title: "Background Sync Interval", required: true, defaultValue: "15", submitOnChange: true, options: [
                "5": "Every 5 Minutes (High Hub Load)",
                "10": "Every 10 Minutes",
                "15": "Every 15 Minutes (Recommended)",
                "30": "Every 30 Minutes",
                "60": "Every 1 Hour",
                "0": "Manual / Event-Driven Only"
            ], description: "How often the app double-checks and syncs codes in the background. Note: Codes will still sync instantly if a lock is used or the house mode changes."
            
            input "numUsers", "number", title: "Number of Identities/Users to Configure (1-20)", required: true, defaultValue: 1, range: "1..20", submitOnChange: true
            input "btnForceSync", "button", title: "Force Sync All Locks Now"
        }
        
        if (masterLocks) {
            section("System Notifications") {
                paragraph "Get alerted when a lock requires preventative maintenance or a battery swap."
                input "notifyDevices", "capability.notification", title: "Send notifications to...", multiple: true, required: false
                input "notifyLowBattery", "bool", title: "Notify on Low Battery", defaultValue: true
                input "notifyHighWear", "bool", title: "Notify on High Wear (Lube Recommended)", defaultValue: true
                input "notifyTime", "time", title: "Daily Maintenance Check Time", required: true, defaultValue: "10:00"
            }

            section("Device Health & Maintenance Thresholds") {
                paragraph "Smart locks are high-torque devices. Tracking their cycles and battery voltage prevents lockouts. Weekly counters automatically reset every Sunday at midnight."
                
                input "lowBatteryThreshold", "number", title: "Critical Battery Threshold (%)", defaultValue: 20, required: true, 
                    description: "Alkaline batteries experience severe voltage drops below 20%, which can cause the internal motor to stall halfway through throwing the deadbolt."
                input "highCycleWarning", "number", title: "High Usage Wear Threshold (Cycles per Week)", defaultValue: 50, required: true,
                    description: "If a deadbolt cycles more than this number of times in a single week, it requires powdered graphite lubrication every 3 months to prevent mechanical stripping."
                input "btnResetCounters", "button", title: "Clear Maintenance Alerts (Reset Counters)"
            }
            
            section("Life Safety & Emergency Overrides") {
                paragraph "If smoke or carbon monoxide is detected, all auto-locking routines will be instantly suspended to ensure emergency egress."
                input "smokeDetectors", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
                input "coDetectors", "capability.carbonMonoxideDetector", title: "Carbon Monoxide Detectors", multiple: true, required: false
            }

            section("Auto-Lock & Door Sensors") {
                paragraph "Configure automatic locking based on time and physical door state. If the timer expires while the door is open, the app will wait for it to close, then lock it after a 10-second grace period."
                masterLocks.each { lock ->
                    input "autoLockTime_${lock.id}", "number", title: "Auto-Lock Timer for ${lock.displayName} (Minutes, 0 to disable)", defaultValue: 0, required: true
                    input "contactSensor_${lock.id}", "capability.contactSensor", title: "Contact Sensor for ${lock.displayName}", required: false, description: "Highly recommended to prevent the deadbolt from throwing while the door is open. Also used to track weekly door cycles."
                }
            }
            
            section("Mode-Based Perimeter Lockdown") {
                paragraph "Automatically secure the perimeter when the house changes mode. (0 = Instant Lock)."
                for (int i = 1; i <= 3; i++) {
                    input "autoLockMode_${i}", "mode", title: "Rule ${i}: Trigger Mode", required: false, multiple: false
                    input "autoLockModeDelay_${i}", "number", title: "Rule ${i}: Delay (Minutes)", defaultValue: 0, required: false
                }
            }
            
            section("Safety Override (Shower Vulnerability Sync)") {
                paragraph "Select motion sensors located in your showers. If motion is detected here, the system assumes you are vulnerable and instantly sweeps the house to lock all closed doors."
                input "showerSensors", "capability.motionSensor", title: "Shower Motion Sensors", multiple: true, required: false
            }
        }
        
        def userCount = settings["numUsers"] ?: 1
        if (userCount > 0 && userCount <= 20) {
            for (int i = 1; i <= (userCount as Integer); i++) {
                def uName = settings["userName_${i}"] ?: "User ${i}"
                section("${uName} Configuration") {
                    href(name: "userHref${i}", page: "userPage", params: [userNum: i], title: "Configure ${uName}")
                }
            }
        }
    }
}

def userPage(params) {
    def uNum = params?.userNum ?: state.currentUser ?: 1
    state.currentUser = uNum
    def currentName = settings["userName_${uNum}"] ?: "User ${uNum}"
    
    dynamicPage(name: "userPage", title: "${currentName} Identity Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Primary Code (Triggers Automations)") {
            input "userName_${uNum}", "text", title: "User Name (e.g., Dog Walker, Mom)", required: false, defaultValue: "User ${uNum}", submitOnChange: true
            input "userSlot_${uNum}", "number", title: "Primary Lock Slot Position (1-30)", required: true, description: "The physical memory slot on the lock. MUST be unique."
            input "userPin_${uNum}", "text", title: "Primary PIN Code (4-8 digits)", required: false
        }
        
        section("Ghost Code (Silent Entry / Bypass Automations)") {
            paragraph "An optional secondary code. Using this code will unlock the door but intentionally skip any mode changes configured below."
            input "userGhostSlot_${uNum}", "number", title: "Ghost Lock Slot Position (1-30)", required: false, description: "Must be a different slot than the Primary."
            input "userGhostPin_${uNum}", "text", title: "Ghost PIN Code (4-8 digits)", required: false
        }
        
        section("Physical & Access Restrictions (The Dynamic Gate)") {
            paragraph "Leave these blank if the user should have 24/7 access to all configured locks."
            input "userLocks_${uNum}", "capability.lock", title: "Allowed Locks", multiple: true, required: false, description: "Leave blank to allow access to ALL configured locks."
            input "userModes_${uNum}", "mode", title: "Allowed Modes", multiple: true, required: false
            input "userStartTime_${uNum}", "time", title: "Start Time", required: false
            input "userEndTime_${uNum}", "time", title: "End Time", required: false
        }
        
        section("Identity-Based Automations (Primary Code Only)") {
            paragraph "If this specific user unlocks the door using their Primary Code while the house is in one of the 'Trigger Modes', automatically change the house to the 'Target Mode'."
            input "triggerFromModes_${uNum}", "mode", title: "If the house is currently in these modes (e.g., Good Night)...", multiple: true, required: false
            input "arrivalTargetMode_${uNum}", "mode", title: "...Change the house to this mode (e.g., Home)", multiple: false, required: false
        }
    }
}

def installed() {
    log.info "Advanced Lock Manager Installed."
    initialize()
}

def updated() {
    log.info "Advanced Lock Manager Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    atomicState.historyLog = atomicState.historyLog ?: []
    atomicState.accessLog = atomicState.accessLog ?: []
    
    state.lastAction = state.lastAction ?: [:]
    state.userProgrammed = state.userProgrammed ?: [:]
    
    // Setup Weekly Counters
    state.weeklyDoorCycles = state.weeklyDoorCycles ?: [:]
    state.weeklyLockCycles = state.weeklyLockCycles ?: [:]
    
    if (masterLocks) {
        subscribe(masterLocks, "lock", lockHandler)
        
        masterLocks.each { lock ->
            def cSensor = settings["contactSensor_${lock.id}"]
            if (cSensor) {
                subscribe(cSensor, "contact", contactHandler)
            }
            state["pendingAutoLock_${lock.id}"] = false
        }
    }
    
    subscribe(location, "mode", modeChangeHandler)
    
    if (showerSensors) {
        subscribe(showerSensors, "motion.active", showerMotionHandler)
    }

    if (smokeDetectors) {
        subscribe(smokeDetectors, "smoke", emergencyAlarmHandler)
    }
    
    if (coDetectors) {
        subscribe(coDetectors, "carbonMonoxide", emergencyAlarmHandler)
    }
    
    // Scheduled Events
    schedule("0 0 0 ? * SUN", resetWeeklyCounters) // Reset counters Sunday at midnight
    
    if (settings["notifyTime"]) {
        schedule(settings["notifyTime"], dailyMaintenanceCheck)
    } else {
        schedule("0 0 10 ? * *", dailyMaintenanceCheck) // Default to 10:00 AM
    }
    
    // Apply User Selected Sync Interval
    def interval = settings["syncInterval"] ?: "15"
    if (interval != "0") {
        if (interval == "60") {
            schedule("0 0 * * * ?", evaluateSchedules) // Hourly
        } else {
            schedule("0 0/${interval} * * * ?", evaluateSchedules)
        }
    }
    
    // Ensure we force sync all codes whenever the app is updated/saved
    runIn(5, "forceSyncSchedules")
}

def forceSyncSchedules() {
    evaluateSchedules(true)
}

// --- UTILITY: LOGGER ---
def addToHistory(String msg) {
    def currentLog = atomicState.historyLog ?: []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    currentLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (currentLog.size() > 20) currentLog = currentLog.take(20)
    atomicState.historyLog = currentLog
    
    log.info "HISTORY: " + msg.replaceAll("\\<.*?\\>", "")
}

def addToAccessLog(String msg) {
    def currentLog = atomicState.accessLog ?: []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    def entry = [time: timestamp, event: msg]
    currentLog.add(0, entry)
    if (currentLog.size() > 10) currentLog = currentLog.take(10)
    atomicState.accessLog = currentLog
}

def isSystemPaused() {
    return (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
}

def isEmergencyActive() {
    def emergency = false
    if (smokeDetectors?.find { it.currentValue("smoke") != "clear" }) emergency = true
    if (coDetectors?.find { it.currentValue("carbonMonoxide") != "clear" }) emergency = true
    return emergency
}

// --- EMERGENCY HANDLER ---
def emergencyAlarmHandler(evt) {
    if (evt.value != "clear") {
        addToHistory("EMERGENCY: ${evt.device.displayName} detected ${evt.name}! Suspending all auto-lock operations.")
        // Immediately invalidate any pending lock routines
        if (masterLocks) {
            masterLocks.each { lock ->
                state["pendingAutoLock_${lock.id}"] = false
                state["autoLockEpoch_${lock.id}"] = new Date().time 
            }
        }
    } else {
        addToHistory("SAFETY: ${evt.device.displayName} is now clear.")
    }
}

// --- MAINTENANCE & RESET ---
def resetWeeklyCounters() {
    addToHistory("MAINTENANCE: Weekly cycle counters have been reset to zero.")
    state.weeklyDoorCycles = [:]
    state.weeklyLockCycles = [:]
}

def dailyMaintenanceCheck() {
    if (!masterLocks) return
    
    def notifyList = []
    
    masterLocks.each { lock ->
        def battery = lock.currentValue("battery")
        def lowBattThresh = settings["lowBatteryThreshold"] ?: 20
        if (battery && battery < lowBattThresh && settings["notifyLowBattery"]) {
            notifyList << "${lock.displayName} battery is critically low (${battery}%)."
        }
        
        def lCycles = state.weeklyLockCycles?."${lock.id}" ?: 0
        def highCycles = settings["highCycleWarning"] ?: 50
        if (lCycles >= highCycles && settings["notifyHighWear"]) {
            notifyList << "${lock.displayName} has high wear (${lCycles} cycles). Lube recommended."
        }
    }
    
    if (notifyList.size() > 0 && settings["notifyDevices"]) {
        def msg = "Lock Manager Maintenance Alert:\n" + notifyList.join("\n")
        settings["notifyDevices"].each { it.deviceNotification(msg) }
        addToHistory("NOTIFICATIONS: Sent daily maintenance alerts.")
    }
}

// --- BUTTON HANDLER ---
def appButtonHandler(btn) {
    if (btn == "btnForceSync") {
        addToHistory("SYSTEM: Manual Force Sync triggered.")
        evaluateSchedules(true)
    } else if (btn == "btnResetCounters") {
        addToHistory("SYSTEM: Manual counter reset triggered. Clearing maintenance alerts.")
        resetWeeklyCounters()
    }
}

// --- SAFETY OVERRIDE HANDLER (Motion Based) ---
def showerMotionHandler(evt) {
    if (isSystemPaused() || !masterLocks) return
    
    if (isEmergencyActive()) {
        addToHistory("SAFETY OVERRIDE ABORTED: Life Safety Emergency is active.")
        return
    }

    def sensorName = evt.device.displayName
    addToHistory("SAFETY OVERRIDE: Motion detected at ${sensorName}. Securing perimeter.")
    
    masterLocks.each { lock ->
        if (lock.currentValue("lock") == "unlocked") {
            def cSensor = settings["contactSensor_${lock.id}"]
            def isClosed = cSensor ? (cSensor.currentValue("contact") == "closed") : true
            
            if (isClosed) {
                addToHistory("SAFETY: ${lock.displayName} was unlocked and closed. Locking immediately.")
                lock.lock()
                state["pendingAutoLock_${lock.id}"] = false
            } else {
                addToHistory("SAFETY ALERT: Cannot secure ${lock.displayName} because it is OPEN. Queuing auto-lock.")
                state["pendingAutoLock_${lock.id}"] = true
            }
        }
    }
}

// --- DOOR SENSOR HANDLER (The Frame-Smash Protector & Cycle Tracker) ---
def contactHandler(evt) {
    if (isSystemPaused()) return
    
    def sensorId = evt.device.id
    def isClosed = (evt.value == "closed")
    
    masterLocks.each { lock ->
        def cSensor = settings["contactSensor_${lock.id}"]
        if (cSensor && cSensor.id == sensorId) {
            
            if (isClosed) {
                if (state["pendingAutoLock_${lock.id}"]) {
                    if (isEmergencyActive()) {
                        addToHistory("SECURITY: Auto-Lock queue aborted for ${lock.displayName} due to Life Safety Emergency.")
                        state["pendingAutoLock_${lock.id}"] = false
                        return
                    }
                    addToHistory("SECURITY: ${lock.displayName} closed. Executing Auto-Lock in 10 seconds.")
                    def epoch = new Date().time
                    state["autoLockEpoch_${lock.id}"] = epoch
                    runIn(10, "executeFinalAutoLock", [data: [lockId: lock.id, epoch: epoch], overwrite: false])
                }
            } else {
                // Tracking Door Open Cycles
                if (!state.weeklyDoorCycles) state.weeklyDoorCycles = [:]
                state.weeklyDoorCycles["${lock.id}"] = (state.weeklyDoorCycles["${lock.id}"] ?: 0) + 1
                
                // If door opens during the 10-second grace period, cancel the lock command
                if (state["pendingAutoLock_${lock.id}"]) {
                    state["autoLockEpoch_${lock.id}"] = new Date().time // Invalidate old timers
                    addToHistory("SECURITY: ${lock.displayName} reopened during 10s grace period. Auto-Lock suspended.")
                }
            }
        }
    }
}

def executeFinalAutoLock(data) {
    def lockId = data.lockId
    if (state["autoLockEpoch_${lockId}"] != data.epoch || isSystemPaused()) return
    
    def lock = masterLocks.find { it.id == lockId }
    
    if (isEmergencyActive()) {
        addToHistory("SECURITY: Auto-Lock aborted for ${lock?.displayName} due to active Life Safety Emergency!")
        state["pendingAutoLock_${lockId}"] = false
        return
    }

    if (lock && lock.currentValue("lock") == "unlocked") {
        addToHistory("SECURITY: Executing delayed Auto-Lock for ${lock.displayName}.")
        lock.lock()
        state["pendingAutoLock_${lockId}"] = false
    }
}

// --- LOCK EVENT HANDLER (The Auditor & Trigger) ---
def lockHandler(evt) {
    def lockId = evt.device.id
    def lockName = evt.device.displayName
    def action = evt.value 
    def desc = evt.descriptionText ?: ""
    
    def logMsg = ""
    def codeName = ""
    
    if (action == "unlocked") {
        // Track Lock Cycle
        if (!state.weeklyLockCycles) state.weeklyLockCycles = [:]
        state.weeklyLockCycles["${lockId}"] = (state.weeklyLockCycles["${lockId}"] ?: 0) + 1
        
        // Parse ID from payload
        if (evt.data) {
            try {
                def dataMap = parseJson(evt.data)
                if (dataMap?.codeName) codeName = dataMap.codeName
            } catch (e) { }
        } 
        // Fallback string parsing
        if (!codeName && desc.contains("unlocked by")) {
            codeName = desc.split("unlocked by ")[1]?.trim()
        }
        
        if (codeName) {
            def isGhost = codeName.endsWith("(Ghost)")
            logMsg = "Unlocked by <b>${codeName}</b>"
            
            if (isGhost) {
                addToAccessLog("箔 ${lockName} unlocked silently by <b>${codeName}</b>")
                addToHistory("ACCESS: ${lockName} unlocked by ${codeName}. Bypassing automations.")
            } else {
                addToAccessLog("箔 ${lockName} unlocked by <b>${codeName}</b>")
                addToHistory("ACCESS: ${lockName} unlocked by ${codeName}.")
            }
            processIdentityAutomation(codeName)
        } else {
            logMsg = "Unlocked (Manual/Thumbturn)"
            addToAccessLog("箔 ${lockName} unlocked manually.")
            addToHistory("ACCESS: ${lockName} unlocked manually.")
        }
        
        // --- AUTO-LOCK TRIGGER ---
        def delayMins = settings["autoLockTime_${lockId}"] ?: 0
        if (delayMins > 0) {
            def epoch = new Date().time
            state["autoLockEpoch_${lockId}"] = epoch
            addToHistory("SECURITY: Auto-Lock timer started for ${lockName} (${delayMins} min).")
            runIn(delayMins * 60, "evaluateAutoLock", [data: [lockId: lockId, epoch: epoch], overwrite: false])
        }
        
    } else if (action == "locked") {
        logMsg = "Locked"
        addToAccessLog("白 ${lockName} was locked.")
        
        // Destroy any running auto-lock timers for this specific door
        state["autoLockEpoch_${lockId}"] = new Date().time 
        state["pendingAutoLock_${lockId}"] = false
    }
    
    state.lastAction["${lockId}"] = logMsg
}

def evaluateAutoLock(data) {
    def lockId = data.lockId
    if (state["autoLockEpoch_${lockId}"] != data.epoch || isSystemPaused()) return
    
    def lock = masterLocks.find { it.id == lockId }
    if (!lock || lock.currentValue("lock") != "unlocked") return

    if (isEmergencyActive()) {
        addToHistory("SECURITY: Auto-Lock aborted for ${lock.displayName} due to active Life Safety Emergency!")
        state["pendingAutoLock_${lockId}"] = false
        return
    }
    
    def cSensor = settings["contactSensor_${lockId}"]
    // If no sensor is configured, we assume the door is closed and blindly throw the deadbolt
    def isClosed = cSensor ? (cSensor.currentValue("contact") == "closed") : true 
    
    if (isClosed) {
        addToHistory("SECURITY: Auto-Lock timer expired. Door is closed. Locking ${lock.displayName}.")
        lock.lock()
        state["pendingAutoLock_${lockId}"] = false
    } else {
        addToHistory("SECURITY: Auto-Lock timer expired, but ${lock.displayName} is OPEN. Suspending lock command.")
        state["pendingAutoLock_${lockId}"] = true
    }
}

// --- IDENTITY AUTOMATION ENGINE ---
def processIdentityAutomation(String unlockedByName) {
    if (isSystemPaused()) return
    if (unlockedByName.endsWith("(Ghost)")) return 
    
    def currentMode = location.mode
    def userCount = settings["numUsers"] ?: 1
    
    for (int i = 1; i <= (userCount as Integer); i++) {
        def configName = settings["userName_${i}"]
        
        if (configName && configName.equalsIgnoreCase(unlockedByName)) {
            def tModes = settings["triggerFromModes_${i}"]
            def targetMode = settings["arrivalTargetMode_${i}"]
            
            if (tModes && targetMode && tModes.contains(currentMode)) {
                addToHistory("IDENTITY TRIGGER: ${configName} arrived. Changing mode from ${currentMode} to ${targetMode}.")
                setLocationMode(targetMode)
            }
            return
        }
    }
}

// --- DYNAMIC CODE INJECTION ENGINE ---
def modeChangeHandler(evt) {
    def currentMode = evt.value
    addToHistory("SYSTEM: Hub mode changed to ${currentMode}. Re-evaluating access schedules.")
    
    // Evaluate if this mode change triggers a Perimeter Lockdown
    if (masterLocks && !isSystemPaused()) {
        for (int i = 1; i <= 3; i++) {
            def targetMode = settings["autoLockMode_${i}"]
            if (targetMode && targetMode == currentMode) {
                def delayMins = settings["autoLockModeDelay_${i}"] ?: 0
                addToHistory("SECURITY: Mode Lockdown triggered. Securing perimeter in ${delayMins} minutes.")
                
                masterLocks.each { lock ->
                    if (lock.currentValue("lock") == "unlocked") {
                        def epoch = new Date().time
                        state["autoLockEpoch_${lock.id}"] = epoch
                        
                        if (delayMins > 0) {
                            runIn(delayMins * 60, "evaluateAutoLock", [data: [lockId: lock.id, epoch: epoch], overwrite: false])
                        } else {
                            // Run instantly
                            evaluateAutoLock([lockId: lock.id, epoch: epoch])
                        }
                    }
                }
            }
        }
    }
    
    evaluateSchedules()
}

def evaluateSchedules(forceSync = false) {
    if (isSystemPaused() || !masterLocks) return
    
    def userCount = settings["numUsers"] ?: 1
    for (int i = 1; i <= (userCount as Integer); i++) {
        def uName = settings["userName_${i}"]
        
        def pSlot = settings["userSlot_${i}"]
        def pPin = settings["userPin_${i}"]
        
        def gSlot = settings["userGhostSlot_${i}"]
        def gPin = settings["userGhostPin_${i}"]
        
        if (!uName) continue
        
        def isAllowed = true
        
        // 1. Check Mode Restrictions
        def allowedModes = settings["userModes_${i}"]
        if (allowedModes && !allowedModes.contains(location.mode)) {
            isAllowed = false
        }
        
        // 2. Check Time Restrictions
        def tStart = settings["userStartTime_${i}"]
        def tEnd = settings["userEndTime_${i}"]
        if (isAllowed && tStart && tEnd) {
            def between = timeOfDayIsBetween(tStart, tEnd, new Date(), location.timeZone)
            if (!between) {
                isAllowed = false
            }
        }
        
        // 3. Inject or Remove the Codes
        def currentlyProgrammed = state.userProgrammed["${i}"] ?: false
        
        if (isAllowed && (!currentlyProgrammed || forceSync)) {
            addToHistory("SECURITY: Access granted for ${uName}. Synchronizing Locks.")
            masterLocks.each { lock ->
                def allowedLocks = settings["userLocks_${i}"]
                def allowedIds = allowedLocks?.collect { it.id }
                def lockPermitted = (!allowedIds || allowedIds.contains(lock.id))
                
                if (lockPermitted) {
                    if (pSlot && pPin && lock.hasCommand("setCode")) lock.setCode(pSlot, pPin, uName)
                    if (gSlot && gPin && lock.hasCommand("setCode")) lock.setCode(gSlot, gPin, "${uName} (Ghost)")
                } else {
                    if (pSlot && lock.hasCommand("deleteCode")) lock.deleteCode(pSlot)
                    if (gSlot && lock.hasCommand("deleteCode")) lock.deleteCode(gSlot)
                }
            }
            state.userProgrammed["${i}"] = true
        } 
        else if (!isAllowed && (currentlyProgrammed || forceSync)) {
            addToHistory("SECURITY: Access revoked for ${uName}. Deleting PIN(s).")
            masterLocks.each { lock ->
                if (pSlot && lock.hasCommand("deleteCode")) lock.deleteCode(pSlot)
                if (gSlot && lock.hasCommand("deleteCode")) lock.deleteCode(gSlot)
            }
            state.userProgrammed["${i}"] = false
        }
    }
}
