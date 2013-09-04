package gr.iti.mklab.geolocation;

import java.util.ArrayList;


import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;

import gr.iti.mklab.visual.utilities.Result;

/**
 * This class implements Geolocation!
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Geolocator {

	/**
	 * The quantization threshold. Two geolocations are considered the same if their distance (in meters) is
	 * less than this threshold.
	 */
	private double quantizationThreshold;
	/**
	 * The images (essentially their visual distances from the query image and their geolocations are used)
	 * upon which the geolocation methods are applied.
	 */
	private ArrayList<Result> images;

	/**
	 * 
	 * @param images
	 * @param visualDistanceThreshold
	 *            The visual distance threshold. If a returned image's visual distance from the query image
	 *            exceeds this threshold, then it is not considered for location estimation.
	 * @param quantizationThreshold
	 * @param topK
	 *            How many of the returned images to be considered in location estimation.
	 * @param ignoreTopResult
	 */
	public Geolocator(Result[] images, double visualDistanceThreshold, double quantizationThreshold, int topK, boolean ignoreTopResult) {
		// === Keep only the topk geotagged images which are above the similarity threshold!!! ===
		// If ignoreTopResult is true, the top returned image is ignored. Used in evaluation where usually the
		// top results is the same as the query.
		int startIndex = 0;
		if (ignoreTopResult) {
			startIndex = 1;
		}
		this.images = new ArrayList<Result>();
		for (int i = startIndex; i < images.length; i++) {
			if (images[i].getDistance() > visualDistanceThreshold) {
				break;
			}
			if (images[i].getGeolocation() == null) {
				continue;
			}
			this.images.add(images[i]);
			if (this.images.size() >= topK) {
				break;
			}
		}
		this.quantizationThreshold = quantizationThreshold;
	}

	/**
	 * Returns the location of most visually similar image. TO DO: We can assign a confidence score for the
	 * estimated location, based on the visual similarity.
	 * 
	 * @return
	 */
	public LatLng nearestNeighborLocation() {
		if (images.size() == 0) {
			return null;
		}
		return images.get(0).getGeolocation();
	}

	/**
	 * Advanced location estimation method. Old version!
	 * 
	 * @return
	 */
	public LatLng estLocCleverOld() {
		if (images.size() < 0) {
			return null;
		}
		// all the distinct cluster centers
		ArrayList<LatLng> clusterCenters = new ArrayList<LatLng>();
		// the number of members in each cluster
		ArrayList<Integer> clusterMembershipCounts = new ArrayList<Integer>();
		// create a new cluster for the location of the top image
		clusterCenters.add(images.get(0).getGeolocation());
		clusterMembershipCounts.add(1);

		// for each of the top images (k>topindex + 1), assign the image to the nearest cluster (if the
		// cluster is not too far away) else create a new cluster
		for (int i = 1; i < images.size(); i++) {
			// this flag tells us if the new geolocation has been assigned to a cluster
			boolean assigned = false;
			for (int j = 0; j < clusterCenters.size(); j++) {
				if (LatLngTool.distance(images.get(i).getGeolocation(), clusterCenters.get(j), LengthUnit.METER) <= quantizationThreshold) {
					// then update the cluster center with this geolocation e.g.:
					// LatLng prevCenter = clusterCenters.get(j);
					// double newLatitude = (prevCenter.getLatitude() +
					// images.get(i).getGeolocation().getLatitude()) / 2;
					// double newLongitude = (prevCenter.getLongitude() +
					// images.get(i).getGeolocation().getLongitude()) / 2;
					// LatLng newCenter = new LatLng(newLatitude, newLongitude);
					// clusterCenters.set(j, newCenter);
					clusterMembershipCounts.set(j, clusterMembershipCounts.get(j) + 1);
					assigned = true;
					break;
				}
			}
			if (!assigned) { // create a new cluster
				clusterCenters.add(images.get(i).getGeolocation());
				clusterMembershipCounts.add(1);
			}
		}

		// find and return as geolocation, the center of the cluster with the most members
		int maxIndex = 0;
		int max = 0;
		for (int i = 0; i < clusterMembershipCounts.size(); i++) {
			if (clusterMembershipCounts.get(i) > max) {
				max = clusterMembershipCounts.get(i);
				maxIndex = i;
			}
		}
		return clusterCenters.get(maxIndex);
	}

	/**
	 * Advanced location estimation method as described in the technical report.
	 * 
	 * @return
	 */
	public LatLng estLocClever() {
		if (images.size() == 0) {
			return null;
		}
		// all the distinct clusters
		ArrayList<ArrayList<Integer>> clusters = new ArrayList<ArrayList<Integer>>();
		// the center of each cluster
		ArrayList<LatLng> clusterCenters = new ArrayList<LatLng>();
		// create a new cluster for the location of the 1nn
		ArrayList<Integer> cluster1 = new ArrayList<Integer>();
		cluster1.add(0);
		clusters.add(cluster1);
		clusterCenters.add(images.get(0).getGeolocation());

		// for each of the top images (k>topindex + 1), assign the image to the nearest cluster (if the
		// cluster is not too far away) else create a new cluster
		for (int i = 1; i < images.size(); i++) {
			// find the closest cluster
			int closestIndex = 0;
			double closestDistance = Double.MAX_VALUE;
			for (int j = 0; j < clusterCenters.size(); j++) {
				double distance = LatLngTool.distance(images.get(i).getGeolocation(), clusterCenters.get(j), LengthUnit.METER);
				if (distance < closestDistance) {
					closestDistance = distance;
					closestIndex = j;
				}
			}

			if (closestDistance <= quantizationThreshold) {
				ArrayList<Integer> closestCluster = clusters.get(closestIndex);
				closestCluster.add(i);
				clusters.set(closestIndex, closestCluster);
				// update the cluster center
				double newLatitude = 0;
				double newLongitude = 0;
				for (int j = 0; j < closestCluster.size(); j++) {
					newLatitude += images.get(closestCluster.get(j)).getGeolocation().getLatitude();
					newLongitude += images.get(closestCluster.get(j)).getGeolocation().getLongitude();
				}
				LatLng newLocation = new LatLng(newLatitude / closestCluster.size(), newLongitude / closestCluster.size());
				clusterCenters.set(closestIndex, newLocation);
			} else { // create a new cluster
				ArrayList<Integer> cluster = new ArrayList<Integer>();
				cluster.add(i);
				clusters.add(cluster);
				clusterCenters.add(images.get(i).getGeolocation());
			}
		}

		// find and return as geolocation, the location of the cluster with the most members
		int maxIndex = 0;
		int max = 0;
		int index = 0;
		for (ArrayList<Integer> cluster : clusters) {
			if (cluster.size() > max) {
				max = cluster.size();
				maxIndex = index;
			}
			index++;
		}

		return clusterCenters.get(maxIndex);
	}
}
