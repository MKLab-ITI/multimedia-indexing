package gr.iti.mklab.visual.utilities;

public class Answer {

	public Result[] getResults() {
		return results;
	}

	public void setResults(Result[] results) {
		this.results = results;
	}

	public long getNameLookupTime() {
		return nameLookupTime;
	}

	public void setNameLookupTime(long nameLookupTime) {
		this.nameLookupTime = nameLookupTime;
	}

	public long getIndexSearchTime() {
		return indexSearchTime;
	}

	public void setIndexSearchTime(long indexSearchTime) {
		this.indexSearchTime = indexSearchTime;
	}

	private Result[] results;
	private long nameLookupTime;
	private long indexSearchTime;

	public Answer(Result[] results, long nameLookupTime, long indexSearchTime) {
		this.results = results;
		this.nameLookupTime = nameLookupTime;
		this.indexSearchTime = indexSearchTime;
	}

}
