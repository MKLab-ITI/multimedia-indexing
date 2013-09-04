package gr.iti.mklab.visual.dimreduction;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.factory.SingularValueDecomposition;
import org.ejml.ops.CommonOps;
import org.ejml.ops.NormOps;
import org.ejml.ops.SingularOps;

import gr.iti.mklab.visual.utilities.Normalization;


/**
 * <p>
 * The following is a simple example of how to perform basic principle component analysis in EJML.
 * </p>
 * 
 * <p>
 * Principal Component Analysis (PCA) is typically used to develop a linear model for a set of data (e.g. face
 * images) which can then be used to test for membership. PCA works by converting the set of data to a new
 * basis that is a subspace of the original set. The subspace is selected to maximize information.
 * </p>
 * <p>
 * PCA is typically derived as an eigenvalue problem. However in this implementation
 * {@link org.ejml.alg.dense.decomposition.SingularValueDecomposition SVD} is since it can produce a more
 * numerically stable solution. Computation using EVD requires explicitly computing the variance of each
 * sample set. The variance is computed by squaring the residual, which can cause loss of precision.
 * </p>
 * 
 * <p>
 * Usage:<br>
 * 1) call setup()<br>
 * 2) For each sample (e.g. an image ) call addSample()<br>
 * 3) After all the samples have been added call computeBasis()<br>
 * 4) Call sampleToEigenSpace() , eigenToSampleSpace() , errorMembership() , response()
 * </p>
 * 
 * @author Peter Abeles
 * @author Elefterios Spyromitros-Xioufis
 */
public class PrincipalComponentAnalysis implements Serializable {

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

	/** mean values of each element across all the samples **/
	double mean[];

	/** principle component subspace is stored in the rows **/
	private DenseMatrix64F V_t;

	/** a diagonal matrix with the singular values **/
	private DenseMatrix64F W;

	/**
	 * Whether to perform whitening.
	 */
	private boolean doWhitening;

	private boolean compact;

	public void setCompact(boolean compact) {
		this.compact = compact;
	}

	/**
	 * @param numComponents
	 *            Number of vectors it will use to describe the data. Typically much smaller than the number
	 *            of elements in the input vector.
	 * @param numSamples
	 *            Number of samples that will be processed (only important at learning).
	 * @param sampleSize
	 *            Number of elements in each sample.
	 */
	public PrincipalComponentAnalysis(int numComponents, int numSamples, int sampleSize) {
		if (numComponents > sampleSize)
			throw new IllegalArgumentException("More components requested that the data's length.");

		this.numComponents = numComponents;
		this.numSamples = numSamples;
		this.sampleSize = sampleSize;
		mean = new double[sampleSize];
		A.reshape(numSamples, sampleSize, false);
		sampleIndex = 0;
		doWhitening = false;
	}

	/**
	 * Adds a new sample of the raw data to internal data structure for later processing. All the samples must
	 * be added before computeBasis is called.
	 * 
	 * @param sampleData
	 *            Sample from original raw data.
	 */
	public void addSample(double[] sampleData) {
		if (A.getNumCols() != sampleData.length)
			throw new IllegalArgumentException("Unexpected sample size");
		if (sampleIndex >= A.getNumRows())
			throw new IllegalArgumentException("Too many samples");

		for (int i = 0; i < sampleData.length; i++) {
			A.set(sampleIndex, i, sampleData[i]);
		}
		sampleIndex++;
	}

	/**
	 * Computes a basis (the principle components) from the most dominant eigenvectors.
	 */
	public void computeBasis() {
		if (sampleIndex != A.getNumRows())
			throw new IllegalArgumentException("Not all the data has been added");
		if (numComponents > numSamples)
			throw new IllegalArgumentException("More data needed to compute the desired number of components");

		// compute the mean of all the samples
		for (int i = 0; i < A.getNumRows(); i++) {
			for (int j = 0; j < mean.length; j++) {
				mean[j] += A.get(i, j);
			}
		}
		for (int j = 0; j < mean.length; j++) {
			mean[j] /= A.getNumRows();
		}

		// subtract the mean from the original data
		for (int i = 0; i < A.getNumRows(); i++) {
			for (int j = 0; j < mean.length; j++) {
				A.set(i, j, A.get(i, j) - mean[j]);
			}
		}

		// Compute SVD and save time by not computing U
		SingularValueDecomposition<DenseMatrix64F> svd = DecompositionFactory.svd(A.numRows, A.numCols, false, true, compact);
		if (!svd.decompose(A))
			throw new RuntimeException("SVD failed");

		V_t = svd.getV(null, true);
		W = svd.getW(null);

		// Singular values are in an arbitrary order initially
		SingularOps.descendingOrder(null, false, W, V_t, true);

		// strip off unneeded components and find the basis
		V_t.reshape(numComponents, sampleSize, true);
	}

