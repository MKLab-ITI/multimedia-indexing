package gr.iti.mklab.visual.quantization;

import gr.iti.mklab.visual.datastructures.Linear;

import java.util.ArrayList;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * This class uses the {@link AbstractQuantizerLearning} class to create a coarse quantizer from a set of vectors that
 * are loaded to a {@link Linear} object from a BDB store.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class CoarseQuantizerLearning {

	/**
	 * 
	 * @param args
	 *            [0] full path to the BDB store that contains the learning vectors
	 * @param args
	 *            [1] the dimensionality of the vectors (e.g. 128)
	 * @param args
	 *            [2] the number of the vectors to use for learning the coarse quantizer e.g. 200000
	 * @param args
	 *            [3] the number of clusters (centroids) to use (e.g. 1024)
	 * @param args
	 *            [4] the maximum number of k-means iterations (e.g. 100)
	 * @param args
	 *            [5] the seed given to k-means (e.g. 1)
	 * @param args
	 *            [6] the number of execution slots to use for k-means (>1 = parallel execution)
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String learningVectorsBDB = args[0];
		int vectorLength = Integer.parseInt(args[1]);
		int numLearningVectors = Integer.parseInt(args[2]);
		int numClusters = Integer.parseInt(args[3]);
		int maxIterations = Integer.parseInt(args[4]);
		int seed = Integer.parseInt(args[5]);
		int numSlots = Integer.parseInt(args[6]);

		// we need to load the vectors into an Instances object
		Linear linear = new Linear(vectorLength, numLearningVectors, true, learningVectorsBDB, false, true, 0);
		int numVectorsLoaded = linear.getLoadCounter();

		// creating weka attributes
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		for (int i = 0; i < vectorLength; i++) {
			Attribute attr = new Attribute("feature" + (i + 1));
			attributes.add(attr);
		}

		Instances data = new Instances(learningVectorsBDB, attributes, numVectorsLoaded);
		for (int i = 0; i < numVectorsLoaded; i++) {
			// creating weka instances
			double[] vladVector = linear.getVector(i);
			DenseInstance instance = new DenseInstance(1.0, vladVector);
			data.add(instance);
		}

		String outFilename = learningVectorsBDB + "qcoarse_k" + numClusters + "n_" + numLearningVectors + ".csv";
		AbstractQuantizerLearning.learnAndWriteQuantizer(outFilename, data, numClusters, maxIterations, seed, numSlots);
	}
}
