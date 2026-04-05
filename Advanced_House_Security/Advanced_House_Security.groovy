/**
 * Advanced House Security
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced House Security",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Defensive peace-of-mind engine for family safety. Granular Zooz routing, Curfew Zones, sensor debounce, left-open reminders, and smart arrival announcements.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    // Failsafe to convert old list-based alerts to the new Map structure upon page load
    if (state.activeAlerts instanceof java.util.List) {
        state.activeAlerts = [:]
    }

    dynamicPage(name: "mainPage", title: "<b>Advanced House Security (Family Protection)</b>", install: true, uninstall: true) {
     
        section("<b>Master Control & Live Dashboard</b>") {
            input "masterEnableSwitch", "capability.switch", title: "<b>Master System Switch</b> (If OFF, the entire security app is bypassed)", required: false, submitOnChange: true
            
            paragraph "<hr>"
            input "btnRefresh", "button", title: "🔄 Refresh Data"
           
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> An 'Eye in the Sky' real-time view of your home's entire defensive perimeter, active alerts, and expected engine responses.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            def alertColor = state.currentAlertLevel == "Critical" ? "#d9534f" : (state.currentAlertLevel == "Warning" ? "#f0ad4e" : "#007bff")
            def sysEnabled = !(masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
      
            if (!sysEnabled) alertColor = "#dc3545" 
            
            def currentMode = location.mode
            def bypassStatus = (state.bypassEndTime && now() < state.bypassEndTime) ? "<span style='color:#d39e00; font-weight:bold;'>ACTIVE (${Math.round((state.bypassEndTime - now())/60000)} mins left)</span>" : "Inactive"

            // Global Status Header
            def dashHTML = """
            <div style='background-color:#e9ecef; padding:12px; border-radius:5px; border-left:5px solid ${alertColor}; margin-bottom: 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);'>
                <div style='font-size: 15px; margin-bottom: 6px;'><b>System Status:</b> ${statusExplanation}</div>
                <div style='font-size: 14px;'><b>Current Mode:</b> <span style='color:#007bff; font-weight:bold;'>${currentMode}</span> &nbsp;|&nbsp; <b>Manual Bypass:</b> ${bypassStatus}</div>
            </div>
            """

            // ACTIVE ALERTS TABLE
            if (state.activeAlerts && state.activeAlerts.size() > 0) {
                def alertHtml = """
                <style>
                    .alert-table { width: 100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; margin-bottom: 15px;}
                    .alert-table th { background-color: ${alertColor}; color: white; padding: 10px; text-align: left; }
                    .alert-table td { border-bottom: 1px solid #ddd; padding: 8px 10px; background-color: #fff; }
                    .alert-table tr:hover td { background-color: #f8d7da; }
                    .alert-badge { background:#343a40; color:white; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; }
                </style>
                <div style='border: 1px solid ${alertColor}; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow-x: auto;'>
                <table class="alert-table">
                    <thead>
                        <tr>
                            <th>First Detected</th>
                            <th>Last Detected</th>
                            <th>Source Device</th>
                            <th>Event Details</th>
                            <th>Count</th>
                            <th>Duration</th>
                        </tr>
                    </thead>
                    <tbody>
                """
                state.activeAlerts.each { key, alert ->
                    alertHtml += "<tr>"
                    alertHtml += "<td><span style='font-size:11px; color:#555;'>${alert.time}</span></td>"
                    alertHtml += "<td><span style='font-size:11px; color:#111; font-weight:bold;'>${alert.lastTime}</span></td>"
                    alertHtml += "<td><b>${alert.device}</b></td>"
                    alertHtml += "<td>${alert.msg}</td>"
                    alertHtml += "<td><span class='alert-badge'>${alert.count}</span></td>"
                    alertHtml += "<td><span style='color:#d9534f; font-weight:bold;'>${alert.duration}</span></td>"
                    alertHtml += "</tr>"
                }
                alertHtml += "</tbody></table></div>"
                
                dashHTML += alertHtml
            }

            // EYE IN THE SKY MATRIX
            dashHTML += """
            <style>
                .eye-wrapper { max-height: 550px; overflow-y: auto; border: 1px solid #ccc; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .eye-table { width: 100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; }
                .eye-table th { background-color: #343a40; color: white; padding: 10px; text-align: left; position: sticky; top: 0; z-index: 10; border-bottom: 2px solid #23272b; }
                .eye-table td { border-bottom: 1px solid #ddd; padding: 8px 10px; background-color: #fff; }
                .eye-table tr:hover td { background-color: #f8f9fa; }
                .state-bad { color: #d9534f; font-weight: bold; }
                .state-good { color: #28a745; font-weight: bold; }
                .zone-badge { display: inline-block; background: #e2e3e5; color: #383d41; padding: 3px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; border: 1px solid #d6d8db; }
            </style>
            
            <div class='eye-wrapper'>
            <table class="eye-table">
                <thead>
                    <tr>
                        <th>Sensor Name</th>
                        <th>Assigned Zone</th>
                        <th>Position / State</th>
                        <th>Engine Status</th>
                        <th>Expected Engine Response</th>
                    </tr>
                </thead>
                <tbody>
            """
            
            def processedIds = []
            def buildRows = { devList, zoneName, stateAttr, badState, expectedAction, isZoneActive ->
                if (devList) {
                    devList.each { dev ->
                        if (!processedIds.contains(dev.id)) {
                            def currentState = dev.currentValue(stateAttr)
                            def stateClass = (currentState == badState) ? "state-bad" : "state-good"
                            
                            def finalStatus = ""
                            def isBypassed = (state.bypassEndTime && now() < state.bypassEndTime && bypassDoors?.find { it.id == dev.id })
      
                            if (!sysEnabled) finalStatus = "<span style='color:#dc3545; font-size:11px; font-weight:bold;'>🔴 DISABLED</span>"
                            else if (isBypassed) finalStatus = "<span style='color:#d39e00; font-size:11px; font-weight:bold;'>🟡 BYPASSED</span>"
                            else if (isZoneActive) finalStatus = "<span style='color:#28a745; font-size:11px; font-weight:bold;'>🟢 ARMED</span>"
                            else finalStatus = "<span style='color:#6c757d; font-size:11px; font-weight:bold;'>⚪ SLEEPING</span>"
                            
                            dashHTML += "<tr>"
                            dashHTML += "<td><b>${dev.displayName}</b></td>"
                            dashHTML += "<td><span class='zone-badge'>${zoneName}</span></td>"
                            dashHTML += "<td class='${stateClass}'>${currentState?.toString()?.toUpperCase() ?: 'UNKNOWN'}</td>"
                            dashHTML += "<td>${finalStatus}</td>"
                            dashHTML += "<td><span style='font-size:12px; color:#555;'>${expectedAction}</span></td>"
                            dashHTML += "</tr>"
                            
                            processedIds << dev.id
                        }
                    }
                }
            }
            
            def hrActive = isAdvancedConditionMet(enableHighRiskRules1, highRiskModes1, highRiskStartTimeType1, highRiskStartTime1, highRiskStartOffset1, highRiskEndTimeType1, highRiskEndTime1, highRiskEndOffset1, enableHighRiskRules2, highRiskModes2, highRiskStartTimeType2, highRiskStartTime2, highRiskStartOffset2, highRiskEndTimeType2, highRiskEndTime2, highRiskEndOffset2)
            def curfActive = isAdvancedConditionMet(enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1, enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2)
            def doorActive = isAdvancedConditionMet(enableDoorRules1, doorModes1, doorStartTimeType1, doorStartTime1, doorStartOffset1, doorEndTimeType1, doorEndTime1, doorEndOffset1, enableDoorRules2, doorModes2, doorStartTimeType2, doorStartTime2, doorStartOffset2, doorEndTimeType2, doorEndTime2, doorEndOffset2)
            def leftActive = isAdvancedConditionMet(enableLeftOpenRules1, leftOpenModes1, leftOpenStartTimeType1, leftOpenStartTime1, leftOpenStartOffset1, leftOpenEndTimeType1, leftOpenEndTime1, leftOpenEndOffset1, enableLeftOpenRules2, leftOpenModes2, leftOpenStartTimeType2, leftOpenStartTime2, leftOpenStartOffset2, leftOpenEndTimeType2, leftOpenEndTime2, leftOpenEndOffset2)
            def outActive = isAdvancedConditionMet(enableOutMotionRules1, outMotionModes1, outStartTimeType1, outMotionStartTime1, outStartOffset1, outEndTimeType1, outMotionEndTime1, outEndOffset1, enableOutMotionRules2, outMotionModes2, outStartTimeType2, outMotionStartTime2, outStartOffset2, outEndTimeType2, outMotionEndTime2, outEndOffset2, overcastSwitch)
            def inActive = isAdvancedConditionMet(enableInMotionRules1, inMotionModes1, inStartTimeType1, inMotionStartTime1, inStartOffset1, inEndTimeType1, inMotionEndTime1, inEndOffset1, enableInMotionRules2, inMotionModes2, inStartTimeType2, inMotionStartTime2, inStartOffset2, inEndTimeType2, inMotionEndTime2, inEndOffset2)

            buildRows(highRiskSensors, "High-Risk", "contact", "open", "⚠️ Triggers Warning/TTS if rules met", hrActive)
            buildRows(curfewDoors, "Curfew", "contact", "open", "🚨 Triggers Critical Alarm if rules met", curfActive)
            buildRows(perimeterDoors, "Perimeter Door", "contact", "open", "🚪 Custom TTS/Chime + Push", doorActive)
            buildRows(perimeterWindows, "Perimeter Window", "contact", "open", "🪟 Logs Event / Perimeter Breach", true) 
            buildRows(leftOpenSensors, "Left-Open Watch", "contact", "open", "⏱️ Delayed TTS Reminder (${leftOpenDelay ?: 15}m)", leftActive)
            buildRows(outdoorMotion, "Outdoor Motion", "motion", "active", "🔊 Motion TTS / Tracks Outbound", outActive)
            buildRows(indoorMotion, "Indoor Motion", "motion", "active", "🏃 Motion TTS / Tracks Intrusion", inActive)
            buildRows(glassBreakSensors, "Glass Break", "sound.detected", "detected", "💥 Critical Escalation if breached", true) 
            
            def allDetectors = (smokeDetectors ?: []) + (coDetectors ?: [])
            allDetectors = allDetectors.unique { it.id } 
            
            if (allDetectors.size() > 0) {
                allDetectors.each { dev ->
                    if (!processedIds.contains(dev.id)) {
                         def sState = dev.hasAttribute("smoke") ? dev.currentValue("smoke") : "unknown"
                        def cState = dev.hasAttribute("carbonMonoxide") ? dev.currentValue("carbonMonoxide") : "unknown"
                        def batt = dev.hasAttribute("battery") ? dev.currentValue("battery") : "--"
                        
                        def statusColor = (sState == "detected" || cState == "detected") ? "state-bad" : "state-good"
                        def displayStatus = "CLEAR"
                        if (sState == "detected" && cState == "detected") displayStatus = "SMOKE & CO DETECTED"
                        else if (sState == "detected") displayStatus = "SMOKE DETECTED"
                        else if (cState == "detected") displayStatus = "CO DETECTED"
                        else if (sState == "tested" || cState == "tested") displayStatus = "TESTING"
                        
                        def lifeStatus = !sysEnabled ? "<span style='color:#dc3545; font-size:11px; font-weight:bold;'>🔴 DISABLED</span>" : "<span style='color:#28a745; font-size:11px; font-weight:bold;'>🟢 24/7 ARMED</span>"

                        dashHTML += "<tr>"
                        dashHTML += "<td><b>${dev.displayName}</b> <span style='font-size:10px; color:gray;'>(Batt: ${batt}%)</span></td>"
                        dashHTML += "<td><span class='zone-badge' style='background:#f8d7da; color:#721c24; border-color:#f5c6cb;'>Life Safety</span></td>"
                        dashHTML += "<td class='${statusColor}'>${displayStatus}</td>"
                        dashHTML += "<td>${lifeStatus}</td>"
                        dashHTML += "<td><span style='font-size:12px; color:#555;'>🔥 Evac Protocol / Overdrive Volume</span></td>"
                        dashHTML += "</tr>"
                        
                        processedIds << dev.id
                    }
                }
            }

            dashHTML += "</tbody></table></div>"
            paragraph dashHTML
            
            if (state.activeAlerts && state.activeAlerts.size() > 0) {
                input "clearAlertsBtn", "button", title: "🧹 Dismiss & Clear Active Alerts"
            }
        }

        section("<b>📋 Recent Context Engine Events (Last 25)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> A rolling timeline of house activity. Crucial for verifying when kids got home or identifying the source of an alert.</div>"
            if (state.eventHistory) {
                def historyStr = state.eventHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No recent events tracked.</i>"
            }
        }

        section("<b>1. 🚫 High-Risk Boundaries (Kids & Hazards)</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Dedicated protection for sensitive areas (Pool gates, gun safes).</div>"
            
            input "enableHighRiskRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableHighRiskRules1) {
                input "highRiskModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "highRiskStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskStartTimeType1 in ["Sunrise", "Sunset"]) input "highRiskStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskStartTime1", "time", title: "Specific Start Time", required: false
                
                input "highRiskEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskEndTimeType1 in ["Sunrise", "Sunset"]) input "highRiskEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableHighRiskRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableHighRiskRules2) {
                input "highRiskModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "highRiskStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskStartTimeType2 in ["Sunrise", "Sunset"]) input "highRiskStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskStartTime2", "time", title: "Specific Start Time", required: false
                
                input "highRiskEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskEndTimeType2 in ["Sunrise", "Sunset"]) input "highRiskEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "highRiskSensors", "capability.contactSensor", title: "Select High-Risk Contact Sensors", required: false, multiple: true, submitOnChange: true
            if (highRiskSensors) {
                highRiskSensors.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "highRiskMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Warning. ${dev.displayName} has been opened."
                    input "highRiskTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "highRiskPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "highRiskZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["highRiskZooz_${dev.id}"]) {
                        settings["highRiskZooz_${dev.id}"].each { zDev -> 
                            input "highRiskZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_highRisk_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>2. 🌙 Toddler / Wander Prevention (Curfew Zones)</b>", hideable: true) {
            input "enableCurfewRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableCurfewRules1) {
                input "curfewModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "curfewStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewStartTimeType1 in ["Sunrise", "Sunset"]) input "curfewStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewStartTime1", "time", title: "Specific Start Time", required: false
                
                input "curfewEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewEndTimeType1 in ["Sunrise", "Sunset"]) input "curfewEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableCurfewRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableCurfewRules2) {
                input "curfewModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "curfewStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewStartTimeType2 in ["Sunrise", "Sunset"]) input "curfewStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewStartTime2", "time", title: "Specific Start Time", required: false
                
                input "curfewEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewEndTimeType2 in ["Sunrise", "Sunset"]) input "curfewEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "curfewDoors", "capability.contactSensor", title: "Select Curfew Doors", required: false, multiple: true, submitOnChange: true
            if (curfewDoors) {
                curfewDoors.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "curfewMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Emergency. ${dev.displayName} was opened during curfew."
                    input "curfewTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "curfewPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "curfewZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["curfewZooz_${dev.id}"]) {
                        settings["curfewZooz_${dev.id}"].each { zDev -> 
                            input "curfewZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_curfew_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>3. 🚪 Perimeter Setup (Doors, Windows, Glass)</b>", hideable: true) {
            input "enableDoorRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableDoorRules1) {
                input "doorModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "doorStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorStartTimeType1 in ["Sunrise", "Sunset"]) input "doorStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorStartTime1", "time", title: "Specific Start Time", required: false
                
                input "doorEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorEndTimeType1 in ["Sunrise", "Sunset"]) input "doorEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableDoorRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableDoorRules2) {
                input "doorModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "doorStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorStartTimeType2 in ["Sunrise", "Sunset"]) input "doorStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorStartTime2", "time", title: "Specific Start Time", required: false
                
                input "doorEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorEndTimeType2 in ["Sunrise", "Sunset"]) input "doorEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "perimeterDoors", "capability.contactSensor", title: "Select Exterior Doors", required: false, multiple: true, submitOnChange: true
            if (perimeterDoors) {
                perimeterDoors.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "doorMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "${dev.displayName} opened."
                    input "doorTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "doorPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "doorZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["doorZooz_${dev.id}"]) {
                        settings["doorZooz_${dev.id}"].each { zDev -> 
                            input "doorZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_door_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
            
            paragraph "<hr>"
            paragraph "<b>Mode Change Reminders</b>"
            input "perimeterCheckModes", "mode", title: "Modes that trigger an 'Open' warning", multiple: true, required: false
            input "perimeterCheckPush", "capability.notification", title: "Who gets this reminder?", multiple: true, required: false
            input "perimeterCheckTTS", "bool", title: "Announce on global TTS?", defaultValue: true
            
            paragraph "<hr>"
            input "perimeterWindows", "capability.contactSensor", title: "Select Perimeter Windows", required: false, multiple: true
            input "glassBreakSensors", "capability.sensor", title: "Select Glass Break Sensors", required: false, multiple: true
            input "outboundGracePeriod", "number", title: "Outbound Grace Period (Seconds)", required: false, defaultValue: 30
        }

        section("<b>4. ⏰ Proactive 'Left Open' Reminders</b>", hideable: true) {
            input "enableLeftOpenRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableLeftOpenRules1) {
                input "leftOpenModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "leftOpenStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenStartTimeType1 in ["Sunrise", "Sunset"]) input "leftOpenStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenStartTime1", "time", title: "Specific Start Time", required: false
                
                input "leftOpenEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenEndTimeType1 in ["Sunrise", "Sunset"]) input "leftOpenEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableLeftOpenRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableLeftOpenRules2) {
                input "leftOpenModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "leftOpenStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenStartTimeType2 in ["Sunrise", "Sunset"]) input "leftOpenStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenStartTime2", "time", title: "Specific Start Time", required: false
                
                input "leftOpenEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenEndTimeType2 in ["Sunrise", "Sunset"]) input "leftOpenEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "leftOpenDelay", "number", title: "Minutes to wait before reminding", required: false, defaultValue: 15
            input "leftOpenSensors", "capability.contactSensor", title: "Select Doors/Windows to Monitor", required: false, multiple: true, submitOnChange: true
            if (leftOpenSensors) {
                leftOpenSensors.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "leftOpenMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Reminder, the ${dev.displayName} is left open."
                    input "leftOpenTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "leftOpenPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "leftOpenZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["leftOpenZooz_${dev.id}"]) {
                        settings["leftOpenZooz_${dev.id}"].each { zDev -> 
                            input "leftOpenZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_leftOpen_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>5. 🏃 Interior & Exterior Motion</b>", hideable: true) {
            paragraph "<b>Outdoor Motion Config</b>"
            input "overcastSwitch", "capability.switch", title: "Overcast / Weather Override Switch (Triggers alerts regardless of time constraints)", required: false

            input "enableOutMotionRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableOutMotionRules1) {
                input "outMotionModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "outStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outStartTimeType1 in ["Sunrise", "Sunset"]) input "outStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionStartTime1", "time", title: "Specific Start Time", required: false

                input "outEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outEndTimeType1 in ["Sunrise", "Sunset"]) input "outEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableOutMotionRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableOutMotionRules2) {
                input "outMotionModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "outStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outStartTimeType2 in ["Sunrise", "Sunset"]) input "outStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionStartTime2", "time", title: "Specific Start Time", required: false

                input "outEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outEndTimeType2 in ["Sunrise", "Sunset"]) input "outEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "outdoorMotion", "capability.motionSensor", title: "Select Outdoor Motion Sensors", required: false, multiple: true, submitOnChange: true
            if (outdoorMotion) {
                outdoorMotion.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "outMotionMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Motion detected at the ${dev.displayName}."
                    input "outMotionTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "outMotionPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "outMotionZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["outMotionZooz_${dev.id}"]) {
                        settings["outMotionZooz_${dev.id}"].each { zDev -> 
                            input "outMotionZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_outMotion_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
            
            paragraph "<hr>"
            paragraph "<b>Indoor Motion Config</b>"
            input "enableInMotionRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableInMotionRules1) {
                input "inMotionModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "inStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inStartTimeType1 in ["Sunrise", "Sunset"]) input "inStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionStartTime1", "time", title: "Specific Start Time", required: false

                input "inEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inEndTimeType1 in ["Sunrise", "Sunset"]) input "inEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableInMotionRules2", "bool", title: "<b>Enable Rule Set 2</b>", defaultValue: false, submitOnChange: true
            if (enableInMotionRules2) {
                input "inMotionModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "inStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inStartTimeType2 in ["Sunrise", "Sunset"]) input "inStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionStartTime2", "time", title: "Specific Start Time", required: false

                input "inEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inEndTimeType2 in ["Sunrise", "Sunset"]) input "inEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "indoorMotion", "capability.motionSensor", title: "Select Indoor Motion Sensors", required: false, multiple: true, submitOnChange: true
            if (indoorMotion) {
                indoorMotion.each { dev ->
                    paragraph "<div style='font-size:12px; color:#1a73e8; font-weight:bold; margin-top:5px;'>${dev.displayName} Granular Settings</div>"
                    input "inMotionMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Motion detected in the ${dev.displayName}."
                    input "inMotionTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "inMotionPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "inMotionZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["inMotionZooz_${dev.id}"]) {
                        settings["inMotionZooz_${dev.id}"].each { zDev -> 
                            input "inMotionZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_inMotion_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>6. 📍 Individual Presence & Safe Transit (Up to 4 People)</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Configure granular arrival and departure rules for up to 4 individuals. Customize TTS, push, and Zooz Chimes specifically for who arrived or departed.</div>"
            
            for (int i = 1; i <= 4; i++) {
                input "enablePerson${i}", "bool", title: "<b>Enable Person / Slot ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["enablePerson${i}"]) {
                    input "personSensor${i}", "capability.presenceSensor", title: "Select Presence Sensor for Person ${i}", required: true
                    
                    paragraph "<div style='background-color:#e9ecef; padding:5px; border-radius:3px;'><b>Arrival Settings (Present)</b></div>"
                    input "arrivalModes${i}", "mode", title: "Active Modes for Arrival Event", multiple: true, required: false
                    input "arrStartTimeType${i}", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["arrStartTimeType${i}"] in ["Sunrise", "Sunset"]) input "arrStartOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "arrStartTime${i}", "time", title: "Specific Start Time", required: false
                    input "arrEndTimeType${i}", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["arrEndTimeType${i}"] in ["Sunrise", "Sunset"]) input "arrEndOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "arrEndTime${i}", "time", title: "Specific End Time", required: false
                    
                    input "arrivalMsg${i}", "text", title: "Custom Arrival Message", required: false, defaultValue: "Welcome home."
                    input "arrivalTTS${i}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "arrivalPush${i}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "arrivalZooz${i}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["arrivalZooz${i}"]) {
                        settings["arrivalZooz${i}"].each { zDev -> 
                            input "arrivalZoozFile${i}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_arrival_${i}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }

                    paragraph "<div style='background-color:#e9ecef; padding:5px; border-radius:3px;'><b>Departure Settings (Not Present)</b></div>"
                    input "departureModes${i}", "mode", title: "Active Modes for Departure Event", multiple: true, required: false
                    input "depStartTimeType${i}", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["depStartTimeType${i}"] in ["Sunrise", "Sunset"]) input "depStartOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "depStartTime${i}", "time", title: "Specific Start Time", required: false
                    input "depEndTimeType${i}", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["depEndTimeType${i}"] in ["Sunrise", "Sunset"]) input "depEndOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "depEndTime${i}", "time", title: "Specific End Time", required: false
                    
                    input "departureMsg${i}", "text", title: "Custom Departure Message", required: false, defaultValue: "Safe travels."
                    input "departureTTS${i}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "departurePush${i}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "departureZooz${i}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["departureZooz${i}"]) {
                        settings["departureZooz${i}"].each { zDev -> 
                            input "departureZoozFile${i}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_departure_${i}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                    paragraph "<hr style='border: 1px solid #1a73e8;'>"
                }
            }
        }

        section("<b>7. 🚨 Life Safety & Panic Response</b>", hideable: true) {
            input "smokeDetectors", "capability.smokeDetector", title: "Select Smoke Detectors", required: false, multiple: true
            input "coDetectors", "capability.carbonMonoxideDetector", title: "Select CO Detectors", required: false, multiple: true
            input "panicButtons", "capability.pushableButton", title: "Select Panic / Duress Buttons", required: false, multiple: true
            
            paragraph "<b>Emergency Evacuation Automations</b>"
            input "emergencySwitches", "capability.switch", title: "Turn ON Standard Lights/Switches", required: false, multiple: true
            input "emergencyColoredLights", "capability.colorControl", title: "Turn ON & Change Color of RGB Lights", required: false, multiple: true
            if (settings.emergencyColoredLights) {
                input "emergencyLightColor", "enum", title: "Select RGB Emergency Color", options: ["Red", "White", "Blue", "Green", "Yellow"], required: false, defaultValue: "Red"
            }
            input "emergencyLocks", "capability.lock", title: "Unlock Doors", required: false, multiple: true
            
            paragraph "<b>Emergency Audio Override</b>"
            input "emergencyTTSMessage", "text", title: "Custom TTS Evacuation Message", required: false, defaultValue: "Emergency. Evacuation protocol initiated. Please exit the house immediately."
            input "emergencyTTS", "capability.speechSynthesis", title: "► Target Global TTS Devices for Emergencies", required: false, multiple: true
            
            input "emergencyZooz", "capability.chime", title: "► Target Zooz Devices for Critical Sirens", multiple: true, submitOnChange: true
            if (emergencyZooz) {
                emergencyZooz.each { zDev -> 
                    input "emergencyZoozFile_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                    input "testBtn_emergency_0_${zDev.id}", "button", title: "🔊 Test Speaker"
                }
            }
        }

        section("<b>8. 🔊 Dynamic Audio Routing & Debounce</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Controls notification fatigue and sets smart volume scaling based on time of day.</div>"
            
            paragraph "<b>Dynamic TTS Volume Scaling</b>"
            input "dayVolume", "number", title: "Standard Day Volume (0-100)", required: false, defaultValue: 70
            input "quietVolume", "number", title: "Whisper/Quiet Volume (0-100)", required: false, defaultValue: 20
            input "emergencyVolume", "number", title: "Emergency Overdrive Volume (0-100)", required: false, defaultValue: 100
            
            paragraph "<i>When should the system use Whisper/Quiet Volume?</i>"
            input "quietVolumeModes", "mode", title: "Quiet Volume Modes", multiple: true, required: false
            
            input "quietStartTimeType", "enum", title: "Quiet Volume Start", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.quietStartTimeType in ["Sunrise", "Sunset"]) input "quietStartOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "quietVolumeStartTime", "time", title: "Specific Start Time", required: false
            
            input "quietEndTimeType", "enum", title: "Quiet Volume End", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.quietEndTimeType in ["Sunrise", "Sunset"]) input "quietEndOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "quietVolumeEndTime", "time", title: "Specific End Time", required: false

            paragraph "<hr>"
            paragraph "<b>Sensor Debounce (Spam Prevention)</b>"
            input "motionDebounce", "number", title: "Motion Cooldown (Minutes)", required: false, defaultValue: 5
            input "doorDebounce", "number", title: "Door/Window Cooldown (Minutes)", required: false, defaultValue: 1
            
            paragraph "<hr>"
            paragraph "<b>Quiet Hours (Granular Routing Rules)</b>"
            paragraph "<div style='font-size:13px; color:#555;'>Configure exactly what happens when the house is in a Quiet Mode. <i>(Life-Safety, Curfews, and High-Risk Boundaries will still override this and sound alarms).</i></div>"
            input "quietModes", "mode", title: "Select Quiet Modes (e.g., Night, Sleeping)", multiple: true, required: false
            
            paragraph "<i>1. Allowed Event Categories in Quiet Mode:</i>"
            input "qmAllowDoors", "bool", title: "Allow Perimeter Door Alerts", defaultValue: false
            input "qmAllowOutMotion", "bool", title: "Allow Outdoor Motion Alerts", defaultValue: false
            input "qmAllowInMotion", "bool", title: "Allow Indoor Motion Alerts", defaultValue: false
            input "qmAllowLeftOpen", "bool", title: "Allow Left-Open Reminders", defaultValue: false
            input "qmAllowPresence", "bool", title: "Allow Arrival/Departure Announcements", defaultValue: false

            paragraph "<i>2. Allowed Output Methods in Quiet Mode (for the events selected above):</i>"
            input "qmAllowPush", "bool", title: "Send Push Notifications", defaultValue: true
            input "qmAllowTTS", "bool", title: "Play TTS Announcements (Uses Whisper/Quiet Volume)", defaultValue: false
            input "qmAllowZooz", "bool", title: "Play Zooz Chimes", defaultValue: false
            
            paragraph "<hr>"
            input "enableCustomAnnouncements", "bool", title: "<b>Master Audio Toggle: Enable Day-Time Routine Announcements</b>", defaultValue: true
        }

        section("<b>9. 📲 Global Alert Notifications</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>These are for engine-level escalations (e.g., Glass Break + Motion tracking), separate from routine granular push events.</div>"
            input "pushCritical", "capability.notification", title: "Who receives Critical Alerts?", multiple: true, required: false
            input "pushWarnings", "capability.notification", title: "Who receives Security Warnings?", multiple: true, required: false
            input "warningModes", "mode", title: "Modes to ALLOW Warning notifications (Leave blank for 24/7)", multiple: true, required: false
          
            input "warnStartTimeType", "enum", title: "Warning Allowed Start", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.warnStartTimeType in ["Sunrise", "Sunset"]) input "warnStartOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "warningStartTime", "time", title: "Specific Start Time", required: false
            
            input "warnEndTimeType", "enum", title: "Warning Allowed End", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.warnEndTimeType in ["Sunrise", "Sunset"]) input "warnEndOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "warningEndTime", "time", title: "Specific End Time", required: false
            
            input "enableWatchdog", "bool", title: "Enable Daily Sensor Health Watchdog", defaultValue: true
            input "pushHealth", "capability.notification", title: "Who receives Maintenance alerts?", multiple: true, required: false
        }
        
        section("<b>10. 🐕 Quality of Life (Temporary Bypasses)</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Use a button push or virtual switch to temporarily mute warnings for specific doors (like letting the dog out at 2 AM) before automatically re-arming.</div>"
            input "bypassButton", "capability.pushableButton", title: "Bypass Button", required: false
            input "bypassButtonNum", "number", title: "Button Number to trigger bypass", required: false, defaultValue: 1
            input "bypassSwitch", "capability.switch", title: "Bypass Switch", required: false
            input "bypassSwitchAutoOff", "bool", title: "Automatically turn Bypass Switch back off?", defaultValue: true
            
            paragraph "<hr>"
            input "bypassDoors", "capability.contactSensor", title: "Which doors/sensors should be ignored?", required: false, multiple: true
            input "bypassDuration", "number", title: "Bypass Duration (Minutes)", required: false, defaultValue: 5
            input "bypassTTS", "capability.speechSynthesis", title: "Acknowledge Bypass via TTS?", required: false, multiple: true
        }

        section("<b>11. 📡 System Integration & Information Output</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Writes the real-time activity and context events to a Virtual Child Device so other Apps/Dashboards can easily read the system state.</div>"
            input "enableChildDevice", "bool", title: "<b>Create Information Child Device</b>", defaultValue: false, submitOnChange: true
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.eventHistory) state.eventHistory = []
    state.activeAlerts = [:] // Formatted as a Map for table deduplication
    
    if (!state.leftOpenAlertSent) state.leftOpenAlertSent = [:]
  
    state.currentAlertLevel = "Normal"
    state.lastDoorOpenTime = 0; state.lastGlassBreakTime = 0
    state.bypassEndTime = 0
    
    // Child Device Creation
    if (enableChildDevice) {
        def childId = "ahs_info_${app.id}"
        def child = getChildDevice(childId)
        if (!child) {
            try {
                addChildDevice("ShaneAllen", "Advanced House Security Information Device", childId, null, [name: "AHS Information", label: "AHS Information Output"])
                logAction("Created Information Child Device.")
            } catch (e) {
                log.error "Could not create child device. Please ensure the custom driver is installed. Error: ${e}"
            }
        }
    }

    subscribe(location, "mode", modeChangeHandler)
    if (masterEnableSwitch) subscribe(masterEnableSwitch, "switch", masterSwitchHandler)
    
    if (perimeterDoors) subscribe(perimeterDoors, "contact", contactHandler)
    if (perimeterWindows) subscribe(perimeterWindows, "contact", contactHandler)
    if (highRiskSensors) subscribe(highRiskSensors, "contact", highRiskHandler)
    if (curfewDoors) subscribe(curfewDoors, "contact", curfewHandler)
    if (leftOpenSensors) subscribe(leftOpenSensors, "contact", leftOpenHandler)
    
    if (outdoorMotion) {
        subscribe(outdoorMotion, "motion.active", outdoorMotionHandler)
        subscribe(outdoorMotion, "motion.inactive", motionInactiveHandler)
    }
    if (indoorMotion) {
        subscribe(indoorMotion, "motion.active", indoorMotionHandler)
        subscribe(indoorMotion, "motion.inactive", motionInactiveHandler)
    }
    
    // Subscribe to individual presence slots
    for (int i = 1; i <= 4; i++) {
        if (settings["enablePerson${i}"] && settings["personSensor${i}"]) {
            subscribe(settings["personSensor${i}"], "presence", individualPresenceHandler)
        }
    }
  
    if (glassBreakSensors) subscribe(glassBreakSensors, "sound.detected", glassBreakHandler) 
    if (smokeDetectors) subscribe(smokeDetectors, "smoke", lifeSafetyHandler)
    if (coDetectors) subscribe(coDetectors, "carbonMonoxide", lifeSafetyHandler)
    if (panicButtons) subscribe(panicButtons, "pushed", panicHandler)
    
    if (bypassButton) subscribe(bypassButton, "pushed", bypassHandler)
    if (bypassSwitch) subscribe(bypassSwitch, "switch.on", bypassHandler)
   
    if (enableWatchdog) schedule("0 0 12 * * ?", dailyHealthCheck)
    
    logAction("Advanced House Security Initialized.")
}

def isSystemEnabled() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

// Child Device Output Helper
def updateChildDevice(msg) {
    if (enableChildDevice) {
        def child = getChildDevice("ahs_info_${app.id}")
        if (child) {
            child.sendEvent(name: "variable", value: msg, descriptionText: "AHS Context Update")
        }
    }
}

// Advanced Sunrise/Sunset helper
def resolveTime(type, specificTime, offset) {
    if (type == "Sunrise" || type == "Sunset") {
        def sun = getSunriseAndSunset()
        if (!sun) return null
        def baseTime = (type == "Sunrise") ? sun.sunrise.time : sun.sunset.time
        return new Date(baseTime + ((offset ?: 0) * 60000))
    } else {
        return specificTime ? timeToday(specificTime, location.timeZone) : null
    }
}

// Master condition evaluation for all dual-rule sections
def isAdvancedConditionMet(en1, modes1, stType1, sTime1, sOff1, etType1, eTime1, eOff1, en2, modes2, stType2, sTime2, sOff2, etType2, eTime2, eOff2, overrideSwitch = null) {
    if (overrideSwitch && overrideSwitch.currentValue("switch") == "on") return true
    if (!en1 && !en2) return false

    def match1 = false
    if (en1) {
        def modeOk = !modes1 || (modes1 as List).contains(location.mode)
        def timeOk = true
        def s = resolveTime(stType1, sTime1, sOff1)
        def e = resolveTime(etType1, eTime1, eOff1)
        if (s && e) {
            def n = new Date()
            timeOk = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        match1 = modeOk && timeOk
    }

    def match2 = false
    if (en2) {
        def modeOk = !modes2 || (modes2 as List).contains(location.mode)
        def timeOk = true
        def s = resolveTime(stType2, sTime2, sOff2)
        def e = resolveTime(etType2, eTime2, eOff2)
        if (s && e) {
            def n = new Date()
            timeOk = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        match2 = modeOk && timeOk
    }

    return match1 || match2
}

// --- DYNAMIC VOLUME HELPER ---
def applyTTSVolume(devices, isCritical=false) {
    if (!devices) return
    def targetVol = settings.dayVolume ?: 70
    
    if (isCritical) {
        targetVol = settings.emergencyVolume ?: 100
    } else {
        def isQuietMode = settings.quietVolumeModes && (settings.quietVolumeModes as List).contains(location.mode)
        def isQuietTime = false
        def s = resolveTime(settings.quietStartTimeType, settings.quietVolumeStartTime, settings.quietStartOffset)
        def e = resolveTime(settings.quietEndTimeType, settings.quietVolumeEndTime, settings.quietEndOffset)
        
        if (s && e) {
            def n = new Date()
            isQuietTime = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        if (isQuietMode || isQuietTime) targetVol = settings.quietVolume ?: 20
    }

    [devices].flatten().findAll{it}.each { dev ->
        if (dev.hasCommand("setVolume")) dev.setVolume(targetVol as Integer)
    }
}

// --- MANUAL BYPASS ENGINE ---
def bypassHandler(evt) {
    if (!isSystemEnabled()) return
    if (evt.name == "pushed" && evt.value.toInteger() != (settings.bypassButtonNum ?: 1)) return
    
    def duration = settings.bypassDuration ?: 5
    state.bypassEndTime = now() + (duration * 60000)
    logContextEvent("System Bypass Activated for ${duration} minutes.")
    
    if (settings.bypassTTS) {
        applyTTSVolume(settings.bypassTTS, false)
        settings.bypassTTS.speak("Security bypass activated for ${duration} minutes.")
    }
    
    if (evt.name == "switch" && settings.bypassSwitchAutoOff) {
        runIn(2, "turnOffBypassSwitch")
    }
}

def turnOffBypassSwitch() {
    if (bypassSwitch) bypassSwitch.off()
}

def masterSwitchHandler(evt) {
    if (evt.value == "off") {
        logAction("MASTER SWITCH OFF: Entire security system disabled.")
        state.currentAlertLevel = "Normal"; state.activeAlerts = [:]
    } else logAction("MASTER SWITCH ON: Security system armed and monitoring.")
}

String getHumanReadableStatus() {
    if (!isSystemEnabled()) return "<span style='color:#dc3545;'><b>SYSTEM DISABLED</b></span>"
    if (state.currentAlertLevel == "Normal") return "<span style='color:green;'><b>All Clear.</b></span>"
    
    def levelHtml = state.currentAlertLevel == "Critical" ? "<span style='color:#d9534f;'><b>CRITICAL ALERT</b></span>" : "<span style='color:#f0ad4e;'><b>WARNING</b></span>"
    return "${levelHtml}: Active security events detected. See Active Alerts table below."
}

def appButtonHandler(btn) {
    if (btn == "clearAlertsBtn") { 
        state.activeAlerts = [:]
        state.currentAlertLevel = "Normal"
        logAction("User manually cleared alerts.") 
    } else if (btn.startsWith("testBtn_")) {
        def parts = btn.split("_")
        if (parts.size() >= 4) {
            def prefix = parts[1]
            def devId = parts[2]
            def zDevId = parts[3]
            
            def zDevs
            def fileNum
            
            if (prefix == "emergency") {
                zDevs = settings["emergencyZooz"]
                fileNum = settings["emergencyZoozFile_${zDevId}"]
            } else if (prefix == "arrival" || prefix == "departure") {
                zDevs = settings["${prefix}Zooz${devId}"]
                fileNum = settings["${prefix}ZoozFile${devId}_${zDevId}"]
            } else {
                zDevs = settings["${prefix}Zooz_${devId}"]
                fileNum = settings["${prefix}ZoozFile_${devId}_${zDevId}"]
            }
            
            def zDev = [zDevs].flatten().findAll{it}.find { it.id == zDevId }
            if (zDev && fileNum) {
                playZoozSound(zDev, fileNum)
                logAction("Test Speaker: Played file ${fileNum} on ${zDev.displayName}")
            }
        }
    }
}

// --- TODDLER / WANDER PREVENTION (CURFEW ZONES) ---

def curfewHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        def conditionMet = isAdvancedConditionMet(
            enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1,
            enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2
        )
        
        if (conditionMet) {
            logContextEvent("CURFEW BREACH: ${evt.device.displayName} Opened")
            def msg = settings["curfewMsg_${evt.device.id}"] ?: "Emergency. ${evt.device.displayName} was opened during curfew hours."
            def tts = settings["curfewTTS_${evt.device.id}"]
            def push = settings["curfewPush_${evt.device.id}"]
            
            if (tts) { applyTTSVolume(tts, true); tts.speak(msg) }
            if (push) push.deviceNotification(msg)
            playGranularZooz("curfewZooz", evt.device)
            
            triggerAlert("Critical", "Curfew Zone Breached: ${evt.device.displayName} opened during restricted hours!", evt.device.id, evt.device.displayName)
        }
    }
}

// --- PROACTIVE LEFT OPEN REMINDERS ---

def leftOpenHandler(evt) {
    if (!isSystemEnabled()) return
    
    def id = evt.device.id
    if (evt.value == "open") {
        def delay = leftOpenDelay ?: 15
        state.leftOpenAlertSent[id] = false
        runIn(delay * 60, "executeLeftOpenAlert", [data: [devId: id], overwrite: false])
    } else {
        state.leftOpenAlertSent[id] = false
    }
}

def executeLeftOpenAlert(data) {
    def dev = leftOpenSensors?.find { it.id == data.devId }
    if (dev && dev.currentValue("contact") == "open" && !state.leftOpenAlertSent[dev.id]) {
        def conditionMet = isAdvancedConditionMet(
            enableLeftOpenRules1, leftOpenModes1, leftOpenStartTimeType1, leftOpenStartTime1, leftOpenStartOffset1, leftOpenEndTimeType1, leftOpenEndTime1, leftOpenEndOffset1,
            enableLeftOpenRules2, leftOpenModes2, leftOpenStartTimeType2, leftOpenStartTime2, leftOpenStartOffset2, leftOpenEndTimeType2, leftOpenEndTime2, leftOpenEndOffset2
        )
        
        if (conditionMet) {
            def isQuiet = quietModes && (quietModes as List).contains(location.mode)
            if (isQuiet && !settings.qmAllowLeftOpen) return // Blocked by Quiet Mode settings
            
            def msg = settings["leftOpenMsg_${dev.id}"] ?: "Just a reminder, the ${dev.displayName} has been left open."
            logContextEvent("REMINDER: ${dev.displayName} left open too long.")
            def tts = settings["leftOpenTTS_${dev.id}"]
            def push = settings["leftOpenPush_${dev.id}"]
             
            if (tts && (!isQuiet || settings.qmAllowTTS)) { applyTTSVolume(tts, false); tts.speak(msg) }
            if (push && (!isQuiet || settings.qmAllowPush)) push.deviceNotification(msg)
            if (!isQuiet || settings.qmAllowZooz) playGranularZooz("leftOpenZooz", dev)
            
            state.leftOpenAlertSent[dev.id] = true
        }
    }
}

// --- PERIMETER MODE WATCHDOG ---

def modeChangeHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (perimeterCheckModes && (perimeterCheckModes as List).contains(evt.value)) {
        def openSensors = []
        if (perimeterDoors) openSensors += perimeterDoors.findAll { it.currentValue("contact") == "open" }.collect { it.displayName }
        if (perimeterWindows) openSensors += perimeterWindows.findAll { it.currentValue("contact") == "open" }.collect { it.displayName }
        
        if (openSensors.size() > 0) {
            def msg = "Warning. The house is now in ${evt.value} mode, but the following are still open: ${openSensors.join(', ')}."
            logAction("Perimeter check failed on mode change: ${msg}")
            if (perimeterCheckPush) perimeterCheckPush.deviceNotification(msg)
            if (perimeterCheckTTS && emergencyTTS) { applyTTSVolume(emergencyTTS, false); emergencyTTS.speak(msg) }
        }
    }
}

// --- SYSTEM HEALTH WATCHDOG ---

def dailyHealthCheck() {
    if (!isSystemEnabled()) return
    def lowBatteryDevices = []
    def allSensors = (perimeterDoors ?: []) + (perimeterWindows ?: []) + (highRiskSensors ?: []) + (curfewDoors ?: []) + (outdoorMotion ?: []) + (indoorMotion ?: []) + (smokeDetectors ?: []) + (coDetectors ?: []) + (glassBreakSensors ?: [])
    allSensors = allSensors.unique { it.id }
    
    allSensors.each { dev ->
        if (dev.hasAttribute("battery")) {
            def battVal = dev.currentValue("battery")
            if (battVal != null && battVal.toInteger() < 15) lowBatteryDevices.add("${dev.displayName} (${battVal}%)")
        }
    }
    
    if (lowBatteryDevices.size() > 0 && pushHealth) {
        pushHealth.deviceNotification("System Health Alert: Low batteries detected on: ${lowBatteryDevices.join(', ')}")
        logContextEvent("System Health: Low battery warning sent.")
    }
}

// --- GRANULAR ZOOZ + TTS/PUSH AUDIO ROUTING WITH DEBOUNCE ---

def playDeviceCustomAlert(device, type, conditionMet) {
    if (!settings.enableCustomAnnouncements) return
    if (!conditionMet) return
    
    def isQuiet = quietModes && (quietModes as List).contains(location.mode)
    
    // Evaluate if this specific event category is permitted during Quiet Mode
    if (isQuiet) {
        def allowed = false
        if (type == "door" && settings.qmAllowDoors) allowed = true
        if (type == "outMotion" && settings.qmAllowOutMotion) allowed = true
        if (type == "inMotion" && settings.qmAllowInMotion) allowed = true
        if (!allowed) return // Blocked entirely by Quiet Mode Rules
    }
    
    def mDebounce = motionDebounce != null ? motionDebounce : 5
    def dDebounce = doorDebounce != null ? doorDebounce : 1
    def cooldownMs = (type.contains("Motion")) ? (mDebounce * 60000) : (dDebounce * 60000)
    
    def lastPlayed = state."lastAlert_${device.id}" ?: 0
    if ((now() - lastPlayed) < cooldownMs) return 
    
    def played = false
    def prefix = type
    
    def msg = settings["${prefix}Msg_${device.id}"]
    def tts = settings["${prefix}TTS_${device.id}"]
    def push = settings["${prefix}Push_${device.id}"]
    
    // Evaluate if output methods are permitted
    if (msg) {
        if (tts && (!isQuiet || settings.qmAllowTTS)) { applyTTSVolume(tts, false); tts.speak(msg); played = true }
        if (push && (!isQuiet || settings.qmAllowPush)) { push.deviceNotification(msg); played = true }
    }
    
    if (!isQuiet || settings.qmAllowZooz) {
        if (playGranularZooz("${prefix}Zooz", device)) played = true
    }
    
    if (played) state."lastAlert_${device.id}" = now()
}

def playGranularZooz(settingPrefix, sourceDevice) {
    def played = false
    def zDevs = settings["${settingPrefix}_${sourceDevice.id}"]
    
    // Flatten ensures that even if only one item is selected (returning an Object instead of a List), it iterates safely
    [zDevs].flatten().findAll{it}.eachWithIndex { zDev, index ->
        if (index > 0) pauseExecution(1000)
        def fileNum = settings["${settingPrefix}File_${sourceDevice.id}_${zDev.id}"]
        if (fileNum) { 
            if (playZoozSound(zDev, fileNum)) played = true
        }
    }
    return played
}

// --- ROBUST ZOOZ PLAYBACK FIX ---
def playZoozSound(zDev, soundNum) {
    if (!zDev || soundNum == null) return false
    
    def played = false
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    try {
        if (zDev.hasCommand("playSound") && trackNum != null) {
            zDev.playSound(trackNum)
            played = true
        } else if (zDev.hasCommand("playTrack")) {
            zDev.playTrack(soundNum.toString())
            played = true
        } else if (zDev.hasCommand("chime") && trackNum != null) {
            zDev.chime(trackNum)
            played = true
        } else {
            log.error "${zDev.displayName} does not support standard audio/siren commands (playSound, playTrack, or chime)."
        }
    } catch (e) {
        log.error "Failed to play sound on Zooz device ${zDev.displayName}: ${e}"
    }
    return played
}

// --- SENSOR HANDLERS ---

def individualPresenceHandler(evt) {
    if (!isSystemEnabled()) return

    def devId = evt.device.id
    def isArrival = (evt.value == "present")
    def slot = null

    for (int i = 1; i <= 4; i++) {
        if (settings["enablePerson${i}"] && settings["personSensor${i}"]?.id == devId) {
             slot = i
            break
        }
    }

    if (!slot) return

    logContextEvent("${evt.device.displayName} ${isArrival ? 'arrived' : 'departed'}.")

    def modeSet = isArrival ? settings["arrivalModes${slot}"] : settings["departureModes${slot}"]
    def timeTypeStart = isArrival ? settings["arrStartTimeType${slot}"] : settings["depStartTimeType${slot}"]
    def timeStart = isArrival ? settings["arrStartTime${slot}"] : settings["depStartTime${slot}"]
    def timeOffStart = isArrival ? settings["arrStartOffset${slot}"] : settings["depStartOffset${slot}"]
    def timeTypeEnd = isArrival ? settings["arrEndTimeType${slot}"] : settings["depEndTimeType${slot}"]
    def timeEnd = isArrival ? settings["arrEndTime${slot}"] : settings["depEndTime${slot}"]
    def timeOffEnd = isArrival ? settings["arrEndOffset${slot}"] : settings["depEndOffset${slot}"]

    def conditionMet = isAdvancedConditionMet(
        true, modeSet,
        timeTypeStart, timeStart, timeOffStart,
        timeTypeEnd, timeEnd, timeOffEnd,
        false, null, null, null, null, null, null, null
    )

    if (conditionMet) {
        def isQuiet = quietModes && (quietModes as List).contains(location.mode)
        if (isQuiet && !settings.qmAllowPresence) return // Blocked by Quiet Mode settings
        
        def msg = isArrival ? settings["arrivalMsg${slot}"] : settings["departureMsg${slot}"]
        def tts = isArrival ? settings["arrivalTTS${slot}"] : settings["departureTTS${slot}"]
        def push = isArrival ? settings["arrivalPush${slot}"] : settings["departurePush${slot}"]
        def zooz = isArrival ? settings["arrivalZooz${slot}"] : settings["departureZooz${slot}"]

        if (msg) {
            if (tts && (!isQuiet || settings.qmAllowTTS)) {
                applyTTSVolume(tts, false)
                tts.speak(msg)
            }
            if (push && (!isQuiet || settings.qmAllowPush)) push.deviceNotification(msg)
        }

        if (!isQuiet || settings.qmAllowZooz) {
            [zooz].flatten().findAll{it}.eachWithIndex { zDev, index ->
                if (index > 0) pauseExecution(1000)
                def fileNum = isArrival ? settings["arrivalZoozFile${slot}_${zDev.id}"] : settings["departureZoozFile${slot}_${zDev.id}"]
                if (fileNum) playZoozSound(zDev, fileNum)
            }
        }
    }
}

def highRiskHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        logContextEvent("HIGH RISK: ${evt.device.displayName} Opened")
        
        def conditionMet = isAdvancedConditionMet(
            enableHighRiskRules1, highRiskModes1, highRiskStartTimeType1, highRiskStartTime1, highRiskStartOffset1, highRiskEndTimeType1, highRiskEndTime1, highRiskEndOffset1,
            enableHighRiskRules2, highRiskModes2, highRiskStartTimeType2, highRiskStartTime2, highRiskStartOffset2, highRiskEndTimeType2, highRiskEndTime2, highRiskEndOffset2
        )
        
        if (conditionMet) {
            def msg = settings["highRiskMsg_${evt.device.id}"] ?: "Warning. ${evt.device.displayName} has been opened."
            def tts = settings["highRiskTTS_${evt.device.id}"]
            def push = settings["highRiskPush_${evt.device.id}"]
            
            if (tts) { applyTTSVolume(tts, true); tts.speak(msg) }
            if (push) push.deviceNotification(msg)
            playGranularZooz("highRiskZooz", evt.device)
 
            triggerAlert("Warning", "High-Risk Boundary Breached: ${evt.device.displayName}", evt.device.id, evt.device.displayName)
        }
    }
}

def panicHandler(evt) {
    if (!isSystemEnabled()) return
    logContextEvent("PANIC BUTTON PUSHED: ${evt.device.displayName}")
    executeEmergencyProtocol("Panic button activated by ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
}

def lifeSafetyHandler(evt) {
    if (!isSystemEnabled()) return
    if (evt.value == "detected") {
        logContextEvent("LIFE SAFETY EMERGENCY: ${evt.name.toUpperCase()} at ${evt.device.displayName}")
        executeEmergencyProtocol("Life Safety Alarm: ${evt.name.toUpperCase()} detected by ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
    }
}

def executeEmergencyProtocol(alertReason, deviceId = "system", deviceName = "System Engine") {
    if (emergencySwitches) emergencySwitches.on()
    if (emergencyColoredLights) setEmergencyColor(emergencyColoredLights, emergencyLightColor ?: "Red")
    if (emergencyLocks) emergencyLocks.unlock()
    
    if (emergencyTTS) applyTTSVolume(emergencyTTS, true)
    
    def msg = emergencyTTSMessage ?: "Emergency. Evacuation protocol initiated. Please exit the house immediately."
    if (emergencyTTS) emergencyTTS.speak(msg)
    
    [emergencyZooz].flatten().findAll{it}.eachWithIndex { zDev, index ->
        if (index > 0) pauseExecution(1000)
        def fileNum = settings["emergencyZoozFile_${zDev.id}"]
        if (fileNum) playZoozSound(zDev, fileNum)
    }
    
    triggerAlert("Critical", alertReason, deviceId, deviceName)
}

def setEmergencyColor(devices, colorName) {
    def hueColor = 0; def saturation = 100
    switch(colorName) { case "White": hueColor=0; saturation=0; break; case "Red": hueColor=0; break; case "Yellow": hueColor=16; break; case "Green": hueColor=33; break; case "Blue": hueColor=66; break; }
    devices.setColor([hue: hueColor, saturation: saturation, level: 100])
}

def contactHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        state.lastDoorOpenTime = now()
        def isWindow = perimeterWindows?.find { it.id == evt.device.id } != null
        def isDoor = perimeterDoors?.find { it.id == evt.device.id } != null
  
        def isCurfewDoor = curfewDoors?.find { it.id == evt.device.id } != null
        def curfewMet = isCurfewDoor && isAdvancedConditionMet(
             enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1,
             enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2
        )
        if (curfewMet) return
        
        logContextEvent("${evt.device.displayName} Opened")
        
        if (isDoor) {
            def conditionMet = isAdvancedConditionMet(
                enableDoorRules1, doorModes1, doorStartTimeType1, doorStartTime1, doorStartOffset1, doorEndTimeType1, doorEndTime1, doorEndOffset1,
                enableDoorRules2, doorModes2, doorStartTimeType2, doorStartTime2, doorStartOffset2, doorEndTimeType2, doorEndTime2, doorEndOffset2
            )
            playDeviceCustomAlert(evt.device, "door", conditionMet)
        }
        
         if (state.currentAlertLevel == "Warning" && state.lastGlassBreakTime > (now() - 60000)) triggerAlert("Critical", "Glass break followed by perimeter breach at ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
         else if (isWindow) triggerAlert("Warning", "${evt.device.displayName} was opened.", evt.device.id, evt.device.displayName)
    } else {
        logContextEvent("${evt.device.displayName} Closed")
    }
}

// MOTION INACTIVE TRACKER FOR DURATION
def motionInactiveHandler(evt) {
    def start = state."motionStart_${evt.device.id}"
    if (start) {
        def durationSec = Math.round((now() - start) / 1000)
        logContextEvent("${evt.device.displayName} motion ended (Duration: ${durationSec}s)")
        updateAlertDuration(evt.device.id, durationSec)
        state.remove("motionStart_${evt.device.id}")
    }
}

def outdoorMotionHandler(evt) {
    if (!isSystemEnabled()) return
    state."motionStart_${evt.device.id}" = now()
    
    def conditionMet = isAdvancedConditionMet(
        enableOutMotionRules1, outMotionModes1, outStartTimeType1, outMotionStartTime1, outStartOffset1, outEndTimeType1, outMotionEndTime1, outEndOffset1,
        enableOutMotionRules2, outMotionModes2, outStartTimeType2, outMotionStartTime2, outStartOffset2, outEndTimeType2, outMotionEndTime2, outEndOffset2,
        overcastSwitch
    )
    
    if (conditionMet) {
        playDeviceCustomAlert(evt.device, "outMotion", true)
        
        def graceMs = (outboundGracePeriod ?: 30) * 1000
        if ((now() - (state.lastDoorOpenTime ?: 0)) <= graceMs) {
            logContextEvent("Ignored ${evt.device.displayName} (Resident outbound logic)")
        } else {
            logContextEvent("Unknown motion at ${evt.device.displayName}")
            triggerAlert("Warning", "Motion detected at ${evt.device.displayName} without door opening.", evt.device.id, evt.device.displayName)
            playGranularZooz("outMotionZooz", evt.device) // Force play to bypass debounce/quiet modes on a real warning
        }
    }
}

def indoorMotionHandler(evt) {
    if (!isSystemEnabled()) return
    state."motionStart_${evt.device.id}" = now()
    
    def conditionMet = isAdvancedConditionMet(
        enableInMotionRules1, inMotionModes1, inStartTimeType1, inMotionStartTime1, inStartOffset1, inEndTimeType1, inMotionEndTime1, inEndOffset1,
        enableInMotionRules2, inMotionModes2, inStartTimeType2, inMotionStartTime2, inStartOffset2, inEndTimeType2, inMotionEndTime2, inEndOffset2
    )
   
    if (conditionMet) {
        logContextEvent("Indoor motion at ${evt.device.displayName}")
        playDeviceCustomAlert(evt.device, "inMotion", true)
        
        if (state.currentAlertLevel == "Warning" || state.lastGlassBreakTime > (now() - 120000)) {
            triggerAlert("Critical", "Intruder tracked to ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
            playGranularZooz("inMotionZooz", evt.device) // Force play to bypass debounce/quiet modes on a real warning
        }
    }
}

def glassBreakHandler(evt) {
    if (!isSystemEnabled()) return
    state.lastGlassBreakTime = now()
    logContextEvent("GLASS BREAK detected at ${evt.device.displayName}")
    triggerAlert("Warning", "Possible glass break detected at ${evt.device.displayName}.", evt.device.id, evt.device.displayName)
}

def triggerAlert(level, message, deviceId = "system", deviceName = "System Engine") {
    if (state.currentAlertLevel == "Critical" && level == "Warning") return 
   
    state.currentAlertLevel = level
    
    // Core structural change from List to Map for deduplication
    def alertKey = "${deviceId}_${level}"
    def alerts = state.activeAlerts ?: [:]
    
    if (alerts[alertKey]) {
        alerts[alertKey].count = (alerts[alertKey].count ?: 1) + 1
        alerts[alertKey].lastTime = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    } else {
        alerts[alertKey] = [
            time: new Date().format("MM/dd hh:mm:ss a", location.timeZone),
            lastTime: new Date().format("MM/dd hh:mm:ss a", location.timeZone),
            device: deviceName,
            msg: message,
            level: level,
            count: 1,
            duration: "Ongoing"
        ]
    }
    state.activeAlerts = alerts
    
    logAction("ALERT TRIGGERED [${level}]: ${message}")
    
    if (level == "Critical") {
        if (pushCritical) pushCritical.deviceNotification("Security CRITICAL: ${message}")
    } else if (level == "Warning") {
        def n = new Date()
        def s = resolveTime(settings.warnStartTimeType, settings.warningStartTime, settings.warnStartOffset)
        def e = resolveTime(settings.warnEndTimeType, settings.warningEndTime, settings.warnEndOffset)
        
        def timeAllowed = (!s || !e) || (s < e ? (n >= s && n <= e) : (n >= s || n <= e))
        if (pushWarnings && (!warningModes || (warningModes as List).contains(location.mode)) && timeAllowed) pushWarnings.deviceNotification("Security Warning: ${message}")
    }
    
    // Write Alert to Child Device
    updateChildDevice("ALERT [${level}]: ${message}")
}

def updateAlertDuration(deviceId, durationSec) {
    def alerts = state.activeAlerts ?: [:]
    alerts.each { key, alert ->
        if (key.startsWith("${deviceId}_") && alert.duration == "Ongoing") {
            def mins = Math.floor(durationSec / 60).toInteger()
            def secs = durationSec % 60
            alert.duration = mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
        }
    }
    state.activeAlerts = alerts
}

def logContextEvent(msg) {
    def h = state.eventHistory ?: [];
    h.add(0, "[${new Date().format("MM/dd hh:mm:ss a", location.timeZone)}] ${msg}")
    if (h.size() > 25) h = h[0..24];
    state.eventHistory = h
    
    // Write Event to Child Device
    updateChildDevice(msg)
}

def logAction(msg) { log.info "${app.label}: ${msg}" }
def logInfo(msg) { log.info "${app.label}: ${msg}" }