	/**
	 * Converts a vector from sample space into eigen space. If {@link #setEigenvalues(double[])} has been
	 * called, then the projected vector is also whitened.
	 * 
	 * @param sampleData
	 *            Sample space data.
	 * @return Eigen space projection.
	 */
	public double[] sampleToEigenSpace(double[] sampleData) {
		if (sampleData.length != sampleSize) {
			throw new IllegalArgumentException("Unexpected sample length");
		}
		DenseMatrix64F mean = DenseMatrix64F.wrap(sampleSize, 1, this.mean);

		DenseMatrix64F s = new DenseMatrix64F(sampleSize, 1, true, sampleData);
		DenseMatrix64F r = new DenseMatrix64F(numComponents, 1);

		CommonOps.sub(s, mean, s);

		CommonOps.mult(V_t, s, r);

		if (doWhitening) { // always perform this normalization step when whitening is used
			return Normalization.normalizeL2(r.data);
		} else {
			return r.data;
		}
	}

	public void setDoWhitening(boolean doWhitening) {
		this.doWhitening = doWhitening;
	}

	public void setMean(double[] mean) {
		this.mean = mean;
	}

	public double[] getMean() {
		return mean;
	}

	/**
	 * Sets the PCA basis matrix.
	 * 
	 * @param basis
	 *            the basis matrix
	 */
	public void setBasisMatrix(double[][] basis) {
		V_t = new DenseMatrix64F(basis.length, basis[0].length);
		for (int i = 0; i < basis.length; i++) {
			for (int j = 0; j < basis[0].length; j++) {
				V_t.set(i, j, basis[i][j]);
			}
		}
	}

	/**
	 * Returns a vector from the PCA's basis.
	 * 
	 * @param which
	 *            Which component's vector is to be returned.
	 * @return Vector from the PCA basis.
	 */
	public double[] getBasisVector(int which) {
		if (which < 0 || which >= numComponents)
			throw new IllegalArgumentException("Invalid component");

		DenseMatrix64F v = new DenseMatrix64F(1, A.numCols);
		CommonOps.extract(V_t, which, which + 1, 0, A.numCols, v, 0, 0);

		return v.data;
	}

	/**
	 * Initializes the diagonal eigenvalue matrix {@link #W} by using the supplied vector and then whitens the
	 * projection matrix {@link #V_t}
	 * 
	 * @param eigenvalues
	 * @throws Exception
	 */
	public void setEigenvalues(double[] eigenvalues) throws Exception {
		if (eigenvalues.length != numComponents) {
			throw new Exception("Wrong number of eigenvalues.");
		}
		W = new DenseMatrix64F(numComponents, numComponents);
		for (int i = 0; i < numComponents; i++) {
			// transform the eigenValues
			eigenvalues[i] = Math.pow(eigenvalues[i], -0.5);
			W.set(i, i, eigenvalues[i]);
		}
		DenseMatrix64F V_t_w = new DenseMatrix64F(numComponents, sampleSize);
		CommonOps.mult(W, V_t, V_t_w);
		V_t = V_t_w.copy();
	}

	/**
	 * Returns a vector with the eignevalues in descending order.
	 * 
	 * @return vector of eigenValues in descending order.
	 */
	public double[] getEigenValues() {
		if (W == null) {
			throw new IllegalArgumentException("Diagonal eigenvalues matrix is null");
		}
		double[] eigenValues = new double[numComponents];
		for (int i = 0; i < numComponents; i++) {
			eigenValues[i] = W.get(i, i);
		}
		return eigenValues;
	}

	/**
	 * Initializes the PCA matrix, means vector and optionally eigenvalues matrix from the given file.
	 * 
	 * @param PCAFileName
	 *            the learning file
	 * @param hasEigenvalues
	 *            whether the eigenvalues are also stored in the file.
	 * @throws Exception
	 */
	public void setPCAFromFile(String PCAFileName) throws Exception {
		// parse the PCA projection file and put the PCA components in a 2-d double array
		BufferedReader in = new BufferedReader(new FileReader(PCAFileName));
		double[][] PCAcomponents = new double[numComponents][sampleSize];
		double[] means = new double[sampleSize];
		double[] eigenvalues = new double[numComponents];
		String line = "";
		// parse the first line which contains the training sample means this is needed for the projection to
		// eigenspace because we first subtract the means
		line = in.readLine();
		String[] meanString = line.trim().split(" ");
		if (meanString.length != sampleSize) {
			in.close();
			throw new Exception("Means line is wrong!");
		}
		for (int j = 0; j < sampleSize; j++) {
			means[j] = Double.parseDouble(meanString[j]);
		}

		line = in.readLine();
		if (doWhitening) {
			String[] eigenvaluesString = line.split(" ");
			for (int i = 0; i < numComponents; i++) {
				eigenvalues[i] = Double.parseDouble(eigenvaluesString[i]);
			}
		}

		for (int i = 0; i < numComponents; i++) {
			try {
				line = in.readLine();
			} catch (IOException e) {
				in.close();
				throw new Exception("Check whether the given PCA projection matrix contains the correct number of components!");
			}
			String[] componentString = line.trim().split(" ");
			PCAcomponents[i] = new double[sampleSize];
			for (int j = 0; j < sampleSize; j++) {
				PCAcomponents[i][j] = Double.parseDouble(componentString[j]);
			}
		}

		setMean(means);
		setBasisMatrix(PCAcomponents);
		if (doWhitening) {
			setEigenvalues(eigenvalues);
		}

		in.close();
	}

