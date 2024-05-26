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
import br.unicamp.cst.core.entities.Codelet;
import br.unicamp.cst.core.entities.MemoryObject;
import br.unicamp.cst.representation.idea.Idea;
//import codelets.motor.Lock;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class Sensor_Position extends Codelet {
    private MemoryObject position_read;
    private SensorI position;
    private int stage,step;
    private String output;
    
    public Sensor_Position(SensorI position, String output){
        this.position = position;
        this.stage = this.position.getStage();
        this.output = output;
        this.step = 0;
    }

    public int getStage() {
        return this.stage;    
    }
    
    public void setStage(int newstage) {
        this.stage = newstage;    
    }
    
    
    @Override
    public void accessMemoryObjects() {
        position_read = (MemoryObject) this.getOutput(this.output);
    }

    @Override
    public void calculateActivation() {
        
    }

    @Override
    public void proc() {
        this.step += 1;
        position_read.setI(new Idea("position"+position.getObjHandle()+this.step, position.getData(),3));
        /*System.out.println("Sensor position:"+this.stage);
        System.out.println("Sensor position getStage:"+getStage());
        System.out.println("Sensor position.getStage:"+position.getStage());
        */
        this.stage = position.getStage();
        
    }
    
}
