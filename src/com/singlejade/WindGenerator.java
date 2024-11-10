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

public class WindGenerator extends Agent {
    private double[] hourlyGeneration;
    private double totalGenerationUsed;

    public WindGenerator() {
        // No-argument constructor
    }

    @Override
    protected void setup() {
        System.out.println("WindGenerator " + getLocalName() + " initialized.");
        loadDataFromCSV("src/com/singlejade/energy_data.csv");
        totalGenerationUsed = 0;

        // Add behavior to respond to requests for wind generation and surplus values
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    String content = msg.getContent();

                    int hour = 0;
                    if (content.startsWith("getEnergyAtHour")) {
                        hour = Integer.parseInt(content.split(":")[1]);
                        double windValue = getGenerationAtHour(hour);
                        totalGenerationUsed += windValue;

                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(windValue));
                        System.out.println("Wind generation for hour " + hour + " is " + windValue + " kWh.");
                        send(reply);

                    } else if (content.equals("getSurplus")) {
                        double surplus = calculateSurplus(hour);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(String.valueOf(surplus));
                        System.out.println("Surplus energy available from wind: " + surplus + " kWh.");
                        send(reply);

                    } else {
                        System.out.println("Received unknown request: " + content);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void loadDataFromCSV(String fileName) {
        try (CSVReader reader = new CSVReader(new FileReader(fileName))) {
            List<String[]> data = reader.readAll();
            hourlyGeneration = new double[24];
            for (int i = 1; i < data.size(); i++) {  // Skip header row
                hourlyGeneration[i - 1] = Double.parseDouble(data.get(i)[3]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    public double getGenerationAtHour(int hour) {
        return hourlyGeneration[hour];
    }


    private double calculateSurplus(int currentHour) {
        double generatedEnergy = getGenerationAtHour(currentHour);
        double surplus = generatedEnergy - totalGenerationUsed;
        totalGenerationUsed = 0;  // Reset after calculating surplus to avoid compounding
        return Math.max(surplus, 0); // Ensure surplus is not negative
    }
}
