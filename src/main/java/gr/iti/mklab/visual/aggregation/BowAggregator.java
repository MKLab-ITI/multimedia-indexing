package gr.iti.mklab.visual.aggregation;

import java.util.ArrayList;

/**
 * This class computes raw (unnormalized) BoW vectors. Also implements a simple soft quantization method.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class BowAggregator extends AbstractFeatureAggregator {

	/**
	 * The number of centroids where each descriptor is quantized.
	 */
	private int k;

	/**
	 * Constructor. Hard quantization (k=1) is used by default.
	 * 
	 * @param codebook
	 */
	public BowAggregator(double[][] codebook) {
		super(codebook);
		k = 1;
	}

	/**
	 * Constructor. Soft quantization for k>1.
	 * 
	 * @param codebook
	 * @param k
	 */
	public BowAggregator(double[][] codebook, int k) {
		super(codebook);
		this.k = k;
	}

	@Override
	protected double[] aggregateInternal(ArrayList<double[]> descriptors) throws Exception {
		double[] bow = new double[numCentroids];
		for (double[] descriptor : descriptors) {
			if (k == 1) {
				int nnIndex = computeNearestCentroid(descriptor);
				bow[nnIndex]++;
			} else {
				int[] nnIndices = computeKNearestCentroids(descriptor, k);
				for (int j = 0; j < k; j++) {
					for (int i = 0; i < descriptorLength; i++) {
						bow[nnIndices[j]]++;
					}
				}
			}
		}
		return bow;
	}

	@Override
	protected double[] aggregateInternal(double[][] descriptors) throws Exception {
		double[] bow = new double[numCentroids];
		for (double[] descriptor : descriptors) {
			if (k == 1) {
				int nnIndex = computeNearestCentroid(descriptor);
				bow[nnIndex]++;
			} else {
				int[] nnIndices = computeKNearestCentroids(descriptor, k);
				for (int j = 0; j < k; j++) {
					for (int i = 0; i < descriptorLength; i++) {
						bow[nnIndices[j]]++;
					}
				}
			}
		}
		return bow;
	}

	@Override
	public int getVectorLength() {
		return numCentroids;
	}

}
