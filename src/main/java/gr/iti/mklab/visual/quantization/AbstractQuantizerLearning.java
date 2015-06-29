package gr.iti.mklab.visual.quantization;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import weka.clusterers.SimpleKMeans;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

/**
 * This class contains a static method than learns a k-means quantizer using a slightly modified (to produce
 * some additional output) version of Weka's SimpleKMeans class and writes the learned quantizer to a file. It
 * supports parallel execution!
 * 
 * @author Eleftherios Spyromitros-Xioufis
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
	 * @param kMeansPlusPlus
	 *            whether to use kmeans++ for the initialization of the centroids (true/false)
	 * @throws Exception
	 */
	public static void learnAndWriteQuantizer(String outFilePath, Instances data, int numClusters,
			int maxIterations, int seed, int numSlots, boolean kMeansPlusPlus) throws Exception {
		System.out.println("--" + data.numInstances() + " vectors loaded--");
		System.out.println("Vector dimensionality: " + data.numAttributes());
		System.out.println("Clustering settings:");
		System.out.println("Num clusters: " + numClusters);
		System.out.println("Max iterations: " + maxIterations);
		System.out.println("Seed: " + seed);

		System.out.println("Clustering started");
		long start = System.currentTimeMillis();
		// create a new Clusterer and initialize appropriately
		SimpleKMeans clusterer = new SimpleKMeans();
		if (kMeansPlusPlus) {
			clusterer.setInitializationMethod(new SelectedTag(SimpleKMeans.KMEANS_PLUS_PLUS,
					SimpleKMeans.TAGS_SELECTION));
		}
		clusterer.setDebug(true);
		clusterer.setSeed(seed);
		clusterer.setNumClusters(numClusters);
		clusterer.setMaxIterations(maxIterations);
		clusterer.setNumExecutionSlots(numSlots);
		clusterer.setFastDistanceCalc(true);
		// build the clusterer
		clusterer.buildClusterer(data);
		// System.out.println("Clusterer:\n" + clusterer.toString());
		long end = System.currentTimeMillis();
		System.out.println("Clustering completed in " + (end - start) + " ms");

		System.out.println("Writing quantizer in file");
		// create a new file to store the codebook
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(outFilePath)));
		// write the results of the clustering to the new file (csv formated)
		Instances clusterCentroids = clusterer.getClusterCentroids();
		for (int j = 0; j < clusterCentroids.numInstances(); j++) {
			Instance centroid = clusterCentroids.instance(j);
			for (int k = 0; k < centroid.numAttributes() - 1; k++) {
				out.write(centroid.value(k) + ",");
			}
			out.write(centroid.value(centroid.numAttributes() - 1) + "\n");
		}
		out.close();
	}
}
