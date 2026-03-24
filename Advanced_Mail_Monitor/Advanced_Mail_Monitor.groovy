/**
 * Advanced Mail Monitor
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Mail Monitor",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced mailbox state tracking with historical averages, audio announcements, nag reminders, and live telemetry dashboard.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            if (mailSensors && mailSwitch) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Metric</th><th style='padding: 8px;'>Today</th><th style='padding: 8px;'>Historical Average</th></tr>"
                
                def tDelivery = state.todayDeliveryTime ?: "--:-- --"
                def tRetrieval = state.todayRetrievalTime ?: "--:-- --"
                
                def avgDel = state.avgDeliveryTime ? minutesToTimeStr(state.avgDeliveryTime) : "--:-- --"
                def avgRet = state.avgRetrievalTime ? minutesToTimeStr(state.avgRetrievalTime) : "--:-- --"
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Mail Delivery</b></td><td style='padding: 8px; color: green;'>${tDelivery}</td><td style='padding: 8px;'>${avgDel}</td></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Mail Retrieval</b></td><td style='padding: 8px; color: blue;'>${tRetrieval}</td><td style='padding: 8px;'>${avgRet}</td></tr>"
                statusText += "</table>"
                
                // Sensor Battery Tracking Table
                def batteryHtml = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-top: 10px;'>"
                batteryHtml += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Mailbox Sensor</th><th style='padding: 8px;'>Battery Health</th></tr>"
                
                mailSensors.each { sensor ->
                    def batt = sensor.currentValue("battery") ?: "N/A"
                    def battColor = "green"
                    if (batt != "N/A") {
                        if (batt.toInteger() <= 15) battColor = "red"
                        else if (batt.toInteger() <= 50) battColor = "orange"
                        batt = "${batt}%"
                    }
                    batteryHtml += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${sensor.displayName}</td><td style='padding: 8px; color: ${battColor}; font-weight: bold;'>${batt}</td></tr>"
                }
                batteryHtml += "</table>"
                statusText += batteryHtml
                
                // Temperature Display
                if (tempSensor) {
                    def currentTemp = tempSensor.currentValue("temperature") ?: "--"
                    statusText += "<div style='margin-top: 10px; padding: 10px; background: #fff3e0; border: 1px solid #ffcc80; border-radius: 4px; font-size: 13px; color: #e65100;'><b>Mailbox Internal Temperature:</b> ${currentTemp}°</div>"
                }
                
                def switchState = mailSwitch.currentValue("switch")?.toUpperCase() ?: "UNKNOWN"
                def switchColor = (switchState == "ON") ? "red" : "green"
                def indicatorText = (switchState == "ON") ? "MAIL WAITING" : "EMPTY / WAITING FOR DELIVERY"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Indicator Switch:</b> <span style='color: ${switchColor}; font-weight: bold;'>${switchState}</span></div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>Current State:</b> <b>${indicatorText}</b></div>"
                statusText += "</div>"

                paragraph statusText
            } else {
                paragraph "<i>Configure devices below to see live system status.</i>"
            }
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as events occur.</i>"
            }
        }
        
        section("Device Configuration") {
            input "mailSensors", "capability.contactSensor", title: "Mailbox Door Sensor(s)", multiple: true, required: true, description: "Select 1 or 2 sensors on the mailbox."
            input "tempSensor", "capability.temperatureMeasurement", title: "Mailbox Temperature Sensor (Optional)", required: false, description: "Select if your contact sensor supports temperature."
            input "mailSwitch", "capability.switch", title: "Virtual Mail Indicator Switch", required: true, description: "Turns ON when delivered, OFF when retrieved."
            input "deliveryLockout", "number", title: "Delivery-to-Retrieval Lockout (Minutes)", defaultValue: 2, required: true, description: "Prevents immediate retrieval if the mail carrier opens the box multiple times."
        }

        section("Audio Announcements (Smart Speakers)") {
            input "ttsSpeakers", "capability.speechSynthesis", title: "Smart Speakers for Announcements", multiple: true, required: false
            input "ttsDeliveryText", "text", title: "Delivery Announcement Text", defaultValue: "The mail has arrived", required: false
            input "ttsRetrievalText", "text", title: "Retrieval Announcement Text", defaultValue: "The mail has been retrieved", required: false
        }

        section("Visual Indicators (Colored Lights)") {
            input "indicatorLight", "capability.colorControl", title: "Mail Indicator Light (Color Bulbs)", required: false, multiple: true, description: "Select standard RGB lights to turn on when mail is delivered."
            input "inovelliSwitches", "capability.colorControl", title: "Inovelli Switch LED Notifiers", required: false, multiple: true, description: "Select up to 3 Inovelli LED Child Devices."
            input "deliveryColor", "enum", title: "Color when Mail is Delivered", required: false, defaultValue: "Green", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"]
            input "lightLevel", "number", title: "Indicator Light Level (%)", defaultValue: 100, required: false, range: "1..100"
            input "retrievalLightAction", "enum", title: "Action when Mail is Retrieved", required: false, defaultValue: "Turn Off", options: ["Turn Off", "Leave On"]
        }

        section("Notifications & Alerts") {
            input "pushDevices", "capability.notification", title: "Select Devices for Notifications", multiple: true, required: false, description: "Leave blank to send to all devices."
            input "sendPushDelivery", "bool", title: "Send Push Notification on Delivery?", defaultValue: false
            input "sendPushRetrieval", "bool", title: "Send Push Notification on Retrieval?", defaultValue: false
            input "tempThreshold", "number", title: "High Temperature Alert Threshold (°F/°C)", defaultValue: 90, required: false, description: "Alerts once per day if mail is waiting and temp exceeds this."
        }
        
        section("Security Alerts (Unexpected Opens)") {
            input "securityStartTime", "time", title: "Security Window Start (e.g., 8:00 PM)", required: false
            input "securityEndTime", "time", title: "Security Window End (e.g., 6:00 AM)", required: false
        }

        section("End-of-Day Nag Reminders") {
            input "enableNag", "bool", title: "Enable End-of-Day Mail Reminder?", defaultValue: false, submitOnChange: true
            if (enableNag) {
                input "nagTime", "time", title: "Time to check for unretrieved mail", required: true
                input "nagPush", "bool", title: "Send Push Notification?", defaultValue: true
                input "nagTTS", "bool", title: "Make Audio Announcement?", defaultValue: false
                input "nagMessage", "text", title: "Nag Message Text", defaultValue: "Don't forget to grab the mail!", required: true
            }
        }
        
        section("Time & Mode Restrictions") {
            input "activeModes", "mode", title: "Active Modes (App only triggers in these)", multiple: true, required: false
            input "startTime", "time", title: "Monitoring Start Time", required: false
            input "endTime", "time", title: "Monitoring End Time", required: false
        }
        
        section("System Reset") {
            input "btnForceReset", "button", title: "Reset Historical Averages & Logs"
        }
    }
}

def installed() {
    log.info "Advanced Mail Monitor Installed."
    initialize()
}

def updated() {
    log.info "Advanced Mail Monitor Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.deliveryCount = state.deliveryCount ?: 0
    state.retrievalCount = state.retrievalCount ?: 0
    state.lastEventTime = state.lastEventTime ?: 0
    
    subscribe(mailSensors, "contact.open", sensorOpenHandler)
    
    if (tempSensor) {
        subscribe(tempSensor, "temperature", tempHandler)
    }
    
    schedule("0 0 0 * * ?", "midnightReset")
    schedule("0 0 10 * * ?", "batteryCheckHandler") // Checks battery daily at 10 AM
    
    if (enableNag && nagTime) {
        schedule(nagTime, "nagHandler")
    }
}

def appButtonHandler(btn) {
    if (btn == "btnForceReset") {
        state.historyLog = []
        state.deliveryCount = 0
        state.retrievalCount = 0
        state.avgDeliveryTime = null
        state.avgRetrievalTime = null
        state.todayDeliveryTime = null
        state.todayRetrievalTime = null
        addToHistory("SYSTEM: Historical averages and logs manually reset by user.")
    }
}

def sensorOpenHandler(evt) {
    // 1. Check Security Window for unexpected opens
    if (securityStartTime && securityEndTime) {
        def isSecurityHours = timeOfDayIsBetween(securityStartTime, securityEndTime, new Date(), location.timeZone)
        if (isSecurityHours) {
            sendMessage("🚨 SECURITY ALERT: The mailbox was opened unexpectedly during restricted hours!")
            addToHistory("SECURITY ALERT: Mailbox opened during restricted hours.")
            return // Halt standard delivery/retrieval processing
        }
    }

    // 2. Check Modes
    if (activeModes && !activeModes.contains(location.mode)) {
        log.info "Mail Monitor: Ignored open event. Hub is not in an active mode."
        return
    }
    
    // 3. Check Time Window
    if (startTime && endTime) {
        def between = timeOfDayIsBetween(startTime, endTime, new Date(), location.timeZone)
        if (!between) {
            log.info "Mail Monitor: Ignored open event. Outside of active time window."
            return
        }
    }
    
    def now = new Date().time
    def switchState = mailSwitch.currentValue("switch")
    def tz = location.timeZone ?: TimeZone.getDefault()
    def currentTimeStr = new Date().format("h:mm a", tz)
    def currentMinutes = getMinutesSinceMidnight(new Date(), tz)
    
    // 4. State-Aware Lockout Logic
    if (switchState == "on") {
        // System is waiting for RETRIEVAL. Check if we are still in the mail carrier's lockout window.
        def lastDelivery = state.lastDeliveryTime ?: 0
        def lockoutMillis = (deliveryLockout != null ? deliveryLockout.toInteger() : 2) * 60000
        
        if ((now - lastDelivery) < lockoutMillis) {
            def secLeft = ((lockoutMillis - (now - lastDelivery)) / 1000).toInteger()
            log.info "Mail Monitor: Ignored Retrieval event. Carrier lockout active for ${secLeft} more seconds."
            return
        }
        
        // Passed lockout, process RETRIEVAL
        mailSwitch.off()
        state.todayRetrievalTime = currentTimeStr
        updateAverage("retrieval", currentMinutes)
        addToHistory("RETRIEVAL DETECTED: Sensor opened. Indicator Switch turned OFF.")
        
        // Handle Lights, Notifications, and TTS
        if (retrievalLightAction == "Turn Off") {
            if (indicatorLight) { indicatorLight.each { it.off() } }
            if (inovelliSwitches) { inovelliSwitches.take(3).each { it.off() } }
        }
        if (sendPushRetrieval) {
            sendMessage("📬 Your mail has been retrieved!")
        }
        if (ttsSpeakers && ttsRetrievalText) {
            ttsSpeakers.speak(ttsRetrievalText)
        }
        
    } else {
        // System is waiting for DELIVERY. 
        // Apply a tiny 10-second hardware debounce to prevent sensor bounce chatter.
        def lastEvent = state.lastEventTime ?: 0
        if ((now - lastEvent) < 10000) return 
        
        // Process DELIVERY
        mailSwitch.on()
        state.todayDeliveryTime = currentTimeStr
        state.lastDeliveryTime = now // Start the carrier lockout timer
        state.lastEventTime = now
        updateAverage("delivery", currentMinutes)
        addToHistory("DELIVERY DETECTED: Sensor opened. Indicator Switch turned ON.")
        
        // Handle Lights, Notifications, and TTS
        if (indicatorLight) {
            setLightColor(indicatorLight, deliveryColor, lightLevel != null ? lightLevel : 100)
        }
        if (inovelliSwitches) {
            def inovelliLimited = inovelliSwitches.take(3)
            setLightColor(inovelliLimited, deliveryColor, lightLevel != null ? lightLevel : 100)
        }
        if (sendPushDelivery) {
            sendMessage("📫 The mail has been delivered!")
        }
        if (ttsSpeakers && ttsDeliveryText) {
            ttsSpeakers.speak(ttsDeliveryText)
        }
    }
}

def tempHandler(evt) {
    if (!tempThreshold || !mailSwitch) return
    
    // Using numericValue prevents NullPointerExceptions if the driver passes strings
    def currentTemp = evt.numericValue ?: evt.value.toDouble()
    def isMailWaiting = mailSwitch.currentValue("switch") == "on"

    if (isMailWaiting && currentTemp >= tempThreshold) {
        def lastAlert = state.lastTempAlertDate
        def today = new Date().format("yyyy-MM-dd", location.timeZone ?: TimeZone.getDefault())
        
        // Only alert once per day to avoid notification spam
        if (lastAlert != today) {
            sendMessage("🌡️ Warning: Mail is delivered and the mailbox temperature is ${currentTemp}°. Retrieve packages soon!")
            state.lastTempAlertDate = today
            addToHistory("TEMP ALERT: Mailbox reached ${currentTemp}° while mail was waiting.")
        }
    }
}

def batteryCheckHandler() {
    mailSensors?.each { sensor ->
        def batt = sensor.currentValue("battery")
        if (batt != null && batt.toInteger() <= 15) {
            sendMessage("🔋 Low Battery Alert: ${sensor.displayName} is at ${batt}%.")
            addToHistory("BATTERY ALERT: ${sensor.displayName} dropped to ${batt}%.")
        }
    }
}

def nagHandler() {
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (mailSwitch && mailSwitch.currentValue("switch") == "on") {
        addToHistory("REMINDER NAG: Mail is still unretrieved. Executing alerts.")
        if (nagPush && nagMessage) {
            sendMessage(nagMessage)
        }
        if (nagTTS && ttsSpeakers && nagMessage) {
            ttsSpeakers.speak(nagMessage)
        }
    } else {
        log.info "Mail Monitor: Nag time reached, but mail was already retrieved. Skipping nag."
    }
}

def sendMessage(String msg) {
    if (pushDevices) {
        pushDevices*.deviceNotification(msg)
    } else {
        sendPush(msg)
    }
}

def setLightColor(devices, colorName, level) {
    def isWhite = false
    def hueColor = 0
    def saturation = 100
    
    switch(colorName) {
        case "White": isWhite = true; break;
        case "Red": hueColor = 100; break;
        case "Green": hueColor = 39; break;
        case "Blue": hueColor = 70; break;
        case "Yellow": hueColor = 25; break;
        case "Orange": hueColor = 10; break;
        case "Purple": hueColor = 75; break;
        case "Pink": hueColor = 83; break;
    }
    
    def deviceList = devices instanceof java.util.Collection ? devices : [devices]
    
    deviceList.each { device -> 
        if (isWhite) {
            // Favor colorTemperature for pure white light if the bulb supports it
            if (device.hasCommand("setColorTemperature")) {
                device.setColorTemperature(6500) // Daylight white
                device.setLevel(level as Integer)
            } else {
                device.setColor([hue: 0, saturation: 0, level: level as Integer])
            }
        } else {
            device.setColor([hue: hueColor, saturation: saturation, level: level as Integer])
        }
        device.on()
    }
}

def updateAverage(type, currentMinutes) {
    if (type == "delivery") {
        def count = state.deliveryCount ?: 0
        def currentAvg = state.avgDeliveryTime ?: currentMinutes
        state.avgDeliveryTime = ((currentAvg * count) + currentMinutes) / (count + 1)
        state.deliveryCount = count + 1
    } else if (type == "retrieval") {
        def count = state.retrievalCount ?: 0
        def currentAvg = state.avgRetrievalTime ?: currentMinutes
        state.avgRetrievalTime = ((currentAvg * count) + currentMinutes) / (count + 1)
        state.retrievalCount = count + 1
    }
}

def getMinutesSinceMidnight(date, tz) {
    def hour = new Date(date.time).format("H", tz).toInteger()
    def min = new Date(date.time).format("m", tz).toInteger()
    return (hour * 60) + min
}

def minutesToTimeStr(minutesNum) {
    if (minutesNum == null) return "--:-- --"
    def totalMinutes = minutesNum.toInteger()
    def h = (totalMinutes / 60).toInteger()
    def m = totalMinutes % 60
    
    def ampm = "AM"
    if (h >= 12) {
        ampm = "PM"
        if (h > 12) h -= 12
    }
    if (h == 0) h = 12
    
    def mStr = m < 10 ? "0${m}" : "${m}"
    return "${h}:${mStr} ${ampm}"
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def tz = location.timeZone ?: TimeZone.getDefault()
    def timestamp = new Date().format("MM/dd HH:mm:ss", tz)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    log.info "HISTORY: [${timestamp}] ${msg}"
}

def midnightReset() {
    state.todayDeliveryTime = null
    state.todayRetrievalTime = null
    state.lastTempAlertDate = null // Reset the daily temp alert flag
    
    // Check if mail was left overnight
    if (mailSwitch.currentValue("switch") == "on") {
        addToHistory("SYSTEM RESET: Midnight cleanup completed. Mail is still waiting from yesterday.")
        // We do NOT turn off the switch here, so the system remembers the mail is still waiting.
        // We also leave the lights on (if configured) so the user sees the visual reminder in the morning.
    } else {
        addToHistory("SYSTEM RESET: Midnight cleanup completed. Mailbox is empty.")
    }
}
