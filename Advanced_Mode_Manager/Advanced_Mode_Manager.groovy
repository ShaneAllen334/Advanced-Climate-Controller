/**
 * Advanced Mode Manager
 */

definition(
    name: "Advanced Mode Manager",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Mode engine. Handles timed transitions AND instant state enforcement. Features modular UI for Mode-based Device Control and Mode-to-Mode Transitions.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Mode Manager</b>", install: true, uninstall: true) {
        
        section("<b>Live Mode Dashboard & History</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your current Hubitat location mode and logs recent activity.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #8e44ad;'>" +
                      "<b>Engine Status:</b> ${statusExplanation}</div>"

            def currentMode = location.mode ?: "Unknown"
            def pendingActionStr = "<span style='color:gray;'>None (Stable)</span>"
            if (state.pendingTargetMode && state.pendingTargetTime) {
                def remainingMins = Math.max(0, Math.round((state.pendingTargetTime - now()) / 60000))
                pendingActionStr = "<span style='color:#e67e22;'><b>Shifting to '${state.pendingTargetMode}' in ${remainingMins} minutes</b></span>"
            }

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Mode Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Current Hub Mode</td><td class="dash-val" style="color:#8e44ad; font-size: 16px;">${currentMode}</td></tr>
                    <tr><td class="dash-hl">Pending Automated Transition</td><td class="dash-val">${pendingActionStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            input "sweepModeBtn", "button", title: "Sweep Mode (Force Immediate Enforcement)"
            if (state.pendingTargetMode) input "abortTransition", "button", title: "Abort Pending Transition"

            paragraph "<hr><b>Recent Action History</b>"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<div style='background-color:#f8f9fa; padding:10px; border:1px solid #ccc; font-size: 13px; font-family: monospace; max-height: 200px; overflow-y: auto;'>${historyStr}</div>"
            }
            input "clearHistory", "button", title: "Clear Action History"
        }

        // ==============================================================================
        // SECTION 1: DEVICE CONTROL PER MODE
        // ==============================================================================
        section("<b>Device Control per Mode</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Instant Enforcement:</b> Define what devices should turn on, turn off, or lock the exact moment a specific mode becomes active. <i>Click a rule below to expand it.</i></div>"
        }
        
        for (int i = 1; i <= 8; i++) {
            def dynamicTitle = (settings["dcEnable${i}"] && settings["dcMode${i}"]) ? "<b>Device Control Rule ${i} (${settings["dcMode${i}"]})</b>" : "<b>Device Control Rule ${i}</b>"
            
            section(dynamicTitle, hideable: true, hidden: true) {
                input "dcEnable${i}", "bool", title: "<b>Enable Device Control ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["dcEnable${i}"]) {
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #2ecc71;'><b>State Sweep / Device Enforcement</b></div>"
                    input "dcMode${i}", "mode", title: "<b>[TRIGGER]</b> When mode becomes...", required: true, submitOnChange: true
                    input "dcSwitchesOff${i}", "capability.switch", title: "Turn OFF these switches", required: false, multiple: true
                    input "dcSwitchesOn${i}", "capability.switch", title: "Turn ON these switches", required: false, multiple: true
                    input "dcLocksLock${i}", "capability.lock", title: "Lock these doors", required: false, multiple: true
                    input "dcGarageClose${i}", "capability.garageDoorControl", title: "Close these garages", required: false, multiple: true
                    
                    // --- INOVELLI LED CONTROL ---
                    input "dcInovelli${i}", "capability.configuration", title: "Set Inovelli LED Notifications for these switches", required: false, multiple: true, submitOnChange: true
                    if (settings["dcInovelli${i}"]) {
                        input "dcInovelliTarget${i}", "enum", title: "Target LEDs", required: true, defaultValue: "All", options: [
                            "All":"All LEDs", "7":"LED 7 (Top)", "6":"LED 6", "5":"LED 5", "4":"LED 4 (Middle)", "3":"LED 3", "2":"LED 2", "1":"LED 1 (Bottom)"
                        ]
                        input "dcInovelliColor${i}", "enum", title: "LED Color", required: true, options: [
                            "0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", 
                            "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", 
                            "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"
                        ]
                        input "dcInovelliLevel${i}", "number", title: "LED Light % (0-100)", required: true, defaultValue: 100
                        input "dcInovelliEffect${i}", "enum", title: "Notification Effect", required: true, options: [
                            "0":"Off", "1":"Solid", "2":"Fast Blink", "3":"Slow Blink", "4":"Pulse", "5":"Chase", "6":"Falling", "7":"Rising", "8":"Blink"
                        ], defaultValue: "1"
                        
                        input "dcInovelliBlocker${i}", "capability.switch", title: "Block LED color changes if this switch is ON (e.g., Mail Arrived Virtual Switch)", required: false, multiple: false
                    }
                }
            }
        }

        // ==============================================================================
        // SECTION 2: MODE TO MODE TRANSITIONS
        // ==============================================================================
        section("<b>Mode to Mode Transitions</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Automated Timers:</b> Configure delayed transitions between modes. Example: When mode changes to 'Arrival', wait 15 minutes, then change to 'Home'. <i>Click a rule below to expand it.</i></div>"
        }
            
        for (int i = 1; i <= 8; i++) {
            def dynamicTitle = (settings["transEnable${i}"] && settings["transTriggerMode${i}"] && settings["transTargetMode${i}"]) ? "<b>Transition Rule ${i} (${settings["transTriggerMode${i}"]} ➔ ${settings["transTargetMode${i}"]})</b>" : "<b>Transition Rule ${i}</b>"
        
            section(dynamicTitle, hideable: true, hidden: true) {
                input "transEnable${i}", "bool", title: "<b>Enable Transition Rule ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["transEnable${i}"]) {
                    input "transTriggerMode${i}", "mode", title: "<b>[TRIGGER]</b> If mode becomes...", required: true, submitOnChange: true
                    input "transDelay${i}", "number", title: "<b>[TIMER]</b> Wait this many minutes", required: true, defaultValue: 15
                    input "transTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: true, submitOnChange: true
                    
                    // Modular Toggle: Presence Tethering
                    input "transUseTether${i}", "bool", title: "Enable Presence Tethering?", defaultValue: false, submitOnChange: true
                    if (settings["transUseTether${i}"]) {
                        paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #3498db;'><b>Presence Tethering</b> (Timer Intercept)</div>"
                        input "transTetherPresence${i}", "capability.presenceSensor", title: "Tether to Presence Sensor", required: true
                        input "transTetherFallbackMode${i}", "mode", title: "If sensor departs, force mode to...", required: true
                    }

                    // Modular Toggle: Condition Gates
                    input "transUseGates${i}", "bool", title: "Enable Condition Gates?", defaultValue: false, submitOnChange: true
                    if (settings["transUseGates${i}"]) {
                        paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #e67e22;'><b>Condition Gates</b> (Pre-Transition Check)</div>"
                        input "transConditionMotion${i}", "capability.motionSensor", title: "Abort if Motion active", required: false, multiple: true
                        input "transConditionPower${i}", "capability.powerMeter", title: "Abort if Power > Threshold", required: false
                        if (settings["transConditionPower${i}"]) input "transPowerThreshold${i}", "decimal", title: "Watts", defaultValue: 15.0
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    clearPendingTransition()
    
    subscribe(location, "mode", modeChangeHandler)
    
    for (int i = 1; i <= 8; i++) {
        // Subscribe to Presence Tethers
        if (settings["transEnable${i}"] && settings["transUseTether${i}"] && settings["transTetherPresence${i}"]) {
            subscribe(settings["transTetherPresence${i}"], "presence", presenceTetherHandler)
        }
        
        // Subscribe to LED Blocker Switches
        if (settings["dcEnable${i}"] && settings["dcInovelliBlocker${i}"]) {
            subscribe(settings["dcInovelliBlocker${i}"], "switch.off", blockerSwitchHandler)
        }
    }
    
    logAction("Mode Manager Initialized.")
}

String getHumanReadableStatus() {
    if (state.pendingTargetMode) return "Countdown Active. Monitoring tethers and waiting to transition."
    return "Idle. Waiting for a trigger mode to activate."
}

def appButtonHandler(btn) {
    if (btn == "abortTransition") { 
        logAction("User aborted transition to '${state.pendingTargetMode}'.")
        clearPendingTransition() 
    }
    else if (btn == "clearHistory") { 
        state.actionHistory = []
        logAction("History cleared.") 
    }
    else if (btn == "sweepModeBtn") { 
        logAction("Sweep Triggered. Enforcing rules for current mode...")
        executeSweep() 
    }
}

// ------------------------------------------------------------------------------
// HANDLERS
// ------------------------------------------------------------------------------

def presenceTetherHandler(evt) {
    if (state.pendingRuleNumber == null || evt.value != "not present") return
    def ruleIdx = state.pendingRuleNumber
    if (settings["transUseTether${ruleIdx}"] && settings["transTetherPresence${ruleIdx}"]?.id == evt.device.id) {
        def fallback = settings["transTetherFallbackMode${ruleIdx}"]
        logAction("🚨 Tether Broken: ${evt.device.displayName} left. Forcing mode to '${fallback}'.")
        clearPendingTransition()
        location.setMode(fallback)
    }
}

// This runs when a blocking switch (like Mail Arrived) turns off
def blockerSwitchHandler(evt) {
    if (evt.value == "off") {
        logAction("LED Blocker Switch '${evt.device.displayName}' turned off. Re-evaluating LED colors for current mode...")
        def currentMode = location.mode
        
        // Loop through all rules to find the one matching the CURRENT mode
        for (int i = 1; i <= 8; i++) {
            if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
                if (settings["dcInovelli${i}"] && settings["dcInovelliColor${i}"] != null) {
                    
                    // Verify the rule's blocker is indeed off before applying
                    def ruleBlocker = settings["dcInovelliBlocker${i}"]
                    if (!ruleBlocker || ruleBlocker.currentValue("switch") != "on") {
                        enforceInovelliLEDs(i)
                        
                        def colorMap = ["0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"]
                        def colorName = colorMap[settings["dcInovelliColor${i}"]] ?: settings["dcInovelliColor${i}"]
                        def levelStr = settings["dcInovelliLevel${i}"] ?: 100
                        def effectStr = settings["dcInovelliEffect${i}"] ?: "1"
                        def targetStr = settings["dcInovelliTarget${i}"] == "All" ? "All LEDs" : "LED ${settings["dcInovelliTarget${i}"]}"
                        logAction("Deferred LED Update -> [Inovelli: ${targetStr} ${colorName} at ${levelStr}% (Effect: ${effectStr})]")
                    }
                }
            }
        }
    }
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    
    if (state.pendingTargetMode != null) {
        logAction("Intervention: Mode changed to '${newMode}'. Aborting timer for '${state.pendingTargetMode}'.")
        clearPendingTransition()
    }
    
    // 1. Process Instant Device Controls
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == newMode) {
            logAction("Device Control ${i}: Instant Enforcement triggered for mode '${newMode}'.")
            enforceDevices(i)
        }
    }

    // 2. Process Transition Timers
    for (int i = 1; i <= 8; i++) {
        if (settings["transEnable${i}"] && settings["transTriggerMode${i}"] == newMode) {
            def delayMins = settings["transDelay${i}"] ?: 0
            
            if (delayMins > 0) {
                def target = settings["transTargetMode${i}"]
                state.pendingTriggerMode = newMode
                state.pendingTargetMode = target
                state.pendingTargetTime = now() + (delayMins * 60000)
                state.pendingRuleNumber = i
                logAction("Transition Rule ${i}: Delay active. Shifting '${newMode}' to '${target}' in ${delayMins} mins.")
                runIn((delayMins * 60).toInteger(), executeTransition)
                break // Only allow one transition timer to run at a time
            }
        }
    }
}

