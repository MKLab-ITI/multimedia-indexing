package gr.iti.mklab.visual.datastructures;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TShortArrayList;
import gr.iti.mklab.visual.aggregation.DescriptorAggregator;
import gr.iti.mklab.visual.utilities.RandomTransformation;
import gr.iti.mklab.visual.utilities.Result;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;


import com.aliasi.util.BoundedPriorityQueue;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * This class implements IVFADC (Indexed Very Fast Asymmetric Distance Computation). It can be used for
 * indexing and searching a database according to IVFADC approach.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class IVFADC extends AbstractSearchStructure {

	private long coarseQuantizationTime;
	private long productQuantizationTime;
	private long persistentIndexUpdateTime;

	/**
	 * The coarse quantizer.
	 * 
	 * A two dimensional table storing the coarse-quantizers. The 1st dimension goes from
	 * 1...numCoarseCentroids and indexes the centroids of the coarse quantizer. The 2nd dimension goes from
	 * 1...vectorLength and indexes the dimensions of each centroid.
	 */
	private double[][] coarseQuantizer;

	// /**
	// * Whether to apply l2 normalization prior to coarse quantization.
	// */
	// private boolean l2BeforeCoarseQuantization;
	//
	// /**
	// * Whether to apply l2 normalization on the residuals prior to product quantization.
	// */
	// private boolean l2BeforeProductQuantization;

	/**
	 * Whether to apply random orthogonal transformation on the residuals prior to product quantization.
	 */
	private boolean randomBeforeProductQuantization;

	/**
	 * The sub-quantizers. They are needed for performing kNN queries using the ADC approach.
	 * 
	 * A three dimensional table storing the sub-quantizers of the product quantizer. The first dimension goes
	 * from 1...numSubquantizers and indexes the sub-quantizers. The second dimension goes from
	 * 1...numProductCentroids and indexes the centroids of each sub-quantizer of the product quantizer. The
	 * third dimension goes from 1...subVectorLength and indexes the dimensions of each centroid of each
	 * sub-quantizer of the product quantizer.
	 */
	private double[][][] productQuantizer;

	/**
	 * The number of sub-vectors used in this product quantization.
	 */
	private int numSubVectors;

	/**
	 * The number of centroids used to quantize each sub-vector. (Depending on this number we can use a
	 * different type for storing the quantization code of each sub-vector. E.g. For k=256=2^8 centroids we
	 * need 8 bits, for k=1024=2^10 we need 10 bits. Currently we use a short for the quantization sub-codes,
	 * so 16 bits)
	 * 
	 */
	private int numProductCentroids;

	/**
	 * Number of centroids in the coarse quantizer
	 */
	private int numCoarseCentroids;

	/**
	 * The length of each subvector of the residual vectors.
	 */
	private int subVectorLength;

	/**
	 * The number of lists to visit.
	 */
	private int w;

	/**
	 * This object is used for applying random orthogonal transformation prior to product quantization.
	 */
	private RandomTransformation randomTransform;

	/**
	 * This vector of TByteArrayList objects is used to store the inverted lists which contain indexed
	 * vectors. All lists are constructed with an equal initial capacity which is equal to the maximum number
	 * of vectors that we want to index, divided by the number of lists. This is done in order to avoid list
	 * expansions which can increase the indexing time.
	 * 
	 */
	private TByteArrayList[] invertedListByteVectors;

	private TShortArrayList[] invertedListShortVectors;

	private TIntArrayList[] invertedListIds;

	/**
	 * Berkeley db for persistence of the IVFADC index
	 */
	private Database ivfadcBDB;

	/**
	 * 
	 * Advanced constructor.
	 * 
	 * 
	 * @param vectorLength
	 *            The dimensionality of the vectors.
	 * @param numCoarseCentroids
	 * @param subVectorLength
	 *            The subvector length.
	 * @param numProductCentroids
	 *            The number of product centroids.
	 * @param randomTransformation
	 *            Whether to perform random transformation.
	 * @param loadCounter
	 *            The initial value of the loadCounter.
	 * @param maxNumVectors
	 *            The maximum number of vectors.
	 * @param BDBEnvHome
	 *            The BDB environment home directory.
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens).
	 * @param countSizeOnLoad
	 *            Whether the loadCounter will be initialized by the size of the persistent store.
	 * 
	 * @throws Exception
	 */
	public IVFADC(int vectorLength, int numCoarseCentroids, int subVectorLength, int numProductCentroids, boolean randomTransformation, int loadCounter, int maxNumVectors, String BDBEnvHome,
			boolean readOnly, boolean countSizeOnLoad) throws Exception {
		super(vectorLength, loadCounter, maxNumVectors, readOnly, countSizeOnLoad);

		createOrOpenBDBEnvAndDbs(BDBEnvHome);

		this.numCoarseCentroids = numCoarseCentroids;
		this.numSubVectors = vectorLength / subVectorLength;
		this.numProductCentroids = numProductCentroids;
		this.subVectorLength = subVectorLength;
		this.randomBeforeProductQuantization = randomTransformation;
		if (randomTransformation) {
			this.randomTransform = new RandomTransformation(1, vectorLength);
		}
		// configuration of the persistent index
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(true);
		dbConf.setAllowCreate(true);// db will be created if it does not exist
		// create/open the db using config
		ivfadcBDB = dbEnv.openDatabase(null, "ivfadc", dbConf);

		if (countSizeOnLoad) {// count and print the size of the db
			int persistentIndexSize = (int) ivfadcBDB.count();
			System.out.println("Persistent index size: " + persistentIndexSize);
			if (idToNameBDB.count() != persistentIndexSize) {
				throw new Exception("Persistent index size and mapping db size are different!");
			}
		}

		// by default set w to 10% of the lists
		w = (int) (numCoarseCentroids * 0.1);

		productQuantizer = new double[numSubVectors][numProductCentroids][subVectorLength];
		coarseQuantizer = new double[numCoarseCentroids][vectorLength];

		invertedListIds = new TIntArrayList[numCoarseCentroids];
		int initialListCapacity = (int) ((double) (1.0 * maxNumVectors) / numCoarseCentroids);
		System.out.println("Calculated list size " + initialListCapacity);

		if (numProductCentroids <= 256) {
			invertedListByteVectors = new TByteArrayList[numCoarseCentroids];
		} else {
			invertedListShortVectors = new TShortArrayList[numCoarseCentroids];
		}

		for (int i = 0; i < numCoarseCentroids; i++) {
			// fixed initial size for each list, allows space efficiency measurements
			if (numProductCentroids <= 256) {
				invertedListByteVectors[i] = new TByteArrayList(initialListCapacity * numSubVectors);
			} else {
				invertedListShortVectors[i] = new TShortArrayList(initialListCapacity * numSubVectors);
			}
			invertedListIds[i] = new TIntArrayList(initialListCapacity);
			// no initial size set, allows more efficient space usage
			// invertedListVectors[i] = new TByteArrayList();
			// invertedListIds[i] = new TIntArrayList();
		}
		// load the existing persistent index in memory
		loadIndexInMemory();

	}

	/**
	 * Simple constructor.
	 * 
	 * @param vectorLength
	 * @param numCoarseCentroids
	 * @param subVectorLength
	 * @param numProductCentroids
	 * @param randomTransformation
	 * @param maxNumVectors
	 * @param BDBEnvHome
	 * @throws Exception
	 */
	public IVFADC(int vectorLength, int numCoarseCentroids, int subVectorLength, int numProductCentroids, boolean randomTransformation, int maxNumVectors, String BDBEnvHome) throws Exception {
		this(vectorLength, numCoarseCentroids, subVectorLength, numProductCentroids, randomTransformation, 0, maxNumVectors, BDBEnvHome, false, true);
	}

	/**
	 * 
	 * @param k
	 *            the number of nearest neighbors to return
	 * @param query
	 *            the non quantized query vector
	 * @param w
	 *            the number of list to be searched for neighbors (equal to the number of nearest coarse
	 *            centroids where the query vector is assigned)
	 * @return
	 * @throws Exception
	 */
	private BoundedPriorityQueue<Result> computeKNN_IVFADC(int k, double[] query, int w) throws Exception {
		// long start = System.nanoTime();
		// this implements a minheap structure of fixed capacity
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// quantize to the k closest centroids of the coarse quantizer
		// long start = System.nanoTime();
		int[] nearestCoarseCentroidIndices = computeNearestCoarseIndices(query, w);
		// System.out.println("Coarse quantization: " + (double) (System.nanoTime() - start) / 1000000 +
		// " ms");

		// int counter = 0;
		// long totalResCompTime = 0;
		// long totalLookupTime = 0;
		// long totalDbScanTime = 0;

		for (int i = 0; i < w; i++) { // for each assignment
			int coarseIndex = nearestCoarseCentroidIndices[i];
			// compute the residual vector
			// start = System.nanoTime();
			double[] queryResidual = computeResidualVector(query, coarseIndex);
			// totalResCompTime += System.nanoTime() - start;
			// apply l2 normalization on the residual vector (as for the indexed vectors), usually false
			// if (l2BeforeProductQuantization) {
			// queryResidual = Normalization.normalizeL2(queryResidual);
			// }
			// apply random orthogonal transformation (as for the indexed vectors)
			if (randomBeforeProductQuantization) {
				queryResidual = randomTransform.transform(queryResidual);
			}
			// compute lookup table
			// start = System.nanoTime();
			double[][] lookUpTable = computeLookupADC(queryResidual);
			// totalLookupTime += System.nanoTime() - start;

			// start = System.nanoTime();
			for (int j = 0; j < invertedListIds[coarseIndex].size(); j++) {
				// counter++;
				// boolean skip = false;
				int id = invertedListIds[coarseIndex].getQuick(j);
				double l2distance = 0;
				if (numProductCentroids <= 256) {
					byte[] pqCode = new byte[numSubVectors];
					for (int m = 0; m < numSubVectors; m++) {
						pqCode[m] = invertedListByteVectors[coarseIndex].getQuick(j * numSubVectors + m);
					}
					for (int m = 0; m < pqCode.length; m++) {
						// plus 128 because byte range is -128..127
						l2distance += lookUpTable[m][pqCode[m] + 128];
						// if (l2distance > lowest) {
						// skip = true;
						// break;
						// }
					}
					// if (skip) {
					// continue;
					// }
				} else {
					short[] pqCode = new short[numSubVectors];
					for (int m = 0; m < numSubVectors; m++) {
						pqCode[m] = invertedListShortVectors[coarseIndex].getQuick(j * numSubVectors + m);
					}
					for (int m = 0; m < pqCode.length; m++) {
						l2distance += lookUpTable[m][pqCode[m]];
						// if (l2distance > lowest) {
						// skip = true;
						// break;
						// }
					}
					// if (skip) {
					// continue;
					// }
				}
				nn.offer(new Result(id, l2distance));
				// only update if we haven loaded k vectors in the nn list
				// if (counter > k) {
				// lowest = nn.last().getDistance();
				// }
			}
			// totalDbScanTime += System.nanoTime() - start;

		}

		// System.out.println("Residual vector computation: " + (double) totalResCompTime / 1000000 + " ms");
		// System.out.println("Lookup computation: " + (double) totalLookupTime / 1000000 + " ms");
		// System.out.println("DB scan: " + (double) totalResCompTime / 1000000 + " ms");

		return nn;
	}

	/**
	 * Takes a query residual vector as input and returns a look-up table containing the distance between each
	 * sub-vector of the query vector from each centroid of each sub-quantizer. The calculation of this
	 * look-up table requires numSubquantizers*kSubquantizer*subVectorLength multiplications. After this
	 * calculation, the distance between the query and any vector in the database can be computed in constant
	 * time (with numSubquantizers additions).
	 * 
	 * @param queryVector
	 *            (residual)
	 * @return a lookup table of size numSubquantizers * kSubquantizer with the distance of each su-bvector
	 *         from the kSubquantizer centroids of each sub-quantizer
	 */
	private double[][] computeLookupADC(double[] queryVector) {
		double[][] distances = new double[numSubVectors][numProductCentroids];

		for (int i = 0; i < numSubVectors; i++) {
			int subvectorStart = i * subVectorLength;
			for (int j = 0; j < numProductCentroids; j++) {
				for (int k = 0; k < subVectorLength; k++) {
					distances[i][j] += (queryVector[subvectorStart + k] - productQuantizer[i][j][k]) * (queryVector[subvectorStart + k] - productQuantizer[i][j][k]);
				}
			}
		}
		return distances;
	}

	/**
	 * Finds and returns the index of the coarse quantizer's centroid which is closer to the given vector.
	 * 
	 * @param vector
	 * @return
	 */
	private int computeNearestCoarseIndex(double[] vector) {
		int centroidIndex = -1;
		double minDistance = Double.MAX_VALUE;
		for (int i = 0; i < numCoarseCentroids; i++) {
			double distance = 0;
			for (int j = 0; j < vectorLength; j++) {
				distance += (coarseQuantizer[i][j] - vector[j]) * (coarseQuantizer[i][j] - vector[j]);
				// when distance becomes greater than minDistance
				// break the inner loop and check the next centroid!!!
				if (distance >= minDistance) {
					// large speed-up for large vectors and many centroids!!!
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
	 * Returns the indices of the k coarse centroids which are closer to the given vector. Fast implementation
	 * with a bounded priority queue.
	 * 
	 * @param vector
	 * @param k
	 * @return
	 */
	protected int[] computeNearestCoarseIndices(double[] vector, int k) {
		BoundedPriorityQueue<Result> bpq = new BoundedPriorityQueue<Result>(new Result(), k);

		// double lowest = Double.MAX_VALUE;
		// int counter = 0;
		for (int i = 0; i < numCoarseCentroids; i++) {
			// counter++;
			// boolean skip = false;
			double l2distance = 0;
			for (int j = 0; j < vectorLength; j++) {
				l2distance += (coarseQuantizer[i][j] - vector[j]) * (coarseQuantizer[i][j] - vector[j]);
				// if (l2distance > lowest) {
				// skip = true;
				// break;
				// }
			}
			// if (skip) {
			// continue;
			// }
			bpq.offer(new Result(i, l2distance));
			// if (counter > k) {
			// lowest = bpq.last().getDistance();
			// }
		}
		int[] nn = new int[k];
		for (int i = 0; i < k; i++) {
			nn[i] = bpq.poll().getInternalId();
		}
		return nn;
	}

	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] query) throws Exception {
		return computeKNN_IVFADC(k, query, w);
	}

	/**
	 * Finds and returns the index of the centroid of the subquantizer with the given index which is closer to
	 * the given subvector.
	 * 
	 * @param subvector
	 * @param subQuantizerIndex
	 * @return
	 */
	private int computeNearestProductIndex(double[] subvector, int subQuantizerIndex) {
		int centroidIndex = -1;
		double minDistance = Double.MAX_VALUE;
		for (int i = 0; i < numProductCentroids; i++) {
			double distance = 0;
			for (int j = 0; j < subVectorLength; j++) {
				distance += (productQuantizer[subQuantizerIndex][i][j] - subvector[j]) * (productQuantizer[subQuantizerIndex][i][j] - subvector[j]);
				// break if distance becomes greater than minDistance
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

	private double[] computeResidualVector(double[] vector, int centroidIndex) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The given vector length does not match with the length of the coarse quantizer's centroids");
		}
		double[] residualVector = new double[vectorLength];
		for (int i = 0; i < vectorLength; i++) {
			residualVector[i] = coarseQuantizer[centroidIndex][i] - vector[i];
		}
		return residualVector;
	}

	public double[][] getCoarseQuantizer() {
		return coarseQuantizer;
	}

	public double[][][] getProductQuantizer() {
		return productQuantizer;
	}

	/**
	 * Update the index with the given VLAD vector.Depending on how the coarse and product quantizers are
	 * learned, we should apply the appropriate transformations in each step.
	 * 
	 * @param id
	 * @param vector
	 * @throws Exception
	 */
	public void indexVectorInternal(int id, double[] vector, Transaction txn) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The supplied vector has the wrong number of dimensions!");
		}

		// quantize to the closest centroid of the coarse quantizer
		long start = System.currentTimeMillis();
		int nearestCoarseCentroidIndex = computeNearestCoarseIndex(vector);
		coarseQuantizationTime += System.currentTimeMillis() - start;
		// compute residual vector
		double[] residualVector = new double[vectorLength];
		for (int i = 0; i < vectorLength; i++) {
			residualVector[i] = coarseQuantizer[nearestCoarseCentroidIndex][i] - vector[i];
		}
		// apply l2 normalization on the residual vector, usually false
		// if (l2BeforeProductQuantization) {
		// residualVector = Normalization.normalizeL2(residualVector);
		// }
		// apply random orthogonal transformation
		if (randomBeforeProductQuantization) {
			residualVector = randomTransform.transform(residualVector);
		}

		// transform the residual vector into a pq code
		start = System.currentTimeMillis();
		if (numProductCentroids <= 256) {
			byte[] pqCode = new byte[numSubVectors];
			for (int i = 0; i < numSubVectors; i++) {
				// first, we take the appropriate sub-vector
				int fromIdex = i * subVectorLength;
				int toIndex = fromIdex + subVectorLength;
				double[] subvector = Arrays.copyOfRange(residualVector, fromIdex, toIndex);
				// next, assign the sub-vector to the nearest centroid of the respective sub-quantizer minus
				// 128 because byte range is -128..127
				pqCode[i] = (byte) (computeNearestProductIndex(subvector, i) - 128);
			}
			productQuantizationTime += System.currentTimeMillis() - start;

			// add a new entry to the corresponding inverted list
			invertedListByteVectors[nearestCoarseCentroidIndex].add(pqCode);
			invertedListIds[nearestCoarseCentroidIndex].add(id);
			// update the persistent index
			start = System.currentTimeMillis();
			updatePersistentIndex(id, nearestCoarseCentroidIndex, pqCode, txn);
			persistentIndexUpdateTime += System.currentTimeMillis() - start;
		} else {
			short[] pqCode = new short[numSubVectors];
			for (int i = 0; i < numSubVectors; i++) {
				// first, we take the appropriate sub-vector
				int fromIdex = i * subVectorLength;
				int toIndex = fromIdex + subVectorLength;
				double[] subvector = Arrays.copyOfRange(residualVector, fromIdex, toIndex);
				// next, assign the sub-vector to the nearest centroid of the respective sub-quantizer minus
				// 128 because byte range is -128..127
				pqCode[i] = (short) (computeNearestProductIndex(subvector, i));
			}
			productQuantizationTime += System.currentTimeMillis() - start;

			// add a new entry to the corresponding inverted list
			invertedListShortVectors[nearestCoarseCentroidIndex].add(pqCode);
			invertedListIds[nearestCoarseCentroidIndex].add(id);
			// update the persistent index
			start = System.currentTimeMillis();
			updatePersistentIndex(id, nearestCoarseCentroidIndex, pqCode, txn);
			persistentIndexUpdateTime += System.currentTimeMillis() - start;
		}
	}

	/**
	 * This method can be called to output indexing time measurements.
	 */
	public void outputIndexingTimesInternal() {
		System.out.println((double) coarseQuantizationTime / loadCounter + " ms => coarseQuantizationTime");
		System.out.println((double) productQuantizationTime / loadCounter + " ms => productQuantizationTime");
		System.out.println((double) persistentIndexUpdateTime / loadCounter + " ms => persistentIndexUpdateTime");

	}

	public void outputItemsPerList() {
		// find the max value
		int max = 0;
		int min = Integer.MAX_VALUE;
		double sum = 0;
		for (int i = 0; i < numCoarseCentroids; i++) {
			// System.out.println("List " + (i + 1) + ": " +
			// perListLoadCounter[i]);
			if (invertedListIds[i].size() > max) {
				max = invertedListIds[i].size();
			}
			if (invertedListIds[i].size() < min) {
				min = invertedListIds[i].size();
			}
			sum += invertedListIds[i].size();
		}

		System.out.println("Maximum number of vectors: " + max);
		System.out.println("Minimum number of vectors: " + min);
		System.out.println("Average number of vectors: " + (sum / numCoarseCentroids));

	}

	/**
	 * Load the coarse quantizers from a given file
	 * 
	 * @param filname
	 * @throws IOException
	 */
	public void loadCoarseQuantizer(String coarseQuantizerFile) throws IOException {
		coarseQuantizer = DescriptorAggregator.readCodebookFile(coarseQuantizerFile, numCoarseCentroids, vectorLength);
	}

	/**
	 * Load the product quantizer from a given file
	 * 
	 * @param filname
	 * @throws IOException
	 */
	public void loadProductQuantizer(String filname) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(new File(filname)));
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
	 * With this method we initialize the table holding the coarse quantizers.
	 * 
	 * @param coarseQuantizer
	 */
	public void setCoarseQuantizer(double[][] coarseQuantizer) {
		this.coarseQuantizer = coarseQuantizer;
	}

	/**
	 * With this method we initialize the table holding the product quantizer.
	 * 
	 * @param productQuantizer
	 */
	public void setProductQuantizer(double[][][] productQuantizer) {
		this.productQuantizer = productQuantizer;
	}

	public void setW(int w) {
		this.w = w;
	}

	/**
	 * The previous example shows how to scan through the records in your database sequentially; that is, in
	 * the record's sort order. This is mostly determined by the value contained in the records' keys
	 * (additional sorting is required in the case of duplicate records). However, you can use cursors to
	 * retrieve records based on how they are stored on disk. This can improve retrieval times, and is useful
	 * if your application needs to scan all the records in the database quickly, without concern for key sort
	 * order. You do this using the DiskOrderedCursor class.
	 * 
	 * @throws Exception
	 * 
	 */
	private void loadIndexInMemory() throws Exception {
		System.out.println("Loading persistent index in memory.");
		long start = System.currentTimeMillis();

		DatabaseEntry foundKey = new DatabaseEntry();
		DatabaseEntry foundData = new DatabaseEntry();

		// disk ordered cursor
		// DiskOrderedCursorConfig docc = new DiskOrderedCursorConfig();
		// DiskOrderedCursor dcursor = ivfadcBDB.openCursor(docc);
		// standard cursor
		Cursor cursor = ivfadcBDB.openCursor(null, null);
		// To iterate, just call getNext() until the last database record has
		// been read. All cursor operations return an OperationStatus, so just
		// read until we no longer see OperationStatus.SUCCESS
		int counter = 0;
		// disk ordered cursor iteration
		// while (dcursor.getNext(foundKey, foundData, null) == OperationStatus.SUCCESS
		// && counter < maxNumVectors) {
		// standard cursor iteration
		while (cursor.getNext(foundKey, foundData, LockMode.DEFAULT) == OperationStatus.SUCCESS && counter < maxNumVectors) {
			// getData() on the DatabaseEntry objects returns the byte array
			// held by that object. We use this to get a String value. If the
			// DatabaseEntry held a byte array representation of some other
			// data type (such as a complex object) then this operation would
			// look considerably different.
			int id = IntegerBinding.entryToInt(foundKey);
			TupleInput input = TupleBinding.entryToInput(foundData);
			int listID = input.readInt();
			// update ram based index
			invertedListIds[listID].add(id);

			if (numProductCentroids <= 256) {
				byte[] code = new byte[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readByte();
				}
				// update ram based index
				invertedListByteVectors[listID].add(code);
			} else {
				short[] code = new short[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readShort();
				}
				// update ram based index
				invertedListShortVectors[listID].add(code);
			}
			counter++;
			if (counter % 1000 == 0) {
				System.out.println(counter + " images loaded in memory!");
			}
		}
		// dcursor.close();
		cursor.close();
		long end = System.currentTimeMillis();
		System.out.println(counter + " images loaded in " + (end - start) + " ms!");
	}

	private void updatePersistentIndex(int id, int listId, byte[] code, Transaction txn) {
		// write id, listId and code
		TupleOutput output = new TupleOutput();
		output.writeInt(listId);
		for (int i = 0; i < numSubVectors; i++) {
			output.writeByte(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		ivfadcBDB.put(txn, key, data);
	}

	private void updatePersistentIndex(int id, int listId, short[] code, Transaction txn) {
		// write id, listId and code
		TupleOutput output = new TupleOutput();
		output.writeInt(listId);
		for (int i = 0; i < numSubVectors; i++) {
			output.writeShort(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		ivfadcBDB.put(txn, key, data);
	}

	@Override
	public void closeInternal() {
		ivfadcBDB.close();
	}

	@Override
	public void syncDBinternal() {
	}

	@Override
	protected boolean deleteVectorInternal(int id) {
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		ivfadcBDB.delete(null, key);
		return true;
	}

	@Override
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int internalId) throws Exception {
		throw new Exception("This method is not supported by the IVFADC structure!");
	}

}
