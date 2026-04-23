/**
 * Advanced Chicken Coop Management
 */
definition(
    name: "Advanced Chicken Coop Management",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Complete homestead flock management: climate, security, lighting, feed/water tracking, health guardian, maintenance logs, and production analytics.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
    page(name: "flockPage")
    page(name: "feedPage")
    page(name: "doorPage")
    page(name: "climatePage")
    page(name: "waterPage")
    page(name: "lightPage")
    page(name: "securityPage")
    page(name: "notificationPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Coop Command Center", install: true, uninstall: true) {
        
        section("Global Settings") {
            input "coopCount", "enum", title: "Number of Coops to Manage", options: ["1", "2", "3"], defaultValue: "1", submitOnChange: true
            input "masterEnable", "bool", title: "Enable Coop Management System", defaultValue: true
            input "manualMode", "bool", title: "Enable Manual Mode (Alerts Only, Suspends Automation)", defaultValue: false, submitOnChange: true, description: "Turns off relays and motors. The app will strictly monitor sensors and send you reminders."
            input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false
        }

        section("Solar Tracking", hideable: false) {
            def sunDisplay = "<div style='text-align:center; padding: 10px; background-color: #2c3e50; color: white; border-radius: 5px; font-size: 16px; font-weight: bold;'>${getSunCountdown()}</div>"
            paragraph sunDisplay
        }

        def count = (settings.coopCount ?: "1") as Integer

        for (int i = 1; i <= count; i++) {
            def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
            def isMolting = settings["moltingMode_${i}"] ?: false
            
            section("${coopName} Dashboard", hideable: true, hidden: false) {
                def dash = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-top: 10px;'>"
                dash += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>System</th><th style='padding: 8px;'>Status</th></tr>"
                
                // Mode Status
                def modeStatus = masterEnable ? (manualMode ? "<span style='color:#e67e22; font-weight:bold;'>MANUAL (Alerts Only)</span>" : "<span style='color:green; font-weight:bold;'>AUTOMATIC</span>") : "<span style='color:red;'>DISABLED</span>"
                if (isMolting) modeStatus += " <span style='background-color:#8e44ad; color:white; padding: 2px 6px; border-radius: 4px; font-size: 11px;'>🪶 MOLTING (REST) MODE</span>"
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>⚙️ System Mode</b></td><td style='padding: 8px;'>${modeStatus}</td></tr>"

                // Door Status with Contact Sensor
                def dDoor = settings["coopDoor_${i}"]
                def dContact = settings["doorContact_${i}"]
                def doorStatus = dDoor ? dDoor.currentValue("door") : "Manual / Not Automated"
                def dColor = doorStatus == "closed" ? "green" : (doorStatus == "open" ? "#e67e22" : "#333")
                
                def doorDisplay = "Motor: <span style='color:${dColor};'>${doorStatus?.toUpperCase()}</span>"
                if (dContact) {
                    def dHealthy = isSensorHealthy(dContact, settings.sensorHealthHours)
                    def contactStatus = dContact.currentValue("contact")
                    def cColor = contactStatus == "closed" ? "green" : (contactStatus == "open" ? "#e67e22" : "#333")
                    def healthWarning = dHealthy ? "" : " <span style='color:red; font-weight:bold;'>[⚠️ OFFLINE/STALE]</span>"
                    doorDisplay += "<br><span style='font-size:11px; color:#555;'>Sensor: <span style='color:${cColor}; font-weight:bold;'>${contactStatus?.toUpperCase()}</span>${healthWarning}</span>"
                } else {
                    doorDisplay += "<br><span style='font-size:11px; color:#555;'>Sensor: Unmonitored</span>"
                }
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🚪 Coop Door</b></td><td style='padding: 8px; font-weight: bold;'>${doorDisplay}</td></tr>"
                
                // Weather Station & Wind
                def dOutTemp = settings["outdoorTempSensor_${i}"]
                def dOutHum = settings["outdoorHumSensor_${i}"]
                def dAqi = settings["aqiSensor_${i}"]
                def dWind = settings["windSensor_${i}"]
                
                def outTemp = dOutTemp ? dOutTemp.currentValue("temperature") : "--"
                def outHum = dOutHum ? dOutHum.currentValue("humidity") : "--"
                def windSpd = dWind ? dWind.currentValue("windSpeed") : 0.0
                
                // Enhanced AQI Attribute Lookup
                def aqiVal = "--"
                if (dAqi) {
                    aqiVal = dAqi.currentValue("airQuality") ?: dAqi.currentValue("Aqi") ?: dAqi.currentValue("aqi") ?: dAqi.currentValue("Air Quality Index") ?: "--"
                }

                // Trend Fetching
                def tOutHigh = state["todayOutHigh_${i}"] ?: "--"
                def tOutLow = state["todayOutLow_${i}"] ?: "--"
                def yOutHigh = state["yesterdayOutHigh_${i}"] ?: "--"
                def yOutLow = state["yesterdayOutLow_${i}"] ?: "--"

                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🌤️ Weather</b></td><td style='padding: 8px;'>Out: ${outTemp}° | ${outHum}% RH | Wind: ${windSpd}mph | AQI: ${aqiVal}<br><span style='font-size:11px; color:#555;'>Trends &rarr; Today High/Low: <span style='color:#c0392b; font-weight:bold;'>${tOutHigh}°</span> / <span style='color:#2980b9; font-weight:bold;'>${tOutLow}°</span> | Yest: ${yOutHigh}° / ${yOutLow}°</span></td></tr>"

                // Climate & Health Guardian Engine (Dynamic Sensor Failover)
                def tData = getCoopTemperature(i)
                def hData = getCoopHumidity(i)
                
                def dFan = settings["exhaustFan_${i}"]
                def dHeat = settings["coopHeater_${i}"]

                def shadePct = settings["sunshadePercent_${i}"] ?: 0
                def shadeDisplay = (shadePct > 0 && isDaylight()) ? "<br><span style='font-size:11px; color:#f39c12;'>🌤️ Sunshade Active (-${shadePct}%)</span>" : ""

                def temp = tData.temp != null ? tData.temp.setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                def hum = hData.hum != null ? hData.hum.setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                def isEstimated = (tData.isEstimated || hData.isEstimated) ? " <span style='color: #e67e22; font-size: 11px; font-weight: bold;'>(Estimated)</span>" : ""

                // Wind Chill Calculation (Only applies if temperature is an outdoor estimate!)
                def windChillDisplay = ""
                def effectiveTempForDisplay = tData.temp
                if (tData.isEstimated && tData.temp != null && tData.temp <= 50 && windSpd >= 3) {
                    def wc = calculateWindChill(tData.temp, windSpd as BigDecimal)
                    effectiveTempForDisplay = wc
                    windChillDisplay = "<br><span style='color:#3498db; font-size:11px; font-weight:bold;'>❄️ Wind Chill: ${wc}°</span>"
                }

                def fanState = dFan ? dFan.currentValue("switch") : "off"
                def heatState = dHeat ? dHeat.currentValue("switch") : "off"
                def predDisplay = state["predictiveActive_${i}"] ? " <span style='color:#e74c3c; font-size:11px; font-weight:bold;'>🔥 PREDICTIVE HEATING</span>" : ""
                
                // Airflow Recommendations & Vent Status
                def openAirTemp = settings["manualAirflowTemp_${i}"] ?: 75
                def closeAirTemp = settings["closeAirflowTemp_${i}"] ?: 40
                def airflowRec = "<span style='color:green;'>OK (User Discretion)</span>"
                if (outTemp != "--") {
                    if ((outTemp as BigDecimal) >= openAirTemp) airflowRec = "<span style='color:red; font-weight:bold;'>OPEN DOORS/VENTS</span>"
                    else if ((outTemp as BigDecimal) <= closeAirTemp) airflowRec = "<span style='color:blue; font-weight:bold;'>CLOSE DOORS/VENTS</span>"
                }

                def vContact = settings["ventContact_${i}"]
                def ventStatusHtml = ""
                if (vContact) {
                    def vHealthy = isSensorHealthy(vContact, settings.sensorHealthHours)
                    def vState = vContact.currentValue("contact")
                    def vColor = vState == "closed" ? "green" : (vState == "open" ? "#e67e22" : "#333")
                    def vHealthWarning = vHealthy ? "" : " <span style='color:red; font-weight:bold;'>[⚠️ OFFLINE/STALE]</span>"
                    ventStatusHtml = "<br><span style='font-size:11px; color:#555;'>Vent Sensor: <span style='color:${vColor}; font-weight:bold;'>${vState?.toUpperCase()}</span>${vHealthWarning}</span>"
                }

                // Health Guardian
                def healthStatus = "<span style='color:green;'>Optimal</span>"
                if (temp != "--" && hum != "--") {
                    if ((temp as BigDecimal) <= 35 && (hum as BigDecimal) >= 70) healthStatus = "<span style='color:red; font-weight:bold;'>FROSTBITE RISK</span>"
                    if ((temp as BigDecimal) >= 85 && (hum as BigDecimal) >= 70) healthStatus = "<span style='color:red; font-weight:bold;'>HEAT STRESS RISK</span>"
                }
                
                // Predictive Health Index (Muted if Molting)
                def day3Avg = calculateAverage(i, 3)
                def day14Avg = calculateAverage(i, 14)
                if (!isMolting && day14Avg >= 1.0 && day3Avg < (day14Avg * 0.70)) {
                    healthStatus += "<br><span style='color:#e74c3c; font-weight:bold;'>⚠️ LAY-RATE DROP (${day3Avg} vs ${day14Avg} avg) - Check health/stress!</span>"
                }

                // Trend Fetching
                def tInHigh = state["todayInHigh_${i}"] ?: "--"
                def tInLow = state["todayInLow_${i}"] ?: "--"
                def yInHigh = state["yesterdayInHigh_${i}"] ?: "--"
                def yInLow = state["yesterdayInLow_${i}"] ?: "--"
                
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🌡️ Climate</b></td><td style='padding: 8px;'>In: ${temp}° | ${hum}% RH${isEstimated}${windChillDisplay}${shadeDisplay}<br><span style='font-size:11px; color:#555;'>Trends &rarr; Today High/Low: <span style='color:#c0392b; font-weight:bold;'>${tInHigh}°</span> / <span style='color:#2980b9; font-weight:bold;'>${tInLow}°</span> | Yest: ${yInHigh}° / ${yInLow}°</span><br><span style='font-size:11px; color:#555;'>Fan: ${fanState.toString().toUpperCase()} | Heater: ${heatState.toString().toUpperCase()}${predDisplay}</span><br>Airflow Rec: ${airflowRec}${ventStatusHtml}<br>Health: ${healthStatus}</td></tr>"
                
                // Virtual/Smart Water Status
                def dWaterFloat = settings["waterFloat_${i}"]
                def dWaterHeat = settings["waterHeater_${i}"]
                def wHeaterState = dWaterHeat ? dWaterHeat.currentValue("switch") : "off"
                def waterStateHtml = ""
                
                def vWater = (state["virtualWaterGal_${i}"] ?: 0.0) as BigDecimal
                def wCap = (settings["waterCapacity_${i}"] ?: 0.0) as BigDecimal
                def wPct = wCap > 0 ? Math.round((vWater / wCap) * 100) : 0
                def wColor = wPct <= 20 ? "red" : "green"
                def birdCount = settings["birdCount_${i}"] ?: 6
                def baseWaterPerBird = (settings["baseWaterPerBird_${i}"] != null) ? (settings["baseWaterPerBird_${i}"] as BigDecimal) : 0.13
                def estWaterDays = (birdCount * baseWaterPerBird) > 0 ? vWater / (birdCount * baseWaterPerBird) : 0
                def daysStr = estWaterDays > 0 ? "~${Math.round(estWaterDays)} days left" : "Empty"

                if (dWaterFloat) {
                    def fState = dWaterFloat.currentValue("water") == "dry" ? "OK" : "<span style='color:red;'>LOW / REFILLING</span>"
                    waterStateHtml = "Level: ${fState} (Sensor)<br><span style='font-size:11px; color:#555;'>Virtual Est: ${vWater} Gal | ${daysStr}</span>"
                } else if (settings["waterCapacity_${i}"]) {
                    waterStateHtml = "<span style='color:${wColor}; font-weight:bold;'>${vWater} Gal (~${wPct}%)</span> - ${daysStr}"
                } else {
                    waterStateHtml = "Manual / Unmonitored"
                }
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>💧 Hydration</b></td><td style='padding: 8px;'>${waterStateHtml} <br><span style='font-size:11px; color:#555;'>De-icer: ${wHeaterState.toString().toUpperCase()}</span></td></tr>"
                
                // Smart Feed Inventory
                def feedLbs = (state["virtualFeedLbs_${i}"] ?: 0.0) as BigDecimal
                def feedSize = (settings["feedBagSize_${i}"] ?: 0.0) as BigDecimal
                def feedPct = feedSize > 0 ? Math.round((feedLbs / feedSize) * 100) : 0
                def feedColor = feedPct <= 20 ? "red" : "green"
                def baseFeedPerBird = (settings["baseFeedPerBird_${i}"] != null) ? (settings["baseFeedPerBird_${i}"] as BigDecimal) : 0.25
                def estFeedDays = (birdCount * baseFeedPerBird) > 0 ? feedLbs / (birdCount * baseFeedPerBird) : 0
                def fDaysStr = estFeedDays > 0 ? "~${Math.round(estFeedDays)} days left" : "Empty"
                
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🌾 Feed Level</b></td><td style='padding: 8px;'><span style='color:${feedColor}; font-weight:bold;'>${feedLbs} lbs (~${feedPct}%)</span> - ${fDaysStr}</td></tr>"

                // Egg Production & Analytics
                def todayEggs = state["eggsToday_${i}"] ?: 0
                def weekAvg = calculateAverage(i, 7)
                def costPerEgg = calculateCostPerEgg(i)
                def cpeDisplay = costPerEgg > 0 ? ('$' + costPerEgg) : "--"
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🥚 Production</b></td><td style='padding: 8px;'><span style='color: #27ae60; font-weight: bold;'>${todayEggs} Eggs Today</span><br><span style='font-size:11px; color:#555;'>7-Day Avg: ${weekAvg}/day | Cost/Egg: ${cpeDisplay}</span></td></tr>"
                
                // Maintenance Logs
                def lClean = state["lastDeepClean_${i}"] ?: new Date().time
                def cInt = settings["cleanInterval_${i}"]
                def daysSinceClean = Math.floor((new Date().time - lClean) / 86400000) as Integer
                def cleanColor = (cInt && daysSinceClean >= cInt) ? "red" : "green"
                dash += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>🧹 Maintenance</b></td><td style='padding: 8px;'><span style='color:${cleanColor};'>Deep Clean: ${daysSinceClean} days ago</span></td></tr>"

                // Security
                def sosStatus = state["sosActive_${i}"] ? "<span style='color: red; font-weight: bold;'>🚨 ACTIVE PREDATOR SOS</span>" : "<span style='color: green;'>Secure</span>"
                dash += "<tr><td style='padding: 8px;'><b>🛡️ Security</b></td><td style='padding: 8px;'>${sosStatus}</td></tr>"
                
                dash += "</table>"
                paragraph dash
                
                input "logEggBtn_${i}", "button", title: "🥚 Log an Egg (+1) - ${coopName}"
                input "removeEggBtn_${i}", "button", title: "➖ Remove an Egg (-1) - ${coopName}"
                input "refillFeedBtn_${i}", "button", title: "🌾 Log Feed Refill - ${coopName}"
                input "refillWaterBtn_${i}", "button", title: "💧 Log Water Refill - ${coopName}"
                input "deepCleanBtn_${i}", "button", title: "🧹 Log Deep Clean - ${coopName}"
            }
        }

        section("Configuration Menus") {
            href(name: "flockHref", page: "flockPage", title: "🐔 Manage Flock & Molting Mode")
            href(name: "feedHref", page: "feedPage", title: "🌾 Smart Feed & Water Tracking")
            href(name: "doorHref", page: "doorPage", title: "🚪 Access, Wind & Storm Response")
            href(name: "climateHref", page: "climatePage", title: "🌡️ Climate & Predictive Defense")
            href(name: "waterHref", page: "waterPage", title: "💧 Automated Hydration Devices")
            href(name: "lightHref", page: "lightPage", title: "💡 Circadian & String Lighting")
            href(name: "securityHref", page: "securityPage", title: "🛡️ Predator Deterrence (SOS)")
            href(name: "notificationHref", page: "notificationPage", title: "📱 Notifications & Audio Announcements")
        }
        
        section("Dashboard Integrations") {
            paragraph "Create a virtual device to display live HTML tiles on your dashboard. Use the 'Attribute' tile type on your dashboard and select 'coop1Tile', 'coop2Tile', etc."
            input "createDeviceBtn", "button", title: "🛠️ Create / Re-sync Information Device"
        }
    }
}

