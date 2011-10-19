package trainableSegmentation.metrics;

/**
 *
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Authors: Ignacio Arganda-Carreras (iarganda@mit.edu)
 */

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import trainableSegmentation.utils.Utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * This class implements the Rand error metric.
 * The Rand error is defined as the 1 - Rand index. We follow the
 * definition of Rand index as described by William M. Rand \cite{Rand71}.
 *
 * BibTeX:
 * <pre>
 * &#64;article{Rand71,
 *   author    = {William M. Rand},
 *   title     = {Objective criteria for the evaluation of clustering methods},
 *   journal   = {Journal of the American Statistical Association},
 *   year      = {1971},
 *   volume    = {66},
 *   number    = {336},
 *   pages     = {846--850},
 *   doi       = {10.2307/2284239)
 * }
 * </pre>
 *
 */
public class RandError extends Metrics
{

	/**
	 * Initialize Rand error metric
	 * @param originalLabels original labels (single 2D image or stack)
	 * @param proposedLabels threshold value to binarize proposal (larger than 0 and smaller than 1)
	 */
	public RandError(ImagePlus originalLabels, ImagePlus proposedLabels) 
	{
		super(originalLabels, proposedLabels);
	}

	
	/**
	 * Calculate the Rand error in 2D between some original labels 
	 * and the corresponding proposed labels. Both image are binarized.
	 * The Rand error is defined as the 1 - Rand index, as described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param binaryThreshold threshold value to binarize proposal (larger than 0 and smaller than 1)
	 * @return Rand error
	 */
	public double getMetricValue(double binaryThreshold)
	{

		final ImageStack labelSlices = originalLabels.getImageStack();
		final ImageStack proposalSlices = proposedLabels.getImageStack();

		double randError = 0;

		// Executor service to produce concurrent threads
		final ExecutorService exe = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final ArrayList< Future<Double> > futures = new ArrayList< Future<Double> >();

		try{
			for(int i = 1; i <= labelSlices.getSize(); i++)
			{
				futures.add(exe.submit( getRandErrorConcurrent(labelSlices.getProcessor(i).convertToFloat(),
											proposalSlices.getProcessor(i).convertToFloat(),										
											binaryThreshold ) ) );
			}

			// Wait for the jobs to be done
			for(Future<Double> f : futures)
			{
				randError += f.get();				
			}			
		}
		catch(Exception ex)
		{
			IJ.log("Error when calculating rand error in a concurrent way.");
			ex.printStackTrace();
		}
		finally{
			exe.shutdown();
		}

		return randError / labelSlices.getSize();
	}
	
	/**
	 * Calculate the Rand index and its derived statistics in 2D between 
	 * some original labels and the corresponding proposed labels. Both images 
	 * are binarized. We follow the definition of Rand index described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param label original labels (single 2D image or stack)
	 * @param proposal proposed new labels (single 2D image or stack of the same as as the original labels)
	 * @param binaryThreshold threshold value to binarize proposal (larger than 0 and smaller than 1)
	 * @return Rand index value and derived satatistics
	 */
	public ClassificationStatistics randIndexStats(
			ImagePlus label,
			ImagePlus proposal,
			double binaryThreshold)
	{
		
		if(label.getWidth() != proposal.getWidth()
				|| label.getHeight() != proposal.getHeight()
				|| label.getImageStackSize() != proposal.getImageStackSize())
		{
			IJ.log("Error: label and proposal image sizes do not fit.");
			return null;
		}

		final ImageStack labelSlices = label.getImageStack();
		final ImageStack proposalSlices = proposal.getImageStack();

		double randIndex = 0;
		double tp = 0;
		double tn = 0;
		double fp = 0;
		double fn = 0;

		// Executor service to produce concurrent threads
		final ExecutorService exe = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final ArrayList< Future<ClassificationStatistics> > futures = new ArrayList< Future<ClassificationStatistics> >();

		try{
			for(int i = 1; i <= labelSlices.getSize(); i++)
			{
				futures.add(exe.submit( getRandIndexStatsConcurrent(labelSlices.getProcessor(i).convertToFloat(),
											proposalSlices.getProcessor(i).convertToFloat(),										
											binaryThreshold ) ) );
			}

			// Wait for the jobs to be done
			for(Future<ClassificationStatistics> f : futures)
			{
				ClassificationStatistics cs = f.get();
				randIndex += cs.metricValue;
				tp += cs.truePositives;
				tn += cs.trueNegatives;
				fp += cs.falsePositives;
				fn += cs.falseNegatives;
			}			
		}
		catch(Exception ex)
		{
			IJ.log("Error when calculating rand error in a concurrent way.");
			ex.printStackTrace();
		}
		finally{
			exe.shutdown();
		}

		return new ClassificationStatistics( tp, tn, fp, fn, randIndex / labelSlices.getSize() );
	}
	
