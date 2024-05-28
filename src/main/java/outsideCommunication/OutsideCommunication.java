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

import CommunicationInterface.MotorI;
import coppelia.IntW;
//import coppelia.IntWA;
import coppelia.remoteApi;

import java.util.ArrayList;

import CommunicationInterface.SensorI;
import coppelia.CharWA;
import coppelia.FloatWA;
import java.util.Collections;
import java.util.List;
//import outsideCommunication.OrientationVrep;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class OutsideCommunication {

	public remoteApi vrep;
	public int clientID;
	public SensorI positionR;
        public SensorI positionB;
        public IntW[] obj_handle;
        private int nObjs = 2;
        private final boolean debug = false;
        public MotorI joint_m;
                
  
	public OutsideCommunication() {
		vrep = new remoteApi();
                obj_handle = new IntW[nObjs];

	}

	public void start() {
		// System.out.println("Program started");
		vrep = new remoteApi();
		vrep.simxFinish(-1); // just in case, close all opened connections
                
		clientID = vrep.simxStart("127.0.0.1", 25000, true, true, 5000, 5);

		if (clientID == -1) {
			System.err.println("Connection failed");
			System.exit(1);
		}

		try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}

		// SYNC
		if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
			vrep.simxSynchronousTrigger(clientID);

		

		//////////////////////////////////////////////////////////////////
		// Sensors - Position
		//////////////////////////////////////////////////////////////////
		IntW objR_handle;

		String objR_sensors_name = "red";
		objR_handle = new IntW(-1);
		vrep.simxGetObjectHandle(clientID, objR_sensors_name, objR_handle, remoteApi.simx_opmode_blocking);
			if (objR_handle.getValue() == -1)
				System.out.println("Error on connenting to sensor ");
			else
				System.out.println("Connected to sensor ");
		

		positionR = new PosVrep(vrep, clientID, objR_handle);
                
		IntW objB_handle;

		String objB_sensors_name = "blue";
		objB_handle = new IntW(-1);
		vrep.simxGetObjectHandle(clientID, objB_sensors_name, objB_handle, remoteApi.simx_opmode_blocking);
			if (objB_handle.getValue() == -1)
				System.out.println("Error on connenting to sensor ");
			else
				System.out.println("Connected to sensor ");
		

		positionB = new PosVrep(vrep, clientID, objB_handle);
                
                IntW joint = new IntW(-1);
           
		vrep.simxGetObjectHandle(clientID, "Prismatic_joint", joint, remoteApi.simx_opmode_blocking);
	
		
		joint_m = new MotorVrep(vrep, clientID, joint.getValue());
                
                joint_m.setPos(50);
                
                try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}

                
		// START SIMULATION
		vrep.simxStartSimulation(clientID, remoteApi.simx_opmode_blocking);

		
	

	
			}
}