/* *****************************************************************************
 *
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package nn;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import java.io.IOException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.dataset.SplitTestAndTrain;

/**
 * "Linear" Data Classification Example
 * <p>
 * Based on the data from Jason Baldridge:
 * https://github.com/jasonbaldridge/try-tf/tree/master/simdata
 * 
 * @author Josh Patterson
 * @author Alex Black (added plots)
 * @author Leonardo Rossi (leolellisr)
 */
public class LinearDataClassifier {

    public static boolean visualize = true;
    public static String dataLocalPath, name;
    private int seeds = 1234;
    private double learningRate = 0.01;
    private  int batchSize = 50;
    private  int nEpochs = 30;

    private  int numInputs = 2;
    private int numOutputs = 2;
    private int numHiddenNodes = 20;
    private MultiLayerNetwork model;
    private DataSet trainIter, testIter;
    private boolean debug = false;
    
    public LinearDataClassifier(int seed, double learningRate, int batchSize, int nEpochs, 
            int numInputs, int numOutputs, int numHiddenNodes, String name){
        this.seeds = seed;
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.nEpochs = nEpochs;

        this.numInputs = numInputs;
        this.numOutputs = numOutputs;
        this.numHiddenNodes = numHiddenNodes;
        this.name = name;
        
              MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(this.learningRate, 0.9))
                .seed(this.seeds)
                .list()
                .layer(new DenseLayer.Builder().nIn(this.numInputs).nOut(this.numHiddenNodes)
                        .activation(Activation.RELU)
                        .build())
                .layer(new OutputLayer.Builder(LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.SOFTMAX)
                        .nIn(this.numHiddenNodes).nOut(this.numOutputs).build())
                .build();


        this.model = new MultiLayerNetwork(conf);
        this.model.init();
        this.model.setListeners(new ScoreIterationListener(10));  //Print score every 10 parameter updates
    }
    
  public void setData(INDArray inputs, INDArray labels) throws IOException, InterruptedException{
        DataSet ds = new DataSet(inputs, labels);
        
        if(debug) System.out.println(ds);
           SplitTestAndTrain split     = ds.splitTestAndTrain(0.5);
     this.trainIter  = split.getTrain();
        this.testIter = split.getTest();
        
  }
  

  
   public void fit(){
        this.model.fit(this.trainIter);

        System.out.println("Evaluate model "+this.name);
        Evaluation eval = new Evaluation(numOutputs);
        for (var t : testIter) {
           
            INDArray features = t.getFeatures();
            INDArray labels = t.getLabels();
            INDArray predicted = this.model.output(features, false);
            eval.eval(labels, predicted);
        }
        //An alternate way to do the above loop
        //Evaluation evalResults = model.evaluate(testIter);

        //Print the evaluation statistics
        System.out.println(eval.stats());

        System.out.println("\n****************Example finished********************");
   }
   

    
}