	public void setPCAFromFile(Configuration conf, Path PCAFileName) throws IOException {
		// parse the PCA projection file and put the PCA components in a 2-d
		// double array
		FileSystem hdfs = FileSystem.get(conf);
		DataInputStream d = new DataInputStream(hdfs.open(PCAFileName));
		BufferedReader in = new BufferedReader(new InputStreamReader(d));
		double[][] PCAcomponents = new double[numComponents][sampleSize];
		// the supplied file should contain the at least numComponents
		// eigenvectors
		String line = "";
		// parse the first line which contains the training sample means
		// this is needed for the projection to eigenspace because we first
		// subtract the means
		line = in.readLine();
		String[] meanString = line.trim().split(" ");
		for (int j = 0; j < meanString.length; j++) {
			mean[j] = Double.parseDouble(meanString[j]);
		}
		setMean(mean);
		for (int i = 0; i < numComponents; i++) {
			// try {
			line = in.readLine();
			// } catch (IOException e) {
			// System.out
			// .println("Check whether the given PCA projection matrix contains the correct number of components!");
			// System.out.println(e);
			// System.exit(0);
			// }
			String[] compString = line.trim().split(" ");
			PCAcomponents[i] = new double[compString.length];
			for (int j = 0; j < compString.length; j++) {
				PCAcomponents[i][j] = Double.parseDouble(compString[j]);
			}
		}
		setBasisMatrix(PCAcomponents);
		in.close();
	}

	/**
	 * Converts a vector from eigen space into sample space.
	 * 
	 * @param eigenData
	 *            Eigen space data.
	 * @return Sample space projection.
	 */
	public double[] eigenToSampleSpace(double[] eigenData) {
		if (eigenData.length != numComponents)
			throw new IllegalArgumentException("Unexpected sample length");

		DenseMatrix64F s = new DenseMatrix64F(A.getNumCols(), 1);
		DenseMatrix64F r = DenseMatrix64F.wrap(numComponents, 1, eigenData);

		CommonOps.multTransA(V_t, r, s);

		DenseMatrix64F mean = DenseMatrix64F.wrap(A.getNumCols(), 1, this.mean);
		CommonOps.add(s, mean, s);

		return s.data;
	}

	/**
	 * <p>
	 * The membership error for a sample. If the error is less than a threshold then it can be considered a
	 * member. The threshold's value depends on the data set.
	 * </p>
	 * <p>
	 * The error is computed by projecting the sample into eigenspace then projecting it back into sample
	 * space and
	 * </p>
	 * 
	 * @param sampleA
	 *            The sample whose membership status is being considered.
	 * @return Its membership error.
	 */
	public double errorMembership(double[] sampleA) {
		double[] eig = sampleToEigenSpace(sampleA);
		double[] reproj = eigenToSampleSpace(eig);

		double total = 0;
		for (int i = 0; i < reproj.length; i++) {
			double d = sampleA[i] - reproj[i];
			total += d * d;
		}

		return Math.sqrt(total);
	}

	/**
	 * Computes the dot product of each basis vector against the sample. Can be used as a measure for
	 * membership in the training sample set. High values correspond to a better fit.
	 * 
	 * @param sample
	 *            Sample of original data.
	 * @return Higher value indicates it is more likely to be a member of input dataset.
	 */
	public double response(double[] sample) {
		if (sample.length != A.numCols)
			throw new IllegalArgumentException("Expected input vector to be in sample space");

		DenseMatrix64F dots = new DenseMatrix64F(numComponents, 1);
		DenseMatrix64F s = DenseMatrix64F.wrap(A.numCols, 1, sample);

		CommonOps.mult(V_t, s, dots);

		return NormOps.normF(dots);
	}

}