/**
 * Advanced Severe Weather Detector
 */

definition(
    name: "Advanced Severe Weather Detector",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "BMS-Grade multi-dimensional predictive hazard detection engine featuring WBGT, Kinetic Air Density, Storm Vectoring, Structural Wind Load, EMI Guard, and MSLP calibration.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "configPage")
    page(name: "speakerMappingPage")
    page(name: "dnaConfigPage")
}

def renderChartHTML() {
    def buildJsArray = { hist ->
        if (!hist) return "[]"
        return "[" + hist.collect { "{ x: ${it.time}, y: ${it.value} }" }.join(",") + "]"
    }

    def buildLightningBars = { hist ->
        if (!hist || hist.size() == 0) return "[]"
        def counts = [:]
        hist.each { entry ->
            def hourMillis = entry.time - (entry.time % 3600000) 
            counts[hourMillis] = (counts[hourMillis] ?: 0) + 1
        }
        return "[" + counts.collect { "{ x: ${it.key}, y: ${it.value} }" }.join(",") + "]"
    }

    def tempJs = buildJsArray(state.tempHistory)
    def pressJs = buildJsArray(state.pressureHistory)
    def spreadJs = buildJsArray(state.spreadHistory)
    def probJs = buildJsArray(state.probHistory)
    
    def windJs = buildJsArray(state.windHistory)
    def lightJs = buildLightningBars(state.lightningHistory)
    
    def windUnit = isMetric() ? "km/h" : "mph"

    return """
    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;">24-Hour Thermodynamic Timeline</h4>
    <div id="chartWrapper1" style="position: relative; height: 350px; width: 100%; background: #fdfdfd; border: 1px solid #eee; border-radius: 4px; margin-bottom: 20px;">
        <div id="chartLoadingText1" style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #555; font-weight: bold; font-size: 14px;">
            Loading chart data...
        </div>
        <canvas id="weatherChart" style="opacity: 0; transition: opacity 0.4s ease-in-out;"></canvas>
    </div>

    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;">Wind & Lightning Activity</h4>
    <div id="chartWrapper2" style="position: relative; height: 250px; width: 100%; background: #fdfdfd; border: 1px solid #eee; border-radius: 4px;">
        <div id="chartLoadingText2" style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #555; font-weight: bold; font-size: 14px;">
            Loading activity data...
        </div>
        <canvas id="windLightChart" style="opacity: 0; transition: opacity 0.4s ease-in-out;"></canvas>
    </div>
    
    <script>
    (function() {
        var chartLibs = [
            "https://cdn.jsdelivr.net/npm/chart.js",
            "https://cdn.jsdelivr.net/npm/luxon",
            "https://cdn.jsdelivr.net/npm/chartjs-adapter-luxon"
        ];
        
        function loadScript(url, callback) {
            if (document.querySelector('script[src="' + url + '"]')) {
                if (callback) callback();
                return;
            }
            var s = document.createElement('script');
            s.type = 'text/javascript';
            s.src = url;
            s.onload = callback;
            document.head.appendChild(s);
        }

        function initChart() {
            var canvas1 = document.getElementById('weatherChart');
            var loading1 = document.getElementById('chartLoadingText1');
            var canvas2 = document.getElementById('windLightChart');
            var loading2 = document.getElementById('chartLoadingText2');
            
            if (!canvas1 || !canvas2 || typeof Chart === 'undefined' || typeof luxon === 'undefined') {
                setTimeout(initChart, 100);
                return;
            }
            
            if (window.myWeatherChart) window.myWeatherChart.destroy();
            if (window.myWindChart) window.myWindChart.destroy();
            
            var ctx1 = canvas1.getContext('2d');
            window.myWeatherChart = new Chart(ctx1, {
                type: 'line',
                data: {
                    datasets: [
                        { label: 'Temperature (°)', data: ${tempJs}, borderColor: 'rgb(255, 99, 132)', yAxisID: 'yTemp', tension: 0.3, pointRadius: 0 },
                        { label: 'Dew Point Spread (°)', data: ${spreadJs}, borderColor: 'rgb(54, 162, 235)', yAxisID: 'yTemp', borderDash: [5, 5], tension: 0.3, pointRadius: 0 },
                        { label: 'Pressure', data: ${pressJs}, borderColor: 'rgb(75, 192, 192)', yAxisID: 'yPress', tension: 0.3, pointRadius: 0 },
                        { label: 'Hazard Probability (%)', data: ${probJs}, backgroundColor: 'rgba(153, 102, 255, 0.2)', borderColor: 'rgb(153, 102, 255)', yAxisID: 'yProb', fill: true, stepped: true, pointRadius: 0 }
                    ]
                },
                options: {
                    responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
                    scales: {
                        x: { type: 'time', time: { unit: 'hour' }, title: { display: false } },
                        yTemp: { type: 'linear', display: true, position: 'left', title: { display: true, text: 'Temp / Spread' } },
                        yPress: { type: 'linear', display: true, position: 'right', title: { display: true, text: 'Pressure' }, grid: { drawOnChartArea: false } },
                        yProb: { type: 'linear', display: true, position: 'right', min: 0, max: 100, title: { display: true, text: 'Probability %' }, grid: { drawOnChartArea: false } }
                    }
                }
            });

            var ctx2 = canvas2.getContext('2d');
            window.myWindChart = new Chart(ctx2, {
                type: 'bar',
                data: {
                    datasets: [
                        { type: 'line', label: 'Wind Speed (${windUnit})', data: ${windJs}, borderColor: 'rgb(255, 159, 64)', backgroundColor: 'rgba(255, 159, 64, 0.2)', yAxisID: 'yWind', tension: 0.3, pointRadius: 0, fill: true },
                        { type: 'bar', label: 'Lightning Strikes (per hr)', data: ${lightJs}, backgroundColor: 'rgba(255, 205, 86, 0.8)', yAxisID: 'yLight', barThickness: 15 }
                    ]
                },
                options: {
                    responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
                    scales: {
                        x: { type: 'time', time: { unit: 'hour' }, title: { display: false }, offset: true },
                        yLight: { type: 'linear', display: true, position: 'left', min: 0, title: { display: true, text: 'Strikes/Hr' }, ticks: { precision: 0 } },
                        yWind: { type: 'linear', display: true, position: 'right', min: 0, title: { display: true, text: 'Wind (${windUnit})' }, grid: { drawOnChartArea: false } }
                    }
                }
            });

            if (loading1) loading1.style.display = 'none';
            if (canvas1) canvas1.style.opacity = '1';
            if (loading2) loading2.style.display = 'none';
            if (canvas2) canvas2.style.opacity = '1';
        }

        loadScript(chartLibs[0], function() {
            loadScript(chartLibs[1], function() {
                loadScript(chartLibs[2], function() {
                    initChart();
                });
            });
        });
    })();
    </script>
    """
}

def renderTableHTML() {
    if (!state.tempHistory || state.tempHistory.size() == 0) {
        return "<h4 style='margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;'>24-Hour Timeline</h4><div>Gathering history data... check back in a few minutes.</div>"
    }
    
    def reversedHist = state.tempHistory.reverse()
    
    def tableHTML = """
    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;">24-Hour Local Data Table</h4>
    <div style="height: 350px; overflow-y: auto; border: 1px solid #eee;">
        <table style="width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; font-family: monospace;">
            <thead style="background: #eee; position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);">
                <tr>
                    <th style="padding: 8px; border-bottom: 1px solid #ccc;">Time</th>
                    <th style="padding: 8px; border-bottom: 1px solid #ccc;">Temp</th>
                    <th style="padding: 8px; border-bottom: 1px solid #ccc;">Pressure</th>
                    <th style="padding: 8px; border-bottom: 1px solid #ccc;">DP Spread</th>
                    <th style="padding: 8px; border-bottom: 1px solid #ccc;">Hazard Prob</th>
                </tr>
            </thead>
            <tbody>
    """
    
    reversedHist.each { entry ->
        def timeStr = new Date((long)entry.time).format("MM/dd HH:mm", location.timeZone)
        def tVal = String.format('%.1f', entry.value)
        
        def findMatch = { hist -> 
            def match = hist?.find { Math.abs(it.time - entry.time) < 120000 }
            return match ? String.format('%.2f', match.value) : "--"
        }
        
        def pVal = findMatch(state.pressureHistory)
        def sVal = findMatch(state.spreadHistory)
        
        def probMatch = state.probHistory?.find { Math.abs(it.time - entry.time) < 120000 }
        def probVal = probMatch ? "${Math.round(probMatch.value)}%" : "--"
        
        tableHTML += """
            <tr style="border-bottom: 1px solid #eee;">
                <td style="padding: 6px 8px; color: #555;">${timeStr}</td>
                <td style="padding: 6px 8px;">${tVal}°</td>
                <td style="padding: 6px 8px;">${pVal}</td>
                <td style="padding: 6px 8px;">${sVal != "--" ? sVal + "°" : "--"}</td>
                <td style="padding: 6px 8px; font-weight: bold; color: ${probMatch && probMatch.value > 50 ? 'red' : 'black'};">${probVal}</td>
            </tr>
        """
    }
    
    tableHTML += """
            </tbody>
        </table>
    </div>
    """
    return tableHTML
}

def renderRadarHTML() {
    def lat = settings.manualLat ?: (location.latitude ?: 39.8283)
    def lon = settings.manualLon ?: (location.longitude ?: -98.5795)
    def radarWind = isMetric() ? "km%2Fh" : "mph"
    def radarTemp = isMetric() ? "%C2%B0C" : "%C2%B0F"

    return """
    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;">Live Regional Radar</h4>
    <iframe width="100%" height="450" src="https://embed.windy.com/embed.html?type=map&location=coordinates&metricRain=in&metricTemp=${radarTemp}&metricWind=${radarWind}&zoom=${settings.radarZoom ?: 8}&overlay=radar&product=radar&level=surface&lat=${lat}&lon=${lon}&detailLat=${lat}&detailLon=${lon}&marker=true&message=true" frameborder="0" style="display:block; border-radius: 4px;"></iframe>
    """
}

