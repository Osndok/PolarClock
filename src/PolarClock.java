
import java.awt.Font;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.BorderLayout;
import java.awt.RenderingHints;

import java.util.Calendar;
import java.util.TimeZone;

import java.text.DecimalFormat;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JComponent;

/** A simple polar clock
 based off source from: http://www.java2s.com/Code/Java/Swing-Components/AsimpleClock.htm
 */
public class PolarClock extends JComponent implements Runnable {
	
	//in pixels, the width of the non-overlapping area of the rings/pie segments; radiuspixels-per-ring
	private static final int RING_WIDTH=50;
	private static final int GUTTER=5;

	//the number of updates within one second (for animations); ticks-per-second
	private static final int TICKS=20;

	private static final int MAX_RINGS=10;
	
	//Dark on the outside
	private static final Color[] ringColors = new Color[] {
		Color.BLACK,
		Color.DARK_GRAY,
		Color.LIGHT_GRAY,
	};

	//private static final
	//Color tickColorOld = new Color(128, 128, 128, 40);

	private static final
	Color tickColorLight=Color.WHITE;

	private static final
	Color tickColorDark=Color.BLACK;

	//the number of adjacent wedges which do not need to be seperated by tick marks
	//conventional clocks use 5; so the default should be a multiple of 5;
	private static final double startingMultiplier=5;
	//for tick marks to quadrants, 4; eigths 8, etc.
	//conventional clocks use 12; good values are 2,4,8 (half, quadrants, eighths)
	//the default is 2 (to make even the 1/2 marker bold)
	private static final int maxEasilyCountableWedges=2;
	
	private static final boolean military=false;
	private static final boolean pacman=false;     //All arcs point the same direction
	private static final boolean clockish=true;

	private static final boolean jagged=false;
	private static final boolean workday=true;
	private static final boolean showSeconds=false; // in the time format, will still show the seconds pie/ring

	private static final boolean floatingTime=false;

	private static final int WORK_START = 8;    //8am
	private static final int WORK_END   = 5+12; //5pm

	//For non-pacman, origin is on the right; e.g. sin(theta)
	//-90 is facing right, 0 is facing up, 90 is facing left, etc...
	//private static final int PACMAN_OFFSET=-90; //pacman
	private static final int PACMAN_OFFSET=-90; //time keeper
	
	protected DecimalFormat leadingZeros, tf;
	protected int[] ringLastValue=new int[MAX_RINGS];
	protected boolean done = false;
	
	public PolarClock() {
		new Thread(this).start();
		tf = new DecimalFormat("#0");
		leadingZeros = new DecimalFormat("00");
	}
	
	public void stop() {
		done = true;
	}
	
	//The size as of last paint()
	private Dimension size;

	private Calendar myCal       = Calendar.getInstance();
	private Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	boolean flutter;

