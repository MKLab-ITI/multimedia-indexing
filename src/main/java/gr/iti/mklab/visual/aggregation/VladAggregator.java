package gr.iti.mklab.visual.aggregation;

import java.util.ArrayList;

/**
 * This class computes raw (unnormalized) VLAD vectors as described in:
 * 
 * <pre>
 * H. Jegou, F. Perronnin, M. Douze, J. Sanchez, P. Perez, and C. Schmid, Aggregating local image descriptors into compact codes, 
 * IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 34, no. 9, pp. 17041716, 2012.
 * </pre>
 * 
 * The produced vectors should be power and L2 normalized afterwards.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class VladAggregator extends AbstractFeatureAggregator {

	/**
	 * Constructor. Calls the super constructor.
	 * 
	 * @param codebook
	 */
	public VladAggregator(double[][] codebook) {
		super(codebook);
	}

	/**
	 * Takes as input an ArrayList of double arrays which contains the set of local descriptors for an image.
	 * Returns the VLAD vector representation of the image using the codebook supplied in the constructor.
	 * 
	 * @param descriptors
	 * @return the VLAD vector
	 * @throws Exception
	 */
	protected double[] aggregateInternal(ArrayList<double[]> descriptors) {
		double[] vlad = new double[numCentroids * descriptorLength];
		if (descriptors.size() == 0) { // when there are 0 local descriptors extracted
			return vlad;
		}
		for (double[] descriptor : descriptors) {
			int nnIndex = computeNearestCentroid(descriptor);
			for (int i = 0; i < descriptorLength; i++) {
				vlad[nnIndex * descriptorLength + i] += descriptor[i] - codebook[nnIndex][i];
			}
		}
		return vlad;
	}

	/**
	 * Same as {@link #aggregateInternal(ArrayList)} but takes a two-dimensional array as input.
	 * 
	 * @param descriptors
	 * @return the VLAD vector
	 * @throws Exception
	 */
	protected double[] aggregateInternal(double[][] descriptors) {
		double[] vlad = new double[numCentroids * descriptorLength];
		// when there are 0 local descriptors extracted
		if (descriptors.length == 0) {
			return vlad;
		}

		for (double[] descriptor : descriptors) {
			int nnIndex = computeNearestCentroid(descriptor);
			for (int i = 0; i < descriptorLength; i++) {
				vlad[nnIndex * descriptorLength + i] += descriptor[i] - codebook[nnIndex][i];
			}
		}
		return vlad;
	}

	@Override
	public int getVectorLength() {
		return numCentroids * descriptorLength;
	}
}
