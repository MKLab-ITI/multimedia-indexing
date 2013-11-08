# multimedia-indexing

A framework for large-scale feature extraction, indexing and retrieval.

## Getting Started

This page contains instructions on how to use the library for:

-   Extracting SURF or SIFT features from an image

-   Aggregating a set of local descriptors into a VLAD vector

-   Applying dimensionality reduction using PCA

-   Building and querying an index

-   Applying Product Quantization

## Extracting SURF or SIFT features from an image

This can be done using the classes in the `gr.iti.mklab.visual.extraction` package. `SURFExtractor `and `SIFTExtractor `implement SURF and SIFT extraction respectively and are actually wrappers of the corresponding classes in the [BoofCV][] library that only expose the most important parameters of the extraction process. Here is a simple example on how to extract SURF features from an image:

1.  Initialize the extractor:

    `AbstractFeatureExtractor surf = new SURFExtractor();`

2.  Read the image file into a BufferedImage object:

    `BufferedImage image = ImageIO.read(new File("sample.jpg"));`

3.  Obtain the features in a two-dimensional double array:

    `double[][] features = surf.extractFeatures(image);`

SIFT features can be obtained in a similar fashion. You can also have a look at the

`SURForSIFTExtractionExample` class that performs SURF or SIFT extraction from a set of images contained in a folder and writes the extracted features in binary or text files. In this class you can also set the maximum size in pixels at which images are scaled prior to feature extraction.

## Aggregating a set of features into a VLAD vector

This can be done using the `VladAggregator `class of the `gr.iti.mklab.visual.aggregation` package. The constructor of this class takes as a parameter a two-dimensional double array that represents a learned codebook (visual vocabulary). Here is an example on how to compute a VLAD vector from a set of features :

1.  Read a codebook of 128 centroids that was learned using 64 dimensional SURF features and is written in a csv file (one centroid per line):

    `double [][] codebook = AbstractFeatureAggregator.readQuantizer("C:/codebook.csv", 128, 64);`

2.  Initialize the aggregator:

    `AbstractFeatureAggregator vlad = new VladAggregator(codebook);`

3.  Obtain the VLAD vector computed from a set of SURF features (represented as a two-dimensional double array) in a double array:

    `double [] vladVector = vlad.aggregate(features);`

In [this page][] we provide pre-computed SURF and SIFT codebooks. However, you can also create your own codebooks using the classes of the `gr.iti.mklab.visual.quantization `package and specifically the `CodebookLearning `class. This class uses a slightly modified (to provide additional output) version of [Weka][]'s `SimpleKMeans` class that implements **multi-threaded** k-means clustering. You might also find handy the `SampleLocalFeatures` class that can be used for taking random samples from a large set of local features.

## Applying dimensionality reduction using PCA

The `PCA` class of the dimensionality `gr.iti.mklab.visual.dimreduction` package makes use of the [EJML][] library and implements basic principle component analysis using Singular Value Decomposition (SVD). Given a set of input (training) vectors, the class computes the singular vectors, singular values and sample means which can then be used for PCA projection or PCA projection + whitening on vectors of the same type.

The `PCALearningExample` and `PCAProjectionExample` classes of the same package exemplify the use of the PCA class for learning a PCA projection matrix and applying PCA projection for dimensionality reduction respectively. Both examples assume that vectors are stored in a Linear index data structure that is described below.

## Building and querying an index

The library supports 3 types of indices: `Linear`, `PQ` and `IVFPQ` which are implemented in the corresponding classes of the `gr.iti.mklab.visual.datastructures` package.  In all 3 classes, writing a new vector to the index in done by calling the `indexVector(String id, double[] vector)` method. The 1st argument is a unique identifier attached to the image and the 2nd is a vector representation of the image (e.g. VLAD or PCA-projceted VLAD). In order to obtain the k most similar vectors in the index with respect to a query vector one should call the `computeNearestNeighbors(int k, double[] queryVector)` method. The 1st argument is the desired number of nearest neighbors and the 2nd is the vector of the query image. The method returns an `Answer` object from which the ids and distances of the k nearest neighbor can be obtained via the .

## Applying Product Quantization

The PQ and IVFPQ index types require a product quantizer file to work. This can be created using the `ProductQuantizationLearning` class of the  `gr.iti.mklab.visual.quantization` package. Besides that file, IVFPQ also requires a coarse quantizer file which can obtained using the `CoarseQuantizerLearning` of the same package.

  [BoofCV]: http://boofcv.org
  [this page]: http://www.socialsensor.eu/results/software/79-image-search-testbed
  [Weka]: http://www.cs.waikato.ac.nz/ml/weka/
  [EJML]: https://code.google.com/p/efficient-java-matrix-library/