// ------------------------------------------------------------------------------
// EXECUTION LOGIC
// ------------------------------------------------------------------------------

def executeSweep() {
    def currentMode = location.mode
    def matchFound = false
    
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
            logAction("Sweep Match (Device Control ${i}): Executing enforcement for '${currentMode}'.")
            enforceDevices(i)
            matchFound = true
        }
    }
    if (!matchFound) logAction("Sweep: No Device Control rules found for mode '${currentMode}'.")
}

def executeTransition() {
    def ruleIdx = state.pendingRuleNumber
    if (location.mode == state.pendingTriggerMode && ruleIdx != null) {
        
        // Condition Gates Check
        if (settings["transUseGates${ruleIdx}"]) {
            if (settings["transConditionMotion${ruleIdx}"]?.any { it.currentValue("motion") == "active" }) {
                logAction("🛑 Aborted: Motion active.")
                clearPendingTransition()
                return
            }
            def pSens = settings["transConditionPower${ruleIdx}"]
            if (pSens && (pSens.currentValue("power") ?: 0) > (settings["transPowerThreshold${ruleIdx}"] ?: 15)) {
                logAction("🛑 Aborted: Power threshold exceeded.")
                clearPendingTransition()
                return
            }
        }
        
        def target = state.pendingTargetMode
        logAction("Transitioning '${location.mode}' to '${target}'.")
        clearPendingTransition()
        location.setMode(target) 
        
    } else {
        logAction("Failsafe: Mode mismatch. Aborting.")
        clearPendingTransition()
    }
}