	/* paint() - get current time and draw (centered) in Component. */
	public void paint(Graphics g) {
		//NB: calendars have internal dates
		final
		long now=System.currentTimeMillis();

		myCal.setTimeInMillis(now);
		utcCalendar.setTimeInMillis(now);

		int hours=myCal.get(Calendar.HOUR_OF_DAY);
		int shortHour=myCal.get(Calendar.HOUR);
		int minutes=myCal.get(Calendar.MINUTE);
		int seconds=myCal.get(Calendar.SECOND);
		
		flutter=(seconds%2==0);
		
		StringBuffer sb = new StringBuffer();
		String longHour_s;
		if (hours<10) {
			longHour_s="0"+Integer.toString(hours);
		} else {
			longHour_s=Integer.toString(hours);
		}
		String shortHour_s;
		if (shortHour==00) {
			shortHour_s="12";
		} else if (shortHour<10) {
			shortHour_s="0"+Integer.toString(shortHour);
		} else {
			shortHour_s=Integer.toString(shortHour);
		}
		String normalHoursFormat;
		String otherHoursFormat;
		if (military) {
			sb.append(longHour_s);
			normalHoursFormat=longHour_s;
			otherHoursFormat=shortHour_s;
		} else {
			sb.append(shortHour_s);
			normalHoursFormat=shortHour_s;
			otherHoursFormat=longHour_s;
		}
		sb.append(':');
		sb.append(leadingZeros.format(minutes));

		if (showSeconds) {
			sb.append(':');
			sb.append(leadingZeros.format(seconds));
		}

		String s = sb.toString();

		if (this.font==null) {
			originalFont=getFont();
			font=originalFont.deriveFont((float)31);
			setFont(font);
			g.setFont(font);
		}

		FontMetrics fm = getFontMetrics(getFont());
		size=getSize();
		int textX = (size.width - fm.stringWidth(s)) / 2;
		boolean pm=shortHour<hours;
		
		resetRings();

		int maxHours=24;

		boolean workMode;

		if (workday && hours>=WORK_START && hours<WORK_END)
		{
			hours-=WORK_START;
			maxHours=WORK_END-WORK_START;
			workMode=true;
		}
		else
		{
			workMode=false;
		}
		
		// hour biggest
		fractionalExtension=minutes/60.0;
		drawRing(g, hours, maxHours, true);
		int hourDegrees=lastRingsEnd;
		drawRing(g, minutes, 60, true);
		
		if (showSeconds)
		{
			drawRing(g, seconds, 60, false);
		}
		
		//int hourDegrees=lastRingsEnd;
		//Subtract five degrees
		double hourRadians=( -10 -hourDegrees)*Math.PI/180;
		//Draw time last, on top
		//g.drawString(s, textX, 10);
		//Draw the time about where the hour would be
		int x=(size.width-fm.stringWidth(s))/2+(int)(Math.cos(hourRadians)*size.width/4);
		int y=size.height/2+(int)(Math.sin(hourRadians)*size.height/4);
		
		if (!floatingTime) {
			x=2;
			y=size.height/2-3;//+10;//+fm.getHeight()/2;
		}
		outlinedString(g, s, x, y, Color.BLUE);

		int zuluHours=utcCalendar.get(Calendar.HOUR_OF_DAY);

		if (true) {
			int y2=y+fm.getHeight();
			if (zuluHours<10) {
				outlinedString(g, "0"+Integer.toString(zuluHours)+'z', x, y2, Color.MAGENTA);
			} else {
				outlinedString(g, Integer.toString(zuluHours)+'z', x, y2, Color.MAGENTA);
			}
		}

		if (!normalHoursFormat.equals(otherHoursFormat)) {
			int y2=y-fm.getHeight();
			outlinedString(g, otherHoursFormat, x, y2, Color.GREEN);
		}

		if (workMode)
		{
			if (this.workFont==null) {
				//workFont=originalFont.deriveFont((float)14);
				workFont=originalFont;
			}
			g.setFont(workFont);
			fm = getFontMetrics(workFont);

			outlinedString(g, "Work Mode", 3, 15, Color.BLACK);
			//System.err.println("now: "+now);

			final
			Calendar cal=myCal;

			final
			long startMillis;
			{
				cal.set(Calendar.HOUR_OF_DAY, WORK_START);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				startMillis=cal.getTimeInMillis();
				//System.err.println("startMillis: "+startMillis);
			}

			final
			long endMillis;
			{
				cal.set(Calendar.HOUR_OF_DAY, WORK_END);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				endMillis=cal.getTimeInMillis();
				//System.err.println("endMillis: "+endMillis);
			}

			final
			long totalWorkMillis=endMillis-startMillis;
			//System.err.println("totalWorkMillis: "+totalWorkMillis);

			final
			long consumedWorkMillis=now-startMillis;
			//System.err.println("consumedWorkMillis: "+consumedWorkMillis);

			final
			long workMillisRemaining=endMillis-now;

			final
			int workPercentComplete=(int)(100*consumedWorkMillis/totalWorkMillis);

			s=String.format("%s%%", workPercentComplete);
			{
				int stringPixels=fm.stringWidth(s);
				x=size.width/2-stringPixels/2;
				y=(size.height/2);//-fm.getHeight());
				outlinedString(g, s, x, y, Color.BLACK);
			}

			s=getDuration(workMillisRemaining);
			{
				int stringPixels=fm.stringWidth(s);
				x=size.width/2-stringPixels/2;
				y=(size.height/2+fm.getHeight());
				outlinedString(g, s, x, y, Color.BLACK);
			}
			g.setFont(font);
		}
	}

