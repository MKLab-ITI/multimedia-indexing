package gr.iti.mklab.visual.quantization;

/**
 * This class is used for computing residual vectors, given a coarse quantizer.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ResidualVectorComputation {

	private double[][] coarseQuantizer;
	private int vectorLength;
	private int numCentroids;

	public ResidualVectorComputation(double[][] coarseQuantizer, int vectorLength, int numCentroids) throws Exception {
		if (numCentroids != coarseQuantizer.length) {
			throw new Exception("The given number of centroids does not match the number of centroids in the coarse quantizer.");
		}
		if (vectorLength != coarseQuantizer[0].length) {
			throw new Exception("The given vector length does not match with the centroid length.");
		}
		this.coarseQuantizer = coarseQuantizer;
		this.vectorLength = vectorLength;
		this.numCentroids = numCentroids;
	}

	public double[] ComputeResidualVector(double[] vector) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The given vector length does not match with the length of the coarse quantizer's centroids");
		}
		int nearestCentroidIndex = computeNearestCentroid(vector);
		double[] residualVector = new double[vectorLength];
		for (int i = 0; i < vectorLength; i++) {
			residualVector[i] = coarseQuantizer[nearestCentroidIndex][i] - vector[i];
		}
		return residualVector;
	}

	/**
	 * Finds and returns the index of the coarse quantizer's centroid which is closer to the given vector.
	 * 
	 * @param vector
	 * @return
	 */
	private int computeNearestCentroid(double[] vector) {
		int centroidIndex = -1;
		double minDistance = Double.MAX_VALUE;
		for (int i = 0; i < numCentroids; i++) {
			double distance = 0;
			for (int j = 0; j < vectorLength; j++) {
				distance += (coarseQuantizer[i][j] - vector[j]) * (coarseQuantizer[i][j] - vector[j]);
				if (distance >= minDistance) {
					break;
				}
			}
			if (distance < minDistance) {
				minDistance = distance;
				centroidIndex = i;
			}
		}
		return centroidIndex;
	}

}