def mainPage() {
    return dynamicPage(name: "mainPage", title: "<b>Advanced Severe Weather Detector</b>", install: true, uninstall: true) {
     
        section("<b>Live Threat Assessment Matrix</b>") {
            if (app.id) {
                input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            }
            
            // --- SYSTEM HEALTH & DIAGNOSTIC HEARTBEAT ---
            def lastEvalMs = state.lastHeartbeat ?: now()
            def timeSinceEval = Math.round((now() - lastEvalMs) / 1000)
            def isHealthy = timeSinceEval < 120
            def healthDot = isHealthy ? "<span style='color:#5cb85c;'>●</span>" : "<span style='color:#d9534f;'>●</span>"
            def execTime = state.lastEvalDuration ?: 0
            def emiStatus = state.emiActive ? "<span style='color:#f0ad4e; font-weight:bold;'>[⚡ EMI INTERFERENCE LOCK ACTIVE]</span>" : ""
            
            def healthHtml = """
            <div style='background-color:#1e1e1e; color:#d4d4d4; padding: 10px; border-radius: 6px; font-family: monospace; font-size: 11px; margin-bottom: 15px;'>
                <div style='color:#569cd6; font-weight:bold; margin-bottom: 4px;'>${healthDot} SYSTEM DIAGNOSTICS & TELEMETRY ${emiStatus}</div>
                Last Engine Cycle: <b>${timeSinceEval}s ago</b> | Cycle Compute Time: <b>${execTime}ms</b><br>
                Telemetry Arrays: Temp[${state.tempHistory?.size()?:0}] Press[${state.pressureHistory?.size()?:0}] Wind[${state.windHistory?.size()?:0}] Light[${state.lightningHistory?.size()?:0}] Prob[${state.probHistory?.size()?:0}]
            </div>
            """
            paragraph healthHtml
            
            def isWatchdog = state.watchdogActive ?: false
            def wdHtml = isWatchdog ? "<span style='color:#d9534f; font-weight:bold;'>[🚨 DEFCON WATCHDOG ACTIVE: Overdrive Polling Engaged]</span>" : "<span style='color:#5cb85c;'>[Standard Local Polling]</span>"
            
            def cloudHtml = ""
            if (settings.enableNOAA) {
                if (state.cloudOffline) {
                    cloudHtml = " <span style='color:#d9534f; font-weight:bold;'>[☁️ CLOUD OFFLINE: NOAA Suspended]</span>"
                } else {
                    cloudHtml = " <span style='color:#0275d8; font-weight:bold;'>[☁️ NOAA Sync Active]</span>"
                }
            }
            
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Runs predictive thermodynamic models for seven severe weather profiles. Features DNA event forensics, gravity-wave detection, structural load physics, and cross-references data with NWS API alerts. ${wdHtml}${cloudHtml}</div>"
            
            // --- FORENSIC EVENT SUMMARY TABLE ---
            if (state.lastEventReport) {
                def forensicHtml = """
                <table style='width:100%; border-collapse: collapse; font-size: 12px; font-family: sans-serif; background-color: #fffcfc; border: 1px solid #d9534f; margin-bottom: 15px; box-shadow: 0 2px 5px rgba(0,0,0,0.05);'>
                    <tr style='background-color: #d9534f; color: white;'>
                        <th colspan='4' style='padding: 6px; text-align: left; font-size: 13px;'>📊 Forensic Event Summary: ${state.lastEventReport.date}</th>
                    </tr>
                    <tr style='background-color: #fdf0f0; color: #333; text-align: center;'>
                        <td style='padding: 8px; border-right: 1px solid #f2dede;'><b>Max Wind Gust</b><br><span style='font-size:14px; color:#c9302c;'>${String.format('%.1f', state.lastEventReport.maxWind)} mph</span></td>
                        <td style='padding: 8px; border-right: 1px solid #f2dede;'><b>Min Pressure</b><br><span style='font-size:14px; color:#c9302c;'>${String.format('%.2f', state.lastEventReport.minPress)} inHg</span></td>
                        <td style='padding: 8px; border-right: 1px solid #f2dede;'><b>Max Temp Drop</b><br><span style='font-size:14px; color:#c9302c;'>${String.format('%.1f', state.lastEventReport.tempDrop)}°</span></td>
                        <td style='padding: 8px;'><b>Event Duration</b><br><span style='font-size:14px; color:#c9302c;'>${state.lastEventReport.duration}</span></td>
                    </tr>
                </table>
                """
                paragraph forensicHtml
            }

            if (sensorTemp && sensorHum && sensorPress) {
                def matrix = state.threatMatrix ?: [:]
                def noaaMap = state.noaaAlertsMap ?: [:]
                def hazards = [
                    [id: "Tornado", icon: "🌪️"],
                    [id: "Thunderstorm", icon: "⛈️"],
                    [id: "Flood", icon: "🌊"],
                    [id: "Freeze", icon: "❄️"],
                    [id: "SevereHeat", icon: "🔥"],
                    [id: "Tropical", icon: "🌀"],
                    [id: "FireWeather", icon: "🔥🌲"]
                ]
                
                hazards.each { hazMap ->
                    def haz = hazMap.id
                    def icon = hazMap.icon
                    def data = matrix[haz] ?: [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Gathering sensor data...", mathEx: "Awaiting math cycle...", lastTrig: 0, history: []]
  
                    def noaaStatus = noaaMap[haz] ?: "Clear"
                    def noaaRaw = noaaMap["Raw_${haz}"] ?: "No official alerts for this sector."
                    def noaaColor = noaaStatus == "ALARM" ? "#d9534f" : (noaaStatus == "WARNING" ? "#f0ad4e" : "#5cb85c")
                    def noaaBg = noaaStatus == "Clear" ? "#f4f8f4" : (noaaStatus == "WARNING" ? "#fcf6ec" : "#fdf0f0")
                    
                    def stateColor = data.state == "ALARM" ? "#d9534f" : (data.state == "WARNING" ? "#f0ad4e" : "#5cb85c")
                    def headerBg = data.state == "ALARM" ? "#fdf0f0" : (data.state == "WARNING" ? "#fcf6ec" : "#f4f8f4")
                    
                    if (data.state == "ICE WARNING" || data.state == "ICE ALARM") {
                        stateColor = "#00bcd4"
                        headerBg = "#e0f7fa"
                    }
                    
                    def tColor = data.threat > 75 ? "#d9534f" : (data.threat > 40 ? "#f0ad4e" : "#5bc0de")
                    def pColor = data.prob > 75 ? "#d9534f" : (data.prob > 40 ? "#f0ad4e" : "#5bc0de")
                    def cColor = data.conf > 75 ? "#5cb85c" : (data.conf > 40 ? "#f0ad4e" : "#d9534f")

                    def histHtml = "<div style='margin-top: 10px; border-top: 1px dashed #ccc; padding-top: 8px;'><b style='font-size:12px; color:#222;'>Recent Event History:</b><br>"
                    if (data.history && data.history.size() > 0) {
                        data.history.each { h ->
                            def c = h.type.contains('ALARM') ? '#d9534f' : '#f0ad4e'
                            histHtml += "<div style='font-size:11px; color:#555;'>• <span style='color:${c}; font-weight:bold;'>${h.type}</span> | Initiated: ${h.start} | Duration: <b>${h.dur}</b></div>"
                        }
                    } else {
                        histHtml += "<div style='font-size:11px; color:#888; font-style:italic;'>No recent events recorded.</div>"
                    }
                    histHtml += "</div>"

                    def prettyName = haz == 'SevereHeat' ? 'Severe Heat' : (haz == 'FireWeather' ? 'Fire Weather' : haz)
                    def sectionTitle = "${icon} ${prettyName} DNA &nbsp;|&nbsp; Threat: ${data.threat}% &nbsp;|&nbsp; Conf: ${data.conf}% &nbsp;|&nbsp; State: ${data.state}"
                    def isHidden = (data.state == "Clear" && noaaStatus == "Clear")

                    section("<b>${sectionTitle}</b>", hideable: true, hidden: isHidden) {
                        def dashboardHtml = """
                        <div style="border: 1px solid #e0e0e0; margin-bottom: 5px; border-radius: 8px; background: #ffffff; overflow: hidden; font-family: 'Segoe UI', Tahoma, sans-serif; box-shadow: 0 2px 5px rgba(0,0,0,0.05);">
                            <div style="display: flex; flex-wrap: wrap; padding: 15px; gap: 15px; border-bottom: 1px solid #eee;">
                                <div style="flex: 1; min-width: 200px;">
                                    <div style="display: inline-block; padding: 3px 10px; background: ${headerBg}; color: ${stateColor}; border: 1px solid ${stateColor}; font-weight: bold; border-radius: 12px; font-size: 11px; margin-bottom: 12px; letter-spacing: 0.5px; text-transform: uppercase;">
                                        LOCAL SYSTEM: ${data.state}
                                    </div>
                                    <div style="margin-bottom: 10px;">
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>Threat Intensity</b><b>${data.threat}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.threat}%; height: 100%; background: ${tColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                    <div style="margin-bottom: 10px;">
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>Probability Score</b><b>${data.prob}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.prob}%; height: 100%; background: ${pColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                    <div>
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>System Confidence</b><b>${data.conf}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.conf}%; height: 100%; background: ${cColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                </div>
                                <div style="flex: 1.2; min-width: 250px; font-size: 13px; color: #444; background: #f8f9fa; border-radius: 6px; padding: 12px; border-left: 4px solid ${stateColor}; display: flex; flex-direction: column; justify-content: flex-start;">
                                    <b style="color:#222; margin-bottom: 6px; font-size: 14px;">Diagnostic Report:</b>
                                    <span style="line-height: 1.4;">${data.howWhy}</span>
                                    ${histHtml}
                                </div>
                                <div style="flex: 1.2; min-width: 200px; font-size: 11px; color: #333; background: #eef2f5; border-radius: 6px; padding: 12px; border-left: 4px solid #8e9eab; display: flex; flex-direction: column; justify-content: flex-start; font-family: 'Courier New', Courier, monospace;">
                                    <b style="color:#222; margin-bottom: 6px; font-size: 12px; font-family: 'Segoe UI', sans-serif;">Algorithmic Engine:</b>
                                    <span style="line-height: 1.5;">${data.mathEx}</span>
                                </div>
                            </div>
                        """
                        if (settings.enableNOAA) {
                            dashboardHtml += """
                            <div style="padding: 12px 15px; background: ${noaaBg}; font-size: 13px; color: #333; display: flex; align-items: center; gap: 15px;">
                                <div style="padding: 3px 10px; background: #fff; color: ${noaaColor}; border: 1px solid ${noaaColor}; font-weight: bold; border-radius: 12px; font-size: 10px; letter-spacing: 0.5px; text-transform: uppercase; white-space: nowrap;">
                                    NOAA: ${noaaStatus}
                                </div>
                                <div style="flex: 1;">
                                    <b>Official NWS Report:</b> ${noaaRaw}
                                </div>
                            </div>
                            """
                        }
                        dashboardHtml += "</div>"
                        paragraph dashboardHtml
                    }
                }
                
                // --- CSS Flexbox Layout for Chart, Table & Radar ---
                def visualWidgets = "<div style='display: flex; flex-wrap: wrap; gap: 15px; margin-top: 15px; align-items: stretch;'>"
                
                def dispMode = settings.historyDisplayMode ?: "Chart (Chart.js)"
                if (dispMode == "Chart (Chart.js)") {
                    visualWidgets += "<div style='flex: 1 1 48%; min-width: 350px; background: #fff; padding: 15px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;'>"
                    visualWidgets += renderChartHTML()
                    visualWidgets += "</div>"
                } else if (dispMode == "Data Table") {
                    visualWidgets += "<div style='flex: 1 1 48%; min-width: 350px; background: #fff; padding: 15px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;'>"
                    visualWidgets += renderTableHTML()
                    visualWidgets += "</div>"
                }
                
                if (settings.enableRadar != false && location.latitude && location.longitude) {
                    visualWidgets += "<div style='flex: 1 1 48%; min-width: 350px; background: #fff; padding: 15px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;'>"
                    visualWidgets += renderRadarHTML()
                    visualWidgets += "</div>"
                }
                visualWidgets += "</div>"
                paragraph visualWidgets
                
                section("<b>Core Physics Data Stream</b>", hideable: true, hidden: true) {
                    def pTrend = state.pressureTrendStr ?: "Stable"
                    def tTrend = state.tempTrendStr ?: "Stable"
                    def wTrend = state.windTrendStr ?: "Stable"
                    def sTrend = state.spreadTrendStr ?: "Stable"
                    
                    def rawP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], 0.0)
                    def p = rawP + (settings.pressOffset ?: 0.0)
                    
                    def windDir = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], "N/A")
                    def strikes = state.lightningHistory?.size() ?: 0
                    def gravWave = state.gravityWaveActive ? "<span style='color:red; font-weight:bold;'>DETECTED (Turbulence)</span>" : "Calm"
                    def rho = state.currentAirDensity ?: 1.225
                    def wbgt = state.currentWBGT ?: getFloat(sensorTemp, ["temperature"], 0.0)
                    
                    // Wind Load UI
                    def windLoadVal = state.currentWindLoad ?: 0.0
                    def windLoadUnit = isMetric() ? "Pascals (Pa)" : "lb/ft² (PSF)"
                    def loadColor = windLoadVal > 20.0 ? "red" : (windLoadVal > 10.0 ? "orange" : "#555")
                    
                    def leakWetStr = "DRY"
                    if (sensorLeak || sensorLeak2 || sensorLeak3) {
                        if (state.dewRejectionActive) leakWetStr = "<span style='color:orange; font-weight:bold;'>DEW/IGNORED</span>"
                        else if (state.stuckLeakActive) leakWetStr = "<span style='color:orange; font-weight:bold;'>STUCK/IGNORED</span>"
                        else if (state.leakWetStatus) leakWetStr = "<span style='color:blue; font-weight:bold;'>WET</span>"
                    }
                    
                    def vectorStr = state.lightningVectorStr ?: "N/A"
                    
                    def physicsDisplay = "<div style='padding: 12px; background: #f0f2f5; border-radius: 6px; font-size: 13px; border: 1px solid #dcdfe3; color: #555; line-height: 1.6;'>"
                    physicsDisplay += "<b>Calibrated MSLP:</b> ${String.format('%.2f', p)} inHg (${pTrend})<br>"
                    physicsDisplay += "<b>Temp Velocity:</b> ${tTrend} &nbsp;|&nbsp; <b>Squeeze Vel:</b> ${sTrend}<br>"
                    physicsDisplay += "<b>Wind:</b> ${wTrend} @ ${windDir}° &nbsp;|&nbsp; <b>Lightning (30m):</b> ${strikes} strikes (${vectorStr})<br>"
                    physicsDisplay += "<b>Structural Wind Load:</b> <span style='color:${loadColor}; font-weight:bold;'>${String.format('%.2f', windLoadVal)} ${windLoadUnit}</span><br>"
                    physicsDisplay += "<b>Air Density (Kinetic Mass):</b> ${String.format('%.3f', rho)} kg/m³<br>"
                    physicsDisplay += "<b>Wet-Bulb Globe Temp (WBGT Proxy):</b> ${String.format('%.1f', wbgt)}°F<br>"
                    physicsDisplay += "<b>Atmospheric Gravity Waves:</b> ${gravWave}<br>"
                    physicsDisplay += "<b>Instant Leak Sensors:</b> ${leakWetStr}</div>"
                    paragraph physicsDisplay
                }
                
            } else {
                paragraph "<i>Primary sensors missing. Click Configuration below to assign devices.</i>"
            }
        }

        section("<b>System Configuration</b>") {
            href(name: "configPageLink", page: "configPage", title: "▶ Base Hardware & BMS Engine Tuning", description: "Set up local sensors, NOAA Cloud Integration, calibration offsets, DEFCON Watchdog, and advanced physics modules.")
            href(name: "dnaConfigPageLink", page: "dnaConfigPage", title: "▶ Threat DNA Granular Mapping", description: "Configure Probability thresholds, specific Mode restrictions, Global All-Clear, and custom Audio/Siren routing.")
        }
        
        section("<b>Child Device Integration</b>") {
            paragraph "<i>Create a single virtual device that exposes the entire Threat Matrix to your dashboards.</i>"
            if (app.id) {
                input "createDeviceBtn", "button", title: "➕ Create Severe Weather Information Device"
            }
        }
        
        if (app.id) {
            section("<b>Global Actions & Debugging</b>", hideable: true, hidden: true) {
                input "forceEvalBtn", "button", title: "⚙️ Force Matrix Evaluation"
                input "clearStateBtn", "button", title: "⚠ Reset Internal Matrix & Hardware"
                input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
                input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false
            }
        }
    }
}