	private static
	String getDuration(long millis)
	{
		long seconds=millis/1000;

		if (seconds>=60)
		{
			long minutes=seconds/60;
			seconds-=minutes*60;

			if (minutes >= 60)
			{
				long hours=minutes/60;
				minutes-=hours*60;
				return String.format("%dh%dm", hours, minutes);
			}
			else
			{
				return String.format("%dm%ds", minutes, seconds);
			}
		}
		else
		{
			return String.format("%ds", seconds);
		}
	}

	private Font originalFont;
	private Font font;
	private Font workFont;

	private void outlinedString(Graphics g, String s, int x, int y, Color c) {
		g.setColor(Color.WHITE);
		g.drawString(s, x-1, y  );
		g.drawString(s, x-1, y-1);
		g.drawString(s, x  , y-1);
		g.drawString(s, x+1, y+1);
		g.drawString(s, x+1, y  );
		g.drawString(s, x  , y+1);
		g.drawString(s, x-1, y+1);
		g.drawString(s, x+1, y-1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private int tickCount=-1;
	private int ringNumber;
	private int lastRingsEnd;
	private double fractionalExtension;
	
	private void drawRing(Graphics g, int passed, int maximum, boolean drawTicks)
	{
		if (!jagged)
		{
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
											 RenderingHints.VALUE_ANTIALIAS_ON);
		}
		//'n' is the ringNumber
		int n = ringNumber;
		//'n'th concentric rectangle of width RING_WIDTH
		int x=RING_WIDTH*n;
		int y=RING_WIDTH*n;
		int w=size.width-2*RING_WIDTH*n;
		int h=size.height-2*RING_WIDTH*n;
		double degreesPerWedge = 360/maximum;
		int degrees = (int)(degreesPerWedge * passed);
		
		int start;
		boolean clearCenter=true;
		if (clockish)
		{
			start=90;
			degrees*=-1;
		}
		else
		if (pacman)
		{
			//No matter where the last ring ended, center it at the bottom
			start = - degrees/2;
			clearCenter=false;
			degrees*=-1;
		}
		else
		{
			start = lastRingsEnd;
			//The negatives are to go the 'conventional' way of a clock
			lastRingsEnd = start-degrees;
			degrees*=-1;
		}


		{
			//garunteed that (ringLastValue[n]<degrees)
			//that
			//remember the last value
			ringLastValue[n]=degrees;
		}

		//Start with a solid background
		{
			g.setColor(Color.WHITE);
			g.fillArc(x, y, w, h, 0, 360);
		}

		// Draw the ring (which at this point are circle-wedges). Minding that the
		// 'fractionalExtension' (if present) is a different color.
		{
			// Draw the ring that we want (over-draws as a 'wedge')
			g.setColor(ringColors[n]);
			g.fillArc(x, y, w, h, start, degrees);

			if (fractionalExtension > 0 /*&& flutter*/)
			{
				int extraDegrees=(int)(degreesPerWedge * fractionalExtension);
				//System.err.println(extraDegrees);
				g.setColor(Color.LIGHT_GRAY);
				g.fillArc(x, y, w, h, start+degrees, -extraDegrees);
				ringLastValue[n]-=extraDegrees;
			}
		}

		if (drawTicks)
		{
			final
			double tickIncr=360.0/maximum;

			// If the tick marks are too wide out, make smaller quarter-marks within them.
			if (maximum <= 12)
			{
				//g.setColor(tickColorLight);
				g.setColor(Color.GRAY);
				for (double i=0; i<360.0; i+=tickIncr/4)
				{
					drawTick(g, x, y, w, h, (int)(start-i));
				}
				
				// The way we get the 'partial tick' is just to overwrite these tick marks so they
				// don't cut across the whole ring. So now we have to DRAW BOTH THE RINGS AGAIN...
				//but this time shorter.
				{
					int rw=RING_WIDTH;
					int hrw=RING_WIDTH/2;
					int aa=2; // anti-alias confounder
					
					//We must erase the background, or else the quarter-tick marks 'in the air' will be far too long & distracting.
					g.setColor(Color.WHITE);
					g.fillArc(x+hrw+aa, y+hrw+aa, w-rw-2*aa, h-rw-2*aa, 0, 360);
		
					// REDRAW the ring segments that we just drew-over...
					g.setColor(ringColors[n]);
					g.fillArc(x+hrw, y+hrw, w-rw, h-rw, start, degrees);
		
					if (fractionalExtension > 0 /*&& flutter*/)
					{
						int extraDegrees=(int)(degreesPerWedge * fractionalExtension);
						//System.err.println(extraDegrees);
						g.setColor(Color.LIGHT_GRAY);
						g.fillArc(x+hrw, y+hrw, w-rw, h-rw, start+degrees, -extraDegrees);
						ringLastValue[n]-=extraDegrees;
					}
				}
			}

			//g.setColor(tickColorOld);
			if (tickIncr>1.0)
			{
				int numDrawn=0;

				g.setColor(Color.GRAY);

				for (double i=0; i<360.0; i+=tickIncr)
				{
					//g.setColor(!landsOnRing(i, start, degrees) ? tickColorLight : tickColorDark);
					drawTick(g, x, y, w, h, (int)(start-i));
					numDrawn++;
				}

				/*Redraw/embolden ruler-function marks if it might help
				//If not drawing all the marks, this algorithim has the odd (but semi-natural) side effect of making more/bolder tick marks as required.
				double lastIncrementMultiplier=startingMultiplier;
				while (numDrawn>maxEasilyCountableWedges)
				{
					numDrawn=0;
					double rulerIncr=(tickIncr*lastIncrementMultiplier);
					for (double i=(skipFirstTick?rulerIncr:0); i<max; i=i+rulerIncr) {
						drawTick(g, x, y, w, h, (int)(start-i));
						numDrawn++;
					}
					lastIncrementMultiplier*=2;
				}
				*/
			}
		}
		
		// Erase the 'inside' of the arc with white, making the circle a ring
		g.setColor(Color.WHITE);
		//g.fillArc(x+RING_WIDTH-GUTTER, y+RING_WIDTH-GUTTER, w-2*RING_WIDTH+2*GUTTER, h-2*RING_WIDTH+2*GUTTER, start, ringLastValue[n]);
		g.fillArc(x+RING_WIDTH-GUTTER, y+RING_WIDTH-GUTTER, w-2*RING_WIDTH+2*GUTTER, h-2*RING_WIDTH+2*GUTTER, 0, 360);
		
		ringNumber++;
		fractionalExtension=0;
	}
	
	private boolean kludge_lastWasOnRing;
	
	// TODO: BUG!!! This is totally wrong/broken! Probably best to rewrite/shift everything so that the clock-0 point is the degrees=0 point... then maybe I wouldn't get so confused.
	private
	boolean landsOnRing(double value, int startingOffset, int negativeIncrement)
	{
		int max=startingOffset+negativeIncrement;

		final
		boolean retval;

		if (max < startingOffset)
		{
			//It's inverted... on the 'outside'.
			retval=value < startingOffset && value > max;
		}
		else
		{
			retval=value > startingOffset && value < max;
		}
		
		if (retval != kludge_lastWasOnRing)
		{
			System.err.println(String.format("DEBUG: landsOnRing(%f,%d,%d); max:%d -> %b", value, startingOffset, negativeIncrement, max, retval));
			kludge_lastWasOnRing=retval;
		}
		return retval;
	}

	private
	void drawTick(Graphics g, int x, int y, int w, int h, int degrees)
	{
		g.fillArc(x, y, w, h, degrees, -1);
	}

	private void resetRings() {
		ringNumber=0;
		//lastRingsEnd=90;
		lastRingsEnd=PACMAN_OFFSET;
		fractionalExtension=0;
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(300, 300);
	}
	
	public Dimension getMinimumSize() {
		return new Dimension(50, 10);
	}

	public void run() {
		int longDelay=1000;
		int shortDelay=1000/TICKS;
		{
			//only use the short delay during animation times
			while (!done) {
				PolarClock.this.repaint(); // request a redraw
				try {
					//System.out.println("Slow...");
					Thread.sleep(longDelay);
				} catch (InterruptedException e) { /* do nothing */
				}
			}
		}
	}
	
	public static void main(String args[]) {
		JFrame window = new PolarClockFrame();
		window.setVisible(true);
	}
}

class PolarClockFrame extends JFrame {
	
    //===================================================== fields
    private PolarClock pc = new PolarClock();
	
    //================================================ Constructor
    PolarClockFrame() {
		
        //... Add components to layout
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout(5, 5));
        content.add(pc, BorderLayout.CENTER);
		
        //... Set window characteristics
        setContentPane(content);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Polar Clock");
        setLocationRelativeTo(null);  // Center window.
        pack();
    }
	
}