	/**
	 * Calculate the precision-recall values based on Rand index between 
	 * some 2D original labels and the corresponding proposed labels. 
	 * We follow the definition of Rand index as described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param label 2D image with the original labels
	 * @param proposal 2D image with the proposed labels
	 * @param minThreshold minimum threshold value to binarize the input images
	 * @param maxThreshold maximum threshold value to binarize the input images
	 * @param stepThreshold threshold step value to use during binarization
	 * @return rand index value and derived statistics for each threshold
	 */
	public ArrayList< ClassificationStatistics > randIndexPrecisionRecall(
			ImagePlus label,
			ImagePlus proposal,
			double minThreshold,
			double maxThreshold,
			double stepThreshold)
	{
		if(label.getWidth() != proposal.getWidth()
				|| label.getHeight() != proposal.getHeight()
				|| label.getImageStackSize() != proposal.getImageStackSize())
		{
			IJ.log("Error: label and proposal image sizes do not fit.");
			return null;
		}
		
		if( minThreshold < 0 || minThreshold >= maxThreshold || maxThreshold > 1)
		{
			IJ.log("Error: unvalid threshold values.");
			return null;
		}
		
		ArrayList< ClassificationStatistics > cs = new ArrayList<ClassificationStatistics>();
		
		for(double th =  minThreshold; th <= maxThreshold; th += stepThreshold)
		{
			cs.add( randIndexStats(label, proposal, th));
		}
		
		return cs;
	}
	
	/**
	 * Get Rand error between two images in a concurrent way 
	 * (to be submitted to an Executor Service). Both images
	 * are binarized.
	 * The Rand error is defined as the 1 - Rand index, as described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param image1 first image
	 * @param image2 second image
	 * @param binaryThreshold threshold to apply to both images
	 * @return Rand error
	 */
	public Callable<Double> getRandErrorConcurrent(
			final ImageProcessor image1, 
			final ImageProcessor image2,
			final double binaryThreshold) 
	{
		return new Callable<Double>()
		{
			public Double call()
			{				
				return randError ( image1, image2, binaryThreshold );
			}
		};
	}

	/**
	 * Get Rand index value and derived statistics between two images 
	 * in a concurrent way (to be submitted to an Executor Service). 
	 * Both images are binarized.
	 * We follow the Rand index definition described by William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param image1 first image
	 * @param image2 second image
	 * @param binaryThreshold threshold to apply to both images
	 * @return Rand index value and derived statistics
	 */
	public  Callable<ClassificationStatistics> getRandIndexStatsConcurrent(
			final ImageProcessor image1, 
			final ImageProcessor image2,
			final double binaryThreshold) 
	{
		return new Callable<ClassificationStatistics>()
		{
			public ClassificationStatistics call()
			{				
				return randIndexStats( image1, image2, binaryThreshold );
			}
		};
	}
	
	/**
	 * Calculate the Rand error between some 2D original labels 
	 * and the corresponding proposed labels. Both image are binarized.
	 * The Rand error is defined as the 1 - Rand index, as described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param label 2D image with the original labels
	 * @param proposal 2D image with the proposed labels
	 * @param binaryThreshold threshold value to binarize the input images
	 * @return Rand error
	 */
	public double randError(
			ImageProcessor label,
			ImageProcessor proposal,
			double binaryThreshold)
	{
		// Binarize inputs
		ByteProcessor binaryLabel = new ByteProcessor( label.getWidth(), label.getHeight() );
		ByteProcessor binaryProposal = new ByteProcessor( label.getWidth(), label.getHeight() );
		
		for(int x=0; x<label.getWidth(); x++)
			for(int y=0; y<label.getHeight(); y++)
			{
				binaryLabel.set(   x, y,    label.getPixelValue( x, y ) > binaryThreshold ? 255 : 0);
				binaryProposal.set(x, y, proposal.getPixelValue( x, y ) > binaryThreshold ? 255 : 0);
			}
		
		// Find components
		ShortProcessor components1 = ( ShortProcessor ) Utils.connectedComponents(
				new ImagePlus("binary labels", binaryLabel), 4).allRegions.getProcessor();
		
		ShortProcessor components2 = ( ShortProcessor ) Utils.connectedComponents(
				new ImagePlus("proposal labels", binaryProposal), 4).allRegions.getProcessor();
		
		return 1 - randIndex( components1, components2 );
		
	}
	
