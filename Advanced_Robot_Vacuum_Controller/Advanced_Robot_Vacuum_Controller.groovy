/**
 * Advanced Robot Vacuum Controller
 * Orchestration app for the Roborock Robot Vacuum driver with Live Dashboard, Master Override, and Dynamic 12-Room Setup.
*/

definition(
    name: "Advanced Robot Vacuum Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced orchestration, Dual-Vacuum support, ROI Analytics, Utilization Logic, and Adaptive Suction.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Vacuum Controller", install: true, uninstall: true, refreshInterval: 10) {
        
        if (vacuum1 || vacuum2) {
            section("<b>Live System Dashboard</b>") {
                paragraph buildDashboardHTML()
            }
            section("<b>Event History (Last 25 Events)</b>") {
                paragraph buildHistoryHTML()
            }
        }
        
        section("<b>1. Device Selection & Overrides</b>") {
            input "vacuum1", "capability.actuator", title: "Select Vacuum 1 (Primary)", required: true, multiple: false, submitOnChange: true
            if (vacuum1) {
                input "vac1Model", "enum", title: "↳ Vacuum 1 Model", options: ["Roborock S8 Series (3W Standby)", "Roborock QRevo Curve (4W Standby)", "Custom"], defaultValue: "Roborock S8 Series (3W Standby)", submitOnChange: true
                if (vac1Model == "Custom") input "vac1Watts", "decimal", title: "↳ Custom Standby Watts (V1)", defaultValue: 3.0
            }
            
            input "vacuum2", "capability.actuator", title: "Select Vacuum 2 (Secondary)", required: false, multiple: false, submitOnChange: true
            if (vacuum2) {
                input "vac2Model", "enum", title: "↳ Vacuum 2 Model", options: ["Roborock S8 Series (3W Standby)", "Roborock QRevo Curve (4W Standby)", "Custom"], defaultValue: "Roborock QRevo Curve (4W Standby)", submitOnChange: true
                if (vac2Model == "Custom") input "vac2Watts", "decimal", title: "↳ Custom Standby Watts (V2)", defaultValue: 4.0
            }
            
            input "masterSwitch", "capability.switch", title: "Master Suspend/Resume Switch", required: false, description: "If OFF, all automated routines are ignored and active cleans are paused."
            input "fullCleanSwitch", "capability.switch", title: "Full House Clean Switch", required: false, description: "Trigger full house clean when this turns ON"
            input "notifyDevice", "capability.notification", title: "Send Push Notifications To", required: false, multiple: true
        }

        section("<b>2. Smart Room Configuration</b>") {
            paragraph "<i>Configure up to 12 rooms. Define parameters, sequencing, and Pre-Evaluation occupancy sensors. Expand each room below to set it up.</i>"
        }

        // Generate individual collapsible sections for each room
        for (int i = 1; i <= 12; i++) {
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            
            section("<b>${rName} Setup</b>", hideable: true, hidden: true) {
                input "enableRoom_${i}", "bool", title: "<b>Enable ${rName}</b>", defaultValue: false, submitOnChange: true
                
                if (settings["enableRoom_${i}"]) {
                    input "vacuumAssign_${i}", "enum", title: "↳ Assign to Vacuum", options: ["Vacuum 1", "Vacuum 2"], defaultValue: "Vacuum 1", required: true
                    input "roomName_${i}", "text", title: "↳ Room Name (e.g., Kitchen)", required: true, submitOnChange: true
                    input "roomId_${i}", "text", title: "↳ Room ID (from vacuum state)", required: false
                    input "roomZone_${i}", "text", title: "↳ Optional: Zone Coordinates (Overrides Room ID)", required: false
                    
                    input "roomWater_${i}", "enum", title: "↳ Mop Water Level", options: ["Off", "Low", "Medium", "High"], defaultValue: "Medium"
                    input "roomSuction_${i}", "enum", title: "↳ Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max"], defaultValue: "Balanced"
                    input "roomSeq_${i}", "number", title: "↳ Cleaning Sequence Order (1 = First)", defaultValue: i
                    
                    paragraph "<b>Utilization & Air Quality:</b>"
                    input "roomOccupancyThreshold_${i}", "number", title: "↳ Min. Active Minutes to Require Cleaning", defaultValue: 15
                    input "roomHeavyTraffic_${i}", "number", title: "↳ Active Minutes for Adaptive Turbo Suction", defaultValue: 120
                    input "roomFan_${i}", "capability.switch", title: "↳ Post-Clean Fan/Purifier", required: false
                    input "roomFanTimer_${i}", "number", title: "↳ Fan Run Time After Vacuum Leaves (Mins)", defaultValue: 15
                    
                    paragraph "<b>Occupancy & Safety Triggers:</b>"
                    input "roomMotion_${i}", "capability.motionSensor", title: "↳ Pre-Check Motion Sensor", required: false
                    input "roomLight_${i}", "capability.switch", title: "↳ Pre-Check Lighting (ON = Occupied)", required: false
                    input "roomSwitch_${i}", "capability.switch", title: "↳ Individual Room Trigger Switch", required: false
                }
            }
        }

        List<String> availableRooms = ["All"]
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) {
                availableRooms << settings["roomName_${i}"]
            }
        }

        section("<b>3. Automated Triggers & Modes</b>") {
            paragraph "<b>Good Night Routine:</b>"
            input "goodNightMode", "mode", title: "Trigger Sweep on 'Good Night' Mode", required: false, multiple: true
            input "goodNightRooms", "enum", title: "Rooms to clean during Good Night routine", options: availableRooms, required: false, multiple: true
            input "goodNightConflicts", "capability.switch", title: "Device Conflict Block", required: false, multiple: true, description: "Do not start Good Night sweep if these devices are actively ON."
            
            paragraph "<b>Configurable Full House Clean Mode:</b>"
            input "fullCleanMode", "mode", title: "Trigger Configured Full Clean on Mode", required: false, multiple: true
            input "fullCleanIgnoreSkip", "bool", title: "Ignore Occupancy Skip Logic (Force Clean All)", defaultValue: false
            input "fullCleanSuction", "enum", title: "Override Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max"], required: false
            input "fullCleanWater", "enum", title: "Override Base Mop Water", options: ["Off", "Low", "Medium", "High"], required: false
            
            paragraph "<b>Other Triggers:</b>"
            input "schoolRunSwitch", "capability.switch", title: "School Drop-Off/Pickup Switch", required: false
            input "schoolRunRooms", "enum", title: "Rooms to clean during School Run", options: availableRooms, required: false, multiple: true
        }

        section("<b>4. Hardware Protection, Efficiency & ROI</b>") {
            input "pauseGracePeriod", "number", title: "Occupancy Grace Period (Minutes)", defaultValue: 2, required: true, description: "Prevents motor wear by waiting this long after a room clears before resuming."
            
            paragraph "<b>Phantom Draw Assassin:</b>"
            input "kwRate", "decimal", title: "Electricity Rate (\$ per kWh)", defaultValue: 0.15, required: true
            input "dockPlug1", "capability.switch", title: "Vacuum 1 Dock Smart Plug", required: false
            input "dockPlug2", "capability.switch", title: "Vacuum 2 Dock Smart Plug", required: false
            input "wakeUpTime", "time", title: "Daily Time to Wake Docks (Prep for routines)", required: false
        }

        section("<b>5. Consumable & Error Alerts</b>") {
            input "alertThreshold", "number", title: "Alert when consumables drop below (%)", defaultValue: 5, required: true
            input "autoPauseOnError", "bool", title: "Auto-Pause Vacuum on Error", defaultValue: true
        }
        
        section("<b>6. Logging & Maintenance</b>") {
            input "logEnable", "bool", title: "Enable Informational Logging", defaultValue: true
            input "clearHistory", "bool", title: "Clear Dashboard History", defaultValue: false, submitOnChange: true
            input "clearOccupancy", "bool", title: "Reset All Room Occupancy Counters", defaultValue: false, submitOnChange: true
            input "clearROI", "bool", title: "Reset Financial Savings Counter", defaultValue: false, submitOnChange: true
        }
        
        if (clearHistory) {
            state.history = []
            app.updateSetting("clearHistory", [value: "false", type: "bool"])
        }
        if (clearOccupancy) {
            for (int i = 1; i <= 12; i++) {
                if (settings["enableRoom_${i}"]) state["occMins_${settings["roomName_${i}"]}"] = 0
            }
            app.updateSetting("clearOccupancy", [value: "false", type: "bool"])
        }
        if (clearROI) {
            state.dock1OfflineHours = 0.0
            state.dock2OfflineHours = 0.0
            app.updateSetting("clearROI", [value: "false", type: "bool"])
        }
    }
}

