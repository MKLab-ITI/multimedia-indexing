package gr.iti.mklab.visual.utilities;

/**
 * Objects of this class represents the respone of an index to a query.
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
	 * An array of Results.
	 */
	private Result[] results;

	/**
	 * Constructor.
	 * 
	 * @param results
	 * @param nameLookupTime
	 * @param indexSearchTime
	 */
	public Answer(Result[] results, long nameLookupTime, long indexSearchTime) {
		this.results = results;
		this.nameLookupTime = nameLookupTime;
		this.indexSearchTime = indexSearchTime;
	}

	public long getIndexSearchTime() {
		return indexSearchTime;
	}

	public long getNameLookupTime() {
		return nameLookupTime;
	}

	public Result[] getResults() {
		return results;
	}

}
