/**
 * Advanced Bathroom Dehumidifier Controller
 */
definition(
    name: "Advanced Bathroom Dehumidifier Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Intelligent climate control for up to 4 bathrooms with predictive tracking, cost analysis, and automated moisture management.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "bathroomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Dehumidifier Controls", install: true, uninstall: true) {
        
        section("Live Climate Dashboard") {
            // Refresh Button
            href(name: "refreshDash", title: "🔄 Refresh Data", description: "Tap to update live charts and timers", page: "mainPage")
            
            // TABLE 1: Current Status
            def statusText = "<b>Live Space Status</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone</th><th style='padding: 8px;'>Humidity<br><span style='font-size:10px; font-weight:normal;'>Now (Avg) / Target</span></th><th style='padding: 8px;'>Motion</th><th style='padding: 8px;'>Dehumidifier State</th><th style='padding: 8px;'>Run Time<br><span style='font-size:10px; font-weight:normal;'>Today (Yest)</span></th></tr>"
            
            def configuredCount = 0
            def numBaths = settings["numBathrooms"] ?: 1
            def now = new Date().time
            
            for (int i = 1; i <= (numBaths as Integer); i++) {
                def bName = settings["bathName_${i}"]
                def bSwitch = settings["bathSwitch_${i}"]
                
                if (bName && bSwitch) {
                    configuredCount++
                    def hSensor = settings["bathHumidity_${i}"]
                    def mSensor = settings["bathMotion_${i}"]
                    def swId = bSwitch.id.toString()
                    
                    def humVal = hSensor ? "${hSensor.currentValue('humidity')}%" : "N/A"
                    def motVal = mSensor ? mSensor.currentValue('motion')?.toUpperCase() : "N/A"
                    def swVal = bSwitch.currentValue('switch')?.toUpperCase() ?: "UNKNOWN"
                    
                    def motColor = (motVal == "ACTIVE") ? "blue" : "#888"
                    def swColor = (swVal == "ON") ? "green" : "black"
                    def humColor = "black"
                    
                    if (hSensor && settings["bathHumHigh_${i}"]) {
                        def currentH = hSensor.currentValue('humidity') as Number
                        def highH = settings["bathHumHigh_${i}"] as Number
                        if (currentH >= highH) humColor = "red"
                    }
                    
                    // Humidity Math & Target Setpoint
                    def humSum = state.dailyHumSum?."bath_${i}" ?: 0.0
                    def humCount = state.dailyHumCount?."bath_${i}" ?: 0
                    def avgHumStr = humCount > 0 ? "${Math.round(humSum / humCount)}%" : "--"
                    
                    def targetH = settings["globalBaselineSensor"] ? settings["globalBaselineSensor"].currentValue("humidity") : settings["bathHumTarget_${i}"]
                    def targetHumStr = targetH ? "${targetH}%" : "N/A"
                    
                    def humDisplay = "<span style='color: ${humColor}; font-weight: bold;'>${humVal}</span><br><span style='font-size:11px; color:#555;'>Avg: ${avgHumStr} / Tgt: ${targetHumStr}</span>"

                    // Live Timers & Run Time Logic
                    def pendingMsg = ""
                    def runSecondsToday = state.dailyDuration?."${swId}" ?: 0
                    def runSecondsYesterday = state.yesterdayDuration?."${swId}" ?: 0
                    
                    if (swVal == "ON") {
                        def startTime = state.switchEpoch?."${swId}"
                        def minRunMins = settings["bathMinRun_${i}"] ?: 15
                        
                        if (startTime) {
                            def elapsedMs = now - startTime
                            runSecondsToday += Math.round(elapsedMs / 1000).toInteger() // Live run time addition
                            
                            def minRunMs = minRunMins * 60000
                            if (elapsedMs < minRunMs) {
                                def remainMs = minRunMs - elapsedMs
                                pendingMsg += "<br><span style='color:purple; font-size:11px;'>Min Run Lock: ${formatTime(remainMs)}</span>"
                            }
                        }

                        def maxEpoch = state["maxRunEpoch_${i}"]
                        def maxMins = settings["bathMaxRun_${i}"] ?: 120
                        if (maxEpoch) {
                            def diffMs = (maxEpoch + (maxMins * 60000)) - now
                            if (diffMs > 0) pendingMsg += "<br><span style='color:red; font-size:11px;'>Max Cutoff: ${formatTime(diffMs)}</span>"
                        }
            
                        def timerEpoch = state["timerEpoch_${i}"]
                        def delayMins = settings["bathMotionDelay_${i}"] ?: 60
                        if (timerEpoch) {
                            def diffMs = (timerEpoch + (delayMins * 60000)) - now
                            if (diffMs > 0) pendingMsg += "<br><span style='color:blue; font-size:11px;'>Motion Off In: ${formatTime(diffMs)}</span>"
                        }
                    }
                    
                    // Format Run Times
                    def tMins = Math.floor(runSecondsToday / 60).toInteger()
                    def tHours = Math.floor(tMins / 60).toInteger()
                    tMins = tMins % 60
                    def tStr = tHours > 0 ? "${tHours}h ${tMins}m" : "${tMins}m"

                    def yMins = Math.floor(runSecondsYesterday / 60).toInteger()
                    def yHours = Math.floor(yMins / 60).toInteger()
                    yMins = yMins % 60
                    def yStr = yHours > 0 ? "${yHours}h ${yMins}m" : "${yMins}m"
                    
                    def runDisplay = "<b>${tStr}</b><br><span style='font-size:11px; color:#666;'>Yest: ${yStr}</span>"
                    
                    // Grace Period formatting
                    def graceEpoch = state["graceEpoch_${i}"]
                    def graceMins = settings["bathGracePeriod_${i}"] ?: 2
                    if (graceEpoch && !state["motionActive_${i}"] && !state["timerEpoch_${i}"]) {
                        def diffMs = (graceEpoch + (graceMins * 60000)) - now
                        if (diffMs > 0) pendingMsg += "<br><span style='color:orange; font-size:11px;'>Grace Period: ${formatTime(diffMs)}</span>"
                    }
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${bName}</b></td><td style='padding: 8px;'>${humDisplay}</td><td style='padding: 8px; color: ${motColor};'>${motVal}</td><td style='padding: 8px; color: ${swColor}; font-weight: bold;'>${swVal}${pendingMsg}</td><td style='padding: 8px;'>${runDisplay}</td></tr>"
                }
            }
            
            if (configuredCount == 0) {
                statusText += "<tr><td colspan='5' style='padding: 8px; color: #888;'>No bathrooms fully configured.</td></tr>"
            }
            statusText += "</table>"
            
            // TABLE 2: 7-Day Analytics
            statusText += "<b>7-Day Efficiency & Cost Analytics</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone</th><th style='padding: 8px;'>Total Cycles</th><th style='padding: 8px;'>Run Time</th><th style='padding: 8px;'>Est. Cost</th></tr>"
            
            if (configuredCount > 0) {
                for (int i = 1; i <= (numBaths as Integer); i++) {
                    def bName = settings["bathName_${i}"]
                    def bSwitch = settings["bathSwitch_${i}"]
                    
                    if (bName && bSwitch) {
                        def swId = bSwitch.id.toString()
                        def cycles = state.weeklyCycles?."${swId}" ?: 0
                        def runSeconds = state.weeklyDuration?."${swId}" ?: 0
                        
                        if (bSwitch.currentValue("switch") == "on") {
                            def epoch = state.switchEpoch?."${swId}"
                            if (epoch) {
                                runSeconds += Math.round((now - epoch) / 1000).toInteger()
                            }
                        }
                        
                        def rHours = Math.floor(runSeconds / 3600).toInteger()
                        def rMins = Math.round((runSeconds % 3600) / 60).toInteger()
                        def timeStr = "${rHours}h ${rMins}m"
                        
                        def costStr = "\$0.00"
                        def kwhRate = settings["costPerKwh"] ?: 0.15
                        def watts = settings["bathWatts_${i}"] ?: 30
                        if (runSeconds > 0) {
                            def totalHours = runSeconds / 3600
                            def kw = watts / 1000
                            def estCost = totalHours * kw * kwhRate
                            costStr = String.format("\$%.2f", estCost)
                        }
                        
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${bName}</b></td><td style='padding: 8px;'>${cycles}</td><td style='padding: 8px;'>${timeStr}</td><td style='padding: 8px; color: red;'>${costStr}</td></tr>"
                    }
                }
            } else {
                statusText += "<tr><td colspan='4' style='padding: 8px; color: #888;'>No data available.</td></tr>"
            }
            statusText += "</table>"
            
            def globalStatus = isSystemPaused() ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            statusText += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>System Core:</b> ${globalStatus}</div>"

            paragraph statusText
        }

        section("Application History Log") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No events logged yet.</i>"
            }
        }
        
        section("Global Core Settings") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "Pausing this stops all automatic dehumidifier actions."
            input "numBathrooms", "number", title: "Number of Bathrooms to Manage (1-4)", required: true, defaultValue: 1, range: "1..4", submitOnChange: true
            input "costPerKwh", "decimal", title: "Electricity Cost (\$ per kWh)", required: true, defaultValue: 0.15, description: "Used to calculate the 7-Day estimated cost. National average is \$0.15."
            input "globalBaselineSensor", "capability.relativeHumidityMeasurement", title: "Dynamic Baseline Humidity Sensor (Optional)", required: false, description: "Select a hallway or living room sensor. If set, bathrooms will dry until they match THIS sensor's humidity, overriding hardcoded targets."
            input "pushNotification", "capability.notification", title: "Push Notification Device (For Danger Alerts)", required: false, multiple: true
        }
        
        def numBaths = settings["numBathrooms"] ?: 1
        if (numBaths > 0 && numBaths <= 4) {
            for (int i = 1; i <= (numBaths as Integer); i++) {
                def bName = settings["bathName_${i}"] ?: "Bathroom ${i}"
                section("${bName} Configuration") {
                    href(name: "bathHref${i}", page: "bathroomPage", params: [bathNum: i], title: "Configure ${bName}")
                }
            }
        }
    }
}

