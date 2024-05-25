/*
 * /*******************************************************************************
 *  * Copyright (c) 2012  DCA-FEEC-UNICAMP
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the GNU Lesser Public License v3
 *  * which accompanies this vision_greenribution, and is available at
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
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class Sensor_ColorGreen extends FeatMapCodelet {
    private final float mr = 255;             //Max. Value of RGB Vision Sensor
    private final int max_time_graph=100;
    private SensorI vision;
    private int num_exp;
    private  int time_graph;

    public Sensor_ColorGreen(SensorI vision, int nsensors, ArrayList<String> sens_names, String featmapname,int timeWin, int mapDim) {
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
        
        MemoryObject vision_bufferMO = (MemoryObject) sensor_buffers.get(0);    //Getting VisionSensor Data
        
        List visionData_buffer;
        
        visionData_buffer = (List) vision_bufferMO.getI();
                
        List vision_greenFM = (List) featureMap.getI();
        
        if(vision_greenFM.size() == timeWindow){
            vision_greenFM.remove(0);
        }
        
        vision_greenFM.add(new ArrayList<>());
        
        int t = vision_greenFM.size()-1;

        ArrayList<Float> vision_greenFM_t = (ArrayList<Float>) vision_greenFM.get(t);
        
        for (int j = 0; j < mapDimension; j++) {
            vision_greenFM_t.add(new Float(0));
        }
        
        MemoryObject visionDataMO;
        
        if(visionData_buffer.size() < 1){
            return;
        }

        visionDataMO = (MemoryObject)visionData_buffer.get(visionData_buffer.size()-1);

        List visionData;

        visionData = (List) visionDataMO.getI();

        double mean_function_value = 0;
        
        Float Fvalue;
            
        //Paper notation CONFUSIOOOOON!!!!
        int pixel_len = 3;
        for (int k = 0; k+pixel_len < visionData.size(); k += pixel_len) {
            Fvalue = (Float) visionData.get(k+1);               //Gets JUST green values for VisionData
            mean_function_value += Fvalue.doubleValue();
        }
        
        mean_function_value /= visionData.size()/3;
        int count_3 = 0;
        for (int j = 0; j+pixel_len < visionData.size(); j+= pixel_len) {
            //double function_value;
           
            Fvalue = (Float) visionData.get(j+1);               //Gets JUST green values for VisionData
            //function_value = abs((Fvalue.doubleValue() - mean_function_value)) / mr;

            vision_greenFM_t.set(count_3, Fvalue);       //Green data
            count_3 += 1;
        }   
        
        printToFile(vision_greenFM_t);
    }
    private void printToFile(ArrayList<Float> arr){
        if(this.vision.getExp() == 1 || this.vision.getExp()%20 == 0){
        //if(time_graph < max_time_graph){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();  
        try(FileWriter fw = new FileWriter("profile/vision_green.txt", true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
        {
            out.println(dtf.format(now)+"_"+this.vision.getExp()+"_"+time_graph+" "+ arr);
            //if(time_graph == max_time_graph-1) System.out.println("vision_green: "+time_graph);
            time_graph++;
            out.close();
        } catch (IOException e) {
                e.printStackTrace();
        }
        } 
    }
    }

    