def enforceDevices(ruleIdx) {
    def logMsg = "State Sweep -> "
    
    if (settings["dcSwitchesOff${ruleIdx}"]) { settings["dcSwitchesOff${ruleIdx}"].off(); logMsg += "[OFF] " }
    if (settings["dcSwitchesOn${ruleIdx}"]) { settings["dcSwitchesOn${ruleIdx}"].on(); logMsg += "[ON] " }
    if (settings["dcLocksLock${ruleIdx}"]) { settings["dcLocksLock${ruleIdx}"].lock(); logMsg += "[Locked] " }
    if (settings["dcGarageClose${ruleIdx}"]) { settings["dcGarageClose${ruleIdx}"].close(); logMsg += "[Closed] " }
    
    // Process Inovelli LED color changes
    if (settings["dcInovelli${ruleIdx}"] && settings["dcInovelliColor${ruleIdx}"] != null) {
        def blocker = settings["dcInovelliBlocker${ruleIdx}"]
        
        // Check if the user defined a blocker switch and if it is currently ON
        if (blocker && blocker.currentValue("switch") == "on") {
            logMsg += "[Inovelli LEDs: Blocked by '${blocker.displayName}'] "
        } else {
            // Safe to change colors
            enforceInovelliLEDs(ruleIdx)
            
            def colorMap = ["0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"]
            def colorName = colorMap[settings["dcInovelliColor${ruleIdx}"]] ?: settings["dcInovelliColor${ruleIdx}"]
            def level = settings["dcInovelliLevel${ruleIdx}"] ?: 100
            def effect = settings["dcInovelliEffect${ruleIdx}"] ?: "1"
            def targetStr = settings["dcInovelliTarget${ruleIdx}"] == "All" ? "All LEDs" : "LED ${settings["dcInovelliTarget${ruleIdx}"]}"
            
            logMsg += "[Inovelli: ${targetStr} ${colorName} at ${level}% (Effect: ${effect})] "
        }
    }
    
    if (logMsg != "State Sweep -> ") logAction(logMsg)
}

