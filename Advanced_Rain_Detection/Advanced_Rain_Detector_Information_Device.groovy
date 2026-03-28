/**
 * Advanced Rain Detector Information Device
 */

metadata {
    definition (
        name: "Advanced Rain Detector Information Device", 
        namespace: "ShaneAllen", 
        author: "ShaneAllen",
        description: "Virtual device that holds advanced meteorological calculations from the Advanced Rain Detection app."
    ) {
        capability "Sensor"
        
        // Core States
        attribute "weatherState", "string"
        attribute "rainProbability", "number"
        attribute "expectedClearTime", "string"
        attribute "dryingPotential", "string"
        
        // Precipitation States
        attribute "sprinkling", "string"
        attribute "raining", "string"
        
        // Calculated Thermodynamics
        attribute "vpd", "number"
        attribute "dewPoint", "number"
        attribute "dewPointSpread", "number"
        
        // Trends
        attribute "pressureTrend", "string"
        attribute "tempTrend", "string"
        attribute "luxTrend", "string"
        attribute "windTrend", "string"
        
        // Accumulation History
        attribute "currentDayRain", "number"
        attribute "recordRainAmount", "number"
        attribute "recordRainDate", "string"
    }
}

def installed() {
    log.info "Advanced Rain Detector Information Device Installed"
}

def updated() {
    log.info "Advanced Rain Detector Information Device Updated"
}
