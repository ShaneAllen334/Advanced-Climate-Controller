/**
 * Advanced Shower Monitor
 */
definition(
    name: "Advanced Shower Monitor",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Monitors up to 4 showers with Volumetric Tracking, Financial Cost Analytics, and Grace-Period Smoothing.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Shower Monitor Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Shower</th><th style='padding: 8px;'>Current Status</th></tr>"
            
            def showerCount = settings["numShowers"] ?: 1
            for (int i = 1; i <= showerCount; i++) {
                def sName = settings["showerName_${i}"] ?: "Shower ${i}"
                def mSensor = settings["motion_${i}"]
                
                if (!mSensor) {
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${sName}</b></td><td style='padding: 8px; color: #888;'>Not Configured</td></tr>"
                    continue
                }
                
                def sStatus = state["showerStatus_${i}"] ?: "Idle"
                def statusColor = (sStatus == "Idle") ? "black" : (sStatus == "Grace Period" ? "orange" : "green")
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${sName}</b></td><td style='padding: 8px; color: ${statusColor}; font-weight: bold;'>${sStatus}</td></tr>"
            }
            statusText += "</table>"
            
            def globalStatus = isSystemPaused() ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : 
                               (!isModeAllowed() ? "<span style='color: orange; font-weight: bold;'>PAUSED (Restricted Mode)</span>" : 
                               "<span style='color: green; font-weight: bold;'>ACTIVE</span>")
            
            statusText += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>Global System Mode:</b> ${globalStatus}</div>"

            paragraph statusText
        }

        section("Financial Analytics (Last 10 Sessions)") {
            def showerCount = settings["numShowers"] ?: 1
            for (int i = 1; i <= showerCount; i++) {
                def sName = settings["showerName_${i}"] ?: "Shower ${i}"
                def logList = state["sessionLog_${i}"] ?: []
                
                if (logList.size() > 0) {
                    def logText = "<div style='margin-bottom: 15px;'><b>${sName}</b><br><table style='width:100%; font-size: 13px; border-collapse: collapse; border: 1px solid #ccc;'>"
                    logText += "<tr style='background-color: #eee; border-bottom: 1px solid #ccc; text-align: left;'><th style='padding: 6px;'>Date & Time</th><th style='padding: 6px;'>Duration</th><th style='padding: 6px;'>Volume</th><th style='padding: 6px;'>Est. Cost</th></tr>"
                    
                    logList.each { entry ->
                        def galStr = entry.gallons ?: "-- gal"
                        def costStr = entry.cost ?: "--"
                        logText += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 6px;'>${entry.time}</td><td style='padding: 6px;'><b>${entry.duration}</b></td><td style='padding: 6px; color: #0066cc;'>${galStr}</td><td style='padding: 6px; color: #008800;'><b>${costStr}</b></td></tr>"
                    }
                    logText += "</table></div>"
                    paragraph logText
                } else {
                    paragraph "<b>${sName}:</b> <i>No shower data recorded yet.</i>"
                }
            }
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Global Settings") {
            input "numShowers", "number", title: "Number of Showers to Monitor (1-4)", defaultValue: 1, range: "1..4", required: true, submitOnChange: true
            
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false,
                description: "ON = Application Runs. OFF = Application Paused."
            
            input "activeModes", "mode", title: "Active Modes (App only runs in these)", multiple: true, required: false
        }
        
        def count = settings["numShowers"] ?: 1
        for (int i = 1; i <= count; i++) {
            section("Shower ${i} Setup") {
                input "showerName_${i}", "text", title: "Custom Name", required: false, defaultValue: "Shower ${i}"
                input "motion_${i}", "capability.motionSensor", title: "Shower Motion Sensor", required: false
                input "light_${i}", "capability.switch", title: "Bathroom Light to Flash", required: false
                
                input "flowRate_${i}", "decimal", title: "Showerhead Flow Rate (GPM)", defaultValue: 2.5, required: true,
                    description: "Standard US showerheads are 2.5 GPM. Low-flow is usually 1.5 to 2.0."
                
                input "costPerGallon_${i}", "decimal", title: "Est. Cost per Gallon of Hot Water (\$)", defaultValue: 0.03, required: true,
                    description: "Includes water, sewer, and the energy required to heat it. The US average is roughly \$0.02 to \$0.04."
                
                input "warn1_${i}", "number", title: "1st Warning (Minutes)", defaultValue: 5, required: true
                input "warn2_${i}", "number", title: "2nd Warning (Minutes)", defaultValue: 8, required: true
                input "warn3_${i}", "number", title: "3rd Warning (Minutes)", defaultValue: 10, required: true
                
                input "gracePeriod_${i}", "number", title: "Empty Shower Grace Period (Minutes)", defaultValue: 2, required: true,
                    description: "Fixes 'chatty' sensors by waiting before resetting timers. This time is automatically subtracted from the final duration."
            }
        }
    }
}

