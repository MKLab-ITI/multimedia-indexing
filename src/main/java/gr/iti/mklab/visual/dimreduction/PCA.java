package gr.iti.mklab.visual.dimreduction;

import gr.iti.mklab.visual.utilities.Normalization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.factory.SingularValueDecomposition;
import org.ejml.ops.CommonOps;
import org.ejml.ops.SingularOps;

/**
 * <p>
 * The following class shows how to perform basic principle component analysis in EJML. This class is a
 * modification of the <a
 * href="https://code.google.com/p/efficient-java-matrix-library/wiki/PrincipleComponentAnalysisExample"
 * >class</a> written by Peter Abeles.
 * </p>
 * <p>
 * Principal Component Analysis (PCA) is typically used to develop a linear model for a set of data (e.g. face
 * images) which can then be used to test for membership. PCA works by converting the set of data to a new
 * basis that is a subspace of the original set. The subspace is selected to maximize information.
 * </p>
 * <p>
 * PCA is typically derived as an eigenvalue problem. However in this implementation
 * {@link org.ejml.factory.SingularValueDecomposition SVD} is used since it can produce a more numerically
 * stable solution. Computation using EVD requires explicitly computing the variance of each sample set. The
 * variance is computed by squaring the residual, which can cause loss of precision.
 * </p>
 * <p>
 * The class was extended to allow simultaneous PCA projection and whitening.
 * </p>
 * 
 * @author Peter Abeles
 * @author Elefterios Spyromitros-Xioufis
 */
public class PCA {

	/** how many principle components are used **/
	private int numComponents;

	/** number of elements in each sample **/
	private int sampleSize;

	/** number of samples that will be used for learning the PCA **/
	private int numSamples;

	/** counts the number of currently loaded samples **/
	private int sampleIndex;

	/** where the data is stored **/
	private DenseMatrix64F A = new DenseMatrix64F(1, 1);

	/** mean values of each element across alls samples **/
	private DenseMatrix64F means;

	/** principle component subspace is stored in the rows **/
	private DenseMatrix64F V_t;

	/** a diagonal matrix with the singular values, required if whitening is applied **/
	private DenseMatrix64F W;

	/** whether to apply whitening */
	private boolean doWhitening;

	/** whether to compute the SVD in compact form, false by default **/
	private boolean compact = false;

	/**
	 * Constructor. Whitening is not applied.
	 * 
	 * @param numComponents
	 *            Number of components it will use to describe each sample. Typically much smaller than the
	 *            number of elements in each sample.
	 * @param numSamples
	 *            Number of samples that will be processed. Only required for allocating memory for the data
	 *            array when learning is performed. 1 can be used at projection time.
	 * @param sampleSize
	 *            number of elements in each sample
	 */
	public PCA(int numComponents, int numSamples, int sampleSize) {
		this(numComponents, numSamples, sampleSize, false);
	}

	/**
	 * Constructor.
	 * 
	 * @param numComponents
	 *            Number of components it will use to describe each sample. Typically much smaller than the
	 *            number of elements in each sample.
	 * @param numSamples
	 *            Number of samples that will be processed. Only required for allocating memory for the data
	 *            array when learning is performed. 1 can be used at projection time.
	 * @param sampleSize
	 *            number of elements in each sample
	 * @param doWhitening
	 *            whether to apply whitening
	 */
	public PCA(int numComponents, int numSamples, int sampleSize, boolean doWhitening) {
		if (numComponents > sampleSize) {
			throw new IllegalArgumentException("More components requested than the data's length.");
		}
		this.numComponents = numComponents;
		this.numSamples = numSamples;
		this.sampleSize = sampleSize;
		this.doWhitening = doWhitening;
		A.reshape(numSamples, sampleSize, false);
		sampleIndex = 0;
	}

	/**
	 * Adds a new sample of the raw data to internal data structure for later processing. All the samples must
	 * be added before computeBasis is called.
	 * 
	 * @param sampleData
	 *            sample from original raw data
	 */
	public void addSample(double[] sampleData) {
		if (sampleIndex >= numSamples)
			throw new IllegalArgumentException("Too many samples");
		if (sampleData.length != sampleSize)
			throw new IllegalArgumentException("Unexpected sample size");

		for (int i = 0; i < sampleData.length; i++) {
			A.set(sampleIndex, i, sampleData[i]);
		}
		sampleIndex++;
	}

	/**
	 * Computes a basis (the principle components) from the most dominant eigenvectors.
	 */
	public void computeBasis() {
		if (sampleIndex != numSamples)
			throw new IllegalArgumentException("Not all the data has been added");
		if (numComponents > numSamples)
			throw new IllegalArgumentException("More data needed to compute the desired number of components");

		means = new DenseMatrix64F(sampleSize, 1);
		// compute the mean of all the samples
		for (int i = 0; i < numSamples; i++) {
			for (int j = 0; j < sampleSize; j++) {
				double val = A.get(i, j);
				means.set(j, val);
			}
		}
		for (int j = 0; j < sampleSize; j++) {
			double avg = means.get(j) / numSamples;
			means.set(j, avg);
		}

		// subtract the mean from the original data
		for (int i = 0; i < numSamples; i++) {
			for (int j = 0; j < sampleSize; j++) {
				A.set(i, j, A.get(i, j) - means.get(j));
			}
		}

		// compute SVD and save time by not computing U
		SingularValueDecomposition<DenseMatrix64F> svd = DecompositionFactory.svd(numSamples, sampleSize,
				false, true, compact);
		if (!svd.decompose(A))
			throw new RuntimeException("SVD failed");

		V_t = svd.getV(null, true);
		W = svd.getW(null);

		// singular values are in an arbitrary order initially and need to be sorted in descending order
		SingularOps.descendingOrder(null, false, W, V_t, true);

		// strip off unneeded components and find the basis
		V_t.reshape(numComponents, sampleSize, true);

		// if whitening is true then whiten the PCA matrix V_t by multiplying it with W
		if (doWhitening) {
			DenseMatrix64F V_t_w = new DenseMatrix64F(numComponents, sampleSize);
			CommonOps.mult(W, V_t, V_t_w);
			V_t = V_t_w;
		}
	}

