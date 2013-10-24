package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * This class performs image scaling
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageScaling {

	/**
	 * Depending on the method that will be called for scaling the image, the targetSize corresponds to:<br/>
	 * 
	 * <pre>
	 * a. The total number of pixels.
	 * b. The dimension of a square thumbnail in pixels.
	 * c. The larger dimension of a rectangular thumbnail in pixels.
	 * </pre>
	 * 
	 */
	private int targetSize;

	/**
	 * If true, this class will use a multi-step scaling technique that provides higher quality than the usual
	 * one-step technique (only useful in downscaling cases and generally only when the {@code BILINEAR} hint
	 * is specified)
	 */
	private boolean higherQuality;

	/**
	 * One of the rendering hints that corresponds to {@code RenderingHints.KEY_INTERPOLATION} (e.g.
	 * {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
	 * {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR}, {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC}
	 * )
	 */
	private Object hint;

	/**
	 * Constructor with no arguments, all fields take the default values
	 */
	public ImageScaling() {
		targetSize = 1024 * 768;
		higherQuality = true;
		hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
	}

	public ImageScaling(int targetSize) {
		this.targetSize = targetSize;
		higherQuality = true;
		hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
	}

	public ImageScaling(int targetSize, boolean highQuality, Object hint) {
		this.targetSize = targetSize;
		this.higherQuality = highQuality;
		this.hint = hint;
	}

	public ImageScaling(String scalingTypeParameter, String scalingSizeParameter) {
		higherQuality = false;
		targetSize = 1024 * 768;
		if (scalingSizeParameter != null) {
			targetSize = Integer.parseInt(scalingSizeParameter);
		}
		hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
		if (scalingTypeParameter.equals("nn")) {
			hint = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
		}
	}

	/**
	 * This method returns a scaled instance of the provided {@code BufferedImage}. The image is scaled to a
	 * maximum of {@link #targetSize} pixels in total by keeping the aspect ratio.
	 * 
	 * @param img
	 *            the original image to be scaled
	 * @return a scaled version of the original {@code BufferedImage} or the original image if no scaling was
	 *         applied
	 */
	public BufferedImage maxPixelsScaling(BufferedImage img) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		// get dimensions of original image
		int originalWidth = img.getWidth();
		int originalHeight = img.getHeight();
		long originalSize = originalWidth * originalHeight;
		if (originalSize <= targetSize) {
			return img;
		}
		double scalingRatio = (double) targetSize / originalSize;
		// scaling ratio per dimension
		scalingRatio = Math.sqrt(scalingRatio);
		int targetWidth = (int) (originalWidth * scalingRatio);
		int targetHeight = (int) (originalHeight * scalingRatio);

		BufferedImage ret = (BufferedImage) img;

		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = img.getWidth();
			h = img.getHeight();
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality && h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}
			// long start = System.currentTimeMillis();
			BufferedImage tmp = new BufferedImage(w, h, type);
			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			// long end = System.currentTimeMillis();
			g2.dispose();
			// System.out.println(end - start);

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}

	/**
	 * This method returns a scaled instance of the provided {@code BufferedImage}. The image is scaled so
	 * that its minimum dimension becomes {@link #targetSize} pixels and then the larger dimension is cropped,
	 * again to {@link #targetSize} pixels to create a square thumbnail.
	 * 
	 * @param img
	 *            the original image to be scaled
	 * @return a scaled version of the original {@code BufferedImage} or the original image if no scaling was
	 *         applied
	 */
	public BufferedImage squareScaling(BufferedImage img) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		// get dimensions of original image
		int originalWidth = img.getWidth();
		int originalHeight = img.getHeight();
		int targetWidth = 0;
		int targetHeight = 0;

		int maxDimension = Math.max(originalWidth, originalHeight);
		int minDimension = Math.min(originalWidth, originalHeight);

		// no processing is required
		if (maxDimension <= targetSize) {
			return img;
		}

		BufferedImage ret = (BufferedImage) img;
		// scaling is required
		if (minDimension > targetSize) {
			double scalingRatio = (double) targetSize / minDimension;

			if (minDimension == originalWidth) {
				targetWidth = targetSize;
				targetHeight = (int) Math.round(originalHeight * scalingRatio);
			} else {
				targetHeight = targetSize;
				targetWidth = (int) Math.round(originalWidth * scalingRatio);
			}

			int w, h;
			if (higherQuality) {
				// Use multi-step technique: start with original size, then
				// scale down in multiple passes with drawImage()
				// until the target size is reached
				w = img.getWidth();
				h = img.getHeight();
			} else {
				// Use one-step technique: scale directly from original
				// size to target size with a single drawImage() call
				w = targetWidth;
				h = targetHeight;
			}

			do {
				if (higherQuality && w > targetWidth) {
					w /= 2;
					if (w < targetWidth) {
						w = targetWidth;
					}
				}

				if (higherQuality && h > targetHeight) {
					h /= 2;
					if (h < targetHeight) {
						h = targetHeight;
					}
				}
				// long start = System.currentTimeMillis();
				BufferedImage tmp = new BufferedImage(w, h, type);
				Graphics2D g2 = tmp.createGraphics();
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
				g2.drawImage(ret, 0, 0, w, h, null);
				// long end = System.currentTimeMillis();
				g2.dispose();
				// System.out.println(end - start);

				ret = tmp;
			} while (w != targetWidth || h != targetHeight);
		}

		// now crop the image
		ret = ret
				.getSubimage(0, 0, Math.min(targetSize, originalWidth), Math.min(targetSize, originalHeight));
		return ret;
	}

	/**
	 * Same as {@link #squareScaling(BufferedImage)} but takes the imageFileName instead of a
	 * {@code BufferedImage}.
	 * 
	 * @param imageFilename
	 * @return
	 * @throws IOException
	 */
	public BufferedImage squareScaling(String imageFilename) throws IOException {
		// read the image
		BufferedImage image;
		try { // first try reading with the default class
			image = ImageIO.read(new File(imageFilename));
		} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
			// This exception is probably because of a grayscale jpeg image.
			System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename);
			image = ImageIOGreyScale.read(new File(imageFilename));
		}
		return squareScaling(image);
	}

	/**
	 * This method returns a scaled instance of the provided {@code BufferedImage}. The image is scaled so
	 * that its maximum dimension becomes {@link #targetSize} pixels.
	 * 
	 * @param img
	 *            the original image to be scaled
	 * @return a scaled version of the original {@code BufferedImage} or the original image if no scaling was
	 *         applied
	 */
	public BufferedImage rectScaling(BufferedImage img) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		// get dimensions of original image
		int originalWidth = img.getWidth();
		int originalHeight = img.getHeight();
		int maxDimension = Math.max(originalWidth, originalHeight);

		// no processing is required
		if (maxDimension <= targetSize) {
			return img;
		}

		// scaling is required
		BufferedImage ret = (BufferedImage) img;
		double scalingRatio = (double) targetSize / maxDimension;
		int targetWidth = (int) (originalWidth * scalingRatio);
		int targetHeight = (int) (originalHeight * scalingRatio);

		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = originalWidth;
			h = originalHeight;
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality && h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}
			// long start = System.currentTimeMillis();
			BufferedImage tmp = new BufferedImage(w, h, type);
			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			// long end = System.currentTimeMillis();
			g2.dispose();
			// System.out.println(end - start);

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}

	/**
	 * Same as {@link #rectScaling(BufferedImage)} but takes the imageFileName instead of a
	 * {@code BufferedImage}.
	 * 
	 * @param imageFilename
	 * @return
	 * @throws IOException
	 */
	public BufferedImage rectScaling(String imageFilename) throws IOException {
		// read the image
		BufferedImage image;
		try { // first try reading with the default class
			image = ImageIO.read(new File(imageFilename));
		} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
			// This exception is probably because of a grayscale jpeg image.
			System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename);
			image = ImageIOGreyScale.read(new File(imageFilename));
		}
		return rectScaling(image);
	}

	public static void main(String args[]) throws IOException {
		int targetDimension = 250;
		ImageScaling scale = new ImageScaling(targetDimension);

		String image = "C:/Users/lef/Desktop/BB2F-nbCQAAD-YL.jpg";
		BufferedImage squareThumb = scale.squareScaling(image);
		BufferedImage rectThumb = scale.rectScaling(image);

		ImageIO.write(squareThumb, "jpeg", new File(image.replace(".jpg", "_square.jpg")));
		ImageIO.write(rectThumb, "jpeg", new File(image.replace(".jpg", "_rect.jpg")));
	}

	public static void advancedUse() throws IOException {
		String targetFolder = "thumbs/";
		String path = "C:/Users/lef/Desktop/thumb_testing_folder/";

		int targetSize = 200;
		boolean highQuality = true;
		Object hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
		ImageScaling scale = new ImageScaling(targetSize, highQuality, hint);

		// --------------Load the image files-------------
		File dir = new File(path);
		// This example does not return any files that do not end with `.jpg'.
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".jpg");
			}
		};
		String[] files = dir.list(filter);

		// --------------Create thumbnail for each image-------------
		for (int i = 0; i < files.length; i++) {
			long start = System.currentTimeMillis();
			BufferedImage image;
			try {
				image = ImageIO.read(new File(path + files[i]));
			} catch (Exception e) {
				image = ImageIOGreyScale.read(new File(path + files[i]));
			}
			long end = System.currentTimeMillis();
			System.out.println("Image reading took :" + (end - start) + "ms");

			start = System.currentTimeMillis();
			// image = scale.getScaledInstance(image);
			image = scale.squareScaling(image);
			end = System.currentTimeMillis();
			System.out.println("Down scaling took : " + (end - start) + "ms");

			File outputfile = new File(path + targetFolder + "t" + files[i]);
			ImageIO.write(image, "jpg", outputfile);
		}
	}
}
