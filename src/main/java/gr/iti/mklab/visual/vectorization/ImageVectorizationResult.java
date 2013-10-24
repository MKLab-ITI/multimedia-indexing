package gr.iti.mklab.visual.vectorization;

/**
 * This class represents the result of an image vectorization task.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVectorizationResult {

	private String imageName;

	private double[] imageVector;

	public ImageVectorizationResult(String imageName, double[] imageVector) {
		this.imageName = imageName;
		this.imageVector = imageVector;
	}

	public String getImageName() {
		return imageName;
	}

	public double[] getImageVector() {
		return imageVector;
	}

}