	/**
	 * Calculate the Rand index between some 2D original labels 
	 * and the corresponding proposed labels. Both image are binarized.
	 * We follow the definition of Rand index as described by
	 * William M. Rand \cite{Rand71}.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param label 2D image with the original labels
	 * @param proposal 2D image with the proposed labels
	 * @param binaryThreshold threshold value to binarize the input images
	 * @return rand index value and derived statistics
	 */
	public  ClassificationStatistics randIndexStats(
			ImageProcessor label,
			ImageProcessor proposal,
			double binaryThreshold)
	{
		// Binarize inputs
		ByteProcessor binaryLabel = new ByteProcessor( label.getWidth(), label.getHeight() );
		ByteProcessor binaryProposal = new ByteProcessor( label.getWidth(), label.getHeight() );
		
		for(int x=0; x<label.getWidth(); x++)
			for(int y=0; y<label.getHeight(); y++)
			{
				binaryLabel.set(   x, y,    label.getPixelValue( x, y ) > binaryThreshold ? 255 : 0);
				binaryProposal.set(x, y, proposal.getPixelValue( x, y ) > binaryThreshold ? 255 : 0);
			}
		
		// Find components
		ShortProcessor components1 = ( ShortProcessor ) Utils.connectedComponents(
				new ImagePlus("binary labels", binaryLabel), 4).allRegions.getProcessor();
		
		ShortProcessor components2 = ( ShortProcessor ) Utils.connectedComponents(
				new ImagePlus("proposal labels", binaryProposal), 4).allRegions.getProcessor();
		
		return getRandIndexStats( components1, components2 );		
	}
	
	
	/**
	 * Calculate the Rand index between to clusters, as described by
	 * William M. Rand \cite{Rand71}. Note that this version of the
	 * Rand index treats the zero component (background) as another 
	 * object.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param cluster1 2D segmented image (objects are labeled with different numbers) 
	 * @param cluster2 2D segmented image (objects are labeled with different numbers)
	 * @return Rand index
	 */
	public double classicRandIndex(
			ShortProcessor cluster1,
			ShortProcessor cluster2)
	{
		final short[] pixels1 = (short[]) cluster1.getPixels();
		final short[] pixels2 = (short[]) cluster2.getPixels();
		
		double n = pixels1.length;
		
		// Form contingency matrix
		int[][]cont = new int[(int) cluster1.getMax() ] [ (int) cluster2.getMax() ];
		
		for(int i=0; i<n; i++)
			cont[ pixels1[i] ] [ pixels2[i] ] ++;
		
		// sum over rows & columnns of nij^2
		double t2 = 0;
		
		// sum of squares of sums of rows
		double[] ni = new double[ cont.length ];
		for(int i=0; i<cont.length; i++)
			for(int j=0; j<cont[i].length; j++)			
				ni[ i ] += cont[ i ][ j ];
		double nis = 0;
		for(int k=0; k<ni.length; k++)
			nis += ni[ k ] * ni[ k ];
		
		// sum of squares of sums of columns
		double[] nj = new double[ cont.length ];
		for(int j=0; j<cont[0].length; j++)
			for(int i=0; i<cont.length; i++)
			{
				nj[ j ] += cont[ i ][ j ];
				t2 += cont[ i ][ j ] * cont[ i ][ j ];
			}
		double njs = 0;
		for(int k=0; k<nj.length; k++)
			njs += nj[ k ] * nj[ k ];
		
		// total number of pairs of entities
		double t1 =  n * (n - 1) / 2 ;
		
		double t3 = 0.5 * (nis+njs);
		
		double agreements=t1+t2-t3;		// number of agreements
		
		return agreements/t1;
	}
	
