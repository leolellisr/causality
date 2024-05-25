/*
 * /*******************************************************************************
 *  * Copyright (c) 2012  DCA-FEEC-UNICAMP
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the GNU Lesser Public License v3
 *  * which accompanies this depthribution, and is available at
 *  * http://www.gnu.org/licenses/lgpl.html
 *  * 
 *  * Contributors:
 *  *     K. Raizer, A. L. O. Paraense, R. R. Gudwin - initial API and implementation
 *  ******************************************************************************/
 
package codelets.sensors;

import CommunicationInterface.SensorI;
import attention.Winner;
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
import java.util.Arrays;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class TD_FM_Depth extends FeatMapCodelet {
    private float mr = 10;                     //Max Value for VisionSensor
    private final int max_time_graph=100;
    private final int res = 256;                     //Resolution of VisionSensor
    private  int time_graph;
    private final int slices = 16;                    //Slices in each coordinate (x & y) 
    private int stage;
    private List<Integer> region_goal;
    private List regionTD;
    private Float depth_goal;
    private SensorI vision;
    //private MemoryObject regionMO;
  
    public TD_FM_Depth(SensorI vision, int nsensors, ArrayList<String> sens_names, String featmapname,int timeWin, int mapDim) {
        super(nsensors, sens_names, featmapname,timeWin,mapDim);
        this.time_graph = 0;
        this.depth_goal = 10f;
        this.region_goal = new ArrayList<>(2);
        this.vision = vision;
        this.stage = this.vision.getStage();
        //this.regionMO = (MemoryObject) this.getOutput("REGION_TOP_FM");
    }
    
    public Float getDepthGoal(){
        return this.depth_goal;
    }
    
    public void setDepthGoal(float new_depth_goal){
        this.depth_goal = new_depth_goal;
    }
    
    public List<Integer> getRegionGoal(){
        return this.region_goal;
    }
    
    public void setRegionGoal(List<Integer> new_region_goal){
        this.region_goal = new_region_goal;
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
        
        this.stage = vision.getStage();
        
        if(region_goal.isEmpty()){
                                        // Region starts in 0, 0
            region_goal.add(8);
            region_goal.add(8);
            
        }
            
        //System.out.println("sensor_buffers size:"+ sensor_buffers.size());
        MemoryObject depth_bufferMO = (MemoryObject) sensor_buffers.get(1);        //Gets Data
        
        List depthData_buffer;
        depthData_buffer = (List) depth_bufferMO.getI();

        List depthFM = (List) featureMap.getI();        
        regionTD = (List) regionMO.getI();
        

         //List winnerList = (List) winners.getI();
        //List winnersTypeList = (List) winnersType.getI();
        /*if(winnerList.size()>0){
            Winner lastWinner = (Winner) winnerList.get(winnerList.size()-1);
            System.out.println("FM depth last winner: "+lastWinner.featureJ);
            if(lastWinner.featureJ < 64) setDepthGoal(0);
            else if(lastWinner.featureJ >= 63 && lastWinner.featureJ < 127) setDepthGoal(4);
            else if(lastWinner.featureJ >= 127 && lastWinner.featureJ < 191) setDepthGoal(7);
            else if(lastWinner.featureJ >= 191 && lastWinner.featureJ < 256) setDepthGoal(10);
           
            for(int n = 0;n<slices;n++){
                for(int m = 0;m<slices;m++){
                    if(n*slices+m == lastWinner.featureJ) {
                        ArrayList<Integer> regionGoal  = new ArrayList<>(Arrays.asList(m, n));
                         System.out.println("FM depth last region: "+(n*slices+m));
                        setRegionGoal(regionGoal);
                    }
                }   
            }
        }*/
        if(depthFM.size() == timeWindow){
            depthFM.remove(0);
        }
        if(regionTD.size() == timeWindow){
            regionTD.remove(0);
        }
        depthFM.add(new ArrayList<>());
        regionTD.add(new ArrayList<>());
        int t = depthFM.size()-1;
        
        
        ArrayList<Float> depthFM_t = (ArrayList<Float>) depthFM.get(t);
        ArrayList<Float> regionTD_t = (ArrayList<Float>) regionTD.get(t);
        
        for (int j = 0; j < mapDimension; j++) {
            depthFM_t.add(new Float(0));
            regionTD_t.add(new Float(0));
        }
        if(this.stage==3){        
            MemoryObject depthDataMO;
            //System.out.println("depthData_buffer size before return:"+ depthData_buffer.size());
            if(depthData_buffer.size() < 1){
                return;
            }
            //System.out.println("depthData_buffer size after return:"+ depthData_buffer.size());        
            depthDataMO = (MemoryObject)depthData_buffer.get(depthData_buffer.size()-1);

            List depthData;

            depthData = (List) depthDataMO.getI();
            //System.out.println("depthData len: "+depthData.size());         

            Float Fvalue;
            float MeanValue = 0;
            ArrayList<Float> depth_mean = new ArrayList<>();
            ArrayList<Float> region_array = new ArrayList<>();

             //Converts res*res image to res/slices*res/slices sensors
            float new_res = (res/slices)*(res/slices);
            float new_res_1_2 = (res/slices);
            int count_mean = 0;
            for(int n = 0;n<slices;n++){
                int ni = (int) (n*new_res_1_2);
                int no = (int) (new_res_1_2+n*new_res_1_2);
                for(int m = 0;m<slices;m++){    
                    int mi = (int) (m*new_res_1_2);
                    int mo = (int) (new_res_1_2+m*new_res_1_2);
                    for (int y = ni; y < no; y++) {
                        for (int x = mi; x < mo; x++) {
                            Fvalue = (Float) depthData.get(y*res+x); 
                            if(Fvalue != 0 && Fvalue != 10 ) Fvalue = mr - Fvalue;
                            else if(Fvalue == 10 ) Fvalue = 0f;
                            
                            MeanValue += Fvalue;
                            count_mean++;
                        }
                    }
                    float correct_mean = MeanValue/new_res;
                  //System.out.println("Mean TD: "+ correct_mean +" Count_mean: "+count_mean+" ni: "+ni+" no: "+no+" mi: "+mi+" mo: "+mo);
                    if(correct_mean==0) depth_mean.add(new Float(0));
                    else if(Math.abs(correct_mean-depth_goal)/depth_goal<0.2) depth_mean.add(new Float(1));
                    else if(Math.abs(correct_mean-depth_goal)/depth_goal<0.4) depth_mean.add(new Float(0.75));
                    else if(Math.abs(correct_mean-depth_goal)/depth_goal<0.6) depth_mean.add(new Float(0.5));
                    else if(Math.abs(correct_mean-depth_goal)/depth_goal<0.8) depth_mean.add(new Float(0.25));
                    else if(Math.abs(correct_mean-depth_goal)/depth_goal<=1) depth_mean.add(new Float(0));
                    else depth_mean.add(new Float(0));     
                    
                    if(n == this.region_goal.get(0) && m == this.region_goal.get(1)) region_array.add(new Float(1));
                    else if(Math.abs(n-this.region_goal.get(0))<2 && Math.abs(m-this.region_goal.get(1))<2) region_array.add(new Float(0.8));
                    else if(Math.abs(n-this.region_goal.get(0))<4 && Math.abs(m-this.region_goal.get(1))<4) region_array.add(new Float(0.6));
                    else if(Math.abs(n-this.region_goal.get(0))<6 && Math.abs(m-this.region_goal.get(1))<6) region_array.add(new Float(0.4));                    
                    else if(Math.abs(n-this.region_goal.get(0))<8 && Math.abs(m-this.region_goal.get(1))<8) region_array.add(new Float(0.2));
                    else region_array.add(new Float(0));
                            
                    //region_array.add(correct_mean/mr);
                    MeanValue = 0;
                    count_mean=0;
                }
            }


            for (int j = 0; j < depth_mean.size(); j++) {
                regionTD_t.set(j, region_array.get(j));
                depthFM_t.set(j, depth_mean.get(j));
            }
        }
        //System.out.println("depthFM size:"+ depthFM_t.size());
        printToFile(depthFM_t, "depth_top_FM.txt");
        printToFile(regionTD_t, "region_top_FM.txt");
    }
    
    private void printToFile(Object object,String filename){
        if(this.vision.getExp() == 1 || this.vision.getExp()%20 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();
        // if(time_graph%2 == 0 ){
            try(FileWriter fw = new FileWriter("profile/"+filename,true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
            {
                out.println(dtf.format(now)+"_"+vision.getExp()+"_"+time_graph+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(filename+": "+time_graph);          
                time_graph++;
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        // }else time_graph++; 
    }
    }
}
    