def notificationPage() {
    dynamicPage(name: "notificationPage", title: "Notifications & Audio Announcements", install: false, uninstall: false) {
        section("Mode Restrictions & Queueing") {
            paragraph "Restrict announcements to specific modes. If an alert occurs during a restricted mode, it will be queued and announced 10 minutes after the hub changes to an allowed mode."
            input "allowedModes", "mode", title: "Allow Notifications in these Modes", multiple: true, required: false
        }
        section("Peace of Mind & Sensor Failsafes") {
            paragraph "If you consistently manually open doors or vents early, the smart mute will keep the app completely silent. Set a bypass interval here so the app occasionally confirms the status to give you peace of mind."
            input "pomBypassHours", "number", title: "Silence Bypass Interval (Hours)", defaultValue: 48, required: false, description: "If an alert is muted because the door/vent is already in the correct state, bypass the mute once every X hours."
            
            paragraph "If a sensor stops communicating entirely (e.g., dead battery, fell off), the app should stop trusting its 'Closed' or 'Open' state and resume sending you manual reminders. Set the timeout threshold below."
            input "sensorHealthHours", "number", title: "Sensor Health Timeout (Hours)", defaultValue: 48, required: false, description: "If a contact sensor has no heartbeat/activity for this many hours, the app assumes it failed and ignores its state."
        }
        section("Notification Devices") {
            input "pushDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false
        }
        section("Audio & Siren Announcers") {
            paragraph "Select speakers or sirens to play custom audio files or Text-to-Speech."
            input "ttsDevices", "capability.speechSynthesis", title: "TTS Audio Devices", multiple: true, required: false
            input "soundDevices", "capability.audioNotification", title: "Sound/Chime Devices (e.g., Zooz)", multiple: true, required: false
            input "audioVolume", "number", title: "Announcement Volume (0-100)", required: false
        }
        section("Event Toggles") {
            input "notifyDoor", "bool", title: "Send alerts for Door Open/Close?", defaultValue: true, submitOnChange: true
            input "notifyTardy", "bool", title: "Send alert if door jams or fails to close?", defaultValue: true
            
            input "notifyTemp", "bool", title: "Send alerts for Extreme Temp warnings (Hot/Freezing)?", defaultValue: true, submitOnChange: true
            input "notifyAirflow", "bool", title: "Send alerts for Airflow recommendations (Open/Close Vents)?", defaultValue: true, submitOnChange: true
            input "notifyAqi", "bool", title: "Send alerts for Unhealthy AQI / Smoke?", defaultValue: true, submitOnChange: true
            
            input "notifyHealth", "bool", title: "Send Health Guardian & Production alerts?", defaultValue: true
            input "notifyStorm", "bool", title: "Send alerts for Rain/Wind Storms?", defaultValue: true, submitOnChange: true
            input "notifyFeed", "bool", title: "Send alerts for Low Virtual Feed/Water?", defaultValue: true
            input "notifyMaintenance", "bool", title: "Send reminders for Coop Cleaning?", defaultValue: true
            input "notifyWater", "bool", title: "Send alerts from float sensors?", defaultValue: true, submitOnChange: true
            
            input "notifySecurity", "bool", title: "Send alerts for nighttime motion/SOS?", defaultValue: true, submitOnChange: true
            input "securityBypassModes", "bool", title: "🚨 Bypass Mode Restrictions for Security SOS?", defaultValue: true, description: "Play security alerts immediately even if the hub is in a restricted mode."
        }
        section("Audio URLs & Track Numbers (Optional)") {
            paragraph "For Zooz/Chimes, enter the track number (e.g., 12). For Sonos/Bose, enter the audio URL. Use the buttons to instantly test your audio configuration."
            
            input "audioDoorOpen", "text", title: "Door Open Track/URL", required: false
            input "test_audioDoorOpen", "button", title: "🔊 Test Door Open"
            
            input "audioDoorClose", "text", title: "Door Close Track/URL", required: false
            input "test_audioDoorClose", "button", title: "🔊 Test Door Close"
            
            input "audioDoorReminderOpen", "text", title: "Hourly Reminder: Needs Opening Track/URL", required: false
            input "test_audioDoorReminderOpen", "button", title: "🔊 Test Hourly Open Reminder"
            
            input "audioDoorReminderClose", "text", title: "Hourly Reminder: Needs Closing Track/URL", required: false
            input "test_audioDoorReminderClose", "button", title: "🔊 Test Hourly Close Reminder"
            
            input "audioVentOpen", "text", title: "Temp Rising: Open Vents Track/URL", required: false
            input "test_audioVentOpen", "button", title: "🔊 Test Open Vents"
            
            input "audioVentClose", "text", title: "Temp Dropping: Close Vents Track/URL", required: false
            input "test_audioVentClose", "button", title: "🔊 Test Close Vents"
            
            input "audioStorm", "text", title: "Storm Warning Track/URL", required: false
            input "test_audioStorm", "button", title: "🔊 Test Storm Warning"
            
            input "audioWater", "text", title: "Low Water Track/URL", required: false
            input "test_audioWater", "button", title: "🔊 Test Low Water"
            
            input "audioSecurity", "text", title: "Security Alert Track/URL", required: false
            input "test_audioSecurity", "button", title: "🔊 Test Security Alert"
        }
        section("Global Audio Room Mapping") {
            paragraph "<div style='font-size:13px; color:#555;'><b>1-to-1 Motion Filtering:</b> Map your speakers to motion sensors here. When the system attempts to play audio on a speaker or chime, it will automatically intercept the command and check if that specific device's room has recent motion. (Devices not mapped here will play unconditionally).</div>"
            
            input "audioMotionTimeout", "number", title: "Audio Motion Timeout (Minutes)", defaultValue: 5, description: "Time to wait after motion stops before muting announcements (prevents muting if someone is sitting still)."
            input "alwaysOnRoom", "enum", title: "Select ONE room to ALWAYS announce (Ignores motion)", options: ["1": "Room 1", "2": "Room 2", "3": "Room 3", "4": "Room 4", "5": "Room 5", "6": "Room 6", "7": "Room 7"], required: false
            
            paragraph "<b>Room 1 Definition</b>"
            input "room1Speaker", "capability.actuator", title: "Room 1 Speaker/Chime(s)", required: false, multiple: true
            input "room1Motion", "capability.motionSensor", title: "Room 1 Motion Sensor(s)", required: false, multiple: true
            input "room1GNSwitch", "capability.switch", title: "Room 1 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 2 Definition</b>"
            input "room2Speaker", "capability.actuator", title: "Room 2 Speaker/Chime(s)", required: false, multiple: true
            input "room2Motion", "capability.motionSensor", title: "Room 2 Motion Sensor(s)", required: false, multiple: true
            input "room2GNSwitch", "capability.switch", title: "Room 2 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 3 Definition</b>"
            input "room3Speaker", "capability.actuator", title: "Room 3 Speaker/Chime(s)", required: false, multiple: true
            input "room3Motion", "capability.motionSensor", title: "Room 3 Motion Sensor(s)", required: false, multiple: true
            input "room3GNSwitch", "capability.switch", title: "Room 3 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 4 Definition</b>"
            input "room4Speaker", "capability.actuator", title: "Room 4 Speaker/Chime(s)", required: false, multiple: true
            input "room4Motion", "capability.motionSensor", title: "Room 4 Motion Sensor(s)", required: false, multiple: true
            input "room4GNSwitch", "capability.switch", title: "Room 4 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 5 Definition</b>"
            input "room5Speaker", "capability.actuator", title: "Room 5 Speaker/Chime(s)", required: false, multiple: true
            input "room5Motion", "capability.motionSensor", title: "Room 5 Motion Sensor(s)", required: false, multiple: true
            input "room5GNSwitch", "capability.switch", title: "Room 5 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 6 Definition</b>"
            input "room6Speaker", "capability.actuator", title: "Room 6 Speaker/Chime(s)", required: false, multiple: true
            input "room6Motion", "capability.motionSensor", title: "Room 6 Motion Sensor(s)", required: false, multiple: true
            input "room6GNSwitch", "capability.switch", title: "Room 6 Good Night Switch (Forces Security Audio ON without motion)", required: false
            
            paragraph "<b>Room 7 Definition</b>"
            input "room7Speaker", "capability.actuator", title: "Room 7 Speaker/Chime(s)", required: false, multiple: true
            input "room7Motion", "capability.motionSensor", title: "Room 7 Motion Sensor(s)", required: false, multiple: true
            input "room7GNSwitch", "capability.switch", title: "Room 7 Good Night Switch (Forces Security Audio ON without motion)", required: false
        }
    }
}

