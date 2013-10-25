package gr.iti.mklab.visual.learning.codebook;

import gr.iti.mklab.visual.utilities.Normalization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import weka.core.Instances;
import weka.core.converters.CSVLoader;

/**
 * This class creates a codebook from a set of local features using a slightly modified (to produce some additional
 * output) version of Weka's SimpleKMeans class. The local features should be stored in an arff or csv formated file. It
 * supports l2 or power+l2 normalization of the local features prior to clustering.s supports parallel execution!
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class CodebookGeneration {

	/**
	 * The power to use when power-normalization is applied on the local features.
	 */
	public static final double power = 0.5;

	/**
	 * @param args
	 *            [0] path to the arff or csv formated (without header) file containing a set the local features
	 * @param args
	 *            [1] the number of clusters to create (e.g. 64)
	 * @param args
	 *            [2] the maximum number of k-means iterations (e.g. 100)
	 * @param args
	 *            [3] the seed given to k-means (e.g. 1)
	 * @param args
	 *            [4] the number of execution slots to use (>1 = parallel execution)
	 * @param args
	 *            [5] the type of normalization to apply on the local features (no/l2/power+l2).
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String filepath = args[0];
		int numClusters = Integer.parseInt(args[1]);
		int maxIterations = Integer.parseInt(args[2]);
		int seed = Integer.parseInt(args[3]);
		int numSlots = Integer.parseInt(args[4]);
		String normalization = args[5];

		Instances data;
		if (filepath.endsWith(".arff")) { // loading instances from arff file
			System.out.println("--Loading descriptors--");
			BufferedReader reader = new BufferedReader(new FileReader(filepath));
			// Using the Instances class of WEKA to read the dataset
			data = new Instances(reader);
			reader.close();
		} else if (filepath.endsWith(".csv")) { // loading instances from csv file
			CSVLoader loader = new CSVLoader();
			loader.setNoHeaderRowPresent(true);
			loader.setSource(new File(filepath));
			data = loader.getDataSet();
		} else {
			throw new Exception("Wrong dataset format!");
		}

		if (!normalization.equals("no")) { // apply normalization on the features
			System.out.println("--Normalizing descriptors--");
			for (int i = 0; i < data.numInstances(); i++) {
				double[] vector = data.instance(i).toDoubleArray();
				if (normalization.equals("l2")) {
					vector = Normalization.normalizeL2(vector);
				}
				if (normalization.equals("power+l2")) {
					vector = Normalization.normalizePower(vector, power);
					vector = Normalization.normalizeL2(vector);
				}
				for (int j = 0; j < vector.length; j++) {
					data.instance(i).setValue(j, vector[j]);
				}
			}
		}
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
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(filepath + "_codebook-" + data.numAttributes()
				+ "A-" + numClusters + "C-" + maxIterations + "I-" + seed + "S" + "_" + normalization + ".arff")));
		// write the results of the clustering to the new file
		Instances clusterCentroids = clusterer.getClusterCentroids();
		for (int i = 0; i < numClusters; i++) {
			out.write(clusterCentroids.instance(i).toStringNoWeight() + "\n");
		}
		out.close();

		System.out.println("Total execution time: " + (end - start));
	}
}
