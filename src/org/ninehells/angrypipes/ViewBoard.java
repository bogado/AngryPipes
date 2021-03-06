package org.ninehells.angrypipes;

//{//  import

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.SurfaceView;

import org.ninehells.angrypipes.Board;
import org.ninehells.angrypipes.Position;

//}//

class ViewBoard extends SurfaceView
{
	ViewBoard (Context context, Board board)
	{//
		super(context);

		mBoard = board;

		Resources res  = context.getResources();
		mZoomLevels = res.getIntArray(R.array.zoom_levels);
		mZoomLevel = res.getInteger(R.integer.zoom_level);

		setWillNotDraw(false);
	}//

	void zoomIn()
	{//
		if (mZoomLevel+1 < mZoomLevels.length) {
			++mZoomLevel;
			requestLayout();
		}
	}

	void zoomOut()
	{
		//TODO: don't if board already fits
		if (mZoomLevel > 0) {
			--mZoomLevel;
			requestLayout();
		}
	}//

	@Override
	public void onMeasure (int w, int h)
	{//
		int torus = mBoard.config().torus_mode ? 1 : 0;
		double scale = (mZoomLevels[mZoomLevel]*1.0)/100.0;
		setMeasuredDimension(
				(int)(scale*(2 + mCellSize*(2*torus + mBoard.config().width))),
				(int)(scale*(2 + mCellSize*(2*torus + mBoard.config().height)))
		);
	}//

	@Override
	public void computeScroll()
	{//
		mComputeScroll = true;
	}//

	@Override
	public boolean onTouchEvent (MotionEvent event)
	{//
		double scale = (mZoomLevels[mZoomLevel]*1.0)/100.0;
		int torus = mBoard.config().torus_mode ? 1 : 0;
		int i = -torus+ (int)(event.getX()/(scale*mCellSize));
		int j = -torus+ (int)(event.getY()/(scale*mCellSize));
		int W = mBoard.config().width;
		int H = mBoard.config().height;
		if (mBoard.config().torus_mode) {
			i = (i + W) % W;
			j = (j + H) % H;
		}
		else {
			if (i < 0) i = 0;  if (i >= W) i = W-1;
			if (j < 0) j = 0;  if (j >= H) j = H-1;
		}

		mMovePos.set(i, j);

		int longPressTimeoutMillis = 400;
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			mComputeScroll = false;
			mDownPos.set(i, j);
			mTimerHandler.postDelayed(mTimerTask, longPressTimeoutMillis);
		}
		else if (event.getAction() == MotionEvent.ACTION_UP) {
			mTimerHandler.removeCallbacks(mTimerTask);
			if (mDownPos.valid && event.getEventTime() - event.getDownTime() >= longPressTimeoutMillis) {
				mBoard.setCursor(i, j);
				if (!mComputeScroll && mBoard.toggleLock(mDownPos))
					invalidate();
			}
			else if (mDownPos.equals(i, j)) {
				if (mAutoRotate || mBoard.cursor().equals(mDownPos))
					mBoard.rotate(mDownPos);
				mBoard.setCursor(i, j);
				invalidate();
			}
			mDownPos.reset();
		}

