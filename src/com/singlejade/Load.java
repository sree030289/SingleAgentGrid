package com.singlejade;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class Load extends Agent {
    private double[] hourlyLoad;

    public Load() {
        // No-argument constructor
    }

    @Override
    protected void setup() {
        System.out.println("LoadAgent " + getLocalName() + " initialized.");
        loadDataFromCSV("src/com/singlejade/energy_data.csv");

        // Add behavior to respond to requests for load values
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null && msg.getContent().startsWith("getLoad")) {
                    int hour = Integer.parseInt(msg.getContent().split(":")[1]);
                    double loadValue = getLoadAtHour(hour);

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(String.valueOf(loadValue));
                    System.out.println("Load for hour " + hour + " is " + loadValue + " kWh.");
                    send(reply);
                } else {
                    block();
                }
            }
        });
    }

    private void loadDataFromCSV(String fileName) {
        try (CSVReader reader = new CSVReader(new FileReader(fileName))) {
            List<String[]> data = reader.readAll();
            hourlyLoad = new double[24];
            for (int i = 1; i < data.size(); i++) {  // Skip header row
                hourlyLoad[i - 1] = Double.parseDouble(data.get(i)[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    public double getLoadAtHour(int hour) {
        return hourlyLoad[hour];
    }
}