def installed() {
    logInfo "Installed with settings: ${settings}"
    state.history = []
    initialize()
}

def updated() {
    logInfo "Updated with settings: ${settings}"
    if (!state.history) state.history = []
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    addToHistory("App Initialized and Subscriptions Updated.")
    
    state.v1_intentAction = "Idle"
    state.v1_intentRooms = "--"
    state.v1_maskedRooms = []
    
    state.v2_intentAction = "Idle"
    state.v2_intentRooms = "--"
    state.v2_maskedRooms = []
    
    if (wakeUpTime) schedule(wakeUpTime, "wakeDocks")
    
    if (masterSwitch) subscribe(masterSwitch, "switch", masterSwitchHandler)
    if (fullCleanSwitch) subscribe(fullCleanSwitch, "switch.on", fullCleanHandler)
    if (schoolRunSwitch) subscribe(schoolRunSwitch, "switch.on", schoolRunHandler)
    subscribe(location, "mode", modeHandler)
    
    if (dockPlug1) subscribe(dockPlug1, "switch", dockPlugHandler)
    if (dockPlug2) subscribe(dockPlug2, "switch", dockPlugHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            if (settings["roomSwitch_${i}"]) subscribe(settings["roomSwitch_${i}"], "switch.on", roomSwitchHandler)
            if (settings["roomMotion_${i}"]) subscribe(settings["roomMotion_${i}"], "motion", occupancyHandler)
            if (settings["roomLight_${i}"]) subscribe(settings["roomLight_${i}"], "switch", occupancyHandler)
        }
    }
    
    if (vacuum1) {
        subscribe(vacuum1, "state", vacuumStateHandler)
        subscribe(vacuum1, "battery", batteryHandler)
        subscribe(vacuum1, "error", errorHandler)
    }
    if (vacuum2) {
        subscribe(vacuum2, "state", vacuumStateHandler)
        subscribe(vacuum2, "battery", batteryHandler)
        subscribe(vacuum2, "error", errorHandler)
    }
}

