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
 
package codelets.sensors;

import CommunicationInterface.SensorI;
import br.unicamp.cst.core.entities.MemoryObject;
import sensory.CombFeatMapCodelet;
//import cst_attmod_app.AgentMind;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.List;


/**
 *
 * @author leolellisr
 */
public class CFM extends CombFeatMapCodelet {
    private static final int BOTTOM_UP = 0;
    private static final int TOP_DOWN = 1;
    private  int time_graph;

    //private  int max_time_graph = 100;
    private SensorI sensor;
    private int stage;
   
    public CFM(SensorI sensor, int numfeatmaps, ArrayList<String> featmapsnames, int timeWin, int CFMdim) {
        super(numfeatmaps, featmapsnames,timeWin,CFMdim);
        this.time_graph = 0;
        this.sensor = sensor;
        this.stage = sensor.getStage();
        
    }

     
    @Override
    public void calculateCombFeatMap() {
        this.stage = sensor.getStage();
//        ArrayList<Integer> sizes = new ArrayList<>();
        
        for (int i = 0; i < num_feat_maps; i++) {
            MemoryObject mo = (MemoryObject)feature_maps.get(i);
            //System.out.println("CFM"+i);
            List fm = (List) mo.getI();
            //sizes.add(fm.size());
        }
        
       
        try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
        List weight_values = (List) weights.getI();
        
        List combinedFM = (List) comb_feature_mapMO.getI();
        List winnersTypeList = (List) winnersType.getI();
        
        if(combinedFM.size() == timeWindow){
            combinedFM.remove(0);
        }
        if(winnersTypeList.size() == timeWindow){
            winnersTypeList.remove(0);
        }
        
        combinedFM.add(new ArrayList<>());
        winnersTypeList.add(new ArrayList<>());
        
        int t = combinedFM.size()-1;

        List CFMrow, winners_row;
        CFMrow = (List) combinedFM.get(t);
        winners_row = (List) winnersTypeList.get(t);
        
        for(int j = 0; j < CFMdimension; j++){
            CFMrow.add(new Float(0));
            winners_row.add(0);
        }
        
        
        for (int j = 0; j < CFMrow.size(); j++) {
            float ctj;
            float sum_top=0, sum_bottom=0;
            ctj = 0;
            
            for (int k = 0; k < num_feat_maps; k++) {
                MemoryObject FMkMO;
                FMkMO = (MemoryObject) feature_maps.get(k);

                List FMk;
                FMk = (List) FMkMO.getI();
                
                
                if(FMk.size() < 1){
                    return;
                }
                
                List FMk_t;
                FMk_t = (List) FMk.get(FMk.size()-1);
                
                Float weight_val, fmkt_val;
                
                fmkt_val = (Float) FMk_t.get(j); 
                                
                weight_val = (Float) weight_values.get(k);
                ctj += weight_val*fmkt_val;
                
                if(stage==3) {
                    if(k>=4) sum_top += weight_val*fmkt_val;
                    else sum_bottom += weight_val*fmkt_val;
                }   
                // TODO: Somar mapas bu e tp pra selecionar winner
               //System.out.println("sum_top: "+sum_top);
        //System.out.println("sum_bottom: "+sum_bottom);
        //System.out.println("sum_top/3: "+sum_top/3);
        //System.out.println("sum_bottom/4: "+sum_bottom/4); 
            }   
            
            CFMrow.set(j, ctj);
            
            if(sum_top > sum_bottom) winners_row.set(j, TOP_DOWN);
            else winners_row.set(j, BOTTOM_UP);
            
        }
        //if(sum_top > sum_bottom) this.winner = TOP_DOWN;
        
        
        printToFile((ArrayList<Float>) CFMrow, "CFM.txt");
        printToFile((ArrayList<Integer>) winners_row, "winnerType.txt");
    }
    
      
    private void printToFile(Object object,String filename){
        if(this.sensor.getExp() == 1 || this.sensor.getExp()%20 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();
        //if(time_graph%2 == 0 ){
            try(FileWriter fw = new FileWriter("profile/"+filename,true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
            {
                out.println(dtf.format(now)+"_"+this.sensor.getExp()+"_"+time_graph+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(filename+": "+time_graph);          
                time_graph++;
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        //}else time_graph++;  
    }
    }
}