package com.singlejade;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Grid extends Agent {

    public Grid() {
        // No-argument constructor
    }

    @Override
    protected void setup() {
        System.out.println("GridAgent " + getLocalName() + " initialized.");

        // Add behavior to respond to power supply requests and handle surplus energy
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    String content = msg.getContent();
                    ACLMessage reply = msg.createReply();

                    if (content.startsWith("supply")) {
                        // Parse the requested power amount from the message
                        double requestAmount = Double.parseDouble(content.split(":")[1]);
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent(String.valueOf(requestAmount));  // Grid can always supply the requested amount
                        System.out.println("Grid supplying " + requestAmount + " kWh.");
                    } else if (content.startsWith("absorbSurplus")) {
                        // Parse the surplus amount from the message
                        double surplusAmount = Double.parseDouble(content.split(":")[1]);
                        absorbSurplus(surplusAmount);
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Surplus of " + surplusAmount + " kWh absorbed.");
                        System.out.println("Grid absorbed surplus of " + surplusAmount + " kWh.");
                    }

                    send(reply);
                } else {
                    block();  // Block until a message is received
                }
            }
        });
    }

    // Method to simulate grid absorbing surplus energy
    public void absorbSurplus(double amount) {
        // In a real-world application, this could be extended to log data or trigger other processes
        System.out.println("Grid absorbing surplus energy: " + amount + " kWh.");
    }
}
