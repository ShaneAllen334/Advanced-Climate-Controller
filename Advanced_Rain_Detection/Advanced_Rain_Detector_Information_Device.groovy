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
        capability "WaterSensor" // Enables native Hubitat Safety Monitor (HSM) Wet/Dry integration
        
        // Core States
        attribute "weatherState", "string"
        attribute "rainProbability", "number"
        attribute "confidenceScore", "number"
        attribute "expectedClearTime", "string"
        
        // Evaporation & Drying
        attribute "dryingPotential", "string"
        attribute "timeToDry", "string"
        
        // Precipitation States
        attribute "sprinkling", "string"
        attribute "raining", "string"
        
        // Calculated Thermodynamics
        attribute "vpd", "number"
        attribute "wetBulb", "number"
        attribute "dewPoint", "number"
        attribute "dewPointSpread", "number"
        
        // Trends, Velocities & Advanced Metrics
        attribute "pressureTrend", "string"
        attribute "tempTrend", "string"
        attribute "spreadTrend", "string"
        attribute "luxTrend", "string"
        attribute "windTrend", "string"
        attribute "windDirection", "number"
        attribute "windShiftDetected", "string"
        
        // Lightning Metrics
        attribute "lightningStrikeCount", "number"
        attribute "lightningClosestDistance", "number"
        
        // Accumulation History
        attribute "currentDayRain", "number"
        attribute "recordRainAmount", "number"
        attribute "recordRainDate", "string"
    }
}

def installed() {
    log.info "Advanced Rain Detector Information Device Installed"
    sendEvent(name: "water", value: "dry")
}

def updated() {
    log.info "Advanced Rain Detector Information Device Updated"
}

// Intercept incoming events from the App to automatically manage the native WaterSensor capability
void sendEvent(Map properties) {
    // Automatically manage the HSM wet/dry state based on the engine's weatherState
    if (properties.name == "weatherState") {
        if (properties.value == "Raining" || properties.value == "Sprinkling") {
            if (device.currentValue("water") != "wet") {
                super.sendEvent([name: "water", value: "wet", descriptionText: "Precipitation active, reporting WET to HSM."])
            }
        } else if (properties.value == "Clear") {
            if (device.currentValue("water") != "dry") {
                super.sendEvent([name: "water", value: "dry", descriptionText: "Precipitation clear, reporting DRY to HSM."])
            }
        }
    }
    
    // Pass the standard event through
    super.sendEvent(properties)
}
