/**
 * Advanced House Security
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced House Security",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Context-aware security engine that learns lifestyle patterns to reduce false alarms. Integrates TTS, Sirens, and Life-Safety evacuation automation.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced House Security</b>", install: true, uninstall: true) {
        
        section("<b>Live Security Dashboard</b>") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, context-aware view of your home's security posture, active alerts, and recent sensor timelines.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            def alertColor = state.currentAlertLevel == "Critical" ? "#d9534f" : (state.currentAlertLevel == "Warning" ? "#f0ad4e" : "#007bff")
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid ${alertColor};'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            // Gather Device States for Dashboard
            def activeDoors = perimeterDoors?.findAll { it.currentValue("contact") == "open" }?.collect { it.displayName }?.join(", ") ?: "None"
            def activeMotion = (outdoorMotion?.findAll { it.currentValue("motion") == "active" } ?: []) + (indoorMotion?.findAll { it.currentValue("motion") == "active" } ?: [])
            activeMotion = activeMotion.collect { it.displayName }.join(", ") ?: "None"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: left; }
                .dash-table th { background-color: #343a40; color: white; text-align: center; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; width: 30%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-transform: uppercase; font-size: 12px; color: #495057; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Sensor Posture</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Open Doors/Windows</td><td>${activeDoors}</td></tr>
                    <tr><td class="dash-hl">Active Motion</td><td>${activeMotion}</td></tr>
                    <tr><td class="dash-hl">Current Alert Level</td><td style="color:${alertColor}; font-weight:bold;">${state.currentAlertLevel ?: "Normal"}</td></tr>
                </tbody>
            </table>
            """
            
            // Build Smoke/CO Table if devices are selected
            def allDetectors = (smokeDetectors ?: []) + (coDetectors ?: [])
            allDetectors = allDetectors.unique { it.id } // Remove duplicates if a device does both
            
            if (allDetectors.size() > 0) {
                dashHTML += "<table class='dash-table' style='margin-top:10px;'><thead><tr><th colspan='3'>Life Safety Devices (Smoke/CO)</th></tr><tr><th>Device</th><th>Current Status</th><th>Battery</th></tr></thead><tbody>"
                allDetectors.each { dev ->
                    def sState = dev.hasAttribute("smoke") ? dev.currentValue("smoke") : "unknown"
                    def cState = dev.hasAttribute("carbonMonoxide") ? dev.currentValue("carbonMonoxide") : "unknown"
                    def batt = dev.hasAttribute("battery") ? dev.currentValue("battery") : "--"
                    
                    def statusColor = (sState == "detected" || cState == "detected") ? "red" : "green"
                    def displayStatus = "Clear"
                    if (sState == "detected" && cState == "detected") displayStatus = "SMOKE & CO DETECTED"
                    else if (sState == "detected") displayStatus = "SMOKE DETECTED"
                    else if (cState == "detected") displayStatus = "CO DETECTED"
                    else if (sState == "tested" || cState == "tested") displayStatus = "Testing Mode"
                    
                    def battColor = (batt != "--" && batt.toInteger() < 20) ? "red" : "black"
                    def battDisplay = batt != "--" ? "${batt}%" : "N/A"
                    
                    dashHTML += "<tr><td class='dash-hl'>${dev.displayName}</td><td style='color:${statusColor}; font-weight:bold;'>${displayStatus}</td><td style='color:${battColor}; font-weight:bold;'>${battDisplay}</td></tr>"
                }
                dashHTML += "</tbody></table>"
            }
            
            paragraph dashHTML
            
            if (state.activeAlerts && state.activeAlerts.size() > 0) {
                input "clearAlertsBtn", "button", title: "Dismiss Active Alerts"
            }
        }

        section("<b>Recent Context Engine Events</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Displays the sequential timeline the app is using to determine context (e.g., Door opened -> Motion active = Resident exiting).</div>"
            if (state.eventHistory) {
                def historyStr = state.eventHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No recent events tracked.</i>"
            }
        }

        section("<b>1. Perimeter Setup (Doors, Windows, Glass)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Establishes your first line of defense. The engine watches these to determine if someone is breaching the perimeter or simply opening a door to walk outside.</div>"
            input "perimeterDoors", "capability.contactSensor", title: "Select Exterior Doors", required: false, multiple: true
            input "perimeterWindows", "capability.contactSensor", title: "Select Perimeter Windows", required: false, multiple: true
            input "glassBreakSensors", "capability.sensor", title: "Select Glass Break Sensors", required: false, multiple: true
            input "outboundGracePeriod", "number", title: "Outbound Grace Period (Seconds) - Time to ignore outdoor motion after a door opens", required: false, defaultValue: 30
        }

        section("<b>2. Interior & Exterior Motion</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks movement. Exterior motion triggers warnings unless preceded by a door opening. Interior motion confirms intrusions if it follows a glass break or forced entry.</div>"
            input "outdoorMotion", "capability.motionSensor", title: "Select Outdoor Motion Sensors", required: false, multiple: true
            input "indoorMotion", "capability.motionSensor", title: "Select Indoor Motion Sensors", required: false, multiple: true
        }

        section("<b>3. Life Safety & Emergency Evacuation Response</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Monitors life-safety sensors. If smoke or CO is detected, it instantly bypasses all context logic, forces a Critical alert, and executes your emergency evacuation automations to assist in safely exiting the home.</div>"
            input "smokeDetectors", "capability.smokeDetector", title: "Select Smoke Detectors", required: false, multiple: true
            input "coDetectors", "capability.carbonMonoxideDetector", title: "Select CO Detectors", required: false, multiple: true
            
            paragraph "<b>Emergency Automations (Triggered on Smoke/CO)</b>"
            input "emergencySwitches", "capability.switch", title: "Turn ON Standard Lights/Switches", required: false, multiple: true
            input "emergencyColoredLights", "capability.colorControl", title: "Turn ON & Change Color of RGB Lights", required: false, multiple: true
            if (settings.emergencyColoredLights) {
                input "emergencyLightColor", "enum", title: "Select RGB Emergency Color", options: ["Red", "White", "Blue", "Green", "Yellow"], required: false, defaultValue: "Red"
            }
            input "emergencyLocks", "capability.lock", title: "Unlock Doors (For rapid exit / first responders)", required: false, multiple: true
            input "emergencyTTSMessage", "text", title: "Custom TTS Evacuation Message", required: false, defaultValue: "Emergency. Smoke or Carbon Monoxide detected. Evacuate the house immediately."
            input "emergencySirenFile", "number", title: "Zooz Siren Sound File # for Fire/CO", required: false, defaultValue: 3
        }

        section("<b>4. General Alerts & Notifications (TTS & Sirens)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines how the house reacts when standard security events occur (e.g., perimeter breaches, unknown motion).</div>"
            input "ttsDevices", "capability.speechSynthesis", title: "Select TTS Devices (Smart Speakers)", required: false, multiple: true
            
            paragraph "<b>Zooz Siren & Chime Integration</b>"
            input "zoozSirens", "capability.chime", title: "Select Zooz Sirens/Chimes", required: false, multiple: true
            input "warningSoundNumber", "number", title: "Sound File # for Warnings (e.g., Outdoor motion)", required: false, defaultValue: 1
            input "criticalSoundNumber", "number", title: "Sound File # for Critical (e.g., Glass break/Intruder)", required: false, defaultValue: 2
            
            paragraph "<b>Push Notifications</b>"
            input "pushDevices", "capability.notification", title: "Select Push Notification Devices", required: false, multiple: true
        }

        section("<b>Disclaimer</b>") {
            paragraph "<div style='font-size:12px; color:#888;'><b>Legal Disclaimer:</b> ShaneAllen is not responsible for any damage or liability with the use of this application. This is a user customer application, use at your own discretion.</div>"
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); initialize() }

def initialize() {
    if (!state.eventHistory) state.eventHistory = []
    if (!state.activeAlerts) state.activeAlerts = []
    state.currentAlertLevel = "Normal"
    state.lastDoorOpenTime = 0
    state.lastGlassBreakTime = 0
    
    if (perimeterDoors) subscribe(perimeterDoors, "contact", contactHandler)
    if (perimeterWindows) subscribe(perimeterWindows, "contact", contactHandler)
    if (outdoorMotion) subscribe(outdoorMotion, "motion", outdoorMotionHandler)
    if (indoorMotion) subscribe(indoorMotion, "motion", indoorMotionHandler)
    
    // Glass break usually registers as 'sound' or 'tamper' depending on the driver, assuming 'sound' for now
    if (glassBreakSensors) subscribe(glassBreakSensors, "sound.detected", glassBreakHandler) 
    
    // Life Safety Subscriptions
    if (smokeDetectors) subscribe(smokeDetectors, "smoke", lifeSafetyHandler)
    if (coDetectors) subscribe(coDetectors, "carbonMonoxide", lifeSafetyHandler)
    
    logAction("Advanced House Security Initialized.")
}

String getHumanReadableStatus() {
    if (state.currentAlertLevel == "Critical") {
        def alertStr = state.activeAlerts.join(", ")
        return "<span style='color:#d9534f;'><b>CRITICAL ALERT:</b></span> Emergency event detected! Escalated due to: ${alertStr}"
    } else if (state.currentAlertLevel == "Warning") {
        def alertStr = state.activeAlerts.join(", ")
        return "<span style='color:#f0ad4e;'><b>WARNING:</b></span> Suspicious activity detected. Monitoring for escalation: ${alertStr}"
    } else {
        return "<span style='color:green;'><b>All Clear.</b></span> System is monitoring context and environmental data normally."
    }
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "clearAlertsBtn") {
        state.activeAlerts = []
        state.currentAlertLevel = "Normal"
        logAction("User manually cleared all active alerts.")
    }
}

// --- LIFE SAFETY EMERGENCY HANDLER ---

def lifeSafetyHandler(evt) {
    if (evt.value == "detected") {
        logContextEvent("LIFE SAFETY EMERGENCY: ${evt.name.toUpperCase()} at ${evt.device.displayName}")
        
        // 1. Execute Visual / Physical Escape Actions
        if (emergencySwitches) emergencySwitches.on()
        if (emergencyColoredLights) setEmergencyColor(emergencyColoredLights, emergencyLightColor ?: "Red")
        if (emergencyLocks) emergencyLocks.unlock()
        
        // 2. Execute Audio Evacuation Alerts
        def msg = emergencyTTSMessage ?: "Emergency. Smoke or Carbon Monoxide detected. Evacuate the house immediately."
        if (ttsDevices) ttsDevices.speak(msg)
        if (zoozSirens && emergencySirenFile) zoozSirens.playSound(emergencySirenFile)
        
        // 3. Trigger Critical Engine Alert
        triggerAlert("Critical", "Life Safety Alarm: ${evt.name.toUpperCase()} detected by ${evt.device.displayName}!")
    } else if (evt.value == "tested") {
        logContextEvent("Life Safety System Test initiated at ${evt.device.displayName}")
    }
}

def setEmergencyColor(devices, colorName) {
    def hueColor = 0
    def saturation = 100
    switch(colorName) {
        case "White": hueColor = 0; saturation = 0; break;
        case "Red": hueColor = 0; break;
        case "Yellow": hueColor = 16; break;
        case "Green": hueColor = 33; break;
        case "Blue": hueColor = 66; break;
    }
    devices.setColor([hue: hueColor, saturation: saturation, level: 100])
    logAction("Emergency RGB Lights set to ${colorName}")
}

// --- CONTEXT EVENT HANDLERS ---

def contactHandler(evt) {
    if (evt.value == "open") {
        state.lastDoorOpenTime = now()
        def isWindow = perimeterWindows?.find { it.id == evt.device.id } != null
        
        logContextEvent("${evt.device.displayName} Opened")
        
        if (state.currentAlertLevel == "Warning" && state.lastGlassBreakTime > (now() - 60000)) {
            // Glass broke, and now a door/window opened. ESCALATE to Critical.
            triggerAlert("Critical", "Glass break followed by perimeter breach at ${evt.device.displayName}!")
        } else if (isWindow) {
            triggerAlert("Warning", "${evt.device.displayName} was opened.")
        }
    } else {
        logContextEvent("${evt.device.displayName} Closed")
    }
}

def outdoorMotionHandler(evt) {
    if (evt.value == "active") {
        def graceMs = (outboundGracePeriod ?: 30) * 1000
        def timeSinceDoor = now() - (state.lastDoorOpenTime ?: 0)
        
        if (timeSinceDoor <= graceMs) {
            // Context Aware: A door was opened right before this outdoor motion. Likely a resident leaving.
            logContextEvent("Ignored ${evt.device.displayName} (Resident outbound logic)")
        } else {
            // No door opened recently. Unknown exterior motion.
            logContextEvent("Unknown motion at ${evt.device.displayName}")
            triggerAlert("Warning", "Motion detected at ${evt.device.displayName} without door opening.")
        }
    }
}

def indoorMotionHandler(evt) {
    if (evt.value == "active") {
        logContextEvent("Indoor motion at ${evt.device.displayName}")
        
        if (state.currentAlertLevel == "Warning" || state.lastGlassBreakTime > (now() - 120000)) {
            // We had a warning (like unknown exterior motion or glass break), and now interior motion is triggered.
            triggerAlert("Critical", "Intruder tracked to ${evt.device.displayName}!")
        }
    }
}

def glassBreakHandler(evt) {
    state.lastGlassBreakTime = now()
    logContextEvent("GLASS BREAK detected at ${evt.device.displayName}")
    triggerAlert("Warning", "Possible glass break detected at ${evt.device.displayName}.")
}

// --- ALERT EXECUTION ENGINE ---

def triggerAlert(level, message) {
    if (state.currentAlertLevel == "Critical" && level == "Warning") return // Don't downgrade an active critical alert
    
    state.currentAlertLevel = level
    if (!state.activeAlerts.contains(message)) {
        state.activeAlerts.add(message)
    }
    
    logAction("ALERT TRIGGERED [${level}]: ${message}")
    
    if (pushDevices) pushDevices.deviceNotification("Security ${level}: ${message}")
    
    // Only execute these standard alert audio if it's not already handled by a life-safety override
    if (!message.contains("Life Safety Alarm")) {
        if (ttsDevices) {
            ttsDevices.speak("Attention. Security system reports a ${level} event. ${message}")
        }
        
        if (zoozSirens) {
            def soundNum = level == "Critical" ? (criticalSoundNumber ?: 2) : (warningSoundNumber ?: 1)
            zoozSirens.playSound(soundNum)
        }
    }
}

def logContextEvent(msg) {
    def timestamp = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    def logMsg = "[${timestamp}] ${msg}"
    
    def h = state.eventHistory ?: []
    h.add(0, logMsg)
    if (h.size() > 15) h = h[0..14]
    state.eventHistory = h
}

def logAction(msg) { log.info "${app.label}: ${msg}" }
def logInfo(msg) { log.info "${app.label}: ${msg}" }
