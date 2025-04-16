/*
 * /*******************************************************************************
 *  * Copyright (c) 2012  DCA-FEEC-UNICAMP
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the GNU Lesser Public License v3
 *  * which accompanies this distribution, and is available at
 *  * http://www.gnu.org/licenses/lgpl.html
 *  * 
 *  * Contributors:
 *  *     K. Raizer, A. L. O. Paraense, R. R. Gudwin - initial API and implementation
 *  ******************************************************************************/
 
package outsideCommunication;

import CommunicationInterface.SensorI;
import br.unicamp.cst.representation.idea.Idea;
//import coppelia.BoolW;
//import coppelia.FloatWA;
//import coppelia.FloatWAA;
import coppelia.CharWA;
import coppelia.FloatWA;
import coppelia.IntWA;

import coppelia.IntW;
import coppelia.remoteApi;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
 import java.util.List;   
import java.util.Collections; 

import java.io.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
//import java.util.Arrays;
import javax.imageio.ImageIO;   

import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class PosVrep implements SensorI{
    private final IntW obj_handle;
    private final remoteApi vrep;
    private final int clientID; 
    private  int time_graph;
    private ArrayList<FloatWA> pos_data0 = new ArrayList<>();
    private ArrayList<Float> pos_data;   
    private int stage, num_exp, step;    
    private final int res = 256;
    private final int max_time_graph=100;
    private boolean debug = false;
    private String name;
    private  OutsideCommunication oc;
    private ArrayList<Float> initialPosition = null;

    public PosVrep(OutsideCommunication oc,remoteApi vrep, int clientid, IntW obj_handle, String name) {
        this.time_graph = 0;
        this.vrep = vrep;
        this.stage =3;
        this.num_exp = 1;
        this.obj_handle = obj_handle;
        clientID = clientid;
        pos_data = new ArrayList<Float>();
        this.name = name;
        // x, y, z, alpha, beta, gamma
        for (int j = 0; j < 10; j++){
            pos_data.add(0f);
        }
        
        this.oc = oc;
        FloatWA position = new FloatWA(3);
	vrep.simxGetObjectPosition(clientID, obj_handle.getValue(), -1, position, vrep.simx_opmode_streaming);

        FloatWA orientation = new FloatWA(3);
	vrep.simxGetObjectOrientation(clientID, obj_handle.getValue(), -1, orientation, vrep.simx_opmode_streaming);
        
        FloatWA quaternion = new FloatWA(4);
	vrep.simxGetObjectQuaternion(clientID, obj_handle.getValue(), -1, quaternion, vrep.simx_opmode_streaming);
        System.out.println("Working directory: " + new File(".").getAbsolutePath());

        if (this.name.equals("NAO1")) {
            initialPosition = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader("./pos_kicker.txt"))) {
                String line = br.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    for (int i = 0; i < Math.min(parts.length, 3); i++) {
                        initialPosition.add(Float.parseFloat(parts[i]));
                    }
                }
                System.out.println(name + " initial position from file: " + initialPosition);
            } catch (IOException e) {
                System.err.println("Error reading position file for NAO1: " + e.getMessage());
            }
        }

        
    }
    
    @Override
    public int getExp() {
            return this.num_exp;    
    }
    
    @Override
    public void setExp(int newExp) {
        this.step = 0;
           this.num_exp = newExp;    
    }
    
  @Override
    public int getStep() {
            return this.step;    
    }
    
    @Override
    public void setStep(int newExp) {
           this.step = newExp;    
    }
    
    @Override
    public int getStage() {
            return this.stage;    
    }
    
    @Override
    public void setStage(int newstage) {
           this.stage = newstage;    
    }
    
    @Override
    
    public String getObjHandle() {
           obj_handle.getValue();
           return String.valueOf(obj_handle.getValue());
    }

    public ArrayList<FloatWA> getDataPos() {
       try {
            Thread.sleep(10);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
        FloatWA position = new FloatWA(3);
	vrep.simxGetObjectPosition(clientID, obj_handle.getValue(), -1, position, vrep.simx_opmode_buffer);

        FloatWA orientation = new FloatWA(3);
	vrep.simxGetObjectOrientation(clientID, obj_handle.getValue(), -1, orientation, vrep.simx_opmode_buffer);
                
        if(debug) System.out.println(name+" Object: "+obj_handle.getValue()+", x: "+position.getArray()[0]+", y: "+position.getArray()[1]+", z: "+position.getArray()[2]+", alpha: "+orientation.getArray()[0]+", betta: "+orientation.getArray()[1]+", gamma: "+orientation.getArray()[2]);
        
        
 	/*if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
            vrep.simxSynchronousTrigger(clientID);
      */
        ArrayList<FloatWA> getDataPos = new ArrayList<>();
        getDataPos.add(position);
        getDataPos.add(orientation);
        
        //Idea position_idea = Idea.createIdea("position",pos_data,3);
        return getDataPos;
        
    }
    
    /*public boolean check_end_table(List<Float> mostRecentInput){
        if(mostRecentInput.get(0)>13 || mostRecentInput.get(0)<-13 || mostRecentInput.get(1)>13 || mostRecentInput.get(1)<-13){
             System.out.print(" posVrep. check_end_table. mostRecentInput.get(0): "+
                     mostRecentInput.get(0)+"\nmostRecentInput.get(1): "+
                     mostRecentInput.get(1));
            try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
            oc.reset();
            
            try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
            return false;
        }
        
        return true;
    }*/
    
    private boolean returnedToStart(List<Float> currentPos) {
        if (initialPosition == null || currentPos.size() < 3) return false;

        float threshold = 0.1f; // acceptable distance error
        float dx = currentPos.get(0) - initialPosition.get(0);
        float dy = currentPos.get(1) - initialPosition.get(1);
        float dz = currentPos.get(2) - initialPosition.get(2);
        float distanceSquared = dx*dx + dy*dy + dz*dz;
        System.out.println("x: "+currentPos.get(0)+" "+initialPosition.get(0));
        System.out.println("x: "+currentPos.get(1)+" "+initialPosition.get(1));
        System.out.println("x: "+currentPos.get(2)+" "+initialPosition.get(2));
        System.out.println(name+" returnedToStart: "+distanceSquared);
        return distanceSquared < threshold * threshold;
    }


    @Override
    public Object getData() {
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        ArrayList<Float> position_array = new ArrayList<>();
        int op_mode = vrep.simx_opmode_buffer;

        if (this.name.equals("NAO1")) {
            // Read last NAO1 line from file
            try {
                List<String> allLines = Files.readAllLines(Paths.get("pos_kicker.txt"));
                for (int i = allLines.size() - 1; i >= 0; i--) {
                    String line = allLines.get(i);
                    if (line.contains("NAO1:") && line.contains("[") && line.contains("]")) {
                        int start = line.indexOf("[");
                        int end = line.indexOf("]");
                        String insideBrackets = line.substring(start + 1, end);
                        String[] parts = insideBrackets.split(",");

                        for (String part : parts) {
                            position_array.add(Float.parseFloat(part.trim()));
                        }
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Error reading NAO1 data from file: " + e.getMessage());
                for (int i = 0; i < 10; i++) position_array.add(0f); // fallback in case of error
            }
        } else {
            // Default V-REP-based position capture
            FloatWA position = new FloatWA(3);
            vrep.simxGetObjectPosition(clientID, obj_handle.getValue(), -1, position, op_mode);

            FloatWA orientation = new FloatWA(3);
            vrep.simxGetObjectOrientation(clientID, obj_handle.getValue(), -1, orientation, op_mode);

            FloatWA quaternion = new FloatWA(4);
            vrep.simxGetObjectQuaternion(clientID, obj_handle.getValue(), -1, quaternion, op_mode);

            position_array.add(position.getArray()[0]);
            position_array.add(position.getArray()[1]);
            position_array.add(position.getArray()[2]);
            position_array.add(orientation.getArray()[0]);
            position_array.add(orientation.getArray()[1]);
            position_array.add(orientation.getArray()[2]);
            position_array.add(quaternion.getArray()[0]);
            position_array.add(quaternion.getArray()[1]);
            position_array.add(quaternion.getArray()[2]);
            position_array.add(quaternion.getArray()[3]);
        }

        // Copy position_array into pos_data
        for (int j = 0; j < 10; j++) {
            pos_data.set(j, position_array.get(j));
        }

        // Special logic for Kicker to track if it returned to start
        if (this.name.equals("Kicker") && returnedToStart(position_array)) {
            this.setExp(this.num_exp + 1);
            oc.positionR.setExp(this.num_exp + 1);
            System.out.println("NAO returned to start. Moving to experiment #" + this.num_exp);
        }

        printToFile(position_array, this.name);
        return pos_data;
    }


    
    private void printToFile(Object object, String file){
        //if(this.num_exp == 1 || this.num_exp%10 == 0){
        file = file.replace("/", "");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();  
        try(FileWriter fw = new FileWriter("profile/position_"+file+".txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw)){
            out.println(dtf.format(now)+"_"+num_exp+"_"+step+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(dtf.format(now)+"vision: "+time_graph);
            time_graph++;
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

	
    
}
