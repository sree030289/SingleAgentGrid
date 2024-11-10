package com.singlejade;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jade.wrapper.AgentContainer;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BatteryGUI extends JFrame {
    private JTextField capacityTextField;
    private JTextField initialSOCTextField;
    private JButton startButton;
    private AgentContainer container;

    public BatteryGUI(AgentContainer container) {
        this.container = container;

        // GUI setup code
        setTitle("Battery Configuration");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        JLabel capacityLabel = new JLabel("Capacity (kWh):");
        capacityLabel.setBounds(10, 10, 100, 25);
        add(capacityLabel);

        capacityTextField = new JTextField();
        capacityTextField.setBounds(120, 10, 150, 25);
        add(capacityTextField);

        JLabel initialSOCLabel = new JLabel("Initial SOC (%):");
        initialSOCLabel.setBounds(10, 50, 100, 25);
        add(initialSOCLabel);

        initialSOCTextField = new JTextField();
        initialSOCTextField.setBounds(120, 50, 150, 25);
        add(initialSOCTextField);

        startButton = new JButton("Start Battery");
        startButton.setBounds(90, 100, 120, 25);
        add(startButton);

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startBatteryAgent();
            }
        });
    }

    private void startBatteryAgent() {
        try {
            double capacity = Double.parseDouble(capacityTextField.getText());
            double initialSOC = Double.parseDouble(initialSOCTextField.getText()) / 100.0; // Convert % to fraction

            // Create and start the BatteryAgent with initialization parameters
            Object[] batteryArgs = new Object[] { capacity, initialSOC };
            try {
                AgentController batteryAgent = container.createNewAgent("BatteryAgent", "com.singlejade.Battery", batteryArgs);
                batteryAgent.start();
                System.out.println("BatteryAgent created and started with capacity=" + capacity + " kWh and initial SOC=" + (initialSOC * 100) + "%");
            } catch (StaleProxyException ex) {
                ex.printStackTrace();
            }

            // Close the GUI after starting the agent
            this.dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid input. Please enter valid numbers.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
