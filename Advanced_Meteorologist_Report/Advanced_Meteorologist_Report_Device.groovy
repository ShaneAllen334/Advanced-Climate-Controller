/**
 * Advanced Meteorologist Report Device
 *
 * Author: ShaneAllen
 */

metadata {
    definition (name: "Advanced Meteorologist Report Device", namespace: "ShaneAllen", author: "ShaneAllen") {
        capability "Sensor"
        capability "Actuator"
        
        // Granular Attributes for Rule Machine / Automations
        attribute "meteorologistScript", "string"
        attribute "currentTemp", "number"
        attribute "currentConditions", "string"
        attribute "todayHigh", "number"
        attribute "todayLow", "number"
        
        // New Metric Attributes
        attribute "todayRain", "number"
        attribute "todayUV", "number"
        attribute "todayPollen", "string"
        attribute "moonPhase", "string"
        
        // Dashboard HTML Tiles
        attribute "htmlTile_Compact", "string"
        attribute "htmlTile_Extended", "string"
    }
}

// Re-structured to use a data Map to handle expanding features without massive function signatures
def updateTile(Map data) {
    // 1. Update Standard Attributes
    sendEvent(name: "meteorologistScript", value: data.script)
    sendEvent(name: "currentTemp", value: data.currentTemp)
    sendEvent(name: "currentConditions", value: data.currentConditions)
    sendEvent(name: "todayPollen", value: data.pollen)
    sendEvent(name: "moonPhase", value: data.moon)
    
    def tHigh = data.highs ? data.highs[0] : "--"
    def tLow = data.lows ? data.lows[0] : "--"
    def tRain = data.rain ? data.rain[0] : "0"
    def tUV = data.uv ? data.uv[0] : "0"
    
    sendEvent(name: "todayHigh", value: tHigh)
    sendEvent(name: "todayLow", value: tLow)
    sendEvent(name: "todayRain", value: tRain)
    sendEvent(name: "todayUV", value: tUV)
    
    def extraDetails = ""
    if (data.pollen != "N/A" || data.moon) {
        extraDetails = "<div style='font-size:11px; margin-bottom:8px; color:#adb5bd;'>💧 ${tRain}\" | 🌸 ${data.pollen} | 🌔 ${data.moon}</div>"
    }
    
    // 2. Build Compact HTML Tile
    def compactHtml = """
    <div style='background-color:#1e1e1e; color:#ffffff; padding:10px; border-radius:8px; font-family:sans-serif; height:100%; box-sizing:border-box;'>
        <div style='font-size:15px; font-weight:bold; border-bottom:1px solid #444; padding-bottom:5px; margin-bottom:8px;'>
            🌦️ Weather Anchor
        </div>
        <div style='font-size:13px; margin-bottom:4px; color:#4dabf7;'>
            <b>${data.currentConditions?.capitalize()}</b> | ${data.currentTemp}° (H: ${tHigh}° L: ${tLow}°)
        </div>
        ${extraDetails}
        <div style='font-size:12px; font-style:italic; line-height:1.4; color:#cccccc; overflow:hidden;'>
            "${data.script}"
        </div>
    </div>
    """
    sendEvent(name: "htmlTile_Compact", value: compactHtml)
    
    // 3. Build Extended HTML Tile
    def forecastRow = ""
    if (data.dates && data.dates.size() >= 5) {
        // Loop through the next 4 days (Index 1 to 4)
        for (int i = 1; i <= 4; i++) {
            def dayName = getDayOfWeek(data.dates[i])
            def fRain = data.rain ? data.rain[i] : "0"
            forecastRow += "<td style='padding:4px; border-left:1px solid #333;'><b style='color:#ccc;'>${dayName}</b><br><span style='color:#ff6b6b;'>${data.highs[i]}°</span><br><span style='color:#4dabf7;'>${data.lows[i]}°</span><br><span style='color:#17a2b8;'>💧 ${fRain}\"</span></td>"
        }
    }
    
    def extendedHtml = """
    <div style='background-color:#1e1e1e; color:#ffffff; padding:10px; border-radius:8px; font-family:sans-serif; height:100%; box-sizing:border-box; display:flex; flex-direction:column; justify-content:space-between;'>
        <div>
            <div style='font-size:15px; font-weight:bold; border-bottom:1px solid #444; padding-bottom:5px; margin-bottom:5px; display:flex; justify-content:space-between;'>
                <span>🌦️ Weather Anchor</span>
                <span style='font-size:11px; font-weight:normal; color:#adb5bd; padding-top:3px;'>🌸 ${data.pollen} | 🌔 ${data.moon}</span>
            </div>
            <div style='font-size:12px; font-style:italic; line-height:1.3; color:#cccccc; margin-bottom:10px;'>
                "${data.script}"
            </div>
        </div>
        <table style='width:100%; text-align:center; font-size:11px; border-top:1px solid #444; padding-top:5px; table-layout:fixed; border-collapse:collapse;'>
            <tr>
                <td style='padding:4px;'><b style='color:#ccc;'>Today</b><br><span style='color:#ff6b6b;'>${tHigh}°</span><br><span style='color:#4dabf7;'>${tLow}°</span><br><span style='color:#17a2b8;'>💧 ${tRain}\"</span></td>
                ${forecastRow}
            </tr>
        </table>
    </div>
    """
    sendEvent(name: "htmlTile_Extended", value: extendedHtml)
}

// Helper to convert date strings to Short Day Names (Mon, Tue, Wed)
def getDayOfWeek(dateString) {
    try {
        def date = Date.parse("yyyy-MM-dd", dateString)
        return date.format("EEE")
    } catch (e) {
        return "N/A"
    }
}
