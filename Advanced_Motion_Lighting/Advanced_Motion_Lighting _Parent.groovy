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
        
        section("Global System Dashboard") {
            def children = getChildApps()
            if (children) {
                def tableHTML = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                tableHTML += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone Name</th><th style='padding: 8px;'>Lights</th><th style='padding: 8px;'>Sensors</th><th style='padding: 8px;'>Current Action</th><th style='padding: 8px;'>Time Left</th></tr>"
                
                def renderedCount = 0
                
                children.each { child ->
                    try {
                        def z = child.getZoneStatus()
                        if (z) {
                            def lightColor = z.light == "ON" ? "green" : "grey"
                            def motionColor = z.motion == "ACTIVE" ? "blue" : (z.motion == "KEEP-ALIVE" ? "#00aadd" : "grey")
                            def rowBg = (renderedCount % 2 == 0) ? "#ffffff" : "#f9f9f9"
                            
                            tableHTML += "<tr style='border-bottom: 1px solid #ddd; background-color: ${rowBg};'>"
                            tableHTML += "<td style='padding: 8px; font-weight: bold;'>${z.name}</td>"
                            tableHTML += "<td style='padding: 8px; color: ${lightColor}; font-weight: bold;'>${z.light}</td>"
                            tableHTML += "<td style='padding: 8px; color: ${motionColor}; font-weight: bold;'>${z.motion}</td>"
                            tableHTML += "<td style='padding: 8px;'>${z.status}</td>"
                            tableHTML += "<td style='padding: 8px;'>${z.timer}</td>"
                            tableHTML += "</tr>"
                            
                            renderedCount++
                        }
                    } catch (e) {
                        log.debug "Skipping dashboard render for ${child.label} (Likely needs code update or initialization)"
                    }
                }
                tableHTML += "</table>"
                
                if (renderedCount > 0) {
                    paragraph tableHTML
                } else {
                    paragraph "<i>Please open and save your child apps to update them to the latest code version to view the dashboard.</i>"
                }
            } else {
                paragraph "<i>No lighting zones created yet.</i>"
            }
        }
        
        section("Master System Control") {
            input "masterEnableSwitch", "capability.switch", title: "Master Disable Switch", required: false, description: "Turn ON to pause all motion lighting globally."
        }
        
        section("Arrival Lighting Strategy") {
            paragraph "Triggers a staggered turn-on of selected child zones when arriving. Reverts to standard motion logic after the set duration."
            input "arrivalMode", "mode", title: "Trigger Mode (e.g., Arrival/Home)", multiple: false, required: false
            input "arrivalShadesSensor", "capability.contactSensor", title: "All Shades Contact Sensor", required: false
            input "arrivalOvercastSwitch", "capability.switch", title: "Overcast Virtual Switch", required: false
            input "arrivalTimeout", "number", title: "Shade Open Timeout (Seconds)", defaultValue: 30
            input "arrivalDuration", "number", title: "Keep Arrival Lights On Duration (Minutes)", defaultValue: 10
            input "staggerDelay", "number", title: "Stagger Delay Between Zones (ms)", defaultValue: 500
        }
        
        section("System Maintenance & Recovery") {
            paragraph "If lights are stuck ON due to missed events from older app versions or sensor mesh delays, use this to force every inactive room to turn off."
            input "btnGlobalSweep", "button", title: "Execute Global Sweep Now"
            
            paragraph "Clear any rooms currently locked ON due to a human physically turning on the switch."
            input "btnClearOverrides", "button", title: "Clear All Manual Overrides"
        }
        
        section("Lighting Rules") {
            app(name: "childApps", appName: "Advanced Motion Lighting Child", namespace: "ShaneAllen", title: "Create New Motion Lighting Rule", multiple: true)
        }
        
        section("Information") {
            paragraph "<b>Advanced Motion Lighting</b><br>A predictive and condition-based lighting engine featuring overcast overrides, history telemetry, and mode-based dimming."
        }
    }
}

def installed() {
    log.info "Advanced Motion Lighting (Parent) Installed."
    initialize()
}

