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
import java.util.Arrays;
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
        public FloatWA position0r;
        public FloatWA orientation0r;
        public FloatWA position0b;
        public FloatWA position0j,position0s;
        public FloatWA orientation0b;
        public FloatWA orientation0j,orientation0s;
        public IntW objR_handle;
        public IntW objB_handle;
        public IntW joint, sphere;
        public int port = 25000;
	public OutsideCommunication() {
		vrep = new remoteApi();
                obj_handle = new IntW[nObjs];

	}

        public void run(){
            this.joint_m.setPos(50);
        }
        public void reset(){
            /*vrep.simxSetObjectPosition(clientID, objR_handle.getValue(), -1, position0r, vrep.simx_opmode_oneshot);
            positionR.setExp(1);
            
            vrep.simxSetObjectPosition(clientID, objB_handle.getValue(), -1, position0b, vrep.simx_opmode_oneshot);
            positionB.setExp(1);
            
             try {
			Thread.sleep(50);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
             
             vrep.simxSetObjectPosition(clientID, joint.getValue(), -1, position0j, vrep.simx_opmode_oneshot);
            
              try {
			Thread.sleep(50);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
             vrep.simxSetObjectPosition(clientID, sphere.getValue(), -1, position0s, vrep.simx_opmode_oneshot);
            
            this.joint_m.setPos(0);
            
            
            try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
*/
                // Stop the simulation clientID) .simxStopSimulation(clientID,
        int stopSimResult = vrep.simxPauseSimulation(clientID,  vrep.simx_opmode_blocking);
        if (stopSimResult != remoteApi.simx_return_ok) {
            System.err.println("Failed to pause simulation. Error code: " + stopSimResult);
            System.exit(1);
        }
        
        // Temporarily switch to asynchronous mode to load the scene
 /*       if (vrep.simxSynchronous(clientID, false) != remoteApi.simx_return_ok) {
            System.err.println("Failed to switch to asynchronous mode.");
            System.exit(1);
        }
*/
          try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
          

                
            int loadSceneResult = vrep.simxLoadScene(clientID, "scenes/causal_scene.ttt", 0xFF, vrep.simx_opmode_oneshot);

        if (loadSceneResult == remoteApi.simx_return_ok) {
            System.out.println("Scene reset successfully.");
        } else {
            System.err.println("Failed to reset scene. Error code: " + loadSceneResult);
        }
        


        
          // Wait before starting the simulation again
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


                // Start the simulation again in synchronous mode
        int startSimResult = vrep.simxStartSimulation(clientID, vrep.simx_opmode_blocking);
        if (startSimResult != remoteApi.simx_return_ok) {
            System.err.println("Failed to start simulation. Error code: " + startSimResult);
            System.exit(1);
        }
        

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
		

		String objR_sensors_name = "red";
		objR_handle = new IntW(-1);
		vrep.simxGetObjectHandle(clientID, objR_sensors_name, objR_handle, remoteApi.simx_opmode_blocking);
			if (objR_handle.getValue() == -1)
				System.out.println("Error on connenting to sensor ");
			else
				System.out.println("Connected to sensor ");
		

		this.positionR = new PosVrep(vrep, clientID, objR_handle, "red");
                ArrayList<FloatWA> getDataPos=this.positionR.getDataPos();
                getDataPos=this.positionR.getDataPos();
                getDataPos=this.positionR.getDataPos();
                this.position0r = getDataPos.get(0);
                this.orientation0r = getDataPos.get(1);
                System.out.println("red pos x: "+this.position0r.getArray()[0]);
                System.out.println("red pos y: "+this.position0r.getArray()[1]);
                System.out.println("red pos z: "+this.position0r.getArray()[2]);
                System.out.println("red ori: "+this.orientation0r.getArray()[0]);
		

		String objB_sensors_name = "blue";
		this.objB_handle = new IntW(-1);
		vrep.simxGetObjectHandle(clientID, objB_sensors_name, objB_handle, remoteApi.simx_opmode_blocking);
			if (objB_handle.getValue() == -1)
				System.out.println("Error on connenting to sensor ");
			else
				System.out.println("Connected to sensor ");
		

		this.positionB = new PosVrep(vrep, clientID, objB_handle, "blue");
                getDataPos=this.positionB.getDataPos();
                getDataPos=this.positionB.getDataPos();
                this.position0b = getDataPos.get(0);
                this.orientation0b = getDataPos.get(1);
                System.out.println("Blue pos x: "+this.position0b.getArray()[0]);
                System.out.println("Blue pos y: "+this.position0b.getArray()[1]);
                System.out.println("Blue pos z: "+this.position0b.getArray()[2]);
                System.out.println("Blue ori: "+this.orientation0b.getArray()[0]);
                
                joint = new IntW(-1);
           
                vrep.simxGetObjectHandle(clientID, "Prismatic_joint", joint, remoteApi.simx_opmode_blocking);
			if (joint.getValue() == -1)
				System.out.println("Error on connenting to joint ");
			else
				System.out.println("Connected to joint ");
		

		PosVrep positionj = new PosVrep(vrep, clientID, joint,"joint");
                getDataPos=positionj.getDataPos();
                getDataPos=positionj.getDataPos();
                getDataPos=positionj.getDataPos();
                this.position0j = getDataPos.get(0);
                this.orientation0j = getDataPos.get(1);
                
                sphere = new IntW(-1);
           
                vrep.simxGetObjectHandle(clientID, "Sphere", sphere, remoteApi.simx_opmode_blocking);
			if (sphere.getValue() == -1)
				System.out.println("Error on connenting to sphere ");
			else
				System.out.println("Connected to sphere ");
		

		PosVrep positions = new PosVrep(vrep, clientID, sphere,"sphere");
                getDataPos=positions.getDataPos();
                getDataPos=positions.getDataPos();
                getDataPos=positions.getDataPos();
                this.position0s = getDataPos.get(0);
                this.orientation0s = getDataPos.get(1);
                
		vrep.simxGetObjectHandle(clientID, "Prismatic_joint", joint, remoteApi.simx_opmode_blocking);
	
		
		this.joint_m = new MotorVrep(vrep, clientID, joint.getValue());
                
                this.joint_m.setPos(50);
                
                try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}

                
		// START SIMULATION
		vrep.simxStartSimulation(clientID, remoteApi.simx_opmode_blocking);

		
	
	System.out.println("OC START - R: "+position0r+" "+orientation0r);
        System.out.println("B: "+position0b+" "+orientation0b);
        
			}
}