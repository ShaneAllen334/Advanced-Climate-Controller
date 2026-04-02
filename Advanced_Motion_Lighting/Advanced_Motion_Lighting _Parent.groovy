/**
 * Advanced Motion Lighting (Parent)
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Motion Lighting",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Parent container for Advanced Motion Lighting child applications.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Motion Lighting", install: true, uninstall: true) {
        
        if (pauseSystem) {
            paragraph "<div style='background-color: #f8d7da; color: #721c24; padding: 10px; border: 1px solid #f5c6cb; border-radius: 5px; font-weight: bold; font-size: 16px; text-align: center;'>⚠️ GLOBAL SYSTEM PAUSED ⚠️<br>All lighting rules are currently disabled.</div>"
        }

        section("Global System Dashboard") {
            input "btnRefresh", "button", title: "🔄 Refresh Dashboard Data Now"
            
            def children = getChildApps()
            if (children) {
                def tableHTML = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                tableHTML += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone Name</th><th style='padding: 8px;'>Lights</th><th style='padding: 8px;'>Action / Last Trigger</th><th style='padding: 8px;'>Time Left</th></tr>"
                
                def healthData = []
                def renderedCount = 0
                
                // Variables for ROI Table
                def roiRenderedCount = 0
                def totalToday = 0.0
                def totalLifetime = 0.0
                def hasROI = false
                
                def roiHTML = "<div style='margin-top:20px; font-weight:bold; font-size:14px;'>Global Energy Savings & ROI</div>"
                roiHTML += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                roiHTML += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone Name</th><th style='padding: 8px;'>Today's Savings</th><th style='padding: 8px;'>Lifetime Savings</th></tr>"

                children.each { child ->
                    try {
                        def z = child.getZoneStatus()
                        if (z) {
                            def lightColor = z.light.contains("ON") ? "green" : "grey"
                            def rowBg = (renderedCount % 2 == 0) ? "#ffffff" : "#f9f9f9"
                            if (z.health) healthData.addAll(z.health)

                            tableHTML += "<tr style='border-bottom: 1px solid #ddd; background-color: ${rowBg};'>"
                            tableHTML += "<td style='padding: 8px; font-weight: bold;'>${z.name}</td>"
                            tableHTML += "<td style='padding: 8px; color: ${lightColor}; font-weight: bold;'>${z.light}</td>"
                            
                            def triggerText = z.lastTrigger ? "<br><span style='font-size: 10px; color: #666;'>Triggered by: ${z.lastTrigger}</span>" : ""
                            tableHTML += "<td style='padding: 8px;'>${z.status}${triggerText}</td>"
                            tableHTML += "<td style='padding: 8px;'>${z.timer}</td>"
                            tableHTML += "</tr>"
                            renderedCount++
                            
                            // Process ROI Data
                            if (z.roi) {
                                hasROI = true
                                def roiRowBg = (roiRenderedCount % 2 == 0) ? "#ffffff" : "#f9f9f9"
                                roiHTML += "<tr style='border-bottom: 1px solid #ddd; background-color: ${roiRowBg};'>"
                                roiHTML += "<td style='padding: 8px;'>${z.name}</td>"
                                roiHTML += "<td style='padding: 8px;'>&#36;${z.roi.today}</td>"
                                roiHTML += "<td style='padding: 8px;'>&#36;${z.roi.lifetime}</td>"
                                roiHTML += "</tr>"
                                
                                totalToday += z.roi.today.toBigDecimal()
                                totalLifetime += z.roi.lifetime.toBigDecimal()
                                roiRenderedCount++
                            }
                        }
                    } catch (e) { log.debug "Dashboard error: ${e.message}" }
                }
                tableHTML += "</table>"
                paragraph tableHTML
                
                if (enableGlobalHealth && healthData) {
                    def hTable = "<div style='margin-top:20px; font-weight:bold; font-size:14px;'>Sensor Health & Battery Watchdog</div>"
                    hTable += "<table style='width:100%; border-collapse: collapse; font-size: 12px; font-family: sans-serif; background-color: #fff; border: 1px solid #ccc;'>"
                    hTable += "<tr style='background-color: #f2dede; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 5px;'>Device Name</th><th style='padding: 5px;'>Battery</th><th style='padding: 5px;'>Last Activity</th></tr>"
                    healthData.unique{ it.name }.each { h ->
                        def bColor = (h.battery && h.battery.toInteger() < 25) ? "red" : "black"
                        hTable += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 5px;'>${h.name}</td><td style='padding: 5px; color: ${bColor}; font-weight: bold;'>${h.battery ?: '--'}%</td><td style='padding: 5px;'>${h.lastActivity}</td></tr>"
                    }
                    hTable += "</table>"
                    paragraph hTable
                }
                
                if (hasROI) {
                    roiHTML += "<tr style='background-color: #dff0d8; border-top: 2px solid #ccc; font-weight: bold;'>"
                    roiHTML += "<td style='padding: 8px; text-align: right;'>SYSTEM TOTALS:</td>"
                    roiHTML += "<td style='padding: 8px; color: green;'>&#36;${totalToday.setScale(2, BigDecimal.ROUND_HALF_UP)}</td>"
                    roiHTML += "<td style='padding: 8px; color: green;'>&#36;${totalLifetime.setScale(2, BigDecimal.ROUND_HALF_UP)}</td>"
                    roiHTML += "</tr>"
                    roiHTML += "</table>"
                    paragraph roiHTML
                }
                
            } else { paragraph "<i>No lighting zones created yet.</i>" }
        }
        
        section("Master System Control") {
            input "pauseSystem", "bool", title: "<b>⏸️ Pause System-Wide (All Rules)?</b>", defaultValue: false, submitOnChange: true
            input "masterEnableSwitch", "capability.switch", title: "Master Disable Switch", required: false
            input "enableGlobalHealth", "bool", title: "Enable Global Battery & Health Watchdog?", defaultValue: false, submitOnChange: true
        }
        
        section("Global Color Temperature") {
            input "globalCTVar", "string", title: "Global CT Hub Variable Name (Exact text)", required: false
        }
        
        section("Arrival Lighting Strategy") {
            input "arrivalMode", "mode", title: "Trigger Mode (e.g., Arrival/Home)", multiple: false, required: false
            input "arrivalShadesSensor", "capability.contactSensor", title: "All Shades Contact Sensor", required: false
            input "arrivalOvercastSwitch", "capability.switch", title: "Overcast Virtual Switch", required: false
            input "arrivalTimeout", "number", title: "Shade Open Timeout (Seconds)", defaultValue: 30
            input "arrivalDuration", "number", title: "Keep Arrival Lights On Duration (Minutes)", defaultValue: 10
            input "staggerDelay", "number", title: "Stagger Delay Between Zones (ms)", defaultValue: 500
        }
       
        section("System Maintenance & Recovery") {
            input "btnGlobalSweep", "button", title: "Execute Global Sweep Now"
            input "btnClearOverrides", "button", title: "Clear All Manual Overrides"
            input "btnResetROI", "button", title: "Reset All ROI Telemetry"
        }
        
        section("Lighting Rules") {
            app(name: "childApps", appName: "Advanced Motion Lighting Child", namespace: "ShaneAllen", title: "Create New Motion Lighting Rule", multiple: true)
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    if (arrivalMode) subscribe(location, "mode", modeChangeHandler)
    if (arrivalShadesSensor) subscribe(arrivalShadesSensor, "contact", shadesContactHandler)
    if (globalCTVar) subscribe(location, "variable.${globalCTVar}", globalCTHandler)
}

def isSystemPaused() {
    return pauseSystem == true
}

def globalCTHandler(evt) {
    if (isSystemPaused()) return
    try {
        def newCT = Math.round(evt.value.toFloat()).toInteger()
        getChildApps().each { it.dynamicCTUpdate(newCT) }
    } catch (ex) { log.error "CT Var Error: ${ex.message}" }
}

def modeChangeHandler(evt) {
    if (isSystemPaused()) return
    if (evt.value == arrivalMode) {
        state.arrivalPending = true
        runIn(arrivalTimeout ?: 30, "arrivalTimeoutCheck")
    }
}

def shadesContactHandler(evt) {
    if (isSystemPaused()) return
    if (state.arrivalPending && evt.value == "open") {
        unschedule("arrivalTimeoutCheck")
        state.arrivalPending = false
        if (arrivalOvercastSwitch?.currentValue("switch") == "on") triggerArrivalLights()
    }
}

def arrivalTimeoutCheck() {
    if (isSystemPaused()) return
    if (state.arrivalPending) {
        state.arrivalPending = false
        def shadeState = arrivalShadesSensor?.currentValue("contact")
        if (shadeState == "closed" || (shadeState == "open" && arrivalOvercastSwitch?.currentValue("switch") == "on")) triggerArrivalLights()
    }
}

def triggerArrivalLights() {
    if (isSystemPaused()) return
    def children = getChildApps()
    children.each { child ->
        if (child.isArrivalEnabled()) {
            child.turnOnArrival()
            pauseExecution(staggerDelay ?: 500)
        }
    }
    runIn((arrivalDuration ?: 10) * 60, "revertArrivalLights")
}

def revertArrivalLights() { 
    if (isSystemPaused()) return
    getChildApps().each { if (it.isArrivalEnabled()) it.revertFromArrival() } 
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        // Do nothing, framework refreshes naturally
    } else if (btn == "btnGlobalSweep") {
        getChildApps().each { child -> 
            try {
                child.executeParentSweep((new Random().nextInt(4500) + 500).toLong())
            } catch (e) {
                log.error "Failed to sweep ${child.label}: ${e.message}"
            }
        }
    } else if (btn == "btnClearOverrides") {
        getChildApps().each { child -> 
            try {
                child.clearManualOverride()
            } catch (e) {
                log.error "Failed to clear override on ${child.label}: ${e.message}"
            }
        }
    } else if (btn == "btnResetROI") {
        getChildApps().each { child -> 
            try {
                child.resetROI()
            } catch (e) {
                log.error "Failed to reset ROI on ${child.label}: ${e.message}"
            }
        }
    }
}
