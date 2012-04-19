
import java.awt.Font;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.BorderLayout;
import java.awt.RenderingHints;

import java.util.Calendar;
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
	
	private static final double FADE_TIME_SECONDS=2.0;
	
	private static final int MAX_RINGS=10;
	
	//Dark on the outside
	private static final Color[] ringColors = new Color[] {
		Color.BLACK,
		Color.DARK_GRAY,
		Color.LIGHT_GRAY,
	};
	
	private static final Color tickColor = new Color(128, 128, 128, 40);
	//the number of adjacent wedges which do not need to be seperated by tick marks
	//conventional clocks use 5; so the default should be a multiple of 5;
	private static final double startingMultiplier=5;
	//for tick marks to quadrants, 4; eigths 8, etc.
	//conventional clocks use 12; good values are 2,4,8 (half, quadrants, eighths)
	//the default is 2 (to make even the 1/2 marker bold)
	private static final int maxEasilyCountableWedges=2;
	
	private static final boolean military=false;
	private static final boolean pacman=false;     //All arcs point the same direction

	private static final boolean jagged=false;
	private static final boolean workday=false;
	private static final boolean smoothSeconds=false;
	private static final boolean skipFirstTick=false;
	private static final boolean drawAllTicks=false;

	private static final boolean floatingTime=false;

	private static final int WORK_START = 8;    //8am
	private static final int WORK_END   = 5+12; //5pm

	//For non-pacman, origin is on the right; e.g. sin(theta)
	//-90 is facing right, 0 is facing up, 90 is facing left, etc...
	//private static final int PACMAN_OFFSET=-90; //pacman
	private static final int PACMAN_OFFSET=180; //time keeper
	
	private static final int FADE_TICKS=(int)(FADE_TIME_SECONDS*TICKS);
	
	protected DecimalFormat tflz, tf;
	protected int[] ringFadeTicks=new int[MAX_RINGS];
	protected int[] ringLastValue=new int[MAX_RINGS];
	protected boolean done = false;
	
	public PolarClock() {
		new Thread(this).start();
		tf = new DecimalFormat("#0");
		tflz = new DecimalFormat("00");
	}
	
	public void stop() {
		done = true;
	}
	
	//The size as of last paint()
	private Dimension size;
	
	/* paint() - get current time and draw (centered) in Component. */
	public void paint(Graphics g) {
		Calendar myCal = Calendar.getInstance();
		int hours=myCal.get(Calendar.HOUR_OF_DAY);
		int shortHour=myCal.get(Calendar.HOUR);
		int minutes=myCal.get(Calendar.MINUTE);
		int seconds=myCal.get(Calendar.SECOND);
		StringBuffer sb = new StringBuffer();
		if (military)
			sb.append(tf.format(hours));
		else if (shortHour!=0) {
			if (shortHour<10) {
				sb.append("0"+tf.format(shortHour));
			} else {
				sb.append(tf.format(shortHour));
			}
		} else
			sb.append(tf.format(12));
		sb.append(':');
		sb.append(tflz.format(minutes));
		sb.append(':');
		sb.append(tflz.format(seconds));
		String s = sb.toString();

		if (this.font==null) {
			font=getFont().deriveFont((float)31);
			setFont(font);
		}

		FontMetrics fm = getFontMetrics(getFont());
		size=getSize();
		int textX = (size.width - fm.stringWidth(s)) / 2;
		boolean pm=shortHour<hours;
		
		resetRings();
		
		/* seconds biggest
		drawRing(g, seconds, 60);
		drawRing(g, minutes, 60);
		drawRing(g, hours, 24);
		 */
		
		int maxHours=24;
		
		if (workday && hours>=WORK_START && hours<=WORK_END) {
			hours-=WORK_START;
			maxHours=WORK_END-WORK_START;
		}
		
		// hour biggest
		drawRing(g, hours, maxHours, true, true);
		int hourDegrees=lastRingsEnd;
		drawRing(g, minutes, 60, true, true);
		if (smoothSeconds) {
			int trueButRoughTicks=seconds*TICKS;
			tickCount++;
			//the first tickCount will be wrong
			if (tickCount<trueButRoughTicks)
				tickCount=trueButRoughTicks;
			if (tickCount>trueButRoughTicks+TICKS)
				tickCount=trueButRoughTicks+TICKS;
			drawRing(g, tickCount, 60*TICKS, false, false);
		} else {
			drawRing(g, seconds, 60, false, false);
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
			y=size.height/2+10;//+fm.getHeight()/2;
		}
		outlinedString(g, s, x, y);
	}

	private Font font;

	private void outlinedString(Graphics g, String s, int x, int y) {
		g.setColor(Color.WHITE);
		g.drawString(s, x-1, y  );
		g.drawString(s, x-1, y-1);
		g.drawString(s, x  , y-1);
		g.drawString(s, x+1, y+1);
		g.drawString(s, x+1, y  );
		g.drawString(s, x  , y+1);
		g.drawString(s, x-1, y+1);
		g.drawString(s, x+1, y-1);
		g.setColor(Color.BLACK);
		g.drawString(s, x, y);
	}

	private int tickCount=-1;
	private int ringNumber;
	private int lastRingsEnd;
	
	private void drawRing(Graphics g, int passed, int maximum, boolean fadeAllChange, boolean drawTicks) {
		if (!jagged) {
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
		int degrees = 360 * passed / maximum;
		int start;
		boolean clearCenter=true;
		if (pacman) {
			//No matter where the last ring ended, center it at the bottom
			start = -90 - degrees/2 + PACMAN_OFFSET;
			clearCenter=false;
		} else {
			start = lastRingsEnd;
			//The negatives are to go the 'conventional' way of a clock
			lastRingsEnd = start-degrees;
			degrees*=-1;
		}
		
		//if we are in a fade, draw the last value proportional to the fade time
		boolean fadeIn=false;
		int fade=ringFadeTicks[n];
		if (fade==0 && ( ringLastValue[n]<degrees || (fadeAllChange && ringLastValue[n]!=degrees))) {
			//we are not fading and the value has jumped backwards... trigger a fade starting this frame
			fade=ringFadeTicks[n]=FADE_TICKS;
		}
		if (fade>0) {
			if (fade>FADE_TICKS || fade<0) {
				System.err.println("Argh... "+n+" / "+fade);
				fade=ringFadeTicks[n]=FADE_TICKS;
			}
			//degrees are negative so this is counter-intuitive
			fadeIn=fadeAllChange && ringLastValue[n]>degrees;
			double opacity=(1.0*fade/FADE_TICKS);
			int alpha=(int)(opacity*255);
			if (alpha<0)
				alpha=0;
			if (alpha>255)
				alpha=255;
			Color normal=ringColors[n];
			//fadeIn means we are transitioning to the new value, so the opaque one is the new value (degrees), the old one is solid
			//!fadeIn means that we have wrapped, so we linger the old (full) value.
			//System.err.println("Fade... "+n+"/"+fadeIn+"/"+fade+"/"+degrees+"/"+ringLastValue[n]);
			if (fadeIn) {
				Color newColor=new Color(normal.getRed(), normal.getGreen(), normal.getBlue(), 255-alpha);
				g.setColor(newColor);
				g.fillArc(x, y, w, h, start, degrees);
				g.setColor(normal);
				g.fillArc(x, y, w, h, start, ringLastValue[n]);
				g.setColor(Color.WHITE);
				g.fillArc(x+RING_WIDTH-GUTTER, y+RING_WIDTH-GUTTER, w-2*RING_WIDTH+2*GUTTER, h-2*RING_WIDTH+2*GUTTER, start, ringLastValue[n]);
			} else {
				Color newColor=new Color(normal.getRed(), normal.getGreen(), normal.getBlue(), alpha);
				g.setColor(newColor);
				g.fillArc(x, y, w, h, start, ringLastValue[n]);
				g.setColor(Color.WHITE);
				g.fillArc(x+RING_WIDTH-GUTTER, y+RING_WIDTH-GUTTER, w-2*RING_WIDTH+2*GUTTER, h-2*RING_WIDTH+2*GUTTER, start, ringLastValue[n]);
			}
			fade--;
			if (fade==0) {
				//we have finished the fade, don't start a new one immediately
				ringLastValue[n]=degrees;
			}
			ringFadeTicks[n]=fade;
		} else {
			//garunteed that (ringLastValue[n]<degrees)
			//that
			//remember the last value
			ringLastValue[n]=degrees;
		}
		
		if (!fadeIn) {
			g.setColor(ringColors[n]);
			g.fillArc(x, y, w, h, start, degrees);
			g.setColor(Color.WHITE);
			g.fillArc(x+RING_WIDTH-GUTTER, y+RING_WIDTH-GUTTER, w-2*RING_WIDTH+2*GUTTER, h-2*RING_WIDTH+2*GUTTER, start, ringLastValue[n]);
		}
		
		if (drawTicks) {
			double tickIncr=360.0/maximum;
			g.setColor(tickColor);
			if (tickIncr>1.0) {
				double max;
				if (drawAllTicks)
					max=359.99;
				else
					max=(-degrees);
				int numDrawn=0;
				//Draw every tick mark once; skip the first (looks more uniform)
				for (double i=(skipFirstTick?tickIncr:0); i<max; i=i+tickIncr) {
					drawTick(g, x, y, w, h, (int)(start-i));
					numDrawn++;
				}
				//Redraw/embolden ruler-function marks if it might help
				//If not drawing all the marks, this algorithim has the odd (but semi-natural) side effect of making more/bolder tick marks as required.
				double lastIncrementMultiplier=startingMultiplier;
				while (numDrawn>maxEasilyCountableWedges) {
					numDrawn=0;
					double rulerIncr=(tickIncr*lastIncrementMultiplier);
					for (double i=(skipFirstTick?rulerIncr:0); i<max; i=i+rulerIncr) {
						drawTick(g, x, y, w, h, (int)(start-i));
						numDrawn++;
					}
					lastIncrementMultiplier*=2;
				}
			}
		}
		
		ringNumber++;
	}
	
	private void drawTick(Graphics g, int x, int y, int w, int h, int degrees) {
		g.fillArc(x, y, w, h, degrees, -1);
	}
	
	private void resetRings() {
		ringNumber=0;
		//lastRingsEnd=90;
		lastRingsEnd=PACMAN_OFFSET;
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(300, 300);
	}
	
	public Dimension getMinimumSize() {
		return new Dimension(50, 10);
	}
	
	private boolean doingAnimation() {
		for (int i=ringFadeTicks.length-1; i>=0; i--) {
			if (ringFadeTicks[i]!=0) {
				return true;
			}
		}
		return false;
	}

	public void run() {
		int longDelay=1000;
		int shortDelay=1000/TICKS;
		if (smoothSeconds) {
			//always use the short delay
			while (!done) {
				PolarClock.this.repaint(); // request a redraw
				try {
					Thread.sleep(shortDelay);
				} catch (InterruptedException e) { /* do nothing */
				}
			}
		} else {
			//only use the short delay during animation times
			while (!done) {
				PolarClock.this.repaint(); // request a redraw
				try {
					if (doingAnimation()) {
						//System.out.print("Quick, ");
						Thread.sleep(shortDelay);
					} else {
						//System.out.println("Slow...");
						Thread.sleep(longDelay);
					}
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
