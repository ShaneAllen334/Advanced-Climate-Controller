/**
 * Advanced Voice Butler
 *
 * Version 1.1
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Voice Butler",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Decoupled TTS orchestrator with Dynamic Priority Queueing, contextual greetings, and Do Not Disturb perimeter guarding.",
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

        section("Live System Dashboard", hideable: true, hidden: false) {
            paragraph "<i>Welcome to the Voice Butler command center. Below is a real-time read-only view of your perimeter status, active voice zones, and today's arrival/departure log.</i>"
            input "btnRefresh", "button", title: "🔄 Refresh Data Dashboard"
            input "btnQuickSave", "button", title: "💾 Quick Save / Refresh Page", description: "Use this to save your current settings without leaving the app page."
            
            def dndModesList = [settings.dndModes].flatten().findAll{it}
            def isDndMode = dndModesList.contains(location.mode)
            def isDndSwitch = dndSwitch?.currentValue("switch") == "on"
            
            def isMasterOff = masterSwitch?.currentValue("switch") == "off"
            def isGuestMode = guestModeSwitch?.currentValue("switch") == "on"
            
            def systemState = ""
            if (isMasterOff) {
                systemState = "<span style='color: #c0392b; font-weight: bold;'>MUTED (Master Switch OFF)</span>"
            } else if (isGuestMode) {
                systemState = "<span style='color: #f39c12; font-weight: bold;'>SILENT (Guest Mode ON)</span>"
            } else {
                systemState = "<span style='color: #27ae60; font-weight: bold;'>ACTIVE (Audio Enabled)</span>"
            }

            def dndState = (isDndSwitch || isDndMode) ? "<span style='color: #c0392b; font-weight: bold;'>ACTIVE (Do Not Disturb)</span>" : "<span style='color: #27ae60; font-weight: bold;'>STANDBY (Accepting Visitors)</span>"
            def inetStatus = (!settings.enableInternetCheck || state.internetActive != false) ? "<span style='color: #27ae60; font-weight: bold;'>ONLINE</span>" : "<span style='color: #c0392b; font-weight: bold;'>OFFLINE (TTS Suppressed)</span>"
            def queueStatus = state.ttsQueue?.size() > 0 ? "<span style='color: #d35400; font-weight: bold;'>${state.ttsQueue.size()} Messages Queued</span>" : "<span style='color: #27ae60;'>Idle</span>"
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>System State:</b> ${systemState}<br>"
            statusText += "<b>Perimeter Status:</b> ${dndState}<br>"
            statusText += "<b>Internet Connection:</b> ${inetStatus}<br>"
            statusText += "<b>TTS Queue Engine:</b> ${queueStatus}</div>"
            
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
                        def sickSw = settings["depSickSwitch_${i}"]
                        def swState = sw ? sw.currentValue("switch") : "N/A"
                        def sickSwState = sickSw?.currentValue("switch") == "on" ? " <span style='color: #c0392b;'>(SICK BYPASS ON)</span>" : ""
                        def swFmt = swState == "on" ? "<span style='color: #27ae60; font-weight: bold;'>ON</span>" : "<span style='color: #7f8c8d;'>OFF</span>"
                        def hasDeparted = state.hasDepartedToday ? state.hasDepartedToday[dName] : false
                        def depStatus = hasDeparted ? "<span style='color: #27ae60; font-weight: bold;'>Departed</span>" : "<span style='color: #7f8c8d;'>Pending/Inactive</span>"
                        
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${dName}</b></td><td style='padding: 8px;'>${sw?.displayName ?: 'None'} (${swFmt})${sickSwState}</td><td style='padding: 8px;'>${depStatus}</td></tr>"
                    }
                }
                statusText += "</table>"
            }
            
            paragraph statusText
        }

        section("System Event History", hideable: true, hidden: false) {
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
        
        section("1. Global System Control & Audio Hardware", hideable: true, hidden: false) {
            paragraph "<i>Configure your master controls and primary speakers here. These act as the defaults for the entire system unless a specific room or sequence overrides them.</i>"
            
            input "masterSwitch", "capability.switch", title: "Master Enable/Pause Switch", required: false, description: "If selected, turning this switch OFF will globally mute all Voice Butler announcements."
            input "guestModeSwitch", "capability.switch", title: "Guest Mode Mute Switch", required: false, description: "If selected, turning this switch ON will globally mute all Voice Butler announcements. Background tracking will continue silently."
            
            paragraph "<hr>"
            
            input "globalIndoorSpeaker", "capability.speechSynthesis", title: "Global Indoor Speaker(s)", multiple: true, required: false, description: "Select the main speaker(s) in central areas (like a Living Room or Kitchen)."
            input "globalVolume", "number", title: "Global Speaker Volume (0-100)", required: false, description: "The baseline volume for general house announcements."
            input "globalTVSwitch", "capability.switch", title: "Global Entertainment / TV Switch", required: false, description: "If this TV switch is ON, routine announcements on the Global Speaker will be gracefully muted to avoid interrupting your show."
            
            input "butlerName", "text", title: "Your Butler's Name", required: false, defaultValue: "Alfred", description: "Give your concierge a name! Use %butler% in any custom messages, or let the smart defaults use it occasionally."
            input "btnTestGlobal", "button", title: "▶️ Test Global Indoor Speaker"
            
            paragraph "<hr>"
            
            input "outdoorSpeaker", "capability.speechSynthesis", title: "Outdoor/Porch Speaker", required: false, description: "Select the speaker located outside. Used for DND deterrents, Welcome Home greetings, and Departures."
            input "outdoorVolume", "number", title: "Default Outdoor Volume (0-100)", required: false, description: "The app will override the speaker to this volume level before speaking outside."
            input "btnTestOutdoor", "button", title: "▶️ Test Outdoor Speaker"
            
            paragraph "<hr>"
            
            input "enableWakeupPad", "bool", title: "Enable Speaker Wake-Up Padding?", defaultValue: false, description: "Adds a brief invisible pause before messages to prevent the first word from cutting off on Wi-Fi speakers."
            input "enableInternetCheck", "bool", title: "Enable Internet Connection Safety?", defaultValue: true, description: "Prevents TTS and external calls from filling your logs with errors during internet outages."
        }

        section("Arrival Greetings & Smart Locks", hideable: true, hidden: false) {
            paragraph "<i>When someone unlocks the front door, the app checks if they have arrived today. If it's their first arrival, it plays a personalized greeting instantly on the Outdoor Speaker. You can use <b>%time%</b>, <b>%date%</b>, and <b>%butler%</b> in any of these messages!</i>"
            
            input "frontDoorLock", "capability.lock", title: "Front Door Smart Lock", required: false, submitOnChange: true, description: "Select the smart lock the app should monitor for entry events."
            input "arrivalVolume", "number", title: "Welcome Home Announcement Volume (0-100)", required: false, description: "Dedicated volume setpoint just for Arrival greetings. If left blank, it defaults to the 'Outdoor Volume' setting."
            
            input "arrivalIndoorSpeaker", "bool", title: "Play Arrival Greeting Indoors Too?", defaultValue: false, submitOnChange: true, description: "If enabled, the welcome message will play on the Global Indoor Speaker as well, catching you as you walk inside."
            if (arrivalIndoorSpeaker) {
                input "arrivalIndoorVolume", "number", title: "Indoor Arrival Volume (0-100)", required: false, description: "Dedicated volume for the indoor arrival greeting. If left blank, it defaults to the Global Speaker Volume."
            }
            
            input "enableHouseRoster", "bool", title: "Enable House Roster Briefing?", defaultValue: false, submitOnChange: true, description: "The Butler will announce who else is currently home when someone arrives."
            if (enableHouseRoster) {
                input "rosterAllowedUsers", "enum", title: "Limit Roster to Specific Users", options: lockUsers, multiple: true, required: false, description: "Only these users will hear the roster briefing. Leave blank to allow everyone to hear it."
                input "rosterAllowedCustom", "text", title: "Limit Roster (Custom Names)", required: false, description: "Comma-separated list (e.g., Admin Code)."
            }
            
            input "quickReturnGrace", "number", title: "Forgotten Item Grace Period (Minutes)", defaultValue: 5, required: false, description: "If a user unlocks the door within this many minutes of THEIR specific departure, the system will silently welcome them back without a full greeting."
            
            input "enableMailCheck", "bool", title: "Enable Mail Delivery Announcements?", defaultValue: false, submitOnChange: true, description: "If enabled, the Butler will ask arriving users to retrieve the mail if the switch below is ON."
            if (enableMailCheck) {
                input "mailSwitch", "capability.switch", title: "Virtual Mail Switch", required: true, description: "Select the switch that turns ON when the mailbox is opened."
                input "mailAllowedUsers", "enum", title: "Limit Mail Alert to Specific Users", options: lockUsers, multiple: true, required: false, description: "Only these users will hear the mail alert. Leave blank to allow everyone to hear it."
                input "mailAllowedCustom", "text", title: "Limit Mail Alert (Custom Names)", required: false, description: "Comma-separated list (e.g., Admin Code)."
            }
            
            input "enableExtendedAbsence", "bool", title: "Enable Extended Absence (Vacation) Greetings?", defaultValue: false, submitOnChange: true, description: "Play a special welcome back message if the user has been gone for several days."
            if (enableExtendedAbsence) {
                input "extendedAbsenceHours", "number", title: "Absence Threshold (Hours)", defaultValue: 48, required: false, description: "How many hours must they be away to trigger the vacation greeting?"
                
                paragraph "<b>Extended Absence Messages (Randomized)</b>"
                def extDefs = [
                    "Welcome back from your trip, %name%. I kept the perimeter secure while you were away.",
                    "Welcome home %name%. I hope you had a wonderful time away.",
                    "It is great to see you again %name%. %butler% missed you.",
                    "Welcome back %name%. House systems are fully operational.",
                    "Hello %name%. I hope your extended time away was pleasant."
                ]
                for (int m = 1; m <= 5; m++) {
                    input "extAbsenceMessage_${m}", "text", title: "Extended Message ${m}", required: false, defaultValue: extDefs[m-1]
                }
            }
            
            paragraph "<hr>"
            input "enableAfterSchool", "bool", title: "Enable After-School Chore Intercept?", defaultValue: false, submitOnChange: true
            if (enableAfterSchool) {
                paragraph "<i>Intercept specific users on weekday afternoons with a custom chore reminder instead of a generic greeting.</i>"
                input "afterSchoolUsers", "enum", title: "Select Kids/Users", options: lockUsers, multiple: true, required: false
                input "afterSchoolCustom", "text", title: "Custom Users (Comma separated)", required: false
                input "afterSchoolStart", "time", title: "Window Start Time (e.g., 2:30 PM)", required: false
                input "afterSchoolEnd", "time", title: "Window End Time (e.g., 4:00 PM)", required: false
                input "afterSchoolMsg", "text", title: "Chore Append Message", defaultValue: "Please remember to empty your lunchbox and finish your homework before turning on the television.", required: false
            }
            paragraph "<hr>"

            input "btnTestArrival", "button", title: "▶️ Test Welcome Home Audio", description: "Click to test the arrival volumes and messages on the selected speakers."

            input "disableGlobalAnnouncements", "bool", title: "Ignore Keys & Manual Unlocks?", defaultValue: false, description: "If enabled, the app will completely ignore physical keys and manual thumb-turns. It will ONLY greet people who enter a recognized digital code on the keypad."
            
            input "ignoredCodes", "enum", title: "Silent / Ghost Codes", options: lockUsers, multiple: true, required: false, description: "Select specific lock codes that should NEVER trigger an arrival greeting or show up on the dashboard."
            input "ignoredCustomCodes", "text", title: "Custom Silent Codes", required: false, description: "Comma-separated list of exact names (like 'Admin Code') to completely ignore if they don't appear in the dropdown above."

            input "arrivalMode", "enum", title: "Arrival Detection Mode", options: ["Automatic (Reads lock memory)", "Manual (Assign names to slots)"], defaultValue: "Automatic (Reads lock memory)", submitOnChange: true, description: "Choose how the app tracks users. Automatic is recommended for most setups."
            
            if (arrivalMode == "Automatic (Reads lock memory)") {
                paragraph "<i><b>Automatic Mode:</b> The app pulls names directly from your lock. Select the specific codes you want to track below so unwanted entries (like 'Ghost' or 'Manual') don't trigger the system or pollute your dashboard.</i>"
                
                input "trackedLockCodes", "enum", title: "Select Codes to Track", options: lockUsers, multiple: true, required: false, submitOnChange: true, description: "Check the names of the lock codes you actually want to trigger greetings for."
                input "adminUserAlias", "text", title: "Admin Code Alias", required: false, description: "If you want the generic 'admin code' to trigger a personalized greeting, type your name here."

                def autoArr = [
                    "Welcome home, %name%.",
                    "Glad you're back, %name%.",
                    "Welcome back %name%, the house is ready.",
                    "Greetings %name%, %butler% has the house systems online.",
                    "Good to see you, %name%.",
                    "Hello %name%. I've adjusted the climate for your arrival.",
                    "Welcome home %name%. %butler% is at your service.",
                    "It is good to have you back, %name%.",
                    "Perimeter disarmed. Welcome home, %name%.",
                    "Greetings %name%. I hope you had a pleasant time away."
                ]
                for (int m = 1; m <= 10; m++) {
                    input "autoGreeting_${m}", "text", title: "Dynamic Welcome Message ${m}", required: false, defaultValue: autoArr[m-1]
                }
            } else {
                paragraph "<i><b>Manual Mode:</b> Assign names to slots below.</i>"
                
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
                    "Welcome home. %butler% is at your service."
                ]
                paragraph "<b>Fallback/Guest Messages (Used if code name isn't matched)</b>"
                for (int d = 1; d <= 10; d++) {
                    input "defaultArrivalMessage_${d}", "text", title: "Default Arrival Message ${d}", required: false, defaultValue: defArr[d-1]
                }
                
                input "numLockUsers", "number", title: "Number of Manual User Slots (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true, description: "How many specific people do you want to configure manual overrides for?"
                
                if (numLockUsers > 0) {
                    def usrArr = [
                        "Welcome home, %name%.",
                        "Glad you're back, %name%.",
                        "Welcome back %name%, the house is ready.",
                        "Greetings %name%, %butler% has the house systems online.",
                        "Good to see you, %name%.",
                        "Hello %name%. I've adjusted the climate for your arrival.",
                        "Welcome home %name%. %butler% is at your service.",
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
                            input "lockGreeting_${i}_${m}", "text", title: "User ${i} Welcome Message ${m}", required: false, defaultValue: usrArr[m-1]
                        }
                    }
                }
            }
        }

        section("Contextual Departures", hideable: true, hidden: false) {
            paragraph "<i>Provide a frictionless departure sequence. The system checks the user's Context Switch (e.g., 'Work Day' or 'School Day'), the current house mode, and the time window. If they leave, it plays a farewell and temporarily mutes DND/Intruder alarms so they can walk to the car in peace.</i>"
            
            input "frontDoorContact", "capability.contactSensor", title: "Front Door Contact Sensor", required: false, description: "The sensor on the door that triggers the departure check when opened."
            
            input "numDepartureUsers", "number", title: "Number of Departure Profiles (0-5)", required: true, defaultValue: 0, range: "0..5", submitOnChange: true
            
            if (numDepartureUsers > 0) {
                for (int i = 1; i <= (numDepartureUsers as Integer); i++) {
                    paragraph "<b>Departure Profile ${i}</b>"
                    input "depUserName_${i}", "text", title: "User Name (replaces %name%)", required: false
                    input "depType_${i}", "enum", title: "Profile Type (Changes default messages below)", options: ["Work", "School", "General"], defaultValue: "Work", submitOnChange: true
                    input "depSwitch_${i}", "capability.switch", title: "Context Switch (e.g. Work Day)", required: false, description: "The departure message will ONLY play if this switch is ON."
                    input "depSickSwitch_${i}", "capability.switch", title: "Sick Day Override Switch", required: false, description: "If this switch is ON, departure messages for this user will be bypassed."
                    input "depModes_${i}", "mode", title: "Allowed House Modes", multiple: true, required: false, description: "Only allow this departure if the house is in one of these modes (e.g. Night, Morning)."
                    input "depTimeStart_${i}", "time", title: "Departure Window Start Time", required: false, description: "e.g., 6:00 AM"
                    input "depTimeEnd_${i}", "time", title: "Departure Window End Time", required: false, description: "e.g., 6:15 AM"
                    input "depDelay_${i}", "number", title: "Greeting Delay (Seconds)", defaultValue: 5, required: false, description: "Wait this long after the door opens before saying goodbye."
                    input "depVolume_${i}", "number", title: "Departure Volume (0-100)", required: false, description: "Optional: Sets a specific volume just for this departure. Leave blank to use default outdoor volume."
                    
                    input "btnTestDeparture_${i}", "button", title: "▶️ Test Departure Profile ${i} Audio", description: "Click to hear how this profile's farewell message sounds on the outdoor speaker."
                    input "btnResetDepMsgs_${i}", "button", title: "🔄 Reset to Default ${settings["depType_${i}"] ?: 'Work'} Messages", description: "Click to forcefully overwrite the 10 messages below with the default set for the selected Profile Type."
                    
                    def workMsgs = [
                        "Have a good day at work, %name%.",
                        "Drive safely to work, %name%.",
                        "Have a productive day at the office, %name%.",
                        "Time to make the donuts, %name%.",
                        "Have a great day at work, %name%.",
                        "Safe commute, %name%.",
                        "See you after work, %name%.",
                        "Farewell %name%. %butler% will keep the house secure.",
                        "Have a great day, %name%.",
                        "Goodbye %name%, %butler% will monitor the perimeter."
                    ]
                    def schoolMsgs = [
                        "Have a great day at school, %name%.",
                        "Learn a lot today, %name%.",
                        "Have fun at school, %name%.",
                        "Have a good day in class, %name%.",
                        "Be good at school, %name%.",
                        "Have an awesome school day, %name%.",
                        "See you after school, %name%.",
                        "Have fun at school! %butler% will be here when you get back.",
                        "Have a great day learning, %name%.",
                        "Goodbye %name%, %butler% will monitor the perimeter."
                    ]
                    def genMsgs = [
                        "Have a great day %name%.",
                        "Safe travels %name%.",
                        "Have a good one %name%.",
                        "Take care out there %name%.",
                        "Goodbye %name%, I will monitor the perimeter.",
                        "Have a productive day %name%.",
                        "See you later %name%.",
                        "Farewell %name%. %butler% will keep the house secure.",
                        "Take it easy %name%.",
                        "Goodbye %name%, %butler% will monitor the perimeter."
                    ]
                    
                    def selMsgs = settings["depType_${i}"] == "School" ? schoolMsgs : (settings["depType_${i}"] == "Work" ? workMsgs : genMsgs)
                    
                    paragraph "<i>Custom Departure Messages for ${settings["depUserName_${i}"] ?: "this user"} (Randomized)</i>"
                    for (int m = 1; m <= 10; m++) {
                        input "depMessage_${i}_${m}", "text", title: "Message ${m}", required: false, defaultValue: selMsgs[m-1]
                    }
                }
            }
        }

        section("Local Voice Zones (Rooms)", hideable: true, hidden: false) {
            paragraph "<i>Create isolated voice zones for specific rooms (like bedrooms). This allows each room to have its own Good Night and Good Morning logic without disturbing the rest of the house.</i>"
            
            input "numRooms", "number", title: "Number of Local Voice Zones (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true, description: "Select how many individual rooms you want to configure."
        }

        if (numRooms > 0) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                section("${settings["roomName_${i}"] ?: "Room Zone ${i}"}", hideable: true, hidden: false) { 
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${settings["roomName_${i}"] ?: "Room ${i}"}", description: "Tap to set up occupant names, triggers, and messages for this room.") 
                }
            }
        }

        section("Indoor Doorbell Routing (Follow-Me)", hideable: true, hidden: false) {
            paragraph "<i>Instead of blasting the doorbell announcement everywhere, the Butler will only speak in rooms where active motion is detected.</i>"
            input "enableIndoorRouting", "bool", title: "Enable Targeted Indoor Routing?", defaultValue: false, submitOnChange: true

            if (enableIndoorRouting) {
                input "indoorDoorbellMsg", "text", title: "Announcement Message", defaultValue: "This is %butler%. Pardon the interruption, but there is a visitor at the front door."
                input "indoorRouteFallback", "bool", title: "Fallback to Global Speaker?", defaultValue: true, description: "If no motion is detected in ANY routed room, announce over the Global Indoor Speaker so you don't miss the visitor. Turn this OFF if you want the house to remain completely silent when no one is in a routed room."
                
                input "indoorRouteMuteDND", "bool", title: "Mute During Do Not Disturb?", defaultValue: true, description: "If the DND Switch is ON, do not announce indoors."
                input "indoorRouteRestrictedModes", "mode", title: "Restricted Modes (Do Not Announce)", multiple: true, required: false, description: "Select house modes (like 'Night') where indoor doorbell routing should be completely muted."

                input "btnTestIndoorRouting", "button", title: "▶️ Test Indoor Routing (Based on Current Motion)", description: "Click to simulate a doorbell press and see which speakers it currently routes to based on active motion in your house."

                input "numRoutingRooms", "number", title: "Number of Routing Zones (1-7)", defaultValue: 0, range: "0..7", submitOnChange: true

                if (numRoutingRooms > 0) {
                    for (int i = 1; i <= (numRoutingRooms as Integer); i++) {
                        paragraph "<b>Routing Zone ${i}</b>"
                        input "routeRoomName_${i}", "text", title: "Zone Name", required: false, defaultValue: "Zone ${i}"
                        input "routeMotion_${i}", "capability.motionSensor", title: "Motion Sensors (If Active, send audio here)", multiple: true, required: false
                        input "routeSpeaker_${i}", "capability.speechSynthesis", title: "Target Speaker", required: false
                        input "routeVolume_${i}", "number", title: "Announcement Volume (0-100)", required: false
                        input "routeTVSwitch_${i}", "capability.switch", title: "Entertainment / TV Mute Switch", required: false, description: "If this TV is ON, doorbell routing to this zone will be seamlessly muted."
                    }
                }
            }
        }

        section("Perimeter Guarding (Do Not Disturb)", hideable: true, hidden: false) {
            paragraph "<i>Configure the system to intercept unwanted visitors. When the selected 'Do Not Disturb' switch is ON or specific House Modes are active, doorbell presses or front porch motion will trigger the outdoor speaker to play a deterrent message instead of ringing your indoor chimes.</i>"
            
            input "dndSwitch", "capability.switch", title: "Do Not Disturb Toggle Switch", required: false, description: "Select the virtual switch you use to activate Do Not Disturb mode for the house."
            input "dndModes", "mode", title: "Do Not Disturb Modes", multiple: true, required: false, description: "Select house modes (like 'Away' or 'Night') that automatically activate DND without needing the switch."
            
            input "frontDoorbell", "capability.pushableButton", title: "Front Doorbell Button", required: false, description: "Select your smart doorbell. This acts as the primary trigger to play the DND message."
            input "frontDoorMotion", "capability.motionSensor", title: "Front Door Motion Sensor", required: false, description: "Optional: Use a porch motion sensor to trigger the DND message before they even press the bell."
            input "dndMotionDebounce", "number", title: "Motion Sensor Cooldown (Minutes)", defaultValue: 5, required: false, description: "Prevents the system from repeating the DND message too often if the motion sensor stays active."
            
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
                "This is %butler%. Please leave your delivery at the door. We cannot answer right now.",
                "Do not disturb. All activity is being logged.",
                "This is %butler%. We are occupied at the moment. Please return another time."
            ]
            for (int d = 1; d <= 10; d++) {
                input "dndMessage_${d}", "text", title: "DND Audio Message ${d}", required: false, defaultValue: dndDefs[d-1]
            }
        }
        
        section("Daytime Doorbell Acknowledgment", hideable: true, hidden: false) {
            paragraph "<i>Keep visitors from leaving too soon and add a layer of daytime security. If the doorbell is pressed while Do Not Disturb is OFF, the system will instantly acknowledge them with one of the messages below.</i>"
            
            input "enableDaytimeDoorbell", "bool", title: "Enable Daytime Doorbell Acknowledgment?", defaultValue: false, submitOnChange: true
            
            if (enableDaytimeDoorbell) {
                input "daytimeDoorbellVolume", "number", title: "Announcement Volume (0-100)", required: false, description: "Leave blank to use the default outdoor speaker volume."
                input "daytimeDoorbellDebounce", "number", title: "Cooldown (Minutes)", defaultValue: 2, required: false, description: "Prevents the speaker from repeating if they spam the doorbell."
                
                paragraph "<b>Daytime Acknowledgment Messages (Randomized)</b>"
                def dayDefs = [
                    "Please wait a moment, I am notifying the homeowner.",
                    "Someone will be right with you, please hold on.",
                    "Thank you for ringing, please wait while I fetch someone.",
                    "The residents have been notified, please wait.",
                    "Just a moment please, someone is on the way.",
                    "Please hold, someone will be at the door shortly.",
                    "Thank you, please wait while I connect you with the homeowner.",
                    "Someone will answer the door in just a moment.",
                    "Please wait here, the homeowner is coming.",
                    "This is %butler%. I am alerting the family, please wait a moment.",
                    "Hang tight, someone will be right out.",
                    "Please wait, I'm calling the homeowner to the door.",
                    "Give us just a second, someone is coming.",
                    "Thank you for visiting, someone will be with you shortly.",
                    "Please wait on the porch, I have alerted the house.",
                    "We're on our way to the door, please hold.",
                    "This is %butler%. Just a moment, unlocking the door shortly.",
                    "Please give us a moment to get to the door.",
                    "Someone has been notified of your presence, please wait.",
                    "Hold on just a second, the homeowner will be right there."
                ]
                for (int d = 1; d <= 20; d++) {
                    input "daytimeMessage_${d}", "text", title: "Daytime Message ${d}", required: false, defaultValue: dayDefs[d-1]
                }
                
                input "btnTestDaytime", "button", title: "▶️ Test Daytime Audio", description: "Test the daytime volume and a randomized message on the outdoor speaker."
            }
            
            paragraph "---"
            
            input "enableDaytimeFollowUp", "bool", title: "Enable Unanswered Door Follow-Up?", defaultValue: false, submitOnChange: true, description: "If you don't answer the door in time, the Butler will apologize to the visitor."
            if (enableDaytimeFollowUp) {
                input "daytimeDoorContact", "capability.contactSensor", title: "Front Door Contact Sensor", required: true, description: "If this door does not open within the timeout, the follow-up message will play."
                input "daytimeFollowUpDelay", "number", title: "Wait Time (Minutes)", defaultValue: 3, required: false, description: "How long should the visitor wait before the Butler comes back with the apology?"
                
                paragraph "<b>Unanswered Follow-Up Messages (Randomized)</b>"
                def noAnswerDefs = [
                    "I am sorry, but the homeowners are currently unavailable to come to the door.",
                    "Apologies, but it seems no one is able to answer the door right now. Please leave a message.",
                    "The homeowners are unable to come to the door at this moment. Have a good day.",
                    "I apologize, but they cannot come to the front door right now.",
                    "It appears the residents are tied up. Please try again later."
                ]
                for (int d = 1; d <= 5; d++) {
                    input "daytimeNoAnswer_${d}", "text", title: "No Answer Message ${d}", required: false, defaultValue: noAnswerDefs[d-1]
                }
            }
        }

        section("After Hours Doorbell Intercept", hideable: true, hidden: false) {
            paragraph "<i>Automatically intercept doorbell presses after a certain time (like 8:00 PM), even if standard DND is not turned on.</i>"
            input "enableAfterHours", "bool", title: "Enable After Hours Intercept?", defaultValue: false, submitOnChange: true

            if (enableAfterHours) {
                input "afterHoursTimeStart", "time", title: "After Hours Start Time (e.g., 8:00 PM)", required: false
                input "afterHoursTimeEnd", "time", title: "After Hours End Time (e.g., 8:00 AM)", required: false
                input "afterHoursVolume", "number", title: "Announcement Volume (0-100)", required: false, description: "Leave blank to use the default outdoor speaker volume."
                input "afterHoursDebounce", "number", title: "Cooldown (Minutes)", defaultValue: 5, required: false, description: "Wait this long before playing the message again if they repeatedly press the doorbell."

                paragraph "<b>After Hours Messages (Randomized)</b>"
                def ahDefs = [
                    "It is currently after hours, the homeowners are unavailable.",
                    "The residents are done receiving visitors for the evening.",
                    "It is too late for visitors. Please return tomorrow.",
                    "The household is resting for the night. Please leave a message.",
                    "We are no longer accepting visitors at this hour.",
                    "It's past visiting hours. The cameras are recording.",
                    "The homeowners are unavailable for the rest of the evening.",
                    "Please leave a package or a message. We are done for the day.",
                    "Visiting hours have concluded. Please try again tomorrow.",
                    "We do not answer the door at this hour.",
                    "The house is settling down for the night. Please depart.",
                    "It's too late in the evening for visitors. Goodbye.",
                    "Please respect our evening hours and return another time.",
                    "This is %butler%. No one will be answering the door this late.",
                    "Evening protocols are active. The homeowners are unavailable."
                ]
                for (int d = 1; d <= 15; d++) {
                    input "afterHoursMessage_${d}", "text", title: "After Hours Message ${d}", required: false, defaultValue: ahDefs[d-1]
                }
                
                input "btnTestAfterHours", "button", title: "▶️ Test After Hours Audio", description: "Test the after hours volume and a randomized message on the outdoor speaker."
            }
        }
        
        section("Nighttime Intruder Deterrent", hideable: true, hidden: false) {
            paragraph "<i>Protect your perimeter at night. If motion is detected while asleep, the outdoor speaker will play a strict deterrent message. This overrides standard DND.</i>"
            
            input "enableIntruder", "bool", title: "Enable Intruder Deterrent?", defaultValue: false, submitOnChange: true
            
            if (enableIntruder) {
                input "intruderModes", "mode", title: "Active Modes (e.g., Night)", multiple: true, required: false, description: "Select the specific house modes where the intruder alarm is armed."
                input "intruderMotion", "capability.motionSensor", title: "Trigger Motion Sensors", multiple: true, required: false, description: "Sensors (like Porch or Side Door) that trigger the deterrent."
                
                input "intruderBypassDoors", "capability.contactSensor", title: "Bypass Doors (Dog Let-Out/User Exit)", multiple: true, required: false, description: "Safety catch: If any of these doors are currently open OR were opened in the last X minutes, the system assumes it's you outside and temporarily disables the deterrent."
                input "intruderBypassMinutes", "number", title: "Door Bypass Timeout (Minutes)", defaultValue: 5, required: false, description: "How long after closing the door should the deterrent remain paused?"
                
                input "intruderDebounce", "number", title: "Deterrent Cooldown (Minutes)", defaultValue: 5, required: false, description: "Prevents the speaker from going off every 10 seconds if someone is lingering. Set to at least 1 minute."
                input "intruderVolume", "number", title: "Deterrent Announcement Volume (0-100)", required: false, description: "Leave blank to use the default outdoor speaker volume."
                
                input "smartCameraDevice", "capability.sensor", title: "Smart Camera", required: false, description: "Select your camera/device that reports object types."
                input "smartAttribute", "text", title: "Smart Detection Attribute", defaultValue: "smartDetectType", description: "The exact attribute name the driver uses (e.g., smartDetectType, detectType)."

                paragraph "<b>Smart Detection Messages (Randomized)</b>\n<i>Using Smart Camera detection will allow the specific messages rather than the generic ones. The generic messages are below.</i>"
                
                // Animal
                def animalDefs = ["Shoo! Get out of here!", "Go away!", "Move along animal!"]
                for (int d = 1; d <= 3; d++) {
                    input "intruderAnimal_${d}", "text", title: "Animal Message ${d}", required: false, defaultValue: animalDefs[d-1]
                }
                
                // Person
                def personDefs = ["Warning. You are trespassing. Security has been notified.", "Perimeter breach detected. Cameras are recording your face.", "Please step away from the house. You are being recorded."]
                for (int d = 1; d <= 3; d++) {
                    input "intruderPerson_${d}", "text", title: "Person Message ${d}", required: false, defaultValue: personDefs[d-1]
                }

                // Vehicle
                def vehicleDefs = ["Unauthorized vehicle detected. License plate logged.", "Please remove your vehicle from the property.", "Vehicle approach recorded."]
                for (int d = 1; d <= 3; d++) {
                    input "intruderVehicle_${d}", "text", title: "Vehicle Message ${d}", required: false, defaultValue: vehicleDefs[d-1]
                }

                paragraph "<b>Generic Intruder Messages (Randomized)</b>"
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
                    input "intruderMessage_${d}", "text", title: "Intruder Message ${d}", required: false, defaultValue: intDefs[d-1]
                }
                
                input "btnTestIntruder", "button", title: "▶️ Test Intruder Audio", description: "Test the intruder volume and a randomized message on the outdoor speaker."
            }
        }

        section("Butler Event Reporting", hideable: true, hidden: false) {
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
        
        section("Screen Time Enforcer (Kids)", hideable: true, hidden: false) {
            paragraph "<i>Let the Butler be the bad guy. When this switch turns OFF, he will firmly announce that screen time is over.</i>"
            input "enableScreenTime", "bool", title: "Enable Screen Time Enforcer?", defaultValue: false, submitOnChange: true
            if (enableScreenTime) {
                input "screenTimeSwitch", "capability.switch", title: "Screen Time Switch", required: true, description: "The virtual or physical switch that tracks screen time."
                input "screenTimeSpeaker", "capability.speechSynthesis", title: "Announcement Speaker", required: false, description: "Leave blank to use the Global Indoor Speaker."
                input "screenTimeVolume", "number", title: "Announcement Volume (0-100)", required: false
                
                paragraph "<b>Screen Time Timeout Messages (Randomized)</b>"
                def stDefs = [
                    "Pardon the interruption, but the daily screen time allotment has expired. Please power down the device.",
                    "Attention. Screen time is now over. Please find a non-digital activity.",
                    "Sir, the screen time timer has reached zero. Please turn off the television.",
                    "Excuse me, but screen time has concluded for the day.",
                    "The screen time switch has been deactivated. Please turn off the screens."
                ]
                for (int d = 1; d <= 5; d++) {
                    input "screenTimeMsg_${d}", "text", title: "Timeout Message ${d}", required: false, defaultValue: stDefs[d-1]
                }
                
                input "btnTestScreenTime", "button", title: "▶️ Test Screen Time Audio"
            }
        }
        
        section("Quiet Hours (Night Mode Audio)", hideable: true, hidden: false) {
            paragraph "<i>To prevent the system from waking the house, you can enforce a strict maximum volume during specific hours. This overrides all other volume settings (Arrivals, DND, Global, and Room volumes) during the time window.</i>"
            
            input "quietHoursStart", "time", title: "Quiet Hours Start Time", required: false, description: "When should the system lower its voice?"
            input "quietHoursEnd", "time", title: "Quiet Hours End Time", required: false, description: "When should normal volume rules resume?"
            input "quietVolume", "number", title: "Quiet Hours Maximum Volume (0-100)", required: false, description: "All voice announcements will be forcibly throttled down to this volume during Quiet Hours."
        }

        section("Birthdays, Anniversaries & Holidays", hideable: true, hidden: false) {
            paragraph "<i>Configure special daily announcements.</i>"
            
            input "enableHolidays", "bool", title: "Enable Morning Holiday Announcements?", defaultValue: false, submitOnChange: true, description: "If enabled, Good Morning greetings will automatically append a reminder for major US holidays."
            if (enableHolidays) {
                input "holidayMessage", "text", title: "Holiday Message Format", defaultValue: "By the way, don't forget today is %holiday%!", description: "Use %holiday% to inject the holiday name."
            }
            
            paragraph "<hr>"
            
            input "enableAnniversary", "bool", title: "Enable House Anniversary Greetings?", defaultValue: false, submitOnChange: true, description: "If enabled, the Butler will add a special anniversary message to arrivals, departures, and specifically selected rooms."
            if (enableAnniversary) {
                def months = ["01":"January", "02":"February", "03":"March", "04":"April", "05":"May", "06":"June", "07":"July", "08":"August", "09":"September", "10":"October", "11":"November", "12":"December"]
                input "annivMonth", "enum", title: "Anniversary Month", options: months, required: true
                input "annivDay", "number", title: "Anniversary Day (1-31)", range: "1..31", required: true
                input "annivAllowedUsers", "enum", title: "Limit Anniversary to Specific Users (Arrival/Departure)", options: lockUsers, multiple: true, required: false, description: "Only these users will hear the anniversary message when arriving or departing. Leave blank to allow everyone."
                input "annivAllowedCustom", "text", title: "Limit Anniversary (Custom Names)", required: false, description: "Comma-separated list (e.g., Admin Code)."

                paragraph "<b>Custom Anniversary Messages</b>"
                input "annivMsgArrival", "text", title: "Arrival Append", defaultValue: "Happy Anniversary! Welcome home."
                input "annivMsgDeparture", "text", title: "Departure Append", defaultValue: "Have a wonderful anniversary today!"
                input "annivMsgMorning", "text", title: "Good Morning Append", defaultValue: "Happy Anniversary! I hope you both have a fantastic day."
                input "annivMsgNight", "text", title: "Good Night Append", defaultValue: "Happy Anniversary. Sleep well, I will keep the perimeter secure."
            }
            
            paragraph "<hr>"
            
            paragraph "<b>User Birthdays</b>\n<i>When it's a user's special day, the system will append a birthday message to their Good Morning, Good Night, Arrival, and Departure greetings. Note: The name entered here must perfectly match their lock code name, departure profile name, or room occupant name.</i>"
            input "numBirthdays", "number", title: "Number of Birthdays to Track (0-10)", defaultValue: 0, submitOnChange: true

            if (numBirthdays > 0) {
                def months = ["01":"January", "02":"February", "03":"March", "04":"April", "05":"May", "06":"June", "07":"July", "08":"August", "09":"September", "10":"October", "11":"November", "12":"December"]
                for (int i = 1; i <= (numBirthdays as Integer); i++) {
                    input "bdayName_${i}", "text", title: "Person's Name ${i}", required: false
                    input "bdayMonth_${i}", "enum", title: "Birth Month ${i}", options: months, required: false
                    input "bdayDay_${i}", "number", title: "Birth Day ${i} (1-31)", range: "1..31", required: false
                }
                
                paragraph "<b>Custom Birthday Messages</b>"
                input "bdayMsgArrival", "text", title: "Arrival Append", defaultValue: "Happy Birthday %name%!"
                input "bdayMsgDeparture", "text", title: "Departure Append", defaultValue: "Have a wonderful birthday today, %name%!"
                input "bdayMsgMorning", "text", title: "Good Morning Append", defaultValue: "Happy Birthday %name%! I hope you have a fantastic day."
                input "bdayMsgNight", "text", title: "Good Night Append", defaultValue: "Happy Birthday %name%. I hope you had a wonderful day."
            }
        }
        
        section("User Aliases (Secret Identities)", hideable: true, hidden: false) {
            paragraph "<i>Give users a fun nickname (like 'Commander' or 'Your Highness'). The Butler will dynamically use this alias instead of their real name in all greetings.</i>"
            input "numAliases", "number", title: "Number of Aliases (0-5)", defaultValue: 0, range: "0..5", submitOnChange: true
            
            if (numAliases > 0) {
                for (int i = 1; i <= (numAliases as Integer); i++) {
                    if (lockUsers.size() > 0) {
                        input "aliasReal_${i}", "enum", title: "Real Name ${i}", options: lockUsers, required: false, description: "Must exactly match their Lock Code or Room Occupant Name."
                    } else {
                        input "aliasReal_${i}", "text", title: "Real Name ${i}", required: false, description: "Must exactly match their Lock Code or Room Occupant Name."
                    }
                    input "aliasFake_${i}", "text", title: "Alias / Nickname ${i}", required: false, description: "e.g., Commander, Batman, Princess"
                }
            }
        }

        section("Advanced Features & Arrival Resets", hideable: true, hidden: false) {
            paragraph "<i>Arrival and Departure statuses automatically reset at Midnight. Use the options below to configure who resets and when.</i>"
            
            input "enableDebug", "bool", title: "Enable Debug Logging?", defaultValue: false, description: "Turn on to see detailed TTS queueing metrics in the Hubitat system logs."
            
            input "stayAtHomeUsers", "enum", title: "Stay At Home Users (Lock Codes)", options: lockUsers, multiple: true, required: false, description: "Select users who are home 24/7. Their arrival status will NOT reset at midnight, meaning they won't trigger a greeting the next day unless the house explicitly goes into a reset mode (like 'Away')."
            input "stayAtHomeCustom", "text", title: "Stay At Home Users (Custom Names)", required: false, description: "Comma-separated list of names (like an Admin Alias) to keep checked-in at midnight if they aren't selectable above."
            
            input "resetModes", "mode", title: "Reset ALL Arrivals on Mode Change", multiple: true, required: false, description: "Select house modes (like 'Away'). When the house enters this mode, EVERYONE is instantly marked as away, overriding Stay At Home status."
            
            paragraph "<b>Sensor & Switch Away Checks</b>\n<i>Link specific users to a virtual switch (like 'Work') or a presence sensor (like their phone). If the switch turns ON, or the presence sensor leaves (Not Present), they are marked Away so their lock code will trigger a greeting next time.</i>"
            input "awayCheckTime", "time", title: "Scheduled Sweep Time", required: false, description: "Time to check if the switches below are ON (e.g., 2:00 AM)."
            
            input "numAwayMappings", "number", title: "Number of User Switch Mappings (0-5)", required: false, defaultValue: 0, range: "0..5", submitOnChange: true, description: "How many users do you want to link to specific Away/Reset triggers?"
            
            if (numAwayMappings > 0) {
                for (int i = 1; i <= (numAwayMappings as Integer); i++) {
                    if (lockUsers.size() > 0) {
                        input "awayMappingUser_${i}", "enum", title: "Mapping ${i} User", options: lockUsers, required: false
                    } else {
                        input "awayMappingUser_${i}", "text", title: "Mapping ${i} User", required: false
                    }
                    input "awayMappingSwitch_${i}", "capability.switch", title: "Mapping ${i} Switch (Optional)", required: false
                    input "awayMappingPresence_${i}", "capability.presenceSensor", title: "Mapping ${i} Presence Sensor (Optional)", required: false, description: "Only tracks departure (Not Present) to reset arrival status."
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
            input "roomOccupantName_${rNum}", "text", title: "Primary Occupant Name(s)", defaultValue: "Guest", required: false, description: "The person(s) who sleeps here. e.g. 'John' or 'John and Jane'. The app will automatically inject this into greetings."
            
            input "roomSpeaker_${rNum}", "capability.speechSynthesis", title: "Dedicated Room Speaker", required: false, description: "Select the specific speaker inside this room. If left blank, announcements will route to the Global Indoor Speaker."
            input "btnTestRoomSpk_${rNum}", "button", title: "▶️ Test Room Speaker Link"
            
            input "roomVolumeGN_${rNum}", "number", title: "Good Night Volume (0-100)", required: false, description: "Volume level when the room goes to sleep."
            input "roomVolumeGM_${rNum}", "number", title: "Good Morning Volume (0-100)", required: false, description: "Volume level when the room wakes up."
            input "roomTVSwitch_${rNum}", "capability.switch", title: "Entertainment / TV Mute Switch", required: false, description: "If this TV is ON, routine announcements (like mail or weather) will be muted in this room so you are not interrupted."
        }
        
        section("Logic & Automations Triggers", hideable: true, hidden: false) {
            input "roomGoodNightSwitch_${rNum}", "capability.switch", title: "Room Good Night Switch", required: false, description: "Select the switch that indicates the room is going to sleep."
            
            input "roomWakeupMode_${rNum}", "enum", title: "Good Morning Trigger Mode", options: [
                "1. Immediate (When Good Night Switch turns OFF)",
                "2. Verified (Wait for switch OFF, then wait for Motion)",
                "3. Motion Driven (Trigger when Motion activates while switch is ON)"
            ], defaultValue: "1. Immediate (When Good Night Switch turns OFF)", submitOnChange: true
            
            if (settings["roomWakeupMode_${rNum}"] != "1. Immediate (When Good Night Switch turns OFF)") {
                input "roomMotion_${rNum}", "capability.motionSensor", title: "Wake-Up Motion Sensors", multiple: true, required: false, description: "Select one or more motion sensors. If ANY of them detect motion, the room wakes up."
            }
            
            input "delayGreetingGN_${rNum}", "number", title: "Good Night Greeting Delay (Seconds)", defaultValue: 5, required: false, description: "Pause before speaking (e.g., 5 seconds) so Good Night music and room can settle."
            input "delayGreetingGM_${rNum}", "number", title: "Good Morning Greeting Delay (Seconds)", defaultValue: 30, required: false, description: "Pause briefly before speaking so morning automations and lights can execute."
        }
        
        section("Personalized Greetings", hideable: true, hidden: false) {
            paragraph "<i>Use the buttons below to instantly hear how your current settings (volume, speaker, and names) will sound in this room. You can use <b>%time%</b>, <b>%date%</b>, and <b>%butler%</b> in custom overrides!</i>"
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
                "Good night %name%. %butler% will keep watch.",
                "Rest easy %name%. Systems are entering night mode.",
                "Good night %name%. Waking protocols are set for tomorrow.",
                "Sleep well %name%. %butler% is shutting down the house."
            ]
            def gmDefs = [
                "Good morning %name%. The house is waking up.",
                "Good morning %name%. I hope you slept well in the %room%.",
                "Rise and shine %name%. Systems are online.",
                "Good morning %name%. %butler% is ready for the day.",
                "Hello %name%. The morning routine has begun.",
                "Good morning %name%. I have disarmed the night perimeter.",
                "Good morning %name%. I hope you have a productive day.",
                "Rise and shine %name%. Waking sequence complete.",
                "Good morning %name%. All night-time automations have concluded.",
                "Hello %name%. %butler% has prepared the house for your morning."
            ]
            
            if (settings["useCustomRoomMessages_${rNum}"]) {
                paragraph "<i><b>Custom Mode Active:</b> Write your own messages below. Use the variables <b>%name%</b> and <b>%room%</b> to dynamically replace them with the Occupant's Name and Room Name.</i>"
                
                paragraph "<b>Good Night Messages (Triggered when switch turns ON)</b>"
                for (int m = 1; m <= 10; m++) {
                    input "gnMessage_${rNum}_${m}", "text", title: "Good Night Message ${m}", required: false, defaultValue: gnDefs[m-1]
                }
                
                paragraph "<b>Good Morning Messages (Triggered when switch turns OFF)</b>"
                for (int m = 1; m <= 10; m++) {
                    input "gmMessage_${rNum}_${m}", "text", title: "Good Morning Message ${m}", required: false, defaultValue: gmDefs[m-1]
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
        
        section("Morning Briefing Add-ons (News, Agenda, Kids)", hideable: true, hidden: false) {
            paragraph "<i>Expand the morning briefing with bespoke daily content. Note: These are appended to the Good Morning sequence in this specific room.</i>"
            
            input "roomAgendaEnable_${rNum}", "bool", title: "Enable Daily Agenda Reminders?", defaultValue: false, submitOnChange: true
            if (settings["roomAgendaEnable_${rNum}"]) {
                paragraph "<i>Type a specific reminder for any day of the week. The Butler will append it to the Good Morning greeting.</i>"
                input "roomAgendaMonday_${rNum}", "text", title: "Monday", required: false
                input "roomAgendaTuesday_${rNum}", "text", title: "Tuesday", required: false
                input "roomAgendaWednesday_${rNum}", "text", title: "Wednesday", required: false
                input "roomAgendaThursday_${rNum}", "text", title: "Thursday", required: false
                input "roomAgendaFriday_${rNum}", "text", title: "Friday", required: false
                input "roomAgendaSaturday_${rNum}", "text", title: "Saturday", required: false
                input "roomAgendaSunday_${rNum}", "text", title: "Sunday", required: false
            }
            
            paragraph "<hr>"
            
            input "roomNewsEnable_${rNum}", "bool", title: "Enable Top Headlines News Fetcher?", defaultValue: false, submitOnChange: true
            if (settings["roomNewsEnable_${rNum}"]) {
                input "roomNewsFeed_${rNum}", "text", title: "RSS Feed URL", defaultValue: "https://feeds.npr.org/1001/rss.xml", description: "Default is NPR Top Stories. Must be a valid RSS XML URL."
            }
            
            paragraph "<hr>"
            
            input "roomKidsMode_${rNum}", "bool", title: "Enable Junior Concierge (Jokes & Facts)?", defaultValue: false, description: "Appends a kid-friendly fun fact or dad joke to the end of the morning routine."
            input "roomWardrobe_${rNum}", "bool", title: "Enable Kid-Focused Wardrobe Advisor?", defaultValue: false, description: "Translates the morning temperature into kid-friendly clothing advice. Requires the Weather Device to be set in the section below."
            input "roomBoredomBuster_${rNum}", "bool", title: "Enable Weekend Boredom Buster?", defaultValue: false, description: "On Saturdays and Sundays, suggests an indoor or outdoor activity based on the weather."
        }

        section("Weather, Security & Briefing Integrations", hideable: true, hidden: false) {
            input "roomPerimeterCheck_${rNum}", "bool", title: "Run Perimeter Security Check on Good Night?", defaultValue: false, submitOnChange: true, description: "If enabled, the Butler will report the status of your locks and coop doors before reading the weather."
            input "roomCurfewWarning_${rNum}", "bool", title: "Enable Missing Person Warning?", defaultValue: false, submitOnChange: true, description: "If enabled, the Butler will warn you if someone departed today but has not yet returned."
            input "roomEnableAnniversary_${rNum}", "bool", title: "Play Anniversary Greeting in this Room?", defaultValue: false, description: "If enabled, the global anniversary message will append to Good Morning and Good Night in this zone."
            
            paragraph "<i>Link your weather station or generic weather device to seamlessly append the day's forecast directly after the room's greeting finishes. It will read your 'Meteorologist Script' if available, or automatically build a sentence using generic Temperature and Weather Condition attributes.</i>"
            input "roomWeatherDevice_${rNum}", "capability.temperatureMeasurement", title: "Weather / Temperature Device", required: false, description: "Select the device providing your daily forecast or temperature readings.", submitOnChange: true
            
            if (settings["roomWeatherDevice_${rNum}"]) {
                def wDevice = settings["roomWeatherDevice_${rNum}"]
                def wText = wDevice.currentValue("meteorologistScript")
                if (!wText) {
                    def temp = wDevice.currentValue("temperature")
                    def cond = wDevice.currentValue("weather") ?: "clear conditions"
                    if (temp) wText = "The current temperature is ${temp} degrees and it is ${cond}."
                }
                
                def displayTxt = wText ?: "Waiting for device to sync data..."
                paragraph "<div style='margin-top: 10px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px;'><b>Live Forecast Preview:</b><br><i>${displayTxt}</i></div>"
            }
            
            input "roomWeatherGM_${rNum}", "bool", title: "Append Forecast to Good Morning", defaultValue: false
            input "roomWeatherGN_${rNum}", "bool", title: "Append Forecast to Good Night", defaultValue: false
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
    if (state.roomAlreadyAwake == null) state.roomAlreadyAwake = [:]
    if (state.resetReasons == null) state.resetReasons = [:]
    if (state.globalResetReason == null) state.globalResetReason = "Awaiting First Entry"
    
    if (state.nightMotionCount == null) state.nightMotionCount = 0
    if (state.awayDoorbellCount == null) state.awayDoorbellCount = 0
    if (state.pendingMorningReport == null) state.pendingMorningReport = false
    if (state.pendingArrivalReport == null) state.pendingArrivalReport = false
    if (state.lastMode == null) state.lastMode = location.mode
    if (state.lastMailDeliveryTime == null) state.lastMailDeliveryTime = 0
    
    if (state.lastBypassDoorOpen == null) state.lastBypassDoorOpen = 0
    if (state.lastIntruderAlert == null) state.lastIntruderAlert = 0
    if (state.departureGracePeriodEnd == null) state.departureGracePeriodEnd = 0
    if (state.lastDepartureTime == null) state.lastDepartureTime = [:]
    
    if (state.internetActive == null) state.internetActive = true
    
    // Engine Queue Maps
    if (state.ttsQueue == null) state.ttsQueue = []
    if (state.speakingUntil == null) state.speakingUntil = 0
    if (state.currentPriority == null) state.currentPriority = 99
    
    // Protects baseline volume from ratcheting
    if (state.originalVolumes == null) state.originalVolumes = [:]
}

def initialize() {
    ensureStateMaps()
    schedule("0 0 0 * * ?", "midnightReset") 
    
    // Internet Connectivity Monitoring
    if (settings.enableInternetCheck) {
        runEvery5Minutes("checkInternetConnection")
        checkInternetConnection() // Initial run
    } else {
        state.internetActive = true
    }
    
    // Perimeter & DND
    if (frontDoorbell) {
        subscribe(frontDoorbell, "pushed", visitorHandler)
        subscribe(frontDoorbell, "pushed", countDoorbellHandler)
    }
    if (frontDoorMotion) {
        subscribe(frontDoorMotion, "motion.active", visitorHandler)
        subscribe(frontDoorMotion, "motion.active", countMotionHandler)
    }
    
    if (settings.enableDaytimeFollowUp && settings.daytimeDoorContact) {
        subscribe(settings.daytimeDoorContact, "contact.open", daytimeDoorHandler)
    }
    
    // Intruder Deterrent
    if (enableIntruder) {
        if (intruderMotion) subscribe(intruderMotion, "motion.active", intruderMotionHandler)
        if (intruderBypassDoors) subscribe(intruderBypassDoors, "contact.open", intruderDoorHandler)
        if (smartCameraDevice && smartAttribute) {
            subscribe(smartCameraDevice, smartAttribute, unifiProtectHandler)
        }
    }
    
    // Arrival Logic
    if (frontDoorLock) subscribe(frontDoorLock, "lock.unlocked", arrivalHandler)
    
    // Mail Checking Logic
    if (settings.enableMailCheck && settings.mailSwitch) {
        subscribe(settings.mailSwitch, "switch.on", mailSwitchHandler)
    }
    
    // Departure Logic
    if (frontDoorContact) subscribe(frontDoorContact, "contact", departureHandler)
    
    // Screen Time Enforcer
    if (settings.enableScreenTime && settings.screenTimeSwitch) {
        subscribe(settings.screenTimeSwitch, "switch.off", screenTimeHandler)
    }

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
        def pSens = settings["awayMappingPresence_${i}"]
        if (pSens) {
            subscribe(pSens, "presence", awayPresenceHandler)
        }
    }
    
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numRoomsSet; i++) {
        def gnSwitch = settings["roomGoodNightSwitch_${i}"]
        if (gnSwitch) {
            subscribe(gnSwitch, "switch.on", goodNightOnHandler)
            subscribe(gnSwitch, "switch.off", goodNightOffHandler)
        }
        
        def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
        if (mode != "1. Immediate (When Good Night Switch turns OFF)") {
            def motionSensors = settings["roomMotion_${i}"]
            if (motionSensors) subscribe(motionSensors, "motion.active", roomMotionHandler)
        }
    }
}

// --- MAIL DELIVERY HANDLER ---
def mailSwitchHandler(evt) {
    ensureStateMaps()
    state.lastMailDeliveryTime = new Date().time
    addToHistory("SYSTEM: Mail delivery logged via switch.")
    if (settings.enableDebug) log.debug "SYSTEM: Mail switch turned ON. Delivery time logged."
}

// --- SCREEN TIME HANDLER ---
def screenTimeHandler(evt) {
    ensureStateMaps()
    def messages = []
    for (int d = 1; d <= 5; d++) {
        def msg = settings["screenTimeMsg_${d}"]
        if (msg) messages << msg
    }
    if (!messages) messages = ["Pardon the interruption, but the daily screen time allotment has expired. Please power down the device."]
    
    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
    
    def targetSpeaker = settings.screenTimeSpeaker ?: globalIndoorSpeaker
    def targetVol = settings.screenTimeVolume ?: globalVolume
    
    if (targetSpeaker) {
        enqueueTTS(targetSpeaker, randomMsg, targetVol, 2)
        addToHistory("SCREEN TIME: Enforcer triggered. Queued: '${randomMsg}'")
    }
}

// --- INTERNET CONNECTIVITY CHECK ---
def checkInternetConnection() {
    if (!settings.enableInternetCheck) return
    
    try {
        def params = [
            uri: "http://captive.apple.com", // Bulletproof endpoint that never redirects
            timeout: 10 // Increased timeout to prevent false positives from hub load
        ]
        asynchttpGet("internetCheckCallback", params)
    } catch (e) {
        log.error "Voice Butler: Failed to initiate internet check: ${e}"
        setInternetState(false)
    }
}

def internetCheckCallback(response, data) {
    // Accept 200 (OK) or 301/302 (Redirects) just in case
    if (response && !response.hasError() && (response.status == 200 || response.status == 301 || response.status == 302)) {
        setInternetState(true)
    } else {
        setInternetState(false)
    }
}

def setInternetState(Boolean isOnline) {
    if (state.internetActive != isOnline) {
        state.internetActive = isOnline
        def statusStr = isOnline ? "ONLINE" : "OFFLINE"
        log.info "SYSTEM: Internet connection status changed to ${statusStr}."
        
        if (!isOnline) {
            addToHistory("SYSTEM ALERT: Internet connection lost. External TTS suppressed to prevent log errors.")
        } else {
            addToHistory("SYSTEM: Internet connection restored. TTS operations resuming.")
        }
    }
}

// --- ALIAS HELPER ---
def applyAlias(String name) {
    if (!name) return name
    def num = settings.numAliases ? settings.numAliases as Integer : 0
    for (int i = 1; i <= num; i++) {
        def real = settings["aliasReal_${i}"]
        def fake = settings["aliasFake_${i}"]
        if (real && fake && real.trim().equalsIgnoreCase(name.trim())) {
            return fake.trim()
        }
    }
    return name
}

// --- DYNAMIC VARIABLE HELPER ---
def applyDynamicVars(String msg) {
    if (!msg) return ""
    def now = new Date()
    def tStr = now.format("h:mm a", location.timeZone)
    def dStr = now.format("EEEE, MMMM d", location.timeZone)
    def bName = settings.butlerName ?: "the concierge"
    return msg.replace("%time%", tStr).replace("%date%", dStr).replace("%butler%", bName)
}

// --- WEATHER HELPER ---
def getWeatherReport(wDevice) {
    if (!wDevice) return ""
    def wText = ""
    try {
        // Find most recent update time of either temperature or meteorologistScript
        def lastUpdateObj = wDevice.currentState("temperature") ?: wDevice.currentState("meteorologistScript")
        def lastUpdate = lastUpdateObj?.date?.time ?: 0
        def now = new Date().time
        
        if ((now - lastUpdate) < 21600000) { // 6 hours
            wText = wDevice.currentValue("meteorologistScript")
            if (!wText) {
                def temp = wDevice.currentValue("temperature")
                def cond = wDevice.currentValue("weather") ?: "clear conditions"
                if (temp) wText = "The current temperature is ${temp} degrees and it is ${cond}."
            }
        } else {
            log.warn "Weather data on ${wDevice.displayName} is stale (over 6 hours old). Skipping append."
        }
    } catch (e) {
        log.error "Error generating weather report: ${e}"
    }
    return wText ?: ""
}

// --- TTS ENGINE & PRIORITY QUEUE ---
def enqueueTTS(speakerInput, msg, originalVol, priority, fastTrack = false) {
    if (!speakerInput) return
    
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") {
        if (settings.enableDebug) log.debug "TTS Suppressed: Master Switch is OFF. Skipped Message: '${msg}'"
        return
    }
    
    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        if (settings.enableDebug) log.debug "TTS Suppressed: Guest Mode is ON. Background tracking continues silently. Skipped Message: '${msg}'"
        return
    }
    
    if (settings.enableInternetCheck && state.internetActive == false) {
        if (settings.enableDebug) log.debug "TTS Suppressed: Internet connection is currently offline. Skipped Message: '${msg}'"
        return
    }
    
    def speakers = speakerInput instanceof List ? speakerInput : [speakerInput]
    def speakerIds = speakers.collect { it.id }

    def item = [
        id: java.util.UUID.randomUUID().toString(),
        speakerIds: speakerIds,
        msg: msg,
        vol: originalVol,
        priority: priority,
        fastTrack: fastTrack,
        queuedAt: new Date().time
    ]

    state.ttsQueue.add(item)
    state.ttsQueue = state.ttsQueue.sort { it.priority }

    if (settings.enableDebug) log.debug "SYSTEM: Queued Priority ${priority} Message: '${msg}'"

    processQueue()
}

def processQueue() {
    if (!state.ttsQueue || state.ttsQueue.size() == 0) {
        state.currentPriority = 99 // Idle
        state.originalVolumes = [:] // Safety net: Force clear locks when the engine goes idle
        return
    }

    def now = new Date().time
    def isSpeaking = (now < (state.speakingUntil ?: 0))
    def nextItem = state.ttsQueue[0]

    // Dynamic Interruption Matrix
    if (isSpeaking) {
        if (nextItem.priority < state.currentPriority) {
            log.info "SYSTEM WARNING: Priority ${nextItem.priority} message is preempting the active Priority ${state.currentPriority} message."
        } else {
            def delaySecs = Math.max(1, Math.ceil((state.speakingUntil - now) / 1000.0).toInteger())
            runIn(delaySecs, "processQueue", [overwrite: true])
            return
        }
    }

    // Dequeue and set active
    state.ttsQueue.remove(0)
    state.currentPriority = nextItem.priority

    // Fire audio to hardware
    def durationMs = executeTTS(nextItem)
    state.speakingUntil = now + durationMs + 1500 // 1.5s pad
    
    def nextQueueCheckDelay = Math.ceil(durationMs / 1000.0).toInteger() + 2
    
    if (state.ttsQueue.size() > 0) {
        runIn(nextQueueCheckDelay, "processQueue", [overwrite: true])
    } else {
        runIn(nextQueueCheckDelay, "resetQueuePriority", [overwrite: true])
    }
}

def resetQueuePriority() {
    state.currentPriority = 99
}

def executeTTS(item) {
    def msg = item.msg
    def vol = item.vol
    def fastTrack = item.fastTrack
    def speakerIds = item.speakerIds
    def priority = item.priority

    def allSpks = getAllSpeakers()
    def speakers = allSpks.findAll { speakerIds.contains(it.id) }

    // --- TV / ENTERTAINMENT MUTE LOGIC ---
    def filteredSpeakers = []
    speakers.each { spk ->
        def isMutedByTV = false
        // Only mute routine/standard announcements (Priority 2, 3, 4). Intruder (1) overrides TV!
        if (priority > 1) { 
            // 1. Check Global Speaker TV Switch
            if (settings.globalTVSwitch && settings.globalTVSwitch.currentValue("switch") == "on" && settings.globalIndoorSpeaker?.find{ it.id == spk.id }) {
                isMutedByTV = true
            }
            // 2. Check Room TV Switches
            def numR = settings.numRooms ? settings.numRooms as Integer : 0
            for (int i = 1; i <= numR; i++) {
                if (settings["roomTVSwitch_${i}"] && settings["roomTVSwitch_${i}"].currentValue("switch") == "on" && settings["roomSpeaker_${i}"]?.id == spk.id) {
                    isMutedByTV = true
                }
            }
            // 3. Check Doorbell Routing TV Switches
            def numRoute = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
            for (int i = 1; i <= numRoute; i++) {
                if (settings["routeTVSwitch_${i}"] && settings["routeTVSwitch_${i}"].currentValue("switch") == "on" && settings["routeSpeaker_${i}"]?.id == spk.id) {
                    isMutedByTV = true
                }
            }
        }
        
        if (!isMutedByTV) {
            filteredSpeakers << spk
        } else {
            if (settings.enableDebug) log.debug "TTS Suppressed on ${spk.displayName}: Linked TV/Entertainment switch is ON."
            addToHistory("SYSTEM: Muted announcement on ${spk.displayName} to avoid interrupting TV/Entertainment.")
        }
    }
    
    speakers = filteredSpeakers
    if (speakers.size() == 0) return 500 // Skip execution entirely if all target speakers are muted
    // -------------------------------------

    def finalVol = vol
    if (quietHoursStart && quietHoursEnd && quietVolume != null) {
        try {
            if (timeOfDayIsBetween(toDateTime(quietHoursStart), toDateTime(quietHoursEnd), new Date(), location.timeZone)) {
                finalVol = quietVolume as Integer
                if (settings.enableDebug) log.debug "SYSTEM: Quiet Hours active. Throttling outbound volume to ${finalVol}%"
            }
        } catch(e) { 
            log.error "Quiet Hours logic check failed: ${e}" 
        }
    }
    
    def safeMsg = msg.replace("&", "and")
    def finalMsg = safeMsg
    
    // Force fast-tracked events (Arrivals) to bypass the padding completely for maximum speed
    if (settings.enableWakeupPad && !fastTrack) {
        finalMsg = ", , , , " + safeMsg 
    }
    
    speakers.each { spk ->
        try {
            def currentVol = spk.currentValue("volume")
            
            // Lock in the true baseline volume before making changes
            if (state.originalVolumes[spk.id] == null) {
                state.originalVolumes[spk.id] = currentVol
            } else {
                currentVol = state.originalVolumes[spk.id]
            }
            
            def targetVol = finalVol != null ? (finalVol as Integer) : currentVol
            
            // OPTIMIZATION: Only send the volume command if it doesn't match, saving ~300ms of network latency.
            if (targetVol != null && currentVol != targetVol) {
                try {
                    spk.setVolume(targetVol as Integer)
                    // Instant speed for Arrivals when fastTrack is true
                    if (!fastTrack) pauseExecution(1000) else pauseExecution(50) 
                } catch(ve) {
                    log.error "Failed to set volume on ${spk.displayName}: ${ve}"
                }
            }
            
            spk.speak(finalMsg)
            
            if (!fastTrack) pauseExecution(500) 
            try { if (spk.hasCommand("play")) spk.play() } catch (ex) {}
            
            if (currentVol != null && targetVol != null && currentVol != targetVol) {
                def delay = Math.max(6, (finalMsg.length() / 12).toInteger() + 4)
                runIn(delay, "restoreVolumeTask", [data: [speakerId: spk.id, oldVol: currentVol], overwrite: false])
            }
        } catch (e) {
            log.error "Voice Butler TTS Execution Error on ${spk.displayName}: ${e}"
        }
    }
    
    return (Math.max(3, (finalMsg.length() / 12).toInteger())) * 1000
}

def restoreVolumeTask(data) {
    def id = data.speakerId
    def vol = data.oldVol
    if (id != null) {
        def spk = getAllSpeakers().find { it.id == id }
        if (spk && vol != null) {
            try { spk.setVolume(vol as Integer) } catch(e) { log.error "Failed to restore volume: ${e}" }
        }
        // Clear the locked baseline so the next standalone message reads fresh
        state.originalVolumes.remove(id)
    }
}

def getAllSpeakers() {
    def list = []
    if (outdoorSpeaker) list << outdoorSpeaker
    if (globalIndoorSpeaker) list.addAll(globalIndoorSpeaker)
    if (butlerLrSpeaker) list << butlerLrSpeaker
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numRoomsSet; i++) {
        if (settings["roomSpeaker_${i}"]) list << settings["roomSpeaker_${i}"]
    }
    def numRouteSet = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
    for (int i = 1; i <= numRouteSet; i++) {
        if (settings["routeSpeaker_${i}"]) list << settings["routeSpeaker_${i}"]
    }
    if (settings.screenTimeSpeaker) list << settings.screenTimeSpeaker
    return list.flatten().findAll { it != null }
}

def resetDepartureMessages(int i) {
    def workMsgs = [
        "Have a good day at work, %name%.",
        "Drive safely to work, %name%.",
        "Have a productive day at the office, %name%.",
        "Time to make the donuts, %name%.",
        "Have a great day at work, %name%.",
        "Safe commute, %name%.",
        "See you after work, %name%.",
        "Farewell %name%. %butler% will keep the house secure.",
        "Have a great day, %name%.",
        "Goodbye %name%, %butler% will monitor the perimeter."
    ]
    def schoolMsgs = [
        "Have a great day at school, %name%.",
        "Learn a lot today, %name%.",
        "Have fun at school, %name%.",
        "Have a good day in class, %name%.",
        "Be good at school, %name%.",
        "Have an awesome school day, %name%.",
        "See you after school, %name%.",
        "Have fun at school! %butler% will be here when you get back.",
        "Have a great day learning, %name%.",
        "Goodbye %name%, %butler% will monitor the perimeter."
    ]
    def genMsgs = [
        "Have a great day %name%.",
        "Safe travels %name%.",
        "Have a good one %name%.",
        "Take care out there %name%.",
        "Goodbye %name%, I will monitor the perimeter.",
        "Have a productive day %name%.",
        "See you later %name%.",
        "Farewell %name%. %butler% will keep the house secure.",
        "Take it easy %name%.",
        "Goodbye %name%, %butler% will monitor the perimeter."
    ]
    
    def type = settings["depType_${i}"] ?: "Work"
    def selMsgs = type == "School" ? schoolMsgs : (type == "Work" ? workMsgs : genMsgs)
    
    for (int m = 1; m <= 10; m++) {
        app.updateSetting("depMessage_${i}_${m}", [type: "text", value: selMsgs[m-1]])
    }
    log.info "SYSTEM: Reset departure messages for Profile ${i} to ${type} defaults."
    addToHistory("SYSTEM: Reset departure messages for Profile ${i} to ${type} defaults.")
}

// --- DEPARTURE LOGIC (Priority 3) ---
def departureHandler(evt) {
    if (evt.value != "open") return
    
    ensureStateMaps()
    
    def nowTime = new Date().time
    if (settings.enableDebug) log.debug "DEPARTURE TRACE: Front door opened. Checking departure profiles..."
    
    def numDep = settings.numDepartureUsers ? settings.numDepartureUsers as Integer : 0
    if (numDep == 0) return
    
    def now = new Date()
    def departedIndexes = []
    
    for (int i = 1; i <= numDep; i++) {
        def uName = settings["depUserName_${i}"]
        def ctxSwitch = settings["depSwitch_${i}"]
        def sickSwitch = settings["depSickSwitch_${i}"]
        def tStart = settings["depTimeStart_${i}"]
        def tEnd = settings["depTimeEnd_${i}"]
        def dModes = [settings["depModes_${i}"]].flatten().findAll{it}
        
        if (uName && ctxSwitch && tStart && tEnd) {
            if (state.hasDepartedToday[uName]) continue
            if (sickSwitch && sickSwitch.currentValue("switch") == "on") continue
            if (dModes.size() > 0 && !dModes.contains(location.mode)) continue
            if (ctxSwitch.currentValue("switch") != "on") continue
            
            try {
                if (!timeOfDayIsBetween(toDateTime(tStart), toDateTime(tEnd), now, location.timeZone)) continue
            } catch (e) {
                continue
            }

            state.hasDepartedToday[uName] = true
            state.lastDepartureTime[uName] = nowTime
            departedIndexes << i
        }
    }
    
    if (departedIndexes.size() > 0) {
        state.departureGracePeriodEnd = nowTime + 300000 
        log.info "SYSTEM: Valid departure detected. Suppressing DND and Intruder alerts for 5 minutes."
        
        departedIndexes.each { idx ->
            def uName = settings["depUserName_${idx}"]
            def displayUserName = applyAlias(uName)
            def messages = []
            
            for (int m = 1; m <= 10; m++) {
                def msg = settings["depMessage_${idx}_${m}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Have a good trip %name%."]
            
            def rawMsg = messages[new Random().nextInt(messages.size())]
            def finalMsg = rawMsg.replace("%name%", displayUserName)
            
            def bdayMsg = getBirthdayMessage(uName, "Departure")
            if (bdayMsg) {
                finalMsg = "${finalMsg} ${bdayMsg}"
            }
            
            def annivMsg = getAnniversaryMessage("Departure", uName)
            if (annivMsg) {
                finalMsg = "${finalMsg} ${annivMsg}"
            }
            
            finalMsg = applyDynamicVars(finalMsg)
            
            def delay = settings["depDelay_${idx}"] != null ? settings["depDelay_${idx}"].toInteger() : 5
            
            if (outdoorSpeaker) {
                def profileVol = settings["depVolume_${idx}"]
                def targetVolume = profileVol != null ? profileVol : (settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"])
                
                log.info "SYSTEM: Departure matched for ${uName}. Queuing farewell in ${delay} seconds."
                runIn(delay, "playDepartureGreeting", [data: [user: uName, message: finalMsg, volume: targetVolume], overwrite: false])
            } else {
                addToHistory("DEPARTURE ERROR: Departure matched for [${uName}], but no Outdoor Front Door Speaker is assigned.")
            }
        }
    }
}

def playDepartureGreeting(data) {
    def uName = data.user
    def finalMsg = data.message
    def targetVolume = data.volume
    
    if (outdoorSpeaker) {
        enqueueTTS(outdoorSpeaker, finalMsg, targetVolume, 3)
        addToHistory("DEPARTURE: Contextual departure window matched for [${uName}]. Queued: '${finalMsg}'")
    }
}

// --- NIGHTTIME INTRUDER DETERRENT (Priority 1) ---
def intruderDoorHandler(evt) {
    ensureStateMaps()
    state.lastBypassDoorOpen = new Date().time
    log.info "Intruder Safety Bypass: Door opened. Pausing deterrents."
}

def canTriggerIntruder() {
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) return false
    def activeModes = [settings.intruderModes].flatten().findAll{it}
    if (!activeModes.contains(location.mode)) return false
    
    def isDoorOpen = false
    if (settings.intruderBypassDoors) {
        settings.intruderBypassDoors.each { door ->
            if (door.currentValue("contact") == "open") isDoorOpen = true
        }
    }
    if (isDoorOpen) return false
    
    def lastDoor = atomicState.lastBypassDoorOpen ?: state.lastBypassDoorOpen ?: 0
    def bpVal = settings.intruderBypassMinutes
    def bypassMins = (bpVal != null && bpVal.toString().isInteger()) ? bpVal.toInteger() : 5
    if ((now - lastDoor) < (bypassMins * 60000)) return false
    
    def dbVal = settings.intruderDebounce
    def dbMins = (dbVal != null && dbVal.toString().isInteger()) ? dbVal.toInteger() : 5
    if (dbMins <= 0) dbMins = 1 // Hard floor to prevent spam
    def debounceMs = dbMins * 60000
    
    def lastAlert = atomicState.lastIntruderAlert ?: state.lastIntruderAlert ?: 0
    if ((now - lastAlert) <= debounceMs) return false
    
    return true
}

def intruderMotionHandler(evt) {
    ensureStateMaps()
    if (!canTriggerIntruder()) return
    
    if (settings.smartCameraDevice && settings.smartAttribute) {
        if (settings.enableDebug) log.debug "INTRUDER: Motion detected, but Smart Camera is active. Waiting 6 seconds for object classification..."
        runIn(6, "executeGenericIntruder", [data: [deviceName: evt.device.displayName], overwrite: true])
    } else {
        executeGenericIntruder([deviceName: evt.device.displayName])
    }
}

def executeGenericIntruder(data) {
    if (!canTriggerIntruder()) return
    
    atomicState.lastIntruderAlert = new Date().time
    atomicState.lastOutdoorGreeting = new Date().time
    
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 10; d++) {
            def msg = settings["intruderMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Unexpected motion detected. Cameras are currently recording."]
        
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
        
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 1)
        addToHistory("INTRUDER DETERRENT: Generic motion detected on ${data?.deviceName ?: 'Camera'}. Queued: '${randomMsg}'")
    }
}

def unifiProtectHandler(evt) {
    ensureStateMaps()
    
    def detectStr = evt.value?.toLowerCase() ?: ""
    if (detectStr == "none" || detectStr == "waiting" || detectStr == "null" || detectStr == "") return
    if (detectStr.contains("package")) {
        addToHistory("INTRUDER LOG: Smart Camera detected a Package. No deterrent audio played.")
        return
    }
    
    if (!canTriggerIntruder()) {
        if (settings.enableDebug) log.debug "INTRUDER: Ignored smart event '${detectStr}' due to cooldown."
        return
    }
    
    // Cancel the pending generic message because the AI caught a specific object
    unschedule("executeGenericIntruder")
    
    atomicState.lastIntruderAlert = new Date().time
    atomicState.lastOutdoorGreeting = new Date().time
    
    // Look for exact matches within the comma separated list
    def isPerson = detectStr.contains("person")
    def isVehicle = detectStr.contains("vehicle")
    def isAnimal = detectStr.contains("animal")

    def messages = []
    def logType = "Unknown"

    if (isPerson) {
        logType = "Person"
        for (int d = 1; d <= 3; d++) { if (settings["intruderPerson_${d}"]) messages << settings["intruderPerson_${d}"] }
        if (!messages) messages = ["Warning. You are trespassing. Security has been notified."]
    } else if (isVehicle) {
        logType = "Vehicle"
        for (int d = 1; d <= 3; d++) { if (settings["intruderVehicle_${d}"]) messages << settings["intruderVehicle_${d}"] }
        if (!messages) messages = ["Unauthorized vehicle detected. License plate logged."]
    } else if (isAnimal) {
        logType = "Animal"
        for (int d = 1; d <= 3; d++) { if (settings["intruderAnimal_${d}"]) messages << settings["intruderAnimal_${d}"] }
        if (!messages) messages = ["Shoo! Get out of here!"]
    } else {
        logType = "Generic"
        for (int d = 1; d <= 10; d++) { if (settings["intruderMessage_${d}"]) messages << settings["intruderMessage_${d}"] }
        if (!messages) messages = ["Unexpected movement detected. Cameras are currently recording."]
    }

    if (outdoorSpeaker) {
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
        
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 1)
        addToHistory("SMART DETERRENT: ${logType} detected on ${evt.device.displayName}. Queued: '${randomMsg}'")
    }
}

// --- BUTLER EVENT TRACKING & REPORTING (Priority 4) ---
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
    
    msg = applyDynamicVars(msg)
    
    def targetSpeaker = settings.butlerLrSpeaker ?: globalIndoorSpeaker
    def targetVol = settings.butlerLrVolume ?: globalVolume
    
    if (targetSpeaker) {
        enqueueTTS(targetSpeaker, msg, targetVol, 4)
        addToHistory("BUTLER REPORT: Delivered ${type} report. Queued: '${msg}'")
    } else {
        addToHistory("BUTLER ERROR: Tried to deliver ${type} report, but no Living Room or Global speaker is assigned.")
    }
}

// --- FRONT DOOR DND, AFTER HOURS, & DAYTIME LOGIC (Priority 2) ---

def daytimeDoorHandler(evt) {
    ensureStateMaps()
    if (settings.enableDebug) log.debug "DAYTIME: Door opened. Canceling any pending unanswered visitor follow-up."
    unschedule("playDaytimeFollowUp")
}

def playDaytimeFollowUp() {
    ensureStateMaps()
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 5; d++) {
            def msg = settings["daytimeNoAnswer_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["I am sorry, but the homeowners are currently unavailable to come to the door."]
        
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume
        
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 2, true)
        addToHistory("DAYTIME GREETING: Doorbell unanswered after timeout. Queued: '${randomMsg}'")
    }
}

def visitorHandler(evt) {
    ensureStateMaps()
    
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) return
    
    // --- CONFLICT RESOLUTION: INTRUDER VS VISITOR ---
    // 1. Gag Order: If Intruder alarm just went off, suppress polite visitor greetings for 60 seconds
    def lastIntruder = atomicState.lastIntruderAlert ?: state.lastIntruderAlert ?: 0
    if ((now - lastIntruder) < 60000) {
        log.info "SYSTEM: Suppressing visitor greeting. An Intruder Alert recently fired."
        return
    }
    
    // 2. Sensor Overlap: If this is a motion event, and Intruder is armed on this mode/sensor, yield to Intruder entirely
    def intruderModeList = [settings.intruderModes].flatten().findAll{it}
    if (evt.name == "motion" && settings.enableIntruder && intruderModeList.contains(location.mode)) {
        def intIds = [settings.intruderMotion].flatten().findAll{it}.collect{it?.id}
        if (settings.smartCameraDevice) intIds << settings.smartCameraDevice.id
        if (intIds.contains(evt.device.id)) {
            if (settings.enableDebug) log.debug "SYSTEM: Yielding DND motion event to Intruder Deterrent."
            return
        }
    }
    // ------------------------------------------------
    
    def dndModesList = [settings.dndModes].flatten().findAll{it}
    def isDndActive = (dndSwitch?.currentValue("switch") == "on") || dndModesList.contains(location.mode)
    def isMotion = evt.name == "motion"
    def lastGreet = atomicState.lastOutdoorGreeting ?: state.lastOutdoorGreeting ?: 0
    def isDoorbell = !isMotion
    
    def isAfterHours = false
    if (enableAfterHours && afterHoursTimeStart && afterHoursTimeEnd) {
        try { isAfterHours = timeOfDayIsBetween(toDateTime(afterHoursTimeStart), toDateTime(afterHoursTimeEnd), new Date(), location.timeZone) } catch(e) {}
    }

    // --- NEW: INDOOR ROUTING (Targeted Intercom) ---
    if (isDoorbell && settings.enableIndoorRouting) {
        def shouldRoute = true
        if (settings.indoorRouteMuteDND && isDndActive) shouldRoute = false
        
        def restrictedModes = [settings.indoorRouteRestrictedModes].flatten().findAll{it}
        if (restrictedModes.contains(location.mode)) shouldRoute = false

        if (shouldRoute) {
            def anyRouted = false
            def routeMsg = settings.indoorDoorbellMsg ?: "This is %butler%. Pardon the interruption, but there is a visitor at the front door."
            routeMsg = applyDynamicVars(routeMsg)

            def numRoutes = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
            for (int i = 1; i <= numRoutes; i++) {
                def mSensors = [settings["routeMotion_${i}"]].flatten().findAll{it}
                def spk = settings["routeSpeaker_${i}"]
                if (mSensors && spk) {
                    def isActive = mSensors.any { it.currentValue("motion") == "active" }
                    if (isActive) {
                        def vol = settings["routeVolume_${i}"]
                        enqueueTTS(spk, routeMsg, vol, 2)
                        anyRouted = true
                        def rName = settings["routeRoomName_${i}"] ?: "Zone ${i}"
                        addToHistory("INDOOR ROUTING: Motion active in ${rName}. Doorbell announced there.")
                    }
                }
            }

            if (!anyRouted && settings.indoorRouteFallback && globalIndoorSpeaker) {
                enqueueTTS(globalIndoorSpeaker, routeMsg, globalVolume, 2)
                addToHistory("INDOOR ROUTING: No active motion found. Doorbell announced on Global fallback.")
            }
        } else {
            if (settings.enableDebug) log.debug "INDOOR ROUTING: Suppressed due to DND or Restricted Mode."
        }
    }
    // -----------------------------------------------
    
    if (isDndActive) {
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
                randomMsg = applyDynamicVars(randomMsg)
                
                enqueueTTS(outdoorSpeaker, randomMsg, outdoorVolume, 2, isDoorbell)
                atomicState.lastOutdoorGreeting = now 
                
                def triggerType = isMotion ? "Motion" : "Doorbell"
                addToHistory("PERIMETER GUARD: Visitor detected (${triggerType}) while DND is active. Queued: '${randomMsg}'")
            }
        }
    } else if (isAfterHours && isDoorbell) {
        def debounceMins = settings.afterHoursDebounce != null ? settings.afterHoursDebounce.toInteger() : 5
        def debounceMs = debounceMins * 60000
        
        if ((now - lastGreet) > debounceMs) {
            if (outdoorSpeaker) {
                def messages = []
                for (int d = 1; d <= 15; d++) {
                    def msg = settings["afterHoursMessage_${d}"]
                    if (msg) messages << msg
                }
                if (!messages) messages = ["It is currently after hours, the homeowners are unavailable."]
                def randomMsg = messages[new Random().nextInt(messages.size())]
                randomMsg = applyDynamicVars(randomMsg)
                def targetVol = settings.afterHoursVolume != null ? settings.afterHoursVolume : settings.outdoorVolume
                
                enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 2, true)
                atomicState.lastOutdoorGreeting = now
                
                addToHistory("AFTER HOURS: Doorbell rung during after hours window. Queued: '${randomMsg}'")
            }
        }
    } else if (!isDndActive && !isAfterHours && isDoorbell && enableDaytimeDoorbell) {
        def debounceMins = settings.daytimeDoorbellDebounce != null ? settings.daytimeDoorbellDebounce.toInteger() : 2
        def debounceMs = debounceMins * 60000
        
        if ((now - lastGreet) > debounceMs) {
            if (outdoorSpeaker) {
                def messages = []
                for (int d = 1; d <= 20; d++) {
                    def msg = settings["daytimeMessage_${d}"]
                    if (msg) messages << msg
                }
                if (!messages) messages = ["Please wait a moment, I am notifying the homeowner."]
                def randomMsg = messages[new Random().nextInt(messages.size())]
                randomMsg = applyDynamicVars(randomMsg)
                def targetVol = settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume
                
                enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 2, true)
                atomicState.lastOutdoorGreeting = now
                
                addToHistory("DAYTIME GREETING: Doorbell rung during daytime. Queued: '${randomMsg}'")
                
                // Unanswered Follow-Up Logic
                if (settings.enableDaytimeFollowUp && settings.daytimeDoorContact) {
                    def delayMins = settings.daytimeFollowUpDelay != null ? settings.daytimeFollowUpDelay.toInteger() : 3
                    runIn(delayMins * 60, "playDaytimeFollowUp", [overwrite: true])
                    if (settings.enableDebug) log.debug "DAYTIME: Scheduled follow-up for ${delayMins} minutes."
                }
            }
        }
    }
}

// --- ARRIVAL & RESET LOGIC (Priority 3) ---
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
        if (disableGlobalAnnouncements) return
        trackingKey = "global"
        actualUserName = "Guest"
    } else if (trackingKey == "global" && disableGlobalAnnouncements) {
         return
    }
    
    if (!state.hasArrivedToday[trackingKey]) {
        def nowTime = new Date().time
        def graceMins = settings.quickReturnGrace != null ? settings.quickReturnGrace.toInteger() : 5
        def graceMs = graceMins * 60000
        
        def lastDepUser = state.lastDepartureTime[trackingKey] ?: 0
        
        if (lastDepUser > 0 && (nowTime - lastDepUser < graceMs)) {
            state.hasArrivedToday[trackingKey] = true 
            log.info "SYSTEM: Quick return / Forgotten item detected for [${trackingKey}]. Bypassing arrival audio."
            addToHistory("ARRIVAL: Quick return detected for [${trackingKey}]. Silently marked as Arrived.")
            return
        }
        
        state.hasArrivedToday[trackingKey] = true
        atomicState.lastOutdoorGreeting = new Date().time
        
        def messages = []
        def isExtended = false
        
        if (settings.enableExtendedAbsence && lastDepUser > 0) {
            def thresholdMs = (settings.extendedAbsenceHours != null ? settings.extendedAbsenceHours.toInteger() : 48) * 3600000
            if ((nowTime - lastDepUser) >= thresholdMs) {
                isExtended = true
            }
        }

        if (isExtended) {
            for (int m = 1; m <= 5; m++) {
                def msg = settings["extAbsenceMessage_${m}"]
                if (msg) messages << msg
            }
            if (!messages) messages = ["Welcome back from your trip, %name%. I kept the perimeter secure while you were away."]
            log.info "SYSTEM: Extended absence detected for [${trackingKey}]. Overriding standard arrival greeting."
        } else {
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
        }

        def displayUserName = applyAlias(actualUserName)
        def rawMsg = messages[new Random().nextInt(messages.size())]
        def greetingToPlay = rawMsg.replace("%name%", displayUserName)
        
        state.lastDepartureTime.remove(trackingKey)
        
        def bdayMsg = getBirthdayMessage(actualUserName, "Arrival")
        if (bdayMsg) greetingToPlay = "${greetingToPlay} ${bdayMsg}"
        
        def annivMsg = getAnniversaryMessage("Arrival", actualUserName)
        if (annivMsg) greetingToPlay = "${greetingToPlay} ${annivMsg}"
        
        // --- AFTER-SCHOOL CHORE INTERCEPT ---
        def isAfterSchool = false
        if (settings.enableAfterSchool && settings.afterSchoolStart && settings.afterSchoolEnd) {
            def cal = Calendar.getInstance(location.timeZone)
            def dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY) {
                if (timeOfDayIsBetween(toDateTime(settings.afterSchoolStart), toDateTime(settings.afterSchoolEnd), new Date(), location.timeZone)) {
                    def asUsers = [settings.afterSchoolUsers].flatten().findAll{it}.collect{it.toLowerCase()}
                    if (settings.afterSchoolCustom) asUsers += settings.afterSchoolCustom.split(',').collect{it.trim().toLowerCase()}
                    if (asUsers.isEmpty() || asUsers.contains(actualUserName.toLowerCase()) || asUsers.contains(trackingKey.toLowerCase())) {
                        isAfterSchool = true
                    }
                }
            }
        }
        
        if (isAfterSchool && settings.afterSchoolMsg) {
            greetingToPlay = "${greetingToPlay} ${settings.afterSchoolMsg}"
        }
        // -----------------------------------
        
        // --- HOUSE ROSTER LOGIC ---
        if (settings.enableHouseRoster) {
            def allowedRoster = [settings.rosterAllowedUsers].flatten().findAll{it}.collect{it.toLowerCase()}
            if (settings.rosterAllowedCustom) allowedRoster += settings.rosterAllowedCustom.split(',').collect{it.trim().toLowerCase()}
            
            if (allowedRoster.isEmpty() || allowedRoster.contains(actualUserName.toLowerCase()) || allowedRoster.contains(trackingKey.toLowerCase())) {
                def othersHome = state.hasArrivedToday.findAll { k, v -> v == true && k.toLowerCase() != trackingKey.toLowerCase() && k != "global" }.keySet().toList()
                def rosterMsg = ""
                if (othersHome.size() == 0) {
                    rosterMsg = " You are the first to arrive. The house is empty."
                } else if (othersHome.size() == 1) {
                    rosterMsg = " ${othersHome[0]} is already home."
                } else if (othersHome.size() == 2) {
                    rosterMsg = " ${othersHome[0]} and ${othersHome[1]} are already home."
                } else {
                    def last = othersHome.pop()
                    rosterMsg = " ${othersHome.join(', ')}, and ${last} are already home."
                }
                greetingToPlay = greetingToPlay + rosterMsg
            }
        }
        // --------------------------
        
        greetingToPlay = applyDynamicVars(greetingToPlay)
        
        // --- MAIL DELIVERY APPEND LOGIC ---
        if (settings.enableMailCheck && settings.mailSwitch && settings.mailSwitch.currentValue("switch") == "on") {
            def allowedMail = [settings.mailAllowedUsers].flatten().findAll{it}.collect{it.toLowerCase()}
            if (settings.mailAllowedCustom) allowedMail += settings.mailAllowedCustom.split(',').collect{it.trim().toLowerCase()}
            
            if (allowedMail.isEmpty() || allowedMail.contains(actualUserName.toLowerCase()) || allowedMail.contains(trackingKey.toLowerCase())) {
                def mailTimeStr = "earlier today"
                if (state.lastMailDeliveryTime && state.lastMailDeliveryTime > 0) {
                    mailTimeStr = new Date(state.lastMailDeliveryTime).format("h:mm a", location.timeZone)
                }
                def mailAppend = " Pardon the reminder, but the mail was delivered at ${mailTimeStr} and still needs to be retrieved."
                greetingToPlay = greetingToPlay + mailAppend
            }
        }
        // ----------------------------------
        
        def outdoorTargetVol = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        def indoorTargetVol = settings["arrivalIndoorVolume"] != null ? settings["arrivalIndoorVolume"] : settings["globalVolume"]
        
        def played = false
        if (outdoorSpeaker) {
            enqueueTTS(outdoorSpeaker, greetingToPlay, outdoorTargetVol, 3, true)
            played = true
        }
        if (settings.arrivalIndoorSpeaker && globalIndoorSpeaker) {
            enqueueTTS(globalIndoorSpeaker, greetingToPlay, indoorTargetVol, 3, true)
            played = true
        }
        
        if (played) {
            def arrLogType = isExtended ? "Extended Vacation Arrival" : "First arrival"
            addToHistory("ARRIVAL: ${arrLogType} detected for [${trackingKey}]. Queued: '${greetingToPlay}'")
        } else {
            addToHistory("ARRIVAL ERROR: Arrival matched for [${trackingKey}], but no speakers are enabled or assigned.")
        }
    }
}

def modeChangeHandler(evt) {
    ensureStateMaps()
    def newMode = evt.value
    def nowT = new Date().time
    
    def resetModesList = [settings.resetModes].flatten().findAll{it}
    if (resetModesList.contains(newMode)) {
        state.hasArrivedToday.each { k, v ->
            if (v) state.lastDepartureTime[k] = nowT
        }
        
        state.hasArrivedToday = [:]
        state.resetReasons = [:]
        state.globalResetReason = "Reset by Mode Change (${newMode})"
        addToHistory("SYSTEM: House mode changed to [${newMode}]. All First Arrival statuses cleared and departure times logged.")
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
                state.lastDepartureTime[uName] = new Date().time
                state.hasArrivedToday.remove(uName)
                state.resetReasons[uName] = "Away Switch Turned ON"
                addToHistory("SYSTEM: Away Switch (${evt.device.displayName}) turned ON for [${uName}]. Arrival status cleared and departure time logged.")
            }
        }
    }
}

def awayPresenceHandler(evt) {
    if (evt.value != "not present") return
    
    ensureStateMaps()
    def deviceId = evt.device.id
    def numMappings = settings.numAwayMappings ? settings.numAwayMappings as Integer : 0
    
    for (int i = 1; i <= numMappings; i++) {
        if (settings["awayMappingPresence_${i}"]?.id == deviceId) {
            def uName = settings["awayMappingUser_${i}"]
            if (uName && state.hasArrivedToday[uName]) {
                state.lastDepartureTime[uName] = new Date().time
                state.hasArrivedToday.remove(uName)
                state.resetReasons[uName] = "Presence Sensor Departed"
                if (settings.enableDebug) log.debug "SYSTEM: Presence Sensor (${evt.device.displayName}) departed for [${uName}]. Arrival status cleared."
                addToHistory("SYSTEM: Presence Sensor (${evt.device.displayName}) departed for [${uName}]. Arrival status cleared.")
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
                state.lastDepartureTime[uName] = new Date().time
                state.hasArrivedToday.remove(uName)
                state.resetReasons[uName] = "Away Switch ON (Scheduled Check)"
                addToHistory("SYSTEM: Scheduled check found Away Switch (${sw.displayName}) ON for [${uName}]. Arrival status cleared and departure time logged.")
            }
        }
    }
}

// --- ROOM STATE LOGIC & WEATHER ROUTING (Priority 4 & 5) ---
def roomMotionHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    
    for (int i = 1; i <= numRoomsSet; i++) {
        def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
        if (mode == "1. Immediate (When Good Night Switch turns OFF)") continue
        
        def sensors = []
        if (settings["roomMotion_${i}"]) sensors += [settings["roomMotion_${i}"]].flatten().collect{it?.id}
        
        if (sensors.contains(deviceId)) {
            if (mode == "2. Verified (Wait for switch OFF, then wait for Motion)") {
                if (state.waitingForMotion["${i}"]) {
                    state.waitingForMotion["${i}"] = false
                    triggerGoodMorningSequence(i)
                }
            } else if (mode == "3. Motion Driven (Trigger when Motion activates while switch is ON)") {
                def gnSwitch = settings["roomGoodNightSwitch_${i}"]
                if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                    if (!state.roomAlreadyAwake["${i}"]) {
                        state.roomAlreadyAwake["${i}"] = true
                        addToHistory("SYSTEM: Wake-Up Motion (${evt.device.displayName}) triggered Good Morning while switch was ON.")
                        triggerGoodMorningSequence(i)
                    }
                }
            }
        }
    }
}

def goodNightOnHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (settings.numRooms as Integer ?: 1); i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == deviceId) {
            state.waitingForMotion["${i}"] = false 
            state.roomAlreadyAwake["${i}"] = false // Reset daily sensor lock
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            
            def cal = Calendar.getInstance(location.timeZone)
            def hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour >= 0 && hour < 7) {
                log.info "SYSTEM: Late night Good Night trigger detected in ${rName} (${hour} AM hour). Suppressing audio."
                addToHistory("ROOM GREETING: Good Night switch ON in ${rName} (Late Night). Audio suppressed.")
                return 
            }

            def delaySec = settings["delayGreetingGN_${i}"] != null ? settings["delayGreetingGN_${i}"].toInteger() : 5
            def occName = settings["roomOccupantName_${i}"] ?: "Guest"
            
            // --- Broadened Missed Arrival Logic ---
            def occupants = occName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect{ it.trim() }
            def missedCount = 0
            def totalOcc = occupants.size()
            
            occupants.each { occ ->
                // Check if they are currently marked as anything other than Arrived
                def checkKey = state.hasArrivedToday?.keySet()?.find { it.equalsIgnoreCase(occ) } ?: occ
                if (!state.hasArrivedToday[checkKey]) {
                    missedCount++
                    state.hasArrivedToday[checkKey] = true
                    state.resetReasons[checkKey] = "Missed entry. Checked in via Good Night Switch."
                    addToHistory("SYSTEM: Occupant [${checkKey}] was marked away. Checked in silently via Good Night.")
                }
            }
            
            def apologyPrefix = ""
            if (missedCount > 0) {
                if (totalOcc > 1 && missedCount < totalOcc) {
                    apologyPrefix = "Pardon me, I did not realize one of you had returned earlier. Welcome home. "
                } else {
                    apologyPrefix = "Pardon me, I did not catch your arrival earlier. Welcome home. "
                }
            }
            // ---------------------------------------
            
            // --- Curfew / Missing Person Warning ---
            def curfewPrefix = ""
            if (settings["roomCurfewWarning_${i}"]) {
                def missingPersons = []
                state.hasDepartedToday.each { depUser, hasDeparted ->
                    if (hasDeparted) {
                        def checkKey = state.hasArrivedToday?.keySet()?.find { it.equalsIgnoreCase(depUser) } ?: depUser
                        if (!state.hasArrivedToday[checkKey]) {
                            missingPersons << depUser
                        }
                    }
                }
                if (missingPersons.size() > 0) {
                    if (missingPersons.size() == 1) {
                        curfewPrefix = "Please note that ${missingPersons[0]} has not yet returned. "
                    } else if (missingPersons.size() == 2) {
                        curfewPrefix = "Please note that ${missingPersons[0]} and ${missingPersons[1]} have not yet returned. "
                    } else {
                        def lastMissing = missingPersons.pop()
                        curfewPrefix = "Please note that ${missingPersons.join(', ')}, and ${lastMissing} have not yet returned. "
                    }
                }
            }
            // ---------------------------------------
            
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
                    "Good night %name%. %butler% will keep watch.",
                    "Rest easy %name%. Systems are entering night mode.",
                    "Good night %name%. Waking protocols are set for tomorrow.",
                    "Sleep well %name%. %butler% is shutting down the house."
                ]
                rawMsg = defaults[new Random().nextInt(defaults.size())]
            }
            
            def displayOccName = applyAlias(occName)
            def finalMsg = apologyPrefix + curfewPrefix + rawMsg.replace("%name%", displayOccName).replace("%room%", rName)
            def bdayMsg = getBirthdayMessage(occName, "Night")
            if (bdayMsg) finalMsg = "${finalMsg} ${bdayMsg}"
            
            if (settings["roomEnableAnniversary_${i}"]) {
                def annivMsg = getAnniversaryMessage("Night")
                if (annivMsg) finalMsg = "${finalMsg} ${annivMsg}"
            }
            
            finalMsg = applyDynamicVars(finalMsg)
            
            runIn(delaySec, "scheduleGoodNightSequence", [data: [roomNum: i, message: finalMsg, roomName: rName], overwrite: false])
            return 
        }
    }
}

def scheduleGoodNightSequence(data) {
    def rNum = data.roomNum
    def finalMsg = data.message
    def rName = data.roomName
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    def targetVol = settings["roomVolumeGN_${rNum}"] != null ? settings["roomVolumeGN_${rNum}"] : settings["roomVolume_${rNum}"]
    
    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume 
    }
    
    if (targetSpeaker) {
        // --- Perimeter Check Logic ---
        if (settings["roomPerimeterCheck_${rNum}"]) {
            def unsecured = []
            settings.perimeterLocks?.each { if (it.currentValue("lock") == "unlocked") unsecured << "the ${it.displayName} is unlocked" }
            settings.perimeterContacts?.each { if (it.currentValue("contact") == "open") unsecured << "the ${it.displayName} is open" }
            settings.coopDoors?.each { if (it.currentValue("contact") == "open") unsecured << "the ${it.displayName} is open" }
            
            def perimeterReport = ""
            if (unsecured.size() > 0) {
                if (unsecured.size() == 1) {
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured[0]}."
                } else if (unsecured.size() == 2) {
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured[0]} and ${unsecured[1]}."
                } else {
                    def last = unsecured.pop()
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured.join(', ')}, and ${last}."
                }
            } else if (settings.perimeterLocks || settings.perimeterContacts || settings.coopDoors) {
                perimeterReport = " The perimeter is secure, and all coops are closed."
            }
            
            if (perimeterReport) finalMsg = finalMsg + perimeterReport
        }
        // -----------------------------
        
        def wDevice = settings["roomWeatherDevice_${rNum}"]
        if (wDevice && settings["roomWeatherGN_${rNum}"]) {
            def wText = getWeatherReport(wDevice)
            if (wText) {
                finalMsg = finalMsg + " " + wText
            }
        }
        
        enqueueTTS(targetSpeaker, finalMsg, targetVol, 4)
        addToHistory("ROOM GREETING: Good Night sequence triggered in ${rName}. Queued: '${finalMsg}'")
    }
}

def goodNightOffHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (settings.numRooms as Integer ?: 1); i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == deviceId) {
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
            
            if (mode == "1. Immediate (When Good Night Switch turns OFF)") {
                triggerGoodMorningSequence(i)
            } else if (mode == "2. Verified (Wait for switch OFF, then wait for Motion)") {
                state.waitingForMotion["${i}"] = true
                addToHistory("SYSTEM: Good Night switch OFF in ${rName}. Armed and waiting for motion activity.")
            } else if (mode == "3. Motion Driven (Trigger when Motion activates while switch is ON)") {
                // Fallback: If switch is turned off manually before motion is triggered
                if (!state.roomAlreadyAwake["${i}"]) {
                    state.roomAlreadyAwake["${i}"] = true
                    triggerGoodMorningSequence(i)
                }
            }
            return
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
            "Good morning %name%. %butler% is ready for the day.",
            "Hello %name%. The morning routine has begun.",
            "Good morning %name%. I have disarmed the night perimeter.",
            "Good morning %name%. I hope you have a productive day.",
            "Rise and shine %name%. Waking sequence complete.",
            "Good morning %name%. All night-time automations have concluded.",
            "Hello %name%. %butler% has prepared the house for your morning."
        ]
        rawMsg = defaults[new Random().nextInt(defaults.size())]
    }
    
    def displayOccName = applyAlias(occName)
    def finalMsg = rawMsg.replace("%name%", displayOccName).replace("%room%", rName)
    
    def bdayMsg = getBirthdayMessage(occName, "Morning")
    if (bdayMsg) finalMsg = "${finalMsg} ${bdayMsg}"

    if (settings["roomEnableAnniversary_${i}"]) {
        def annivMsg = getAnniversaryMessage("Morning")
        if (annivMsg) finalMsg = "${finalMsg} ${annivMsg}"
    }

    if (settings.enableHolidays) {
        def holiday = getTodayHoliday()
        if (holiday) {
            def hMsg = settings.holidayMessage ?: "By the way, don't forget today is %holiday%!"
            finalMsg = "${finalMsg} ${hMsg.replace('%holiday%', holiday)}"
        }
    }
    
    finalMsg = applyDynamicVars(finalMsg)
    
    runIn(delaySec, "scheduleGoodMorningSequence", [data: [roomNum: i, message: finalMsg, roomName: rName], overwrite: false])
}

def scheduleGoodMorningSequence(data) {
    def rNum = data.roomNum
    def finalMsg = data.message
    def rName = data.roomName
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    def targetVol = settings["roomVolumeGM_${rNum}"] != null ? settings["roomVolumeGM_${rNum}"] : settings["roomVolume_${rNum}"]
    
    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume 
    }
    
    if (targetSpeaker) {
        def wDevice = settings["roomWeatherDevice_${rNum}"]
        if (wDevice && settings["roomWeatherGM_${rNum}"]) {
            def wText = getWeatherReport(wDevice)
            if (wText) {
                finalMsg = finalMsg + " " + wText
            }
        }
        
        // --- NEW: Kid-Focused Wardrobe Advisor ---
        if (settings["roomWardrobe_${rNum}"] && wDevice) {
            try {
                def tempStr = wDevice.currentValue("temperature")
                if (tempStr != null) {
                    def t = tempStr.toString().replaceAll("[^0-9.-]", "").toFloat().toInteger()
                    if (t < 40) finalMsg += " It is freezing out there at ${t} degrees! Make sure to bundle up with your heavy winter coat, hat, and gloves today."
                    else if (t < 55) finalMsg += " It is quite chilly today at ${t} degrees. A warm jacket and long pants are a great idea."
                    else if (t < 70) finalMsg += " The weather is nice and cool at ${t} degrees. A light jacket or a cozy sweater will be perfect for school."
                    else if (t < 85) finalMsg += " It is a beautiful and warm ${t} degrees outside! Shorts and a t-shirt are the way to go."
                    else finalMsg += " It is going to be a scorcher today at ${t} degrees! Dress lightly and do not forget to drink plenty of water."
                }
            } catch(e) { log.error "Wardrobe Advisor Error: ${e}" }
        }

        // --- NEW: Weekend Boredom Buster ---
        if (settings["roomBoredomBuster_${rNum}"]) {
            def cal = Calendar.getInstance(location.timeZone)
            def dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                if (wDevice) {
                    try {
                        def tempStr = wDevice.currentValue("temperature")
                        def condStr = wDevice.currentValue("weather")?.toString()?.toLowerCase() ?: ""
                        if (tempStr != null) {
                            def t = tempStr.toString().replaceAll("[^0-9.-]", "").toFloat().toInteger()
                            def isBadWeather = (t < 50 || condStr.contains("rain") || condStr.contains("storm") || condStr.contains("snow"))
                            if (isBadWeather) {
                                finalMsg += " Since the weather is not great today, it looks like a perfect day for a board game, building a fort, or watching a movie."
                            } else {
                                finalMsg += " It is beautiful outside today! %butler% recommends playing in the yard, riding your bike, or going for a walk."
                            }
                        }
                    } catch(e) {}
                } else {
                     finalMsg += " Enjoy your weekend! It's a great day to find a fun activity."
                }
            }
        }

        // --- NEW: Top Headlines News Fetcher ---
        if (settings["roomNewsEnable_${rNum}"]) {
            def feedUrl = settings["roomNewsFeed_${rNum}"] ?: "https://feeds.npr.org/1001/rss.xml"
            try {
                httpGet([uri: feedUrl, timeout: 5]) { resp ->
                    if (resp.status == 200 && resp.data) {
                        def rssText = resp.data.text
                        def rss = new XmlSlurper().parseText(rssText)
                        def items = rss.channel.item
                        if (items.size() >= 2) {
                            def title1 = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                            def title2 = items[1].title.text().trim().replace("&", "and").replace("\"", "")
                            finalMsg += " Here is your morning news briefing. ${title1}. In other news, ${title2}."
                        }
                    }
                }
            } catch (e) {
                log.warn "Voice Butler: Failed to fetch news for Room ${rNum} - ${e}"
            }
        }

        // --- NEW: Daily Agenda Gentle Nudge ---
        if (settings["roomAgendaEnable_${rNum}"]) {
            def dow = new Date().format("EEEE", location.timeZone)
            def agendaText = settings["roomAgenda${dow}_${rNum}"]
            if (agendaText) {
                finalMsg += " As a gentle reminder, today is ${dow}. ${agendaText}"
            }
        }

        // --- NEW: Junior Concierge (Jokes/Facts) ---
        if (settings["roomKidsMode_${rNum}"]) {
            def funList = [
                "Did you know that a shrimp's heart is located in its head?",
                "Here is a joke for you: Why did the scarecrow win an award? Because he was outstanding in his field!",
                "Did you know that honey never spoils? Archaeologists have found pots of honey that are over three thousand years old!",
                "Here is a joke: What do you call a fake noodle? An impasta!",
                "Did you know that octopuses have three hearts and blue blood?",
                "Here is a joke for you: Why can't you give Elsa a balloon? Because she will let it go!",
                "Did you know that cows have best friends and get stressed when they are separated?",
                "Here is a joke: What falls, but never breaks? Nightfall!",
                "Did you know that a day on Venus is longer than a year on Venus?",
                "Here is a joke for you: Why did the math book look sad? Because it had too many problems."
            ]
            finalMsg += " " + funList[new Random().nextInt(funList.size())]
        }
        
        enqueueTTS(targetSpeaker, finalMsg, targetVol, 4)
        addToHistory("ROOM GREETING: Good Morning sequence triggered in ${rName}. Queued: '${finalMsg}'")
    }
}

def testDepartureGreeting(int idx) {
    if (outdoorSpeaker) {
        def uName = settings["depUserName_${idx}"] ?: "Guest"
        def displayUserName = applyAlias(uName)
        def messages = []
        for (int m = 1; m <= 10; m++) {
            def msg = settings["depMessage_${idx}_${m}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Have a good trip %name%."]
        
        def rawMsg = messages[new Random().nextInt(messages.size())]
        def finalMsg = rawMsg.replace("%name%", displayUserName)
        
        def bdayMsg = getBirthdayMessage(uName, "Departure")
        if (bdayMsg) {
            finalMsg = "${finalMsg} ${bdayMsg}"
        }
        
        def annivMsg = getAnniversaryMessage("Departure", uName)
        if (annivMsg) {
            finalMsg = "${finalMsg} ${annivMsg}"
        }
        
        finalMsg = applyDynamicVars(finalMsg)
        
        def profileVol = settings["depVolume_${idx}"]
        def targetVolume = profileVol != null ? profileVol : (settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"])
        def volLog = targetVolume != null ? "${targetVolume}%" : "Hardware Default"
        
        log.info "TESTING DEPARTURE GREETING (Profile ${idx}): '${finalMsg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, finalMsg, targetVolume, 1)
    } else {
        log.warn "Cannot test Departure greeting - no outdoor speaker assigned."
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
                "Good night %name%. %butler% will keep watch.",
                "Rest easy %name%. Systems are entering night mode.",
                "Good night %name%. Waking protocols are set for tomorrow.",
                "Sleep well %name%. %butler% is shutting down the house."
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
                "Good morning %name%. %butler% is ready for the day.",
                "Hello %name%. The morning routine has begun.",
                "Good morning %name%. I have disarmed the night perimeter.",
                "Good morning %name%. I hope you have a productive day.",
                "Rise and shine %name%. Waking sequence complete.",
                "Good morning %name%. All night-time automations have concluded.",
                "Hello %name%. %butler% has prepared the house for your morning."
            ]
            rawMsg = defaults[new Random().nextInt(defaults.size())]
        }
    }

    def displayOccName = applyAlias(occName)
    def finalMsg = rawMsg.replace("%name%", displayOccName).replace("%room%", rName)
    def bdayMsg = getBirthdayMessage(occName, type == "Good Night" ? "Night" : "Morning")
    if (bdayMsg) finalMsg = "${finalMsg} ${bdayMsg}"

    if (settings["roomEnableAnniversary_${i}"]) {
        def annivMsg = getAnniversaryMessage(type == "Good Night" ? "Night" : "Morning")
        if (annivMsg) finalMsg = "${finalMsg} ${annivMsg}"
    }

    if (settings.enableHolidays && type != "Good Night") {
        def holiday = getTodayHoliday() ?: "a Holiday" 
        def hMsg = settings.holidayMessage ?: "By the way, don't forget today is %holiday%!"
        finalMsg = "${finalMsg} ${hMsg.replace('%holiday%', holiday)}"
    }
    
    finalMsg = applyDynamicVars(finalMsg)
    
    def targetSpeaker = settings["roomSpeaker_${i}"]
    def volSetting = type == "Good Night" ? settings["roomVolumeGN_${i}"] : settings["roomVolumeGM_${i}"]
    def targetVol = volSetting != null ? volSetting : settings["roomVolume_${i}"]

    if (!targetSpeaker && globalIndoorSpeaker) {
        targetSpeaker = globalIndoorSpeaker
        targetVol = globalVolume
    }

    if (targetSpeaker) {
        // --- Perimeter Check Logic ---
        if (settings["roomPerimeterCheck_${i}"]) {
            def unsecured = []
            settings.perimeterLocks?.each { if (it.currentValue("lock") == "unlocked") unsecured << "the ${it.displayName} is unlocked" }
            settings.perimeterContacts?.each { if (it.currentValue("contact") == "open") unsecured << "the ${it.displayName} is open" }
            settings.coopDoors?.each { if (it.currentValue("contact") == "open") unsecured << "the ${it.displayName} is open" }
            
            def perimeterReport = ""
            if (unsecured.size() > 0) {
                if (unsecured.size() == 1) {
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured[0]}."
                } else if (unsecured.size() == 2) {
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured[0]} and ${unsecured[1]}."
                } else {
                    def last = unsecured.pop()
                    perimeterReport = " Before you go to sleep, I must warn you that ${unsecured.join(', ')}, and ${last}."
                }
            } else if (settings.perimeterLocks || settings.perimeterContacts || settings.coopDoors) {
                perimeterReport = " The perimeter is secure, and all coops are closed."
            }
            
            if (perimeterReport) finalMsg = finalMsg + perimeterReport
        }
        // -----------------------------
        
        def wDevice = settings["roomWeatherDevice_${i}"]
        def appendWeather = type == "Good Night" ? settings["roomWeatherGN_${i}"] : settings["roomWeatherGM_${i}"]
        
        if (wDevice && appendWeather) {
            def wText = getWeatherReport(wDevice)
            if (wText) {
                finalMsg = finalMsg + " " + wText
            }
        }
        
        if (type == "Good Morning") {
            // --- NEW: Kid-Focused Wardrobe Advisor ---
            if (settings["roomWardrobe_${i}"] && wDevice) {
                try {
                    def tempStr = wDevice.currentValue("temperature")
                    if (tempStr != null) {
                        def t = tempStr.toString().replaceAll("[^0-9.-]", "").toFloat().toInteger()
                        if (t < 40) finalMsg += " It is freezing out there at ${t} degrees! Make sure to bundle up with your heavy winter coat, hat, and gloves today."
                        else if (t < 55) finalMsg += " It is quite chilly today at ${t} degrees. A warm jacket and long pants are a great idea."
                        else if (t < 70) finalMsg += " The weather is nice and cool at ${t} degrees. A light jacket or a cozy sweater will be perfect for school."
                        else if (t < 85) finalMsg += " It is a beautiful and warm ${t} degrees outside! Shorts and a t-shirt are the way to go."
                        else finalMsg += " It is going to be a scorcher today at ${t} degrees! Dress lightly and do not forget to drink plenty of water."
                    }
                } catch(e) {}
            }

            // --- NEW: Weekend Boredom Buster ---
            if (settings["roomBoredomBuster_${i}"]) {
                def cal = Calendar.getInstance(location.timeZone)
                def dow = cal.get(Calendar.DAY_OF_WEEK)
                if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                    if (wDevice) {
                        try {
                            def tempStr = wDevice.currentValue("temperature")
                            def condStr = wDevice.currentValue("weather")?.toString()?.toLowerCase() ?: ""
                            if (tempStr != null) {
                                def t = tempStr.toString().replaceAll("[^0-9.-]", "").toFloat().toInteger()
                                def isBadWeather = (t < 50 || condStr.contains("rain") || condStr.contains("storm") || condStr.contains("snow"))
                                if (isBadWeather) {
                                    finalMsg += " Since the weather is not great today, it looks like a perfect day for a board game, building a fort, or watching a movie."
                                } else {
                                    finalMsg += " It is beautiful outside today! %butler% recommends playing in the yard, riding your bike, or going for a walk."
                                }
                            }
                        } catch(e) {}
                    } else {
                         finalMsg += " Enjoy your weekend! It's a great day to find a fun activity."
                    }
                }
            }

            // --- NEW: Top Headlines News Fetcher ---
            if (settings["roomNewsEnable_${i}"]) {
                def feedUrl = settings["roomNewsFeed_${i}"] ?: "https://feeds.npr.org/1001/rss.xml"
                try {
                    httpGet([uri: feedUrl, timeout: 5]) { resp ->
                        if (resp.status == 200 && resp.data) {
                            def rssText = resp.data.text
                            def rss = new XmlSlurper().parseText(rssText)
                            def items = rss.channel.item
                            if (items.size() >= 2) {
                                def title1 = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                                def title2 = items[1].title.text().trim().replace("&", "and").replace("\"", "")
                                finalMsg += " Here is your morning news briefing. ${title1}. In other news, ${title2}."
                            }
                        }
                    }
                } catch (e) {
                    log.warn "Voice Butler: Failed to fetch news for test - ${e}"
                }
            }

            // --- NEW: Daily Agenda Gentle Nudge ---
            if (settings["roomAgendaEnable_${i}"]) {
                def dow = new Date().format("EEEE", location.timeZone)
                def agendaText = settings["roomAgenda${dow}_${i}"]
                if (agendaText) {
                    finalMsg += " As a gentle reminder, today is ${dow}. ${agendaText}"
                }
            }

            // --- NEW: Junior Concierge (Jokes/Facts) ---
            if (settings["roomKidsMode_${i}"]) {
                def funList = [
                    "Did you know that a shrimp's heart is located in its head?",
                    "Here is a joke for you: Why did the scarecrow win an award? Because he was outstanding in his field!",
                    "Did you know that honey never spoils? Archaeologists have found pots of honey that are over three thousand years old!",
                    "Here is a joke: What do you call a fake noodle? An impasta!",
                    "Did you know that octopuses have three hearts and blue blood?",
                    "Here is a joke for you: Why can't you give Elsa a balloon? Because she will let it go!",
                    "Did you know that cows have best friends and get stressed when they are separated?",
                    "Here is a joke: What falls, but never breaks? Nightfall!",
                    "Did you know that a day on Venus is longer than a year on Venus?",
                    "Here is a joke for you: Why did the math book look sad? Because it had too many problems."
                ]
                finalMsg += " " + funList[new Random().nextInt(funList.size())]
            }
        }
        
        def volLog = targetVol != null ? "${targetVol}%" : "Hardware Default"
        log.info "TESTING ${type} GREETING FOR ${rName}: '${finalMsg}' at ${volLog} volume."
        enqueueTTS(targetSpeaker, finalMsg, targetVol, 1)
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
        randomMsg = applyDynamicVars(randomMsg)
        
        def volLog = outdoorVolume != null ? "${outdoorVolume}%" : "Hardware Default"
        log.info "TESTING DND GREETING: '${randomMsg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, randomMsg, outdoorVolume, 1, true)
    } else {
        log.warn "Cannot test DND greeting - no outdoor speaker assigned."
    }
}

def testAfterHoursGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 15; d++) {
            def msg = settings["afterHoursMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["It is currently after hours, the homeowners are unavailable."]
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.afterHoursVolume != null ? settings.afterHoursVolume : settings.outdoorVolume
        def volLog = targetVol != null ? "${targetVol}%" : "Hardware Default"
        
        log.info "TESTING AFTER HOURS GREETING: '${randomMsg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 1, true)
    } else {
        log.warn "Cannot test After Hours greeting - no outdoor speaker assigned."
    }
}

def testDaytimeGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 20; d++) {
            def msg = settings["daytimeMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Please wait a moment, I am notifying the homeowner."]
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume
        def volLog = targetVol != null ? "${targetVol}%" : "Hardware Default"
        
        log.info "TESTING DAYTIME GREETING: '${randomMsg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 1, true)
    } else {
        log.warn "Cannot test Daytime greeting - no outdoor speaker assigned."
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
        def displayUserName = applyAlias(testName)
        def greetingToPlay = rawMsg.replace("%name%", displayUserName)
        
        def bdayMsg = getBirthdayMessage(testName, "Arrival")
        if (bdayMsg) {
            greetingToPlay = "${greetingToPlay} ${bdayMsg}"
        }
        
        def annivMsg = getAnniversaryMessage("Arrival", testName)
        if (annivMsg) {
            greetingToPlay = "${greetingToPlay} ${annivMsg}"
        }
        
        greetingToPlay = applyDynamicVars(greetingToPlay)
        
        def outdoorTargetVol = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        def indoorTargetVol = settings["arrivalIndoorVolume"] != null ? settings["arrivalIndoorVolume"] : settings["globalVolume"]
        
        def played = false
        if (outdoorSpeaker) {
            log.info "TESTING OUTDOOR ARRIVAL: '${greetingToPlay}' at ${outdoorTargetVol ?: 'Hardware Default'} volume."
            enqueueTTS(outdoorSpeaker, greetingToPlay, outdoorTargetVol, 1, true)
            played = true
        }
        if (settings.arrivalIndoorSpeaker && globalIndoorSpeaker) {
            log.info "TESTING INDOOR ARRIVAL: '${greetingToPlay}' at ${indoorTargetVol ?: 'Hardware Default'} volume."
            enqueueTTS(globalIndoorSpeaker, greetingToPlay, indoorTargetVol, 1, true)
            played = true
        }
        
        if (!played) {
            log.warn "Cannot test Arrival greeting - no speakers are configured to play it."
        }
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
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
        def volLog = targetVol != null ? "${targetVol}%" : "Hardware Default"
        
        log.info "TESTING INTRUDER GREETING: '${randomMsg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, randomMsg, targetVol, 1)
    } else {
        log.warn "Cannot test Intruder greeting - no outdoor speaker assigned."
    }
}


// --- CALENDAR & HISTORY HELPERS ---

def getTodayHoliday() {
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    int m = cal.get(Calendar.MONTH) + 1
    int d = cal.get(Calendar.DAY_OF_MONTH)
    int dow = cal.get(Calendar.DAY_OF_WEEK)
    int wom = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH)
    
    // Static Date Holidays
    if (m == 1 && d == 1) return "New Year's Day"
    if (m == 2 && d == 2) return "Groundhog Day"
    if (m == 2 && d == 14) return "Valentine's Day"
    if (m == 3 && d == 17) return "Saint Patrick's Day"
    if (m == 4 && d == 1) return "April Fools' Day"
    if (m == 4 && d == 22) return "Earth Day"
    if (m == 5 && d == 5) return "Cinco de Mayo"
    if (m == 6 && d == 14) return "Flag Day"
    if (m == 6 && d == 19) return "Juneteenth"
    if (m == 7 && d == 4) return "Independence Day"
    if (m == 9 && d == 11) return "Patriot Day"
    if (m == 10 && d == 31) return "Halloween"
    if (m == 11 && d == 11) return "Veterans Day"
    if (m == 12 && d == 24) return "Christmas Eve"
    if (m == 12 && d == 25) return "Christmas Day"
    if (m == 12 && d == 31) return "New Year's Eve"
    
    // Dynamic / Floating US Holidays
    if (m == 1 && dow == Calendar.MONDAY && wom == 3) return "Martin Luther King Jr. Day"
    if (m == 2 && dow == Calendar.MONDAY && wom == 3) return "Presidents' Day"
    if (m == 5 && dow == Calendar.SUNDAY && wom == 2) return "Mother's Day"
    if (m == 5 && dow == Calendar.MONDAY && (d + 7 > 31)) return "Memorial Day"
    if (m == 6 && dow == Calendar.SUNDAY && wom == 3) return "Father's Day"
    if (m == 9 && dow == Calendar.MONDAY && wom == 1) return "Labor Day"
    if (m == 10 && dow == Calendar.MONDAY && wom == 2) return "Columbus Day"
    if (m == 11 && dow == Calendar.TUESDAY && d >= 2 && d <= 8) return "Election Day"
    if (m == 11 && dow == Calendar.THURSDAY && wom == 4) return "Thanksgiving"
    if (m == 11 && dow == Calendar.FRIDAY && d >= 23 && d <= 29) return "Black Friday"
    
    return null
}

def getAnniversaryMessage(String type, String userName = "") {
    if (!settings.enableAnniversary || !settings.annivMonth || !settings.annivDay) return ""
    
    if (userName && (type == "Arrival" || type == "Departure")) {
        def allowedUsers = [settings.annivAllowedUsers].flatten().findAll{it}.collect{it.toLowerCase()}
        if (settings.annivAllowedCustom) allowedUsers += settings.annivAllowedCustom.split(',').collect{it.trim().toLowerCase()}
        
        if (!allowedUsers.isEmpty() && !allowedUsers.contains(userName.toLowerCase())) {
            return "" 
        }
    }
    
    def now = new Date()
    def currentMonth = now.format("MM", location.timeZone).toInteger()
    def currentDay = now.format("dd", location.timeZone).toInteger()

    if (settings.annivMonth.toInteger() == currentMonth && settings.annivDay.toInteger() == currentDay) {
        def rawMsg = ""
        switch(type) {
            case "Arrival": rawMsg = settings.annivMsgArrival ?: "Happy Anniversary! Welcome home."; break
            case "Departure": rawMsg = settings.annivMsgDeparture ?: "Have a wonderful anniversary today!"; break
            case "Morning": rawMsg = settings.annivMsgMorning ?: "Happy Anniversary! I hope you both have a fantastic day."; break
            case "Night": rawMsg = settings.annivMsgNight ?: "Happy Anniversary. Sleep well, I will keep the perimeter secure."; break
        }
        return rawMsg
    }
    return ""
}

def getBirthdayMessage(String name, String type) {
    if (!name || !settings.numBirthdays) return ""
    
    def numBdays = settings.numBirthdays as Integer
    if (numBdays <= 0) return ""
    
    def now = new Date()
    def currentMonth = now.format("MM", location.timeZone).toInteger()
    def currentDay = now.format("dd", location.timeZone).toInteger()

    for (int i = 1; i <= numBdays; i++) {
        def bName = settings["bdayName_${i}"]
        def bMonth = settings["bdayMonth_${i}"]
        def bDay = settings["bdayDay_${i}"]
        
        if (bName && bMonth && bDay && bName.toLowerCase() == name.toLowerCase()) {
            if (bMonth.toInteger() == currentMonth && bDay.toInteger() == currentDay) {
                def rawMsg = ""
                switch(type) {
                    case "Arrival": rawMsg = settings.bdayMsgArrival ?: "Happy Birthday %name%!"; break
                    case "Departure": rawMsg = settings.bdayMsgDeparture ?: "Have a wonderful birthday today, %name%!"; break
                    case "Morning": rawMsg = settings.bdayMsgMorning ?: "Happy Birthday %name%! I hope you have a fantastic day."; break
                    case "Night": rawMsg = settings.bdayMsgNight ?: "Happy Birthday %name%. I hope you had a wonderful day."; break
                }
                return rawMsg.replace("%name%", name)
            }
        }
    }
    return ""
}

def midnightReset() {
    ensureStateMaps()
    
    def stayHomeList = [settings.stayAtHomeUsers].flatten().findAll{it}
    if (settings.stayAtHomeCustom) {
        stayHomeList += settings.stayAtHomeCustom.split(',').collect{ it.trim() }
    }
    
    def newHasArrived = [:]
    def newResetReasons = [:]
    
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
    
    // Purge queue nightly to fix any potential stuck states
    state.ttsQueue = []
    state.speakingUntil = 0
    state.currentPriority = 99
    state.originalVolumes = [:] // Safety net
    
    addToHistory("SYSTEM: Midnight Reset completed. Daily statuses cleared and Queue flushed.")
}

def appButtonHandler(btn) {
    ensureStateMaps()
    
    if (btn != "btnRefresh") {
        addToHistory("SYSTEM: Action triggered via UI Dashboard - ${btn}")
    }
    
    if (btn == "btnForceReset") {
        state.hasArrivedToday = [:]
        state.hasDepartedToday = [:]
        state.resetReasons = [:]
        state.globalResetReason = "Reset manually via Dashboard"
        state.lastDepartureTime = [:]
        state.ttsQueue = []
        state.speakingUntil = 0
        state.currentPriority = 99
        addToHistory("SYSTEM: Manual reset of all Daily statuses triggered. TTS Queue Flushed.")
    } else if (btn == "btnRefresh") {
        log.info "Voice Butler Refresh Triggered."
    } else if (btn == "btnQuickSave") {
        log.info "Voice Butler Quick Save Triggered."
    } else if (btn == "btnTestGlobal") {
        def msg = applyDynamicVars("This is a test of the global indoor speakers. The time is %time%.")
        def volLog = globalVolume != null ? "${globalVolume}%" : "Hardware Default"
        log.info "TESTING GLOBAL SPEAKER: '${msg}' at ${volLog} volume."
        enqueueTTS(globalIndoorSpeaker, msg, globalVolume, 1)
    } else if (btn == "btnTestOutdoor") {
        def msg = applyDynamicVars("This is a test of the outdoor speaker. The date is %date%.")
        def volLog = outdoorVolume != null ? "${outdoorVolume}%" : "Hardware Default"
        log.info "TESTING OUTDOOR SPEAKER: '${msg}' at ${volLog} volume."
        enqueueTTS(outdoorSpeaker, msg, outdoorVolume, 1)
    } else if (btn == "btnTestIndoorRouting") {
        def routeMsg = settings.indoorDoorbellMsg ?: "This is %butler%. Pardon the interruption, but there is a visitor at the front door."
        routeMsg = applyDynamicVars(routeMsg)
        def anyRouted = false
        def numRoutes = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
        for (int i = 1; i <= numRoutes; i++) {
            def mSensors = [settings["routeMotion_${i}"]].flatten().findAll{it}
            def spk = settings["routeSpeaker_${i}"]
            if (mSensors && spk) {
                def isActive = mSensors.any { it.currentValue("motion") == "active" }
                if (isActive) {
                    def vol = settings["routeVolume_${i}"]
                    enqueueTTS(spk, routeMsg, vol, 1) 
                    anyRouted = true
                    def rName = settings["routeRoomName_${i}"] ?: "Zone ${i}"
                    log.info "TESTING INDOOR ROUTING: Motion active in ${rName}. Announcing there."
                }
            }
        }
        if (!anyRouted) {
            if (settings.indoorRouteFallback && globalIndoorSpeaker) {
                enqueueTTS(globalIndoorSpeaker, routeMsg, globalVolume, 1)
                log.info "TESTING INDOOR ROUTING: No active motion found. Announcing on Global fallback."
            } else {
                log.warn "TESTING INDOOR ROUTING: No active motion found and fallback is disabled (or no global speaker assigned). Silence."
            }
        }
    } else if (btn == "btnTestScreenTime") {
        def messages = []
        for (int d = 1; d <= 5; d++) {
            if (settings["screenTimeMsg_${d}"]) messages << settings["screenTimeMsg_${d}"]
        }
        if (!messages) messages = ["Pardon the interruption, but the daily screen time allotment has expired. Please power down the device."]
        def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
        def targetSpeaker = settings.screenTimeSpeaker ?: globalIndoorSpeaker
        def targetVol = settings.screenTimeVolume ?: globalVolume
        if (targetSpeaker) {
            enqueueTTS(targetSpeaker, randomMsg, targetVol, 1)
            log.info "TESTING SCREEN TIME: '${randomMsg}'"
        } else {
            log.warn "Cannot test Screen Time - no speaker assigned."
        }
    } else if (btn.startsWith("btnTestRoomSpk_")) {
        def rNum = btn.split("_")[1].toInteger()
        def targetSpeaker = settings["roomSpeaker_${rNum}"] ?: globalIndoorSpeaker
        def rName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
        def msg = applyDynamicVars("Testing the speaker connection for ${rName}.")
        def targetVol = settings["roomVolumeGN_${rNum}"] != null ? settings["roomVolumeGN_${rNum}"] : settings["roomVolume_${rNum}"]
        def volLog = targetVol != null ? "${targetVol}%" : "Hardware Default"
        log.info "TESTING ROOM SPEAKER (${rName}): '${msg}' at ${volLog} volume."
        enqueueTTS(targetSpeaker, msg, targetVol, 1)
    } else if (btn.startsWith("btnTestDeparture_")) {
        def idx = btn.split("_")[1].toInteger()
        testDepartureGreeting(idx)
    } else if (btn.startsWith("btnResetDepMsgs_")) {
        def idx = btn.split("_")[1].toInteger()
        resetDepartureMessages(idx)
    } else if (btn.startsWith("btnTestGN_")) {
        def rNum = btn.split("_")[1].toInteger()
        testRoomGreeting(rNum, "Good Night")
    } else if (btn.startsWith("btnTestGM_")) {
        def rNum = btn.split("_")[1].toInteger()
        testRoomGreeting(rNum, "Good Morning")
    } else if (btn == "btnTestDND") {
        testDndGreeting()
    } else if (btn == "btnTestAfterHours") {
        testAfterHoursGreeting()
    } else if (btn == "btnTestDaytime") {
        testDaytimeGreeting()
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
