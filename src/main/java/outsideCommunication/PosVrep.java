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
    private ArrayList<Float> pos_data;   
    private int stage, num_exp;    
    private final int res = 256;
    private final int max_time_graph=100;
    private boolean debug = true;
    
    public PosVrep(remoteApi vrep, int clientid, IntW obj_handle) {
        this.time_graph = 0;
        this.vrep = vrep;
        this.stage =3;
        this.num_exp = 1;
        this.obj_handle = obj_handle;
        clientID = clientid;
        pos_data = new ArrayList<Float>();

        // x, y, z, alpha, beta, gamma
        for (int j = 0; j < 6; j++){
            pos_data.add(0f);
        }
    }
    
    @Override
    public int getExp() {
            return this.num_exp;    
    }
    
    @Override
    public void setExp(int newExp) {
           this.num_exp = newExp;    
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

    @Override
    public Object getData() {
       try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        FloatWA position = new FloatWA(3);
	vrep.simxGetObjectPosition(clientID, obj_handle.getValue(), -1, position, vrep.simx_opmode_streaming);

        FloatWA orientation = new FloatWA(3);
	vrep.simxGetObjectOrientation(clientID, obj_handle.getValue(), -1, orientation, vrep.simx_opmode_streaming);
                
        if(debug) System.out.println("Object: "+obj_handle.getValue()+", x: "+position.getArray()[0]+", y: "+position.getArray()[1]+", z: "+position.getArray()[2]+", alpha: "+orientation.getArray()[0]+", betta: "+orientation.getArray()[1]+", gamma: "+orientation.getArray()[2]);
        
        
 	if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
            vrep.simxSynchronousTrigger(clientID);
        ArrayList<Float> position_array = new ArrayList<Float>();
        position_array.add(position.getArray()[0]);
        position_array.add(position.getArray()[1]);
        position_array.add(position.getArray()[2]);
        position_array.add(orientation.getArray()[0]);
        position_array.add(orientation.getArray()[1]);
        position_array.add(orientation.getArray()[2]);
        
        for (int j = 0; j < 6; j++){
            pos_data.set(j, position_array.get(j));
        }
        printToFile(position_array, String.valueOf(obj_handle.getValue()));
        //Idea position_idea = Idea.createIdea("position",pos_data,3);
        return pos_data;
        
    }
    
    private void printToFile(Object object, String file){
        //if(this.num_exp == 1 || this.num_exp%10 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();  
        try(FileWriter fw = new FileWriter("profile/position"+file+".txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw)){
            out.println(dtf.format(now)+"_"+time_graph+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(dtf.format(now)+"vision: "+time_graph);
            time_graph++;
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

	@Override
	public void resetData() {
		// TODO Auto-generated method stub
		
	}
    
}