	/**
	 * Converts a vector from sample space into eigen space. If {@link #doWhitening} is true, then the vector
	 * is also L2 normalized after projection.
	 * 
	 * @param sampleData
	 *            Sample space vector
	 * @return Eigen space projected vector
	 */
	public double[] sampleToEigenSpace(double[] sampleData) {
		if (sampleData.length != sampleSize) {
			throw new IllegalArgumentException("Unexpected vector length!");
		}
		DenseMatrix64F sample = new DenseMatrix64F(sampleSize, 1, true, sampleData);
		DenseMatrix64F projectedSample = new DenseMatrix64F(numComponents, 1);
		// mean subtraction
		CommonOps.sub(sample, means, sample);
		// projection
		CommonOps.mult(V_t, sample, projectedSample);
		// whitening
		if (doWhitening) { // always perform this normalization step when whitening is used
			return Normalization.normalizeL2(projectedSample.data);
		} else {
			return projectedSample.data;
		}
	}

	/**
	 * Writes the means, the eigenvalues and the PCA matrix to a text file. The 1st row of the file contains
	 * the training sample means per component, the 2nd row contains the eigenvalues in descending order and
	 * subsequent rows contain contain the eigenvectors in descending eigenvalue order.
	 * 
	 * @param PCAFileName
	 *            the PCA file
	 * @throws Exception
	 */
	public void savePCAToFile(String PCAFileName) throws Exception {
		if (V_t == null) {
			throw new Exception("Cannot save to file, PCA matrix is null!");
		}
		BufferedWriter out = new BufferedWriter(new FileWriter(PCAFileName));
		// the first line of the file contains the training sample means per component
		for (int i = 0; i < sampleSize; i++) {
			out.write(means.get(i) + " ");
		}
		out.write("\n");

		// the second line of the file contains the eigenvalues in descending order
		for (int i = 0; i < numComponents; i++) {
			out.write(W.get(i, i) + " ");
		}
		out.write("\n");

		// the next lines of the file contain the eigenvectors in descending eigenvalue order
		for (int i = 0; i < numComponents; i++) {
			for (int j = 0; j < sampleSize; j++) {
				out.write(V_t.get(i, j) + " ");
			}
			out.write("\n");
		}
		out.close();
	}

	/**
	 * Loads the PCA matrix, means and eigenvalues matrix (if {@link #doWhitening} is true) from the given
	 * file. The file is supposed to be generated by the {@link #savePCAToFile(String)} method.
	 * 
	 * @param PCAFileName
	 *            the PCA file
	 * @throws Exception
	 */
	public void loadPCAFromFile(String PCAFileName) throws Exception {
		// parse the PCA projection file and put the PCA components in a 2-d double array
		BufferedReader in = new BufferedReader(new FileReader(PCAFileName));
		String line = "";

		// parse the first line which contains the training sample means
		line = in.readLine();
		String[] meanString = line.trim().split(" ");
		if (meanString.length != sampleSize) {
			throw new Exception("Means line is wrong!");
		}
		means = new DenseMatrix64F(sampleSize, 1);
		for (int j = 0; j < sampleSize; j++) {
			means.set(j, Double.parseDouble(meanString[j]));
		}

		// parse the first line which contains the eigenvalues and initialize the diagonal eigenvalue matrix W
		line = in.readLine();
		if (doWhitening) {
			String[] eigenvaluesString = line.split(" ");
			if (eigenvaluesString.length < numComponents) {
				throw new Exception("Eigenvalues line is wrong!");
			}
			W = new DenseMatrix64F(numComponents, numComponents);
			for (int i = 0; i < numComponents; i++) {
				// transform the eigenValues
				double eigenvalue = Double.parseDouble(eigenvaluesString[i]);
				eigenvalue = Math.pow(eigenvalue, -0.5);
				W.set(i, i, eigenvalue);
			}
		}

		V_t = new DenseMatrix64F(numComponents, sampleSize);
		for (int i = 0; i < numComponents; i++) {
			try {
				line = in.readLine();
			} catch (IOException e) {
				throw new Exception(
						"Check whether the given PCA matrix contains the correct number of components!");
			}
			String[] componentString = line.trim().split(" ");
			for (int j = 0; j < sampleSize; j++) {
				double componentElement = Double.parseDouble(componentString[j]);
				V_t.set(i, j, componentElement);
			}
		}

		// if whitening is true then whiten the PCA matrix V_t by multiplying it with W
		if (doWhitening) {
			DenseMatrix64F V_t_w = new DenseMatrix64F(numComponents, sampleSize);
			CommonOps.mult(W, V_t, V_t_w);
			V_t = V_t_w;
		}

		in.close();
	}

	public void setCompact(boolean compact) {
		this.compact = compact;
	}
}