def configPage() {
    return dynamicPage(name: "configPage", title: "<b>Base Hardware & BMS Engine Tuning</b>", install: false, uninstall: false) {
        
        section("<b>Dashboard UI Preferences</b>", hideable: true, hidden: true) {
            paragraph "<i>Customize how data is visualized on your main application page.</i>"
            input "historyDisplayMode", "enum", title: "24-Hour History Display", options: ["Chart (Chart.js)", "Data Table", "Hidden"], defaultValue: "Chart (Chart.js)", required: true, submitOnChange: true
            input "enableRadar", "bool", title: "Enable Live NOAA Radar Map", defaultValue: true, submitOnChange: true
            if (enableRadar) {
                input "radarZoom", "number", title: "Map Zoom Level (5 - 15)", defaultValue: 8, required: true
            }
        }
        
        section("<b>Primary Thermodynamic Sensors (Required)</b>") {
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor", required: true
            input "sensorPress", "capability.sensor", title: "Barometric Pressure Sensor", required: true
        }

        section("<b>Kinetic & Precipitation Sensors (Required for Full Matrix)</b>") {
            input "sensorWind", "capability.sensor", title: "Wind Speed / Gust Sensor", required: false
            input "sensorWindDir", "capability.sensor", title: "Wind Direction Sensor", required: false
            input "sensorLightning", "capability.sensor", title: "Lightning Detector", required: false
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor (in/hr or mm/hr)", required: false
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: false
            input "sensorRainWeekly", "capability.sensor", title: "Weekly Rain Accumulation Sensor (Used for Fire Weather Fuel Proxy)", required: false
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor (Used for WBGT)", required: false
        }
        
        section("<b>Instant 'First Drop' Sensors (Micro-Precipitation)</b>") {
            paragraph "<i>Map up to 3 standard Z-Wave/Zigbee leak sensors placed outside to bypass tipping-bucket delays. Provides instant reactive triggers for Thunderstorm, Flood, and Tropical DNAs.</i>"
            input "sensorLeak", "capability.waterSensor", title: "Instant Rain Sensor 1", required: false
            input "sensorLeak2", "capability.waterSensor", title: "Instant Rain Sensor 2", required: false
            input "sensorLeak3", "capability.waterSensor", title: "Instant Rain Sensor 3", required: false
            input "leakSensorRequiredCount", "enum", title: "Number of Instant Sensors required to trigger", options: ["1", "2", "3"], defaultValue: "1", required: true
            input "enableDewRejection", "bool", title: "Dew & Frost Rejection (First Drop Filter)", defaultValue: true, description: "Ignores the instant leak sensor on cold/calm mornings (Dew Point Spread < 3°, Calm Wind, Low Lux) to prevent false alerts."
            input "stuckLeakTimeout", "number", title: "Stuck Sensor Timeout (Minutes)", required: true, defaultValue: 60
        }
        
        section("<b>☁️ External Cloud Integration</b>") {
            input "enableNOAA", "bool", title: "Enable NOAA / NWS Forecasts & Alerts", defaultValue: true, submitOnChange: true
            if (enableNOAA) {
                input "noaaTriggersHardware", "bool", title: "Allow NOAA Alerts to trigger your physical Warning & Alarm switches", defaultValue: false
            }
        }
        
        section("<b>Advanced Physics & Calibration Modules</b>") {
            paragraph "<i>These modules separate a hobbyist weather station from a professional meteorological instrument.</i>"
            input "pressOffset", "decimal", title: "Barometric MSLP Offset (inHg)", defaultValue: 0.0, description: "Crucial for Tropical DNA accuracy. If your elevation pressure reads 29.00 but the airport reports 29.90, enter 0.90 here."
            
            // [SCIENCE: WBGT]
            input "enableWBGT", "bool", title: "Enable Wet-Bulb Globe Temperature (WBGT)", defaultValue: true, description: "Calculates a proxy utilizing Temp, Hum, Wind, and Solar Radiation to measure true heat stress in direct sunlight."
            
            // [SCIENCE: KINETIC AIR DENSITY & STRUCTURAL LOAD]
            input "enableKineticWind", "bool", title: "Enable Kinetic Air Density & Structural Wind Load", defaultValue: true, description: "Tracks the physical mass (kg/m³) of the air and converts raw wind speed into dynamic Structural Load pressure (lb/ft² or Pascals). Applies directly to Tornado/Storm threat arrays."
            
            // [SCIENCE: STORM VECTORING]
            input "enableStormVectoring", "bool", title: "Enable Storm Intercept Vectoring (Approach Velocity)", defaultValue: true, description: "Tracks the distance of lightning strikes over time to calculate approach velocity (MPH) and Estimated Time of Arrival (ETA). Filters out random residential sensor 'ghost' strikes via historical averaging."
            
            // [SCIENCE: GRAVITY WAVES]
            input "enableGravityWave", "bool", title: "Enable Atmospheric Gravity Wave Detection", defaultValue: true, description: "Analyzes micro-oscillations in barometric pressure to detect massive supercell updrafts before they arrive."
            
            // [SCIENCE: DRY MICROBURST]
            input "enableDryMicroburst", "bool", title: "Enable Dry Microburst / Virga Detection", defaultValue: true, description: "Looks for massive Dew Point spreads combined with sudden evaporative cooling (Temp crashes) and Wind Spikes to detect straight-line winds."
            
            // [SCIENCE: EMI/EMP GUARD]
            input "enableEMIGuard", "bool", title: "Enable EMI/EMP Sensor Freeze Guard", defaultValue: true, description: "If a lightning strike occurs within 3 miles and your sensors immediately go stale, the system flags it as Electromagnetic Interference rather than hardware failure, protecting the 'Dying Breath' failover."
            
            input "enableEyeOfStorm", "bool", title: "Enable 'Eye of the Storm' Barometric Lock", defaultValue: true, description: "Refuses to clear an active ALARM if barometric pressure is dangerously low (<29.60) and stable."
            input "enableDyingBreath", "bool", title: "Enable 'Dying Breath' Sensor Failover", defaultValue: true, description: "If local sensors go offline during an ALARM, the system assumes they were destroyed, latches the alarm ON, and fails over to cloud telemetry."
            
            input "enableHardwareFilter", "bool", title: "Enable Hardware Anomaly Rejection", defaultValue: true
            input "enableThermalSmoothing", "bool", title: "Thermal Smoothing (Sun-Spike Protection)", defaultValue: true
            input "staleDataTimeout", "number", title: "Stale Data Timeout (Minutes)", defaultValue: 30
        }
        
        section("<b>DEFCON Watchdog Polling</b>") {
            input "enablePolling", "bool", title: "Enable Standard Active Device Polling", defaultValue: true, submitOnChange: true
            if (enablePolling) {
                input "pollInterval", "number", title: "Standard Polling Interval (Minutes)", required: true, defaultValue: 5
            }
            input "enableDefcon", "bool", title: "Enable DEFCON Watchdog Overdrive", defaultValue: true, submitOnChange: true
            if (enableDefcon) {
                input "defconThresh", "number", title: "DEFCON Activation Threshold (Probability %)", required: true, defaultValue: 25
                input "defconMins", "enum", title: "DEFCON Overdrive Polling Rate (Minutes)", required: true, defaultValue: "1", options: ["1", "2", "3", "4", "5"]
            }
        }
        
        section("<b>Output Hardware Master Devices</b>") {
            input "audioDevices", "capability.speechSynthesis", title: "TTS Audio Devices (e.g., Sonos/Echo)", multiple: true, required: false
            input "soundDevices", "capability.audioNotification", title: "Sound File/MP3 Players", multiple: true, required: false
            input "sirenDevices", "capability.alarm", title: "Sirens & Strobes (e.g., Zooz Siren)", multiple: true, required: false
            input "notifyDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false
            input "audioVolume", "number", title: "Master Announcement Volume Level (%)", defaultValue: 65, range: "1..100"
            
            if (audioDevices || soundDevices) {
                href(name: "speakerMappingPageLink", page: "speakerMappingPage", title: "▶ Smart Room Presence Audio Routing", description: "Map your speakers to motion sensors to avoid blasting audio in empty rooms.")
            }
        }
    }
}

def speakerMappingPage() {
    return dynamicPage(name: "speakerMappingPage", title: "<b>Smart Room Presence Audio Routing</b>", install: false, uninstall: false) {
        section() { paragraph "<i>Assign one or multiple motion sensors to your speakers.</i>" }
        def allSpeakers = []
        if (settings.audioDevices) allSpeakers += settings.audioDevices
        if (settings.soundDevices) allSpeakers += settings.soundDevices
        allSpeakers = allSpeakers.unique { it.id }
        
        if (allSpeakers) {
            allSpeakers.each { speaker ->
                section("<b>Routing for: ${speaker.displayName}</b>") {
                    input "isAlwaysOn_${speaker.id}", "bool", title: "🎙️ Make this a Master Always-Announce Speaker", defaultValue: false, submitOnChange: true
                    if (!settings["isAlwaysOn_${speaker.id}"]) {
                        input "motionMap_${speaker.id}", "capability.motionSensor", title: "🚶 Required Motion Sensors (Active within 5 mins)", required: false, multiple: true
                        input "nightSwitch_${speaker.id}", "capability.switch", title: "🌙 Room 'Good Night' Override Switch", required: false
                    }
                }
            }
        }
    }
}