def bathroomPage(params) {
    def bNum = params?.bathNum ?: state.currentBath ?: 1
    state.currentBath = bNum
    def currentName = settings["bathName_${bNum}"] ?: "Bathroom ${bNum}"
    
    dynamicPage(name: "bathroomPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        
        section("Zone Identification") {
            input "bathName_${bNum}", "text", title: "Zone Name (e.g., Master Bath, Guest Bath)", required: true, defaultValue: "Bathroom ${bNum}", submitOnChange: true
        }
        
        section("Hardware Devices") {
            input "bathSwitch_${bNum}", "capability.switch", title: "Dehumidifier / Exhaust Fan Switch", required: true
            input "bathWatts_${bNum}", "number", title: "Device Wattage (For Cost Math)", required: true, defaultValue: 30, description: "Standard exhaust fans are 30-50W. Standalone dehumidifiers are 300-700W."
            input "bathMotion_${bNum}", "capability.motionSensor", title: "Motion Sensor", required: false
            input "bathHumidity_${bNum}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: false
        }
        
        section("Energy Efficiency & Failsafes") {
            paragraph "If a sensor breaks or gets stuck, this setting ensures your fan won't run forever and drain your electricity."
            input "bathMaxRun_${bNum}", "number", title: "Absolute Maximum Run Time (Minutes)", required: true, defaultValue: 120, description: "Emergency shutoff. Forcefully cuts power if the fan runs this long continuously, regardless of motion or humidity."
            input "bathMinRun_${bNum}", "number", title: "Compressor Minimum Run Time (Minutes)", required: true, defaultValue: 15, description: "Prevents short-cycling by keeping the unit on for at least this long once started."
        }

        section("Automation Rules (Motion)") {
            paragraph "Configure motion-based triggers and the 'Quiet Hours' bypass to prevent fans from waking you up at night."
            input "bathQuietStart_${bNum}", "time", title: "Quiet Hours Start Time (Optional)", required: false
            input "bathQuietEnd_${bNum}", "time", title: "Quiet Hours End Time (Optional)", required: false
            input "bathGracePeriod_${bNum}", "number", title: "Motion Grace Period (Minutes)", required: true, defaultValue: 2, description: "Wait this long after motion stops BEFORE starting the final Turn-Off Delay timer."
            input "bathMotionDelay_${bNum}", "number", title: "Turn-Off Delay Timer (Minutes)", required: true, defaultValue: 60, description: "Once the grace period ends, count down this many minutes before shutting off the fan."
        }
        
        section("Automation Rules (Humidity)") {
            paragraph "Overrides motion. If humidity spikes above the Turn-On threshold, the fan turns on. It ignores Quiet Hours."
            input "bathHumHigh_${bNum}", "number", title: "Turn-On Threshold (%)", required: false, defaultValue: 65
            input "bathHumTarget_${bNum}", "number", title: "Target Turn-Off Threshold (%)", required: false, defaultValue: 50, description: "NOTE: Ignored if you selected a Dynamic Baseline Sensor on the main page."
            input "bathDangerHum_${bNum}", "number", title: "Danger Push Alert Threshold (%)", required: true, defaultValue: 75, description: "If the fan turns off but the room remains above this level for 30 minutes, push an alert to your phone."
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.weeklyCycles = state.weeklyCycles ?: [:]
    state.weeklyDuration = state.weeklyDuration ?: [:]
    state.dailyDuration = state.dailyDuration ?: [:]
    state.yesterdayDuration = state.yesterdayDuration ?: [:]
    state.dailyHumSum = state.dailyHumSum ?: [:]
    state.dailyHumCount = state.dailyHumCount ?: [:]
    state.switchEpoch = state.switchEpoch ?: [:]
    
    def numBaths = settings["numBathrooms"] ?: 1
    
    for (int i = 1; i <= (numBaths as Integer); i++) {
        def bSwitch = settings["bathSwitch_${i}"]
        def mSensor = settings["bathMotion_${i}"]
        def hSensor = settings["bathHumidity_${i}"]
        
        state["motionActive_${i}"] = false
        state["graceEpoch_${i}"] = null
        state["timerEpoch_${i}"] = null
        state["ownsCycle_${i}"] = false // Ownership state initialization
        
        if (bSwitch) subscribe(bSwitch, "switch", switchHandler)
        if (mSensor) subscribe(mSensor, "motion", motionHandler)
        if (hSensor) subscribe(hSensor, "humidity", humidityHandler)
    }
    
    subscribe(location, "systemStart", hubRebootHandler)
    schedule("0 0 0 ? * SUN", resetWeeklyCounters)
    schedule("0 0 0 * * ?", resetDailyCounters)
    runIn(5, "sweepOrphanedFans")
}

def formatTime(ms) {
    if (ms < 0) return "0s"
    def totalSecs = Math.round(ms / 1000).toInteger()
    def mins = Math.floor(totalSecs / 60).toInteger()
    def secs = totalSecs % 60
    return mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
}

def isSystemPaused() {
    return (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
}

def resetDailyCounters() {
    def now = new Date().time
    def numBaths = settings["numBathrooms"] ?: 1

    if (!state.yesterdayDuration) state.yesterdayDuration = [:]
    if (!state.dailyDuration) state.dailyDuration = [:]
    if (!state.weeklyDuration) state.weeklyDuration = [:]

    // Pre-process currently running fans to "bank" their time up to midnight
    for (int i = 1; i <= (numBaths as Integer); i++) {
        def bSwitch = settings["bathSwitch_${i}"]
        if (bSwitch && bSwitch.currentValue("switch") == "on") {
            def swId = bSwitch.id.toString()
            if (state.switchEpoch && state.switchEpoch["${swId}"]) {
                def durationSeconds = Math.round((now - state.switchEpoch["${swId}"]) / 1000).toInteger()
                state.dailyDuration["${swId}"] = (state.dailyDuration["${swId}"] ?: 0) + durationSeconds
                state.weeklyDuration["${swId}"] = (state.weeklyDuration["${swId}"] ?: 0) + durationSeconds
                
                // Reset epoch to midnight for the new day's math
                state.switchEpoch["${swId}"] = now
            }
        }
    }

    // Move today to yesterday, then clear trackers
    state.yesterdayDuration = state.dailyDuration.clone()
    state.dailyDuration = [:]
    state.dailyHumSum = [:]
    state.dailyHumCount = [:]
}

def resetWeeklyCounters() {
    state.weeklyCycles = [:]; state.weeklyDuration = [:]; state.switchEpoch = [:]
}

def hubRebootHandler(evt) { sweepOrphanedFans() }

def sweepOrphanedFans() {
    if (isSystemPaused()) return
    def numBaths = settings["numBathrooms"] ?: 1
    for (int i = 1; i <= (numBaths as Integer); i++) {
        def bSwitch = settings["bathSwitch_${i}"]
        if (bSwitch && bSwitch.currentValue("switch") == "on") {
            evaluateTurnOff([bathNum: i, source: "reboot"])
        }
    }
}

def switchHandler(evt) {
    def swId = evt.device.id.toString()
    def swName = evt.device.displayName
    def action = evt.value
    def targetBathNum = 0
    def numBaths = settings["numBathrooms"] ?: 1
    for (int i = 1; i <= (numBaths as Integer); i++) {
        if (settings["bathSwitch_${i}"]?.id == swId) targetBathNum = i
    }
    
    if (action == "on") {
        if (!state.switchEpoch) state.switchEpoch = [:]
        def currentEpoch = new Date().time
        state.switchEpoch["${swId}"] = currentEpoch
        if (!state.weeklyCycles) state.weeklyCycles = [:]
        state.weeklyCycles["${swId}"] = (state.weeklyCycles["${swId}"] ?: 0) + 1
        addToHistory("HARDWARE: ${swName} turned ON.")
        
        if (targetBathNum > 0) {
            // ONLY start the guillotine timer if THIS app owns the current run cycle
            if (state["ownsCycle_${targetBathNum}"]) {
                def maxRunMins = settings["bathMaxRun_${targetBathNum}"] ?: 120
                state["maxRunEpoch_${targetBathNum}"] = currentEpoch 
                runIn(maxRunMins * 60, "executeEmergencyShutoff", [data: [bathNum: targetBathNum, epoch: currentEpoch], overwrite: false])
            }
        }
    } else if (action == "off") {
        if (state.switchEpoch && state.switchEpoch["${swId}"]) {
            def durationSeconds = Math.round((new Date().time - state.switchEpoch["${swId}"]) / 1000).toInteger()
            
            if (!state.weeklyDuration) state.weeklyDuration = [:]
            state.weeklyDuration["${swId}"] = (state.weeklyDuration["${swId}"] ?: 0) + durationSeconds
            
            if (!state.dailyDuration) state.dailyDuration = [:]
            state.dailyDuration["${swId}"] = (state.dailyDuration["${swId}"] ?: 0) + durationSeconds
            
            state.switchEpoch["${swId}"] = null
            
            if (targetBathNum > 0) {
                state["ownsCycle_${targetBathNum}"] = false // CLEAR the cycle ownership flag
                state["maxRunEpoch_${targetBathNum}"] = null
                state["timerEpoch_${targetBathNum}"] = null
                state["graceEpoch_${targetBathNum}"] = null
                runIn(30 * 60, "dangerCheck", [data: [bathNum: targetBathNum]])
            }
        }
        addToHistory("HARDWARE: ${swName} turned OFF.")
    }
}

def dangerCheck(data) {
    def hSensor = settings["bathHumidity_${data.bathNum}"]
    if (hSensor) {
        def dangerT = settings["bathDangerHum_${data.bathNum}"] ?: 75
        def currentH = hSensor.currentValue("humidity") as Number
        if (currentH >= dangerT) {
            def msg = "🚨 WARNING: ${settings["bathName_${data.bathNum}"]} is failing to dry (${currentH}%)."
            if (settings["pushNotification"]) settings["pushNotification"].deviceNotification(msg)
        }
    }
}

def executeEmergencyShutoff(data) {
    if (!state["ownsCycle_${data.bathNum}"]) return // Verify cycle ownership
    if (state["maxRunEpoch_${data.bathNum}"] != data.epoch || isSystemPaused()) return
    
    def bSwitch = settings["bathSwitch_${data.bathNum}"]
    if (bSwitch && bSwitch.currentValue("switch") == "on") {
        bSwitch.off()
        runIn(60, "executeDoubleTap", [data: [bathNum: data.bathNum], overwrite: false])
    }
}

def executeDoubleTap(data) {
    if (!state["ownsCycle_${data.bathNum}"]) return // Verify cycle ownership
    
    def bSwitch = settings["bathSwitch_${data.bathNum}"]
    if (bSwitch && bSwitch.currentValue("switch") == "on" && !state["motionActive_${data.bathNum}"]) {
        bSwitch.off()
    }
}

def motionHandler(evt) {
    if (isSystemPaused()) return
    def mId = evt.device.id
    def action = evt.value
    def numBaths = settings["numBathrooms"] ?: 1
    for (int i = 1; i <= (numBaths as Integer); i++) {
        if (settings["bathMotion_${i}"]?.id == mId) {
            def bSwitch = settings["bathSwitch_${i}"]
            if (!bSwitch) continue
           
            if (action == "active") {
                state["motionActive_${i}"] = true
                state["graceEpoch_${i}"] = null
                state["timerEpoch_${i}"] = null
                def qStart = settings["bathQuietStart_${i}"], qEnd = settings["bathQuietEnd_${i}"]
                if (!(qStart && qEnd && timeOfDayIsBetween(qStart, qEnd, new Date(), location.timeZone))) {
                    if (bSwitch.currentValue("switch") != "on") {
                        state["ownsCycle_${i}"] = true // CLAIM the cycle
                        bSwitch.on()
                    }
                }
            } else {
                state["motionActive_${i}"] = false
                def graceMins = settings["bathGracePeriod_${i}"] ?: 2
                def epoch = new Date().time
                state["graceEpoch_${i}"] = epoch 
                runIn(graceMins * 60, "startDehumidifierTimer", [data: [bathNum: i, epoch: epoch], overwrite: false])
            }
        }
    }
}

def startDehumidifierTimer(data) {
    if (state["graceEpoch_${data.bathNum}"] != data.epoch || state["motionActive_${data.bathNum}"]) return 
    state["graceEpoch_${data.bathNum}"] = null
    state["timerEpoch_${data.bathNum}"] = new Date().time
    def delayMins = settings["bathMotionDelay_${data.bathNum}"] ?: 60
    runIn(delayMins * 60, "evaluateTurnOff", [data: [bathNum: data.bathNum, source: "timer"]])
}

def humidityHandler(evt) {
    if (isSystemPaused()) return
    def hId = evt.device.id
    def currentHum = evt.value.toDouble() 
    def numBaths = settings["numBathrooms"] ?: 1
    
    for (int i = 1; i <= (numBaths as Integer); i++) {
        if (settings["bathHumidity_${i}"]?.id == hId) {
            
            // Add to the daily rolling average map
            if (!state.dailyHumSum) state.dailyHumSum = [:]
            if (!state.dailyHumCount) state.dailyHumCount = [:]
            def sum = state.dailyHumSum["bath_${i}"] ?: 0.0
            def count = state.dailyHumCount["bath_${i}"] ?: 0
            state.dailyHumSum["bath_${i}"] = sum + currentHum
            state.dailyHumCount["bath_${i}"] = count + 1

            def bSwitch = settings["bathSwitch_${i}"]
            def highT = settings["bathHumHigh_${i}"]?.toString()?.toDouble() 
            def targetT = settings["globalBaselineSensor"] ? settings["globalBaselineSensor"].currentValue("humidity") : settings["bathHumTarget_${i}"]
            
            if (!bSwitch) continue
            
            if (highT && currentHum >= highT && bSwitch.currentValue("switch") != "on") {
                state["ownsCycle_${i}"] = true // CLAIM the cycle
                bSwitch.on()
            }
            
            if (targetT && currentHum <= targetT.toString().toDouble()) {
                state["timerEpoch_${i}"] = null
                state["graceEpoch_${i}"] = null
                evaluateTurnOff([bathNum: i, source: "humidity"])
            }
        }
    }
}

def evaluateTurnOff(data) {
    if (isSystemPaused()) return
    def bNum = data.bathNum
    
    if (!state["ownsCycle_${bNum}"]) return // IMMEDIATELY YIELD if we don't own the run cycle
    
    def bSwitch = settings["bathSwitch_${bNum}"]
    def mSensor = settings["bathMotion_${bNum}"]
    def hSensor = settings["bathHumidity_${bNum}"]
    if (!bSwitch || bSwitch.currentValue("switch") == "off") return
    if (mSensor && (mSensor.currentValue("motion") == "active" || state["motionActive_${bNum}"])) return
    
    def targetT = settings["globalBaselineSensor"] ? settings["globalBaselineSensor"].currentValue("humidity") : settings["bathHumTarget_${bNum}"]
    if (hSensor && targetT && hSensor.currentValue("humidity") > (targetT as Number)) return

    // --- Compressor Minimum Run Time Check ---
    def swId = bSwitch.id.toString()
    def startTime = state.switchEpoch?."${swId}"
    def minRunMins = settings["bathMinRun_${bNum}"] ?: 15
    
    if (startTime) {
        def elapsedMs = new Date().time - startTime
        def minRunMs = minRunMins * 60000
        if (elapsedMs < minRunMs) {
            def remainingSecs = Math.round((minRunMs - elapsedMs) / 1000).toInteger()
            runIn(remainingSecs, "evaluateTurnOff", [data: [bathNum: bNum, source: "minRunCheck"], overwrite: true])
            return
        }
    }

    bSwitch.off()
    runIn(60, "executeDoubleTap", [data: [bathNum: bNum], overwrite: false])
}