def updated() {
    log.info "Advanced Motion Lighting (Parent) Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (arrivalMode) {
        subscribe(location, "mode", modeChangeHandler)
    }
    if (arrivalShadesSensor) {
        subscribe(arrivalShadesSensor, "contact", shadesContactHandler)
    }
}

// --- ARRIVAL LOGIC ---

def modeChangeHandler(evt) {
    if (evt.value == arrivalMode) {
        log.info "ADVANCED MOTION LIGHTING: ${arrivalMode} mode activated. Waiting up to ${arrivalTimeout ?: 30}s for shades to report."
        state.arrivalPending = true
        runIn(arrivalTimeout ?: 30, "arrivalTimeoutCheck")
    }
}

def shadesContactHandler(evt) {
    // If the shades open while we are waiting in the 30-second arrival window
    if (state.arrivalPending && evt.value == "open") {
        log.info "ADVANCED MOTION LIGHTING: Shades opened within arrival window."
        unschedule("arrivalTimeoutCheck")
        state.arrivalPending = false
        
        if (arrivalOvercastSwitch?.currentValue("switch") == "on") {
            log.info "ADVANCED MOTION LIGHTING: Overcast is active. Triggering Arrival Lights."
            triggerArrivalLights()
        } else {
            log.info "ADVANCED MOTION LIGHTING: Not overcast. Aborting Arrival Lights."
        }
    }
}

def arrivalTimeoutCheck() {
    if (state.arrivalPending) {
        state.arrivalPending = false
        def shadeState = arrivalShadesSensor?.currentValue("contact")
        
        if (shadeState == "closed") {
            log.info "ADVANCED MOTION LIGHTING: Shades did not open within timeout. Triggering Arrival Lights."
            triggerArrivalLights()
        } else if (shadeState == "open" && arrivalOvercastSwitch?.currentValue("switch") == "on") {
            log.info "ADVANCED MOTION LIGHTING: Shades open but overcast. Triggering Arrival Lights."
            triggerArrivalLights()
        }
    }
}

def triggerArrivalLights() {
    def children = getChildApps()
    def count = 0
    
    children.each { child ->
        if (child.isArrivalEnabled()) {
            child.turnOnArrival()
            count++
            pauseExecution(staggerDelay ?: 500) // Stagger to protect Zigbee/Z-Wave mesh
        }
    }
    
    if (count > 0) {
        log.info "ADVANCED MOTION LIGHTING: Arrival lighting executed on ${count} zones. Scheduled revert in ${arrivalDuration ?: 10} minutes."
        runIn((arrivalDuration ?: 10) * 60, "revertArrivalLights")
    }
}

def revertArrivalLights() {
    log.info "ADVANCED MOTION LIGHTING: Arrival duration elapsed. Reverting zones based on current room activity."
    def children = getChildApps()
    children.each { child ->
        if (child.isArrivalEnabled()) {
            child.revertFromArrival()
        }
    }
}

// --- BUTTON/SWEEP LOGIC ---

def appButtonHandler(btn) {
    if (btn == "btnGlobalSweep") {
        log.info "ADVANCED MOTION LIGHTING: Global Sweep triggered by user."
        def children = getChildApps()
        def sweptCount = 0
        
        children.each { child ->
            try {
                // Generate a random stagger between 500ms and 5000ms for each zone
                def randomStagger = new Random().nextInt(4500) + 500
                child.executeParentSweep(randomStagger)
                sweptCount++
            } catch (e) {
                log.error "Failed to sweep child app ${child.label}: ${e.message}"
            }
        }
        log.info "ADVANCED MOTION LIGHTING: Global Sweep command successfully sent to ${sweptCount} lighting rules."
        
    } else if (btn == "btnClearOverrides") {
        log.info "ADVANCED MOTION LIGHTING: Clear Manual Overrides triggered by user."
        def children = getChildApps()
        def clearedCount = 0
        
        children.each { child ->
            try {
                if (child.clearManualOverride()) {
                    clearedCount++
                }
            } catch (e) {
                log.error "Failed to clear override for child app ${child.label}: ${e.message}"
            }
        }
        log.info "ADVANCED MOTION LIGHTING: Manual overrides cleared on ${clearedCount} active zones."
    }
}