		return true;
	}//

	@Override
	public void draw (Canvas canvas)
	{//
		canvas.save();
		double scale = (mZoomLevels[mZoomLevel]*1.0)/100.0;
		canvas.scale((float)scale, (float)scale);

		int w = mBoard.config().width;
		int h = mBoard.config().height;
		int torus = mBoard.config().torus_mode ? 1 : 0;

		Paint paint = new Paint();
		canvas.drawRGB(0, 0, 0);

		Rect r = canvas.getClipBounds();
		int i0 = -torus+ r.left/mCellSize;
		int j0 = -torus+ r.top /mCellSize;
		int i1 = +torus+ Math.min(r.right /mCellSize, w-1);
		int j1 = +torus+ Math.min(r.bottom/mCellSize, h-1);

		w += 2*torus;
		h += 2*torus;

		paint.setARGB(0xff, 0x30, 0x30, 0x30);

		// grid
		for (int j = j0; j <= j1+1; ++j)
			canvas.drawLine(0, j*mCellSize, w*mCellSize-1, j*mCellSize, paint);
		for (int i = i0; i <= i1+1; ++i)
			canvas.drawLine(i*mCellSize, 0, i*mCellSize, h*mCellSize-1, paint);

		// border
		if (!mBoard.config().torus_mode) {
			canvas.drawLine(3, 3, w*mCellSize-3-1, 3, paint);
			canvas.drawLine(3, 3, 3, h*mCellSize-3-1, paint);
			canvas.drawLine(w*mCellSize-3-1, h*mCellSize-3-1, w*mCellSize-3-1, 3, paint);
			canvas.drawLine(w*mCellSize-3-1, h*mCellSize-3-1, 3, h*mCellSize-3-1, paint);
		}

		// pipes
		for (int j = j0; j <= j1; ++j)
		for (int i = i0; i <= i1; ++i) {
			float x0 = (i+torus)*mCellSize+mBorder,  y0 = (j+torus)*mCellSize+mBorder;
			float xc = x0+mSegmentSize,  yc = y0+mSegmentSize;
			float x1 = xc+mSegmentSize,  y1 = yc+mSegmentSize;

			/* set pipe color */
			if (mBoard.isSolved())
				paint.setARGB(0xff, 0x00, 0xff, 0x00);
			else if (mBoard.filled(i,j)) {
				if (mBoard.isBadFill())
					paint.setARGB(0xff, 0xff, 0x40, 0x40);
				else
					paint.setARGB(0xff, 0xff, 0xff, 0x40);
			}
			else
				paint.setARGB(0xff, 0xff, 0xff, 0xff);

			/* draw pipe */
			boolean simple = (scale < .5);
			if (!simple) canvas.drawCircle(xc, yc, 3, paint);
			if (mBoard.locked(i,j)) drawLock(xc, yc, simple, canvas, paint);
			if (mBoard.right(i,j)) drawSegment(xc, yc, x1, yc, simple, canvas, paint);
			if (mBoard.up   (i,j)) drawSegment(xc, yc, xc, y0, simple, canvas, paint);
			if (mBoard.left (i,j)) drawSegment(xc, yc, x0, yc, simple, canvas, paint);
			if (mBoard.down (i,j)) drawSegment(xc, yc, xc, y1, simple, canvas, paint);

			if (mBoard.moved(i,j) || mBoard.locked(i,j)) {
				paint.setARGB(0x20, 0x00, 0x80, 0x00);
				canvas.drawRect(x0, y0, x1, y1, paint);
			}

			/* torus border */
			if (i<0 || j<0 || i>=mBoard.config().width || j>=mBoard.config().height) {
				paint.setARGB(0x40, 0x80, 0x80, 0x80);
				canvas.drawRect(x0, y0, x1, y1, paint);
			}

			/* cursor */
			if (mBoard.cursor().equals(i, j)) {
				paint.setStyle(Paint.Style.STROKE);
				paint.setARGB(0xff, 0xff, 0x00, 0x00);
				canvas.drawRect(x0+1, y0+1, x1-1, y1-1, paint);
				paint.setStyle(Paint.Style.FILL);
			}
		}

		canvas.restore();
	}//

	private void drawSegment (float x, float y, float x1, float y1, boolean simple, Canvas canvas, Paint paint)
	{//
		canvas.drawLine(x, y, x1, y1, paint);
		if (simple)
			return;

		paint.setAlpha(0x80);
		if (x == x1) {
			int d = y<y1 ? -1 : 1;
			canvas.drawLine(x-1, y, x-1, y1+d, paint);
			canvas.drawLine(x+1, y, x+1, y1+d, paint);
		}
		else if (y == y1) {
			int d = x<x1 ? -1 : 1;
			canvas.drawLine(x, y-1, x1+d, y-1, paint);
			canvas.drawLine(x, y+1, x1+d, y+1, paint);
		}
	}//

	private void drawLock (float x, float y, boolean simple, Canvas canvas, Paint paint)
	{//
		if (simple) return;
		paint.setAlpha(0x60);
		canvas.drawLine(x-5, y-5, x+5, y-5, paint);
		canvas.drawLine(x+5, y-5, x+5, y+5, paint);
		canvas.drawLine(x+5, y+5, x-5, y+5, paint);
		canvas.drawLine(x-5, y+5, x-5, y-5, paint);
	}//

	private Board mBoard = null;
	private Position mDownPos = new Position();
	private Position mMovePos = new Position();
	private boolean mAutoRotate = true;

	private int[] mZoomLevels;
	private int mZoomLevel;

	private final int mSegmentSize = 21;
	private final int mBorder = 1;
	private final int mCellSize = 2*mBorder+2*mSegmentSize;

	private boolean mComputeScroll = false;
	private Handler mTimerHandler = new Handler();
	private Runnable mTimerTask = new Runnable()
	{//
		public void run() {
			if (mMovePos.equals(mDownPos)) {
				if (!mComputeScroll && mBoard.toggleLock(mDownPos)) {
					mBoard.setCursor(mDownPos.i, mDownPos.j);
					invalidate();
				}
			}
			mDownPos.reset();
		}
	}//
	;
}

// vim600:fdm=marker:fmr={//,}//:fdn=2:nu:
