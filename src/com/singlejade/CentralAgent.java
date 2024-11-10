package com.singlejade;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.TickerBehaviour;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;

public class CentralAgent extends Agent {
    private static final String CSV_FILE_PATH = "energy_log.csv";

    @Override
    protected void setup() {
        System.out.println("CentralAgent " + getLocalName() + " initialized.");
        initializeCSV();

        // Wait for BatteryAgent initialization confirmation
        waitForBatteryInitialization();

        // Add behaviour to process each hour
        addBehaviour(new TickerBehaviour(this, 1000) { // Runs every second for testing purposes
            private int hour = 0;

            @Override
            protected void onTick() {
                if (hour < 24) {
                    handleHour(hour);
                    hour++;
                } else {
                    System.out.println("Stopping CentralAgent after 24 hours.");
                    stop();
                    doDelete();
                }
            }
        });
    }

    private void waitForBatteryInitialization() {
        ACLMessage initResponse = blockingReceive(MessageTemplate.MatchContent("Battery ready"), 10000); // 10-second timeout
        if (initResponse != null) {
            System.out.println("BatteryAgent initialization confirmed.");
        } else {
            System.out.println("BatteryAgent initialization not confirmed within the timeout period. Check if BatteryAgent sent the message.");
        }
    }

    private void handleHour(int hour) {
        System.out.println("Processing hour " + hour);
        double load = requestLoad(hour);
        System.out.println("Load for hour " + hour + ": " + load + " kWh");
        double currentLoad = load;
        double solarGeneration = requestEnergyFromSource("SolarAgent", hour, Double.MAX_VALUE);
        double windGeneration = requestEnergyFromSource("WindAgent", hour, Double.MAX_VALUE);
        double solarUsed = 0, windUsed = 0, batteryUsed = 0, gridUsed = 0;
        double solarSurplus = 0, windSurplus = 0;
        double batterySOC = 0;
        double surplusToGrid = 0;

        // Step 1: Use solar energy to meet the load
        if (solarGeneration >= currentLoad) {
            solarUsed = currentLoad;
            solarSurplus = solarGeneration - currentLoad;
            currentLoad = 0;
        } else {
            solarUsed = solarGeneration;
            currentLoad -= solarGeneration;
            solarSurplus = 0;
        }
        System.out.println("Solar generation used for hour " + hour + ": " + solarUsed + " kWh");
        System.out.println("Calculated solar surplus for hour " + hour + ": " + solarSurplus + " kWh");

        // Step 2: Use wind energy if load is not fully met
        if (currentLoad > 0) {
            if (windGeneration >= currentLoad) {
                windUsed = currentLoad;
                windSurplus = windGeneration - currentLoad;
                currentLoad = 0;
            } else {
                windUsed = windGeneration;
                currentLoad -= windGeneration;
                windSurplus = 0;
            }
            System.out.println("Wind generation used for hour " + hour + ": " + windUsed + " kWh");
            System.out.println("Calculated wind surplus for hour " + hour + ": " + windSurplus + " kWh");
        }

        // Step 3: Use battery energy if load is not fully met
        if (currentLoad > 0) {
            batteryUsed = requestEnergyFromBattery(hour, currentLoad);
            System.out.println("Battery used for hour " + hour + ": " + batteryUsed + " kWh");
            currentLoad -= batteryUsed;
        }

        // Step 4: Use grid energy for any remaining unmet load
        if (currentLoad > 0) {
            gridUsed = requestEnergyFromGrid(currentLoad);
            System.out.println("Grid used for hour " + hour + ": " + gridUsed + " kWh");
        }

        // Step 5: Handle and send surplus energy to the grid if any
        surplusToGrid = handleSurplusEnergy(solarSurplus, windSurplus);
        System.out.println("Surplus energy sent to grid for hour " + hour + ": " + surplusToGrid + " kWh");

        // Log results to CSV
        batterySOC = getBatterySOC();
        System.out.println("Battery SOC after hour " + hour + ": " + batterySOC + "%");
        logToCSV(hour, load, solarGeneration, windGeneration, batteryUsed, gridUsed, batterySOC, surplusToGrid);
    }

    private double requestLoad(int hour) {
        ACLMessage loadRequest = new ACLMessage(ACLMessage.REQUEST);
        loadRequest.addReceiver(getAID("LoadAgent"));
        loadRequest.setContent("getLoad:" + hour);
        send(loadRequest);
        System.out.println("Sending load request for hour " + hour);

        ACLMessage loadResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        if (loadResponse != null) {
            System.out.println("Received load response: " + loadResponse.getContent());
            return Double.parseDouble(loadResponse.getContent());
        } else {
            System.out.println("No response received for load request");
            return 0;
        }
    }

    private double requestEnergyFromSource(String agentName, int hour, double requiredEnergy) {
        ACLMessage energyRequest = new ACLMessage(ACLMessage.REQUEST);
        energyRequest.addReceiver(getAID(agentName));
        energyRequest.setContent("getEnergyAtHour:" + hour);
        send(energyRequest);
        System.out.println("Sending energy request to " + agentName + " for hour " + hour);

        ACLMessage energyResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
        if (energyResponse != null) {
            System.out.println("Received energy response from " + agentName + ": " + energyResponse.getContent());
            return Double.parseDouble(energyResponse.getContent());
        } else {
            System.out.println("No response received from " + agentName);
            return 0;
        }
    }

