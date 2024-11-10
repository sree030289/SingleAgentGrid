package com.singlejade;

import jade.core.AID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

public class Main {
    public static void main(String[] args) {
        // Initialize JADE runtime
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        AgentContainer container = runtime.createMainContainer(profile);

        try {
            // Start the RMA agent for monitoring
            AgentController rma = container.createNewAgent("rma", "jade.tools.rma.rma", null);
            rma.start();

            // Start the Sniffer agent
            AgentController sniffer = container.createNewAgent("sniffer", "jade.tools.sniffer.Sniffer", null);
            sniffer.start();

            // Start the GUI for battery configuration (this will start BatteryAgent)
            BatteryGUI batteryGUI = new BatteryGUI(container);
            batteryGUI.setVisible(true);

            // Start other agents (LoadAgent, SolarGenerator, WindAgent, GridAgent, CentralAgent)
            container.createNewAgent("LoadAgent", "com.singlejade.Load", null).start();
            container.createNewAgent("SolarAgent", "com.singlejade.SolarGenerator", null).start();
            container.createNewAgent("WindAgent", "com.singlejade.WindGenerator", null).start();
            container.createNewAgent("GridAgent", "com.singlejade.Grid", null).start();
            container.createNewAgent("CentralAgent", "com.singlejade.CentralAgent", null).start();

            // Set agents to be sniffed (optional, if you want to automate the selection)
            String agentsToSniff = "LoadAgent;SolarAgent;WindAgent;GridAgent;CentralAgent";
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(container.getAgent("sniffer").getName(), AID.ISLOCALNAME));
            msg.setContent(agentsToSniff);
            container.getAgent("sniffer").putO2AObject(msg, true);

        } catch (StaleProxyException e) {
            e.printStackTrace();
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }
    }
}
