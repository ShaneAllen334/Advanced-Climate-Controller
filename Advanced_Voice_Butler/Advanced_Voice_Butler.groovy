/**
 * Advanced Voice Butler
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Voice Butler",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Decoupled TTS orchestrator for contextual greetings, room state announcements, and Do Not Disturb perimeter guarding.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Voice Butler Configuration", install: true, uninstall: true) {
        
        // Fetch Lock Users for Dashboard and Settings
        def lockUsers = []
        if (frontDoorLock) {
            try {
                def lockCodesStr = frontDoorLock.currentValue("lockCodes")
                if (lockCodesStr) {
                    def parsed = new groovy.json.JsonSlurper().parseText(lockCodesStr)
                    if (parsed instanceof Map) {
                        lockUsers = parsed.collect { it.value.name ?: it.value }.findAll { it }.sort()
                    }
                }
            } catch (e) {}
        }

        section("Live System Dashboard") {
            paragraph "<i>Welcome to the Voice Butler command center. Below is a real-time read-only view of your perimeter status, active voice zones, and today's arrival/departure log.</i>"
            input "btnRefresh", "button", title: "🔄 Refresh Data Dashboard"
            
            def dndModesList = [settings.dndModes].flatten().findAll{it}
            def isDndMode = dndModesList.contains(location.mode)
            def isDndSwitch = dndSwitch?.currentValue("switch") == "on"
            
            def dndState = (isDndSwitch || isDndMode) ? "<span style='color: #c0392b; font-weight: bold;'>ACTIVE (Do Not Disturb)</span>" : "<span style='color: #27ae60; font-weight: bold;'>STANDBY (Accepting Visitors)</span>"
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>Perimeter Status:</b> ${dndState}</div>"
            
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Butler Incident Log</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Event Type</th><th style='padding: 8px;'>Current Count</th><th style='padding: 8px;'>Pending Report?</th></tr>"
            
            def morningPend = state.pendingMorningReport ? "<span style='color: #c0392b; font-weight: bold;'>Yes (Waiting for Motion in correct mode)</span>" : "<span style='color: #7f8c8d;'>No</span>"
            def arrivalPend = state.pendingArrivalReport ? "<span style='color: #c0392b; font-weight: bold;'>Yes (Waiting for Motion in correct mode)</span>" : "<span style='color: #7f8c8d;'>No</span>"
            
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Porch Motion (Night)</b></td><td style='padding: 8px;'>${state.nightMotionCount ?: 0}</td><td style='padding: 8px;'>${morningPend}</td></tr>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Doorbell Rings (Away)</b></td><td style='padding: 8px;'>${state.awayDoorbellCount ?: 0}</td><td style='padding: 8px;'>${arrivalPend}</td></tr>"
            statusText += "</table>"
            
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Active Voice Zones</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Voice Zone</th><th style='padding: 8px;'>Assigned Speaker</th><th style='padding: 8px;'>Last Announcement</th></tr>"
            
            def outSpeakerName = outdoorSpeaker ? outdoorSpeaker.displayName : "None Assigned"
            def outLast = state.lastOutdoorGreeting ? new Date(state.lastOutdoorGreeting).format("MM/dd h:mm a", location.timeZone) : "--:--"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Front Door</b></td><td style='padding: 8px;'>${outSpeakerName}</td><td style='padding: 8px;'>${outLast}</td></tr>"

            def inSpeakerName = globalIndoorSpeaker ? globalIndoorSpeaker.displayName : "None Assigned"
            def inLast = state.lastGlobalGreeting ? new Date(state.lastGlobalGreeting).format("MM/dd h:mm a", location.timeZone) : "--:--"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Main House (Global)</b></td><td style='padding: 8px;'>${inSpeakerName}</td><td style='padding: 8px;'>${inLast}</td></tr>"

            if (numRooms > 0) {
                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def rSpeaker = settings["roomSpeaker_${i}"] ? settings["roomSpeaker_${i}"].displayName : "Uses Global"
                    def rLast = state.lastRoomGreeting?."${i}" ? new Date(state.lastRoomGreeting."${i}").format("MM/dd h:mm a", location.timeZone) : "--:--"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${rName}</b></td><td style='padding: 8px;'>${rSpeaker}</td><td style='padding: 8px;'>${rLast}</td></tr>"
                }
            }
            statusText += "</table>"
            
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Daily Arrival Status</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>User</th><th style='padding: 8px;'>Status</th><th style='padding: 8px;'>Context / Reason Not Home</th></tr>"
            
            if (arrivalMode == "Automatic (Reads lock memory)") {
                def allNames = []
                settings.trackedLockCodes?.each { codeName ->
                    if (codeName.toLowerCase() == "admin code" && settings.adminUserAlias) {
                        allNames << settings.adminUserAlias
                    } else {
                        allNames << codeName
                    }
                }
                
                allNames += (state.hasArrivedToday ?: [:]).keySet().findAll { it != "global" }
                allNames = allNames.unique().sort()
                
                allNames.each { uName ->
                    def hasArrived = state.hasArrivedToday ? state.hasArrivedToday[uName] : false
                    def arrStatus = hasArrived ? "<span style='color: #27ae60; font-weight: bold;'>Arrived</span>" : "<span style='color: #7f8c8d;'>Waiting...</span>"
                    def contextText = hasArrived ? "Present" : (state.resetReasons?."${uName}" ?: state.globalResetReason ?: "Awaiting First Entry")
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${uName}</b></td><td style='padding: 8px;'>${arrStatus}</td><td style='padding: 8px; color: #555;'><i>${contextText}</i></td></tr>"
                }
                
            } else if (numLockUsers && numLockUsers > 0) {
                for (int i = 1; i <= (numLockUsers as Integer); i++) {
                    def uName = settings["lockUserName_${i}"]
                    if (uName) {
                        def hasArrived = state.hasArrivedToday ? state.hasArrivedToday[uName] : false
                        def arrStatus = hasArrived ? "<span style='color: #27ae60; font-weight: bold;'>Arrived</span>" : "<span style='color: #7f8c8d;'>Waiting...</span>"
                        def contextText = hasArrived ? "Present" : (state.resetReasons?."${uName}" ?: state.globalResetReason ?: "Awaiting First Entry")
                        
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${uName}</b></td><td style='padding: 8px;'>${arrStatus}</td><td style='padding: 8px; color: #555;'><i>${contextText}</i></td></tr>"
                    }
                }
            }
            
            if (!disableGlobalAnnouncements) {
                def globalArrived = state.hasArrivedToday ? state.hasArrivedToday["global"] : false
                def arrStatus = globalArrived ? "<span style='color: #27ae60; font-weight: bold;'>Arrived</span>" : "<span style='color: #7f8c8d;'>Waiting...</span>"
                def contextText = globalArrived ? "Present" : (state.resetReasons?."global" ?: state.globalResetReason ?: "Awaiting First Entry")
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Global Guest / Physical Key</b></td><td style='padding: 8px;'>${arrStatus}</td><td style='padding: 8px; color: #555;'><i>${contextText}</i></td></tr>"
            }
            
            statusText += "</table>"
            
            if (numDepartureUsers > 0) {
                statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Daily Departure Status</h4>"
                statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>User</th><th style='padding: 8px;'>Context Switch</th><th style='padding: 8px;'>Status</th></tr>"
                
                for (int i = 1; i <= (numDepartureUsers as Integer); i++) {
                    def dName = settings["depUserName_${i}"]
                    if (dName) {
                        def sw = settings["depSwitch_${i}"]
                        def swState = sw ? sw.currentValue("switch") : "N/A"
                        def swFmt = swState == "on" ? "<span style='color: #27ae60; font-weight: bold;'>ON</span>" : "<span style='color: #7f8c8d;'>OFF</span>"
                        def hasDeparted = state.hasDepartedToday ? state.hasDepartedToday[dName] : false
                        def depStatus = hasDeparted ? "<span style='color: #27ae60; font-weight: bold;'>Departed</span>" : "<span style='color: #7f8c8d;'>Pending/Inactive</span>"
                        
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${dName}</b></td><td style='padding: 8px;'>${sw?.displayName ?: 'None'} (${swFmt})</td><td style='padding: 8px;'>${depStatus}</td></tr>"
                    }
                }
                statusText += "</table>"
            }
            
            paragraph statusText
        }

        section("System Event History", hideable: true, hidden: true) {
            paragraph "<i>A running log of the last 30 voice announcements and status changes orchestrated by this app.</i>"
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry ->
                    histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>"
                }
                histHtml += "</div>"
                paragraph histHtml
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
        }
        
        section("Perimeter Guarding (Do Not Disturb)", hideable: true, hidden: true) {
            paragraph "<i>Configure the system to intercept unwanted visitors. When the selected 'Do Not Disturb' switch is ON or specific House Modes are active, doorbell presses or front porch motion will trigger the outdoor speaker to play a deterrent message instead of ringing your indoor chimes.</i>"
            
            input "dndSwitch", "capability.switch", title: "Do Not Disturb Toggle Switch", required: false, description: "Select the virtual switch you use to activate Do Not Disturb mode for the house."
            input "dndModes", "mode", title: "Do Not Disturb Modes", multiple: true, required: false, description: "Select house modes (like 'Away' or 'Night') that automatically activate DND without needing the switch."
            
            input "frontDoorbell", "capability.pushableButton", title: "Front Doorbell Button", required: false, description: "Select your smart doorbell. This acts as the primary trigger to play the DND message."
            input "frontDoorMotion", "capability.motionSensor", title: "Front Door Motion Sensor", required: false, description: "Optional: Use a porch motion sensor to trigger the DND message before they even press the bell."
            input "dndMotionDebounce", "number", title: "Motion Sensor Cooldown (Minutes)", defaultValue: 10, required: false, description: "Prevents the system from repeating the DND message too often if the motion sensor stays active."
            
            input "outdoorSpeaker", "capability.speechSynthesis", title: "Outdoor/Porch Speaker", required: false, description: "Select the speaker located outside. This is used for both DND deterrents, Welcome Home greetings, and Departures."
            input "outdoorVolume", "number", title: "Default Outdoor Volume (0-100)", required: false, description: "The app will override the speaker to this volume level before speaking, then restore its previous volume."
            
            input "btnTestDND", "button", title: "▶️ Test DND Intercept Audio", description: "Click to hear a sample DND message played on the outdoor speaker."

            paragraph "<b>Randomized Intercept Messages</b>\n<i>The app will randomly select one of the 10 filled-in messages below when a visitor arrives during DND hours.</i>"
            def dndDefs = [
                "We cannot come to the door right now. The camera is recording, please leave your message.",
                "Please leave a package or a message. We are currently unavailable.",
                "Do not disturb is active. Please try again later.",
                "We are unable to answer the door right now. Video recording is active.",
                "No one is available to answer the door. Please leave a message.",
                "We are not accepting visitors at this time. Please leave.",
                "The residents are currently unavailable. Camera surveillance is recording.",
                "Please leave your delivery at the door. We cannot answer right now.",
                "Do not disturb. All activity is being logged.",
                "We are occupied at the moment. Please return another time."
            ]
            for (int d = 1; d <= 10; d++) {
                input "dndMessage_${d}", "text", title: "DND Audio Message ${d}", required: (d == 1), defaultValue: dndDefs[d-1]
            }
        }
        
        section("Contextual Departures", hideable: true, hidden: true) {
            paragraph "<i>Provide a frictionless departure sequence. The system checks the user's Context Switch (e.g., 'Work Day' or 'School Day'), the current house mode, and the time window. If they leave, it plays a farewell and temporarily mutes DND/Intruder alarms so they can walk to the car in peace.</i>"
            
            input "frontDoorContact", "capability.contactSensor", title: "Front Door Contact Sensor", required: false, description: "The sensor on the door that triggers the departure check when opened."
            
            input "numDepartureUsers", "number", title: "Number of Departure Profiles (0-5)", required: true, defaultValue: 0, range: "0..5", submitOnChange: true
            
            if (numDepartureUsers > 0) {
                for (int i = 1; i <= (numDepartureUsers as Integer); i++) {
                    paragraph "<b>Departure Profile ${i}</b>"
                    input "depUserName_${i}", "text", title: "User Name (replaces %name%)", required: true
                    input "depType_${i}", "enum", title: "Profile Type (Changes default messages below)", options: ["Work", "School", "General"], defaultValue: "Work", submitOnChange: true
                    input "depSwitch_${i}", "capability.switch", title: "Context Switch (e.g. Work Day)", required: true, description: "The departure message will ONLY play if this switch is ON."
                    input "depModes_${i}", "mode", title: "Allowed House Modes", multiple: true, required: false, description: "Only allow this departure if the house is in one of these modes (e.g. Night, Morning)."
                    input "depTimeStart_${i}", "time", title: "Departure Window Start Time", required: true, description: "e.g., 6:00 AM"
                    input "depTimeEnd_${i}", "time", title: "Departure Window End Time", required: true, description: "e.g., 6:15 AM"
                    input "depVolume_${i}", "number", title: "Departure Volume (0-100)", required: false, description: "Optional: Sets a specific volume just for this departure. Leave blank to use default outdoor volume."
                    
                    def workMsgs = [
                        "Have a good day at work, %name%.",
                        "Drive safely to work, %name%.",
                        "Have a productive day at the office, %name%.",
                        "Time to make the donuts, %name%.",
                        "Have a great day at work, %name%.",
                        "Safe commute, %name%.",
                        "See you after work, %name%.",
                        "The perimeter will be secured while you work.",
                        "Have a great day, %name%.",
                        "Farewell %name%, the house is secure."
                    ]
                    def schoolMsgs = [
                        "Have a great day at school, %name%.",
                        "Learn a lot today, %name%.",
                        "Have fun at school, %name%.",
                        "Have a good day in class, %name%.",
                        "Be good at school, %name%.",
                        "Have an awesome school day, %name%.",
                        "See you after school, %name%.",
                        "Time for school, %name%.",
                        "Have a great day learning, %name%.",
                        "Have fun at school!"
                    ]
                    def genMsgs = [
                        "Have a great day %name%.",
                        "Safe travels %name%.",
                        "Have a good one %name%.",
                        "Take care out there %name%.",
                        "Goodbye %name%, I will monitor the perimeter.",
                        "Have a productive day %name%.",
                        "See you later %name%.",
                        "Have a good trip %name%.",
                        "Take it easy %name%.",
                        "Farewell %name%. The house is secure."
                    ]
                    
                    def selMsgs = settings["depType_${i}"] == "School" ? schoolMsgs : (settings["depType_${i}"] == "Work" ? workMsgs : genMsgs)
                    
                    paragraph "<i>Custom Departure Messages for ${settings["depUserName_${i}"] ?: "this user"} (Randomized)</i>"
                    for (int m = 1; m <= 10; m++) {
                        input "depMessage_${i}_${m}", "text", title: "Message ${m}", required: (m == 1), defaultValue: selMsgs[m-1]
                    }
                }
            }
        }
        
        section("Nighttime Intruder Deterrent", hideable: true, hidden: true) {
            paragraph "<i>Protect your perimeter at night. If motion is detected while asleep, the outdoor speaker will play a strict deterrent message. This overrides standard DND.</i>"
            
            input "enableIntruder", "bool", title: "Enable Intruder Deterrent?", defaultValue: false, submitOnChange: true
            
            if (enableIntruder) {
                input "intruderModes", "mode", title: "Active Modes (e.g., Night)", multiple: true, required: true, description: "Select the specific house modes where the intruder alarm is armed."
                input "intruderMotion", "capability.motionSensor", title: "Trigger Motion Sensors", multiple: true, required: true, description: "Sensors (like Porch or Side Door) that trigger the deterrent."
                
                input "intruderBypassDoors", "capability.contactSensor", title: "Bypass Doors (Dog Let-Out/User Exit)", multiple: true, required: false, description: "Safety catch: If any of these doors are currently open OR were opened in the last X minutes, the system assumes it's you outside and temporarily disables the deterrent."
                input "intruderBypassMinutes", "number", title: "Door Bypass Timeout (Minutes)", defaultValue: 5, required: true, description: "How long after closing the door should the deterrent remain paused?"
                
                input "intruderDebounce", "number", title: "Deterrent Cooldown (Minutes)", defaultValue: 5, required: true, description: "Prevents the speaker from going off every 10 seconds if someone is lingering."
                input "intruderVolume", "number", title: "Deterrent Announcement Volume (0-100)", required: false, description: "Leave blank to use the default outdoor speaker volume."
                
                paragraph "<b>Intruder Messages (Randomized)</b>"
                def intDefs = [
                    "Unexpected motion detected. Cameras are currently recording.",
                    "Warning. You are trespassing. Security has been notified.",
                    "Perimeter breach detected. Video logging initiated.",
                    "Please step away from the house. You are being recorded.",
                    "Alert. Unauthorized movement detected. Activating security protocols.",
                    "You have triggered the night perimeter alarm. Leave immediately.",
                    "Warning. Motion detected in restricted area. Camera is active.",
                    "Security alert. Your presence is being monitored and recorded.",
                    "Private property. Leave the area immediately or authorities will be contacted.",
                    "Motion sensors activated. Security cameras are tracking your movement."
                ]
                for (int d = 1; d <= 10; d++) {
                    input "intruderMessage_${d}", "text", title: "Intruder Message ${d}", required: (d == 1), defaultValue: intDefs[d-1]
                }
                
                input "btnTestIntruder", "button", title: "▶️ Test Intruder Audio", description: "Test the intruder volume and a randomized message on the outdoor speaker."
            }
        }

        section("Butler Event Reporting", hideable: true, hidden: true) {
            paragraph "<i>As a proper butler, the system will keep track of visitors while you are away or asleep, and provide a verbal summary when you wake up or return home. It waits until it sees motion in a central area before delivering the report.</i>"
            
            input "butlerAwayModes", "mode", title: "Away Modes (Track Doorbell)", multiple: true, required: false, description: "Modes where the house is empty. The system will count doorbell rings."
            input "butlerArrivalModes", "mode", title: "Arrival/Home Modes (Deliver Away Report)", multiple: true, required: false, description: "The Away report will ONLY play when the house is currently in one of these modes."

            input "butlerNightModes", "mode", title: "Night Modes (Track Porch Motion)", multiple: true, required: false, description: "Modes where the house is asleep. The system will count front porch motion."
            input "butlerMorningModes", "mode", title: "Morning Modes (Deliver Night Report)", multiple: true, required: false, description: "The Night report will ONLY play when the house is currently in one of these modes."

            input "butlerLrMotion", "capability.motionSensor", title: "Central Living Room Motion Sensor", required: false, description: "When motion is detected here AFTER the house leaves Away or Night mode, the report sequence begins."
            input "butlerLrSpeaker", "capability.speechSynthesis", title: "Living Room Speaker", required: false, description: "Select the speaker that will deliver the Butler Report. If blank, it uses the Global Speaker."
            input "butlerLrVolume", "number", title: "Living Room Speaker Volume (0-100)", required: false
            
            input "butlerReportDelay", "number", title: "Report Delay (Seconds)", defaultValue: 120, description: "Wait this long after central motion is detected before speaking, allowing Wi-Fi speakers/receivers to power up."
            
            input "btnTestMorningReport", "button", title: "▶️ Test Morning Report Audio"
            input "btnTestArrivalReport", "button", title: "▶️ Test Arrival Report Audio"
        }

        section("Global House Speakers", hideable: true, hidden: true) {
            paragraph "<i>Global speakers act as the primary voice of the house. Any configured room that does not have a dedicated 'Room Speaker' assigned will automatically route its Good Night / Good Morning announcements to the Global Speakers selected here.</i>"
            
            input "globalIndoorSpeaker", "capability.speechSynthesis", title: "Global Indoor Speaker(s)", multiple: true, required: false, description: "Select the main speaker(s) in central areas (like a Living Room or Kitchen)."
            input "globalVolume", "number", title: "Global Speaker Volume (0-100)", required: false, description: "The baseline volume for general house announcements."
        }
        
        section("Arrival Greetings & Smart Locks", hideable: true, hidden: true) {
            paragraph "<i>When someone unlocks the front door, the app checks if they have arrived today. If it's their first arrival, it plays a personalized greeting instantly on the Outdoor Speaker.</i>"
            
            input "frontDoorLock", "capability.lock", title: "Front Door Smart Lock", required: false, submitOnChange: true, description: "Select the smart lock the app should monitor for entry events."
            input "arrivalVolume", "number", title: "Welcome Home Announcement Volume (0-100)", required: false, description: "Dedicated volume setpoint just for Arrival greetings. If left blank, it defaults to the 'Outdoor Volume' setting."
            
            input "quickReturnGrace", "number", title: "Forgotten Item Grace Period (Minutes)", defaultValue: 5, description: "If a user unlocks the door within this many minutes of ANY front door opening, the system will assume they forgot something and silently welcome them back without a full greeting."
            
            input "btnTestArrival", "button", title: "▶️ Test Welcome Home Audio", description: "Click to test the arrival volume and message on the outdoor speaker."

            input "disableGlobalAnnouncements", "bool", title: "Ignore Keys & Manual Unlocks?", defaultValue: false, description: "If enabled, the app will completely ignore physical keys and manual thumb-turns. It will ONLY greet people who enter a recognized digital code on the keypad."
            
            input "ignoredCodes", "enum", title: "Silent / Ghost Codes", options: lockUsers, multiple: true, required: false, description: "Select specific lock codes that should NEVER trigger an arrival greeting or show up on the dashboard."
            input "ignoredCustomCodes", "text", title: "Custom Silent Codes", required: false, description: "Comma-separated list of exact names (like 'Admin Code') to completely ignore if they don't appear in the dropdown above."

            input "arrivalMode", "enum", title: "Arrival Detection Mode", options: ["Automatic (Reads lock memory)", "Manual (Assign names to slots)"], defaultValue: "Automatic (Reads lock memory)", submitOnChange: true, description: "Choose how the app tracks users. Automatic is recommended for most setups."
            
            if (arrivalMode == "Automatic (Reads lock memory)") {
                paragraph "<i><b>Automatic Mode:</b> The app pulls names directly from your lock. Select the specific codes you want to track below so unwanted entries (like 'Ghost' or 'Manual') don't trigger the system or pollute your dashboard.</i>"
                
                input "trackedLockCodes", "enum", title: "Select Codes to Track", options: lockUsers, multiple: true, required: true, submitOnChange: true, description: "Check the names of the lock codes you actually want to trigger greetings for."
                input "adminUserAlias", "text", title: "Admin Code Alias", required: false, description: "If you want the generic 'admin code' to trigger a personalized greeting, type your name here."

                def autoArr = [
                    "Welcome home, %name%.",
                    "Glad you're back, %name%.",
                    "Welcome back %name%, the house is ready.",
                    "Greetings %name%, house systems are online.",
                    "Good to see you, %name%.",
                    "Hello %name%. I've adjusted the climate for your arrival.",
                    "Welcome home %name%. All systems nominal.",
                    "It is good to have you back, %name%.",
                    "Perimeter disarmed. Welcome home, %name%.",
                    "Greetings %name%. I hope you had a pleasant time away."
                ]
                for (int m = 1; m <= 10; m++) {
                    input "autoGreeting_${m}", "text", title: "Dynamic Welcome Message ${m}", required: (m == 1), defaultValue: autoArr[m-1]
                }
            } else {
                paragraph "<i><b>Manual Mode:</b> Assign names to slots below. (Note: User reset switches have been moved to the Advanced section below).</i>"
                
                def defArr = [
                    "Welcome home. The house is ready for you.",
                    "Welcome back. I've been waiting.",
                    "Hello. All systems are operating normally.",
                    "Welcome home. Good to see you.",
                    "Greetings. The perimeter is disarmed.",
                    "Welcome. The climate control has been resumed.",
                    "Good to see you. Security systems are now on standby.",
                    "Welcome back. I will prepare the house for your evening.",
                    "Hello there. Your arrival has been logged.",
                    "Welcome home. Let me know if you need anything."
                ]
                paragraph "<b>Fallback/Guest Messages (Used if code name isn't matched)</b>"
                for (int d = 1; d <= 10; d++) {
                    input "defaultArrivalMessage_${d}", "text", title: "Default Arrival Message ${d}", required: (d == 1), defaultValue: defArr[d-1]
                }
                
                input "numLockUsers", "number", title: "Number of Manual User Slots (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true, description: "How many specific people do you want to configure manual overrides for?"
                
                if (numLockUsers > 0) {
                    def usrArr = [
                        "Welcome home, %name%.",
                        "Glad you're back, %name%.",
                        "Welcome back %name%, the house is ready.",
                        "Greetings %name%, house systems are online.",
                        "Good to see you, %name%.",
                        "Hello %name%. I've adjusted the climate for your arrival.",
                        "Welcome home %name%. All systems nominal.",
                        "It is good to have you back, %name%.",
                        "Perimeter disarmed. Welcome home, %name%.",
                        "Greetings %name%. I hope you had a pleasant time away."
                    ]
                    
                    for (int i = 1; i <= (numLockUsers as Integer); i++) {
                        paragraph "<b>User ${i} Configuration</b>"
                        
                        if (lockUsers.size() > 0) {
                            input "lockUserName_${i}", "enum", title: "User ${i} Lock Code Name", options: lockUsers, required: false, description: "Select the user from the list pulled directly from the lock."
                        } else {
                            input "lockUserName_${i}", "text", title: "User ${i} Lock Code Name", required: false, description: "Type the exact name of the code as it appears in your lock manager."
                        }
                        
                        for (int m = 1; m <= 10; m++) {
                            input "lockGreeting_${i}_${m}", "text", title: "User ${i} Welcome Message ${m}", required: (m == 1), defaultValue: usrArr[m-1]
                        }
                    }
                }
            }
        }

        section("Local Voice Zones (Rooms)", hideable: true, hidden: true) {
            paragraph "<i>Create isolated voice zones for specific rooms (like bedrooms). This allows each room to have its own Good Night and Good Morning logic without disturbing the rest of the house.</i>"
            
            input "numRooms", "number", title: "Number of Local Voice Zones (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true, description: "Select how many individual rooms you want to configure."
        }

        if (numRooms > 0) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                section("${settings["roomName_${i}"] ?: "Room Zone ${i}"}", hideable: true, hidden: true) { 
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${settings["roomName_${i}"] ?: "Room ${i}"}", description: "Tap to set up occupant names, triggers, and messages for this room.") 
                }
            }
        }
        
        section("Quiet Hours (Night Mode Audio)", hideable: true, hidden: true) {
            paragraph "<i>To prevent the system from waking the house, you can enforce a strict maximum volume during specific hours. This overrides all other volume settings (Arrivals, DND, Global, and Room volumes) during the time window.</i>"
            
            input "quietHoursStart", "time", title: "Quiet Hours Start Time", required: false, description: "When should the system lower its voice?"
            input "quietHoursEnd", "time", title: "Quiet Hours End Time", required: false, description: "When should normal volume rules resume?"
            input "quietVolume", "number", title: "Quiet Hours Maximum Volume (0-100)", required: false, description: "All voice announcements will be forcibly throttled down to this volume during Quiet Hours."
        }

        section("Advanced Features & Arrival Resets", hideable: true, hidden: true) {
            paragraph "<i>Arrival and Departure statuses automatically reset at Midnight. Use the options below to configure who resets and when.</i>"
            
            input "stayAtHomeUsers", "enum", title: "Stay At Home Users (Lock Codes)", options: lockUsers, multiple: true, required: false, description: "Select users who are home 24/7. Their arrival status will NOT reset at midnight, meaning they won't trigger a greeting the next day unless the house explicitly goes into a reset mode (like 'Away')."
            input "stayAtHomeCustom", "text", title: "Stay At Home Users (Custom Names)", required: false, description: "Comma-separated list of names (like an Admin Alias) to keep checked-in at midnight if they aren't selectable above."
            
            input "enableWakeupPad", "bool", title: "Enable Speaker Wake-Up Padding?", defaultValue: false, description: "If the first word of your announcements gets cut off on Wi-Fi speakers like Sonos, turn this ON to add a brief invisible pause before the message plays."
            
            input "resetModes", "mode", title: "Reset ALL Arrivals on Mode Change", multiple: true, required: false, description: "Select house modes (like 'Away'). When the house enters this mode, EVERYONE is instantly marked as away, overriding Stay At Home status."
            
            paragraph "<b>Scheduled User Away Checks (Work/School)</b>\n<i>Link specific users to a virtual switch (like 'Work' or 'School'). If this switch turns ON, they are marked Away. You can also set a scheduled time (like 2:00 AM) for the app to sweep these switches and reset users if their switch was left ON overnight.</i>"
            input "awayCheckTime", "time", title: "Scheduled Sweep Time", required: false, description: "Time to check if the switches below are ON (e.g., 2:00 AM)."
            
            input "numAwayMappings", "number", title: "Number of User Switch Mappings (0-5)", required: false, defaultValue: 0, range: "0..5", submitOnChange: true, description: "How many users do you want to link to specific Away/Reset switches?"
            
            if (numAwayMappings > 0) {
                for (int i = 1; i <= (numAwayMappings as Integer); i++) {
                    if (lockUsers.size() > 0) {
                        input "awayMappingUser_${i}", "enum", title: "Mapping ${i} User", options: lockUsers, required: false
                    } else {
                        input "awayMappingUser_${i}", "text", title: "Mapping ${i} User", required: false
                    }
                    input "awayMappingSwitch_${i}", "capability.switch", title: "Mapping ${i} Switch", required: false
                }
            }
            
            input "btnForceReset", "button", title: "🔄 Force Reset All Daily Statuses", description: "Tap to manually clear today's arrival and departure logs for everyone right now."
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    
    dynamicPage(name: "roomPage", title: "Room Voice Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Zone Identification & Occupant", hideable: true, hidden: false) {
            input "roomName_${rNum}", "text", title: "Custom Room Name", defaultValue: "Bedroom ${rNum}", submitOnChange: true, description: "The name of this room (used in variables as %room%)."
            input "roomOccupantName_${rNum}", "text", title: "Primary Occupant Name", defaultValue: "Guest", required: true, description: "The person who sleeps here. The app will automatically inject this name into greetings."
            
            input "roomSpeaker_${rNum}", "capability.speechSynthesis", title: "Dedicated Room Speaker", required: false, description: "Select the specific speaker inside this room. If left blank, announcements will route to the Global Indoor Speaker."
            input "roomVolumeGN_${rNum}", "number", title: "Good Night Volume (0-100)", required: false, description: "Volume level when the room goes to sleep."
            input "roomVolumeGM_${rNum}", "number", title: "Good Morning Volume (0-100)", required: false, description: "Volume level when the room wakes up."
        }
        
        section("Logic & Automations Triggers", hideable: true, hidden: true) {
            input "roomGoodNightSwitch_${rNum}", "capability.switch", title: "Room Good Night Switch", required: true, description: "Select the switch that indicates the room is going to sleep (ON = Good Night, OFF = Good Morning)."
            input "roomMotion_${rNum}", "capability.motionSensor", title: "Motion Sensor (Verified Mornings)", required: false, description: "Highly Recommended: If selected, the app waits to see motion AFTER the Good Night switch turns off before saying Good Morning."
            
            input "delayGreetingGN_${rNum}", "number", title: "Good Night Greeting Delay (Seconds)", defaultValue: 5, description: "Pause before speaking (e.g., 5 seconds) so Good Night music and room can settle."
            input "delayGreetingGM_${rNum}", "number", title: "Good Morning Greeting Delay (Seconds)", defaultValue: 30, description: "Pause briefly before speaking so morning automations and lights can execute."
        }
        
        section("Personalized Greetings", hideable: true, hidden: true) {
            paragraph "<i>Use the buttons below to instantly hear how your current settings (volume, speaker, and names) will sound in this room.</i>"
            input "btnTestGN_${rNum}", "button", title: "▶️ Test Good Night Audio"
            input "btnTestGM_${rNum}", "button", title: "▶️ Test Good Morning Audio"
            
            input "useCustomRoomMessages_${rNum}", "bool", title: "Write Custom Overrides?", defaultValue: false, submitOnChange: true, description: "Turn this ON if you want to write your own personalized messages instead of using the smart defaults."
            
            def gnDefs = [
                "Good night %name%. Sleep well.",
                "Sweet dreams %name%. The house is secure.",
                "Rest well. I am shutting down the %room%.",
                "Good night %name%. Perimeter defense is active.",
                "Sleep tight %name%.",
                "Have a peaceful night %name%. All locks are engaged.",
                "Good night %name%. I will keep watch.",
                "Rest easy %name%. Systems are entering night mode.",
                "Good night %name%. Waking protocols are set for tomorrow.",
                "Sleep well %name%. The house is now asleep."
            ]
            def gmDefs = [
                "Good morning %name%. The house is waking up.",
                "Good morning %name%. I hope you slept well in the %room%.",
                "Rise and shine %name%. Systems are online.",
                "Good morning %name%. Ready for the day.",
                "Hello %name%. The morning routine has begun.",
                "Good morning %name%. I have disarmed the night perimeter.",
                "Good morning %name%. I hope you have a productive day.",
                "Rise and shine %name%. Waking sequence complete.",
                "Good morning %name%. All night-time automations have concluded.",
                "Hello %name%. The house is ready for your morning."
            ]
            
            if (settings["useCustomRoomMessages_${rNum}"]) {
                paragraph "<i><b>Custom Mode Active:</b> Write your own messages below. Use the variables <b>%name%</b> and <b>%room%</b> to dynamically replace them with the Occupant's Name and Room Name.</i>"
                
                paragraph "<b>Good Night Messages (Triggered when switch turns ON)</b>"
                for (int m = 1; m <= 10; m++) {
                    input "gnMessage_${rNum}_${m}", "text", title: "Good Night Message ${m}", required: (m == 1), defaultValue: gnDefs[m-1]
                }
                
                paragraph "<b>Good Morning Messages (Triggered when switch turns OFF)</b>"
                for (int m = 1; m <= 10; m++) {
                    input "gmMessage_${rNum}_${m}", "text", title: "Good Morning Message ${m}", required: (m == 1), defaultValue: gmDefs[m-1]
                }
            } else {
                def defaultText = "<i><b>Smart Mode Active:</b> The system will automatically inject <b>${settings["roomOccupantName_${rNum}"] ?: "Guest"}</b> into one of the following random messages for Good Night and Good Morning.</i>"
                paragraph defaultText
                
                def gnList = gnDefs.collect { "• <i>${it}</i>" }.join("<br>")
                def gmList = gmDefs.collect { "• <i>${it}</i>" }.join("<br>")
                
                paragraph "<b>Good Night Defaults:</b><br>${gnList}"
                paragraph "<b>Good Morning Defaults:</b><br>${gmList}"
            }
        }
        
        section("Weather Integration", hideable: true, hidden: true) {
            paragraph "<i>Link your Advanced Meteorologist Report device to seamlessly append the day's forecast directly after the room's greeting finishes.</i>"
            input "roomWeatherDevice_${rNum}", "capability.actuator", title: "Meteorologist Child Device", required: false, description: "Select the child device generated by your Meteorologist App.", submitOnChange: true
            
            if (settings["roomWeatherDevice_${rNum}"]) {
                def wText = settings["roomWeatherDevice_${rNum}"].currentValue("script") ?: "Waiting for first Meteorologist sync..."
                paragraph "<div style='margin-top: 10px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px;'><b>Live Forecast Preview:</b><br><i>${wText}</i></div>"
            }
            
            input "roomWeatherGM_${rNum}", "bool", title: "Append Forecast to Good Morning", defaultValue: false
            input "roomWeatherGN_${rNum}", "bool", title: "Append Forecast to Good Night", defaultValue: false
            input "roomWeatherDelay_${rNum}", "number", title: "Pause before forecast (Seconds)", defaultValue: 3, description: "How long to wait after the 'Good Morning/Night' finishes before delivering the weather report."
        }
    }
}

def installed() {
    log.info "Advanced Voice Butler Installed."
    initialize()
}

def updated() {
    log.info "Advanced Voice Butler Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def ensureStateMaps() {
    if (state.historyLog == null) state.historyLog = []
    if (state.lastRoomGreeting == null) state.lastRoomGreeting = [:]
    if (state.hasArrivedToday == null) state.hasArrivedToday = [:]
    if (state.hasDepartedToday == null) state.hasDepartedToday = [:]
    if (state.waitingForMotion == null) state.waitingForMotion = [:]
    if (state.resetReasons == null) state.resetReasons = [:]
    if (state.globalResetReason == null) state.globalResetReason = "Awaiting First Entry"
    
    if (state.nightMotionCount == null) state.nightMotionCount = 0
    if (state.awayDoorbellCount == null) state.awayDoorbellCount = 0
    if (state.pendingMorningReport == null) state.pendingMorningReport = false
    if (state.pendingArrivalReport == null) state.pendingArrivalReport = false
    if (state.lastMode == null) state.lastMode = location.mode
    
    if (state.lastBypassDoorOpen == null) state.lastBypassDoorOpen = 0
    if (state.lastIntruderAlert == null) state.lastIntruderAlert = 0
    if (state.departureGracePeriodEnd == null) state.departureGracePeriodEnd = 0
    if (state.lastDepartureTime == null) state.lastDepartureTime = [:]
}

def initialize() {
    ensureStateMaps()
    schedule("0 0 0 * * ?", "midnightReset") 
    
    // Perimeter & DND
    if (frontDoorbell) {
        subscribe(frontDoorbell, "pushed", visitorHandler)
        subscribe(frontDoorbell, "pushed", countDoorbellHandler)
    }
    if (frontDoorMotion) {
        subscribe(frontDoorMotion, "motion.active", visitorHandler)
        subscribe(frontDoorMotion, "motion.active", countMotionHandler)
    }
    
    // Intruder Deterrent
    if (enableIntruder) {
        if (intruderMotion) subscribe(intruderMotion, "motion.active", intruderMotionHandler)
        if (intruderBypassDoors) subscribe(intruderBypassDoors, "contact.open", intruderDoorHandler)
    }
    
    // Arrival Logic
    if (frontDoorLock) subscribe(frontDoorLock, "lock.unlocked", arrivalHandler)
    
    // Departure Logic
    if (frontDoorContact) subscribe(frontDoorContact, "contact", departureHandler)
    
    // Mode Tracking for resets and Butler Reports
    subscribe(location, "mode", modeChangeHandler)
    
    // Butler Living Room Trigger
    if (butlerLrMotion) subscribe(butlerLrMotion, "motion.active", butlerLrMotionHandler)
    
    // Scheduled User Away Check
    if (awayCheckTime) {
        schedule(awayCheckTime, "scheduledAwayCheck")
    }
    
    def numMappings = settings.numAwayMappings ? settings.numAwayMappings as Integer : 0
    for (int i = 1; i <= numMappings; i++) {
        def sw = settings["awayMappingSwitch_${i}"]
        if (sw) {
            subscribe(sw, "switch.on", awaySwitchOnHandler)
        }
    }
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def gnSwitch = settings["roomGoodNightSwitch_${i}"]
        if (gnSwitch) {
            subscribe(gnSwitch, "switch.on", goodNightOnHandler)
            subscribe(gnSwitch, "switch.off", goodNightOffHandler)
        }
        
        def motionSensor = settings["roomMotion_${i}"]
        if (motionSensor) {
            subscribe(motionSensor, "motion.active", roomMotionHandler)
        }
    }
}

// --- AUDIO RESTORE & QUIET HOURS ENGINE ---
def speakWithRestore(speakerInput, msg, originalVol) {
    if (!speakerInput) return
    def speakers = speakerInput instanceof List ? speakerInput : [speakerInput]
    
    def finalVol = originalVol
    if (quietHoursStart && quietHoursEnd && quietVolume != null) {
        try {
            if (timeOfDayIsBetween(quietHoursStart, quietHoursEnd, new Date(), location.timeZone)) {
                finalVol = quietVolume as Integer
                log.debug "Quiet Hours active. Throttling volume to ${finalVol}%"
            }
        } catch(e) { 
            log.error "Quiet Hours check failed: ${e}" 
        }
    }
    
    def safeMsg = msg.replace("&", "and")
    def finalMsg = safeMsg
    if (settings.enableWakeupPad) {
        finalMsg = ", , , " + safeMsg
    }
    
    speakers.each { spk ->
        try {
            def currentVol = spk.currentValue("volume")
            def targetVol = finalVol != null ? (finalVol as Integer) : currentVol
            
            if (targetVol != null) {
                spk.setVolume(targetVol)
                pauseExecution(1000) 
            }
            
            spk.speak(finalMsg)
            pauseExecution(500) 
            
            try {
                if (spk.hasCommand("play")) {
                    spk.play()
                }
            } catch (ex) {}
            
            if (currentVol != null && targetVol != null && currentVol != targetVol) {
                def delay = Math.max(6, (finalMsg.length() / 12).toInteger() + 4)
                runIn(delay, "restoreVolumeTask", [data: [speakerId: spk.id, oldVol: currentVol], overwrite: false])
            }
        } catch (e) {
            log.error "Voice Butler Error sending to ${spk.displayName}: ${e}"
        }
    }
}

def restoreVolumeTask(data) {
    def id = data.speakerId
    def vol = data.oldVol
    def spk = getAllSpeakers().find { it.id == id }
    if (spk && vol != null) {
        spk.setVolume(vol as Integer)
    }
}

def getAllSpeakers() {
    def list = []
    if (outdoorSpeaker) list << outdoorSpeaker
    if (globalIndoorSpeaker) list.addAll(globalIndoorSpeaker)
    if (butlerLrSpeaker) list << butlerLrSpeaker
    for (int i = 1; i <= 5; i++) {
        if (settings["roomSpeaker_${i}"]) list << settings["roomSpeaker_${i}"]
    }
    return list.flatten().findAll { it != null }
}

// --- DEPARTURE LOGIC ---
def departureHandler(evt) {
    if (evt.value != "open") return
    
    ensureStateMaps()
    
    def nowTime = new Date().time
    state.lastDepartureTime["globalDoorOpen"] = nowTime
    
    log.debug "DEPARTURE TRACE: Front door opened. Checking departure profiles..."
    
    def numDep = settings.numDepartureUsers ? settings.numDepartureUsers as Integer : 0
    if (numDep == 0) {
        log.debug "DEPARTURE TRACE: No departure profiles configured."
        return
    }
    
    def now = new Date()
    def departedIndexes = []
    
    for (int i = 1; i <= numDep; i++) {
        def uName = settings["depUserName_${i}"]
        def ctxSwitch = settings["depSwitch_${i}"]
        def tStart = settings["depTimeStart_${i}"]
        def tEnd = settings["depTimeEnd_${i}"]
        def dModes = [settings["depModes_${i}"]].flatten().findAll{it}
        
        log.debug "DEPARTURE TRACE: Checking Profile ${i} (${uName})..."
        
        if (uName && ctxSwitch && tStart && tEnd) {
            if (state.hasDepartedToday[uName]) {
                log.debug "DEPARTURE TRACE: ${uName} has already departed today. Skipping."
                continue
            }
            
            def modeAllowed = (dModes.size() == 0) || dModes.contains(location.mode)
            if (!modeAllowed) {
                log.debug "DEPARTURE TRACE: Mode blocked for ${uName}. Current mode is ${location.mode}, Allowed: ${dModes}"
                continue
            }
            
            if (ctxSwitch.currentValue("switch") != "on") {
                log.debug "DEPARTURE TRACE: Context switch (${ctxSwitch.displayName}) is OFF for ${uName}. Skipping."
                continue
            }
            
            try {
                def isTimeOk = timeOfDayIsBetween(tStart, tEnd, now, location.timeZone)
                if (!isTimeOk) {
                    log.debug "DEPARTURE TRACE: Outside of allowed time window for ${uName}. Skipping."
                    continue
                }
            } catch (e) {
                log.error "DEPARTURE TRACE ERROR: Hubitat failed to parse the time window for ${uName}. Check time format. Error: ${e}"
                continue
            }

            log.debug "DEPARTURE TRACE: All conditions matched for ${uName}!"
            state.hasDepartedToday[uName] = true
            state.lastDepartureTime[uName] = nowTime
            departedIndexes << i
            
        } else {
            log.debug "DEPARTURE TRACE: Profile ${i} is missing required fields."
        }
    }
    
    if (departedIndexes.size() > 0) {
        state.departureGracePeriodEnd = nowTime + 300000 
        log.info "SYSTEM: Valid departure detected. Suppressing DND and Intruder alerts for 5 minutes."
        
        departedIndexes.each { idx ->
            def uName = settings["depUserName_${idx}"]
            def messages = []
            
            for (int m = 1; m <= 10; m++) {
                def msg = settings["depMessage_${idx}_${m}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Have a good trip %name%."]
            
            def rawMsg = messages[new Random().nextInt(messages.size())]
            def finalMsg = rawMsg.replace("%name%", uName)
            
            if (outdoorSpeaker) {
                def profileVol = settings["depVolume_${idx}"]
                def targetVolume = profileVol != null ? profileVol : (settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"])
                
                speakWithRestore(outdoorSpeaker, finalMsg, targetVolume)
                addToHistory("DEPARTURE: Contextual departure window matched for [${uName}]. Played: '${finalMsg}'")
            } else {
                addToHistory("DEPARTURE ERROR: Departure matched for [${uName}], but no Outdoor Front Door Speaker is assigned.")
            }
            
            pauseExecution(3000)
        }
    } else {
        log.debug "DEPARTURE TRACE: Door opened, but no departure profiles met all requirements."
    }
}

// --- NIGHTTIME INTRUDER DETERRENT ---
def intruderDoorHandler(evt) {
    ensureStateMaps()
    state.lastBypassDoorOpen = new Date().time
    log.info "Intruder Safety Bypass: Door opened. Pausing deterrents."
}

def intruderMotionHandler(evt) {
    ensureStateMaps()
    
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) {
        log.debug "Intruder deterrent bypassed due to active departure grace period."
        return
    }
    
    def activeModes = [settings.intruderModes].flatten().findAll{it}
    if (!activeModes.contains(location.mode)) return
    
    def isDoorOpen = false
    if (settings.intruderBypassDoors) {
        settings.intruderBypassDoors.each { door ->
            if (door.currentValue("contact") == "open") isDoorOpen = true
        }
    }
    if (isDoorOpen) {
        log.debug "Intruder deterrent bypassed: A bypass door is currently OPEN."
        return
    }
    
    def lastDoor = state.lastBypassDoorOpen ?: 0
    def bypassMins = settings.intruderBypassMinutes != null ? settings.intruderBypassMinutes.toInteger() : 5
    if ((now - lastDoor) < (bypassMins * 60000)) {
        log.debug "Intruder deterrent bypassed: A bypass door was opened in the last ${bypassMins} minutes."
        return
    }
    
    def debounceMs = (settings.intruderDebounce != null ? settings.intruderDebounce.toInteger() : 5) * 60000
    def lastAlert = state.lastIntruderAlert ?: 0
    
    if ((now - lastAlert) > debounceMs) {
        state.lastIntruderAlert = now
        state.lastOutdoorGreeting = now 
        
        if (outdoorSpeaker) {
            def messages = []
            for (int d = 1; d <= 10; d++) {
                def msg = settings["intruderMessage_${d}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Unexpected motion detected. Cameras are currently recording."]
            
            def randomMsg = messages[new Random().nextInt(messages.size())]
            def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
            
            speakWithRestore(outdoorSpeaker, randomMsg, targetVol)
            addToHistory("INTRUDER DETERRENT: Motion detected on ${evt.device.displayName}. Played: '${randomMsg}'")
        }
    } else {
        log.debug "Intruder deterrent blocked by debounce cooldown."
    }
}

// --- BUTLER EVENT TRACKING & REPORTING ---
def countDoorbellHandler(evt) {
    def awayList = [settings.butlerAwayModes].flatten().findAll{it}
    if (awayList.contains(location.mode)) {
        state.awayDoorbellCount = (state.awayDoorbellCount ?: 0) + 1
        addToHistory("BUTLER: Doorbell rang while Away. Count: ${state.awayDoorbellCount}")
    }
}

def countMotionHandler(evt) {
    def nightList = [settings.butlerNightModes].flatten().findAll{it}
    if (nightList.contains(location.mode)) {
        state.nightMotionCount = (state.nightMotionCount ?: 0) + 1
        addToHistory("BUTLER: Porch motion detected while asleep. Count: ${state.nightMotionCount}")
    }
}

def butlerLrMotionHandler(evt) {
    ensureStateMaps()
    if (evt.value == "active") {
        def arrModes = [settings.butlerArrivalModes].flatten().findAll{it}
        def mornModes = [settings.butlerMorningModes].flatten().findAll{it}
        
        if (state.pendingArrivalReport && (!arrModes || arrModes.contains(location.mode))) {
            state.pendingArrivalReport = false 
            def delay = settings.butlerReportDelay != null ? settings.butlerReportDelay.toInteger() : 120
            addToHistory("SYSTEM: Living room motion detected in allowed mode. Triggering Arrival Report in ${delay} seconds.")
            runIn(delay, "playButlerReport", [data: [type: "Arrival", count: state.awayDoorbellCount], overwrite: true])
        }
        
        if (state.pendingMorningReport && (!mornModes || mornModes.contains(location.mode))) {
            state.pendingMorningReport = false 
            def delay = settings.butlerReportDelay != null ? settings.butlerReportDelay.toInteger() : 120
            addToHistory("SYSTEM: Living room motion detected in allowed mode. Triggering Morning Report in ${delay} seconds.")
            runIn(delay, "playButlerReport", [data: [type: "Morning", count: state.nightMotionCount], overwrite: true])
        }
    }
}

def playButlerReport(data) {
    def type = data.type
    def count = data.count
    def msg = ""
    
    if (type == "Arrival") {
        msg = "Pardon the interruption, but there were ${count} doorbell rings while you were away. Please check the cameras."
        state.awayDoorbellCount = 0 
    } else {
        msg = "Good morning. There were ${count} motion events at the front door last night. Please check the cameras."
        state.nightMotionCount = 0 
    }
    
    def targetSpeaker = settings.butlerLrSpeaker ?: globalIndoorSpeaker
    def targetVol = settings.butlerLrVolume ?: globalVolume
    
    if (targetSpeaker) {
        speakWithRestore(targetSpeaker, msg, targetVol)
        addToHistory("BUTLER REPORT: Delivered ${type} report. Played: '${msg}'")
    } else {
        addToHistory("BUTLER ERROR: Tried to deliver ${type} report, but no Living Room or Global speaker is assigned.")
    }
}

// --- FRONT DOOR DND LOGIC ---
def visitorHandler(evt) {
    ensureStateMaps()
    
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) {
        log.debug "DND Intercept bypassed due to active departure grace period."
        return
    }
    
    def dndModesList = [settings.dndModes].flatten().findAll{it}
    def isDndActive = (dndSwitch?.currentValue("switch") == "on") || dndModesList.contains(location.mode)
    
    if (isDndActive) {
        def lastGreet = state.lastOutdoorGreeting ?: 0
        def isMotion = evt.name == "motion"
        
        def debounceMs = 30000 
        if (isMotion) {
            def debounceMins = settings.dndMotionDebounce != null ? settings.dndMotionDebounce.toInteger() : 10
            debounceMs = debounceMins * 60000
        }
        
        if ((now - lastGreet) > debounceMs) {
            if (outdoorSpeaker) {
                def messages = []
                for (int d = 1; d <= 10; d++) {
                    def msg = settings["dndMessage_${d}"]
                    if (msg) messages << msg
                }
                if (!messages) messages = ["We cannot come to the door right now. The camera is recording, please leave your message."]
                def randomMsg = messages[new Random().nextInt(messages.size())]
                
                speakWithRestore(outdoorSpeaker, randomMsg, outdoorVolume)
                state.lastOutdoorGreeting = now 
                
                def triggerType = isMotion ? "Motion" : "Doorbell"
                addToHistory("PERIMETER GUARD: Visitor detected (${triggerType}) while DND is active. Played: '${randomMsg}'")
            } else {
                addToHistory("PERIMETER ERROR: Visitor detected while DND is active, but no outdoor speaker is assigned.")
            }
        } else {
            log.debug "DND Intercept blocked by debounce cooldown (${isMotion ? 'motion' : 'doorbell'})."
        }
    }
}

// --- ARRIVAL & RESET LOGIC ---
def arrivalHandler(evt) {
    ensureStateMaps()
    
    def desc = evt.descriptionText ?: ""
    def dataStr = evt.data ?: ""
    def actualUserName = "Guest"
    def trackingKey = "global"
    def isKeypadUnlock = false
    
    try {
        if (evt.data) {
            def parsedData = new groovy.json.JsonSlurper().parseText(evt.data)
            if (parsedData?.codeName) {
                actualUserName = parsedData.codeName
                trackingKey = actualUserName
                isKeypadUnlock = true
            }
        }
    } catch (e) {}

    if (!isKeypadUnlock && desc.toLowerCase().contains("unlocked by")) {
        def match = desc =~ /unlocked by (.*)/
        if (match) {
            actualUserName = match[0][1].trim()
            trackingKey = actualUserName
            isKeypadUnlock = true
        }
    } else if (desc.toLowerCase().contains("code") || desc.toLowerCase().contains("keypad")) {
        isKeypadUnlock = true
    }
    
    def originalCodeName = actualUserName
    
    def ignoredList = [settings.ignoredCodes].flatten().findAll{it}
    if (settings.ignoredCustomCodes) {
        ignoredList += settings.ignoredCustomCodes.split(',').collect{ it.trim() }
    }
    def lowerIgnored = ignoredList.collect { it.toLowerCase() }
    
    if (lowerIgnored.contains(originalCodeName.toLowerCase()) || originalCodeName.toLowerCase().contains("ghost")) {
        log.info "IGNORED/GHOST CODE DETECTED: [${originalCodeName}]. Bypassing all arrival logic."
        return
    }
    
    if (actualUserName.toLowerCase() == "admin code" && settings.adminUserAlias) {
        actualUserName = settings.adminUserAlias
        trackingKey = actualUserName
    }
    
    if (arrivalMode == "Automatic (Reads lock memory)") {
        def tracked = settings.trackedLockCodes ?: []
        if (!tracked.contains(originalCodeName)) {
            log.info "Code [${originalCodeName}] is not mapped in trackedLockCodes. Ignoring."
            return
        }
    }
    
    def matchedUserIdx = null
    
    if (arrivalMode == "Manual (Assign names to slots)" && numLockUsers) {
        for (int i = 1; i <= (numLockUsers as Integer); i++) {
            def uName = settings["lockUserName_${i}"]
            if (uName && (actualUserName.toLowerCase() == uName.toLowerCase() || desc.toLowerCase().contains(uName.toLowerCase()) || dataStr.toLowerCase().contains(uName.toLowerCase()))) {
                matchedUserIdx = i
                trackingKey = uName
                actualUserName = uName
                isKeypadUnlock = true 
                break
            }
        }
    }
    
    if (!isKeypadUnlock) {
        if (disableGlobalAnnouncements) {
            log.debug "Global Arrivals disabled. Ignoring manual key/thumb turn unlock."
            return
        }
        trackingKey = "global"
        actualUserName = "Guest"
    } else if (trackingKey == "global" && disableGlobalAnnouncements) {
         log.debug "Global Arrivals disabled. Ignoring generic code without a name."
         return
    }
    
    if (!state.hasArrivedToday[trackingKey]) {
        def nowTime = new Date().time
        def graceMins = settings.quickReturnGrace != null ? settings.quickReturnGrace.toInteger() : 5
        def graceMs = graceMins * 60000
        
        def lastDoor = state.lastDepartureTime["globalDoorOpen"] ?: 0
        def lastDepUser = state.lastDepartureTime[trackingKey] ?: 0
        
        if ((nowTime - lastDoor < graceMs) || (nowTime - lastDepUser < graceMs)) {
            state.hasArrivedToday[trackingKey] = true 
            log.info "SYSTEM: Quick return / Forgotten item detected for [${trackingKey}]. Bypassing arrival audio."
            addToHistory("ARRIVAL: Quick return detected for [${trackingKey}]. Silently marked as Arrived.")
            return
        }
        
        state.hasArrivedToday[trackingKey] = true
        state.lastOutdoorGreeting = new Date().time
        
        def messages = []
        if (arrivalMode == "Automatic (Reads lock memory)") {
            for (int m = 1; m <= 10; m++) {
                def msg = settings["autoGreeting_${m}"]
                if (msg) messages << msg
            }
        } else {
            if (matchedUserIdx) {
                for (int m = 1; m <= 10; m++) {
                    def msg = settings["lockGreeting_${matchedUserIdx}_${m}"]
                    if (msg) messages << msg
                }
            } else {
                for (int m = 1; m <= 10; m++) {
                    def msg = settings["defaultArrivalMessage_${m}"]
                    if (msg) messages << msg
                }
            }
        }
        
        if (!messages) messages = ["Welcome home %name%."] 
        def rawMsg = messages[new Random().nextInt(messages.size())]
        def greetingToPlay = rawMsg.replace("%name%", actualUserName)
        
        def targetVolume = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        
        if (outdoorSpeaker) {
            speakWithRestore(outdoorSpeaker, greetingToPlay, targetVolume)
            addToHistory("ARRIVAL: First arrival detected for [${trackingKey}]. Played on Front Door Speaker: '${greetingToPlay}'")
        } else {
            addToHistory("ARRIVAL ERROR: First arrival detected for [${trackingKey}], but no Outdoor Front Door Speaker is assigned.")
        }
    }
}

def modeChangeHandler(evt) {
    ensureStateMaps()
    def newMode = evt.value
    
    def resetModesList = [settings.resetModes].flatten().findAll{it}
    if (resetModesList.contains(newMode)) {
        state.hasArrivedToday = [:]
        state.resetReasons = [:]
        state.globalResetReason = "Reset by Mode Change (${newMode})"
        addToHistory("SYSTEM: House mode changed to [${newMode}]. All First Arrival statuses cleared.")
    }
    
    def awayList = [settings.butlerAwayModes].flatten().findAll{it}
    def nightList = [settings.butlerNightModes].flatten().findAll{it}
    
    if (state.lastMode in awayList && !(newMode in awayList)) {
        if (state.awayDoorbellCount > 0) {
            state.pendingArrivalReport = true
            addToHistory("SYSTEM: Left Away mode. Armed Butler to deliver Arrival Report on next Living Room motion.")
        }
    }
    if (newMode in awayList) {
        state.awayDoorbellCount = 0
        state.pendingArrivalReport = false
    }
    
    if (state.lastMode in nightList && !(newMode in nightList)) {
        if (state.nightMotionCount > 0) {
            state.pendingMorningReport = true
            addToHistory("SYSTEM: Left Night mode. Armed Butler to deliver Morning Report on next Living Room motion.")
        }
    }
    if (newMode in nightList) {
        state.nightMotionCount = 0
        state.pendingMorningReport = false
    }
    
    state.lastMode = newMode
}

def awaySwitchOnHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    def numMappings = settings.numAwayMappings ? settings.numAwayMappings as Integer : 0
    
    for (int i = 1; i <= numMappings; i++) {
        if (settings["awayMappingSwitch_${i}"]?.id == deviceId) {
            def uName = settings["awayMappingUser_${i}"]
            if (uName && state.hasArrivedToday[uName]) {
                state.hasArrivedToday.remove(uName)
                state.resetReasons[uName] = "Away Switch Turned ON"
                addToHistory("SYSTEM: Away Switch (${evt.device.displayName}) turned ON for [${uName}]. Arrival status cleared.")
            }
        }
    }
}

def scheduledAwayCheck() {
    ensureStateMaps()
    def numMappings = settings.numAwayMappings ? settings.numAwayMappings as Integer : 0
    
    for (int i = 1; i <= numMappings; i++) {
        def sw = settings["awayMappingSwitch_${i}"]
        if (sw && sw.currentValue("switch") == "on") {
            def uName = settings["awayMappingUser_${i}"]
            if (uName && state.hasArrivedToday[uName]) {
                state.hasArrivedToday.remove(uName)
                state.resetReasons[uName] = "Away Switch ON (Scheduled Check)"
                addToHistory("SYSTEM: Scheduled check found Away Switch (${sw.displayName}) ON for [${uName}]. Arrival status cleared.")
            }
        }
    }
}

// --- ROOM STATE LOGIC & WEATHER ROUTING ---
def goodNightOnHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == deviceId) {
            state.waitingForMotion["${i}"] = false 
            
            def delaySec = settings["delayGreetingGN_${i}"] != null ? settings["delayGreetingGN_${i}"].toInteger() : 5
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            def occName = settings["roomOccupantName_${i}"] ?: "Guest"
            
            def rawMsg = ""
            if (settings["useCustomRoomMessages_${i}"]) {
                def messages = []
                for (int m = 1; m <= 10; m++) {
                    def msg = settings["gnMessage_${i}_${m}"]
                    if (msg) messages << msg
                }
                if (!messages) messages = ["Good night %name%."]
                rawMsg = messages[new Random().nextInt(messages.size())]
            } else {
                def defaults = [
                    "Good night %name%. Sleep well.",
                    "Sweet dreams %name%. The house is secure.",
                    "Rest well. I am shutting down the %room%.",
                    "Good night %name%. Perimeter defense is active.",
                    "Sleep tight %name%.",
                    "Have a peaceful night %name%. All locks are engaged.",
                    "Good night %name%. I will keep watch.",
                    "Rest easy %name%. Systems are entering night mode.",
                    "Good night %name%. Waking protocols are set for tomorrow.",
                    "Sleep well %name%. The house is now asleep."
                ]
                rawMsg = defaults[new Random().nextInt(defaults.size())]
            }
            
            def finalMsg = rawMsg.replace("%name%", occName).replace("%room%", rName)
            runIn(delaySec, "playRoomGreeting", [data: [roomNum: i, message: finalMsg, type: "Good Night", roomName: rName], overwrite: false])
            
            // WEATHER APPEND LOGIC
            def wDevice = settings["roomWeatherDevice_${i}"]
            if (wDevice && settings["roomWeatherGN_${i}"]) {
                def wText = wDevice.currentValue("script")
                if (wText) {
                    def greetDur = Math.max(3, (finalMsg.length() / 12).toInteger())
                    def wPause = settings["roomWeatherDelay_${i}"] != null ? settings["roomWeatherDelay_${i}"].toInteger() : 3
                    runIn(delaySec + greetDur + wPause, "playRoomWeather", [data: [roomNum: i, message: wText, type: "Good Night Weather", roomName: rName], overwrite: false])
                }
            }
            
            return 
        }
    }
}

def goodNightOffHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == deviceId) {
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            
            if (settings["roomMotion_${i}"]) {
                state.waitingForMotion["${i}"] = true
                addToHistory("SYSTEM: Good Night switch OFF in ${rName}. Armed and waiting for motion.")
                return
            } else {
                triggerGoodMorningSequence(i)
                return
            }
        }
    }
}

def roomMotionHandler(evt) {
    ensureStateMaps()
    if (evt.value == "active") {
        def deviceId = evt.device.id
        for (int i = 1; i <= (numRooms as Integer); i++) {
            if (settings["roomMotion_${i}"]?.id == deviceId && state.waitingForMotion["${i}"]) {
                state.waitingForMotion["${i}"] = false
                triggerGoodMorningSequence(i)
            }
        }
    }
}

def triggerGoodMorningSequence(int i) {
    def delaySec = settings["delayGreetingGM_${i}"] != null ? settings["delayGreetingGM_${i}"].toInteger() : 30
    def rName = settings["roomName_${i}"] ?: "Room ${i}"
    def occName = settings["roomOccupantName_${i}"] ?: "Guest"
    
    def rawMsg = ""
    if (settings["useCustomRoomMessages_${i}"]) {
        def messages = []
        for (int m = 1; m <= 10; m++) {
            def msg = settings["gmMessage_${i}_${m}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Good morning %name%."]
        rawMsg = messages[new Random().nextInt(messages.size())]
    } else {
        def defaults = [
            "Good morning %name%. The house is waking up.",
            "Good morning %name%. I hope you slept well in the %room%.",
            "Rise and shine %name%. Systems are online.",
            "Good morning %name%. Ready for the day.",
            "Hello %name%. The morning routine has begun.",
            "Good morning %name%. I have disarmed the night perimeter.",
            "Good morning %name%. I hope you have a productive day.",
            "Rise and shine %name%. Waking sequence complete.",
            "Good morning %name%. All night-time automations have concluded.",
            "Hello %name%. The house is ready for your morning."
        ]
        rawMsg = defaults[new Random().nextInt(defaults.size())]
    }
    
    def finalMsg = rawMsg.replace("%name%", occName).replace("%room%", rName)
    runIn(delaySec, "playRoomGreeting", [data: [roomNum: i, message: finalMsg, type: "Good Morning", roomName: rName], overwrite: false])
    
    // WEATHER APPEND LOGIC
    def wDevice = settings["roomWeatherDevice_${i}"]
    if (wDevice && settings["roomWeatherGM_${i}"]) {
        def wText = wDevice.currentValue("script")
        if (wText) {
            def greetDur = Math.max(3, (finalMsg.length() / 12).toInteger())
            def wPause = settings["roomWeatherDelay_${i}"] != null ? settings["roomWeatherDelay_${i}"].toInteger() : 3
            runIn(delaySec + greetDur + wPause, "playRoomWeather", [data: [roomNum: i, message: wText, type: "Good Morning Weather", roomName: rName], overwrite: false])
        }
    }
}

def playRoomGreeting(data) {
    def rNum = data.roomNum
    def msg = data.message
    def rName = data.roomName
    def type = data.type
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    
    def volSetting = type == "Good Night" ? settings["roomVolumeGN_${rNum}"] : settings["roomVolumeGM_${rNum}"]
    def targetVol = volSetting != null ? volSetting : settings["roomVolume_${rNum}"]
    
    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume 
    }
    
    if (targetSpeaker) {
        speakWithRestore(targetSpeaker, msg, targetVol)
        state.lastRoomGreeting["${rNum}"] = new Date().time
        addToHistory("ROOM GREETING: ${type} sequence triggered in ${rName}. Played: '${msg}'")
    }
}

def playRoomWeather(data) {
    def rNum = data.roomNum
    def msg = data.message
    def rName = data.roomName
    def type = data.type
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    
    def volSetting = type.contains("Good Night") ? settings["roomVolumeGN_${rNum}"] : settings["roomVolumeGM_${rNum}"]
    def targetVol = volSetting != null ? volSetting : settings["roomVolume_${rNum}"]
    
    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume 
    }
    
    if (targetSpeaker) {
        speakWithRestore(targetSpeaker, msg, targetVol)
        addToHistory("ROOM WEATHER: Appended forecast played in ${rName}. Played: '${msg}'")
    }
}

def testRoomGreeting(int i, String type) {
    def rName = settings["roomName_${i}"] ?: "Room ${i}"
    def occName = settings["roomOccupantName_${i}"] ?: "Guest"
    def rawMsg = ""

    if (type == "Good Night") {
        if (settings["useCustomRoomMessages_${i}"]) {
            def messages = []
            for (int m = 1; m <= 10; m++) {
                def msg = settings["gnMessage_${i}_${m}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Good night %name%."]
            rawMsg = messages[new Random().nextInt(messages.size())]
        } else {
            def defaults = [
                "Good night %name%. Sleep well.",
                "Sweet dreams %name%. The house is secure.",
                "Rest well. I am shutting down the %room%.",
                "Good night %name%. Perimeter defense is active.",
                "Sleep tight %name%.",
                "Have a peaceful night %name%. All locks are engaged.",
                "Good night %name%. I will keep watch.",
                "Rest easy %name%. Systems are entering night mode.",
                "Good night %name%. Waking protocols are set for tomorrow.",
                "Sleep well %name%. The house is now asleep."
            ]
            rawMsg = defaults[new Random().nextInt(defaults.size())]
        }
    } else {
        if (settings["useCustomRoomMessages_${i}"]) {
            def messages = []
            for (int m = 1; m <= 10; m++) {
                def msg = settings["gmMessage_${i}_${m}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Good morning %name%."]
            rawMsg = messages[new Random().nextInt(messages.size())]
        } else {
            def defaults = [
                "Good morning %name%. The house is waking up.",
                "Good morning %name%. I hope you slept well in the %room%.",
                "Rise and shine %name%. Systems are online.",
                "Good morning %name%. Ready for the day.",
                "Hello %name%. The morning routine has begun.",
                "Good morning %name%. I have disarmed the night perimeter.",
                "Good morning %name%. I hope you have a productive day.",
                "Rise and shine %name%. Waking sequence complete.",
                "Good morning %name%. All night-time automations have concluded.",
                "Hello %name%. The house is ready for your morning."
            ]
            rawMsg = defaults[new Random().nextInt(defaults.size())]
        }
    }

    def finalMsg = rawMsg.replace("%name%", occName).replace("%room%", rName)
    def targetSpeaker = settings["roomSpeaker_${i}"]
    
    def volSetting = type == "Good Night" ? settings["roomVolumeGN_${i}"] : settings["roomVolumeGM_${i}"]
    def targetVol = volSetting != null ? volSetting : settings["roomVolume_${i}"]

    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume
    }

    if (targetSpeaker) {
        log.info "TESTING ${type} GREETING FOR ${rName}: '${finalMsg}' at ${targetVol}% volume."
        speakWithRestore(targetSpeaker, finalMsg, targetVol)
        
        // Test Weather Append if enabled
        def wDevice = settings["roomWeatherDevice_${i}"]
        def appendWeather = type == "Good Night" ? settings["roomWeatherGN_${i}"] : settings["roomWeatherGM_${i}"]
        
        if (wDevice && appendWeather) {
            def wText = wDevice.currentValue("script") ?: "This is a test of your Meteorologist weather forecast."
            def greetDur = Math.max(3, (finalMsg.length() / 12).toInteger())
            def wPause = settings["roomWeatherDelay_${i}"] != null ? settings["roomWeatherDelay_${i}"].toInteger() : 3
            
            log.info "TESTING WEATHER APPEND FOR ${rName}: Forecast will play in ${greetDur + wPause} seconds."
            runIn(greetDur + wPause, "playRoomWeather", [data: [roomNum: i, message: wText, type: "${type} Weather", roomName: rName], overwrite: false])
        }
    } else {
        log.warn "Cannot test greeting for ${rName} - no speaker assigned."
    }
}

def testDndGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 10; d++) {
            def msg = settings["dndMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["We cannot come to the door right now. The camera is recording, please leave your message."]
        def randomMsg = messages[new Random().nextInt(messages.size())]
        
        log.info "TESTING DND GREETING: '${randomMsg}' at ${outdoorVolume}% volume."
        speakWithRestore(outdoorSpeaker, randomMsg, outdoorVolume)
    } else {
        log.warn "Cannot test DND greeting - no outdoor speaker assigned."
    }
}

def testArrivalGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        if (arrivalMode == "Automatic (Reads lock memory)") {
            for (int m = 1; m <= 10; m++) {
                def msg = settings["autoGreeting_${m}"]
                if (msg) messages << msg
            }
        } else {
            for (int m = 1; m <= 10; m++) {
                def msg = settings["defaultArrivalMessage_${m}"]
                if (msg) messages << msg
            }
        }
        if (!messages) messages = ["Welcome home %name%."]
        def rawMsg = messages[new Random().nextInt(messages.size())]
        
        def testName = settings.adminUserAlias ?: "Test User"
        def greetingToPlay = rawMsg.replace("%name%", testName)
        def targetVolume = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        
        log.info "TESTING ARRIVAL GREETING: '${greetingToPlay}' at ${targetVolume}% volume."
        speakWithRestore(outdoorSpeaker, greetingToPlay, targetVolume)
    } else {
        log.warn "Cannot test Arrival greeting - no outdoor speaker assigned."
    }
}

def testIntruderGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 10; d++) {
            def msg = settings["intruderMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Unexpected motion detected. Cameras are currently recording."]
        def randomMsg = messages[new Random().nextInt(messages.size())]
        
        def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
        log.info "TESTING INTRUDER GREETING: '${randomMsg}' at ${targetVol}% volume."
        speakWithRestore(outdoorSpeaker, randomMsg, targetVol)
    } else {
        log.warn "Cannot test Intruder greeting - no outdoor speaker assigned."
    }
}


// --- HELPERS ---
def midnightReset() {
    ensureStateMaps()
    
    // Gather all explicitly "Stay at Home" users
    def stayHomeList = [settings.stayAtHomeUsers].flatten().findAll{it}
    if (settings.stayAtHomeCustom) {
        stayHomeList += settings.stayAtHomeCustom.split(',').collect{ it.trim() }
    }
    
    def newHasArrived = [:]
    def newResetReasons = [:]
    
    // Check if the stay at home user has already arrived, keep their status true
    stayHomeList.each { u ->
        if (state.hasArrivedToday[u]) {
            newHasArrived[u] = true
            newResetReasons[u] = "Stayed Home (Carried Over)"
        }
    }
    
    state.hasArrivedToday = newHasArrived
    state.hasDepartedToday = [:] 
    state.resetReasons = newResetReasons
    state.globalResetReason = "Reset by Midnight Routine"
    
    addToHistory("SYSTEM: Midnight Reset completed. Daily Arrival and Departure statuses cleared.")
}

def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn == "btnForceReset") {
        state.hasArrivedToday = [:]
        state.hasDepartedToday = [:]
        state.resetReasons = [:]
        state.globalResetReason = "Reset manually via Dashboard"
        addToHistory("SYSTEM: Manual reset of all Daily Arrival and Departure statuses triggered.")
    } else if (btn == "btnRefresh") {
        log.info "Voice Butler Refresh Triggered."
    } else if (btn.startsWith("btnTestGN_")) {
        def rNum = btn.split("_")[1].toInteger()
        testRoomGreeting(rNum, "Good Night")
    } else if (btn.startsWith("btnTestGM_")) {
        def rNum = btn.split("_")[1].toInteger()
        testRoomGreeting(rNum, "Good Morning")
    } else if (btn == "btnTestDND") {
        testDndGreeting()
    } else if (btn == "btnTestArrival") {
        testArrivalGreeting()
    } else if (btn == "btnTestIntruder") {
        testIntruderGreeting()
    } else if (btn == "btnTestMorningReport") {
        playButlerReport([type: "Morning", count: 3])
    } else if (btn == "btnTestArrivalReport") {
        playButlerReport([type: "Arrival", count: 2])
    }
}

def addToHistory(String msg) {
    ensureStateMaps()
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
    log.info "VOICE HISTORY: [${timestamp}] ${msg}"
}