	/**
	 * Calculate the Rand index between to clusters, as described by
	 * William M. Rand \cite{Rand71}, but pruning out the zero component.
	 * Otherwise the Rand index gets symmetric.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param cluster1 2D segmented image (objects are labeled with different numbers) 
	 * @param cluster2 2D segmented image (objects are labeled with different numbers)
	 * @return Rand index
	 */
	public double randIndex(
			ShortProcessor cluster1,
			ShortProcessor cluster2)
	{
		final short[] pixels1 = (short[]) cluster1.getPixels();
		final short[] pixels2 = (short[]) cluster2.getPixels();
		
		//(new ImagePlus("cluster 1", cluster1)).show();
		//(new ImagePlus("cluster 2", cluster2)).show();
		
		double nPixels = pixels1.length;
		
		// number of pixels that are "in" (not background)
		double n = 0;
		
		// Form the contingency matrix
		int[][]cont = new int[(int) cluster1.getMax() + 1] [ (int) cluster2.getMax() + 1];		

		for(int i=0; i<nPixels; i++)
		{						
			cont[ pixels1[i] ] [ pixels2[i] ] ++;
			if( pixels1[ i ] > 0)
				n++;
		}

		// sum of squares of sums of rows
		// (skip background objects in the first cluster)
		double[] ni = new double[ cont.length ];
		for(int i=1; i<cont.length; i++)
			for(int j=0; j<cont[0].length; j++)
			{
				ni[ i ] += cont[ i ][ j ];				
			}

		// sum of squares of sums of columns
		// (prune out the zero component in the labeling (un-assigned "out" space))
		double[] nj = new double[ cont[0].length ];
		for(int j=1; j<cont[0].length; j++)
			for(int i=1; i<cont.length; i++)
			{
				nj[ j ] += cont[ i ][ j ];
			}
		
		// true positives - type (i): objects in the pair are placed in the 
		// same class in cluster1 and in the same class in claster2
		// (prune out the zero component in the labeling (un-assigned "out" space))
		double truePositives = 0;
		for(int j=1; j<cont[0].length; j++)
			for(int i=1; i<cont.length; i++)			
				truePositives += cont[ i ][ j ] * ( cont[ i ][ j ] - 1 ) / 2;			
						
		// total number of pairs
		double nPairsTotal = n * (n-1) / 2 ;
		
		double nPosTrue = 0;
		for(int k=0; k<ni.length; k++)
			nPosTrue += ni[ k ] * (ni[ k ]-1) /2;
		
		double nPosActual = 0;
		for(int k=0; k<nj.length; k++)
			nPosActual += nj[ k ] * (nj[ k ]-1)/2;				
				
		// true negatives - type (ii): objects in the pair are placed in different 
		// classes in cluster1 and in different classes in claster2
		//double trueNegatives = (n*n + t2 - nis - njs) / 2;		
		double trueNegatives = nPairsTotal + truePositives - nPosTrue - nPosActual;
		
		double agreements = truePositives + trueNegatives;		// number of agreements
		
		double randIndex = agreements / nPairsTotal;
		
		return randIndex;
	}
	
	
	/**
	 * Calculate the Rand index between to clusters, as described by
	 * William M. Rand \cite{Rand71}, but pruning out the zero component.
	 * Otherwise the Rand index gets symmetric.
	 *
	 * BibTeX:
	 * <pre>
	 * &#64;article{Rand71,
	 *   author    = {William M. Rand},
	 *   title     = {Objective criteria for the evaluation of clustering methods},
	 *   journal   = {Journal of the American Statistical Association},
	 *   year      = {1971},
	 *   volume    = {66},
	 *   number    = {336},
	 *   pages     = {846--850},
	 *   doi       = {10.2307/2284239)
	 * }
	 * </pre>
	 * 
	 * @param cluster1 2D segmented image (objects are labeled with different numbers) 
	 * @param cluster2 2D segmented image (objects are labeled with different numbers)
	 * @return Rand index
	 */
	public ClassificationStatistics getRandIndexStats(
			ShortProcessor cluster1,
			ShortProcessor cluster2)
	{
		final short[] pixels1 = (short[]) cluster1.getPixels();
		final short[] pixels2 = (short[]) cluster2.getPixels();
		
		//(new ImagePlus("cluster 1", cluster1)).show();
		//(new ImagePlus("cluster 2", cluster2)).show();
		
		double nPixels = pixels1.length;
		
		// number of pixels that are "in" (not background)
		double n = 0;
		
		// Form the contingency matrix
		int[][]cont = new int[(int) cluster1.getMax() + 1] [ (int) cluster2.getMax() + 1];		

		for(int i=0; i<nPixels; i++)
		{						
			cont[ pixels1[i] ] [ pixels2[i] ] ++;
			if( pixels1[ i ] > 0)
				n++;
		}
		// sum over rows & columnns of nij^2
		//double t2 = 0;				
		
		// sum of squares of sums of rows
		// (skip background objects in the first cluster)
		double[] ni = new double[ cont.length ];
		for(int i=1; i<cont.length; i++)
			for(int j=0; j<cont[0].length; j++)
			{
				ni[ i ] += cont[ i ][ j ];				
			}
		/*
		double nis = 0;
		for(int k=0; k<ni.length; k++)
			nis += ni[ k ] * ni[ k ];
		*/
		
		// sum of squares of sums of columns
		// (prune out the zero component in the labeling (un-assigned "out" space))
		double[] nj = new double[ cont[0].length ];
		for(int j=1; j<cont[0].length; j++)
			for(int i=1; i<cont.length; i++)
			{
				nj[ j ] += cont[ i ][ j ];
				//t2 += cont[ i ][ j ] * cont[ i ][ j ];
			}
		/*
		double njs = 0;
		for(int k=0; k<nj.length; k++)
			njs += nj[ k ] * nj[ k ];
		*/
		
		// true positives - type (i): objects in the pair are placed in the 
		// same class in cluster1 and in the same class in claster2
		// (prune out the zero component in the labeling (un-assigned "out" space))
		double truePositives = 0;
		for(int j=1; j<cont[0].length; j++)
			for(int i=1; i<cont.length; i++)			
				truePositives += cont[ i ][ j ] * ( cont[ i ][ j ] - 1 ) / 2;			
						
		// total number of pairs
		double nPairsTotal = n * (n-1) / 2 ;
		
		double nPosTrue = 0;
		for(int k=0; k<ni.length; k++)
			nPosTrue += ni[ k ] * (ni[ k ]-1) /2;
		
		double nPosActual = 0;
		for(int k=0; k<nj.length; k++)
			nPosActual += nj[ k ] * (nj[ k ]-1)/2;				
		
		double nNegCorrect = nPairsTotal + truePositives - nPosTrue - nPosActual;
		
		// true negatives - type (ii): objects in the pair are placed in different 
		// classes in cluster1 and in different classes in claster2
		//double trueNegatives = (n*n + t2 - nis - njs) / 2;		
		double trueNegatives = nNegCorrect;
		
		// false positives - type (iii): objects in the pair are placed in different 
		// classes in cluster1 and in the same class in claster2
		double falsePositives = nPosActual - truePositives; //(njs - t2) / 2;
		
		// false negatives - type (iv): objects in the pair are placed in the same 
		// class in cluster1 and in different classes in claster2
		double nNegActual = nPairsTotal - nPosActual;
		double falseNegatives = nNegActual - nNegCorrect; //(nis - t2) / 2;
		
		/*
		IJ.log(" In getRandIndexStats:");
		IJ.log("  tp = " + truePositives);
	    IJ.log("  tn = " + trueNegatives);
	    IJ.log("  fp = " + falsePositives);
	    IJ.log("  fn = " + falseNegatives);
	    IJ.log(" nPairsTotal = " + nPairsTotal);
	    IJ.log(" nPosTrue = " + nPosTrue);
	    IJ.log(" nPosActual = " + nPosActual);
	    IJ.log(" nNegCorrect = " + nNegCorrect);
	    IJ.log(" nNegActual = " + nNegActual);
		*/
		double agreements = truePositives + trueNegatives;		// number of agreements
		
		double randIndex = agreements / nPairsTotal;
		
		return new ClassificationStatistics( truePositives, trueNegatives, 
									falsePositives,  falseNegatives, randIndex);
	}

}
