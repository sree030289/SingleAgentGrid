package com.singlejade;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Battery extends Agent {
    private double capacity;  // Maximum capacity in kWh
    private double currentCharge;  // Current charge in kWh

    @Override
    protected void setup() {
        System.out.println("Battery " + getLocalName() + " initialized.");

        // Get initial capacity and SOC from agent arguments
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.capacity = Double.parseDouble(args[0].toString());
            double initialSOC = Double.parseDouble(args[1].toString());
            this.currentCharge = capacity * (initialSOC / 100)*100;  // Initial SOC as a fraction of capacity
            System.out.println("Battery initialized with capacity: " + capacity + " kWh and initial SOC: " + initialSOC + "%");
        } else {
            System.out.println("Error: Missing battery parameters.");
            doDelete();
        }

        // Send an initialization confirmation to the CentralAgent
        sendInitializationConfirmation();

        // Add behavior logic for discharging, charging, and responding to SOC requests
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    String content = msg.getContent();
                    ACLMessage reply = msg.createReply();

                    if (content.startsWith("discharge")) {
                        String[] parts = content.split(":");
                        double requestAmount = Double.parseDouble(parts[2]);
                        double dischargedAmount = discharge(requestAmount);
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(dischargedAmount));
                        System.out.println("Battery discharged: " + dischargedAmount + " kWh. Current SOC: " + getSOC() + "%");
                    } else if (content.startsWith("charge")) {
                        String[] parts = content.split(":");
                        double chargeAmount = Double.parseDouble(parts[2]);
                        double chargedAmount = charge(chargeAmount);
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(chargedAmount));
                        System.out.println("Battery charged: " + chargedAmount + " kWh. Current SOC: " + getSOC() + "%");
                    } else if (content.equals("getSOC")) {
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(String.valueOf(getSOC()));
                        System.out.println("Sent SOC to CentralAgent: " + getSOC() + "%");
                    } else {
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                        reply.setContent("Invalid command");
                        System.out.println("Received invalid command: " + content);
                    }
                    send(reply);
                } else {
                    block();
                }
            }
        });
    }

    private void sendInitializationConfirmation() {
        ACLMessage initMsg = new ACLMessage(ACLMessage.INFORM);
        initMsg.addReceiver(new AID("CentralAgent", AID.ISLOCALNAME));
        initMsg.setContent("Battery ready");
        send(initMsg);
        System.out.println("Sent initialization confirmation to CentralAgent.");
    }

    public double getSOC() {
        return (currentCharge / capacity) * 100;  // Return state of charge as a percentage
    }

    public double discharge(double amount) {
        double dischargeAmount = Math.min(amount, currentCharge);
        currentCharge -= dischargeAmount;
        return dischargeAmount;
    }

    public double charge(double amount) {
        double chargeAmount = Math.min(amount, capacity - currentCharge);
        currentCharge += chargeAmount;
        return chargeAmount;
    }
}
