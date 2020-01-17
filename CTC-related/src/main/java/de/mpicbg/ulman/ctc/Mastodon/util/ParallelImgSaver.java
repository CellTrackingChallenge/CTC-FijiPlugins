package de.mpicbg.ulman.ctc.Mastodon.util;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.NumericType;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

public class ParallelImgSaver
{
	/** the list of images waiting to be saved */
	private
	final List<ImgPathPair> imgQueue
		= Collections.synchronizedList(new LinkedList<>());

	/** the array of workers -- each takes one image from
	    the list and makes sure to save it */
	private
	final Worker[] workersQueue;

	/** setups initially the crowd of savers,
	    see the Worker inner class */
	public
	ParallelImgSaver(final int noOfWriterThreads)
	{
		workersQueue = new Worker[noOfWriterThreads];

		for (int i=0; i < noOfWriterThreads; i++)
		{
			workersQueue[i] = new Worker(i);
			workersQueue[i].start();
		}
	}

	/** user-adjustable period between job queue is considered,
	    applies only for (blocking API) methods: addImgSaveRequestOrBlockUntilLessThan()
	    and closeAllWorkers_FinishFirstAllUnsavedImages() */
	public
	long clientPollingMillis = 2000;

	/** user-adjustable period between job queue is checked by the image
	    saving workers/threads (after their finish their image saving) */
	public
	long workersPollingMillis = 1000;


	/** how many images are waiting for (and not yet) being saved */
	public
	int notYetSavedImgsCount()
	{
		return imgQueue.size();
	}

	/** enlist the 'img' to the list of images to be saved,
	    returns immediately */
	public <T extends NumericType<T>>
	void addImgSaveRequest(final RandomAccessibleInterval<T> img,
	                       final String path)
	{
		imgQueue.add( new ImgPathPair(img,path) );
	}

	/** enlist the 'img' to the list of images to be saved,
	    returns immediately only if the list of images waiting to be saved
	    is smaller than 'maxQueueLength' -- otherwise it blocks/waits until
	    this holds (until this.imgQueue.size() < maxQueueLength) making sure
	    the list is never longer than 'maxQueueLength' */
	public <T extends NumericType<T>>
	void addImgSaveRequestOrBlockUntilLessThan(final int maxQueueLength,
	                                           final RandomAccessibleInterval<T> img,
	                                           final String path)
	throws InterruptedException
	{
		while (imgQueue.size() >= maxQueueLength)
		{
			Thread.sleep(clientPollingMillis);
		}
		//NB: not atomic between size() and add()....

		imgQueue.add( new ImgPathPair(img,path) );
	}


	/** the body of every image saving thread */
	class Worker extends Thread
	{
		/** create a new image saver with title "Image saver #"+'id' */
		Worker(final int id)
		{
			super("Image saver #"+id);
		}

		/** loops until interrupted: retrieve/remove next available image from
		    the image queue and start saving it, then repeat... there's 10 secs
		    delay between polling the image queue (relaxed busy waiting model) */
		@Override
		public
		void run()
		{
			boolean shouldStopSaving = false;
			while (!this.isInterrupted() && !shouldStopSaving)
			{
				//the image to be saved
				ImgPathPair ipp = null;

				//try to retrieve a reference on an image to be saved
				try { ipp = imgQueue.remove(0); }
				catch (IndexOutOfBoundsException e)
				{
					//failed -> queue is empty
					ipp = null;
				}

				if (ipp != null)
				{
					//save it
					savingInProgress = true;
					IJ.save( ipp.img, ipp.path );
					savingInProgress = false;
				}
				else
				{
					//wait 10 secs
					try { Thread.sleep(workersPollingMillis); }
					catch (InterruptedException e)
					{
						shouldStopSaving = true;
					}
				}
			}
		}

		boolean savingInProgress = false;
	}


	/** inspection method to see if there is at least one worker
	    that is currently saving some image */
	public
	boolean isSomeSavingInProgress()
	{
		for (Worker t : workersQueue)
			if (t.savingInProgress) return true;
		return false;
	}


	/** interrupts every image saving thread and waits for it to finish,
	    every thread finishes its current saving job and will not start
	    a new one (even if there is still some images waiting in the queue) */
	public
	void closeAllWorkers_LeavePossiblyUnsavedImages()
	throws InterruptedException
	{
		//we first notify all our image saving threads to stop
		for (Thread t : workersQueue)
		if (t != null)
		{
			t.interrupt();
		}

		//we than wait for them to finish (unless we got interrupted ourselves)
		for (Thread t : workersQueue)
		if (t != null)
		{
			t.join();
		}
	}

	/** interrupts every image saving thread and waits for it to finish,
	    every thread finishes its current saving job and will start
	    a new saving unless the queue of waiting images is empty */
	public
	void closeAllWorkers_FinishFirstAllUnsavedImages()
	throws InterruptedException
	{
		boolean shouldStopWaiting = false;

		//first, make sure the queue is empty...
		while (imgQueue.size() > 0 && !shouldStopWaiting)
		{
			//wait 10 secs
			try { Thread.sleep(clientPollingMillis); }
			catch (InterruptedException e)
			{
				//no matter what is in the queue, we're closing...
				shouldStopWaiting = true;
			}
		}

		//just to make sure that it takes a lot longer from the queue test above
		//to isSomeSavingInProgress() test below than it takes to Worker.run() from
		//its imgQueue.remove() to savingInProgress=true;
		if (!shouldStopWaiting)
		{
			try { Thread.sleep(500); }
			catch (InterruptedException e) {}
		}

		//...and, then that no saving is still in progress
		//(assuming no one would add a new job again)
		while (isSomeSavingInProgress() && !shouldStopWaiting)
		{
			//wait 10 secs
			try { Thread.sleep(clientPollingMillis); }
			catch (InterruptedException e)
			{
				//no matter what is in the queue, we're closing...
				shouldStopWaiting = true;
			}
		}

		closeAllWorkers_LeavePossiblyUnsavedImages();
	}


	private class ImgPathPair
	{
		final ImagePlus img;
		final String path;

		<T extends NumericType<T>>
		ImgPathPair(final RandomAccessibleInterval<T> i, final String p)
		{
			img = ImageJFunctions.wrap(i,p);
			path = p;
		}
	}
}