// Helper function to process native driver commands for Inovelli LEDs
def enforceInovelliLEDs(ruleIdx) {
    def colorVal = settings["dcInovelliColor${ruleIdx}"]?.toInteger() ?: 170
    def levelVal = settings["dcInovelliLevel${ruleIdx}"]?.toInteger() ?: 100
    def effectVal = settings["dcInovelliEffect${ruleIdx}"]?.toInteger() ?: 1
    def targetVal = settings["dcInovelliTarget${ruleIdx}"] ?: "All"
    def durationVal = 255 // 255 = Indefinite duration
    
    settings["dcInovelli${ruleIdx}"].each { dev ->
        // 1. Update the base "Idle" colors and intensities using standard setParameter
        // The driver's setParameter method handles size natively
        dev.setParameter(95, colorVal) // LED Color (When On)
        dev.setParameter(96, colorVal) // LED Color (When Off)
        dev.setParameter(97, levelVal) // LED Intensity (When On)
        dev.setParameter(98, levelVal) // LED Intensity (When Off)
        
        // 2. Trigger the active Notification Effect using the driver's native custom commands
        if (targetVal == "All") {
            if (dev.hasCommand("ledEffectAll")) {
                dev.ledEffectAll(effectVal, colorVal, levelVal, durationVal)
            } else {
                log.warn "Advanced Mode Manager: Device ${dev.displayName} does not support ledEffectAll command."
            }
        } else {
            // Target is a specific LED (1-7)
            if (dev.hasCommand("ledEffectOne")) {
                dev.ledEffectOne(targetVal, effectVal, colorVal, levelVal, durationVal)
            } else {
                log.warn "Advanced Mode Manager: Device ${dev.displayName} does not support ledEffectOne command."
            }
        }
    }
}

// ------------------------------------------------------------------------------
// UTILITY FUNCTIONS
// ------------------------------------------------------------------------------

def clearPendingTransition() {
    unschedule(executeTransition)
    state.pendingTargetMode = null
    state.pendingTriggerMode = null
    state.pendingTargetTime = null
    state.pendingRuleNumber = null
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if (h.size() > 30) h = h[0..29]
    state.actionHistory = h
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
