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
import java.util.ArrayList;
import java.util.List;
import outsideCommunication.OutsideCommunication;
//import codelets.motor.Lock;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class CausalityCodelet extends Codelet {
    private MemoryObject input_redMO;
    private SensorI position;
    private int dimension;
    private String input_red, input_blue, output;
    private List input_red_idea, input_blue_idea;
    private Idea output_idea;
    private OutsideCommunication oc;
    public CausalityCodelet(OutsideCommunication oc,String input_red, String input_blue, String output, int dimension){
        this.input_red = input_red;
        this.input_blue = input_blue;
        this.output = output; 
        this.dimension = 0;
        this.oc = oc;

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

    @Override
    public void proc() {
        this.oc.joint_m.setPos(50);
    	try {
            Thread.sleep(50);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
        if(input_red_idea.size() < 1 || input_blue_idea.size() < 1){
            return;
        }

        if(input_red_idea!= null && input_blue_idea!= null && input_red_idea.get(input_red_idea.size()-1)!= null && input_blue_idea.get(input_blue_idea.size()-1)!= null){

            if(!input_red_idea.isEmpty() && !input_blue_idea.isEmpty()){
                MemoryObject red = (MemoryObject) input_red_idea.get(input_red_idea.size()-1);
                List<Float> mostRecentInput_red = (List<Float>) red.getI();
                MemoryObject blue = (MemoryObject) input_blue_idea.get(input_blue_idea.size()-1);
                List<Float> mostRecentInput_blue = (List<Float>) blue.getI();
                
                
                System.out.print(" red = "+mostRecentInput_red+" \n blue = "+mostRecentInput_blue);


            }
            
        }
        else{
            this.dimension += 1;
            System.out.print(" causalisy else = "+this.dimension);
            System.out.print(input_red_idea);
            System.out.print(input_blue_idea);
            
        }
        
    }
    
}