// ==========================================
// EVENT HANDLERS
// ==========================================

def batteryHandler(evt) {
    if (evt.value == "100") {
        if (evt.deviceId == vacuum1?.id && dockPlug1 && dockPlug1.currentValue("switch") != "off" && vacuum1.currentValue("state") in ["charging", "docked"]) {
            dockPlug1.off()
            addToHistory("V1 Dock Powered OFF (Phantom Draw Assassin)")
        }
        if (evt.deviceId == vacuum2?.id && dockPlug2 && dockPlug2.currentValue("switch") != "off" && vacuum2.currentValue("state") in ["charging", "docked"]) {
            dockPlug2.off()
            addToHistory("V2 Dock Powered OFF (Phantom Draw Assassin)")
        }
    }
}

def dockPlugHandler(evt) {
    if (evt.value == "off") {
        if (evt.deviceId == dockPlug1?.id && !state.dock1OffTime) state.dock1OffTime = now()
        if (evt.deviceId == dockPlug2?.id && !state.dock2OffTime) state.dock2OffTime = now()
    } else if (evt.value == "on") {
        if (evt.deviceId == dockPlug1?.id && state.dock1OffTime) {
            state.dock1OfflineHours = (state.dock1OfflineHours ?: 0.0) + ((now() - state.dock1OffTime) / 3600000.0)
            state.dock1OffTime = null
        }
        if (evt.deviceId == dockPlug2?.id && state.dock2OffTime) {
            state.dock2OfflineHours = (state.dock2OfflineHours ?: 0.0) + ((now() - state.dock2OffTime) / 3600000.0)
            state.dock2OffTime = null
        }
    }
}

