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

import coppelia.remoteApi;
import CommunicationInterface.MotorI;
import coppelia.FloatW;

/**
 *
 * @author L. M. Berto
 * @author L. L. Rossi (leolellisr)
 */
public class MotorVrep implements MotorI {
    private boolean debug =  false;
    private float position;        //Position of joint
    private final remoteApi vrep;
    private final int clientID;
    private final int motor_handle;
    
    public MotorVrep(remoteApi rApi_, int clientid, int mot_han){
        vrep = rApi_;
        clientID = clientid;
        motor_handle = mot_han;   
        position=0;
    }
    
    @Override
    public float getPos() {  //Actually gets position of joint
        return position;
    }

    @Override
    public int setPos(float targetVelocity) {  //Actually sets new position on joint
        this.position = targetVelocity;
        //vrep.simxPauseCommunication(clientID,true);
        //vrep.simxPauseCommunication(clientID, true);
        //float targetVelocity = 50;
        int ret = vrep.simxSetJointTargetVelocity(clientID,motor_handle,targetVelocity, remoteApi.simx_opmode_oneshot);
        if(debug) System.out.println("motor setPos velocidade 50");
        if( ret == remoteApi.simx_error_noerror) {
    	   
            if(debug) System.out.println(motor_handle+" new_pos 0: "+position);
            //vrep.simxPauseCommunication(clientID,false);
            //vrep.simxSynchronousTrigger(clientID);
           return 0;
        } 
       
        else {
           //System.out.println(motor_handle+" ret 5:"+ret5);
           System.out.println(motor_handle+" erro: "+ret);
           FloatW pos = new FloatW(-1);
           ret = vrep.simxSetJointTargetPosition(clientID, motor_handle, position, remoteApi.simx_opmode_oneshot);
           if(debug) System.out.println("motor setPos posição 50");
           if( ret == remoteApi.simx_error_noerror) {
           //vrep.simxPauseCommunication(clientID,false);
           //vrep.simxSynchronousTrigger(clientID);
            System.out.println(motor_handle+" sucess "+ret);
           return 0;
           }
           int err = vrep.simxGetJointPosition(clientID, motor_handle, pos, remoteApi.simx_opmode_buffer);
           System.out.println(motor_handle+"Error setting new_pos: "+position+". Actual pos: "+pos.getValue());
           return 1;
       }
       
    }
    
}
