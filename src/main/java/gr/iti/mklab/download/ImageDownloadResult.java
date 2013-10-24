package gr.iti.mklab.download;

import java.awt.image.BufferedImage;

/**
 * This class represents the result of an image download task.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownloadResult {

	private String imageId;

	private String imageUrl;

	private BufferedImage image;

	public ImageDownloadResult(String imageId, String imageUrl, BufferedImage image) {
		this.imageId = imageId;
		this.imageUrl = imageUrl;
		this.image = image;
	}

	public String getImageId() {
		return imageId;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public BufferedImage getImage() {
		return image;
	}
}