def wakeDocks() {
    if (dockPlug1 && dockPlug1.currentValue("switch") != "on") dockPlug1.on()
    if (dockPlug2 && dockPlug2.currentValue("switch") != "on") dockPlug2.on()
    addToHistory("Docks Powered ON (Scheduled Wake-Up)")
}

def vacuumStateHandler(evt) {
    String vState = evt.value?.toString()?.toLowerCase()
    
    if (vState in ["charging", "docked", "returning to dock", "idle"]) {
        if (evt.device.id == vacuum1?.id && state.v1_maskedRooms?.size() > 0) {
            state.v1_maskedRooms.each { rName -> triggerFanCountdown(rName) }
            state.v1_maskedRooms = []
            state.v1_intentAction = "Idle"
            state.v1_intentRooms = "--"
        }
        if (evt.device.id == vacuum2?.id && state.v2_maskedRooms?.size() > 0) {
            state.v2_maskedRooms.each { rName -> triggerFanCountdown(rName) }
            state.v2_maskedRooms = []
            state.v2_intentAction = "Idle"
            state.v2_intentRooms = "--"
        }
    }
}

def triggerFanCountdown(String roomName) {
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == roomName && settings["roomFan_${i}"]) {
            int delayMins = settings["roomFanTimer_${i}"] ?: 15
            runIn(delayMins * 60, "turnOffFan", [data: [roomId: i], overwrite: true])
            addToHistory("${roomName} Dust Settler: Fan off in ${delayMins}m.")
        }
    }
}

def turnOffFan(data) {
    def fan = settings["roomFan_${data.roomId}"]
    if (fan) {
        fan.off()
        addToHistory("${settings["roomName_${data.roomId}"]} Dust Settler: Complete (Fan OFF)")
    }
}

def masterSwitchHandler(evt) {
    if (evt.value == "off") {
        addToHistory("<span style='color:orange;'>Master Switch: OFF (Suspended)</span>")
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) vacuum1.appPause()
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) vacuum2.appPause()
        unschedule("resumeVacuum1")
        unschedule("resumeVacuum2")
    } else {
        addToHistory("<span style='color:green;'>Master Switch: ON (Resumed)</span>")
    }
}

def occupancyHandler(evt) {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    
    String triggeredRoom = ""
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            if (settings["roomMotion_${i}"]?.id == evt.deviceId || settings["roomLight_${i}"]?.id == evt.deviceId) {
                triggeredRoom = settings["roomName_${i}"]
                break
            }
        }
    }
    if (!triggeredRoom) return

    // Tally Cumulative Occupancy
    if (evt.name == "motion") {
        if (evt.value == "active") {
            state["motionStart_${triggeredRoom}"] = now()
        } else if (evt.value == "inactive") {
            long start = state["motionStart_${triggeredRoom}"] ?: now()
            int mins = ((now() - start) / 60000) as Integer
            if (mins > 0) {
                state["occMins_${triggeredRoom}"] = (state["occMins_${triggeredRoom}"] ?: 0) + mins
            }
        }
    }

    if (state.v1_maskedRooms?.contains(triggeredRoom) || state.v2_maskedRooms?.contains(triggeredRoom)) return 

    if (evt.value == "active" || evt.value == "on") {
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V1 Paused: Intrusion in ${triggeredRoom}</span>")
            vacuum1.appPause()
            unschedule("resumeVacuum1")
        }
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V2 Paused: Intrusion in ${triggeredRoom}</span>")
            vacuum2.appPause()
            unschedule("resumeVacuum2")
        }
    } else if (evt.value == "inactive" || evt.value == "off") {
        int delaySeconds = (settings.pauseGracePeriod ?: 2) * 60
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() == "paused") {
            addToHistory("V1 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
            runIn(delaySeconds, "resumeVacuum1", [overwrite: true])
        }
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() == "paused") {
            addToHistory("V2 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
            runIn(delaySeconds, "resumeVacuum2", [overwrite: true])
        }
    }
}

