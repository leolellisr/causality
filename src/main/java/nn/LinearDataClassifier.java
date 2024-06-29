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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.eval.RegressionEvaluation;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.indexing.INDArrayIndex;

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
    public String name;
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
    private int time_graph;
    private double best_eval = 1;
    public LinearDataClassifier(int seed, double learningRate, int batchSize, int nEpochs, 
            int numInputs, int numOutputs, int numHiddenNodes, String name, boolean load) throws IOException{
        time_graph = 0;
        this.seeds = seed;
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.nEpochs = nEpochs;

        this.numInputs = numInputs;
        this.numOutputs = numOutputs;
        this.numHiddenNodes = numHiddenNodes;
        this.name = name;
        
        if(!load){
              MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(this.learningRate, 0.9))
                .seed(this.seeds)
                .list()
                .layer(new DenseLayer.Builder().nIn(this.numInputs).nOut(this.numHiddenNodes)
                        .activation(Activation.RELU)
                        .build())
                .layer(new OutputLayer.Builder(LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .nIn(this.numHiddenNodes).nOut(this.numOutputs).build())
                .build();
              this.model = new MultiLayerNetwork(conf);
        }else{
            this.model = MultiLayerNetwork.load(new File("profile/causal_"+this.name), true);
        }
        
        this.model.init();
        this.model.setListeners(new ScoreIterationListener(10));  //Print score every 10 parameter updates
    }
    
  public void setData(INDArray inputs, INDArray labels) throws IOException, InterruptedException{

    final DataSet allData = new DataSet(inputs,labels);

    final List<DataSet> list = allData.asList();

    ListDataSetIterator iterator = new ListDataSetIterator(list, this.batchSize);

        
        if(debug){
            System.out.println(iterator.next());
            
            ArrayList<DataSet> DataSetList = new ArrayList<>();
            DataSetList.add(iterator.next());
            plotDataset(DataSetList); //Plot the data, make sure we have the right data.
           //    SplitTestAndTrain split     = ds.splitTestAndTrain(0.5);
        }
       this.trainIter  = iterator.next();
       this.testIter = iterator.next();
       if(debug) System.out.println("numExamples trainIter: "+this.trainIter.numExamples());
  }
  

  
   public void fit() throws IOException{
        this.model.fit(this.trainIter);
        
        //System.out.println("Evaluate model "+this.name);
        RegressionEvaluation eval = new RegressionEvaluation();
        for (var t : testIter) {
           
            INDArray features = t.getFeatures();
            INDArray labels = t.getLabels();
            INDArray predicted = this.model.output(features, false);
            eval.eval(labels, predicted);
            printToFile(predicted,this.name);
        }
        //An alternate way to do the above loop
        //Evaluation evalResults = model.evaluate(testIter);
        if(eval.averageMeanSquaredError() < best_eval){
            System.out.println("Eval "+eval.averageMeanSquaredError()+" better than best eval "+best_eval+". Saving model to tmp folder: "+"profile/causal_"+this.name);
            model.save(new File("profile/causal_"+this.name), true);
            best_eval = eval.averageMeanSquaredError();
        
        }
        //Print the evaluation statistics
       System.out.println(eval.stats());
       
       System.out.println("\n****************Example finished********************");
       
       
   }
   

    
    /**
     * Generate an xy plot of the datasets provided.
     */
    private static void plotDataset(ArrayList<DataSet> DataSetList) {

        XYSeriesCollection c = new XYSeriesCollection();

        int dscounter = 1; //use to name the dataseries
        for (DataSet ds : DataSetList) {
            INDArray features = ds.getFeatures();
            INDArray outputs = ds.getLabels();

            int nRows = features.rows();
            XYSeries series = new XYSeries("S" + dscounter);
            for (int i = 0; i < nRows; i++) {
                series.add(features.getDouble(i), outputs.getDouble(i));
            }

            c.addSeries(series);
        }

        String title = "title";
        String xAxisLabel = "xAxisLabel";
        String yAxisLabel = "yAxisLabel";
        PlotOrientation orientation = PlotOrientation.VERTICAL;
        boolean legend = false;
        boolean tooltips = false;
        boolean urls = false;
        //noinspection ConstantConditions
        JFreeChart chart = ChartFactory.createScatterPlot(title, xAxisLabel, yAxisLabel, c, orientation, legend, tooltips, urls);
        JPanel panel = new ChartPanel(chart);

        JFrame f = new JFrame();
        f.add(panel);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setTitle("Training Data");

        f.setVisible(true);
    }
    
        private void printToFile(Object object, String file){
        //if(this.num_exp == 1 || this.num_exp%10 == 0){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");  
        LocalDateTime now = LocalDateTime.now();  
        try(FileWriter fw = new FileWriter("profile/causal_"+file+".txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw)){
            out.println(dtf.format(now)+"_"+time_graph+" "+ object);
                //if(time_graph == max_time_graph-1) System.out.println(dtf.format(now)+"vision: "+time_graph);
            time_graph++;
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