    private double requestEnergyFromBattery(int hour, double requiredEnergy) {
        ACLMessage batteryRequest = new ACLMessage(ACLMessage.REQUEST);
        batteryRequest.addReceiver(getAID("BatteryAgent"));
        batteryRequest.setContent("discharge:" + hour + ":" + requiredEnergy);
        send(batteryRequest);
        System.out.println("Sending battery discharge request for hour " + hour);

        ACLMessage batteryResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
        if (batteryResponse != null) {
            double dischargedAmount = Double.parseDouble(batteryResponse.getContent());
            double newSOC = getBatterySOC();

            // Adjust discharge to prevent SOC from dropping below 20%
            if (newSOC < 20) {
                double maxAllowedDischarge = getBatteryCapacity() * (newSOC / 100) - (getBatteryCapacity() * 0.2);
                dischargedAmount = Math.max(0, maxAllowedDischarge);
                System.out.println("Limiting discharge to maintain minimum SOC of 20%. Discharge adjusted to: " + dischargedAmount + " kWh.");
            }

            return dischargedAmount;
        } else {
            System.out.println("No response received for battery discharge request");
            return 0;
        }
    }


    private double handleSurplusEnergy(double solarSurplus, double windSurplus) {
        double totalSurplus = solarSurplus + windSurplus;
        double batterySOC = getBatterySOC();

        if (totalSurplus > 0) {
            if (batterySOC >= 90) {
                System.out.println("Battery SOC is above 90%. Limiting charging and sending remaining surplus to the grid.");
                return totalSurplus;
            } else {
                double spaceAvailable = getBatteryCapacity() * 0.9 - (batterySOC / 100 * getBatteryCapacity());
                double chargeAmount = Math.min(totalSurplus, spaceAvailable);

                ACLMessage batteryChargeRequest = new ACLMessage(ACLMessage.REQUEST);
                batteryChargeRequest.addReceiver(getAID("BatteryAgent"));
                batteryChargeRequest.setContent("charge:0:" + chargeAmount);
                send(batteryChargeRequest);
                System.out.println("Sending surplus energy storage request to BatteryAgent");

                ACLMessage batteryChargeResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                if (batteryChargeResponse != null) {
                    double batteryCharge = Double.parseDouble(batteryChargeResponse.getContent());
                    System.out.println("Received battery charge response: " + batteryCharge);

                    if (batterySOC + (batteryCharge / getBatteryCapacity()) * 100 > 90) {
                        double excessCharge = (batterySOC + (batteryCharge / getBatteryCapacity()) * 100) - 90;
                        System.out.println("Limiting charge to prevent SOC from exceeding 90%. Excess of " + excessCharge + " kWh sent to the grid.");
                        return totalSurplus - batteryCharge + excessCharge;
                    }

                    return totalSurplus - batteryCharge;
                } else {
                    System.out.println("No response received for battery charge request");
                    return totalSurplus;
                }
            }
        }
        return 0;
    }


    private double requestEnergyFromGrid(double remainingLoad) {
        ACLMessage gridRequest = new ACLMessage(ACLMessage.REQUEST);
        gridRequest.addReceiver(getAID("GridAgent"));
        gridRequest.setContent("supply:" + remainingLoad);
        send(gridRequest);
        System.out.println("Sending grid supply request");

        ACLMessage gridResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.CONFIRM));
        if (gridResponse != null) {
            System.out.println("Received grid supply response: " + gridResponse.getContent());
            return Double.parseDouble(gridResponse.getContent());
        } else {
            System.out.println("No response received for grid supply request");
            return 0;
        }
    }

    private double getBatterySOC() {
        ACLMessage socRequest = new ACLMessage(ACLMessage.REQUEST);
        socRequest.addReceiver(getAID("BatteryAgent"));
        socRequest.setContent("getSOC");
        send(socRequest);
        System.out.println("Requesting SOC from BatteryAgent");

        ACLMessage socResponse = blockingReceive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        if (socResponse != null) {
            System.out.println("Received SOC response: " + socResponse.getContent());
            return Double.parseDouble(socResponse.getContent());
        } else {
            System.out.println("No response received for SOC request");
            return 0;
        }
    }

    private double getBatteryCapacity() {
        // This method should retrieve the battery's capacity, assuming it's static or retrieved once.
        // You may replace this logic if needed to dynamically query the battery capacity.
        return 1000.0; // Example capacity, replace with actual logic.
    }

    private void initializeCSV() {
        try (CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_PATH))) {
            String[] header = {"Hour", "Load", "SolarGen", "WindGen", "BatteryUsed", "GridUsed", "BatterySOC", "SurplusToGrid"};
            writer.writeNext(header);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logToCSV(int hour, double load, double solarGen, double windGen, double batteryUsed, double gridUsed, double batterySOC, double surplusToGrid) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_PATH, true))) {
            String[] data = {
                    String.valueOf(hour),
                    String.valueOf(load),
                    String.valueOf(solarGen),
                    String.valueOf(windGen),
                    String.valueOf(batteryUsed),
                    String.valueOf(gridUsed),
                    String.valueOf(batterySOC),
                    String.valueOf(surplusToGrid)
            };
            writer.writeNext(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