def resumeVacuum1() {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() == "paused") {
        addToHistory("<span style='color:green;'>V1 Resumed: Grace Period Complete</span>")
        vacuum1.appRoomResume() 
    }
}

def resumeVacuum2() {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() == "paused") {
        addToHistory("<span style='color:green;'>V2 Resumed: Grace Period Complete</span>")
        vacuum2.appRoomResume() 
    }
}

def modeHandler(evt) {
    if (goodNightMode && evt.value in goodNightMode) {
        if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
        if (goodNightConflicts) {
            def activeConflicts = goodNightConflicts.findAll { it.currentValue("switch") == "on" }
            if (activeConflicts.size() > 0) {
                def conflictNames = activeConflicts.collect { it.displayName }.join(", ")
                addToHistory("<span style='color:orange;'>Good Night Blocked: Active Devices (${conflictNames})</span>")
                return
            }
        }
        wakeDocks() 
        addToHistory("Triggered: Good Night Sweep")
        executeRoomClean(goodNightRooms, [ignoreSkip: false])
    }
    
    if (fullCleanMode && evt.value in fullCleanMode) {
        if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
        wakeDocks()
        addToHistory("Triggered: Configurable Full Clean Mode")
        executeRoomClean(["All"], [
            ignoreSkip: settings.fullCleanIgnoreSkip, 
            suction: settings.fullCleanSuction, 
            water: settings.fullCleanWater
        ])
    }
}

def fullCleanHandler(evt) {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    wakeDocks()
    addToHistory("Triggered: Full Clean Switch")
    executeRoomClean(["All"], [ignoreSkip: true]) 
}

def roomSwitchHandler(evt) {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    wakeDocks()
    for (int i = 1; i <= 12; i++) {
        def sw = settings["roomSwitch_${i}"]
        if (settings["enableRoom_${i}"] && sw && sw.id == evt.deviceId) {
            executeRoomClean([settings["roomName_${i}"]], [ignoreSkip: true])
            break
        }
    }
}

def schoolRunHandler(evt) {
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    wakeDocks()
    addToHistory("Triggered: School Run Routine")
    executeRoomClean(schoolRunRooms, [ignoreSkip: false])
}

def errorHandler(evt) {
    String errorMsg = evt.value?.toString()?.toLowerCase()
    if (errorMsg == "no error" || errorMsg == "0") return
    
    String msg = "${evt.device.displayName} Error: ${evt.value}"
    addToHistory("<span style='color:red;'><b>${msg}</b></span>")
    if (notifyDevice) notifyDevice.deviceNotification(msg)
    if (autoPauseOnError) evt.device.appPause()
}

// ==========================================
// CORE LOGIC: PRE-EVALUATION & DISPATCH
// ==========================================

