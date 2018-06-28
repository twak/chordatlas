package org.twak.viewTrace.franken.style.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

import org.twak.utils.Imagez;
import org.twak.utils.ui.SimpleFileChooser;

public class ImageFileDrop extends JComponent {

	JLabel empty = new JLabel("style");
	BufferedImage dropped;
	
	boolean hover;
	
	public ImageFileDrop(BufferedImage image) {
//		super (label);
		
		setPreferredSize( new Dimension( 200,80) );
//		setHorizontalAlignment( SwingConstants.CENTER );
//		setBorder( new LineBorder( Color.black, 3 ) );
//		setOpaque( true );
//		setBackground( Color.white );
//		setForeground( Color.black );
		
		MouseAdapter ma = new MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent e) {
				hover = true;
				repaint();
			};
			public void mouseExited(java.awt.event.MouseEvent e) {
				hover = false;
				repaint();
			};
			public void mouseDragged(java.awt.event.MouseEvent e) {
				hover = true;
				repaint();
			};
			
			@Override
			public void mouseClicked( MouseEvent e ) {
				
				if (e.getButton() == 3) {
					rightClick();
				} else {
				
				new SimpleFileChooser(null, false, "select image file, or drag n drop") {
					@Override
					public void heresTheFile( File f ) throws Throwable {
						BufferedImage read = process(f);
						setImage( read );
					}
				}; }
			}
		};
		
		addMouseListener( ma );
		addMouseMotionListener( ma );
		
		setDropTarget(new DropTarget() {
		    public synchronized void drop(DropTargetDropEvent evt) {
		        try {
		            evt.acceptDrop(DnDConstants.ACTION_COPY);
		            List<File> droppedFiles = (List<File>)
		                evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
		            new Thread() {
		            	public void run() {
		            		for (File file : droppedFiles) {
		            			dropped = null;
		            			ImageFileDrop.this.repaint();
								BufferedImage read = process(file);
								
								setImage( read );
		            		}
		            	};
		            }.start();
		        } catch (Exception ex) {
		            ex.printStackTrace();
		        }
		    }
		});
		
		setImage( image );
	}

	private void setImage( BufferedImage read ) {
		if (read != null ) {
			dropped = Imagez.scaleLongest( read, 80 );
			repaint();
		}
	}
	
	@Override
	protected void paintComponent( Graphics g ) {
		super.paintComponent( g );
		
		g.setColor( hover ? Color.black : Color.darkGray );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		
		if (dropped != null) {
			g.drawImage( dropped, (int) ( (getWidth() - getHeight() ) / 2 ), 0, null );
		}
		
		if (hover) {
			empty.setText( "drop" );
//			g.setColor( Color.gray );
			empty.setSize( getSize() );
			empty.paintComponents( g );
		}
		else if (dropped == null ){
			empty.setText( "drop style image here" );
			empty.setForeground( Color.white );
//			g.setColor( Color.black );
			empty.setSize( getSize() );
			empty.paintComponents( g );
		}
		
		empty.paintComponents( g );
	}
	
	public void rightClick() {}
	
	public BufferedImage process (File f) {
		return null;
	}
}
