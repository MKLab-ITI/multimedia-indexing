package gr.iti.mklab.visual.quantization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Random;

/**
 * This class is used for taking samples of local features (in csv format) which can then be used for learning visual
 * vocabularies. The local features of each image should be stored in a separate csv file (binary files are currently
 * not supported).
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class SampleLocalFeatures {

	/**
	 * 
	 * @param args
	 *            [0] full path to the folder containing the local feature files.
	 * @param args
	 *            [1] the target number of features to be retained (e.g. 100000).
	 * @param args
	 *            [2] the extension of the feature files (surf or sift).
	 * @param args
	 *            [3] the number of samples to produce (>=1).
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		String path = args[0]; // "C:/Users/lef/Desktop/ITI/data/Flickr200K/surf_raw_0-10/"
		int targetNumFeatures = Integer.parseInt(args[1]); // 100000
		final String extension = args[2]; // .surf
		int numSamples = Integer.parseInt(args[3]); // 1

		File dir = new File(path);
		// return only files that have the specified extension
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("." + extension);
			}
		};
		String[] files = dir.list(filter);

		// count the total number of local features in all files
		long lineCounter = 0;
		long start = System.currentTimeMillis();
		for (int i = 0; i < files.length; i++) {
			if (i % 500 == 0) { // print progress information
				System.out.println("Reading file: " + i);
				System.out.println("Time elapsed: " + (System.currentTimeMillis() - start) + " ms");
			}
			BufferedReader in = new BufferedReader(new FileReader(path + files[i]));
			while ((in.readLine()) != null) {
				lineCounter++;
			}
			in.close();
		}
		System.out.println("Total number of local features: " + lineCounter);
		// compute the per image sampling ratio
		double samplingRatio = (double) targetNumFeatures / lineCounter;
		System.out.println("Sampling ratio: " + samplingRatio);

		System.out.println("Generating " + numSamples + " samples..");
		// create k output files where the samples will be written and k Random samples
		BufferedWriter[] outs = new BufferedWriter[numSamples];
		Random[] rands = new Random[numSamples];
		for (int k = 0; k < numSamples; k++) {
			outs[k] = new BufferedWriter(new FileWriter(path + "sample" + targetNumFeatures + "s" + k + ".csv"));
			rands[k] = new Random(k);
		}

		// perform the random rejection sampling
		for (int i = 0; i < files.length; i++) {
			if (i % 500 == 0) { // print progress information
				System.out.println("Sampling from file: " + i);
				System.out.println("Time elapsed: " + (System.currentTimeMillis() - start) + " ms");
			}
			BufferedReader in = new BufferedReader(new FileReader(path + files[i]));
			String line;
			while ((line = in.readLine()) != null) {
				// for each line of the file randomly decide if it will be written in a sample file
				for (int k = 0; k < numSamples; k++) {
					if (rands[k].nextDouble() <= samplingRatio) {
						outs[k].write(line + "\n");
					}
				}
			}
			in.close();
		}

		// closing output files
		for (int k = 0; k < numSamples; k++) {
			outs[k].close();
		}
	}

}
