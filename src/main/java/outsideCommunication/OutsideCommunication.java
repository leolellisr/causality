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

import coppelia.IntW;
//import coppelia.IntWA;
import coppelia.remoteApi;

import java.util.ArrayList;

import CommunicationInterface.MotorI;
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
	public IntW marta_handle;
	public MotorI NeckYaw_m, HeadPitch_m;       
	public SensorI vision;
        public SensorI depth;
	public SensorI marta_orientation;
	public SensorI marta_position;
	public ArrayList<SensorI> vision_orientations;
        public static final int Resolution = 256;
        public IntW[] obj_handle;
        private int nObjs = 4;
        private final boolean debug = false;
        private List<FloatWA> objsPositions;
        private ArrayList<FloatWA> allobjsPositions;
        private ArrayList<FloatWA> objsOrientations;

  
	public OutsideCommunication() {
		vrep = new remoteApi();
		vision_orientations = new ArrayList<>();
                obj_handle = new IntW[nObjs];
                objsPositions = new ArrayList<>();
                allobjsPositions = new ArrayList<>();
                objsOrientations = new ArrayList<>();

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
		// Sensors - Vision
		//////////////////////////////////////////////////////////////////
		IntW vision_handles;

		String vision_sensors_name = "Vision_sensor";
		vision_handles = new IntW(-1);

			//// System.out.println(proximity_sensors_name);

		vrep.simxGetObjectHandle(clientID, vision_sensors_name, vision_handles, remoteApi.simx_opmode_blocking);
			if (vision_handles.getValue() == -1)
				System.out.println("Error on connenting to sensor ");
			else
				System.out.println("Connected to sensor ");
		

		vision = new VisionVrep(vrep, clientID, vision_handles);
                depth = new DepthVrep(vrep, clientID, vision_handles, vision.getStage(), vision);    
		try {
			Thread.sleep(1000);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}

                //Get the initial position and orientation of obstacles (cuboids) to set during the learning mode
                for (int i = 0; i < nObjs; i++) {
                    FloatWA position = new FloatWA(3);
                    FloatWA orientation = new FloatWA(3);

                    obj_handle[i] = new IntW(-1);

                    String s = "obj" + (i+1);

                    vrep.simxGetObjectHandle(clientID, s, obj_handle[i], remoteApi.simx_opmode_blocking);

                    vrep.simxGetObjectPosition(clientID, obj_handle[i].getValue(), -1, position, vrep.simx_opmode_blocking);
                    vrep.simxGetObjectOrientation(clientID, obj_handle[i].getValue(), -1, orientation, remoteApi.simx_opmode_blocking);
                    if(debug) System.out.println("obj "+i+" position - x: "+position.getArray()[0]+", y: "+position.getArray()[1]+", z: "+position.getArray()[2]+
                            "\n obj "+i+" orientation - x: "+orientation.getArray()[0]+", y: "+orientation.getArray()[1]+", z: "+orientation.getArray()[2]);
                    allobjsPositions.add(position);
                    objsOrientations.add(orientation);
                            }
                
                objsPositions = allobjsPositions.subList(0,3);
		// START SIMULATION
		vrep.simxStartSimulation(clientID, remoteApi.simx_opmode_blocking);

		// Vision initialization reading
		int ret = vrep.simxGetVisionSensorImage(clientID, vision_handles.getValue(), null, null, 1,
					remoteApi.simx_opmode_streaming);
		if (ret == remoteApi.simx_return_ok  || ret == remoteApi.simx_return_novalue_flag) {
			System.out.println("init ok ");
		} else {
				System.exit(1);
		}
	

	
			}
	
	public void shuffle_positions(){
            Collections.shuffle(objsPositions);
            for (int i = 0; i < nObjs-1; i++) {
                allobjsPositions.set(i, objsPositions.get(i));
            }
        }
        
        public void set_object_back(int obj) throws InterruptedException{
            int time = 500;
            vrep.simxSetObjectPosition(clientID, obj_handle[obj].getValue(), -1, allobjsPositions.get(3), vrep.simx_opmode_oneshot);        
            if (obj == 0 || obj == 2) {
                time = time*2;
            }
            try {
                Thread.sleep(time);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }    
        }

        public void reset_positions(){
            for (int i = 0; i < nObjs; i++) {
                vrep.simxSetObjectPosition(clientID, obj_handle[i].getValue(), -1, allobjsPositions.get(i), vrep.simx_opmode_oneshot);
            }
        }
        

}