def flockPage() {
    dynamicPage(name: "flockPage", title: "Flock Roster & Production", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Roster & Mode") {
                input "coopName_${i}", "text", title: "Coop ${i} Custom Name", required: false, defaultValue: "Coop ${i}"
                input "birdCount_${i}", "number", title: "Number of Birds in Flock (Used for Feed/Water Math)", defaultValue: 6, required: true
                input "moltingMode_${i}", "bool", title: "🪶 Enable Biological Rest (Molting Mode)", defaultValue: false, submitOnChange: true, description: "Disables lay-rate drop alerts and forces Circadian lights OFF."
                input "chickenNames_${i}", "text", title: "Chicken Names (comma separated)", required: false, submitOnChange: true
                if (settings["chickenNames_${i}"]) {
                    def roster = settings["chickenNames_${i}"].split(",").collect { it.trim() }
                    paragraph "<b>Current Roster (${roster.size()} Named Birds):</b><br>${roster.join('<br>')}"
                }
                input "manualEggInput_${i}", "number", title: "Set Today's Exact Egg Count", required: false, submitOnChange: true
                input "saveManualEggsBtn_${i}", "button", title: "Save Override Count"
            }
        }
    }
}

def feedPage() {
    dynamicPage(name: "feedPage", title: "Smart Feed, Water & Maintenance", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        section("How It Works") {
            paragraph "The app will calculate dynamic daily consumption based on your Flock Size and extreme weather (Hot = +Water, Cold = +Feed). Use the inputs below to set the standard consumption rate per bird."
        }
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Virtual Tracking") {
                input "feedBagSize_${i}", "decimal", title: "Feed Refill Size (lbs)", defaultValue: 50.0, required: false
                input "baseFeedPerBird_${i}", "decimal", title: "Base Feed per Bird (lbs/day)", defaultValue: 0.25, required: false
                input "feedBagCost_${i}", "decimal", title: "Cost per Feed Bag (USD) - Used for Analytics", defaultValue: 25.00, required: false
                
                input "waterCapacity_${i}", "decimal", title: "Water Refill Size (Gallons)", defaultValue: 5.0, required: false
                input "baseWaterPerBird_${i}", "decimal", title: "Base Water per Bird (Gal/day)", defaultValue: 0.13, required: false
                
                input "cleanInterval_${i}", "number", title: "Deep Clean Reminder Interval (Days)", defaultValue: 14, required: false
            }
        }
    }
}

def doorPage() {
    dynamicPage(name: "doorPage", title: "Access & Storm Response", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Door Controls & Wind") {
                input "coopDoor_${i}", "capability.doorControl", title: "Coop Door Controller", required: false
                input "doorContact_${i}", "capability.contactSensor", title: "Verification Contact Sensor", required: false
                input "openOffset_${i}", "number", title: "Morning Open Offset (Mins after Sunrise)", defaultValue: 15, required: false
                input "closeOffset_${i}", "number", title: "Evening Close Offset (Mins after Sunset)", defaultValue: 30, required: false
                input "stormSwitch_${i}", "capability.switch", title: "Severe Weather / Storm Switch", required: false
                input "windSensor_${i}", "capability.sensor", title: "Outdoor Wind Sensor (Enables Wind-Chill)", required: false
                input "windThreshold_${i}", "number", title: "Alert/Close if Wind Gusts Exceed (MPH)", defaultValue: 30, required: false
            }
        }
    }
}

def climatePage() {
    dynamicPage(name: "climatePage", title: "Climate & Environmental Control", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Indoor Sensors") {
                input "tempSensor_${i}", "capability.temperatureMeasurement", title: "Inside Coop Temp Sensor", required: false
                input "humSensor_${i}", "capability.relativeHumidityMeasurement", title: "Inside Coop Humidity Sensor", required: false
            }
            section("Coop ${i} - Outdoor Station & Fallback Calculation") {
                paragraph "If indoor sensors are missing, the app estimates the coop climate using outdoor data + offsets. Wind sensors enable Wind-Chill logic automatically."
                input "outdoorTempSensor_${i}", "capability.temperatureMeasurement", title: "Outdoor Temp Sensor", required: false
                input "outdoorHumSensor_${i}", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor", required: false
                input "estTempOffset_${i}", "decimal", title: "Base Temp Offset (Heat Gain °F/°C)", defaultValue: 5.0, required: false
                input "sunshadePercent_${i}", "number", title: "Sunshade Blockage (%)", defaultValue: 0, range: "0..100", required: false, description: "If installed, reduces estimated heat gain during daylight hours."
                input "estHumOffset_${i}", "decimal", title: "Estimated Coop Humidity Offset (% RH to add)", defaultValue: 5.0, required: false
            }
            section("Coop ${i} - Predictive Thermal Defense") {
                paragraph "Calculates the rate of temperature drop to preemptively engage heaters and de-icers before freezing occurs."
                input "enablePredictiveTemp_${i}", "bool", title: "Enable Predictive Defense", defaultValue: false, submitOnChange: true
                if (settings["enablePredictiveTemp_${i}"]) {
                    input "predictiveDropRate_${i}", "decimal", title: "Pre-heat if temp drops faster than (°F/hr)", defaultValue: 3.0, required: false
                    input "predictiveThreshold_${i}", "number", title: "Only activate if current temp is below (°F)", defaultValue: 45, required: false
                }
            }
            section("Coop ${i} - Air Quality & Respiratory Guardian") {
                input "aqiSensor_${i}", "capability.sensor", title: "Outdoor AQI Sensor (Reports 'airQuality' or 'Aqi')", required: false
                input "aqiThreshold_${i}", "number", title: "Alert: AQI Unhealthy Threshold (Shuts off fans)", defaultValue: 100, required: false
            }
            section("Coop ${i} - Airflow & Automation Thresholds") {
                input "manualAirflowTemp_${i}", "number", title: "Alert: OPEN Vents/Doors when Outdoor Temp > (°F)", defaultValue: 75, required: false
                input "closeAirflowTemp_${i}", "number", title: "Alert: CLOSE Vents/Doors when Outdoor Temp < (°F)", defaultValue: 40, required: false
                input "ventContact_${i}", "capability.contactSensor", title: "Airflow Vent/Window Contact Sensor (Optional)", required: false, description: "Silences manual open/close reminders if vents are already in the correct state."
                
                input "alertHighCoopTemp_${i}", "number", title: "Alert: Coop Inside is Too Hot (°F)", defaultValue: 85, required: false
                input "alertLowCoopTemp_${i}", "number", title: "Alert: Coop Inside is Too Cold (°F)", defaultValue: 32, required: false
                
                input "exhaustFan_${i}", "capability.switch", title: "Exhaust / Ventilation Fan", multiple: true, required: false
                input "fanOnTemp_${i}", "number", title: "Turn Fan ON when Temp > (°F)", defaultValue: 85, required: false
                input "fanOffTemp_${i}", "number", title: "Turn Fan OFF when Temp < (°F)", defaultValue: 75, required: false
                
                input "coopHeater_${i}", "capability.switch", title: "Coop Heater Smart Plug", multiple: true, required: false
                input "heaterOnTemp_${i}", "number", title: "Turn Heater ON when Temp < (°F)", defaultValue: 20, required: false
                input "heaterOffTemp_${i}", "number", title: "Turn Heater OFF when Temp > (°F)", defaultValue: 35, required: false
            }
        }
    }
}

def waterPage() {
    dynamicPage(name: "waterPage", title: "Automated Hydration Devices", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Device Setup") {
                paragraph "Configure physical valves and float sensors here. If you do not use a float sensor, configure Virtual Water Capacity on the Feed Page."
                input "waterFloat_${i}", "capability.waterSensor", title: "Water Level Float Sensor", required: false
                input "waterValve_${i}", "capability.valve", title: "Irrigation Fill Valve", required: false
                input "maxFillTime_${i}", "number", title: "Maximum Fill Time (Minutes)", defaultValue: 5, required: false
                input "waterHeater_${i}", "capability.switch", title: "Water De-icer / Heater Base", required: false
                input "waterFreezeTemp_${i}", "number", title: "Turn De-icer ON when Temp < (°F)", defaultValue: 35, required: false
            }
        }
    }
}

def lightPage() {
    dynamicPage(name: "lightPage", title: "Circadian Lighting", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Lighting Setup") {
                input "enableLighting_${i}", "bool", title: "Enable Morning Daylight Extension", defaultValue: false, submitOnChange: true
                if (settings["enableLighting_${i}"]) {
                    input "coopLights_${i}", "capability.switch", title: "Coop Lights", required: false
                    input "lightOnTime_${i}", "time", title: "Turn Lights ON at", required: false
                    input "lightOffTime_${i}", "enum", title: "Turn Lights OFF at", options: ["Sunrise", "Specific Time"], defaultValue: "Sunrise", submitOnChange: true
                    if (settings["lightOffTime_${i}"] == "Specific Time") {
                        input "specificLightOffTime_${i}", "time", title: "Specific OFF Time", required: false
                    }
                }
            }
        }
    }
}