def dnaConfigPage() {
    return dynamicPage(name: "dnaConfigPage", title: "<b>Threat DNA Granular Mapping</b>", install: false, uninstall: false) {
        
        section("<b>✅ Global All-Clear & Stand-Down Sequencer</b>") {
            paragraph "<i>This sequencer waits until EVERY SINGLE hazard in the matrix reads 'Clear', and verifies that pressure has stabilized and winds have died down before officially broadcasting the all-clear.</i>"
            input "globalAllClearNotify", "bool", title: "Send Push Notification for Global All-Clear", defaultValue: true
            input "globalAllClearTTS", "bool", title: "Broadcast TTS for Global All-Clear", defaultValue: true, submitOnChange: true
            if (settings.globalAllClearTTS) input "ttsGlobalAllClear", "text", title: "All-Clear TTS String", required: false, defaultValue: "The severe weather event has concluded. All clear."
            input "globalAllClearSound", "bool", title: "Play Sound File / Zooz Chime for All-Clear", defaultValue: false, submitOnChange: true
            if (settings.globalAllClearSound) input "urlGlobalAllClear", "text", title: "Sound File URL or Chime #", required: true, description: "Enter a URL or valid Chime number (e.g., 5)"
        }
        
        def dnas = [
            [id: "Tornado", icon: "🌪️", wTTS: "Warning. Elevated probability of tornado conditions.", aTTS: "Critical Alert. Tornado conditions detected locally."],
            [id: "Thunderstorm", icon: "⛈️", wTTS: "Warning. Elevated probability of severe thunderstorms.", aTTS: "Critical Alert. Severe thunderstorm conditions actively detected."],
            [id: "Flood", icon: "🌊", wTTS: "Warning. Elevated probability of flash flood conditions.", aTTS: "Critical Alert. Flash flood conditions detected locally."],
            [id: "Freeze", icon: "❄️", wTTS: "Warning. Rapid trajectory towards freeze detected.", aTTS: "Alert. Freeze conditions actively occurring."],
            [id: "SevereHeat", icon: "🔥", wTTS: "Warning. High heat index detected. Caution advised.", aTTS: "Critical Alert. Severe heat index conditions detected locally."],
            [id: "Tropical", icon: "🌀", wTTS: "Warning. Tropical cyclone signatures detected.", aTTS: "Critical Alert. Deep tropical cyclone conditions actively impacting location."],
            [id: "FireWeather", icon: "🔥🌲", wTTS: "Warning. Elevated fire weather conditions.", aTTS: "Critical Alert. Extreme Red Flag fire weather actively detected. No outdoor sparks."]
        ]
        
        dnas.each { dna ->
            def id = dna.id
            def lName = id.toLowerCase()
            def prettyName = id == 'SevereHeat' ? 'Severe Heat' : (id == 'FireWeather' ? 'Fire Weather' : id)
            section("<b>${dna.icon} ${prettyName} DNA Configuration</b>", hideable: true, hidden: true) {
                input "enable${id}", "bool", title: "Enable ${id} Engine", defaultValue: true, submitOnChange: true
                if (settings["enable${id}"] != false) {
                    input "${lName}WarnThresh", "number", title: "WARNING Probability Threshold (%)", defaultValue: 50, required: true
                    input "${lName}AlarmThresh", "number", title: "ALARM Probability Threshold (%)", defaultValue: 80, required: true
                    input "switch${id}Warn", "capability.switch", title: "Map WARNING Virtual Switch", required: false
                    input "switch${id}Alarm", "capability.switch", title: "Map ALARM Virtual Switch", required: false
                    
                    if (id == "Freeze") {
                        input "switchIceAlarm", "capability.switch", title: "Map ICE STORM / FREEZING RAIN Virtual Switch", required: false, description: "If the Freeze DNA detects active precipitation while the wet-bulb is below freezing, it routes here instead of the standard Freeze switch."
                    }
                    
                    paragraph "<b>▶ Warning Output Routing</b>"
                    input "${lName}WarnNotify", "bool", title: "Send Push Notification", defaultValue: true
                    input "${lName}WarnTTS", "bool", title: "Broadcast TTS", defaultValue: true, submitOnChange: true
                    if (settings["${lName}WarnTTS"]) input "tts${id}Warn", "text", title: "TTS String", required: false, defaultValue: dna.wTTS
                    
                    paragraph "<b>▶ Alarm Output Routing</b>"
                    input "${lName}AlarmNotify", "bool", title: "Send Push Notification", defaultValue: true
                    input "${lName}AlarmTTS", "bool", title: "Broadcast TTS", defaultValue: true, submitOnChange: true
                    if (settings["${lName}AlarmTTS"]) input "tts${id}Alarm", "text", title: "TTS String", required: false, defaultValue: dna.aTTS
                    input "${lName}AlarmSiren", "bool", title: "Trigger Siren Device", defaultValue: (id == "Tornado" || id == "Thunderstorm")
                    input "${lName}AlarmSound", "bool", title: "Play Sound File", defaultValue: false, submitOnChange: true
                    if (settings["${lName}AlarmSound"]) input "url${id}Alarm", "text", title: "Sound File URL or Chime #", required: true
                }
            }
        }
        section("<b>System-Wide Debounce</b>") {
            input "debounceMins", "number", title: "Safety Hardware Hold Time (Minutes)", required: true, defaultValue: 15
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE (ULTIMATE EDITION)
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    def m = [:]
    ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical", "FireWeather"].each { haz ->
        m[haz] = [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0, history: [], currentEvent: null]
    }
    state.threatMatrix = m
    
    state.watchdogActive = false
    state.cloudOffline = false
    state.survivalModeActive = false
    state.globalThreatActive = false
    state.globalAllClearPending = false
    state.emiActive = false
    state.noaaFailCount = 0
    state.noaaAlertsMap = [:]
    if (!state.noaaGlobalAlerts) state.noaaGlobalAlerts = "Initializing broadcast listener..."
    
    if (!state.lastHeartbeat) state.lastHeartbeat = now()
    if (!state.smoothedTemp) state.smoothedTemp = null
    
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.spreadHistory) state.spreadHistory = []
    if (!state.windHistory) state.windHistory = []
    if (!state.windDirHistory) state.windDirHistory = []
    if (!state.lightningHistory) state.lightningHistory = []
    if (!state.probHistory) state.probHistory = []
    if (!state.sevenDayRain) state.sevenDayRain = []
    
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    if (!state.eventActive) state.eventActive = false
    if (!state.activeEventStats) state.activeEventStats = [:]
    
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "tempHandler")
    subscribeMulti(sensorHum, ["humidity"], "stdHandler")
    subscribeMulti(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "windHandler")
    subscribeMulti(sensorWindDir, ["windDirection", "winddir", "windDir"], "windDirHandler")
    subscribeMulti(sensorLightning, ["lightningDistance", "distance"], "lightningHandler")
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], "stdHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "stdHandler")
    subscribeMulti(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "luxHandler")
    
    [sensorLeak, sensorLeak2, sensorLeak3].each { dev ->
        if (dev) subscribe(dev, "water", "stdHandler")
    }
    
    def motionSensorsToSub = []
    settings.each { key, val ->
        if (key.startsWith("motionMap_") && val) {
            if (val instanceof List) motionSensorsToSub.addAll(val)
            else motionSensorsToSub << val
        }
    }
    if (motionSensorsToSub) {
        motionSensorsToSub = motionSensorsToSub.unique { it.id }
        subscribe(motionSensorsToSub, "motion.active", "motionActiveHandler")
    }
    
    unschedule()
    if (enablePolling && pollInterval) {
        def safeInterval = Math.max(1, Math.min(59, pollInterval.toInteger()))
        schedule("0 */${safeInterval} * ? * *", "standardPoll")
    }
    
    if (settings.enableNOAA) {
        runEvery5Minutes("pollNOAA")
        pollNOAA()
    }
    
    runEvery1Minute("evaluateMatrix") 
    logAction("BMS Advanced Severe Weather Matrix Initialized.")
    evaluateMatrix()
}

// === BUTTON HANDLER ===
void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") return
    if (btn == "createDeviceBtn") { createChildDevice(); return }
    if (btn == "forceEvalBtn") {
        logAction("MANUAL OVERRIDE: Forcing matrix evaluation.")
        evaluateMatrix()
    }
    if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging matrix history, records, and internal states.")
        
        // Nuke the arrays from orbit
        state.remove("pressureHistory")
        state.remove("tempHistory")
        state.remove("spreadHistory")
        state.remove("windHistory")
        state.remove("windDirHistory")
        state.remove("lightningHistory")
        state.remove("probHistory")
        state.remove("actionHistory")
        
        state.remove("lastEventReport")
        state.remove("activeEventStats")
        state.eventActive = false
        state.globalThreatActive = false
        state.globalAllClearPending = false
        state.emiActive = false
        state.watchdogActive = false
        state.survivalModeActive = false
        
        // Re-initialize the clean arrays
        state.pressureHistory = []
        state.tempHistory = []
        state.spreadHistory = []
        state.windHistory = []
        state.windDirHistory = []
        state.lightningHistory = []
        state.probHistory = []
        
        def m = [:]
        ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical", "FireWeather"].each { haz ->
            m[haz] = [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "System Reset...", mathEx: "Awaiting next cycle...", lastTrig: now(), history: [], currentEvent: null]
        }
        state.threatMatrix = m
        
        evaluateMatrix()
    }
}

// === UNIT DETECTION HELPER ===
def isMetric() {
    return location?.temperatureScale == "C"
}

def subscribeMulti(device, attrs, handler) {
    if (!device) return
    attrs.each { attr -> subscribe(device, attr, handler) }
}

