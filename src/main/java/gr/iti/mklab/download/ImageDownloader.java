package gr.iti.mklab.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Class for multi-threaded image download.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownloader {

	private ExecutorService downloadExecutor;

	private CompletionService<ImageDownload> pool;

	private int numPendingTasks;

	public ImageDownloader(int numThreads) {
		downloadExecutor = Executors.newFixedThreadPool(numThreads);
		pool = new ExecutorCompletionService<ImageDownload>(downloadExecutor);
		numPendingTasks = 0;
	}

	/**
	 * Returns the number of tasks which have not been consumed.
	 * 
	 * @return
	 */
	public int getNumPendingTasks() {
		return numPendingTasks;
	}

	public void shutDown() throws InterruptedException {
		downloadExecutor.shutdown();
		downloadExecutor.awaitTermination(10, TimeUnit.SECONDS);
	}

	/**
	 * 
	 * @param URL
	 *            The url of the image file.
	 * @param id
	 *            An id used to name the image file after downloading.
	 * @param downloadFolder
	 *            The folder where the image file is downloaded.
	 */
	public void submitImageDownloadTask(String URL, String id, String downloadFolder) {
		Callable<ImageDownload> call = new ImageDownload(URL, id, downloadFolder);
		pool.submit(call);
		numPendingTasks++;
		// System.out.println("Pending download tasks: " + numPendingTasks);
	}

	/**
	 * Gets an image download results from the pool.
	 * 
	 * @return the download result, or null in no results are ready
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public ImageDownload getImageDownloadResult() throws Exception {
		Future<ImageDownload> future = pool.poll();
		if (future == null) {
			return null;
		}// no completed tasks in the pull
		else {
			try {
				ImageDownload imd = future.get();
				numPendingTasks--;
				return imd;
			} catch (Exception e) {
				numPendingTasks--;
				throw e;
			} // in any case (Exception or not) the numPendingTask should be reduced
		}
	}

	/**
	 * Gets an image download results from the pool, waiting if necessary.
	 * 
	 * @return the download result
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public ImageDownload getImageDownloadResultWait() throws Exception {
		try {
			ImageDownload imd = pool.take().get();
			numPendingTasks--;
			return imd;
		} catch (Exception e) {
			numPendingTasks--;
			throw e;
		} // in any case (Exception or not) the numPendingTask should be reduced
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		// String dowloadFolder = args[0];
		// String urlsFile = args[1];
		// int numUrls = Integer.parseInt(args[2]);

		String dowloadFolder = "C:/Users/lef/Desktop/ITI/data/ImageNET/images/";
		String urlsFile = "C:/Users/lef/Desktop/ITI/data/ImageNET/urls_sample2.txt";
		int numUrls = 100;
		int urlsToSkip = 0;

		downloadFromUrlsFile(dowloadFolder, urlsFile, numUrls, urlsToSkip);
	}

	public static void downloadFromUrlsFile(String dowloadFolder, String urlsFile, int numUrls, int urlsToSkip) throws Exception {
		// Example of multi-threaded image download and write to a folder.
		long start = System.currentTimeMillis();

		int numThreads = 5;
		int maxNumPendingTasks = 10;
		BufferedReader in = new BufferedReader(new FileReader(new File(urlsFile)));

		for (int i = 0; i < urlsToSkip; i++) {
			in.readLine();
		}

		ImageDownloader downloader = new ImageDownloader(numThreads);
		int submittedCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;
		String line = "";
		while (true) {
			String url;
			String id = "";
			// if we can submit more tasks to the downloader then do it
			while (submittedCounter < numUrls) {
				if (downloader.getNumPendingTasks() == maxNumPendingTasks) {
					break;
				}
				System.out.println("IF1");
				line = in.readLine();
				url = line.split("\\s+")[1];
				id = line.split("\\s+")[0];
				downloader.submitImageDownloadTask(url, id, dowloadFolder);
				submittedCounter++;
			}
			// if the completed and failed tasks are less than the submitted task,
			// try to consume a submitted task
			if (completedCounter + failedCounter < submittedCounter) {
				System.out.println("IF2");
				try {
					downloader.getImageDownloadResultWait();
					completedCounter++;
					System.out.println(completedCounter + " downloads completed!");
				} catch (NullPointerException e) {
					// do nothing, means that there is no download ready for consumption
				} catch (Exception e) {
					System.out.println(e.getMessage());
					failedCounter++;
					System.out.println(failedCounter + " downloads failed!");
				}
			}
			// if all tasks have been consumed then break;
			if (completedCounter + failedCounter == numUrls) {
				System.out.println("IF3");
				downloader.shutDown();
				in.close();
				break;
			}
		}

		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start) + " ms");
		System.out.println("Downloaded images: " + completedCounter);
		System.out.println("Failed images: " + failedCounter);
	}
}
