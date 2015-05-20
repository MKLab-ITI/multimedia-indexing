package gr.iti.mklab.visual.utilities;

import java.util.Comparator;

/**
 * This class implements the Comparator interface and is used for feeding distance calculation results in a
 * BoundedPriorityQueue during k-nearest neighbor search. Each Result has an Integer id and a distance.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Result implements Comparator<Result> {

	/**
	 * The distance associated with this result object.
	 */
	private double distance;

	/**
	 * The id associated with this result object.
	 */
	private int id;

	public Result() {
	}

	/**
	 * Constructor
	 * 
	 * @param internalId
	 * @param distance
	 */
	public Result(int internalId, double distance) {
		this.id = internalId;
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

	public int getId() {
		return id;
	}

}
