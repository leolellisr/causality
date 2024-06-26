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
import coppelia.FloatW;
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
        public int port = 25000, try_i = 2;
	public OutsideCommunication() {
		vrep = new remoteApi();
                obj_handle = new IntW[nObjs];
                
	}

        public void run(){
            this.joint_m.setPos(50);
        }
        public void reset(){
            
            boolean not_stopped = true;
            vrep.simxSetObjectPosition(clientID, objR_handle.getValue(), -1, position0r, vrep.simx_opmode_oneshot);
            positionR.setExp(1);
            
            vrep.simxSetObjectPosition(clientID, objB_handle.getValue(), -1, position0b, vrep.simx_opmode_oneshot);
            positionB.setExp(1);
            
             try {
			Thread.sleep(50);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
             
             int ret = vrep.simxSetJointTargetVelocity(clientID,joint.getValue(),-50, remoteApi.simx_opmode_oneshot_wait);
             System.out.println("OC reset zerei velocidade");
            // vrep.simxSetJointTargetPosition(clientID, joint.getValue(), 0, vrep.simx_opmode_oneshot_wait);
            //System.out.println("OC reset zerei posição");
            /*FloatW position = null;
            vrep.simxGetJointPosition(clientID, joint.getValue(), position, vrep.simx_opmode_oneshot_wait);

            System.out.println("OC reset zerei posição: "+position); 
*/            
            try {
			Thread.sleep(50);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
             //vrep.simxSetObjectPosition(clientID, sphere.getValue(), -1, position0s, vrep.simx_opmode_oneshot);
            
            /*this.joint_m.setPos(0);
            
            
            try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
            
*/
                // Stop the simulation clientID) .simxStopSimulation(clientID,
        
/*        if(this.try_i==1){        
                int pauseSimResult = vrep.simxPauseCommunication(clientID, true);
        if (pauseSimResult != remoteApi.simx_return_ok) {
            System.err.println("Failed to pause simulation. Error code: " + pauseSimResult);
            System.exit(1);
        }
        }
       /* int stopSimResult = vrep.simxStopSimulation(clientID,  vrep.simx_opmode_oneshot);
        if (stopSimResult != remoteApi.simx_return_ok) {
            System.err.println("Failed to stop simulation. Error code: " + stopSimResult);
            System.exit(1);
        }
        */
        // Temporarily switch to asynchronous mode to load the scene
    /*  if (vrep.simxSynchronous(clientID, false) != remoteApi.simx_return_ok) {
            System.err.println("Failed to switch to asynchronous mode.");
            System.exit(1);
        }
          try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
          */

                
        /*    int loadSceneResult = vrep.simxLoadScene(clientID, "scenes/causal_scene.ttt", 0xFF, vrep.simx_opmode_oneshot);

        if (loadSceneResult == remoteApi.simx_return_ok) {
            System.out.println("Scene reset successfully.");
        } else {
            System.err.println("Failed to reset scene. Error code: " + loadSceneResult);
        }
        */
        /*IntW simulationState = new IntW(-1);
        
      int stateResult = vrep.simxGetIntegerSignal(clientID, "cycle_sync", simulationState, remoteApi.simx_opmode_streaming);
      int get_msg = vrep.simxGetInMessageInfo(clientID, remoteApi.simx_headeroffset_server_state, simulationState);
      if(stateResult==remoteApi.simx_return_ok) {
          not_stopped = true;
           System.out.println("retrieve simulation state");
      }
      else if (stateResult != remoteApi.simx_return_ok) {
        System.out.println("Failed to retrieve simulation state. Error code: " + stateResult);
        return;
    }

         if (simulationState.getValue() != 1) {
        System.out.println("Simulation is not running. No need to stop it.");
    } else {
     */   // Retry mechanism for stopping the simulation
/*        boolean simulationStopped = false;
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            int stopSimResult = vrep.simxStopSimulation(clientID, remoteApi.simx_opmode_oneshot);
            if (stopSimResult == remoteApi.simx_return_ok || stopSimResult == remoteApi.simx_return_novalue_flag) {
                simulationStopped = true;
                break;
            } else {
                System.out.println("Failed to stop simulation. Attempt " + (i + 1) + " Error code: " + stopSimResult);
                try {
                    Thread.sleep(500); // Wait before retrying
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
              }
        }
        
        
          if (!simulationStopped) {
            System.out.println("Failed to stop simulation after " + maxRetries + " attempts.");
            return;
            }

        // Wait for the simulation to stop
/*        int simState = -1;
        while (simState != remoteApi.simx_return_novalue_flag) {
            IntW state = new IntW(-1);
            int getSimStateResult = vrep.simxGetIntegerSignal(clientID, "simulationState", state, remoteApi.simx_opmode_buffer);

            if (getSimStateResult != remoteApi.simx_return_ok && getSimStateResult != remoteApi.simx_return_novalue_flag) {
                System.out.println("Failed to retrieve simulation state during stop. Error code: " + getSimStateResult);
                return;
            }

            simState = state.getValue();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            }
*/        // Return to synchronous mode 


        


// Retry mechanism for Start the simulation again in synchronous mode
  /*       boolean simulationStart = false;

        for (int i = 0; i < maxRetries; i++) {
            
        int startSimResult = vrep.simxStartSimulation(clientID, vrep.simx_opmode_oneshot);
        if (startSimResult == remoteApi.simx_return_ok) {
            System.err.println("Started simulation. code: " + startSimResult);
            simulationStart = true;
                break;
            } else {
                System.out.println("Failed to start simulation. Attempt " + (i + 1) + " Error code: " + startSimResult);
                try {
                    Thread.sleep(500); // Wait before retrying
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
              }
        }
        
        
          if (!simulationStart) {
            System.out.println("Failed to start simulation after " + maxRetries + " attempts.");
            return;
            }
          
          
          // Retry mechanism for  switch  the simulation to synchronous mode
        boolean simulationSync = false;
        for (int i = 0; i < maxRetries; i++) {
            int syncSimResult = vrep.simxSynchronous(clientID, true);
        
        if ( syncSimResult== remoteApi.simx_return_ok) {
            System.err.println("Switched to synchronous mode.");
            simulationSync = true;
            break;
            } else {
                System.out.println("Failed to Switch to synchronous mode. Attempt " + (i + 1) + " Error code: " + syncSimResult);
                try {
                    Thread.sleep(500); // Wait before retrying
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
              }
        }
        if (!simulationSync) {
            System.out.println("Failed to Switch to synchronous mode after " + maxRetries + " attempts.");
            return;
            }
    */    
 }

    

	public void start() {
		// System.out.println("Program started");
		vrep = new remoteApi();
		vrep.simxFinish(-1); // just in case, close all opened connections
                //
		clientID = vrep.simxStart("127.0.0.1", 19997, true, true, 5000, 5);

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
		/*if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
			vrep.simxSynchronousTrigger(clientID);
*/
		

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
                
                //this.joint_m.setPos(50);
                
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