/**
 * Advanced Severe Weather Component Device
 */
metadata {
    definition(name: "Advanced Severe Weather Device", namespace: "ShaneAllen", author: "ShaneAllen", component: true) {
        capability "Sensor"
        capability "Actuator"
        
        // Global States
        attribute "globalThreatState", "string"
        attribute "pressureTrend", "string"
        attribute "tempTrend", "string"
        attribute "windTrend", "string"
        attribute "currentDayRain", "number"

        // NOAA Forecast
        attribute "noaaForecastText", "string"
        attribute "noaaForecastTile", "string"

        // Tornado
        attribute "tornadoThreat", "number"
        attribute "tornadoProb", "number"
        attribute "tornadoConf", "number"
        attribute "tornadoState", "string"
        attribute "tornadoTile", "string"

        // Thunderstorm
        attribute "thunderstormThreat", "number"
        attribute "thunderstormProb", "number"
        attribute "thunderstormConf", "number"
        attribute "thunderstormState", "string"
        attribute "thunderstormTile", "string"

        // Flood
        attribute "floodThreat", "number"
        attribute "floodProb", "number"
        attribute "floodConf", "number"
        attribute "floodState", "string"
        attribute "floodTile", "string"

        // Freeze
        attribute "freezeThreat", "number"
        attribute "freezeProb", "number"
        attribute "freezeConf", "number"
        attribute "freezeState", "string"
        attribute "freezeTile", "string"

        // Severe Heat
        attribute "severeheatThreat", "number"
        attribute "severeheatProb", "number"
        attribute "severeheatConf", "number"
        attribute "severeheatState", "string"
        attribute "severeheatTile", "string"

        // Tropical
        attribute "tropicalThreat", "number"
        attribute "tropicalProb", "number"
        attribute "tropicalConf", "number"
        attribute "tropicalState", "string"
        attribute "tropicalTile", "string"
    }
}

def installed() {
    log.info "Severe Weather Child Device Installed"
}

def updated() {
    log.info "Severe Weather Child Device Updated"
}
