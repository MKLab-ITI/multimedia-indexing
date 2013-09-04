package gr.iti.mklab.visual.datastructures;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TShortArrayList;
import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.RandomTransformation;
import gr.iti.mklab.visual.utilities.Result;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


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
 * This class implements ADC (Asymmetric Distance Computation). It can be used for indexing and searching a
 * database according to ADC approach.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ADC extends AbstractSearchStructure {

	private long productQuantizationTime;
	private long persistentIndexUpdateTime;

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
	 * The length of each subvector.
	 */
	private int subVectorLength;

	/**
	 * The product-quantization codes for all vector are stored in this list if the code can fit in the byte
	 * range.
	 */
	private TByteArrayList pqByteCodes;

	/**
	 * The product-quantization codes for all vector are stored in this list if the code cannot fit in the
	 * byte range.
	 */
	private TShortArrayList pqShortCodes;

	/**
	 * The ids of the corresponding images are stored in this list.
	 */
	private TIntArrayList ids;

	/**
	 * Whether to apply random orthogonal transformation on the vectors prior to product quantization.
	 */
	private boolean randomTransformation;

	/**
	 * This object is used for applying random orthogonal transformation prior to product quantization.
	 */
	private RandomTransformation randomTransform;

	/**
	 * Berkeley db for persistence of the ADC index
	 */
	private Database adcBDB;

	/**
	 * Advanced constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the vectors.
	 * @param subVectorLength
	 *            The subvector length.
	 * @param numProductCentroids
	 *            The number of product centroids.
	 * @param randomTransformation
	 *            Whether to perform random transformation.
	 * @param maxNumVectors
	 *            The maximum number of vectors.
	 * @param loadCounter
	 *            The initial value of the loadCounter.
	 * @param BDBEnvHome
	 *            The BDB environment home directory.
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens).
	 * @param countSizeOnLoad
	 *            Whether the loadCounter will be initialized by the size of the persistent store.
	 * @throws Exception
	 */
	public ADC(int vectorLength, int subVectorLength, int numProductCentroids, boolean randomTransformation, int maxNumVectors, int loadCounter, String BDBEnvHome, boolean readOnly,
			boolean countSizeOnLoad) throws Exception {
		super(vectorLength, loadCounter, maxNumVectors, readOnly, countSizeOnLoad);

		createOrOpenBDBEnvAndDbs(BDBEnvHome);

		// configuration of the persistent index
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(true);		
		//EnvironmentConfig.setCacheSize(96 * 1024);
		
		// db will be created if it does not exist
		dbConf.setAllowCreate(true);
		// create/open the db using config
		adcBDB = dbEnv.openDatabase(null, "adc", dbConf);

		if (countSizeOnLoad) {// count and print the size of the db
			int persistentIndexSize = (int) adcBDB.count();
			System.out.println("Persistent index size: " + persistentIndexSize);
			if (idToNameMappings != persistentIndexSize) {
				throw new Exception("Persistent index size: " + persistentIndexSize + " != mapping db size: " + idToNameMappings + " !");
			}
		}

		this.subVectorLength = subVectorLength;
		this.numSubVectors = vectorLength / subVectorLength;
		this.numProductCentroids = numProductCentroids;
		this.randomTransformation = randomTransformation;
		if (randomTransformation) {
			// seed should be the same as the one used during learning the pq
			this.randomTransform = new RandomTransformation(1, vectorLength);
		}
		productQuantizer = new double[numSubVectors][numProductCentroids][subVectorLength];
		if (numProductCentroids <= 256) {
			pqByteCodes = new TByteArrayList(maxNumVectors * numSubVectors);
		} else {
			pqShortCodes = new TShortArrayList(maxNumVectors * numSubVectors);
		}
		ids = new TIntArrayList(maxNumVectors);

		loadIndexInMemory();
	}

	/**
	 * Simple constructor.
	 * 
	 * @param vectorLength
	 * @param subVectorLength
	 * @param numProductCentroids
	 * @param randomTransformation
	 * @param maxNumVectors
	 * @param BDBEnvHome
	 * @throws Exception
	 */
	public ADC(int vectorLength, int subVectorLength, int numProductCentroids, boolean randomTransformation, int maxNumVectors, String BDBEnvHome) throws Exception {
		this(vectorLength, subVectorLength, numProductCentroids, randomTransformation, maxNumVectors, 0, BDBEnvHome, false, true);
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
		// DiskOrderedCursor dcursor = adcBDB.openCursor(docc);
		// standard cursor
		Cursor cursor = adcBDB.openCursor(null, null);
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
			if (numProductCentroids <= 256) {
				byte[] code = new byte[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readByte();
				}
				// update ram based index
				pqByteCodes.add(code);
			} else {
				short[] code = new short[numSubVectors];
				for (int i = 0; i < numSubVectors; i++) {
					code[i] = input.readShort();
				}
				// update ram based index
				pqShortCodes.add(code);
			}
			ids.add(id);
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

	/**
	 * Append the pqCodes and ids arrays with the code and the id of the given vector.
	 * 
	 * @param id
	 * @param vector
	 * @throws Exception
	 */
	protected void indexVectorInternal(int id, double[] vector, Transaction txn) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The supplied vector has the wrong number of dimensions!");
		}
		// apply random orthogonal transformation
		if (randomTransformation) {
			vector = randomTransform.transform(vector);
		}
		// transform the vector into a pq code
		long start = System.currentTimeMillis();
		if (numProductCentroids <= 256) {
			byte[] pqCode = new byte[numSubVectors];
			for (int i = 0; i < numSubVectors; i++) {
				// first, we take the appropriate sub-vector
				int fromIdex = i * subVectorLength;
				int toIndex = fromIdex + subVectorLength;
				double[] subvector = Arrays.copyOfRange(vector, fromIdex, toIndex);
				// next, assign the sub-vector to the nearest centroid of the respective sub-quantizer minus
				// 128
				// because byte range is -128..127
				pqCode[i] = (byte) (computeNearestProductIndex(subvector, i) - 128);
			}
			// append the pqCodes list
			pqByteCodes.add(pqCode);
			// update the persistent index
			start = System.currentTimeMillis();
			updatePersistentIndex(id, pqCode, txn);
		} else {
			short[] pqCode = new short[numSubVectors];
			for (int i = 0; i < numSubVectors; i++) {
				// first, we take the appropriate sub-vector
				int fromIdex = i * subVectorLength;
				int toIndex = fromIdex + subVectorLength;
				double[] subvector = Arrays.copyOfRange(vector, fromIdex, toIndex);
				pqCode[i] = (short) (computeNearestProductIndex(subvector, i));
			}
			// append the pqCodes list
			pqShortCodes.add(pqCode);
			// update the persistent index
			start = System.currentTimeMillis();
			updatePersistentIndex(id, pqCode, txn);
		}
		persistentIndexUpdateTime += System.currentTimeMillis() - start;
		productQuantizationTime += System.currentTimeMillis() - start;

		// append the ids list
		ids.add(id);

	}

	private void updatePersistentIndex(int id, byte[] code, Transaction txn) {
		// write id and code
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < numSubVectors; i++) {
			output.writeByte(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		adcBDB.put(txn, key, data);
	}

	private void updatePersistentIndex(int id, short[] code, Transaction txn) {
		// write id and code
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < numSubVectors; i++) {
			output.writeShort(code[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		adcBDB.put(txn, key, data);
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

	/**
	 * Takes a query vector as input and returns a look-up table containing the distance between each
	 * sub-vector of the query vector from each centroid of each sub-quantizer. The calculation of this
	 * look-up table requires numSubquantizers*kSubquantizer*subVectorLength multiplications. After this
	 * calculation, the distance between the query and any vector in the database can be computed in constant
	 * time.
	 * 
	 * @param queryVector
	 * @return a lookup table of size numSubquantizers * kSubquantizer with the distance of each sub-vector
	 *         from the kSubquantizer centroids of each sub-quantizer
	 */
	public double[][] computeLookupADC(double[] queryVector) {
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

	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] query) throws Exception {
		return computeKNN_ADC(k, query);
	}

	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int internalId) throws Exception {
		return computeKNN_SDC(k, internalId);
	}

	private BoundedPriorityQueue<Result> computeKNN_SDC(int k, int internalId) {
		// this implements a minheap structure of fixed capacity
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);
		// first find the product quantization code of the vector with the given id, i.e the centroid indices
		byte[] pqCodeQueryByte = null;
		short[] pqCodeQueryShort = null;
		if (pqByteCodes != null) {
			pqCodeQueryByte = new byte[numSubVectors];
			for (int m = 0; m < numSubVectors; m++) {
				pqCodeQueryByte[m] = pqByteCodes.getQuick(internalId * numSubVectors + m);
			}
		} else {
			pqCodeQueryShort = new short[numSubVectors];
			for (int m = 0; m < numSubVectors; m++) {
				pqCodeQueryShort[m] = pqShortCodes.getQuick(internalId * numSubVectors + m);
			}
		}

		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < loadCounter; i++) {
			int id = ids.getQuick(i);
			double l2distance = 0;
			if (pqByteCodes != null) {
				for (int j = 0; j < numSubVectors; j++) {
					int pqCode = pqByteCodes.getQuick(i * numSubVectors + j) + 128;
					int pqCodeQuery = pqCodeQueryByte[j] + 128;
					for (int m = 0; m < subVectorLength; m++) {
						l2distance += (productQuantizer[j][pqCode][m] - productQuantizer[j][pqCodeQuery][m]) * (productQuantizer[j][pqCode][m] - productQuantizer[j][pqCodeQuery][m]);
						if (l2distance > lowest) {
							break; // break the inner loop
						}
					}
					if (l2distance > lowest) {
						break; // break the outer loop
					}
				}
			} else {
				for (int j = 0; j < numSubVectors; j++) {
					int pqCode = pqShortCodes.getQuick(i * numSubVectors + j);
					int pqCodeQuery = pqCodeQueryShort[j];
					for (int m = 0; m < subVectorLength; m++) {
						l2distance += productQuantizer[j][pqCode][m] - productQuantizer[j][pqCodeQuery][m];
						if (l2distance > lowest) {
							break; // break the inner loop
						}
					}
					if (l2distance > lowest) {
						break; // break the outer loop
					}
				}
			}

			nn.offer(new Result(id, l2distance));
			if (i > k) {
				lowest = nn.last().getDistance();
			}
		}
		return nn;
	}

	private BoundedPriorityQueue<Result> computeKNN_ADC(int k, double[] queryVector) {
		// long start = System.nanoTime();
		// this implements a minheap structure of fixed capacity
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// long start = System.nanoTime();
		// apply random orthogonal transformation
		if (randomTransformation) {
			queryVector = randomTransform.transform(queryVector);
		}
		// System.out.println("Random transformation: " + (double) (System.nanoTime() - start) / 1000000);

		// start = System.nanoTime();
		double[][] lookUpTable = computeLookupADC(queryVector);
		// System.out.println("Lookup table creation: " + (double) (System.nanoTime() - start) / 1000000);

		// start = System.nanoTime();
		for (int i = 0; i < loadCounter; i++) {
			int id = ids.getQuick(i);
			int codeStart = i * numSubVectors;
			double l2distance = 0;
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
			nn.offer(new Result(id, l2distance));
		}
		// System.out.println("Exhaustive scearch: " + (double) (System.nanoTime() - start) / 1000000);

		return nn;
	}

	@Override
	public void closeInternal() {
		adcBDB.close();
	}

	@Override
	public void syncDBinternal() {
	}

	/**
	 * This method can be called to output indexing time measurements.
	 */
	public void outputIndexingTimesInternal() {
		System.out.println((double) productQuantizationTime / loadCounter + " ms => productQuantizationTime");
		System.out.println((double) persistentIndexUpdateTime / loadCounter + " ms => persistentIndexUpdateTime");

	}

	@Override
	protected boolean deleteVectorInternal(int id) {
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		adcBDB.delete(null, key);
		return true;
	}

	public String toString() {
		String output = "Printing the first 10 indexed vectors.\n";
		for (int i = 0; i < 10; i++) {
			output += ids.getQuick(i) + " : ";
			for (int j = 0; j < numSubVectors; j++) {
				output += pqByteCodes.getQuick(i * numSubVectors + j) + " ";
			}
			output += "\n";
		}
		return output;

	}
	
	public static void main(String[] args) {
		
		String id = "Twitter::351692666301468672";
		
		String learningFolder = "/home/manosetro/Desktop/learning_files";
        String indexLocation = "/home/manosetro/Desktop/webIndex";
        
        String codebookFile = "/home/manosetro/Desktop/learning_files/codebook.txt";
		String pcaFile = "/home/manosetro/Desktop/learning_files/pca.txt";
		
		int vectorLength = 96;
        int subVectorLength = 6;
        int numProductCentroids = 256;
        
        int maxCapacity = 100000;
        int k = 10;
        
        File productQuantizerFile = new File(learningFolder, "qproduct_adc_16x256.txt"); 
        
		try {
			
			ImageVectorizer vectorizer = new ImageVectorizer(codebookFile, pcaFile, false);
        
        	File jeLck = new File(indexLocation, "je.lck");
        	if(jeLck.exists()) {
        		jeLck.delete();
        	}
        	
			ADC index = new ADC(vectorLength, subVectorLength, numProductCentroids, true,
        			maxCapacity, indexLocation);
        
        	((ADC) index).loadProductQuantizer(productQuantizerFile.getCanonicalPath());
        	
        	Set<String> set1 = new HashSet<String>();
        	Set<String> set2 = new HashSet<String>();
        	
        	Result[] results = index.computeNearestNeighbors(k, id);
        	for(Result result : results) {
        		set1.add(result.getExternalId());
        		System.out.println(result.getExternalId());
        	}
        	System.out.println("==============================");
        	String imageFilename = "/home/manosetro/Desktop/BOF2nd0CUAAFbYS.jpg";
			double[] queryVector = vectorizer.transformToVector(imageFilename);
			
			Answer answer = index.computeNearestNeighbors(k, queryVector);
			for(Result result : answer.getResults()) {
				set2.add(result.getExternalId());
				System.out.println(result.getExternalId());
        	}
			
			System.out.println("==============================");
			set1.retainAll(set2);
			System.out.println(set1.size());
        }
        catch(Exception e) {
        	e.printStackTrace();
        }
		
	}
	
}
