package org.twak.tweed.dbg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.twak.tweed.Tweed;
import org.twak.utils.DRectangle;
import org.twak.utils.ListDownLayout;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.Rainbow;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.ui.WindowManager;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

public class Plot extends JComponent {

	PanMouseAdaptor ma;

	String text= "";
	
	public List<Object> toPaint = new ArrayList();
	
	boolean firstFrame = true;
	
	public void publicPaintComponent(Graphics g) {
		paintComponent( g );
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;
		
		g2.setColor(Color.white);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor( new Color(0,0,0,10));
		
		PaintThing.resetBounds();
		
		paint(toPaint, g2);
		
		DRectangle bounds = PaintThing.getBounds();
		if (firstFrame && bounds != null) {
			if ( bounds != null )
				ma.view( bounds );
			else
				ma.resetView();
			firstFrame = false;
		}
		
	}
	
	
	private void paint(Iterable<Object> p, Graphics2D g2) {
		
		int count = 4;
		for (Object o : p) {
			g2.setColor(Rainbow.getColour( count++ ) );
			PaintThing.paint(o, g2, ma);
		}
		
		g2.setColor(Color.black);
		PaintThing.paintDebug( g2, ma );
		g2.drawString( text, 20, getHeight() - 30 );
		
	}
	
	public static class Origin implements ICanPaint {

		@Override
		public void paint( Graphics2D g, PanMouseAdaptor ma ) {
			
			g.setColor( Color.black );
			
			int big = 10000;
			
			g.drawLine(
					ma.toX(0),
					0,
					ma.toX(0),
					big );
			
			g.drawLine(
					0,
					ma.toY(0),
					big,
					ma.toY(0) );
					
		}
		
	}

	public static JFrame open = null;
	JPanel controls;
	static Plot last;
	
	public Plot(Object...o) {

		this();
		
		{
//			Point location = null;
//			Dimension lastDim
			
			JFrame frame = new JFrame( "plot" );
			WindowManager.register( frame );


			if (last != null)
				ma.resetView( last.ma );
			
			frame.setLayout( new BorderLayout() );
			frame.add( this, BorderLayout.CENTER );
			
			controls = new JPanel(new ListDownLayout());
			for (int i = 0; i < o.length; i++) 
				if (o[i] instanceof JComponent) {
					controls.add( (JComponent ) o[i]);
					o[i] = null;
				}
			
			if (controls.getComponentCount() > 0)
				frame.add( controls, BorderLayout.EAST );

			frame.pack();
			frame.setVisible( true );
			
			if ( open != null ) {
				try{ 
				frame.setLocation( open.getLocationOnScreen() );
				frame.setSize( open.getSize() );
//				frame.setState( open.getState() );
				
				open.setVisible( false );
				open.dispose();
				}
				catch (Throwable th) {}
			}
			
			
			last = this;
			open = frame;
		}
		
		toPaint.addAll(Arrays.asList(o));
	}

	
	public Plot add (Object o) {
		
		if (o instanceof JComponent) {
			controls.add( (JComponent) o);
			controls.revalidate();
		}
		else {
			toPaint.add(o);
			repaint();
		}
		
		return this;
	}

	private void dumpSVG() {
		
		// Get a DOMImplementation.
		DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
		// Create an instance of org.w3c.dom.Document.
		String svgNS = "http://www.w3.org/2000/svg";
		Document document = domImpl.createDocument( svgNS, "svg", null );
		// Create an instance of the SVG Generator.
		SVGGraphics2D svgGenerator = new SVGGraphics2D( document );
		// Ask the test to render into the SVG Graphics2D implementation.
		paintComponent( svgGenerator );

		try {
			svgGenerator.stream( new FileWriter( Tweed.CONFIG + "dump.svg" ), true );
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
	}
	
	public interface ICanEdit {
		public double getDistance (Point2d pt);
		public void mouseDown(MouseEvent e, PanMouseAdaptor ma);
		public void mouseDragged(MouseEvent e, PanMouseAdaptor ma);
		public void mouseReleased(MouseEvent e, PanMouseAdaptor ma);
		public void getMenu( MouseEvent e, PanMouseAdaptor ma, ChangeListener cl );
	}
	
	List<Changed> onChange = new ArrayList<>();
	
	public void addEditListener (Changed changed) {
		onChange.add( changed );
	}
	
	ICanEdit clickedOn = null;
	
	public Plot() {
		ma = new PanMouseAdaptor(this);
		ma.button = MouseEvent.BUTTON1;
		
		this.setPreferredSize(new Dimension(600, 600));

		MouseAdapter m = new MouseAdapter() {
			
			public void mouseClicked(java.awt.event.MouseEvent e) {
				setText(""+ ma.from( e ) );
			};
			
			public void mousePressed(java.awt.event.MouseEvent e) {
				
					ICanEdit bestEdit = null;
					double bestDist = 10;//px

					for ( Object o : toPaint )
						if ( o instanceof ICanEdit ) {
							ICanEdit ice = (ICanEdit) o;
							double dist = ma.toZoom( ice.getDistance( ma.from( e ) ) );
							if ( dist < bestDist ) {
								bestDist = dist;
								bestEdit = ice;
							}
						}

				if ( e.getButton() == MouseEvent.BUTTON3 ) {
					clickedOn = bestEdit;

					if ( clickedOn != null )
						clickedOn.mouseDown( e, ma );

					repaint();
				}
				else if (e.getButton() == MouseEvent.BUTTON2) {
					if (bestEdit != null)
						bestEdit.getMenu(e, ma, new ChangeListener() {
							@Override
							public void stateChanged( ChangeEvent e ) {
								Plot.this.repaint();

								for (Changed c : onChange)
									c.changed();
							}
						});
				}
			};
			
			public void mouseDragged(java.awt.event.MouseEvent e) {
				if (clickedOn != null)
					clickedOn.mouseDragged( e, ma );
				
				repaint();
			};
			
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (clickedOn != null)
					clickedOn.mouseReleased( e, ma );
				repaint();
				for (Changed c : onChange)
					c.changed();
			};
			
			public void mouseExited(java.awt.event.MouseEvent e) {
				setText("");
				mouseReleased( e );
			};
		};
		
		addMouseListener( m );
		addMouseMotionListener( m );
		
		addKeyListener( new KeyListener() {
			
			@Override
			public void keyTyped( KeyEvent e ) {
			}
			
			@Override
			public void keyReleased( KeyEvent e ) {
			}
			
			@Override
			public void keyPressed( KeyEvent e ) {
				if (e.getKeyCode() == KeyEvent.VK_S) 
					dumpSVG();
			}
		} );
	}

	protected void setText( String string ) {
		this.text = string;
		repaint();
	}

	public void writeImage( String string ) {
		
		BufferedImage out = new BufferedImage ( getWidth(), getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		paintComponent(out.getGraphics());
		
		try {
			ImageIO.write( out, "png", new File ( string+".png" ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}
	
}
