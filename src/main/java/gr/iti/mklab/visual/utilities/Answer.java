package gr.iti.mklab.visual.utilities;

/**
 * Objects of this class represent the response of an index structure to a query.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class Answer {

	/**
	 * Time taken to search the index (ms).
	 */
	private long indexSearchTime;
	/**
	 * Time taken for name look-up (ms).
	 */
	private long nameLookupTime;

	/**
	 * The ids of the results ordered by increasing distance.
	 */
	private String[] ids;

	/**
	 * The distances of the results in ascending order.
	 */
	private double[] distances;

	public String[] getIds() {
		return ids;
	}

	public double[] getDistances() {
		return distances;
	}

	/**
	 * Constructor
	 * 
	 * @param ids
	 * @param distances
	 * @param nameLookupTime
	 * @param indexSearchTime
	 */
	public Answer(String[] ids, double[] distances, long nameLookupTime, long indexSearchTime) {
		this.ids = ids;
		this.distances = distances;
		this.nameLookupTime = nameLookupTime;
		this.indexSearchTime = indexSearchTime;
	}

	public long getIndexSearchTime() {
		return indexSearchTime;
	}

	public long getNameLookupTime() {
		return nameLookupTime;
	}

}
