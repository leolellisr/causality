package outsideCommunication;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
//import java.util.ArrayList;

import CommunicationInterface.SensorI;
import coppelia.FloatWA;
import coppelia.IntW;
import coppelia.remoteApi;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PositionVrep implements SensorI {
	
	private final remoteApi vrep;
    private final int clientID;
    private FloatWA position;
    private final IntW handle;
    private int time_graph;
    private int stage; 
    private SensorI vision;
	public PositionVrep (int clientID, IntW handle, remoteApi vrep, int stageVision, SensorI vision) {
        this.handle = handle;
        this.time_graph = 0;
        this.stage = stageVision;
        this.vision = vision;
        
        this.vrep = vrep;
        this.clientID = clientID;
        this.position = new FloatWA(3);
	}

    @Override
    public int getStage() {
            return this.stage;    
    }
    
    @Override
    public void setStage(int newstage) {
           this.stage = newstage;    
    }
    
	@Override
	public Object getData() {
		FloatWA position = new FloatWA(3);
		vrep.simxGetObjectPosition(clientID, handle.getValue(), -1, position,
                vrep.simx_opmode_streaming);
		
		printToFile(position, "positions.txt");
		
		return position;
	}
	
	public void resetData() {
		System.out.println("Resseting position");
		vrep.simxPauseCommunication(clientID, true);
		FloatWA position = initFloatWA(false);
		//vrep.simxStopSimulation(clientID, vrep.simx_opmode_oneshot);
                vrep.simxCallScriptFunction(clientID, "Martabot", vrep.sim_scripttype_childscript, "reset",  null , 
				null ,null , null , null, null , null, null, vrep.simx_opmode_blocking);
		vrep.simxGetObjectPosition(clientID, handle.getValue(), -1, position, vrep.simx_opmode_oneshot);
               // vrep.simxSetObjectPosition(clientID, left_motor.getValue(), -1, position,                vrep.simx_opmode_oneshot);
                //vrep.simxSetObjectPosition(clientID, right_motor.getValue(), -1, position,                vrep.simx_opmode_oneshot);
                
		//FloatWA angles = initFloatWA(true);
		//vrep.simxSetObjectOrientation(clientID, handle.getValue(), -1, angles, vrep.simx_opmode_oneshot);
          
		vrep.simxPauseCommunication(clientID, false);
                
                // SYNC
		//if (vrep.simxSynchronous(clientID, true) == remoteApi.simx_return_ok)
		//	vrep.simxSynchronousTrigger(clientID);
                
		vrep.simxSynchronousTrigger(clientID);

	}
	
	private FloatWA initFloatWA(boolean orient) {
		FloatWA position = new FloatWA(3);
		float[] pos;
                pos = position.getArray();
		
		if (orient) {
			pos[0] = 0.0f;
			pos[1] = 0.0f;
			pos[2] = (float) Math.random() * 360;
		}
		else {
			pos[0] = -0.061865106f;
			pos[1] = -2.1647725f;
			pos[2] = 0.3929429f;
		}
		return position;
	}
	
	private void printToFile(FloatWA position,String filename) {
             //if(time_graph < 100){
               //System.out.println("time_graph: "+time_graph+" "+ position.getArray()[2]);
               DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
                LocalDateTime now = LocalDateTime.now();
		try(FileWriter fw = new FileWriter("profile/"+filename,true);
	            BufferedWriter bw = new BufferedWriter(fw);
	            PrintWriter out = new PrintWriter(bw))
	        {
	            out.println(dtf.format(now)+"_"+time_graph+" x:"+ position.getArray()[0]+" y:"+ position.getArray()[1]+" z:"+ position.getArray()[2]);
                    time_graph++;
	            out.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }

	//}
    }

    @Override
    public void setExp(int exp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getExp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
