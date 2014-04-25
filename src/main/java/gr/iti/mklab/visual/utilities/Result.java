package gr.iti.mklab.visual.utilities;

import java.util.Comparator;

/**
 * This class implements the Comparator interface and is used for feeding distance calculation results in a
 * BoundedPriorityQueue during k-nearest neighbor search. Each Result has an internalId, an externalId and a
 * distance.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Result implements Comparator<Result> {

	/**
	 * The distance.
	 */
	private double distance;

	/**
	 * The internal id of the returned result.
	 */
	private int internalId;

	/**
	 * The external id of the returned result.
	 */
	private String externalId;

	public Result() {
	}

	public Result(int internalId, double distance) {
		this.internalId = internalId;
		this.distance = distance;
	}

	public Result(String externalId, double distance) {
		this.externalId = externalId;
		this.distance = distance;
	}

	public Result(int internalId, String externalId, double distance) {
		this.internalId = internalId;
		this.externalId = externalId;
		this.distance = distance;
	}

	public int compare(Result o1, Result o2) {
		if ((o1).getDistance() > (o2).getDistance())
			return -1;
		else if ((o1).getDistance() < (o2).getDistance())
			return 1;
		else
			return 0;
	}

	public double getDistance() {
		return distance;
	}

	public int getInternalId() {
		return internalId;
	}

	public void setInternalId(int internalId) {
		this.internalId = internalId;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

}
