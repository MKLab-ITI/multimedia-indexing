package gr.iti.mklab.visual.quantization;

import gr.iti.mklab.visual.aggregation.AbstractFeatureAggregator;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.utilities.RandomPermutation;
import gr.iti.mklab.visual.utilities.RandomRotation;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import weka.clusterers.AbstractClusterer;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

/**
 * This class is used to learn a Product Quantizer from a set of vectors that are stored in a {@link Linear}
 * index (BDB store).
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ProductQuantizationLearning {

	/** the number of k-means executions for each sub-quantizer */
	public static int numKmeansRepeats = 1;

	/**
	 * The various options can be given from the command line.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {

		// create Options object
		Options options = new Options();
		// add options
		options.addOption("path", true,
				"path to the Linear BDB index that contains the training vectors: e.g. C:/lef/BDB_1024/");
		options.addOption("d", true, "the dimensionality of the training vectors: e.g. 128 or 1024");
		options.addOption("m", true,
				"the number of subvectors to be created (d should be divided exactly by m)");
		options.addOption("c", true, "the number of centroids of each subquantizer: e.g. 256 or 1024");
		options.addOption("transform", true,
				"the type of transformation to apply before learning the product quantizer");
		options.addOption("samples", true, "how many learning vectors to use: e.g. 20000");
		options.addOption("i", true, "the maximum number of clustering iterations (default 100).");
		options.addOption("s", true, "the number of parallel execution slots to use in k-means clustering");

		options.addOption("split", false,
				"whether to also split the training vectors before learning the sub-quantizers");
		options.addOption("ivf", false,
				"whether product quantization will be combined with an inverted file (ivf)");
		options.addOption("cqfile", true, "path to the coarse quantizer file");
		options.addOption("cqcentroids", true, "number of coarse quantizer centroids: e.g. 8192");

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			System.exit(0);
		}

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Product quantization learning", options, true);

		// the following two parameters get the default value of false is they are
		// not passed in the console
		boolean split = cmd.hasOption("split");
		if (split) {
			System.out.println("Vectors will be splitted");
		} else {
			System.out.println("Splitting will not be performed");
		}
		String transformationType = null;
		if (cmd.getOptionValue("transform").equals("no")) {
			System.out.println("No transformation will be applied");
			transformationType = "no";
		} else if (cmd.getOptionValue("transform").equals("rr")) {
			System.out.println("Random Rotation will be applied");
			transformationType = "rr";
		} else if (cmd.getOptionValue("transform").equals("rp")) {
			System.out.println("Random Permutation will be applied");
			transformationType = "rp";
		} else {
			throw new Exception("Unsupported transformation type!");
		}
		boolean ivf = cmd.hasOption("ivf");
		String coarseQuantizerFile = "";
		int numCoarseCentroids = 0;
		if (ivf) {
			System.out.println("IVF will be used");
			// parse the other ivf related options
			if (cmd.getOptionValue("cqfile") == null) {
				throw new Exception("IVF selected but coarse quantizer file not given.");
			}
			coarseQuantizerFile = cmd.getOptionValue("cqfile");

			if (cmd.getOptionValue("cqcentroids") == null) {
				throw new Exception("IVF selected but number of coarse quantizer centroids not given.");
			}
			numCoarseCentroids = Integer.parseInt(cmd.getOptionValue("cqcentroids"));
		}

		int numVectors;
		if (cmd.getOptionValue("samples") == null) {
			System.out.println("Using the default 50000 samples for learning.");
			numVectors = 50000;
		} else {
			numVectors = Integer.parseInt(cmd.getOptionValue("samples"));
			System.out.println("Using " + numVectors + " samples for learning.");
		}

		int maxIterations;
		if (cmd.getOptionValue("i") == null) {
			System.out.println("Using the default 100 max iterations.");
			maxIterations = 100;
		} else {
			maxIterations = Integer.parseInt(cmd.getOptionValue("i"));
			System.out.println("Using " + maxIterations + " maximum iterations.");
		}

		String BDBpath = cmd.getOptionValue("path");
		if (cmd.getOptionValue("path") == null) {
			throw new Exception("The path to the training vectors is undefined!");
		}

		int numSlots = 1;
		if (cmd.getOptionValue("s") == null) {
			System.out.println("Using 1 execution slot!");
		}
		numSlots = Integer.parseInt(cmd.getOptionValue("s"));

		int vectorLength;
		if (cmd.getOptionValue("d") == null) {
			throw new Exception("The dimensionality of the training vectors is undefined!");
		}
		vectorLength = Integer.parseInt(cmd.getOptionValue("d"));

		int numProductCentroids;
		if (cmd.getOptionValue("d") == null) {
			throw new Exception("The number of centroids for each sub-quantizer is undefined!");
		}
		numProductCentroids = Integer.parseInt(cmd.getOptionValue("c"));

		int m;
		if (cmd.getOptionValue("m") == null) {
			throw new Exception("The number of sub-vectors is undefined!");
		}
		m = Integer.parseInt(cmd.getOptionValue("m"));
		// checking that m is valid
		int subVectorLength;// the dimensionality of the subvectors
		if (vectorLength % m != 0) { // d is not a multiple of m
			throw new Exception("d is not a multiple of m");
		}
		subVectorLength = vectorLength / m;

		RandomRotation rr = null;
		RandomPermutation rp = null;
		if (transformationType.equals("rr")) {
			rr = new RandomRotation(1, vectorLength);
		} else if (transformationType.equals("rp")) {
			rp = new RandomPermutation(1, vectorLength);
		}

		System.out.println("== Creating subquantizers using " + numVectors + " vectors ==");
		System.out.println("Vector dimensionality: " + vectorLength);
		System.out.println("Sub vector dimensionality: " + subVectorLength);
		System.out.println("Num centroids: " + numProductCentroids);
		System.out.println("Max iterations: " + maxIterations);

		// create a single file to store all the sub-quantizers of the product quantizer
		// construct the filename
		String subquantizersFilename = BDBpath + "/pq_" + vectorLength + "_" + m + "x"
				+ (int) (Math.log(numProductCentroids) / Math.log(2)) + "_" + numVectors;
		if (transformationType.equals("rr")) {
			subquantizersFilename += "_rr";
		} else if (transformationType.equals("rp")) {
			subquantizersFilename += "_rp";
		}
		if (ivf) {
			subquantizersFilename += "_ivf_c" + numCoarseCentroids;
		}
		subquantizersFilename += ".csv";
		BufferedWriter out = new BufferedWriter(new FileWriter(new String(subquantizersFilename)));

		// load the vectors from the Linear index (BDB store)
		Linear vectors = new Linear(vectorLength, numVectors, true, BDBpath, false, true, 0);
		// in case of ivf, load the coarse quantizer so that residual vectors can be computed.
		ResidualVectorComputation res = null;
		if (ivf) {
			double[][] coarseQuantizer = AbstractFeatureAggregator.readQuantizer(coarseQuantizerFile,
					numCoarseCentroids, vectorLength);
			res = new ResidualVectorComputation(coarseQuantizer, vectorLength, numCoarseCentroids);
		}

		// create one Instances object for learning each sub-quantizer
		Instances[] datasets = new Instances[m];
		// creating weka attributes
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		for (int i = 0; i < subVectorLength; i++) {
			Attribute attr = new Attribute("feature" + (i + 1));
			attributes.add(attr);
		}
		for (int i = 0; i < m; i++) {
			datasets[i] = new Instances("subvectors", attributes, numVectors);
		}

		// load the sub-vectors into the corresponding Instances objects
		for (int i = 0; i < numVectors; i++) {
			double[] vector = vectors.getVector(i);
			// compute the residual vector if needed
			if (ivf) {
				vector = res.ComputeResidualVector(vector);
			}
			// apply the appropriate transformation
			if (transformationType.equals("rr")) {
				vector = rr.rotate(vector);
			} else if (transformationType.equals("rp")) {
				vector = rp.permute(vector);
			}
			// load each sub-vector into the corresponding Instances object
			for (int j = 0; j < m; j++) {
				double[] subVector = Arrays.copyOfRange(vector, j * subVectorLength, j * subVectorLength
						+ subVectorLength);
				DenseInstance instance = new DenseInstance(1.0, subVector);
				datasets[j].add(instance);
			}
		}

		// learning the sub-quantizers with k-means clustering
		for (int i = 0; i < m; i++) {
			System.out.println("Learning sub-quantizer " + (i + 1));
			double minSSE = Double.MAX_VALUE;
			SimpleKMeans bestClusterer = null;
			// try k-mean using numKmeansRepeats different random seeds and keep the one with lowest SSE
			for (int j = 0; j < numKmeansRepeats; j++) {
				// Create a new k-means instance
				SimpleKMeans clusterer = new SimpleKMeans();
				clusterer.setInitializationMethod(new SelectedTag(SimpleKMeans.KMEANS_PLUS_PLUS,
						SimpleKMeans.TAGS_SELECTION));
				clusterer.setNumExecutionSlots(numSlots);
				clusterer.setNumClusters(numProductCentroids);
				clusterer.setMaxIterations(maxIterations);
				// clusterer.setDebug(false);
				clusterer.setSeed(j + 1);
				// build the clusterer
				clusterer.buildClusterer(datasets[i]);
				double SSE = clusterer.getSquaredError();
				if (SSE < minSSE) {
					minSSE = SSE;
					bestClusterer = (SimpleKMeans) AbstractClusterer.makeCopy(clusterer);
				}
			}

			System.out.println("Mininum SSE: " + minSSE + " Seed: " + bestClusterer.getSeed());
			System.out.println("Saving best sub-quantizer in file..");
			// write the results of the clustering to the new file (csv formated)
			Instances clusterCentroids = bestClusterer.getClusterCentroids();
			for (int j = 0; j < clusterCentroids.numInstances(); j++) {
				Instance centroid = clusterCentroids.instance(j);
				for (int k = 0; k < centroid.numAttributes() - 1; k++) {
					out.write(centroid.value(k) + ",");
				}
				out.write(centroid.value(centroid.numAttributes() - 1) + "\n");
			}

			// check whether fewer than the desired clusters where generated and add fake centroids in that
			// case
			int numCentroidsMissing = numProductCentroids - clusterCentroids.numInstances();
			// add fake, distant centroids so that no vector is quantized in them
			if (numCentroidsMissing > 0) {
				System.out
						.println("Problem! Number of generated clusters is smaller that the desired one. Use more samples!");
				System.out.println("Non empty clusters: " + clusterCentroids.numInstances() + " instead of: "
						+ numProductCentroids);
				// System.exit(1);
				// Random rand = new Random(1);
				for (int k = 0; k < numCentroidsMissing; k++) {
					// int index = rand.nextInt(dataset.numInstances());
					// String instanceString = dataset.instance(index).toStringNoWeight();
					// outputStream.println(instanceString);
					for (int f = 0; f < subVectorLength - 1; f++) {
						out.write("1000,");
					}
					out.write("1000\n");
				}
			}
			out.flush();
		}
		out.close();

	}
}