def installed() {
    log.info "Shower Monitor Installed."
    initialize()
}

def updated() {
    log.info "Shower Monitor Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    def showerCount = settings["numShowers"] ?: 1
    
    for (int i = 1; i <= showerCount; i++) {
        if (settings["motion_${i}"]) subscribe(settings["motion_${i}"], "motion", "motionHandler${i}")
        state["showerActive_${i}"] = state["showerActive_${i}"] ?: false
        state["showerStatus_${i}"] = state["showerStatus_${i}"] ?: "Idle"
        state["sessionLog_${i}"] = state["sessionLog_${i}"] ?: []
    }
}

// --- UTILITY: HISTORY LOGGER ---
def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

// --- DYNAMIC HANDLERS ---
def motionHandler1(evt) { handleMotion(1, evt.value) }
def motionHandler2(evt) { handleMotion(2, evt.value) }
def motionHandler3(evt) { handleMotion(3, evt.value) }
def motionHandler4(evt) { handleMotion(4, evt.value) }

def warnTierOne1() { triggerFlash(1, 1) }
def warnTierTwo1() { triggerFlash(1, 2) }
def warnTierThree1() { triggerFlash(1, 3) }
def endShower1() { terminateShower(1) }

def warnTierOne2() { triggerFlash(2, 1) }
def warnTierTwo2() { triggerFlash(2, 2) }
def warnTierThree2() { triggerFlash(2, 3) }
def endShower2() { terminateShower(2) }

def warnTierOne3() { triggerFlash(3, 1) }
def warnTierTwo3() { triggerFlash(3, 2) }
def warnTierThree3() { triggerFlash(3, 3) }
def endShower3() { terminateShower(3) }

def warnTierOne4() { triggerFlash(4, 1) }
def warnTierTwo4() { triggerFlash(4, 2) }
def warnTierThree4() { triggerFlash(4, 3) }
def endShower4() { terminateShower(4) }

// --- UTILITY: SYSTEM CHECKS ---
def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def isModeAllowed() {
    if (!activeModes) return true
    return activeModes.contains(location.mode)
}

// --- CORE LOGIC ---
def handleMotion(showerId, motionState) {
    def sName = settings["showerName_${showerId}"] ?: "Shower ${showerId}"
    def grace = settings["gracePeriod_${showerId}"] ?: 2
    
    if (motionState == "active") {
        unschedule("endShower${showerId}")
        
        if (!state["showerActive_${showerId}"]) {
            if (isSystemPaused() || !isModeAllowed()) return
            
            state["showerActive_${showerId}"] = true
            state["showerStartTime_${showerId}"] = new Date().time
            state["showerStatus_${showerId}"] = "Active (Timers Running)"
            addToHistory("${sName}: Shower started. Countdowns armed.")
            
            runIn((settings["warn1_${showerId}"] ?: 5) * 60, "warnTierOne${showerId}", [overwrite: true])
            runIn((settings["warn2_${showerId}"] ?: 8) * 60, "warnTierTwo${showerId}", [overwrite: true])
            runIn((settings["warn3_${showerId}"] ?: 10) * 60, "warnTierThree${showerId}", [overwrite: true])
        } else if (state["showerStatus_${showerId}"] == "Grace Period") {
            state["showerStatus_${showerId}"] = "Active (Timers Running)"
        }
    } else if (state["showerActive_${showerId}"]) {
        state["showerStatus_${showerId}"] = "Grace Period"
        addToHistory("${sName}: Motion stopped. Waiting ${grace}m grace period.")
        runIn(grace * 60, "endShower${showerId}", [overwrite: true])
    }
}

