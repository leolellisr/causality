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
import coppelia.IntWA;
import coppelia.remoteApi;

import java.util.ArrayList;

import CommunicationInterface.MotorI;
import CommunicationInterface.SensorI;
import coppelia.CharWA;
import coppelia.FloatWA;
import java.io.IOException;
import java.net.InetAddress;
import outsideCommunication.OrientationVrep;
import outsideCommunication.Client;

/**
 *
 * @author Leticia
 */
public class OutsideCommunication {

	public remoteApi vrep;
	public int clientID;

  public IntW[] cuboid_handle;
  private int nCubs = 8;

  private ArrayList<FloatWA> cuboidsPositions;
  private ArrayList<FloatWA> cuboidsOrientations;

	public OutsideCommunication() {
		vrep = new remoteApi();
    cuboid_handle = new IntW[nCubs];
    cuboidsPositions = new ArrayList<>();
    cuboidsOrientations = new ArrayList<>();
	}

	public void start() {
		vrep = new remoteApi();
		vrep.simxFinish(-1); // just in case, close all opened connections
		clientID = vrep.simxStart("127.0.0.1", 25000, true, true, 5000, 5);

		if (clientID == -1) {
			System.err.println("Connection failed");
			System.exit(1);
		}

    //Get the initial position and orientation of obstacles (cuboids) to set during the learning mode
    for (int i = 0; i < nCubs; i++) {
        FloatWA position = new FloatWA(3);
        FloatWA orientation = new FloatWA(3);

        cuboid_handle[i] = new IntW(-1);

        String s = "Cuboid" + (i+1);

        vrep.simxGetObjectHandle(clientID, s, cuboid_handle[i], remoteApi.simx_opmode_blocking);

        vrep.simxGetObjectPosition(clientID, cuboid_handle[i].getValue(), -1, position, vrep.simx_opmode_blocking);
        vrep.simxGetObjectOrientation(clientID, cuboid_handle[i].getValue(), -1, orientation, remoteApi.simx_opmode_blocking);

        cuboidsPositions.add(position);
        cuboidsOrientations.add(orientation);
		}

		// START
		vrep.simxStartSimulation(clientID, remoteApi.simx_opmode_blocking);

	}

    public void resetCuboids(){
        for (int i = 0; i < nCubs; i++) {
            vrep.simxSetObjectPosition(clientID, cuboid_handle[i].getValue(), -1, cuboidsPositions.get(i), vrep.simx_opmode_oneshot);
            vrep.simxSetObjectOrientation(clientID, cuboid_handle[i].getValue(), -1, cuboidsOrientations.get(i), vrep.simx_opmode_oneshot);
        }
    }
}