void executeRoomClean(List selectedRoomNames, Map options = [:]) {
    boolean ignoreSkip = options.ignoreSkip ?: false
    String overrideSuction = options.suction
    String overrideWater = options.water

    if (!selectedRoomNames) return

    if (selectedRoomNames.contains("All")) {
        List<String> allActive = []
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) allActive << settings["roomName_${i}"]
        }
        selectedRoomNames = allActive
    }

    def v1Queue = []
    def v2Queue = []
    def skippedOccupied = []
    def skippedClean = []

    for (String targetName : selectedRoomNames) {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == targetName) {
                
                int occMins = state["occMins_${targetName}"] ?: 0
                int minThreshold = settings["roomOccupancyThreshold_${i}"] ?: 15
                int heavyThreshold = settings["roomHeavyTraffic_${i}"] ?: 120
                
                if (!ignoreSkip && occMins < minThreshold) {
                    skippedClean << "${targetName} (${occMins}/${minThreshold}m)"
                    continue 
                }

                boolean isOccupied = false
                if (settings["roomMotion_${i}"] && settings["roomMotion_${i}"].currentValue("motion") == "active") isOccupied = true
                if (settings["roomLight_${i}"] && settings["roomLight_${i}"].currentValue("switch") == "on") isOccupied = true
                
                if (isOccupied) {
                    skippedOccupied << targetName
                    continue
                }

                String finalSuction = overrideSuction ?: settings["roomSuction_${i}"] ?: "Balanced"
                if (!overrideSuction && occMins >= heavyThreshold) {
                    finalSuction = "Turbo"
                    addToHistory("Adaptive Suction: ${targetName} set to Turbo (${occMins}m traffic)")
                }

                def roomData = [
                    name: targetName,
                    id: settings["roomId_${i}"],
                    zone: settings["roomZone_${i}"],
                    water: overrideWater ?: settings["roomWater_${i}"] ?: "Medium",
                    suction: finalSuction,
                    seq: settings["roomSeq_${i}"] ?: 99,
                    index: i
                ]
                if (settings["vacuumAssign_${i}"] == "Vacuum 2") v2Queue << roomData
                else v1Queue << roomData
            }
        }
    }

    if (skippedOccupied.size() > 0) addToHistory("<span style='color:orange;'>Skipped (Occupied): ${skippedOccupied.join(', ')}</span>")
    if (skippedClean.size() > 0) addToHistory("<span style='color:gray;'>Skipped (Clean): ${skippedClean.join(', ')}</span>")

    if (v1Queue.size() > 0 && vacuum1) {
        v1Queue.sort { it.seq }
        state.v1_maskedRooms = v1Queue.collect { it.name }
        state.v1_intentAction = "Dispatched (Sequence)"
        state.v1_intentRooms = state.v1_maskedRooms.join(", ")
        
        v1Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            if (room.zone) vacuum1.appZoneClean(room.zone)
            else if (room.id) vacuum1.appRoomClean(room.id, room.water, room.suction) 
            state["occMins_${room.name}"] = 0 
            pauseExecution(2500)
        }
    }

    if (v2Queue.size() > 0 && vacuum2) {
        v2Queue.sort { it.seq }
        state.v2_maskedRooms = v2Queue.collect { it.name }
        state.v2_intentAction = "Dispatched (Sequence)"
        state.v2_intentRooms = state.v2_maskedRooms.join(", ")
        
        v2Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            if (room.zone) vacuum2.appZoneClean(room.zone)
            else if (room.id) vacuum2.appRoomClean(room.id, room.water, room.suction) 
            state["occMins_${room.name}"] = 0 
            pauseExecution(2500)
        }
    }
}

void logInfo(String msg) {
    if (settings?.logEnable) log.info "${app.name}: ${msg}"
}

// ==========================================
// HTML DASHBOARD GENERATION
// ==========================================

void addToHistory(String event) {
    if (!state.history) state.history = []
    String timeStamp = new Date().format("MM/dd/yy hh:mm:ss a", location.timeZone)
    state.history.add(0, "[${timeStamp}] ${event}")
    if (state.history.size() > 25) state.history = state.history[0..24] 
}

String buildHistoryHTML() {
    if (!state.history || state.history.size() == 0) return "<div style='padding:10px; border: 1px solid #ccc; border-radius: 5px; background-color: #fcfcfc;'><i>No events logged yet.</i></div>"
    
    String html = "<div style='max-height: 250px; overflow-y: auto; padding: 10px; border: 1px solid #ccc; border-radius: 5px; background-color: #fcfcfc; font-size: 13px; font-family: monospace;'>"
    state.history.each { item -> html += "<div style='margin-bottom: 4px; border-bottom: 1px solid #eee; padding-bottom: 2px;'>${item}</div>" }
    html += "</div>"
    return html
}

double getVacWatts(String modelStr, Double customWatts) {
    if (modelStr?.contains("S8")) return 3.0
    if (modelStr?.contains("QRevo")) return 4.0
    return customWatts ?: 3.0
}