def getFloat(device, attrs, fallbackStr = null) {
    if (!device) return fallbackStr
    for (attr in attrs) {
        def val = device.currentValue(attr)
        if (val != null) {
            try { return val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch (e) {}
        }
    }
    return fallbackStr
}

def standardPoll() { if (!state.watchdogActive) pollSensors() }
def watchdogPoll() {
    if (state.watchdogActive) {
        pollSensors()
        def mins = (settings.defconMins ?: 1).toInteger()
        runIn(mins * 60, "watchdogPoll")
    }
}

def pollSensors() {
    [sensorTemp, sensorHum, sensorPress, sensorRain, sensorWind, sensorWindDir, sensorLightning, sensorLux].each { dev ->
        if (dev && dev.hasCommand("refresh")) { try { dev.refresh() } catch (e) {} }
    }
}

// === ASYNC NOAA CLOUD POLLING ===
def pollNOAA() {
    if (settings.enableNOAA != true) return
    if (state.cloudOffline && state.cloudRetryTime && now() < state.cloudRetryTime) return 

    def lat = location.latitude
    def lon = location.longitude
    if (!lat || !lon) return

    def params = [
        uri: "https://api.weather.gov/alerts/active?point=${lat},${lon}",
        timeout: 10,
        headers: ["User-Agent": "Hubitat-AdvancedSevereWeatherApp/3.0", "Accept": "application/geo+json"]
    ]
    try { asynchttpGet("noaaResponseHandler", params) } catch (e) { log.error "Async HTTP Get failed: ${e}" }
}

def noaaResponseHandler(response, data) {
    if (response.hasError()) {
        state.noaaFailCount = (state.noaaFailCount ?: 0) + 1
        if (state.noaaFailCount >= 3) {
            state.cloudOffline = true
            state.cloudRetryTime = now() + 1800000 
        }
        return
    }
    
    state.cloudOffline = false
    state.noaaFailCount = 0
    def json = null
    try { json = response.getJson() } catch (e) { return }
    
    def alerts = json?.features ?: []
    def parsedAlerts = ["Tornado": "Clear", "Thunderstorm": "Clear", "Flood": "Clear", "Freeze": "Clear", "SevereHeat": "Clear", "Tropical": "Clear", "FireWeather": "Clear"]
    def globalAlertsHtml = ""
    
    alerts.each { alert ->
        def event = alert.properties?.event ?: ""
        def headline = alert.properties?.headline ?: event
        globalAlertsHtml += "<b>${event}</b>: ${headline}<br><br>"
        
        if (event.contains("Tornado Warning")) parsedAlerts["Tornado"] = "ALARM"
        else if (event.contains("Tornado Watch") && parsedAlerts["Tornado"] != "ALARM") parsedAlerts["Tornado"] = "WARNING"
        if (event.contains("Severe Thunderstorm Warning")) parsedAlerts["Thunderstorm"] = "ALARM"
        else if (event.contains("Severe Thunderstorm Watch") && parsedAlerts["Thunderstorm"] != "ALARM") parsedAlerts["Thunderstorm"] = "WARNING"
        if (event.contains("Flash Flood") || event.contains("Flood Warning")) parsedAlerts["Flood"] = "ALARM"
        else if (event.contains("Flood Watch") || event.contains("Flood Advisory")) parsedAlerts["Flood"] = "WARNING"
        if (event.contains("Freeze Warning") || event.contains("Ice Storm Warning") || event.contains("Winter Storm Warning")) parsedAlerts["Freeze"] = "ALARM"
        else if (event.contains("Freeze Watch") || event.contains("Frost Advisory") || event.contains("Winter Weather Advisory")) parsedAlerts["Freeze"] = "WARNING"
        if (event.contains("Excessive Heat Warning")) parsedAlerts["SevereHeat"] = "ALARM"
        else if (event.contains("Heat Advisory")) parsedAlerts["SevereHeat"] = "WARNING"
        if (event.contains("Hurricane Warning") || event.contains("Tropical Storm Warning")) parsedAlerts["Tropical"] = "ALARM"
        else if (event.contains("Hurricane Watch") || event.contains("Tropical Storm Watch")) parsedAlerts["Tropical"] = "WARNING"
        if (event.contains("Red Flag Warning") || event.contains("Fire Weather Warning")) parsedAlerts["FireWeather"] = "ALARM"
        else if (event.contains("Fire Weather Watch")) parsedAlerts["FireWeather"] = "WARNING"
    }
    
    if (globalAlertsHtml == "") globalAlertsHtml = "<i>No active national weather alerts for your area at this time.</i>"
    state.noaaGlobalAlerts = globalAlertsHtml
    state.noaaAlertsMap = parsedAlerts
    evaluateMatrix()
}

// === HISTORY, PRESENCE, & HEARTBEAT ===
def markActive() { state.lastHeartbeat = now() }
def motionActiveHandler(evt) { state["motionLastActive_${evt.deviceId}"] = now() }

def updateHistory(historyName, val, maxAgeMs) {
    markActive()
    if (val == null) return
    def cleanVal
    try { cleanVal = val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) { return }
    
    if (settings.enableHardwareFilter != false) {
        def hist = state."${historyName}" ?: []
        if (hist.size() > 0) {
            def lastVal = hist.last().value
            def timeDiffSecs = (now() - hist.last().time) / 1000.0
            def delta = Math.abs(cleanVal - lastVal)
            if (timeDiffSecs < 120) {
                if (historyName == "tempHistory" && delta > 8.0) return 
                if (historyName == "pressureHistory" && delta > 0.3) return
            }
        }
    }
    
    def hist = state."${historyName}" ?: []
    hist.add([time: now(), value: cleanVal])
    def cutoff = now() - maxAgeMs
    hist = hist.findAll { it.time >= cutoff }
    
    // Memory Bloat Protection (Limit to 288 points = 24 hours at 5m intervals)
    def maxPoints = 288
    if (hist.size() > maxPoints) {
        hist = hist.drop(hist.size() - maxPoints)
    }
    
    state."${historyName}" = hist
}

def stdHandler(evt) { markActive(); runIn(1, "evaluateMatrix") }
def tempHandler(evt) { updateHistory("tempHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def pressureHandler(evt) { 
    def raw = 0.0
    try { raw = evt.value.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) {}
    def cal = raw + (settings.pressOffset ?: 0.0)
    updateHistory("pressureHistory", cal, 10800000)
    runIn(1, "evaluateMatrix") 
} 
def windHandler(evt) { updateHistory("windHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def windDirHandler(evt) { updateHistory("windDirHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def lightningHandler(evt) { updateHistory("lightningHistory", evt.value, 1800000); runIn(1, "evaluateMatrix") }
def luxHandler(evt) { runIn(1, "evaluateMatrix") }

// ==============================================================================
// [SCIENCE: ADVANCED METEOROLOGICAL MATHEMATICS]
// ==============================================================================

def getTrendData(hist, minTimeHr) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    def oldest = hist.first()
    def newest = hist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    if (timeSpanHr < minTimeHr) return [rate: 0.0, diff: diff, str: "Stable"]
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def getAccelerationData(hist) {
    if (!hist || hist.size() < 4) return 0.0
    def cutoff = now() - 900000 
    def recent = hist.findAll { it.time >= cutoff }
    def older = hist.findAll { it.time < cutoff && it.time >= now() - 3600000 } 
    if (recent.size() < 2 || older.size() < 2) return 0.0
    return getTrendData(recent, 0.1).rate - getTrendData(older, 0.1).rate
}

def getStormVectorData(hist) {
    if (!hist || hist.size() < 4) return [status: "Gathering", speed: 0.0, eta: -1]
    
    def sorted = hist.sort { it.time }
    def mid = (sorted.size() / 2).toInteger()
    def older = sorted[0..(mid-1)]
    def newer = sorted[mid..(sorted.size()-1)]
    
    def oldDist = older.sum { it.value } / older.size()
    def newDist = newer.sum { it.value } / newer.size()
    def oldTime = older.sum { it.time } / older.size()
    def newTime = newer.sum { it.time } / newer.size()
    
    def timeDiffHr = (newTime - oldTime) / 3600000.0
    if (timeDiffHr <= 0) return [status: "Stalled", speed: 0.0, eta: -1]
    
    def distDiff = oldDist - newDist // Positive means approaching
    def speedMph = distDiff / timeDiffHr
    
    if (speedMph > 2.0) {
        def etaHr = newDist / speedMph
        return [status: "Approaching", speed: speedMph, eta: etaHr * 60.0]
    } else if (speedMph < -2.0) {
        return [status: "Departing", speed: Math.abs(speedMph), eta: -1]
    }
    return [status: "Stalled/Lateral", speed: 0.0, eta: -1]
}

def detectGravityWaves(hist) {
    if (!hist || hist.size() < 6) return false
    def cutoff = now() - 1800000 // 30 mins
    def recent = hist.findAll { it.time >= cutoff }
    if (recent.size() < 6) return false
    
    int directionChanges = 0
    def lastDeltaSign = 0
    for (int i = 1; i < recent.size(); i++) {
        def delta = recent[i].value - recent[i-1].value
        if (Math.abs(delta) < 0.005) continue 
        def currentSign = delta > 0 ? 1 : -1
        if (lastDeltaSign != 0 && currentSign != lastDeltaSign) {
            directionChanges++
        }
        lastDeltaSign = currentSign
    }
    return (directionChanges >= 3)
}

def calculateAirDensity(tF, rh, pInHg) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def tK = tC + 273.15
    def pMb = pInHg * 33.8639
    def pPa = pMb * 100.0 
    
    def ePa = (rh / 100.0) * 6.1078 * Math.pow(10, (7.5 * tC) / (tC + 237.3)) * 100.0
    def rDry = 287.058
    def rVapor = 461.495
    
    def pDry = pPa - ePa
    def density = (pDry / (rDry * tK)) + (ePa / (rVapor * tK))
    return density 
}

def calculateWindLoad(rho, windMph) {
    def v_ms = windMph * 0.44704
    def q_Pa = 0.5 * rho * (v_ms * v_ms)
    return isMetric() ? q_Pa : (q_Pa * 0.0208854) // Returns Pa or PSF
}

def calculateWBGT(tF, rh, windMph, lux) {
    def wbF = calculateWetBulb(tF, rh)
    def solarHeat = (lux / 10000.0) * 3.5 
    def windCooling = (windMph * 0.3)
    def globeF = tF + solarHeat - windCooling
    if (globeF < tF) globeF = tF 
    
    def wbgt = (0.7 * wbF) + (0.2 * globeF) + (0.1 * tF)
    return wbgt
}

def calculateVPD(tVal, rh) {
    def tC = (tVal - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def calculateDewPoint(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    return (dpC * (9.0 / 5.0)) + 32.0
}

def calculateWetBulb(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def twC = tC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331) + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035
    return (twC * (9.0 / 5.0)) + 32.0
}

def getAngularDiff(angle1, angle2) {
    def diff = Math.abs(angle1 - angle2) % 360
    return diff > 180 ? 360 - diff : diff
}

def calculateHeatIndex(tF, rh) {
    if (tF < 80.0) return tF
    def hi = -42.379 + (2.04901523 * tF) + (10.14333127 * rh) - (0.22475541 * tF * rh) - (0.00683783 * tF * tF) - (0.05481717 * rh * rh) + (0.00122874 * tF * tF * rh) + (0.00085282 * tF * rh * rh) - (0.00000199 * tF * tF * rh * rh)
    return hi
}

// === THE MATRIX EVALUATOR ===
def evaluateMatrix() {
    def evalStart = now() 
    
    // MIDNIGHT ROLLOVER - Daily Rain pushed to 7-Day History
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.currentDateStr) state.currentDateStr = todayStr
    
    if (state.currentDateStr != todayStr) {
        def yesterdayTotal = state.currentDayRain ?: 0.0
        def hist = state.sevenDayRain ?: []
        hist.add(0, [date: state.currentDateStr, amount: yesterdayTotal])
        if (hist.size() > 7) hist = hist[0..6]
        state.sevenDayRain = hist

        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
    
    def currentDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    if (currentDaily > (state.currentDayRain ?: 0.0)) state.currentDayRain = currentDaily

    if (!sensorTemp || !sensorHum || !sensorPress) return

    def staleMins = settings.staleDataTimeout ?: 30
    def isStale = (settings.enableStaleCheck != false) && ((now() - (state.lastHeartbeat ?: now())) > (staleMins * 60000))
    state.isStale = isStale
    state.survivalModeActive = false
    
    // --- EMI/EMP GUARD & DYING BREATH SENSOR FAILOVER ---
    def wasAlarmActive = state.threatMatrix?.any { k, v -> v.state == "ALARM" || v.state == "ICE ALARM" }
    state.emiActive = false
    
    if (isStale && settings.enableEMIGuard != false) {
        def recentCloseStrike = state.lightningHistory?.find { (now() - it.time) < 1800000 && it.value <= 3.0 }
        if (recentCloseStrike) {
            logAction("⚡ EMI GUARD ACTIVE: Sensors went stale immediately after a close lightning strike. Latching current states and blocking cloud failover (Hardware likely frozen, not destroyed).")
            state.emiActive = true
            isStale = false 
        }
    }
    
    if (isStale && wasAlarmActive && settings.enableDyingBreath != false && !state.emiActive) {
        logAction("🚨 DYING BREATH: Local sensors went offline during an active ALARM. Latching states.")
        isStale = false 
    }
    
    // Fetch Data
    def t = getFloat(sensorTemp, ["temperature", "tempf"], 0.0)
    def h = getFloat(sensorHum, ["humidity"], 0.0)
    def rawP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], 0.0)
    def p = rawP + (settings.pressOffset ?: 0.0)
    
    def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
    def windVal = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def windDirVal = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
    def luxVal = getFloat(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], 0.0)
    def strikeCount = state.lightningHistory?.size() ?: 0

    if (settings.enableThermalSmoothing != false) {
        def lastT = state.smoothedTemp != null ? state.smoothedTemp : t
        def delta = Math.abs(t - lastT)
        if (delta > 3.0 && state.tempHistory?.size() > 0) t = lastT + ((t - lastT) * 0.3)
        state.smoothedTemp = t
    }
    
    def dp = calculateDewPoint(t, h)
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    updateHistory("spreadHistory", dpSpread, 3600000)

    def pTrendData = getTrendData(state.pressureHistory, 0.25)
    def pTrend3Hr = getTrendData(state.pressureHistory, 2.5) 
    def pAccel = getAccelerationData(state.pressureHistory)
    def tTrendData = getTrendData(state.tempHistory, 0.25)
    def sTrendData = getTrendData(state.spreadHistory, 0.25)
    def wTrendData = getTrendData(state.windHistory, 0.25)
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.spreadTrendStr = sTrendData.str
    state.windTrendStr = sensorWind ? wTrendData.str : "N/A"
    
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    
    def wb = calculateWetBulb(t, h)
    state.currentWetBulb = wb
    
    // Kinetic & Load Physics
    def airDensity = 1.225
    def windLoad = 0.0
    if (settings.enableKineticWind != false) {
        airDensity = calculateAirDensity(t, h, p)
        state.currentAirDensity = airDensity
        if (sensorWind) {
            windLoad = calculateWindLoad(airDensity, windVal)
            state.currentWindLoad = windLoad
        }
    }
    
    def kineticWindMultiplier = airDensity / 1.225 
    
    def isGravityWave = false
    if (settings.enableGravityWave != false) {
        isGravityWave = detectGravityWaves(state.pressureHistory)
        state.gravityWaveActive = isGravityWave
    }

    def shiftMagnitude = 0.0
    if (sensorWindDir && state.windDirHistory && state.windDirHistory.size() > 5) {
        def oldestDir = state.windDirHistory.first().value
        shiftMagnitude = getAngularDiff(oldestDir, windDirVal)
    }

    // Storm Vectoring (ETA)
    def vectorData = getStormVectorData(state.lightningHistory)
    if (settings.enableStormVectoring != false && sensorLightning && strikeCount > 0) {
        if (vectorData.status == "Approaching") state.lightningVectorStr = "Approaching @ ${String.format('%.1f', vectorData.speed)} mph (ETA: ~${String.format('%.0f', vectorData.eta)}m)"
        else if (vectorData.status == "Departing") state.lightningVectorStr = "Departing @ ${String.format('%.1f', vectorData.speed)} mph"
        else state.lightningVectorStr = vectorData.status
    } else {
        state.lightningVectorStr = "N/A"
    }

    // INTELLIGENT DEW REJECTION (First Drop Leak Sensors)
    def wetCountRaw = [sensorLeak, sensorLeak2, sensorLeak3].count { it?.currentValue("water") == "wet" }
    def reqWets = settings.leakSensorRequiredCount ? settings.leakSensorRequiredCount.toInteger() : 1
    def rawLeakWet = (wetCountRaw >= reqWets)
    
    if (rawLeakWet) {
        if (!state.leakWetStartTime) state.leakWetStartTime = now()
    } else {
        state.leakWetStartTime = null
    }

    def stuckLeakTimeoutMins = settings.stuckLeakTimeout ?: 60
    def stuckLeakActive = false
    if (rawLeakWet && state.leakWetStartTime && r == 0) {
        if ((now() - state.leakWetStartTime) > (stuckLeakTimeoutMins * 60000)) stuckLeakActive = true
    }
    state.stuckLeakActive = stuckLeakActive
    
    // Dew Rejection filters out morning frost/dew by ensuring wind is calm, lux is low, and spread is tight, unless real rain gauge tips
    def dewRejectionActive = false
    if (rawLeakWet && settings.enableDewRejection != false && r == 0.0) {
        def checkLux = sensorLux ? (luxVal < 100) : true
        def checkWind = sensorWind ? (windVal < 2.0) : true
        if (checkLux && checkWind && dpSpread <= 3.0) dewRejectionActive = true
    }
    state.dewRejectionActive = dewRejectionActive
    
    def leakWet = rawLeakWet && !stuckLeakActive && !dewRejectionActive
    state.leakWetStatus = leakWet

    def debounceMs = (settings.debounceMins ?: 15) * 60000
    def highestProbThisCycle = 0.0

    // --------------------------------------------------------------------------------
    // 1. TORNADO / EXTREME SHEAR DNA
    // --------------------------------------------------------------------------------
    if (settings.enableTornado != false && !isStale) {
        def tThreat = 0.0
        def tMath = ""
        
        if (sensorWind) {
            def kineticWindForce = windVal * kineticWindMultiplier
            tThreat = (kineticWindForce / 50.0) * 100.0
            if (tThreat > 100.0) tThreat = 100.0
            tMath += "Thrt: (W[${String.format('%.1f', windVal)}] * Dens[${String.format('%.2f', kineticWindMultiplier)}] / 50) * 100 = ${String.format('%.1f', tThreat)}%<br>"
        } else {
            tMath += "Thrt: 0% [No Wind Sensor]<br>"
        }
        
        def tProb = 0.0
        def tConf = 50
        def tWhy = "Atmospheric shear and barometric metrics are currently stable."
        tMath += "Prob Base: 0.0%<br>"
        
        if (isGravityWave) {
            tProb += 15.0
            tMath += "&nbsp;↳ PROACTIVE: Atmospheric Gravity Wave Detected: +15.0%<br>"
        }
        
        if (pTrendData.rate <= -0.04 && shiftMagnitude >= 45.0) {
            tProb += 30.0
            tMath += "&nbsp;↳ PROACTIVE: Severe Shear/Pressure Synergy: +30.0%<br>"
            if (dpSpread <= 5.0) {
                tProb += 15.0
                tMath += "&nbsp;&nbsp;↳ PROACTIVE: Low LCL/Saturated Base: +15.0%<br>"
            }
        }
        
        if (pAccel <= -0.08) {
            tProb += 50.0
            tMath += "&nbsp;↳ REACTIVE: Violent Pressure Acceleration (TVS) [${String.format('%.2f', pAccel)}]: +50.0%<br>"
        }
        
        if (settings.enableKineticWind != false && windLoad >= 10.0) {
            tConf += 25
            tProb += 40.0
            tMath += "&nbsp;↳ REACTIVE: Destructive Structural Wind Load [${String.format('%.1f', windLoad)}]: +40.0%<br>"
        } else if (sensorWind && windVal >= 35.0) {
            tConf += 25
            tProb += 40.0
            tMath += "&nbsp;↳ REACTIVE: Kinetic Destructive Wind: +40.0%<br>"
        }
        
        if (sensorWindDir) tConf += 25
        
        tProb = Math.round(tProb)
        if (tProb > 100) tProb = 100
        tThreat = Math.round(tThreat)
        if (tThreat > 100) tThreat = 100
        
        tMath += "<b>Final Prob: ${tProb}%</b>"
        
        if (tProb > highestProbThisCycle) highestProbThisCycle = tProb
        
        if (tProb > 20) {
            tWhy = "Probability is ${tProb}%. "
            if (isGravityWave) tWhy += "<b>PROACTIVE THREAT:</b> Pre-storm atmospheric gravity waves detected (supercell proxy). "
            if (pTrendData.rate <= -0.04 && shiftMagnitude >= 45.0) tWhy += "<b>PROACTIVE THREAT:</b> Dangerous synergy of rapidly falling pressure and shifting winds detected. "
            if (pAccel <= -0.08) tWhy += "<b>REACTIVE THREAT:</b> A violent, localized pressure plunge is actively occurring (TVS signature). "
            if (windLoad >= 10.0) tWhy += "Destructive structural wind load of ${String.format('%.1f', windLoad)} is impacting the location."
        }
        
        processHazardState("Tornado", tThreat, tProb, tConf, tWhy, tMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 2. THUNDERSTORM DNA
    // --------------------------------------------------------------------------------
    if (settings.enableThunderstorm != false && !isStale) {
        def tsThreat = 0.0
        def tsMath = ""
        
        if (sensorWind) {
            def kineticWindForce = windVal * kineticWindMultiplier
            tsThreat = (kineticWindForce / 40.0) * 100.0
            if (tsThreat > 100.0) tsThreat = 100.0
            tsMath += "Thrt(Wind): (W[${String.format('%.1f', windVal)}] * Dens[${String.format('%.2f', kineticWindMultiplier)}] / 40) * 100 = ${String.format('%.1f', tsThreat)}%<br>"
        }
        if (strikeCount > 0) {
            def lThreat = (strikeCount / 20.0) * 100.0
            if (lThreat > 100.0) lThreat = 100.0
            if (lThreat > tsThreat) tsThreat = lThreat
            tsMath += "Thrt(Lght): (Strk[${strikeCount}] / 20) * 100 = ${String.format('%.1f', lThreat)}%<br>"
        }
        if (!sensorWind && strikeCount == 0) tsMath += "Thrt: 0.0%<br>"
        
        def tsProb = 0.0
        def tsConf = 40
        def tsWhy = "No localized convective or electrical activity detected."
        tsMath += "Prob Base: 0.0%<br>"
        
        if (isGravityWave) {
            tsProb += 15.0
            tsMath += "&nbsp;↳ PROACTIVE: Atmospheric Gravity Wave Detected: +15.0%<br>"
        }
        
        if (t > 75.0 && dp > 65.0) {
            tsProb += 20.0
            tsMath += "&nbsp;↳ PROACTIVE: High Heat/Moisture (CAPE Proxy): +20.0%<br>"
            if (sTrendData.rate <= -2.0) {
                tsProb += 25.0
                tsMath += "&nbsp;&nbsp;↳ PROACTIVE: Severe Atmospheric Squeeze: +25.0%<br>"
            }
        }
        
        // Storm Vectoring Impact
        if (settings.enableStormVectoring != false && vectorData.status == "Approaching") {
            tsProb += 40.0
            tsMath += "&nbsp;↳ REACTIVE: Storm Core Approaching: +40.0%<br>"
        } else if (settings.enableStormVectoring != false && vectorData.status == "Departing") {
            tsProb -= 40.0
            tsMath += "&nbsp;↳ REACTIVE: Storm Core Departing: -40.0%<br>"
        } else if (strikeCount > 0) {
            tsConf += 30
            def lScore = (strikeCount / 10.0) * 40.0
            def lCap = lScore > 50.0 ? 50.0 : lScore
            tsProb += lCap
            tsMath += "&nbsp;↳ REACTIVE: Strikes [${strikeCount}]: +${String.format('%.1f', lCap)}%<br>"
        }
        
        if (windVal > 20.0 && pTrendData.rate >= 0.03 && tTrendData.rate < -2.0) {
            tsProb += 40.0
            tsMath += "&nbsp;↳ REACTIVE: Gust Front / Mesohigh Pressure Jump: +40.0%<br>"
        }
        
        // REACTIVE INSTANT DROP (Leak Sensor)
        if (leakWet) {
            tsProb += 30.0
            tsMath += "&nbsp;↳ REACTIVE: First Drop Leak Sensor: +30.0%<br>"
        }
        
        // DRY MICROBURST / VIRGA DETECTION
        if (settings.enableDryMicroburst != false && dpSpread >= 25.0 && tTrendData.diff <= -3.0 && wTrendData.diff >= 15.0 && r == 0.0) {
            tsProb += 60.0
            tsThreat = 100.0
            tsMath += "&nbsp;↳ REACTIVE: DRY MICROBURST (Virga Evaporative Cooling): +60.0%<br>"
            tsWhy += "<b>DRY MICROBURST DETECTED:</b> Massive dew point spread + Temp crash + Wind spike with ZERO rain indicates Virga flash-cooling the air column resulting in destructive straight-line downbursts. "
        }
        
        if (sensorWind) tsConf += 30
        
        tsProb = Math.round(tsProb)
        if (tsProb > 100) tsProb = 100
        if (tsProb < 0) tsProb = 0
        tsThreat = Math.round(tsThreat)
        if (tsThreat > 100) tsThreat = 100
        
        tsMath += "<b>Final Prob: ${tsProb}%</b>"
        
        if (tsProb > highestProbThisCycle) highestProbThisCycle = tsProb
        
        if (tsProb > 20 && !tsWhy.contains("MICROBURST")) {
            tsWhy = "Probability is ${tsProb}%. "
            if (isGravityWave) tsWhy += "<b>PROACTIVE THREAT:</b> Pre-storm atmospheric gravity waves detected. "
            if (vectorData.status == "Approaching") tsWhy += "<b>STORM VECTORING:</b> Active storm cell approaching sensor. "
            if (t > 75.0 && dp > 65.0 && sTrendData.rate <= -2.0) tsWhy += "<b>PROACTIVE THREAT:</b> High instability combined with rapid saturation indicates brewing convection. "
            if (windVal > 20.0 && pTrendData.rate >= 0.03) tsWhy += "<b>REACTIVE THREAT:</b> A sudden pressure jump, temp crash, and wind spike indicates a severe downdraft/gust front is directly overhead. "
        }
        
        processHazardState("Thunderstorm", tsThreat, tsProb, tsConf, tsWhy, tsMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 3. FLOOD DNA
    // --------------------------------------------------------------------------------
    if (settings.enableFlood != false && !isStale) {
        def fMath = ""
        def fThreat = (state.currentDayRain / 3.0) * 100.0
        if (fThreat > 100.0) fThreat = 100.0
        fMath += "Thrt: (Acc[${String.format('%.2f', state.currentDayRain)}] / 3.0) * 100 = ${String.format('%.1f', fThreat)}%<br>"
        
        def fProb = 0.0
        def fConf = 20
        def fWhy = "Ground is absorbing moisture effectively with no extreme rain rates."
        fMath += "Prob Base: 0.0%<br>"
        
        if (sensorRain) fConf += 40
        if (sensorRainDaily) fConf += 40
        
        if (state.currentDayRain >= 1.5 && pTrendData.rate <= -0.03) {
            fProb += 30.0
            fMath += "&nbsp;↳ PREDICTIVE: Saturated Ground + Low Pressure Front: +30.0%<br>"
        }
        
        def rScore = (r / 2.0) * 60.0
        def rCap = rScore > 70.0 ? 70.0 : rScore
        fProb += rCap
        fMath += "&nbsp;↳ Rate [${String.format('%.2f', r)} in/hr]: +${String.format('%.1f', rCap)}%<br>"
        
        if (leakWet && r == 0.0) {
            fProb += 15.0
            fMath += "&nbsp;↳ Reactive: Instant Leak (Pre-Gauge): +15.0%<br>"
        }
        
        def aScore = (state.currentDayRain / 3.0) * 40.0
        def aCap = aScore > 60.0 ? 60.0 : aScore
        fProb += aCap
        fMath += "&nbsp;↳ Accumulation: +${String.format('%.1f', aCap)}%<br>"
        
        fProb = Math.round(fProb)
        if (fProb > 100) fProb = 100
        fThreat = Math.round(fThreat)
        
        fMath += "<b>Final Prob: ${fProb}%</b>"
        
        if (fProb > highestProbThisCycle) highestProbThisCycle = fProb
        
        if (fProb > 0) {
            fWhy = "Threat intensity is ${fThreat}% based on ${String.format('%.2f', state.currentDayRain)} inches of total daily accumulation. "
            if (state.currentDayRain >= 1.5 && pTrendData.rate <= -0.03) fWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> Ground is already heavily saturated and an incoming low-pressure front is detected. "
            if (r > 0) fWhy += "Probability of flash flooding is ${fProb}% driven by a real-time rain rate of ${String.format('%.2f', r)} in/hr. "
        }
        
        processHazardState("Flood", fThreat, fProb, fConf, fWhy, fMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 4. FREEZE & ICE DNA
    // --------------------------------------------------------------------------------
    if (settings.enableFreeze != false && !isStale) {
        def frMath = ""
        def frThreat = 0.0
        if (t <= 32.0) {
            frThreat = 100.0
            frMath += "Thrt: Temp[${t}] <= 32 = 100.0%<br>"
        } else {
            frThreat = 100.0 - ((t - 32.0) * 5.0)
            if (frThreat < 0.0) frThreat = 0.0
            frMath += "Thrt: 100 - ((T[${t}] - 32) * 5) = ${String.format('%.1f', frThreat)}%<br>"
        }
        
        def frProb = 0.0
        def frConf = 70
        def frWhy = "Temperatures safely above freezing."
        frMath += "Prob Base: 0.0%<br>"
        
        if (sensorHum) frConf += 30
        def isIceStorm = false
        
        if (t <= 35.0 && t >= 31.0 && wb <= 32.0 && (r > 0.0 || leakWet)) {
            isIceStorm = true
            frProb = 100
            frThreat = 100
            frMath += "&nbsp;↳ REACTIVE: ICE STORM (Liquid Precip + Sub-Freezing Wet Bulb): 100%<br>"
            frWhy = "<b>ICE ACCUMULATION WARNING:</b> Precipitation is falling into a sub-freezing evaporative layer. Flash-freezing and ice accumulation actively occurring. "
        }
        else if (t <= 32.0) {
            frProb = 100
            frMath += "&nbsp;↳ Hard Freeze Active: 100%<br>"
        } else if (t <= 40.0 && tTrendData.rate < 0) {
            def dropRate = Math.abs(tTrendData.rate > 0.1 ? tTrendData.rate : 0.1)
            def hoursToFreeze = (t - 32.0) / dropRate
            frMath += "&nbsp;↳ PREDICTIVE: Est Time to 32°: ${String.format('%.1f', hoursToFreeze)} hrs<br>"
            if (hoursToFreeze <= 2.0) { frProb = 90; frMath += "&nbsp;&nbsp;&nbsp;↳ < 2hrs: +90.0%<br>" }
            else if (hoursToFreeze <= 4.0) { frProb = 65; frMath += "&nbsp;&nbsp;&nbsp;↳ < 4hrs: +65.0%<br>" }
            else { frProb = 30; frMath += "&nbsp;&nbsp;&nbsp;↳ > 4hrs: +30.0%<br>" }
        } else {
             frMath += "&nbsp;↳ Trajectory Safe.<br>"
        }
        
        frProb = Math.round(frProb)
        if (frProb > 100) frProb = 100
        frThreat = Math.round(frThreat)
        if (frThreat > 100) frThreat = 100
        
        frMath += "<b>Final Prob: ${frProb}%</b>"
        
        if (frProb > highestProbThisCycle) highestProbThisCycle = frProb
        
        if (frProb > 0 && !isIceStorm) {
            frWhy = "Current temperature is ${String.format('%.1f', t)}°. Threat intensity is ${frThreat}%. "
            if (frProb == 100) {
                frWhy += (dpSpread <= 3.0) ? "Hard Freeze with Hoar Frost actively occurring. " : "Dry Hard Freeze actively occurring. "
            } else {
                frWhy += "Probability is ${frProb}% based on a trajectory of dropping ${String.format('%.2f', Math.abs(tTrendData.rate))}°/hr towards the freezing point. "
            }
        }
        
        if (isIceStorm) mathEx += "|ICE_FLAG_TRUE"
        processHazardState("Freeze", frThreat, frProb, frConf, frWhy, frMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 5. SEVERE HEAT DNA (WBGT)
    // --------------------------------------------------------------------------------
    if (settings.enableSevereHeat != false && !isStale) {
        def shMath = ""
        def targetHeatMetric = 0.0
        
        if (settings.enableWBGT != false && sensorLux && sensorWind) {
            targetHeatMetric = calculateWBGT(t, h, windVal, luxVal)
            state.currentWBGT = targetHeatMetric
            shMath += "True WBGT (Sun+Wind+Hum): ${String.format('%.1f', targetHeatMetric)}°F<br>"
        } else {
            targetHeatMetric = calculateHeatIndex(t, h)
            shMath += "NWS Shade Heat Index: ${String.format('%.1f', targetHeatMetric)}°F<br>"
        }
        
        def shThreat = 0.0
        if (targetHeatMetric >= 90.0) {
            shThreat = ((targetHeatMetric - 90.0) / 35.0) * 100.0
        }
        if (shThreat > 100.0) shThreat = 100.0
        shMath += "Thrt: ((Metric - 90)/35)*100 = ${String.format('%.1f', shThreat)}%<br>"
        
        def shProb = 0.0
        def shConf = 60
        if (sensorHum) shConf += 40
        def shWhy = "Heat levels are currently safe."
        shMath += "Prob Base: 0.0%<br>"
        
        if (targetHeatMetric >= 90.0) {
            shProb = shThreat 
            shMath += "&nbsp;↳ Base Level: +${String.format('%.1f', shProb)}%<br>"
            if (tTrendData.rate > 0) {
                def bump = (tTrendData.rate / 2.0) * 20.0
                shProb += bump
                shMath += "&nbsp;↳ PREDICTIVE: Heating Bump [${String.format('%.1f', tTrendData.rate)}°/hr]: +${String.format('%.1f', bump)}%<br>"
            }
        } else if (t >= 80.0 && tTrendData.rate > 1.0) {
             shProb = ((t - 80.0)/10.0) * 20.0
             shMath += "&nbsp;↳ PREDICTIVE: Trajectory to 90°: +${String.format('%.1f', shProb)}%<br>"
        }
        
        shProb = Math.round(shProb)
        if (shProb > 100) shProb = 100
        shThreat = Math.round(shThreat)
        
        shMath += "<b>Final Prob: ${shProb}%</b>"
        
        if (shProb > highestProbThisCycle) highestProbThisCycle = shProb
        
        if (shProb > 0) {
            shWhy = "Current Heat Metric is ${String.format('%.1f', targetHeatMetric)}°F. "
            if (targetHeatMetric >= 103.0) shWhy += "<b>DANGER:</b> High risk of heat exhaustion or heat stroke for individuals or pets outside. "
            else if (targetHeatMetric >= 90.0) shWhy += "CAUTION: Prolonged exposure and physical activity may lead to heat exhaustion. "
        }
        
        processHazardState("SevereHeat", shThreat, shProb, shConf, shWhy, shMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 6. TROPICAL DNA
    // --------------------------------------------------------------------------------
    if (settings.enableTropical != false && !isStale) {
        def trMath = ""
        def trThreat = ((29.90 - p) / 0.50) * 100.0
        if (trThreat > 100.0) trThreat = 100.0
        if (trThreat < 0.0) trThreat = 0.0
        trMath += "Thrt: ((29.90 - P[${String.format('%.2f', p)}])/0.5) * 100 = ${String.format('%.1f', trThreat)}%<br>"
        
        def trProb = 0.0
        def trConf = 50
        def trWhy = "No sustained tropical barometric signatures detected locally."
        trMath += "Prob Base: 0.0%<br>"
        
        if (sensorPress) trConf += 25
        if (sensorWind) trConf += 25
        
        if (p < 29.80) {
            def pScore = ((29.80 - p) / 0.30) * 60.0
            def pCap = pScore > 70.0 ? 70.0 : pScore
            trProb += pCap
            trMath += "&nbsp;↳ Depth Score (MSLP Calibrated): +${String.format('%.1f', pCap)}%<br>"
        }
        
        if (pTrend3Hr.rate <= -0.05) {
            trProb += 15
            trMath += "&nbsp;↳ PREDICTIVE: Sustained 3hr Drop [${String.format('%.2f', pTrend3Hr.rate)}]: +15.0%<br>"
            if (dp >= 70.0) {
                trProb += 15
                trMath += "&nbsp;&nbsp;&nbsp;↳ PREDICTIVE: Deep Moisture Loading Synergy: +15.0%<br>"
            }
        }
        
        if (windVal > 30.0) {
            trProb += 30
            trMath += "&nbsp;↳ Sustained Wind [>30mph]: +30.0%<br>"
        }
        
        trProb = Math.round(trProb)
        if (trProb > 100) trProb = 100
        trThreat = Math.round(trThreat)
        if (trThreat > 100) trThreat = 100
        
        trMath += "<b>Final Prob: ${trProb}%</b>"
        
        if (trProb > highestProbThisCycle) highestProbThisCycle = trProb
        
        if (trProb > 20) {
            trWhy = "Threat intensity is ${trThreat}% due to a current barometric depth of ${String.format('%.2f', p)} inHg. "
            if (pTrend3Hr.rate <= -0.05 && dp >= 70.0) trWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> A long-duration barometric vacuum is combining with deep atmospheric moisture loading. "
            trWhy += "Probability is ${trProb}% factoring in a 3-hour pressure trajectory of ${String.format('%.2f', pTrend3Hr.rate)} inHg/hr and sustained winds of ${String.format('%.1f', windVal)} mph. "
        }
        
        processHazardState("Tropical", trThreat, trProb, trConf, trWhy, trMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 7. FIRE WEATHER (RED FLAG) DNA
    // --------------------------------------------------------------------------------
    if (settings.enableFireWeather != false && !isStale) {
        def fwMath = ""
        def fwThreat = 0.0
        
        def rainWeek = getFloat(sensorRainWeekly, ["rainWeekly", "weeklyrainin", "weeklyWater"], 0.0)
        def past7DayTotal = state.sevenDayRain?.sum { it.amount } ?: 0.0
        def totalFuelMoisture = rainWeek > 0.0 ? rainWeek : past7DayTotal
        fwMath += "Fuel Moisture Proxy (7d Rain): ${String.format('%.2f', totalFuelMoisture)} in<br>"
        
        if (vpd > 1.5 && windVal > 10.0 && totalFuelMoisture < 0.25) {
            fwThreat = ((vpd - 1.5) / 1.5) * 50.0 + ((windVal - 10.0) / 20.0) * 50.0
        }
        if (fwThreat > 100.0) fwThreat = 100.0
        if (fwThreat < 0.0) fwThreat = 0.0
        fwMath += "Thrt: VPD[${String.format('%.2f', vpd)}] + Wind[${String.format('%.1f', windVal)}] = ${String.format('%.1f', fwThreat)}%<br>"
        
        def fwProb = 0.0
        def fwConf = 75
        def fwWhy = "Fire spread risk is currently low."
        fwMath += "Prob Base: 0.0%<br>"
        
        if (totalFuelMoisture < 0.1) {
            if (vpd >= 2.0) {
                fwProb += 40.0
                fwMath += "&nbsp;↳ Severe VPD (Air sucking moisture from fuel): +40.0%<br>"
            } else if (vpd >= 1.5) {
                fwProb += 20.0
                fwMath += "&nbsp;↳ Elevated VPD: +20.0%<br>"
            }
            
            if (windVal >= 25.0) {
                fwProb += 60.0
                fwMath += "&nbsp;↳ Critical Wind Spread Potential: +60.0%<br>"
            } else if (windVal >= 15.0) {
                fwProb += 30.0
                fwMath += "&nbsp;↳ Elevated Wind Spread Potential: +30.0%<br>"
            }
        } else {
            fwMath += "&nbsp;↳ Recent Rain (${String.format('%.2f', totalFuelMoisture)} in) suppressing fire threat.<br>"
        }
        
        fwProb = Math.round(fwProb)
        if (fwProb > 100) fwProb = 100
        fwThreat = Math.round(fwThreat)
        
        fwMath += "<b>Final Prob: ${fwProb}%</b>"
        
        if (fwProb > highestProbThisCycle) highestProbThisCycle = fwProb
        
        if (fwProb > 0) {
            fwWhy = "Probability is ${fwProb}% based on bone-dry local fuel (0.00in rain recently). "
            if (vpd >= 2.0) fwWhy += "<b>DANGER:</b> Extreme Vapor Pressure Deficit (${String.format('%.2f', vpd)} kPa) is actively dehydrating vegetation. "
            if (windVal >= 15.0) fwWhy += "Sustained winds of ${String.format('%.1f', windVal)} mph create critical rapid-spread potential for any spark."
        }
        
        processHazardState("FireWeather", fwThreat, fwProb, fwConf, fwWhy, fwMath, debounceMs)
    }

    if (isStale && !state.survivalModeActive) {
        def hazards = ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical", "FireWeather"]
        hazards.each { haz -> processHazardState(haz, 0, 0, 0, "⚠ Sensors Offline. Stale data prevented safe calculation.", "Offline. Connect sensors.", debounceMs) }
        highestProbThisCycle = 0.0 
    }
    
    // Store highest probability for chart rendering
    updateHistory("probHistory", highestProbThisCycle, 86400000)

    // --- FORENSIC EVENT TRACKING & SUMMARY ---
    def anyAlarmActive = state.threatMatrix?.any { k, v -> v.state == "ALARM" || v.state == "WARNING" || v.state == "ICE ALARM" || v.state == "ICE WARNING" }
    
    if (anyAlarmActive) {
        state.globalThreatActive = true
        state.globalAllClearPending = false 
        if (!state.eventActive) {
            state.eventActive = true
            state.activeEventStats = [
                maxWind: windVal, 
                minPress: p, 
                startTemp: t, 
                minTemp: t, 
                startTime: now()
            ]
        } else {
            if (windVal > state.activeEventStats.maxWind) state.activeEventStats.maxWind = windVal
            if (p < state.activeEventStats.minPress && p > 0) state.activeEventStats.minPress = p
            if (t < state.activeEventStats.minTemp) state.activeEventStats.minTemp = t
        }
    } else if (state.eventActive) {
        state.eventActive = false
        def durMs = now() - state.activeEventStats.startTime
        def durMins = Math.round(durMs / 60000)
        def durStr = durMins > 60 ? "${Math.round(durMins/60)}h ${durMins%60}m" : "${durMins}m"
        def tDrop = state.activeEventStats.startTemp - state.activeEventStats.minTemp
        
        state.lastEventReport = [
            date: new Date(state.activeEventStats.startTime).format("MM/dd/yy hh:mm a", location.timeZone),
            maxWind: state.activeEventStats.maxWind,
            minPress: state.activeEventStats.minPress,
            tempDrop: tDrop > 0 ? tDrop : 0.0,
            duration: durStr
        ]
        logAction("Forensic Event Concluded: Max Wind ${state.lastEventReport.maxWind}mph, Min Press ${state.lastEventReport.minPress}inHg, Duration ${durStr}.")
    }

    // --- GLOBAL STAND-DOWN SEQUENCER (ALL-CLEAR) ---
    if (!anyAlarmActive && state.globalThreatActive) {
        if (pTrendData.rate >= 0.0 && windVal < 15.0) {
            logAction("✅ GLOBAL STAND-DOWN: All hazard matrices have cleared, pressure is stabilizing, and winds are calm.")
            state.globalThreatActive = false
            state.globalAllClearPending = false
            
            if (settings.globalAllClearNotify) sendNotification("✅ Weather All-Clear: All severe weather threats have passed.")
            if (settings.globalAllClearTTS) playAudio(settings.ttsGlobalAllClear ?: "The severe weather event has concluded. All clear.")
            if (settings.globalAllClearSound) playSoundFile(settings.urlGlobalAllClear)
        } else {
            if (!state.globalAllClearPending) {
                logAction("⏳ STAND-DOWN PENDING: Matrix cleared, but waiting for barometric/kinetic stabilization to officially broadcast all-clear.")
                state.globalAllClearPending = true
            }
        }
    }

    // DEFCON WATCHDOG LOGIC
    if (settings.enableDefcon != false) {
        def threshold = settings.defconThresh ?: 25
        if (highestProbThisCycle >= threshold && !state.watchdogActive) {
            state.watchdogActive = true
            logAction("🚨 DEFCON WATCHDOG ENGAGED: Threat probability crossed ${threshold}%. Commencing overdrive polling.")
            watchdogPoll()
        } else if (highestProbThisCycle < threshold && state.watchdogActive) {
            state.watchdogActive = false
            logAction("✅ DEFCON WATCHDOG DISENGAGED: All threat probabilities below ${threshold}%. Returning to standard polling schedule.")
        }
    } else {
        state.watchdogActive = false
    }

    state.lastEvalDuration = now() - evalStart
    updateChildDevice()
}

// === HAZARD STATE ROUTING ===
def processHazardState(haz, threat, prob, conf, why, mathEx, debounceMs) {
    def matrix = state.threatMatrix ?: [:]
    def data = matrix[haz] ?: [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0, history: [], currentEvent: null]
    if (!data.history) data.history = []
    
    def pfx = haz.toLowerCase()
    def warnThresh = settings["${pfx}WarnThresh"] ?: 50
    def alarmThresh = settings["${pfx}AlarmThresh"] ?: 80
    
    def targetState = "Clear"
    if (prob >= alarmThresh) targetState = "ALARM"
    else if (prob >= warnThresh) targetState = "WARNING"
    
    def isIceStorm = mathEx.contains("|ICE_FLAG_TRUE")
    if (haz == "Freeze" && isIceStorm && targetState != "Clear") {
        targetState = targetState == "ALARM" ? "ICE ALARM" : "ICE WARNING"
    }
    
    if (settings.enableNOAA && settings.noaaTriggersHardware) {
        def noaaMap = state.noaaAlertsMap ?: [:]
        def officialStatus = noaaMap[haz] ?: "Clear"
        
        if (officialStatus == "ALARM") {
            targetState = isIceStorm ? "ICE ALARM" : "ALARM"
            why = "<b>[🚨 OVERRIDE: NOAA OFFICIAL ALARM TRIGGERED]</b> " + why
        } else if (officialStatus == "WARNING" && targetState == "Clear") {
            targetState = isIceStorm ? "ICE WARNING" : "WARNING"
            why = "<b>[⚠ OVERRIDE: NOAA OFFICIAL WARNING TRIGGERED]</b> " + why
        }
    }
    
    def currentState = data.state
    def timeSinceChange = now() - data.lastTrig
    def allowTransition = false
    
    def p = getFloat(sensorPress, ["pressure"], 0.0) + (settings.pressOffset ?: 0.0)
    def pTrend = getTrendData(state.pressureHistory, 0.25)
    def isEyeOfStorm = (settings.enableEyeOfStorm != false && p > 0 && p < 29.60 && pTrend.rate <= 0.05)

    if (isEyeOfStorm && currentState.contains("ALARM") && (targetState == "Clear" || targetState.contains("WARNING")) && (haz == "Thunderstorm" || haz == "Tornado" || haz == "Tropical")) {
        targetState = currentState 
        allowTransition = false
        why += " <br><span style='color:#d9534f;'><b>[BAROMETRIC LOCK: Holding ALARM due to 'Eye of Storm'. Low Pressure center directly overhead]</b></span>"
        mathEx += "<b>EYE OF STORM LOCK ACTIVE</b>"
    } else {
        if (currentState == "Clear" && targetState != "Clear") allowTransition = true
        else if (currentState.contains("WARNING") && targetState.contains("ALARM")) allowTransition = true
        else if (currentState == targetState) allowTransition = true 
        else if (timeSinceChange >= debounceMs) allowTransition = true
        else why += " <br><span style='color:#f0ad4e;'><b>[Safety Hardware Hold Active for ${Math.ceil((debounceMs - timeSinceChange)/60000).toInteger()} more mins]</b></span>"
    }
    
    if (allowTransition && currentState != targetState) {
        logAction("${haz} DNA shifted from ${currentState} to ${targetState}.")
        data.lastTrig = now()
        
        if (currentState != "Clear") {
            def startTs = data.currentEvent?.start ?: (now() - timeSinceChange)
            def durMs = now() - startTs
            def durMins = Math.round(durMs / 60000)
            def durStr = durMins > 60 ? "${Math.round(durMins/60)}h ${durMins%60}m" : "${durMins}m"
            def startStr = new Date(startTs).format("MM/dd HH:mm", location.timeZone)
            
            data.history.add(0, [type: currentState, start: startStr, dur: durStr])
            if (data.history.size() > 5) data.history = data.history[0..4]
        }
        data.currentEvent = [state: targetState, start: now()]

        def warnAllowedModes = settings["${pfx}WarnModes"]
        def warnModeOk = (!warnAllowedModes || warnAllowedModes.contains(location.mode))
        
        def alarmAllowedModes = settings["${pfx}AlarmModes"]
        def alarmModeOk = (!alarmAllowedModes || alarmAllowedModes.contains(location.mode))
        
        def warnSw = settings["switch${haz}Warn"]
        def alarmSw = settings["switch${haz}Alarm"]
        
        if (targetState.contains("ALARM")) {
            if (haz == "Freeze" && isIceStorm && settings.switchIceAlarm) {
                safeOn(settings.switchIceAlarm)
                safeOff(alarmSw)
            } else {
                safeOn(alarmSw)
                if (haz == "Freeze" && settings.switchIceAlarm) safeOff(settings.switchIceAlarm)
            }
            safeOff(warnSw)
            
            if (alarmModeOk) {
                if (settings["${pfx}AlarmNotify"]) sendNotification("🚨 CRITICAL: ${targetState == 'ICE ALARM' ? 'ICE STORM' : haz} ALARM Conditions Detected!")
                if (settings["${pfx}AlarmTTS"]) playAudio(settings["tts${haz}Alarm"])
                if (settings["${pfx}AlarmSiren"]) triggerSiren()
                if (settings["${pfx}AlarmSound"]) playSoundFile(settings["url${haz}Alarm"])
            } else {
                logAction("Alarm audio/push suppressed due to Mode Restriction.")
            }
            
        } else if (targetState.contains("WARNING")) {
            safeOff(alarmSw)
            if (haz == "Freeze" && settings.switchIceAlarm) safeOff(settings.switchIceAlarm)
            safeOn(warnSw)
            
            if (warnModeOk) {
                if (settings["${pfx}WarnNotify"]) sendNotification("⚠ WARNING: Elevated ${targetState == 'ICE WARNING' ? 'Freezing Rain/Ice' : haz} Conditions.")
                if (settings["${pfx}WarnTTS"]) playAudio(settings["tts${haz}Warn"])
                if (settings["${pfx}WarnSiren"]) triggerSiren()
                if (settings["${pfx}WarnSound"]) playSoundFile(settings["url${haz}Warn"])
            } else {
                logAction("Warning audio/push suppressed due to Mode Restriction.")
            }
            
        } else {
            safeOff(alarmSw)
            safeOff(warnSw)
            if (haz == "Freeze" && settings.switchIceAlarm) safeOff(settings.switchIceAlarm)
            if (currentState != "Clear") stopAllSirens()
        }
    }
    
    data.threat = threat
    data.prob = prob
    data.conf = conf
    data.state = allowTransition ? targetState : currentState
    data.howWhy = why
    data.mathEx = mathEx
    
    matrix[haz] = data
    state.threatMatrix = matrix
}

// === HARDWARE ROUTING ===
def shouldSpeakerAnnounce(device) {
    if (settings["isAlwaysOn_${device.id}"]) return true
    def overrideSw = settings["nightSwitch_${device.id}"]
    if (overrideSw && overrideSw.currentValue("switch") == "on") return true
    
    def mappedSensors = settings["motionMap_${device.id}"]
    if (!mappedSensors) return true 
    
    def sensorList = mappedSensors instanceof List ? mappedSensors : [mappedSensors]
    def isActive = false
    for (sensor in sensorList) {
        if (sensor.currentValue("motion") == "active") {
            isActive = true
            break
        }
        def lastActive = state["motionLastActive_${sensor.id}"] ?: 0
        if (now() - lastActive <= 300000) {
            isActive = true
            break
        }
    }
    return isActive
}

def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on") {
        try { dev.on() } catch (e) { log.error "Failed ON: ${e}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed OFF: ${e}" }
    }
}

def sendNotification(msg) {
    if (notifyDevices) {
        notifyDevices.each { it.deviceNotification(msg) }
        logAction("Push Sent: ${msg}")
    }
}

def playAudio(msg, force = false) {
    if (!msg || !audioDevices) return
    def vol = audioVolume ?: 65
    try {
        audioDevices.each { speaker ->
            if (force || shouldSpeakerAnnounce(speaker)) {
                if (speaker.hasCommand("setVolume")) speaker.setVolume(vol)
                if (speaker.hasCommand("speak")) speaker.speak(msg)
                else if (speaker.hasCommand("playText")) speaker.playText(msg)
            } else {
                logAction("TTS suppressed on ${speaker.displayName} - Room Empty/No Motion.")
            }
        }
        logAction("TTS Broadcast Execution Completed.")
    } catch (e) { log.error "TTS routing failed: ${e}" }
}

def playSoundFile(url, force = false) {
    if (!url || !soundDevices) return
    def vol = audioVolume ?: 65
    try {
        soundDevices.each { player ->
            if (force || shouldSpeakerAnnounce(player)) {
                if (player.hasCommand("setVolume")) player.setVolume(vol)
                if (url.trim().isInteger() && player.hasCommand("playSound")) {
                    player.playSound(url.trim().toInteger())
                } else if (player.hasCommand("playTrack")) {
                    player.playTrack(url.trim())
                }
            } else {
                logAction("Sound file suppressed on ${player.displayName} - Room Empty/No Motion.")
            }
        }
        logAction("Sound File Routing Completed.")
    } catch (e) { log.error "Audio routing failed: ${e}" }
}

def triggerSiren() {
    if (sirenDevices) {
        try {
            sirenDevices.each { siren -> 
                if (siren.hasCommand("siren")) siren.siren()
                else if (siren.hasCommand("on")) siren.on() 
            }
            logAction("SIREN TRIGGERED")
        } catch (e) { log.error "Siren trigger failed: ${e}" }
    }
}

def stopAllSirens() {
    if (sirenDevices) {
        try {
            sirenDevices.each { siren ->
                if (siren.hasCommand("off")) siren.off()
            }
            logAction("All Sirens Silenced")
        } catch (e) { log.error "Siren shutoff failed: ${e}" }
    }
}

def createChildDevice() {
    def deviceId = "SevWeather-${app.id}"
    if (!getChildDevice(deviceId)) {
        try {
            addChildDevice("ShaneAllen", "Advanced Severe Weather Information Device", deviceId, null, [name: "Advanced Severe Weather Information Device", label: "Advanced Severe Weather Information Device", isComponent: false])
            logAction("Child device successfully created.")
            updateChildDevice()
        } catch (e) { log.error "Failed to create child device. Ensure the driver is installed. ${e}" }
    }
}

// === CHILD DEVICE SYNCHRONIZATION ===
def updateChildDevice() {
    def child = getChildDevice("SevWeather-${app.id}")
    if (child) {
        def matrix = state.threatMatrix
        def hazards = ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical", "FireWeather"]
        
        def highestState = "Clear"
        hazards.each { haz ->
            if (!matrix[haz]) return
            def s = matrix[haz].state
            if (s.contains("ALARM")) highestState = "ALARM"
            else if (s.contains("WARNING") && highestState == "Clear") highestState = "WARNING"
        }
        
        def statsMap = [
            globalState: highestState,
            pressureTrend: state.pressureTrendStr ?: "Stable",
            tempTrend: state.tempTrendStr ?: "Stable",
            windTrend: state.windTrendStr ?: "Stable",
            currentDayRain: state.currentDayRain ?: 0.0
        ]
        
        child.updateData(matrix, statsMap)
    }
}

// === LOGGING ===
def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
def modeChangeHandler(evt) { evaluateMatrix() }