def securityPage() {
    dynamicPage(name: "securityPage", title: "Predator Deterrence (SOS)", install: false, uninstall: false) {
        def count = (settings.coopCount ?: "1") as Integer
        for (int i = 1; i <= count; i++) {
            section("Coop ${i} - Security Setup") {
                input "enableSecurity_${i}", "bool", title: "Enable Nighttime SOS Mode", defaultValue: false, submitOnChange: true
                if (settings["enableSecurity_${i}"]) {
                    input "outdoorMotion_${i}", "capability.motionSensor", title: "Outdoor Perimeter Motion Sensors", multiple: true, required: false
                    input "sosLights_${i}", "capability.switch", title: "Floodlights to Flash/Turn ON", multiple: true, required: false
                    input "sosSiren_${i}", "capability.alarm", title: "Siren to Pulse", required: false
                    input "sosDuration_${i}", "number", title: "SOS Duration (Seconds)", defaultValue: 30, required: false
                }
            }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!masterEnable) return
    def count = (settings.coopCount ?: "1") as Integer
    
    state.queuedAlerts = state.queuedAlerts ?: []
    state.groupAlerts = [:] // Ensure aggregation map is clean

    schedule("0 0 0 * * ?", midnightReset)
    schedule("0 0 * * * ?", checkDoorStates) // Hourly door verification logic
    runEvery5Minutes("updateDashboardDevice") // Tile Generator Sync
    subscribe(location, "sunriseTime", scheduleSunEvents)
    subscribe(location, "sunsetTime", scheduleSunEvents)
    subscribe(location, "mode", "modeChangeHandler")
    scheduleSunEvents() 

    // Subscribe to specific room motion sensors for Audio Logic
    if (settings.room1Motion) subscribe(settings.room1Motion, "motion.active", "room1MotionHandler")
    if (settings.room2Motion) subscribe(settings.room2Motion, "motion.active", "room2MotionHandler")
    if (settings.room3Motion) subscribe(settings.room3Motion, "motion.active", "room3MotionHandler")
    if (settings.room4Motion) subscribe(settings.room4Motion, "motion.active", "room4MotionHandler")
    if (settings.room5Motion) subscribe(settings.room5Motion, "motion.active", "room5MotionHandler")
    if (settings.room6Motion) subscribe(settings.room6Motion, "motion.active", "room6MotionHandler")
    if (settings.room7Motion) subscribe(settings.room7Motion, "motion.active", "room7MotionHandler")

    for (int i = 1; i <= count; i++) {
        // State init
        state["eggsToday_${i}"] = state["eggsToday_${i}"] ?: 0
        state["eggLog_${i}"] = state["eggLog_${i}"] ?: [] 
        state["sosActive_${i}"] = false
        state["virtualFeedLbs_${i}"] = state["virtualFeedLbs_${i}"] != null ? state["virtualFeedLbs_${i}"] : (settings["feedBagSize_${i}"] ?: 0.0)
        state["virtualWaterGal_${i}"] = state["virtualWaterGal_${i}"] != null ? state["virtualWaterGal_${i}"] : (settings["waterCapacity_${i}"] ?: 0.0)
        state["eggsSinceRefill_${i}"] = state["eggsSinceRefill_${i}"] ?: 0
        state["lastDeepClean_${i}"] = state["lastDeepClean_${i}"] ?: new Date().time
        
        state["highTempAlertSent_${i}"] = false
        state["lowTempAlertSent_${i}"] = false
        state["ventOpenAlertSent_${i}"] = false
        state["ventCloseAlertSent_${i}"] = false
        state["frostbiteAlertSent_${i}"] = false
        state["heatAlertSent_${i}"] = false
        state["feedAlertSent_${i}"] = false
        state["waterAlertSent_${i}"] = false
        state["windAlertSent_${i}"] = false
        state["aqiAlertSent_${i}"] = false
        state["aqiOverride_${i}"] = false
        state["predictiveActive_${i}"] = false
        
        // Setup initial Trend States safely
        state["todayInHigh_${i}"] = state["todayInHigh_${i}"] ?: null
        state["todayInLow_${i}"] = state["todayInLow_${i}"] ?: null
        state["yesterdayInHigh_${i}"] = state["yesterdayInHigh_${i}"] ?: "--"
        state["yesterdayInLow_${i}"] = state["yesterdayInLow_${i}"] ?: "--"
        
        state["todayOutHigh_${i}"] = state["todayOutHigh_${i}"] ?: null
        state["todayOutLow_${i}"] = state["todayOutLow_${i}"] ?: null
        state["yesterdayOutHigh_${i}"] = state["yesterdayOutHigh_${i}"] ?: "--"
        state["yesterdayOutLow_${i}"] = state["yesterdayOutLow_${i}"] ?: "--"

        // Subs & Battery Heartbeat Monitoring
        if (settings["doorContact_${i}"]) {
            subscribe(settings["doorContact_${i}"], "contact", "genericSensorHeartbeat")
            subscribe(settings["doorContact_${i}"], "battery", "genericSensorHeartbeat")
        }
        if (settings["ventContact_${i}"]) {
            subscribe(settings["ventContact_${i}"], "contact", "genericSensorHeartbeat")
            subscribe(settings["ventContact_${i}"], "battery", "genericSensorHeartbeat")
        }

        if (settings["tempSensor_${i}"]) {
            subscribe(settings["tempSensor_${i}"], "temperature", "climateHandler${i}")
            subscribe(settings["tempSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        if (settings["humSensor_${i}"]) {
            subscribe(settings["humSensor_${i}"], "humidity", "healthGuardianHandler${i}")
            subscribe(settings["humSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        
        if (settings["outdoorTempSensor_${i}"]) {
            subscribe(settings["outdoorTempSensor_${i}"], "temperature", "outdoorClimateHandler${i}")
            subscribe(settings["outdoorTempSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        if (settings["outdoorHumSensor_${i}"]) {
            subscribe(settings["outdoorHumSensor_${i}"], "humidity", "outdoorHumHandler${i}")
            subscribe(settings["outdoorHumSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        
        if (settings["aqiSensor_${i}"]) {
            subscribe(settings["aqiSensor_${i}"], "airQuality", "aqiHandler${i}")
            subscribe(settings["aqiSensor_${i}"], "Aqi", "aqiHandler${i}")
            subscribe(settings["aqiSensor_${i}"], "aqi", "aqiHandler${i}")
            subscribe(settings["aqiSensor_${i}"], "Air Quality Index", "aqiHandler${i}")
            subscribe(settings["aqiSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        
        if (settings["windSensor_${i}"]) {
            subscribe(settings["windSensor_${i}"], "windSpeed", "windHandler${i}")
            subscribe(settings["windSensor_${i}"], "battery", "genericSensorHeartbeat")
        }
        
        if (settings["waterFloat_${i}"]) subscribe(settings["waterFloat_${i}"], "water", "waterHandler${i}")
        
        if (settings["enableSecurity_${i}"] && settings["outdoorMotion_${i}"]) subscribe(settings["outdoorMotion_${i}"], "motion", "securityHandler${i}")
        if (settings["stormSwitch_${i}"]) subscribe(settings["stormSwitch_${i}"], "switch", "stormHandler${i}")
        
        if (settings["enableLighting_${i}"] && settings["lightOnTime_${i}"]) schedule(settings["lightOnTime_${i}"], "turnOnLights${i}")
    }
}

// --- NOTIFICATION AGGREGATION ENGINE ---
// This queues alerts and sends them out cleanly combined 5 seconds later
def queueGroupingAlert(category, coopName, msgTemplate, audioTrack, appendWarning = "") {
    if (!state.groupAlerts) state.groupAlerts = [:]
    if (!state.groupAlerts[category]) state.groupAlerts[category] = [coops: [], msgTemplate: msgTemplate, audioTrack: audioTrack, warnings: []]

    if (!state.groupAlerts[category].coops.contains(coopName)) {
        state.groupAlerts[category].coops << coopName
        if (appendWarning && !state.groupAlerts[category].warnings.contains(appendWarning)) {
            state.groupAlerts[category].warnings << appendWarning
        }
    }
    
    // Refresh the 5-second timer. If another coop fires within 5 seconds, it groups perfectly.
    runIn(5, "flushGroupAlerts")
}

def flushGroupAlerts() {
    if (!state.groupAlerts) return

    state.groupAlerts.each { category, data ->
        if (data.coops.size() > 0) {
            def coopStr = data.coops.size() > 1 ? data.coops.join(" & ") : data.coops[0]
            def warnStr = data.warnings ? data.warnings.join(" ") : ""
            def finalMsg = data.msgTemplate.replace("%COOPS%", coopStr) + warnStr
            
            sendAlert(finalMsg, data.audioTrack)
        }
    }
    state.groupAlerts = [:] // Clear the queue after sending
}

// --- SENSOR HEALTH & FAILOVER ENGINE ---
def genericSensorHeartbeat(evt) {
    if (evt.deviceId) {
        state["lastSeen_${evt.deviceId}"] = new Date().time
    }
}

def isSensorHealthy(sensor, timeoutHours) {
    if (!sensor) return false
    if (!timeoutHours || timeoutHours <= 0) return true
    
    // Try native Hubitat getLastActivity if available
    try {
        def lastAct = sensor.getLastActivity()
        if (lastAct != null) {
            return (new Date().time - lastAct.time) <= (timeoutHours * 3600000)
        }
    } catch (Exception e) {}
    
    // Fallback to our own state tracking heartbeat
    def lastSeen = state["lastSeen_${sensor.id}"]
    if (lastSeen) {
        return (new Date().time - lastSeen) <= (timeoutHours * 3600000)
    }
    
    return true // Prevent false positives directly after a fresh install
}

def getCoopTemperature(i) {
    def dTemp = settings["tempSensor_${i}"]
    def dOutTemp = settings["outdoorTempSensor_${i}"]
    def healthHours = settings.sensorHealthHours ?: 48

    // Try indoor sensor first
    if (dTemp && isSensorHealthy(dTemp, healthHours)) {
        def t = dTemp.currentValue("temperature")
        if (t != null) return [temp: t as BigDecimal, isEstimated: false]
    }

    // Failover to outdoor sensor + offset
    if (dOutTemp && isSensorHealthy(dOutTemp, healthHours)) {
        def t = dOutTemp.currentValue("temperature")
        if (t != null) {
            def estTemp = (t as BigDecimal) + getEffectiveTempOffset(i)
            return [temp: estTemp, isEstimated: true]
        }
    }

    return [temp: null, isEstimated: false]
}

def getCoopHumidity(i) {
    def dHum = settings["humSensor_${i}"]
    def dOutHum = settings["outdoorHumSensor_${i}"]
    def healthHours = settings.sensorHealthHours ?: 48

    if (dHum && isSensorHealthy(dHum, healthHours)) {
        def h = dHum.currentValue("humidity")
        if (h != null) return [hum: h as BigDecimal, isEstimated: false]
    }

    if (dOutHum && isSensorHealthy(dOutHum, healthHours)) {
        def h = dOutHum.currentValue("humidity")
        if (h != null) {
            def estHum = (h as BigDecimal) + (settings["estHumOffset_${i}"] ?: 5.0)
            return [hum: estHum, isEstimated: true]
        }
    }
    return [hum: null, isEstimated: false]
}

// --- DASHBOARD TILE ENGINE ---
def updateDashboardDevice() {
    def childId = "coopPanel_${app.id}"
    def child = getChildDevice(childId)
    
    if (!child) return // Only update if they created the device
    
    def count = (settings.coopCount ?: "1") as Integer
    child.sendEvent(name: "sunCountdown", value: getSunCountdown())

    for (int i = 1; i <= count; i++) {
        def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
        
        // Door Status
        def dDoor = settings["coopDoor_${i}"]
        def dContact = settings["doorContact_${i}"]
        def dStatus = "Unknown"
        def dColor = "#333"
        if (dContact) {
            dStatus = dContact.currentValue("contact")?.toUpperCase() ?: "N/A"
        } else if (dDoor) {
            dStatus = dDoor.currentValue("door")?.toUpperCase() ?: "N/A"
        }
        if (dStatus == "OPEN") dColor = "#e67e22"
        if (dStatus == "CLOSED") dColor = "green"

        // Temp Status via Failover Engine
        def tData = getCoopTemperature(i)
        def tempStr = tData.temp != null ? tData.temp.setScale(1, BigDecimal.ROUND_HALF_UP) + "&deg;" : "--"
        
        // Feed & Water
        def vWater = (state["virtualWaterGal_${i}"] ?: 0.0) as BigDecimal
        def wCap = (settings["waterCapacity_${i}"] ?: 0.0) as BigDecimal
        def wPct = wCap > 0 ? Math.round((vWater / wCap) * 100) : 0
        def wColor = wPct <= 20 ? "red" : "green"
        
        def feedLbs = (state["virtualFeedLbs_${i}"] ?: 0.0) as BigDecimal
        def feedSize = (settings["feedBagSize_${i}"] ?: 0.0) as BigDecimal
        def feedPct = feedSize > 0 ? Math.round((feedLbs / feedSize) * 100) : 0
        def feedColor = feedPct <= 20 ? "red" : "green"
        
        // Eggs
        def todayEggs = state["eggsToday_${i}"] ?: 0
        
        // Build Compact HTML
        def html = "<div style='border:1px solid #aaa;border-radius:8px;padding:5px;background:#fff;color:#000;font-size:13px;line-height:1.4;'>"
        html += "<div style='font-size:15px;font-weight:bold;color:#2c3e50;text-align:center;margin-bottom:2px;'>${coopName}</div>"
        html += "<hr style='margin:2px 0 4px 0;'>"
        html += "🚪 <b>Door:</b> <span style='color:${dColor};font-weight:bold;'>${dStatus}</span><br>"
        html += "🌡️ <b>Temp:</b> ${tempStr}<br>"
        html += "💧 <b>Water:</b> <span style='color:${wColor}'>${vWater}g (${wPct}%)</span><br>"
        html += "🌾 <b>Feed:</b> <span style='color:${feedColor}'>${feedLbs}lb (${feedPct}%)</span><br>"
        html += "🥚 <b>Eggs:</b> <span style='color:#27ae60;font-weight:bold;'>${todayEggs} Today</span>"
        html += "</div>"

        // Push to custom driver attribute
        child.sendEvent(name: "coop${i}Tile", value: html)
    }
}

// --- DOOR VERIFICATION ENGINE ---
def checkDoorStates() {
    if (!masterEnable) return
    
    def sunInfo = getSunriseAndSunset()
    def now = new Date()
    def count = (settings.coopCount ?: "1") as Integer
    
    for (int i = 1; i <= count; i++) {
        def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
        def dContact = settings["doorContact_${i}"]
        def cDoor = settings["coopDoor_${i}"]
        def sSwitch = settings["stormSwitch_${i}"]
        
        if (!dContact && !cDoor) continue
        
        def isOpen = false
        def dHealthy = isSensorHealthy(dContact, settings.sensorHealthHours)
        
        if (dContact && dHealthy) {
            isOpen = (dContact.currentValue("contact") == "open")
        } else {
            isOpen = (cDoor?.currentValue("door") == "open")
        }
        
        def oOff = settings["openOffset_${i}"] ?: 15
        def cOff = settings["closeOffset_${i}"] ?: 30
        
        if (sunInfo?.sunrise && sunInfo?.sunset) {
            def openTime = new Date(sunInfo.sunrise.time + (oOff * 60000))
            def closeTime = new Date(sunInfo.sunset.time + (cOff * 60000))
            
            // Should be Open if current time is inside daytime bounds
            def shouldBeOpen = (now.after(openTime) && now.before(closeTime))
            
            // Storm switch override forces the door closed
            if (sSwitch && sSwitch.currentValue("switch") == "on") {
                shouldBeOpen = false
            }
            
            def healthWarning = (!dHealthy && dContact) ? " [⚠️ Sensor Offline/Stale]" : ""
            
            if (shouldBeOpen && !isOpen) {
                queueGroupingAlert("hourlyDoorOpen", coopName, "Hourly Reminder: The door for %COOPS% is still CLOSED but it is daytime.", settings.audioDoorReminderOpen, healthWarning)
            } else if (!shouldBeOpen && isOpen) {
                queueGroupingAlert("hourlyDoorClose", coopName, "Hourly Reminder: The door for %COOPS% is still OPEN but it is nighttime.", settings.audioDoorReminderClose, healthWarning)
            }
        }
    }
}

// --- SUN COUNTDOWN ENGINE ---
def getSunCountdown() {
    def now = new Date().time
    def sunInfo = getSunriseAndSunset()

    if (!sunInfo || !sunInfo.sunrise || !sunInfo.sunset) return "Awaiting Solar Data..."

    def sunrise = sunInfo.sunrise.time
    def sunset = sunInfo.sunset.time

    if (now > sunrise) sunrise += 86400000 
    if (now > sunset) sunset += 86400000

    if (sunrise < sunset) {
        def diff = sunrise - now
        def hours = Math.floor(diff / 3600000) as Integer
        def mins = Math.floor((diff % 3600000) / 60000) as Integer
        return "🌅 Sunrise in ${hours}h ${mins}m"
    } else {
        def diff = sunset - now
        def hours = Math.floor(diff / 3600000) as Integer
        def mins = Math.floor((diff % 3600000) / 60000) as Integer
        return "🌇 Sunset in ${hours}h ${mins}m"
    }
}

// --- WIND CHILL ENGINE ---
def calculateWindChill(temp, windSpd) {
    if (temp <= 50 && windSpd >= 3) {
        def wc = 35.74 + (0.6215 * temp) - (35.75 * Math.pow(windSpd, 0.16)) + (0.4275 * temp * Math.pow(windSpd, 0.16))
        return (wc as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    return temp
}

// --- AUDIO & 1-TO-1 MOTION HELPER ENGINE ---
def room1MotionHandler(evt) { state.lastMotionRoom1 = now() }
def room2MotionHandler(evt) { state.lastMotionRoom2 = now() }
def room3MotionHandler(evt) { state.lastMotionRoom3 = now() }
def room4MotionHandler(evt) { state.lastMotionRoom4 = now() }
def room5MotionHandler(evt) { state.lastMotionRoom5 = now() }
def room6MotionHandler(evt) { state.lastMotionRoom6 = now() }
def room7MotionHandler(evt) { state.lastMotionRoom7 = now() }

def isSpeakerMotionActive(speaker, alertType) {
    boolean isMapped = false
    boolean hasMotion = false
    
    for (int i = 1; i <= 7; i++) {
        def mappedSpeaker = settings["room${i}Speaker"]
        if (mappedSpeaker) {
            def mappedList = mappedSpeaker instanceof List ? mappedSpeaker : [mappedSpeaker]
            if (mappedList.any { it.id == speaker.id }) {
                isMapped = true
                
                // Security Good Night Switch Override
                if (alertType == "security") {
                    def gnSwitch = settings["room${i}GNSwitch"]
                    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                        return true // Overrides motion constraint!
                    }
                }
                
                // 1. Check Always On Room
                if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == i.toString()) {
                    hasMotion = true
                }
                
                // 2. Evaluate Standard Motion
                if (!hasMotion) {
                    def motion = settings["room${i}Motion"]
                    if (!motion) {
                        hasMotion = true // Mapped, but no sensor to restrict it
                    } else {
                        def mList = motion instanceof List ? motion : [motion]
                        if (mList.any { it?.currentValue("motion") == "active" }) {
                            state."lastMotionRoom${i}" = now()
                            hasMotion = true
                        } else {
                            def lastTime = state."lastMotionRoom${i}"
                            if (lastTime) {
                                // FIX: Enforce Integer casting to prevent Groovy String multiplication crash
                                long timeoutMillis = ((settings.audioMotionTimeout ?: 5) as Integer) * 60000L
                                if ((now() - lastTime) <= timeoutMillis) {
                                    hasMotion = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (!isMapped) return true // Unmapped speakers play unconditionally
    return hasMotion
}

// --- NOTIFICATION & QUEUE ENGINE ---
def modeChangeHandler(evt) {
    if (!settings.allowedModes) return 
    
    if (settings.allowedModes.contains(evt.value)) {
        if (state.queuedAlerts && state.queuedAlerts.size() > 0) {
            logDebug("Entered allowed mode (${evt.value}). Scheduling queued alerts in 10 minutes.")
            runIn(600, "playQueuedAlerts")
        }
    }
}

def playQueuedAlerts() {
    if (!state.queuedAlerts || state.queuedAlerts.size() == 0) return
    
    def alert = state.queuedAlerts.remove(0)
    sendAlert(alert.pushMsg, alert.audioMsg, false, alert.alertType)
    
    if (state.queuedAlerts.size() > 0) {
        runIn(15, "playQueuedAlerts") // Stagger multiple queued alerts by 15 seconds
    }
}

def sendAlert(pushMsg, audioMsg, isTest = false, alertType = "general") {
    def isAllowed = true
    if (settings.allowedModes && !settings.allowedModes.contains(location.mode)) {
        isAllowed = false
    }
    
    if (alertType == "security" && settings.securityBypassModes) {
        isAllowed = true // Bypass hub mode restrictions for SOS alerts
    }
    
    if (!isAllowed && !isTest) {
        if (!state.queuedAlerts) state.queuedAlerts = []
        state.queuedAlerts << [pushMsg: pushMsg, audioMsg: audioMsg, alertType: alertType]
        if (state.queuedAlerts.size() > 10) state.queuedAlerts = state.queuedAlerts.drop(1)
        logDebug("Mode restricted. Alert queued: ${pushMsg}")
        return
    }

    if (pushDevices && pushMsg) {
        pushDevices.each { it.deviceNotification(pushMsg) }
        logDebug("Push Alert Sent: ${pushMsg}")
    }
    
    if (ttsDevices && pushMsg) {
        // FIX: Cast volume to Integer safely
        def vol = (settings.audioVolume ?: 65) as Integer
        ttsDevices.each { speaker ->
            if (isTest || isSpeakerMotionActive(speaker, alertType)) {
                if (speaker.hasCommand("setVolume")) speaker.setVolume(vol)
                if (speaker.hasCommand("speak")) speaker.speak(pushMsg)
                else if (speaker.hasCommand("playText")) speaker.playText(pushMsg)
            } else {
                logDebug("Skipping TTS on ${speaker.displayName}: No recent motion.")
            }
        }
    }
    
    if (soundDevices && audioMsg) {
        // FIX: Cast volume to Integer safely
        def vol = (settings.audioVolume ?: 65) as Integer
        def isNumeric = audioMsg.toString().isNumber()
        def trackNum = isNumeric ? audioMsg.toString().toInteger() : null

        soundDevices.each { player ->
            if (isTest || isSpeakerMotionActive(player, alertType)) {
                if (player.hasCommand("setVolume")) player.setVolume(vol)
                
                if (player.hasCommand("playSound") && trackNum != null) {
                    player.playSound(trackNum)
                } else if (player.hasCommand("playTrack")) {
                    player.playTrack(audioMsg.toString())
                } else if (player.hasCommand("chime") && trackNum != null) {
                    player.chime(trackNum)
                } else {
                    log.warn "${player.displayName} does not support standard audio commands."
                }
            } else {
                logDebug("Skipping Sound File on ${player.displayName}: No recent motion.")
            }
        }
    }
}

// --- BUTTON & DATA ENGINE ---
def appButtonHandler(btn) {
    def parts = btn.split("_")
    def action = parts[0]
    
    if (action == "test") {
        def alertTrigger = parts[1]
        def audioConfig = settings[alertTrigger]
        def msg = "[TEST] Testing ${alertTrigger} notification routing."
        def aType = (alertTrigger == "audioSecurity") ? "security" : "general"
        sendAlert(msg, audioConfig, true, aType)
        logDebug("Triggered manual test for notification: ${alertTrigger}")
        return
    }
    
    if (action == "createDeviceBtn") {
        def childId = "coopPanel_${app.id}"
        def child = getChildDevice(childId)
        if (!child) {
            try {
                addChildDevice("ShaneAllen", "Coop Information Panel", childId, null, [name: "Coop Information Panel", label: "Coop Dashboard Panel", isComponent: true])
                logDebug("Information Device Created Successfully.")
                runIn(3, "updateDashboardDevice") // Sync it right away
            } catch (e) {
                log.error "Failed to create device. Make sure the 'Coop Information Panel' driver is installed in your Drivers Code section! Error: ${e}"
            }
        } else {
            logDebug("Information Device already exists. Re-syncing data now.")
            updateDashboardDevice()
        }
        return
    }
    
    def i = parts.size() > 1 ? parts[1] as Integer : 1
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"

    if (action == "logEggBtn") {
        state["eggsToday_${i}"] = (state["eggsToday_${i}"] ?: 0) + 1
        state["eggsSinceRefill_${i}"] = (state["eggsSinceRefill_${i}"] ?: 0) + 1
        logDebug("${coopName}: Logged 1 egg. Total today: ${state["eggsToday_${i}"]}")
        updateDashboardDevice()
    } else if (action == "removeEggBtn") {
        if (state["eggsToday_${i}"] > 0) state["eggsToday_${i}"] = state["eggsToday_${i}"] - 1
        if (state["eggsSinceRefill_${i}"] > 0) state["eggsSinceRefill_${i}"] = state["eggsSinceRefill_${i}"] - 1
        logDebug("${coopName}: Removed 1 egg.")
        updateDashboardDevice()
    } else if (action == "saveManualEggsBtn") {
        if (settings["manualEggInput_${i}"] != null) {
            state["eggsToday_${i}"] = settings["manualEggInput_${i}"]
            app.updateSetting("manualEggInput_${i}", [type: "number", value: null]) 
            updateDashboardDevice()
        }
    } else if (action == "refillFeedBtn") {
        state["virtualFeedLbs_${i}"] = (settings["feedBagSize_${i}"] ?: 50.0) as BigDecimal
        state["eggsSinceRefill_${i}"] = 0
        state["feedAlertSent_${i}"] = false
        logDebug("${coopName}: Feed inventory reset.")
        updateDashboardDevice()
    } else if (action == "refillWaterBtn") {
        state["virtualWaterGal_${i}"] = (settings["waterCapacity_${i}"] ?: 5.0) as BigDecimal
        state["waterAlertSent_${i}"] = false
        logDebug("${coopName}: Water inventory reset.")
        updateDashboardDevice()
    } else if (action == "deepCleanBtn") {
        state["lastDeepClean_${i}"] = new Date().time
        logDebug("${coopName}: Deep Clean Logged.")
    }
}

def calculateAverage(i, maxDays) {
    def logArr = state["eggLog_${i}"]
    if (!logArr || logArr.size() == 0) return 0.0
    def total = 0.0
    def count = 0
    def limit = Math.min(logArr.size(), maxDays)
    if (limit == 0) return 0.0
    
    for (int j = 0; j < limit; j++) {
        total += (logArr[j].count as BigDecimal)
        count++
    }
    return ((total / count) as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
}

def calculateCostPerEgg(i) {
    def cost = settings["feedBagCost_${i}"]
    def eggs = state["eggsSinceRefill_${i}"]
    if (!cost || !eggs || eggs == 0) return 0.00
    return ((cost / eggs) as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)
}

def midnightReset() {
    def yesterday = new Date() - 1
    def dateStr = yesterday.format("yyyy-MM-dd", location.timeZone)
    def count = (settings.coopCount ?: "1") as Integer

    for (int i = 1; i <= count; i++) {
        def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
        def isMolting = settings["moltingMode_${i}"] ?: false
        
        if (!state["eggLog_${i}"]) state["eggLog_${i}"] = []
        state["eggLog_${i}"].add(0, [date: dateStr, count: state["eggsToday_${i}"]])
        if (state["eggLog_${i}"].size() > 30) state["eggLog_${i}"] = state["eggLog_${i}"].take(30)
        state["eggsToday_${i}"] = 0
        
        // Predictive Health Alert
        if (notifyHealth && !isMolting) {
            def day3Avg = calculateAverage(i, 3)
            def day14Avg = calculateAverage(i, 14)
            if (day14Avg >= 1.0 && day3Avg < (day14Avg * 0.70)) {
                sendAlert("[${coopName}] HEALTH WARNING: 3-day lay rate (${day3Avg}/day) has dropped >30% below your 14-day average (${day14Avg}/day). Check for predators, illness, or mites.", null)
            }
        }
        
        // Adaptive Feed & Water Engine (Now using specific Trend states)
        def birdCount = (settings["birdCount_${i}"] ?: 6) as Integer
        def lowT = state["todayOutLow_${i}"] ?: (state["todayInLow_${i}"] ?: 50.0)
        def highT = state["todayOutHigh_${i}"] ?: (state["todayInHigh_${i}"] ?: 50.0)
        
        def baseFeedPerBird = (settings["baseFeedPerBird_${i}"] != null) ? (settings["baseFeedPerBird_${i}"] as BigDecimal) : 0.25
        def baseWaterPerBird = (settings["baseWaterPerBird_${i}"] != null) ? (settings["baseWaterPerBird_${i}"] as BigDecimal) : 0.13
        
        // Feed calculation with BigDecimal casting fix
        if (settings["feedBagSize_${i}"]) {
            def baseFeed = (birdCount * baseFeedPerBird) as BigDecimal
            def actualFeed = lowT < 32 ? (baseFeed * 1.25) : baseFeed
            def currentFeed = (state["virtualFeedLbs_${i}"] ?: 0.0) as BigDecimal
            def newFeed = currentFeed - actualFeed
            
            state["virtualFeedLbs_${i}"] = newFeed < 0 ? 0.0 : newFeed.setScale(1, BigDecimal.ROUND_HALF_UP)
            
            def estFeedDays = baseFeed > 0 ? state["virtualFeedLbs_${i}"] / baseFeed : 0
            if (estFeedDays <= 2.0 && notifyFeed && !state["feedAlertSent_${i}"]) {
                sendAlert("[${coopName}] Feed is low (Estimated < 2 days remaining). Time to refill!", null)
                state["feedAlertSent_${i}"] = true
            }
        }
        
        // Water calculation with BigDecimal casting fix
        if (settings["waterCapacity_${i}"]) {
            def baseWater = (birdCount * baseWaterPerBird) as BigDecimal
            def actualWater = highT > 85 ? (baseWater * 1.5) : baseWater
            def currentWater = (state["virtualWaterGal_${i}"] ?: 0.0) as BigDecimal
            def newWater = currentWater - actualWater
            
            state["virtualWaterGal_${i}"] = newWater < 0 ? 0.0 : newWater.setScale(2, BigDecimal.ROUND_HALF_UP)
            
            def estWaterDays = baseWater > 0 ? state["virtualWaterGal_${i}"] / baseWater : 0
            if (estWaterDays <= 2.0 && notifyFeed && !state["waterAlertSent_${i}"] && !settings["waterFloat_${i}"]) {
                sendAlert("[${coopName}] Virtual Water is low (Estimated < 2 days remaining). Time to refill!", null)
                state["waterAlertSent_${i}"] = true
            }
        }
        
        // Roll Today's Trends into Yesterday's, then reset Today's
        state["yesterdayInHigh_${i}"] = state["todayInHigh_${i}"] ?: "--"
        state["yesterdayInLow_${i}"] = state["todayInLow_${i}"] ?: "--"
        state["todayInHigh_${i}"] = null
        state["todayInLow_${i}"] = null
        
        state["yesterdayOutHigh_${i}"] = state["todayOutHigh_${i}"] ?: "--"
        state["yesterdayOutHigh_${i}"] = state["todayOutHigh_${i}"] ?: "--"
        state["todayOutHigh_${i}"] = null
        state["todayOutLow_${i}"] = null
        
        // Maintenance
        def lClean = state["lastDeepClean_${i}"]
        def cInt = settings["cleanInterval_${i}"]
        if (lClean && cInt && notifyMaintenance) {
            def daysSince = Math.floor((new Date().time - lClean) / 86400000) as Integer
            if (daysSince >= cInt) sendAlert("[${coopName}] Reminder: It's been ${daysSince} days since the last deep clean.", null)
        }
    }
    
    updateDashboardDevice()
    logDebug("Midnight reset complete.")
}

// --- SCHEDULING WRAPPERS ---
def scheduleSunEvents(evt = null) {
    def sunInfo = getSunriseAndSunset()
    def sunrise = sunInfo?.sunrise
    def sunset = sunInfo?.sunset
    def count = (settings.coopCount ?: "1") as Integer
    def now = new Date()

    for (int i = 1; i <= count; i++) {
        def oOff = settings["openOffset_${i}"]
        def cOff = settings["closeOffset_${i}"]
        
        if (sunrise && oOff != null) {
            def openTime = new Date(sunrise.time + (oOff * 60000))
            if (openTime.before(now)) openTime = new Date(openTime.time + 86400000)
            schedule(openTime, "openCoopDoor${i}")
        }
        
        if (sunset && cOff != null) {
            def closeTime = new Date(sunset.time + (cOff * 60000))
            if (closeTime.before(now)) closeTime = new Date(closeTime.time + 86400000)
            schedule(closeTime, "closeCoopDoor${i}")
        }
        
        if (settings["enableLighting_${i}"] && settings["lightOffTime_${i}"] == "Sunrise" && sunrise) {
            def lightOff = sunrise
            if (lightOff.before(now)) lightOff = new Date(lightOff.time + 86400000)
            schedule(lightOff, "turnOffLights${i}")
        }
    }
}

// Method Generators for Coops 1-3
def openCoopDoor1() { handleDoorOpen(1) }
def openCoopDoor2() { handleDoorOpen(2) }
def openCoopDoor3() { handleDoorOpen(3) }

def closeCoopDoor1() { handleDoorClose(1) }
def closeCoopDoor2() { handleDoorClose(2) }
def closeCoopDoor3() { handleDoorClose(3) }

def verifyDoorClosed1() { handleVerifyDoor(1) }
def verifyDoorClosed2() { handleVerifyDoor(2) }
def verifyDoorClosed3() { handleVerifyDoor(3) }

def turnOnLights1() { handleLights(1, true) }
def turnOnLights2() { handleLights(2, true) }
def turnOnLights3() { handleLights(3, true) }

def turnOffLights1() { handleLights(1, false) }
def turnOffLights2() { handleLights(2, false) }
def turnOffLights3() { handleLights(3, false) }

def failSafeCloseWater1() { handleWaterFailSafe(1) }
def failSafeCloseWater2() { handleWaterFailSafe(2) }
def failSafeCloseWater3() { handleWaterFailSafe(3) }

def clearSOS1() { handleClearSOS(1) }
def clearSOS2() { handleClearSOS(2) }
def clearSOS3() { handleClearSOS(3) }

def stormHandler1(evt) { handleStorm(evt, 1) }
def stormHandler2(evt) { handleStorm(evt, 2) }
def stormHandler3(evt) { handleStorm(evt, 3) }

def windHandler1(evt) { handleWind(evt, 1) }
def windHandler2(evt) { handleWind(evt, 2) }
def windHandler3(evt) { handleWind(evt, 3) }

def climateHandler1(evt) { handleClimate(evt, 1) }
def climateHandler2(evt) { handleClimate(evt, 2) }
def climateHandler3(evt) { handleClimate(evt, 3) }

def forceClimateCheck1() { doForceClimateCheck(1) }
def forceClimateCheck2() { doForceClimateCheck(2) }
def forceClimateCheck3() { doForceClimateCheck(3) }

def doForceClimateCheck(i) {
    def dOutTemp = settings["outdoorTempSensor_${i}"]
    if (dOutTemp) {
        def tempVal = dOutTemp.currentValue("temperature")
        if (tempVal != null) {
            handleOutdoorClimate([value: tempVal], i)
        }
    }
}

def healthGuardianHandler1(evt) { handleHealthGuardian(evt, 1) }
def healthGuardianHandler2(evt) { handleHealthGuardian(evt, 2) }
def healthGuardianHandler3(evt) { handleHealthGuardian(evt, 3) }

def outdoorClimateHandler1(evt) { handleOutdoorClimate(evt, 1) }
def outdoorClimateHandler2(evt) { handleOutdoorClimate(evt, 2) }
def outdoorClimateHandler3(evt) { handleOutdoorClimate(evt, 3) }

def outdoorHumHandler1(evt) { handleOutdoorHum(evt, 1) }
def outdoorHumHandler2(evt) { handleOutdoorHum(evt, 2) }
def outdoorHumHandler3(evt) { handleOutdoorHum(evt, 3) }

def aqiHandler1(evt) { handleAqi(evt, 1) }
def aqiHandler2(evt) { handleAqi(evt, 2) }
def aqiHandler3(evt) { handleAqi(evt, 3) }

def waterHandler1(evt) { handleWater(evt, 1) }
def waterHandler2(evt) { handleWater(evt, 2) }
def waterHandler3(evt) { handleWater(evt, 3) }

def securityHandler1(evt) { handleSecurity(evt, 1) }
def securityHandler2(evt) { handleSecurity(evt, 2) }
def securityHandler3(evt) { handleSecurity(evt, 3) }

// --- CORE HANDLER LOGIC ---

def isDaylight() {
    def sunInfo = getSunriseAndSunset()
    def now = new Date()
    if (sunInfo?.sunrise && sunInfo?.sunset) {
        return now.after(sunInfo.sunrise) && now.before(sunInfo.sunset)
    }
    return false
}

def getEffectiveTempOffset(i) {
    def baseOffset = (settings["estTempOffset_${i}"] ?: 5.0) as BigDecimal
    def shadePct = (settings["sunshadePercent_${i}"] ?: 0) as BigDecimal
    
    if (shadePct > 0 && isDaylight()) {
        return baseOffset * (1.0 - (shadePct / 100.0))
    }
    return baseOffset
}

def handleDoorOpen(i) {
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    def sSwitch = settings["stormSwitch_${i}"]
    def cDoor = settings["coopDoor_${i}"]
    def dContact = settings["doorContact_${i}"]

    if (sSwitch && sSwitch.currentValue("switch") == "on") {
        logDebug("${coopName}: Skipping scheduled door open: Storm Switch ACTIVE.")
        return
    }
    
    // Reset daily open vent reminder so it triggers again for the new day
    state["ventOpenAlertSent_${i}"] = false
    runIn(15, "forceClimateCheck${i}")
    
    // Check if the door is already physically open (And Sensor is Healthy)
    def isAlreadyOpen = false
    def dHealthy = isSensorHealthy(dContact, settings.sensorHealthHours)
    
    if (dContact && dHealthy) {
        if (dContact.currentValue("contact") == "open") isAlreadyOpen = true
    } else if (cDoor) {
        if (cDoor.currentValue("door") == "open") isAlreadyOpen = true
    }

    def sensorWarning = (!dHealthy && dContact) ? " [⚠️ Sensor Offline/Stale]" : ""

    if (isAlreadyOpen) {
        def pomHours = settings.pomBypassHours
        def lastPom = state["lastPomTime_doorOpen_${i}"] ?: 0
        def nowMs = new Date().time
        
        if (pomHours != null && pomHours > 0 && (nowMs - lastPom) >= (pomHours * 3600000)) {
            if (notifyDoor) queueGroupingAlert("pomDoorOpen", coopName, "Peace of Mind: Morning schedule reached. Door is already OPEN for %COOPS%.", settings.audioDoorOpen)
            state["lastPomTime_doorOpen_${i}"] = nowMs
        } else {
            logDebug("${coopName}: Door is already open. Skipping reminders.")
        }
        updateDashboardDevice()
        return
    }

    // Since we are sending a real notification, reset the Peace of Mind timer
    state["lastPomTime_doorOpen_${i}"] = new Date().time

    if (manualMode || !cDoor) {
        if (notifyDoor) queueGroupingAlert("doorOpenManual", coopName, "Reminder: Manually OPEN the door for %COOPS%!", settings.audioDoorOpen, sensorWarning)
    } else {
        logDebug("${coopName}: Opening Door.")
        cDoor.open()
        if (notifyDoor) queueGroupingAlert("doorOpenAuto", coopName, "Automated doors are opening for %COOPS%.", settings.audioDoorOpen, sensorWarning)
    }
    updateDashboardDevice()
}

def handleDoorClose(i) {
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    def cDoor = settings["coopDoor_${i}"]
    def dContact = settings["doorContact_${i}"]

    // Reset daily close vent reminder so it triggers again for the coming night
    state["ventCloseAlertSent_${i}"] = false
    runIn(15, "forceClimateCheck${i}")

    // Check if the door is already physically closed (And Sensor is Healthy)
    def isAlreadyClosed = false
    def dHealthy = isSensorHealthy(dContact, settings.sensorHealthHours)
    
    if (dContact && dHealthy) {
        if (dContact.currentValue("contact") == "closed") isAlreadyClosed = true
    } else if (cDoor) {
        if (cDoor.currentValue("door") == "closed") isAlreadyClosed = true
    }

    def sensorWarning = (!dHealthy && dContact) ? " [⚠️ Sensor Offline/Stale]" : ""

    if (isAlreadyClosed) {
        def pomHours = settings.pomBypassHours
        def lastPom = state["lastPomTime_doorClose_${i}"] ?: 0
        def nowMs = new Date().time
        
        if (pomHours != null && pomHours > 0 && (nowMs - lastPom) >= (pomHours * 3600000)) {
            if (notifyDoor) queueGroupingAlert("pomDoorClose", coopName, "Peace of Mind: Evening schedule reached. Door is already CLOSED for %COOPS%.", settings.audioDoorClose)
            state["lastPomTime_doorClose_${i}"] = nowMs
        } else {
            logDebug("${coopName}: Door is already closed. Skipping reminders.")
        }
        updateDashboardDevice()
        return
    }

    // Since we are sending a real notification, reset the Peace of Mind timer
    state["lastPomTime_doorClose_${i}"] = new Date().time

    if (manualMode || !cDoor) {
        if (notifyDoor) queueGroupingAlert("doorCloseManual", coopName, "Reminder: Manually CLOSE the door for %COOPS%!", settings.audioDoorClose, sensorWarning)
    } else {
        logDebug("${coopName}: Closing Door.")
        cDoor.close()
        if (notifyDoor) queueGroupingAlert("doorCloseAuto", coopName, "Automated doors are closing for %COOPS%.", settings.audioDoorClose, sensorWarning)
        if (dContact && notifyTardy) runIn(300, "verifyDoorClosed${i}")
    }
    updateDashboardDevice()
}

def handleVerifyDoor(i) {
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    def dContact = settings["doorContact_${i}"]
    def dHealthy = isSensorHealthy(dContact, settings.sensorHealthHours)
    
    // Only verify if the sensor is healthy. If it's failed, it will just spam tardy alerts every day.
    if (dContact && dHealthy && dContact.currentValue("contact") == "open") {
        log.warn "CRITICAL [${coopName}]: Door failed to close!"
        sendAlert("[${coopName}] CRITICAL: Jammed Door/Tardy Chicken! Door is OPEN 5 mins after close time.", "Warning, the ${coopName} door is jammed.")
    }
}

def handleStorm(evt, i) {
    if (evt.value == "on") {
        def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
        if (notifyStorm) sendAlert("[${coopName}] Storm alert! Securing flock.", settings.audioStorm)
        handleDoorClose(i)
    }
}

def handleWind(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    def thresh = settings["windThreshold_${i}"]
    def windSpd = evt.value as BigDecimal
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    
    // Trip wind-chill recalculation when wind changes
    handleClimate(null, i)
    
    if (!thresh) return
    if (windSpd >= thresh && !state["windAlertSent_${i}"]) {
        if (notifyStorm) sendAlert("[${coopName}] SEVERE WIND: Gusts over ${thresh} MPH. Securing door!", settings.audioStorm)
        state["windAlertSent_${i}"] = true
        if (settings["coopDoor_${i}"] && !manualMode) handleDoorClose(i)
    } else if (windSpd < (thresh - 10)) {
        state["windAlertSent_${i}"] = false 
    }
}

def handleAqi(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    def aqiVal = 0
    try {
        aqiVal = evt.value as Integer
    } catch (Exception e) {
        logDebug("AQI value is not an integer: ${evt.value}")
        return
    }

    def thresh = settings["aqiThreshold_${i}"] ?: 100
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    
    if (aqiVal >= thresh) {
        state["aqiOverride_${i}"] = true
        if (notifyAqi && !state["aqiAlertSent_${i}"]) {
            queueGroupingAlert("aqiAlert", coopName, "RESPIRATORY WARNING: AQI is Unhealthy (${aqiVal}). Automatically shutting off exhaust fans for %COOPS% to prevent pulling in smoke.", null)
            state["aqiAlertSent_${i}"] = true
        }
        def fan = settings["exhaustFan_${i}"]
        if (fan && !manualMode) fan.off()
    } else {
        state["aqiOverride_${i}"] = false
        state["aqiAlertSent_${i}"] = false
        // Re-evaluate the climate to see if fans need to be turned back on
        handleClimate(null, i)
    }
}

def handleClimate(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    
    def isEst = false
    def currentTemp
    def now = new Date().time
    
    if (evt?.value != null) {
        currentTemp = evt.value as BigDecimal
        if (evt.device && settings["outdoorTempSensor_${i}"] && evt.device.id == settings["outdoorTempSensor_${i}"].id) {
            isEst = true
        }
    } else {
        def tData = getCoopTemperature(i)
        currentTemp = tData.temp
        isEst = tData.isEstimated
    }

    if (currentTemp == null) return

    // High/Low Tracking for Today's Indoor Trend
    if (state["todayInHigh_${i}"] == null || currentTemp > state["todayInHigh_${i}"]) state["todayInHigh_${i}"] = currentTemp
    if (state["todayInLow_${i}"] == null || currentTemp < state["todayInLow_${i}"]) state["todayInLow_${i}"] = currentTemp

    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    
    // Wind Chill Calculation (ONLY apply to estimated outdoor temps, not indoor sensors)
    def effectiveTemp = currentTemp
    def dWind = settings["windSensor_${i}"]
    def windSpd = dWind ? (dWind.currentValue("windSpeed") as BigDecimal) : 0.0
    
    if (isEst && currentTemp <= 50 && windSpd >= 3) {
        effectiveTemp = calculateWindChill(currentTemp, windSpd)
    }
    
    // Predictive Thermal Logic 
    def isPredictiveHeating = false
    def lastTemp = state["lastTemp_${i}"]
    def lastTime = state["lastTempTime_${i}"]
    
    if (settings["enablePredictiveTemp_${i}"] && lastTemp != null && lastTime != null) {
        def timeDiffHours = (now - lastTime) / 3600000.0
        if (timeDiffHours > 0.08) { 
            def tempDiff = (lastTemp as BigDecimal) - effectiveTemp 
            def dropRate = tempDiff / timeDiffHours
            def pRate = (settings["predictiveDropRate_${i}"] ?: 3.0) as BigDecimal
            def pThresh = (settings["predictiveThreshold_${i}"] ?: 45) as BigDecimal
            
            if (dropRate >= pRate && effectiveTemp <= pThresh) {
                isPredictiveHeating = true
                logDebug("[${coopName}] Predictive Defense Active! Effective Temp dropping at ${String.format("%.1f", dropRate)}°/hr.")
            }
        }
    }
    
    // Update State for next check
    state["lastTemp_${i}"] = effectiveTemp
    state["lastTempTime_${i}"] = now
    state["predictiveActive_${i}"] = isPredictiveHeating

    def highTemp = settings["alertHighCoopTemp_${i}"]
    def lowTemp = settings["alertLowCoopTemp_${i}"]

    if (notifyTemp) {
        if (highTemp && currentTemp >= highTemp && !state["highTempAlertSent_${i}"]) {
            queueGroupingAlert("highTempAlert", coopName, "CLIMATE WARNING: It is extremely hot (${currentTemp}°). Check %COOPS%!", null)
            state["highTempAlertSent_${i}"] = true
        } else if (highTemp && currentTemp < (highTemp - 2)) {
            state["highTempAlertSent_${i}"] = false
        }
        
        if (lowTemp && effectiveTemp <= lowTemp && !state["lowTempAlertSent_${i}"]) {
            def windNotice = isEst ? " (Feels like ${effectiveTemp}°)" : ""
            queueGroupingAlert("lowTempAlert", coopName, "CLIMATE WARNING: It is freezing${windNotice}. Check %COOPS%!", null)
            state["lowTempAlertSent_${i}"] = true
        } else if (lowTemp && effectiveTemp > (lowTemp - 2)) {
            state["lowTempAlertSent_${i}"] = false
        }
    }
    
    handleHealthGuardian(null, i)
    if (manualMode) return
    
    def fan = settings["exhaustFan_${i}"]
    def fOn = settings["fanOnTemp_${i}"]
    def fOff = settings["fanOffTemp_${i}"]
    if (fan && fOn != null && fOff != null && !state["aqiOverride_${i}"]) {
        def fs = fan.currentValue("switch")
        if (currentTemp >= fOn && fs != "on") fan.on()
        else if (currentTemp <= fOff && fs != "off") fan.off()
    } else if (fan && state["aqiOverride_${i}"]) {
        if (fan.currentValue("switch") != "off") fan.off()
    }
    
    // Heaters and De-icers use Effective Temp 
    def heat = settings["coopHeater_${i}"]
    def hOn = settings["heaterOnTemp_${i}"]
    def hOff = settings["heaterOffTemp_${i}"]
    if (heat && hOn != null && hOff != null) {
        def hs = heat.currentValue("switch")
        if ((effectiveTemp <= hOn || isPredictiveHeating) && hs != "on") heat.on()
        else if (effectiveTemp >= hOff && !isPredictiveHeating && hs != "off") heat.off()
    }
    
    def wHeat = settings["waterHeater_${i}"]
    def wFreeze = settings["waterFreezeTemp_${i}"]
    if (wHeat && wFreeze != null) {
        def whs = wHeat.currentValue("switch")
        if ((effectiveTemp <= wFreeze || isPredictiveHeating) && whs != "on") wHeat.on()
        else if (effectiveTemp > wFreeze && !isPredictiveHeating && whs != "off") wHeat.off()
    }
}

def handleHealthGuardian(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    if (!notifyHealth) return
    
    def tData = getCoopTemperature(i)
    def hData = getCoopHumidity(i)
    
    if (tData.temp == null || hData.hum == null) return

    def currentTemp = tData.temp
    def currentHum = hData.hum
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"

    if (currentTemp <= 35 && currentHum >= 70) {
        if (!state["frostbiteAlertSent_${i}"]) {
            queueGroupingAlert("frostbite", coopName, "HEALTH: Frostbite Risk! High humidity at freezing temps for %COOPS%.", null)
            state["frostbiteAlertSent_${i}"] = true
        }
    } else if (currentHum < 65 || currentTemp > 38) {
        state["frostbiteAlertSent_${i}"] = false
    }
    
    if (currentTemp >= 85 && currentHum >= 70) {
        if (!state["heatAlertSent_${i}"]) {
            queueGroupingAlert("heatStress", coopName, "HEALTH: Heat Stress Risk! High temp & humidity limits cooling for %COOPS%.", null)
            state["heatAlertSent_${i}"] = true
        }
    } else if (currentTemp < 80 || currentHum < 65) {
        state["heatAlertSent_${i}"] = false
    }
}

def handleOutdoorClimate(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    def currentTemp = evt.value as BigDecimal
    def mAirOpen = settings["manualAirflowTemp_${i}"]
    def mAirClose = settings["closeAirflowTemp_${i}"]
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    
    // High/Low Tracking for Today's Outdoor Trend
    if (state["todayOutHigh_${i}"] == null || currentTemp > state["todayOutHigh_${i}"]) state["todayOutHigh_${i}"] = currentTemp
    if (state["todayOutLow_${i}"] == null || currentTemp < state["todayOutLow_${i}"]) state["todayOutLow_${i}"] = currentTemp
    
    def vContact = settings["ventContact_${i}"]
    def vHealthy = isSensorHealthy(vContact, settings.sensorHealthHours)
    def isVentOpen = (vContact && vHealthy) ? (vContact.currentValue("contact") == "open") : false
    def isVentClosed = (vContact && vHealthy) ? (vContact.currentValue("contact") == "closed") : false
    def sensorWarning = (!vHealthy && vContact) ? " [⚠️ Sensor Offline]" : ""
    
    def pomHours = settings.pomBypassHours
    def nowMs = new Date().time
    
    if (notifyAirflow) {
        if (mAirOpen != null && currentTemp >= mAirOpen && !state["ventOpenAlertSent_${i}"]) {
            if (vContact && isVentOpen) {
                def lastPom = state["lastPomTime_ventOpen_${i}"] ?: 0
                if (pomHours != null && pomHours > 0 && (nowMs - lastPom) >= (pomHours * 3600000)) {
                    queueGroupingAlert("pomVentOpen", coopName, "Peace of Mind: Temp is ${currentTemp}°. Vents are already OPEN for %COOPS%.", settings.audioVentOpen)
                    state["lastPomTime_ventOpen_${i}"] = nowMs
                } else {
                    logDebug("${coopName}: Vents already open. Muting OPEN reminder.")
                }
                state["ventOpenAlertSent_${i}"] = true
                state["ventCloseAlertSent_${i}"] = false 
            } else {
                queueGroupingAlert("ventOpen", coopName, "Reminder: Outdoor temp rising (${currentTemp}°). OPEN vents/airflow doors for %COOPS%!", settings.audioVentOpen, sensorWarning)
                state["lastPomTime_ventOpen_${i}"] = nowMs // Reset timer since we actually spoke
                state["ventOpenAlertSent_${i}"] = true
                state["ventCloseAlertSent_${i}"] = false 
            }
        } else if (mAirClose != null && currentTemp <= mAirClose && !state["ventCloseAlertSent_${i}"]) {
            if (vContact && isVentClosed) {
                def lastPom = state["lastPomTime_ventClose_${i}"] ?: 0
                if (pomHours != null && pomHours > 0 && (nowMs - lastPom) >= (pomHours * 3600000)) {
                    queueGroupingAlert("pomVentClose", coopName, "Peace of Mind: Temp is ${currentTemp}°. Vents are already CLOSED for %COOPS%.", settings.audioVentClose)
                    state["lastPomTime_ventClose_${i}"] = nowMs
                } else {
                    logDebug("${coopName}: Vents already closed. Muting CLOSE reminder.")
                }
                state["ventCloseAlertSent_${i}"] = true
                state["ventOpenAlertSent_${i}"] = false 
            } else {
                queueGroupingAlert("ventClose", coopName, "Reminder: Outdoor temp dropping (${currentTemp}°). CLOSE vents/airflow doors for %COOPS%!", settings.audioVentClose, sensorWarning)
                state["lastPomTime_ventClose_${i}"] = nowMs // Reset timer since we actually spoke
                state["ventCloseAlertSent_${i}"] = true
                state["ventOpenAlertSent_${i}"] = false 
            }
        }
    }
    
    // Failover Check: Only push outdoor temp to the climate logic if the indoor sensor is dead/missing
    def indoorHealthy = settings["tempSensor_${i}"] && isSensorHealthy(settings["tempSensor_${i}"], settings.sensorHealthHours)
    if (!indoorHealthy) {
        def estTemp = currentTemp + getEffectiveTempOffset(i)
        handleClimate([value: estTemp, device: evt?.device], i)
    }
}

def handleOutdoorHum(evt, i) {
    if (evt) genericSensorHeartbeat(evt)
    if (!settings["humSensor_${i}"]) {
        handleHealthGuardian(null, i)
    }
}

def handleWater(evt, i) {
    def valve = settings["waterValve_${i}"]
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"

    if (evt.value == "dry") {
        if (notifyWater) sendAlert("[${coopName}] Water is low!", settings.audioWater)
        if (valve && !manualMode) {
            valve.open()
            runIn((settings["maxFillTime_${i}"] ?: 5) * 60, "failSafeCloseWater${i}")
        }
    } else if (evt.value == "wet" && valve && !manualMode) {
        valve.close()
        unschedule("failSafeCloseWater${i}")
        
        // Automatically sync virtual tracking with physical fill!
        state["virtualWaterGal_${i}"] = (settings["waterCapacity_${i}"] ?: 5.0) as BigDecimal
        state["waterAlertSent_${i}"] = false
        logDebug("Auto-Valve closed. Synced virtual water level back to max capacity.")
        updateDashboardDevice()
    }
}

def handleWaterFailSafe(i) {
    def valve = settings["waterValve_${i}"]
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    log.warn "CRITICAL [${coopName}]: Water fill hit time limit."
    if (valve) valve.close()
    sendAlert("[${coopName}] CRITICAL: Water valve hit maximum fill time and force-closed.", "Warning, ${coopName} water timeout.")
}

def handleLights(i, turnOn) {
    if (settings["moltingMode_${i}"]) {
        logDebug("Coop ${i} is in Molting Mode. Lights remain off to allow rest.")
        if (settings["coopLights_${i}"]) settings["coopLights_${i}"].off()
        return
    }

    def cLights = settings["coopLights_${i}"]
    def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
    
    if (manualMode || !cLights) {
        if (notifyDoor) sendAlert("[${coopName}] Reminder: Manually turn ${turnOn ? 'ON' : 'OFF'} the lights.", null)
    } else {
        if (turnOn) cLights.on() else cLights.off()
    }
}

def handleSecurity(evt, i) {
    if (evt.value != "active") return
    def now = new Date()
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunrise || !sunInfo.sunset) return
    
    if (now.after(sunInfo.sunset) || now.before(sunInfo.sunrise)) {
        if (!state["sosActive_${i}"]) {
            state["sosActive_${i}"] = true
            def coopName = settings["coopName_${i}"] ?: "Coop ${i}"
            if (notifySecurity) sendAlert("🚨 [${coopName}] Nighttime motion detected!", settings.audioSecurity, false, "security")
            
            if (!manualMode) {
                if (settings["sosLights_${i}"]) settings["sosLights_${i}"].on()
                if (settings["sosSiren_${i}"]) settings["sosSiren_${i}"].siren()
            }
            runIn(settings["sosDuration_${i}"] ?: 30, "clearSOS${i}")
        }
    }
}

def handleClearSOS(i) {
    state["sosActive_${i}"] = false
    if (!manualMode) {
        if (settings["sosLights_${i}"]) settings["sosLights_${i}"].off()
        if (settings["sosSiren_${i}"]) settings["sosSiren_${i}"].off()
    }
}

def logDebug(msg) {
    if (settings.debugLogging) log.debug "Coop Command: ${msg}"
}
