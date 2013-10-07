package gr.iti.mklab.visual.datastructures;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TShortArrayList;
import gr.iti.mklab.visual.utilities.RandomPermutation;
import gr.iti.mklab.visual.utilities.RandomRotation;
import gr.iti.mklab.visual.utilities.Result;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

import com.aliasi.util.BoundedPriorityQueue;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.ForwardCursor;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

/**
 * This class implements indexing using Product Quantization (PQ) and search using Asymmetric (ADC) or
 * Symmetric (SDC) Distance Computation.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class PQ extends AbstractSearchStructure {

	/**
	 * BDB store for persistent storage of the ADC index.
	 */
	private Database iidToPqDB;

	/**
	 * The number of sub-vectors.
	 */
	private int numSubVectors;

	/**
	 * The length of each subvector (= vectorLength/numSubVectors).
	 */
	private int subVectorLength;

	/**
	 * The number of centroids used to quantize each sub-vector. (Depending on this number we use a different
	 * type for storing the quantization code of each sub-vector. For k<=256=2^8 centroids we use a byte (8
	 * bits per subvector), for k>256 we use a short (16 bits per subvector).
	 * 
	 */
	private int numProductCentroids;

	/**
	 * The product-quantization codes for all vectors are stored in this list if the code can fit in the byte
	 * range.
	 */
	private TByteArrayList pqByteCodes;

	/**
	 * The product-quantization codes for all vector are stored in this list if the code cannot fit in the
	 * byte range.
	 */
	private TShortArrayList pqShortCodes;

	/**
	 * The sub-quantizers of the product quantizer. They are needed for indexing and search using PQ.<br>
	 * 
	 * A three dimensional array storing the sub-quantizers of the product quantizer. The first dimension goes
	 * from 1..numSubquantizers and indexes the sub-quantizers. The second dimension goes from
	 * 1..numProductCentroids and indexes the centroids of each sub-quantizer of the product quantizer. The
	 * third dimension goes from 1...subVectorLength and indexes the components of each centroid.
	 */
	private double[][][] productQuantizer;

	/**
	 * The supported transformation types.
	 */
	public enum TransformationType {
		None, RandomRotation, RandomPermutation
	}

	/**
	 * The type of transformation to perform on the vectors prior to product quantization.
	 */
	private TransformationType transformation;

	/**
	 * This object is used for applying random permutation prior to product quantization.
	 */
	private RandomPermutation rp;

	/**
	 * This object is used for applying random rotation prior to product quantization.
	 */
	private RandomRotation rr;

	/**
	 * The seed used in random transformations. Should be the same as the one used at learning time.
	 */
	public final int seed = 1;

	/**
	 * Whether to use a disk ordered cursor or not. This setting changes how fast the index will be loaded in
	 * main memory.
	 */
	public final boolean useDiskOrderedCursor = false;

	/**
	 * Advanced constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 * @param BDBEnvHome
	 *            The BDB environment home directory
	 * @param numSubVectors
	 *            The number of subvectors
	 * @param numProductCentroids
	 *            The number of centroids used to quantize each sub-vector
	 * @param transformation
	 *            The type of transformation to perform on each vector
	 * @param countSizeOnLoad
	 *            Whether the load counter will be initialized by the size of the persistent store
	 * @param loadCounter
	 *            The initial value of the load counter
	 * @throws Exception
	 */
	public PQ(int vectorLength, int maxNumVectors, boolean readOnly, String BDBEnvHome, int numSubVectors, int numProductCentroids, TransformationType transformation, boolean countSizeOnLoad,
			int loadCounter) throws Exception {
		super(vectorLength, maxNumVectors, readOnly, countSizeOnLoad, loadCounter);
		this.numSubVectors = numSubVectors;
		if (vectorLength % numSubVectors > 0) {
			throw new Exception("The given number of subvectors is not valid!");
		}
		this.subVectorLength = vectorLength / numSubVectors;
		this.numProductCentroids = numProductCentroids;
		this.transformation = transformation;

		if (transformation == TransformationType.RandomRotation) {
			this.rr = new RandomRotation(seed, vectorLength);
		} else if (transformation == TransformationType.RandomPermutation) {
			this.rp = new RandomPermutation(seed, vectorLength);
		}

		createOrOpenBDBEnvAndDbs(BDBEnvHome);

		// configuration of the persistent index
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(transactional);
		dbConf.setAllowCreate(true); // db will be created if it does not exist
		iidToPqDB = dbEnv.openDatabase(null, "adc", dbConf); // create/open the db using config

		if (numProductCentroids <= 256) {
			pqByteCodes = new TByteArrayList(maxNumVectors * numSubVectors);
		} else {
			pqShortCodes = new TShortArrayList(maxNumVectors * numSubVectors);
		}
		// load any existing persistent index in memory
		loadIndexInMemory();
	}

	/**
	 * Simple constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 * @param BDBEnvHome
	 *            The BDB environment home directory
	 * @param numSubVectors
	 *            The number of subvectors
	 * @param numProductCentroids
	 *            The number of centroids used to quantize each sub-vector
	 * @param transformation
	 *            The type of transformation to perform on each vector
	 * @throws Exception
	 */
	public PQ(int vectorLength, int maxNumVectors, boolean readOnly, String BDBEnvHome, int numSubVectors, int numProductCentroids, TransformationType transformation) throws Exception {
		this(vectorLength, maxNumVectors, readOnly, BDBEnvHome, numSubVectors, numProductCentroids, transformation, true, 0);
	}

	/**
	 * Load a product quantizer from the given file.
	 * 
	 * @param filename
	 *            Full path to the file containing the product quantizer
	 * @throws Exception
	 */
	public void loadProductQuantizer(String filename) throws Exception {
		productQuantizer = new double[numSubVectors][numProductCentroids][subVectorLength];
		BufferedReader in = new BufferedReader(new FileReader(new File(filename)));
		for (int i = 0; i < numSubVectors; i++) {
			for (int j = 0; j < numProductCentroids; j++) {
				String line = in.readLine();
				String[] centroidString = line.split(",");
				for (int k = 0; k < subVectorLength; k++) {
					productQuantizer[i][j][k] = Double.parseDouble(centroidString[k]);
				}
			}
		}
		in.close();
	}

	/**
	 * Append the PQ index with the given vector.
	 * 
	 * @param vector
	 *            The vector to be indexed
	 * @throws Exception
	 */
	protected void indexVectorInternal(double[] vector) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The dimensionality of the vector is wrong!");
		}
		// apply a random transformation if needed
		if (transformation == TransformationType.RandomRotation) {
			vector = rr.rotate(vector);
		} else if (transformation == TransformationType.RandomPermutation) {
			vector = rp.permute(vector);
		}
		// transform the vector into a PQ code
		int[] pqCode = new int[numSubVectors];

		for (int i = 0; i < numSubVectors; i++) {
			// take the appropriate sub-vector
			int fromIdex = i * subVectorLength;
			int toIndex = fromIdex + subVectorLength;
			double[] subvector = Arrays.copyOfRange(vector, fromIdex, toIndex);
			// assign the sub-vector to the nearest centroid of the respective sub-quantizer
			pqCode[i] = computeNearestProductIndex(subvector, i);
		}

		if (numProductCentroids <= 256) {
			byte[] pqByteCode = transformToByte(pqCode);
			pqByteCodes.add(pqByteCode); // append the ram-based index
			appendPersistentIndex(pqByteCode); // append the disk-based index
		} else {
			short[] pqShortCode = transformToShort(pqCode);
			pqShortCodes.add(pqShortCode); // append the ram-based index
			appendPersistentIndex(pqShortCode); // append the disk-based index
		}
	}

	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] query) throws Exception {
		return computeKnnADC(k, query);
	}

	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int internalId) throws Exception {
		return computeKnnSDC(k, internalId);
	}

	/**
	 * Computes and returns the k nearest neighbors of the query vector using the ADC approach.
	 * 
	 * @param k
	 *            The number of nearest neighbors to be returned
	 * @param qVector
	 *            The query vector
	 * @return A bounded priority queue of Result objects, which contains the k nearest neighbors along with
	 *         their iids and distances from the query vector, ordered by lowest distance.
	 */
	private BoundedPriorityQueue<Result> computeKnnADC(int k, double[] qVector) {
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// apply a random transformation if needed
		if (transformation == TransformationType.RandomRotation) {
			qVector = rr.rotate(qVector);
		} else if (transformation == TransformationType.RandomPermutation) {
			qVector = rp.permute(qVector);
		}

		// compute the lookup table
		double[][] lookUpTable = computeLookupADC(qVector);

		for (int i = 0; i < loadCounter; i++) {
			double l2distance = 0;
			int codeStart = i * numSubVectors;
			if (numProductCentroids <= 256) {
				byte[] pqCode = pqByteCodes.toArray(codeStart, numSubVectors);
				for (int j = 0; j < pqCode.length; j++) {
					// plus 128 because byte range is -128..127
					l2distance += lookUpTable[j][pqCode[j] + 128];
				}
			} else {
				short[] pqCode = pqShortCodes.toArray(codeStart, numSubVectors);
				for (int j = 0; j < pqCode.length; j++) {
					l2distance += lookUpTable[j][pqCode[j]];
				}
			}
			nn.offer(new Result(i, l2distance));
		}

		return nn;
	}

	/**
	 * Computes and returns the k nearest neighbors of the query internal id using the SDC approach.
	 * 
	 * @param k
	 *            The number of nearest neighbors to be returned
	 * @param iid
	 *            The internal id of the query vector (code actually)
	 * @return A bounded priority queue of Result objects, which contains the k nearest neighbors along with
	 *         their iids and distances from the query vector, ordered by lowest distance.
	 */
	private BoundedPriorityQueue<Result> computeKnnSDC(int k, int iid) {
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);
		// find the product quantization code of the vector with the given id, i.e the centroid indices
		int[] pqCodeQuery = new int[numSubVectors];
		for (int m = 0; m < numSubVectors; m++) {
			if (pqByteCodes != null) {
				pqCodeQuery[m] = pqByteCodes.getQuick(iid * numSubVectors + m);
			} else {
				pqCodeQuery[m] = pqShortCodes.getQuick(iid * numSubVectors + m);
			}
		}

		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < loadCounter; i++) {
			double l2distance = 0;
			for (int j = 0; j < numSubVectors; j++) {
				int pqSubCode = pqByteCodes.getQuick(i * numSubVectors + j);
				int pqSubCodeQuery = pqCodeQuery[j];
				if (pqByteCodes != null) {
					// plus 128 because byte range is -128..127
					pqSubCode += 128;
					pqSubCodeQuery += 128;
				}
				for (int m = 0; m < subVectorLength; m++) {
					l2distance += (productQuantizer[j][pqSubCode][m] - productQuantizer[j][pqSubCodeQuery][m]) * (productQuantizer[j][pqSubCode][m] - productQuantizer[j][pqSubCodeQuery][m]);
					if (l2distance > lowest) {
						break; // break the inner loop
					}
				}
				if (l2distance > lowest) {
					break; // break the outer loop
				}
			}
			nn.offer(new Result(i, l2distance));
			if (i >= k) {
				lowest = nn.last().getDistance();
			}
		}
		return nn;
	}

	/**
	 * Takes a query vector as input and returns a lookup table containing the distance between each
	 * sub-vector from each centroid of the corresponding sub-quantizer. The calculation of this look-up table
	 * requires numSubVectors*numProductCentroids*subVectorLength multiplications. After this calculation, the
	 * distance between the query and any vector in the database can be computed in constant time.
	 * 
	 * @param qVector
	 *            The query vector
	 * @return A lookup table of size numSubVectors * numProductCentroids with the distance of each sub-vector
	 *         from the centroids of each sub-quantizer
	 */
	private double[][] computeLookupADC(double[] qVector) {
		double[][] distances = new double[numSubVectors][numProductCentroids];
		for (int i = 0; i < numSubVectors; i++) {
			int subVectorStart = i * subVectorLength;
			for (int j = 0; j < numProductCentroids; j++) {
				for (int k = 0; k < subVectorLength; k++) {
					distances[i][j] += (qVector[subVectorStart + k] - productQuantizer[i][j][k]) * (qVector[subVectorStart + k] - productQuantizer[i][j][k]);
				}
			}
		}
		return distances;
	}

	/**
	 * Finds and returns the index of the centroid of the subquantizer with the given index which is closer to
	 * the given subvector.
	 * 
	 * @param subvector
	 *            The subvector
	 * @param subQuantizerIndex
	 *            The index of the the subquantizer
	 * @return The index of the nearest centroid
	 */
	private int computeNearestProductIndex(double[] subvector, int subQuantizerIndex) {
		int centroidIndex = -1;
		double minDistance = Double.MAX_VALUE;
		for (int i = 0; i < numProductCentroids; i++) {
			double distance = 0;
			for (int j = 0; j < subVectorLength; j++) {
				distance += (productQuantizer[subQuantizerIndex][i][j] - subvector[j]) * (productQuantizer[subQuantizerIndex][i][j] - subvector[j]);
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

	/**
	 * Loads the persistent index in memory.
	 * 
	 * @throws Exception
	 */
	private void loadIndexInMemory() throws Exception {
		long start = System.currentTimeMillis();
		System.out.println("Loading persistent index in memory.");

		DatabaseEntry foundKey = new DatabaseEntry();
		DatabaseEntry foundData = new DatabaseEntry();

		ForwardCursor cursor = null;
		if (useDiskOrderedCursor) { // disk ordered cursor
			DiskOrderedCursorConfig docc = new DiskOrderedCursorConfig();
			cursor = iidToPqDB.openCursor(docc);
		} else {
			cursor = iidToPqDB.openCursor(null, null);
		}

		int counter = 0;
		while (cursor.getNext(foundKey, foundData, LockMode.DEFAULT) == OperationStatus.SUCCESS && counter < maxNumVectors) {
			TupleInput input = TupleBinding.entryToInput(foundData);

			if (numProductCentroids <= 256) {
				byte[] code = new byte[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readByte();
				}
				pqByteCodes.add(code); // update ram based index
			} else {
				short[] code = new short[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readShort();
				}
				pqShortCodes.add(code); // update ram based index
			}
			counter++;
			if (counter % 1000 == 0) {
				System.out.println(counter + " vectors loaded in memory!");
			}
		}
		cursor.close();
		long end = System.currentTimeMillis();
		System.out.println(counter + " vectors loaded in " + (end - start) + " ms!");
	}

	/**
	 * Appends the persistent index with the given (byte) code.
	 * 
	 * @param code
	 *            The code
	 */
	private void appendPersistentIndex(byte[] code) {
		// write id and code
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < numSubVectors; i++) {
			output.writeByte(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(loadCounter, key);
		iidToPqDB.put(null, key, data);
	}

	/**
	 * Appends the persistent index with the given (short) code.
	 * 
	 * @param code
	 *            The code
	 */
	private void appendPersistentIndex(short[] code) {
		// write id and code
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < numSubVectors; i++) {
			output.writeShort(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(loadCounter, key);
		iidToPqDB.put(null, key, data);
	}

	@Override
	public void outputIndexingTimesInternal() {
	}

	@Override
	public void closeInternal() {
		iidToPqDB.close();
	}

	public String toString() {
		String output = "Printing the first 10 indexed vectors.\n";
		for (int i = 0; i < 10; i++) {
			output += i + " : ";
			for (int j = 0; j < numSubVectors; j++) {
				output += pqByteCodes.getQuick(i * numSubVectors + j) + " ";
			}
			output += "\n";
		}
		return output;
	}

	public static short[] transformToShort(int[] code) {
		short[] shortCode = new short[code.length];
		for (int i = 0; i < code.length; i++) {
			shortCode[i] = (short) code[i];
		}
		return shortCode;
	}

	public static byte[] transformToByte(int[] code) {
		byte[] byteCode = new byte[code.length];
		for (int i = 0; i < code.length; i++) {
			byteCode[i] = (byte) (code[i] - 128); // -128 because byte range is -128..127
		}
		return byteCode;
	}

}
