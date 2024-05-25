/*
 * /*******************************************************************************
 *  * Copyright (c) 2012  DCA-FEEC-UNICAMP
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the GNU Lesser Public License v3
 *  * which accompanies this vision_blueribution, and is available at
 *  * http://www.gnu.org/licenses/lgpl.html
 *  * 
 *  * Contributors:
 *  *     K. Raizer, A. L. O. Paraense, R. R. Gudwin - initial API and implementation
 *  ******************************************************************************/
 
package codelets.sensors;

import CommunicationInterface.SensorI;
import br.unicamp.cst.core.entities.MemoryObject;
import sensory.FeatMapCodelet;
//import codelets.motor.Lock;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
//import static java.lang.Math.abs;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class BU_FM_ColorBlue extends FeatMapCodelet {
    private final float mr = 255;                     //Max Value for VisionSensor
    private final int max_time_graph=100;
    private final int res = 256;                      //Resolution of VisionSensor
    private int time_graph;
    private final int slices = 16;                    //Slices in each coordinate (x & y) 
    private SensorI vision;
    
    //private float max_value = 0;
    public BU_FM_ColorBlue(SensorI vision, int nsensors, ArrayList<String> sens_names, String featmapname,int timeWin, int mapDim) {
        super(nsensors, sens_names, featmapname,timeWin,mapDim);
        this.time_graph = 0;
        this.vision = vision;
        
    }

    @Override
    public void calculateActivation() {
        
    }

    @Override
    public void proc() {
        try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
        MemoryObject vision_bufferMO = (MemoryObject) sensor_buffers.get(0);       //Gets Blue Data
        
        List visionData_buffer;
        visionData_buffer = (List) vision_bufferMO.getI();

        List vision_blueFM = (List) featureMap.getI();        
        
        if(vision_blueFM.size() == timeWindow){
            vision_blueFM.remove(0);
        }
        
        vision_blueFM.add(new ArrayList<>());
        
        int t = vision_blueFM.size()-1;

        ArrayList<Float> vision_blueFM_t = (ArrayList<Float>) vision_blueFM.get(t);
        
        for (int j = 0; j < mapDimension; j++) {
            vision_blueFM_t.add(new Float(0));
        }
        
        MemoryObject visionDataMO;
        
        if(visionData_buffer.size() < 1){
            return;
        }

        visionDataMO = (MemoryObject)visionData_buffer.get(visionData_buffer.size()-1);

        List visionData;

        visionData = (List) visionDataMO.getI();
        //ArrayList<Float> visionData_Array = (ArrayList<Float>) visionData.get(visionData.size()-1);   

        
        Float Fvalue;
        float MeanValue = 0;
        ArrayList<Float> vision_mean_blue = new ArrayList<>();
        ArrayList<Float> visionData_Array = new ArrayList<>();
        
        for (int j = 0; j < res*res; j++) {
            visionData_Array.add(new Float(0));
        }
        
        int pixel_len = 3;
        int count_3 = 0;
        //System.out.println("Vision data b size:"+visionData.size());
        for (int j = 0; j+pixel_len < visionData.size(); j+= pixel_len) {
           
            Fvalue = (Float) visionData.get(j+2);               //Gets JUST blue values for VisionData

            visionData_Array.set(count_3, Fvalue);        //Blue data
            count_3 += 1;
        }
        //System.out.println("Vision b size:"+visionData_Array.size());
        
        
        // get mean all elements
        float sum = 0;
        for (float value : visionData_Array) {
            sum += value;
        }
    
        float mean_all = sum / visionData_Array.size();
        
        //Converts res*res image to res/slices*res/slices sensors
        float new_res = (res/slices)*(res/slices);
        float new_res_1_2 = (res/slices);
        
        for(int n = 0;n<slices;n++){
            int ni = (int) (n*new_res_1_2);
            int no = (int) (new_res_1_2+n*new_res_1_2);
            for(int m = 0;m<slices;m++){    
                int mi = (int) (m*new_res_1_2);
                int mo = (int) (new_res_1_2+m*new_res_1_2);
                for (int y = ni; y < no; y++) {
                    for (int x = mi; x < mo; x++) {
                        Fvalue = visionData_Array.get(y*res+x);                         
                        MeanValue += Fvalue;
                        
                    }
                }
                float correct_mean = MeanValue/new_res - mean_all;
                //if(correct_mean>this.max_value) this.max_value = correct_mean;
                //System.out.println("max_value b: "+ max_value);
                if(correct_mean/mr>1) vision_mean_blue.add(new Float(1));
                else if(correct_mean/mr<0.001) vision_mean_blue.add(new Float(0));
                else vision_mean_blue.add(correct_mean/mr);   
                
                //vision_mean_blue.add(correct_mean/mr);
                MeanValue = 0;
                
            }
        }
        
        
        for (int j = 0; j < vision_mean_blue.size(); j++) {
           
            vision_blueFM_t.set(j, vision_mean_blue.get(j));
        }   
        
        printToFile(vision_blueFM_t);
    }
    private void printToFile(ArrayList<Float> arr){
        //if(time_graph%2 == 0 ){
        if(this.vision.getExp() == 1 || this.vision.getExp()%20 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now(); 
        try(FileWriter fw = new FileWriter("profile/vision_blue_FM.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            out.println(dtf.format(now)+"_"+this.vision.getExp()+"_"+time_graph+" "+ arr);
                //if(time_graph == max_time_graph-1) System.out.println("vision_blue_FM: "+time_graph);
            time_graph++;
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        }
       // }else time_graph++; 
    }
}
    