def terminateShower(showerId) {
    def sName = settings["showerName_${showerId}"] ?: "Shower ${showerId}"
    
    // --- ANALYTICS, VOLUMETRIC & FINANCIAL CALCULATOR ---
    def startTime = state["showerStartTime_${showerId}"] ?: new Date().time
    def endTime = new Date().time
    def graceSecs = (settings["gracePeriod_${showerId}"] ?: 2) * 60
    
    // 1. Calculate Duration
    def totalMillis = endTime - startTime - (graceSecs * 1000)
    if (totalMillis < 0) totalMillis = 0 
    
    def totalSecs = (totalMillis / 1000) as Integer
    def mins = (totalSecs / 60) as Integer
    def secs = totalSecs % 60
    def durationStr = "${mins}m ${secs}s"
    
    // 2. Calculate Volume
    def gpm = settings["flowRate_${showerId}"]?.toBigDecimal() ?: 2.5
    def gallonsUsed = (totalSecs / 60.0) * gpm
    def gallonsStr = "${gallonsUsed.setScale(1, BigDecimal.ROUND_HALF_UP)} gal"
    
    // 3. Calculate Cost
    def costFactor = settings["costPerGallon_${showerId}"]?.toBigDecimal() ?: 0.03
    def totalCost = gallonsUsed * costFactor
    def costStr = "\$" + totalCost.setScale(2, BigDecimal.ROUND_HALF_UP)
    
    def startTimeFormatted = new Date(startTime).format("MM/dd hh:mm a", location.timeZone)
    
    // Log it to the Analytics Table
    def entry = [time: startTimeFormatted, duration: durationStr, gallons: gallonsStr, cost: costStr]
    def logs = state["sessionLog_${showerId}"] ?: []
    logs.add(0, entry)
    if (logs.size() > 10) logs = logs.take(10)
    state["sessionLog_${showerId}"] = logs
    // ----------------------------------------

    addToHistory("${sName}: Empty. Timers reset. Duration: ${durationStr} | ${gallonsStr} | ${costStr}")
    
    state["showerActive_${showerId}"] = false
    state["showerStatus_${showerId}"] = "Idle"
    unschedule("warnTierOne${showerId}")
    unschedule("warnTierTwo${showerId}")
    unschedule("warnTierThree${showerId}")
}

def triggerFlash(showerId, flashes) {
    if (!state["showerActive_${showerId}"] || isSystemPaused() || !isModeAllowed()) return
    def light = settings["light_${showerId}"]
    if (!light) return

    addToHistory("${settings["showerName_${showerId}"]} limit reached. Flashing ${flashes}x.")
    def delayMultiplier = 0
    for (int i = 0; i < flashes; i++) {
        runInMillis(delayMultiplier * 2000, "turnLightOff", [data: [showerId: showerId], overwrite: false])
        runInMillis((delayMultiplier * 2000) + 1000, "turnLightOn", [data: [showerId: showerId], overwrite: false])
        delayMultiplier++
    }
}

def turnLightOff(data) { settings["light_${data.showerId}"]?.off() }
def turnLightOn(data) { settings["light_${data.showerId}"]?.on() }
