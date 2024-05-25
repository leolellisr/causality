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
//import coppelia.BoolW;
import coppelia.FloatWA;
//import coppelia.FloatWAA;
//import coppelia.CharWA;
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

//import java.io.*;
//import java.awt.image.BufferedImage;
//import java.util.Arrays;
//import javax.imageio.ImageIO;   

import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class DepthVrep implements SensorI{
    private final IntW vision_handles;
    private remoteApi vrep;
    private final int clientID; 
    private  int time_graph;
    private List<Float> depth_data;    
    private int stage;    
    private final int res = 256;
    private final int max_time_graph=100;
    private SensorI vision;
    private boolean debug = false;
    public DepthVrep(remoteApi vrep, int clientid, IntW vision_handles, int stageVision, SensorI vision) {
        this.time_graph = 0;
        depth_data = Collections.synchronizedList(new ArrayList<>(res*res));
        this.vrep = vrep;
        this.stage = stageVision;
        this.vision = vision;
        this.vision_handles = vision_handles;
        clientID = clientid;

        for (int i = 0; i < res*res; i++) {
            depth_data.add(0f);
        }    
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
    public Object getData() {
       try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        //CharWA image_RGB = new CharWA(res*res*3);           //CharWA that returns RGB data of Vision Sensor
        IntWA resolution = new IntWA(2);            //Array to get resolution of Vision Sensor
        FloatWA auxValues_WA = new FloatWA(res*res);
        //FloatWA auxValues_WA[];
        float temp_dep[];
        
        FloatWA position = new FloatWA(3);
	vrep.simxGetObjectPosition(clientID, vision_handles.getValue(), -1, position,
        vrep.simx_opmode_streaming);
		
	printToFile(position.getArray()[2], "positions.txt");
        if(debug) System.out.println("Marta on exp "+this.vision.getExp()+" with z = "+position.getArray()[2]);        
        if (this.vision.getExp() > 1 && position.getArray()[2] < 0.3) {
                            vrep.simxStopSimulation(clientID, vrep.simx_opmode_oneshot);
                            System.out.println("Marta crashed on exp "+this.vision.getExp()+" with z = "+position.getArray()[2]);
                            System.exit(0);
                            
        }
        
        long startTime = System.currentTimeMillis();
        int read_depth = vrep.simxGetVisionSensorDepthBuffer(clientID, vision_handles.getValue(), resolution, auxValues_WA, vrep.simx_opmode_streaming);     
        
        //int ret_RGB = vrep.simxGetVisionSensorImage(clientID, vision_handles.getValue(), resolution, image_RGB, 0, vrep.simx_opmode_streaming); 
        while (System.currentTimeMillis()-startTime < 2000)
        {
            //ret_RGB = vrep.simxGetVisionSensorImage(clientID, vision_handles.getValue(), resolution, image_RGB, 0, remoteApi.simx_opmode_buffer);
            read_depth = vrep.simxGetVisionSensorDepthBuffer(clientID, vision_handles.getValue(), resolution, auxValues_WA, remoteApi.simx_opmode_buffer );
        
            if (read_depth== remoteApi.simx_return_ok  || read_depth == remoteApi.simx_return_novalue_flag){
                int count_aux = 0; 
                temp_dep = auxValues_WA.getArray();
                float[] depth_or = new float[res*res];

                for(int y =0; y < res; y++){  
                    for(int x =0; x < res; x++){  
                        float depth_c= temp_dep[y*res+x];
                        if(depth_c*10>10) depth_or[count_aux]=10;
                        else if(depth_c*10<0.0009999) depth_or[count_aux]=0;
                        else depth_or[count_aux]=depth_c*10;
                        count_aux += 1;
                    }
                }
                if(stage==3){
                    for(int i =0; i < res*res; i++){
                        depth_data.set(i, depth_or[i]);
                        //System.out.println("Stage 3 - i: "+i+" r: "+ (float)pixels_red[i] + " g: "+ (float)pixels_green[i] +" b: "+ (float)pixels_blue[i]);

                    }
                }
                if(stage==2){
                    float MeanValue = 0;
                    
                    for(int n = 0;n<res/2;n++){
                        int ni = (int) (n*2);
                        int no = (int) (2+n*2);
                        for(int m = 0;m<res/2;m++){    
                            int mi = (int) (m*2);
                            int mo = (int) (2+m*2);
                            for (int y = ni; y < no; y++) {
                                for (int x = mi; x < mo; x++) {
                                    float Fvalue_r = depth_or[y*res+x];                         
                                    MeanValue += Fvalue_r;

                                }
                            }
                            float correct_mean = MeanValue/4;
                           
                            for (int y = ni; y < no; y++) {
                                for (int x = mi; x < mo; x++) {
                                    depth_data.set(y*res+x, correct_mean);
                                    
                                }
                            }
                //System.out.println("Stage 2 - Mean_r: "+ correct_mean_r + " Mean_g: "+ correct_mean_g +" Mean_b: "+ correct_mean_b +" ni: "+ni+" no: "+no+" mi: "+mi+" mo: "+mo);
                            MeanValue = 0;
                    }
                }       
                
                }                        
                if(stage==1){
                    float MeanValue = 0;
                    for(int n = 0;n<res/4;n++){
                        int ni = (int) (n*4);
                        int no = (int) (4+n*4);
                        for(int m = 0;m<res/4;m++){    
                            int mi = (int) (m*4);
                            int mo = (int) (4+m*4);
                            for (int y = ni; y < no; y++) {
                                for (int x = mi; x < mo; x++) {
                                    float Fvalue = depth_or[y*res+x];                         
                                    MeanValue += Fvalue;
                                    
                                }
                            }
                            float correct_mean = MeanValue/16;
                         
                            for (int y = ni; y < no; y++) {
                                for (int x = mi; x < mo; x++) {
                                    depth_data.set(y*res+x, correct_mean);
                                   
                                }
                            }
                //System.out.println("Stage 1 - Mean_r: "+ correct_mean_r + " Mean_g: "+ correct_mean_g +" Mean_b: "+ correct_mean_b +" ni: "+ni+" no: "+no+" mi: "+mi+" mo: "+mo);
                            MeanValue = 0;
                          
                    }
                }       
                
                }      
            } else{
                int count_aux = 0; 
                for(int y =0; y < res; y++){  
                    for(int x =0; x < res; x++){  
                        depth_data.set(count_aux, new Float(0));
                       
                        count_aux += 1;
                    }
                }
            }
       }
        
        // SYNC
 	if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
            vrep.simxSynchronousTrigger(clientID);
        printToFile(depth_data, "depth.txt");        
        //System.out.println(depth_data);
        return  depth_data;
    }
    
    private void printToFile(Object object, String filename){
        if(this.vision.getExp() == 1 || this.vision.getExp()%5 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();  
        
        try(FileWriter fwd = new FileWriter("profile/"+filename, true);
            BufferedWriter bwd = new BufferedWriter(fwd);
            PrintWriter outd = new PrintWriter(bwd)){
            outd.println(dtf.format(now)+"_"+this.vision.getExp()+"_"+time_graph+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(dtf.format(now)+"vision: "+time_graph);
            time_graph++;
            outd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        }
    }

	@Override
	public void resetData() {
		// TODO Auto-generated method stub
		
	}

    @Override
    public void setExp(int exp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getExp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
