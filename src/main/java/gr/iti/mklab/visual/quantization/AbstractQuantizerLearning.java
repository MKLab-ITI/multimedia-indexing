package gr.iti.mklab.visual.quantization;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import weka.core.Instances;

/**
 * This class contains a static method than learns a k-means quantizer using a slightly modified (to produce some
 * additional output) version of Weka's SimpleKMeans class and writes the learned quantizer to a file. It supports
 * parallel execution!
 * 
 * @author Eleftherios Spyromitros-Xioufiss
 */
public class AbstractQuantizerLearning {

	/**
	 * 
	 * @param outFilePath
	 *            full path to the output file
	 * @param data
	 *            the instances object containing the data on which the quantizer is learner
	 * @param numClusters
	 *            the number of clusters in k-means
	 * @param maxIterations
	 *            the maximum number of k-means iterations
	 * @param seed
	 *            the seed given to k-means
	 * @param numSlots
	 *            the number of execution slots to use (>1 = parallel execution)
	 * @throws Exception
	 */
	public static void learnAndWriteQuantizer(String outFilePath, Instances data, int numClusters, int maxIterations,
			int seed, int numSlots) throws Exception {
		System.out.println("--" + data.numInstances() + " descriptors loaded--");
		System.out.println("Descriptor dimensionality: " + data.numAttributes());
		System.out.println("Clustering settings:");
		System.out.println("Num clusters: " + numClusters);
		System.out.println("Max iterations: " + maxIterations);
		System.out.println("Seed: " + seed);

		long start = System.currentTimeMillis();
		// create a new instance for the Clusterer
		SimpleKMeansWithOutput clusterer = new SimpleKMeansWithOutput();
		clusterer.setSeed(seed);
		clusterer.setNumClusters(numClusters);
		clusterer.setMaxIterations(maxIterations);
		clusterer.setNumExecutionSlots(numSlots);
		clusterer.setFastDistanceCalc(false);
		// build the clusterer
		clusterer.buildClusterer(data);
		System.out.println(clusterer.toString());
		long end = System.currentTimeMillis();

		// create a new file to store the codebook
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(outFilePath)));
		// write the results of the clustering to the new file (csv formated)
		Instances clusterCentroids = clusterer.getClusterCentroids();
		for (int i = 0; i < numClusters; i++) {
			out.write(clusterCentroids.instance(i).toStringNoWeight() + "\n");
		}
		out.close();
		System.out.println("Total execution time: " + (end - start));

	}
}