String generateVacuumTable(vacDevice, String vacTitle, String intentAction, String intentRooms) {
    if (!vacDevice) return ""
    String vState = vacDevice.currentValue("state") ?: "Unknown"
    String vBat = vacDevice.currentValue("battery") ?: "--"
    String vErr = vacDevice.currentValue("error") ?: "No error"

    String stateColor = "black"
    if (vState.toLowerCase() in ["cleaning", "room clean", "zone clean"]) stateColor = "green"
    if (vState.toLowerCase() == "charging") stateColor = "blue"
    if (vState.toLowerCase() in ["paused", "returning to dock"]) stateColor = "orange"

    String html = "<div style='margin-bottom: 15px;'><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
    html += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th colspan='2' style='padding: 8px;'>${vacTitle}</th></tr>"
    html += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px; width: 40%;'><b>App Intent (Action)</b></td><td style='padding: 8px; color: #0066cc;'><b>${intentAction}</b></td></tr>"
    html += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>App Intent (Target)</b></td><td style='padding: 8px;'>${intentRooms}</td></tr>"
    html += "<tr style='border-bottom: 1px solid #ddd; background-color: #f9f9f9;'><td style='padding: 8px;'><b>Actual Device State</b></td><td style='padding: 8px; color: ${stateColor}; font-weight: bold;'>${vState.capitalize()}</td></tr>"
    html += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Battery Level</b></td><td style='padding: 8px;'>${vBat}%</td></tr>"
    
    String errColor = (vErr.toLowerCase() == 'no error') ? "green" : "red"
    html += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Hardware Error</b></td><td style='padding: 8px; color: ${errColor};'>${vErr.capitalize()}</td></tr>"
    html += "</table></div>"
    return html
}

String buildDashboardHTML() {
    String html = ""
    if (vacuum1) html += generateVacuumTable(vacuum1, "Vacuum 1 (Primary)", state.v1_intentAction ?: "Idle", state.v1_intentRooms ?: "--")
    if (vacuum2) html += generateVacuumTable(vacuum2, "Vacuum 2 (Secondary)", state.v2_intentAction ?: "Idle", state.v2_intentRooms ?: "--")
    
    List<String> utilList = []
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) {
            int mins = state["occMins_${settings["roomName_${i}"]}"] ?: 0
            utilList << "<b>${settings["roomName_${i}"]}:</b> ${mins}m"
        }
    }
    
    boolean isPaused = masterSwitch ? (masterSwitch.currentValue("switch") == "off") : false
    String globalStatus = isPaused ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
        
    html += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>Global System Mode:</b> ${globalStatus}</div>"
    
    if (utilList.size() > 0) {
        html += "<div style='margin-top: 10px; padding: 8px; background: #f4f4f4; border: 1px solid #ddd; border-radius: 4px; font-size: 12px; color: #555;'><b>Current Room Utilization:</b> ${utilList.join(' | ')}</div>"
    }

    // ROI Calculation
    double v1Watts = getVacWatts(settings.vac1Model, settings.vac1Watts)
    double v2Watts = getVacWatts(settings.vac2Model, settings.vac2Watts)
    
    double currentV1Off = (state.dock1OffTime) ? ((now() - state.dock1OffTime) / 3600000.0) : 0.0
    double currentV2Off = (state.dock2OffTime) ? ((now() - state.dock2OffTime) / 3600000.0) : 0.0
    
    double totalV1Hours = (state.dock1OfflineHours ?: 0.0) + currentV1Off
    double totalV2Hours = (state.dock2OfflineHours ?: 0.0) + currentV2Off
    
    double kwhSaved = ((totalV1Hours * v1Watts) + (totalV2Hours * v2Watts)) / 1000.0
    double moneySaved = kwhSaved * (settings.kwRate ?: 0.15)
    
    if (kwhSaved > 0) {
        html += "<div style='margin-top: 10px; padding: 8px; background: #e8f5e9; border: 1px solid #c8e6c9; border-radius: 4px; font-size: 13px; color: #2e7d32;'>"
        html += "<b>Financial & Energy ROI:</b> System has prevented <b>${String.format("%.2f", kwhSaved)} kWh</b> of phantom draw, saving <b>\$${String.format("%.2f", moneySaved)}</b>.</div>"
    }

    return html
}
