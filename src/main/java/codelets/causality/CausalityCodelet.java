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
 
package codelets.causality;

import CommunicationInterface.SensorI;
import br.unicamp.cst.core.entities.Codelet;
import br.unicamp.cst.core.entities.MemoryObject;
import br.unicamp.cst.representation.idea.Idea;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import outsideCommunication.OutsideCommunication;
import nn.LinearDataClassifier;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class CausalityCodelet extends Codelet {
    private MemoryObject input_redMO;
    private SensorI position;
    private int dimension, step = 0;
    private  boolean reset = true;
    private static boolean debug = false;
    private String input_red, input_blue, output;
    private List input_red_idea, input_blue_idea;
    private Idea output_idea;
    private OutsideCommunication oc;
    private LinearDataClassifier nn_r, nn_b;

    public CausalityCodelet(OutsideCommunication oc,String input_red, String input_blue, String output, int dimension, boolean load) throws IOException{
        this.input_red = input_red;
        this.input_blue = input_blue;
        this.output = output; 
        this.dimension = 0;
        this.oc = oc;
        int numRows=10;
        int numColumns=10;
        
          this.nn_r = new LinearDataClassifier(1233, 0.01, 5, 10, numColumns, numColumns, 20, "red_nn",load);

          this.nn_b = new LinearDataClassifier(1234, 0.01, 5, 10, numColumns, numColumns, 20, "blue_nn",load);
          
    }
    
    @Override
    public void accessMemoryObjects() {
        MemoryObject MO;
        MO = (MemoryObject) this.getInput(input_red);
        input_red_idea = (List) MO.getI();
        MO = (MemoryObject) this.getInput(input_blue);
        input_blue_idea = (List) MO.getI();
        MO = (MemoryObject) this.getOutput(output);
        output_idea =  (Idea) MO.getI();
        

    }

    @Override
    public void calculateActivation() {
        
    }

    
    
    public float[][] convert(ArrayList<List<Float>> arrayList){
        
        int numRows = arrayList.size();
        int numCols = arrayList.get(0).size();
        float[][] floatArray = new float[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                floatArray[i][j] = arrayList.get(i).get(j);
            }
        }
        return floatArray;
    }
    
    @Override
    public void proc() {
        //this.oc.run();
    	try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
        if(input_red_idea.size() < 1 || input_blue_idea.size() < 1){
            return;
        }

        if(input_red_idea!= null && input_blue_idea!= null && input_red_idea.get(input_red_idea.size()-1)!= null && input_blue_idea.get(input_blue_idea.size()-1)!= null){

            
            if(input_red_idea.size() > 13 && input_blue_idea.size() > 13){

                ArrayList<List<Float>> labels_r = new ArrayList<>();
                ArrayList<List<Float>> labels_b = new ArrayList<>();

                ArrayList<List<Float>> inputs_r = new ArrayList<>();
                ArrayList<List<Float>> inputs_b = new ArrayList<>();

                int j=0;    
                for(int i=0; i< input_red_idea.size()-2; i++){    
                MemoryObject red = (MemoryObject) input_red_idea.get(input_red_idea.size()-i-1);
                List<Float> mostRecentInput_red = (List<Float>) red.getI();
                MemoryObject blue = (MemoryObject) input_blue_idea.get(input_blue_idea.size()-i-1);
                List<Float> mostRecentInput_blue = (List<Float>) blue.getI();
                
                if(debug) System.out.print(" red L = "+mostRecentInput_red+", size: "+mostRecentInput_red.size()+" \n blue L = "+mostRecentInput_blue+", size: "+mostRecentInput_blue.size());
                
                if(mostRecentInput_red.size() > 2 && mostRecentInput_blue.size() > 2){
                   
                    labels_r.add(mostRecentInput_red);
                    labels_b.add(mostRecentInput_blue);
                    
                    MemoryObject redi = (MemoryObject) input_red_idea.get(input_red_idea.size()-i-2);
                    List<Float> mostRecentInput_redi = (List<Float>) redi.getI();
                    MemoryObject bluei = (MemoryObject) input_blue_idea.get(input_blue_idea.size()-i-2);
                    List<Float> mostRecentInput_bluei = (List<Float>) bluei.getI();
                    if(debug) System.out.print(" red i= "+i+" "+mostRecentInput_redi+" \n blue i= "+mostRecentInput_bluei);
                    inputs_r.add(mostRecentInput_redi);
                    inputs_b.add(mostRecentInput_bluei);
                    j+=1;
                    }
                i+=1;
                }
                
                if(debug) {
                    System.out.print(" sizes. ir:"+inputs_r.size()+"ib:"+inputs_b.size());
                    System.out.print(" sizes. lr:"+labels_r.size()+"lb:"+labels_b.size());
                }
                  // list off input values, 4 training samples with data for 2
        // input-neurons each
                
            INDArray labels_ra = Nd4j.create(convert(labels_r));
            INDArray input_ra = Nd4j.create(convert(inputs_r));

            try {
                    // correspondending list with expected output values, 4 training samples
                    // with data for 2 output-neurons each
                    this.nn_r.setData(input_ra,labels_ra);
                    this.nn_r.fit();
            } catch (IOException ex) {
                    Logger.getLogger(CausalityCodelet.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                    Logger.getLogger(CausalityCodelet.class.getName()).log(Level.SEVERE, null, ex);
            }

            step += 1;
            INDArray labels_ba = Nd4j.create(convert(labels_b));
            INDArray input_ba = Nd4j.create(convert(inputs_b));
            System.out.print(" step = "+this.step);
            if(step>1000) System.exit(1);
            try {
                    // correspondending list with expected output values, 4 training samples
                    // with data for 2 output-neurons each
                    this.nn_b.setData(input_ba,labels_ba);
                    this.nn_b.fit();
            } catch (IOException ex) {
                    Logger.getLogger(CausalityCodelet.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                    Logger.getLogger(CausalityCodelet.class.getName()).log(Level.SEVERE, null, ex);
            }



            }
            
        }
        else{
            this.dimension += 1;
                if(debug){ 
                System.out.print(" causalisy else = "+this.dimension);
                System.out.print(input_red_idea);
                System.out.print(input_blue_idea);
            }
        }
        
    }
    
}
