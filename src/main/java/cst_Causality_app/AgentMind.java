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
 
package cst_Causality_app;


import br.unicamp.cst.core.entities.Codelet;
import br.unicamp.cst.core.entities.Memory;
import br.unicamp.cst.core.entities.MemoryObject;
import br.unicamp.cst.core.entities.Mind;
import br.unicamp.cst.representation.idea.Idea;
import sensory.SensorBufferCodelet;
import codelets.causality.*;
import codelets.sensors.Sensor_Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import outsideCommunication.OutsideCommunication;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class AgentMind extends Mind {
    public static final int Resolution = 256;
    public static final int Buffersize = 100;
    public static final int Posdimension = 6;
    public static final int Vision_image_dimension = Resolution*Resolution;
    public static final int Sensor_dimension = 256;
    public static final boolean debug = true;
    private int index_hunger, index_curiosity;
        private String stringOutputac = "", stringOutputre = "";

    public AgentMind(OutsideCommunication oc, String mode, String motivation) throws IOException{
        
        super();
        //System.out.println("AgentMind");
        //////////////////////////////////////////////
        //Declare Memory Objects
        //////////////////////////////////////////////
    
        //Position
        List posR_data = Collections.synchronizedList(new ArrayList<Float>(Posdimension));
        MemoryObject posR_read = createMemoryObject("POSR", posR_data);

        List posB_data = Collections.synchronizedList(new ArrayList<Float>(Posdimension));
        MemoryObject posB_read = createMemoryObject("POSB", posB_data);

        //Position buffer
        List posR_buffer_list = Collections.synchronizedList(new ArrayList<Memory>(Buffersize));
        MemoryObject posR_bufferMO = createMemoryObject("POSR_BUFFER",posR_buffer_list);

        List posB_buffer_list = Collections.synchronizedList(new ArrayList<Memory>(Buffersize));
        MemoryObject posB_bufferMO = createMemoryObject("POSB_BUFFER",posB_buffer_list);
        
        //Causality
        Idea causality_idea = new Idea("causality", Collections.synchronizedList(new ArrayList<Object>(Posdimension)), 1);
        MemoryObject causalityMO = createMemoryObject("CAUSALITY", causality_idea);

//        
//        
//        ////////////////////////////////////////////
//        //Codelets
//        ////////////////////////////////////////////
////        
        //Position
        Codelet positionR = new Sensor_Position(oc.positionR, "POSR");
        positionR.addOutput(posR_read);
        insertCodelet(positionR);

        Codelet positionB = new Sensor_Position(oc.positionB, "POSB");
        positionB.addOutput(posB_read);
        insertCodelet(positionB);
        
        //Sensor Buffers
        Codelet posR_buffer = new SensorBufferCodelet("POSR", "POSR_BUFFER", Buffersize);
        posR_buffer.addInput(posR_read);
        posR_buffer.addOutput(posR_bufferMO);
        insertCodelet(posR_buffer);

        Codelet posB_buffer = new SensorBufferCodelet("POSB", "POSB_BUFFER", Buffersize);
        posB_buffer.addInput(posB_read);
        posB_buffer.addOutput(posB_bufferMO);
        insertCodelet(posB_buffer);
        
        Codelet causality = new CausalityCodelet("POSR_BUFFER", "POSB_BUFFER", "CAUSALITY", Posdimension);
        causality.addInput(posR_bufferMO);
        causality.addInput(posB_bufferMO);
        causality.addOutput(causalityMO);
        insertCodelet(causality);
        ///////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////
        
        // NOTE Sets the time interval between the readings
        // sets a time step for running the codelets to avoid heating too much your machine
        for (Codelet c : this.getCodeRack().getAllCodelets())
            c.setTimeStep(200);
	
        try {
            Thread.sleep(200);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        
     
	// Start Cognitive Cycle
	start(); 
	
    }
}
