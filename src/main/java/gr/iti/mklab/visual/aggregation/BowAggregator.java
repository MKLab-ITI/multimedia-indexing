package gr.iti.mklab.visual.aggregation;

import java.util.ArrayList;

/**
 * This class computes raw BOW vectors. The produced vectors should be (optionally) power and L2 normalized
 * afterwards.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class BowAggregator extends DescriptorAggregator {

	/**
	 * The number of centroids where each descriptor is quantized. Default = 1 means hard quantization.
	 */
	private int k = 1;

	public BowAggregator(double[][] codebook) {
		super(codebook);
	}

	public BowAggregator(double[][] codebook, int k) {
		super(codebook);
		this.k = k;
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
	public int getVectorLength() {
		return numCentroids;
	}

}
